package com.example.normpix.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt

/**
 * The way a desktop window opens and closes, as everything the user can call a window in NormPix does it in the Linux window
 * style - a page, a sheet, a question - from one definition, so they cannot drift apart:
 *
 * - **Opening**: the window is a thin horizontal strip along the bottom edge, and it expands upward from it, its top edge rising
 *   until it is its full size. It is not scaled: its content stays the size and place it will have, and is uncovered from the
 *   bottom up - so nothing in it is ever squashed, and what is readable at rest is as readable on the way.
 * - **Closing** is the same the other way and quicker: the top edge sinks, shrinking the window downward back into the strip.
 * - **Easing** is a long soft landing for opening (quick away from the strip, slow to settle) and a smooth acceleration into
 *   the strip for closing - never a bounce.
 * - **Fading** is subtle: the strip is already there (about a third visible) and is fully so by the time the window is half
 *   open; closing, the window stays whole until it is nearly the strip and only then fades the last of itself away.
 *
 * Reduced motion: whoever applies this passes 0 ms (or does not apply it), and the window just appears and goes.
 */
internal object LinuxWindowMotion {
    /** A window takes its time to open and is quicker to close, as on a desktop. */
    const val OPEN_MS = 380
    const val CLOSE_MS = 260

    /** How much of the window's height is showing as the strip it opens from and closes into: a thin line, not nothing. */
    const val STRIP = 0.04f

    /** How visible the strip is: enough to be seen as a line before the window grows out of it. */
    const val STRIP_ALPHA = 0.35f

    /** How much of its opening a window takes to be fully visible. */
    const val FADE_IN_SHARE = 0.45f

    /** How far into its closing a window starts to fade away: it keeps its body until it is nearly the strip. */
    const val FADE_OUT_FROM = 0.55f

    /** Ease-out cubic: leaves the strip briskly, settles gently. */
    val openEase: Easing = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    /** Ease-in-out: leaves at once, is quickest in the middle, and lands softly in the strip - not an ease-in that sits still for its first fifth. */
    val closeEase: Easing = CubicBezierEasing(0.4f, 0.05f, 0.45f, 1f)

    /** How the last of a closing window fades: slowly at first, then gone. */
    private val fadeOutEase: Easing = CubicBezierEasing(0.4f, 0f, 0.8f, 0.6f)

    /** The share of the window's height showing [t] (0..1) of the way through opening: the strip, growing to all of it. */
    fun openReveal(t: Float): Float = STRIP + (1f - STRIP) * openEase.transform(t.coerceIn(0f, 1f))

    /** How visible the window is [t] (0..1) of the way through opening. */
    fun openAlpha(t: Float): Float = STRIP_ALPHA + (1f - STRIP_ALPHA) * (t / FADE_IN_SHARE).coerceIn(0f, 1f)

    /** The share of the window's height showing [p] (0..1) of the way through closing: all of it, sinking to the strip. */
    fun closeReveal(p: Float): Float = 1f - (1f - STRIP) * closeEase.transform(p.coerceIn(0f, 1f))

    /** How visible the window is [p] (0..1) of the way through closing: whole until [FADE_OUT_FROM], then gone by the end. */
    fun closeAlpha(p: Float): Float = 1f - fadeOutEase.transform(((p - FADE_OUT_FROM) / (1f - FADE_OUT_FROM)).coerceIn(0f, 1f))

    /**
     * A page opening, as a transition: the page's height grows from the strip, anchored at the bottom edge (the container it is
     * in must be aligned to its bottom - see the navigation host's `contentAlignment`), and it fades in over the first part.
     * [ms] is its duration.
     */
    fun opening(ms: Int): EnterTransition =
        expandVertically(tween(ms, easing = openEase), expandFrom = Alignment.Bottom) { full -> strip(full) } +
            fadeIn(tween((ms * FADE_IN_SHARE).roundToInt(), easing = LinearEasing), initialAlpha = STRIP_ALPHA)

    /** A page closing: its height sinks to the strip at the bottom edge, and the last of it fades away. [ms] is its duration. */
    fun closing(ms: Int): ExitTransition =
        shrinkVertically(tween(ms, easing = closeEase), shrinkTowards = Alignment.Bottom) { full -> strip(full) } +
            fadeOut(tween(ms, easing = fadeOutEase))

    private fun strip(full: Int): Int = (full * STRIP).roundToInt().coerceAtLeast(1)
}

/**
 * What is showing of a window that is opening from, or closing into, a strip at its bottom edge: [fraction] of its height, the
 * part nearest the bottom, with its top corners rounded by [topCorner] px (never by more than half of what is showing, so a
 * strip is a pill and not a pair of ears). Clip a window to it and the window looks to rise out of its bottom edge.
 */
@Immutable
internal class BottomRevealShape(private val fraction: Float, private val topCorner: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val shown = size.height * fraction.coerceIn(0f, 1f)
        val rect = Rect(0f, size.height - shown, size.width, size.height)
        val corner = topCorner.coerceIn(0f, shown / 2f)
        if (corner <= 0f) return Outline.Rectangle(rect)
        return Outline.Rounded(
            RoundRect(rect, topLeft = CornerRadius(corner), topRight = CornerRadius(corner), bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero),
        )
    }
}
