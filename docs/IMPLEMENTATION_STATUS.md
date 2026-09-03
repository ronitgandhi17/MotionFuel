# PRD implementation status

| Area | Status | Evidence |
| --- | --- | --- |
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
| Full workout/nutrition Firestore sync | Pending | Local `syncState` fields are present; WorkManager queue is next |
| Google Maps basemap | Implemented; restricted key required | Maps Compose live/saved routes, dark map style and attributed share snapshot |
| Input robustness | Implemented | Non-finite GPS rejection, sensor sanitisation and non-negative calorie estimation |
| Share privacy | Implemented | Full-route confirmation, narrow FileProvider path, private cache and 24-hour expiry |
| Backup/lock-screen privacy | Implemented | Room/DataStore exclusions, cache-only shares and private foreground notification visibility |
