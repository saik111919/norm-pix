package com.example.normpix.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tabs under the back gesture: the tab on screen (red) is drawn as the user's page style closes a page, by how far the
 * gesture has gone, over the tab it goes back to (blue), which is laid out whole beneath it. Told apart by what is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w100dp-h100dp-xxhdpi")
class TabBackPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    private val back = TabBackPreview()

    /** Home (red, on screen) and Wallet (blue, hidden), in [style], with the gesture as [back] has it. */
    private fun show(style: PageAnimationType, reducedMotion: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(
                LocalPageAnimation provides style,
                LocalReducedMotionOverride provides reducedMotion,
            ) {
                Box(Modifier.fillMaxSize()) {
                    TabHost(visible = true, animateArrival = false, back = back, ordinal = 0) {
                        Box(Modifier.fillMaxSize().background(Color.Red))
                    }
                    TabHost(visible = false, animateArrival = false, back = back, ordinal = 3) {
                        Box(Modifier.fillMaxSize().background(Color.Blue))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The colour [x] (0..1 across) of the way across the middle of the screen. */
    private fun pixelAt(x: Float): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt(), pixels.height / 2]
    }

    private fun gesture(target: Int, progress: Float, direction: Float = 1f) {
        back.target = target
        back.progress = progress
        back.direction = direction
    }

    @Test
    fun withNoGestureTheTabOnScreenIsDrawnAsItAlwaysWas() {
        gesture(TabBackPreview.NO_TAB, progress = 0.5f)
        show(PageAnimationType.SlideHorizontal)
        assertEquals(Color.Red, pixelAt(0.25f))
        assertEquals(Color.Red, pixelAt(0.75f))
    }

    @Test
    fun aSlideMovesTheTabTowardsTheFingerAndShowsTheOneItGoesBackToBeneath() {
        gesture(target = 3, progress = 0.5f, direction = 1f)
        show(PageAnimationType.SlideHorizontal)
        assertEquals(Color.Blue, pixelAt(0.25f), "from the left edge the tab goes right, and the tab beneath shows on the left")
        assertEquals(Color.Red, pixelAt(0.75f))
    }

    @Test
    fun fromTheRightEdgeItGoesTheOtherWay() {
        gesture(target = 3, progress = 0.5f, direction = -1f)
        show(PageAnimationType.SlideHorizontal)
        assertEquals(Color.Red, pixelAt(0.25f), "from the right edge the tab goes left")
        assertEquals(Color.Blue, pixelAt(0.75f), "and the tab beneath shows on the right")
    }

    @Test
    fun aFadeThroughFadesTheTabOutOverTheOneBeneath() {
        gesture(target = 3, progress = 0.5f)
        show(PageAnimationType.FadeThrough)
        for (x in listOf(0.25f, 0.75f)) {
            val pixel = pixelAt(x)
            assertTrue(pixel.red > 0.3f && pixel.blue > 0.3f, "half way: a mix of the two tabs, in place, at $x: $pixel")
        }
    }

    @Test
    fun whenASlideIsDoneTheTabBeneathIsAllThatIsLeft() {
        gesture(target = 3, progress = 1f)
        show(PageAnimationType.SlideHorizontal)
        assertEquals(Color.Blue, pixelAt(0.25f))
        assertEquals(Color.Blue, pixelAt(0.75f))
    }

    @Test
    fun whenAFadeIsDoneTheTabBeneathIsAllThatIsLeft() {
        gesture(target = 3, progress = 1f)
        show(PageAnimationType.FadeThrough)
        assertEquals(Color.Blue, pixelAt(0.25f))
        assertEquals(Color.Blue, pixelAt(0.75f))
    }

    @Test
    fun theTabBeneathIsOnlyDrawnForTheTabTheGestureGoesBackTo() {
        gesture(target = 1, progress = 0.5f)
        show(PageAnimationType.SlideHorizontal)
        val pixel = pixelAt(0.25f)
        // With no tab beneath, what shows where the slid tab was is the window's own background (near white), not the Wallet's blue.
        assertNotEquals(Color.Blue, pixel, "Wallet is not the tab being returned to, so it stays where it was: $pixel")
    }

    @Test
    fun withAnimationsRemovedTheGestureShowsNothingExtra() {
        gesture(target = 3, progress = 0.5f)
        show(PageAnimationType.SlideHorizontal, reducedMotion = true)
        assertEquals(Color.Red, pixelAt(0.25f))
        assertEquals(Color.Red, pixelAt(0.75f))
    }
}
