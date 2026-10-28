# Keeping this skill true

The skill only helps if agents can trust it instead of re-reading code. A stale skill is worse than none, so **updating it is part
of finishing a code change**, in the same commit.

Contents: 1 The stamp and the machine-checked facts · 2 What to update when · 3 How to update · 4 The Stop hook · 5 Style rules

## 1. The stamp and the machine-checked facts

`scripts/verify.sh` compares the values below with the code, checks that every identifier in the symbols block still exists, that
every file path cited in the skill still resolves, and that every top-level package is in `SKILL.md`'s map. Run it after any
change that might move these:

```bash
bash .claude/skills/normpix/scripts/verify.sh          # full check
bash .claude/skills/normpix/scripts/verify.sh --quick  # facts + packages only (what the hook runs)
```

When a fact drifts: fix the prose that mentions it **first**, then change the value here to the new truth. Keys marked
`verified_*` are the stamp (not checked against code): bump them whenever you review the skill, including when you find that
nothing needed changing.

<!-- facts:begin -->
verified_commit=46665c2c
verified_date=2026-10-28
versionName=1.5.0
finance_db_version=12
reminders_db_version=2
finance_backup_format=2
app_backup_version=1
cards_file_version=1
min_sdk=26
target_sdk=35
compile_sdk=37
kotlin=2.4.0
agp=9.1.0
compose_bom=2026.09.00
gradle=9.3.1
robolectric=4.17
launcher_icon_aliases=10
guide_topics=12
<!-- facts:end -->

Identifiers the skill names (each must still exist somewhere under `native/app`):

<!-- symbols:begin -->
AppContainer
DummyData
FinanceController
FinanceRepository
FinanceDatabase
TransactionQuery
settledCutoff
checkLedger
combineSelfTransfer
PaymentAppReceipts
SharedTransactionParser
ReceiptLayout
rupeeRestored
BrandedReceipts
SharedTextInbox
MlKitSharedPictureReader
CardsPolicy
CardsController
SecureClipboard
RegisteredTags
NfcTagReader
NfcCardOutletService
ScheduledFeature
CardBankLinker
BankCatalog
AppOverlays
EditorSheet
DiscardChangesGuard
ConfirmSheetContent
ConnectedChoiceRow
DeckFacts
SwipeRevealRow
AppSplitButton
AppLinearProgress
AppLoadingIndicator
SeeThroughColors
solidColorScheme
rememberWindowBlurAvailable
frostedGlass
frostedSource
blursBackdrop
rememberEasedBlur
blurredBy
rememberBottomControls
LocalOverlays
LocalAppScope
AppNavigator
NavControllerNavigator
RecordingNavigator
Routes
ShellState
AppearanceSection
SettingsCatalog
SettingsEntry
AppPrefsState
exportAll
BACKUP_EXCLUDED
PageAnimationType
SheetAnimationType
BackButtonStyle
AccentColor
GuideTopic
GuideArt
WidgetRequest
WidgetIntents
DropTelemetryBackend
UpdateJobService
UpdateDownloadService
ReleaseMetadata
ChangelogPill
TestEnvironment
AppBackupArchive
CardsTransfer
FinanceBackupCodec
<!-- symbols:end -->

## 2. What to update when

| If you changed ... | Update |
|---|---|
| `native/app/build.gradle.kts`, `native/gradle/libs.versions.toml`, wrapper, `gradle.properties`, `proguard-rules.pro` | `architecture.md` section 1; the facts block |
| `AndroidManifest.xml` (components, permissions), `network_security_config.xml` | `architecture.md` section 2; `SKILL.md` hard rules if privacy-relevant |
| A database schema/version, a backup/cards/update.json format | `architecture.md` section 3; the facts block; "Hard rules" if a rule changes |
| `AppContainer`, `NormPixApplication`, `MainActivity`, `DummyData` | `architecture.md` section 2 |
| Money/ledger rules, `finance/domain`, `FinanceRepository` | `architecture.md` section 4; `features.md` sections 1-2 |
| `finance/share` (readers, matching, self transfers) | `features.md` section 3 |
| `wallet/`, `availability/`, lock, clipboard, NFC | `features.md` section 4; `SKILL.md` hard rules if privacy-relevant |
| `reminders/`, `wallet/expiry` | `features.md` section 5 |
| `backup/`, `LedgerCheck`, sample data | `features.md` section 6 |
| `updates/`, `.github/workflows/` | `features.md` section 7; `build-test-release.md` section 7 |
| `widgets/`, `about/` | `features.md` sections 8-9 |
| `settings/` (new setting, default, page), `SettingsCatalog`, `AppearanceSection` | `architecture.md` sections 6-7; `features.md` section 10 |
| `navigation/` (routes, shell, navigator interface) | `architecture.md` section 5 |
| `ui/components`, `ui/theme`, blur, sheets, motion | `design-system.md` |
| A new top-level package | the package map in `SKILL.md` (verify.sh fails otherwise) |
| Test infrastructure (`testing/`, `robolectric.properties`) | `architecture.md` section 8; `build-test-release.md` section 5 |
| Git/CI/release/signing process, or the owner's branch/PR rules | `build-test-release.md` sections 7-9 |
| The owner corrected you, or gave a standing instruction | `owner-working-style.md` (and `SKILL.md` hard rules if it is a rule) |
| You verified a library/platform fact | `verified-facts.md` |
| You finished a backlog item or the owner parked a new one | `features.md` section 11 |

A refactor that changes no behaviour and no named identifier needs no edit: bump the stamp and move on.

## 3. How to update

1. Edit the reference file(s) from the table, and `SKILL.md` only if the map, a hard rule or the read-order table changed.
2. Run `verify.sh`; fix drift. If you renamed an identifier the skill cites, change the prose and the symbols block together.
3. Bump `verified_commit` (the current `git rev-parse --short HEAD`, or the base commit when work is uncommitted) and
   `verified_date`.
4. Stage the skill with the code in the same commit. The skill is part of the change, not a follow-up.

## 4. The Stop hook

`.claude/settings.json` registers a `Stop` hook that runs `.claude/hooks/skill-sync.sh` when an agent finishes a turn. If source
files that the skill describes (`native/app/src/main`, the Gradle build files, `.github/workflows`) have uncommitted changes
**newer than the newest skill file**, or `verify.sh --quick` reports drift, it blocks the stop once with the list and asks you to
review the skill (edit it, or bump the stamp if nothing needed changing). It fails open (any error means it does nothing) and asks
at most once per turn. To switch it off, remove the `Stop` entry from `.claude/settings.json`.

## 5. Style rules for the skill

- State facts and the *why*; leave history to `native/TASKS.md` and git. Name identifiers and files, not line numbers.
- Don't copy numbers that change every week (test counts, line counts) unless `verify.sh` checks them.
- Never put secrets, keystore passwords, device serial numbers, real names, account numbers, UPI ids or receipts in the skill.
- Keep `SKILL.md` under ~250 lines; push detail into `references/` and say when to open each file.
- If you are unsure a statement is still true, check it (`grep`) or delete it; don't leave a guess that reads as a fact.
