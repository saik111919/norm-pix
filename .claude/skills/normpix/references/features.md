# NormPix features, rules and decisions

Verified 2026-10-27 against commit `763cc08f`. This is the *behavioural* picture: what each feature does, the rules it
obeys and the decisions behind them. For the dated history of any feature, grep `native/TASKS.md` for its name (do not read
that file whole: it is ~680 lines of phase log; the last entries are Phases 33-36, 2026-10-26).

Contents: 1 Screens · 2 Money rules · 3 Reading receipts shared into the app · 4 Wallet, cards, NFC and privacy ·
5 Reminders and card expiry · 6 Data safety: backup, restore, check, sample data · 7 Updates · 8 Widgets · 9 About guide ·
10 Appearance catalogue · 11 Open and parked

## 1. Screens

- **Home** (`screens/HomeScreen.kt`, `HomeBanners.kt`): month overview hero (spent / left / used, progress), **Needs
  attention** banners (overdue, update available, ...; the page's own tonal surface, status said by icon and eyebrow colour,
  never colour alone), quick add (expense / income / transfer), coming up, **Top spending** (stack or carousel by
  `TopSpendingLayout`), cards on Home, **Recent** (the latest *as of today*; "See all" opens the whole history, All time),
  budgets tile. The account pill / profile button at the top opens Settings (or Profile, by choice).
- **Plans & Activity** (`screens/PlansAndTransactionsScreen.kt`, `finance/ui/TransactionsScreen.kt`): two tabs.
  *Activity* = the transaction history: month switcher (two looks, `MonthControlStyle` Track | Split), **All time** scope,
  **by day** (`DaySwitcher`; a day heading shows what went out and opens that day), an **Upcoming** row for entries dated
  after today, search + filters (type, account, category, person, amount, dates: `TransactionFilterSheet`,
  `DateRangeSheet`), swipe Edit/Delete (`SwipeableTransactionRow`, a setting), details sheet with Delete/Edit and the
  attached picture. *Plans* = Budgets (per category + overall, history page, sub-categories fold into the parent),
  Recurring (`RecurringView`/`RecurringEditor`; rows record themselves on their day), Reminders (`RemindersView`).
- **Insights** (`finance/ui/InsightsScreen.kt`, `finance/domain/Insights.kt`): a story - snapshot hero (one sentence of how
  this compares and with what, two bars on one scale), a single-series multi-month trend, ranked category rows with share and
  a link to their transactions, "More detail" collapsed (day by day, income sources). A month in progress is compared
  like-for-like (`comparisonRange`). Custom date ranges.
- **Wallet** (`screens/WalletScreen.kt`, `wallet/`): Accounts (layouts Stack | Cards | Rows | Carousel) and Cards (layouts
  Stack/Cards/Rows/Carousel), each with its own **"+ Add"** beside its heading (no dashed add tiles). NFC buttons show only
  while NFC is usable (section 4).
- **People** (`PeoplePage`, `PersonDetailPage`): label expenses with who they were for; totals per person. **Categories**
  (`CategoriesPage`): two levels, ready-made set, 3D / emoji / outline icons with brand logos, archive, reorder.
- **Search** (`navigation/AppSearch.kt`): transactions, accounts (also by their bank's name), categories, people, settings.
- **Settings** (`settings/ui/`): Appearance, Finance, Data & backup, Wallet & Cards, Notifications, Availability &
  Schedules, Security & privacy, Updates & changelog, plus About, Profile (name, emoji or own photo), Categories, People.
- **Update page / Changelog page** (`updates/ui/`): see section 7. **Reminder alert screen** (`reminders/ReminderAlertScreen`).

## 2. Money rules

- See `architecture.md` section 4 for the model. Rules worth knowing before touching totals:
  - Every figure is **as of the end of today** (upcoming entries count from their own day). The controller works things out
    again on the first resume of a new day.
  - A budget on an *archived* category stops adding its unspent limit to "Left to spend" from the current month on.
  - A card's "% used" follows the app's one percent rule (`percentLabel`), not truncation.
  - Paying a credit card's bill is a **transfer** (to the card account), not spending. Card bill recorded as spending is
    flagged by Check and recalculate ("Record as a payment onto ..."). Credit cards carry a limit, statement day and due day
    (`BillingCycle`, `CardBills`); `PayBillSheet` records the payment.
  - An expense and the same income on another of the user's accounts within a day can be combined into a transfer
    (`combineSelfTransfer`).
  - Duplicate detection for shares: a differing reference is never a duplicate; without references the window is +-3 hours
    when the time is known, the same day otherwise (`SAME_PAYMENT_WINDOW`).
- **Check and recalculate** (Settings > Data & backup): `checkLedger` (pure, `LedgerCheck.kt`) and
  `FinanceRepository.checkAndRepair`, under a non-cancellable loading sheet. It fixes in one SQL transaction (with Undo): a
  sub-category whose parent is gone/archived/of the other kind; a budget on an income category; a recurring rule whose next
  date was already recorded; a default account archived or gone; then records repeats that were due. It only *shows*: entries
  dated after today, the same payment twice in a day, a card bill recorded as spending, an expense + matching income
  (make it a transfer), archived accounts holding money, entries under a category of the other kind.

## 3. Reading receipts shared into the app

Flow: another app shares text or a picture to NormPix (`ShareTransactionActivity`, exported, no UI) -> `SharedTextInbox`
(memory only) -> MainActivity -> the transaction editor opens with a **draft the user checks and saves**; nothing is saved
unseen. Pictures are read on the device by ML Kit (`MlKitSharedPictureReader`); the same picture can be attached to the
transaction and read again to fill the form.

- Recognizer reality (not what the screen looks like): line order is **scrambled**; the rupee sign is read as `7`, `Z` or
  dropped (`rupeeRestored` uses ML Kit's per-symbol confidence: a misread symbol scores ~0.07-0.12, real digits 0.6-0.95);
  the user's bank can be a logo only; "Powered by UPI <PSP bank>" is not the user's bank. So `ReceiptLayout` rebuilds rows
  from bounding boxes; readers never assume label-then-value adjacency.
- **One reader per payment app** (`PaymentAppReceipt`, `finance/share/PaymentAppReceipts.kt`): PhonePe, Paytm, Kredit.Pe,
  Google Pay, CRED, PayZapp, SBI, `MutualFundOrderReceipt` (Groww-style order details) and `BrandedReceipts.all` (brand-only
  apps via `LabelledReceipt`). A reader is chosen by printed signatures (`tells`: labels, logo rows, id shapes), never by
  values. Shared vocabulary is `ReceiptVocabulary.kt`. A new app = a new reader object added to `readers`, tested with a
  fixture built from real phone geometry. `SharedTransactionParser` is the **fallback** when no app matched or the reader
  found neither amount nor payee. Google Pay names its shared file `<id> - 30.00 To <payee> on Google Pay.png`
  (`OpenableColumns.DISPLAY_NAME` is the most reliable source of amount, payee and direction).
- **Accounts first, then cards**: `findAccount` matches the account by the digits shown (an account's `number_ending`, or its
  linked Wallet card's number prefix + suffix; at least 3 shown digits, 2 digits are "Weak"), then by bank; an account whose
  known number contradicts the shown one is out. A **credit card's last 4 digits are not enough on their own**: the first 6
  (`number_start`) help. No match + a number shown + more than one active account -> the form starts with no account chosen
  and an attention notice. **Never guess: empty beats wrong.**
- **Self transfers** (`SelfTransfers.kt`): a share becomes a Transfer when any cue holds - the other side's number matches
  another of the user's accounts; words ("self transfer", "to self", "own account"); the same person on both sides - and the
  other account can be found. Readers fill `selfTransfer`, `otherAccountText`, `otherMaskedNumbers` (phone numbers and UPI ids
  are dropped). Paying a friend at the same bank is a payment, not a transfer. Unknown other side -> left unchosen with a
  notice; fewer than 2 accounts -> stays Expense/Income with a notice.
- Category suggestion order: the user's own last entry for the payee, the app's merchant table (`MerchantCategories`), else
  the word the payer wrote (`CategoryOrigin`).
- Test fixtures (`PhoneReceipts.kt`) keep the phone's real boxes with personal details swapped for stand-ins. Self-transfer
  fixtures are still *synthetic* (no app publishes a self-transfer receipt); rebuild from a real one when the owner shares it.
  Never commit real names, UPI ids, account numbers or references.

## 4. Wallet, cards, NFC and privacy

- Cards live only in the encrypted vault (section 3 of architecture). `StoredCard` has number, expiry, holder, CVV (optional),
  network, bank key. **`CardsPolicy`** (Settings > Wallet & Cards: *Store cards*, *Save CVV*) is enforced in
  `CardsController` at every write, so no path (form, NFC tap, cards file, backup) can keep what the user said not to.
- Add a card: form, **camera scan** (on-device ML Kit; `CardScan`), or **NFC tap** (`NfcTagReader`: NormPix AID, then the
  public PPSE directory, then NDEF; **never GPO / READ RECORD**; a bank card prefills network/kind and explains that the
  number must be typed or scanned). Card numbers are never in a normal backup; a separate password-locked **cards file**
  moves them (`CardsTransfer`).
- **Lock** (`wallet/AppLock.kt`, `AppLockService`): biometrics or device credential; scope Wallet only | Entire app;
  timeout immediate / 30 s / 1 min / 5 min / until closed. Locking closes every sheet (`AppOverlays.dismissAll`) and widgets
  show nothing of the ledger while NormPix is locked.
- **Reveal gate**: details are hidden until unlocked; optionally a tap on the user's own NFC tag (`NfcTagReveal`); "Only my
  registered tags" (`RegisteredTags`, up to 3, hardware ID kept AES-GCM-encrypted, a tag showing a new random ID each tap is
  refused; turning the limit off is always possible; a tag's ID is an identity, not a secret).
- **Clipboard** (`wallet/clipboard/`): "Copy card details" sheet (number, holder, expiry, CVV); `SecureClipboard` tags the clip
  with a per-copy token and clears only a clip that is still that copy, after the chosen delay (10 s default / 30 s / 1 min /
  5 min / never); survives leaving the screen and process death (persisted token + deadline, boot/restart reconcile, backup
  alarm). Nothing is logged.
- **Card exchange between phones** (`wallet/nfc/NfcCardExchange`, HCE `NfcCardOutletService`; Receive / Share tabs). Offered
  only while the phone's NFC is on **and** the in-app switch allows it (`ScheduledFeature.NfcExchange`, default **Off**; the
  availability scheduler supports Always / Scheduled weekly windows / Off). `nfcExchangeOffered` is followed live.
- **Card tilt** (`wallet/motion/CardTilt.kt`): gyro/accelerometer lean (6 degree limit, dead zone, one shared listener, off under
  reduced motion). It leans the saved cards **and the accounts drawn as cards** (`AccountCardView(tilt)`: the carousel's front card and
  the list's cards; the stack stays flat as the cards' stack does), with the sheen `drawSheen`; Wallet feeds one `rememberCardTilt` to both and
  the accounts lean even where *Store cards* is off (the setting is under Effects either way). **Card shadow** is painted from the card's rounded outline, not the hardware shadow.
- Bank card faces use shared pattern-and-colour themes (`BankCardDesign`, `AccountCardDesign.themeFor`). A per-bank redesign is
  **blocked on the owner supplying the designs - do not start it from a guess**.
- Privacy posture to preserve: no SMS or notification access; the only network host family is GitHub; ML Kit telemetry removed;
  `FLAG_SECURE` on release; clipboard cleared; hidden-amounts mode (eye button) for Home/Wallet; nothing sensitive in logs.

## 5. Reminders and card expiry

- **Reminders** (`reminders/`): one-off or repeating (`RepeatEnd`), exact alarms (`AlarmReminderScheduler`, re-armed after
  boot/update), notification actions Complete / Snooze, a full alert screen when opened from the notification. Linked to a
  card, account, transaction or recurring payment (`ReminderLinkKind`); overdue is easy to find (badges, a notice, counts),
  never alarming. Stored in `RemindersDatabase`; part of an app backup.
- **Card expiry** (`wallet/expiry/`): alerts start on the 1st of the month before the expiry month, daily at 9:00 until the card
  is marked done; per-card state Normal / Expiring soon / Reminder active / Completed in its own store (bank, last four,
  expiry, done); renewal starts a fresh cycle; re-armed after reboot, update, clock or time-zone change; falls back to an
  in-app notice when notifications are off.
- Backlog: Samsung-Reminder-style alarm alerts (full-screen intent, act from the alert, re-alert until handled) - parked.

## 6. Data safety: backup, restore, check, sample data

- Exports: **Full backup with pictures (.zip)**, **small backup (.json)**, **CSV**, **App backup** (zip with settings,
  reminders, profile, font, optional cards - chosen part by part when restoring; `backup/`). Restore validates first and asks
  before replacing anything; old backups still restore; a newer-format file is refused.
- **Sample data** (Settings > Data & backup): in-memory only (`DummyData`), about three months of accounts, transactions,
  budgets, recurring, reminders, cards; turning it off brings the real data back untouched. Widgets always show the user's
  own ledger, never the sample set.
- Loading sheets (`overlays.loading`) wrap anything slow or that changes data (reading a picture, reading/restoring a backup,
  check and recalculate); `cancellable = false` work runs to its end once begun.
- "Reset all settings" and deletes go through confirm sheets (destructive in error colours); deletes of transactions offer Undo.

## 7. Updates

- Source: GitHub releases of `saik111919/norm-pix` (public, no token). `update.json` published by CI lists version, APK(s),
  sizes, SHA-256, min SDK, notes, `mandatory`. A release is accepted only if tag, version, attached asset size and checksum all
  agree (`ReleaseMetadata`). Prereleases/drafts are never offered.
- Behaviour: check once a day in the background (JobScheduler `UpdateJobService`, not on low battery, Wi-Fi unless "Download
  on mobile data") and at launch, or on demand. What happens to an update found in the background: **Tell me** | **Download it,
  then ask** (default) | **Install it automatically** (Android 12+, after the app has been closed a while). **Nothing pops over
  the screen**: an update is Home's banner, which opens the update page; notifications also open it.
- **Update now** downloads the APK (foreground `UpdateDownloadService` so it continues with the app closed; notification with
  Cancel/Install), verifies size, SHA-256, package name, version and **signing key**, and only then hands it to Android's
  installer, which asks the user. The app never uninstalls itself. Only release builds update themselves.
- **Changelog**: the update page holds a **Changelog pill** opening `ChangelogPage` (all published versions, newest first,
  from the releases' own notes, cached for offline reading). The changelog loads only there.
- **Preview update channel** (opt-in builds from `features`): designed, **not built** - no code exists. See the owner notes:
  needs a versionCode scheme with room for previews, a DB `onDowngrade` policy and a data-safety fallback. Do not start
  without the owner's explicit go.

## 8. Widgets (`widgets/`)

Seven, reached only by the system (not exported): **Clock: watch dial** (`ClockWidget`: hour and minute are `TextClock`s the launcher keeps
on time; the face and the inner minutes ring are a picture `WatchDialRenderer` draws, upright numbers, **redrawn every 15 s** by
`WatchDialTicker`'s non-waking alarm; the outer **seconds ring really turns**: an animated vector (`widget_dial_seconds.xml`, groups
`orbit` + `nNN`) run by an indeterminate `ProgressBar` the launcher animates itself - the ring group turns once a minute, each number's
group the other way, so the numbers orbit upright. An animation cannot be told where to start, so there is one AVD + layout + two
animators **per second** (`widget_dial_seconds_00..59`, listed in the generated `WatchDialSecondsLayouts.kt`); `ClockWidget.face` does
`removeAllViews` + `addView(RemoteViews(layout[second]))` into `widget_clock_seconds`, so a redraw resyncs it: the ring is at most
15 s out after the launcher shows it again, and between redraws it keeps time itself. All of it from `design/cookie-clock/CookieFace.java`),
the four **cookie clocks** (`CookieClockWidget`: `CookieAnalogWidget` ticks and hands, `CookieNumbersWidget` the same with numerals 1-12 and shorter hands,
`CookiePlainWidget` hands only - no ticks, no numbers,
`CookieDigitalWidget` the hour over the minute plus the date as `TextClock`s sized by `CookieFace`; the face is Material 3
Expressive's four-sided cookie, drawn with the hands and numerals by `design/cookie-clock/CookieFace.java` - run it with
`java CookieFace.java` in that folder, there is no Python on the PC - and `CookieFace.kt` shares its numbers; colours are the
`widget_clock_*` tokens, light and night; no update period, a tap opens the clock app), **Recent transactions** (Home's Recent as of today; amounts stay hidden
while hidden in the app; shows only "locked" while NormPix is locked; redrawn on ledger change, resize, day turn), **Add
transaction** (one cell = one add button; larger = Expense / Income / Transfer). Taps reach the app as a `WidgetRequest`
(`WidgetIntents`), done once the app is unlocked and only once. Widget rules followed: picker previews, resizable, Android 12
size maps, `@android:id/background` with the launcher's radius, M3 colour roles from the wallpaper, 48 dp targets, TalkBack
descriptions, immutable PendingIntents, **no update period**.

**Widget settings (clocks only).** Every clock has Android's "Widget settings" (`android:configure` =
`ClockWidgetConfigActivity`, `widgetFeatures` reconfigurable|configuration_optional, exported because the launcher starts it; it finishes for
an id that is not a clock of ours and keeps the widget however it is left). `ClockWidgetSettings` (Compose) edits `ClockOptions`, kept
per widget id in `ClockOptionsStore` (`normpix_widget_clocks`, forgotten in `onDeleted`, not in backups): **Transparent** (the cookie fill
is its own drawable `widget_cookie_fill`, hidden; the watch dial's picture has no face and its pill clears the ring; the simple clock's
background goes), **Clock face | Digital clock**, **Digital clock when small**, and for the digital clock 12/24 h, seconds, date.
The **plain digital clock** (`SimpleClock`, `SimpleClockLayout`: just the time as large as fits, the date only with room) replaces a face
when `min(width, height) < ClockOptions.SMALL_FACE_DP` (92 dp; one cell is 40-70 dp, two 110+) - each size of an Android 12 size map is
decided on its own - or when the user chose it. The clocks may be resized down to 40 dp (one cell) for that. `ClockWidgets` is the one
function that draws any clock for its size and options. A vector with no path does not inflate, so an empty dial still has one
transparent path.

**Settings > Widgets** (`SettingsPage.Widgets`, second in the list; `WidgetsSettingsPage`): `HomeWidget` lists the seven (pill,
title, description, group Clocks / Transactions, provider, preview size) - `HomeWidgetTest` keeps it equal to the manifest's
widget receivers, so a new widget is added to both. A pill previews its widget in a stage above (the very `RemoteViews` the
home screen gets, `HomeWidget.previewViews`, applied in an `AndroidView` with its taps removed) and **Add to home screen**
calls `WidgetPinning.request` (`requestPinAppWidget`; the launcher shows its own dialog; `lock.expectExternalActivity()` first;
a launcher that can't be asked gets the picker's way in words; "added" shows when the page resumes with one more placed).
The preview uses the context's resources, so it follows the *system* day/night, as the real widget does, not the app's theme.
The pills are `FilterChip`s with `Role.RadioButton` (one choice of several); the Settings row's summary is one short line
("Clocks, recent transactions, quick add"). The faces and hands are regenerated, never hand-edited: the 280 dp drawables carry
`tools:ignore="VectorRaster"` from the generator. Checked on the Medium_Phone emulator (qa build) on 2026-10-27: previews,
the launcher's own add dialog, and all three cookie clocks placed and running.
`InteractiveClockWidget` / `ClockWidgetTokens` (the in-app interactive watch dial, commit `c99a54e2`) is a Compose component
that nothing in the app shows yet.

**Banks** (`banks/BankCatalog`, `wallet/BankDirectory`): 158 built-in banks, each with a `BankCategory` (public sector, private, small finance,
payments, co-operative, regional rural, foreign, earlier names - a merged bank carries `note` "Now part of ..."). Ids are what accounts and cards
keep: only ever add, never change or reuse one; no two banks may share a normalised name or alias (`BankLogoResolverTest` checks it) and short
common-word aliases are avoided because the resolver matches whole words in receipt text. Logos: the `assets/banks/` SVGs (IFSC-code names) and,
for banks that have only that, a round full-colour picture from the brand set (`BankLogoArt(symbol = "brandlogos/<key>.webp")`, drawn by
`BankArtwork`; a bank with neither shows the generic bank glyph). **Settings > Banks** (`SettingsPage.Banks`, `BanksSettingsPage`): the whole
list, grouped by kind, with a search that also finds a bank by its other names, chips (All / In use / Added by you / each kind), "In use"
marks, a detail sheet, and Add a bank (`BankFormSheet`). More logos need a source that is bundled and licensed: the owner can send them.

## 9. About guide (`about/`)

Settings > About NormPix opens a page (not a dialog): name, version, a card per topic (`GuideTopic`: AddTransaction,
ShareReceipt, Home, Activity, Plans, Insights, Wallet, CardExchange, Widgets, LookAndFeel, Backup, Privacy), your data, credits.
Topics are data (checked by `GuideTopicTest`); pictures (`GuideArt`) are **drawn in code from theme colours**, so they follow
Light/Dark/Pure/wallpaper colours, add nothing to the APK and can't go stale; inert and hidden from screen readers. **When a
user-visible feature changes, update its topic text.**

## 10. Appearance catalogue

Theme (System/Light/Dark) + Colors (Wallpaper colors on Android 12+, or Other colors: Emerald, Ocean, Royal, Coral, Amber,
**Volt** (default, hand-drawn), **Pure** (black/white, hand-drawn); each shown as a tile with the three colours it gives
the app), App icon (ten launcher icons), Interface font (GS Flex presets Standard/Zenith/Neo/Impact/Airy or hand-set axes,
System, or the user's own .ttf/.otf), Icons (category style 3D/Emoji/Outline, bank logo style, shape behind), Progress
indicators (Wavy | Flat), Transitions (six page styles incl. **Linux window**, five sheet/dialog styles, "Try it"), Effects
(background blur, footer blur, card tilt), Navigation & controls (footer labels, Reset action menu - the "+" menu itself is
arranged in its Edit mode, bottom controls, back button style, month control), Layouts (Top
spending, Accounts, Cards). Search finds each choice by its own name.

## 11. Open and parked (check `native/TASKS.md` Backlog; verify against the code first - old "[ ]" lines go stale)

- Per-bank card designs: blocked on the owner's designs.
- Samsung-style reminder alerts: parked.
- Preview update channel + data-safety fallbacks: designed, awaiting the owner's go.
- Not tried on a phone at the end of Phase 36: the About guide's pictures at real sizes, the Colors tiles, Pure on OLED,
  widgets on every launcher, the Top spending carousel's feel.
- Recurring-payment rows have no swipe actions.
- Renaming/deleting a bank the user added (Settings > Banks lists and adds them, but cannot rename or delete).
- Fixtures for self-transfer receipts are synthetic until a real one is shared.
