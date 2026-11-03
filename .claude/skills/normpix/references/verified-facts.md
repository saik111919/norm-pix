# Verified facts ledger

Durable, version-specific facts that cost time to find. Look here **before** researching (`research-protocol.md`), and add a line
when you verify something new. Each entry says how sure we are:

- **RE-CHECKED** - confirmed in this form on the date shown, against the source named.
- **NOTES** - recorded by an earlier session (project notes); believed correct, not re-run since. Re-check before building on it
  if the version in question has moved.

Format: `claim - applies to - source - status/date`.

## Libraries and build

- material3 **1.4.0** contains the Material 3 Expressive *token* files (`LoadingIndicatorTokens`, `SplitButton{XSmall..XLarge}Tokens`,
  `LinearProgressIndicatorTokens`) but **not** the composables `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`,
  `LoadingIndicator`, `SplitButtonLayout` or `MaterialShapes`. - Compose BOM 2026.09.00 pins 1.4.0 - sources jar
  `~/.gradle/caches/modules-2/files-2.1/androidx.compose.material3/material3-android/1.4.0/.../material3-android-1.4.0-sources.jar`
  (no `fun LoadingIndicator` / `fun SplitButtonLayout` in any `.kt`) - **RE-CHECKED 2026-10-26**. The app builds its own
  (`Progress.kt`, `SplitButton.kt`).
- `androidx.graphics:graphics-shapes` 1.1.0 is declared explicitly in `libs.versions.toml` (the loader's morph; it was already on
  the runtime classpath) - build files - **RE-CHECKED 2026-10-26**.
- R8 removes the no-argument constructors ML Kit builds its components with by reflection (`NoSuchMethodException ...Registrar.<init>`),
  so text recognition never starts in a release build; a keep rule in `proguard-rules.pro` fixes it - R8 with ML Kit
  text-recognition 16.0.1 - NOTES (2026-10), rule present in `proguard-rules.pro`.
- The ML Kit telemetry transport finds its backend through the `backend:com.google.android.datatransport.cct.CctBackendFactory`
  meta-data of the library manifest; removing that entry from the merged manifest leaves it nowhere to upload -
  `DropTelemetryBackend` in `app/build.gradle.kts` - **RE-CHECKED 2026-10-26** (task present).
- AGP does not treat the ABI filter as an input of the package step: an `assembleRelease` can say UP-TO-DATE and still ship
  `lib/x86_64/` left over from a `-PemulatorAbi` build. Rebuild with `:app:stripReleaseDebugSymbols :app:packageRelease --rerun`
  and check `unzip -l app-release.apk | grep '\.so'` - AGP 9.1.0 - NOTES (2026-10-14).
- A "Failed to tokenize ...invokeSuspend" Compose mapping warning is caused by a suspend function whose composable lambda captures a
  local `Boolean`; computing the Boolean inside the lambda fixes it - Compose compiler with Kotlin 2.4.0 - NOTES (2026-10-22).
- Kotlin incremental compilation fails with "different roots" when the project (D:) and Gradle's caches (C:) are on different
  Windows drives, hence `kotlin.incremental=false` - `native/gradle.properties` - **RE-CHECKED 2026-10-26** (property present).
- Robolectric's Conscrypt, registered first, cannot verify the JDK's JCE signing certificate on newer JDKs and breaks plain `Cipher`
  use after a Robolectric test; `conscryptMode=OFF` in `src/test/resources/robolectric.properties` - Robolectric 4.17 -
  **RE-CHECKED 2026-10-26** (property present).
- Under Robolectric, a `LaunchedEffect` coroutine resumes on whatever thread finished its `withContext`; with the app's dedicated
  `normpix-*-db` threads this can stall Home on "Loading your month". Tests that build their own container use
  `Dispatchers.Unconfined` for the repositories - Robolectric 4.17 + Compose ui-test - NOTES (2026-10-16).

## Android platform

- `Window.setBackgroundBlurRadius` does nothing on Compose `Dialog` / `ModalBottomSheet` windows (their theme is not translucent);
  `FLAG_BLUR_BEHIND` + `blurBehindRadius` works but each change is a request to the system server applied frames later, so the blur
  trailed the sheet. The app therefore blurs the page itself with a `RenderEffect` driven by the sheet's position - API 31+ -
  NOTES (2026-10-08, Phases 21-23).
- Apps follow the system's blur switch with `WindowManager.isCrossWindowBlurEnabled()` + `addCrossWindowBlurEnabledListener`
  (API 31+). It goes false for battery saver, the developer option "Allow window-level blurs" off
  (`settings put global disable_window_blurs 1`) and devices without blur. Android 16 QPR2's Accessibility > "Reduce blur effects" has
  no public API; the window manager appears to read the same `disable_window_blurs` flag (inferred from services.jar on an API 37
  emulator, not toggled on a device) - NOTES (2026-10-08).
- Android refuses to install a lower `versionCode` over a higher one without uninstalling (which wipes app data); same package +
  same signing key + higher `versionCode` keeps data. A SQLite downgrade throws unless `onDowngrade` is overridden - Android 8+ -
  NOTES (design analysis for the preview channel, 2026-10-25).
- `UPDATE_PACKAGES_WITHOUT_USER_ACTION` lets an app update itself without a confirmation on Android 12+; otherwise the installer asks.
  The app never silently uninstalls - Android 12+ - manifest comment + README, NOTES.
- A tag or phone that shows a new random 4-byte ID each tap (ID starting `0x08`) cannot be recognised again, so NFC tag registration
  refuses it - NfcA/NfcTagRegistration - NOTES (Phase 20).
- Reading a bank card over NFC: NormPix AID, then the public PPSE directory, then NDEF; never GPO / READ RECORD (those would read
  card data) - `NfcTagReader.kt` - NOTES (Phase 14).
- `am start -a SEND` with a MediaStore URI fails (MediaProvider permission); share a picture to the qa app by opening it in Google
  Photos with `-p com.google.android.apps.photos -f 0x10008000` and using its Share sheet - Android 14-17 devices - NOTES (2026-10-13).

## Encryption at rest (whole-app encryption assessment, 2026-11-03; nothing built)

- SQLCipher community edition: `net.zetetic:sqlcipher-android:4.20.0@aar` + `androidx.sqlite:sqlite:2.7.0`, Android API 23 and up, ABIs
  armeabi-v7a/x86/x86_64/arm64-v8a, `System.loadLibrary("sqlcipher")` before any database call, classes in `net.zetetic.database.sqlcipher`,
  `SupportOpenHelperFactory` for androidx.sqlite/Room. The AAR is 4,050,842 bytes (all four ABIs) - github.com/sqlcipher/sqlcipher-android
  README + repo1.maven.org `net/zetetic/sqlcipher-android/4.20.0/` (latest = release = 4.20.0) - **RE-CHECKED 2026-11-03**. Not stated
  anywhere checked: the 16 KB page-size alignment, the per-ABI size, the licence text. The zetetic.net Android page describes the
  *commercial* edition (that one needs `PRAGMA cipher_license`).
- A plaintext SQLite file is encrypted with `ATTACH DATABASE 'enc.db' AS encrypted KEY '...'; SELECT sqlcipher_export('encrypted');
  DETACH DATABASE encrypted;`. `PRAGMA rekey` cannot encrypt a plain database; `sqlcipher_export` does not copy `user_version` or
  `auto_vacuum` (set them on the target). Defaults: PBKDF2_HMAC_SHA512, 256,000 iterations, page size 4096; `cipher_memory_security` is
  off by default - zetetic.net/sqlcipher/sqlcipher-api - **RE-CHECKED 2026-11-03**.
- `androidx.security:security-crypto` (EncryptedSharedPreferences, EncryptedFile, MasterKey): every API is deprecated since 1.1.0-alpha07 /
  1.1.0 (2025) "in favour of existing platform APIs and direct use of Android Keystore". Don't add it; use Keystore directly, as
  `CardVault` does - developer.android.com/jetpack/androidx/releases/security - **RE-CHECKED 2026-11-03**.
- Keystore: key material never enters the app process, but "if the app's process is compromised, the attacker might be able to use the
  app's keys". User-auth-bound keys work for a set time or for each operation (`BiometricPrompt` + CryptoObject). A biometric-only key is
  invalidated by a new biometric enrolment unless `setInvalidatedByBiometricEnrollment(false)` - developer.android.com/privacy-and-security/keystore
  - **RE-CHECKED 2026-11-03**. NormPix's vault/NFC keys are *not* auth-bound: the app lock (`AppLockService`) is session state in memory.
- File-based encryption is required on every device that launches with Android 10 or later. Credential-encrypted storage (the default,
  where `filesDir`/databases live) opens only after the user unlocks the device - source.android.com/docs/security/features/encryption/file-based
  - **RE-CHECKED 2026-11-03**.

## OCR and receipts

- ML Kit returns **scrambled line order** and reads the rupee sign as `7`, `Z` or nothing; per-symbol confidence for a misread rupee
  is ~0.07-0.12 versus 0.6-0.95 for real digits, which `rupeeRestored` uses (threshold 0.25 and below half the rest). - ML Kit
  text-recognition 16.0.1 on the owner's phone - NOTES (2026-10-13).
- Google Pay names its shared image `<id> - 30.00 To <payee> on Google Pay.png` (amount, payee, direction) and
  `OpenableColumns.DISPLAY_NAME` returns it - Google Pay Android share - NOTES (2026-10-13).
- UPI receipts show the receiving bank's KYC name, which for a self transfer is the user's own; no app prints a self-transfer-specific
  receipt, so the self-transfer fixtures are synthetic - NOTES (2026-10-25).
- On the owner's phone (motorola edge 50 neo, Android 16, Lawnchair) the launcher re-sends a widget's last RemoteViews when it comes back to the front, which restarts the dial's seconds-ring animation at the second that picture was drawn for: ring :31 at true :55 with a 15 s inexact tick; `SCHEDULE_EXACT_ALARM` is denied by default there, `USE_EXACT_ALARM` gives `exactAllowReason=policy_permission` in `dumpsys alarm`; within ~1 s of the clock after the change - measured by screenshot against `date` (2026-10-31).
- A `<vector>` drawable with no `<path>` does not inflate ("no path defined", `VectorDrawable.inflateChildElements`): an empty dial still needs one path drawn in no colour - Robolectric test failure on `widget_cookie_dial_plain` (2026-10-27).
- An `AnalogClock` second hand (Android 12+) in a widget turns by the real time: on the owner's phone (Android 16, Lawnchair) the watch
  dial's bezel stepped exactly 6.00 degrees once a second over 12 s with no drift between steps, and was at the right second 0.6 s after
  returning to the home screen from an app (2026-11-02, screenrecord measured frame by frame). The restarted animated ring it replaces
  could not do either: in the owner's own video it jumped 1.5 degrees while resized and 0.8 at rest.
- `RemoteViews.addView` + `removeAllViews` replace a nested view on every `reapply`, so a widget's animated child restarts from its first frame each time the widget is drawn - Android source behaviour, used by the watch dial's seconds ring; **not yet seen on a launcher** (2026-10-27).

- Pixel / stock Quick Settings edit mode (the design the "+" menu's Edit mode follows): since Android 16 QPR1 (the September 2025 Pixel Drop; first seen in QPR1 Beta 1, May 2025) tiles can be **resized between two sizes**, icon-only (1x1) and wide with the name (2x1); in edit mode a selected tile shows a handle on its right edge, dragged left for icon-only and right for the name; tiles are reordered by dragging; the editor lists available tiles in categories and has a Reset at the bottom - Google Pixel Help https://support.google.com/pixelphone/answer/6111329 ("tap and drag the right side of the icon to the left ... to the right", "tap and drag the settings to where you want them placed"; fetched 2026-10-28), 9to5Google and a web search for the QPR1 details; NOT verified: the exact long-press versus tap to start a drag, and any lift/scale numbers (Gemini's 1.2x came from old AOSP `TileAdapter`, not checked for QPR1) - researched by Haiku 5.5 xhigh + Gemini 3.8 Flash High, checked against the support page 2026-10-28.
- Compose test of motion on a manual clock (`mainClock.autoAdvance = false`) needs `Snapshot.sendApplyNotifications()` after a state write, or the composition never sees it - found with `ActionMenuMotionTest` (2026-10-28).
- sameerasw/essentials v20.3-beta.1 (the footer the owner points to; read through the GitHub API, 2026-10-31): its footer, the file named EssentialsFloatingToolbar under `ui/components` there, is
  Material 3 Expressive's `HorizontalFloatingToolbar` (primary container, a FAB beside it, no scroll behaviour passed from `MainActivity`). Its motion: each tab's width
  and the gap after it are `animateDpAsState(spring(DampingRatioMediumBouncy, StiffnessLow))`; the bar's modes (tabs, back + title, search) swap in an
  `AnimatedContent` with `fadeIn + scaleIn(0.85)` against `fadeOut + scaleOut(0.85)` and `SizeTransform(clip = false)`, the selected pill kept by `sharedBounds`; the back
  title's width collapses on `tween(380, CubicBezierEasing(0.2, 0, 0, 1))`; the FAB's icon swaps with fade + scale 0.6; a jump over more than one tab snaps the pager,
  then a spring (LowBouncy, MediumLow) brings the content in from 48 dp, alpha 0 to 1 and scale 0.96 to 1; pushed pages slide on `spring(0.9, 500)` (`AppNavTransitions`).
  Every press has a haptic (`HapticUtil`). Source: the repository at tag v20.3-beta.1 - verified by reading.

## How to add an entry

`claim - applies to (library/API level/version) - source (URL or path:line) - status/date`. If a fact is later disproved, delete it
(don't leave it with a caveat) and note the correction in the commit message.
