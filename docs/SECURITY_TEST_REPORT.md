# MotionFuel Test and Security Assessment

**Assessment date:** 3 September 2026  
**Branch:** `codex/firebase-motionfuel-update`  
**Scope:** Android client, local persistence, Firebase Authentication/Firestore configuration, Google Maps configuration, activity sharing, build configuration, and direct dependencies.

## Executive summary

MotionFuel's original 10 JVM unit tests and debug assembly passed in GitHub Actions. The first expanded 45-test run compiled the production and test sources, then found one reproducible robustness defect: `GpsFilter` accepted a sample whose accuracy was `Float.NaN`. The production boundary has now been corrected and all three known-defect probes are active regression tests. The current source declares 55 Android/JVM tests plus six Firebase Emulator Security Rules tests.

The application's baseline Android controls are good: cleartext traffic and backups are disabled, the workout service and `FileProvider` are not exported, the share provider exposes only `cache/shared_activities/`, Firestore has a global default deny, and no live credentials were found in tracked source. No critical cross-user data-access flaw was identified through static analysis.

The recommended source/configuration remediations have been implemented: mandatory verified-email gating, build-variant App Check providers, Firestore schema rules and Emulator tests, corrected backup exclusions, non-finite input handling, private notifications, route-share confirmation and cache expiry. The current source declares 55 Android/JVM tests plus six Firebase Emulator Security Rules tests. Firebase Console enforcement, Maps key restrictions and signed physical-device testing remain deployment-owner actions.

## Remediation status

| Finding | Status |
|---|---|
| MF-01 GPS `NaN` accuracy | Fixed; active regression test |
| MF-02 non-finite sensor confidence | Fixed; active regression test |
| MF-03 negative-duration calories | Fixed; active regression test |
| MF-04 unverified-email access | Fixed in navigation and Firestore rules |
| MF-05 missing App Check | Providers implemented; Console registration/enforcement remains |
| MF-06 schema-free Firestore writes | Fixed for implemented profile/weight schemas; Emulator tests added |
| MF-07 local application-layer encryption | Residual risk accepted for MVP; sandbox, device encryption, backup denial and data deletion remain controls |
| MF-08 incorrect DataStore backup rule | Fixed for cloud backup and device transfer |
| MF-09 complete-route privacy | Mitigated with explicit confirmation; complete-route product requirement retained |
| MF-10 weather coordinate disclosure | Corrected assessment: current client sends a fixed Melbourne context, not the device's live coordinate |
| MF-11 lock-screen workout details | Fixed with private visibility and a generic public notification |
| MF-12 Maps-key restriction | Requires Google Cloud Console action and rotation |
| MF-13 retained share cache | Fixed with startup expiry after 24 hours |

## Test execution record

| Test activity | Result | Evidence / limitation |
|---|---:|---|
| Original JVM unit suite | 10 passed | GitHub Actions run on the same application source completed successfully. |
| Expanded JVM boundary/security suite | 44 passed, 1 failed | GitHub Actions run `33674275981`; failure is `GpsFilterBoundaryTest.invalidAccuracyValuesAreRejected`. |
| Current Android/JVM regression suite | 55 declared | All tests are active; final GitHub result is recorded after the remediation push. |
| Firestore Security Rules suite | 6 declared | Firebase Emulator tests cover unauthenticated, unverified, cross-user, valid-owner and invalid-schema/range access. |
| Production Kotlin compilation | Passed | Expanded run compiled debug production and test Kotlin before executing tests. |
| Debug APK assembly | Passed previously | Passed on the application source before the test-only changes. The expanded run stopped at the failing test before packaging. |
| Release assembly and Android lint | Configured, not executed | Added to the local workflow; not claimed as passed. |
| XML and JSON resources | Passed | Every Android XML resource parsed with a namespace-aware XML parser and every JSON resource parsed with `jq`; Android resource compilation also passed previously. |
| Direct dependency advisory lookup | No matches | Queried the GitHub Advisory Database for 13 pinned direct dependencies. This is not an exhaustive transitive/Firebase BOM scan. |
| Secret-pattern scan | No live secrets found | Current tracked tree contains no Google API key, Stripe key, or private-key literal. One history match was a three-character documentation placeholder, not key material. |
| Emulator/device/UI tests | Not executed | No Android SDK, emulator, or connected device is available in this workspace. |
| Firebase rules integration tests | Added to CI | Uses pinned Firebase CLI 15.28.2, JS SDK 12.18.0 and rules-unit-testing 5.0.2. |

## Coverage added

The expanded suite exercises:

- GPS coordinate and accuracy boundaries, impossible jumps, timestamp ordering, stationary drift, smoothing, and reset behavior.
- Haversine/route calculations and privacy-zone boundaries.
- Sensor classification, confidence bounds, stabilizer hysteresis, and adaptive insight rules.
- Maintenance-calorie and energy-estimation boundaries.
- Manifest, network-security, `FileProvider`, Firestore ownership/default-deny, and source-secret invariants.

There are no Compose UI tests or Android instrumentation tests in the project yet.

## Findings

### MF-01 — Non-finite GPS accuracy bypassed validation (Medium, fixed)

`GpsFilter.evaluate` rejects accuracy values with `accuracy <= 0` or `accuracy > maximum`, but both comparisons are false for `Float.NaN`. The sample is therefore accepted and classified with an invalid quality value. This was reproduced in GitHub Actions.

**Resolution:** latitude, longitude, altitude and accuracy must be finite before quality/range calculations. `nanAccuracyIsRejected` and a non-finite-altitude case are active regression tests.

### MF-02 — Non-finite sensor features could produce non-finite confidence (Low, fixed)

The sensor scoring helpers do not validate `NaN` or infinite inputs. A non-finite signal can propagate through score averaging and confidence calculation.

**Resolution:** optional and required classifier features are normalized to finite values at the boundary; the regression test covers `NaN` and both infinities.

### MF-03 — Negative duration produced negative calories (Low, fixed)

`EnergyEstimator.calories` accepts a negative duration. The normal tracking service clamps elapsed time, which limits reachability, but the domain API itself permits an invalid result.

**Resolution:** non-positive duration and non-positive/non-finite weight produce zero calories, with active boundary tests.

### MF-04 — Email verification was not enforced (Medium, fixed)

Registration sends a verification email but immediately puts the account into `SIGNED_IN`. Firestore rules check only the Firebase UID and do not require `request.auth.token.email_verified == true`. An unverified user can therefore use all owner-scoped features.

**Resolution:** unverified sessions route to a dedicated verification screen with refresh/resend/account-switch actions. Firestore private reads, updates, deletes and weight access require the verified token claim.

### MF-05 — Firebase App Check was absent (Medium, code fixed; deployment pending)

No App Check dependency, provider, or initialization was found. Firebase configuration shipped in an Android app is public by design; without App Check, scripted clients can use the project endpoints, create accounts, and consume quota even though the current ownership rules prevent cross-user access.

**Resolution:** debug and release source sets install Debug and Play Integrity providers respectively. The Firebase owner must register debug/release clients, monitor valid traffic and enable enforcement in Console.

### MF-06 — Firestore rules enforced ownership but not schema (Medium, fixed for implemented collections)

An authenticated user has unrestricted read/write access beneath their own `/users/{uid}` tree. The rules do not allowlist fields or validate types, sizes, ranges, timestamps, or immutable ownership fields. This protects users from each other but permits malformed or oversized owner data and weakens analytics/data integrity.

**Resolution:** profile and weight-entry rules validate field allowlists, types, ranges, token email and server timestamps. Undefined nested collections are denied, and six Emulator tests are part of CI.

### MF-07 — Sensitive local data is unencrypted at the application layer (Medium)

Room and Preferences DataStore contain precise routes, nutrition, weight, and preferences without application-layer encryption. Android's app sandbox, device encryption, and `allowBackup="false"` provide meaningful protection, but data remains exposed on a compromised/rooted or unlocked device.

**Recommendation:** document the threat model and retention policy. For a higher-risk deployment, encrypt especially sensitive fields with keys protected by Android Keystore and minimize route retention.

### MF-08 — DataStore backup exclusion targeted the wrong domain/path (Medium, fixed)

`data_extraction_rules.xml` excludes `motionfuel.preferences_pb` from the `sharedpref` domain. Preferences DataStore normally stores this file under the app files `datastore/` directory. Device-transfer rules also exclude the Room database but not DataStore. Although `allowBackup="false"` is set, the rule should be corrected for defense in depth and OEM/device-transfer behavior.

**Resolution:** cloud backup and device transfer now exclude the Room database/WAL/SHM, DataStore's file path and share cache.

### MF-09 — Full route sharing exposes start and finish locations (High privacy impact, accepted product behavior)

The current product requirement deliberately shares the complete route and does not trim endpoints. The action is user-initiated and uses Android's chooser with a read-only URI grant, but the resulting image can reveal a home, workplace, or routine.

**Resolution:** tapping the single share button now opens a confirmation that explicitly identifies start/finish disclosure. Cancel performs no share; the complete-route/no-trimming requirement remains.

### MF-10 — Weather coordinate disclosure review (Not currently applicable)

The current Android client calls Open-Meteo with a fixed Melbourne context rather than the device's live GPS coordinate. It therefore does not currently disclose a user's exact workout location to that service.

**Future requirement:** if device-derived weather is added, disclose the recipient/purpose and send a coarse location unless exact precision is genuinely necessary.

### MF-11 — Workout details could appear on the lock screen (Low privacy, fixed)

The foreground-service notification includes live workout text and does not explicitly set private visibility.

**Resolution:** the active notification is private and supplies a generic public version for secured lock screens.

### MF-12 — Maps key requires console-side restriction (Medium if unrestricted)

The Maps key is injected from ignored local configuration rather than tracked Kotlin/XML, which is correct. Any Maps key inside an APK can still be extracted.

**Recommendation:** restrict the key in Google Cloud to the Android package `com.ronitgandhi.motionfuel`, the production signing certificate fingerprints, and only the required Maps SDK APIs. Rotate any key that was shared outside its intended environment.

### MF-13 — Shared image remained in internal cache (Low privacy, fixed)

The app deletes previous shared PNGs before creating a new one, but the newest image remains until another share or cache cleanup. The `FileProvider` configuration and temporary read permission are otherwise narrow and appropriate.

**Resolution:** share files older than 24 hours are removed at application startup; creating another share also removes earlier PNG files.

## Positive controls verified

- `android:allowBackup="false"` and `android:usesCleartextTraffic="false"` are set.
- Network security config denies cleartext traffic.
- Main activity is the only intentionally exported component; service and share provider are private.
- `FileProvider` exposes only `cache/shared_activities/`, not root or external storage.
- Share intents grant read access only and use `content://` URIs.
- Firestore rules require authenticated UID ownership and include a global deny fallback.
- No WebView JavaScript bridge, permissive TLS trust manager, dynamic code loading, raw user-built SQL, or production logging calls were found.
- Secret-bearing files and signing keys are ignored; no live secret literal was found in tracked source.
- Release builds enable shrinking/obfuscation.

## Recommended remediation order

1. Register App Check debug/release clients, observe metrics and enable Firebase Console enforcement.
2. Rotate/restrict the Maps key and secure Play App Signing/CI release credentials.
3. Run lint, debug/release assembly, JVM tests and Firebase Emulator tests in GitHub Actions.
4. Add Compose UI/instrumentation tests and transitive SBOM/dependency scanning.
5. Test a signed release on physical devices across denied/approximate/background location, offline/reconnect, process death, rotation, dark mode and share targets.
6. Reassess application-layer encryption if MotionFuel moves beyond the university MVP or stores higher-risk health/location history.

## Release-testing limitations

This report is a source/configuration review plus JVM CI testing, not a penetration-test certification. Runtime checks still needed include Android permission-state transitions, exported-component invocation against a signed APK, network capture/certificate validation, Firebase App Check enforcement, Firestore rules fuzzing, deep-link/intent abuse, memory/storage inspection, accessibility/UI automation, and Play Integrity behavior.

## References

- [Firebase App Check with Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Firestore field restrictions and validation](https://firebase.google.com/docs/firestore/security/rules-fields)
- [Android backup security best practices](https://developer.android.com/privacy-and-security/risks/backup-best-practices)
- [Android application backup behavior](https://developer.android.com/guide/topics/manifest/application-element)
- [Google Maps API key security](https://developers.google.com/maps/api-security-best-practices)
- [Android FileProvider security guidance](https://developer.android.com/privacy-and-security/risks/file-providers)
- [Android security best practices](https://developer.android.com/privacy-and-security/security-best-practices)
