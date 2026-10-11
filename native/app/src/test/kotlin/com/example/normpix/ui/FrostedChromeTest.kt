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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.rememberDockColors
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.FrostedBackdrop
import com.example.normpix.ui.components.frostedGlass
import com.example.normpix.ui.components.frostedSource
import com.example.normpix.ui.components.rememberFrostedBackdrop
import com.example.normpix.ui.components.rememberFrostedChrome
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * The floating bars (the dock, the back bar) are glass only where something is really blurred behind them, and solid
 * otherwise - the same rule as the blur behind a sheet.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h200dp-xxhdpi")
class FrostedChromeTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("frosted_chrome")
    }

    @After
    fun tearDown() = env.close()

    private val density = 3f // xxhdpi

    /** [rememberFrostedChrome] under the given conditions. [backdrop] false: a bar with no page to blur. */
    private fun glass(hasBackdrop: Boolean, blurAvailable: Boolean): Boolean {
        var result = false
        compose.setContent {
            env.Content {
                val backdrop: FrostedBackdrop? = if (hasBackdrop) rememberFrostedBackdrop() else null
                result = rememberFrostedChrome(backdrop, blurAvailable)
            }
        }
        compose.waitForIdle()
        return result
    }

    @Test
    fun barsAreGlassWhenThereIsAPageToBlurAndTheSystemBlurs() {
        assertTrue(glass(hasBackdrop = true, blurAvailable = true))
    }

    @Test
    fun barsAreSolidWhenTheSystemIsNotBlurring() {
        // Blur switched off (battery saver, the developer option) or not there (before Android 12).
        assertFalse(glass(hasBackdrop = true, blurAvailable = false))
    }

    @Test
    fun barsAreSolidWithNothingToBlur() {
        assertFalse(glass(hasBackdrop = false, blurAvailable = true))
    }

    @Test
    fun barsAreSolidWhenFrostedGlassIsSwitchedOff() {
        env.preferences.setSheetBlurEnabled(false)
        assertFalse(glass(hasBackdrop = true, blurAvailable = true))
    }

    @Test
    fun theDockTintIsTranslucentOnlyWhereItIsGlass() {
        var glassContainer = Color.Unspecified
        var solidContainer = Color.Unspecified
        var glassCompanion = Color.Unspecified
        var solidCompanion = Color.Unspecified
        compose.setContent {
            NormPixTheme(Appearance(mode = ThemeMode.Dark)) {
                rememberDockColors(blurred = true).let { glassContainer = it.container; glassCompanion = it.companionContainer }
                rememberDockColors(blurred = false).let { solidContainer = it.container; solidCompanion = it.companionContainer }
            }
        }
        compose.waitForIdle()
        assertTrue(glassContainer.alpha < 1f && glassCompanion.alpha < 1f, "glass is see-through")
        assertEquals(1f, solidContainer.alpha, "solid is not")
        assertEquals(1f, solidCompanion.alpha)
    }

    // --- the blur itself -------------------------------------------------------------------------------------

    /** Red on the left, blue on the right, a 120 x 40dp glass bar across the seam; the page is 300 x 100dp. */
    private fun showPageWithBar(blurAvailable: Boolean) {
        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                    val backdrop = rememberFrostedBackdrop()
                    Box(Modifier.size(300.dp, 100.dp)) {
                        Row(Modifier.fillMaxSize().frostedSource(backdrop)) {
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
                            Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                        }
                        Box(
                            Modifier
                                .align(Alignment.Center)
                                .size(120.dp, 40.dp)
                                .frostedGlass(backdrop, RectangleShape, blurAvailable),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun glassBlursThePageBehindIt_andLeavesThePageAroundItSharp() {
        showPageWithBar(blurAvailable = true)
        val px = compose.onRoot().captureToImage().toPixelMap()
        val seamX = (150 * density).toInt()
        val inside = px[seamX, (50 * density).toInt()] // on the seam, inside the bar
        val above = px[seamX - 3, (10 * density).toInt()] // just left of the seam, above the bar

        // The seam is a hard red | blue edge on the page; behind the bar it is smeared into both.
        assertTrue(inside.red > 0.15f && inside.blue > 0.15f, "blurred seam mixes red and blue: $inside")
        assertTrue(above.red > 0.95f && above.blue < 0.05f, "the page outside the bar is sharp: $above")
    }

    @Test
    fun withoutGlassTheBarDrawsNothingOfItsOwn() {
        showPageWithBar(blurAvailable = false)
        val px = compose.onRoot().captureToImage().toPixelMap()
        val seamX = (150 * density).toInt()
        val onTheSeam = px[seamX - 3, (50 * density).toInt()]
        assertTrue(onTheSeam.red > 0.95f && onTheSeam.blue < 0.05f, "the page shows through untouched (the bar's tint is what makes it solid): $onTheSeam")
    }
}
