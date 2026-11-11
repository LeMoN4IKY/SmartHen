# SmartHen Quail migration

## Working scope

Native Android/iOS application in `multiplatform/`, preserving the legacy Android application.
Backend v1 is developed separately in `backend/`; the legacy server and its database are not migrated automatically.

## Architecture

Compose screens render immutable state and dispatch user actions. ViewModels own observable state and cancellation. Repositories handle API and cache access. Pure domain code evaluates measurement freshness and command eligibility. Platform adapters provide HTTP engines and storage.

Comments explain contracts and failure behaviour rather than restating syntax. Prefer small interfaces at actual boundaries; avoid speculative abstractions.

## Verification so far

- Android debug APK built.
- Kotlin shared module compiled for iOS simulator arm64 (not an installed iPhone application yet).
- Common tests passed on Android host: data validation, scoped cache, HTTP errors, cancellation, timeouts and monitor lifecycle.
- No release or deployment has been performed.

## Real device contract

The server accepts authenticated device telemetry and retains device measurement time separately from server receive time. Device commands require capability checks and explicit acknowledgement. An accepted request is not a successful physical operation.

A separate simulator is for development only. No synthetic readings are seeded into production startup. Hardware wiring, firmware, sensor calibration, and end-to-end verification remain necessary before live use.

## Remaining integration

Implemented foundation: authenticated API, platform secure session stores, account/profile, owned houses and manual device claim. Remaining: QR scanning, full account recovery/editing, analytics; events; camera provider; payment provider; complete iOS host project and simulator UI verification. Device-specific automation must operate safely without phone or server connectivity.
