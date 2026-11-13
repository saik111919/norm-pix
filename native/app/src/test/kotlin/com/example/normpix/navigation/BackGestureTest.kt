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
        assertEquals(PageAnimationType.System, BackGestureStyle.Shrink.animation(PageAnimationType.LinuxWindow))
        assertEquals(PageAnimationType.SlideHorizontal, BackGestureStyle.Slide.animation(PageAnimationType.LinuxWindow))
        assertEquals(PageAnimationType.FadeThrough, BackGestureStyle.Fade.animation(PageAnimationType.SlideHorizontal))
    }

    @Test
    fun aPageMovesTowardsTheSideTheFingerIsOn() {
        assertEquals(1f, BackGesture.directionOf(BackEventCompat.EDGE_LEFT))
        assertEquals(-1f, BackGesture.directionOf(BackEventCompat.EDGE_RIGHT))
    }
}
