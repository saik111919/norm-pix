package com.example.normpix.navigation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The navigation bar leaves on a real downward scroll, returns on an upward one, and ignores wobble. */
class ScrollAwareBarStateTest {
    private val threshold = 16f
    private val state = ScrollAwareBarState(threshold)

    /** A finger drag: [dy] is negative when scrolling down the list (content moves up). */
    private fun drag(dy: Float, source: NestedScrollSource = NestedScrollSource.UserInput): Offset =
        state.connection.onPreScroll(Offset(0f, dy), source)

    @Test
    fun startsVisible_andScrollingDownPastTheThresholdHidesIt() {
        assertFalse(state.hidden)
        drag(-10f)
        assertFalse(state.hidden, "a short scroll is not enough")
        drag(-10f)
        assertTrue(state.hidden)
    }

    @Test
    fun anyUpwardScrollPastTheThresholdBringsItBack() {
        drag(-40f)
        assertTrue(state.hidden)
        drag(10f)
        assertTrue(state.hidden, "needs a real upward scroll, not a twitch")
        drag(10f)
        assertFalse(state.hidden)
    }

    @Test
    fun wobbleNeverToggles() {
        repeat(20) { i -> drag(if (i % 2 == 0) -9f else 9f) }
        assertFalse(state.hidden)
        // ...and a wobble around a hidden bar doesn't bring it back either.
        drag(-40f)
        repeat(20) { i -> drag(if (i % 2 == 0) 9f else -9f) }
        assertTrue(state.hidden)
    }

    @Test
    fun aChangeOfDirectionStartsTheCountAgain() {
        drag(-12f)
        drag(12f) // direction change: the 12 already travelled down no longer counts
        drag(-12f)
        assertFalse(state.hidden, "12 + 12 down with a reversal in between is still under the threshold")
    }

    @Test
    fun flingsDontDecide_onlyTheFingerDoes() {
        drag(-100f, NestedScrollSource.SideEffect)
        assertFalse(state.hidden)
    }

    @Test
    fun show_bringsItBackAndForgetsTheTravel() {
        drag(-40f)
        assertTrue(state.hidden)
        state.show()
        assertFalse(state.hidden)
        drag(-10f)
        assertFalse(state.hidden, "the count starts from zero after show()")
    }

    @Test
    fun itOnlyListens_neverConsumesScroll() {
        assertEquals(Offset.Zero, drag(-40f))
        assertEquals(Offset.Zero, drag(40f))
        assertEquals(Offset.Zero, drag(0f))
    }
}
