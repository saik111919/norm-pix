---
name: normpix
description: "Everything about the NormPix project - a private, offline-first Android personal-finance and card-wallet app (Kotlin, Jetpack Compose, Material 3 Expressive) in native/ - so an agent does not have to re-read the code: architecture and code map, feature rules, design system, build/test/CI/release/git workflow, the owner's working style, and when to delegate research to Haiku 5.5 xhigh plus Gemini 3.8 Flash High. Use this skill FIRST for any NormPix task: adding or changing a feature, UI or design work, bugs, failing builds or tests, receipt sharing, wallet/cards/NFC, backups, updates, settings, tablet layouts, widgets, PRs and releases, 'continue where we left', 'what is left in TODO', or any question about how the app works."
---

# NormPix project skill

Stamp: verified against commit `c99a54e2` (v1.4.0) on 2026-10-27. If `git log` shows newer commits on source paths, or
`bash .claude/skills/normpix/scripts/verify.sh` reports drift, trust the code and fix the skill (`references/maintenance.md`).

## 1. The 60-second picture

NormPix is a **native Android app** (Kotlin 2.4, Jetpack Compose, Material 3 Expressive; minSdk 26, targetSdk 35) for personal
money and cards: record spending by hand or by **sharing a payment app's receipt** into it (read on-device by ML Kit), keep
budgets, repeating payments and reminders, see where the money went, and hold cards in an **encrypted vault** unlocked by
biometrics or the user's own NFC tag. It is **offline-first and private**: no account, no server, no analytics; the one
connection is to GitHub to check for its own updates. The owner is one person; releases come out of `main` through CI and the
app updates itself from GitHub.

- Code lives in `native/` (single Gradle module `:app`, package `com.example.normpix`, ~270 main and ~240 test Kotlin files,
  ~2,200 tests). No DI framework, no Room: plain `AppContainer`, raw SQLite, SharedPreferences. All UI text is hard-coded
  English in Kotlin (no string resources). Currency: INR only (amounts are integer paise).
- The rest of the repo: `.github/workflows/` (CI), `docs/` (README pictures), `lib/` (a leftover Flutter widget, ignore),
  `native/TASKS.md` (dated work log + Backlog), `README.md` (user-facing description, install, release process).
- Version: `versionName` in `native/app/build.gradle.kts` (1.4.0 on 2026-10-26). A push to `main` publishes a release.

## 2. Read order: open the file for your task, not the code

| Your task | Open |
|---|---|
| Structure, stores, DI, navigation, "how do I add a setting / route / service", tests infrastructure | `references/architecture.md` |
| What a feature does, its rules and why (money, receipts, wallet, reminders, backups, updates, widgets, About, Settings) | `references/features.md` |
| Any visual change: sheets, messages, blur, motion, accessibility, tablets, themes | `references/design-system.md` |
| Run/fix tests, lint, CI, releases, signing, branches, PRs, device QA, Windows quirks | `references/build-test-release.md` |
| How the owner asks, what their phrases mean, how to reply, handoffs, the TASKS log, model tiers | `references/owner-working-style.md` |
| You need a fact about an API, library or design spec that you don't have | `references/research-protocol.md`, then `references/verified-facts.md` |
| Updating this skill | `references/maintenance.md` |

Then read code **only** to change it or to confirm a detail the skill doesn't state; open the specific file named in the
reference rather than exploring. For the history or reasoning behind a feature, `Grep` `native/TASKS.md` for its name (it is a
680-line phase log; don't read it whole).

## 3. Hard rules (each one cost the owner something when broken)

1. **Privacy.** Only GitHub is ever contacted (`network_security_config.xml` refuses every other host; ML Kit telemetry is stripped
   at build time). No SMS, notification-listener or analytics. **Never log or store** card numbers, CVVs, clipboard contents,
   account numbers, UPI ids or references. Release builds set `FLAG_SECURE`.
2. **The owner's data is sacred.** Database migrations are additive (`ALTER TABLE`/new tables behind `if (oldVersion < N)`), never
   drop or recreate. Backup/cards/update formats are versioned and additive; old files must still restore; validate before writing.
   Never rename a persisted preference key or an `AppearanceSection` name (they are keys/routes). Sample data is in-memory only.
3. **Money.** Integer minor units; INR is the only selectable currency; every figure is "as of today" (future-dated entries are
   *upcoming*); a credit-card bill payment is a transfer, not spending; amounts can be hidden.
4. **UI.** The phone design is the reference: tablet/wide work branches on window size and never restyles the phone. Decisions,
   forms and pickers are **bottom sheets**; never snackbar from inside a sheet (say it in the sheet; slow work goes in
   `overlays.loading`); reuse existing components (`EditorSheet`, tonal fields, `ConnectedChoiceRow`) before inventing. 48 dp
   targets, TalkBack descriptions, never colour alone, holds at 1.6x text, honours reduced motion. Work in Light, Dark, Pure and
   wallpaper colours.
5. **Options, not replacements.** When the owner says "keep the old design", add a Settings choice. A new default must not change
   what an existing user already picked.
6. **Things only the owner can supply.** Per-bank card designs (blocked on their designs), real receipts (build fixtures from real
   recognizer output with personal details swapped; never commit real names/numbers), the release keystore (never regenerate,
   replace or commit it).
7. **Devices.** Test with the `qa` build only; never install over, open or touch the owner's real NormPix or its data; clean up
   dumps and copies afterwards.
8. **Tooling on this Windows PC.** Sources are mostly CRLF: use the Edit tool for multi-line edits, grep after scripted edits.
   "BUILD FAILED" with no failing test means the tests didn't run (rerun with `--no-build-cache`); run `lintDebug` alone; check
   that no other agent is running Gradle in the tree; `kotlin.incremental` is off on purpose.
9. **Git.** Commit, push, open or merge PRs **only when asked** (and "raise PR and merge it if everything is good" means verify
   first, merge only on green). Check `git branch --show-current` and `git status` first; the owner uses the same checkout and
   may switch branches. Features go to `features`, bugs to `bugs`, nothing to `main` without the owner's go. A push to `main` ships.
10. **Prove it.** Run the affected tests and read the result files; render UI changes and look at the image; say plainly what you did
    not verify (especially on a phone). Never report a skipped or flaky run as a pass.
11. **Registries to keep in step.** New `AppContainer` service -> also `DummyData`. New `AppNavigator` method -> also
    `RecordingNavigator`. New setting -> key + setter + row + `SettingsCatalog` entry + test. User-visible feature -> its About guide
    topic. New top-level package -> the map below. Significant work -> a dated entry in `native/TASKS.md`.
12. **Don't re-derive, don't guess.** If this skill answers it, use it. If you lack a fact about an API/library/spec, follow
    `references/research-protocol.md` (below). If a statement here turns out wrong, fix it.

## 4. Code map (`native/app/src/main/kotlin/com/example/normpix/`)

| Package | What lives there |
|---|---|
| (root) | `MainActivity` (intents, lock lifecycle, FLAG_SECURE), `NormPixApplication` + `AppContainer` (all services), `DummyData` (sample-data container) |
| `finance/` | The money core, in four layers - |
| `finance/data/` | `FinanceDatabase` (SQLite v12), `FinanceRepository` (all queries/commands, `TransactionQuery`, `checkAndRepair`), backup codec/archive, `TransactionPictures`, `CategorySuggestions` |
| `finance/domain/` | Pure rules: `Entities`, `Ledger` (balances, budgets), `Period` (months, "as of today"), `Insights`, `LedgerCheck`, `BillingCycle`, `CardBills`, `Recurrence`, `RepeatEnd`, `Money`/`CurrencySpec`, `CategoryTree` |
| `finance/share/` | Receipts shared in: `ShareTransactionActivity`, `SharedPictureReader` (ML Kit), one reader per payment app + `ReceiptVocabulary`, `SharedTransactionParser` fallback, `AccountMatching`, `SelfTransfers`, `SharedTransactionImport` |
| `finance/ui/` | `FinanceController`/`FinanceState`, `FinanceActions`, `TransactionsScreen`, `InsightsScreen`, `TransactionEditor`, `RecurringEditor`, forms/pickers/selectors, categories/people pages, month/day/date-range controls, charts |
| `wallet/` | Cards and accounts as cards: `CardVault` (AES-GCM, Keystore), `CardsController`, `CardsPolicy`, `AppLock`, bank directory/designs/logos, `CardStack`/`CardCarousel`/`AccountCarousel`, `CardScan`, `CardsTransfer`, `WalletOverview` |
| `wallet/nfc/` | `NfcTagReader` (add by tap), `NfcCardExchange` + HCE `NfcCardOutletService`, tag registration, tap guide/sheets |
| `wallet/clipboard/` | `SecureClipboard` (per-copy token, timed clear), copy sheet |
| `wallet/expiry/` | Card expiry tracker, alarms, notices |
| `wallet/motion/` | `CardTilt` |
| `reminders/` | Model, `RemindersDatabase` (v2), alarms, notifications, alert screen, links |
| `screens/` | The tabs' screens: `HomeScreen`, `HomeBanners`, `PlansAndTransactionsScreen`, `WalletScreen` |
| `navigation/` | `NormPixRoot` (`Routes`, `ShellState`, locals), `AppShell`, `AppNavigator`, `FloatingNavBar`, `PageTransitions`, `LinuxWindowMotion`, `AppSearch`, `AppearanceSection`, splash |
| `settings/` | `AppPreferences` (`AppPrefsState`, all choice enums), app icons, notifications, custom font, profile photos |
| `settings/ui/` | Settings pages, `SettingsCatalog` (search), `SettingsTwoPane`, Appearance sections, backup UI, ledger-check UI |
| `ui/` | The design system, nothing app-specific - |
| `ui/components/` | Design-system components: `Overlays` (sheets/confirm/loading/snackbars), `Sheets`, `Pages`, `Lists`, `DeckCarousel`, `SwipeReveal`, `Progress`, `SplitButton`, `BottomControls`, glass/blur |
| `ui/theme/` | `Theme` (palettes), `Tokens`, `Typography`, `StatusColors`, `AppIcons` |
| `updates/` | `AppUpdater`, `ReleaseMetadata`, downloader/installer/background job/foreground service, `Changelog` |
| `updates/ui/` | Update page, changelog page, Home update banner |
| `banks/` | `BankCatalog` (built-in + custom banks, shared by accounts and cards) |
| `banks/ui/` | `BankSelector`, `BankBadge` |
| `availability/` | `ScheduledFeature` + weekly schedule rules (NFC exchange), settings widgets |
| `backup/` | App backup zip: archive, manifest, reader, restorer, settings codec |
| `widgets/` | Watch-dial and cookie clocks, Recent, Add widgets, `HomeWidget` (the Settings > Widgets list, previews, pinning), `WidgetIntents`/`WidgetRequest` |
| `about/` | About page and the in-app guide (`GuideTopic`, `GuideArt` drawn in code) |

Outside it: tests mirror these packages in `native/app/src/test/kotlin/...` (`testing/TestEnvironment.kt` is the harness);
`native/app/src/qa/` is the qa build's manifest label; `native/design/` generates icons and widget art (`clock-widget/` Python, `cookie-clock/` Java: `java CookieFace.java`); `.github/workflows/`
(`android.yml` verify/build/release, `screenshots.yml`).

## 5. Working with the owner (full detail: `references/owner-working-style.md`)

- Messages are short, informal and context-assuming ("continue where we left", "this is not looking good", "also ..."). Rebuild
  context yourself, never ask them to restate, split multi-part messages into a checklist and report each item, and ask only for
  decisions that are truly theirs.
- Screenshots are the spec; reference apps are inspiration to be rebuilt in this design system; "follow the design pattern" means
  reuse what exists; "keep the previous design" means add an option.
- They expect end-to-end results (reproduce, fix, test, verify, and ship when they say so) and an honest account of what was and
  wasn't verified. Lead with the result in plain words.
- Models for work: Haiku 5.5 for low, Sonnet 5.5 for medium to high, Opus 5.5 for complex (not Haiku for medium/hard).

## 6. When you need facts you don't have (full detail: `references/research-protocol.md`)

| Situation | Route |
|---|---|
| Unfamiliar API, library or design specification | Research with **Haiku 5.5 at xhigh** and **Gemini 3.8 Flash (High)** in parallel; tell both not to hallucinate; fact-check; then continue with the change |
| Complex or version-sensitive technical decision | Use **xhigh** effort when its additional reasoning is justified |
| Already in this skill or the repo | Don't research; read it or `grep` |

In T3 Code: two `delegate_task` calls in one message (`claudeAgent` / `claude-haiku-5-5` with `options.effort = "xhigh"`;
`antigravity` / `gemini-3.8-flash-high`), `role: "research"`, `mode: "wait"`, read-only brief with the anti-hallucination rules in
the reference. Fact-check disagreements and uncited claims against the repo, the pinned library sources (Gradle cache sources
jar), official docs, or a compile before relying on them; record verified facts in `references/verified-facts.md`.

## 7. Keeping this skill true (full detail: `references/maintenance.md`)

This skill is part of the code. When a change alters something it states, update the matching reference **in the same commit**
(`references/maintenance.md` has the code-area -> section table), run `bash .claude/skills/normpix/scripts/verify.sh`, and bump
`verified_commit` / `verified_date`. If a change needed no skill edit, bump the stamp anyway. A `Stop` hook
(`.claude/hooks/skill-sync.sh`) reminds you once per turn when source files are newer than the skill. Record the owner's
corrections in `references/owner-working-style.md`.
