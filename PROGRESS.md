# Progress / Session Log

Running log of in-progress work and decisions that aren't obvious from `git log` alone.
Read top-to-bottom, most recent first. If you're picking this up cold (new session, ran
out of tokens, etc.), start here before re-deriving context from scratch.

## 2026-07-06

**Dependency upgrade to latest stable (AGP 9 line):**
- AGP 8.7.3 → 9.1.1 — adopted AGP 9's built-in Kotlin support; removed the
  `org.jetbrains.kotlin.android` plugin from `build.gradle.kts` and `app/build.gradle.kts`
  (no longer needed — AGP 9 compiles Kotlin itself). `kotlin-compose` plugin stays (separate
  Compose-compiler plugin, unaffected by built-in Kotlin).
- Gradle 8.9 → 9.3.1 (wrapper + CI workflow) — AGP 9.1.1 requires Gradle ≥ 9.1.0.
- Kotlin 2.0.21 → 2.4.0.
- AndroidX: core-ktx 1.15.0 → 1.17.0, lifecycle-runtime-ktx 2.8.7 → 2.10.0,
  activity-compose 1.9.3 → 1.12.4, core-splashscreen 1.0.1 → 1.2.0.
- Compose BOM 2024.12.01 → 2026.04.01 (Compose 1.11.x).
- Verified with a real `assembleDebug` run (see below) — builds clean, APK produced.

**Deliberately did NOT bump to compileSdk/targetSdk 37 (Android 17, stable June 2026),**
even though it's the newest, because:
- Compose BOM 2026.06.00+ / core-ktx 1.19+ / lifecycle 2.11+ all require compileSdk 37,
  and only SDK platforms 35/36/36.1 are installed locally (no `cmdline-tools`/`sdkmanager`
  available in this environment to fetch platform 37 — would need Android Studio's SDK
  Manager or `sdkmanager "platforms;android-37"`).
- More importantly: apps that **target** API 37 get a 3-hour delay before
  `RECEIVE_SMS`/`READ_SMS` can see incoming SMS (Android's new anti-OTP-theft measure).
  NormPix's entire value prop is auto-capturing bank SMS in real time — targeting 37
  would silently break that until the delay guidance is designed around. This is a
  product decision, not just a version bump, so left for the user to weigh in on.
- **If resuming this thread:** install SDK Platform 37, decide whether targetSdk 37 is
  worth the SMS delay tradeoff, then bump compileSdk/targetSdk + compose-bom/core-ktx/
  lifecycle to the 37-line versions together.

**Verified locally:** `assembleDebug` builds clean using the cached Gradle 9.3.1 at
`~/.gradle/wrapper/dists/gradle-9.3.1-bin/.../gradle-9.3.1/bin/gradle` (no `gradlew` checked
into this repo — see README's "Build from source" section).

**Also fixed:** `TogglePill` in `app/src/main/java/com/normpix/cashflow/ui/Common.kt` to
match the updated M3 switch spec — off-track color `t.hi` (was `t.cont`), off-thumb color
`t.out` (was `t.onSV`), animated thumb size 16dp↔24dp (was fixed 24dp), thumb offset
6dp↔22dp (was 2dp↔22dp), thumb color now animated (was a plain conditional), check icon
14dp→16dp. No call-site changes needed (`RemindersScreen.kt`, `SettingsScreen.kt` both
consume the shared composable).

### Open questions for the user
- targetSdk 37 vs. the SMS OTP-delay tradeoff above.
- Play Protect flags the sideloaded APK — see chat discussion (READ_SMS/RECEIVE_SMS is the
  #1 malware-heuristic fingerprint, compounded by being an unsigned/unverified sideload
  with no install-base reputation, not because the app does anything actually harmful).
