# PRD implementation status

Direct Wear OS pairing and command messaging are intentionally not part of the application. Optional external health records are accessed through Health Connect only.

| Area | Status | Evidence |
| --- | --- | --- |
| Adaptive daily fuel | Implemented | Guardrailed pure-Kotlin engine combines exercise energy, steps, weather and context with visible reasons |
| Health Connect | Implemented; compatible provider and permission required | Optional read-only import, install/update/settings recovery UI, unsupported-emulator explanation and manual fallback |
| Barcode food scan | Implemented; Google Play Services required | Permissionless scanner UI and Open Food Facts barcode endpoint |
| Workout splits/records | Implemented | Kilometre split calculation, route elevation delta, distance/duration/pace/step records and streaks |
| Hydration | Implemented locally | Room v5 table, Today quick-add, adaptive target and widget publication |
| Recovery readiness | Implemented | Bounded score, explainable factors, Health Connect inputs and editable manual fallbacks |
| Meal planning | Implemented locally | Tomorrow plan table, saved-food planning, removal and copy-to-today |
| Goals/achievements | Implemented | Editable active-day, step and water targets with streaks and milestone labels |
| Android widget | Implemented | Private-state calories/steps/water RemoteViews widget |
| Export/account deletion | Implemented | Complete local JSON export; confirmed deletion with recent-login guard and owned-cloud cleanup |
| Weekly report | Implemented | Seven-day consistency/workout/nutrition/weight summary and Android text sharing |
| Smart meals and label OCR | Implemented | Explainable macro ranking and ML Kit on-device text recognition with temporary private captures |
| Recipe builder | Implemented locally | Room v6 recipe persistence, ingredient aggregation and serving scaling |
| Workout/route planning | Implemented locally | One-time reminders, completion state, bounded loop generation and offline Canvas preview |
| Exercise zones/prediction | Implemented | Sanitised pace zones, age-estimated HR ranges and confidence-labelled weight projections |
| Safety sharing | Implemented; verified accounts required | One-hour Firestore token, owner-only live updates, receiver deep link and stop action |
| Challenges/leaderboards | Implemented; verified accounts required | Seven-day challenge documents and own-member-only score writes |
| Celebrations/reminders | Implemented | Existing secure social-card pipeline and opt-in WorkManager notifications |
| Data source/accessibility/localisation | Implemented | Source disclosure, 118% font mode and English/Hindi per-app locale preference |
| Firebase account flow | Implemented; project configuration required | Login, signup, password reset, mandatory verification gate, resend/refresh and session listener |
| Firebase App Check | Implemented in client; console enforcement required | Debug provider by debug variant and Play Integrity provider by release variant |
| TDEE onboarding | Implemented | Multi-step profile fields and pure Mifflin–St Jeor use case |
| Firestore profile | Implemented | `users/{uid}` write/read, verified-owner/schema rules and Emulator CI tests |
| MyFitnessPal-inspired UI | Implemented foundation | Today equation card, meal diary, quick actions and Progress cards |
| Calorie trends | Implemented locally | Compose Canvas bar graph with an independent Day/Week/Month selector |
| Weight trends | Implemented locally | Room table/migration, Add Weight and an independent Day/Week/Month selector |
| Saved food photos | Implemented locally | Camera/Photo Picker choice, private FileProvider camera URI, Room saved-food table and reusable thumbnail cards |
| Saved food detail/share | Implemented | Tappable detail page, nutrition breakdown, add-to-meal and Android image/text share sheet |
| Saved food swipe actions | Implemented | Right swipe opens Add to today meal selection; left swipe opens deletion confirmation |
| Swipe navigation | Implemented | Horizontal pager synchronised with all five bottom-navigation destinations |
| GPS indicator removal | Implemented | No user-facing GPS status badge; filtering remains internal |
| Workout service and sensors | Retained | Foreground GPS, accelerometer, gyroscope and step counter |
| Food/weather APIs | Retained and surfaced | Online search/context with cached fallbacks; weather displayed during live tracking |
| Full workout/nutrition Firestore sync | Pending | Local `syncState` fields are present; WorkManager queue remains future scope |
| Google Maps basemap | Implemented; restricted key required | Maps Compose live/saved routes, dark map style and attributed share snapshot |
| Input robustness | Implemented | Non-finite GPS rejection, sensor sanitisation and non-negative calorie estimation |
| Share privacy | Implemented | Full-route confirmation, narrow FileProvider path, private cache and 24-hour expiry |
| Backup/lock-screen privacy | Implemented | Room/DataStore exclusions, cache-only shares and private foreground notification visibility |
