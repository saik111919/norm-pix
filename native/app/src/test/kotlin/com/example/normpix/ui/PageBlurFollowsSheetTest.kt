package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalWindowBlurOverride
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The page itself, drawn by the app's root, is blurred in step with a sheet: partly while the sheet is partway up, fully
 * once it is open, and sharp again once it is gone - never sharp under a sheet that is already up and blurring in late.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
class PageBlurFollowsSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("page_blur")
    }

    @After
    fun tearDown() = env.close()

    private lateinit var overlays: AppOverlays

    /** A page that is red on the left and blue on the right: blur shows as blue bleeding over the seam. */
    private fun showPage(blurAvailable: Boolean = true) {
        compose.setContent {
            CompositionLocalProvider(LocalWindowBlurOverride provides blurAvailable) {
                env.Content {
                    overlays = LocalOverlays.current
                    Row(Modifier.fillMaxSize().testTag("page")) {
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Red))
                        Box(Modifier.weight(1f).fillMaxHeight().background(Color.Blue))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** How much blue has bled 8px to the left of the seam: 0 on a sharp page. */
    private fun bleed(): Float {
        val px = compose.onNodeWithTag("page").captureToImage().toPixelMap()
        return px[px.width / 2 - 8, px.height / 4].blue
    }

    private fun openSheet() {
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                overlays.sheet<Unit> { Text("Sheet body", Modifier.height(240.dp)) }
            }
        }
    }

    private class Frame(val share: Float, val bleed: Float) {
        override fun toString() = "%.2f/%.2f".format(share, bleed)
    }

    private fun frames(count: Int): List<Frame> = List(count) {
        compose.mainClock.advanceTimeByFrame()
        val share = compose.runOnIdle { overlays.backdropBlur }
        Frame(share, bleed())
    }

    @Test
    fun thePageBlursAsTheSheetRises_andClearsAsItGoes() {
        showPage()
        assertEquals(0f, bleed(), 0.02f, "sharp with nothing open")

        compose.mainClock.autoAdvance = false
        openSheet()
        val rising = frames(40)
        val open = rising.last()
        assertEquals(1f, open.share, 0.01f, "open: $rising")
        assertTrue(open.bleed > 0.1f, "the page under an open sheet is blurred: $rising")

        // Partway up, partly blurred: the blur came with the sheet, not after it landed.
        val partway = rising.filter { it.share in 0.15f..0.85f }
        assertTrue(partway.isNotEmpty(), "the sheet was seen partway up: $rising")
        assertTrue(partway.all { it.bleed > 0.005f && it.bleed < open.bleed }, "partly blurred while partway: $rising")
        // At most a frame behind the sheet: once it has started up, the page is never left sharp.
        val started = rising.indexOfFirst { it.share > 0.1f }
        assertTrue(rising.drop(started + 1).all { it.bleed > 0.005f }, "no sharp frames under a rising sheet: $rising")

        compose.runOnUiThread { overlays.dismissAll() }
        val closing = frames(30)
        assertEquals(0f, closing.last().share, "closed: $closing")
        assertEquals(0f, closing.last().bleed, 0.02f, "sharp again: $closing")
        // Removed all at once, the blur still eases out rather than snapping.
        assertTrue(closing.any { it.share == 0f && it.bleed in 0.01f..(open.bleed - 0.01f) }, "eases out: $closing")
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun whereTheSystemDoesNotBlur_thePageStaysSharpUnderASheet() {
        showPage(blurAvailable = false)
        openSheet()
        compose.waitForIdle()
        assertEquals(0f, bleed(), 0.02f)
    }

    @Test
    fun withBlurSwitchedOff_thePageStaysSharpUnderASheet() {
        env.preferences.setSheetBlurEnabled(false)
        showPage()
        openSheet()
        compose.waitForIdle()
        assertEquals(0f, bleed(), 0.02f)
    }
}
