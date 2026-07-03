# Cashflow (NormPix)

[![Build & Release APK](https://github.com/saik111919/norm-pix/actions/workflows/android-release.yml/badge.svg)](https://github.com/saik111919/norm-pix/actions/workflows/android-release.yml)

A **local-first expense tracker** for Android — Kotlin + Jetpack Compose, Material 3 Expressive. No `INTERNET` permission, no tracking: transactions are parsed from bank SMS **on the device** and nothing ever leaves your phone.

Implemented from a Claude Design prototype (`Cashflow App.dc.html`), including the app icon (`NormPix App Icon.dc.html`).

## 📲 Download the latest APK

**[⬇ Download from GitHub Releases](https://github.com/saik111919/norm-pix/releases/latest)** — grab the `.apk` under *Assets*.

Every push to `main` automatically builds and publishes a new release (see `.github/workflows/android-release.yml`). On your phone: download the APK, open it, and allow installing from unknown sources if prompted. Requires Android 7.0+; blur effects need Android 12+.

## Screens

| Tab | What it does |
|---|---|
| **Home** | "Spent this month" hero cards with budget ring, SMS-review nudge, category mini-bars, recent transactions |
| **Activity** | Full transaction list with search, category filter chips, day grouping, tap-to-edit |
| **Inbox** | Auto-detected bank SMS waiting for review — Confirm / Edit / Ignore, duplicate & low-confidence warnings |
| **Insights** | Monthly budget ring, per-category budgets with over-budget alerts, subscriptions, 6-month trend |
| **Settings** | Color themes, light/dark/system mode, nav-bar glass controls, bank & sender rules, categories, export |

Plus a full-screen **Add** flow (calculator keypad with `+ − × ÷` expressions, expense/income/transfer, subscriptions) and an edit bottom sheet.

## Theming

Eight color themes, switchable at runtime from Settings and persisted:

- **Forest / Ocean / Orchid** — from the original design
- **Depths / Sunset / Canopy / Mono** — Theme Factory palettes (Ocean Depths, Sunset Boulevard, Forest Canopy, Modern Minimalist)
- **Dynamic** — Material You wallpaper colors on Android 12+, like Pixel system apps

Each theme has hand-tuned light **and** dark variants; the launcher icon includes a monochrome layer for themed icons.

## Project layout

```
app/src/main/java/com/normpix/
├── MainActivity.kt                  # edge-to-edge entry point
├── cashflow/
│   ├── CashflowApp.kt               # root: screens, nav, FAB, add flow, sheet, toast
│   ├── CashflowTheme.kt             # 7 palettes + Material You dynamic tokens
│   ├── CashflowModels.kt            # data models, sample data, ₹ formatting, calc engine
│   ├── CashflowState.kt             # single state holder + SharedPreferences persistence
│   └── ui/                          # one file per screen + shared components
└── ui/floatingnav/                  # reusable glass nav bar (below)
```

## Build from source

1. Open the folder in Android Studio and let Gradle sync (Gradle 8.9, AGP 8.7, JDK 17).
2. Press **Run** — API 31+ shows the real backdrop blur; older devices get the translucent fallback by design.

Command line: `gradle assembleDebug` (or generate the wrapper once via Studio's `wrapper` task, then `./gradlew assembleDebug`).

---

# FloatingGlassNavBar (component docs)

The floating, glass-like bottom navigation bar is a reusable, dependency-free component. Active tab shows icon + label in a soft tinted pill; inactive tabs show icons only. Supports count badges and live-tunable blur/opacity.

## Customization parameters

| Parameter | Default | Controls |
|---|---|---|
| `blurRadius` | `22.dp` | blur strength (0.dp disables) |
| `backgroundOpacity` | `0.55f` | translucency of the tint over the blur |
| `cornerRadius` | `32.dp` | pill roundness |
| `activeContentColor` | `primary` | active icon + label color |
| `inactiveIconColor` | `onSurfaceVariant` | inactive icon color |
| `activeIndicatorColor` | active @ 14% | pill behind the active tab |
| `animationDurationMillis` | `420` | overall animation speed |
| `barHeight` | `64.dp` | bar height (keep ≥ 60.dp for 48.dp touch targets) |
| `itemSpacing` | `4.dp` | gap between items |
| `shadowElevation` | `12.dp` | soft floating shadow |
| `borderColor` | white | glass edge hairline color |

## How it works

**Blur.** `Modifier.glassSource` records the screen content into a Compose `GraphicsLayer` display list (no bitmap copies) while still drawing it normally. The bar re-draws just the slice of that layer behind itself into a second layer and applies a `RenderEffect` chain — blur + a slight saturation boost, which is what makes glass read as "glass". `RenderEffect` needs API 31, so below that (or with no `glassState`) the bar falls back to a translucent surface.

**Scroll sync.** Scrolling moves content inside its own render layer, which never re-runs the source's draw — so panels would show a stale blur. `rememberGlassSyncedScrollState()` (or `GlassBackdropState.invalidate()`) nudges panels to re-record on every scroll frame.

**Translucency & depth.** Three cheap layers sell the effect: the blurred backdrop, a tint at `backgroundOpacity`, and a 1dp gradient hairline (brightest at the top edge) that reads as a lit glass edge. A soft `shadow` with translucent ambient/spot colors floats the bar without heaviness.

**Animation.** Spatial motion (label expand, slide, scale) uses springs whose stiffness scales with `animationDurationMillis`, matching M3 Expressive's spatial-spring character. Non-spatial motion (fades, color shifts) uses tweens with the M3 emphasized curve `cubic-bezier(0.2, 0, 0, 1)`. Icons crossfade outlined↔filled via `AnimatedContent` with a gentle scale pop.

**Accessibility.** `selectableGroup()` container, `Modifier.selectable(role = Role.Tab, selected = …)` items (TalkBack announces "selected, Home, tab"), `contentDescription` on icon-only tabs, ≥ 48dp touch targets.

## Reusing the bar

```kotlin
val glassState = rememberGlassBackdropState()
var selected by rememberSaveable { mutableIntStateOf(0) }

Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalGlassBackdropState provides glassState) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource(glassState)
                .verticalScroll(rememberGlassSyncedScrollState()),
        ) { /* content */ }
    }

    FloatingGlassNavBar(
        items = listOf(
            FloatingNavItem("Home", Icons.Outlined.Home, Icons.Rounded.Home),
            FloatingNavItem("Inbox", Icons.Outlined.Inbox, Icons.Rounded.Inbox, badgeCount = 3),
        ),
        selectedIndex = selected,
        onItemSelected = { selected = it },
        glassState = glassState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
    )
}
```

Selection state is hoisted, so it plugs straight into Navigation Compose: drive `selectedIndex` from the back stack and navigate in `onItemSelected`.

## Privacy

Local-first by design: no `INTERNET` permission, SMS parsing is read-only and on-device, and sender rules are matched locally.
