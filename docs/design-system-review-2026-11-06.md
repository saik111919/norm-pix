# NormPix design system review - 2026-11-06

A proposal for the owner. **Nothing in the app was changed.** Evidence: the source on branch `features` (commit `99d6c0f3`), the
nine README renders in `docs/screenshots/` (Robolectric, 411 x 891 dp, sample data, blur not drawn), and the pinned
`material3-1.4.0` sources. No phone or emulator was used (see section 11).

## 1. Verdict

**Score: 82 / 100.** This is a judgment against the rubric below, not a measurement.

| Area | Score | Why |
|---|---|---|
| Colour and theming | 23 / 25 | Everything reads from `MaterialTheme`; the 238 raw `Color(0x..)` outside `ui/theme` are bank art and widget faces; contrast is tested per theme (`ThemeContrastTest`, `PureThemeTest`, `BannerColorsTest`). |
| Spacing, shape, type tokens | 15 / 25 | The scales exist and are used (1,109 `AppSpacing`, 111 `AppRadius`, 347 type roles) but about 221 off-scale `dp` values bypass them, there is no touch-target token, and the Home hero's 32 dp corner has no name. |
| Components | 19 / 25 | 34 shared components, and the sheets, banners, amount field and dock are excellent. But there are four heading components, a private `DayHeader`, and an unused `AppSplitButton`. |
| Accessibility | 16 / 20 | The 48 dp rule, large-text tests and TalkBack strings are real. Two clickable rows fall short (a 40 dp day header; budget rows with no role or label). |
| Documentation and tests | 9 / 10 | `design-system.md` is unusually complete and is checked by `verify.sh`; nothing stops a new literal from landing. |

**In one paragraph:** the system is strongest where design is hardest (colour, glass, motion, forms) and weakest where it is
cheapest to fix. A handful of small things were never promoted into the system: the section heading, the transaction row's
detail line, a touch-target size, a 32 dp radius and the "overdue" tone. Most fixes are small and invisible. The visible ones
need an owner pick, so section 10 lists them. Localisation was weighed and **rejected** (section 5).

## 2. How this review was made

| Step | Who | Result |
|---|---|---|
| Audit | Claude Sonnet 5.5 (this session) | Counted tokens in the source, read the design reference, looked at 7 of the 9 renders (all light ones plus `home-dark` and `activity-dark`). |
| Round 1, independent | **Gemini 3.8 Flash (High)**, **DeepSeek V4.1 Flash (max)**, **ChatGPT gpt-6-luna (high)** | Each got the same brief and measured facts, but not my conclusions. Gemini **could not open images** and said so; DeepSeek opened the renders; ChatGPT opened them. |
| Fact-check | Claude | Opened the cited file and line for every claim that changed a conclusion (section 6 lists the ones that failed). |
| Round 2, vote | The same three | Each voted ACCEPT / AMEND / REJECT on seven consensus items and four disputes, then ranked its top five and its futures. |

Process notes: a Haiku 5.5 run was started by an interrupted call and was cancelled when the reviewer set changed; Opus 5.5 was
not used. DeepSeek's free tier did not run out this time.

## 3. Audit: what the code says (grep, 2026-11-06)

| Category | Defined | Found |
|---|---|---|
| Colour | `MaterialTheme` schemes, `StatusTone`, Volt/Pure by hand | 238 raw `Color(0x..)` *lines* outside the theme (514 occurrences in all, theme included): `BankCardDesign` 142, `BankLogos` 33, `ClockWidgetTokens` 16, `AccountCardView` 16, `WalletCardView` 12, `CategoryVisuals` 8. Art, not UI drift. `Color.White/Black` 88x (28 in `WalletCardView`). |
| Spacing | `AppSpacing` 2,4,8,12,16,20,24,32,40,48 | Used 1,109x. Literal `N.dp` outside `Tokens.kt`: 1,155, mostly in art (`GuideArt` 139, `WalletCardView` 50, `BankLogos` 48, `NfcTapGuide` 38) plus `FloatingNavBar` 37, `ActionMenu` 31, `Charts` 27. Off-scale in the UI: `6` x60, `18` x49, `10` x39, `22` x38, `14` x35 = **221**. |
| Touch target | none | `48.dp` written out 60x outside the theme; a clickable `DayHeader` is 40 dp. |
| Shape | `AppRadius` xs 4 ... xxl 28 | `AppRadius.*` 111x vs 42 literal `RoundedCornerShape(n)`. The Home hero uses a private `OuterCorner = 32.dp` (`HeroCard.kt:70`). |
| Type | `MaterialTheme.typography` | 347 role uses vs 115 literal `.sp` (mostly bank-card art: `BankLogos` 28, `WalletCardView` 18). |
| Headings | `SectionHeader`, `SectionSubheader`, `ListSection(title)` | Plus `WalletSectionHeader` and a private `DayHeader`; 87 ad-hoc `titleLarge/Medium/headlineSmall` uses. |
| Strings | none | 0 `stringResource`; all UI text is hard-coded English. |
| Semantics | - | `contentDescription = null` in 231 of 397 uses (almost all decorative). Charts already carry a spoken `semanticsLabel`. |
| Big design files | - | `AppShell` 1,015 lines, `Pages` 924, `ActionMenu` 902, `FloatingNavBar` 852. |

**Reading it:** colour discipline is excellent. Spacing discipline is good where the UI is plain and loose in the hand-tuned
chrome (dock, "+" menu, charts). The literals are not a crisis; the missing guard is the problem.

## 4. Findings, decided

"Votes" counts the three round-2 reviewers (Gemini abstained on anything that needed to *see* a render).

| ID | Finding | Evidence | Votes | Sev. | Fix | Owner's go? |
|---|---|---|---|---|---|---|
| **C1** | Section headings are four components with different insets and type. The label's left edge is **20 dp** from the screen edge on Wallet and Activity, **32 dp** on Insights and Budgets, **36 dp** on Home; cards start at 16 dp. | `Lists.kt:47` (start 20, `titleMedium`), `Lists.kt:95` (start 16, `titleSmall`, primary), `WalletOverview.kt:151` (start 4, `titleLarge` SemiBold), `TransactionsScreen.kt:622` (private, `titleSmall`, primary); `home/wallet/insights/activity/budgets.png` | 2 amend, 1 accept | Med | M | **Yes**: it moves labels by 4-16 dp |
| **C2** | Transaction rows cut the account first. `"Transport › Bus & metro · P…"` sits beside a two-line Spotify row, so heights are uneven. TalkBack is unaffected (the row speaks a full string). | `FinanceWidgets.kt:87-96` builds `[category path, account, "For x"]`; `:157-169` joins with " · " and allows one line unless repeating or large text; `activity.png` | 2 accept, 1 abstain (source-confirmed) | Med | S | **Yes**: choose how (section 10) |
| **C3** | No touch-target token; `48.dp` is a literal 60x; a tappable `DayHeader` is 40 dp. | `TransactionsScreen.kt:631` (`heightIn(min = 40.dp)`), `Tokens.kt` | 3 accept | Med | S | No: it is the existing 48 dp rule |
| **C4** | Token gaps: about 221 off-scale values; the 32 dp hero corner has no name, and the Wallet hero is 28 dp. | `HeroCard.kt:70`, `WalletOverview.kt:68` (`rXxl`); **`ShapeTokens.kt` in material3 1.4.0: `CornerExtraLargeIncreased = 32.dp`**, so 32 is on the M3 Expressive scale and only `AppRadius` lacks it | 3 accept | Low | S | No: adding a name changes no pixel |
| **C5** | All UI text is hard-coded English. | 0 `stringResource` | 3 accept the fact, **3 reject the project** (D3) | n/a | n/a | No |
| **C6** | The overdue count "2" appears three times on one screen: dock icon, Plans switcher, Reminders chip. | `budgets.png`; `FloatingNavBar.kt:793`, `PlansAndTransactionsScreen.kt:120,173` | 3 accept | Low | S | Small: it removes a number |
| **C7** | Navigation chrome files are very large. | line counts above | 2 accept, 1 "defer" | Low | M | No: not for the next release |
| **N1** | **Overdue has two tones.** Red on Home's banner, amber on the badge and notice. | `ReminderStyle.kt:33` (`Overdue` is `StatusTone.Error`), `OverdueIndicators.kt:55,81` (`Warning`; its own comment says "deliberately calm"); `home.png` red vs `budgets.png` amber | found by DeepSeek, verified by Claude | Med | S | **Yes**: a product call |
| **N2** | Budget rows are clickable with no role or click label, so TalkBack says a generic "double tap to activate". (`clickable` already merges its children; only the role and label are missing.) | `BudgetsView.kt:167`, `:286` | found by Gemini, verified by Claude | Low | S | No |
| **N3** | The Wallet's Cards summary wraps and leaves one word on its own line ("device"). | `wallet.png`; `WalletScreen.kt:285` | found by DeepSeek, confirmed by Claude | Low | S | No |

## 5. Disputes and how they settled

| ID | Question | Settled as | Basis |
|---|---|---|---|
| **D1** | Day headings use `primary`: near-black in Light, lime in Dark, where they are the most saturated text on Activity. | **Demote past days** to `onSurface` or `onSurfaceVariant`, keep `primary` for "Today". 3 of 3 agree. | `TransactionsScreen.kt:639`; `activity-dark.png` vs `activity.png`. It changes the brand accent's balance, so the owner decides. |
| **D2** | Dark banners are "invisible" (DeepSeek). | **Change nothing.** 3 of 3, DeepSeek included, after re-reading `home-dark.png`: the hairline, the solid status disc and the status-coloured heading carry the tone. | The owner asked for *more* status colour on 2026-11-01; `BannerColorsTest` guards contrast. |
| **D3** | Localise to string resources? | **Reject.** 3 of 3 plus Claude. | One owner who reads English; about a thousand strings and every layout test for no reader. Revisit only if the audience changes. |
| **D4** | Too much chrome above the first row (about 326 dp on Activity, 376 dp on Plans). | **No structural change.** The only agreed cheap win is folding "Spent · Received" into the month control's caption (about 24 dp). Gemini wanted search moved to the top bar. | Everything above the list scrolls away; the opt-in **Bottom controls** setting already moves search and filters. Owner's call. |

## 6. Claims that did not survive checking (so nobody re-raises them)

| Claim | Who | What is true |
|---|---|---|
| `AppSplitButton.Small` breaks the 48 dp rule | Gemini | The component has **no callers** outside its own file, so it is not a live defect. |
| "Cut the Home hero to 28 dp to match the Wallet" | Gemini | 32 dp is M3's own *Extra-large increased*. Name it; do not change it (phone design stays). |
| The dock hides the Insights chart's labels | ChatGPT | A README render is taken at scroll 0, so content continues under the dock. Not a defect. |
| Dark banners "disappear" | DeepSeek | They stay distinct (D2). The 8% wash is decoration; the disc, hairline and heading are the signal. |
| `clickable` rows need `mergeDescendants` | Gemini | `Modifier.clickable` already merges descendants. Only the role and label are missing (N2). |
| "514 `Color(0x..)`" | DeepSeek | **DeepSeek was right and the first audit under-counted.** The audit counted lines (368: 238 outside the theme, 130 inside it); by occurrence there are 514, which is what `DesignTokenRatchetTest` records. |
| "On track" on *All category budgets* is a bug | Claude, first read | Deliberate: `BudgetsView.kt:211`, the aggregate is the whole and the subtitle says how many are over. Its wording can still read as contradictory (section 10). |
| Futures that already exist: home-screen widgets, lakh grouping, privacy blur at the task switcher, haptic tokens, spoken chart summaries | Gemini, ChatGPT | `widgets/` (clocks, Recent, Add), `indianGrouping` in `Money.kt` (renders `3,65,804.00`), `FLAG_SECURE` in `MainActivity.kt:30`, `AppHaptics` in `Overlays.kt:571`, `semanticsLabel` in `Charts.kt`. |

## 7. Roadmap

**Now: the next release (small, mostly invisible)**
1. **Touch-target token and the 48 dp day header (S).** Add `AppLayout.minTouchTarget = 48.dp`. For `DayHeader`, move the 8 dp top gap *inside* the clickable so the hit area is 48 dp while the picture does not move (proposal; render to confirm).
2. **Row detail line (S).** `FinanceWidgets.kt:157-169`; the owner picks the order (section 10).
3. **Name the 32 dp radius (S).** Add `AppRadius.xxlIncreased = 32.dp` (and an `rXxlIncreased`), use it in `HeroCard.kt`. No pixel changes.
4. **Badge rule (S).** A selected control never carries the badge; badges point at somewhere you are not. `PlansAndTransactionsScreen.kt:113-125`, `FloatingNavBar.kt:793`.
5. **Design lint, as a ratchet (M).** Spec in section 8.
6. **Small a11y and wrap fixes (S).** `onClickLabel` and `role` on the two Budgets clickables (N2); stop the widowed "device" (N3).

**Next: two or three releases (owner picks first)**
7. **One heading component (M).** Spec in section 8; needs the alignment pick (C1).
8. **One status grammar (M).** Spec in section 8; resolves N1.
9. **A thin hero shell (S-M).** One shared shape, radius and padding token set for the Home, Insights and Wallet heroes. Not a full component: the heroes differ on purpose.
10. **Quiet day headings (S).** D1, once the owner has seen the render.

**Later:** per-bar chart inspection for TalkBack (the summary label exists, the bars cannot be explored); an adaptive header that folds controls at large text or short windows; more haptic cues through `AppHaptics`; split the chrome files **only when a change touches them**, pulling out pure layout as `ActionMenuLayout` was.

**Dropped (3 of 3, or 2 of 3):** string resources; a monthly recap sheet (a product feature, not a design-system item); a theme-and-font preview sheet (Settings already applies them live).

## 8. Specs for the new pieces

### 8.1 One section heading
**Problem.** Four components share one job. **Existing:** `SectionHeader`, `SectionSubheader`, `ListSection(title)`, `WalletSectionHeader`, `DayHeader`.
**Proposal.** Keep `SectionHeader` and give it a `level`; route the other four through it.

| Level | Used by | Type | Extras |
|---|---|---|---|
| Block | Wallet "Accounts" / "Cards" | `titleLarge` SemiBold | optional summary line, trailing action |
| Section | Home "Needs attention" | `titleMedium` | trailing deck button, action |
| Group | `ListSection` title, Insights, Budgets | `titleSmall`, `primary` | none |
| Day (a Group variant) | Activity | `titleSmall`, `primary` or quiet (D1) | trailing total, optional chevron, **48 dp** target, a heading role |

**One inset rule** for all of them, picked from: **A** edge-aligned (16 dp), **B** 20 dp (what Wallet and Activity use now), **C** card-text-aligned (32 dp, what Insights and Budgets use, matching Material's list subheaders). Option B moves Home and Insights/Budgets; option C moves Wallet and Activity. Render all three before choosing.
**Accessibility.** Keep `heading()`; the Day level keeps its merged "name, spent amount" description.

### 8.2 One status grammar
**Problem.** `StatusTone` is shared, but the *state to tone* mapping is local: overdue is `Error` in `ReminderStyle.kt:33` and `Warning` in `OverdueIndicators.kt:55,81`; `BudgetHealth` has its own pill.
**Proposal.** One map, state to tone and icon and word (`Overdue`: Error, ErrorOutline, "Overdue"), and three weights a surface can choose: **Dot/Badge**, **Pill**, **Banner**. Principle: *the state decides the tone, the surface decides the weight*; hue never carries meaning alone (the word and icon always come with it).
**Open question.** Is a quiet *amber* count a feature ("deliberately calm") or drift? See N1.

### 8.3 Tokens to add (no visual change)
`AppLayout.minTouchTarget = 48.dp`; `AppRadius.xxlIncreased = 32.dp` (`rXxlIncreased`). Half-steps (6, 10, 14, 18, 22) are **not** added: snap them to the scale when a file is next touched, not in a sweep (DeepSeek's rule, and the cheapest way not to disturb the phone design).

### 8.4 Design lint (a ratchet, not a sweep)
A JVM test (no Robolectric; Gradle runs it from `native/app`, so it reads `src/main/kotlin`) that counts, per file, `Color(0x`, `RoundedCornerShape(<digit>`, `<n>.dp` and `<n>.sp` outside an allow-list (`ui/theme`, `wallet/Bank*`, `*Art*`, `widgets/*Tokens*`, `about/ui/GuideArt.kt`). A checked-in baseline records today's counts; **the test fails only when a file's count goes up**, and the baseline is lowered as files are cleaned. This is the only guard for the 221 off-scale values that does not require touching 1,100 lines first.

## 9. The best design: "The Quiet Ledger"

Money is the only loud thing on the screen.

1. **Money speaks, chrome whispers.** The hero number stays the signature. Everything else (headings, dates, controls) sits one step below it in weight and colour.
2. **One gutter, one heading voice.** A label's left edge means the same thing on every tab.
3. **State decides tone; surface decides weight.** Overdue is the same colour in a banner, a badge and a notice; a state is never shown by hue alone.
4. **Rows say the same things in the same order**, and what disambiguates a payment (the account) is the last thing to be cut.
5. **The system defends itself.** A ratchet test and named tokens, so the next screen cannot drift.
6. **Options, not replacements.** Every visible change ships as a render the owner picks from, and a look that is replaced stays reachable in Settings.

The three screens that change most: **Activity** (row detail, day headings, control density), **Home** (heading inset, overdue tone) and **Plans/Budgets** (badge rule, heading inset, the "On track" wording). The Wallet changes least: its headings join the shared component and one orphaned word is fixed.

**Keep exactly as it is** (all reviewers agree): the hero number with its delta pill; the hero amount field; `DiscardChangesGuard` and the sheet rules; the page-blur and glass engine with its system-blur gate; the bento action menu and its edit mode; theme-derived colour with per-theme contrast tests, including Pure; the flat overlapping deck; the status banners as the owner shaped them on 2026-11-01.

## 10. Decisions for the owner

| # | Decision | My recommendation |
|---|---|---|
| 1 | **Heading alignment** (C1): A 16, B 20 or C 32 dp | Render all three on Home, Wallet and Insights, then pick. Leaning **B (20 dp)**: two screens already use it and the Home's 36 is the outlier. |
| 2 | **Row detail** (C2): account first, or the category path truncates first, or two lines | **Category path truncates first**, so the account survives and the row height stays; keep two lines for repeating rows. DeepSeek preferred account-first; render both. |
| 3 | **Quiet day headings** (D1) | Yes: past days `onSurfaceVariant`, "Today" in `primary`. Changes the Volt accent's balance, so look at the render first. |
| 4 | **Overdue tone** (N1): red or amber | One tone, **Error**, with the badge and notice kept *smaller* rather than a different colour. Your 2026-11-01 call ("every colour says its status") points the same way. |
| 5 | **"On track" on the aggregate card** (section 6) | Reword to show the mix, for example "1 over" in the error tone, since the subtitle already says it. Optional. |
| 6 | **Activity controls** (D4) | Leave. At most fold "Spent · Received" into the month caption. |
| 7 | **Badge rule** (C6) | Yes: the selected tab or switcher shows no badge. |

Things that need no go: items 1, 3, 5 and 6 of section 7, the Budgets labels and the wrapped word.

## 11. What was not verified

- **Nothing was run on a phone or emulator.** Every visual claim comes from README renders (light and dark Volt only). **Pure, wallpaper colours, 1.6x text and blur on** were not rendered. Claude viewed 7 of the 9 renders (not `insights-dark`, not `wallet-dark`); DeepSeek viewed 7 (the same two missing); ChatGPT viewed what it cited; **Gemini could not see images**, so its votes on C1 pixels, C2 and C6 are source-only.
- The 48 dp `DayHeader` fix (section 7, item 1) is a proposal: it has not been compiled or rendered.
- TalkBack behaviour is read from source, not heard.
- `FloatingNavBar.kt:793` and `PlansAndTransactionsScreen.kt:120,173` as the badge sites come from Gemini's vote; Claude confirmed the three badges in the render but did not open those lines.
- Reviewers are language models: the votes show where independent readings converge. They do not stand in for the owner's eye on a phone.

## Appendix: who found what (round 1)

| Finding | Gemini | DeepSeek | ChatGPT | Claude |
|---|:-:|:-:|:-:|:-:|
| Headings fragmented (C1) | yes | yes | yes | yes |
| Row detail truncated (C2) | not visible | yes | yes | yes |
| Touch target token or 40 dp (C3) | yes | no | yes | yes |
| Off-scale tokens (C4) | yes | no | partly | yes |
| No localisation (C5) | yes | no | yes | yes |
| Triple badge (C6) | no | yes | no | yes |
| Big chrome files (C7) | yes | no | no | yes |
| Overdue tone split (N1) | no | **yes** | no | no |
| Budgets clickables (N2) | **yes** | no | no | no |
| Wrapped "device" (N3) | no | **yes** | no | yes |
| Day header colour (D1) | no | **yes** | no | no |

## 12. Progress (2026-11-06, later the same day)

Done on `features`, uncommitted, with the owner's go for the no-decision items only (section 10 is untouched):

| Roadmap item | State |
|---|---|
| 1. Touch-target token and the 48 dp day header | **Done.** `AppLayout.minTouchTarget = 48.dp`; `DayHeader`'s 8 dp top gap moved inside its tappable pill, so the page is pixel-for-pixel where it was (checked on the `activity.png` render) and the tap area is 48 dp. Only the press ripple is 8 dp taller. |
| 3. Name the 32 dp radius | **Done.** `AppRadius.xxlIncreased` / `rXxlIncreased`; `HeroCard` uses it. No visual change. |
| 5. Design lint as a ratchet | **Done.** `DesignTokenRatchetTest` + `app/src/test/design-baseline.txt` (65 files; 514 colours, 49 corners, 221 half-steps). Proven to fail both ways (a new leak, and a clean-up not recorded). `build.gradle.kts` declares the files as test inputs, found when the first mutation run came back "up to date" in 2 s. |
| 6. Budgets labels, the wrapped word | **Done.** The two Budgets rows say "Change the overall budget" / "Change this budget" and are buttons for TalkBack; the Wallet's section summaries break evenly ("…encrypted and / kept on this device"). |
| 2, 4, 7-10 | **Not started: waiting for the owner's picks** (row detail, badge rule, heading alignment, status tone, quiet day headings). |

**Also fixed (asked separately): Home's number collapsing into the header.** It rushed ahead of the card, shrank to a third in about 36 dp of scroll, and the header chip showed as a box on the card from the first pixel of scroll. See `references/features.md` (Home) for the new rule; the numbers are `COLLAPSE_STRETCH` (1.8) and `SHRUNK_BY` (0.5) in `screens/HeroCollapse.kt`.

Not verified on a phone: every claim here comes from Robolectric renders and unit tests. The hero's *feel* in motion in particular needs a thumb on the `qa` build.
