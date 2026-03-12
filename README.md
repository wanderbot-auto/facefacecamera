# FaceFaceCamera

Android selfie camera prototype focused on front-camera face distortion filters.

## Current MVP

- Kotlin + Jetpack Compose + CameraX app scaffold
- Front-camera preview and capture flow
- ML Kit face detection pipeline
- 6 preset face filters with stylized preview overlays
- Capture feedback with shutter tone and short flash
- Processed image save to `Pictures/FaceFaceCamera`
- Share intent from the latest saved capture

## Structure

- `app/src/main/java/com/facefacecamera/camera`: CameraX binding and capture coordinator
- `app/src/main/java/com/facefacecamera/facefx`: filter presets, face tracking, still renderer
- `app/src/main/java/com/facefacecamera/feature/capture`: Compose capture screen and state
- `app/src/main/java/com/facefacecamera/media`: bitmap decode, save, and share utilities

## Notes

- The live preview currently uses face-aware visual overlays and filter selection state.
- Still captures are post-processed with a simple band-based bitmap deformation renderer.
- The renderer interface is isolated so the current implementation can be replaced with a GPU mesh pipeline later.

## Local Setup

This environment did not have `Java`, `Gradle`, or `ANDROID_HOME`, so the project could not be built here.

To run locally:

1. Install JDK 17+
2. Install Android Studio / Android SDK with API 35
3. Add `gradle/wrapper/gradle-wrapper.jar` by running `gradle wrapper`
4. Open the project in Android Studio and sync
5. Run on an Android 11+ device with a front camera

## One-Click macOS Run

Use the helper script to boot an Android emulator, install the debug app, and launch it:

```zsh
./scripts/start_macos_vm_and_app.sh
```

What it does:

- Ensures required Android SDK packages are installed
- Creates an AVD named `FaceFaceCamera_API_35` on first run
- Starts the emulator and waits until Android finishes booting
- Runs `./gradlew installDebug`
- Launches `com.facefacecamera/.MainActivity`

Useful flags:

```zsh
./scripts/start_macos_vm_and_app.sh --cold-boot
./scripts/start_macos_vm_and_app.sh --skip-build
./scripts/start_macos_vm_and_app.sh --avd-name MyAvd --device-id pixel_8_pro
```
