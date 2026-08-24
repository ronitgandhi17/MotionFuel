# Clerk authentication and Stripe memberships

MotionFuel now requires a Clerk session before the dashboard is rendered. Stripe PaymentSheet is used for the first subscription payment, while subscription creation, customer lookup, portal sessions and webhook verification remain on the included server so secret keys never enter the APK.

## Exactly where each key goes

Create these two local files from the included examples; both real files are already excluded by `.gitignore`.

| Value | Where to copy it from | Put it in | Safe in Android APK? |
|---|---|---|---|
| `CLERK_PUBLISHABLE_KEY` | Clerk Dashboard → API keys | Root `secrets.properties` and `backend/.env` | Yes |
| `STRIPE_PUBLISHABLE_KEY` | Stripe Dashboard → Developers → API keys | Root `secrets.properties` only | Yes |
| `MEMBERSHIP_API_BASE_URL` | HTTPS URL where you deploy `backend/` | Root `secrets.properties` only | Yes |
| `CLERK_SECRET_KEY` | Clerk Dashboard → API keys | `backend/.env` only | No |
| `STRIPE_SECRET_KEY` | Stripe Dashboard → Developers → API keys | `backend/.env` only | No |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook endpoint signing secret | `backend/.env` only | No |
| `STRIPE_PRO_MONTHLY_PRICE_ID` | Stripe recurring Price, starting with `price_` | `backend/.env` only | No |
| `MEMBERSHIP_RETURN_URL` | Your public HTTPS account page | `backend/.env` only | N/A |

The file layout must look like this:

```text
MotionFuel/
├── secrets.properties        # Client-safe publishable values; do not commit.
├── settings.gradle.kts
├── app/
│   └── src/main/java/com/ronitgandhi/motionfuel/config/AppConfig.kt
└── backend/
    └── .env                  # Server-only secret values; do not commit.
```

Use these client values in `MotionFuel/secrets.properties`:

```properties
CLERK_PUBLISHABLE_KEY=pk_test_replace_me
STRIPE_PUBLISHABLE_KEY=pk_test_replace_me
MEMBERSHIP_API_BASE_URL=https://api.your-domain.com
```

Use these server values in `MotionFuel/backend/.env`:

```dotenv
CLERK_PUBLISHABLE_KEY=pk_test_replace_me
CLERK_SECRET_KEY=sk_test_replace_me
STRIPE_SECRET_KEY=sk_test_replace_me
STRIPE_WEBHOOK_SECRET=whsec_replace_me
STRIPE_PRO_MONTHLY_PRICE_ID=price_replace_me
MEMBERSHIP_RETURN_URL=https://your-domain.com/account
PORT=4242
```

`AppConfig.kt` is the single Kotlin access point for client-safe configuration, but it deliberately reads generated `BuildConfig` values instead of hardcoding any key. Never add server secrets to `AppConfig.kt`, `build.gradle.kts`, the manifest, a resource XML file or Kotlin source.

## 1. Configure Clerk

1. Create a Clerk application and enable email/password authentication.
2. Enable the Native API in Clerk's **Native applications** settings.
3. Add both application IDs to Clerk while testing: `com.ronitgandhi.motionfuel` and `com.ronitgandhi.motionfuel.debug`.
4. Copy root `secrets.properties.example` to root `secrets.properties` and set `CLERK_PUBLISHABLE_KEY`.

The app supports email/password sign-in, sign-up and email-code verification. Keep multifactor authentication disabled for this MVP's password screen, or extend `ClerkAuthViewModel` with the required second-factor flow before enabling MFA.

## 2. Configure Stripe

1. In Stripe test mode, create a **MotionFuel Pro** product and an AUD recurring monthly Price.
2. Enable and configure Stripe Customer Portal.
3. Copy `backend/.env.example` to `backend/.env`.
4. Add your Clerk keys, Stripe secret key, recurring Price ID, webhook secret and an HTTPS portal return URL.
5. In `secrets.properties`, set `STRIPE_PUBLISHABLE_KEY` and the deployed HTTPS `MEMBERSHIP_API_BASE_URL`.

Do not put `CLERK_SECRET_KEY`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, or a raw Stripe Price selected by the client in the Android configuration.

## 3. Run the membership API

From the `backend` directory:

```bash
npm install
npm run check
npm start
```

Deploy this service behind HTTPS. Configure Stripe to deliver events to `https://YOUR_HOST/stripe/webhook`. The Android app sends its current Clerk JWT in the Authorization header; Clerk middleware verifies it before any billing operation.

No Clerk callback activity or Stripe activity needs to be added to the manifest for this implementation: Clerk uses its native email/password SDK, Stripe PaymentSheet registers through AndroidX Activity Result APIs, and Customer Portal opens in an Android Custom Tab. The manifest already contains `INTERNET` plus the fitness permissions and application/service declarations the app needs.

## 4. Test

Use Clerk and Stripe test-mode keys together. Sign up in the app, verify the email, open **Profile**, and tap **Join MotionFuel Pro**. Stripe's test card `4242 4242 4242 4242` can be used with a future expiry date and any CVC/postcode. After PaymentSheet completes, tap **Refresh membership status** if the verified Stripe event is still processing.

The server maps the fixed `pro_monthly` plan to `STRIPE_PRO_MONTHLY_PRICE_ID`, looks up the Stripe Customer using Clerk's user ID, and returns only short-lived PaymentSheet secrets. Membership access comes from Stripe subscription state rather than from the PaymentSheet completion callback.

After changing `secrets.properties`, run **Sync Project with Gradle Files**, then rebuild the app because these values are generated into `BuildConfig` at build time.
