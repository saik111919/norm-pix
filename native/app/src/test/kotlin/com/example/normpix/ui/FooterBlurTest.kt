package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.FooterBlur
import com.example.normpix.ui.components.LocalWindowBlurOverride
import com.example.normpix.ui.components.frostedSource
import com.example.normpix.ui.components.rememberFrostedBackdrop
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The footer: the page passing under the dock is blurred and faded into the page's colour, more towards the edge - where
 * blur is on, the footer's own switch is on, and the dock is showing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h300dp-xxhdpi")
class FooterBlurTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("footer_blur")
    }

    @After
    fun tearDown() = env.close()

    private val density = 3f // xxhdpi

    /** A 300 x 200dp page, red on the left and blue on the right, with a footer zone of 40dp of bars (and its fade) along its foot. */
    private fun showPage(blurAvailable: Boolean = true, visible: Float = 1f) {
        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalReducedMotionOverride provides true, LocalWindowBlurOverride provides blurAvailable) {
                    val backdrop = rememberFrostedBackdrop()
                    Box(Modifier.size(300.dp, 200.dp)) {
                        Row(Modifier.fillMaxSize().frostedSource(backdrop)) {
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                        }
                        FooterBlur(backdrop, Modifier.align(Alignment.BottomCenter), barsHeight = 40.dp, visible = { visible })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pixel(xDp: Int, yDp: Int) = compose.onRoot().captureToImage().toPixelMap()[(xDp * density).toInt(), (yDp * density).toInt()]

    @Test
    fun theFootFadesIntoThePage_andTheRestOfThePageStaysSharp() {
        showPage()
        // Far from the seam, a sharp red page has no green; at the foot the page's own light colour has washed over it.
        val foot = pixel(30, 198)
        assertTrue(foot.green > 0.35f, "faded into the page at the foot: $foot")
        // Above the footer zone (it starts 76dp up) the page is untouched.
        val above = pixel(30, 100)
        assertTrue(above.red > 0.95f && above.green < 0.05f, "sharp above the footer: $above")
        // The seam, a hard red | blue edge, is smeared into both inside the footer, and still sharp above it.
        val seamFoot = pixel(150, 170)
        assertTrue(seamFoot.red > 0.1f && seamFoot.blue > 0.1f, "blurred seam: $seamFoot")
        val seamAbove = pixel(147, 100)
        assertTrue(seamAbove.red > 0.95f && seamAbove.blue < 0.05f, "sharp seam above the footer: $seamAbove")
    }

    @Test
    fun theFadeGrowsTowardsTheEdge() {
        showPage()
        val high = pixel(30, 140)
        val low = pixel(30, 198)
        assertTrue(low.green > high.green, "more washed at the foot ($low) than higher up ($high)")
    }

    @Test
    fun withTheFooterSwitchedOffThePageIsLeftAlone() {
        env.preferences.setFooterBlurEnabled(false)
        showPage()
        val foot = pixel(30, 198)
        assertTrue(foot.red > 0.95f && foot.green < 0.05f, "untouched: $foot")
    }

    @Test
    fun withTheSystemNotBlurringThePageIsLeftAlone() {
        showPage(blurAvailable = false)
        val foot = pixel(30, 198)
        assertTrue(foot.red > 0.95f && foot.green < 0.05f, "untouched: $foot")
    }

    @Test
    fun asTheDockGoesTheFooterGoesWithIt() {
        showPage(visible = 0f)
        val foot = pixel(30, 198)
        assertTrue(foot.red > 0.95f && foot.green < 0.05f, "nothing drawn once the dock is gone: $foot")
    }

}
