package com.example.normpix.navigation

import com.example.normpix.settings.PageAnimationType
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the back gesture shows when it takes the app to the previous tab: the tab being left in the user's page style, moved
 * by how far the finger has gone, over the tab it goes back to. These are the pure poses; that the tabs really draw them
 * is the shell's job.
 */
class BackPreviewTest {
    private val everyStyle = PageAnimationType.entries
    private val steps = (0..20).map { it / 20f }

    private fun assertNear(expected: Float, actual: Float, message: String) =
        assertTrue(abs(expected - actual) < 1e-4f, "$message: expected $expected, was $actual")

    private fun BackPose.assertRest(message: String) {
        assertNear(1f, scale, "$message scale")
        assertNear(1f, alpha, "$message alpha")
        assertNear(0f, shiftX, "$message shift")
        assertNear(0f, corners, "$message corners")
        assertNear(0f, lift, "$message lift")
        assertNear(1f, reveal, "$message reveal")
    }

    @Test
    fun theSideTheFingerCameFromIsTheDirection() {
        assertEquals(1f, BackPreview.directionOf(fromRightEdge = false), "from the left edge the page follows the finger to the right")
        assertEquals(-1f, BackPreview.directionOf(fromRightEdge = true), "from the right edge, to the left")
    }

    @Test
    fun beforeTheFingerMovesEveryStyleLeavesTheTabAsItWas() {
        for (type in everyStyle) {
            BackPreview.leaving(type, 0f, 1f).assertRest("$type leaving")
            BackPreview.leaving(type, 0f, -1f).assertRest("$type leaving (from the right)")
        }
    }

    @Test
    fun whenTheGestureIsDoneTheTabLeavingIsGone() {
        // Gone is invisible or moved off by its whole width: either way the switch to the tab beneath is seamless.
        for (type in everyStyle) {
            for (direction in listOf(1f, -1f)) {
                val pose = BackPreview.leaving(type, 1f, direction)
                assertTrue(pose.alpha < 1e-3f || abs(pose.shiftX) >= 1f - 1e-4f, "$type: not gone at the end ($pose)")
            }
        }
    }

    @Test
    fun whenTheGestureIsDoneTheTabItGoesBackToIsAtRest() {
        for (type in everyStyle) {
            for (direction in listOf(1f, -1f)) {
                val pose = BackPreview.revealed(type, 1f, direction)
                assertNear(1f, pose.scale, "$type scale")
                assertNear(1f, pose.alpha, "$type alpha")
                assertNear(0f, pose.shiftX, "$type shift")
            }
        }
    }

    @Test
    fun theTabItGoesBackToIsNeverFaded() {
        // It is the one the user is returning to: seen whole under whatever fades or slides away over it.
        for (type in everyStyle) {
            for (p in steps) assertEquals(1f, BackPreview.revealed(type, p, 1f).alpha, "$type at $p")
        }
    }

    @Test
    fun progressOutsideZeroToOneIsHeldAtTheEnds() {
        for (type in everyStyle) {
            BackPreview.leaving(type, -0.5f, 1f).assertRest("$type below 0")
            val end = BackPreview.leaving(type, 1f, 1f)
            val past = BackPreview.leaving(type, 3f, 1f)
            assertEquals(end.scale, past.scale, "$type scale past 1")
            assertEquals(end.alpha, past.alpha, "$type alpha past 1")
            assertEquals(end.shiftX, past.shiftX, "$type shift past 1")
            assertEquals(end.reveal, past.reveal, "$type reveal past 1")
        }
    }

    @Test
    fun aFadeThroughOnlyFadesTheTabLeaving() {
        for (p in steps) {
            val pose = BackPreview.leaving(PageAnimationType.FadeThrough, p, 1f)
            assertNear(1f - p, pose.alpha, "alpha at $p")
            assertNear(1f, pose.scale, "scale at $p")
            assertNear(0f, pose.shiftX, "shift at $p")
            assertNear(0f, pose.corners, "corners at $p")
            assertNear(0f, pose.lift, "lift at $p")
        }
    }

    @Test
    fun aSlideMovesTheTabLeavingTheWholeWidthTowardsTheFinger() {
        for (p in steps) {
            val fromLeft = BackPreview.leaving(PageAnimationType.SlideHorizontal, p, 1f)
            val fromRight = BackPreview.leaving(PageAnimationType.SlideHorizontal, p, -1f)
            assertNear(p, fromLeft.shiftX, "from the left at $p")
            assertNear(-p, fromRight.shiftX, "from the right at $p")
            assertNear(1f, fromLeft.scale, "a slide does not scale at $p")
            assertNear(1f, fromLeft.alpha, "a slide does not fade at $p")
        }
    }

    @Test
    fun theStylesThatMoveSidewaysGoTowardsTheSideTheFingerIsOn() {
        val sideways = listOf(PageAnimationType.SlideHorizontal, PageAnimationType.System, PageAnimationType.SharedAxis)
        for (type in sideways) {
            val left = BackPreview.leaving(type, 0.5f, 1f).shiftX
            val right = BackPreview.leaving(type, 0.5f, -1f).shiftX
            assertTrue(left > 0f, "$type: from the left edge it goes right")
            assertTrue(right < 0f, "$type: from the right edge it goes left")
            assertNear(left, -right, "$type: the same way either side")
            // And the tab beneath is a little the other way, coming into place as the first one goes.
            val beneath = BackPreview.revealed(type, 0.25f, 1f).shiftX
            assertTrue(beneath < 0f, "$type: the tab beneath waits on the other side")
            assertTrue(abs(beneath) < 0.5f, "$type: only a short way")
        }
    }

    @Test
    fun theStylesThatDoNotMoveSidewaysStayWhereTheyAre() {
        for (type in listOf(PageAnimationType.LinuxWindow, PageAnimationType.ScaleAndFade, PageAnimationType.FadeThrough)) {
            for (p in steps) {
                assertEquals(0f, BackPreview.leaving(type, p, 1f).shiftX, "$type leaving at $p")
                assertEquals(0f, BackPreview.revealed(type, p, 1f).shiftX, "$type revealed at $p")
            }
        }
    }

    @Test
    fun aWindowSinksIntoItsStripAtTheBottomAndAScaleShrinksAboutItsCentre() {
        val window = BackPreview.leaving(PageAnimationType.LinuxWindow, 1f, 1f)
        assertNear(LinuxWindowMotion.STRIP, window.reveal, "what shows of the window at the end: the strip")
        assertNear(0f, window.alpha, "and the strip has faded away")
        assertNear(1f, window.scale, "a window is not scaled: it is uncovered, so nothing on it is squashed")
        assertNear(0f, window.shiftX, "and it goes down, not to the side")

        // Half way down it is a smaller window than it was, and still whole: it fades only when it is nearly the strip.
        val half = BackPreview.leaving(PageAnimationType.LinuxWindow, 0.5f, 1f)
        assertTrue(half.reveal < 1f - (1f - LinuxWindowMotion.STRIP) * 0.1f && half.reveal > LinuxWindowMotion.STRIP, "part-way down: ${half.reveal}")
        assertNear(1f, half.alpha, "half way down it has not started to fade")

        val scale = BackPreview.leaving(PageAnimationType.ScaleAndFade, 1f, 1f)
        assertNear(PageTransitions.GROW_SCALE, scale.scale, "scale-and-fade scale at the end")
        assertEquals(androidx.compose.ui.graphics.TransformOrigin.Center, scale.origin, "about its centre")

        // The window behind settles up to full size; the scale-and-fade's does not move.
        assertTrue(BackPreview.revealed(PageAnimationType.LinuxWindow, 0f, 1f).scale < 1f, "the window beneath starts small")
        assertNear(1f, BackPreview.revealed(PageAnimationType.ScaleAndFade, 0f, 1f).scale, "the tab beneath a scale-and-fade")
    }

    @Test
    fun scrubbingNeverRunsBackwardsOrJumps() {
        // A finger drags the motion to and fro, so each piece of it has to be a plain function of the progress: what leaves
        // only ever leaves further, and what is beneath only ever settles.
        for (type in everyStyle) {
            var lastAlpha = 1f
            var lastScale = 1f
            var lastShift = 0f
            var lastReveal = 1f
            var lastBeneath = BackPreview.revealed(type, 0f, 1f)
            for (p in steps) {
                val leaving = BackPreview.leaving(type, p, 1f)
                assertTrue(leaving.alpha <= lastAlpha + 1e-4f, "$type: the tab leaving only fades out ($p)")
                assertTrue(leaving.scale <= lastScale + 1e-4f, "$type: the tab leaving only shrinks ($p)")
                assertTrue(leaving.shiftX >= lastShift - 1e-4f, "$type: the tab leaving only goes further ($p)")
                assertTrue(leaving.reveal <= lastReveal + 1e-4f, "$type: the tab leaving only sinks further ($p)")
                lastAlpha = leaving.alpha
                lastScale = leaving.scale
                lastShift = leaving.shiftX
                lastReveal = leaving.reveal

                val beneath = BackPreview.revealed(type, p, 1f)
                assertTrue(beneath.scale >= lastBeneath.scale - 1e-4f, "$type: the tab beneath only grows into place ($p)")
                assertTrue(abs(beneath.shiftX) <= abs(lastBeneath.shiftX) + 1e-4f, "$type: the tab beneath only settles ($p)")
                lastBeneath = beneath
            }
        }
    }

    @Test
    fun theCornersAreRoundOnceTheTabHasStartedToMoveAndOnlyForTheStylesThatMoveAWindow() {
        for (type in listOf(PageAnimationType.LinuxWindow, PageAnimationType.ScaleAndFade, PageAnimationType.System, PageAnimationType.SlideHorizontal)) {
            assertEquals(0f, BackPreview.leaving(type, 0f, 1f).corners, "$type: square at rest")
            assertNear(1f, BackPreview.leaving(type, BackPreview.CORNERS_BY, 1f).corners, "$type: round by ${BackPreview.CORNERS_BY}")
            assertNear(1f, BackPreview.leaving(type, 1f, 1f).corners, "$type: round at the end")
            assertTrue(BackPreview.leaving(type, 0.1f, 1f).lift > 0f, "$type: lifted off the tab beneath")
        }
        for (type in listOf(PageAnimationType.FadeThrough, PageAnimationType.SharedAxis)) {
            for (p in steps) assertEquals(0f, BackPreview.leaving(type, p, 1f).corners, "$type: a fade has no corners to round ($p)")
        }
    }
}
