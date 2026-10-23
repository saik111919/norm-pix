package com.example.normpix.ui

import com.example.normpix.navigation.LinuxWindowMotion
import com.example.normpix.settings.SheetAnimationType
import com.example.normpix.ui.components.SheetPose
import com.example.normpix.ui.components.sheetLag
import com.example.normpix.ui.components.sheetPose
import com.example.normpix.ui.components.sheetRiseShare
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * How a sheet or a decision is drawn at each point of its rise, in each [SheetAnimationType]. The pose is a plain function of
 * where Material has the sheet, so it follows a finger dragging it exactly; these are the numbers for a window 1000 px tall
 * and a sheet 400 px tall, which rests with its top at 600 and is hidden with its top at 1000.
 */
class SheetMotionTest {
    private val window = 1000f
    private val height = 400f
    private val hidden = 1000f
    private val half = 800f
    private val open = 600f
    private val tops = (0..20).map { hidden - (hidden - open) * it / 20f }

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) < 1e-4f, "$message: expected $expected, was $actual")

    private fun pose(style: SheetAnimationType, top: Float) = sheetPose(style, window, top, height)

    @Test
    fun theRiseIsHowFarUpTheSheetIs() {
        assertNear(0f, sheetRiseShare(window, hidden, height), "hidden")
        assertNear(0.5f, sheetRiseShare(window, half, height), "half way")
        assertNear(1f, sheetRiseShare(window, open, height), "open")
        assertNear(1f, sheetRiseShare(window, 500f, height), "dragged higher than it rests")
        assertNear(0f, sheetRiseShare(window, 1200f, height), "below the window")
    }

    @Test
    fun aTallSheetIsUpOnceItCoversHalfTheWindow() {
        // 900 px tall in a 1000 px window: it rises through half of the window, and is fully up there.
        assertNear(0.5f, sheetRiseShare(window, 750f, 900f), "a quarter of the window up")
        assertNear(1f, sheetRiseShare(window, 500f, 900f), "half the window up")
    }

    @Test
    fun theLagIsHowFarBelowItsPlaceTheSheetIs() {
        assertNear(400f, sheetLag(window, hidden, height), "hidden: all of it")
        assertNear(200f, sheetLag(window, half, height), "half way")
        assertNear(0f, sheetLag(window, open, height), "open")
        assertNear(0f, sheetLag(window, 500f, height), "dragged higher: no more than none")
    }

    @Test
    fun slideIsMaterialsOwnSheetUntouched() {
        for (top in tops) assertSame(SheetPose.Rest, pose(SheetAnimationType.Slide, top), "top $top")
    }

    @Test
    fun aSheetWithNoHeightIsLeftAlone() {
        for (style in SheetAnimationType.entries) assertSame(SheetPose.Rest, sheetPose(style, window, hidden, 0f), "$style")
    }

    @Test
    fun windowGrowsOutOfTheBottomEdgeAsItRises() {
        val hiddenPose = pose(SheetAnimationType.Window, hidden)
        assertNear(0.85f, hiddenPose.scale, "hidden scale")
        assertNear(0f, hiddenPose.alpha, "hidden alpha")

        val halfPose = pose(SheetAnimationType.Window, half)
        assertNear(0.925f, halfPose.scale, "half way scale")
        assertNear(1f, halfPose.alpha, "quick to fade in: fully visible half way up")

        val openPose = pose(SheetAnimationType.Window, open)
        assertNear(1f, openPose.scale, "open scale")
        assertNear(1f, openPose.alpha, "open alpha")

        // It still rises (not held), and pivots on its own bottom edge, wherever that is: (top + height) / height.
        for (top in tops) {
            val p = pose(SheetAnimationType.Window, top)
            assertNear(0f, p.translationY, "window is not held at $top")
            assertNear((top + height) / height, p.pivotY, "pivot at $top")
        }
        assertNear(0.5f, pose(SheetAnimationType.Window, 900f).alpha, "a quarter up: half visible")
    }

    @Test
    fun fadeAndScaleHoldTheSheetWhereItRests() {
        // Drawn at its top, then moved by the pose: always at the resting place.
        val held = listOf(SheetAnimationType.Fade, SheetAnimationType.Scale, SheetAnimationType.LinuxWindow)
        for (style in held) {
            for (top in tops) assertNear(open, top + pose(style, top).translationY, "$style at $top")
            for (top in tops) assertNear(open, top + sheetPose(style, window, top, height, closing = true).translationY, "$style closing at $top")
        }
        // Dragged above its resting place the sheet follows the finger.
        for (style in held) {
            assertNear(0f, pose(style, 550f).translationY, "$style dragged higher")
        }
    }

    @Test
    fun fadeOnlyFades() {
        val hiddenPose = pose(SheetAnimationType.Fade, hidden)
        assertNear(0f, hiddenPose.alpha, "hidden")
        assertNear(1f, hiddenPose.scale, "never scaled")
        assertNear(0.5f, pose(SheetAnimationType.Fade, half).alpha, "half way")
        assertNear(1f, pose(SheetAnimationType.Fade, open).alpha, "open")
        for (top in tops) assertNear(1f, pose(SheetAnimationType.Fade, top).scale, "scale at $top")
    }

    @Test
    fun scaleGrowsFromTheBottomEdgeWhileItFadesIn() {
        assertNear(0.9f, pose(SheetAnimationType.Scale, hidden).scale, "hidden scale")
        assertNear(0f, pose(SheetAnimationType.Scale, hidden).alpha, "hidden alpha")
        assertNear(0.95f, pose(SheetAnimationType.Scale, half).scale, "half way scale")
        assertNear(0.5f / 0.6f, pose(SheetAnimationType.Scale, half).alpha, "half way alpha: visible a little before it is up")
        assertNear(1f, pose(SheetAnimationType.Scale, open).scale, "open scale")
        assertNear(1f, pose(SheetAnimationType.Scale, open).alpha, "open alpha")
        // The pivot is the bottom of the sheet where it is held: its resting bottom, in the sheet's own height.
        for (top in tops) assertNear((top + height) / height, pose(SheetAnimationType.Scale, top).pivotY, "pivot at $top")
    }

    @Test
    fun linuxWindowOpensFromAStripAtItsBottomEdgeUpIntoTheWholeSheet_heldWhereItRests() {
        val hiddenPose = pose(SheetAnimationType.LinuxWindow, hidden)
        assertNear(LinuxWindowMotion.STRIP, hiddenPose.reveal, "hidden: just the strip")
        assertNear(0f, hiddenPose.alpha, "hidden: nothing is drawn before the sheet has started to come")

        // Within the first few percent of its rise the strip is there to be seen; it never fades the sheet by scaling it.
        val started = pose(SheetAnimationType.LinuxWindow, hidden - (hidden - open) * 0.06f)
        assertNear(LinuxWindowMotion.openAlpha(0.06f), started.alpha, "the strip appears")

        val halfPose = pose(SheetAnimationType.LinuxWindow, half)
        assertNear(LinuxWindowMotion.openReveal(0.5f), halfPose.reveal, "half way up: eased, not a straight line of the rise")
        assertTrue(halfPose.reveal > 0.75f, "an ease-out is most of the way up by half the rise: ${halfPose.reveal}")
        assertNear(1f, halfPose.alpha, "whole by half way")

        val openPose = pose(SheetAnimationType.LinuxWindow, open)
        assertNear(1f, openPose.reveal, "open: all of it")
        assertNear(1f, openPose.alpha, "open alpha")

        // Not scaled and not risen: held where it rests, its content the size and place it will have.
        for (top in tops) {
            val p = pose(SheetAnimationType.LinuxWindow, top)
            assertNear(1f, p.scale, "never scaled, at $top")
            assertNear(open, top + p.translationY, "held at $top")
        }
    }

    @Test
    fun linuxWindowClosesSinkingDownIntoTheStripAndFadesTheLastOfItAtTheEnd() {
        fun closing(top: Float) = sheetPose(SheetAnimationType.LinuxWindow, window, top, height, closing = true)

        assertNear(LinuxWindowMotion.STRIP, closing(hidden).reveal, "gone: just the strip, the one it opened from")
        assertNear(0f, closing(hidden).alpha, "gone: and the strip has faded")
        assertNear(1f, closing(open).reveal, "open: all of it")
        assertNear(1f, closing(open).alpha, "open alpha")

        // Half way down it is a smaller window than it was, and still whole: it fades only when it is nearly the strip.
        val halfPose = closing(half)
        assertNear(LinuxWindowMotion.closeReveal(0.5f), halfPose.reveal, "half way down")
        assertTrue(halfPose.reveal < 0.8f && halfPose.reveal > LinuxWindowMotion.STRIP, "part-way: ${halfPose.reveal}")
        assertNear(1f, halfPose.alpha, "not faded half way down")

        for (top in tops) {
            assertNear(1f, closing(top).scale, "never scaled, at $top")
            assertNear(open, top + closing(top).translationY, "held at $top")
        }
    }

    @Test
    fun linuxWindowGoesAwayDifferentlyFromHowItCame() {
        val opening = pose(SheetAnimationType.LinuxWindow, half)
        val closing = sheetPose(SheetAnimationType.LinuxWindow, window, half, height, closing = true)
        assertTrue(opening.reveal > closing.reveal + 0.1f, "same place, a different height showing: ${opening.reveal} against ${closing.reveal}")
    }

    @Test
    fun linuxWindowTopEdgeNeverSinksAsItComesUp_andNeverRisesAsItGoesDown() {
        var lastReveal = 1f
        var lastAlpha = 1f
        for (top in tops.reversed()) {
            val p = sheetPose(SheetAnimationType.LinuxWindow, window, top, height, closing = true)
            assertTrue(p.reveal <= lastReveal + 1e-4f && p.alpha <= lastAlpha + 1e-4f, "going down at $top: ${p.reveal}, ${p.alpha}")
            lastReveal = p.reveal
            lastAlpha = p.alpha
        }
        lastReveal = 0f
        lastAlpha = 0f
        for (top in tops) {
            val p = pose(SheetAnimationType.LinuxWindow, top)
            assertTrue(p.reveal >= lastReveal - 1e-4f && p.alpha >= lastAlpha - 1e-4f, "coming up at $top: ${p.reveal}, ${p.alpha}")
            lastReveal = p.reveal
            lastAlpha = p.alpha
        }
    }

    @Test
    fun everyStyleIsFullyThereWhenOpenAndNeverMoreVisibleAsItGoesDown() {
        for (style in SheetAnimationType.entries) {
            val atRest = pose(style, open)
            assertNear(1f, atRest.scale, "$style open scale")
            assertNear(1f, atRest.alpha, "$style open alpha")
            assertNear(0f, atRest.translationY, "$style open translation")

            // Going down (a close, or a drag): never more visible, never bigger, than a moment before.
            var lastAlpha = 1f
            var lastScale = 1f
            for (top in tops.reversed()) {
                val p = pose(style, top)
                assertTrue(p.alpha <= lastAlpha + 1e-4f, "$style: ${p.alpha} at $top is fainter than before")
                assertTrue(p.scale <= lastScale + 1e-4f, "$style: ${p.scale} at $top is smaller than before")
                lastAlpha = p.alpha
                lastScale = p.scale
            }
        }
    }
}
