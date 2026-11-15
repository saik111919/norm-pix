package com.example.normpix.navigation

import androidx.activity.BackEventCompat
import com.example.normpix.settings.BackGestureSpeed
import com.example.normpix.settings.BackGestureStyle
import com.example.normpix.settings.PageAnimationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The back gesture's own rules, the same for a tab and a page: the finger plays the start of the close and not all of it, letting go
 * plays the rest in what is left of one clock, and the style is the pages' own unless the setting names another.
 */
class BackGestureTest {
    @Test
    fun theFingerPlaysTheStartOfTheClose_notAllOfIt() {
        assertEquals(0f, BackGesture.eased(0f))
        assertEquals(1f, BackGesture.eased(1f))
        // An ordinary swipe gets a third of the way: until 2026-11-11 that was a third of the whole close, gone under the finger.
        assertTrue(BackGesture.eased(0.3f) < 0.15f, "a short pull is a preview: ${BackGesture.eased(0.3f)}")
        assertTrue(BackGesture.eased(0.5f) < 0.35f, "half the gesture is a third of the close: ${BackGesture.eased(0.5f)}")
        // And it never goes backwards or jumps as the finger moves on.
        var last = 0f
        for (step in 1..100) {
            val now = BackGesture.eased(step / 100f)
            assertTrue(now >= last, "at $step%")
            assertTrue(now - last < 0.03f, "no jump at $step%: ${now - last}")
            last = now
        }
        // Out of range is the nearest end, not a number to draw with.
        assertEquals(0f, BackGesture.eased(-0.2f))
        assertEquals(1f, BackGesture.eased(1.4f))
    }

    @Test
    fun lettingGoPlaysTheRestInWhatIsLeftOfTheClock() {
        val whole = BackGestureSpeed.Standard.ms
        assertEquals(whole, BackGesture.finishMs(0f, whole))
        assertEquals(whole / 2, BackGesture.finishMs(0.5f, whole))
        assertEquals(0, BackGesture.finishMs(1f, whole), "dragged all the way, there is nothing left to play")
        // It was a fixed 160 ms: after an ordinary swipe the rest now has well over that.
        assertTrue(BackGesture.finishMs(0.3f, whole) > 250, "${BackGesture.finishMs(0.3f, whole)} ms")
        // With animations removed the clock is nothing, and so is the rest.
        assertEquals(0, BackGesture.finishMs(0.3f, 0))
    }

    @Test
    fun theSpeedsAreInOrder_andEveryOneIsSlowerThanTheOldSnap() {
        assertTrue(BackGestureSpeed.Relaxed.ms > BackGestureSpeed.Standard.ms)
        assertTrue(BackGestureSpeed.Standard.ms > BackGestureSpeed.Brisk.ms)
        assertTrue(BackGestureSpeed.Brisk.ms > 160)
    }

    @Test
    fun theStyleIsThePagesOwn_unlessTheSettingNamesAnother() {
        for (pages in PageAnimationType.entries) assertEquals(pages, BackGestureStyle.PageStyle.animation(pages))
        assertEquals(PageAnimationType.System, BackGestureStyle.System.animation(PageAnimationType.LinuxWindow))
        assertEquals(PageAnimationType.System, BackGestureStyle.Shrink.animation(PageAnimationType.LinuxWindow))
        assertEquals(PageAnimationType.SlideHorizontal, BackGestureStyle.Slide.animation(PageAnimationType.LinuxWindow))
        assertEquals(PageAnimationType.FadeThrough, BackGestureStyle.Fade.animation(PageAnimationType.SlideHorizontal))
    }

    @Test
    fun androidsOwn_isTheDefaultStyle_andFollowsTheFingerAsItIs() {
        assertTrue(BackGestureStyle.System.isAndroid)
        assertTrue(BackGestureStyle.entries.filter { it.isAndroid } == listOf(BackGestureStyle.System))
        // The page styles ease the start; Android's own moves as far as the finger has, from the first pixel.
        for (raw in listOf(0.05f, 0.3f, 0.6f)) {
            assertEquals(raw, AndroidBack.follow(raw))
            assertTrue(AndroidBack.follow(raw) > BackGesture.eased(raw), "at $raw the page is further along than an eased one")
        }
        val android = BackLook(PageAnimationType.LinuxWindow, android = true)
        val pages = BackLook(PageAnimationType.LinuxWindow, android = false)
        assertEquals(0.3f, android.eased(0.3f))
        assertEquals(BackGesture.eased(0.3f), pages.eased(0.3f))
    }

    @Test
    fun androidsOwn_movesMostAtTheStart_shrinksAndDriftsWhileHeld_thenFadesAsTheScreenBeneathComesForward() {
        // A short pull already shows - more than a page style's close does at the same point (the design page's decelerating curve).
        val pull = AndroidBack.leaving(0.1f, direction = 1f)
        assertTrue(pull.scale < 0.97f && pull.shiftX > 0f, "moves at once: scale ${pull.scale}, shift ${pull.shiftX}")
        val eased = BackPreview.leaving(PageAnimationType.System, BackGesture.eased(0.1f), 1f)
        assertTrue(pull.scale < eased.scale, "further along than an eased start: ${pull.scale} vs ${eased.scale}")
        // All the way through the preview it is whole and on screen: smaller, a little towards the finger, rounded, not faded.
        for (c in listOf(0.2f, 0.35f, AndroidBack.PREVIEW_BY)) {
            val pose = AndroidBack.leaving(c, direction = 1f)
            assertEquals(1f, pose.alpha, "not faded at $c")
            assertTrue(pose.scale >= AndroidBack.SCALE - 0.001f, "no smaller than ${AndroidBack.SCALE} at $c: ${pose.scale}")
            assertTrue(pose.shiftX <= AndroidBack.DRIFT + 0.0001f, "a drift, not a slide, at $c: ${pose.shiftX}")
            assertTrue(pose.corners > 0.5f, "rounding at $c")
        }
        val held = AndroidBack.leaving(AndroidBack.PREVIEW_BY, 1f)
        assertEquals(AndroidBack.SCALE, held.scale, 0.001f)
        assertEquals(1f, held.corners, 0.001f)
        // Past the preview (letting go) it fades within the first fifth of the commit, where it is.
        assertTrue(AndroidBack.leaving(AndroidBack.PREVIEW_BY + 0.11f, 1f).alpha < 0.001f, "gone a fifth into the commit")
        assertTrue(AndroidBack.leaving(1f, direction = -1f).shiftX < 0f, "from the right edge it drifts left")
        // Never backwards, never a jump.
        var last = AndroidBack.leaving(0f, 1f)
        for (step in 1..100) {
            val now = AndroidBack.leaving(step / 100f, 1f)
            assertTrue(now.shiftX >= last.shiftX && now.scale <= last.scale + 0.0001f, "monotonic at $step%")
            assertTrue(last.scale - now.scale < 0.03f, "no jump at $step%: ${last.scale - now.scale}")
            last = now
        }
        // The screen beneath waits small and off to the other side through the preview, then comes forward to full size.
        val waiting = AndroidBack.revealed(0.3f, 1f)
        assertEquals(AndroidBack.SCALE, waiting.scale, 0.001f)
        assertEquals(-AndroidBack.ENTER_OFFSET, waiting.shiftX, 0.001f)
        assertEquals(1f, AndroidBack.revealed(1f, 1f).scale, 0.0001f)
        assertEquals(0f, AndroidBack.revealed(1f, 1f).shiftX, 0.0001f)
        assertEquals(1f, AndroidBack.revealed(0.7f, 1f).alpha, "never faded")
    }

    @Test
    fun androidsOwn_takesAndroidsTimeToCommit() {
        // The commit is the second half of the clock: 450 ms at Standard, as the platform's.
        val clock = AndroidBack.clockMs(BackGestureSpeed.Standard.ms, android = true)
        assertEquals(450f, clock * (1f - AndroidBack.PREVIEW_BY), 10f)
        assertEquals(BackGestureSpeed.Standard.ms, AndroidBack.clockMs(BackGestureSpeed.Standard.ms, android = false))
    }

    @Test
    fun androidsOwn_aSheetFollowsTheFinger_butOnlyPartWayOut() {
        assertEquals(0f, AndroidBack.sheet(0f))
        assertTrue(AndroidBack.sheet(0.1f) > 0.1f * AndroidBack.SHEET_SHARE, "most at the start")
        assertEquals(AndroidBack.SHEET_SHARE, AndroidBack.sheet(1f), 0.0001f)
        assertTrue(AndroidBack.sheet(1f) <= 0.5f, "letting go closes it; the finger alone never takes it out")
    }

    @Test
    fun aPageMovesTowardsTheSideTheFingerIsOn() {
        assertEquals(1f, BackGesture.directionOf(BackEventCompat.EDGE_LEFT))
        assertEquals(-1f, BackGesture.directionOf(BackEventCompat.EDGE_RIGHT))
    }
}
