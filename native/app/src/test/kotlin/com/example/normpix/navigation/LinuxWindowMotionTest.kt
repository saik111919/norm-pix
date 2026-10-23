package com.example.normpix.navigation

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A desktop window's open and close ([LinuxWindowMotion]): from a thin strip along the bottom edge up to the whole window and
 * back down into it, eased softly and faded subtly. These are the plain numbers every window in the app is drawn by.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinuxWindowMotionTest {
    private val steps = (0..40).map { it / 40f }

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) < 1e-4f, "$message: expected $expected, was $actual")

    @Test
    fun aWindowOpensFromAThinStripToTheWholeWindow() {
        assertNear(LinuxWindowMotion.STRIP, LinuxWindowMotion.openReveal(0f), "the strip it starts as")
        assertNear(1f, LinuxWindowMotion.openReveal(1f), "the whole window at the end")
        assertTrue(LinuxWindowMotion.STRIP in 0.01f..0.1f, "the strip is thin: ${LinuxWindowMotion.STRIP} of the window's height")
    }

    @Test
    fun aWindowClosesBackDownIntoTheSameStrip() {
        assertNear(1f, LinuxWindowMotion.closeReveal(0f), "whole when it starts to close")
        assertNear(LinuxWindowMotion.STRIP, LinuxWindowMotion.closeReveal(1f), "the strip it sinks into: the one it opened from")
    }

    @Test
    fun theTopEdgeOnlyEverRisesWhileOpeningAndOnlyEverSinksWhileClosing() {
        var last = 0f
        for (t in steps) {
            val reveal = LinuxWindowMotion.openReveal(t)
            assertTrue(reveal >= last - 1e-5f, "opening never goes back down ($t)")
            last = reveal
        }
        last = 1f
        for (p in steps) {
            val reveal = LinuxWindowMotion.closeReveal(p)
            assertTrue(reveal <= last + 1e-5f, "closing never comes back up ($p)")
            last = reveal
        }
    }

    @Test
    fun openingLeavesTheStripBrisklyAndSettlesGently() {
        // An ease-out: more than half way up in the first third of the time, and the last tenth of the time moves little.
        assertTrue(LinuxWindowMotion.openReveal(1f / 3f) > 0.5f, "brisk away from the strip")
        assertTrue(LinuxWindowMotion.openReveal(1f) - LinuxWindowMotion.openReveal(0.9f) < 0.05f, "a soft landing")
    }

    @Test
    fun closingStartsAtOnceAndLandsSoftlyInTheStrip() {
        // Not an ease-in that sits still: it has moved by a tenth of the way, and the last of the way is slow.
        assertTrue(1f - LinuxWindowMotion.closeReveal(0.1f) > 0.005f, "it has begun to sink by a tenth of the way")
        assertTrue(LinuxWindowMotion.closeReveal(0.9f) - LinuxWindowMotion.closeReveal(1f) < 0.1f, "slow into the strip")
    }

    @Test
    fun theFadeIsSubtle_theStripIsAlreadyThereAndTheWindowIsWholeByHalfOpen() {
        assertNear(LinuxWindowMotion.STRIP_ALPHA, LinuxWindowMotion.openAlpha(0f), "the strip is already visible")
        assertTrue(LinuxWindowMotion.STRIP_ALPHA in 0.2f..0.5f, "about a third")
        assertNear(1f, LinuxWindowMotion.openAlpha(LinuxWindowMotion.FADE_IN_SHARE), "fully visible by the end of its fade-in")
        assertNear(1f, LinuxWindowMotion.openAlpha(0.5f), "and whole by half way")
        var last = 0f
        for (t in steps) {
            val alpha = LinuxWindowMotion.openAlpha(t)
            assertTrue(alpha >= last - 1e-5f, "never fainter as it opens ($t)")
            last = alpha
        }
    }

    @Test
    fun closingKeepsItsBodyUntilItIsNearlyTheStripAndOnlyThenFadesTheLastOfIt() {
        assertNear(1f, LinuxWindowMotion.closeAlpha(0f), "whole when it starts to close")
        assertNear(1f, LinuxWindowMotion.closeAlpha(LinuxWindowMotion.FADE_OUT_FROM), "still whole where the fade starts")
        assertNear(0f, LinuxWindowMotion.closeAlpha(1f), "gone at the end")
        var last = 1f
        for (p in steps) {
            val alpha = LinuxWindowMotion.closeAlpha(p)
            assertTrue(alpha <= last + 1e-5f, "never brighter as it closes ($p)")
            last = alpha
        }
    }

    @Test
    fun aWindowClosesQuickerThanItOpens() {
        assertTrue(LinuxWindowMotion.CLOSE_MS < LinuxWindowMotion.OPEN_MS, "as on a desktop")
        assertEquals(LinuxWindowMotion.OPEN_MS, PageTransitions.WINDOW_OPEN_MS, "pages open for as long as the shared definition says")
        assertEquals(LinuxWindowMotion.CLOSE_MS, PageTransitions.WINDOW_CLOSE_MS)
    }

    @Test
    fun aTabRisesFromFurtherUpThanAPagesStrip_theSameMotionShorter() {
        assertNear(TabMotion.TAB_REVEAL_FROM, TabMotion.reveal(com.example.normpix.settings.PageAnimationType.LinuxWindow, 0f), "a tab starts part-way up")
        assertNear(1f, TabMotion.reveal(com.example.normpix.settings.PageAnimationType.LinuxWindow, 1f), "and arrives whole")
        assertTrue(TabMotion.TAB_REVEAL_FROM > LinuxWindowMotion.STRIP, "a tab is not a new window")
        for (other in com.example.normpix.settings.PageAnimationType.entries - com.example.normpix.settings.PageAnimationType.LinuxWindow) {
            assertNear(1f, TabMotion.reveal(other, 0f), "$other does not uncover anything: it is whole")
        }
    }

    // ------------------------------------------------------------ what shows of a window

    private fun outlineOf(fraction: Float, corner: Float = 0f): Outline =
        BottomRevealShape(fraction, corner).createOutline(Size(200f, 400f), LayoutDirection.Ltr, Density(1f))

    @Test
    fun theRevealIsThePartNearestTheBottomEdge() {
        val outline = outlineOf(0.25f) as Outline.Rectangle
        assertNear(0f, outline.rect.left, "full width")
        assertNear(200f, outline.rect.right, "full width")
        assertNear(300f, outline.rect.top, "a quarter of 400 up from the bottom")
        assertNear(400f, outline.rect.bottom, "anchored on the bottom edge")
    }

    @Test
    fun theRevealIsNothingBelowZeroAndAllOfItAboveOne() {
        assertNear(400f, (outlineOf(-1f) as Outline.Rectangle).rect.top, "nothing shows")
        assertNear(0f, (outlineOf(3f) as Outline.Rectangle).rect.top, "all of it shows")
    }

    @Test
    fun theTopCornersAreRoundAndTheBottomOnesAreNot() {
        val round = outlineOf(0.5f, corner = 30f) as Outline.Rounded
        assertNear(30f, round.roundRect.topLeftCornerRadius.x, "top left")
        assertNear(30f, round.roundRect.topRightCornerRadius.x, "top right")
        assertNear(0f, round.roundRect.bottomLeftCornerRadius.x, "bottom left: the window's own bottom edge")
        assertNear(0f, round.roundRect.bottomRightCornerRadius.x, "bottom right")
    }

    @Test
    fun aStripIsAPillAndNotAPairOfEars() {
        // 4% of 400 is 16 px: corners of 30 would be taller than the strip; they are no more than half of it.
        val strip = outlineOf(0.04f, corner = 30f) as Outline.Rounded
        assertNear(8f, strip.roundRect.topLeftCornerRadius.x, "no more than half of what is showing")
    }
}
