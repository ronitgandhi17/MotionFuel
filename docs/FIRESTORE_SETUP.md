# Firestore cloud sync (backend gateway)

MotionFuel stores workouts and nutrition entries in Firestore, but the Android app **never** talks to Firestore directly. Every read and write flows through the same Clerk-verified Node backend that handles billing, using the **Firebase Admin SDK** on the server. There is no Firebase client SDK in the APK and no `google-services.json` — cloud sync needs only server-side configuration.

```
Android (Room = source of truth)
  └─ SyncWorker (WorkManager) ─ Clerk JWT ─▶ HTTPS Bearer ─▶ backend /sync/push · /sync/pull
        backend (clerkMiddleware verifies the JWT)
          └─ Firebase Admin SDK ─▶ users/{clerkUserId}/workouts/{id}
                                   users/{clerkUserId}/nutritionEntries/{id}
```

The backend derives ownership from the **verified** Clerk user ID and ignores any client-supplied owner, so one account can never reach another's data. `firestore.rules` denies all direct client access; because the Admin SDK bypasses rules, this is a defence-in-depth guarantee that no leaked or reverse-engineered client could ever read the database.

Cloud sync is **optional and config-gated**, exactly like Clerk and Stripe. With no Firebase configuration the backend still serves billing and the app runs fully offline on Room; the `/sync/*` routes simply return HTTP 503.

## Exactly where each value goes

All Firebase configuration is **server-only** — nothing Firebase-related belongs in the APK or `secrets.properties`.

| Value | Where to get it | Put it in | Safe in Android APK? |
|---|---|---|---|
| `FIREBASE_SERVICE_ACCOUNT` | Firebase Console → Project settings → Service accounts → Generate new private key (paste the JSON as one line) | `backend/.env` only | No |
| `GOOGLE_APPLICATION_CREDENTIALS` | Absolute path to that key file (alternative to the JSON string) | `backend/.env` only | No |
| `FIREBASE_PROJECT_ID` | Firebase Console → Project settings (optional, for logging) | `backend/.env` only | N/A |
| `MEMBERSHIP_API_BASE_URL` | HTTPS URL where you deploy `backend/` | Root `secrets.properties` only | Yes |

The Android sync client reuses `MEMBERSHIP_API_BASE_URL` — sync and billing are the same server, so no new client value is required. See [CLERK_STRIPE_SETUP.md](CLERK_STRIPE_SETUP.md) for that file's layout.

## 1. Create the Firebase project and database

1. In the [Firebase Console](https://console.firebase.google.com/), create a project (or reuse an existing one).
2. Open **Build → Firestore Database** and click **Create database**.
3. Choose **production mode** (locked rules). MotionFuel's rules deny all client access; the backend reaches data through the Admin SDK, which is unaffected.
4. Pick a region close to your users.

## 2. Generate a service-account key

1. Open **Project settings → Service accounts**.
2. Click **Generate new private key** and confirm. A JSON file downloads once — treat it like a password.
3. Do **not** commit this file. It grants full server-side access to your project.

## 3. Configure the backend

Add the credentials to `backend/.env` (created from `backend/.env.example`). Use **one** of the two credential forms:

```dotenv
# Option A — paste the whole service-account JSON as a single line (preferred for most hosts):
FIREBASE_SERVICE_ACCOUNT={"type":"service_account","project_id":"...","private_key":"-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n","client_email":"...","...":"..."}

# Option B — reference a key file already present on the server instead:
# GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/service-account.json

# Optional, used only for logging:
FIREBASE_PROJECT_ID=your-project-id
```

Notes:
- When pasting `FIREBASE_SERVICE_ACCOUNT`, keep the escaped newlines (`\n`) inside `private_key` exactly as they appear in the JSON.
- Leave every Firebase value blank to run billing-only; `/sync/*` then returns HTTP 503 and the app stays offline-capable.
- The Admin SDK is initialised lazily, so a bad or missing key never prevents the billing routes from starting.

## 4. Deploy the Firestore rules

The repository root contains `firestore.rules`, which denies all direct client access. Deploy it with the Firebase CLI:

```bash
firebase deploy --only firestore:rules
```

(Run `firebase login` and `firebase use <project-id>` first if you have not linked the CLI to this project.) These rules never block the backend, which uses the Admin SDK.

## 5. Run and deploy the backend

From the `backend` directory:

```bash
npm install
npm run check
npm start
```

Deploy the service behind HTTPS and set `MEMBERSHIP_API_BASE_URL` in the root `secrets.properties` to that HTTPS origin. The Android `SyncWorker` sends the current Clerk JWT as `Authorization: Bearer <token>`; `clerkMiddleware` verifies it before any Firestore access. After changing `secrets.properties`, run **Sync Project with Gradle Files** and rebuild so the value is regenerated into `BuildConfig`.

## 6. How sync behaves on Android

- `AppConfig.isSyncConfigured` is true when Clerk is configured and `MEMBERSHIP_API_BASE_URL` is a real HTTPS URL. When false, `SyncWorker` and `SyncScheduler` no-op and the app is Room-only.
- On a completed workout or a logged food, the row is written to Room as `PENDING` and a network-constrained WorkManager job is enqueued.
- `SyncWorker` fetches a Clerk token, pulls remote records (stored locally as `SYNCED`), then pushes pending rows and marks them `SYNCED` once the backend confirms.
- The detailed route is uploaded only when the **route-backup** setting is on (off by default); otherwise an empty route is sent. Coordinates never leave the device unless the user opts in, and then only to their own private, client-denied scope.
- The UI always observes Room, never Firestore.

## 7. Verify a round-trip

1. With a service account configured, sign in on the app, record a workout, and log a food.
2. Confirm the documents appear under `users/{yourClerkUserId}/workouts` and `.../nutritionEntries` in the Firebase Console.
3. Reinstall or use a second device with the **same** account and confirm the records reappear after a sync (pull-on-sync).
4. Confirm a **different** account cannot see the first account's data — the backend scopes every query to the verified token's user ID.
5. With route backup off, confirm uploaded workout documents contain an empty `route`.

## Security checklist

- The service-account JSON and `backend/.env` are never committed (both are git-ignored).
- No Firebase key, `google-services.json`, or client SDK is present in the APK.
- All remote traffic is HTTPS; the sync client refuses any non-HTTPS base URL.
- `firestore.rules` denies all direct client reads and writes (NFR-10).
- Ownership is derived server-side from the verified Clerk user ID; client-supplied owner fields are ignored.
