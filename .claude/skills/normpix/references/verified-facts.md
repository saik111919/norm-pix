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

## OCR and receipts

- ML Kit returns **scrambled line order** and reads the rupee sign as `7`, `Z` or nothing; per-symbol confidence for a misread rupee
  is ~0.07-0.12 versus 0.6-0.95 for real digits, which `rupeeRestored` uses (threshold 0.25 and below half the rest). - ML Kit
  text-recognition 16.0.1 on the owner's phone - NOTES (2026-10-13).
- Google Pay names its shared image `<id> - 30.00 To <payee> on Google Pay.png` (amount, payee, direction) and
  `OpenableColumns.DISPLAY_NAME` returns it - Google Pay Android share - NOTES (2026-10-13).
- UPI receipts show the receiving bank's KYC name, which for a self transfer is the user's own; no app prints a self-transfer-specific
  receipt, so the self-transfer fixtures are synthetic - NOTES (2026-10-25).
- A `<vector>` drawable with no `<path>` does not inflate ("no path defined", `VectorDrawable.inflateChildElements`): an empty dial still needs one path drawn in no colour - Robolectric test failure on `widget_cookie_dial_plain` (2026-10-27).
- `RemoteViews.addView` + `removeAllViews` replace a nested view on every `reapply`, so a widget's animated child restarts from its first frame each time the widget is drawn - Android source behaviour, used by the watch dial's seconds ring; **not yet seen on a launcher** (2026-10-27).

- Pixel / stock Quick Settings edit mode (the design the "+" menu's Edit mode follows): since Android 16 QPR1 (the September 2025 Pixel Drop; first seen in QPR1 Beta 1, May 2025) tiles can be **resized between two sizes**, icon-only (1x1) and wide with the name (2x1); in edit mode a selected tile shows a handle on its right edge, dragged left for icon-only and right for the name; tiles are reordered by dragging; the editor lists available tiles in categories and has a Reset at the bottom - Google Pixel Help https://support.google.com/pixelphone/answer/6111329 ("tap and drag the right side of the icon to the left ... to the right", "tap and drag the settings to where you want them placed"; fetched 2026-10-28), 9to5Google and a web search for the QPR1 details; NOT verified: the exact long-press versus tap to start a drag, and any lift/scale numbers (Gemini's 1.2x came from old AOSP `TileAdapter`, not checked for QPR1) - researched by Haiku 5.5 xhigh + Gemini 3.8 Flash High, checked against the support page 2026-10-28.
- Compose test of motion on a manual clock (`mainClock.autoAdvance = false`) needs `Snapshot.sendApplyNotifications()` after a state write, or the composition never sees it - found with `ActionMenuMotionTest` (2026-10-28).

## How to add an entry

`claim - applies to (library/API level/version) - source (URL or path:line) - status/date`. If a fact is later disproved, delete it
(don't leave it with a caveat) and note the correction in the commit message.
