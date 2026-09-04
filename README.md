# MotionFuel

MotionFuel is a Kotlin/Jetpack Compose fitness and nutrition app that combines Firebase accounts, maintenance-calorie estimates, an offline nutrition diary, walk/run tracking and explainable context insights.

## Implemented in this revision

- Firebase Authentication email/password login, registration, password reset, mandatory verification gate and persisted sessions.
- Firebase App Check with debug attestation in debug builds and Play Integrity in release builds.
- Multi-step signup for name, age, sex, height, weight and activity level.
- Pure Kotlin Mifflin–St Jeor BMR and activity-factor TDEE calculation with unit tests.
- Firestore user profiles stored at `users/{uid}` and protected by verified-owner, schema/range and server-timestamp rules.
- MyFitnessPal-inspired light-first Today, Diary and Progress information hierarchy with original MotionFuel branding.
- `Goal − Food + Exercise = Remaining` calorie summary and separate maintenance-calorie value.
- Breakfast, Lunch, Dinner and Snacks diary sections with meal-specific food logging.
- Separate calorie and weight bar graphs drawn with Compose Canvas, each with its own Day/Week/Month selector.
- Swipe navigation across Today, Activity, Food, Progress and Profile, synchronised with the bottom bar.
- Root-only swipe navigation: saved-food details, activity summaries, workouts and profile editing do not change tabs when swiped.
- Manual foods with a Camera/Gallery choice, private FileProvider camera storage, a persistent My saved foods list and one-tap diary reuse.
- Tappable saved-food details with an Add to meal Breakfast/Lunch/Dinner selector and a branded 1080 × 1350 social card containing the photo, name, calories, carbohydrates, protein and fat.
- Saved-food swipe actions: right to choose a meal and add it to today, left to confirm deletion.
- Diary editing: swipe a logged food left under Breakfast, Lunch, Dinner or Snack, then confirm to remove only that day's entry.
- Diary food rows inherit their meal card colour in light and dark themes, with the delete colour shown only during a left swipe.
- Firebase-backed profile editing for name, age, sex, height, weight, activity level and calorie goal, with email kept read-only.
- Live weather temperature, humidity, wind and rain context on the Track Activity screen.
- Room migration and offline weight-history persistence.
- User-facing GPS quality indicator removed while internal point validation and drift rejection remain active.
- Tappable saved activities with a detailed Google Maps route and workout-statistics summary.
- One-button 1080 × 1350 social image sharing using the complete route and attributed Google basemap, with a full-route privacy confirmation.
- GPS rejection counts, noise-removal messages and endpoint-trimming controls removed from the UI.
- Existing foreground workout service, sensor fusion, weather, food search, cloud-route privacy masking and AFEE insights retained.

## Product requirements

The implementation-aligned specification is maintained in [docs/MotionFuel_PRD_Updated_Firebase.md](docs/MotionFuel_PRD_Updated_Firebase.md).

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
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Firestore rules tests run with the Emulator Suite:

```bash
cd firebase-tests
npm ci --ignore-scripts
npm test
```

The project uses AGP 9.0.1, Gradle 9.1.0, KSP 2.3.6, Google services plugin 4.5.0 and Firebase BoM 34.18.0.

## Testing and security

See [docs/SECURITY_TEST_REPORT.md](docs/SECURITY_TEST_REPORT.md) for the executed test matrix, confirmed defects, static security findings, positive controls and remaining device-test scope.

## Assessment demo

1. Create a Firebase account, show the TDEE preview, verify the email and refresh the verification screen.
2. Open Today and explain maintenance calories versus the editable daily goal.
3. Add foods to different Diary meals and show immediate calorie/macronutrient changes.
4. Run the debug-only deterministic workout trace and show the filtered route and sensor-derived metrics.
5. Tap a saved Activity to open its full summary, then use **Share activity image** to open Android's share sheet.
6. Add a weight in Progress and switch between Day, Week and Month calorie/weight bar graphs.
7. Reopen the app offline to demonstrate Room-backed history.

## Privacy

- Passwords and sessions are managed by Firebase Authentication.
- Firestore rules restrict private data to the verified authenticated UID and validate supported document schemas.
- App Check attests debug/release clients but does not replace Authentication or Firestore rules.
- Detailed route backup defaults to off.
- Shared activity images are created only after a full-route warning is confirmed, include the complete recorded route and expire from cache.
- High-frequency inertial samples remain on device.
- `app/google-services.json`, `local.properties` and signing files are ignored by Git.
- Calorie, burn and TDEE values are transparent wellness estimates rather than medical advice.
