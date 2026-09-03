# PRD implementation status

| Area | Status | Evidence |
| --- | --- | --- |
| Firebase account flow | Implemented; project configuration required | Login, signup, password reset, mandatory verification gate, resend/refresh and session listener |
| Firebase App Check | Implemented in client; console enforcement required | Debug provider by debug variant and Play Integrity provider by release variant |
| TDEE onboarding | Implemented | Multi-step profile fields and pure Mifflin–St Jeor use case |
| Firestore profile | Implemented | `users/{uid}` write/read, verified-owner/schema rules and Emulator CI tests |
| MyFitnessPal-inspired UI | Implemented foundation | Today equation card, meal diary, quick actions and Progress cards |
| Calorie trends | Implemented locally | 7/30-day Compose Canvas bar graph |
| Weight trends | Implemented locally | Room table/migration, Add Weight and 7/30-day bar graph |
| GPS indicator removal | Implemented | No user-facing GPS status badge; filtering remains internal |
| Workout service and sensors | Retained | Foreground GPS, accelerometer, gyroscope and step counter |
| Food/weather APIs | Retained | Online search/context with cached examples/fallbacks |
| Full workout/nutrition Firestore sync | Pending | Local `syncState` fields are present; WorkManager queue is next |
| Google Maps basemap | Implemented; restricted key required | Maps Compose live/saved routes, dark map style and attributed share snapshot |
| Input robustness | Implemented | Non-finite GPS rejection, sensor sanitisation and non-negative calorie estimation |
| Share privacy | Implemented | Full-route confirmation, narrow FileProvider path, private cache and 24-hour expiry |
| Backup/lock-screen privacy | Implemented | Room/DataStore exclusions, cache-only shares and private foreground notification visibility |
