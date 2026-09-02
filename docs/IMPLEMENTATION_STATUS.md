# PRD implementation status

| Area | Status | Evidence |
| --- | --- | --- |
| Firebase account flow | Implemented; project configuration required | Login, signup, password reset, verification email and session listener |
| TDEE onboarding | Implemented | Multi-step profile fields and pure Mifflin–St Jeor use case |
| Firestore profile | Implemented | `users/{uid}` write/read and UID security rules |
| MyFitnessPal-inspired UI | Implemented foundation | Today equation card, meal diary, quick actions and Progress cards |
| Calorie trends | Implemented locally | 7/30-day Compose Canvas bar graph |
| Weight trends | Implemented locally | Room table/migration, Add Weight and 7/30-day bar graph |
| GPS indicator removal | Implemented | No user-facing GPS status badge; filtering remains internal |
| Workout service and sensors | Retained | Foreground GPS, accelerometer, gyroscope and step counter |
| Food/weather APIs | Retained | Online search/context with cached examples/fallbacks |
| Full workout/nutrition Firestore sync | Pending | Local `syncState` fields are present; WorkManager queue is next |
| Google Maps basemap | Pending | Deterministic route Canvas remains the current map fallback |
