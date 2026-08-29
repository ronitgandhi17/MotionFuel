import { clerkMiddleware, getAuth } from "@clerk/express";
import express from "express";
import Stripe from "stripe";
import { applicationDefault, cert, getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";

const requiredEnvironment = [
  "CLERK_PUBLISHABLE_KEY",
  "CLERK_SECRET_KEY",
  "STRIPE_SECRET_KEY",
  "STRIPE_PRO_MONTHLY_PRICE_ID",
  "MEMBERSHIP_RETURN_URL",
];
// Fails at startup instead of serving billing routes with missing secret configuration.
const missingEnvironment = requiredEnvironment.filter((name) => !process.env[name]);
if (missingEnvironment.length > 0) {
  throw new Error(`Missing environment variables: ${missingEnvironment.join(", ")}`);
}

const stripe = new Stripe(process.env.STRIPE_SECRET_KEY);
const app = express();

// Lazily initialises the Firebase Admin SDK so billing keeps working even without Firestore configured.
let firestoreInstance;
let firestoreInitialised = false;
function getFirestoreOrNull() {
  if (firestoreInitialised) return firestoreInstance;
  firestoreInitialised = true;
  try {
    if (getApps().length === 0) {
      const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT;
      if (serviceAccountJson) {
        // Preferred: a full service-account JSON string kept only in the server environment.
        initializeApp({ credential: cert(JSON.parse(serviceAccountJson)) });
      } else if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
        // Fallback: Application Default Credentials referenced by the standard Google env var.
        initializeApp({ credential: applicationDefault() });
      } else {
        firestoreInstance = null;
        return null;
      }
    }
    firestoreInstance = getFirestore();
  } catch (error) {
    console.error("Firebase Admin initialisation failed:", error.message);
    firestoreInstance = null;
  }
  return firestoreInstance;
}

// Rejects sync requests with 503 until a Firestore service account is configured on this server.
function requireFirestore(request, response, next) {
  const database = getFirestoreOrNull();
  if (!database) {
    return response.status(503).json({ error: "Cloud sync is not configured on this server." });
  }
  request.firestore = database;
  return next();
}

// Stripe signature verification requires the exact raw request body.
app.post("/stripe/webhook", express.raw({ type: "application/json" }), (request, response) => {
  if (!process.env.STRIPE_WEBHOOK_SECRET) {
    return response.status(503).json({ error: "STRIPE_WEBHOOK_SECRET is not configured." });
  }
  try {
    const event = stripe.webhooks.constructEvent(
      request.body,
      request.headers["stripe-signature"],
      process.env.STRIPE_WEBHOOK_SECRET,
    );
    if (
      event.type === "customer.subscription.updated" ||
      event.type === "customer.subscription.deleted" ||
      event.type === "invoice.paid" ||
      event.type === "invoice.payment_failed"
    ) {
      console.info(`Verified Stripe membership event: ${event.type}`);
    }
    return response.json({ received: true });
  } catch (error) {
    return response.status(400).json({ error: `Invalid Stripe signature: ${error.message}` });
  }
});

app.use(express.json());
// Verifies Clerk session tokens before protected billing middleware runs.
app.use(clerkMiddleware());

app.get("/health", (_request, response) => response.json({ ok: true }));

// Rejects billing requests that do not contain a valid signed-in Clerk user.
function requireClerkUser(request, response, next) {
  const { userId } = getAuth(request);
  if (!userId) return response.status(401).json({ error: "A valid Clerk session is required." });
  request.clerkUserId = userId;
  return next();
}

// Finds or creates the Stripe Customer permanently mapped to one Clerk user ID.
async function stripeCustomerForClerkUser(clerkUserId, createIfMissing = true) {
  const escapedUserId = clerkUserId.replaceAll("'", "\\'");
  const matches = await stripe.customers.search({
    query: `metadata['clerk_user_id']:'${escapedUserId}'`,
    limit: 1,
  });
  if (matches.data[0]) return matches.data[0];
  if (!createIfMissing) return null;
  return stripe.customers.create({ metadata: { clerk_user_id: clerkUserId } });
}

// Chooses the most important subscription state when Stripe returns older records too.
function membershipFromSubscriptions(subscriptions) {
  const priority = ["active", "trialing", "past_due", "incomplete", "canceled"];
  return [...subscriptions].sort(
    (left, right) => priority.indexOf(left.status) - priority.indexOf(right.status),
  )[0];
}

// Returns membership state from Stripe instead of trusting the Android client.
app.get("/billing/membership", requireClerkUser, async (request, response, next) => {
  try {
    const customer = await stripeCustomerForClerkUser(request.clerkUserId, false);
    if (!customer) return response.json({ status: "free" });
    const subscriptions = await stripe.subscriptions.list({
      customer: customer.id,
      status: "all",
      limit: 20,
    });
    const membership = membershipFromSubscriptions(subscriptions.data);
    if (!membership) return response.json({ status: "free" });
    const periodEnd =
      membership.items.data[0]?.current_period_end ?? membership.current_period_end ?? null;
    return response.json({
      status: membership.status,
      currentPeriodEnd: periodEnd,
    });
  } catch (error) {
    return next(error);
  }
});

// Creates the fixed server-selected monthly plan and returns short-lived PaymentSheet values.
app.post("/billing/subscription", requireClerkUser, async (request, response, next) => {
  try {
    if (request.body?.plan !== "pro_monthly") {
      return response.status(400).json({ error: "Unknown membership plan." });
    }
    const customer = await stripeCustomerForClerkUser(request.clerkUserId);
    const existing = await stripe.subscriptions.list({
      customer: customer.id,
      status: "all",
      limit: 20,
    });
    if (existing.data.some((item) => ["active", "trialing", "past_due"].includes(item.status))) {
      return response.status(409).json({ error: "A membership already exists. Open billing to manage it." });
    }

    const subscription = await stripe.subscriptions.create({
      customer: customer.id,
      items: [{ price: process.env.STRIPE_PRO_MONTHLY_PRICE_ID }],
      payment_behavior: "default_incomplete",
      payment_settings: { save_default_payment_method: "on_subscription" },
      metadata: {
        clerk_user_id: request.clerkUserId,
        motionfuel_plan: "pro_monthly",
      },
      expand: ["latest_invoice.confirmation_secret"],
    });
    const clientSecret = subscription.latest_invoice?.confirmation_secret?.client_secret;
    if (!clientSecret) throw new Error("Stripe did not return a subscription confirmation secret.");

    const ephemeralKey = await stripe.ephemeralKeys.create(
      { customer: customer.id },
      { apiVersion: "2025-06-30.basil" },
    );
    return response.json({
      subscriptionId: subscription.id,
      clientSecret,
      ephemeralKey: ephemeralKey.secret,
      customerId: customer.id,
    });
  } catch (error) {
    return next(error);
  }
});

// Creates an authenticated Stripe Customer Portal session for membership management.
app.post("/billing/portal", requireClerkUser, async (request, response, next) => {
  try {
    const customer = await stripeCustomerForClerkUser(request.clerkUserId);
    const session = await stripe.billingPortal.sessions.create({
      customer: customer.id,
      return_url: process.env.MEMBERSHIP_RETURN_URL,
    });
    return response.json({ url: session.url });
  } catch (error) {
    return next(error);
  }
});

// Clamps a numeric field into a safe range, ignoring client values that are missing or malformed.
function num(value, min, max, fallback = 0) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return fallback;
  return Math.min(max, Math.max(min, parsed));
}

// Returns a length-capped string, or an empty string when the client value is not a string.
function str(value, maxLength = 200) {
  return typeof value === "string" ? value.slice(0, maxLength) : "";
}

const WORKOUT_TYPES = new Set(["WALK", "RUN"]);
const ACTIVITY_TYPES = new Set(["STATIONARY", "WALKING", "RUNNING", "UNKNOWN"]);
const MEAL_TYPES = new Set(["BREAKFAST", "LUNCH", "DINNER", "SNACK"]);

// Rebuilds a workout document from client input, forcing ownership to the verified Clerk user.
function sanitizeWorkout(raw, uid) {
  const id = str(raw?.id, 128);
  if (!id) return null;
  const route = Array.isArray(raw?.route)
    ? raw.route.slice(0, 5000).map((point) => ({
        lat: num(point?.lat, -90, 90),
        lon: num(point?.lon, -180, 180),
        alt: point?.alt == null ? null : num(point.alt, -1000, 12000),
        accuracy: num(point?.accuracy, 0, 10000, 5),
        time: num(point?.time, 0, Number.MAX_SAFE_INTEGER),
      }))
    : [];
  return {
    id,
    type: WORKOUT_TYPES.has(raw?.type) ? raw.type : "RUN",
    startedAtMillis: num(raw?.startedAtMillis, 0, Number.MAX_SAFE_INTEGER),
    durationSeconds: num(raw?.durationSeconds, 0, 604800),
    distanceMeters: num(raw?.distanceMeters, 0, 1000000),
    averagePaceSecPerKm: raw?.averagePaceSecPerKm == null ? null : num(raw.averagePaceSecPerKm, 0, 100000),
    steps: num(raw?.steps, 0, 10000000),
    elevationGainMeters: num(raw?.elevationGainMeters, 0, 100000),
    caloriesKcal: num(raw?.caloriesKcal, 0, 100000),
    dominantActivity: ACTIVITY_TYPES.has(raw?.dominantActivity) ? raw.dominantActivity : "UNKNOWN",
    rejectedGpsPoints: num(raw?.rejectedGpsPoints, 0, 1000000),
    route,
    ownerId: uid,
    updatedAt: FieldValue.serverTimestamp(),
  };
}

// Rebuilds a nutrition document from client input, forcing ownership to the verified Clerk user.
function sanitizeNutrition(raw, uid) {
  const id = str(raw?.id, 128);
  if (!id) return null;
  return {
    id,
    name: str(raw?.name, 200) || "Food",
    caloriesKcal: num(raw?.caloriesKcal, 0, 100000),
    proteinG: num(raw?.proteinG, 0, 100000),
    carbohydratesG: num(raw?.carbohydratesG, 0, 100000),
    fatG: num(raw?.fatG, 0, 100000),
    mealType: MEAL_TYPES.has(raw?.mealType) ? raw.mealType : "SNACK",
    consumedAtMillis: num(raw?.consumedAtMillis, 0, Number.MAX_SAFE_INTEGER),
    createdOffline: Boolean(raw?.createdOffline),
    ownerId: uid,
    updatedAt: FieldValue.serverTimestamp(),
  };
}

// Removes server-internal fields before returning documents to the Android client.
function stripInternal(document) {
  const { ownerId, updatedAt, ...rest } = document;
  return rest;
}

// Commits writes in chunks so a large sync stays within Firestore's 500-operation batch limit.
async function commitInChunks(database, entries) {
  for (let index = 0; index < entries.length; index += 450) {
    const batch = database.batch();
    for (const entry of entries.slice(index, index + 450)) {
      batch.set(entry.ref, entry.data, { merge: true });
    }
    await batch.commit();
  }
}

// Persists the signed-in user's workouts and nutrition entries to their private Firestore scope.
app.post("/sync/push", requireClerkUser, requireFirestore, async (request, response, next) => {
  try {
    const uid = request.clerkUserId;
    const database = request.firestore;
    const userDocument = database.collection("users").doc(uid);
    const rawWorkouts = Array.isArray(request.body?.workouts) ? request.body.workouts : [];
    const rawNutrition = Array.isArray(request.body?.nutritionEntries) ? request.body.nutritionEntries : [];

    const entries = [];
    for (const raw of rawWorkouts) {
      const workout = sanitizeWorkout(raw, uid);
      if (workout) entries.push({ ref: userDocument.collection("workouts").doc(workout.id), data: workout });
    }
    for (const raw of rawNutrition) {
      const entry = sanitizeNutrition(raw, uid);
      if (entry) entries.push({ ref: userDocument.collection("nutritionEntries").doc(entry.id), data: entry });
    }
    await commitInChunks(database, entries);
    return response.json({ written: entries.length });
  } catch (error) {
    return next(error);
  }
});

// Returns the signed-in user's cloud workouts and nutrition entries for local mirroring.
app.get("/sync/pull", requireClerkUser, requireFirestore, async (request, response, next) => {
  try {
    const uid = request.clerkUserId;
    const database = request.firestore;
    const userDocument = database.collection("users").doc(uid);
    const [workoutSnapshot, nutritionSnapshot] = await Promise.all([
      userDocument.collection("workouts").orderBy("startedAtMillis", "desc").limit(500).get(),
      userDocument.collection("nutritionEntries").orderBy("consumedAtMillis", "desc").limit(500).get(),
    ]);
    return response.json({
      workouts: workoutSnapshot.docs.map((snapshot) => stripInternal(snapshot.data())),
      nutritionEntries: nutritionSnapshot.docs.map((snapshot) => stripInternal(snapshot.data())),
    });
  } catch (error) {
    return next(error);
  }
});

// Converts unexpected server failures into a consistent JSON response.
app.use((error, _request, response, _next) => {
  console.error(error);
  response.status(500).json({ error: error.message || "Membership service failed." });
});

const port = Number(process.env.PORT || 4242);
// Starts the membership API on the configured hosting port.
app.listen(port, () => console.info(`MotionFuel membership API listening on ${port}`));
