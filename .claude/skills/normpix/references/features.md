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
  `SplitStatBanner` for every active credit card that owes something and has a due day, soonest first (**several are a stack** since 2026-11-11:
  swipe for the next, hold to lay them all out, the heading's Stack button gathers them; one bill stays a plain banner) - "Due date" with how far away it is, a pill
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
  **Notifications page** that lists everything that needs the user -
  overdue reminders, budgets over or nearly used, cards about to expire, credit cards near their limit (section 2), a crash report, a backup due (or an automatic backup that failed), an update waiting - **grouped by
  kind** (Reminders, Budgets, Cards, Backup, NormPix = crash report + update), each notification drawn as **Home's banner** (since
  2026-11-14, owner: "Notification screen cards are not following the new design"; `BannerCard`: its two panels over its bar of buttons,
  the status colour throughout, never stacked; pressing the panels opens what it is about). From 2026-11-09 to 2026-11-13 they were
  tonal list rows. **Home's Needs attention is one banner** ("4 things need attention", the kinds in a line, the
  colour of the worst) that opens this page (owner, 2026-11-09); **an update keeps its own banner on Home** with its own buttons, as before,
  and is also listed on the page. There is **no history**: a
  notification lives as long as the thing it says does. **Opening the page is seeing it all** (all keys listed are written as seen;
  the keys of things that have gone are dropped, so the same budget over again next month is new again); the bell then shows none
  until something new arrives. The **+ menu shows the newest three unread above its panel** (on a container of their own with the sheets'
  blurred-glass surface, 2026-11-13), each opening what it is about, with
  "All notifications (n more)" opening the page; nothing at all when nothing is new. Phone dock only: the wide-window rail has
  no + menu, so no strip there (owner, 2026-11-08), just the bell. Settings > Notifications (reminder delivery)
  is linked at the foot of the page. Tests: `NotificationsTest`, `NotificationsRenderTest` (pictures in
  `app/build/qa_notifications/`).
- **Plans & Activity** (`screens/PlansAndTransactionsScreen.kt`, `finance/ui/TransactionsScreen.kt`): two tabs.
  *Activity* = the transaction history: month switcher (two looks, `MonthControlStyle` Track | Split), **All time** scope,
  **by day** (`DaySwitcher`; a day heading shows what went out and opens that day), an **Upcoming** row for entries dated
  after today, search + filters (type, account, category, person, amount, dates: `TransactionFilterSheet`,
  `DateRangeSheet`), Gmail-style swipe (`SwipeableTransactionRow` on `SwipeActionRow`, a setting): left = Edit (opens the editor, the
  row springs back), right = Delete, which **asks first** since 2026-11-11 (owner: "when I swipe delete it's not asking for
  confirmation"; `swipeDeleteAsks`, Settings > Finance > "Ask before a swipe deletes", on by default: the row springs back and the
  usual question comes up, "no" leaves it where it was; off is the earlier way - the row flies off and folds away, **no confirmation**,
  Undo snackbar - kept as the choice), details sheet with Delete/Edit and the attached picture. On a month's **last day** the month ends
  exactly at the cutoff, so the Upcoming query keys on `range.end == cutoff` (month view only) to keep next month's entries listed. *Plans* = Budgets (per category + overall, history page, sub-categories fold into the parent),
  Recurring (`RecurringView`/`RecurringEditor`; rows record themselves on their day; **a payment and its reminder are kept in step** -
  section 5, `RecurringReminders`; a row with a reminder shows a small bell and says "with a reminder" to TalkBack), Reminders (`RemindersView`), Goals (`GoalsView`, 2026-11-15: savings goals, see section 2).
- **What repeating payments cost, and suggestions** (2026-11-14, no database change; `finance/domain/RepeatingCosts.kt`, `RepeatSuggestions.kt`,
  `finance/ui/RepeatingCostsUi.kt`): at the top of Plans > Recurring, the two-panel banner (`SplitStatBanner`'s two-figure overload) says
  **"A month" / "A year"**; it opens the page **Repeating costs** (`Routes.REPEATING_COSTS`, `openRepeatingCosts`): each active, not-finished
  **expense** rule, biggest first with its amount and frequency, its cost a month, "Next 12 Dec" and a **switch** (the rule's `active` flag:
  `FinanceActions.setRecurringPaused` saves the rule through `saveRecurringRule` - nothing is deleted; resuming moves `nextDate` to
  `firstOccurrenceAfter(now)` as the editor does, so what was missed is skipped; the reminder follows via `ReminderLauncher.follow`); paused
  ones are listed after, dimmed, and cost nothing. **Money rules**: cost a year = amount x (daily 365, weekly 52, monthly 12, yearly 1) / interval,
  a month = the year / 12, both **rounded half up in integer paise**; the totals are the sum of the years and the year over 12 (so the rows,
  each rounded alone, need not add up to the banner); transfers (a card bill, a SIP) are **not spending** and income is not a cost, so neither is
  counted. **Suggestions** (`suggestRepeats`, pure; `FinanceRepository.repeatSuggestions`): past *expenses with a payee and no `recurring_id`*
  grouped by `payeeKey` (trimmed, lower case, spaces collapsed); a group qualifies when going back from the newest payment the gaps fit one frequency
  (weekly 6-8 days, monthly 26-35, yearly 350-380) for at least 3 payments in the last 13 months (the run is cut back from its oldest end while
  the amounts are not all within 20% of the median, or a gap breaks it), the newest is at most 1.5 periods old (10 / 45 / 547 days), no *active*
  rule has that payee, the user has not dismissed it, and **the payee is not a loan's name** (`loanPayees`, 2026-11-16: an EMI's interest is an expense paid
  to the loan every month and was offered, so adding it would have counted the interest twice; `repeatSuggestions` passes every `isLoan` account's name). **Deviation from the brief: a yearly pattern needs only 2 payments** (looked for over
  36 months) - three would take two years and a 13-month window can hold two at most, so it could never be offered. The dearest a year first,
  at most 3 shown. Cards under the banner: **Add** opens `RecurringEditorSheet(suggestion = ...)` filled in (payee, amount, frequency, account and
  category of the newest payment, first payment on the day expected, or the next one after it when that has gone by - never a past day, which
  would record payments already in the ledger); **Not this one** keeps the payee key in the finance DB's **`meta` table** (`dismissed_repeat_payees`,
  one key per line; not in a backup, kept across a restore and an Undo - `FinanceRepository.restore` puts it back - and cleared by a reset).
- **Cash-flow forecast** (2026-11-14, no database change; `finance/domain/Forecast.kt`, `finance/data/PlanningData.kt`, `finance/ui/ForecastPage.kt`):
  a page (`Routes.FORECAST`, `openForecast`) opened from the **Forecast** row at the top of Plans > Recurring (not a fourth Plans tab: four pills
  do not fit the secondary tabs). It shows the *money that can be spent* day by day from today to an end date: chips **30 / 60 / 90 days**,
  **Until payday** (the next occurrence of the active income rule with the biggest amount, `nextPayday`; the chip is absent with none) and
  **Pick a date** (`DatePickerDialogFor(earliest = tomorrow, latest = today + 12 months)`; the page says "Pick a date up to 12 months ahead").
  The choice and the everyday switch live on `ShellState` (`forecastEnd`, `forecastEveryday`): session only, never saved. A `SplitStatBanner`
  (two-figure) gives the **end balance** and the **lowest point** (earliest day when tied) with the day; a notice says when it goes below zero;
  `BalanceChart` (Charts.kt) draws the line (dashed zero line and a red wash below it when it goes negative, the lowest point ringed, touch to read
  a day; TalkBack gets "lowest point ... ends at ...", not every point; with amounts hidden only the dates); then the **dated items grouped by
  day**; the footnote "Only what's scheduled is known; everyday spending is an estimate." **Money rules** (`buildForecast`, pure, integer paise):
  start = today's balances (as of end of today, `accountBalances(asOf)`) of the *spendable* accounts - kinds Cash, Bank, Wallet, Other, not archived,
  and **not owed** - "owed" is the account form's switch, kept as a negative **opening** balance (`Forms.kt`); an account merely overdrawn today
  still counts, its shortfall with it (main agent's review, 2026-11-15: leaving it out made the forecast look better than it is). Added on their
  dates: entries already dated after today and the next occurrences of active rules up to the end date (`occurrencesUntil`, bounded by a date, with
  interval and end date; a rule due later today counts today). An income into a spendable account adds; an expense from one takes away; a transfer
  from spendable to non-spendable (a card bill, savings) takes away, the other way adds, between spendables is nothing. **An expense on a card is not an
  outflow on its day**: it goes into the card's bill, due on the due date of the statement it falls in (`dueDateFor`); a card with no due day has no bill, so
  its expense counts on its day. Card bills: each card's bill as Home shows it (`cardBill`, a late one counted today), what was charged since the last
  statement (due after the next one), and the charges to come, each on its due date; a transfer onto the card dated by the due date **pays that much
  of the bill** (so a repeating transfer that pays the card is not counted twice, and one that pays part leaves the rest). **Everyday spending**
  (switch, on by default): the expenses of the last 90 days that came from no rule (`recurring_id` null), on every account, /90 rounded half up,
  taken off every day after today; shown as "About Rs X a day ... It is a guess". Tests: `RepeatingCostsTest`, `RepeatSuggestionsTest`, `ForecastTest`,
  `PlanningDataTest`, `RepeatingCostsPageTest`, `RepeatSuggestionCardsTest`, `ForecastPageTest`, `PlanningLargeTextTest`.
- **Bills calendar** (2026-11-15, no database or backup change; `finance/domain/BillsCalendar.kt`, `finance/data/PlanningData.kt` `calendarLedger`, `finance/ui/BillsCalendarPage.kt`,
  the markers of `MonthCalendar` in `DateTimeSheets.kt` - design-system section 2): a page (`Routes.BILLS_CALENDAR`, `openBillsCalendar`) opened from the **Bills calendar** row under
  Forecast at the top of Plans > Recurring ("Card bills, repeating payments and reminders by day") and from a **Calendar** action on Home's **"Next 7 days"** heading (the heading is
  what the brief called "Coming up"; the action is its `trailing` slot, before Plans, so the heading is built as it was; Home shows that section only while something falls in the next
  7 days, so the action is there only then). **One month at a time**, the current one first, **one back and twelve ahead** (`calendarFirstMonth` / `calendarLastMonth`), switched
  from memory (nothing is read again; the facts - the rules, the entries dated ahead to the end of the last month, the reminders, the cards' bills - are gathered once and `billsMonth`
  is worked out per month); nothing about the month is kept (the page always opens on this one). The page: a `SplitStatBanner` (two figures) **"Due this month" / "Coming in"** with
  the note "From today" or "In March" (this month counts from today, any other the whole month); the month grid (`MonthCalendar` with markers, in a panel of its own, then a legend
  in words); the **chosen day's things** under a heading ("Today, 10 Mar", "Tomorrow, ...", "Wednesday, 12 Mar") - the day is today in this month, else the first day with
  something (the month's first when empty), and a day touched stays until the month changes; a note at the foot. **What goes on it** (`billsMonth`, pure, integer paise): each card's
  bill on its due date (`rememberDueBills`, one bill per card, the unpaid part of the last statement as Home shows it - the **next statements' bills are not known here**, the
  Forecast works those out); the occurrences of the **active, not finished** repeating payments in the month (`occurrencesUntil`, with interval and end date); the entries
  already entered with a date **after today**; the reminders **not done**, on their `effectiveDueAt` day (a snoozed one where it was put; **one item per reminder - a repeating
  reminder is not expanded into its later times**, and there is no "dismissed" state: the alert card's Dismiss only puts the card away). **A reminder about a repeating payment that
  has an occurrence that day, or about a card (account link) that has a bill that day, is folded into that item** ("With a reminder") instead of listed beside it - the payment and
  its reminder are one thing; a reminder on another day, or about an account with no bill that day, is its own row, saying what it is about (`rememberResolvedLink`). **Past days
  show what the sources still hold**: a bill that is overdue (said "Overdue"; an overdue bill from before last month is on no month), a reminder not done, a repeating payment not yet
  recorded; a payment that was made is in the ledger and Activity, not here, and nothing says "paid" beyond what a source says of itself. **Summary rules**: "Due this month" =
  bills (what is left unpaid of one, once - an overdue unpaid bill counts in the month it fell due even when before today) + expense occurrences and entries + **transfers that go
  to an account that is not for spending** (a card, savings, a loan; a transfer between two spending accounts - cash, bank, wallet, other - is neither) ; an expense **charged to a
  credit card with a due day is not counted** (it is in the card's bill); "Coming in" = income occurrences and entries, whichever account. **A transfer onto a card dated by its
  bill's due date pays that much of the bill** (`by` = the due date, or today for a late one; the same netting as the Forecast), so the transfer counts in full and the bill what it
  leaves - never twice; one dated after the due date, or in an earlier month for a later bill, follows the same rule by its date. Items before today in the current month are on the
  calendar but outside the figures. A day lists, in order: the bill, what goes out (the dearer first), what comes in, the reminders. **Rows**: category avatar (a transfer's, a bill's
  card icon, a reminder's category icon), title (payee, else the category, "Payment to <card>", "<card> bill"), amount or "Reminder", and a line of what it is in words ("Repeating
  payment · HDFC", "Upcoming income · SBI", "Repeating card payment · HDFC → ICICI", "Card bill · Overdue", "Reminder · when · what it is about"); at 1.6x the amount goes under the
  name. **A tap opens what it is about** with the existing openers: a repeating payment `editRecurring`, an entry `showDetails`, a bill `payBill(card, suggested = what is due)` (as Home's
  banner), a reminder `ReminderFlows.edit`. TalkBack: each day reads "Tuesday, 10 March 2026, 3 things due" / "nothing due" and the grid is a collection; amounts hidden hide every figure,
  the banner's spoken line included; reduced motion: no slide between months, no size animation. The sample data needs nothing added: its two cards' bills, eight repeating payments (on next month's
  grid; this month's are recorded) and seven reminders fill the calendar. It has **no entries dated ahead on purpose** - `DummyDataTest` says nothing in it is in the future, and an
  Upcoming row would show in the README pictures - so that kind is the one the sample data does not show. Tests: `BillsCalendarTest` (26: each source, interval + end date, paused/finished, reminders done and folded, the order,
  the default day, the summary from today and for another month, the bill counted once beside a transfer, the transfer / card / income rules), `BillsCalendarPageTest` (markers on the
  right days, selecting, each opener, month switching and its bounds, animated vs reduced, hidden amounts, empty, the two doors); `DateTimeSheetsTest` / `DateRangeSheetTest` unchanged.
  Not seen on a phone.
- **Insights** (`finance/ui/InsightsScreen.kt`, `finance/domain/Insights.kt`), redesigned 2026-11-13 after the owner ("not liking the
  insight screen design somehow and the chart also") and **two research reports** (Haiku 5.5 xhigh and Gemini 3.8 Flash High, same
  brief, checked against primary sources - `verified-facts.md` "Charts and Insights design"): one story, **this month before the months
  around it**: the hero (amount, a **verdict sentence with the difference in money then as a share**, `verdict()`: "₹4,100 less than this
  time last month (7%)", then Income and Saved) -> **"How this month built up"**, the running-total `PaceChart` as a panel of its own
  (168 dp, solid this period, dashed the one before, the amount it points at above it, scrub the whole plot; it was the 104 dp chart
  inside the hero) -> ranked category rows (**each with its change from last month as a small arrow and figure, "Same" or "New"**, never
  red or green: `CategoryRowChange`; **no separate "What changed" list any more** - it listed the same categories twice; the figure
  is not shown when the period before had no spending) -> By person -> the six-month bars (zero-based, bars standing on a firm baseline,
  rounded tops) -> "More detail" collapsed (day by day, income sources). Categories can still be (Settings > Appearance > Layouts >
  **Categories in Insights**, `InsightsCategoriesLayout.Carousel`) the Home top-spending carousel; days that were picked always show
  the list; default List. A month in progress is compared like-for-like (`comparisonRange`); `heroBasis` is the one phrase every
  comparison on the screen uses. Custom date ranges. Tests: `InsightsScreenTest`, `InsightsRenderTest` (pictures in `app/build/qa_insights/`).
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
  **The main list is grouped under headings** (2026-11-11, owner: "a little bit confusing, can you rearrange them";
  `SettingsListGroup` in `SettingsScreen.kt`): *Look & feel* (Appearance, Widgets), *Money* (Finance, Banks & Categories),
  *Cards* (Wallet & Cards, Availability & Schedules), *Alerts & safety* (Notifications, Security & Lock, Data & Backup), then
  *System* (Updates & changelog, Welcome tour, About, and **Reset settings last**). Only where a row is shown changed: `SettingsPage`,
  its routes and search are as they were, and `SettingsGroups` (the flat order, Appearance first) is derived from the groups.
  **Previews** (`settings/ui/MotionPreview.kt`): Transitions > Pages and > Back gesture each show a thumb-sized screen playing the
  choice, drawn from `BackPreview`'s own poses; it loops only under `LocalLiveMotion` with animations on, else one still frame.
  Tests: `SettingsArrangementTest` (pictures in `app/build/qa_settings/`).
- **Update page / Changelog page** (`updates/ui/`): see section 7. **Reminder alert screen** (`reminders/ReminderAlertScreen`).

## 2. Money rules

- See `architecture.md` section 4 for the model. Rules worth knowing before touching totals:
  - Every figure is **as of the end of today** (upcoming entries count from their own day). The controller works things out
    again on the first resume of a new day.
  - A budget on an *archived* category stops adding its unspent limit to "Left to spend" from the current month on.
  - A card's "% used" follows the app's one percent rule (`percentLabel`), not truncation.
  - **Credit card limit warning** (2026-11-15; owner, 2026-11-14: warn at 30% by default; `finance/domain/CardLimit.kt`, `cardLimitBanners` in
    `screens/HomeBanners.kt`, Settings > Wallet & Cards > "Card limit warning", `CardLimitWarning`, key `card_limit_warning`, no database
    change): Off / **30%** / 50% / 75% / 90%, chosen in an `OptionSheet`; the row says why (lenders look at how much of a limit is used;
    under 30% is the usual advice). Each **active account with a credit limit above zero** whose owed share (`creditRoom`: the balance
    below zero over the limit) is **at or above** the level is a banner under **Cards** on the Notifications page (so in Home's Needs
    attention count as "card" and the bell), made as a budget's: Warning, **Error once over the limit** ("Over the limit", figure "Over by");
    eyebrow "Limit used" (or "Over the limit"), title the account's name, message "₹45,000 of ₹1,00,000", the share as the ring ("45%",
    full past the limit, "120%" inside; spoken "45% used"), figure "Available" with what is left, button **View card** =
    `openAccountHistory` (the account page), most used first, no Later. **The words are short on purpose**: the brief's "45% of the limit
    used" and "... of ₹1,00,000 used" were cut with an ellipsis in the panel beside the figure at 360 dp (seen in a render), and the ring
    already says the share; at 411 dp even a ₹10,00,000 limit fits, at 360 dp a 7-digit limit still cuts. **Exact in whole paise** (`owed * 100 >= limit * level`: ₹30,000 of ₹1,00,000 warns at 30%, a paisa under
    does not; the share shown is rounded half up, never under the level); archived, no-limit and zero-limit accounts and cards in credit
    never; Off = none. **Nothing is stored**: it is derived from the balances at each look, so paying a card down under the level takes the
    banner away at once and spending again brings it back (a key per card, `card-limit-<account id>`, so an unchanged banner stays seen).
    Independent of Store cards (it is about the account, not the vault). Hidden amounts hide the message and figure (`money.format`); the
    ring's share stays, as a budget banner's does. Tests: `CardLimitTest`, `CardLimitBannerTest`, `CardLimitWarningSettingTest`.
  - Paying a credit card's bill is a **transfer** (to the card account), not spending. Card bill recorded as spending is
    flagged by Check and recalculate ("Record as a payment onto ..."). Credit cards carry a limit, statement day and due day
    (`BillingCycle`, `CardBills`); `PayBillSheet` records the payment.
  - An expense and the same income on another of the user's accounts within a day can be combined into a transfer
    (`combineSelfTransfer`).
  - Duplicate detection for shares: a differing reference is never a duplicate; without references the window is +-3 hours
    when the time is known, the same day otherwise (`SAME_PAYMENT_WINDOW`).
- **Loans and EMIs** (2026-11-15, Phase 72; owner, 2026-11-14: "both options"; database 14, backup format 4; `finance/domain/Loans.kt`, `finance/ui/LoanForm.kt`,
  `LoanPage.kt`, `FinanceRepository.recordLoanEmi`). **Two ways to have a loan, one set of details**: an account of the new kind **Loan** (`AccountKind.Loan`, key
  `loan`), or any other kind but Card with the form's **"This is a loan"** switch on. A loan is always owed: the Loan kind turns "I owe this amount" on and
  disables it, the switch turns it on and says so, and a new loan's starting balance follows the amount borrowed (owed) until it is typed by hand (an older
  loan: type what is owed now). `Account.loan: LoanTerms?` (principal, `rateBp`, `tenureMonths`, `emi`, `start` = the first EMI's day; `isLoan` = a loan or the
  Loan kind); the columns are empty for every other account. **Forms**: Loan details = amount borrowed, interest a year (0-100, at most two decimals), a choice
  **EMI | Months** (give the EMI and the months it takes are worked out - `monthsToPay` - or the months and the EMI is - `emiFor`; both are stored), the first
  EMI's date (starts a month from today); errors sit under each field after Save, and an EMI that doesn't cover the first month's interest says that interest.
  `saveAccount` refuses terms that don't hold (amount above zero, rate 0-100%, 1-600 months, EMI above zero), a card with a loan, and the Loan kind without
  terms. **Maths** (pure, integer paise, BigDecimal, half up): monthly rate = bp / 10,000 / 12; EMI = P*i*(1+i)^n / ((1+i)^n - 1) (P / n with no rate; Rs 10,00,000
  at 9% for 240 months = Rs 8,997.26); a schedule month's interest = what is owed x rate, principal = EMI - that, and **the last month pays exactly what is left** -
  the month in which what is owed with its interest is within 5% over the EMI (a rounded-down EMI otherwise leaves a month of a few paise: Rs 1,000 over 12
  months with no interest is 12 x Rs 83.33 and 4 paise short); an EMI below the first month's interest, or one that takes over 600 months, is refused.
  **The loan's page** (the account's own page, `AccountHistoryPage`, `LoanPanel` above its history): the two-panel banner **Still to pay** (what the
  balance says, as of today, turned round) / **Months left** with the payoff month, rows EMI, Interest a year, Interest still to pay and **Next EMI** (principal +
  interest, its day), the **Record EMIs automatically** switch, **Record EMI** with a line under it, and the **Schedule** (six months, "Show all" on its
  heading). The next EMI's day (`nextEmiIndex`): with none recorded the first on or after today; once one is, the one after the day it is *nearest to* (paid up
  to half a month early or late covers that day), counted from the latest transfer onto the loan (`lastLoanPayment`). **EMIs recorded each month** (2026-11-16,
  Phase 74; owner: "If I entered EMI amount it can be given on monthly based amount right think about this"; no database or backup change): a **repeating
  payment** - a monthly **Transfer of the EMI** from the paying account onto the loan at 09:00, payee the loan's name, note "EMI" (`emiRule`), from the next EMI
  on or after today (`firstAutoEmiIndex`: EMIs missed before today are never back-filled) to the schedule's payoff day (`endDate`) - so Plans > Recurring lists
  it, and the Forecast and the Bills calendar count it as money out with no change of theirs. **`processRecurring` treats a due Transfer rule onto a loan
  (`recordsEmiOf`) as an EMI** (`FinanceRepository.recordDueEmis`): each due date is **split by what is owed just before it** (`emiSplit`: a month's interest on
  it, the rest principal; the month that pays it off pays exactly what is left and its interest; a payment below the interest is all interest) into the same two
  entries Record EMI makes (`loanEmiEntries`), both carrying the rule's `recurring_id`; the interest's category is `emiInterestCategoryOrFallback` (Loans & EMI,
  else Other, else the first expense category; with none at all the EMI waits, the rule left due on it). **One recorded by hand is not recorded again**: a transfer
  onto the loan whose note starts "EMI " (Record EMI's) not made by the rule, within half a month of the day (`emiCovered`: -14..+15 days, a tie going to the
  earlier EMI as `nextEmiIndex` reads it), consumes that occurrence; a plain transfer onto the loan (a part payment) does not. **Once nothing is owed** the rule's
  `end_date` is set to just before that occurrence (so `isFinished`) and nothing more is recorded. A `FinanceValidationException` from one EMI is caught and that
  date passed over, so one bad EMI can never stop every other repeating payment. **Turning it on**: the page's switch opens `AutoEmiSheet` (Paid from = the last
  EMI's account, else the default; "The first is recorded on ..."; **Turn on** -> `FinanceController.startLoanEmis` = `startLoanEmis` + `processRecurring`); it
  is refused for an account that isn't a loan, nothing owed and paying from the loan, and a second turn-on gives back the rule there is. **A new loan** made in
  the account form has **"Record each EMI automatically"** (on by default, paid from the default account or the first other active account; hidden when there is
  none) and the form starts the rule after saving (a refusal is swallowed: the page offers the switch). **Turning it off** (`stopLoanEmis`) deletes the rule
  (what it recorded stays, as with any rule) with Undo (`undoable` + `offerUndo`); reminders follow once settled. **A new EMI** saved on the loan
  (`saveAccount` -> `followNewEmi`) changes the rule's amount when it was the old EMI (an amount set by hand on the rule stays) and takes its end off (it ends when
  nothing is owed). `emiRuleOf(loanId)` = the active, unfinished transfer rule onto it (a paused one counts as off). On, the switch says
  "₹X from <account> each month, next on <day>. Each is split into principal and interest." and the line under Record EMI says it is for one paid another way
  or a payment on top; off, "Record each EMI here when it's paid, or turn on Record EMIs automatically." The sample data's home loan has it on from HDFC
  Savings (`DummyData`: `repo.startLoanEmis` after its EMIs so far). Tests: `LoanAutoEmiTest` (repository), `LoansTest` (the pure parts), `LoanUiTest` (page,
  sheet, form). **Record EMI** (`RecordEmiSheet`, also in the account's actions sheet): paid from the
  account the last EMI came from, else the default account; the day (today); the principal and the interest, filled from the schedule, both editable (a line
  says how the total stands against the EMI); the interest's category, **Loans & EMI** when the user still has it (no "interest" sub-category exists in
  `CategorySuggestions`; asked only while the interest is above zero). "The principal lowers what you owe; the interest is spending." **Two entries in ONE SQL
  transaction** (`recordLoanEmi`, each validated as it is written, so a failure of the second takes the first back out): a **Transfer** of the principal from
  that account onto the loan (what is owed drops; not spending) and an **Expense** of the interest from the same account (payee = the loan's name), notes
  "EMI Nov 2026 - principal" / "- interest" (`loanEmiNote`); a part of zero makes no entry. **Around it**: a loan is never in the forecast's spendable money
  (`spendableAccounts` leaves `isLoan` out), is not a candidate for a shared receipt's account (`AccountMatching`), reads "Owed" in the Wallet and has its own
  card colours; Check and recalculate leaves it alone. **Backup format 4**: an account may carry `"loan": {"principal", "rateBp", "tenureMonths", "emi",
  "start": "yyyy-MM-dd"}` (only a loan has the key; an EMI left out is worked out; each term is validated like `saveAccount`, a bad one refuses the whole file
  with `Account "X" has a loan ...`, and the kind `loan` needs its details); version 1 to 3 files restore as before and a file newer than 4 is refused. The sample
  data has one home loan ("HDFC Home Loan") with its EMIs so far, in memory only. Tests: `LoansTest`, `LoanMigrationTest`, `LoanBackupTest`,
  `LoanRepositoryTest`, `LoanUiTest`. Not seen on a phone.
- **Tags and the year by tag** (2026-11-15, Phase 72; backlog round 2 item 8; database 15, backup format 5; `finance/domain/Tags.kt`,
  `FinancialYear.kt`, `finance/ui/TagsUi.kt`, `YearByTagPage.kt`). **Free tags the user names** (1-30 characters, unique ignoring case),
  any number on an **expense or income** (a transfer has none: it moves money between the user's own accounts, so the field is hidden);
  "Tax-deductible" and "Reimbursable" are offered ready-made in the picker, created only when picked. The editor's **Tags** field shows chips
  and opens the picker (find or add, tick, Edit tags to rename or delete - a delete asks first, saying how many transactions it is on, and
  removes it from them, keeping the transactions). Stored in `tags` + `transaction_tags` (foreign keys ON DELETE CASCADE, so no link can
  dangle), loaded with the transactions in batches, saved with the transaction in one SQL transaction. `TransactionQuery.tagIds` (any of
  them) and the text search matches tag names; the filter sheet has Tags. **Year by tag** (from Insights, `YearByTagLink`; route
  `YEAR_BY_TAG`): a **financial year** switcher - April to March by default, or January to December, the setting **Year starts in** (key
  `year_start`, `YearStart`, Settings > Finance, in backups) - and per tag what was spent and received and how many transactions, Tax-deductible
  first; transfers aren't counted and an entry dated after today counts from its own day; a tag opens its transactions for that year. A
  transaction with two tags counts under both. Backup format 5: a top-level `tags` list and a transaction's `tags` (tag ids), both optional,
  validated (names valid and unique ignoring case, every id known, no repeats); a restore clears the tag tables with the rest first. The CSV
  export has a Tags column (names joined with "; "). Tests: `TagMigrationTest`, `TagBackupTest`, `TagRepositoryTest`, `FinancialYearTest`,
  `TagsUiTest`, `YearByTagPageTest`.
- **Savings goals** (2026-11-15, Phase 72; backlog round 2 item 9; database 16, backup format 6; `finance/domain/Goals.kt`, `finance/ui/GoalsUi.kt`,
  `FinanceRepository` goals section, `FinanceActions.addGoal` / `openGoal`). A goal is a **target the user sets and a way of counting** - it moves no money and
  changes no balance, total or budget (the screens say so). **Fields**: name (1-40 characters, tidied), target (paise, above zero), an optional target date, one emoji
  (default 🎯; the app's `EmojiPicker`, as the person form uses it), and **how it counts what is saved, one of two**: **Linked to an account** - that account's balance
  *as of today* (the balances in `FinanceState`, so an entry dated ahead is not in it; below zero counts as nothing) is what is saved, no second set of books; the
  account must be cash, bank, wallet, savings or other, **not archived, not a card, not a loan** (`goalAccounts`; checked when the link is made or changed - a goal
  keeps counting an account that is archived or has since become a card, and a card's balance is owed so it counts as nothing); or **On its own** - a `saved` amount
  kept on the goal, changed with **Add money** / **Take out** (`adjustGoalSaved`, one SQL transaction; never below zero: "You can't take out more than is saved here.";
  refused for a linked goal) - **no ledger entry**, the sheets say "This doesn't move money between accounts". A linked goal keeps **no amount of its own** (stored as
  zero, `FinanceRows.goal`), so one whose account is deleted (`ON DELETE SET NULL`) is simply "On its own" with nothing saved. **Derived** (pure, integer paise):
  `goalSaved`, `goalProgress` -> saved (uncapped), left (never below zero), fraction 0..1, reached (saved >= target), and with a date still ahead and the goal not reached
  `monthsLeft` = the **calendar months from this month to the date's, at least 1** (a date this month or today is 1) and `monthlyNeeded` = left over those months **rounded up
  to the whole rupee**; a date before today without the goal reached is `datePassed` ("The date has passed"); a reached goal has neither. `goalsSummary` counts **each goal
  up to its own target** (so Saved + Still to go = what the goals ask for; a savings account holding 10 lakh does not make a 1 lakh goal look like 10). **UI**: **Plans gains
  a fourth section, Goals** (`PlanSection.Goals`, last so the others keep their remembered places; the "+" button on it is "Add goal"): the two-figure banner **Saved / Still
  to go** (not a button), then a row per goal - picture (`GoalAvatar`, the emoji on the avatar disc), name, share, a calm bar (success colour and a tick when reached),
  "Rs X of Rs Y" and "Rs Z a month to reach it by Mar 2027" / "Reached" / "The date has passed" (no line for a goal with no date) - still-being-saved-for first, reached after;
  "Add goal" under them; an empty state when there are none. **Four secondary pills do not fit side by side** ("Reminde…" at 411 dp), so `AppSecondaryTabs` now scrolls
  (each pill as wide as its word, the chosen one kept in view) when it has more than three tabs - all four fit whole at 411 dp, and at 360 dp Goals is half in view and
  scrolls; the large-text path was already this. Tapping a goal opens `GoalSheet` (name, bar, saved of target, left, date, what it counts, then Add money and Take out - only
  for a goal on its own, Take out only while something is saved - Edit, Delete); **Delete asks first** ("The goal is removed. No account or balance changes." / "The goal and
  the Rs X counted on it are removed. That money isn't in any account, so nothing moves.") and is undoable for a few seconds (the snapshot carries goals). `GoalFormSheet`
  (`EditorSheet`): name, the target as the hero amount, a target date (optional, `DatePickerDialogFor` from today to 30 years, "Remove date"), the choice **Linked account |
  On its own** (a new goal starts Linked when there is a Savings-kind account, else On its own) with `AccountSelector` or "Saved so far", and the emoji picker; messages sit
  under their fields and the rest above Save. **Deleting an account** that goals count says so in its question ("A savings goal counts this account's balance: it stays, on
  its own, with nothing saved"). Hidden amounts hide every figure (the bar and share stay). **Data**: table `goals` (migration 16, additive, `IF NOT EXISTS`, also created
  by `onOpen` if missing); **backup format 6**: a top-level `goals` list (`id, name, target, targetDate "yyyy-MM-dd", emoji, accountId, saved, sortOrder, createdAt, updatedAt`),
  optional - formats 1-5 restore exactly as before; validated (name 1-40 characters, target above zero, saved not below zero, one emoji or none, ids unique, the account
  in the file, a date that is a date; a refused file says which goal: `Goal "Trip" has a target that is not above zero.`) - the account's *kind* is not checked (it may have
  changed since); a restore writes the goals after the accounts in the same SQL transaction and `clearAll` deletes `goals` first; the snapshot (so Undo, Check and recalculate,
  a reset) carries them. The sample data has two goals (an emergency fund counting SBI Savings, a Goa trip on its own), in memory only. Tests: `GoalsTest`, `GoalMigrationTest`,
  `GoalBackupTest`, `GoalRepositoryTest`, `GoalsUiTest`. Not seen on a phone.
- **Split payments** (2026-11-15, Phase 72; backlog round 2 item 11; database 17, backup format 7; `finance/domain/Splits.kt`, `finance/ui/SplitEditor.kt`,
  `FinanceRepository` split section, `FinanceController.saveSplit`/`unsplit`/`releaseSplit`, `FinanceActions.showDetails`, `TransactionEditor`, `Receipt.kt`,
  `FinanceWidgets.kt` `SplitChip`, `LedgerCheck.kt`, `settings/ui/LedgerCheckUi.kt`). **One real payment - an expense or an income, never a transfer - shown as 2 to 10
  parts**, each its own transaction row with its own category, amount and note, all sharing a random UUID in the new `split_group` column and everything else: account,
  date and time, payee, person ("For"), tags, picture. The payment's total is the parts' sum. Because each part is an ordinary row, **category totals, budgets, Insights,
  the forecast and a category filter are right with no change**; only the count of transactions goes up. Repeating payments, shared receipts, CSV and statement imports
  and NFC still make single rows. **What holds together** (`splitFault`, pure, the one rule the backup, the check and snapshots use): 2 to 10 rows, none a transfer, all
  expenses or all income, one account, one exact date and time, one payee (blank = none, trimmed), one person. **Repository** (each in ONE SQL transaction, all or
  nothing; each part is validated as it is written, so a failing later part takes the earlier ones back out): `saveSplit(payment, parts, group = null)` - `payment` says
  what is shared and its amount must equal the parts' sum, a part naming an existing row (`SplitPart.id`) keeps that row (its id, when it was added, its repeating-payment
  link), a new split may only reuse a plain row that is not already in a group, an edit deletes the rows no part names and refuses a part from another group; the tags go
  to every part (`writeTags`), the picture name to every part; `unsplit(group, category?)` - the first part's row (lowest id) becomes the whole payment (total, the chosen
  category or the largest part's, the notes joined with " · ", the shared fields stay) and the other rows go; it refuses a group that does not hold except one with a
  single part left; `releaseSplit(group)` - only takes the mark off (what Check and recalculate's "Unsplit" does); `deleteTransaction(id)` of any part deletes **every
  part**; `splitParts(group)` reads them. **`saveTransaction` of one part** (the form's Save) writes that row's own category, amount and note and **copies the shared
  fields - account, date and time, payee, person, picture, tags - to every other part** in the same step (`savePartOfSplit`); a part can't change between spending and
  income or become a transfer, a card bill or a repeating payment (refused in words: unsplit it first); a group id on a row that is saved on its own is ignored (only the
  split functions make or end a group), and `combineSelfTransfer` refuses a part. **Pictures**: every part names the same file; `FinanceController.saveSplit(..., picture)`
  keeps a new one first (deleted again if the save fails), deletes the replaced or removed one only after the save, and `TransactionPictures.sweep` keeps a file while
  any row names it; deleting the payment keeps the file until the Undo is settled. **Data** (additive, `addSplitGroup`, safe to run twice, also in `createSchema` and as
  an `onOpen` safety net): `split_group TEXT` and `idx_tx_split`; existing rows untouched. **Backup format 7**: a part carries `"splitGroup": "<id>"` (1 to 64 characters
  of text; anything else is a damaged file); absent = not split, so formats 1-6 restore as before; `validate` refuses a group that does not hold with
  `A split payment (transaction #12) is on more than one account.` and the like (the first row names it); `encode` writes the mark only for groups that hold, and
  **`snapshot()` takes the mark off any group that does not hold** (nothing the app writes makes one), so a backup and an Undo are always restorable. **Check and
  recalculate** finds a group that no longer holds (a part left alone, parts on two accounts, dates, kinds, payees or people) as a finding "Split payments that don't add
  up" with an **Unsplit** button: it only takes the mark off (nothing is merged, since merging parts on two accounts would have to choose one), every amount stays, no
  Undo; the other findings leave a part alone (never "entered twice", never half of a self-transfer, never a card bill). **UI**: the form's **Split** field
  (`SplitField`, a tonal row after Tags; shown for an expense or income once the type is chosen, not for a transfer; "Enter the amount to split" first when there is none)
  opens the split editor (`SplitEditorSheet`, an `EditorSheet` over the form): the **total** as the hero amount (editable), a line of what the parts share, then a card per
  part (category field, amount, optional note; a way to remove one while more than two are left), "Add a part" up to 10, and the **running line pinned above Save** -
  "₹120 left to share" / "₹50 over the total" / "Shared exactly" / "Give every part a category and an amount" (a tone is only a second cue) - with **Save enabled only when
  the parts add up to the total and each has a category and an amount above zero**; the last part has "Add the ₹120 left". A new split starts with the original category
  and the total as part 1 and an empty part 2; a part's own form (swipe Edit) shows "2 parts · Edit split" and opens the split editor with this part as the form has it and
  the form's shared fields. **Activity**: each part is its own row (so a category filter and search find it), a small **Split** chip leading its details and "part of a
  split payment" in what a screen reader hears; the **details** of a part show the whole payment (`receiptOf(..., parts)`: the total, "Split into 2 parts", a line per part
  with its category, amount and note; `ReceiptRow.figure` keeps an amount whole at 1.6x text while the long category path wraps - it was broken one character to a line) and the actions **Edit split | Unsplit | Delete** (no Remind for a split; at 1.6x "Edit split" is cut to "Edit ..." by the segment, as the group always does); **Delete** asks "Delete this split payment?" and says it
  removes all its N parts, with Undo ("Split payment deleted"); **Unsplit** asks which category (largest part's first) and has Undo. Hidden amounts hide every figure, the
  running line's too (what is typed in a field stays). The sample data has one split (a DMart bill, ₹1,380 Staples & grains + ₹640 Household supplies, two days ago), in
  memory only. Tests: `SplitsTest`, `SplitMigrationTest`, `SplitBackupTest`, `SplitRepositoryTest`, `SplitUiTest`, `SplitLargeTextTest` (native graphics; failed against the old row layout). Not seen on a phone.
- **Check and recalculate** (Settings > Data & backup): `checkLedger` (pure, `LedgerCheck.kt`) and
  `FinanceRepository.checkAndRepair`, under a non-cancellable loading sheet. It fixes in one SQL transaction (with Undo): a
  sub-category whose parent is gone/archived/of the other kind; a budget on an income category; a recurring rule whose next
  date was already recorded; a default account archived or gone; then records repeats that were due. It only *shows*: entries
  dated after today, the same payment twice in a day, a card bill recorded as spending, an expense + matching income
  (make it a transfer), archived accounts holding money, entries under a category of the other kind, and a split payment that no longer holds together (offers "Unsplit", see "Split payments").

## 3. Reading receipts shared into the app

Flow: another app shares text or a picture to NormPix (`ShareTransactionActivity`, exported, no UI - the component other apps
know; it only forwards what `sharedTextOf` / `sharedPictureOf` took, with the right to read the picture, and finishes) ->
`QuickAddActivity`, the see-through overlay (section 8) -> `FinanceActions.addFromShare` -> the transaction editor sheet opens
over the sharing app with a **draft the user checks and saves**; nothing is saved unseen, and **NormPix itself is not opened**
(since 2026-11-16; before that the text waited in `SharedTextInbox` for MainActivity - the inbox, `ReceiveSharedTransactions`
and `AppContainer.sharedText` are gone). The text now travels in the forwarded intent (cut at `MAX_LENGTH + 1`), which the
overlay is not a task root of and never restores; the picture is staged at once, while the activity holds its read grant. A
second share while the overlay is open queues behind the first (`QuickAddRequests`). A locked app asks for the prompt first and
sample data on asks "Turn off sample data" first, both in the overlay. Pictures are read on the device by ML Kit (`MlKitSharedPictureReader`); the same picture can be attached to the
transaction and read again to fill the form. Only another app's `content://` picture is taken (`sharedPictureOf`): never a file
path, never NormPix's own providers - judged by the authority **after the last `@`** (`isOwnAuthority`), as Android reads
`user@name` and `0@<own provider>` is the app's own files (2026-11-13).

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
- **Keyboard card fill** (2026-11-14, owner: "keyboard gives an option to scan the cards like how Swiggy does"; Settings > Wallet & Cards,
  `cardKeyboardFill`, key `card_keyboard_fill`, **off by default**, never carried by a backup). That "Scan card" chip is the phone's
  autofill service (Autofill with Google, shown by the keyboard), so turning it on routes the card form through a service outside the
  app - hence opt-in. On: the form's fields carry `ContentType.CreditCardNumber` / `ExpirationMonth` / `ExpirationYear` / `SecurityCode`
  and `PersonFullName` (`Modifier.cardAutofill`), a four-digit year from a service is kept as two (`expiryYearDigits`), and the session is
  **cancelled** on save and as the sheet starts to close (`LocalSheetClosing`) - compose-ui 1.12.1 commits a session by itself when
  content-typed fields leave the screen, which is what makes a service offer "Save card?". Off: every card field is
  `ContentDataType.None`, so no service sees the form at all (before this the CVV field was reported as a password field); the account
  form's card number is always None. `KeyboardCardFillTest`. Not seen on a device yet.
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
  **Encrypted since 2026-11-15 (exchange version 2; owner's choice 2026-11-14: Bluetooth-style numeric comparison).** Files:
  `NfcPairing.kt` (the cryptography: a fresh P-256 key per share, ECDH, HKDF from HMAC-SHA256, the sender's commitment to its nonce,
  a 6-digit code and an AES-256-GCM card key bound to the transcript, points checked on the curve), `NfcShareResponder.kt` (the
  sharing phone's state machine; `NfcCardOutletService.processCommandApdu` hands it every command, `onDeactivated` ends its
  session; one shared instance, `NfcCardOutletService.responder`), `NfcShareInitiator.kt` (the reader's side, blocking on the NFC
  thread; `readTag` / `readIsoDep` run it; `ShareProblem` = why a share ended without a card). Short APDUs only:
  1. R -> S `00 A4 04 00 07 F0010203040506 00` (SELECT) -> `4E 50 58 02` ("NPX" + version) `90 00`, or `6F 00` with nothing armed
     or not allowed. Every SELECT starts a new session (fresh keys, fresh code).
  2. `80 10 00 00 41` + R's point (65) `00` (KEYS) -> S's point (65) + commitment (32) `90 00`; a point not on P-256 -> `6A 80`.
  3. `80 12 00 00 10` + Nr (16) `00` (NONCE) -> Ns (16) `90 00` and S shows the code; before KEYS `69 85`. R checks the commitment
     ("This share was interfered with. Nothing was shared." when it fails) and shows the same code.
  4. `80 14 P1P2 00` (CARD, P1P2 = offset), asked every 250 ms: `6F 10` while undecided, `6F 11` after Doesn't match (S disarms at
     once), after **Match** the card sealed once and sent from offset 0 as a 2-byte length + 238 bytes, then 240 a time; when the last
     byte goes out the card is disarmed (plain bytes zeroed: one reader, once) and the sheet says "Card shared". Wrong state `69 85`,
     unknown command `6D 00`. R gives up after 60 s or when the phones part ("Keep the phones together until the card arrives").
  The plain payload never travels; nothing about keys, nonces, codes or the card is logged (`logTagRead` names only the outcome).
  `isAllowed` is asked at **every** command, so NormPix leaving the front stops a share mid-way. A Match carries the session's
  `Pairing.id`, so it is never applied to a newer session. **Refused**: a phone answering SELECT with plain JSON (an older NormPix:
  "That phone has an older NormPix. Update it there to share cards securely."; the card has already crossed in the clear from it,
  but nothing is kept), a newer exchange version, a tampered KEYS answer. The NDEF path (tags written by NormPix, marker
  `normpix_card`) is unchanged and plain. **What it does not stop:** a relay that forwards both sides faithfully - both screens then
  honestly show the same code - so the sheet says "Only tap Match for a phone you can see." The service stays fenced as in the
  2026-11-13 security review: **unlocked** phone only (`apduservice.xml` `requireDeviceUnlock="true"`), NormPix **in front**
  (`ActivityHost.visible`). UI: Share shows the code large (grouped "123 456", tabular figures, TalkBack reads "Code 1 2 3 4 5 6"),
  "Match - share the card" / "Doesn't match", then "Sending...", "Card shared" or "Not shared" with Share again; Receive
  (`NfcTapPanel`, also the Add card tap sheet) shows the same code under "Compare the codes" while it waits (`NfcInletState.Comparing`,
  `ShareFailed`). On Receive, a number that isn't one, or an expiry that isn't a month 1-12 and a 2- or 4-digit year, makes the
  payload no card (`parseCardFromNfcPayload`). Tests: `NfcPairingTest`, `NfcShareExchangeTest` (both sides in one process),
  `NfcCardOutletServiceTest`, `NfcTagReaderTest`, `NfcShareSheetTest`. **Not tried between two real phones yet.**
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
- **A backup holds the user's categories, not the app's** (2026-11-12, owner: "why include all the categories when it is related to
  the app"; finance format **version 3**): the export sites call `FinanceSnapshot.trimmedForBackup()` (`FinanceBackup.kt`), which
  leaves out a default category only if it is exactly as seeded (name, family, icon, colour, not archived; `FinanceDatabase.defaultSeedPlan`),
  **nothing refers to it** (no transaction, budget, repeating payment, or kept sub-category) and **it is the only one of its name** -
  so it can be made again. Everything else is kept whole with its id. The file names the defaults the user removed
  (`removedDefaultCategories`), and `restore` then calls `FinanceDatabase.addMissingDefaults` (never doubling a name, each at its
  seed order). `repository.snapshot()` itself is still everything (undo restores it), and a version 1/2 file - which lists every
  category - is restored exactly as before, nothing added. Why not drop the in-use defaults too: categories have no "built-in" mark
  and every reference is by id, so the file must carry what the data points at. Tests: `BackupCategoriesTest` (a namesake - the
  user's own "Hobbies" beside the app's sub-category - is why "only one of its name" is a rule: left out, it was gone after a restore).
- **Default categories** (`FinanceDatabase.defaultCategories`, sub-categories in `CategorySuggestions.byParent`): since 2026-11-12
  (database version 13, `CategorySuggestions.addedIn13`) also *Money sent* (sent to friends, lent, repaid, bill splits), *Gifts given*,
  *Fees & charges*, *Work expenses*, and for income *Money received*, *Investment returns*, *Sold items* - with icon keys
  `money_sent`, `money_received`, `sold_items`. An existing ledger gets them once (`addDefaultFamilies`: never where a category of
  that kind already goes by the name). **2026-11-14: +50 ready-made sub-categories** (owner: "Food & Dining subcategories like snacks,
  lunch, dinner, Swiggy ... used in daily life"): Food & dining gained Snacks, Lunch, Dinner, Swiggy, Zomato, Tiffin & mess, Office
  canteen, Sweets & mithai; Groceries Blinkit, Zepto, Instamart, BigBasket, DMart, Milk delivery; Transport Uber, Ola, Rapido, FASTag
  recharge, RTO & licence; and more across Bills, Housing, Health, Fitness, Events, Education, Kids, Subscriptions, Loans, Salary and Other
  income. No database change: a new install seeds them (`seedDefaults` walks `byParent`), an existing phone takes them from Banks &
  Categories > "Add suggested sub-categories". Brand sub-categories carry their logo key (swiggy, zomato, blinkit ...). `MerchantCategories`
  rules may name a newer sub with `Rule.orElseSub`, the broader sub to use on a phone that lacks it (Swiggy -> Swiggy, else Delivery apps;
  Uber -> Uber, else Cabs & rideshare; FASTag -> FASTag recharge, else Tolls; "lunch"/"dinner"/"snacks"/"tiffin" words -> their sub, else
  the parent). Families stay at most 18 sub-categories except Subscriptions (`CategorySuggestionsTest`).
- **Locked backups** (since 2026-11-03, whole-app encryption stage 1): each restorable export first asks "Lock the backup?"
  (`BackupLockSheet` in `settings/ui/LockedBackupUi.kt`; **Lock it** is preselected, **Don't lock** saves the file exactly as before).
  Locked, the whole file is wrapped by `backup/LockedFile` and saved as `<name>.locked` (`application/octet-stream`); unlocked it is
  the old file byte for byte, so every reader and limit is unchanged. Restore sniffs the first 15 bytes (`openBackup`): a locked file
  asks for its password (again after a wrong one), is read through the usual `readBackup`, then read to its end (`BackupSource.finish`)
  so a cut or extended file is refused before anything changes. In a locked app backup the saved cards are locked with the backup's
  password (no second sheet), and restoring tries that password on them first. The CSV is never locked (it is for spreadsheets).
  A manual backup's password is stored nowhere; a forgotten one can't be recovered. (The automatic backup's own password is the one
  exception: kept sealed by the Keystore, see **Automatic locked backups** below.)
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

- **Backup reminder** (2026-11-07, `backup/BackupFreshness.kt`): only the *time* of the last restorable backup is kept (`last_backup_at`: the small file, the full zip or the app backup saved from the real data - not the CSV, not the sample set); Home's Needs attention says "Back up your data" when it is older than the reminder (Settings > Data & backup > Backup reminder: every week / 2 weeks / month, default 2 weeks, or off) or there was never one 3 days after install, only when there is something to keep, "Later" puts it off 3 days. `last_backup_at` and the snooze are in `BACKUP_EXCLUDED` and survive Reset settings. An automatic backup that is written counts too (`recordAutoBackup` sets `last_backup_at`).
- **Automatic locked backups** (2026-11-14, Phase 72; owner: "Set a one time password and your suggestion also user will decide";
  `backup/AutoBackup.kt`, `AutoBackupFolder.kt`, `AutoBackupJob.kt`, `SealedSecret.kt`, UI `settings/ui/AutoBackupUi.kt`): Settings >
  Data & backup > Automatic backup makes **the app backup** (the same zip as "Everything", via `AppBackupExporter`) by itself and writes it,
  locked by `LockedFile.locker` exactly as a manual locked backup (PBKDF2 310,000 rounds), **straight into a folder the user picked**
  (`ACTION_OPEN_DOCUMENT_TREE` + `takePersistableUriPermission` READ|WRITE; only the tree URI and its name are kept) as
  `NormPix-auto-backup-YYYY-MM-DD.zip.locked` (`-2`, `-3` for more that day). The zip is streamed through the locker into the document:
  never unlocked anywhere, not even the cache; a failed write deletes the partial document. It restores with the ordinary **Restore from
  backup** (open the file, type the password) - no restore code of its own. **Its own password**, set once (twice, at least
  `LockedFile.MIN_PASSWORD`), is the one secret NormPix keeps: sealed by `SealedSecret` (AES-256-GCM under Keystore alias
  `normpix_auto_backup_key_v1`, **not** user-auth-bound so the job can open it; `noBackupFilesDir/auto_backup.key`, `[IV][ct+tag]`,
  temp file + rename, read back); never in preferences, a backup or a log; handed around as `CharArray`/`ByteArray` and zeroed after
  use (the Compose text field's `String` is the one copy that can't be). **Saved cards are left out** unless "Include saved cards" is on
  (asks first, and unlocks while Wallet lock is on); then they go in as a cards file locked with the same password, as in a locked manual
  backup; cards that can't be read are left out and that is said (`CardsLeftOut`). **Retention**: the newest 5 (`AutoBackupNames.KEEP`; owner, 2026-11-14: "delete old backups if it's more than 5")
  - only files directly in that folder whose name matches the exact pattern are ever deleted, never the one just written. **Schedule**:
  `AutoBackupSchedule` (JobScheduler job 7_602, periodic Daily / **Weekly** (default) / Monthly, flex the last quarter, persisted, battery
  and storage not low, no network), `AutoBackupJobService` (not exported, `BIND_JOB_SERVICE`), re-synced from `NormPixApplication`
  (`followAutoBackupSettings`, which also deletes a password left behind with the feature off). **Turning on** = a sheet that explains,
  the folder picker, the password sheet, then on and the first backup at once under `overlays.loading`; **off** asks, then deletes the
  sealed file and releases the folder grant (backups made stay). Rows while on: the last backup (date · file) or the last problem, Folder,
  How often (`OptionSheet`), Include saved cards, Change password (backups made keep theirs), Back up now (same code path). **Failures**
  are recorded as `AutoBackupProblem` (FolderGone "Choose the folder again", PasswordMissing, OutOfSpace, CardsLeftOut, Failed;
  SampleData is only the guard's) and shown on the page and as a Backup notification (`autoBackupProblemBanner` in `HomeBanners.kt`,
  which then stands in for the "Back up your data" nudge); no system notification. **Sample data**: the one `AutoBackupService` is built
  over the real container (`ContainerBackupData`) and handed to the sample set as it is, and refuses a container with `isSampleData`, so
  the sample set is never written. All its preferences (`auto_backup_*`, `AUTO_BACKUP_KEYS`) are in `BACKUP_EXCLUDED` and **kept by Reset
  settings** (a reset must not orphan the sealed password; turning it off is its own switch). The footer says a cloud folder's app may
  upload the (locked) file; NormPix sends nothing. Not checked on a phone yet (SAF providers, Drive folders, the job firing).
- **Importing a bank statement** (2026-11-15, Phase 72, round-2 item 7; **no database or backup-format change**; the pure part is `finance/statement/`,
  the flow and sheets `settings/ui/StatementImportUi.kt`, the row is Settings > Data & backup > **Import** > "Import a bank statement", "A CSV file from your
  bank's website or app", disabled - and saying why - while sample data is on). **The file**: `BackupFiles.openStatement(maxBytes)` (the default reads through
  `open()`; the real picker is the `OpenStatement` contract: `ACTION_OPEN_DOCUMENT` + `CATEGORY_OPENABLE`, `BackupFiles.STATEMENT_TYPES` = text/csv,
  text/comma-separated-values, text/plain, application/csv, application/vnd.ms-excel and, beyond the brief, text/x-csv and text/tab-separated-values, after
  `lock.expectExternalActivity()`); **2 MB** (`StatementCsv.MAX_BYTES`; read no further than the cap, then "That file is too large"); the bytes are read once,
  never copied, kept or logged (no parser message holds a cell); a PDF or workbook (magic bytes) is refused in words. **Parser** (`StatementCsv`): UTF-8 (BOM
  dropped) else Windows-1252; the delimiter is whichever of comma / semicolon / tab splits the first 300 records into the most rows of one width; RFC 4180
  (a quote opens a quoted cell only where a cell starts, `""`, line breaks inside quotes); blank records dropped. **The table** (`StatementLayout.find`): the
  first record with a date-like and an amount-like (or debit / credit) heading; what is before it is the preamble, what is after it the body; a body record
  whose date column is no date (a summary, a footer, a heading printed again at a page break) is skipped and counted. **Guesses** (`guess`; headings compared as
  lower-case letters and digits with bracketed words dropped, so "Chq./Ref.No." is `chqrefno`): Date (a transaction / txn / tran / plain date before a posting date
  before a value date), Description (Narration, Particulars, Details, Remarks, ...; else the widest text column), Debit and Credit (Withdrawal Amt., Deposit Amt.,
  Dr, Cr, ...; if either exists money is read from them), else one Amount, with a Dr / Cr marker column **only when every value in it is a Dr / Cr word** (a
  "Type" column of UPI / NEFT is not), Reference (Chq./Ref.No., Ref No, UTR, Transaction ID, ...); balance columns are never read. `StatementMapping.with` keeps a column
  on one field and the two ways of giving money apart. **Dates** (`StatementDates`): nine formats read by hand, English month names, an optional time (24 h or
  am / pm), year 1990 to next year, a two-digit year in this century unless that is past next year; the format is the one that reads the most cells, and when
  dd/MM/yyyy and MM/dd/yyyy read the same cells but to different dates **the user is asked** ("Both ways of reading fit every date ..." with two dates read both
  ways; Review waits for an answer) - dates that mean the same both ways are not a question. **Amounts** (`StatementAmounts` over `parseMinorUnits`): Rs / INR / the
  rupee sign, a minus or brackets, a Dr / Cr beside the figure, Indian grouping, a decimal comma only when one or two digits follow the last comma. **Which way**
  (`StatementReader`): debit and credit columns - both, neither, zero or a non-amount skips the row; one amount - a Dr / Cr beside it, then the marker column, then
  its sign (a row "Positive amounts are Money in | Money out" appears when there is no marker; the default is money out for a **Card** account, whose statement
  shows spending as a plain amount). **Account**: an `AccountSelector` over the active accounts that are not loans; preselected only when there is just one
  (empty beats wrong). **The entry** (`StatementNarration`, borrowing `narrationOf` and `tidyName` from `finance/share`): payee = the other side's name from a UPI /
  IMPS / NEFT narration, else the first part that could be a name (not a reference, UPI id, IFSC or masked number), "ATM withdrawal" for a cash withdrawal, an
  uncoded narration as it is, 60 characters at most; note = **"Ref 428312345678 · " and then the narration as written, 300 characters at most** (the Ref first, so the
  repository's first-`Ref` reading is the bank's); reference = the reference column's letters and digits (6 to 35, the zeros some banks pad a number with taken
  off an all-digit one - HDFC's `0000428312345678` is the UPI reference `428312345678` a payment app shows), else a UPI path's or a lone 12-digit number in
  the narration. A row with only a day is saved at **noon** (`StatementImport.DAY_ONLY_TIME`), one with a time keeps it. **Category** (`StatementImport.prepare`): the user's
  last filing of the payee (`lastCategoryForPayee`), else `MerchantCategories` by the payee then the whole narration (expenses only), else the top-level "Other" /
  "Other income", else the first top-level of its kind, else none - and then the row says "No category to file it under", is unticked and can't be ticked.
  **Already in NormPix**: two passes of `possibleDuplicates` (first with the bank's payee, then - for the rows left - on reference, amount and day alone, so
  "Store near home" and "EXAMPLE STORE" still match), `timeKnown` only when the statement gave a time (so the window is the same day, else three hours), **one entry
  accounts for one row** (a claimed set: two equal rows are not both put on one entry), a reference match is checked as a whole `Ref` token (the repository's
  search is a substring), in **any** account - the tag then names it, "Already in NormPix, in Cash" - and such a row starts unticked (every row can be ticked). So a
  second import of the same statement finds every row. **The flow** (`StatementImportFlows.run`): picker -> loading "Reading the statement" -> the setup sheet
  (`EditorSheet`: account, the columns - a `PickerFieldRow` per field opening an `OptionSheet` of the columns with their first three values and "None" - and Date format
  right under Date, status line "3 transactions found, 4 lines skipped" live) -> loading "Checking against NormPix" -> the preview sheet (a tick box per row: payee, date
  · type · category, the narration, amount, the tag; "Import 23 of 27 (4 already in NormPix, 2 lines skipped)", Select all / Clear all, the arrow back to the columns, **Import 23**;
  at font scale over 1.3 the amount goes under the name) -> loading "Importing" (not cancellable) -> `FinanceController.saveTransactionsUndoably` ->
  `FinanceRepository.saveTransactions` (each row validated as a single save is, **one SQL transaction: all or none**; a failure alerts "Nothing was imported") -> a snackbar
  "23 transactions imported into HDFC Savings" **with Undo**, said once the sheets have closed. **Not in this version**: recognising transfers between the user's own
  accounts and card bill payments (they import as expenses and income to edit), loan accounts as a target, a row's category or payee edited in the preview,
  narrations that run on over several records (counted as skipped lines), `.xlsx` and PDF files, number formats with a decimal comma beyond the one-or-two-digit
  rule. Tests: `StatementCsvTest`, `StatementLayoutTest`, `StatementDatesTest`, `StatementAmountsTest`, `StatementReaderTest`, `StatementNarrationTest` (pure, made-up
  statements in `StatementFixtures`), `StatementImportTest` (a real in-memory ledger: categories, duplicates, all-or-nothing, notes, undo), `StatementImportFlowTest` (the page,
  the picker's intent, refused files, the columns, the date question, the preview, the import and the same file again), `StatementImportCatalogTest`. Not tried with a real
  bank's file or on a phone.
- **Crash log** (2026-11-07, `diagnostics/`; owner asked 2026-10-31): a default uncaught-exception handler (installed first in `NormPixApplication`) keeps a report - app and Android version, device model, thread, exception class and stack, **messages scrubbed** (4+ digit numbers, `name@bank` handles, anything in quotes taken out) - as one file each in `files/crash-log/`, the newest 20, 12,000 characters at most; the system's record of an app-not-responding or native crash (`ApplicationExitInfo`, Android 11+) is kept too, once. About > Crash log lists, shares (the newest 5, through Android's own share sheet) and clears (asks first); Home offers the last crash on the next start until seen or dismissed (`crash_log_seen_at`, excluded from backups). Nothing is sent by the app.

## 7. Updates

- Source: GitHub releases of `saik111919/norm-pix` (public, no token). `update.json` published by CI lists version, the APK,
  size, SHA-256, min SDK, notes, `mandatory`. **A release is one APK + `update.json`** (2026-11-08, owner: "only show release apk in GitHub
  release"): the universal APK, no per-CPU APKs and no `.sha256` files - GitHub shows each file's SHA-256 itself. `update.json` must stay (every
  installed copy reads it) and has no `apks` now; `ReleaseMetadata` still reads `apks` from older releases and takes the universal APK without it. A release is accepted only if tag, version, attached asset size and checksum all
  agree (`ReleaseMetadata`). Prereleases/drafts are never offered.
- **NormPix downloads and installs nothing itself** (owner, 2026-11-16, built the same day; phase 75 in `TASKS.md`). Play Protect blocked the app as
  "harmful" because of `REQUEST_INSTALL_PACKAGES` (`build-test-release.md` section 8), so updates go **through the user's browser**: NormPix
  checks GitHub, notifies, and shows the banner, page and changelog; the **Download update** button opens the release's APK link in a
  **Chrome Custom Tab**, the browser downloads it, the user opens the download, and Android's own installer asks before updating NormPix over
  itself (same signing key, data kept; Android asks once to let the browser install apps). The manifest has no `REQUEST_INSTALL_PACKAGES`,
  `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `FOREGROUND_SERVICE*`, download service or installer receiver (`UpdateManifestTest` guards the source
  manifest and the merged one). `androidx.browser:browser` 1.10.0; no `<queries>` entry (`launchUrl` is `setData` + `startActivity`).
- Behaviour: check once a day in the background (JobScheduler `UpdateJobService`, not on low battery, **any network** - it only asks GitHub) and at
  launch, or on demand. **The background run only checks and posts the "Update available" notification** (once per release; a tap opens the update
  page). The stored `BackgroundUpdates` (Notify / Download / Install, key `background_updates`) and `update_on_mobile_data` are still read but
  **ignored** - Download and Install read as Notify, no screen offers them. **Nothing pops over the screen**: an update is Home's banner, which
  opens the update page; notifications also open it.
- **Download update** (sheet, page and Home banner; `AppUpdater.openDownload(look)` -> `BrowserDownloadOpener`, `updates/UpdateDownload.kt`):
  opens `release.apkUrl` in a Custom Tab (toolbar and navigation bar in the app's surface colour, light or dark by that colour's luminance -
  `rememberDownloadTabLook()`), falls back to a plain `ACTION_VIEW`, and reports `UpdateProblem.NoBrowser` when neither resolves. **Only
  `https://github.com/<BuildConfig.UPDATE_REPOSITORY>/releases/download/<tag>/<file>` opens** (`DownloadLink.trusted`: https, host `github.com`,
  no port/credentials/query/fragment, this repository exactly, plain tag and file names); anything else is `UpdateProblem.BadLink` and nothing
  launches. `lock.expectExternalActivity()` is called first, as for the file pickers, and `cancelExternalActivity()` undoes it when nothing
  opened. The state stays `Available` after a successful open (the update is still waiting until the app is replaced); a failure is
  `Failed(problem, release)`, said **in the sheet or page** ("Couldn't open the download"; no browser has Try again, a refused link only Close),
  never as a snackbar, and Home's banner stays while Try again can help. Under the buttons, `DOWNLOAD_STEPS` / `DownloadSteps`: "Your browser
  downloads it. Open the download and tap Update. The first time, Android asks you to let your browser install apps. Your data stays." Later
  (Not now for a mandatory release) is unchanged. `UpdateState` is Idle, Checking, UpToDate, Available, Failed.
- At `AppUpdater.start()` the old in-app download folder `cacheDir/updates` is deleted whole (`oldDownloads`), stale notifications are taken
  down and the one-time "NormPix updated itself" note is said for a version an earlier release installed quietly (the records keys
  `announced_version_code`, `ready_announced_version_code`, `quiet_install_version_code` are kept; never rename a stored key).
- **Leftover APKs** (2026-11-14, owner: "if an apk is left can we give an option to delete it"; since 2026-11-16 these are what **the browser leaves in Downloads**
  when Download update is used - NormPix saves nothing itself): not NormPix's to list. Settings > Automatic updates >
  "Update files" > **Delete downloaded APK files** (`UpdateFilesSection`, on the page in every state; `settings/ui/ApkCleanup.kt`, logic in
  `updates/ApkFiles.kt`) opens the system picker for APKs (`OpenMultipleDocuments`, after `lock.expectExternalActivity()`), then a confirm sheet lists the files
  (name, size, "Not named like a NormPix update" - `isNormPixApkName` - still deletable; "Can't be deleted from here" when the provider's
  `FLAG_SUPPORTS_DELETE` is off, then not counted or tried), deletes with `DocumentsContract.deleteDocument` and says the result in a snackbar after the sheet
  closes. **It uses the picker on purpose: listing Downloads would need `MANAGE_EXTERNAL_STORAGE`, a new permission and a Play Protect risk.** Never log
  a file's name. Tests: `ApkFilesTest`, `ApkCleanupTest` (a fake registry answers the picker, a fake `ApkFileStore` stands for the files), `UpdateUiTest`.
- Settings > Automatic updates keeps the check settings (switch, how often, when, check when opened), the notifications-off note and Update
  files; "When an update is found", "Network" (Download on mobile data) and "Android's leave" are gone, and so are their search words.
  Tests: `AppUpdaterTest` (the check, Download update through a `FakeOpener`, no browser, a refused link, the background run, `start()`),
  `DownloadLinkTest` and `BrowserDownloadOpenerTest` (a `RecordingActivity` that looks at the lock as each launch starts), `UpdateUiTest`,
  `HomeUpdateBannerTest`, `UpdateScheduleTest`, `UpdateManifestTest`. **Not tried on a phone** (Custom Tab, the browser's download, the installer).
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
(`WidgetIntents`): an add opens the sheet over the app in front (`QuickAddActivity`, section 8), a Recent row or its heading opens
MainActivity (`ReceiveWidgetRequests`, done once the app is unlocked and only once). Widget rules followed: picker previews, resizable, Android 12
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

- **Quick add** (2026-11-07, `widgets/QuickAdd.kt`): Add expense, Add income and Transfer on the app icon's long-press menu (`QuickAddShortcuts`: three *dynamic* shortcuts, published in `MainActivity.onCreate` on a fresh start; dynamic and not static because the launcher entry is one of ten `activity-alias`es, and a static shortcut list (an XML resource) would have to be named on each) and an "Add expense" Quick Settings tile (`QuickAddTileService`, in the manifest with `BIND_QUICK_SETTINGS_TILE`, so only the system can bind it; the user adds it in the tile editor). Both open the new-transaction sheet **over the app the user is in** (2026-11-16, Phase 76; before that they opened MainActivity) through `WidgetIntents.intent`, the same plain-word `WidgetRequest.Add` the Add widget's buttons send (`PendingIntent` through `WidgetIntents.open`) - nothing of the ledger is on a shortcut or the intent. The target is `QuickAddActivity` (below); `Show` / `Activity` (the Recent widget's rows and heading) still open MainActivity. `ReceiveWidgetRequests`' add branch stays only for widgets and shortcuts an earlier version made, which name MainActivity until redrawn / republished (`QuickAddShortcuts.publish` runs when MainActivity first opens). The tile launches with a `PendingIntent` from Android 14 on; the `Intent` form it needs before that is deprecated there and lint (`StartActivityAndCollapseDeprecated`) makes it an error unless it sits behind `SDK_INT < UPSIDE_DOWN_CAKE`. Tests: `QuickAddTest`.
  **The overlay** (`quickadd/`, Phase 76): `QuickAddActivity` is a `FragmentActivity`, not exported, `singleTask` with its own `taskAffinity` (`${applicationId}.quickadd`; the empty one is the reminder alert's) and `excludeFromRecents` (its own task: it never brings MainActivity's forward, and a second request reaches the one instance through `onNewIntent`), `Theme.NormPix.Overlay` (translucent, no dimming - the sheet's own scrim does it - no animation, no launch screen), MainActivity's `configChanges`, `FLAG_SECURE` when `BuildConfig.SECURE_WINDOW`. It composes `QuickAddHost`: the user's theme + `ProvideAppEnvironment` + a `QuickAddNavigator` (every page request opens MainActivity and closes the overlay; nothing in the add flow makes one) + `LocalReminderLauncher`, and runs `QuickAddRequests` one after the other: `Add(type)` -> `FinanceActions.addTransaction(type)`, `Share(text, picture)` -> `addFromShare(text, picture, container.sharedPictures)`; both return their `Job`, which is joined. It ends when the requests are done and the app scope has no active child (the saved snackbar, an Undo in flight - polled), or at once on Back or a touch on the empty screen after the sheet has gone. **Lock**: whole-app scope only (wallet-only never locks it); while `isAppLocked` nothing is composed and `authenticate` runs at once, a refusal finishes it; a lock that engages while it is open closes it. **Its window and sheets** (fixed on the emulator, 2026-11-16): `NormPixTheme(paintWindow = false)` (the theme otherwise paints every activity's window in the surface colour, which hid the app underneath), and `LocalPageBehindToBlur provides false` so the sheets and bars are solid (the blur-off look; glass over another app's icons was unreadable) with Material's scrim dimming the other app. **Hand-over**: `ShareTransactionActivity` marks the share `EXTRA_HANDED_OVER` (a SEND without it - the emptied intent Android restarts an old overlay task with after the process went, as an update does - is no request), passes the picture as `EXTRA_STREAM` with a ClipData grant, or - when the sharing app gave no right to pass on - as `EXTRA_PICTURE_WITHOUT_GRANT` (never `EXTRA_STREAM` there: Android adds the grant to any SEND carrying one and refuses it again, which crashed the share target); the overlay reads it with `handedOverPictureOf`. "Nothing to do" is decided in `onResume`, after a request that brought an old task back has arrived through `onNewIntent`. `onStart` / `onStop` call the lock's `onAppResumed` / `onAppPaused` as MainActivity's do, but **never set `host.visible`** (the main screen's flag: the NFC card outlet hands a card readied in the main screen only while it is in front, so not while the overlay is - 2026-11-16 review). It takes `host.activity` and `lock.authenticator` and gives the main screen's back when it goes (only if still its own and the main screen is alive). The ledger is loaded by `FinanceController.ensureLoaded()` (once; `load()` stays the main screen's), the reminders and the cards (up to 3 s) are waited for. A recreated activity (process death) and one reopened from Recents finish at once. Tests: `QuickAddRequestsTest`, `QuickAddOverlayTest`, `QuickAddActivityTest` (`OverlayHarness` runs the host on a `TestEnvironment`; `TestEnvironment(preload = false)` is a process where the main screen never ran).

## 9. About guide (`about/`)

Settings > About NormPix opens a page (not a dialog): name, version, a **Feature guide** pill (`PagePill`, the same as the update page's
Changelog; since 2026-11-01 - it was a heading, "How NormPix works", over a grid of every topic on About itself), your data, crash log,
welcome tour, version, **On GitHub** (2026-11-08, owner: "so it is easy to go there to check things": one row, "NormPix on GitHub", that
opens `https://github.com/<BuildConfig.UPDATE_REPOSITORY>` in the browser with `ACTION_VIEW` (`url.toUri()`, the KTX form lint asks for) inside `runCatching`, the address written on
the row; the same constant the updater reads, pinned by `AboutPageTest`; Android opens the page, NormPix connects to nothing new; found by
searching "github", "source code", "repository" or "releases"), credits. The
pill opens `GuideIndexPage` (route `guide`, `SettingsDetail.GuideIndex` in the tablet panes, `openGuideIndex()`): a card per topic (`GuideTopic`:
**WhatsNew** first (2026-11-16, owner: "update the user guide and How user can access new features": the newest features, each tip naming the path to it -
keep it current when a feature ships; drawn art, no screenshot), AddTransaction, ShareReceipt, Home, Notifications, Activity, Plans, Insights, Wallet,
CardExchange, Widgets, LookAndFeel, Backup, Privacy), each opening its
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
- "Recommendations, round 2" (TASKS.md Backlog): item 1, automatic locked backups, built 2026-11-14 (Phase 72; not tried on a phone -
  a real folder picker, Google Drive's folder, the job firing on its own); items 2-11 are next, in that order.
- Bank statement import (item 7), built 2026-11-15 (section 6): not tried with a real bank's CSV or on a phone; transfers between own accounts and
  card bill payments in a statement are not recognised.
