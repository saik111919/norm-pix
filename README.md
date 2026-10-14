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

## CI/CD and releases

[`.github/workflows/android.yml`](.github/workflows/android.yml) runs on GitHub Actions:

- **Pull requests into `main`**: unit and Robolectric UI tests, Android Lint, and a release build. The APK is attached to the run as the `apk` artifact.
- **Every push to `main`** (or a manual run on `main` from the Actions tab): the same checks, then a GitHub release tagged `v<versionName>-build.<run number>` with the APK and its SHA-256 checksum.

### Signing key

Android installs an update only if it is signed with the same key as the app already on the phone. Add these repository secrets (Settings → Secrets and variables → Actions) so every release is signed with one fixed key:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The keystore file, base64-encoded: `base64 -w0 release.jks` (macOS: `base64 -i release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | The keystore password |
| `RELEASE_KEY_ALIAS` | The key's alias |
| `RELEASE_KEY_PASSWORD` | The key's password |

Release builds made on your own machine are signed with its debug key (`~/.android/debug.keystore`; alias `androiddebugkey`, both passwords `android`). Using that keystore for the secrets lets the CI releases install over the app you already have, without losing its data. To use a new key instead, create one with `keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias normpix`; the first install signed with it needs the old app uninstalled.

Without the secrets the workflow still publishes, but each APK is signed with a throwaway key and won't install over an earlier one.

The same variables sign a local release build: set `NORMPIX_KEYSTORE_FILE` (path to the keystore), `NORMPIX_KEYSTORE_PASSWORD`, `NORMPIX_KEY_ALIAS` and `NORMPIX_KEY_PASSWORD` before `./gradlew assembleRelease`.
