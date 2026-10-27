package com.example.normpix.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/*
 * NormPix design tokens - the single source for spacing, shape, layout,
 * motion, and icon sizing. Colors and typography live in the MaterialTheme
 * (see Theme.kt); composables read those from the theme, never hard-code them.
 */

/** Blur behind sheets and dialogs, and the floating bars' glass. */
object AppFrostedTokens {
    /**
     * The blur of the page behind an open sheet or dialog, so it reads as soft colour, not as shapes. CSS `blur(24px)` is a
     * Gaussian with sigma 24px; an Android blur radius is about sqrt(3) x sigma, so the same blur here is about 40dp.
     */
    val blurRadius = 40.dp

    /**
     * How much of a sheet's or dialog's own colour lies over the blurred page; the rest of the page shows through it as soft
     * colour, as in the launcher's app drawer. Only where the page is blurred - over a sharp page a sheet is solid. The
     * glass controls floating over the top of a page are tinted as much.
     */
    val surfaceAlpha = 0.62f

    /** The dim over the blurred page around a sheet: slight, since the blur already sets the page back. */
    val scrimAlpha = 0.08f

    /** The blur behind the floating bars: they are small, and a lighter blur keeps the page's colours readable through them. */
    val chromeBlurRadius = 20.dp

    /** The blur of the footer zone under the dock: the page there is set well back, so the bars read over it. */
    val footerBlurRadius = 28.dp

    /** How far above the dock the footer's blur and fade begin: it eases in over this and the dock's own height. */
    val footerFade = 36.dp

    /** How much of the page's colour the footer's fade washes over the blur at its foot. */
    val footerWashAlpha = 0.72f

    /** Corner radius for sheet and dialog surfaces. */
    val cornerRadius = AppRadius.xxl
}


/** 4dp-based spacing scale. */
object AppSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val x4l = 40.dp
    val x5l = 48.dp

    /** Horizontal page gutter on compact windows. */
    val page = PaddingValues(horizontal = lg)
}

/**
 * Corner radii mapped onto the Material 3 shape scale. Pick by role, not by
 * look: the shape communicates what kind of surface something is.
 */
object AppRadius {
    /** Extra-small - inner corners of grouped list segments. */
    val xs = 4.dp

    /** Small - chips, tags, menus' inner items. */
    val sm = 8.dp

    /** Medium - text fields, menus, standalone list rows, small tiles. */
    val md = 12.dp

    /** Large - cards and card faces. */
    val lg = 16.dp

    /** Large-increased (M3 Expressive) - outer corners of grouped lists, the contextual status surface, snackbars. */
    val xl = 20.dp

    /** Extra-large - bottom sheets and dialogs. */
    val xxl = 28.dp

    val rXs = RoundedCornerShape(xs)
    val rSm = RoundedCornerShape(sm)
    val rMd = RoundedCornerShape(md)
    val rLg = RoundedCornerShape(lg)
    val rXl = RoundedCornerShape(xl)
    val rXxl = RoundedCornerShape(xxl)
    val rFull = RoundedCornerShape(CornerSize(50))
}

object AppIconSizes {
    val xs = 12.dp
    val sm = 16.dp
    val md = 20.dp
    val lg = 24.dp
    val xl = 32.dp
    val hero = 40.dp
}

/** Material 3 window size classes. */
enum class WindowSize {
    /** < 600dp: phones in portrait. Bottom navigation bar. */
    Compact,

    /** 600-839dp: foldables, small tablets, phones in landscape. Rail. */
    Medium,

    /** 840-1199dp: tablets in landscape, desktop. Rail, two columns. */
    Expanded,

    /** >= 1200dp: large desktop windows and tablets in landscape. Wide rail (labels beside the icons), when the window is tall enough. */
    Large;

    val isCompact: Boolean get() = this == Compact
    val usesRail: Boolean get() = this != Compact
    val isExpandedOrLarger: Boolean get() = this == Expanded || this == Large

    companion object {
        fun fromWidth(widthDp: Int): WindowSize = when {
            widthDp < 600 -> Compact
            widthDp < 840 -> Medium
            widthDp < 1200 -> Expanded
            else -> Large
        }
    }
}

/** The width of the window the app is in, in whole dp: what [WindowSize] is worked out from. */
@Composable
fun rememberWindowWidthDp(): Int {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val density = LocalDensity.current.density
    return remember(widthPx, density) { (widthPx / density).toInt() }
}

@Composable
fun rememberWindowSize(): WindowSize {
    val width = rememberWindowWidthDp()
    return remember(width) { WindowSize.fromWidth(width) }
}

/** The height of the window the app is in, in whole dp. */
@Composable
fun rememberWindowHeightDp(): Int {
    val heightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current.density
    return remember(heightPx, density) { (heightPx / density).toInt() }
}

/** What the app has beside its tabs: nothing (the floating dock is under them), the narrow rail, or the wide one with its labels beside the icons. */
enum class RailStyle {
    None,
    Compact,
    Wide,
    ;

    /** How much of the window's width it takes. */
    val width: Dp
        get() = when (this) {
            None -> 0.dp
            Compact -> AppLayout.railWidth
            Wide -> AppLayout.wideRailWidth
        }

    companion object {
        /**
         * The rail for a window [widthDp] by [heightDp]. A window under 600dp wide has the dock. The wide rail takes a Large window -
         * a tablet in landscape - as long as it is tall enough to hold its rows ([canBeWide]), unless the user has folded it down
         * ([collapsed]); a phone on its side, however wide, keeps the narrow one, which holds as much in less room.
         */
        fun of(widthDp: Int, heightDp: Int, collapsed: Boolean = false): RailStyle = when {
            WindowSize.fromWidth(widthDp) == WindowSize.Compact -> None
            canBeWide(widthDp, heightDp) && !collapsed -> Wide
            else -> Compact
        }

        /** Whether a window this size has room for the wide rail, and so for the button that folds it down and opens it again. */
        fun canBeWide(widthDp: Int, heightDp: Int): Boolean =
            WindowSize.fromWidth(widthDp) == WindowSize.Large && heightDp >= AppLayout.minRoomyHeight.value
    }
}

/** True while the user has folded the wide rail down to the narrow one (Settings holds it, see `AppPrefsState.railCollapsed`). */
val LocalRailCollapsed = compositionLocalOf { false }

@Composable
fun rememberRailStyle(): RailStyle {
    val width = rememberWindowWidthDp()
    val height = rememberWindowHeightDp()
    val collapsed = LocalRailCollapsed.current
    return remember(width, height, collapsed) { RailStyle.of(width, height, collapsed) }
}

/** Whether the window has room for the wide rail, folded down or not: the rail then has the button that folds it and opens it again. */
@Composable
fun rememberRailFoldable(): Boolean {
    val width = rememberWindowWidthDp()
    val height = rememberWindowHeightDp()
    return remember(width, height) { RailStyle.canBeWide(width, height) }
}

/**
 * Whether a screen that is a list of things that open pages (Settings) shows the list and the open page side by side: the window
 * is [WindowSize.Expanded] or wider - room for the list and a page beside it - and tall enough that it is not a phone on its side.
 */
fun isListDetail(widthDp: Int, heightDp: Int): Boolean =
    WindowSize.fromWidth(widthDp).isExpandedOrLarger && heightDp >= AppLayout.minRoomyHeight.value

@Composable
fun rememberListDetail(): Boolean {
    val width = rememberWindowWidthDp()
    val height = rememberWindowHeightDp()
    return remember(width, height) { isListDetail(width, height) }
}

/** The width the app's tabs have: the window's, less the rail beside them once the window is wide enough for one. */
@Composable
fun rememberTabRoom(): Dp {
    val width = rememberWindowWidthDp()
    val height = rememberWindowHeightDp()
    val collapsed = LocalRailCollapsed.current
    return remember(width, height, collapsed) { width.dp - RailStyle.of(width, height, collapsed).width }
}

object AppLayout {
    /** Max width for single-column reading content (lists, forms, settings). */
    val maxContentWidth = 720.dp

    /** Max width for a list that is one column on a big window (transactions): wider than a form, short of stretching a row. */
    val maxListWidth = 840.dp

    /** Max width for multi-column dashboards on large windows. */
    val maxWideContentWidth = 1200.dp

    /** The room (after the page's own padding) from which a dashboard page lays itself out in two columns. */
    val twoColumnMinWidth = 760.dp

    /** The navigation rail's width, on windows wide enough for it in place of the floating dock. */
    val railWidth = 96.dp

    /** The wide rail's width on a Large window: room for a label beside its icon, and for the longest of its add buttons. */
    val wideRailWidth = 256.dp

    /** The shortest window the wide rail and the list-beside-page Settings are for: under it (a phone on its side) neither fits its rows. */
    val minRoomyHeight = 480.dp

    /** The width of the list when Settings shows it beside the page that is open. */
    val listPaneWidth = 360.dp

    /** Bottom clearance under a tab that has the rail beside it: there is no dock or button over the list to scroll clear of. */
    val railBottomClearance = AppSpacing.xxl

    /** Bottom clearance so the last list item scrolls above the floating navbar. */
    val navClearance = 100.dp

    /** Bottom clearance so the last list item scrolls above a floating navbar and FAB. */
    val fabClearance = 168.dp

    /** Horizontal gutter for the given window size. */
    fun gutter(size: WindowSize): Dp = when (size) {
        WindowSize.Compact -> AppSpacing.lg
        WindowSize.Medium -> AppSpacing.xxl
        WindowSize.Expanded, WindowSize.Large -> AppSpacing.xxxl
    }
}

/** Motion tokens (M3 durations + easing) and reduced-motion handling. */
object AppMotion {
    const val SHORT_MS = 200
    const val MEDIUM_MS = 300
    const val LONG_MS = 500

    /** Elements entering the screen (M3 emphasized decelerate). */
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** Elements leaving the screen (M3 emphasized accelerate). */
    val exit: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    /** Elements changing size/shape/color in place (M3 emphasized). */
    val standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** [ms], or zero when the user turned animations off system-wide. */
    @Composable
    fun duration(ms: Int): Int = if (rememberReducedMotion()) 0 else ms

    /**
     * Material 3 Expressive "default spatial" spring: things that move or change shape settle with a
     * hint of overshoot, never a wobble. One spec for every such motion (tab indicator, selected
     * segment, the navigation bar leaving), so they feel related. Instant when animations are off.
     */
    @Composable
    fun <T> spatial(): FiniteAnimationSpec<T> =
        if (rememberReducedMotion()) snap() else spring(dampingRatio = 0.8f, stiffness = 380f)
}

/** True when the OS "remove animations" / animator-scale-0 setting is on. */
@Composable
fun rememberReducedMotion(): Boolean {
    val override = LocalReducedMotionOverride.current
    if (override != null) return override
    // Inside the app this is one value, kept up to date by [ProvideAppEnvironment]; elsewhere (a preview) it is read once.
    LocalSystemReducedMotion.current?.let { return it }
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { readReducedMotion(resolver) }
}

/** The system's reduced-motion state as the app last read it (see [rememberSystemReducedMotion]); null outside the app. */
val LocalSystemReducedMotion = compositionLocalOf<Boolean?> { null }

/**
 * Whether the system has animations removed, read now and again each time the app is resumed. The setting is changed in
 * system settings, which the user comes back from, so a change applies at once rather than after the app is next started -
 * until then every page and sheet would keep moving (or keep standing still) against what the phone was just told.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) { mutableStateOf(readReducedMotion(resolver)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduced = readReducedMotion(resolver) }
    return reduced
}

private fun readReducedMotion(resolver: android.content.ContentResolver): Boolean =
    runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)

/** Tests (and screenshot harnesses) can force reduced motion on or off. */
val LocalReducedMotionOverride = compositionLocalOf<Boolean?> { null }
