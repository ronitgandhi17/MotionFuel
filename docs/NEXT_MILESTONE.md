# Next milestone — a licensed basemap

Account-scoped cloud sync landed in this build (see the two sections below, now marked **Done**). The one remaining assessed-MVP milestone is replacing the offline route canvas with a licensed map provider. Sync configuration is gated exactly like Clerk and Stripe, so an unconfigured build still runs fully offline on Room.

## 1. Account-scoped cloud data — Done

1. ✅ Firestore is the cloud database, reached **only** through the Clerk-verified backend gateway using the Firebase Admin SDK. The Android app never links the Firebase client SDK.
2. ✅ Every `/sync` request carries the same Clerk JWT the membership server already verifies (`Clerk.session.fetchToken()` → `Authorization: Bearer`).
3. ✅ Records are scoped to `users/{clerkUserId}/…`; ownership is derived from the verified token and any client-supplied owner is ignored. `firestore.rules` denies all direct client access (NFR-10).
4. ✅ Only the Clerk user ID appears in cloud paths; profile measurements stay off the cloud.
5. Onboarding/profile state in DataStore remains a later milestone; the Clerk-authenticated app shell is retained.

## 2. Cloud sync queue — Done

1. ✅ Both entities already carry a `syncState` column; new rows are written `PENDING` and flipped `SYNCED` only after the backend confirms the write.
2. ✅ The domain record is written to Room first, then a network-constrained WorkManager job is enqueued.
3. ✅ `SyncScheduler` enqueues a unique `OneTimeWorkRequest` constrained to `NetworkType.CONNECTED` with exponential backoff.
4. ✅ `SyncWorker` pulls remote records (upserting them as `SYNCED`), then pushes pending workout summaries and nutrition snapshots through the Clerk-verified API.
5. ✅ The detailed route is uploaded only when `routeBackupEnabled` is on (off by default); otherwise an empty route is sent, so no coordinates leave the device.
6. ✅ Local rows are marked `SYNCED` only after the API confirms success.
7. Last-write-wins for editable scalar profile/settings fields is deferred with the onboarding milestone; workouts remain append-only.

The UI continues to observe Room, never the cloud database directly. `PrivacyZoneMasker` still powers the on-device masking preview; the route-backup toggle is the single control that governs whether real coordinates are ever uploaded to the user's own private, client-denied scope.

## 3. Map provider — remaining

Replace the current exact custom route canvas with Google Maps Compose or Mapbox only after its API key is injected from a local secrets file. Keep `RouteCanvas` as the deterministic/offline fallback used by tests.

## 4. Verification gates

- ✅ The backend derives ownership from the verified Clerk user ID, so one user cannot read or write another's scope; `firestore.rules` denies all direct client access.
- A completed workout is visible after an airplane-mode restart (Room is the source of truth).
- Re-enabling the network drains the queue exactly once (unique work + `markSynced` after confirmation).
- Turning route backup off prevents all detailed route uploads.
- Cloud coordinates exist only for the user's own opted-in backup, inside a scope no client can read; the masking preview never exports endpoints.
- No build contains an in-app demo/assessor entry point — the synthetic trace player has been removed entirely.
