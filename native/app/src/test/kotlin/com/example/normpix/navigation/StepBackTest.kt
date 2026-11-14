package com.example.normpix.navigation

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * One step back inside a screen ([StepBackHandler] + [StepBackLayers]), with a real gesture: the side on screen (red, 1) closes under
 * the finger in the Back gesture style over the side it goes back to (blue, 0); letting go makes the step, giving up leaves it be.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w100dp-h100dp-xxhdpi")
class StepBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var side by mutableIntStateOf(1)

    private fun show(reduced: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(
                LocalBackAnimation provides PageAnimationType.SlideHorizontal,
                LocalReducedMotionOverride provides reduced,
            ) {
                val state = rememberStepBack()
                StepBackHandler(state, enabled = side != 0, to = 0) { side = 0 }
                StepBackLayers(state, shown = side, of = { it }) { which ->
                    Box(Modifier.fillMaxSize().background(if (which == 0) Color.Blue else Color.Red))
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pixelAt(x: Float): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt().coerceAtMost(pixels.width - 1), pixels.height / 2]
    }

    private fun drag(progress: Float) {
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            back.dispatchOnBackStarted(BackEventCompat(0f, 150f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(60f, 150f, progress, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
    }

    @Test
    fun theSideOnScreenSlidesTowardsTheFinger_overTheSideItGoesBackTo() {
        show()
        drag(0.5f)
        // Half the gesture is a third of the close: a slide a third of the way to the right.
        assertEquals(Color.Blue, pixelAt(0.15f), "the side it goes back to shows where this one has left")
        assertEquals(Color.Red, pixelAt(0.7f))
        assertEquals(1, side, "nothing has changed while the finger is down")
    }

    @Test
    fun lettingGoMakesTheStep() {
        show()
        drag(0.5f)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, side)
        assertEquals(Color.Blue, pixelAt(0.15f))
        assertEquals(Color.Blue, pixelAt(0.85f))
    }

    @Test
    fun aGestureGivenUpPutsItBack() {
        show()
        drag(0.5f)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals(1, side)
        assertEquals(Color.Red, pixelAt(0.15f))
        assertEquals(Color.Red, pixelAt(0.85f))
    }

    @Test
    fun withAnimationsRemovedThereIsNothingBeneath_backJustSteps() {
        show(reduced = true)
        drag(0.5f)
        assertEquals(Color.Red, pixelAt(0.15f), "no preview with animations off")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, side)
    }
}
