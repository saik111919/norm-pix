package com.example.normpix.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.BannerProgress
import com.example.normpix.ui.components.SplitStatBanner
import com.example.normpix.ui.components.quietInk
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import kotlin.math.abs
import kotlin.test.assertEquals
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
 * The two-panel summary banner (the card bills on Home and the Wallet): one button with one spoken description, a pill in the
 * status colour when it has a tone and the theme's primary when it has none, the panels stacked rather than squeezed at large text,
 * and the quieter lines readable on the container in every theme.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h800dp-xxhdpi")
class SplitStatBannerTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var env: TestEnvironment

    // What the banner on screen is given: the test changes these and the same composition follows.
    private var tone by mutableStateOf<StatusTone?>(null)
    private var fontScale by mutableFloatStateOf(1f)
    private var clickable by mutableStateOf(true)
    private var clicks = 0
    private var warning = Color.Unspecified
    private var primary = Color.Unspecified

    /** The contrast of the quieter lines on the container, for each theme and accent as it was drawn. */
    private val quietContrast = mutableMapOf<String, Float>()

    @Before
    fun setUp() {
        env = TestEnvironment("split_stat_banner_test")
        compose.setContent {
            env.Content {
                warning = StatusTone.Warning.resolve().color
                primary = MaterialTheme.colorScheme.primary
                val scheme = MaterialTheme.colorScheme
                val prefs by env.preferences.state.collectAsState()
                val quiet = quietInk(scheme.onSecondaryContainer, scheme.secondaryContainer)
                quietContrast["${prefs.mode}/${prefs.accent}"] = contrast(quiet, scheme.secondaryContainer)
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    SplitStatBanner(
                        progress = BannerProgress("Due date", "Tomorrow", 0.5f, "1 day left", from = "5 Nov", to = "20 Nov"),
                        figure = BannerFigure("Total due", "₹8,450", note = "ICICI Credit Card"),
                        spoken = SPOKEN,
                        modifier = Modifier.testTag("banner"),
                        tone = tone,
                        onClickLabel = "Pay this bill",
                        onClick = if (clickable) ({ clicks++ }) else null,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = env.close()

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** How many sampled pixels of the banner are [color] (within rounding). */
    private fun pixelsOf(color: Color): Int {
        val map = compose.onNodeWithTag("banner").captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until map.height step 2) {
            for (x in 0 until map.width step 2) {
                val p = map[x, y]
                if (abs(p.red - color.red) < 0.01f && abs(p.green - color.green) < 0.01f && abs(p.blue - color.blue) < 0.01f) count++
            }
        }
        return count
    }

    @Test
    fun `it is one button that says the whole banner, and a click goes through`() {
        compose.onNodeWithContentDescription(SPOKEN).assertHasClickAction().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `without a click it is not a button`() {
        compose.runOnIdle { clickable = false }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(SPOKEN).assertHasNoClickAction()
    }

    @Test
    fun `a banner with a tone puts its colour in the pill, and one without uses the primary`() {
        val plain = pixelsOf(primary)
        assertTrue(plain > 200, "the pill is the primary without a tone ($plain)")
        assertEquals(0, pixelsOf(warning), "and no warning colour is on a banner without one")

        compose.runOnIdle { tone = StatusTone.Warning }
        compose.waitForIdle()
        assertTrue(pixelsOf(warning) > 200, "the pill is the warning colour with the tone")
        assertTrue(pixelsOf(primary) < plain / 4, "and no longer the primary")
    }

    @Test
    fun `large text stacks the panels instead of squeezing them`() {
        val side = compose.onNodeWithTag("banner").getUnclippedBoundsInRoot().height
        compose.runOnIdle { fontScale = 1.6f }
        compose.waitForIdle()
        val stacked = compose.onNodeWithTag("banner").getUnclippedBoundsInRoot().height
        // Two panels one above the other are about twice as tall as the two side by side - and not more than three times (a
        // stacked panel that filled the screen's height instead of holding its content was 10x).
        assertTrue(stacked.value > side.value * 1.6f, "stacked $stacked against side by side $side")
        assertTrue(stacked.value < side.value * 3.5f, "stacked $stacked is no taller than its content needs, against $side")
    }

    @Test
    fun `the quieter lines read on the container in every theme`() {
        env.preferences.setColorSource(ColorSource.Custom)
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            for (accent in AccentColor.entries) {
                env.preferences.setMode(mode)
                env.preferences.setAccent(accent)
                compose.waitForIdle()
                val ratio = quietContrast["$mode/$accent"] ?: throw AssertionError("$mode/$accent was drawn")
                assertTrue(ratio >= 4.5f, "$mode/$accent: the quieter lines read ($ratio)")
            }
        }
    }

    private companion object {
        const val SPOKEN = "ICICI Credit Card bill. Due tomorrow. Total due ₹8,450. 1 day left"
    }
}
