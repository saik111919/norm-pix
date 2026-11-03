# NormPix design system and UI rules

Verified 2026-10-27 against commit `763cc08f`. The owner judges the app by how it looks and feels on their phone, and
corrects design by screenshot. Reuse what exists; a new bespoke widget needs a reason.

Contents: 1 Theme and tokens · 2 The shared components · 3 Sheets, messages and menus · 4 Blur and glass ·
5 Navigation chrome · 6 Motion · 7 Accessibility · 8 Wide screens · 9 Material 3 Expressive in this app ·
10 Process for a UI change

## 1. Theme and tokens (`ui/theme/`)

- Colours and typography come from `MaterialTheme` (`Theme.kt`, `Typography.kt`); composables read them from the theme and
  never hard-code colours. `AppTheme` builds the scheme: from the wallpaper (Android 12+ dynamic colour), or from an
  `AccentColor` seed through `material-kolor`. **Volt** and **Pure** are drawn by hand (`AppTheme.voltScheme`,
  `pureScheme`); Pure's containers are tonal greys so cards and selected pills stay readable. `StatusColors.kt` gives
  success / warning / info / error roles (`StatusTone`). `ThemeContrastTest.everyTheme` checks contrast for every theme; a
  new palette must be added there.
- Tokens (`Tokens.kt`): `AppSpacing` (2,4,8,12,16,20,24,32,40,48 dp as xxs..x5l), `AppRadius` (xs 4, sm 8, md 12, lg 16, xl
  20, xxl 28; pick by *role*: xs inner list corners, sm chips, md fields/menus/rows, lg cards, xl outer list groups and
  snackbars, xxl sheets/dialogs), `AppIconSizes`, `AppLayout` (content max widths 720/840/1200, `railWidth` 96, `wideRailWidth`
  256, `listPaneWidth` 360, bottom clearances), `AppMotion` (200/300/500 ms; emphasized enter/exit/standard easings; a
  `spatial()` spring with damping 0.8), `AppFrostedTokens` (page blur 40 dp, chrome blur 20 dp, see-through surface 62%,
  scrim 8%).
- Type: interface font is GS Flex (Google Sans Flex, variable) by default - weight/width/roundness axes via presets or hand
  values; Nunito (bundled, variable), System font or the user's own file or Google Fonts zip are alternatives (features.md
  section "Interface font"). Money and hero numbers use the "hero amount" styles.
- Money colours: expense warm, income positive, transfer neutral (`TransactionType.role()` in `finance/ui/TransactionStyle.kt`);
  the selected type tints the editor's amount and Save, not everything.

## 2. The shared components (`ui/components/`)

Use these before writing anything new:

- **Pages**: `AppPage` (tab pages), `AppSubPage` (pushed pages; owns the back button styles), `Pages.kt`. Lists: `Lists.kt`
  (`ListSection`, `SectionSubheader`, grouped segments with 4 dp inner / 20 dp outer corners).
- **Sheets**: `EditorSheet` (every add/edit form: titled sheet, scrolling body, **pinned full-width Save**, no Cancel/close,
  `DiscardChangesGuard` asks before dropping unsaved changes), `ConfirmSheetContent` (every confirm/alert), `SheetSurface.kt`.
  Opened imperatively: `LocalOverlays.current.sheet<T> { dismiss -> ... }`, `.confirm(...)`, `.alert(...)`, `.loading(...)`.
- **Choices**: `ConnectedChoiceRow` (`finance/ui/FormSelectors.kt`: the type selector, theme System|Light|Dark,
  Expense|Income - one pill track with the chosen choice as a pill inside; 2 dp gaps, radio semantics), `ButtonGroup.kt`,
  `AppSplitButton` (`SplitButton.kt`),
  tonal selection cards with a 40 dp circular icon badge (Category, Paid from, From/To), split date/time chips.
- **Progress**: `AppLinearProgress`, `AppLoadingIndicator` (`Progress.kt`) in Wavy or Flat (`ProgressStyle`).
- **Decks**: `DeckCarousel` (a **flat** overlapping row since 2026-10-31: the front card whole, its neighbours at 0.9 and shaded, about 44 dp of
  each showing - inside Material's 40-56 dp peek - snapping on `AppMotion.spatial()`, Material's default spatial spring 0.8 / 380; it was fanned 46
  degrees in 3D, which the owner did not like; the details panel slides, shrinks and fades but no longer turns), `DeckStack`, `DeckFacts`
  (accounts, cards and Top spending share them; `DeckFacts` lays facts out
  side by side while every label fits one line, else stacked, decided on labels alone so the panel never changes size as the
  deck moves; a label is never broken mid-word).
- **Rows**: `SwipeRevealRow` (`SwipeReveal.kt`; trailing corners square off as it opens so it meets the action tile flush; the
  reminders and wallet cards), `SwipeActionRow` (`SwipeActionRow.kt`; Gmail's way: one `SwipeAction` per side, its colour and icon
  behind the row, armed past 40% of the width or 160 dp (tick + firmer colour + icon pop), a `leaves` action flies off and folds
  its height away, otherwise it springs back), `SwipeableTransactionRow` (on `SwipeActionRow`).
- **Banners**: `BannerStack`/`Status.kt` ("Needs attention" - since 2026-11-01 every colour on a banner says its status, after the
  owner found the calm version "not informative": `bannerColors(role, scheme)` washes the surface with the status colour at 8% over
  `surfaceContainer`, edges it with a 1 dp hairline at 28%, puts the icon in a **solid** disc of the status colour (`onColor` icon),
  the eyebrow (SemiBold) and the quieter text button in it, and fills the main tonal button with an 18% tint of it. Always the status
  **colour** at a fixed strength, never the role's container or `onContainer` - containers differ in strength (an error's pale, a
  warning's deep; Fidelity-generated warning/info `onContainer` is white) and `onContainer` came out white on a pale tint. Every ink is
  checked for 4.5:1 (`readableOn`, falling back to `onSurface`), `BannerColorsTest` asserts it for every tone x accent x Light/Dark.
  Same layout and size as before; the banners behind the front one are only 6% darker, `DeckStack(behindDim)`, where cards take 20%). **Search**: `SearchField`, `SearchFilterBar`. **Reveal/LiveRegion** helpers for notices.
- **Chrome**: `FloatingNavBar` (dock), `BottomControls.kt`, `Tabs.kt`, `FocusRing.kt`, `LockGate.kt`, `Snackbar.kt`.
- **Fields**: tonal fill `surfaceContainerHighest`, `TonalFieldShape`, min 64 dp, `IconBadge`; typed fields (Paid to, Note,
  names, numbers) get a soft outline, picking fields stay tonal; the amount is centred at Display size with no box (a soft
  outline appears while typing, error colour when invalid).
- **Icons/art**: `ui/theme/AppIcons.kt` (app icon set; Wallet uses the supplied Google Wallet artwork), `finance/ui/CategoryArt`
  / `CategoryVisuals` / `BrandLogos`, `banks/ui/BankBadge`, `AssetImage`, `SvgAsset`.

## 3. Sheets, messages and menus (rules the owner has corrected before)

- **Decisions, forms and pickers are bottom sheets**, not dialogs: add/edit forms, confirms ("Reset all settings", delete,
  discard), month/date/time/range pickers (`DateSheet`, `TimeSheet`, `MonthPickerSheet`, `DateRangeSheet`; the `*DialogFor`
  names are old wrappers that open them). The destructive action wears the error colours. Swipe, scrim, back and locking the
  app all mean "no". Every sheet has a drag handle; a tall sheet that fits opens fully, otherwise half-way
  (`expandable = true`).
- **Say things inside the sheet, not in a snackbar.** The snackbar host lives in the activity window and every sheet is a
  window above it, so a snackbar fired mid-form shows faintly *behind* the sheet. Since 2026-10-14
  `AppSnackbars(heldBack = { overlays.isOpen })` holds every snackbar until no sheet is open. Do instead: a notice at the top
  of the sheet (`SharedDraftNotice` pattern, with `RevealVisibility` + `politeLiveRegion()`), `FieldError` under a field,
  `overlays.alert` for something the user must see at once. A form that fails to save says why through
  `EditorSheet(errorText = ...)`, pinned above Save where it cannot scroll out of sight; every form's `scope.launch` must also catch
  `Exception` (not only `FinanceValidationException`): a `rememberCoroutineScope` has no handler, so an uncaught failure closes the
  whole app (the account and category forms did not, until 2026-10-31).
- **Snackbar wording and place.** A snackbar says what happened ("Expense updated", never a bare "Changes saved"), and an edit
  that changed nothing closes without one (`dirty`) - in the transaction form only: a recurring rule is always rewritten, since saving
  it again normalises it (`RecurringEndsTest`), so it keeps saying "Recurring payment updated". On a phone it rides `bottomInset` above the dock and comes down with it
  (`AppSnackbars.dockGone`, set by `AppShell`) so it never floats over the strip where a hidden dock was.
- **Slow work -> `overlays.loading(title, message) { work }`** (a sheet with the loader and Cancel, at least 600 ms, gives
  null if cancelled, `cancellable = false` for data-changing work). Never a "working..." snackbar. Change the form only after
  it returns so Cancel leaves it untouched.
- **Menus/popups inside a sheet** (`DropdownMenu`): `containerColor = solidColorScheme.surfaceContainer`,
  `shape = AppRadius.rLg`, because inside a see-through sheet the theme's surface tones are translucent tints.
- Forms use tonal fields, dropdowns not chip grids, connected choices, one `EditorSheet` layout, and a "Create category" inside
  the category menu.

## 4. Blur and glass (the part most easily broken)

- The app blurs **the page itself** in step with the sheet, not through the window manager (the window-manager approach
  trailed the sheet by frames and was removed). `AppOverlays.backdropBlur` is the largest on-screen share of any open sheet or
  dialog; sheets report it with `Modifier.blursBackdrop`; the root draws the page through a `RenderEffect` whose radius it
  reads every frame (`rememberEasedBlur` + `Modifier.blurredBy`). Blur is front-loaded against the sheet's travel, fades out
  as it closes, and a sheet opened over a sheet blurs the one underneath (`AppOverlays.blurAbove`).
- Where the page is blurred, sheets and dialogs are **see-through** (62% of their surface colour) and fields inside them use
  `SeeThroughColors` tints that come out as the solid tones when laid over the sheet's own tone; where it isn't (setting off,
  system blur off, before Android 12) everything is **solid** with Material's usual dim. A sheet stops under the status bar.
- Gate for all of it: Settings > Appearance > Effects > Background blur, Android 12+, **and** the system's window blur
  (`rememberWindowBlurAvailable()` follows `WindowManager.isCrossWindowBlurEnabled` and its listener: battery saver, the
  developer option, or "Reduce blur effects" turn it off; there is no public API for the last).
- **Footer blur** (`ui/components/FooterBlur.kt`, Settings > Appearance > Effects > Footer blur, follows Background blur): behind the dock / back row
  the page is blurred (`AppFrostedTokens.footerBlurRadius`) and washed with the page colour, fading in from nothing over `footerFade` above the bars
  (a `DstIn` gradient on an offscreen layer), like DropSauce's `BarBlur`; it draws from the same `frostedSource` recording, takes no touches, and
  goes down with the dock (`visible`). `AppShell` (compact) and `AppSubPage` (floating back row / search card) place it; none where blur is off.
- Floating bars (dock, "+" action panel, top back circle and actions pill) are real glass: the page records itself once
  (`frostedSource`) and each bar draws a blurred copy of what is behind it (`frostedGlass`); solid with a slight shadow where
  blur is off; no shadow under glass.
- Blur can't be seen in Robolectric directly; tests inject `LocalWindowBlurOverride provides true`, drive `mainClock` and
  compare pixels (`SheetSurfaceAndBlurTest`, `PageBlurFollowsSheetTest`, `StackedOverlayBlurTest`, `FrostedChromeTest`).

## 5. Navigation chrome

- Bottom **dock** (`FloatingNavBar`): primary-tinted pill, selected item a `primaryContainer` pill with icon + label, other
  items icons, 48 dp targets, concentric corners, hides on scroll down and returns on scroll up / at the list end / on tab
  change (`ScrollAwareBar`: offset + alpha only so nothing jumps; 16 dp jitter threshold). What changes size when the tab changes (the
  selected pill's padding and its label opening) moves on **`dockSpring`** (damping 0.65, stiffness 300: ~7% overshoot, settled in a third
  of a second, instant with animations off), after Essentials' toolbar (verified-facts.md) - the app's own `spatial()` spring measured
  almost the same as the old 300 ms curve, so the dock has a livelier one; colours stay short curves, which must not overshoot. The trailing button is the **"+"
  action menu** (`navigation/ActionMenu.kt`): a bento panel growing out of the button, on a grid of four equal cells - by default the
  two main actions as capsules side by side on the top row (Expense filled, Income tonal), then every other action as the same
  squircle (icon over its name, 12 sp). The circular **Edit** button is not in the grid since 2026-11-01 (owner: put it in the empty space left of the close button, only while the menu is open): `EditActionsButton` in `FloatingNavigationRow` overlays the faded dock's end, 10 dp left of the "x", the same diameter (`DockHeight`), filled with the dock's `companionContainer` and its icon in `companionContent` (the "+" button's resting colours) with a 6 dp shadow - an outlined circle on the panel's near-white fill vanished on a light page (owner, 2026-11-01; done by Gemini 3.8 Flash High, `ActionMenuEditTest.the_edit_button_is_visible_in_a_light_theme`); it fades and grows in only after the dock has gone (`(p - 0.5) / 0.5` in a `graphicsLayer`), takes touches and TalkBack only while open, and hides in Edit mode. The panel is fully opaque, so it draws no
  blur (an invisible blur cost a frame's GPU; at 95% the page behind showed through as a ghost once the blur was gone), and its tiles fade with `CompositingStrategy.ModulateAlpha`, not an off-screen copy each
  (measured on the phone 2026-10-31: GPU median per frame 14 ms -> 8 ms while closing) (no ornament: the sparkle in the first mock was removed on the owner's word). The eleven actions are built in
  `AppShell` (`allActions`, each with a stable `FooterAction.id`); **`ActionMenuLayout`** (pure, `navigation/ActionMenuLayout.kt`) is
  the user's arrangement - order plus a size per tile, **Small or Wide, the two sizes of Pixel's Quick Settings editor** (verified,
  `verified-facts.md`) - kept as `id:s,id:w,...` in the preference `action_menu_layout` (empty = the design; carried by an app backup
  like any preference; Settings > Appearance > Navigation & controls > **Reset action menu** puts it back). `bentoPlan` packs it:
  the **first tile is the featured one**, a two-cell capsule that starts the top row; the others flow from beside it across four columns, a wide
  tile moving to the next row when it does not fit (leaving a hole, as on Pixel); no cell is kept for Edit. **Edit mode** (the button;
  state held in `AppShell` so Back leaves it before it closes the menu) follows Pixel's editor: a tap selects a tile instead of
  running it, the selected one gets a handle on its right edge that drags between the sizes, touch-and-hold lifts a tile and the
  others make room (positions and sizes animate on the spatial spring), and a bar under the grid has Reset (only when something
  changed) and Done, plus Move earlier / Move later / Make wide|small for those who don't drag - the same moves are custom
  accessibility actions on every tile. Edits show at once and are kept as they are made (`shown` in the panel, so the saved value
  coming back a frame later never flashes the old order); a tile keeps its colour wherever it is put. **One progress value** (`rememberActionMenuProgress`: spatial
  spring open, a critically damped spring (stiffness 520) close, instant with animations off; the close was a 200 ms fast-start curve
  until 2026-10-31, which took the panel from open to nearly gone in three frames - a blink, with the dock popping back at once -
  and a spring keeps its speed if "+" is tapped mid-close) drives the panel's growth from the button's centre (visible from a third of
  the way open and kept to the end of the way back), each
  tile's arrival (staggered by distance from the button, `arrivalRank`), the "+" turning into "x", the dock stepping back
  (`dockShown`: gone by three-quarters open (`DockGoneAt`), and while not all there it **slides** `DockSlide` 72 dp towards the "+" and
  shrinks `DockShrink` 15%, so on the close it visibly slides back out of the button over ~200 ms; until 2026-11-01 it was gone by half open
  and only faded with a 10% shrink, which the owner saw "snap into place" - filmed on the phone after: a steady ease-out slide) and the
  page's dim (`actionMenuScrim`); read it only in `graphicsLayer`/`drawBehind` lambdas so `AppShell` never recomposes per frame. While
  open the dock is not pressable and hidden from TalkBack (a tap on it closes the menu), the scrim is a tap-to-close target, back
  closes it. With large text (font scale over 1.15) or cells under 54 dp the same actions come as a three-column icon-over-label
  list with two-line labels (reordered with the bar's buttons; sizes mean nothing there, so no resize). Tile colours are **three theme roles, as the rest of the app uses them** (`tileTone`): the first tile is the
  filled `primary` (as Home's "Add income" is), the second the tonal `primaryContainer`, every other the neutral `surfaceContainerHighest` (what
  fields and list segments are) with its icon in the accent; the panel is a tone lower (`surfaceContainerLow` in dark, `surfaceContainerLowest`
  in light, 95-96% opaque) so tiles stand off it. Rank is size and fill, never a different blue per tile: a five-step ramp (2026-10-31, owner:
  "not looking and feeling good") read as a patchwork whose darkest tiles melted into the panel. Every tile's label **and** icon are
  **checked for 4.5:1** (`tone`: the first candidate that reads, else black or white) and `ActionMenuTest` asserts it for every accent in
  Light and Dark, and that the roles stay three. Bento labels auto-size from 12 sp down to 9 sp (`TextAutoSize`) so a long word in a
  custom font never touches the tile's edge.
- **Back button styles** (`BackButtonStyle`): *Top floating circle* (default; no app bar - a glass circle and an actions pill
  float over a page that scrolls on under them, a large heading scrolls away; the status bar keeps the page's colour), *Bottom
  dock*, *Standard top bar*. The account/profile pill and page title share one floating row on tab pages.
- **Bottom controls** (opt-in, phone-only toggle, modelled on komi-store's Classic layout): back, page actions and
  search/filters move to the bottom. `rememberBottomControls()` = setting on **and** compact window. A page with its own
  search takes `bottomSearch` and leaves the field out of its list in that mode; a pushed page built on bare
  `AppPage(onBack)` misses the bottom dock unless routed through `AppSubPage` (see `TransactionHistory`).
- Page transitions: six styles (`PageAnimationType`); only the page on top changes opacity (no ghosting); the window
  background follows the app's surface colour so Dark on a light phone never flashes white. Judge motion on the qa/release
  build: a debug build recomposes Home from scratch when a page closes and can stall.

## 6. Motion

**Live motion** (endless, ambient): only the hero's weather today. It runs only where `LocalLiveMotion` is true - provided by
`MainActivity` alone, so tests, previews and pages rendered on their own never have endless motion and every screen still settles (a
Compose test's `waitForIdle` would hang on one) - and only while wanted and seen: its setting on, animations on, Battery Saver off, the
screen resumed, the card on screen (`onGloballyPositioned` bounds). Its clock (`rememberLiveClock`) ticks at **30 fps** (`LIVE_FPS`), not
every frame, and is read only in draw lambdas / graphics layers: on Home every tick also re-blurs the frosted dock's copy of the page,
~10 ms of GPU on the owner's phone, so every-frame motion saturated it (measured 2026-11-01: 648 frames/5 s, 29 ms median; at 30 fps
234 frames, 13 ms; another tab 0). Particles are pure functions of index and time. Light (sun, snow, glow) is white in every theme;
what darkens (rain) is in the card's ink.

Reduced motion (system "remove animations") removes the app's own motion everywhere (`AppMotion.duration` returns 0,
`spatial()` becomes `snap()`; the card tilt never starts). Material's own `ModalBottomSheet` springs/curves are used for
sheets; do not invent custom sheet motion. Sheet style is separate from page style (`SheetAnimationType`).

## 7. Accessibility (a hard requirement here, not polish)

- 48 dp minimum targets; every control described for TalkBack; custom accessibility actions on swipe rows; state is never
  colour alone (overdue = badge + text + colour); announcements use polite live regions.
- Check large text: the layout must hold at 1.3x (dock labels) and 1.6x font scale (`LargeTextTest`, `LargeTextLayoutTest`).
- Contrast is tested per theme. Pictures in the About guide are inert and hidden from screen readers.

## 8. Wide screens

The phone design is the reference and **must stay exactly as it is**. Branch on window size only (`WindowSize`,
`RailStyle.of`, `isListDetail`) and put wide-only behaviour behind composition locals (`LocalDetailPane`,
`LocalDockHosted`, `LocalRailCollapsed`) so the phone path is the default. Android's own tablet Settings is the visual
reference for wide Settings (search pill with profile picture on top, flat rows with the open one a rounded pill, the page on
a tone-on-tone panel). Test sizes with `@Config(qualifiers = "w1280dp-h800dp")` (wide rail, two-pane), `w1024dp-h768dp`
(narrow rail), `w915dp-h412dp` (phone on its side). After any shared-composable change, render the phone size and compare
with the old render.

## 9. Material 3 Expressive in this app

The project resolves **material3 1.4.0**, which has the *tokens* but not the composables for wavy progress, the loading
indicator or split buttons. The app builds them: `ui/components/Progress.kt`, `ui/components/SplitButton.kt`
(shape morph via `androidx.graphics:graphics-shapes`). Before reaching for an Expressive composable, grep the material3 sources
jar in the Gradle cache for it; if absent, extend the app's own components rather than bumping Compose to an alpha (judged
riskier). Pull token numbers from the `*Tokens.kt` in that jar.

## 10. Process for a UI change

1. Find the nearest existing pattern (a sheet, a list, a deck) and follow it; the owner's phrase is "follow the design pattern".
2. If the owner sent a screenshot, treat it as the spec for the look; a *reference app* (CRED, Pixel Settings, komi-store,
   DropSauce, Samsung Reminder, the launcher drawer) is inspiration - build the app's own version in this design system.
3. Make it work for Light, Dark, **Pure** and wallpaper colours; blur on and off; 411 dp phone and wide windows; 1.6x text.
4. **Render it** (throwaway Robolectric test, see `build-test-release.md`) and look at the PNG before claiming it is right.
5. Keep the previous design as a choice when the owner says so (a Settings option), and don't change phone layouts for a
   tablet request.
6. Update the About guide topic and `SettingsCatalog` entry if the change is user-visible.
