# SmartHen Quail — Kotlin Multiplatform

Open this `multiplatform` directory in Android Studio. The original XML application remains in the parent directory and is not changed by this build.

## Build

Use JDK 25 (Android Studio's bundled runtime on the development machine), Android SDK 37 and the included Gradle wrapper:

```sh
./gradlew :androidApp:assembleDebug :shared:testAndroidHostTest
./gradlew :shared:compileKotlinIosSimulatorArm64
```

The Android APK is produced at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
The iOS compilation checks shared Kotlin code. `iosApp/SmartHenApp.swift` is the host entry point; the runnable Xcode host and simulator verification are still in progress.

## Current integration

The profile, houses and monitoring screens use the new authenticated `/api/v1` contract. Sign in, create/select a house, and claim a provisioned controller. Monitoring is paused while signed out. The legacy adapter remains for tests/migration only. Real readings require provisioned hardware or the explicitly launched simulator.

Account transport requires HTTPS by default and rejects redirects. Session tokens use Android Keystore encryption and iOS device-only Keychain storage. Passwords are not persisted. The old unauthenticated server is intended only for a private development network.

## Behaviour

- RU/EN, light/dark/system theme.
- Cached telemetry stays scoped to its source and is labelled as saved.
- Measurement timestamps determine freshness, not HTTP response time.
- Polling stops when the app leaves the foreground; retries do not swallow cancellation.
- No camera recording, payment success, or hardware command is simulated as a real completed operation.

See `docs/STATUS.md` and `docs/BACKEND-GAPS.md` for current limitations. Tests cover transport failures, cache isolation, session changes and monitoring lifecycle. Compilation does not replace physical-device testing.
