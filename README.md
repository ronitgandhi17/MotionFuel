# MotionFuel

MotionFuel is a Kotlin/Jetpack Compose fitness and nutrition app that combines Firebase accounts, maintenance-calorie estimates, an offline nutrition diary, walk/run tracking and explainable context insights.

## Implemented in this revision

- Firebase Authentication email/password login, registration, password reset, verification email and persisted sessions.
- Multi-step signup for name, age, sex, height, weight and activity level.
- Pure Kotlin Mifflin–St Jeor BMR and activity-factor TDEE calculation with unit tests.
- Firestore user profiles stored at `users/{uid}` and protected by UID-based security rules.
- MyFitnessPal-inspired light-first Today, Diary and Progress information hierarchy with original MotionFuel branding.
- `Goal − Food + Exercise = Remaining` calorie summary and separate maintenance-calorie value.
- Breakfast, Lunch, Dinner and Snacks diary sections with meal-specific food logging.
- Separate 7-day/30-day calorie and weight bar graphs drawn with Compose Canvas.
- Room migration and offline weight-history persistence.
- User-facing GPS quality indicator removed while internal point validation and drift rejection remain active.
- Existing foreground workout service, sensor fusion, weather, food search, privacy masking and AFEE insights retained.

## Firebase setup

Follow [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md). The app intentionally shows a Firebase setup screen until a valid `app/google-services.json` is present.

## Open and run

1. Open this folder—the one containing `settings.gradle.kts`—in Android Studio.
2. Use Android Studio's Embedded JDK 17 or 21 and install Android SDK 36 if prompted.
3. Add Firebase configuration by following the setup guide.
4. Select the `debug` build variant and sync Gradle.
5. Run on an Android 10+ emulator or physical device.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

The project uses AGP 9.0.1, Gradle 9.1.0, KSP 2.3.6, Google services plugin 4.5.0 and Firebase BoM 34.18.0.

## Assessment demo

1. Create a Firebase account and show the TDEE preview during signup.
2. Open Today and explain maintenance calories versus the editable daily goal.
3. Add foods to different Diary meals and show immediate calorie/macronutrient changes.
4. Run the debug-only deterministic workout trace and show the filtered route and sensor-derived metrics.
5. Add a weight in Progress and switch between 7-day and 30-day calorie/weight bar graphs.
6. Reopen the app offline to demonstrate Room-backed history.

## Privacy

- Passwords and sessions are managed by Firebase Authentication.
- Firestore rules restrict private data to the authenticated UID.
- Detailed route backup defaults to off.
- High-frequency inertial samples remain on device.
- `app/google-services.json`, `local.properties` and signing files are ignored by Git.
- Calorie, burn and TDEE values are transparent wellness estimates rather than medical advice.
