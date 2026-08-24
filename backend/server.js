import { clerkMiddleware, getAuth } from "@clerk/express";
import express from "express";
import Stripe from "stripe";

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

// Converts unexpected server failures into a consistent JSON response.
app.use((error, _request, response, _next) => {
  console.error(error);
  response.status(500).json({ error: error.message || "Membership service failed." });
});

const port = Number(process.env.PORT || 4242);
// Starts the membership API on the configured hosting port.
app.listen(port, () => console.info(`MotionFuel membership API listening on ${port}`));
