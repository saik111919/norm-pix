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
  values; System font or the user's own file are alternatives. Money and hero numbers use the "hero amount" styles.
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
- **Decks**: `DeckCarousel`, `DeckStack`, `DeckFacts` (accounts, cards and Top spending share them; `DeckFacts` lays facts out
  side by side while every label fits one line, else stacked, decided on labels alone so the panel never changes size as the
  deck moves; a label is never broken mid-word).
- **Rows**: `SwipeRevealRow` (`SwipeReveal.kt`; trailing corners square off as it opens so it meets the action tile flush),
  `SwipeableTransactionRow`.
- **Banners**: `BannerStack`/`Status.kt` ("Needs attention" - the page's own tonal surface, status said by icon + eyebrow
  colour). **Search**: `SearchField`, `SearchFilterBar`. **Reveal/LiveRegion** helpers for notices.
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
  `overlays.alert` for something the user must see at once.
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
- Floating bars (dock, More actions, top back circle and actions pill) are real glass: the page records itself once
  (`frostedSource`) and each bar draws a blurred copy of what is behind it (`frostedGlass`); solid with a slight shadow where
  blur is off; no shadow under glass.
- Blur can't be seen in Robolectric directly; tests inject `LocalWindowBlurOverride provides true`, drive `mainClock` and
  compare pixels (`SheetSurfaceAndBlurTest`, `PageBlurFollowsSheetTest`, `StackedOverlayBlurTest`, `FrostedChromeTest`).

## 5. Navigation chrome

- Bottom **dock** (`FloatingNavBar`): primary-tinted pill, selected item a `primaryContainer` pill with icon + label, other
  items icons, 48 dp targets, concentric corners, hides on scroll down and returns on scroll up / at the list end / on tab
  change (`ScrollAwareBar`: offset + alpha only so nothing jumps; 16 dp jitter threshold). The trailing button is "More
  actions" (three dots) with the screen's actions and Settings.
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
