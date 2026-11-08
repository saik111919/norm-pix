# NormPix features, rules and decisions

Verified 2026-10-27 against commit `763cc08f`. This is the *behavioural* picture: what each feature does, the rules it
obeys and the decisions behind them. For the dated history of any feature, grep `native/TASKS.md` for its name (do not read
that file whole: it is ~680 lines of phase log; the last entries are Phases 33-36, 2026-10-26).

Contents: 1 Screens · 2 Money rules · 3 Reading receipts shared into the app · 4 Wallet, cards, NFC and privacy ·
5 Reminders and card expiry · 6 Data safety: backup, restore, check, sample data · 7 Updates · 8 Widgets · 9 About guide ·
10 Appearance catalogue · 11 Open and parked

## 1. Screens

- **Home** (`screens/HomeScreen.kt`, `HomeBanners.kt`): month overview hero (spent / left / used, progress) wearing the **month's weather**
  (since 2026-11-01, after sameerasw/Overcast: `finance/ui/HeroWeather.kt`, `heroWeatherFor` - snow before anything is spent, sun on track,
  heat when the budget is nearly used or, with no budget, spending is 25%+ up on last month, rain over budget; Settings > Appearance >
  Effects > Hero weather, default on; see design-system "Live motion"), **Needs
  attention** banners (overdue, update available, ...; the page's own tonal surface, status said by icon and eyebrow colour,
  never colour alone), quick add (expense / income / transfer), **Bill due / Bills due** (2026-11-07, owner's reference picture; **what is due is the unpaid part of the card's last statement** - `cardBill`, `CardStatement.kt`: owed at the end of the statement day less what was put onto the card since, never more than owed now; a card with no statement day keeps "Owed so far"; past the due date the bill is **Overdue** in the error colour - and nothing once it is paid; the pay sheet opens on that amount; one two-panel
  `SplitStatBanner` for every active credit card that owes something and has a due day, soonest first - "Due date" with how far away it is, a pill
  that fills from the last statement to the due date, "Total due" with the card's name; tapping opens `PayBillSheet`; `screens/HomeBills.kt`,
  `billCycle` in `BillingCycle.kt`; the **Wallet** shows the same banners above its accounts; a card with nothing owed or no due day has none), coming up, **Top spending** (stack or carousel by
  `TopSpendingLayout`), cards on Home, **Recent** (the latest *as of today*; "See all" opens the whole history, All time),
  budgets tile. The account pill / profile button at the top opens Settings (or Profile, by choice). **The month's number collapses into the
  header as the page is scrolled** (2026-11-05, owner: "see how the number became small and then the same way it becomes big", after Google
  Weather's temperature; `screens/HeroCollapse.kt`): "Spent in November" and the amount shrink and glide up (`heroCollapseFrame`, a pure
  function of the scroll) and settle as a small primary chip where the page's title was; scrolling back reverses it frame for frame.
  **Reworked 2026-11-06** (owner: it "goes up quickly", spacing not smooth, "the background of it is not looking good on the card"; filmed
  as a contact sheet of Home scrolled step by step): each line leaves at the page's own speed and *slows to a stop*
  (`1 - (1 - p)^COLLAPSE_STRETCH`, so it takes 1.8x its way in scroll, **never ahead of the card** - before, a blend toward the chip put it up to
  a quarter of its way ahead, then stopped it sharply), and one smooth curve shrinks it over the first half of that (`SHRUNK_BY`; it used to
  fall to a third in about 36 dp of scroll). The chip is **a capsule at every size** (radius = half its height, room round the lines growing
  with their height; a box with square corners was the "patch" on the card) and comes in only across the page colour's own fade
  (`HeroCollapseSpec.chipSolidAt` + `fade`: the floating header's cover is whole down to 12 dp above its foot and fades out over that, a bar's
  to its foot) - it used to start 40 dp above the foot, which on a phone is *inside the card's own stretch*, so it showed from the first
  pixel of scroll. "Home" fades **with the chip** (`titleAlpha = 1 - max(titleGone, chipAlpha)`, `HeroCollapseFrame.titleAlpha` through
  `AppPage(titleAlpha)`), so the chip's edge never cuts through its letters. Tests: `HeroCollapseTest` (12, with a real phone's numbers),
  `HeroCollapseSpecTest` (the real window's fade). The lines are drawn by
  `HeroCollapseOverlay` over the page while it is scrolled (the card clips what is inside it); the card's own copies stay for layout and
  TalkBack and are undrawn only then (`isCollapsing`), same text/style/width so the hand-over is invisible. Scroll-driven, so no
  reduced-motion variant is needed. The chip is there once the lines are over the header's colour (`chipAlpha`), because the
  lines are `onPrimary` and dark on a dark page otherwise. **Where the lines rest is computed from the list's own numbers** (`listTop` +
  `layoutInfo.beforeContentPadding`, plus the lines' places *inside* the item), never from the item's position on screen: that lags a scroll by a
  frame, and a first version drew the chip a whole step ahead of the text in a render. Home only; written for the top floating header (default) and the standard top
  bar. Not verified on a phone.
- **Notifications** (`notifications/`, 2026-11-08): a bell on the top row of every top-level page (Home, Plans & Transactions,
  Insights, Wallet, Settings) - top-level only (owner, 2026-11-08: a pushed page keeps its back button and its own actions
  instead), in all three top-bar styles through `LocalTopBarExtras`. It carries how many are new; tapping it opens a
  **Notifications page** that lists everything Home's Needs attention would show -
  overdue reminders, budgets over or nearly used, cards about to expire, a crash report, a backup due, an update waiting - **grouped by
  kind** (Reminders, Budgets, Cards, Backup, NormPix) and each banner in full, with its own buttons. There is **no history**: a
  notification lives as long as the thing it says does. **Opening the page is seeing it all** (all keys listed are written as seen;
  the keys of things that have gone are dropped, so the same budget over again next month is new again); the bell then shows none
  until something new arrives. The **+ menu shows the newest three unread above its panel**, each opening what it is about, with
  "All notifications (n more)" opening the page; nothing at all when nothing is new. Phone dock only: the wide-window rail has
  no + menu, so no strip there (owner, 2026-11-08), just the bell. Settings > Notifications (reminder delivery)
  is linked at the foot of the page. Tests: `NotificationsTest`, `NotificationsRenderTest` (pictures in
  `app/build/qa_notifications/`).
- **Plans & Activity** (`screens/PlansAndTransactionsScreen.kt`, `finance/ui/TransactionsScreen.kt`): two tabs.
  *Activity* = the transaction history: month switcher (two looks, `MonthControlStyle` Track | Split), **All time** scope,
  **by day** (`DaySwitcher`; a day heading shows what went out and opens that day), an **Upcoming** row for entries dated
  after today, search + filters (type, account, category, person, amount, dates: `TransactionFilterSheet`,
  `DateRangeSheet`), Gmail-style swipe (`SwipeableTransactionRow` on `SwipeActionRow`, a setting): left = Edit (opens the editor, the
  row springs back), right = Delete (the row flies off and folds away, **no confirmation**, Undo snackbar; the details sheet and the
  TalkBack action still ask first), details sheet with Delete/Edit and the attached picture. On a month's **last day** the month ends
  exactly at the cutoff, so the Upcoming query keys on `range.end == cutoff` (month view only) to keep next month's entries listed. *Plans* = Budgets (per category + overall, history page, sub-categories fold into the parent),
  Recurring (`RecurringView`/`RecurringEditor`; rows record themselves on their day; **a payment and its reminder are kept in step** -
  section 5, `RecurringReminders`; a row with a reminder shows a small bell and says "with a reminder" to TalkBack), Reminders (`RemindersView`).
- **Insights** (`finance/ui/InsightsScreen.kt`, `finance/domain/Insights.kt`): a story - snapshot hero (one sentence of how
  this compares and with what, two bars on one scale), a single-series multi-month trend, ranked category rows with share and
  a link to their transactions (or, Settings > Appearance > Layouts > **Categories in Insights**, `InsightsCategoriesLayout.Carousel`
  - the Home top-spending `CategoryCarousel` over every category of the month; days that were picked always show the list;
  default List), "More detail" collapsed (day by day, income sources). A month in progress is compared
  like-for-like (`comparisonRange`). Custom date ranges.
- **Wallet** (`screens/WalletScreen.kt`, `wallet/`): Accounts (layouts Stack | Cards | Rows | Carousel) and Cards (layouts
  Stack/Cards/Rows/Carousel; **the cards' default is Cards**, and the Wallet's Stack layout draws `CardStack` (a `DeckStack`; swipe the
  front card to bring the next forward, hold it to spread them out, Back gathers them again). **A "filed like folders" stack
  (`wallet/CardFolderStack.kt`, tab strips, made 2026-11-02 with Stack as the default) is still in the source but the Wallet no longer
  draws it and the default went back to Cards in `ec8a9cb3` - nothing in the log says the owner asked for that; ask before reviving or
  deleting it.** Each of the two has its own **"+ Add"** beside its heading (no dashed add tiles). NFC buttons show only
  while NFC is usable (section 4).
- **People** (`PeoplePage`, `PersonDetailPage`): label expenses with who they were for; totals per person. The **For** field starts on the user (2026-11-08, owner: by default it should be me; it was "No one in particular"): labelled **"Asha (me)"** when a name is saved in the app (`myLabel` in `FormSelectors.kt`: the profile name from the welcome tour / Settings > Profile, tidied and cut at 24 characters *before* " (me)" is added, so the end of a long name is what goes) and a bare **Me** without one; the system's own name is not used (Android gives apps none without a permission NormPix does not ask for). An expense on nobody has no person stored, so nothing in the data changed, and the People page's other total still reads "On me". The filter sheet lists real people only, so it has no such row. **Categories**
  (`CategoriesContent`, the Categories tab of Settings > Banks & Categories; `Routes.CATEGORIES` opens that page on it): two levels, ready-made set, 3D / emoji / outline icons with brand logos, archive, reorder. With the 3D style the main Settings list's groups wear 3D pictures too (`SettingsPage.emoji`, `SettingsRowEmoji`, drawn by `SettingsGlyph` on the group's tinted tile at 30 dp) unless **3D icons in Settings** (Appearance > Icons, pref `settings_icons_3d`, default on, disabled without the 3D style) is off; `LocalSettingsIcons3d` is provided by `ProvideAppEnvironment`, flat outside it; search results stay flat.
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
  notice; fewer than 2 accounts -> stays Expense/Income with a notice. **Only Google Pay's receipts are turned into transfers**
  (`SelfTransfers.DEFAULT_TRUSTED`, owner 2026-10-31: no real receipt exists for the other apps); every other app stays an
  Expense/Income as read, and the ledger check can still offer to combine two halves. Tests that exercise the logic itself set
  `SelfTransfers.trustedApps = null`.
- Category suggestion order: the user's own last entry for the payee, the app's merchant table (`MerchantCategories`), else
  the word the payer wrote (`CategoryOrigin`).
- Test fixtures (`PhoneReceipts.kt`) keep the phone's real boxes with personal details swapped for stand-ins. Self-transfer
  fixtures are still *synthetic* (no app publishes a self-transfer receipt); rebuild from a real one when the owner shares it.
  Never commit real names, UPI ids, account numbers or references.

## 4. Wallet, cards, NFC and privacy

- Cards live only in the encrypted vault (section 3 of architecture). `StoredCard` has number, expiry, holder, CVV (optional),
  network, bank key and an optional **card name** (`cardName`, "Regalia Gold": additive in the vault JSON, absent = none; shown above the chip on
  the face; the form's "Card name" field suggests the bank's real products from `CardNames` - names only, verified against CardInsider for HDFC, SBI,
  ICICI, Axis, Kotak and IndusInd). A card whose name is one of 36 known products is drawn in **that product's colours** (`CardLooks`,
  `BankCardDesign.forCard`): sampled from the card's own picture on CardInsider (k-means over its pixels, white margins and black letterbox bars
  excluded, checked against the picture by eye; **never taken from a description** - an earlier text-model attempt invented colours), the main colour
  holding the first 40% of the face and the others blending in toward the far corner, each darkened only as far as the white print needs (4.5:1 for
  the print and the captions; `CardLooksTest`), with a dark glass kind pill and the bank's chip and mark. Light cards (Kotak White / White Reserve /
  Air+, IndusInd Pinnacle World / Pioneer Heritage) and the HPCL mosaic have no look and keep the bank's face, since the face prints in white. **`CardsPolicy`** (Settings > Wallet & Cards: *Store cards*, *Save CVV*) is enforced in
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
- **Card tilt** (`wallet/motion/CardTilt.kt`): gyro/accelerometer lean (4 degree limit and a gain of 0.3 since 2026-11-08 - the owner found 6 degrees at 0.5 "tilting way too much" - dead zone, one shared listener, off under
  reduced motion). It leans the saved cards **and the accounts drawn as cards** (`AccountCardView(tilt)`: the carousel's front card and
  the list's cards; the accounts' stack stays flat; the cards' stacks, `CardStack` and `CardFolderStack`, lean their front card), with the
  sheen `drawSheen`; Wallet feeds one `rememberCardTilt` to both and
  the accounts lean even where *Store cards* is off (the setting is under Effects either way). **Every card on the screen leans
  together** (restored 2026-11-04). `ec8a9cb3` (2026-11-02) had let only the card nearest the window's vertical centre lean, through a
  `TiltFocus` that has been deleted; nothing recorded the owner asking for it, and they asked why the tilt had changed - do not bring
  a "focused card" rule back without their word. **Cards have no shadow** (owner, 2026-11-04): `CardFace` is cut out by its hairline edge
  alone. A card answers a finger with `ui/components/HoldFocus.kt` `Modifier.holdFocus(source, shape)`: it sinks while pressed and, held
  for a long press, rises 2% with a 3 dp primary ring inside its edge (`DeckStack` items, `AccountCardView`); the "+" menu's tile held to
  be moved wears the same ring instead of a lift shadow. Where the hold opens a sheet (`LocalOverlaysOrNull`, provided with `LocalOverlays`) the ring stays until that sheet closes, so the card stays marked as the one it is about.
  **Every card face answers a finger the same way** (owner, 2026-11-05: "cards focus is not consistent"): `WalletCardView`'s flip card (the carousel's
  front card, the card list, the card sheet) was the one still on a plain `clickable` with a ripple; it now sinks while pressed (`holdFocus(longPress = false)`:
  a hold does nothing there, so no lifted-with-a-ring step), has no ripple, and shows keyboard focus as `focusRing` along its edge - as the stacks and
  `AccountCardView` do. `holdFocus` is the one modifier to put on any new card.
- **Card designs** (Settings > Appearance > Card design; `CardDesign` enum: `Classic`, `Banner`, `Ledger`, `Tinted`): The user picks how stored cards and account cards are drawn across the app (Home stack, Wallet stack and carousel, Add/Edit card previews, Card details sheet).
  - `Classic` (default): byte-for-byte existing look with bank brand theme gradient, chip, masked digits, bank wordmark/compact symbol, cardholder name, expiry, kind pill, and frosted back.
  - `Banner`: a 2 x 2 grid of `secondaryContainer` panels with a 3 dp seam (the card's own 16 dp outer corners, `AppRadius.lg`, because every stack, carousel and hold ring clips a card at that radius - at the bill banner's 28 dp the card behind showed through the gap in a stack, 2026-11-08 - and 4 dp inner ones): a wide top panel (bank mark, kind pill, card name, the number or the balance, faint rings) and two small panels under it (cardholder; expiry + network; for an account the top panel has the mark, the name once and the balance, and the bottom a credit-limit meter panel when there is a limit - or else, when amounts are shown, the account's share ("Of your money", as the carousel's panel says it) - plus a panel of `AccountFlagPills`; one wide panel for whichever there is, or none. The saved card's number is 30 sp on Banner, 28 on Ledger and 26 on Tinted, the cardholder and expiry 15-16 sp and the captions 10 sp: at the first version's 22 / 11 / 8 sp the panels read as empty). **Every flag Classic's account face shows is on every design** (`accountFlags`, Classic's list in Classic's order, pinned by `AccountFlagsTest`; the Banner's old else-if chain showed only the first it met). The bank's mark on Banner and Tinted is `DesignBankMark`: the artwork variant that suits the panel (`BankLogoView(onDark = ...)`), or for a bank with no artwork its glyph and name in ink - the hand-drawn white lettering vanished on a pale panel.
  - `Ledger` (`surfaceContainerHigh`): statement-inspired surface with 1 dp outline, 28 dp bank avatar disc, monospace tabular digit typography, cardholder uppercase, credit/debit pill, and signature/CVV strip on back.
  - `Tinted`: Classic's layout on the bank's own hue, with an outline chip and faint rings (no gradient or gloss). `tintedCardScheme(theme)` in `CardDesigns.kt` is the one source of its colours for a face, its back, its plate and a folder-stack strip: `ArtworkColors.scheme` of the bank's first gradient colour, or the theme's `primaryContainer` when the Artwork colors switch is off or the hue is unusable (grey). Pure and Volt pass `keepSurfaces = true` as `ArtworkScope` does; what that looks like on a card has not been seen.
  - **The new designs' print scales with the card** (`ScaledToCard`, wrapped at every dispatch in `WalletCardView` / `AccountCardView`; 0.8x to 1.6x of the 328 dp `PrintReferenceWidth`): every dp and sp inside is scaled by card width / 328 dp, so the face keeps its proportions on the Wallet's ~380 dp card (wider on most phones) and on a narrow carousel card. Classic's print stays one size (its gradient fills a wider card); a flat design has nothing to fill it with, so unscaled it was a small print in a big blank panel (the owner kept seeing "cards leaving space", 2026-11-08). A new face must be put inside `ScaledToCard`; a deck's covered strip is given as `bottomInset / scale`. Pinned by `CardDesignRenderTest.theNewDesignsPrintScalesWithTheCard`.
  - All designs share identical `CARD_ASPECT`, `holdFocus`, `focusRing`, `CardTilt`, `drawSheen`, 2 dp hairline edge, TalkBack content descriptions and action semantics, with text pairs pinned at >= 4.5:1 for Light, Dark, Pure and Volt by token maths (`CardDesignsVisualQaTest`; Tinted with one bank colour only). `CardDesignRenderTest` drives the real preferences (`TestEnvironment`), so Light, Dark, Pure and Volt (light and dark) really apply, and it dumps faces, backs, the carousel's compact print and both stacks to `app/build/qa_card_designs/<scene>_<design>_<look>.png`; it asserts that every face fills its own corners inside the card's `AppRadius.lg` clip and that Banner and Ledger render differently in Dark, Pure and Volt. `testFontScaleOnePointSixLayoutIntegrity` only renders (no assertion). **Settings > Appearance > Layouts > Card design** shows each design as a made-up saved card and account beside its radio row (`CardDesignPreview`, the faces laid out at `PrintReferenceWidth` and scaled to half the row; a touch on it chooses the design). **Known gaps (2026-11-08):** Tinted's contrast is tested with one bank colour, and the lean's sheen is a 16% white highlight that a pale face cannot show.
- Bank card faces use shared pattern-and-colour themes (`BankCardDesign`, `AccountCardDesign.themeFor`). A per-bank redesign is
  **blocked on the owner supplying the designs - do not start it from a guess**.
- Privacy posture to preserve: no SMS or notification access; the only network host family is GitHub; ML Kit telemetry removed;
  `FLAG_SECURE` on release; clipboard cleared; hidden-amounts mode (eye button) for Home/Wallet; nothing sensitive in logs.

## 5. Reminders and card expiry

- **Reminders** (`reminders/`): one-off or repeating (`RepeatEnd`; kinds Once/Daily/Weekdays/Weekly/Monthly/Custom days and, since
  2026-11-04, `Months` = every N months, 12 = a year, which a yearly or every-other-month repeating payment's reminder uses), exact
  alarms (`AlarmReminderScheduler`). Linked to a card, account, transaction or recurring payment (`ReminderLinkKind`); overdue is easy
  to find (badges, a notice, counts), never alarming. Stored in `RemindersDatabase`; part of an app backup.
- **Delivery is a hybrid** (`ReminderDelivery`): (1) **the notification, always** (`ReminderNotifications.post`: buttons *Paid/Received/Done* (`Reminder.shortDoneLabel`; the long `doneLabel` "Mark as paid" is for the card and in-app rows - it was cut to "Mark as p..." in a heads-up),
  *Snooze* (1 h), *Open*; private on a lock screen that hides content; a **full-screen alert** (`ReminderAlertActivity`, over the lock
  screen, only the title and due time) **only when the phone is locked or its screen is off** (a full-screen intent on a notification
  that arrives on a lit phone made the heads-up stick on screen and cover the floating card - found on the emulator) and the user's
  switch is on and `ReminderAccess.canUseFullScreen` - on Android 14+ that permission is not granted for you, so it is optional and the
  notification is the fallback); (2) **the floating card on top** (`ReminderOverlay`, a `TYPE_APPLICATION_OVERLAY` window with `ReminderAlertCard`: Mark
  as paid, then **Snooze | Dismiss | Open app as one `ConnectedButtonGroup`** (owner, 2026-11-05: "make these a Material button group";
  52 dp, round at the ends, squared where they meet; Snooze opens 15 min / 1 hour / Tomorrow 9:00 as the same kind of group); **the reminder's
  notes show on the floating card** (`showNotes`, up to 5 lines, and the card grows to hold them - the window wraps its content) but never on the
  lock-screen one, which stays title and time only because a locked phone may be in front of others; a swipe up or 30 s puts it away, the
  notification stays). It is
  **off by default** (`reminderOverlayEnabled`, excluded from backups) and `decideOverlay` (pure, `ReminderOverlayPolicy.kt`) lets it
  show only on an unlocked, screen-on phone with "Display over other apps" allowed, never over NormPix itself. **NormPix never asks for
  Usage access and never looks at which other app is open** (owner, 2026-11-05: "no need to take usage access of apps ... this is going
  into a separate way from the intentions of the app"): the *excluded apps* list, the `PACKAGE_USAGE_STATS` permission, the launcher-apps
  `<queries>` entry, `ExcludedApps` and `foregroundPackage` were all removed, and the stored `reminder_overlay_excluded` list is deleted from the
  phone on start (`AppPreferences` init). The cost is plain: the card can now appear over any app, a banking app included - it never
  shows what is under it and it is off by default. Do not bring a "which app is in front" check back without the owner's word.
  **No Accessibility service, nothing forced.** While Settings or a system permission dialog is in front Android itself hides overlay windows (`mPolicyVisibility=false`: no card there, only the notification - seen on the emulator); the card is put away when NormPix comes to the front (`MainActivity.onStart`), and it was seen working over an ordinary app (Clock). Settings >
  Notifications > *Reaching you on time* (exact alarms, battery, full-screen) and *Floating reminders* (`ReminderAlertSettings`, "Try it" sample;
  `openAndroidPage` opens Android's own switch pages, shared with the welcome tour). Mark as paid only finishes the reminder; it records no transaction.
- **Alarm to overlay, checked end to end** (owner, 2026-11-05: "confirm when there is an alarm we need this overlay to appear"):
  `AlarmToOverlayTest` runs the real chain on Robolectric - a saved reminder arms an exact `RTC_WAKEUP` alarm for its minute, the alarm's own
  intent goes to `ReminderAlarmReceiver`, which delivers the notification and the floating card and stamps `lastFiredAt` - and the places the
  card must not come (switch off, no "Display over other apps", reminder finished or deleted meanwhile). A broadcast receiver run by hand has no
  `goAsync()` result: `work` tolerates that. What this does not prove: a real phone's ROM (see the emulator notes in memory for the recipe).
- **"Display over other apps" is asked for on NormPix, and the person is told which row to tap** (owner, 2026-11-05: "when allowing over other
  apps in system settings focus on our app so the user can go directly there"): the request names the package
  (`ReminderAccess.overlaySettingsIntent`), which lands straight on NormPix's switch on the phones that honour it. **Found on an Android 17
  emulator that Settings ignores the package and opens the list of every app**; the `:settings:fragment_args_key` extras did nothing, and the one
  action that does open one app's page there (`android.settings.MANAGE_APP_OVERLAY_PERMISSION`) is **refused to other apps** (log: "requires
  android.permission.INTERNAL_SYSTEM_WINDOW") - so neither is used. What is done instead: the explanation sheet and the welcome tour say "tap
  NormPix and turn on Allow display over other apps", and `openAndroidPage(hint = ReminderAccess.OVERLAY_HINT)` repeats it in a toast while Settings
  is up. Exact alarms and full-screen alerts already open on the package (their actions are app-specific) and the battery page is a list by nature.
  `SettingsFocusTest`. Whether the owner's phone lands directly is not known.
- **Add to Clock alarms** (owner, 2026-11-05: "connect these to the system alarm app if possible"): a reminder's actions sheet (long press) has
  "Add to Clock alarms", which opens the Clock app's own alarm form (`AlarmClock.ACTION_SET_ALARM`, `EXTRA_SKIP_UI` false so the person sees and
  saves it; `ClockAlarms.intentFor`). A clock alarm only knows a time of day and weekdays, so it is offered only for a reminder that falls within
  the next 24 h or repeats daily / on weekdays / on days of the week - not for a monthly bill or one three days off. Needs the normal
  permission `com.android.alarm.permission.SET_ALARM` and a `<queries>` entry for `SET_ALARM`. NormPix keeps nothing about the alarm; making
  every reminder a status-bar "next alarm" with `AlarmManager.setAlarmClock` was not done (it would show a NormPix reminder in place of the
  person's morning alarm on the lock screen) - a question for the owner if they want it.
- **A repeating payment and its reminder are one thing** (owner, 2026-11-05: "in Planning we have recurrence, we need to connect them to
  reminders - they are always inter-linked"; `reminders/RecurringReminders.kt`, a pure rule, applied by `ReminderFlows.follow/forget`, reached
  from the finance screens through `ReminderLauncher`): a new payment gets its reminder at once (due its next day at 9:00, repeating as it does;
  Undo in the snackbar); **editing** the payment edits it - title and notes only where they are still what NormPix wrote, and the day and repeat
  only when the payment's schedule (frequency, interval, start, end) changed, at the hour the reminder already rang at; a **paused or ended**
  payment has none (it is removed, and comes back when the payment resumes); a **deleted** payment takes its reminders once the Undo is let go
  (`offerUndo(onSettled)`), so no reminder is left saying "this repeating payment no longer exists". A reminder the person deleted from a
  payment that is still going is **not** brought back. **A sheet cannot see `LocalReminderLauncher`**: sheets are drawn by `OverlayHost`, which stands
  above where `NormPixRoot` provides it, so the editors read `null` there and the old "a new repeating payment gets a reminder at once" never ran
  from the form (found 2026-11-05 by `RecurringReminderLinkTest`); `FinanceActions.WithReminders` now provides it around each editor sheet it opens.
  Any new sheet that needs a local provided below the overlay host must be given it the same way. Plans > Recurring rows show a bell (`LedgerRow(reminder)`, from `remindedRules`). Not
  built, and a question for the owner: reminding *before* the day ("nearing the day to pay") - a reminder rings on the payment's own day at 9:00.
- **Reliability rules** (audited 2026-11-04): an alarm carries the reminder's id; `alerted()` stamps `lastFiredAt` and, for a repeating
  reminder nobody dealt with, sets an alarm for its next time (`ReminderActions.nextCycle`) so one ignored occurrence does not silence the
  rest; completing a long-overdue repeating reminder moves it to the first time still **ahead** (it used to land in the past, where no
  alarm is ever set); after a **restart or an update** `rearmAll(deliverMissed = true)` hands back reminders that came due while the
  phone was off (once - `wasAlerted`); the boot receiver also re-arms on `TIME_SET`, `TIMEZONE_CHANGED` and the exact-alarm permission
  change; a failed receiver never crashes the app; a payment's reminder is at **9:00**, not at the midnight a date-only payment is stored as
  (`ReminderDrafts.morningIfDateOnly`).
- **Card expiry** (`wallet/expiry/`): alerts start on the 1st of the month before the expiry month, daily at 9:00 until the card
  is marked done; per-card state Normal / Expiring soon / Reminder active / Completed in its own store (bank, last four,
  expiry, done); renewal starts a fresh cycle; re-armed after reboot, update, clock or time-zone change; falls back to an
  in-app notice when notifications are off. The notice is **one banner design everywhere** (2026-11-07, owner: the Wallet's own plain
  box "did not match"): `cardExpiryBanner` in `CardExpiryNotice.kt` builds it, Home's Needs attention stacks it, and
  `CardExpiryNotice` shows it as a stack of one above the card it is about on the Wallet page (press = open the card).
- Not built: a **re-alert until handled** for a reminder that does not repeat (one notification that stays in the shade is all it
  gets), and anything checked on a real phone - see the Phase 50 entry in `native/TASKS.md` for what was and was not tried.

## 6. Data safety: backup, restore, check, sample data

- Exports: **Full backup with pictures (.zip)**, **small backup (.json)**, **CSV**, **App backup** (zip with settings,
  reminders, profile, font, optional cards - chosen part by part when restoring; `backup/`). Restore validates first and asks
  before replacing anything; old backups still restore; a newer-format file is refused.
- **Locked backups** (since 2026-11-03, whole-app encryption stage 1): each restorable export first asks "Lock the backup?"
  (`BackupLockSheet` in `settings/ui/LockedBackupUi.kt`; **Lock it** is preselected, **Don't lock** saves the file exactly as before).
  Locked, the whole file is wrapped by `backup/LockedFile` and saved as `<name>.locked` (`application/octet-stream`); unlocked it is
  the old file byte for byte, so every reader and limit is unchanged. Restore sniffs the first 15 bytes (`openBackup`): a locked file
  asks for its password (again after a wrong one), is read through the usual `readBackup`, then read to its end (`BackupSource.finish`)
  so a cut or extended file is refused before anything changes. In a locked app backup the saved cards are locked with the backup's
  password (no second sheet), and restoring tries that password on them first. The CSV is never locked (it is for spreadsheets).
  No password is stored anywhere; a forgotten one can't be recovered.
- **Encrypted on the phone** (since 2026-11-03, stage 2): the finance and reminders databases are SQLCipher files
  (`*.encrypted.db`), key in the Keystore; an existing plain ledger is moved over on the first start of the new build and the plain
  file deleted only after the copy is proven identical (see `architecture.md` section 3). Settings > Security & Lock > Your data says
  "Your data is encrypted" only when both are moved with no plain copy left (read from the files), else "isn't encrypted by NormPix
  yet". About > Credits > SQLCipher shows its BSD licence (required attribution). Not encrypted yet: transaction pictures, the
  profile photo, the font and SharedPreferences (stages 3-4: pictures, an optional biometric-bound key - not started).
- **Sample data** (Settings > Data & backup): in-memory only (`DummyData`), since 2026-11-01 **six months** (`MONTHS`, seeded `Random`):
  6 accounts (Cash, HDFC/SBI savings, ICICI/Axis cards, Paytm), 6 people, a few hundred transactions over real sub-categories, card bills
  paid as transfers, this month's budgets in all three states (Fitness nearly used, Shopping over from the 2nd, the overall on track), 8
  recurring rules, 7 reminders (two overdue), 4 cards with published test numbers only (one expiring next month); turning it off brings
  the real data back untouched, and its pictures' folder in the cache goes - so does any left by a set that was never closed. Widgets always show the user's
  own ledger, never the sample set.
- Loading sheets (`overlays.loading`) wrap anything slow or that changes data (reading a picture, reading/restoring a backup,
  check and recalculate); `cancellable = false` work runs to its end once begun.
- "Reset all settings" and deletes go through confirm sheets (destructive in error colours); deletes of transactions offer Undo.

- **Backup reminder** (2026-11-07, `backup/BackupFreshness.kt`): only the *time* of the last restorable backup is kept (`last_backup_at`: the small file, the full zip or the app backup saved from the real data - not the CSV, not the sample set); Home's Needs attention says "Back up your data" when it is older than the reminder (Settings > Data & backup > Backup reminder: every week / 2 weeks / month, default 2 weeks, or off) or there was never one 3 days after install, only when there is something to keep, "Later" puts it off 3 days. `last_backup_at` and the snooze are in `BACKUP_EXCLUDED` and survive Reset settings. Automatic backups to a folder are **not built**: a locked backup's password is stored nowhere (see above), so unattended locking would need a deliberate opt-in change to that rule - the owner's call.
- **Crash log** (2026-11-07, `diagnostics/`; owner asked 2026-10-31): a default uncaught-exception handler (installed first in `NormPixApplication`) keeps a report - app and Android version, device model, thread, exception class and stack, **messages scrubbed** (4+ digit numbers, `name@bank` handles, anything in quotes taken out) - as one file each in `files/crash-log/`, the newest 20, 12,000 characters at most; the system's record of an app-not-responding or native crash (`ApplicationExitInfo`, Android 11+) is kept too, once. About > Crash log lists, shares (the newest 5, through Android's own share sheet) and clears (asks first); Home offers the last crash on the next start until seen or dismissed (`crash_log_seen_at`, excluded from backups). Nothing is sent by the app.

## 7. Updates

- Source: GitHub releases of `saik111919/norm-pix` (public, no token). `update.json` published by CI lists version, the APK,
  size, SHA-256, min SDK, notes, `mandatory`. **A release is one APK + `update.json`** (2026-11-08, owner: "only show release apk in GitHub
  release"): the universal APK, no per-CPU APKs and no `.sha256` files - GitHub shows each file's SHA-256 itself. `update.json` must stay (every
  installed copy reads it) and has no `apks` now; `ReleaseMetadata` still reads `apks` from older releases and takes the universal APK without it. A release is accepted only if tag, version, attached asset size and checksum all
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
on time; the face and the inner minutes ring are a picture `WatchDialRenderer` draws (the ring at the minute **and the seconds into it**, `WatchDialFace.innerAngle(value, minute: Float)`, so drawn at every tick it creeps 0.5 degrees at a time instead of jumping 6 at the minute's end; the rings cache is keyed on the second; **each tick is sent on a second's edge** (since 2026-11-01, `ClockWidget.refreshAllOnTheSecond`): the seconds ring turns smoothly and each redraw restarts it at a whole second, so how far it jumps is how far the moment the launcher shows it is from that second's edge; the alarm's lateness and the drawing time varied, so it jumped 1-6 degrees every 5 s (owner, on the phone). Now the tick draws its dials for `WatchDialTicker.nextSecondEdge` (the first whole second at least `SCREEN_LATENCY_MS` + 100 ms ahead) and holds them (`goAsync` + a main-looper `postDelayed`, `holdBefore`: up to ~1.1 s) until `SCREEN_LATENCY_MS` (300) before it, so every restart lands on an edge however late the alarm was; one-off sends (placed, resized, a change of clock, the preview) use `dialMoment` = now + 300 ms rounded to the nearest second. The old rule - anchor an on-time alarm to its tick, read the clock for one over `ON_TIME_MS` late - is gone), upright numbers, inset 6 dp (`INSET_DP`) from the
widget's edge, **redrawn every 5 s** (`WatchDialTicker.TICK_MS`) by its non-waking, exact alarm (`USE_EXACT_ALARM`, granted without a switch); the outer **seconds ring**: on **Android 12+ a bezel the system turns** (since 2026-11-02, owner's choice of an exact ring that
steps over a smooth one that can be nudged): `ClockWidget.views(bezel = true)` adds `layout/widget_dial_bezel.xml`, an `AnalogClock` whose
second hand is the whole ring (`drawable/widget_dial_bezel`, the ring at :00 with its numbers set along it like a watch bezel, 1200 dp so
AnalogClock - which only scales down - fits it to the face as `fitCenter` fits the picture; dial and hour/minute hands
`widget_dial_bezel_frame`, empty) - measured on the phone: exactly 6.00 degrees a step, once a second, still between steps, and right the
moment the home screen comes back. Over it a **pill cap** (`widget_clock_pill`, `drawable/widget_dial_pill`: the pill, rim and accent dash,
always solid - at 84% on a transparent clock the ring showed through) and the **second as a `TextClock` ("ss")** in it, where the ring's
number passes the pointer (`ClockFace.secondTextDp` 9 units, `secondBoxPaddingDp` at 182.4 units; not read out). All three drawables come
from `CookieFace.java` (`bezel()`, `pillCap()`). **Before Android 12** the ring is the old one: an animated vector (`widget_dial_seconds.xml`, groups
`orbit` + `nNN`) run by an indeterminate `ProgressBar` the launcher animates itself - the ring group turns once a minute, each number's
group the other way, so the numbers orbit upright. An animation cannot be told where to start, so there is one AVD + layout + two
animators **per second** (`widget_dial_seconds_00..59`, listed in the generated `WatchDialSecondsLayouts.kt`); `ClockWidget.face` does
`removeAllViews` + `addView(RemoteViews(layout[second]))` into `widget_clock_seconds`, so a redraw resyncs it: the ring is at most
5 s out after the launcher shows it again (it re-sends the last picture on resume, so the ring restarts at that picture's second;
measured on the phone: ~24 s out with a 15 s inexact tick, within ~1 s now), and between redraws it keeps time itself. All of it from `design/cookie-clock/CookieFace.java`),
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
background, its own `widget_simple_fill` view, goes) with **Behind the clock**, a **Tint slider** (0-100% in steps of 5, since
2026-11-01; it was four radio choices) kept as `<id>.backdrop_percent` (`ClockOptions.backdropPercent`, 0 by default so earlier widgets
look as they did); a widget set up before it has only `<id>.backdrop` (Clear / Soft / Medium / Strong = 0 / 30 / 55 / 80) and reads as
that, and the nearest name is still written beside the number for an older build. `ClockBackdrop.nearest` gives the words under the
slider. The preview follows the finger; the widget is sent once, when it lifts. It is a tint of the clock's own face colour, not a blur:
a widget cannot blur the wallpaper under it (RemoteViews has no blur, and a widget is not told where on screen it is), and the footer
says so (the owner asked for blur on 2026-11-01, saying the tint "feels just background colour"); the marks are made to read on that colour (`tintBehind` sets the fill's image alpha;
`WatchDialRenderer` draws the disc and the pill at that alpha and the rings cache is keyed on it), **Clock face | Digital clock**, **Digital clock when small**, and for the digital clock 12/24 h, seconds, date.
The **plain digital clock** (`SimpleClock`, `SimpleClockLayout`: just the time as large as fits, the date only with room) replaces a face
when `min(width, height) < ClockOptions.SMALL_FACE_DP` (92 dp; one cell is 40-70 dp, two 110+) - each size of an Android 12 size map is
decided on its own - or when the user chose it. The clocks may be resized down to 40 dp (one cell) for that, and up to 1,200 dp wide
(`maxResizeWidth`; it was 280 dp, which is why a clock could not be stretched across a phone - the launcher reads it from the manifest, so an
existing widget may have to be removed and added again to pick up the new limit). `ClockWidgets` is the one
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

**Interface font** (`settings/CustomFont.kt`, `settings/FontFile.kt`, `ui/theme/Typography.kt`): GS Flex, **Nunito** (`InterfaceFont.Nunito`,
Google Fonts' variable `res/font/nunito.ttf`, OFL text in `assets/licenses/nunito-ofl.txt`, credited in About), System, or **Your font**. Your font
takes a `.ttf`/`.otf` **or the `.zip` Google Fonts hands out** (told by its first bytes): `FontFileInfo` reads each file's own OS/2, head and fvar
tables (weight, italic, width, `wght` range), `FontSets.choose` keeps one variable file when there is one (every weight from one file) else one
upright normal-width file per weight (at most 8, regular first). The main file is still `CustomFont.FILE_NAME`; other weights sit beside it as
`CustomFont.extraName(100..900)` (`CustomFont.isSavedName`, carried by app backups; a later single-file pick clears them). Drawn by
`customFontFamily(file, faces)`: a variable file at each of 100..900 through `FontVariation.weight`, a family at each file's own weight - before,
one `Font(file)` served every weight and bold text was emboldened (why an uploaded font "did not apply properly"). Cache copies are named by the
main file's size + last change (`faces`/`readyFaces` for the extras), so a new set is a new path. `FontSetsTest` builds fake sfnt files (`FontTestData.kt`).

**Profile** (`settings/ui/ProfilePage.kt`, `settings/ProfilePhotos.kt`, `ui/components/ProfileAvatar.kt`): a hero card (120 dp ringed avatar with a
camera badge, the name, the picture kind in words - "Getting your photo ready..." while a picture is kept, in that same tag, never a line of its
own: one came and went under the buttons and the page jumped 28 dp, `ProfileAvatarSteadyTest`; the card's real changes of height ease with
`animateContentSize` - Choose/Change/Remove photo), an **Avatars** row (`AvatarPreset` Fox/Robot/Mouse from
`assets/avatars/*.jpg`; a tap keeps a framed copy as a photo - no new setting; the robot is cropped to its face), then name, Initial|Emoji, account
button. **GIFs**: a picked GIF (told by its first bytes) skips framing and is kept as it came (`photo-<stamp>.gif`, up to 5 MB, `saveGif`); the
avatar draws its first frame at once and an `AnimatedImageDrawable` (API 28+, `ImageDecoder`, 512 px) over it, still where animations are off.
`isProfilePhotoName` accepts `.jpg|.gif`; the backup takes a `.gif` only if its bytes are a GIF.

**Welcome tour** (`onboarding/OnboardingTour.kt`, reference: DropSauce's onboarding): four steps in a pager (look: theme + colours, applied live; name +
avatar; the reminders permissions; where to start), animated pill dots, an arrow that becomes a check, Skip, Back steps back. Shown only on a
**new install**: `welcomeNewInstall` (NormPixApplication) compares package first-install and last-update time, **or** finds no ledger yet
(`hadData`: `normpix_finance.db` absent before the container opens it - a new install, or one whose storage was cleared, which keeps an update's
times: fixed 2026-11-01; verified on the phone: a fresh qa install shows the tour, and so does an updated one after Clear storage); installing a
build **over** an existing NormPix is an update and is not welcomed, by design. The preference `onboarding_done`
defaults to true, so updates and tests never see it; it is outside backups and reset. **Settings > Welcome tour** (a row in the System group, also
found by searching "tour", "welcome" or "onboarding": `SettingsSpecial.WelcomeTour`) and Settings > About > Welcome tour play it again. It covers
the app (composed beneath, touches blocked) until `finishOnboarding`. Its look is the app's own: each step is Home's `Hero` card (primary, the cookie
bleeding off its corner) holding the step's picture and title, with the tonal cards below. **The pictures** (`onboarding/TourArt.kt`, 2026-11-04; the
owner chose "3D stickers" over a flat drawn scene and a morphing badge, after "onboarding is not feeling fun"): each step is a little scene of
Fluent 3D emoji (the bundled `assets/fluent3d` set, MIT) on pads of M3 Expressive shapes (cookie, sunny, four-sided cookie, soft burst, pill) in the
theme's containers, so the pads follow Light/Dark/Pure/wallpaper colours and the pictures keep theirs: 🎨 💳 🪙 ✨ (welcome and look), the live
avatar + 🪪 ✍️ (its pad is `tertiaryContainer`, because the empty avatar's disc is `primaryContainer`), 🔔 ⏰ 🗓️ (reminders), 🚀 🎉 🧾 (start). The
pictures are 128 px, so the main one stays at 80 dp (the avatar 96). Motion, all off with reduced motion: the stickers pop in one after another when
a step comes into view (`popOf`: 520 ms each, 80 ms apart, an overshooting curve), lag behind the page as it is swiped (`StepPlace.offset` x each
sticker's depth), and bob gently on the step at rest (a few dp, the live clock at 30 fps, only where `LocalLiveMotion` allows, so tests settle). The
art is decorative (`clearAndSetSemantics`, tag `tour_art`); the tour decodes all its pictures on open (`tourPictures`). Some pictures are drawn off the middle of their box (the credit card and the ID card sit 20 px low in 128): `pictureOffset` nudges them
back onto their pad's centre and turns them about their own middle, and `TourArtCentringTest` measures every padded picture's opaque bounds
(the welcome step's card was visibly low in its blob, owner 2026-11-05). **Step 3 ("Reminders that arrive")** asks for notifications as before and,
under "Make sure they reach you", shows only what is still to be allowed, each with what it is for and a button that opens Android's own page
(`openAndroidPage`; read again on resume): exact alarms (only where not granted - Android 13+ grants them at install), a **card over other apps**
(optional; turning it on sets `reminderOverlayEnabled` and opens "Display over other apps"; says NormPix does not see the screen) and
**full-screen alerts** (Android 14+, optional). All optional, the same switches as Settings > Notifications. A wide hero card (inside
>= 560 dp) puts the art beside the title at the phone's 323 dp. The first step's Colors card is Settings > Appearance > Colors' own picker
(`PaletteChoices(wrap = true)` - the same squircle tiles, wrapped into even rows instead of a sideways-scrolling row inside the pager - and
`ColorSourceChoice`, Wallpaper colors | Other colors), so the two look alike (owner, 2026-11-04).

**Banks** (`banks/BankCatalog`, `wallet/BankDirectory`): 158 built-in banks, each with a `BankCategory` (public sector, private, small finance,
payments, co-operative, regional rural, foreign, earlier names - a merged bank carries `note` "Now part of ..."). Ids are what accounts and cards
keep: only ever add, never change or reuse one; no two banks may share a normalised name or alias (`BankLogoResolverTest` checks it) and short
common-word aliases are avoided because the resolver matches whole words in receipt text. Logos: the `assets/banks/` SVGs (IFSC-code names) and,
for banks that have only that, a round full-colour picture from the brand set (`BankLogoArt(symbol = "brandlogos/<key>.webp")`, drawn by
`BankArtwork`). A bank with **no artwork wears a monogram** (`BankMarks`: 1-4 capitals in the badge's own tint, drawn by `BankMonogram` in
`BankGlyph`; the listed abbreviation for a built-in bank, else the initials of the name's distinctive words; a name the directory does not know
still gets the generic bank glyph). Eight banks (Deutsche, Barclays, Bank of America, SBM, Fi, Uni, Niyo, Freo) had hand-made imitations, not their
logos, and now wear monograms; their files were deleted. The listed marks were proposed by DeepSeek V4.1 Flash and then checked (format, uniqueness,
the weak 1-2 letter ones replaced by the conventional "...GB" forms; `BankMarksTest`). **Settings > Banks & Categories** (since 2026-11-01 one page, `BanksAndCategoriesPage(initialTab)`: `AppPrimaryTabs` [Banks | Categories] as the list's first item under the large title, each tab the old page's content - `BanksContent`, `CategoriesContent` - with its own header actions and bottom search, kept per tab by a `SaveableStateHolder`; the row keeps the persisted name `SettingsPage.Banks`, its summary is "N banks · M categories"; the Settings row opens Banks, `openCategories()` - Finance settings, the "+" tile, search - opens Categories). The Banks tab: the whole
list, grouped by kind, with a search that also finds a bank by its other names (it moves into the header, beside "+", once scrolled away, as on Settings),
chips (All / In use / Added by you / each kind), "In use" marks, a detail sheet, and Add a bank (`BankFormSheet`). A bank the user added can be **renamed**
(`BankRenameSheet`; its `custom-...` key never changes, so accounts and cards follow) and **deleted** (a confirm sheet; refused while an account names it,
and the sheet does not offer it while a card does - `FinanceRepository.renameBank` / `deleteBank`, additive: nothing is dropped). More logos need a source
that is bundled and licensed: the owner can send them.

**Launch splash** (`navigation/CookieSplash.kt`, `LaunchSplash.kt`; 2026-11-07, reference: DropSauce's splash - its timings and idea, drawn from scratch;
Settings > Appearance > Transitions > Launch, `LaunchAnimation`, key `launch_animation`): **Cookie** (default; on Android 12+ its intro is Android's own launch screen, see "The intro is Android's" below) spins a nine-lobed
cookie in around the app's monochrome mark (`ic_launcher_monochrome`, tinted `onPrimary`, on `primary`, over `window_background`) and, once the data has loaded, opens it
like a window onto the app; **Name** is the older writing of "NormPix" letter by letter and the app receding under it. Both are shown only on a cold start and not
with animations off; the Name splash and the cookie below Android 12 hold the app's first composition until the intro is done (`onWritten`: the heaviest work of a cold start must not land among
moving frames) and consume touches until they have gone. The cookie's motion is pure functions of milliseconds (`CookieMotion`): **intro 600 ms** - the cookie's scale
and its lobes are springs (`spring(t, damping, omega)`, closed form: damping 0.6 / omega 15.1 overshoots ~9.5% at a quarter of the way; the lobes 0.7 / 11.8), it
turns from -150 degrees to upright on emphasized decelerate, the mark grows 0.6 -> 1 from 15% in; **exit 750 ms** - the mark sinks (100-250 ms, emphasized
accelerate), the cookie squishes to 0.82 (0-250, standard), then grows (250-750) until it clears the screen's corners by 2%, turning an eighth of a turn
(150-550, emphasized decelerate) while its lobes smooth into a circle (250-350), its own colour fading out at 230-250 so the app shows through; the ground
is the screen less the cookie (`PathFillType.EvenOdd`), so the opening is a real hole. One `Animatable` clock per half, so a late frame shows where the motion
should be by then. The cookie is a polar curve (circle + 9 lobes, 216 points smoothed through their midpoints), rebuilt per frame. Tests: `CookieMotionTest`,
`CookieSplashTest` (the corner is the app's colour after the exit), `CalmFramesTest`, `LaunchSplashTest` (pinned to Name, plus two for the cookie at the root).
**One splash, not two (2026-11-07, owner: "why do we have 2 splash screens ... show only the new one"; the empty launch screen described here is now only the Name splash's `Theme.NormPix.LaunchBlank` - the cookie's own launch screen carries its intro, next paragraph)**: the owner's recording showed Android 12+'s own launch screen (the launcher icon, green, on `window_background` dark green) and then the cookie on the palette's brown. Android cannot be told to draw no launch screen, so `values-v31/themes.xml` and `values-night-v31/themes.xml` (the whole `Theme.NormPix`, duplicated because a night qualifier outranks a version one) set `windowSplashScreenBackground` = `@color/window_background` and `windowSplashScreenAnimatedIcon` = `@drawable/splash_icon_empty` (a fully transparent vector): an empty screen of the window's colour. `CookieSplash` draws its ground in the same `colorResource(R.color.window_background)` instead of the palette's surface, so nothing changes colour where one gives way to the other (it follows the *system's* light/dark, as the launch screen does, whatever the app is set to). The cookie's own colours stay the palette's. The old `Name` splash still draws on the palette surface (it is the kept option). Cost: the icon no longer shows during a slow cold start - the person sees a blank window colour until the cookie starts. `LaunchScreenTest`, `CookieSplashTest` (2 new). Before Android 12 there is no launch screen: the window background alone shows, the same blank colour.
**The intro is Android's (2026-11-07, owner: "why is this taking such a long time and not smooth at all initially", DropSauce as the reference)**: a recording showed ~1.4 s of an empty screen on the phone (~2.3 s on the emulator) before the cookie appeared, then the finished cookie held ~2 s. Cause: the cookie was drawn in Compose, so it could not start before the process, the activity and the first composition existed, and the real app was composed only after the intro (`holdBack`), so its heaviest work queued behind it. DropSauce's way, taken as behaviour only (GPL-3.0): the intro is an animated vector the *system* plays from the first moment of a start. `res/drawable/avd_splash.xml` (288 dp icon: the cookie group's scale and rotation, a 216-point Q-path lobes morph, the mark's scale and three `fillAlpha`s) is `windowSplashScreenAnimatedIcon` in `values-v31/themes.xml` / `values-night-v31/themes.xml` (the whole `Theme.NormPix`, duplicated because a night qualifier outranks a version one), on `window_background` (Android 14+: the system's own surface colour, `values-v34/colors.xml` = `system_surface_light`, `values-night-v34` = `system_surface_dark`, which is the app's `dynamicLightColorScheme` surface for the wallpaper palette - ground #FDF6F4 = page #FDF6F4 on the phone; before 14 a fixed #FBFDF8 / #101410), `windowSplashScreenAnimationDuration` 600. **It and `splash_icon.xml` (its last frame) are generated** by `native/design/launch-splash/LaunchSplashAvd.py` from `CookieMotion`'s springs and easings (sampled every 10 ms into `pathInterpolator`s; run it with `C:\Users\saiha\AppData\Local\Python\bin\python.exe -I`, the `python` on PATH is a Store stub): change `CookieMotion` and you must re-run it, and `LaunchSplashAvdTest` (reads the written XML, checks every curve against `CookieMotion`) fails until you do. Colours: `splash_cookie` / `splash_cookie_ink` (`values{,-night}{,-v31}/colors_splash.xml`) are the wallpaper accent (`system_accent1_600`/`_0` by day, `_200`/`_800` by night, which is the default palette's primary/onPrimary); a palette that differs (Custom accents, other wallpaper styles) gets the accent's colour first and a `TINT_MS` 240 ms blend after the hand-over, because a launch screen cannot follow a palette. Android masks the icon to a 192 dp circle, so the cookie is a **fixed `CookieMotion.RADIUS_DP` = 80 dp** (not a share of the screen: the system's cannot scale, and Compose's takes over from it) and must stay under 96 dp at its overshoot (tested). **Hand-over** (`MainActivity.onLaunchScreen`, `navigation/LaunchScreen.kt`: `Pending | None | Held(introEndsAtNanos, release)`): Android hands the app its `SplashScreenView` a frame or two after the first draw; for a cold start with an animated icon (`iconAnimationDuration > 0`), animators on and the Cookie choice the view is *kept* (4 s failsafe) and `NormPixRoot` gets `Held`; otherwise it fades in 90 ms as before. `CookieSplash` draws the *settled* cookie (launch colours, launch ground) from its very first frame, under the system's screen, so where the system swaps its screen for the app's the cookie shows and never an empty ground (the owner's phone showed an **empty frame** at the hand-over and another where the view was removed, when the cookie was drawn only just before `remove()`). It waits until nothing visible is left to move in the AVD (`iconAnimationStart` + `LAUNCH_DELAY_MS` 100 + `INTRO_SETTLED_MS` 420: the springs are within 0.01 of rest by then, tested; the last 180 ms of the 600 are an unseen tail), waits `COVER_FRAMES`, calls `release()` (the view **fades** over `RELEASE_FADE_MS` 90 and is then removed: a fade between two identical pictures hides a dropped frame), waits the fade out, blends the colours to the palette's (`tint`), then holds for the data and `CalmFrames` and runs the usual exit, with no `HOLD_MS` beat. **The ground** is `lerp(window_background, scheme.surface, tint)`, so the opening reveals a screen of the ground's own colour (the first build kept the window colour and the owner's recording showed a greenish ring #FAFCF7 round a pinkish page #FCF5F3: "missing how the animation reveals the screen"). Every AVD animator has `startOffset` `LAUNCH_DELAY_MS` 100 (generator `START_DELAY_MS`): the system starts the icon when it makes the launch screen but the window is seen only after the launcher's opening, so without the offset the first ~100 ms were never seen (a disc that popped into a cookie). With no hand-over within `HANDOVER_WAIT_MS` 1.2 s (counted after two drawn frames) it plays its own intro as before; Android 11 and older never get one (`LaunchScreen.None`). `splashWritten` starts true for Cookie when the launch screen is not `None`: the app composes at once, under the system's cookie, so its work overlaps the intro. **Name** keeps Phase 54's empty launch screen: `Theme.NormPix.LaunchBlank` (the empty `splash_icon_empty`), which `MainActivity.keepLaunchScreenThemeInStep` hands to Android with `SplashScreen.setSplashScreenTheme` (from Android 13, off the main thread; `Resources.ID_NULL` for Cookie; Android persists it by *name*, so that style name must never change; on 12/12L Name users see the cookie's launch screen first). Seen on a device (2026-11-07, owner's phone: motorola edge 50 neo, Android 16, the `qaRelease` build, screen-recorded and cut with OpenCV every 33 ms): the first version of this (hard `remove()`, no cookie under the system's) showed in the owner's own recording a disc, an empty frame, the finished cookie (the intro popped), another empty frame at the release, and the greenish ground ring at the reveal; the current one shows ~100 ms of ground, the dot growing through the lobes and the mark (~250 ms visible), no empty frame in the hold, the ground the page's colour, the opening ~200 ms, and Home open ~1.2 s after the tap; `am start -W` TotalTime 260-325 ms. The same code as the debuggable `qa` build: cookie at ~0.2 s too, but Home only at ~2.7 s and TotalTime ~1.5 s - **a debug build is what made it slow and held the finished cookie ~2 s** (Home composes slowly there and the cookie waits for it), so time and record a start with `qaRelease`, never `qa`. Not seen: the dark theme on the phone (tests cover its ground), a non-default palette, Android 12/12L, the Name choice and animator scale 0 on the phone. Tests: `LaunchScreenTest` (the v34 system surface by day/night, the fixed stand-in at sdk 33), `LaunchSplashAvdTest` (the curves and the start offset), `CookieSplashTest` (the cookie settled from the first frame, hand-over, release order, fade, colour and ground blend, and a pixel comparison with `splash_icon.xml` that writes `build/launch-splash/cookie-vs-launch-screen.png`).
**Two things only the phone showed (2026-11-07, qa build, screen-recorded and cut with OpenCV)**: (1) on Android 12+ the system keeps its launch screen (the icon)
over the app until the first frame and then fades it at its own pace, so the intro played *behind* it - the first frame seen was the cookie already full size.
That was first answered by taking the screen away with a 90 ms fade (`MainActivity`, `splashScreen.setOnExitAnimationListener`, then `remove()`; no new dependency) and a `systemSplashGone` flag;
the same day that was replaced by the hand-over above (the system's screen *is* the intro now); only the Name splash (`LaunchSplash`) still waits for the screen to go (`launchScreen !is Pending`, up to `SYSTEM_SPLASH_WAIT_MS` 1.5 s, in case it never reports). (2) The exit started
as the data arrived, so the real Home's first heavy frames landed in the opening and it showed the same small hole for five frames (a ~250 ms stall). The exit now
waits for the data **and** `CalmFrames`: 4 frames in a row, none later than 40 ms after the one before, capped at `SETTLE_MAX_MS` 900. The limit is for a stall, not for
the pace: a slow build draws at ~23 ms a frame, and a first version that wanted 22 ms never saw calm and always waited out the cap (+1 s on every start). Measured after:
the exit is 19 distinct frames over ~440 ms, 22 ms apart, one 66 ms gap. The qa build is unoptimised: its cold start shows the system icon for ~1.5-2 s first.

- **Quick add** (2026-11-07, `widgets/QuickAdd.kt`): Add expense, Add income and Transfer on the app icon's long-press menu (`QuickAddShortcuts`: three *dynamic* shortcuts, published in `MainActivity.onCreate` on a fresh start; dynamic and not static because the launcher entry is one of ten `activity-alias`es, and a static shortcut list (an XML resource) would have to be named on each) and an "Add expense" Quick Settings tile (`QuickAddTileService`, in the manifest with `BIND_QUICK_SETTINGS_TILE`, so only the system can bind it; the user adds it in the tile editor). Both open the app on the new-transaction sheet through `WidgetIntents.intent`, the same plain-word `WidgetRequest.Add` the Add widget sends, acted on once the app is unlocked - nothing of the ledger is on a shortcut or the intent. The tile launches with a `PendingIntent` from Android 14 on; the `Intent` form it needs before that is deprecated there and lint (`StartActivityAndCollapseDeprecated`) makes it an error unless it sits behind `SDK_INT < UPSIDE_DOWN_CAKE`. Tests: `QuickAddTest`.

## 9. About guide (`about/`)

Settings > About NormPix opens a page (not a dialog): name, version, a **Feature guide** pill (`PagePill`, the same as the update page's
Changelog; since 2026-11-01 - it was a heading, "How NormPix works", over a grid of every topic on About itself), your data, crash log,
welcome tour, version, **On GitHub** (2026-11-08, owner: "so it is easy to go there to check things": one row, "NormPix on GitHub", that
opens `https://github.com/<BuildConfig.UPDATE_REPOSITORY>` in the browser with `ACTION_VIEW` (`url.toUri()`, the KTX form lint asks for) inside `runCatching`, the address written on
the row; the same constant the updater reads, pinned by `AboutPageTest`; Android opens the page, NormPix connects to nothing new; found by
searching "github", "source code", "repository" or "releases"), credits. The
pill opens `GuideIndexPage` (route `guide`, `SettingsDetail.GuideIndex` in the tablet panes, `openGuideIndex()`): a card per topic (`GuideTopic`:
AddTransaction, ShareReceipt, Home, Activity, Plans, Insights, Wallet, CardExchange, Widgets, LookAndFeel, Backup, Privacy), each opening its
page; a topic's Back goes to the guide, the guide's to About. Topics are data (checked by `GuideTopicTest`). Pictures (`GuideArt`) are **real
screenshots of the app** (`GuideShots`, `assets/guide/<topic>-<light|dark>.webp`, 480 x 527, ~16 kB each, ~340 kB in all, the dark one in
a dark theme) shown as a phone's screen rising out of the topic's colour; made from the real screens on the sample data in the Volt accent by
`GuideScreenshots` (a test skipped unless `-Pguideshots=<absolute path to app/src/main/assets/guide>`; Robolectric writes the WebP itself) -
**run it again when a screen a topic shows changes**. ShareReceipt has none (a shared receipt needs ML Kit) and keeps the drawing in code
from theme colours, the fallback for any topic without a screenshot. Inert and hidden from screen readers. **When a
user-visible feature changes, update its topic text.**

## 10. Appearance catalogue

Theme (System/Light/Dark) + Colors (Wallpaper colors on Android 12+, or Other colors: Emerald, Ocean, Royal, Coral, Amber,
**Volt** (default, hand-drawn), **Pure** (black/white, hand-drawn); each shown as a tile with the three colours it gives
the app), App icon (ten launcher icons), Interface font (GS Flex presets Standard/Zenith/Neo/Impact/Airy or hand-set axes,
Nunito, System, or the user's own .ttf/.otf/Google Fonts .zip), Icons (**interface icons Material | Lucide** - design-system "Interface icons: Material or Lucide"; category style 3D/Emoji/Outline, bank logo style, shape behind), Progress
indicators (Wavy | Flat), Transitions (six page styles incl. **Linux window**, five sheet/dialog styles, "Try it", and since 2026-11-07 **Launch**:
**Cookie** (default; `navigation/CookieSplash.kt`) | Name (the name written letter by letter, `LaunchSplash`), and **Dock**: **Glide** (default) | Fade in place, see
`design-system.md` section 5), Effects
(background blur, footer blur, card tilt), and under Colors **Artwork colors** (a switch, on: Add/Edit sheets take their colours from the picture or card they show - design-system "Artwork colors"), Navigation & controls (footer labels, Reset action menu - the "+" menu itself is
arranged in its Edit mode, bottom controls, back button style, month control), Layouts (Top
spending, Categories in Insights, Accounts, Cards). Search finds each choice by its own name.

## 11. Open and parked (check `native/TASKS.md` Backlog; verify against the code first - old "[ ]" lines go stale)

- Per-bank card designs: blocked on the owner's designs.
- Samsung-style reminders: built 2026-11-04 (floating card + full-screen alert + notification); open: a phone test, and a re-alert for a non-repeating reminder that nobody handled.
- Preview update channel + data-safety fallbacks: designed, awaiting the owner's go.
- Not tried on a phone at the end of Phase 36: the About guide's pictures at real sizes, the Colors tiles, Pure on OLED,
  widgets on every launcher, the Top spending carousel's feel.
- Recurring-payment rows have no swipe actions.
- Renaming/deleting a bank the user added (Settings > Banks & Categories lists and adds them, but cannot rename or delete).
- Fixtures for self-transfer receipts are synthetic until a real one is shared.
