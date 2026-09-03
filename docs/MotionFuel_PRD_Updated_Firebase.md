# MotionFuel — Product Requirements Document (PRD)

**Project type:** Advanced Android mobile systems university project  
**Primary stack:** Kotlin, Android Studio, Jetpack Compose, Material Design 3, Firebase Authentication, Cloud Firestore, Firebase Storage, Room, DataStore, Retrofit/OkHttp, Google Maps SDK for Android  
**Target platform:** Android 10+ (API 29+) for the university build, with graceful feature degradation when optional sensors are unavailable  
**Architecture:** Feature-oriented Clean Architecture + MVVM  
**Document status:** Version 1.2 — implementation-aligned Firebase/App Check security revision, September 2026  

---

## 0. Revision Summary — September 2026

This revision keeps MotionFuel realistic for a one-student Android build and replaces the previous third-party identity design completely with **Firebase Authentication**.

The most important product and architecture changes are:

- MotionFuel now has its own **Jetpack Compose Login and Sign Up screens** backed by Firebase Authentication email/password accounts.
- The Sign Up flow collects **name, email, password, age, sex, height, weight and activity level**. Sex and activity level are required because the requested maintenance-calorie calculation cannot be performed correctly from age, height and weight alone.
- The app calculates **Basal Metabolic Rate (BMR)** using the Mifflin–St Jeor equation and then calculates **Total Daily Energy Expenditure (TDEE)** using the selected activity factor.
- The calculated TDEE is stored as the user's **estimated maintenance calories** and is displayed prominently on the Today/Home screen.
- The user's daily calorie goal initially defaults to maintenance calories. If the user later selects a weight-loss or weight-gain goal, MotionFuel may suggest a modest editable adjustment, but it never presents the value as medical advice.
- Maintenance calories are recalculated whenever the user updates weight, height, age/sex profile data or activity level. The MVP does **not** attempt to infer maintenance from weeks of logged calories and weight change.
- Firebase Authentication provides sign-up, sign-in, password reset, mandatory email verification, session persistence and sign-out.
- The dashboard remains inaccessible until Firebase confirms `isEmailVerified`; the verification screen provides refresh, resend and use-another-account actions.
- Cloud Firestore stores user profile, calorie target, nutrition logs, workouts, progress records and settings using the Firebase Authentication UID as the owner key.
- Firestore Security Rules enforce UID ownership, verified-email access, document field allowlists, types, ranges and trusted server timestamps.
- Firebase App Check uses the debug provider only in debug builds and Play Integrity in release builds; production enforcement is enabled in Firebase Console after release registration.
- **Google Maps SDK for Android + Maps Compose** remains the required map implementation for live and saved run routes.
- The full application UI follows a **MyFitnessPal-inspired visual hierarchy and interaction model**: a card-based Today screen, diary-style meal sections, prominent calorie remaining summary, quick-add actions, bottom navigation and compact Progress cards. MotionFuel retains its own branding, colours, icons, copy and original implementation.
- The user-facing GPS quality indicator is removed. Location validation and drift rejection continue internally, and the UI only shows a general location-unavailable message when tracking cannot safely continue.
- GPS filtering remains an internal implementation detail: rejected/noisy sample counts and “GPS noise removed” messages are not shown in the live workout, activity history or activity-detail UI.
- Tapping a saved activity opens a Strava-inspired MotionFuel summary with the Google Maps route, distance, moving time, average pace, energy, steps, elevation and dominant movement.
- The activity summary provides one **Share activity image** button. Before export, a confirmation warns that the complete start and finish locations will be included. The 1080 × 1350 image contains the complete route on the loaded Google basemap and key statistics; there is no endpoint-trimming control.
- GPS, sensor and calorie-estimation domain boundaries reject or sanitize non-finite values so `NaN`/infinity cannot corrupt saved metrics.
- Room, DataStore and generated share images are excluded from cloud backup and device transfer; share images expire from the internal cache after 24 hours.
- **USDA FoodData Central** remains the default food database. A small Cloud Function or other trusted backend may proxy requests if an API credential must be kept out of the APK.
- Breakfast, Lunch and Dinner remain first-class calorie sections, with Snacks as a compact optional section.
- Custom Meals, MyFitnessPal-inspired 7-day/30-day calorie and weight trend bar graphs, weather, step counting, background workout tracking and Social Recipes remain in scope as described below.

The result is a simpler architecture than the previous third-party identity version because authentication and Firestore ownership now use the same Firebase identity.

---

## 1. Product Name

### Candidate names

1. **MotionFuel** — communicates the central idea: physical movement and nutrition are interpreted together rather than tracked as separate silos.
2. **KineticBalance** — emphasises balancing activity, recovery, environment, and intake.
3. **ContextFit** — highlights context-aware fitness recommendations driven by sensors and external data.

### Selected name: **MotionFuel**

**Tagline:** *Move smarter. Fuel with context.*

MotionFuel is selected because it is easy to remember, immediately communicates the relationship between exercise and nutrition, and is broad enough to support walking, running, cycling, and future activities without sounding like a clone of an existing fitness tracker.

---

## 2. Executive Summary

MotionFuel is an Android fitness and nutrition application that combines real-time activity sensing, localisation, environmental context, nutrition tracking, and personalised goals to generate context-aware fitness guidance.

Unlike conventional fitness applications that separately display route statistics or calorie intake, MotionFuel maintains a continuously updated **Daily Context Model**. This model combines data from phone sensors, GPS, step counts, barometric elevation, food logs, weather, user goals, historical behaviour, and optional wearable data. The model feeds a flagship feature called the **Adaptive Fuel & Effort Engine (AFEE)**.

AFEE does not diagnose health conditions and does not provide medical advice. It produces transparent wellness-oriented estimates and recommendations such as:

- detecting that a user's run is more strenuous than usual because pace is declining while elevation gain and temperature are high;
- identifying that a highly active day is not matched by the user's logged energy or protein intake;
- distinguishing genuine movement from GPS drift by combining inertial sensors, steps, and localisation;
- adapting workout feedback in real time when environmental or movement context changes.

The project is deliberately scoped so that one student can implement a technically impressive MVP while still demonstrating postgraduate-level knowledge in Android development, sensing, localisation, data fusion, real-time computing, cloud systems, privacy, HCI, data analytics, and software architecture.

---

## 3. Product Vision

> **MotionFuel should help users understand not only what they did, but what their activity means in the context of their nutrition, environment, goals, and recent behaviour.**

The product vision is to move beyond isolated counters such as steps, kilometres, calories, and macros. MotionFuel instead creates a coherent picture of the user's day and updates that picture as new sensor readings, food logs, weather conditions, and workout events arrive.

The application should feel useful during three moments:

1. **Before activity** — helping the user understand current conditions and readiness.
2. **During activity** — providing low-latency workout metrics and context-aware feedback.
3. **After activity** — connecting workout strain, route, environment, and nutrition into a meaningful summary.

---

## 4. Problem Statement

Current consumer fitness ecosystems often fragment important information across multiple applications:

- route and pace data in activity trackers;
- calorie and macro data in nutrition trackers;
- weather in a separate weather app;
- heart rate or steps in wearable platforms;
- recovery interpretation in premium services.

This fragmentation makes users perform the reasoning themselves. A user may know that they ran 6 km, ate 1,800 kcal, climbed 130 m, and exercised in 30°C weather, but they may not understand how these facts relate.

MotionFuel addresses this gap by combining real-time sensor data and logged information into a transparent, context-aware interpretation layer.

### Core problem

**Users receive many measurements but little integrated explanation.**

### Technical problem

Mobile sensor data is noisy, heterogeneous, battery-sensitive, asynchronous, and privacy-sensitive. A robust mobile application must reconcile:

- different sensor sampling rates;
- inaccurate GPS fixes;
- missing sensors;
- changing network conditions;
- background execution constraints;
- local/cloud consistency;
- sensitive location and health-related information.

The project therefore provides strong technical depth rather than being only a CRUD fitness application.

---

## 5. Proposed Solution

MotionFuel will provide:

- account creation and secure authentication;
- user profile and fitness goals;
- walking/running workout tracking;
- live GPS route, distance, speed, pace, steps, cadence estimate, elevation, and energy expenditure;
- inertial-sensor-based movement analysis;
- sensor fusion for activity state and confidence;
- nutrition search and food logging via an external API;
- weather context via an external weather API;
- offline-first local persistence using Room;
- settings and privacy preferences using DataStore;
- Firebase cloud synchronisation;
- an adaptive recommendation engine that combines activity, nutrition, environment, history, and user goals;
- privacy-aware route handling and optional route masking;
- responsive Material 3 UI built entirely in Jetpack Compose.

The MVP intentionally prioritises **walking and running**. Cycling, advanced social functionality, and continuous wearable heart-rate streaming should be Phase 2 or stretch features.

---

## 6. What Makes the App Novel

MotionFuel's novelty is not that it contains both workout tracking and food logging. Its novelty is the **live relationship between the two domains**.

### Novel capability

The application maintains a continuously updated Daily Context Model containing:

- energy consumed;
- protein/carbohydrate/fat intake;
- hydration logs;
- workout duration and intensity;
- movement classification;
- pace trend;
- elevation effort;
- distance;
- step/cadence data;
- weather stress indicators;
- recent activity baseline;
- user goal;
- optional heart-rate context.

AFEE evaluates this model whenever important events occur. It then produces explainable recommendations containing:

- recommendation type;
- confidence;
- severity/priority;
- evidence used;
- human-readable explanation;
- suggested action;
- expiry condition.

### Example

Instead of:

> 6.1 km — 37:40 — 430 kcal

MotionFuel can show:

> **Higher effort than usual**  
> Your pace fell 11% over the final 15 minutes while elevation gain and temperature were above your recent baseline. Estimated effort was higher than distance alone suggests. Consider an easier cooldown and normal post-exercise hydration.

This is an unexpected extension of Strava-like route tracking because it integrates nutrition and environmental context into the interpretation of exercise.

---

## 7. Target Users

### Primary users

- university students and young professionals who exercise recreationally;
- people who already track runs/walks but want clearer context;
- users who track calories or macros but struggle to relate food intake to activity;
- users who value privacy and transparent explanations over opaque AI scores.

### Secondary users

- beginners building a consistent exercise routine;
- casual runners trying to understand pace and exertion patterns;
- users who exercise in changing weather conditions;
- users who want a single daily activity-and-nutrition overview.

### Explicitly out of scope

MotionFuel is not intended to:

- diagnose disease;
- replace a medical professional;
- provide medical treatment recommendations;
- determine clinical hydration requirements;
- claim laboratory-grade calorie accuracy;
- replace certified sports-performance devices.

---

## 8. User Personas

### Persona A — Student runner

**Name:** Maya  
**Age:** 23  
**Goal:** Maintain fitness while balancing study and irregular meal timing.  
**Pain point:** Uses a run tracker and food app separately and rarely connects the information.  
**Needs:** Fast workout start, low cognitive load, understandable summaries, offline support.

### Persona B — Beginner building consistency

**Name:** Daniel  
**Age:** 28  
**Goal:** Increase weekly walking/running activity without overcomplicating fitness tracking.  
**Pain point:** Fitness dashboards contain too many unexplained numbers.  
**Needs:** Simple labels, meaningful insights, weekly trends, conservative guidance.

### Persona C — Privacy-conscious user

**Name:** Priya  
**Age:** 25  
**Goal:** Track outdoor workouts while keeping home location private.  
**Pain point:** Does not want raw route data shared unnecessarily.  
**Needs:** local-first processing, privacy zones, route masking, clear data controls.

---

## 9. Core User Journey

1. User installs MotionFuel.
2. User sees a short onboarding explanation covering nutrition estimates, location/sensor use, privacy and background workout tracking.
3. User opens **Sign Up** and enters name, email, password and password confirmation.
4. The same multi-step Sign Up flow collects **age, sex, height, weight and activity level**. A fitness goal may also be selected at this stage or later from Profile.
5. On final submission, MotionFuel creates the Firebase Authentication account with email/password.
6. MotionFuel calculates BMR and TDEE locally using a pure Kotlin `CalculateMaintenanceCaloriesUseCase`.
7. The app creates `users/{uid}` in Cloud Firestore with the profile and calculated maintenance calories. A local Room/DataStore copy is also retained for fast startup/offline display.
8. MotionFuel sends a Firebase verification email and blocks access at **Verify Email** until the refreshed Firebase user reports `isEmailVerified == true`.
9. After verification, Today opens with **estimated maintenance calories**, daily calorie goal, calories consumed, calories remaining, Breakfast/Lunch/Dinner totals, steps, current weather, latest workout and one current insight.
10. User can log food by searching the food database or by creating a Custom Meal with optional photo and macros.
11. User taps **Start Run** or **Start Walk**.
12. MotionFuel requests location/activity permissions in context, checks location quality and starts a foreground workout service.
13. The Live Workout screen opens with **Google Maps**, route polyline, current position, timer, distance, pace, steps and estimated calories burned.
14. If the user locks the phone or opens another application, the foreground service continues the active workout and keeps a persistent notification visible.
15. Weather is captured at workout start and may refresh during longer sessions.
16. The user pauses/resumes or ends the workout.
17. MotionFuel saves the workout to Room first, then queues Firebase synchronisation.
18. The Activity history shows the newly saved workout without displaying GPS-rejection diagnostics.
19. The user taps the activity to open its summary with the Google Maps route, time, distance, pace, steps, elevation, estimated burn and dominant movement.
20. The user can tap **Share activity image**, confirm the full-route privacy warning, and open Android's share sheet with a 1080 × 1350 image containing the complete mapped route and activity statistics.
21. The user opens **Progress** to log a new weight and view separate calorie and weight trend bar graphs for 7 or 30 days.
22. When a new weight or activity level becomes current, MotionFuel recalculates estimated maintenance calories and shows the updated value on Today. It does not silently overwrite a custom calorie goal.
23. If the user forgets the password, the Login screen provides Firebase password reset by email.
24. Social Recipes can be enabled in Phase 2 without changing the core nutrition/workout architecture.

---

## 10. Key Features

### MVP features — assessed build

#### Authentication & onboarding

- Custom Jetpack Compose **Login** screen.
- Custom Jetpack Compose **Sign Up** screen.
- Firebase Authentication email/password account creation and sign-in.
- Password confirmation and client-side validation.
- Firebase password reset email.
- Mandatory Firebase email verification before private profile reads or dashboard access.
- Verification refresh, resend and account-switch actions.
- Firebase App Check with debug and Play Integrity providers separated by build variant.
- Session persistence and sign-out.
- Sign Up profile fields: name, age, sex, height, weight, activity level and optional fitness goal.
- Immediate BMR/TDEE calculation after signup.
- Estimated maintenance calories stored in the profile and shown on the Today screen.
- User-editable daily calorie goal, initially set from maintenance calories.

#### Calorie & meal tracking

- USDA FoodData Central-backed food search.
- Add serving size/quantity to Breakfast, Lunch, Dinner or Snack.
- Breakfast/Lunch/Dinner cards showing meal calories and percentage of daily calorie goal.
- Daily calorie goal, calories consumed and calories remaining.
- A separate **Maintenance Calories** value so users can distinguish estimated energy needs from the goal they are currently following.
- Daily protein, carbohydrate and fat totals.
- Custom Meal creation with name, serving, protein, carbohydrates, fat, calories and optional photo.
- Manual/offline food entry when the API is unavailable.
- Edit/delete nutrition entries.
- MyFitnessPal-inspired 7-day and 30-day calorie trend bar graph with a daily target reference.

#### Fitness tracking

- Walk/run only for MVP.
- Start, pause, resume and finish workout.
- Google Maps live run map.
- Filtered route polyline and current location marker.
- Timer and moving time.
- Optional target distance (for example 3 km, 5 km or custom).
- Estimated time-to-finish when a target distance is selected and enough pace data exists.
- Distance, current pace and average pace.
- Step counter and cadence estimate where supported.
- Estimated calories burned.
- Weather snapshot and low-frequency refresh.
- Foreground-service background workout tracking.
- Accelerometer/gyroscope-based movement confidence and GPS drift rejection.
- Tappable workout-history cards that open a detailed activity summary.
- Social-media-ready activity image sharing with the complete Google Maps route and key statistics.

#### Progress & insight

- Weight entry history.
- MyFitnessPal-inspired 7-day and 30-day weight trend bar graph.
- Recalculate maintenance calories when current profile weight or activity level changes.
- Store maintenance-calorie snapshots so changes can be explained historically.
- AFEE context cards with evidence.
- Workout history/detail.
- Daily/weekly activity summaries.

#### Data, privacy & quality

- Room as local source of truth for workout/nutrition data.
- DataStore for lightweight settings and cached profile preferences.
- WorkManager retry queue for cloud sync.
- Firebase Authentication + Firestore for account-backed cloud data.
- Firestore Security Rules scoped by Firebase UID with verified-email and schema validation.
- Automated Firebase Emulator rules tests for unauthenticated, unverified, cross-user, malformed and valid-owner access.
- Route backup preference and privacy-zone route masking.
- Dark mode and accessibility support.

### Phase 2 features — explicitly planned

- **Social Recipes:** publish a recipe with ingredients/macros/photo, browse recent recipes, save a recipe and import it into a meal.
- Health Connect import for historical steps/heart-rate summaries.
- Cycling tracking profile.
- Push reminders.
- Barcode food lookup if a suitable data provider is added.
- Cloud backup for custom-meal/recipe photos.
- CSV/JSON export.

### Stretch features

- Offline map tiles where licensing permits.
- Wear OS companion.
- On-device ML activity classifier.
- Personalised route recommendation.

---

## 11. Flagship Innovative Feature — Adaptive Fuel & Effort Engine (AFEE)

### 11.1 Purpose

AFEE transforms raw measurements into contextual, explainable wellness insights. It is the feature that differentiates MotionFuel from a conventional activity tracker plus nutrition diary.

### 11.2 Inputs

AFEE consumes a `DailyContext` assembled from repositories:

- current workout state;
- rolling pace trend;
- distance and duration;
- activity classification and confidence;
- steps/cadence;
- elevation gain/loss;
- estimated energy expenditure;
- current temperature, humidity, precipitation and wind;
- energy/macronutrients logged today;
- hydration logs where used;
- user weight and goals;
- recent 7–28 day activity baseline;
- optional heart-rate summaries;
- time of day;
- data-quality flags.

### 11.3 Outputs

Each insight is represented as:

```text
Insight
- id
- category
- title
- message
- priority
- confidence
- evidence[]
- createdAt
- expiresAt
- actionLabel?
- actionDestination?
- disclaimerType
```

Example categories:

- `EFFORT_HIGHER_THAN_USUAL`
- `PACE_DECLINE_WITH_HEAT`
- `LOW_ACTIVITY_TODAY`
- `RECOVERY_FUEL_CONTEXT`
- `HIGH_ELEVATION_LOAD`
- `LOCATION_QUALITY_LOW`
- `GOAL_PROGRESS`

### 11.4 Processing approach

AFEE is intentionally implemented as a **transparent rule + score engine**, not an opaque generative-AI system.

The engine:

1. normalises relevant input values;
2. compares current values with recent personal baselines where enough history exists;
3. calculates context scores;
4. evaluates explainable rules;
5. applies cooldowns to prevent repeated notifications;
6. ranks candidate insights;
7. emits only the most relevant one or two items.

### 11.5 Example scoring model

For a workout effort context score:

```text
EffortContextScore =
    0.30 * paceStrainScore
  + 0.20 * elevationScore
  + 0.20 * durationScore
  + 0.15 * temperatureStressScore
  + 0.10 * movementIntensityScore
  + 0.05 * heartRateScore (only when available; weights re-normalised otherwise)
```

The weights are product heuristics, clearly documented and testable. They are not presented as medical truth.

### 11.6 Pseudocode

```text
function evaluateDailyContext(context):
    if context.dataQuality == CRITICAL:
        return insight("Tracking quality is currently limited")

    candidates = []

    effort = computeEffortScore(context)
    nutrition = computeNutritionContext(context)
    weather = computeWeatherStress(context.weather)
    paceTrend = computePaceTrend(context.workout)

    if context.workout.isActive:
        if weather.high and paceTrend.declining and effort.high:
            candidates += createInsight(
                type = PACE_DECLINE_WITH_HEAT,
                evidence = [weather, paceTrend, effort]
            )

        if context.elevationGain > personalElevationBaseline * 1.35:
            candidates += createInsight(HIGH_ELEVATION_LOAD)

    if context.todayActivity > activityBaseline * 1.30
       and nutrition.proteinRatio < configuredRecoveryThreshold:
        candidates += createInsight(RECOVERY_FUEL_CONTEXT)

    candidates = applyCooldowns(candidates)
    candidates = rankByPriorityConfidenceRecency(candidates)

    return takeTop(candidates, 2)
```

### 11.7 Complexity

For one evaluation cycle, most calculations are **O(1)** because aggregates and rolling statistics are maintained incrementally. A pace trend over a bounded recent window of `w` samples is **O(w)**, where `w` is intentionally small and fixed. Therefore practical runtime is constant with respect to total historical database size.

### 11.8 UI behaviour

- During workouts: show a compact insight card that never blocks core metrics.
- On Today dashboard: show the highest-ranked current insight.
- On workout summary: show 2–3 evidence-backed interpretations.
- Tapping **Why am I seeing this?** reveals the metrics used.

### 11.9 Example scenarios

#### Scenario A — Heat + pace decline

- Temperature rises to 30°C.
- Current pace becomes 12% slower than the first half of the workout.
- Accelerometer still confirms active running.
- AFEE concludes that the pace change is unlikely to be caused by stopping.
- UI shows a low-interruption insight: *“Effort appears higher than earlier in this run. Heat and pace trend are contributing factors.”*

#### Scenario B — Elevation-heavy route

- Distance is similar to user's normal 5 km run.
- Barometer/GPS elevation fusion detects much more climbing.
- Summary says: *“This route involved more climbing than your recent runs, so distance alone understates the effort.”*

#### Scenario C — High-activity day with limited logged recovery nutrition

- User has completed substantially more activity than their baseline.
- Logged protein is below their configured target range.
- Dashboard displays: *“You have logged more activity than usual today while protein intake remains below your target. Consider reviewing your next meal.”*

### 11.10 Technical implementation

- `AdaptiveInsightEngine` in domain layer.
- Pure Kotlin logic with no Android dependencies so it can be unit tested.
- Inputs arrive as immutable domain models.
- Output exposed from `InsightsRepository` as `Flow<List<Insight>>`.
- Re-evaluation triggered by important state changes rather than every raw sensor sample.
- Uses `distinctUntilChanged()` and rate limiting to avoid UI churn.

---

## 12. Sensor Strategy

### 12.1 Sensor overview

| Sensor / source | Role in MotionFuel | MVP | Typical collection strategy | Battery notes | Fallback |
|---|---|---:|---|---|---|
| Accelerometer | Movement intensity, step-like periodicity, stop/move detection | Yes | 25–50 Hz during active workout | Moderate | GPS + step counter |
| Gyroscope | Motion dynamics, orientation change, improves activity confidence | Yes | 25–50 Hz during active workout | Moderate | Accelerometer-only classifier |
| Step Counter | Cumulative steps, cadence support, walking/running evidence | Yes | Event-driven | Low | Accelerometer step periodicity |
| GPS / Fused Location | Route, distance, speed, pace, localisation | Yes | ~1 Hz target while active, adaptive when stationary | High | Reduced-quality inertial estimate; no trusted route |
| Barometer | Relative elevation trend and climb detection | Optional but strongly recommended | 2–5 Hz | Low–moderate | GPS altitude/elevation service |
| Magnetometer | Heading support at low speed / map orientation | Optional | 5–10 Hz only when needed | Low–moderate | GPS bearing |
| Heart rate via Health Connect/wearable | Additional effort context | Phase 2 | Platform-dependent | External | Reweight model without HR |
| Weather API | Environmental stress context | Yes | On workout start + periodic low-frequency refresh | Network | Cached recent weather |

### 12.2 Accelerometer

**Data:** X/Y/Z acceleration including gravity depending on API mode.  
**Processing:** magnitude calculation, gravity/high-pass separation where appropriate, windowed mean/variance, energy, zero-crossing or peak features.  
**Purpose:** identify movement intensity and whether GPS motion corresponds to genuine user movement.  
**Interactions:** fused with gyroscope, step counter, and GPS speed.  
**Feature:** activity-state classification and GPS drift rejection.  
**Sampling:** 25 Hz is adequate for an MVP classifier; 50 Hz can be evaluated experimentally.  
**Battery:** stop immediately when workout is inactive.  
**Fallback:** step counter and GPS-derived motion state.

### 12.3 Gyroscope

**Data:** angular velocity around three axes.  
**Processing:** windowed angular variance/energy.  
**Purpose:** distinguish richer body/phone motion from smooth vehicular or stationary GPS movement and improve classifier confidence.  
**Sampling:** aligned with accelerometer window.  
**Fallback:** classifier uses fewer features and returns lower confidence.

### 12.4 Step Counter

**Data:** cumulative step count since boot.  
**Processing:** snapshot at workout start; difference produces workout steps. Cadence derived from step deltas over time.  
**Purpose:** strong low-power evidence for pedestrian activity.  
**Fallback:** approximate step cadence from accelerometer peak periodicity, clearly marked as estimated.

### 12.5 GPS / Fused Location

**Data:** latitude, longitude, timestamp, accuracy, speed, bearing, altitude when available.  
**Processing:** accuracy validation, stale-fix rejection, impossible-jump rejection, smoothing, Haversine distance, pace.  
**Purpose:** route and core outdoor workout metrics.  
**Fallback:** workout can continue with time/steps/movement, but distance is marked degraded until acceptable validated location updates resume.

### 12.6 Barometer

**Data:** atmospheric pressure.  
**Processing:** relative pressure changes mapped to approximate altitude change, calibrated against starting reference/elevation.  
**Purpose:** detect relative climb/descent more smoothly than noisy GPS altitude.  
**Fallback:** GPS altitude/elevation estimates with reduced confidence.

### 12.7 Magnetometer

Use only if the device supports it and only when map heading provides meaningful UX value. It is not a core algorithm dependency.

### 12.8 Heart rate

Optional input through Health Connect or a supported wearable source. The app must remain fully functional without heart rate.

---

## 13. Sensor Fusion Strategy

### 13.1 Objective

Combine independent evidence to estimate the user's movement state more reliably than any single sensor can.

### 13.2 MVP classes

- `STATIONARY`
- `WALKING`
- `RUNNING`
- `UNKNOWN`

Cycling is Phase 2.

### 13.3 Sliding-window feature extraction

Use 2-second windows with 50% overlap.

Features may include:

- acceleration magnitude mean;
- acceleration magnitude variance;
- peak count;
- dominant frequency band;
- gyroscope magnitude variance;
- steps per minute;
- GPS speed;
- GPS speed variance;
- location accuracy;
- barometric vertical-rate trend when available.

### 13.4 Hybrid classifier

A rule-based probabilistic score is recommended for the MVP because it is:

- explainable to assessors;
- trainable/tunable without requiring a large labelled dataset;
- deterministic and testable;
- lightweight enough for real-time mobile use.

Example:

```text
runningScore =
    w1 * normalize(cadence)
  + w2 * normalize(gpsSpeed)
  + w3 * normalize(accelEnergy)
  + w4 * normalize(gyroEnergy)
  - w5 * stationaryPenalty
```

Scores for all classes are normalised. A confidence threshold prevents false certainty.

### 13.5 State stabilisation

Avoid changing activity label on every window.

Use:

- minimum confidence threshold;
- 2–3 consecutive windows before state transition;
- hysteresis so the current class remains active unless another class is meaningfully stronger.

### 13.6 Benefit

This fusion supports a visible assessor demonstration:

- GPS may show tiny movement while phone is stationary;
- accelerometer + steps indicate no movement;
- MotionFuel rejects this as GPS drift;
- route and pace remain stable.

That is a much stronger sensing demonstration than simply reading sensor values.

---

## 14. Advanced Algorithms

### 14.1 Algorithm A — Sensor-fusion activity classifier

**Purpose:** infer stationary/walking/running state.  
**Inputs:** accelerometer, gyroscope, step counter, GPS speed.  
**Processing:** windowing → feature extraction → class scores → confidence → state hysteresis.  
**Output:** `ActivityClassification(type, confidence, timestamp)`.  
**Complexity:** O(n) per sensor window, where n is samples in a bounded window; effectively constant-time per second.

**Pseudocode:**

```text
for each sensorWindow:
    f = extractFeatures(sensorWindow)

    scores.stationary = stationaryModel(f)
    scores.walking = walkingModel(f)
    scores.running = runningModel(f)

    class = argmax(scores)
    confidence = normalizedMargin(scores)

    if confidence < MIN_CONFIDENCE:
        class = UNKNOWN

    stableClass = hysteresis.update(class, confidence)
    emit(stableClass)
```

**Why it improves the app:** it improves movement interpretation, route filtering, calorie estimation, and dynamic UI state.

### 14.2 Algorithm B — GPS validation, outlier rejection, and smoothing

**Purpose:** reduce noisy routes and false distance accumulation.

**Inputs:** successive locations containing coordinates, timestamp, accuracy, speed.  
**Processing:**

1. reject stale timestamps;
2. reject very poor accuracy beyond a configurable threshold;
3. compute raw displacement using Haversine;
4. derive implied speed;
5. reject impossible jumps;
6. use sensor-fusion activity state to reject stationary drift;
7. smooth accepted point using lightweight Kalman or exponential filter;
8. accumulate distance only from accepted points.

**Pseudocode:**

```text
function processLocation(prev, current, activity):
    if current.isStale: reject
    if current.accuracy > MAX_ACCURACY: markLowQuality

    d = haversine(prev.raw, current.raw)
    dt = current.time - prev.time
    impliedSpeed = d / dt

    if impliedSpeed > activitySpecificMaxSpeed:
        reject OUTLIER

    if activity == STATIONARY and d < driftRadius(current.accuracy):
        reject DRIFT

    filtered = kalman.update(current.position, current.accuracy)
    return accepted(filtered)
```

**Complexity:** O(1) per location point.

### 14.3 Algorithm C — Distance and pace

Distance between accepted points uses the Haversine formula. Pace is derived from accepted moving time and filtered distance, with optional rolling pace over the most recent 30–60 seconds.

**Complexity:** O(1) incremental update.

### 14.4 Algorithm D — Elevation fusion

**Purpose:** estimate climb/descent more robustly than raw GPS altitude.

**Inputs:** barometer relative altitude, GPS altitude/elevation, movement state.  
**Approach:** complementary fusion:

- barometer gives smooth short-term relative change;
- GPS/elevation source provides slower absolute correction;
- discard pressure spikes and accumulate gain only after a minimum vertical threshold.

**Pseudocode:**

```text
relativeBaro = pressureToRelativeAltitude(pressure)
absoluteGps = validatedGpsAltitude
fusedAltitude = alpha * previousFused
              + beta * relativeBaroCorrected
              + gamma * absoluteGps

if fusedAltitude - localMinimum > gainThreshold:
    accumulateElevationGain()
```

**Complexity:** O(1) per update.

### 14.5 Algorithm E — Energy expenditure estimate

**Purpose:** produce a more context-aware estimate than distance-only calories.

Baseline model:

```text
kcal = MET(activity, paceBand) * 3.5 * weightKg / 200 * minutes
```

Adjustments may consider:

- pace band;
- grade/elevation effort;
- validated running/walking classification;
- optional heart-rate confidence modifier.

All adjustments must be conservative and labelled **estimated**.

**Complexity:** O(1).

### 14.6 Algorithm F — Adaptive Fuel & Effort Engine

Described in Section 11.

### 14.7 Algorithm G — Privacy-zone route masking for cloud backup

**Purpose:** prevent exact locations such as home from being included in optional detailed-route cloud backups.

**Inputs:** route polyline, privacy-zone centre, radius.  
**Processing:** remove or coarsen points within the configured radius.  
**Output:** masked route for cloud backup while preserving the complete local route. The MVP social image intentionally uses the complete route and does not expose an endpoint-trimming control.

**Pseudocode:**

```text
function maskRoute(route, zones):
    output = []
    for point in route:
        if any(distance(point, zone.center) < zone.radius):
            continue
        output += point
    return output
```

**Complexity:** O(p × z), where p is number of route points and z is number of privacy zones. Because z is very small, practical complexity is linear in route size.

### 14.8 Advanced-algorithm recommendation

For the assessed MVP, implement Algorithms A, B, C, E, F, and G. Add elevation fusion if the test device has a pressure sensor.

---

## 15. Location & Google Maps Strategy

### 15.1 Required mapping stack

The assessed build will use:

- **FusedLocationProviderClient** for location collection;
- **Google Maps SDK for Android** for map rendering;
- **Maps Compose** so the map is a native Jetpack Compose component;
- `GoogleMap` for the map surface;
- `Polyline` for the accepted workout route;
- `Marker` for the start point and, where useful, current position;
- `CameraPositionState` for follow-mode and user-controlled camera state.

Implementation baseline (August 2026):

```kotlin
// version catalog is preferred
implementation("com.google.maps.android:maps-compose:8.4.0")
implementation("com.google.android.gms:play-services-location:<current-compatible-version>")
```

The Google Maps API key is exposed to the Maps SDK through the manifest metadata key `com.google.android.geo.API_KEY`, with the value injected from a non-committed build secret/local property.

The map is a visualisation layer only. Distance, pace, GPS validation and route acceptance are calculated in domain code before points reach the map.

### 15.2 API key setup

- Create a Google Cloud project and enable Maps SDK for Android.
- Store the Maps key outside source control and inject it into the Android manifest through the build configuration/secrets mechanism.
- Restrict the production key to the Android package name and signing certificate where supported.
- Do not use the Maps key for server APIs.
- Keep nutrition/weather server secrets behind the backend proxy rather than treating an APK as a secret store.

### 15.3 Location quality

```text
LocationQuality
- GOOD
- FAIR
- POOR
- UNAVAILABLE
```

Quality is derived from horizontal accuracy, fix age, consistency with the previous accepted point, implied speed and agreement with sensor/step movement state.

### 15.4 Route-processing pipeline

```text
Fused location update
→ timestamp/freshness validation
→ accuracy validation
→ implied-speed check
→ stationary-drift check using steps/sensor fusion
→ smoothing
→ AcceptedLocationPoint
→ Room buffer
→ Workout metrics
→ ViewModel StateFlow
→ Google Maps Polyline
```

Rejected points are never added to total distance or the visible route polyline.

### 15.5 Live Run Map UX

The Live Workout screen shows:

- Google Map occupying the top ~45–55% of the screen;
- growing route polyline;
- start marker;
- current-position marker or Maps location layer where permission state is valid;
- optional follow-camera button;
- GPS status chip (`Good`, `Fair`, `Weak`, `Unavailable`);
- bottom metric sheet with elapsed time, distance, pace, steps, estimated burn and optional predicted finish time;
- pause/resume/end controls that remain reachable with one hand.

Camera behaviour:

- initial camera centers on the first trusted fix;
- while follow-mode is enabled, the camera updates at a limited rate rather than every raw location callback;
- manual pan/zoom temporarily disables follow-mode;
- a recenter button restores follow-mode.

### 15.6 Saved workout map

Workout Detail reconstructs the route from accepted persisted points. It displays:

- full route polyline;
- start/end markers;
- fit-bounds camera so the entire route is visible;
- distance, moving time, average pace, energy, steps, elevation and dominant movement;
- a single **Share activity image** button with no explanatory copy or endpoint-trimming setting.

The share action waits until the Maps SDK view has loaded, captures the attributed Google basemap with the full route, composites it with the activity statistics into a 1080 × 1350 PNG, and opens Android's system share sheet. If Google Maps is not configured, the renderer may fall back to the route outline so sharing still completes.

### 15.7 What is intentionally not required

- Google Directions API is **not** required to track a run because MotionFuel records the user's actual GPS path rather than requesting a route from Google.
- Places API is not required for MVP.
- Offline map tiles are not required.

This keeps cost and integration risk low while still delivering a real Google Maps workout experience.

---

## 16. Nutrition & Calorie Tracking System

### 16.1 Product goal

Nutrition is intentionally narrower than MyFitnessPal but complete enough to feel like a real daily calorie tracker. The core job is: **find food quickly, assign it to a meal, understand calories against the user's target, and connect intake to activity/progress.**

### 16.2 Food database

Use **USDA FoodData Central (FDC)** as the default provider for the assessed build.

Required integration behaviour:

- search foods by text;
- fetch nutrient details for the selected food;
- map provider nutrients into the MotionFuel domain model;
- cache recently selected foods locally;
- debounce search requests;
- keep provider-specific DTOs outside the domain layer;
- access FDC through the app backend/proxy so the provider API key is not committed to or trusted inside the APK.

Provider limitations must be visible: if a nutrient is missing, MotionFuel displays only the available values and never invents values.

### 16.3 Meal model and dashboard UI

Meal types:

```text
BREAKFAST
LUNCH
DINNER
SNACK
```

Food Dashboard layout:

1. **Daily calorie header**
   - calorie limit;
   - calories consumed;
   - calories remaining;
   - progress bar/ring.
2. **Breakfast card**
   - current calories;
   - percentage of daily goal;
   - item count;
   - `Add food` action.
3. **Lunch card** — same behaviour.
4. **Dinner card** — same behaviour.
5. **Snacks** — compact expandable section.
6. **Macro row** — protein, carbohydrates and fat consumed vs targets where configured.

Meal calories are not separate hard limits by default. Each card shows its contribution to the daily target so users can see where calories are being consumed without falsely implying a rigid meal prescription.

### 16.4 Daily calorie goal and maintenance calories

MotionFuel stores two separate values because they represent different concepts:

```text
maintenanceCaloriesKcal = estimated TDEE needed to maintain current weight
calorieTargetKcal       = the daily intake goal currently selected by the user
```

At first signup:

```text
calorieTargetKcal = maintenanceCaloriesKcal
```

If a user later chooses a weight-loss or weight-gain goal, the app may suggest a modest editable adjustment, but the user remains in control of the final target.

The Today screen must keep the distinction visible:

```text
Estimated maintenance     2,500 kcal/day
Daily goal                2,500 kcal
Food logged               1,420 kcal
Remaining                 1,080 kcal
```

For the MVP:

```text
remainingCalories = calorieTargetKcal - caloriesConsumedKcal
```

Estimated workout calories are shown separately and are **not automatically added back** into remaining calories. This avoids giving false precision from exercise-burn estimates. A future optional setting may support exercise-adjusted goals.

`calorieTargetKcal` is copied into each `DailySummary` as a snapshot so historical charts remain correct after the target changes.

### 16.5 Maintenance-calorie calculation during Sign Up

The calculation follows the Mifflin–St Jeor BMR equation and TDEE activity multipliers supplied for this project.

#### Required inputs

- age in years;
- sex used for the equation (`MALE` or `FEMALE`);
- height in centimetres;
- weight in kilograms;
- activity level.

#### Step 1 — Calculate BMR

```text
Male:
BMR = (10 × weightKg) + (6.25 × heightCm) − (5 × ageYears) + 5

Female:
BMR = (10 × weightKg) + (6.25 × heightCm) − (5 × ageYears) − 161
```

BMR is the estimated energy requirement at rest.

#### Step 2 — Select activity factor

| Activity level | UI description | Factor |
|---|---|---:|
| Sedentary | Little/no exercise | 1.2 |
| Light | Exercise 1–3 days/week | 1.375 |
| Moderate | Exercise 3–5 days/week | 1.55 |
| Very Active | Exercise 6–7 days/week | 1.725 |
| Extremely Active | Hard physical work/training | 1.9 |

#### Step 3 — Calculate TDEE

```text
TDEE = BMR × activityFactor
maintenanceCaloriesKcal = round(TDEE)
```

Example:

```text
BMR = 1,750 kcal/day
Activity factor = 1.55
TDEE = 1,750 × 1.55 = 2,712.5
Estimated maintenance = 2,713 kcal/day
```

#### Step 4 — Establish the user's calorie goal

- `MAINTAIN`: initial goal = estimated TDEE.
- `LOSE_WEIGHT`: app may suggest a moderate configurable deficit below TDEE.
- `GAIN_WEIGHT`: app may suggest a moderate configurable surplus above TDEE.
- User can edit the suggested goal before accepting it.

The university MVP can make **Maintain** the default so the core logic remains deterministic and easy to test.

#### Validation

Suggested input ranges for UI validation:

- age: 16–100 years;
- height: 120–230 cm;
- weight: 35–300 kg.

These are software sanity limits, not medical definitions. Invalid values produce inline validation errors and prevent calculation.

#### Domain implementation

The formula must live in pure Kotlin, not inside a Composable or ViewModel:

```text
CalculateMaintenanceCaloriesUseCase
    input: age, sex, heightCm, weightKg, activityLevel
    output: MaintenanceCaloriesResult(
        bmrKcal,
        activityFactor,
        tdeeKcal,
        roundedMaintenanceKcal
    )
```

This makes the equation easy to unit test independently from Firebase and UI code.

### 16.6 Firebase Sign Up persistence flow

```text
Sign Up UI
→ validate email/password/profile fields
→ CalculateMaintenanceCaloriesUseCase
→ FirebaseAuth.createUserWithEmailAndPassword()
→ receive Firebase UID
→ create users/{uid} profile document
→ store maintenanceCaloriesKcal + calorieTargetKcal
→ cache profile locally
→ optionally send Firebase email verification
→ navigate to Today
```

If Firebase Authentication succeeds but the profile write temporarily fails, the app must retain a `PROFILE_INCOMPLETE` state and retry/profile-repair rather than creating a second account.

### 16.7 Add food flow

```text
Food Dashboard
→ tap Breakfast/Lunch/Dinner/Snack
→ Search Food
→ debounced provider search
→ select FoodItem
→ choose serving/quantity
→ preview calories/macros
→ Add
→ save NutritionEntry locally
→ DailySummary Flow updates
→ meal card + daily total recompose
→ Firestore sync queued
```

### 16.8 Custom Meal with photo

The Custom Meal feature exists for homemade food, restaurant food or items missing from the provider.

Fields:

- meal name — required;
- meal type — required;
- serving label/amount — optional;
- protein grams — optional;
- carbohydrate grams — optional;
- fat grams — optional;
- calories — required or calculated;
- optional photo;
- notes — optional.

If calories are not entered but macros are present, prefill:

```text
estimatedCalories = proteinG * 4 + carbohydrateG * 4 + fatG * 9
```

The user can override the calculated value because labels, fibre, sugar alcohols and food composition can make this approximation imperfect.

Photo implementation for MVP:

- use Android Photo Picker or a camera intent;
- copy the selected/captured image into app-private storage;
- persist a local URI/path in `CustomMeal`;
- do not require broad media-library permission;
- cloud image backup is Phase 2.

### 16.9 Progress calorie and weight trend bar graphs

The Progress screen uses a familiar MyFitnessPal-inspired summary layout while retaining MotionFuel's own visual design. It contains two separate chart cards:

- **Calories** — one bar per day representing `totalCaloriesConsumed`, with a reference line for `calorieTargetSnapshotKcal`;
- **Weight** — one bar per day representing the latest valid weight recorded for that date, displayed in the user's selected unit.

Both cards share the same range selector:

- **7 days** — one bar per day;
- **30 days** — one bar per day with horizontally scrollable or compact labels.

Calories and weight must remain in separate charts because they use different units and scales. The screen must not use a combined or dual axis. Above each chart, show the latest value and the change over the selected period where enough data exists.

Implementation recommendation: use **Jetpack Compose Canvas** for both bar charts rather than adding a chart framework. This keeps the assessed build smaller and demonstrates custom Compose drawing.

Chart interactions:

- tap a calorie bar to show date, calories consumed and historical target;
- tap a weight bar to show date, recorded weight and change from the previous recorded value;
- missing calorie days are shown as zero or no-data according to the chosen policy;
- missing weight days show no bar and must not be treated as zero or filled with invented measurements;
- changing the user's target does not rewrite historical target snapshots.

### 16.10 Offline behaviour

Users can always:

- create a Custom Meal;
- reuse recent cached foods;
- edit/delete local entries;
- view today's totals and historical charts.

Remote search simply becomes unavailable until connectivity returns.

### 16.11 Safety and wording

MotionFuel must not:

- diagnose nutrient deficiency;
- recommend extreme calorie restriction;
- claim calorie or burn estimates are exact;
- silently change the user's target.

The app presents transparent estimates and user-configured goals.

---

## 17. Progress, Maintenance Calories & Adaptive Recommendation System

### 17.1 Daily Context Model

```text
DailyContext
- date
- userGoal
- calorieTargetSnapshot
- maintenanceCaloriesKcal
- totalSteps
- totalDistance
- activeMinutes
- workoutLoadEstimate
- energyExpendedEstimate
- energyConsumed
- proteinConsumed
- carbohydrateConsumed
- fatConsumed
- weatherContext
- currentWorkout?
- activityBaseline
- nutritionTargetProgress
- latestWeight?
- dataQuality
```

### 17.2 Weight tracking

Progress allows users to add dated weight entries manually.

Rules:

- values are stored locally first and later synchronised;
- user may edit/delete an incorrect entry;
- the Progress screen displays weight as a 7-day/30-day bar graph, with no fabricated values for dates without a measurement;
- the chart header displays the latest weight and the net change over the selected period when enough data exists;
- no automatic judgement labels such as `good` or `bad` are attached to weight change;
- the user can mark the latest measurement as their **current profile weight**.

### 17.3 Maintenance-calorie recalculation

The MVP does **not** infer maintenance calories from logged food and weight change. It uses the same deterministic TDEE formula as signup.

Recalculate maintenance when any calculation input changes:

- age;
- sex used by the equation;
- height;
- current weight;
- activity level.

Example flow:

```text
User records new current weight
→ UserProfile.currentWeightKg changes
→ CalculateMaintenanceCaloriesUseCase runs
→ new BMR/TDEE calculated
→ maintenanceCaloriesKcal updated
→ MaintenanceCalorieSnapshot stored
→ Today UI updates immediately
```

If the user is following a custom calorie target, MotionFuel does not silently overwrite it. Instead:

> **Maintenance estimate updated: 2,430 kcal/day**  
> Your current daily goal is 2,200 kcal.  
> `Use maintenance as goal`

If the user is explicitly in a `MAINTAIN` goal mode and has enabled `Keep goal matched to maintenance`, the app may update the calorie goal automatically. This behaviour should be opt-in and clearly labelled.

### 17.4 Maintenance history

Each recalculation may store a lightweight snapshot:

```text
MaintenanceCalorieSnapshot
- calculatedAt
- ageYears
- sexForEquation
- heightCm
- weightKg
- activityLevel
- bmrKcal
- activityFactor
- maintenanceCaloriesKcal
```

This supports explainability without a complex inference algorithm.

### 17.5 Personal baselines

AFEE may still calculate rolling behavioural baselines such as:

- average active minutes over 14 days;
- typical run pace;
- usual daily steps;
- usual workout duration;
- typical elevation per kilometre.

These baselines affect activity insights, not the core TDEE formula.

### 17.6 AFEE integration

AFEE can compare:

- calories consumed vs user calorie target;
- current maintenance-calorie estimate vs chosen goal;
- workout activity vs recent baseline;
- weather stress context;
- protein/carbohydrate/fat totals;
- route difficulty and pace trend.

Example insight:

> **High-activity day**  
> You have completed substantially more activity than your recent average while your logged intake remains below your current daily goal.

The insight must remain neutral and must not prescribe extreme intake changes.

### 17.7 Explainability

Every adaptive insight must identify the metrics that triggered it.

Maintenance calories must also expose the calculation inputs on demand:

```text
Weight: 74 kg
Height: 178 cm
Age: 24
Activity: Moderate (1.55)
BMR: 1,720 kcal/day
Estimated maintenance: 2,666 kcal/day
```

The UI should provide a `How is this calculated?` action linking to the BMR/TDEE explanation.

---

## 18. External APIs & Platform Services

### 18.1 Nutrition — USDA FoodData Central

Use FoodData Central for food search/detail where suitable.

Conceptual access:

```text
GET /foods/search?query={text}
GET /food/{fdcId}
```

The exact provider endpoint and response mapping remain behind `NutritionRemoteDataSource` so another provider can be substituted without rewriting the UI.

If the API credential must remain secret, call the provider through a small Firebase Cloud Function/backend proxy rather than embedding an unrestricted secret in the APK.

### 18.2 Weather API

Required fields:

- temperature;
- relative humidity;
- precipitation/rain state;
- wind speed;
- optional UV index.

Refresh at workout start and only at low frequency during a long workout.

### 18.3 Google Maps

Use Google Maps SDK for Android with Maps Compose for:

- Live Workout map;
- route polyline;
- current-position marker;
- start marker;
- saved-workout map;
- privacy-zone preview.

Route calculations and filtering remain independent of the map UI.

### 18.4 Firebase Authentication

Firebase Authentication is the identity provider for the MVP.

Required flows:

- `createUserWithEmailAndPassword` for Sign Up;
- `signInWithEmailAndPassword` for Login;
- `sendPasswordResetEmail` for Forgotten Password;
- sign-out;
- current-user/session restoration at app launch;
- optional `sendEmailVerification` after signup.

Authentication logic belongs behind `AuthRepository`; Composables should not call Firebase SDK methods directly.

### 18.5 Cloud Firestore

Cloud Firestore stores account-backed user data such as:

- profile and maintenance-calorie inputs/results;
- workout summaries;
- nutrition entries;
- daily summaries;
- weight entries;
- settings/goal metadata;
- optional route chunks when cloud route backup is enabled.

Ownership is derived from Firebase Authentication UID.

### 18.6 Firebase Storage

Storage is optional for the assessed MVP.

Potential uses:

- profile image;
- cloud-backed Custom Meal photo;
- Social Recipe image in Phase 2.

Custom Meal photos may remain local-only initially to reduce scope.

### 18.7 Firebase Cloud Functions / trusted backend

Use only where server-side trust is genuinely required, for example:

- proxying an external API secret;
- account-deletion cleanup across collections;
- Phase 2 public recipe moderation/aggregation.

Do not add a custom backend merely to perform ordinary authenticated Firestore CRUD that Security Rules can protect directly.

### 18.8 API error mapping

Map provider/network failures into domain-level errors such as:

```text
NetworkError
Timeout
RateLimited
NotFound
MalformedResponse
ServiceUnavailable
Unauthenticated
PermissionDenied
Unknown
```

UI shows actionable messages instead of raw HTTP/Firebase exception strings.

---

## 19. Firebase Authentication & Cloud Architecture

### 19.1 Responsibility split

| Concern | Technology | Responsibility |
|---|---|---|
| Identity | Firebase Authentication | signup, login, reset, optional email verification, session |
| User/private cloud data | Cloud Firestore | profile, workouts, nutrition, progress, settings |
| Media | Firebase Storage | optional profile/custom meal/recipe images |
| Local structured data | Room | workout/nutrition/progress source of truth where offline support matters |
| Small preferences | DataStore | theme, units, privacy toggles, lightweight cached settings |
| Deferred sync | WorkManager | retry pending uploads when network returns |
| Secret-key proxy / cleanup | Cloud Functions, only when needed | protect server secrets and perform trusted maintenance |

### 19.2 Authentication state

```text
FirebaseAuth.currentUser
→ AuthRepository
→ StateFlow<AuthState>
→ App navigation
```

Possible states:

```text
Loading
SignedOut
EmailVerificationRequired(email)
SignedInProfileIncomplete(uid)
SignedIn(uid)
```

A Firebase account with an unverified email must never load private Firestore data or route to Today. After verification, an account without a complete Firestore profile routes to profile completion rather than Today.

### 19.3 Local-first principle

The active workout never depends on Firestore availability.

During workout:

- sensor/location processing runs locally;
- route and metrics are persisted locally;
- cloud writes are deferred.

After workout:

- Room marks the record `PENDING_SYNC`;
- WorkManager writes eligible records to Firestore;
- sync state becomes `SYNCED` or `FAILED_RETRYABLE`.

### 19.4 Direct Firestore access with Firebase identity

Because Firebase Authentication and Firestore use the same identity, the Android client may perform normal user-scoped Firestore reads/writes directly.

Firestore Security Rules use:

```text
request.auth != null
&& request.auth.uid == userId
&& request.auth.token.email_verified == true
```

Initial profile creation is the only limited operation allowed before verification, because it is part of the atomic signup experience. The create request must use the authenticated token email, a field allowlist, accepted enum/range values and a server timestamp. Reads, updates, deletion and weight subcollection access require verified email. Unknown subcollections are denied.

Firebase App Check complements Authentication and Security Rules by attesting that requests originate from the registered app. Debug builds use a registered debug token; release builds use Play Integrity. Enforcement is enabled only after valid debug/internal/release traffic is visible in Firebase metrics.

### 19.5 Cloud data minimisation

By default sync only useful user-level records:

- user profile;
- maintenance calorie inputs/result;
- workout summaries;
- nutrition entries;
- daily summaries;
- weight history;
- user goals/settings needed across devices.

Do not upload raw high-frequency accelerometer or gyroscope traces in the production build.

Detailed routes are uploaded only when cloud route backup is enabled.

### 19.6 Social Recipe architecture — Phase 2

Private user data stays under `users/{uid}`. A recipe becomes public only after an explicit Publish action copies a sanitised recipe representation into `publicRecipes/{recipeId}`.

---

## 20. Local Storage & Offline Strategy

### 20.1 Room entities

Required:

- `UserProfileEntity`
- `WorkoutEntity`
- `RoutePointEntity`
- `NutritionEntryEntity`
- `CustomMealEntity`
- `DailySummaryEntity`
- `WeightEntryEntity`
- `MaintenanceCalorieSnapshotEntity`
- `InsightEntity`
- `SyncQueueEntity`
- `CachedFoodEntity`
- `RecipeCacheEntity` — Phase 2

Optional debug/research only:

- `SensorFeatureWindowEntity`

### 20.2 DataStore

Store:

- theme;
- units;
- onboarding complete;
- selected calorie target preference metadata;
- default workout type;
- privacy-zone metadata;
- route-cloud-backup preference;
- chart range preference (7/30 days);
- insight preferences;
- last known user ID/session-related non-secret UI state where appropriate.

Do not use DataStore for large structured records that belong in Room.

### 20.3 Offline-first rules

- Room is the source of truth for user-generated records.
- UI reads Room via Flow.
- Writes commit locally first.
- Sync queue records cloud mutations.
- WorkManager retries when network returns.
- Food search requires network unless cached/recent results are available.
- Custom Meals and weight entries are always creatable offline.
- Calorie charts are generated entirely from local daily summaries.

### 20.4 Custom meal images

For MVP, images are stored in app-private files and referenced by a stable local URI/path. Deleting the meal should remove the unreferenced local image according to a cleanup policy.

### 20.5 Daily summary aggregation

Whenever nutrition/workout/weight data changes, update or derive the local day summary containing:

- total calories consumed;
- protein/carbs/fat;
- calorie target snapshot;
- steps;
- active minutes;
- estimated workout burn;
- latest weight for the day if present.

This makes 7/30-day charts fast and deterministic.

### 20.6 Conflict strategy

- stable UUIDs for user-created records;
- `updatedAt` and version fields;
- idempotent backend upserts;
- last-write-wins only for simple scalar edits;
- never silently merge two different workout routes;
- public recipe imports create immutable private snapshots.

---

## 21. Real-Time Processing Architecture

### 21.1 Principles

- Raw sensors produce high-frequency events.
- UI should not recompose at raw sensor frequency.
- Domain processors aggregate sensor data into stable state.
- StateFlow exposes only UI-relevant derived values.

### 21.2 Suggested frequencies

| Data | Raw/collection frequency | UI update target |
|---|---:|---:|
| Accelerometer | 25–50 Hz | classification 1–2 Hz |
| Gyroscope | 25–50 Hz | classification 1–2 Hz |
| Barometer | 2–5 Hz | elevation 1 Hz |
| GPS | ~1 Hz active | 1 Hz map/metrics |
| Step counter | event-driven | 1 Hz cadence display max |
| Workout timer | internal | 1 Hz |
| Weather | start + 20–30 min | on change only |
| Nutrition totals | on database change | immediate after transaction |
| AFEE | event-triggered | only when insight materially changes |

### 21.3 Coroutines

Use:

- `Dispatchers.Default` for CPU-bound feature extraction/filtering;
- `Dispatchers.IO` for Room/network operations where library does not already manage threading;
- structured concurrency tied to service/repository lifecycle;
- SupervisorJob where one non-critical stream must not cancel the entire workout processor.

### 21.4 Flow

Use Flow for:

- sensor feature windows;
- location samples;
- active workout state;
- database observations;
- weather context;
- insights.

Use operators such as:

- `combine`
- `sample`
- `debounce` where appropriate
- `distinctUntilChanged`
- `mapLatest`
- `stateIn`

### 21.5 Foreground service

An active outdoor workout should run in a foreground service with a persistent notification so tracking survives temporary backgrounding and respects Android background-execution rules.

### 21.6 Compose

ViewModels expose immutable UI state:

```text
WorkoutUiState
- elapsedTime
- distance
- currentPace
- averagePace
- steps
- cadence
- elevationGain
- caloriesEstimate
- activityLabel
- gpsQuality
- routePoints
- insight?
- isPaused
```

Compose renders this state and sends user actions back as events.

---

## 22. Application Architecture

### 22.1 Architectural style

**Feature-oriented Clean Architecture with MVVM.**

### 22.2 Layers

#### Presentation

- Compose screens/components
- ViewModels
- UI state/event models

#### Domain

- pure Kotlin models
- use cases
- interfaces
- algorithms

#### Data

- repository implementations
- Room
- Firebase
- Retrofit/OkHttp
- sensor/location adapters

#### Platform/Core

- permission helpers
- connectivity monitor
- clock abstraction
- dispatchers
- logging abstraction
- common UI/theme

### 22.3 Why this supports readable software

- business logic is not embedded inside Composables;
- Android-specific dependencies are isolated;
- algorithms can be unit tested using deterministic inputs;
- repository interfaces make local/remote behaviour explicit;
- features are grouped by domain rather than giant generic folders;
- immutable UI state reduces hidden coupling;
- naming reflects user concepts rather than implementation accidents.

---

## 23. Architecture Diagram

```text
┌──────────────────────────────────────────────────────────────┐
│                    Jetpack Compose UI                        │
│ Login | Sign Up | Today | Activity | Food | Progress        │
└──────────────────────────────┬───────────────────────────────┘
                               │ user events / UiState
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                         ViewModels                           │
│ AuthVM | TodayVM | WorkoutVM | FoodVM | ProgressVM          │
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                         Use Cases                            │
│ SignUp | Login | CalculateMaintenance | LogFood             │
│ StartWorkout | ProcessLocation | SaveWeight | GenerateInsight│
└──────────────────────────────┬───────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────┐
│                        Repositories                          │
│ Auth | User | Workout | Nutrition | Progress | Weather      │
└───────────────┬────────────────────┬─────────────────────────┘
                │                    │
                ▼                    ▼
┌────────────────────────┐  ┌─────────────────────────────────┐
│ Local / Device         │  │ Firebase / Remote               │
│ Room, DataStore, GPS,  │  │ Auth, Firestore, Storage, APIs │
│ sensors, Maps state    │  │ Cloud Function only if needed  │
└────────────────────────┘  └─────────────────────────────────┘
```

Authentication state:

```text
Firebase Authentication
→ AuthRepository
→ AuthState StateFlow
→ navigation gate
```

Profile/TDEE flow:

```text
Sign Up form
→ CalculateMaintenanceCaloriesUseCase
→ Firebase Auth account
→ users/{uid} Firestore profile
→ Today maintenance-calorie card
```

### Architectural rule

Business logic such as BMR/TDEE calculation, GPS filtering, distance calculation and AFEE rules must remain pure domain logic and must not be embedded in Composables or Firebase callbacks.

---

## 24. Sensor Data Pipeline

```text
Accelerometer / Gyroscope / Step Counter / Barometer
                    │
                    ▼
            Android Sensor Adapters
                    │
                    ▼
           timestamp normalisation
                    │
                    ▼
              bounded buffers
                    │
                    ▼
          sliding-window processor
                    │
                    ▼
     feature extraction + noise handling
                    │
                    ▼
             SensorFusionEngine
                    │
                    ▼
       ActivityClassification Flow
                    │
           ┌────────┴─────────┐
           ▼                  ▼
 LocationProcessor       WorkoutMetrics
           │                  │
           └────────┬─────────┘
                    ▼
              WorkoutState
                    │
                    ▼
             WorkoutRepository
                    │
                    ▼
          ViewModel StateFlow
                    │
                    ▼
           Jetpack Compose UI
```

### Important design rule

Raw sensor samples do not go directly to the UI and do not go directly to Firebase.

---

## 25. Location Data Pipeline

```text
Fused Location Update
        │
        ▼
Timestamp / freshness check
        │
        ▼
Horizontal accuracy check
        │
        ▼
Haversine displacement
        │
        ▼
Implied-speed / jump validation
        │
        ▼
Stationary drift check using sensor-fusion state
        │
        ▼
Kalman / smoothing stage
        │
        ▼
Accepted filtered LocationPoint
        │
 ┌──────┼───────────────┬────────────────┐
 ▼      ▼               ▼                ▼
Route  Distance       Pace            Map state
 │      │               │                │
 └──────┴──────┬────────┴────────────────┘
               ▼
         Workout aggregate
               │
               ▼
      Room buffer / persistence
```

---

## 26. Data Models

### 26.1 UserProfile

```text
UserProfile
- userId: String                  // Firebase Authentication UID
- displayName: String
- email: String
- ageYears: Int
- sexForEquation: MALE | FEMALE
- heightCm: Double
- currentWeightKg: Double
- activityLevel: SEDENTARY | LIGHT | MODERATE | VERY_ACTIVE | EXTREMELY_ACTIVE
- activityFactor: Double
- goalType: MAINTAIN | LOSE_WEIGHT | GAIN_WEIGHT
- bmrKcal: Double
- maintenanceCaloriesKcal: Int    // rounded TDEE
- calorieTargetKcal: Int
- proteinTargetG: Double?
- carbohydrateTargetG: Double?
- fatTargetG: Double?
- profileComplete: Boolean
- createdAt: Instant
- updatedAt: Instant
```

`maintenanceCaloriesKcal` is recalculated from the profile inputs. `calorieTargetKcal` is separately editable so a calculated maintenance-calorie estimate is never confused with the user's chosen goal.

### 26.2 Workout

```text
Workout
- id: String
- userId: String
- type: WALK | RUN
- startTime: Instant
- endTime: Instant?
- durationSeconds: Long
- movingTimeSeconds: Long
- distanceMeters: Double
- targetDistanceMeters: Double?
- predictedFinishDurationSeconds: Long?
- steps: Long?
- averagePaceSecPerKm: Double?
- averageSpeedMps: Double?
- elevationGainMeters: Double?
- caloriesEstimate: Double?
- weatherSnapshot: WeatherContext?
- routePrivacyMode: RoutePrivacyMode
- syncState: SyncState
```

### 26.3 LocationPoint

```text
LocationPoint
- workoutId: String
- sequence: Int
- latitude: Double
- longitude: Double
- filteredLatitude: Double
- filteredLongitude: Double
- altitudeMeters: Double?
- accuracyMeters: Float
- speedMps: Float?
- bearingDegrees: Float?
- timestamp: Instant
- quality: LocationQuality
- accepted: Boolean
- rejectionReason: String?       // debug only
```

### 26.4 ActivityClassification

```text
ActivityClassification
- type: STATIONARY | WALKING | RUNNING | UNKNOWN
- confidence: Float
- evidence: Set<ActivityEvidence>
- timestamp: Instant
```

### 26.5 FoodItem

```text
FoodItem
- providerId: String
- name: String
- brand: String?
- servingLabel: String
- servingAmount: Double?
- caloriesKcal: Double
- proteinG: Double?
- carbohydratesG: Double?
- fatG: Double?
- fibreG: Double?
- sodiumMg: Double?
- source: FoodSource
```

### 26.6 NutritionEntry

```text
NutritionEntry
- id: String
- userId: String
- foodSnapshot: FoodSnapshot
- quantityMultiplier: Double
- mealType: BREAKFAST | LUNCH | DINNER | SNACK
- consumedAt: Instant
- sourceType: PROVIDER | CUSTOM_MEAL | RECIPE
- customMealId: String?
- recipeId: String?
- notes: String?
- syncState: SyncState
```

### 26.7 CustomMeal

```text
CustomMeal
- id: String
- userId: String
- name: String
- servingLabel: String?
- caloriesKcal: Double
- proteinG: Double?
- carbohydratesG: Double?
- fatG: Double?
- localImageUri: String?
- notes: String?
- createdAt: Instant
- updatedAt: Instant
```

### 26.8 DailySummary

```text
DailySummary
- date: LocalDate
- calorieTargetSnapshotKcal: Int
- caloriesConsumedKcal: Double
- breakfastCaloriesKcal: Double
- lunchCaloriesKcal: Double
- dinnerCaloriesKcal: Double
- snackCaloriesKcal: Double
- proteinG: Double
- carbohydratesG: Double
- fatG: Double
- steps: Long
- activeMinutes: Int
- workoutCaloriesEstimate: Double
- latestWeightKg: Double?
```

### 26.9 WeightEntry

```text
WeightEntry
- id: String
- userId: String
- weightKg: Double
- measuredAt: Instant
- note: String?
- syncState: SyncState
```

### 26.10 MaintenanceCalorieSnapshot

```text
MaintenanceCalorieSnapshot
- id: String
- userId: String
- calculatedAt: Instant
- ageYears: Int
- sexForEquation: MALE | FEMALE
- heightCm: Double
- weightKg: Double
- activityLevel: ActivityLevel
- activityFactor: Double
- bmrKcal: Double
- maintenanceCaloriesKcal: Int
- trigger: SIGN_UP | WEIGHT_UPDATE | PROFILE_UPDATE | ACTIVITY_LEVEL_UPDATE
```

### 26.11 WeatherContext

```text
WeatherContext
- timestamp: Instant
- temperatureC: Double
- humidityPercent: Double
- windSpeedMps: Double
- precipitationState: String
- uvIndex: Double?
- sourceAgeMinutes: Int
```

### 26.12 SocialRecipe — Phase 2

```text
SocialRecipe
- id: String
- authorUserId: String
- authorDisplayName: String
- title: String
- description: String?
- ingredients: List<RecipeIngredient>
- servings: Int
- caloriesPerServing: Double
- proteinPerServingG: Double?
- carbohydratesPerServingG: Double?
- fatPerServingG: Double?
- imageUrl: String?
- createdAt: Instant
- likeCount: Int               // optional; omit if scope is tight
```

### 26.13 Insight

See Section 11.

---

## 27. Firebase Cloud Data Model & Authorisation

### 27.1 Firestore structure

```text
users/{uid}
users/{uid}/workouts/{workoutId}
users/{uid}/nutritionEntries/{entryId}
users/{uid}/dailySummaries/{yyyy-MM-dd}
users/{uid}/weightEntries/{weightEntryId}
users/{uid}/maintenanceSnapshots/{snapshotId}
users/{uid}/goals/{goalId}

publicRecipes/{recipeId}                   // Phase 2
```

Detailed route backup can use chunked route documents only when the user enables route backup.

### 27.2 Firebase UID ownership

The client never chooses an arbitrary owner ID. After authentication:

```text
uid = FirebaseAuth.currentUser.uid
```

All user-private document paths use this UID.

### 27.3 Firestore Security Rules concept

```text
match /users/{userId} {
  allow read, write: if request.auth != null
                     && request.auth.uid == userId;

  match /{document=**} {
    allow read, write: if request.auth != null
                       && request.auth.uid == userId;
  }
}
```

Production rules should also validate allowed fields, numeric ranges, timestamps, string lengths and immutable ownership where appropriate.

### 27.4 User profile document

Store:

- display name;
- email or a normalised profile reference as appropriate;
- age;
- sex used for the BMR equation;
- height;
- current weight;
- activity level/factor;
- BMR;
- maintenance calories;
- current calorie target;
- goal type;
- timestamps/profile-complete state.

### 27.5 Workout records

Store compact summaries:

- type;
- start/end time;
- duration;
- distance;
- steps;
- pace;
- elevation;
- estimated burn;
- weather snapshot;
- route backup flag;
- updatedAt.

### 27.6 Nutrition records

Store immutable food snapshot values used at the time of logging so provider data changes do not retroactively alter history.

### 27.7 Daily summaries

Document ID is the user's logical local date. Store:

- total calories;
- meal calorie breakdown;
- target snapshot;
- maintenance-calorie snapshot if useful for history;
- macros;
- steps/active minutes;
- workout burn estimate;
- optional weight summary.

### 27.8 Weight & maintenance records

Weight entries stay private under the user scope. A maintenance snapshot is written whenever a relevant profile value causes the TDEE calculation to change.

### 27.9 Public recipes — Phase 2

Only explicitly published recipe data is copied to `publicRecipes`. No route, weight, calorie target, workout or private nutrition history is exposed in public recipe documents.

---

## 28. UI/UX Design

### 28.1 MyFitnessPal-inspired UI direction

The assessed Android application should closely resemble the current MyFitnessPal mobile experience in layout density, information hierarchy and interaction familiarity, while remaining an original MotionFuel implementation.

Required shared characteristics:

- a clean light-first interface with white or near-white surfaces, restrained dividers and a strong blue primary accent;
- a compact top app bar containing the screen title, selected date where relevant and profile/settings access;
- a scrollable card-based **Today** screen with the calorie summary placed above secondary health/activity cards;
- a diary-style **Food** screen with Breakfast, Lunch, Dinner and Snacks as vertically stacked sections, right-aligned calorie totals and a clear `Add Food` action under each meal;
- a prominent quick-add action for Food, Weight and Workout logging;
- a simple bottom navigation bar with clear icons and labels;
- rounded cards, compact spacing, bold numeric values and subdued supporting labels;
- familiar date switching, 7-day/30-day range controls and tap-for-detail chart interactions;
- consistent skeleton, empty, offline and error states that preserve the same layout instead of causing large visual shifts.

The following must remain original to MotionFuel:

- app name, logo, colour tokens and icon assets;
- written copy, illustrations and onboarding content;
- Activity/Google Maps workout screens and AFEE insight presentation;
- exact component dimensions and code implementation.

The goal is strong visual and interaction familiarity, not a pixel-for-pixel reproduction or use of MyFitnessPal proprietary assets.

### 28.2 Design principles

- Material Design 3 components and typography.
- One clear primary action per screen.
- Large live workout metrics readable outdoors.
- Minimal interaction required while moving.
- Colour is supplemental, never the only state indicator.
- Support light/dark mode.
- Content descriptions for icons.
- Minimum touch target sizes consistent with Android accessibility guidance.
- If location tracking cannot safely continue, show a general plain-language message such as **Location temporarily unavailable** without exposing a signal-quality score or persistent quality indicator.

### 28.3 Visual identity

Suggested visual direction:

- familiar nutrition-diary structure with an energetic MotionFuel identity;
- rounded summary cards and compact meal rows inspired by MyFitnessPal's information hierarchy;
- map integrated with a bottom sheet during live workouts;
- motion-themed line/route motifs;
- contextual insight cards with clear evidence chips;
- charts that prioritise trends over decorative complexity.

### 28.4 Loading/error/empty states

Every remote-data screen should have:

- skeleton/progress state;
- retry action;
- cached-data indication when offline;
- useful empty state.

---

## 29. Screen-by-Screen Specification

| Screen | Purpose | Main components | Dynamic behaviour / actions |
|---|---|---|---|
| Splash | Restore app state | Logo, loading state | checks `FirebaseAuth.currentUser`, then profile completion |
| Onboarding | Explain app + privacy | 3–4 concise pages | no permission spam; explains estimates/background workout |
| Login | authenticate existing user | email, password, Forgot Password, Login CTA | Firebase sign-in, validation, loading/error states |
| Sign Up — Account | create account details | name, email, password, confirm password | next step only when valid |
| Sign Up — Profile | gather calorie inputs | age, sex, height, weight, activity level, optional goal | live BMR/TDEE preview |
| Sign Up — Review | confirm calculated values | estimated BMR, maintenance calories, daily goal | final submit creates Firebase account/profile |
| Forgot Password | recover access | email field, Send Reset Link | Firebase password-reset email |
| Verify Email | mandatory verification gate | email, refresh, resend, use-another-account | blocks private Firestore reads and Today until Firebase reports a verified email |
| Today | MyFitnessPal-inspired daily command centre | date header, maintenance calories, Goal − Food + Exercise = Remaining summary, meal progress, steps, weather, last workout, insight, quick-add action | updates from Room/Flow/profile state |
| Food | MyFitnessPal-inspired nutrition diary | date selector, Breakfast/Lunch/Dinner/Snack sections, right-aligned totals, Add Food actions, macro row | instant local recalculation |
| Food Search | database lookup | search, recent foods, results | debounced remote search |
| Add Food | serving log | serving/quantity, meal selector, nutrition preview | live calorie/macro preview |
| Custom Meal | homemade/manual meal | name, macros, calories, image, meal type | macro-derived calorie prefill; local photo storage |
| Activity | workout history + start | recent workouts, Start Run/Walk | Room-backed list |
| Start Workout | readiness | walk/run, weather, location-provider state, permission state | starts location FGS from visible activity |
| Live Workout | real-time run | Google Map, polyline, timer, distance, pace, steps, burn | ~1 Hz UI; route grows; background-safe service |
| Workout Summary | review | Google Map route, metrics, weather, insight evidence | local save + sync status |
| Workout Detail | historical detail and sharing | full Google Map route, distance, moving time, pace, energy, steps, elevation, dominant movement, Share activity image button | fit route bounds; full-route privacy confirmation; attributed 1080 × 1350 share image; no endpoint trimming |
| Progress | MyFitnessPal-inspired long-term view | Overview header, Add Weight, calorie trend bar graph, weight trend bar graph, current maintenance calories | shared range selector for 7/30 days |
| Calorie Trends | calorie bars | Compose Canvas bar graph, target line | tap bar for date/calories/target |
| Weight Trends | weight bars | Compose Canvas bar graph, latest value and period change | tap bar for date/weight/change |
| Maintenance Detail | explain TDEE | age/sex/height/weight/activity factor, BMR, TDEE | recalculation explanation; use as goal action |
| Insights | optional deep view | AFEE cards/evidence | may be nested under Progress |
| Recipes — Phase 2 | social recipes | browse/save/publish | import recipe serving into Food |
| Publish Recipe — Phase 2 | share recipe | title, ingredients, macros, image | validates public fields |
| Profile | identity/goals | age, height, weight, activity, maintenance, calorie goal | edit inputs; recalc maintenance |
| Privacy | route/data controls | route backup, privacy zone, delete account/data | Google Map preview |
| Settings | preferences | units, theme, notifications | DataStore-backed |

### Today/Home calorie card — required behaviour

The Home screen should provide a MyFitnessPal-style calorie summary using the familiar **Goal − Food + Exercise = Remaining** hierarchy without hiding the distinction between maintenance and goal:

```text
Good morning, Alex

Estimated maintenance
2,500 kcal/day

Goal       Food     Exercise     Remaining
2,500   −  1,420   +    240    =    1,320
███████████░░░░░░░░

Breakfast   420 kcal
Lunch       610 kcal
Dinner      390 kcal
Snacks        0 kcal
```

If the goal differs from maintenance:

```text
Estimated maintenance   2,500 kcal/day
Current goal             2,200 kcal/day
```

The UI must never label 2,200 as maintenance if it is actually a deficit/surplus goal.

### Primary Food screen visual requirement

The three meal cards should remain immediately understandable:

```text
Breakfast        420 kcal
██████░░░░       19% of daily goal

Lunch            610 kcal
████████░░       27% of daily goal

Dinner           530 kcal
███████░░░       24% of daily goal
```

The percentages are calculated against the daily calorie target, not independent meal quotas.

---

## 30. Navigation Architecture

### Bottom navigation — 5 destinations

1. **Today**
2. **Activity**
3. **Food**
4. **Progress**
5. **Profile**

This is more realistic than a permanent `Insights` tab because Progress naturally contains weight, calorie trends, maintenance calories and longer-term insights. The highest-priority AFEE insight still appears on Today.

The bar should visually follow MyFitnessPal's compact labelled navigation style. A prominent quick-add button or sheet provides shortcuts for **Food**, **Weight** and **Workout**, while the five MotionFuel destinations remain available for the assessed feature set.

### Primary actions

- `Start Run/Walk` is prominent on Today and Activity.
- `Add Food` is prominent on Food.
- `Add Weight` is prominent on Progress.

### Nested screens

```text
Food → Search / Add Food / Custom Meal / Recipes (Phase 2)
Activity → Start Workout → Live Workout → Summary → Detail
Progress → Calorie Trends / Weight History / Maintenance Detail / Insight Detail
Profile → Goals / Privacy / Settings / Recovery-related account management
```

### Navigation principles

- Live Workout is a focused full-screen destination.
- Bottom navigation is hidden during active workout.
- App start routing depends on Firebase Authentication session + profile completion.
- Navigation survives configuration changes where appropriate.
- Destructive actions require confirmation.

---

## 31. Dynamic UI Behaviour

### Demonstration 1 — Google Maps route reaction

```text
Accepted GPS update
→ route point appended
→ distance/pace recalculated
→ WorkoutUiState changes
→ Google Maps Polyline grows
→ current-position/camera state updates at controlled rate
```

### Demonstration 2 — Breakfast/Lunch/Dinner calorie update

```text
NutritionEntry saved to Room
→ daily aggregation updates
→ meal-specific calories recalculate
→ consumed/remaining calories recalculate
→ Food + Today cards recompose
```

### Demonstration 3 — Custom Meal macro calculation

```text
User enters protein/carbs/fat
→ estimated calorie field prefills using 4/4/9 rule
→ user may override
→ optional image stored locally
→ meal logged immediately
```

### Demonstration 4 — Step counter + activity state

```text
Step counter delta + accelerometer/gyro + GPS window
→ fusion features
→ RUNNING confidence rises
→ live activity label/cadence updates
```

### Demonstration 5 — GPS drift rejection

```text
GPS reports movement
+ no steps
+ inertial state stationary
→ likely drift rejected
→ route polyline does not grow
→ distance stays stable
```

### Demonstration 6 — Background workout

```text
User starts run while app visible
→ location foreground service starts
→ user locks phone / switches app
→ service continues route + timer + checkpoints
→ persistent notification remains
→ reopening UI reconnects to current workout state
```

### Demonstration 7 — Weather context

```text
Weather refresh
→ cached WeatherContext changes
→ AFEE reevaluates
→ insight changes only if materially different
```

### Demonstration 8 — Calorie and weight trend charts

```text
User selects 7 days / 30 days
→ Room queries emit DailySummary and WeightEntry ranges
→ each Canvas independently scales its own bars and units
→ calorie and weight charts redraw
→ tapping a calorie bar shows exact day calories + target
→ tapping a weight bar shows exact day weight + change
```

### Demonstration 9 — Weight + maintenance recalculation

```text
User adds a new weight and marks it current
→ UserProfile.currentWeightKg updates
→ CalculateMaintenanceCaloriesUseCase runs
→ BMR/TDEE recalculated
→ maintenance-calorie snapshot saved
→ Today card updates
→ custom calorie goal remains unchanged unless user chooses to update it
```

### Demonstration 10 — Theme/settings

DataStore changes theme/units immediately without app restart.

---

## 32. Functional Requirements

| ID | Requirement |
|---|---|
| FR-01 | The system shall provide a custom Sign Up screen and create accounts using Firebase Authentication email/password. |
| FR-02 | The system shall provide a custom Login screen and sign users in using Firebase Authentication email/password. |
| FR-03 | The system shall provide password reset by email using Firebase Authentication. |
| FR-04 | The Sign Up flow shall collect name, age, sex, height, weight and activity level in addition to authentication credentials. |
| FR-05 | The system shall calculate BMR using the Mifflin–St Jeor equation. |
| FR-06 | The system shall calculate TDEE by multiplying BMR by the selected activity factor. |
| FR-07 | The system shall store the rounded TDEE as estimated maintenance calories. |
| FR-08 | The Today screen shall display estimated maintenance calories separately from the current daily calorie goal. |
| FR-09 | The initial daily calorie goal shall default to maintenance calories unless the user selects/accepts another goal. |
| FR-10 | The system shall recalculate maintenance calories when relevant profile calculation inputs change. |
| FR-11 | Recalculation shall not silently overwrite a custom calorie goal. |
| FR-12 | The system shall allow users to sign out and restore an authenticated session at app launch. |
| FR-13 | The system shall allow users to search a remote food database and log serving quantity. |
| FR-14 | The system shall assign nutrition entries to Breakfast, Lunch, Dinner or Snack. |
| FR-15 | The system shall calculate meal-level and daily calorie/macronutrient totals. |
| FR-16 | Breakfast, Lunch and Dinner shall display calories and percentage of the current daily calorie goal. |
| FR-17 | The system shall allow a user to create a Custom Meal with macros, calories and optional image. |
| FR-18 | The Progress screen shall provide separate 7-day and 30-day calorie and weight trend bar graphs, with historical calorie-target snapshots and no combined axis. |
| FR-19 | The system shall allow a user to start, pause, resume and finish a walking/running workout. |
| FR-20 | During an active workout the system shall collect validated location updates and display the route using Google Maps. |
| FR-21 | The system shall collect step-counter data when supported by the device. |
| FR-22 | The system shall calculate distance, elapsed time, moving time, current pace, average pace and estimated workout calories. |
| FR-23 | If a target distance is selected and enough pace data exists, the system shall show a clearly labelled estimated finish time. |
| FR-24 | The system shall retrieve current weather context for a workout when network access is available. |
| FR-25 | The system shall continue a user-started active workout through a location foreground service when the UI is backgrounded. |
| FR-26 | The system shall use multiple movement/location signals to reject implausible GPS drift/jumps. |
| FR-27 | The system shall save completed workouts locally before cloud synchronisation. |
| FR-28 | The system shall allow users to add, edit and delete weight entries. |
| FR-29 | The system shall generate explainable AFEE context insights using activity, nutrition, weather and user goals. |
| FR-30 | The system shall synchronise eligible records to the authenticated user's Firestore scope when connectivity is available. |
| FR-31 | Firestore access shall be restricted to the authenticated Firebase UID by Security Rules. |
| FR-32 | The system shall maintain pending sync operations when offline and retry later. |
| FR-33 | The system shall allow the user to configure detailed route cloud backup. |
| FR-34 | The system shall support privacy-zone masking for optional detailed-route cloud backup. |
| FR-35 | Phase 2 shall allow publishing, browsing, saving and importing Social Recipes. |
| FR-36 | The system shall provide clear states when permissions, sensors, GPS, Firebase or external APIs are unavailable. |
| FR-37 | Tapping a saved activity shall open a detailed summary containing its full mapped route and key workout statistics. |
| FR-38 | The activity-detail screen shall provide a single share button that creates a 1080 × 1350 image with the complete Google Maps route and opens Android's system share sheet. |
| FR-39 | The UI shall not display GPS quality, rejected-sample counts or GPS-noise-removal diagnostics. |
| FR-40 | The system shall block dashboard and private Firestore access until the account email is verified and shall provide refresh/resend actions. |
| FR-41 | The system shall reject non-finite GPS inputs and sanitize non-finite sensor/energy-estimation inputs before calculating or persisting metrics. |
| FR-42 | Before sharing, the system shall warn that the complete route includes start and finish locations; cancelling shall produce no share action. |
| FR-43 | Firebase App Check shall use a debug provider in debug builds and Play Integrity in release builds. |

---

## 33. Non-Functional Requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Performance | Core user interactions should provide visible response within 100 ms where no network call is required. |
| NFR-02 | Performance | Sensor-to-derived-workout-state latency should normally remain below 500 ms for metrics that depend on inertial sensors. |
| NFR-03 | Performance | Live workout metric UI should update at approximately 1 Hz without processing raw sensor events on the main thread. |
| NFR-04 | Performance | Today dashboard should display cached/local data within 300 ms on a typical test device after database initialisation. |
| NFR-05 | Reliability | An active workout shall continue recording locally during temporary network loss. |
| NFR-06 | Reliability | A completed workout shall be committed locally before being marked complete in the UI. |
| NFR-07 | Privacy | Raw high-frequency inertial sensor traces shall not be uploaded to cloud storage in the production build. |
| NFR-08 | Privacy | Route cloud backup shall be user-configurable. |
| NFR-09 | Security | All remote application traffic shall use encrypted transport. |
| NFR-10 | Security | Firestore rules shall require the authenticated owner and verified email for private access, validate field allowlists/types/ranges/timestamps, and default-deny undefined paths. |
| NFR-11 | Maintainability | Domain algorithms shall not depend directly on Android UI classes. |
| NFR-12 | Maintainability | Public domain/repository functions shall use descriptive names and documented non-obvious behaviour. |
| NFR-13 | Usability | Starting a workout from the Today screen should require no more than two primary taps after permissions are granted. |
| NFR-14 | Accessibility | Interactive controls shall expose meaningful accessibility labels and avoid colour-only status communication. |
| NFR-15 | Battery | Inertial sensor listeners shall stop within a few seconds after a workout ends or is discarded. |
| NFR-16 | Battery | GPS update policy shall reduce frequency or stop when no active workout requires high-accuracy tracking. |
| NFR-17 | Offline | Previously loaded history and locally stored nutrition/workouts shall remain viewable offline. |
| NFR-18 | Scalability | Cloud queries shall be paginated or date-bounded rather than downloading the user's entire history. |
| NFR-19 | Data integrity | Every syncable mutation shall carry a stable identifier and update timestamp. |
| NFR-20 | Explainability | Every adaptive recommendation shall expose at least one evidence source to the user. |
| NFR-21 | Visual consistency | Today, Food and Progress shall use the documented MyFitnessPal-inspired hierarchy, card density, quick-add pattern and navigation familiarity while retaining original MotionFuel branding and assets. |
| NFR-22 | Security | Firestore rules shall be exercised in CI through the Firebase Emulator against unauthenticated, unverified, cross-user and malformed-write cases. |
| NFR-23 | Privacy | Workout notification details shall use private lock-screen visibility and generated share images shall expire from internal cache. |
| NFR-24 | Supply-chain security | CI dependencies and Firebase test tools shall be pinned and reviewed; release secrets/signing material shall never be committed. |

---

## 34. User Stories & Acceptance Criteria

### US-01 — Create my account

> As a new user, I want to create a MotionFuel account and provide the information required for my maintenance-calorie estimate.

**Given** I enter valid name, email, password, age, sex, height, weight and activity level  
**When** I submit Sign Up  
**Then** Firebase creates my account, MotionFuel calculates my BMR/TDEE, stores my profile and opens the authenticated experience.

### US-02 — Log in and recover my password

> As a returning user, I want to log in securely and recover my password if I forget it.

**Given** I have an existing Firebase account  
**When** I enter valid credentials  
**Then** I reach the application; and if I choose Forgot Password, Firebase can send a reset email.

### US-03 — See my maintenance calories

> As a user, I want to see an estimated maintenance-calorie value based on my profile so that I understand the starting point for my daily calorie goal.

**Given** my age, sex, height, weight and activity level are available  
**When** my profile is created or updated  
**Then** MotionFuel calculates BMR and TDEE and displays estimated maintenance calories on Today with a `How is this calculated?` explanation.

### US-04 — Track meals by time of day

> As a nutrition user, I want Breakfast, Lunch and Dinner separated so that I can understand where my daily calories are coming from.

**Given** I have logged foods  
**When** I open Food  
**Then** each meal card shows its calories and contribution to my daily calorie goal.

### US-05 — Search a real food database

> As a user, I want to search foods rather than manually enter every macro.

**Given** I have internet access  
**When** I type a food query  
**Then** debounced results from the configured food database appear and can be logged with a serving quantity.

### US-06 — Create a custom meal with a picture

> As a user eating homemade food, I want to save my own meal with macros and a photo.

**Given** a provider food is not suitable  
**When** I enter a name/macros and select or capture a photo  
**Then** the meal is saved locally and can be added to Breakfast, Lunch, Dinner or Snack.

### US-07 — See my calorie and weight trends

> As a user, I want familiar weekly/monthly calorie and weight bar graphs so that I can understand my progress over time.

**Given** I have daily nutrition summaries and saved weight entries  
**When** I choose 7 days or 30 days in Progress  
**Then** separate charts display calorie and weight bars using their own scales, the calorie chart shows the relevant daily target reference, and missing weight dates are not represented as zero.

### US-08 — Run with a live Google Map

> As a runner, I want to see my route on Google Maps while I run.

**Given** location permission is granted and a trusted fix exists  
**When** I start a run  
**Then** the Google Map displays my current route using only accepted filtered points.

### US-09 — Keep tracking when the phone is locked

> As a runner, I want tracking to continue when I lock my phone.

**Given** I started the workout while the app was visible  
**When** the app is backgrounded  
**Then** the location foreground service continues timer/location checkpoints and a persistent notification remains visible.

### US-10 — Count steps and burn estimate

> As a user, I want steps and estimated calories burned shown during my workout.

**Given** required sensors/profile data are available  
**When** a workout is active  
**Then** workout steps and estimated burn update without claiming clinical accuracy.

### US-11 — See weather during activity

> As a user, I want weather context so that I can understand conditions during my run.

**Given** network is available  
**When** the workout starts  
**Then** MotionFuel stores and displays a weather snapshot and may refresh it during long sessions.

### US-12 — Reject GPS drift

> As a user, I do not want stationary GPS noise to inflate my distance.

**Given** GPS reports a small movement while step/inertial evidence remains stationary  
**When** the location processor evaluates the point  
**Then** likely drift is rejected and the map/distance do not update from that point.

### US-13 — Track weight

> As a user, I want to record weight over time so that I can see my progress.

**Given** I enter a valid weight  
**When** I save it  
**Then** it appears in weight history and updates the 7-day/30-day weight bar graph and period-change summary.

### US-14 — Recalculate maintenance after progress changes

> As a user, I want my maintenance-calorie estimate to update when my current weight or activity level changes.

**Given** I update a TDEE input such as current weight or activity level  
**When** the profile change is saved  
**Then** MotionFuel recalculates maintenance calories, stores the updated value, displays it on Today and leaves any custom calorie goal unchanged until I choose otherwise.

### US-15 — Continue offline

> As a user, I want the app to remain usable without internet.

**Given** network is unavailable  
**When** I complete a workout, add a custom meal or log weight  
**Then** the data saves locally and cloud sync is queued for later.

### US-16 — Understand recommendations

> As a user, I want to know why an insight appeared.

**Given** AFEE emits an insight  
**When** I open its evidence  
**Then** the contributing activity/nutrition/weather values are shown in plain language.

### US-17 — Protect my cloud-backed route

> As a privacy-conscious user, I want sensitive route endpoints masked.

**Given** I configured a privacy zone  
**When** a route is prepared for optional detailed cloud backup  
**Then** points inside the zone are removed/coarsened according to policy.

### US-20 — Review and share a completed activity

> As a user, I want to open a saved activity and share a visual summary on social media.

**Given** a completed activity has been saved  
**When** I tap its Activity-history card and select **Share activity image**  
**Then** MotionFuel opens Android's share sheet with a 1080 × 1350 image containing the complete mapped route and key statistics.

### US-18 — Recover active UI state

> As a user, I want the workout screen to reconnect after recreation.

**Given** the foreground service still owns an active workout  
**When** the Activity/ViewModel is recreated  
**Then** the UI reconnects to current metrics instead of starting a new workout.

### US-19 — Social Recipe — Phase 2

> As a user, I want to browse a shared recipe and add one serving to my meal log.

**Given** a public recipe is available  
**When** I select `Add to Lunch`  
**Then** MotionFuel creates a private nutrition snapshot for that serving without exposing my private profile to the recipe author.

---

## 35. Security & Privacy

### 35.1 Authentication

- Firebase Authentication is the identity provider.
- Email/password is required for the MVP.
- Password reset uses Firebase reset email.
- Email verification is mandatory before the dashboard or private Firestore reads are available.
- Verify Email offers refresh, resend and use-another-account actions without exposing raw Firebase errors.
- Authentication calls are wrapped by `AuthRepository`.
- UI never stores raw passwords.
- Re-authentication should be considered before destructive account deletion if required by Firebase for a sensitive operation.

### 35.2 Firestore authorisation

Private records are stored under the Firebase UID and protected by Security Rules.

Core ownership rule:

```text
request.auth != null
&& request.auth.uid == userId
&& request.auth.token.email_verified == true
```

The app must not trust a client-supplied owner ID that differs from the authenticated UID. Rules also allowlist fields, validate types and numeric ranges, preserve `createdAt`, and require `updatedAt == request.time` for established profiles. Unknown private subcollections remain denied until their schemas are explicitly defined.

### 35.2.1 Firebase App Check

- Debug builds use `firebase-appcheck-debug`; its generated debug token is registered only in the development Firebase project.
- Release builds use `firebase-appcheck-playintegrity`.
- App Check enforcement is enabled for Authentication/Firestore only after monitoring valid traffic and registering the Play release.
- App Check is defense in depth and never replaces Authentication or Security Rules.

### 35.3 Data minimisation

Collect only what is necessary for enabled features.

- No contacts access.
- No microphone.
- Camera/photo access only when the user explicitly adds a Custom Meal/recipe image.
- Raw inertial sensor traces are processed locally and are not uploaded in the production build.

### 35.4 API keys and secrets

- Do not commit unrestricted production secrets to source control.
- Restrict Google Maps Android keys by application/package/signing certificate where supported.
- Firebase client configuration is not treated as an authorisation secret; Firestore/Storage access must still be protected by rules.
- Truly secret external API credentials should be kept in a trusted backend/Cloud Function.

### 35.5 Local protection

- Use app-private Room storage.
- Do not log Firebase ID tokens, passwords, precise route coordinates, weight history or private meal notes in release logs.
- Store only the minimum authentication/session state required by the Firebase SDK.
- Set `allowBackup=false` and exclude the Room database, WAL/SHM files, `datastore/motionfuel.preferences_pb` and `cache/shared_activities/` in both cloud-backup and device-transfer rules.
- Expire generated share images from internal cache after 24 hours.

### 35.6 Location privacy

Controls:

- route cloud-backup toggle;
- privacy zones;
- route masking for optional cloud backup;
- clear explanation of local vs cloud route data;
- social image sharing occurs only after an explicit button press and includes the complete recorded route;
- a confirmation explicitly warns that the complete start and finish locations will be included;
- MotionFuel never publishes a route automatically.

The no-trimming product decision is retained, but the user must make an informed confirmation each time. Future versions may add an optional endpoint privacy zone without changing the saved local route.

### 35.7 Weight/nutrition privacy

Weight, age, sex used for the equation, calorie goals, maintenance-calorie estimates and private nutrition history remain user-private.

### 35.8 Account/data deletion

Deletion flow should cover:

- local Room records;
- Firestore user document/subcollections;
- Firebase Storage media owned by the user;
- pending sync queue;
- Firebase Authentication account.

### 35.9 Privacy principle

**Process high-frequency sensor data locally; synchronise only useful account-level records.**

---

## 36. Android Permissions & Background Tracking

Permission behaviour must be validated against the target SDK used for the final build.

### 36.1 Manifest/runtime permissions for MVP

#### Internet

```text
android.permission.INTERNET
```

Used for Firebase Authentication, Cloud Firestore, Google Maps tiles/services, nutrition/weather requests and any optional Cloud Function proxy.

#### Fine/coarse location

```text
android.permission.ACCESS_COARSE_LOCATION
android.permission.ACCESS_FINE_LOCATION
```

Fine location is requested in context when the user starts their first outdoor workout. Coarse-only access produces degraded route quality and should be explained.

#### Activity recognition

```text
android.permission.ACTIVITY_RECOGNITION
```

Request only when the app is about to use step/activity functionality on Android versions where the permission is required.

#### Foreground service

```text
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_LOCATION
```

The workout service declares:

```xml
android:foregroundServiceType="location"
```

The service is started from the visible Start Workout flow after location permission is available.

#### Notifications

```text
android.permission.POST_NOTIFICATIONS
```

Request at an appropriate time on Android versions where runtime notification permission applies. The app must still explain that an active foreground service uses an ongoing notification.

### 36.2 Background location decision

`ACCESS_BACKGROUND_LOCATION` is **not part of the default MVP permission set**.

Reason:

- MotionFuel tracks location only after the user explicitly starts a workout;
- the location foreground service is created while the app has a visible Activity and location permission;
- the service then continues the active workout when the UI moves to the background;
- there is no passive all-day location collection.

Add `ACCESS_BACKGROUND_LOCATION` only if a later feature genuinely needs location to begin/continue outside the user-started foreground-service model and after reviewing Android/Play policy implications.

### 36.3 Custom Meal photos

Preferred MVP approach:

- Android Photo Picker for selecting an existing image;
- camera intent for capture if implemented;
- app-private copy of selected/captured image.

Avoid requesting broad `READ_MEDIA_IMAGES` solely for this feature.

### 36.4 Permission UX sequence

Do not request everything at install/first launch.

```text
Sign up/profile → no location permission
Food photo action → photo picker only when requested
Start first workout → explain + request location
Enable step counting → explain + request activity recognition if required
Start workout → start FGS while visible
Notification permission → request in context according to target Android behaviour
```

### 36.5 Denial behaviour

- Location denied: workout can run as timer/manual session but map/distance are unavailable.
- Activity recognition denied: GPS workout continues; step count may be unavailable.
- Notifications denied where allowed: follow platform rules and clearly communicate any impact.
- GPS disabled: show `Location services off` with action to open settings.

---

## 37. Performance & Battery Optimisation

### 37.1 Sensor lifecycle

- register inertial listeners only during active workout;
- use lower rates when high frequency is unnecessary;
- unregister immediately when workout ends;
- avoid keeping sensors active on static dashboard screens.

### 37.2 Adaptive GPS

Possible policy:

- high-accuracy ~1 Hz when moving outdoors;
- lower frequency after sustained stationary state;
- restore higher accuracy when inertial sensors/steps indicate movement.

### 37.3 Batching

- buffer route points and persist in small batches;
- persist critical summary/checkpoint data periodically;
- do not write every accelerometer sample to Room.

### 37.4 Compose optimisation

- expose stable immutable UI state;
- keep large route lists out of unrelated composables;
- use derived state for formatted metrics;
- avoid recomposition from raw sensor streams;
- use keyed lazy lists.

### 37.5 Network

- cache weather and food search results where practical;
- debounce food searches ~300–500 ms;
- avoid frequent cloud writes during live workout;
- batch sync after completion.

### 37.6 Measurable targets

- normal UI input feedback: <100 ms;
- live metric update: ~1 Hz;
- inertial classification latency: <=2.5 seconds including stabilisation window;
- sensor processing work per window: comfortably below window interval;
- local workout checkpoint: every 15–30 seconds plus important lifecycle events;
- location point persistence batch: approximately every 5–10 accepted points;
- dashboard cached render: <300 ms target;
- external-data refresh: non-blocking; cached UI remains interactive;
- active workout battery evaluation target: preferably below ~10–12% battery per hour on the primary test device, reported as an empirical project measurement rather than guaranteed across all hardware.

---

## 38. Error Handling & Edge Cases

| Scenario | Expected behaviour |
|---|---|
| GPS unavailable | Timer/sensors continue; show `Location unavailable`; distance marked unavailable/degraded. |
| Poor GPS accuracy | Do not trust poor samples blindly; show signal quality and avoid false distance accumulation. |
| No internet | Continue workout and local food manual logging; cached weather shown if fresh enough; sync deferred. |
| Accelerometer unavailable | Use steps/GPS; classification confidence reduced. |
| Gyroscope unavailable | Use reduced feature set; no fatal error. |
| Step counter unavailable | Use accelerometer periodicity estimate or omit step metric. |
| Barometer unavailable | Use GPS altitude or omit high-confidence elevation fusion. |
| Cloud/backend failure | Local data remains authoritative; show pending sync state. |
| Nutrition API timeout | Show retry + manual entry + recent/cached foods. |
| User revokes location permission | Pause trusted route metrics; clearly instruct how to restore access. |
| App goes to background | Foreground service continues active workout tracking. |
| Process UI recreated | Rebind to service/repository state. |
| Device kills service unexpectedly | Recover last checkpoint where possible; mark workout as interrupted and let user resume/save. |
| Battery saver enabled | Display potential accuracy warning; use safe lower-power policy if necessary. |
| Duplicate finish action | Idempotent workout finalisation using stable workout ID. |
| Impossible GPS jump | Reject point and record diagnostic reason in debug build. |
| Wearable disconnects | Reweight energy/effort model without heart rate. |
| Nutrition data incomplete | Show only available nutrients; never fabricate missing values. |
| Cloud/local conflict | Resolve with stable IDs/version timestamps; surface unresolved destructive conflict. |
| Weather unavailable | AFEE omits weather-based rules rather than guessing. |
| Timezone change | Daily summaries use an explicit local-date policy and timestamps in UTC. |

---

## 39. Testing Strategy

### 39.1 Pure Kotlin unit tests

- Haversine distance;
- GPS jump/outlier rejection;
- stationary-drift rule;
- pace calculation;
- estimated burn;
- sensor-fusion scoring/hysteresis;
- AFEE rules;
- calorie aggregation by meal;
- 4/4/9 Custom Meal calorie prefill;
- daily target snapshot logic;
- weight trend range aggregation and period-change calculation;
- Mifflin–St Jeor male BMR formula;
- Mifflin–St Jeor female BMR formula;
- all activity-factor TDEE calculations;
- maintenance rounding and goal-separation logic;
- privacy-zone masking.
- non-finite latitude/longitude/altitude/accuracy rejection;
- non-finite sensor feature sanitisation and bounded classifier confidence;
- zero/negative/non-finite energy-estimator input handling;
- manifest, cleartext, backup, `FileProvider`, App Check dependency and secret-literal invariants.

### 39.2 Deterministic sensor/location fixtures

Use prerecorded traces such as:

```text
trace_walk_flat.json
trace_run_flat.json
trace_stationary_gps_drift.json
trace_run_bad_gps_jump.json
trace_background_resume.json
```

The same processor used by production code consumes the fixture stream under virtual/test time.

### 39.3 Firebase Authentication tests

Test with Firebase Authentication (preferably Emulator Suite where practical):

- successful sign-up;
- duplicate email;
- invalid email;
- weak/invalid password according to configured policy;
- successful login;
- wrong password;
- password-reset request;
- sign-out;
- app relaunch with existing session;
- mandatory email-verification gate;
- resend verification;
- refresh after the verification link is completed;
- unverified accounts cannot reach Today or load the private profile.

### 39.4 Firestore Security Rules tests

Use Firebase Emulator Suite to verify:

- an unverified authenticated user can create only its valid initial profile but cannot read it;
- a verified authenticated user can read/update its own valid profile;
- a verified authenticated user can create valid bounded weight entries;
- one user cannot read/write another user's private documents;
- unauthenticated access is denied;
- invalid fields, types, ranges, token-email mismatches and client timestamps are denied;
- undefined subcollections remain denied.

These tests run as a separate GitHub Actions job using pinned Firebase CLI, JavaScript SDK and `@firebase/rules-unit-testing` versions.

Also unit test `CalculateMaintenanceCaloriesUseCase` for male/female formulas and all activity factors.

### 39.5 Food tests

Mock backend/provider responses for:

- successful search;
- no results;
- incomplete macros;
- timeout;
- rate limit;
- malformed response;
- offline Custom Meal fallback.

### 39.6 Google Maps/location tests

- map renders with valid API configuration;
- accepted point appends to polyline;
- rejected point does not append;
- camera follow can be disabled/re-enabled;
- saved workout fit-bounds works;
- no trusted fix shows a clear state instead of a fake route.

### 39.7 Background workout tests

On a physical device:

- start workout while visible;
- lock screen for several minutes;
- verify ongoing notification;
- unlock and verify elapsed time/route/steps continue;
- switch applications and return;
- test process recreation where feasible;
- test Battery Saver behaviour and document observed limitations.

### 39.8 Room/repository tests

- local-first nutrition write;
- local-first workout completion;
- weight write;
- daily aggregation;
- sync queue idempotency;
- retry after network recovery;
- conflict behaviour.

### 39.9 Compose UI tests

- profile required fields;
- suggested calorie confirmation;
- Breakfast/Lunch/Dinner cards;
- custom meal form validation;
- shared 7/30-day range controls for the separate calorie and weight bar graphs;
- calorie bar tap details, target line and historical target values;
- weight bar tap details, unit display and missing-date behaviour;
- Progress maintenance card values and `How is this calculated?` details;
- workout pause/resume/end;
- saved-activity card navigation to Activity Detail;
- activity image sharing waits for the map and sends a PNG through a content URI;
- full-route sharing displays a privacy confirmation and Cancel creates no file/intent;
- activity detail exposes no GPS quality, rejected-sample or noise-removal diagnostics;
- email verification blocks private navigation and provides refresh/resend actions;
- light and dark themes preserve readable contrast on authentication, dashboard, maps, charts and sharing flows;
- permission/error states;
- accessibility semantics.

### 39.10 Real-world test sessions

Perform and document:

- stationary desk GPS test;
- outdoor walk;
- outdoor run;
- backgrounded/locked-screen run;
- poor-signal route section to confirm internal filtering and graceful degraded-location handling;
- `NaN`/infinite location fixture injection to confirm rejection without a crash;
- release App Check/Play Integrity success and rejected unattested-client requests;
- private lock-screen notification content;
- share-cache expiry after the retention window;
- meal logging online/offline;
- at least one synthetic/seeded 30-day history for chart/maintenance demonstration.

---

## 40. MVP Features — Must Have

The assessed MVP should include only the features required to complete the main user loops reliably.

### Authentication/onboarding

- custom Compose Login page;
- custom multi-step Compose Sign Up page;
- Firebase Authentication email/password;
- password reset;
- name, age, sex, height, weight and activity level profile fields;
- Mifflin–St Jeor BMR calculation;
- TDEE maintenance-calorie calculation;
- maintenance calories displayed on Today;
- editable daily calorie goal.

### Food/calories

- food search;
- Breakfast/Lunch/Dinner/Snack logging;
- calorie/macronutrient totals;
- Custom Meal;
- daily goal/remaining UI;
- 7-day/30-day calorie trend bar graph.

### Fitness

- Google Maps live route;
- walk/run tracking;
- foreground-service background tracking;
- GPS filtering;
- steps/cadence where available;
- distance/time/pace;
- estimated burn;
- weather;
- sensor-fusion movement state.

### Progress/insight

- weight entries;
- 7-day/30-day weight trend bar graph;
- maintenance recalculation from current profile inputs;
- AFEE insight cards;
- workout history/detail.

### Architecture/data/privacy

- Clean Architecture + MVVM;
- Room;
- DataStore;
- Retrofit/OkHttp;
- Firebase Authentication;
- Cloud Firestore + Security Rules;
- WorkManager retry;
- route backup control;
- privacy-zone masking;
- clear permission states.

### Explicitly not required for MVP

- follower graph;
- comments/DMs;
- leaderboards;
- complex recipe ranking;
- continuous wearable streaming;
- full meal planning;
- opaque ML calorie prediction;
- maintenance inference from weeks of calorie/weight logs.

---

## 41. Phase 2 Features — Should Have

### 41.1 Social Recipes — primary Phase 2 feature

Keep this deliberately small.

Users can:

- create a recipe with title, ingredients, servings and macros;
- attach one image;
- publish it to a simple public recipe feed;
- browse/search recent recipes;
- save/bookmark a recipe;
- import one serving into Breakfast, Lunch, Dinner or Snack.

Not required initially:

- comments;
- followers;
- direct messages;
- complex moderation tooling;
- leaderboards;
- personalised social ranking.

Basic moderation/safety for a university demo can include report/hide plus server-side field validation.

### 41.2 Other Phase 2 improvements

- Health Connect step/heart-rate history;
- cycling profile;
- barcode lookup;
- notification reminders;
- cloud media backup;
- CSV/JSON export;
- richer weight/maintenance analytics;
- pace/elevation trend charts;
- saved/recent food shortcuts.

---

## 42. Stretch Features — Could Have

### 42.1 Adaptive route suggestion

Suggest a route/target distance before exercise based on weather and desired duration. This requires additional routing APIs and billing/policy review, so it is not needed for route recording.

### 42.2 On-device ML classifier

Compare a lightweight trained classifier with the transparent heuristic fusion engine using the same recorded traces.

### 42.3 Wear OS companion

Show pace, heart rate where available and current workout controls while the phone remains the primary recorder.

### 42.4 Enhanced privacy-preserving workout sharing

Optionally reintroduce user-configurable privacy-zone masking for social images in a future release. The assessed MVP follows the simplified interaction requirement: one share button and the complete recorded route, without exposing weight, calorie goal or private nutrition history.

---

## 43. Project Package Structure

Recommended package root:

```text
com.ronitgandhi.motionfuel
│
├── app
│   ├── MotionFuelApp.kt
│   ├── MainActivity.kt
│   └── navigation
│
├── core
│   ├── auth                 // Firebase Authentication wrapper/session state
│   ├── database             // Room
│   ├── datastore
│   ├── network
│   ├── permissions
│   ├── location             // Fused location adapter
│   ├── sensors
│   ├── maps                 // map UI helpers only
│   ├── files                // custom meal image storage
│   └── ui
│
├── feature
│   ├── auth
│   ├── onboarding
│   ├── profile
│   ├── today
│   ├── workout
│   │   ├── data
│   │   ├── domain
│   │   │   ├── model
│   │   │   ├── algorithm
│   │   │   └── usecase
│   │   └── presentation
│   ├── nutrition
│   ├── progress
│   │   ├── weight
│   │   ├── calories
│   │   └── maintenance
│   ├── insights
│   ├── recipes              // Phase 2
│   └── settings
│
├── data
│   ├── backend              // Firebase-authenticated HTTPS API
│   ├── weather
│   ├── nutritionapi
│   └── sync
│
└── service
    └── workout
        ├── WorkoutForegroundService.kt
        └── WorkoutSessionController.kt
```

### Package rules

- `core/maps` renders route state but does not calculate distance.
- `core/auth` is the only layer directly aware of Firebase Authentication SDK details.
- `feature/progress/maintenance` contains a pure Kotlin estimator.
- provider DTOs never leak into Compose screens.
- the foreground service owns active workout lifecycle, not the Activity.

---

## 44. Development Roadmap

### Phase 1 — Foundation

Project setup, Compose Material 3, navigation, Hilt if selected, Room, DataStore, Firebase project configuration and Google Maps key configuration.

### Phase 2 — Firebase Authentication + TDEE Onboarding

Build Login, Sign Up, Forgot Password, Firebase Auth repository, user profile model, BMR/TDEE use case, Firestore profile persistence and Today maintenance-calorie card.

### Phase 3 — Nutrition Core

Food database client, Breakfast/Lunch/Dinner/Snack entries, daily totals, Custom Meal, Room persistence and 7/30-day calorie chart.

### Phase 4 — Google Maps + Workout Service

Fused location, Maps Compose, foreground service, route model, distance/pace, Room workout storage and history.

### Phase 5 — Sensors + Workout Metrics

Step counter, accelerometer, gyroscope, optional barometer, movement classifier, GPS drift rejection and calorie-burn estimate.

### Phase 6 — Weather + AFEE

Weather integration, caching and 6–8 high-quality explainable AFEE rules.

### Phase 7 — Progress

Weight logging/trend, maintenance recalculation from current profile inputs, maintenance history snapshots and Progress UI.

### Phase 8 — Firebase Sync + Privacy

Firestore sync, Security Rules, WorkManager retry, route-backup toggle, privacy-zone masking and deletion flows.

### Phase 9 — Quality Pass

Accessibility, dark mode, offline/error states, battery profiling, UI tests, emulator tests and physical-device runs.

### Phase 10 — Social Recipes (only after assessed MVP is stable)

Publish/browse/save/import recipe with minimal public schema.

### Phase 11 — Final Demonstration

Seeded test account/data, deterministic GPS/sensor trace, screenshots, architecture diagrams and final assessor walkthrough.

---

## 45. Technical Complexity Analysis

Scores: 1 = low, 5 = high.

| Component | Difficulty | Assessment value | Novelty | Development risk | Priority |
|---|---:|---:|---:|---:|---|
| Multi-sensor activity fusion | 4 | 5 | 4 | 3 | Very high |
| GPS filtering + drift rejection | 4 | 5 | 4 | 3 | Very high |
| AFEE context engine | 4 | 5 | 5 | 3 | Very high |
| Foreground real-time workout pipeline | 4 | 5 | 3 | 4 | Very high |
| Offline-first Room + Firebase-authenticated cloud sync | 4 | 5 | 3 | 4 | High |
| Privacy-zone route masking | 2 | 5 | 4 | 2 | Very high |
| Barometer/GPS elevation fusion | 4 | 4 | 4 | 4 | Medium-high |

### Priority recommendation

The best marks-per-development-hour likely come from:

1. sensor fusion;
2. GPS filtering;
3. AFEE;
4. privacy-zone route masking;
5. offline-first cloud sync.

Barometer fusion is excellent if the test device supports it, but it should not block the project.

---

## 46. 5–8 Minute Demonstration Plan

### Demo preparation

Use:

- a real Android device with Google Play services;
- a Firebase Authentication development/test account;
- seeded 30-day nutrition/weight history;
- cached food/weather examples;
- debug-only route/sensor trace fallback.

### 0:00–0:50 — Sign-up/profile value

1. Open custom Sign Up.
2. Show email/password plus age, sex, height, weight and activity level fields.
3. Enter a test profile.
4. Show BMR/TDEE preview and explain the activity multiplier.
5. Complete Firebase account creation.
6. Land on Today showing **Estimated maintenance calories** and the starting daily calorie goal.

### 0:50–1:45 — Food dashboard

Open Food:

- Breakfast/Lunch/Dinner calorie cards;
- daily limit/consumed/remaining;
- search and log one FDC food;
- add a Custom Meal with macros/photo preview if time permits.

### 1:45–3:30 — Live Google Maps workout

Start a run/walk:

- Google Map visible;
- route polyline grows;
- timer/distance/pace/steps/burn update;
- weather visible.

Show one drift/jump rejection using a debug trace or controlled test.

### 3:30–4:00 — Background tracking

Background/lock the app briefly, show ongoing notification, then return and show the workout continued.

### 4:00–4:40 — Workout summary + AFEE

End workout and show:

- route map;
- steps/burn/weather;
- one contextual insight;
- `Why am I seeing this?` evidence.

### 4:40–5:40 — Progress

Open Progress:

- separate 7-day/30-day calorie and weight trend bar graphs;
- tap details for individual calorie and weight bars;
- current estimated maintenance calories;
- `How is this calculated?` showing BMR, activity factor and TDEE;
- a new current-weight update triggering immediate maintenance recalculation.

### 5:40–6:20 — Offline/local-first

Explain or demonstrate:

- Room saves first;
- Custom Meal/weight/workout remain local offline;
- sync queue becomes pending.

### 6:20–7:00 — Cloud/security/privacy

Restore network or show prepared evidence:

- WorkManager sync;
- backend verifies Firebase Authentication identity;
- Firestore user scope;
- route backup toggle/privacy-zone mask.

### 7:00–7:30 — Engineering proof

Show:

- pure Kotlin GPS/fusion/maintenance algorithms;
- deterministic tests;
- package separation.

### 7:30–8:00 — Close

Summarise the integration:

```text
Google Maps + sensors + steps + weather
+
Food database + meal calories + weight trend
+
Firebase Authentication + local-first/cloud architecture
=
context-aware fitness and nutrition companion
```

---

## 47. Assessment Criteria Mapping

| Assessment Criterion | App Feature | Technical Implementation | Evidence During Demo |
|---|---|---|---|
| Readable software | feature-oriented architecture | Clean Architecture, MVVM, repository boundaries | package structure + pure Kotlin algorithm |
| Multiple sensors | run tracking | accelerometer, gyroscope, step counter, GPS, optional barometer | live workout readiness/state |
| Advanced sensing | movement confidence | sliding windows, feature extraction, hysteresis | walking/running transition |
| Localisation | reliable run route | fused location, Haversine, outlier/drift rejection | map route + rejected fake jump |
| Mapping | live/saved routes | Google Maps SDK + Maps Compose, Polyline, markers/camera | live run map |
| Background mobile systems | workout continuity | location foreground service, persistent notification | lock/background test |
| Internet data | food/weather/maps/auth | backend→FDC, weather API, Google Maps, Firebase Authentication | food search + weather + map + auth |
| Authentication/security | verified identity | Firebase Authentication + Firestore Security Rules | signed-in state + cross-user access denial test |
| Local storage | offline-first app | Room + DataStore | airplane/offline flow |
| Cloud computing | synchronisation | WorkManager + Cloud Firestore; Cloud Function only for secret-key proxy/cleanup | pending→synced record |
| Data analytics | calorie/weight progress | daily summaries, two Canvas bar graphs, period change | 7/30-day Progress screen |
| Applied algorithm | maintenance calories | Mifflin–St Jeor BMR + activity-factor TDEE calculation | signup calculation + maintenance detail screen |
| Advanced algorithm | AFEE | rule/score engine across domains | insight + `Why` evidence |
| Privacy | route + health-like data | local processing, route masking, public/private recipe separation | privacy settings |
| HCI/UX | realistic daily workflow | Breakfast/Lunch/Dinner cards, outdoor-readable workout UI | main walkthrough |
| Novelty | integrated context | activity + nutrition + weather + personal progress | same dashboard/insight uses multiple domains |
| Reliability | deterministic testability | trace player + repository/API mocks | tests/debug trace |
| Interdisciplinary knowledge | exercise/nutrition/GIS/mobile systems | MET estimates, calorie aggregation, GIS filtering, contextual algorithms | summary/explanation |

---

## 48. Risks and Mitigation

| Risk | Impact | Mitigation |
|---|---|---|
| Firebase account created but profile write fails | High | use `PROFILE_INCOMPLETE` state, local pending profile data and retry/repair flow |
| Google Maps API key misconfiguration | High | enable SDK early; restrict key; test debug/release signing configs |
| Maps billing/quota surprise | Medium | use only Maps SDK needed for display; no Directions/Places in MVP; set cloud budget alerts |
| GPS poor indoors | High | physical outdoor test + deterministic debug trace; internally reject unreliable points and show only a general location-unavailable message when tracking cannot continue |
| Android foreground-service restrictions | High | start location FGS from visible Activity after location permission; test target SDK behaviour |
| User denies notifications/location | Medium | graceful degraded state and contextual permission rationale |
| Step counter missing | Medium | omit step metric or use clearly-labelled accelerometer fallback |
| FoodData Central coverage differs from local packaged foods | Medium | Custom Meal/manual fallback; provider abstraction allows replacement/addition |
| Provider API key exposed | High | nutrition requests go through backend proxy |
| Custom meal images inflate storage | Medium | downscale/compress, app-private storage, delete orphaned files |
| Calorie chart misleading after goal changes | Medium | daily target snapshots; do not rewrite history |
| Maintenance calories become stale after weight/activity changes | Medium | recalculate TDEE whenever calculation inputs change; keep maintenance separate from custom calorie goal |
| Background battery drain | High | ~1 Hz GPS target, stop sensors immediately after workout, empirical battery profiling |
| Raw sensor data grows storage/cloud | High | process in memory, persist only derived/debug data where needed |
| Social recipe scope explodes | Medium | Phase 2 only; no comments/followers/ranking required |
| Cloud unavailable during demo | Medium | local-first demo path, seeded/cached data, screenshots/test evidence |
| Sensitive progress data accidentally public | High | public recipe schema is physically separate from private user collections |
| Health interpretation sounds medical | High | estimated/neutral language, explainability, no diagnosis/treatment claims |

---

## 49. Future Enhancements

- Health Connect import for heart-rate/step history;
- cycling-specific tracking and energy estimation;
- barcode scanning with an appropriate food provider;
- improved Australian/local branded-food coverage through an additional provider;
- cloud backup for custom meal/recipe images;
- richer Social Recipes with moderation and search;
- route difficulty score;
- personal pace-zone adaptation;
- barometric elevation calibration;
- Wear OS companion;
- on-device ML activity classifier;
- exportable progress reports;
- route recommendation using a routing API only after core recording is stable;
- notification reminders for meals, weight logging or planned workouts;
- advanced maintenance estimation using longer windows and uncertainty intervals.

---

## 50. Final Recommended Scope

The realistic assessed application is not a full Strava/MyFitnessPal clone. It is a focused product with four complete loops:

1. **Create a Firebase account + calculate maintenance calories**
2. **Log meals + understand daily calories**
3. **Track a run on Google Maps, including background execution**
4. **Review progress and recalculate maintenance when profile inputs change**

### Implement deeply

- custom Login and Sign Up Compose screens;
- Firebase Authentication email/password + password reset;
- age, sex, weight, height and activity-level onboarding;
- Mifflin–St Jeor BMR calculation;
- TDEE maintenance-calorie calculation;
- maintenance calories displayed on Today;
- separate editable daily calorie goal;
- Breakfast/Lunch/Dinner calorie UI;
- FoodData Central search;
- Custom Meal with macros and optional image;
- separate 7-day/30-day calorie and weight trend bar graphs;
- Google Maps live/saved run route;
- foreground-service background tracking;
- step counter;
- elapsed/moving time, pace, distance and optional predicted finish time;
- estimated burn;
- weather;
- accelerometer + gyro + step/GPS fusion;
- GPS outlier/drift rejection;
- weight tracking;
- maintenance recalculation after current-weight/activity-profile changes;
- AFEE explainable insights;
- Room + DataStore;
- WorkManager + Firebase Auth/Firestore sync;
- Firestore Security Rules;
- privacy route masking;
- polished MyFitnessPal-inspired Compose UI with original MotionFuel branding.

### Include in the PRD but build after MVP stability

- Social Recipes: publish, browse, save, import serving;
- Health Connect;
- barcode lookup;
- recipe/cloud image backup;
- cycling.

### Do not spend assessed MVP time on

- follower graph;
- comments/DMs;
- leaderboards;
- complex recipe recommendation engine;
- full meal plans;
- continuous wearable streaming;
- generative AI chatbot;
- expensive cloud ML;
- multi-week maintenance inference from intake/weight history;
- Google Directions/Places unless a later feature genuinely needs them.

This boundary is achievable, coherent and still demonstrates authentication, sensors, localisation, mapping, Android background execution, networking, cloud security, local persistence, analytics, privacy and algorithmic reasoning.

---

# Recommended Version I Should Actually Build

## Build: **MotionFuel — Firebase + Google Maps Run Tracker + Practical Nutrition Companion**

### 1. Firebase Login and Sign Up

Build custom Compose screens rather than using a prebuilt authentication UI.

**Login**

- email;
- password;
- Login button;
- Forgot Password;
- link to Sign Up.

**Sign Up — account step**

- name;
- email;
- password;
- confirm password.

**Sign Up — personal details step**

- age;
- sex used for Mifflin–St Jeor equation;
- height in cm;
- weight in kg;
- activity level;
- optional goal.

Use Firebase Authentication for account creation/login/reset and Cloud Firestore for the completed profile.

### 2. Maintenance calories immediately after Sign Up

Use:

```text
Male BMR   = 10W + 6.25H - 5A + 5
Female BMR = 10W + 6.25H - 5A - 161

TDEE = BMR × activity factor
```

Activity factors:

```text
Sedentary       1.2
Light           1.375
Moderate        1.55
Very Active     1.725
Extremely Active 1.9
```

The result becomes `maintenanceCaloriesKcal`.

### 3. Today screen similar to a practical calorie tracker

Show:

```text
Estimated maintenance   2,500 kcal/day
Daily goal              2,500 kcal
Food                    1,560 kcal
Remaining                 940 kcal

Breakfast                 420 kcal
Lunch                     610 kcal
Dinner                    530 kcal
Snacks                      0 kcal
```

Maintenance and goal must remain separate fields because a future weight-loss/gain goal can differ from TDEE.

### 4. Food screen

Implement:

- USDA FDC search;
- serving quantity;
- Breakfast/Lunch/Dinner/Snack assignment;
- daily calories/protein/carbs/fat;
- recent foods;
- Custom Meal;
- 7-day/30-day calorie trend bar graph.

### 5. Custom Meal

Fields:

- name;
- protein;
- carbohydrates;
- fat;
- calories;
- meal type;
- optional photo;
- notes.

Keep photo local in the MVP unless cloud media becomes necessary.

### 6. Google Maps run tracker

Display:

- filtered live route;
- current position;
- start marker;
- timer;
- optional predicted finish time for target distance;
- distance;
- current/average pace;
- steps;
- estimated burn;
- weather;

Route calculations remain independent of the Google Map component.

### 7. Background workout service

Start a `location` foreground service from the visible Start Workout screen. Continue when the phone is locked or another app is opened, with a persistent notification.

### 8. Sensor depth

Use:

- GPS;
- accelerometer;
- gyroscope;
- step counter;
- optional barometer.

Use a simple fusion classifier for stationary/walking/running and reject likely stationary GPS drift.

### 9. Progress and maintenance recalculation

Show:

- Add Weight;
- weight history/trend;
- separate 7-day/30-day calorie and weight trend bar graphs;
- latest weight and selected-period change summary;
- current maintenance calories;
- current daily calorie goal;
- AFEE insight.

When a user updates current weight or activity level:

```text
profile update
→ recalculate BMR
→ recalculate TDEE
→ update maintenance calories
→ store maintenance snapshot
→ refresh Today
```

Do not infer maintenance from calorie logs in the MVP.

### 10. Firebase data architecture

```text
Firebase Authentication
        ↓ UID
Compose/ViewModels
        ↓
Room local source of truth
        ↓
SyncQueue / WorkManager
        ↓
Cloud Firestore
        ↓
Security Rules: request.auth.uid == userId
```

A Cloud Function is only needed for a secret external API key or trusted cleanup—not for ordinary authentication.

### 11. Social Recipes — Phase 2

After the assessed build is stable:

- publish recipe;
- browse feed;
- save recipe;
- add one serving to Breakfast/Lunch/Dinner/Snack.

Avoid comments/followers/complex ranking until everything above works.

---

# Definition of Done for the Assessed Build

- [ ] Today, Food and Progress follow the documented MyFitnessPal-inspired visual hierarchy, card density, meal-section layout and quick-add interaction pattern.
- [ ] MotionFuel uses original branding, colours, icons and copy rather than proprietary MyFitnessPal assets.
- [ ] Custom Compose Login page works.
- [ ] Custom multi-step Compose Sign Up page works.
- [ ] Firebase email/password account creation works on a physical Android device.
- [ ] Firebase login, sign-out and session restoration work.
- [ ] Unverified accounts remain on Verify Email and cannot read private Firestore data or open Today.
- [ ] Verify Email refresh and resend actions work.
- [ ] Forgot Password sends a Firebase reset email.
- [ ] Sign Up collects age, sex, height, weight and activity level.
- [ ] Mifflin–St Jeor BMR is unit tested for male and female formula paths.
- [ ] All five activity factors are unit tested.
- [ ] TDEE is calculated and rounded into estimated maintenance calories.
- [ ] Today displays estimated maintenance calories.
- [ ] Today displays a separate daily calorie goal, consumed calories and remaining calories.
- [ ] Editing current weight/activity level recalculates maintenance calories.
- [ ] Recalculation does not silently overwrite a custom daily calorie goal.
- [ ] Firestore profile is stored under the Firebase UID.
- [ ] Firestore Security Rules prevent cross-user private-data access.
- [ ] Firestore Emulator tests pass for unauthenticated, unverified, cross-user, schema and timestamp cases.
- [ ] App Check uses debug attestation for debug builds and Play Integrity for release builds; production enforcement is enabled after registration.
- [ ] Breakfast, Lunch and Dinner show their own calorie totals.
- [ ] Food search returns real remote database results.
- [ ] A Custom Meal can be saved with carbs/fat/protein and an optional picture.
- [ ] Nutrition totals remain available offline after being saved.
- [ ] Progress shows separate working 7-day and 30-day calorie and weight trend bar graphs.
- [ ] Calorie bars show historical target references and weight bars never treat missing measurements as zero.
- [ ] A walk/run can start, pause, resume and finish reliably.
- [ ] Google Maps displays the live accepted route polyline.
- [ ] Tapping a saved activity opens its detailed full-route summary.
- [ ] The activity-detail screen shows one Share activity image button and no endpoint-trimming control or explanatory size text.
- [ ] Sharing produces a 1080 × 1350 PNG containing the attributed Google basemap, complete route and key workout statistics.
- [ ] Full-route sharing shows an explicit start/finish-location warning before opening the system share sheet.
- [ ] No GPS quality, rejected-sample count or GPS-noise-removal diagnostic is visible to the user.
- [ ] Workout steps update on a supported device or the missing-sensor state is handled cleanly.
- [ ] Estimated workout burn and elapsed time are displayed.
- [ ] If a target distance is selected, a clearly labelled approximate finish time is displayed after enough pace data exists.
- [ ] Weather is displayed or a clean offline/unavailable state is shown.
- [ ] Active workout continues when the phone is locked/backgrounded.
- [ ] A foreground-service notification is visible during background tracking.
- [ ] At least one stationary GPS drift/jump case is rejected.
- [ ] Non-finite GPS and sensor inputs cannot enter route metrics or produce non-finite confidence/calorie values.
- [ ] Completed workout saves to Room before Firestore sync.
- [ ] Users can add/edit/delete weight entries.
- [ ] AFEE exposes evidence for its insights.
- [ ] WorkManager can synchronise a pending record when connectivity returns.
- [ ] Route backup can be disabled and privacy-zone masking works.
- [ ] No production secret is committed to the repository.
- [ ] Maps key is restricted to the Android package, release signing certificate and required Maps SDK APIs.
- [ ] Room, DataStore and share cache are excluded from cloud backup/device transfer, and stale share images expire.
- [ ] Core permission-denied/offline/GPS-unavailable states are tested.
- [ ] The assessor demo can be completed with deterministic backup location/sensor data if live GPS is unreliable.

---

# Final Product Statement

**MotionFuel is a realistic Android fitness and nutrition application that uses custom Firebase-backed Login and Sign Up screens, calculates Mifflin–St Jeor BMR and TDEE maintenance calories from the user's profile, displays maintenance and daily calorie progress on the homepage, tracks meals and macros, records runs on Google Maps in the foreground/background, incorporates steps/sensor data and weather, and stores progress securely using a local-first Room + Firebase architecture.**

The assessed MVP is technically deep without depending on an unnecessary authentication provider or an overly complex maintenance-calorie inference model.

---

# Implementation Reference Notes — August 2026

- Firebase Authentication is the sole account identity system in this PRD.
- Cloud Firestore user-private collections use the authenticated Firebase UID as their owner key.
- BMR/TDEE calculation is pure Kotlin domain logic and does not depend on Firebase.
- Google Maps is presentation only; route filtering/distance calculations remain domain logic.
- Android background workout recording is implemented with a user-started location foreground service.
- External API secrets that must remain confidential belong in a trusted backend/Cloud Function rather than in the APK.
