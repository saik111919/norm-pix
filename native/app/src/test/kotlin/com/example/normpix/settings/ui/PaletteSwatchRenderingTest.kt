package com.example.normpix.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.PaletteSwatch
import kotlin.math.abs
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What a palette tile draws: a circle, the primary on its upper half, the container and the accent side by side below it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h400dp-xxhdpi")
class PaletteSwatchRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    private val page = Color.White
    private val primary = Color(0xFFC00000)
    private val container = Color(0xFF00A000)
    private val tertiary = Color(0xFF0000C0)

    private fun shot(): PixelMap {
        compose.setContent {
            Box(Modifier.testTag("swatch").size(100.dp).background(page)) {
                SwatchCircle(PaletteSwatch(primary, container, tertiary), Modifier.fillMaxSize())
            }
        }
        compose.waitForIdle()
        return compose.onNodeWithTag("swatch").captureToImage().toPixelMap()
    }

    private fun PixelMap.at(shareX: Float, shareY: Float): Color = this[(width * shareX).toInt(), (height * shareY).toInt()]

    private fun near(expected: Color, actual: Color, what: String) {
        val off = abs(expected.red - actual.red) + abs(expected.green - actual.green) + abs(expected.blue - actual.blue)
        assertTrue(off < 0.03f, "$what: expected $expected, drawn $actual")
    }

    @Test
    fun theUpperHalfIsThePrimary_theLowerHalfIsTheContainerThenTheAccent() {
        val pixels = shot()

        near(primary, pixels.at(0.5f, 0.2f), "top")
        near(primary, pixels.at(0.3f, 0.35f), "upper left")
        near(primary, pixels.at(0.7f, 0.35f), "upper right")
        near(container, pixels.at(0.3f, 0.7f), "lower left")
        near(tertiary, pixels.at(0.7f, 0.7f), "lower right")
    }

    @Test
    fun itIsACircle_theCornersOfItsSquareAreLeftBare() {
        val pixels = shot()

        near(page, pixels.at(0.04f, 0.04f), "top left corner")
        near(page, pixels.at(0.96f, 0.04f), "top right corner")
        near(page, pixels.at(0.04f, 0.96f), "bottom left corner")
        near(page, pixels.at(0.96f, 0.96f), "bottom right corner")
    }
}
