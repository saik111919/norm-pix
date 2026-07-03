# NormPix

Local-first Android app (Kotlin, Jetpack Compose, Material 3 Expressive). No INTERNET permission, no tracking — nothing leaves the device.

Includes **FloatingGlassNavBar**: a floating, glass-like bottom navigation bar. Active tab shows icon + label in a soft tinted pill; inactive tabs show icons only. Zero third-party dependencies.

## Run

1. Open this folder in Android Studio (File → Open).
2. Let Gradle sync (first sync downloads Gradle 8.9 + dependencies).
3. Press **Run** on an emulator or device — API 31+ shows the real backdrop blur; older devices get the translucent fallback by design.

If sync asks which Gradle to use: Settings → Build, Execution, Deployment → Build Tools → Gradle → "Use Gradle from: gradle-wrapper.properties file". For command-line builds, generate the wrapper jar once: run the `wrapper` task from Studio's Gradle panel (or `gradle wrapper` if Gradle is installed).

## Where things live

- `app/src/main/java/com/normpix/MainActivity.kt` — edge-to-edge setup + dynamic color theme
- `app/src/main/java/com/normpix/ui/floatingnav/`
  - `FloatingGlassNavBar.kt` — reusable component (`FloatingGlassNavBar`, `FloatingNavItem`)
  - `GlassBackdrop.kt` — blur machinery (`rememberGlassBackdropState`, `Modifier.glassSource`, `Modifier.glassPanel`)
  - `FloatingNavBarDemo.kt` — demo screen + preview

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
| `shadowElevation` | `8.dp` | soft floating shadow |

## How it works

**Blur.** `Modifier.glassSource` records the screen content into a Compose `GraphicsLayer` display list (no bitmap copies) while still drawing it normally. The bar re-draws just the slice of that layer behind itself into a second layer and applies a `RenderEffect` chain — blur + a slight saturation boost, which is what makes glass read as "glass". `RenderEffect` needs API 31, so below that (or with no `glassState`) the bar falls back to a translucent surface. Because the blur re-renders only the bar's own bounds, scrolling content stays blurred live at low cost.

**Translucency & depth.** Three cheap layers sell the effect: the blurred backdrop, a tint at `backgroundOpacity`, and a 1dp gradient hairline (white 30% → 5%) that reads as a lit glass edge. A soft `shadow` with translucent ambient/spot colors floats the bar without heaviness.

**Animation.** Spatial motion (label expand, slide, scale) uses springs whose stiffness scales with `animationDurationMillis`, matching M3 Expressive's spatial-spring character. Non-spatial motion (fades, color shifts) uses tweens with the M3 emphasized curve `cubic-bezier(0.2, 0, 0, 1)`. Labels animate via `AnimatedVisibility` (`fadeIn + expandHorizontally + slideInHorizontally + scaleIn`); icons crossfade outlined↔filled via `AnimatedContent` with a gentle scale pop.

**Accessibility.** `selectableGroup()` container, `Modifier.selectable(role = Role.Tab, selected = …)` items (TalkBack announces "selected, Home, tab"), `contentDescription` on icon-only tabs, ≥ 48dp touch targets. M3's stock `NavigationBar` wasn't used because it forces a full-width 80dp container — wrong shape for a floating pill — but all its semantics are preserved.

## Reusing the bar

```kotlin
val glassState = rememberGlassBackdropState()
var selected by rememberSaveable { mutableIntStateOf(0) }

Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().glassSource(glassState)) { /* content */ }

    FloatingGlassNavBar(
        items = listOf(FloatingNavItem("Home", Icons.Outlined.Home, Icons.Filled.Home), /* … */),
        selectedIndex = selected,
        onItemSelected = { selected = it },
        glassState = glassState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp),
    )
}
```

Selection state is hoisted, so it plugs straight into Navigation Compose: drive `selectedIndex` from the back stack and navigate in `onItemSelected`.
