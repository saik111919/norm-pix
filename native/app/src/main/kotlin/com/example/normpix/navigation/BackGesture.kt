package com.example.normpix.navigation

import androidx.activity.BackEventCompat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
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

/** The page style whose way of closing the back gesture plays (the Back gesture style, see `BackGestureStyle.animation`). */
val LocalBackAnimation = staticCompositionLocalOf { PageAnimationType.LinuxWindow }

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
internal class BackGestureTransitions(private val type: PageAnimationType, totalMs: Int, reduced: Boolean) {
    private val ms = if (reduced) 0 else totalMs

    private fun <T> along(easing: Easing = BackGesture.ease) = tween<T>(ms, easing = easing)

    /** The page beneath, coming back: whole at all times, at most moved or receded a little ([BackPreview.revealed]). */
    fun enter(swipeEdge: Int): EnterTransition {
        val direction = BackGesture.directionOf(swipeEdge)
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
