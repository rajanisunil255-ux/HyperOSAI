# HyperOS AI — Android starter

Native Kotlin + Jetpack Compose starter for a Redmi Note 14 5G / HyperOS assistant.

## Included
- Material 3 Compose UI with Liquid Glass-inspired translucent cards
- Functional Pure AMOLED toggle and six accent palettes
- Editable HyperOS version field, persisted locally
- Gemini API key field, persisted using AndroidX Security Crypto
- Gemini REST chat integration (model name configurable in `GeminiClient.kt`)
- Shizuku permission/status integration and an explicit, allowlisted command runner
- Safe tweak guidance and command confirmation dialog

## Build
1. Open this folder in Android Studio (recent stable version).
2. Use JDK 17.
3. Let Gradle sync and install on an Android device.
4. Install Shizuku separately from its official project and start it using the method supported by your Android version.
5. In the app, grant Shizuku permission, then use only the included allowlisted read-only commands at first.

## Gemini setup
Create an API key in Google AI Studio. Paste it in Settings. This starter calls the Gemini REST API directly from the device. This is convenient for a personal prototype, but a distributed app should use a backend proxy to protect keys and apply quotas. Do not commit your key to source control.

## Shizuku / ADB safety
This project intentionally allows only a small set of read-only diagnostic commands (`getprop`, `wm size`, `wm density`, `settings get`). It does not run arbitrary shell input. Some commands can still be unavailable depending on Android/HyperOS, Shizuku mode, or permissions. Do not add unrestricted shell execution without designing a robust permission and command review model.

Shizuku is an optional external app; this project does not bundle it. A user must explicitly start Shizuku and grant permission. This starter does not silently change system settings.

## Knowledge accuracy
The app includes general starter guidance, not a live or complete HyperOS knowledge database. Version-specific information should be added from verified documentation and tested device observations. The selected HyperOS version is passed to Gemini as context; it does not automatically guarantee that Gemini knows every build-specific detail.

## Important
- This is a starter project, not a signed APK.
- A local encrypted preference store reduces casual exposure but is not a substitute for a backend secret-management strategy.
- Test on a spare device/profile before introducing write-capable ADB operations.

## Build verification

A GitHub Actions workflow is included at `.github/workflows/android-build.yml`. On push, pull request, or manual dispatch it installs JDK 17, Android SDK 35, and Gradle 8.11.1, then runs:

```bash
gradle --no-daemon assembleDebug lintDebug testDebugUnitTest
```

This project archive was updated with the workflow, but the build was **not executed in this workspace** because a configured Android SDK/Gradle installation is not available here. Treat the first CI run or a local Android Studio build as the actual compile verification.

## Shizuku write actions (carefully limited)

The Tools screen now refreshes Shizuku status when the permission result arrives, when the Shizuku binder connects/disconnects, and when the app returns to the foreground. The app still does not accept arbitrary shell commands from the user or Gemini.

Only two write actions are included:

- **Set animation scales to 0.5×** — writes only `animator_duration_scale`, `transition_animation_scale`, and `window_animation_scale` under the global settings namespace.
- **Rollback last animation change** — restores the exact values captured before the most recent successful 0.5× change.

Both require an explicit confirmation dialog. Before writing, the app reads and validates all three current values and persists the rollback values. If applying the new values fails partway through, it attempts to restore any values already changed and reports any rollback errors. A failed or interrupted rollback can still leave a partial change, so inspect the output and use Developer options to restore values manually if necessary. The rollback snapshot is stored locally and can be lost if app data is cleared or the app is uninstalled.

These changes are reversible but still affect system-wide animation behavior. They do not improve raw CPU performance, and some HyperOS builds may restrict or ignore writes. Back up important data and review the confirmation text before proceeding. No debloating, package disabling, security changes, or unrestricted shell execution is provided.
