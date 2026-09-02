# Firebase setup

## 1. Create and register the Android app

1. Open the Firebase console and create or select the `MotionFuel` project.
2. Add an Android app with package name `com.ronitgandhi.motionfuel`.
3. Download `google-services.json`.
4. Place it at `MotionFuel/app/google-services.json`.

The Firebase configuration file contains project identifiers used by the client; access is enforced by Firebase Authentication and Firestore Security Rules rather than by hiding this file.

## 2. Enable authentication

1. Open **Build → Authentication → Sign-in method**.
2. Enable **Email/Password**.
3. Keep Email link/passwordless disabled unless the Android flow is extended for it.

MotionFuel sends a verification email after signup, but verification is non-blocking for the assessed MVP.

## 3. Create Firestore

1. Open **Build → Firestore Database**.
2. Create the database in a region appropriate for the project.
3. Deploy the repository's `firestore.rules` file with the Firebase CLI:

```bash
firebase login
firebase use YOUR_PROJECT_ID
firebase deploy --only firestore:rules
```

Do not leave Firestore in unrestricted test mode.

## 4. Sync and test

1. In Android Studio select **File → Sync Project with Gradle Files**.
2. Run the app and create a test account.
3. Confirm Authentication contains the new user.
4. Confirm Firestore contains `users/{uid}` with the completed profile.
5. Sign out, sign in again and verify that the session/profile loads.
6. Test **Forgot password** with the same email.

## Configuration rules

- No Clerk or Stripe keys are used by this version.
- Do not create a Kotlin class containing Firebase service-account credentials.
- Never place a service-account JSON file, private key, password or signing key in the Android project.
- Restrict any separate Google Maps API key by Android package name and signing certificate before release.

## Optional Google Maps key

Add the Android-restricted Maps SDK key to the root `local.properties` file:

```properties
MAPS_API_KEY=your_restricted_android_maps_key
```

`local.properties` is ignored by Git. Never place an unrestricted server key in the manifest or Kotlin source.
