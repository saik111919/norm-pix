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

Android installs an update only if it is signed with the same key as the app already on the phone. Every release is signed with one fixed key, the **release key**: the key the installed NormPix has always carried (it began as this machine's `~/.android/debug.keystore`; alias `androiddebugkey`, both passwords `android`; certificate SHA-256 `96:B3:0A:AE:…:5A:34:88`). CI reads it from these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The keystore file, base64-encoded: `base64 -w0 release.jks` (macOS: `base64 -i release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | The keystore password |
| `RELEASE_KEY_ALIAS` | The key's alias |
| `RELEASE_KEY_PASSWORD` | The key's password |

The release job checks the APK's certificate against the release key's fingerprint (`RELEASE_CERT_SHA256` in the workflow) and publishes nothing signed with any other key. Without the secrets the workflow still publishes, but each APK is signed with the debug key and won't install over the app.

A release built on your own machine is signed with the same key when Gradle knows where it is, from `~/.gradle/gradle.properties`:

```properties
normpixKeystoreFile=C:/Users/<you>/.android/normpix-release.jks
normpixKeystorePassword=android
normpixKeyAlias=androiddebugkey
normpixKeyPassword=android
```

(or the environment variables `NORMPIX_KEYSTORE_FILE`, `NORMPIX_KEYSTORE_PASSWORD`, `NORMPIX_KEY_ALIAS`, `NORMPIX_KEY_PASSWORD`, which CI uses). Keep a backup of that keystore: without it no later APK can update the app.

**Debug and qa builds** are signed with a key of their own, `native/app/debug.keystore` (in the repository; alias `androiddebugkey`, passwords `android`), so every machine signs them alike and they can never install over the real app. A debug build installed before this key existed has to be uninstalled once.
