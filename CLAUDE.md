# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

EasyQR — a single-screen Android QR code scanner/generator written in Java (no Kotlin). The app was renamed from "ScanQr" but the identifiers were not: the Gradle root project is still `ScanQr`, the namespace/applicationId is `com.fc.scanqr`, and the theme is `Theme.ScanQr`. Only `app_name` in `strings.xml` says EasyQR. Don't "fix" this inconsistency unless asked — changing `applicationId` breaks upgrades for installed users.

## Commands

```bash
./gradlew assembleDebug              # build debug APK -> app/build/outputs/apk/debug/
./gradlew installDebug               # build + install on connected device/emulator
./gradlew testDebugUnitTest          # JVM unit tests
./gradlew connectedDebugAndroidTest  # instrumented tests (needs a device)
./gradlew lint                       # Android Lint -> app/build/reports/lint-results-debug.html
./gradlew clean

# single test / single test method
./gradlew testDebugUnitTest --tests "com.fc.scanqr.ExampleUnitTest"
./gradlew testDebugUnitTest --tests "com.fc.scanqr.ExampleUnitTest.addition_isCorrect"
```

Only the generated template tests exist; there is no real test coverage.

Dependencies are declared in `gradle/libs.versions.toml` (version catalog) and referenced as `libs.*` — add new libraries there, not inline in `app/build.gradle.kts`.

## Architecture

`MainActivity` (~550 lines) is effectively the entire app. One screen, one `EditText` (`qrContentEditText`) that is both the scan output and the generate input, plus Scan / Make / Clear / Copy buttons and a gallery FAB.

**Scanning** does not use `zxing-android-embedded` (declared as `libs.android.integration`, but unused — no `CaptureActivity`, no `IntentIntegrator`). Instead:
- CameraX `ImageAnalysis` (`STRATEGY_KEEP_ONLY_LATEST`) on a single-thread executor feeds `analyzeImage()`.
- The image's Y plane is wrapped directly in a ZXing `PlanarYUVLuminanceSource` and decoded by a shared `MultiFormatReader` (hints: QR_CODE only, TRY_HARDER).
- Gallery import (`scanQRFromImage`) reaches the same decoder but hand-builds a luminance array from ARGB pixels.
- Decoded text is **appended** to whatever is already in the EditText, then scanning stops. Typing in the EditText also stops scanning.
- Camera lifecycle is manual: `bindToLifecycle` on start, `unbindAll` on stop; `previewView` visibility toggles against a static overlay + hint text.

**Generation** (`generateQRCode`): content over **400 UTF-8 bytes** is split by `splitContent()` into multiple chunks, each encoded as its own 461×461 QR code (UTF-8, error correction M, margin 2). The chunks carry no sequencing header — reassembly relies on the user scanning them in order. Results are shown in an `AlertDialog` hosting a `ViewPager2` + page indicator (`dialog_qr_display.xml` + top-level `QRPagerAdapter`); Save writes each bitmap into `MediaStore` `Pictures/`.

**Dead code — do not extend it:** `QRDisplayActivity` (plus `activity_qr_display.xml`, and its own nested duplicate of `QRPagerAdapter`) is not in `AndroidManifest.xml` and is never started. The live path is the dialog described above. `item_qr_display.xml` is shared by both.

`FC-SDK/` is an empty `java-library` stub (`MyClass` has no members). `include(":FC-SDK")` has been removed from `settings.gradle.kts` in the working tree, so it is not part of the build.

## Conventions to match

- `findViewById` everywhere; `viewBinding = true` is enabled in `app/build.gradle.kts` but nothing uses it.
- Every user-facing string is in `values/strings.xml` with a Chinese counterpart in `values-zh/strings.xml` — add both when adding a string. (Note the zh `app_name` is "一直扫", not a translation of EasyQR.)
- Theme is Material3 DayNight with `values-night/` overrides, but dialog text colors are computed imperatively in `showQRDialog()` from `UI_MODE_NIGHT_MASK`, and the status bar color is hardcoded in `onCreate`.
- Errors surface as `Toast` with a string resource; exceptions are swallowed (no logging framework in use).
- Permissions: `REQUIRED_PERMISSIONS` is built at class-init from `Build.VERSION.SDK_INT` (CAMERA always, plus WRITE_EXTERNAL_STORAGE only on API ≤ 28); saving uses MediaStore `RELATIVE_PATH` on API 29+.
- `minSdk 23`, `compileSdk 35`, `targetSdk 34`, Java 11 source/target. Builds fine on the JDK 18 currently on PATH with Gradle 8.10.2 / AGP 8.8.0.
