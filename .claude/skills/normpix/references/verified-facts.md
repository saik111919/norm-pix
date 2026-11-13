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
- `ModalBottomSheet` **Back** skips the `confirmValueChange` veto: the sheet's dialog runs `scope.launch { sheetState.hide() }
  .invokeOnCompletion { onDismissRequest() }` with no `isVisible` check (the swipe's `settleToDismiss` and the scrim's
  `animateToDismiss` have one), so a vetoed hide still dismisses. Its `PredictiveBackOnBackPressedCallback` also leaves the sheet
  scaled (up to 48 dp narrower) when that hide doesn't happen - material3 1.4.0 - sources jar `commonMain/.../ModalBottomSheet.kt`
  lines ~151-183 and `androidMain/.../ModalBottomSheet.android.kt` ~528-640 - **RE-CHECKED 2026-11-04** (and on the phone: Back
  closed a dirty form). Fix in `OverlayHost` / `SheetDismissConfirmation`: `design-system.md` section 3.
- activity-compose **1.13.0** `BackHandler` resolves its owner as `LocalNavigationEventDispatcherOwner` (view-tree host default),
  falling back to `LocalOnBackPressedDispatcherOwner`. Inside sheet content that is the sheet's own `ComponentDialog`, and it is added after
  Material's dialog callback, so it wins - activity-compose 1.13.0, navigationevent-compose 1.1.2 - sources jars + `SheetBackTest` +
  the phone - **RE-CHECKED 2026-11-04**.
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
- **Launch screen (platform `android.window.SplashScreen`, no androidx library)**, all RE-CHECKED 2026-11-07 against the SDK files
  `Sdk/sources/android-36.1/android/window/SplashScreen.java` + `SplashScreenView.java` and the SDK's platforms/android-36.1/data/api-versions.xml (outside the repo)
  (two researchers, Haiku 5.5 xhigh and Gemini 3.8 Flash High, were run first; where they disagreed the source decided):
  - `SplashScreen.setSplashScreenTheme(int)` exists from **API 31** (the method has no `since` of its own inside `since="31"`; "API 33" was wrong,
    though DropSauce and NormPix guard the call at 33). It persists the theme by resource *name* (the name must be stable across versions);
    `Resources.ID_NULL` resets to the manifest theme (`SplashScreen.java` 98-113, 177-198). It makes a system call: keep it off the main thread.
  - `setOnExitAnimationListener` only takes effect during or before `onResume` (`SplashScreen.java` 75-89). The `SplashScreenView` it hands over
    is drawn over the activity until the app calls `remove()` (`SplashScreenView.java` 523-560): an app may keep it as long as it likes. Its icon
    is a `SurfacePackage` of the system's own surface (the animation keeps running there) centred with `Gravity.CENTER` in the view
    (`SplashScreenView.java` 262-292, 366-369). `getIconAnimationStart()` (`Instant`) and `getIconAnimationDuration()` are public from API 31 and
    the duration is null or 0 unless the icon is an `AnimatedVectorDrawable` / `AnimationDrawable` (`SplashScreenView.java` 449-471).
  - The icon is 288 dp, shown through a 192 dp circle (no icon background; 240 / 160 with `windowSplashScreenIconBackgroundColor`) - developer.android.com
    splash-screen page, via both researchers. `windowSplashScreenAnimationDuration` is deprecated at 33 (the duration is the AVD's own).
  - A `pathData` morph (`valueType="pathType"`) needs `valueFrom` and `valueTo` with identical commands (`AnimatorInflater` throws otherwise); a
    `pathInterpolator` may overshoot 1.0 on y (both researchers, `PathInterpolator.java`) - the AVD with those interpolators plays on the Pixel_10 emulator (Android 17) and the owner's motorola edge 50 neo (Android 16), 2026-11-07.
  - `@android:color/system_accent1_0/_200/_600/_800` resolve in `values-v31` (the framework's colors_dynamic.xml); they are the tones Material's
    dynamic scheme uses for primary / onPrimary (light 600 / 0, dark 200 / 800).
  - UNVERIFIED: one researcher (from a fetched summary of AOSP, not verbatim) says the Shell clamps how long it shows an AVD launch screen to
    ~400-500 ms when the app draws early; the app does not depend on it (it waits out the AVD's remaining intro itself). On the Moto (Android 16,
    release build, 2026-11-07) the launch screen showed continuously from ~0.1 s until the app released it, ~0.5-0.6 s later, with no early cut seen, but that was not isolated: still not settled.
- **Launch-screen hand-over on a phone** (motorola edge 50 neo, Android 16, release build, logs and 30 fps recordings, 2026-11-07): `SplashScreenView.iconAnimationStart`
  is when the system actually started the AVD (`animationStartCallback`), ~0.02-0.03 s after the tap, so by the app's first drawn frame (`TotalTime` 260-325 ms) it had
  played 222-316 ms; the window is seen only after the launcher's opening (~100+ ms), which hid the first ~100 ms of a 0-offset intro. The exit listener fires a frame or two after
  the app's first draw: **at the hand-over and again where the held view is removed the screen showed one empty frame of the ground** when the app's own layer drew no cookie
  under it - draw the settled cookie from the first frame and fade the view out instead of removing it (no empty frame after that). `android:color/system_surface_light` /
  `system_surface_dark` exist from API 34 (the SDK's api-versions data); on this phone the system surface equals the app's own page colour (#FDF6F4 both).
- **Debug vs release start**: on the Moto the same code took `am start -W` TotalTime ~1.5 s as the debuggable `qa` build and 261-318 ms as `qaRelease`
  (R8, not debuggable), and Home opened at ~2.7 s against ~1.2 s - time and record cold starts only with `qaRelease`.
- **Emulator timing**: with Gradle running next to the headless `Pixel_10` emulator its guest went to 97% system CPU and swapped, and
  `am start -W` read 16-24 s instead of ~1.7 s. Run `gradlew --stop`, cold-boot the emulator (`-no-snapshot-load`) and measure only when calm.
  `screenrecord` on it is variable-frame-rate: read frame times from OpenCV's `CAP_PROP_POS_MSEC`, not index / fps - observed 2026-11-07.

## Encryption at rest (2026-11-03: stage 1 locked backups and stage 2 SQLCipher databases built; pictures and strict mode not)

- SQLCipher community edition: `net.zetetic:sqlcipher-android:4.20.0@aar` + `androidx.sqlite:sqlite:2.7.0`, Android API 23 and up, ABIs
  armeabi-v7a/x86/x86_64/arm64-v8a, `System.loadLibrary("sqlcipher")` before any database call, classes in `net.zetetic.database.sqlcipher`,
  `SupportOpenHelperFactory` for androidx.sqlite/Room. The AAR is 4,050,842 bytes (all four ABIs) - github.com/sqlcipher/sqlcipher-android
  README + repo1.maven.org `net/zetetic/sqlcipher-android/4.20.0/` (latest = release = 4.20.0) - **RE-CHECKED 2026-11-03**. Measured
  in our qa APK, 2026-11-03: `libsqlcipher.so` is 2,110,992 bytes for arm64-v8a (~1.03 MB deflated, which is what the release's
  compressed native libraries add to the download) and 1,057,676 for armeabi-v7a (~0.67 MB deflated); every LOAD segment of the
  arm64 and x86_64 libraries is aligned `0x4000` (NDK 28 `llvm-readelf -lW`), so it is **16 KB page-size ready**. The zetetic.net
  Android page describes the *commercial* edition (that one needs `PRAGMA cipher_license`).
- A plaintext SQLite file is encrypted with `ATTACH DATABASE 'enc.db' AS encrypted KEY '...'; SELECT sqlcipher_export('encrypted');
  DETACH DATABASE encrypted;`. `PRAGMA rekey` cannot encrypt a plain database; `sqlcipher_export` does not copy `user_version` or
  `auto_vacuum` (set them on the target). Defaults: PBKDF2_HMAC_SHA512, 256,000 iterations, page size 4096; `cipher_memory_security` is
  off by default - zetetic.net/sqlcipher/sqlcipher-api - **RE-CHECKED 2026-11-03**.
- Read in the 4.20.0 sources jar (repo1.maven.org `...-4.20.0-sources.jar`), **RE-CHECKED 2026-11-03**, the basis of `storage/`:
  `net.zetetic.database.sqlcipher.SQLiteDatabase` **implements** androidx `SupportSQLiteDatabase`; `insert(table, conflict, values)`
  = `insertWithOnConflict` (throws), `insert(table, nullHack, values)` swallows and returns -1. `SupportOpenHelperFactory(password,
  hook, enableWal)` -> `SupportHelper`, which passes a **null** error handler and does not call the androidx callback's
  `onCorruption`; SQLCipher's `DefaultDatabaseErrorHandler.onCorruption` returns without deleting when `SQLiteDatabase.hasCodec()`
  (native, true in SQLCipher builds) - so a wrong key never deletes the file (androidx's framework helper *does* delete on
  corruption: never point it at an encrypted file). The key is applied with `nativeKey` (sqlite3_key) only when the password is
  non-empty: an empty password opens a plain SQLite file. Nothing in Java calls `System.loadLibrary("sqlcipher")` - the app must.
  The password array is kept by reference, never wiped. `SQLiteOpenHelper` mkdirs the databases folder. `rawExecSQL(sql)` runs
  ATTACH / `SELECT sqlcipher_export(...)` statements. Licence: BSD 3-clause (`LICENSE` in github.com/sqlcipher/sqlcipher-android),
  attribution in the app's interface or materials - bundled as `assets/licenses/sqlcipher.txt`, shown from About.
- Robolectric on this Windows PC names each test's data folder after the test method; long test names plus long database file
  names pass the 260-character path limit and SQLite fails with `SQLITE_CANTOPEN` (code 14) - use short database names in tests
  (`EncryptedDatabasesTest` uses `fin.db`). Not an issue on phones or Linux CI - **seen 2026-11-03**.
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

## Charts and Insights design (2026-11-13: two research runs, checked against the pages)

- Apple HIG "Charts" (developer.apple.com/design/human-interface-guidelines/charts.md, page dated 2022-09-23, read in full 2026-11-13): "Let people
  interact with the data when it makes sense, but don't require interaction to reveal critical information" (Stocks: drag a vertical indicator through
  the line); "consider expanding the hit target to include the entire plot area, letting people scrub across the area"; bars "work well ... to compare values
  in different categories or view the relative proportions of various parts in a whole", lines for change over time; "bar charts can work well when you use
  zero for the lower bound"; "Prefer familiar sequences of values in the tick and grid-line labels"; "if people can inspect individual data points by
  interacting ... you might use fewer grid lines and light label colors"; "Summarize the main message of your chart"; "In a compact environment, maximize
  the width of the plot area"; "Avoid relying solely on color ... use different shapes or patterns"; hide axis and tick labels from assistive technology and
  describe values, not colours; "highlight the changes in other ways, too, ... people who turn off animations". Verified by opening the page.
- Datawrapper: "Truncated y-axes in column charts and bar charts are considered deceptive and misleading" (datawrapper.de/academy/why-our-column-and-bar-charts-start-at-zero) -
  verified 2026-11-13.
- **A researcher's [SOURCE-CHECKED] label was wrong once**: Gemini 3.8 Flash High cited `m3.material.io/blog/data-accessibility` as checked; it is HTTP 404. Haiku 5.5's report
  (with its own UNVERIFIED marks - Material's data-visualisation page returned only its title; Monzo's help page says nothing about bars) was the reliable one.
  Anything resting only on Gemini's Material, Monzo or "Copilot Money" citations was dropped. Open the pages before building on a "checked" label.
- A child session in this checkout runs the project's Stop hook too: Haiku's final message answered the "skill may be out of date" reminder instead of the question;
  its report was earlier in its thread (`t3_thread_read`, `afterPosition`). Read the whole child thread, not only the delivered summary.

## How to add an entry

`claim - applies to (library/API level/version) - source (URL or path:line) - status/date`. If a fact is later disproved, delete it
(don't leave it with a caveat) and note the correction in the commit message.
