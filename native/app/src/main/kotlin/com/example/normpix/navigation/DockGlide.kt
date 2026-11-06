package com.example.normpix.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates

/*
 * The dock's "glide" (Settings > Appearance > Transitions > Dock), after the way DropSauce's floating bar moves its selection: one pill
 * that travels from the tab you leave to the tab you open, on a long soft curve, instead of one pill fading out as another fades in.
 * Its timings are DropSauce's (a tap takes 350 ms, a little more for each tab it crosses, 400 at most); the code is this app's own. The
 * pill is drawn once, behind the items, from where they are at that frame - they open and close their labels as it goes - and its two
 * edges do not travel together: the edge it moves towards runs ahead and the other follows, so it stretches in flight.
 */
internal object DockGlide {
    /** A tap on the next tab takes this long; each tab it crosses beyond that adds [PER_TAB_MS], up to [MAX_MS]. */
    const val MIN_MS = 350
    const val PER_TAB_MS = 16
    const val MAX_MS = 400

    /** An S-curve with no start-up jolt (it leaves rest gently) and a long settle: what the pill moves on. */
    val curve: Easing = CubicBezierEasing(0.32f, 0f, 0.14f, 1f)

    /** The leading edge has arrived by this share of the flight; the trailing edge starts [FOLLOW_AT] of it late. */
    private const val LEAD_END = 0.82f
    private const val FOLLOW_AT = 0.1f

    /** How long a glide across [tabs] tabs takes (1 for the next one). */
    fun durationMs(tabs: Int): Int = (MIN_MS + PER_TAB_MS * (tabs - 1).coerceAtLeast(0)).coerceAtMost(MAX_MS)

    /**
     * The pill's left and right edges [progress] (0..1, linear in time) of the way from where it was ([startLeft], [startRight]) to
     * the tab it is going to ([endLeft], [endRight]) - which may itself still be growing or shrinking as it goes, so it is read
     * fresh at every frame. The edge on the side it is moving towards runs ahead of the other, so the pill is longest half way.
     */
    fun edges(startLeft: Float, startRight: Float, endLeft: Float, endRight: Float, progress: Float): Pair<Float, Float> {
        if (progress >= 1f) return endLeft to endRight
        val ahead = curve.transform((progress / LEAD_END).coerceIn(0f, 1f))
        val behind = curve.transform(((progress - FOLLOW_AT) / (1f - FOLLOW_AT)).coerceIn(0f, 1f))
        val movingRight = endLeft + endRight >= startLeft + startRight
        val leftBy = if (movingRight) behind else ahead
        val rightBy = if (movingRight) ahead else behind
        return (startLeft + (endLeft - startLeft) * leftBy) to (startRight + (endRight - startRight) * rightBy)
    }
}

/**
 * Where the dock's items are, in the space the row draws in, as the last layout left them. The pill is drawn from this, so it is
 * where the tabs are at that frame. Each item is measured against the row's own coordinates (not its parent's: an item sits in a
 * tooltip's wrapper, where it is always at 0), and when it is asked for, not when it reports, so the order the layout calls
 * back in does not matter. [tick] moves whenever an item moves or resizes, for a draw that reads it.
 */
@Stable
internal class DockGeometry(count: Int) {
    /** The row the pill is drawn in: its coordinates are where every item's place is measured. */
    var row: LayoutCoordinates? = null

    private val items = arrayOfNulls<LayoutCoordinates>(count)

    var tick by mutableIntStateOf(0)
        private set

    fun place(index: Int, coordinates: LayoutCoordinates) {
        if (index !in items.indices) return
        items[index] = coordinates
        tick++
    }

    private fun at(index: Int): LayoutCoordinates? {
        val item = items.getOrNull(index)?.takeIf { it.isAttached } ?: return null
        return item.takeIf { row?.isAttached == true }
    }

    fun isKnown(index: Int) = at(index) != null

    fun left(index: Int): Float = row!!.localPositionOf(at(index)!!, Offset.Zero).x

    fun right(index: Int): Float = left(index) + at(index)!!.size.width

    /** The top of the items, and their height: they are all as tall as each other. */
    fun top(index: Int): Float = row!!.localPositionOf(at(index)!!, Offset.Zero).y

    fun height(index: Int): Float = at(index)!!.size.height.toFloat()
}
