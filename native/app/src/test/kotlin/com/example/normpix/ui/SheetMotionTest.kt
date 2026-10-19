package com.example.normpix.ui

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
        for (style in listOf(SheetAnimationType.Fade, SheetAnimationType.Scale)) {
            for (top in tops) assertNear(open, top + pose(style, top).translationY, "$style at $top")
        }
        // Dragged above its resting place the sheet follows the finger.
        for (style in listOf(SheetAnimationType.Fade, SheetAnimationType.Scale)) {
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
