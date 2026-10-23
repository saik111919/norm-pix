package com.example.normpix.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A tab that arrives together with the shell (launch, or a page closing over it) is there from its first frame - the
 * page transition does its entrance - while a tab switched to afterwards fades itself in. Told apart by what is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w100dp-h100dp-xxhdpi")
class TabArrivalTest {
    @get:Rule
    val compose = createComposeRule()

    /** What a solid red tab looks like in the middle of the screen, 40 ms after it was composed as the visible one. */
    private fun centre(animateArrival: Boolean): Color {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TabHost(visible = true, animateArrival = animateArrival) {
                    Box(Modifier.fillMaxSize().background(Color.Red).testTag("tab"))
                }
            }
        }
        compose.mainClock.advanceTimeBy(40)
        val pixels = compose.onNodeWithTag("tab").captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    @Test
    fun aTabThatArrivesWithTheShellIsFullyThereFromItsFirstFrame() {
        assertEquals(Color.Red, centre(animateArrival = false))
    }

    /** A red tab switched to, in the default (window) style, with the clock frozen at the moment it was composed. */
    private fun showArrivingTab() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                TabHost(visible = true, animateArrival = true) {
                    Box(Modifier.fillMaxSize().background(Color.Red).testTag("tab"))
                }
            }
        }
    }

    /** The colour [fraction] (0..1) of the way across the tab and [down] (0..1) of the way down it. */
    private fun sample(fraction: Float, down: Float): Color {
        val pixels = compose.onNodeWithTag("tab").captureToImage().toPixelMap()
        return pixels[(pixels.width * fraction).toInt(), (pixels.height * down).toInt()]
    }

    @Test
    fun aTabSwitchedToRisesFromTheBottomEdgeInTheLinuxWindowStyle() {
        showArrivingTab()
        compose.mainClock.advanceTimeBy(40)
        // The default style: a window. Early on only the part along the bottom edge is uncovered - the top is still the page behind.
        assertEquals(Color.White, sample(0.5f, 0.1f), "the top of the tab is not uncovered yet")
        val bottom = sample(0.5f, 0.9f)
        assertTrue(bottom.green < 0.95f, "but the bottom of it is already there (faintly: it is also fading in): $bottom")
    }

    @Test
    fun aTabSwitchedToIsWholeOnceItHasArrived() {
        showArrivingTab()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(Color.Red, sample(0.5f, 0.1f), "arrived: the top is uncovered too")
        assertEquals(Color.Red, sample(0.5f, 0.9f))
    }

    @Test
    fun aTabSwitchedToFadesIn() {
        val pixel = centre(animateArrival = true)
        // Part way up from invisible: the white behind it still shows through (a fully drawn tab has none).
        assertTrue(pixel.green > 0.1f && pixel.blue > 0.1f, "still fading in 40 ms after it was switched to: $pixel")
    }
}
