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

MotionFuel sends a verification email after signup. Verification is mandatory: the app remains on **Verify Email**, and private Firestore reads remain blocked, until the user opens the email link and taps **I've verified my email**.

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

## 4. Configure Firebase App Check

1. Open **Build → App Check** in Firebase Console and select the Android app.
2. Register **Play Integrity** for the release app and confirm the production signing certificate is registered in Project Settings.
3. Run the debug build and find the App Check debug token in Logcat.
4. In App Check, add that token under **Manage debug tokens**; never commit or share it.
5. Confirm valid debug/internal/release requests appear in App Check metrics.
6. Enable enforcement for Firestore, then for other supported Firebase products only after valid traffic is confirmed.

The debug provider exists only in the debug dependency/source set. Release builds compile against the Play Integrity provider.

## 5. Sync and test

1. In Android Studio select **File → Sync Project with Gradle Files**.
2. Run the app and create a test account.
3. Confirm Authentication contains the new user.
4. Confirm the app stays on Verify Email before verification and cannot read the private profile.
5. Open the verification link, return to the app and tap **I've verified my email**.
6. Confirm Firestore contains `users/{uid}` with the completed profile.
7. Sign out, sign in again and verify that the session/profile loads.
8. Test **Forgot password** with the same email.
9. Run `cd firebase-tests && npm ci --ignore-scripts && npm test` to exercise the deployed rule model locally.

## Configuration rules

- No Clerk or Stripe keys are used by this version.
- Do not create a Kotlin class containing Firebase service-account credentials.
- Never place a service-account JSON file, private key, password or signing key in the Android project.
- Restrict the Google Maps API key to Android apps, package `com.ronitgandhi.motionfuel`, the debug/release signing certificate fingerprints and only Maps SDK for Android.
- Rotate any Maps key that has previously been pasted into chat, logs, screenshots or another unintended location.

## Optional Google Maps key

Add the Android-restricted Maps SDK key to the root `local.properties` file:

```properties
MAPS_API_KEY=your_restricted_android_maps_key
```

`local.properties` is ignored by Git. Never place an unrestricted server key in the manifest or Kotlin source.
