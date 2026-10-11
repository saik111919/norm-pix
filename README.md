# NormPix

NormPix is an offline-first, private personal finance and card manager for Android built natively with **Kotlin**, **Jetpack Compose**, and **Material 3**.

## Architecture & Features

- **Jetpack Compose & Material 3**: Fluid UI following the express design system.
- **Offline & Zero Permissions**: No `INTERNET`, `ACCESS_NETWORK_STATE`, SMS, or notification listener permissions.
- **Privacy & Security**: SQLite database for financial data, AES-256-GCM encrypted secure card vault, and `FLAG_SECURE` window protection.
- **OCR Scan**: On-device credit card and document number scanning with Google ML Kit.

## Building and Testing

The Android project is located in the [`native/`](native/) directory:

```bash
cd native

# Run all unit and Robolectric Compose UI tests
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lintDebug

# Build release APK (R8 minified and resource-shrunk)
./gradlew assembleRelease
```
