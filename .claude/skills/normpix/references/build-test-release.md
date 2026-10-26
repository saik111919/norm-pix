# Build, test, release and git workflow

Verified 2026-10-27 against commit `c99a54e2`. The owner's machine is Windows 11 (PowerShell and Git Bash); CI is
`ubuntu-24.04`. The cloud sandboxes some sessions run in have no Android SDK: that is a limit of the sandbox, not of the project.

Contents: 1 Local environment · 2 Commands · 3 Reading results · 4 Windows and load traps · 5 Writing tests ·
6 Looking at a render · 7 CI and releases · 8 Signing · 9 Git, branches and PRs · 10 Device QA · 11 Hygiene

## 1. Local environment

- Repo root `D:\Work\Personal\NormPix`; the Android project is `native/`. `native/local.properties` (untracked) holds `sdk.dir`.
  Use `native\gradlew.bat`. The Gradle daemon wants JDK 25 and downloads it; `JAVA_HOME` may be older. Android Studio is
  often open with its own daemon (they coexist).
- `adb` is not on PATH: `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. There is no Python on the PC; parse XML with
  PowerShell `[xml]`, use Bash for `gh`/`git`/`unzip`.
- **Other agents may be using the same tree and Gradle.** Before running tests, list `java.exe` command lines
  (`Get-CimInstance Win32_Process`) for another `GradleWrapperMain ... test`; two concurrent runs share
  `app/build/test-results` and both die with `NoSuchFileException ... in-progress-results-generic*.bin`. Never edit another
  agent's half-written files; wait or verify in a scratch copy (`tar` the tree without `build`/`.gradle` into a temp dir).
- **Line endings:** `core.autocrlf=true`; the index is LF and most working-tree files are CRLF (~390 .kt files CRLF, ~115
  LF, 2 mixed; check one with `git ls-files --eol <file>`). A multi-line `perl -0pi` / `sed` pattern silently matches nothing
  on CRLF files and exits 0. Use the **Edit tool** for anything past one line, and grep for the new text after any scripted
  edit. Single-line `sed -i 's/a/b/'` is fine.

## 2. Commands (run from `native/`)

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-build-cache --console=plain --tests "*ClassA" --tests "*ClassB"   # targeted: 20 s - 1.5 min
.\gradlew.bat :app:testDebugUnitTest --no-build-cache --console=plain                                       # everything: ~3-7 min
.\gradlew.bat :app:lintDebug --console=plain                                                                # run on its own, ~2 min
.\gradlew.bat :app:assembleQa                                                                               # installable QA build
.\gradlew.bat :app:assembleRelease                                                                          # R8 release (needs release key props to be update-compatible)
.\gradlew.bat :app:assembleRelease -PsplitAbis                                                              # what CI builds: per-CPU APKs + universal
.\gradlew.bat testDebugUnitTest --rerun --tests 'com.example.normpix.ReadmeScreenshots' -Pscreenshots="$PWD\..\docs\screenshots"
```

There is only `testDebugUnitTest` (no `testReleaseUnitTest`, no qa tests, no `androidTest`). `:app:check` runs `lintDebug`
and `lint`. A "CI-equivalent" gate = full `testDebugUnitTest`, then `lintDebug` alone (expect 0 errors and a few advisory
warnings). CI takes ~8 min a cycle, so reproduce a CI failure locally first.

## 3. Reading results

- `native/app/build/test-results/testDebugUnitTest/TEST-<fqcn>.xml`: count `tests` / `failures` / `errors` per file.
  **"BUILD SUCCESSFUL" alone is not proof, and "BUILD FAILED" with no failing test means the tests never ran** (see below).
  Always confirm by counting result XMLs or the "N tests completed" line before saying the suite passed.
- Lint: `native/app/build/reports/lint-results-debug.xml` / `.html`.
- From a CI run: `gh run view <id> --log-failed`, or download the `reports` artifact with
  `gh api repos/saik111919/norm-pix/actions/runs/<id>/artifacts` then `gh api .../zip > x.zip` **from Bash** (PowerShell's `>`
  corrupts the zip). The workflow's "Name what failed" step annotates compile errors, failed tests and lint errors on the run page.

## 4. Windows and load traps

- **R.jar lock:** a combined run (`testDebugUnitTest lintDebug assembleQa --continue`) can fail `processDebugResources` with
  "Unable to delete file ...\R.jar" (Android Studio's process holds files). With `--continue` the tests are then *skipped
  silently*. Fix: rerun with `--no-build-cache`.
- **Whole-suite load flakes** (pass alone): `ShareTransactionFlowTest`, `ProfilePhotoFlowTest`, `PictureAttachmentFlowTest`,
  `InsightsScreenTest` have each timed out only under full-suite load. Re-run the one class alone (`--tests`) before treating
  it as a regression, and report it as a load flake, never as a pass.
- **Run `lintDebug` alone.** A combined `testDebugUnitTest lintDebug assembleRelease` once hung `lintAnalyzeDebug` at 100% of a
  core for 30+ minutes; alone it takes ~2 min. If a gate goes quiet after the tests, take a thread dump, stop the client and
  daemon and run lint by itself.
- **Deleting one qualifier of a resource** (e.g. `mipmap-*dpi/ic_launcher.png` while keeping `mipmap-anydpi/ic_launcher.xml`)
  breaks the incremental merge: run `:app:mergeQaResources --rerun :app:mergeReleaseResources --rerun`.
- Kotlin incremental compilation is off (drives D: vs C:). A "Failed to tokenize ...invokeSuspend" Compose-mapping warning
  comes from a suspend function whose composable lambda captures a local `Boolean`; compute it inside the lambda
  (`:app:reportReleaseComposeMappingErrors` reproduces it).
- Deleting a worktree's Gradle `build/` fails with "Filename too long": use `Remove-Item -LiteralPath "\\?\<path>"`.

## 5. Writing tests

Where: mirror the main package under `app/src/test/kotlin/...`. Pure domain/parser tests need no Android; screens use
`TestEnvironment` + `createComposeRule()` (see `architecture.md` section 8). Traps that make a *new* test fail for harness
reasons - check these before suspecting the code:

1. **Home blocks emit siblings** (`TopSpendingStack`, `CardsOnHome`): host them in a `Column { }`, not straight in a `Box`,
   or the deck swallows the header's clicks. Tell: `performClick` does nothing, `performSemanticsAction(OnClick)` works.
2. **`testTag` goes before `padding`** (`Modifier.size(140.dp).padding(20.dp).testTag("x")` tags the inner box). Derive pixel
   scale from `PixelMap.width / dp`.
3. **Text under `clearAndSetSemantics`** (account card, month pill) is not in the merged tree: use `useUnmergedTree = true`; to
   assert something is *said once*, count in the unmerged tree.
4. `getUnclippedBoundsInRoot()` returns a `DpRect`: import `androidx.compose.ui.unit.width` / `height`.
5. **Render-to-PNG and text-overflow tests need `@GraphicsMode(GraphicsMode.Mode.NATIVE)`** (otherwise the first vector icon
   crashes the draw, and text is measured with fake narrow widths). Prove a new layout test can fail by running it once against
   the old code (`git checkout <base> -- <files>`).
6. **Save is pinned** below the scrolling body of `EditorSheet`: click it directly, no `performScrollTo()`. Fields inside the body
   still need `performScrollTo()`.
7. **Cash already exists** in a fresh `TestEnvironment`: look accounts up by name, don't `accounts().single()`.
8. **The Wallet defaults to a stack drawing only the front account**: set `AccountsLayout.Rows` (names as text) or wait for
   "Add account".
9. `compose.waitUntil { prefsOrFileCondition }` never idles the looper when the lambda reads no node: write
   `waitUntil(10_000) { compose.waitForIdle(); condition() }`.
10. A test that builds its **own `AppContainer`** (not `TestEnvironment`) must pass `dispatcher = Dispatchers.Unconfined` to
    `FinanceRepository` and `ReminderDao`, or Home can stick on "Loading your month" depending on which tests ran first.
11. A pixel "ink" count needs a clean fill sample (not inside a 40 dp avatar); `Font(file)` has no value equality (compare
    `toString()`); a trailing lambda binds to the *last* parameter - name it.
12. Bottom-sheet windows don't capture with `captureToImage`: render the sheet composable inline.
13. New navigation methods: add to `RecordingNavigator`. New `AppContainer` services: add to `DummyData`.

Test data rules: receipt fixtures come from real recognizer output/phone geometry with personal details swapped for stand-ins
(never real names, UPI ids, account numbers or references). Don't validate a reader against fixtures you wrote yourself - "don't
read what you created" is the owner's correction; use the real shared images.

## 6. Looking at a render

Write a throwaway Robolectric test with `@GraphicsMode(NATIVE)`, wrap content in `NormPixTheme { ProvideAppEnvironment(container) { ... } }`
(or `env.Content { }`), `captureToImage().asAndroidBitmap().compress(PNG, ...)` into `java.io.tmpdir`, then Read the PNG.
~25 s per run. Render dark + light (+ Pure), 411 dp and wide, and 1.6x font. **Delete the throwaway test afterwards** (the owner may
commit the tree mid-session and it would be swept in). Don't write PNGs with PowerShell `>` (the BOM corrupts them).

## 7. CI and releases

- `.github/workflows/android.yml` (name `Android`): jobs **`verify`** (Tests and lint: `testDebugUnitTest`, `lintDebug`, annotate
  failures, upload reports), **`build`** (Release APK: reads the version, loads the signing key, builds `-PsplitAbis`, collects
  APKs, checks the version is newer than the latest release, writes notes + `update.json`), **`release`** (GitHub release; not
  on PRs). Triggers: PRs into `main`, pushes to `main`, tags `v*`, manual dispatch. Concurrency cancels superseded PR runs only.
- `.github/workflows/screenshots.yml` (`Render`): re-renders `docs/screenshots` when the app changes on `main`; on a PR it pushes
  them to `readme-screenshots/<branch>` (a PR's own branch is never pushed to).
- PR checks to wait for: **Tests and lint (~8 min), Release APK (~5 min), Render (~3 min)**. "GitHub release" is skipped on PRs.
- **A push to `main` publishes a release** at the next free patch of `versionName`'s `X.Y` (highest `vX.Y.*` tag + 1), so only a
  minor/major bump is edited by hand (`versionName` in `native/app/build.gradle.kts`; `versionCode` follows). Notes = the commit
  subjects since the last release (so write commit subjects a user can read), or an annotated tag's message; `mandatory: true` in
  a tag message makes the app offer it at every launch. `MAIN_BUILDS: release|prerelease` at the top of the workflow chooses.
  CI fails a tag that doesn't match `versionName`, a release not signed with the release key, and a `versionCode` not above the
  latest.
- History: v1.0.x up to v1.0.22; v1.1.0 (tablet layouts, 2026-10-23); v1.2.0 (as of today, Check and recalculate, Activity by
  day); v1.2.1 (Wallet headings, Settings grouped); v1.3.0 (widgets); v1.4.0 (NFC off by default, Colors previews, Pure, About).
  Minor bumps went with each feature PR the owner asked to ship.

## 8. Signing

- **Release key** = the key the installed app has always carried (certificate SHA-256 `96:B3:0A:AE:...:5A:34:88`). CI reads it
  from secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` and fails a
  release whose certificate isn't `RELEASE_CERT_SHA256`. Locally, `~/.gradle/gradle.properties` carries `normpixKeystoreFile`,
  `normpixKeystorePassword`, `normpixKeyAlias`, `normpixKeyPassword` so a local `assembleRelease` is update-compatible.
  **Never regenerate, replace or commit the release keystore**: without it no APK can update the installed app.
- **Debug and qa** builds use `native/app/debug.keystore` (committed; `androiddebugkey` / `android`), so they can never install
  over the real app, nor it over them. A debug/qa build installed before that key existed needs one uninstall (never the owner's
  real app).
- Without secrets, CI still publishes a prerelease signed with the debug key and fails a real release.

## 9. Git, branches and PRs

- Remote `origin` = `github.com/saik111919/norm-pix`, git user `saik-11`. The repo uses **merge commits** (`gh pr merge N --merge`);
  remote branches are deleted afterwards. PRs #19-#24 (2026-10-23 to 26) each went from a branch into `main`.
- **Owner's branch plan (2026-10-25)**: a standing local `features` branch for new features and a standing `bugs` branch for
  fixes (which also go to `main`); `bugs` merges to `main` only when the owner says "go"; `features` merges when the owner says
  it is ready (suggested: ~5 main features or ~10 minors, minor version bump, bugs first). In practice since then, the owner asked
  for PRs per feature batch and said "raise PR and merge it if everything is good". On 2026-10-26 `features` == `origin/main`
  (v1.4.0). **Check `git branch --show-current` and `git status` first**, put new work on the right branch, and never merge to
  `main` unless the owner asked in this conversation.
- **Commit / push / PR / merge only when asked.** "Raise PR and merge it if everything is good" authorizes that one PR: run the
  full local gate, rebase on `origin/main`, push, `gh pr create`, wait for the three checks, merge with `--merge`, report. Red
  checks or failing tests mean stop and report. Commit only your own files (the owner's unstaged changes stay out). Attribution
  trailers come from the session's system reminder. In T3 Code, register every PR with `link_pull_request`.
- **The owner uses the same checkout.** They have committed an agent's uncommitted work and switched branches mid-session
  (`git status` suddenly clean, files "gone"). Check `git branch --show-current`, `git reflog -8`, `git status` at the start of
  work; if the checkout moved, continue in a worktree (`git worktree add ../NormPix-<name> <branch>`, copy `native/local.properties`,
  ~3 min cold build) rather than switching the shared checkout back.
- Fast-forward local `main` without checking it out: `git fetch origin main:main`.

## 10. Device QA

- Only the **`qa` build** (`com.example.normpix.qa`, label "NormPix QA", FLAG_SECURE off) goes on an emulator or the owner's own
  phone (allowed since 2026-10-13 for testing; qa build only; never install over, open or touch the real `com.example.normpix` or
  its data; say what you'll install; always pass `-s <serial>`; delete UI dumps, receipt copies and test data afterwards).
  Release/debug builds blank screenshots (FLAG_SECURE in release); inspect a release through `uiautomator dump`.
- The emulator may be driven by someone else: if taps arrive that you didn't send (`adb shell dumpsys input | grep -A12
  RecentQueue`), stop and verify with Robolectric renders instead. Emulators have crashed mid-QA; Robolectric is the reliable
  fallback. Wait 12-16 s after launch and after opening a sheet; a tap during loading is silently lost; screenshot before trusting
  a blind replay.
- Sharing a real picture to the qa app on a phone: `am start -a SEND` with a MediaStore URI fails; open the picture in Google
  Photos (`-a android.intent.action.VIEW -d content://media/external/images/media/<id> -p com.google.android.apps.photos -f 0x10008000`)
  and use its Share sheet.
- To see ML Kit's real output for a picture, use a temporary qa-only `OcrDumpActivity` (never commit it) and remove it, the dumps
  and the qa app afterwards.
- Visual claims need a render or a device look; "tests pass" does not say a design is right. Say plainly what was and wasn't
  tried on a phone (TASKS entries end with "Not tried on a phone: ...").

## 11. Hygiene

Delete throwaway tests and PNG/dump files; don't leave `build` dirs in worktrees; don't commit real receipts, names, account
numbers or UPI ids; don't leave debug logging of card data, clipboard contents, account numbers or references (nothing sensitive
is ever logged in this app). `native/TASKS.md` gets a dated phase entry for substantial work (see `owner-working-style.md`).
