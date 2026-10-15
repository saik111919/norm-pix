package com.example.normpix.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.navigation.NavBackStackEntry
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.ui.theme.AppFrostedTokens
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.rememberReducedMotion

/**
 * Route transitions for the user's [PageAnimationType] preference.
 *
 * Two rules hold for every style, because pages are opaque and a see-through page shows what is behind the app:
 * - Only the page on top moves and changes opacity as it comes or goes. The page under it stays whole - at most it
 *   shifts or recedes a little, never fades - so two pages are never half-visible over each other, and nothing the
 *   user chose to see (their theme's background) is ever replaced by the window's.
 * - The styles that do fade a page (fade through, shared axis) follow Material's sequencing: the page that leaves
 *   fades out first, then the page that arrives fades in, instead of both at once.
 *
 * - [PageAnimationType.System]: the platform's own feel. A page slides in from the edge a short way as it fades in
 *   while the one under it shifts back; going back (or previewing it with the back gesture) the top page shrinks
 *   and slides away, uncovering the page beneath.
 * - [PageAnimationType.SlideHorizontal]: the new page slides in from the side, over the old one, which shifts back.
 * - [PageAnimationType.FadeThrough]: the old page fades out, then the new one fades in and scales up gently.
 * - [PageAnimationType.SharedAxis]: the same, with both pages gliding a short distance along the horizontal axis.
 * - [PageAnimationType.ScaleAndFade]: the new page grows into place as it fades in, over the old one.
 * - [PageAnimationType.LinuxWindow]: a desktop window manager's open and close (GNOME's, near enough). A page opens by
 *   growing out of the bottom edge (where a dock would be) as it fades in, over a page that stays solid and recedes a
 *   little; it closes faster than it opened, shrinking back into itself (a little low, towards the dock) as it fades,
 *   while the page beneath comes forward. Its motion is eased the way a desktop's windows are ([WindowEasing]), not with
 *   Material's emphasized curve, which is three quarters done in its first fifth: a new page's first frames go to
 *   composing it, so on a phone the window had all but opened before it was ever drawn, and seemed not to move at all.
 */
class PageTransitions(type: PageAnimationType, reduced: Boolean) {
    private val duration = if (reduced) 0 else AppMotion.MEDIUM_MS

    /** The part of a fade-through in which the leaving page fades out; the arriving page fades in after it. */
    private val outMs = (duration * 0.3f).toInt()
    private val inMs = duration - outMs

    private fun <T> slide(easing: Easing = AppMotion.enter) = tween<T>(duration, easing = easing)

    private fun <T> fadeOutFirst() = tween<T>(outMs, easing = FastOutLinearInEasing)

    private fun <T> fadeInAfter() = tween<T>(inMs, delayMillis = outMs, easing = LinearOutSlowInEasing)

    private val forward: Pair<EnterTransition, ExitTransition>
    private val backward: Pair<EnterTransition, ExitTransition>

    init {
        when (type) {
            PageAnimationType.LinuxWindow -> {
                val open = if (reduced) 0 else WINDOW_OPEN_MS
                val close = if (reduced) 0 else WINDOW_CLOSE_MS
                val grow = tween<Float>(open, easing = WindowEasing.open)
                // Faded in over the first half, evenly: the window is seen growing, not arriving whole.
                val fadeInQuick = tween<Float>(open / 2, easing = LinearEasing)
                val recede = tween<Float>(open, easing = WindowEasing.open)
                val shrink = tween<Float>(close, easing = WindowEasing.open)
                // Fades late, so it is seen shrinking before it goes.
                val fadeOutLate = tween<Float>(close, easing = WindowEasing.close)
                val comeForward = tween<Float>(close, easing = WindowEasing.open)
                // The opening window grows from the bottom edge; the page under it only recedes, never fades.
                forward = (scaleIn(grow, initialScale = WINDOW_OPEN_SCALE, transformOrigin = FROM_BOTTOM) + fadeIn(fadeInQuick)) to
                    scaleOut(recede, targetScale = WINDOW_BEHIND_SCALE, transformOrigin = TransformOrigin.Center)
                // The closing window shrinks into itself as it fades; the page under it comes forward.
                backward = scaleIn(comeForward, initialScale = WINDOW_BEHIND_SCALE, transformOrigin = TransformOrigin.Center) to
                    (scaleOut(shrink, targetScale = WINDOW_CLOSE_SCALE, transformOrigin = CLOSING_ORIGIN) + fadeOut(fadeOutLate))
            }
            PageAnimationType.System -> {
                // In: slides a short way from the edge, fading in over the first part. The page under shifts back.
                forward = (slideInHorizontally(slide()) { it / SYSTEM_SLIDE } + fadeIn(tween(inMs, easing = LinearOutSlowInEasing))) to
                    slideOutHorizontally(slide()) { -it / SYSTEM_PARALLAX }
                // Out: the page shrinks and slides away (this is also what the back gesture previews as it is dragged),
                // while the page under comes back from where it shifted to.
                backward = slideInHorizontally(slide(AppMotion.standard)) { -it / SYSTEM_PARALLAX } to
                    (scaleOut(slide(AppMotion.standard), targetScale = SYSTEM_BACK_SCALE) +
                        slideOutHorizontally(slide(AppMotion.standard)) { it / SYSTEM_BACK_SLIDE } +
                        fadeOut(tween(duration, easing = FastOutLinearInEasing)))
            }
            PageAnimationType.SlideHorizontal -> {
                forward = slideInHorizontally(slide()) { it } to slideOutHorizontally(slide()) { -it / SLIDE_PARALLAX }
                backward = slideInHorizontally(slide(AppMotion.standard)) { -it / SLIDE_PARALLAX } to
                    slideOutHorizontally(slide(AppMotion.standard)) { it }
            }
            PageAnimationType.FadeThrough -> {
                val arrive = fadeIn(fadeInAfter()) + scaleIn(fadeInAfter(), initialScale = FADE_THROUGH_SCALE)
                forward = arrive to fadeOut(fadeOutFirst())
                backward = arrive to fadeOut(fadeOutFirst())
            }
            PageAnimationType.SharedAxis -> {
                forward = (slideInHorizontally(fadeInAfter()) { it / AXIS_SHIFT } + fadeIn(fadeInAfter())) to
                    (slideOutHorizontally(fadeOutFirst()) { -it / AXIS_SHIFT } + fadeOut(fadeOutFirst()))
                backward = (slideInHorizontally(fadeInAfter()) { -it / AXIS_SHIFT } + fadeIn(fadeInAfter())) to
                    (slideOutHorizontally(fadeOutFirst()) { it / AXIS_SHIFT } + fadeOut(fadeOutFirst()))
            }
            PageAnimationType.ScaleAndFade -> {
                // The new page grows over a page that stays (a fade to full opacity: nothing visible, but it keeps the page
                // composed for the whole transition); going back, the top page shrinks away revealing it.
                forward = (scaleIn(slide(), initialScale = GROW_SCALE) + fadeIn(slide())) to fadeOut(slide(), targetAlpha = 1f)
                backward = EnterTransition.None to (scaleOut(slide(AppMotion.standard), targetScale = GROW_SCALE) + fadeOut(slide(AppMotion.standard)))
            }
        }
    }

    /** How a page arrives as it is opened, for a screen that opens over the app outside the navigation graph (the picture viewer). */
    val opening: EnterTransition get() = forward.first

    /** How a page leaves as it is closed, likewise. */
    val closing: ExitTransition get() = backward.second

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { forward.first }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { forward.second }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = { backward.first }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = { backward.second }

    companion object {
        /** A window takes its time to open and is quicker to close, as on a desktop. */
        const val WINDOW_OPEN_MS = 340
        const val WINDOW_CLOSE_MS = 240
        private const val WINDOW_OPEN_SCALE = 0.8f
        private const val WINDOW_CLOSE_SCALE = 0.85f
        private const val WINDOW_BEHIND_SCALE = 0.94f

        // Divisors of the page width: how far a page travels (a larger divisor is a shorter way).
        private const val SYSTEM_SLIDE = 6
        private const val SYSTEM_PARALLAX = 8
        private const val SYSTEM_BACK_SLIDE = 10
        private const val SYSTEM_BACK_SCALE = 0.9f
        private const val SLIDE_PARALLAX = 4
        private const val AXIS_SHIFT = 14
        private const val FADE_THROUGH_SCALE = 0.92f
        private const val GROW_SCALE = 0.9f

        /** Windows grow from the bottom edge, where a dock would be... */
        private val FROM_BOTTOM = TransformOrigin(0.5f, 1f)

        /** ...and shrink into themselves, a little low: towards it. */
        private val CLOSING_ORIGIN = TransformOrigin(0.5f, 0.75f)
    }
}

/**
 * How a desktop's windows move ([PageAnimationType.LinuxWindow]): an ease-out quad, the curve GNOME opens and closes its
 * windows with - quick to start but spread over the whole time, so the motion is seen - and an ease-in quad for a window
 * fading as it closes, which keeps it in view while it shrinks.
 */
internal object WindowEasing {
    val open: Easing = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)
    val close: Easing = CubicBezierEasing(0.55f, 0.085f, 0.68f, 0.53f)
}

/** The user's chosen [PageAnimationType], for the pieces that move pages without the navigation graph (tab switches). */
val LocalPageAnimation = staticCompositionLocalOf { PageAnimationType.LinuxWindow }

/**
 * What a top-level tab does as it comes forward - the same family of motion as a pushed page in the user's
 * [PageAnimationType], scaled down because switching tabs is not opening a new page. (The tab that was in front
 * is gone at once, so only the entrance can be animated.) [progress] runs 0 -> 1 as the tab arrives.
 */
internal object TabMotion {
    /** How long a tab takes to come forward: a window's opening time in the window style, else a page's. */
    fun openMs(type: PageAnimationType): Int =
        if (type == PageAnimationType.LinuxWindow) PageTransitions.WINDOW_OPEN_MS else AppMotion.MEDIUM_MS

    /** How far into its motion a tab is, [time] (0..1, linear) into it: a window's own curve in the window style, else Material's. */
    fun ease(type: PageAnimationType, time: Float): Float =
        if (type == PageAnimationType.LinuxWindow) WindowEasing.open.transform(time) else AppMotion.enter.transform(time)

    fun scale(type: PageAnimationType, progress: Float): Float = when (type) {
        PageAnimationType.LinuxWindow -> 0.9f + 0.1f * progress
        PageAnimationType.ScaleAndFade -> 0.92f + 0.08f * progress
        PageAnimationType.FadeThrough -> 0.96f + 0.04f * progress
        PageAnimationType.System, PageAnimationType.SharedAxis, PageAnimationType.SlideHorizontal -> 1f
    }

    /**
     * How visible an arriving tab is, [time] (0..1, linear) into its arrival: a window fades in evenly over the first half
     * while it grows, as a page opens; a fade-through waits for the leaving tab to go first; the rest fade in as they move.
     */
    fun arrivalAlpha(type: PageAnimationType, time: Float): Float = when (type) {
        PageAnimationType.LinuxWindow -> (time / 0.5f).coerceIn(0f, 1f)
        PageAnimationType.FadeThrough, PageAnimationType.SharedAxis ->
            LinearOutSlowInEasing.transform(((time - 0.3f) / 0.7f).coerceIn(0f, 1f))
        else -> AppMotion.enter.transform(time.coerceIn(0f, 1f))
    }

    /** The tab going away under the arriving one: in the window style it recedes a little, as a page under a new one does. */
    fun departureScale(type: PageAnimationType, progress: Float): Float =
        if (type == PageAnimationType.LinuxWindow) 1f - 0.04f * progress else 1f

    /** Only the fading styles fade the tab going away, and first, before the new one fades in; the others keep it solid. */
    fun departureAlpha(type: PageAnimationType, time: Float): Float = when (type) {
        PageAnimationType.FadeThrough, PageAnimationType.SharedAxis -> 1f - FastOutLinearInEasing.transform((time / 0.3f).coerceIn(0f, 1f))
        else -> 1f
    }

    /** Whether the tab arrives sliding in from the side it sits on (the others grow in place). */
    fun slides(type: PageAnimationType): Boolean = when (type) {
        PageAnimationType.System, PageAnimationType.SharedAxis, PageAnimationType.SlideHorizontal -> true
        PageAnimationType.LinuxWindow, PageAnimationType.ScaleAndFade, PageAnimationType.FadeThrough -> false
    }

    fun origin(type: PageAnimationType): TransformOrigin =
        if (type == PageAnimationType.LinuxWindow) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
}

/** How round a page's corners are while it moves: a window's, the same as a sheet's. */
internal val PageCornerRadius = AppFrostedTokens.cornerRadius

/** How quickly a leaving page's corners round: well inside the shortest exit, so it never makes one last longer. */
private const val CORNERS_ROUND_MS = 90

/** How quickly an arriving page's corners square up: inside the shortest arrival, for the same reason. */
private const val CORNERS_SETTLE_MS = 180

/**
 * [composable], with the page drawn as a window while it moves ([PageWindow]).
 */
internal fun NavGraphBuilder.page(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry -> PageWindow { content(entry) } }

/**
 * A page drawn as a window while it moves: its corners round as soon as it starts to leave - or to be previewed by the
 * back gesture, which plays the same transition under the finger - and square up again as it settles, so a page that
 * shrinks, slides or grows never shows the hard corners of a cut-out rectangle. At rest it is a plain full-screen page.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun AnimatedVisibilityScope.PageWindow(content: @Composable () -> Unit) {
    val reduced = rememberReducedMotion()
    val radius by transition.animateDp(
        transitionSpec = {
            when {
                reduced -> snap<Dp>()
                targetState == EnterExitState.Visible -> tween<Dp>(CORNERS_SETTLE_MS, easing = AppMotion.standard)
                else -> tween<Dp>(CORNERS_ROUND_MS, easing = AppMotion.standard)
            }
        },
        label = "page_corners",
    ) { state -> if (state == EnterExitState.Visible) 0.dp else PageCornerRadius }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            val corner = radius.toPx()
            clip = corner > 0f
            shape = if (corner > 0f) RoundedCornerShape(corner) else RectangleShape
        },
    ) { content() }
}
