# Next milestone — account-scoped sync and maps

The current build is a reliable algorithm-first vertical slice. Complete the assessed MVP in this order so cloud configuration cannot destabilise workout tracking.

## 1. Account-scoped cloud data

1. Select the cloud database used for cross-device workout and nutrition sync.
2. Verify every API request with the same Clerk JWT already used by the membership server.
3. Scope all records to Clerk's stable user ID and deny cross-user reads and writes server-side.
4. Store only the Clerk user ID in cloud paths; keep profile measurements private.
5. Add onboarding/profile state to DataStore and retain the existing Clerk-authenticated app shell.

## 2. Cloud sync queue

1. Add a Room `sync_queue` table containing entity type, local ID, operation, attempt count and last error.
2. Insert the domain record and queue record in one Room transaction.
3. Add a unique WorkManager job constrained to network availability.
4. Upload workout summaries and nutrition snapshots through the Clerk-verified API first.
5. Upload route chunks only when `routeBackupEnabled` is true, after privacy masking.
6. Mark local rows synced only after the cloud API confirms success.
7. Use last-write-wins only for editable scalar profile/settings fields; workouts remain append-only.

The UI must continue to observe Room, never the cloud database directly.

## 3. Map provider

Replace the current exact custom route canvas with Google Maps Compose or Mapbox only after its API key is injected from a local secrets file. Keep `RouteCanvas` as the deterministic/offline fallback used by tests and the assessor demo.

## 4. Verification gates

- Backend integration tests prove cross-user reads/writes are denied.
- A completed workout is visible after airplane-mode restart.
- Re-enabling the network drains the queue exactly once.
- Turning route backup off prevents all detailed route uploads.
- A privacy-zone trace never exposes an endpoint in exported/cloud coordinates.
- Release builds contain no assessor-demo entry point.
