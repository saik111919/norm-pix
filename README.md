# NormPix

NormPix is an offline-first, private personal finance and card manager for Android built natively with **Kotlin**, **Jetpack Compose**, and **Material 3**.

## Architecture & Features

- **Jetpack Compose & Material 3**: Fluid UI following the express design system.
- **Private by design**: Everything stays on the phone. The one connection the app makes is to GitHub, to check for and download its own updates; [`network_security_config.xml`](native/app/src/main/res/xml/network_security_config.xml) refuses every other host, and ML Kit's telemetry upload is removed. No SMS or notification listener permissions; `ACCESS_NETWORK_STATE` is there only so the daily update check in the background can wait for a network (Wi-Fi, to download).
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
- **Every push to `main`** (or a manual run on `main` from the Actions tab): the same checks, then a **release** the app updates to, at the next free version: `versionName`, or the patch after the highest `vX.Y.Z` tag of its `X.Y` if that is as high (with `versionName` 1.0.2 and `v1.0.8` tagged, the next is `v1.0.9`). Raise `versionName` (to `1.1.0`, say) to start a new line. To publish `main`'s builds as test builds instead (**prereleases**, tagged `v<versionName>-build.<run number>`, which the app never offers as an update), set `MAIN_BUILDS: prerelease` at the top of the workflow.
- **A version tag** (`v1.1.0`, matching `versionName`): the same checks, then that version as a **release** (see below).

### Releases and in-app updates

The app checks GitHub for a newer release once a day, in the background and at launch (Settings → Automatic updates, on by default; turn it off and the app only checks when asked), or when asked: Settings → Check for updates (or Check now under Automatic updates) opens the update page, which asks GitHub and shows where updating stands. What happens to an update found in the background is the user's choice there: **Tell me** (a notification), **Download it, then ask** (the default: downloaded in the background, then a notification says it's ready to install), or **Install it automatically** (downloaded and installed without asking once the app has been closed for a while, on Android 12 and later; a notification says what was installed, and if Android wants a confirmation it waits for the user's Install instead). Background downloads wait for Wi-Fi unless **Download on mobile data** is on. The daily run is a JobScheduler job (`UpdateJobService`), kept over a restart, and not run while the battery is low.

In the app, **Update now** downloads the APK and checks its size, SHA-256, package name, version and signing key; the page (or sheet) then says it's ready, and only **Install** hands it to Android's installer, which asks the user to confirm. While it downloads, a notification shows how far it is, with **Cancel**; a download the user started runs in a foreground service (`UpdateDownloadService`, `FOREGROUND_SERVICE_DATA_SYNC`), so it carries on with the app closed, and once it's done the notification says it's ready to install, with **Install**. An "Update available" notification has **Download**, which downloads it the same way without opening the app. Every download, in the app or in the background, gets the same checks, and the app is never uninstalled. Only release builds update themselves: debug and qa builds carry another key, so no release could install over them.

Every push to `main` publishes one on its own. To publish a version of your choosing, with notes of your own:

1. Raise `versionName` in [`native/app/build.gradle.kts`](native/app/build.gradle.kts). `versionCode` is made from it (`1.0.3` → `10003`), so a newer version always has the higher code; the app compares releases by that number.
2. Commit, then tag that commit `v<versionName>` and push the tag:

   ```bash
   git tag -a v1.0.3 -m "What changed in this release"
   git push origin v1.0.3
   ```

CI fails a tag that doesn't match `versionName`, a release that isn't signed with the release key, and a `versionCode` that isn't above the latest release's. It publishes the APK, its checksum and **`update.json`**, the release's description the app reads (version, APK name, size, SHA-256, minimum Android version, notes), and marks the release latest. The notes are the annotated tag's message, or else the commits since the previous release. A line `mandatory: true` in the tag's message makes the app offer the release at every launch, even after Later; it is still never forced.

**Where the app looks.** The app asks GitHub without signing in and carries no token, so the releases it reads have to be **public**. It looks in the repository it was built for: `NORMPIX_UPDATE_REPOSITORY` (Gradle property `normpixUpdateRepository`), which CI sets from the `UPDATE_REPOSITORY` repository variable and otherwise to this repository. While this repository is private, either:

- make it public, or
- create a public repository for releases only (with at least one commit, e.g. a README), set the repository variable `UPDATE_REPOSITORY` to it (`owner/name`), and add a secret `RELEASES_TOKEN`: a fine-grained token with **Contents: read and write** on that repository only. CI then publishes releases (and prereleases) there, and the app built by that run looks there.

### Signing key

Android installs an update only if it is signed with the same key as the app already on the phone. Every release is signed with one fixed key, the **release key**: the key the installed NormPix has always carried (certificate SHA-256 `96:B3:0A:AE:…:5A:34:88`; its alias and passwords are kept with the keystore, not here). CI reads it from these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The keystore file, base64-encoded: `base64 -w0 release.jks` (macOS: `base64 -i release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | The keystore password |
| `RELEASE_KEY_ALIAS` | The key's alias |
| `RELEASE_KEY_PASSWORD` | The key's password |

The release job checks the APK's certificate against the release key's fingerprint (`RELEASE_CERT_SHA256` in the workflow) and publishes nothing signed with any other key. Without the secrets a prerelease is still published, signed with the debug key (it won't install over the app), and a release fails.

A release built on your own machine is signed with the same key when Gradle knows where it is, from `~/.gradle/gradle.properties`:

```properties
normpixKeystoreFile=C:/Users/<you>/.android/normpix-release.jks
normpixKeystorePassword=<keystore password>
normpixKeyAlias=<key alias>
normpixKeyPassword=<key password>
```

(or the environment variables `NORMPIX_KEYSTORE_FILE`, `NORMPIX_KEYSTORE_PASSWORD`, `NORMPIX_KEY_ALIAS`, `NORMPIX_KEY_PASSWORD`, which CI uses). Keep a backup of that keystore: without it no later APK can update the app.

**Debug and qa builds** are signed with a key of their own, `native/app/debug.keystore` (in the repository; alias `androiddebugkey`, passwords `android`), so every machine signs them alike and they can never install over the real app. A debug build installed before this key existed has to be uninstalled once.
