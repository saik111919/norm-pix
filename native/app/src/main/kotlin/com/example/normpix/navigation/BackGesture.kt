package com.example.normpix.navigation

import androidx.activity.BackEventCompat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.example.normpix.settings.BackGestureSpeed
import com.example.normpix.settings.PageAnimationType
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The back gesture, as one thing wherever it goes back (owner, 2026-11-11: "not consistent throughout the app", "it is fast"): a tab
 * to the tab before it, a pushed page to what it was opened from. Two rules, the same in both:
 *
 * - **The finger plays the start of the close, not all of it.** How far the close has gone is [eased] of how far the finger has: slow
 *   at first, so a short pull moves the page a little (a preview), and only a long drag takes it most of the way. Until 2026-11-11 the
 *   two were the same number, so an ordinary quick swipe played the whole close in the time the finger took, with nothing left to see.
 * - **Letting go plays the rest in what is left of one clock** ([finishMs] of the speed's whole time): a short pull leaves most of the
 *   time for it, a long drag little - there is little left to do. (It was a fixed 160 ms for a tab, and for a page whatever the
 *   navigation library's own default left: a card that vanished in a frame.)
 *
 * Which close is played is the Back gesture style ([LocalBackAnimation]); how long the clock is, the Back gesture speed
 * ([LocalBackGestureMs]). The tabs draw it themselves ([BackPreview], `AppShell`); a page is drawn by the navigation host, which is
 * given the same motion as transitions it seeks ([BackGestureTransitions]).
 */
internal object BackGesture {
    /** The power the finger's progress is raised to: 0.3 of the way is an eighth of the close, 0.5 a third, 0.8 two thirds. */
    private const val CURVE = 1.7f

    /** How far the close has gone when the gesture is [raw] (0..1) of the way. */
    fun eased(raw: Float): Float = raw.coerceIn(0f, 1f).pow(CURVE)

    /** [eased] as an easing, for an animation the navigation host seeks with the gesture's own progress. */
    val ease: Easing = Easing { eased(it) }

    /** How long the rest of the close takes once the finger lets go at [raw]: the share of [totalMs] it has not used. */
    fun finishMs(raw: Float, totalMs: Int): Int = ((1f - raw.coerceIn(0f, 1f)) * totalMs).roundToInt()

    /** 1 for a gesture from the left edge (or a back button), -1 from the right edge: the side a page moves towards. */
    fun directionOf(swipeEdge: Int): Float = BackPreview.directionOf(swipeEdge == BackEventCompat.EDGE_RIGHT)
}

/**
 * The navigation host with the app's motion in it: [pages] for a page opening and for one closed by a tap, [gesture] for one closed
 * by the back gesture. The gesture's two transitions must be given by name: left out, the library plays its own default for a
 * gesture (see [BackGestureTransitions]), whatever the app's page style - which is how a page and a tab came to go back differently.
 *
 * As each transition is asked for it tells [beneath] whether what follows is the gesture's, so the shell under a page draws itself
 * in the same style and on the same clock (it is asked before the shell's own entry composes).
 */
@Composable
internal fun AppNavHost(
    navController: NavHostController,
    startDestination: String,
    pages: PageTransitions,
    gesture: BackGestureTransitions,
    beneath: ShellBeneath,
    contentAlignment: Alignment,
    modifier: Modifier = Modifier,
    builder: NavGraphBuilder.() -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        contentAlignment = contentAlignment,
        enterTransition = {
            beneath.byGesture = false
            pages.enter(this)
        },
        exitTransition = {
            beneath.byGesture = false
            pages.exit(this)
        },
        popEnterTransition = {
            beneath.byGesture = false
            pages.popEnter(this)
        },
        popExitTransition = {
            beneath.byGesture = false
            pages.popExit(this)
        },
        predictivePopEnterTransition = { edge ->
            beneath.gestureFrom(edge)
            gesture.enter(edge)
        },
        predictivePopExitTransition = { edge ->
            beneath.gestureFrom(edge)
            gesture.exit(edge)
        },
        builder = builder,
    )
}

/**
 * Android's own back preview, as the "Android" Back gesture style draws it (owner, 2026-11-14: "the predictive back follows the system
 * back gesture for better feel"). Its numbers are the platform's (checked 2026-11-14, `verified-facts.md`): Android's predictive-back
 * design page (the surface scales to 90%, drifts ((width / 20) - 8) dp towards the finger, and the gesture's progress goes through a
 * *decelerating* curve "so that the gesture is more apparent in the beginning") and AOSP's cross-activity animation (the screen
 * beneath waits at 90% and 96 dp to the side, the one left fades within the first fifth of the commit, the commit takes 450 ms on
 * the emphasized curve). Two things set it apart from the page styles' closes:
 *
 * - **It moves most at the start.** The page styles ease the start in ([BackGesture.eased], raw^1.7) so a short pull is a small
 *   preview of their close - on top of the platform's own easing of the finger's progress, which made it feel late. This one is the
 *   finger's progress as it comes ([follow]), through the decelerating curve, so the page answers the first pixel of the swipe.
 * - **Its first half is the preview and its second the commit**, on the one timeline the navigation host seeks with the finger:
 *   up to [PREVIEW_BY] the page shrinks to [SCALE], rounds and lifts, and drifts [DRIFT] of its width towards the finger, while the
 *   screen beneath waits at [SCALE], [ENTER_OFFSET] of the width off to the other side. Past it - letting go, or a finger that keeps
 *   going, as the design page's own fade-through does at 35% - the page fades out at once and the screen beneath comes forward to
 *   full size. Its clock is [TIME] times the Back gesture speed, so the commit takes Android's 450 ms at Standard.
 */
internal object AndroidBack {
    /** How small what is left gets while the finger holds it, and how small what it goes back to waits. */
    const val SCALE = 0.9f

    /** How far into the timeline (0..1) the preview is done: the rest is the commit. */
    const val PREVIEW_BY = 0.5f

    /** How far, as a share of its width, it drifts towards the finger: ((411 / 20) - 8) dp of a 411 dp phone. */
    const val DRIFT = 0.03f

    /** How far, as a share of the width, what it goes back to waits off to the other side: 96 dp of a 411 dp phone. */
    const val ENTER_OFFSET = 0.23f

    /** The share of the commit over which the page left fades out (AOSP: alpha = 1 - 5 x progress). */
    private const val FADE_SHARE = 0.2f

    /** The clock of this style against the Back gesture speed's: the commit, half of it, is then 450 ms at Standard (420 ms). */
    const val TIME = 2.15f

    /** The whole clock of a back gesture in this style, for a Back gesture speed of [speedMs] (see [TIME]); [speedMs] in the others. */
    fun clockMs(speedMs: Int, android: Boolean): Int = if (android) (speedMs * TIME).roundToInt() else speedMs

    /** The finger's progress drives the timeline as it comes: the platform has already eased it. */
    fun follow(raw: Float): Float = raw.coerceIn(0f, 1f)

    val ease: Easing = Easing { follow(it) }

    /** Android's decelerating curve for back previews (the design page's `PathInterpolator(0f, 0f, 0f, 1f)`). */
    private val Decelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Material's emphasized curve, which the platform's commit runs on. */
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** How much of the preview is shown at [c] of the timeline: most of it early, all of it by [PREVIEW_BY]. */
    private fun previewed(c: Float): Float = Decelerate.transform((c / PREVIEW_BY).coerceIn(0f, 1f))

    /** How far into the commit [c] of the timeline is (0 all through the preview). */
    private fun committed(c: Float): Float = ((c - PREVIEW_BY) / (1f - PREVIEW_BY)).coerceIn(0f, 1f)

    /** How much of its way out a sheet is drawn down while the finger is [raw] of the way: the decelerating preview, up to [SHEET_SHARE]. */
    fun sheet(raw: Float): Float = previewed(follow(raw) * PREVIEW_BY) * SHEET_SHARE

    /** The most of its way out a sheet goes while the finger holds it; letting go closes it from there. */
    const val SHEET_SHARE = 0.4f

    fun leaving(progress: Float, direction: Float): BackPose {
        val c = progress.coerceIn(0f, 1f)
        val p = previewed(c)
        return BackPose(
            scale = 1f + (SCALE - 1f) * p,
            alpha = 1f - (committed(c) / FADE_SHARE).coerceIn(0f, 1f),
            shiftX = direction * DRIFT * p,
            corners = p,
            lift = p,
        )
    }

    fun revealed(progress: Float, direction: Float): BackPose {
        val forward = Emphasized.transform(committed(progress.coerceIn(0f, 1f)))
        return BackPose(scale = SCALE + (1f - SCALE) * forward, shiftX = -direction * ENTER_OFFSET * (1f - forward))
    }

    // The same motion as transitions the navigation host seeks, each part on its own curve over the one timeline.

    fun exit(ms: Int, direction: Float): ExitTransition =
        scaleOut(tween(ms, easing = Easing { previewed(it) }), targetScale = SCALE) +
            slideOutHorizontally(tween(ms, easing = Easing { previewed(it) })) { (direction * it * DRIFT).roundToInt() } +
            fadeOut(tween(ms, easing = Easing { (committed(it) / FADE_SHARE).coerceIn(0f, 1f) }))

    fun enter(ms: Int, direction: Float): EnterTransition {
        val forward = Easing { Emphasized.transform(committed(it)) }
        return scaleIn(tween(ms, easing = forward), initialScale = SCALE) +
            slideInHorizontally(tween(ms, easing = forward)) { (-direction * it * ENTER_OFFSET).roundToInt() }
    }
}

/** The page style whose way of closing the back gesture plays (the Back gesture style, see `BackGestureStyle.animation`). */
val LocalBackAnimation = staticCompositionLocalOf { PageAnimationType.LinuxWindow }

/** Whether the back gesture is Android's own preview (the Back gesture style "Android", see [AndroidBack]) rather than a page style's close. */
val LocalAndroidBack = staticCompositionLocalOf { false }

/**
 * How the back gesture is drawn, wherever it goes back: [type]'s close, the finger playing the start of it ([BackGesture]), or - with
 * [android] - Android's own preview ([AndroidBack]), the finger moving it as it moves. Everything that draws the gesture asks this,
 * so a tab, a page, a step inside a screen and the tour agree.
 */
@Immutable
internal class BackLook(val type: PageAnimationType, val android: Boolean) {
    /** How far the close has gone when the finger is [raw] (0..1) of the way. */
    fun eased(raw: Float): Float = if (android) AndroidBack.follow(raw) else BackGesture.eased(raw)

    /** [eased] as an easing, for a transition the navigation host seeks with the gesture's own progress. */
    val ease: Easing get() = if (android) AndroidBack.ease else BackGesture.ease

    /** What is left, [progress] (already [eased]) of the way through its close. */
    fun leaving(progress: Float, direction: Float): BackPose =
        if (android) AndroidBack.leaving(progress, direction) else BackPreview.leaving(type, progress, direction)

    /** What it goes back to, beneath, at the same point. */
    fun revealed(progress: Float, direction: Float): BackPose =
        if (android) AndroidBack.revealed(progress, direction) else BackPreview.revealed(type, progress, direction)
}

/** The back gesture's look, from the Back gesture style ([LocalBackAnimation], [LocalAndroidBack]). */
@Composable
internal fun rememberBackLook(): BackLook {
    val type = LocalBackAnimation.current
    val android = LocalAndroidBack.current
    return remember(type, android) { BackLook(type, android) }
}

/** How long a back gesture's close takes from start to end (the Back gesture speed), before reduced motion makes it nothing. */
val LocalBackGestureMs = staticCompositionLocalOf { BackGestureSpeed.Standard.ms }

/**
 * What the navigation host plays while the back gesture is dragged over a pushed page, and goes on playing when it is let go: the
 * page on top closing as [type] closes a page ([exit]) over the page beneath ([enter]) - the motions of [BackPreview], which the tabs
 * draw by hand, as transitions. The host seeks them to the finger's progress, so each is [totalMs] long and eased by
 * [BackGesture.ease]: at the finger's 40% the page is where [BackGesture.eased] of 40% puts it, exactly as a tab is.
 *
 * (Without these the host uses the library's own default for a gesture - the page shrinking to 0.7 as a card, then gone in a frame -
 * whatever the app's page style: that is how pages and tabs came to differ. navigation-compose 2.10, `DefaultNavTransitions`.)
 */
internal class BackGestureTransitions(
    private val type: PageAnimationType,
    totalMs: Int,
    reduced: Boolean,
    /** Android's own preview ([AndroidBack]) instead of [type]'s close: the "Android" Back gesture style. */
    private val android: Boolean = false,
) {
    private val ms = if (reduced) 0 else totalMs

    private fun <T> along(easing: Easing = BackGesture.ease) = tween<T>(ms, easing = easing)

    /** The page beneath, coming back: whole at all times, at most moved or receded a little ([BackPreview.revealed]). */
    fun enter(swipeEdge: Int): EnterTransition {
        val direction = BackGesture.directionOf(swipeEdge)
        if (android) return AndroidBack.enter(ms, direction)
        return when (type) {
            PageAnimationType.LinuxWindow -> scaleIn(
                along { LinuxWindowMotion.openEase.transform(BackGesture.eased(it)) },
                initialScale = PageTransitions.WINDOW_BEHIND_SCALE,
            )
            PageAnimationType.System -> slideInHorizontally(along()) { (-direction * it / PageTransitions.SYSTEM_PARALLAX).roundToInt() }
            PageAnimationType.SlideHorizontal -> slideInHorizontally(along()) { (-direction * it / PageTransitions.SLIDE_PARALLAX).roundToInt() }
            PageAnimationType.FadeThrough -> scaleIn(along(), initialScale = PageTransitions.FADE_THROUGH_BACK_SCALE)
            PageAnimationType.SharedAxis -> slideInHorizontally(along()) { (-direction * it / PageTransitions.AXIS_SHIFT).roundToInt() }
            PageAnimationType.ScaleAndFade -> EnterTransition.None
        }
    }

    /** The page on top, going: [BackPreview.leaving], moved towards the side the finger is on where the style moves it sideways. */
    fun exit(swipeEdge: Int): ExitTransition {
        val direction = BackGesture.directionOf(swipeEdge)
        if (android) return AndroidBack.exit(ms, direction)
        return when (type) {
            PageAnimationType.LinuxWindow -> LinuxWindowMotion.closingUnder(ms, BackGesture.ease)
            PageAnimationType.System ->
                scaleOut(along(), targetScale = PageTransitions.SYSTEM_BACK_SCALE) +
                    slideOutHorizontally(along()) { (direction * it / PageTransitions.SYSTEM_BACK_SLIDE).roundToInt() } +
                    fadeOut(along { FastOutLinearInEasing.transform(BackGesture.eased(it)) })
            PageAnimationType.SlideHorizontal -> slideOutHorizontally(along()) { (direction * it).roundToInt() }
            PageAnimationType.FadeThrough -> fadeOut(along())
            PageAnimationType.SharedAxis ->
                slideOutHorizontally(along()) { (direction * it / PageTransitions.AXIS_SHIFT).roundToInt() } + fadeOut(along())
            PageAnimationType.ScaleAndFade -> scaleOut(along(), targetScale = PageTransitions.GROW_SCALE) + fadeOut(along())
        }
    }
}
