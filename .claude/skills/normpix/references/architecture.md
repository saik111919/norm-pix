# NormPix architecture

Verified 2026-10-27 against commit `763cc08f` (v1.5.0 in the working tree). Volatile numbers (versions, DB versions) are re-checked by
`scripts/verify.sh`; trust the code over this file if they ever disagree, then fix this file.

Contents: 1 Stack and build · 2 Process wiring · 3 Data and storage · 4 Money model · 5 UI wiring and navigation ·
6 Preferences (and how to add a setting) · 7 Settings UI · 8 Test infrastructure

## 1. Stack and build

- One Gradle module `:app` (`native/settings.gradle.kts`, root project name `NormPix`). The Android project is `native/`;
  the repo root also holds `README.md`, `docs/` (README pictures; `docs/screenshots` is generated), `.github/workflows/`,
  `lib/widgets/wallet_card_view.dart` (the one tracked leftover of the earlier Flutter app) and `.claude/` (this skill).
- Kotlin 2.4.0, AGP 9.1.0, Gradle wrapper 9.3.1. The Gradle daemon asks for **JDK 25**
  (`native/gradle/gradle-daemon-jvm.properties`, auto-provisioned); source/target compatibility is Java 17.
- Compose BOM 2026.09.00, which pins **material3 1.4.0** (see `verified-facts.md`: it lacks the Expressive composables).
  Also: navigation-compose 2.10.2, lifecycle 2.10.0, activity-compose 1.13.0, biometric 1.1.0, core-ktx 1.19.1,
  exifinterface, `material-kolor` 5.0.1 (palettes from a seed colour), `androidx.graphics:graphics-shapes` 1.1.0 (the
  loader's shape morph), ML Kit text-recognition 16.0.1 (on-device OCR for card scan and receipts), AndroidSVG 1.4 (bank
  logos), coroutines 1.10.2, kotlinx-serialization-json 1.10.0. Versions live in `native/gradle/libs.versions.toml`.
- Tests: JUnit 4.13.2, kotlin-test, Robolectric 4.17, androidx test core/ext-junit, compose `ui-test-junit4`,
  coroutines-test.
- **No DI framework, no Room, no Hilt.** Plain constructor injection through `AppContainer`; raw `SQLiteOpenHelper`
  databases; `SharedPreferences`; JSON by kotlinx-serialization.
- `applicationId` `com.example.normpix`, minSdk 26, targetSdk 35, compileSdk 37 (`android.suppressUnsupportedCompileSdk`
  in `native/gradle.properties`).
- `versionName` is in `native/app/build.gradle.kts` `defaultConfig`; `versionCodeOf` turns `X.Y.Z` into `X*10000+Y*100+Z`
  (minor and patch must stay below 100). CI overrides it through `NORMPIX_VERSION_NAME` / `normpixVersionName`.
- Build types: `debug` (signed with the committed `native/app/debug.keystore`); `qa` (initWith debug, application id
  suffix `.qa`, label "NormPix QA" from `app/src/qa/AndroidManifest.xml`, installs beside the real app, FLAG_SECURE off so
  screenshots work); `release` (R8 minify + resource shrink, `BuildConfig.SECURE_WINDOW` true, `UPDATES_ENABLED` true,
  phone ABIs only, native libs stored compressed).
- `BuildConfig` fields: `SECURE_WINDOW`, `UPDATES_ENABLED`, `UPDATE_REPOSITORY` (default `saik111919/norm-pix`; override
  `NORMPIX_UPDATE_REPOSITORY` / `normpixUpdateRepository`). Only a release updates itself: debug and qa carry another key.
- ABIs: release carries `arm64-v8a` + `armeabi-v7a` (`-PemulatorAbi` adds `x86_64`); `-PsplitAbis` (what CI uses) builds one
  APK per CPU plus a universal one. AGP does not treat the ABI filter as an input of the package step, so after switching
  between these, rebuild with `:app:stripReleaseDebugSymbols :app:packageRelease --rerun` and check the APK's `.so` files.
- `DropTelemetryBackend` (task in `app/build.gradle.kts`) removes ML Kit's `CctBackendFactory` meta-data from every
  variant's merged manifest, so ML Kit's telemetry has no backend to upload to.
- Locale: `localeFilters += "en"`. **All UI text is hard-coded English in Kotlin**; there is no `stringResource` use
  anywhere and `res/values/strings.xml` is a few lines (app/widget labels). Don't introduce string resources piecemeal.
- `proguard-rules.pro` keeps the no-argument constructors ML Kit builds its components with by reflection (R8 removed
  them once and text recognition never started).
- `native/gradle.properties`: build cache and parallel on; `kotlin.incremental=false` because the project (D:) and Gradle's
  caches (C:) are on different Windows drives ("different roots" error otherwise).
- Assets (~6 MB in `app/src/main/assets`): `banks/`, `brandlogos/`, `brands/`, `fluent3d/` (Microsoft Fluent Emoji 3D,
  MIT), `licenses/`, `wallet.svg`. The default font is bundled: `res/font/gs_flex.ttf` (Google Sans Flex, SIL OFL).
  `native/design/` holds the generators for launcher icons (`app-icon/`), the watch-dial widget's rings (`clock-widget/`,
  Python) and the cookie clocks' faces and hands (`cookie-clock/CookieFace.java`, plain Java: `java CookieFace.java`).
- Lint: `ChromeOsAbiSupport` is disabled on purpose. Expect 0 errors and a handful of advisory warnings.
- Test options: Android resources included; test JVM heap 2 GB and `--enable-native-access=ALL-UNNAMED`;
  `-Pscreenshots=<dir>` sets `normpix.screenshots` for `ReadmeScreenshots`.

## 2. Process wiring

- `NormPixApplication` builds the `AppContainer` (`buildContainer`), creates notification channels (reminders, card
  expiry), points `NfcCardOutletService.isAllowed` at the availability schedule, and starts observers that must work
  without any screen open: `followCardsForExpiry` (card expiry tracker syncs only from a successfully loaded list),
  `followCardsForBanks` (`CardBankLinker` links cards to banks once finance is loaded), `updates.start()`,
  `followUpdateSettings` (background update job follows Settings), `followLedgerForWidgets` (redraws the Recent widget
  500 ms after the ledger or how it is shown changes).
- `AppContainer` (`NormPixApplication.kt`) holds: `preferences` (AppPreferences), `finance` (FinanceController),
  `reminders` (RemindersController), `vault` + `cards` (CardVault, CardsController), `lock` (AppLockService), `host`
  (ActivityHost), `nfc`, `registeredTags`, `scanner` (null in tests), `alarms`, `clipboard` (SecureClipboard), `expiry`
  (CardExpiryTracker), `receipts` (ReceiptSharer), `sharedText` (SharedTextInbox), `sharedPictures`
  (SharedPictureReader), `updates` (AppUpdater?, null in tests), `widgetRequests`, plus the request flows
  `reminderAlerts`, `walletRequests`, `updateRequests`, `updateInstallRequests`. Every service has an inert default so a
  test builds a container cheaply.
- **Sample data** is a second container: `DummyData.open(context, real, now)` (`DummyData.kt`) with in-memory
  databases, an in-memory vault and cache-dir pictures, sharing the real container's non-data services. **A service added
  to `AppContainer` must be passed through in `DummyData` too** (`receipts = real.receipts`, ...) or the sample mode
  silently gets the inert default; `DummyDataTest` asserts it.
- `MainActivity` is a `FragmentActivity` (BiometricPrompt needs one). Release sets FLAG_SECURE (no screenshots, blank
  recents thumbnail). It goes edge-to-edge, wires `host.activity` and `lock.authenticator`, and turns launch/new intents
  into container flows: a reminder id (alert screen), card-expiry (open Wallet), update notification (open update page,
  optionally install), a widget's request. Lifecycle: `onStart` re-arms expiry alarms and tells the lock the app resumed;
  `onResume/onPause` tell the updater; `onWindowFocusChanged` reconciles the clipboard; `onStop` tells the lock.
- Entry points in `AndroidManifest.xml`: `MainActivity`; ten launcher `activity-alias`es (`.IconDefault`, `.IconGlossy`,
  `.IconComic`, `.IconMinimal`, `.IconFlat`, `.IconGlass`, `.IconClay`, `.IconPixel`, `.IconPaper`, `.IconNeon`; only the
  default is enabled, `AppIcons.use` swaps); `ShareTransactionActivity` (NoDisplay share target for text/plain and
  image/*); three widget receivers; `NfcCardOutletService` (host card emulation); reminder, card-expiry, clipboard-clear
  and update receivers/services (`UpdateDownloadService` is a foreground `dataSync` service, `UpdateJobService` the daily
  JobScheduler check).
- Permissions: NFC, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, VIBRATE, INTERNET +
  ACCESS_NETWORK_STATE (updates only), REQUEST_INSTALL_PACKAGES, UPDATE_PACKAGES_WITHOUT_USER_ACTION,
  FOREGROUND_SERVICE(+_DATA_SYNC). No SMS, no notification-listener, no contacts. `allowBackup=false` and
  `data_extraction_rules.xml` excludes everything from cloud backup and device transfer.
- Network: `network_security_config.xml` trusts nothing by default and allows only `api.github.com`, `github.com`,
  `objects.githubusercontent.com`, `release-assets.githubusercontent.com` (no cleartext). `updates/UpdateNetwork.kt` keeps
  the same host list; change both together.

## 3. Data and storage

| Store | Where | Notes |
|---|---|---|
| Finance DB | `normpix_finance.db`, `finance/data/FinanceDatabase.kt` | `VERSION` 12. Tables: accounts, people, banks, categories, transactions, budgets, recurring_rules, meta. FKs on. CHECK constraints and triggers enforce positive amounts, transfer shape, "only an expense names a person". |
| Reminders DB | `RemindersDatabase.kt` | `VERSION` 2 (reminders can link to a card/account/transaction/recurring rule). |
| Preferences | SharedPreferences `normpix_preferences` | One `AppPrefsState` flow; see section 6. Other files: `normpix_nfc_tags` (registered NFC tags, AES-GCM entries), `normpix_card_expiry`. |
| Card vault | `filesDir/card_vault.bin` | AES-256-GCM, key in Android Keystore, written atomically (`FileCardVault`). Cards are never in the finance DB and never in a normal backup. |
| Pictures | `TransactionPictures` (kept + drafts folders) | A transaction's attached picture; backed up in the full (.zip) backup. |
| Profile photo / custom font | `settings/ProfilePhotos.kt`, `settings/CustomFont.kt` | Files in app storage. |

- **Migrations are additive.** `FinanceDatabase.onUpgrade` is a chain of `if (oldVersion < N) { ... }` steps (v2 banks,
  v3 sub-categories, v4 credit limits, v5 people, v6 rule people, v7 brand icons, v8 transaction pictures, v9 billing days,
  v10 account numbers/card link, v11 later default categories, v12 account number start). `ALTER TABLE` / new tables only;
  never drop or recreate; existing rows stay exactly as they were. A new migration needs a test that opens a database
  hand-built at the previous version (see `BankMigrationTest`, `PeopleMigrationTest`, `PictureMigrationTest`).
- Backup formats are versioned and **additive**; old files must still restore:
  full finance backup `FinanceBackupCodec.FORMAT_VERSION = 2` (`.json`, or `.zip` with pictures); app backup
  `AppBackupManifest.VERSION = 1` (zip: finance json, pictures, `app/manifest.json`, `app/settings.json`,
  `app/reminders.json`, `app/profile/`, `app/fonts/`, `app/cards.json`); cards file `CardsTransfer.VERSION = 1`
  (AES-256-GCM, PBKDF2 password, min 8 chars); CSV export. Reading validates first, nothing is written until a snapshot
  validated cleanly, entry names must be plain relative paths, sizes are capped. Restore asks before replacing.
  `update.json` (published by CI) has `schema: 1`.
- Threads: finance and reminders DB work run on dedicated single threads (`normpix-finance-db`, `normpix-reminders-db`);
  repositories take a `dispatcher` parameter so tests run them in place (`Dispatchers.Unconfined`).

## 4. Money model

- Amounts are integer **minor units** (paise); `CurrencySpec` (`finance/domain/Money.kt`). Only **INR is selectable**
  (`CurrencySpec.active`); the other currencies stay in the catalogue for display/restore. `MAX_MAJOR_DIGITS = 12`.
  Indian digit grouping (1,00,000). `MoneyFormatter` honours "amounts hidden".
- `TransactionType` expense | income | transfer. A transfer has a `to_account_id` different from `account_id` and no
  category; expense/income have a category and no `to_account_id`. Only an expense may name a person.
- `AccountKind`: cash, bank, card, wallet, savings, other. A fresh install seeds one **Cash** account and a ready-made
  category set (29 top-level families plus sub-categories, `FinanceDatabase.defaultCategories`,
  `CategorySuggestions`). Balance = opening + income - expense + transfers in - out (`finance/domain/Ledger.kt`);
  a credit card's negative balance is what is owed.
- Categories: two levels, same type, a child takes its parent's colour, archive cascades, delete is refused while children
  exist; filtering by a parent includes its children; budgets fold children into the parent (`BudgetOverview.nested`).
- Budgets are per category per `effective_month` (and an overall one), with history. Recurring rules
  (`RecurrenceFrequency` daily/weekly/monthly/yearly, interval, `RepeatEnd` Never/OnDate/After) record themselves on their
  day and advance `next_date`.
- **"As of today"** (`settledCutoff`, `settledBy` in `Period.kt`, `FinanceState.asOf`): an entry dated after today is
  *upcoming*; it is not in balances, "spent this month", budgets, Top spending, Insights or People until its own day, and
  Activity folds such entries into an "Upcoming" row. Past months are untouched. Repository calls take `asOf`.
- Banks: accounts and cards share one `BankCatalog` (built-in `BankDirectory` entries + the user's own, `banks` table;
  stable keys: directory id or `custom-<uuid>`). The bank is the relationship; an account may optionally name its Wallet
  card (`card_id`, one card per account), and may carry `number_ending` (last 4) and `number_start` (first 6) so a receipt's
  digits can be matched.
- "Check and recalculate" (`LedgerCheck.kt`, `FinanceRepository.checkAndRepair`) repairs a fixed list of inconsistencies in
  one SQL transaction with Undo and only *shows* the ambiguous ones (see `features.md`).

## 5. UI wiring and navigation

- `NormPixRoot` (navigation/) sets up theme, lock gate, `OverlayHost`, snackbars, the page blur, the splash and the
  `NavHost`. `AppShell` is the tab host: **Home, Plans & Activity, Insights, Wallet** (`AppDestination`), a floating dock
  (`FloatingNavBar`) on phones and a rail on wide windows, hiding on scroll (`ScrollAwareBar`). `ShellState` (hoisted:
  tab index, section, plans tab) survives navigation. Settings is not a tab.
- Routes (`Routes` object in `NormPixRoot.kt`): `main`, `categories`, `budget-history`, `people`, `person/{id}`,
  `category/{id}/{month}?from&to`, `account/{id}`, `settings`, `settings/{page}`, `appearance/{section}`, `profile`,
  `search`, `reminder/{id}`, `update`, `changelog`, `about`, `about/{topic}`.
- Screens depend on the `AppNavigator` interface (`navigation/AppNavigator.kt`), not on the navigation library;
  `NavControllerNavigator` implements it. Newer methods have a default body (`= Unit` or a fallback) so test doubles keep
  compiling; **a new abstract method must also be added to `RecordingNavigator`** (in `testing/TestEnvironment.kt`).
- Composition locals: `LocalAppContainer`, `LocalAppPreferences`, `LocalNavigator`, `LocalShellState`, `LocalOverlays`,
  `LocalAppScope`, `LocalSnackbars`, `LocalWalletCards`; `rememberFinanceState()` for `FinanceState`;
  `rememberFinanceActions()` for `FinanceActions` (addTransaction, editTransaction, deleteTransaction, showDetails,
  addAccount, manageAccount, addCategory/addSubCategory, addPerson/managePerson, addRecurring/editRecurring,
  addFromShare, payBill). Flows are imperative: they open sheets through `LocalOverlays` and read like straight-line code.
- Controllers: `FinanceController` (`finance/ui/`) holds the small always-needed reference data as `FinanceState`
  (accounts, categories, balances, banks, people, currency, `revision` that increments on every change, `asOf`,
  `upcomingCount`); everything else (history, totals, budgets, charts) is queried from the repository by the screen that
  shows it. `RemindersController`, `CardsController` (state `Loading/Ready/...`).
- Page transitions (`PageTransitions`, `LinuxWindowMotion`) follow `PageAnimationType`; sheets follow
  `SheetAnimationType`; reduced motion removes the app's own motion. Details in `design-system.md`.
- Wide windows branch on window size only (`WindowSize`, `RailStyle`, `isListDetail`, `LocalDetailPane`,
  `LocalDockHosted`, `LocalRailCollapsed`); the phone path is the default and must not change.

## 6. Preferences, and how to add a setting

`settings/AppPreferences.kt`: `AppPrefsState` is an immutable data class exposed as `state: StateFlow`; setters call
`change({ copy(...) }) { putX(KEY, v) }`. Choice types are enums carrying `label` and `description`
(`PageAnimationType`, `SheetAnimationType`, `BackButtonStyle`, `ProgressStyle`, `MonthControlStyle`, `TopSpendingLayout`,
`AccountsLayout`, `CardsLayout`, `AppLockScope`, `AppLockTimeout`, `ClipboardTimeout`, `AccentColor`, `InterfaceFont`,
`FontPreset`, `ThemeMode`, ...). Defaults (as of 2026-10-26): theme follows the system; accent **Volt** (when wallpaper
colours are off/unavailable); page animation **Linux window**; back button **Top floating circle**; bottom controls off;
month control **Track**; progress **Wavy**; accounts layout **Stack**, cards layout **Cards**, Top spending **Stack**;
category icons **3D** with shape **None**; font **GS Flex, Zenith preset**; sheet blur on; swipe actions on (edit + delete);
NFC card exchange **Off** (`ScheduledFeature.defaultMode`); wallet lock off (scope Wallet only, timeout immediate);
Save CVV and Store cards on; clipboard clears after **10 s**; amounts shown; notifications on; update checks on (daily,
also at launch), background updates **download then ask**, Wi-Fi only. Read `AppPrefsState` for the full list.

To add a setting, touch these places (the `bottomControls` setting is the worked example):

1. A field with its default on `AppPrefsState`.
2. Read it in the loader (`prefs.getX(K_..., defaults.x)`).
3. A setter using `change({ copy(...) }) { putX(K_..., v) }`.
4. `const val K_... = "snake_case_key"` in the companion. **Keys are persisted: never rename one.**
5. The UI row: Appearance rows live in `settings/ui/AppearanceSectionPages.kt` (a screen per `AppearanceSection`) with a
   summary line in `AppearanceSettingsPage.kt`; other pages are `FinanceSettingsPage`, `DataSettingsPage`,
   `WalletSettingsPage`, `NotificationsSettingsPage`, `AvailabilitySettingsPage`, `SecuritySettingsPage`,
   `UpdatesSettingsPage`, built from `SettingsRows.kt`.
6. A `SettingsEntry(title, page, keywords, appearance = ...)` in `settings/ui/SettingsCatalog.kt` so Settings search finds it.
7. Backup is automatic: `exportAll()` returns every stored key except `BACKUP_EXCLUDED`, so a new key travels with an
   app backup without anyone listing it. Add to the exclusion list only device-specific or secret keys.
8. Tests: a round trip including `exportAll()["key"]` and a UI test (`BottomControlsSettingTest`).

A new default must not change what an existing user already chose: only explicit picks are stored, and a default is
resolved at read time (`SheetAnimationType.defaultFor`, `ScheduledFeature.defaultRule`).

## 7. Settings UI

- `SettingsPage` = Appearance, Widgets, Finance, Banks, Data, Wallet, Notifications, Availability, Security, Updates. A new page
  needs its title and summary (`SettingsScreen.kt`), hue (`SettingsRows.kt`), icon and search entries (`SettingsCatalog.kt`)
  and its page in `SettingsPageContent` (`SettingsTwoPane.kt`); the build says what is missing. Settings has a search
  field over `SettingsCatalog.entries`. On a wide window Settings is a list beside its page (`SettingsTwoPane`,
  `SettingsListPane`, `SettingsDetail`), including About and its topics.
- `AppearanceSection` = Colors, AppIcon, InterfaceFont, CategoryIcons ("Icons"), ProgressIndicators, Transitions, Effects,
  Controls ("Navigation & controls"), Layouts. **The enum name is a route key: never rename it**; a screen folded into
  another keeps its old name as an alias in `AppearanceSection.named`. A new entry must be given its group, icon, summary
  and screen in `AppearanceSectionPages.kt` (the build says what is missing). The Colors row is the only inline switch row
  (`ColorsSwitchRowTest` pins that).

## 8. Test infrastructure

- ~2,200 `@Test`s across ~240 files in `app/src/test/kotlin/...` mirroring the main packages: pure JVM tests for domain
  and parsers, Robolectric + Compose tests for screens. There is no `androidTest/`; instrumented tests do not exist.
- `testing/TestEnvironment.kt`: an `AppContainer` on **in-memory databases** with `Dispatchers.Unconfined`, a throwaway
  prefs file, `InMemoryVault`, `RecordingClipboard`, `RecordingReceiptSharer`, `FakeNfcTagReveal`, `RecordingNavigator`,
  `ShellState`, and `Content { }` which wraps a screen in `NormPixTheme` + `ProvideAppEnvironment`. A fresh environment
  already has the default **Cash** account.
- `robolectric.properties` sets `conscryptMode=OFF` (Conscrypt can't verify the JDK's JCE certificate on newer JDKs and
  breaks Cipher use after a Robolectric test).
- `ReadmeScreenshots.kt` renders the README pictures from the real screens with sample data (skipped unless
  `-Pscreenshots=` is passed; CI's `screenshots.yml` runs it).
- Test-writing traps and the local run loop are in `build-test-release.md`.
