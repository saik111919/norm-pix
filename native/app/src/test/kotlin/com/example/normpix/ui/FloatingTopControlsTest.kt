package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LocalWindowBlurOverride
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * In the top floating circle style a sub-page's back circle (and its actions, in a pill) float over the page as glass,
 * the page scrolling on under them - with no bar behind them. The other styles keep their app bar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FloatingTopControlsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private lateinit var list: LazyListState
    private var blurAvailable by mutableStateOf(true)
    private var withActions by mutableStateOf(false)
    private var shown = false

    @Before
    fun setUp() {
        env = TestEnvironment("floating_top_controls_test")
    }

    @After
    fun tearDown() = env.close()

    private val share: @Composable RowScope.() -> Unit = {
        IconButton(onClick = {}) { Icon(Icons.Rounded.Share, contentDescription = "Share") }
    }

    /** A sub-page whose content opens with a red block, then rows; scrolled to its top. */
    private fun page(style: BackButtonStyle, blurAvailable: Boolean = true, withActions: Boolean = false) {
        env.preferences.setBackButtonStyle(style)
        this.blurAvailable = blurAvailable
        this.withActions = withActions
        if (!shown) {
            shown = true
            compose.setContent {
                CompositionLocalProvider(LocalWindowBlurOverride provides this.blurAvailable) {
                    env.Content {
                        list = rememberLazyListState()
                        AppSubPage(title = "Appearance", onBack = {}, listState = list, actions = if (this.withActions) share else null) {
                            item { Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Red).testTag("red")) }
                            items(20) { Box(Modifier.fillMaxWidth().height(80.dp)) { Text("Row $it") } }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        scrollTo(0)
    }

    private fun scrollTo(index: Int) {
        compose.runOnUiThread { CoroutineScope(Dispatchers.Main.immediate).launch { list.scrollToItem(index) } }
        compose.waitForIdle()
    }

    /** Scrolls the red block up under the controls. */
    private fun scrollRedUnderTheControls() = scrollTo(2)

    private fun back(): Rect = compose.onNode(hasContentDescription("Back", substring = true)).getBoundsInRoot().toPx()

    private fun DpRect.toPx() = with(compose.density) { Rect(left.toPx(), top.toPx(), right.toPx(), bottom.toPx()) }

    @Test
    fun theBackCircleFloatsOverThePage_andThePageScrollsOnUnderIt() {
        page(BackButtonStyle.TopFloatingCircle)
        val title = compose.onNode(hasText("Appearance") and isHeading()).getBoundsInRoot().toPx()
        val circle = back()
        assertTrue(title.top >= circle.bottom, "the page opens with its title, below the circle")

        scrollRedUnderTheControls()
        val red = compose.onNodeWithTag("red").getBoundsInRoot().toPx()
        assertTrue(red.overlaps(circle), "the page runs on under the circle: $red, $circle")
        assertEquals(circle, back(), "which stays where it is")
    }

    /** The circle's colour, beside its arrow, with the red block under it. */
    private fun circleFill(): Color {
        val circle = back()
        val px = compose.onRoot().captureToImage().toPixelMap()
        return px[(circle.left + circle.width * 0.2f).toInt(), circle.center.y.toInt()]
    }

    @Test
    fun theControlsAreGlass_whereTheBarsAre_andSolidWhereTheyAreNot() {
        page(BackButtonStyle.TopFloatingCircle, blurAvailable = true)
        scrollRedUnderTheControls()
        val glass = circleFill()

        page(BackButtonStyle.TopFloatingCircle, blurAvailable = false)
        scrollRedUnderTheControls()
        val solid = circleFill()

        env.preferences.setSheetBlurEnabled(false)
        page(BackButtonStyle.TopFloatingCircle, blurAvailable = true)
        scrollRedUnderTheControls()
        val switchedOff = circleFill()

        // Redness, as the theme may be light (white glass turns pink) or dark.
        assertTrue(glass.red - glass.green > solid.red - solid.green + 0.15f, "the page shows through the glass, blurred: $glass vs $solid")
        assertEquals(solid, switchedOff, "with background blur switched off it is solid, as where the system does not blur")
    }

    @Test
    fun theActionsPill_isThereOnlyWhenThePageHasActions() {
        // A pixel where the pill would be, and one in the empty middle of the controls' row.
        fun pillSpotAndMiddle(): Pair<Color, Color> {
            val circle = back()
            val px = compose.onRoot().captureToImage().toPixelMap()
            val y = circle.center.y.toInt()
            return px[px.width - (circle.left + circle.width * 0.2f).toInt(), y] to px[px.width / 2, y]
        }

        page(BackButtonStyle.TopFloatingCircle, withActions = false)
        val (bare, bareMiddle) = pillSpotAndMiddle()
        assertEquals(bareMiddle, bare, "no empty pill")

        page(BackButtonStyle.TopFloatingCircle, withActions = true)
        val share = compose.onNodeWithContentDescription("Share").getBoundsInRoot().toPx()
        val circle = back()
        assertTrue(share.center.y in circle.top..circle.bottom && share.left > circle.right, "beside the circle, on its row")
        val (pill, middle) = pillSpotAndMiddle()
        assertFalse(pill == middle, "in a pill")
    }

    /** How much of the controls' row, right of the circle, is not the page's own colour: the title, when it is up there. */
    private fun rowInk(): Float {
        val circle = back()
        val px = compose.onRoot().captureToImage().toPixelMap()
        val bg = px[px.width - 6, circle.center.y.toInt()]
        var total = 0f
        for (y in circle.top.toInt() until circle.bottom.toInt()) {
            for (x in (circle.right + 16).toInt() until px.width - 16) {
                val c = px[x, y]
                total += abs(c.red - bg.red) + abs(c.green - bg.green) + abs(c.blue - bg.blue)
            }
        }
        return total
    }

    private fun headings() = compose.onAllNodes(hasText("Appearance") and isHeading()).fetchSemanticsNodes().size

    @Test
    fun theTitleGoesUpIntoTheControlsRow_asThePageScrolls() {
        page(BackButtonStyle.TopFloatingCircle)
        assertEquals(0f, rowInk(), "at rest the title is down on the page, and the row beside the circle is empty")
        assertEquals(1, headings(), "the page's own title")

        scrollTo(1)
        assertTrue(rowInk() > 200f, "scrolled up, the title is in the row beside the circle: ${rowInk()}")
        assertEquals(1, headings(), "spoken once: by the one in the row now")

        scrollTo(0)
        assertEquals(0f, rowInk(), "and back down with the page")
        assertEquals(1, headings())
    }

    /** The darkest and the brightest pixel in the controls' row beside the circle: where the title's colour shows. */
    private fun rowLuminanceRange(): Pair<Float, Float> {
        val circle = back()
        val px = compose.onRoot().captureToImage().toPixelMap()
        var darkest = 1f
        var brightest = 0f
        for (y in circle.top.toInt() until circle.bottom.toInt()) {
            for (x in (circle.right + 16).toInt() until px.width - 16) {
                val c = px[x, y]
                val luminance = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue
                darkest = minOf(darkest, luminance)
                brightest = maxOf(brightest, luminance)
            }
        }
        return darkest to brightest
    }

    @Test
    fun theTitleInTheRowIsReadable_lightOnDark_andDarkOnLight() {
        env.preferences.setMode(ThemeMode.Dark)
        page(BackButtonStyle.TopFloatingCircle)
        scrollTo(1)
        val (_, brightest) = rowLuminanceRange()
        assertTrue(brightest > 0.75f, "light text on the dark page, not the default black: $brightest")

        env.preferences.setMode(ThemeMode.Light)
        compose.waitForIdle()
        val (darkest, _) = rowLuminanceRange()
        assertTrue(darkest < 0.3f, "dark text on the light page: $darkest")
    }

    @Test
    fun theTitleInTheRowIsReadable_whateverTheContentUnderIt() {
        // The red block runs on under the row; the title is still the page's text colour, over the page's colour.
        env.preferences.setMode(ThemeMode.Dark)
        page(BackButtonStyle.TopFloatingCircle)
        scrollRedUnderTheControls()
        val (darkest, brightest) = rowLuminanceRange()
        assertTrue(brightest > 0.75f, "still light text: $brightest")
        assertTrue(darkest < 0.25f, "on a dark page, though red runs under it: $darkest")
    }

    @Test
    fun theTitleInTheRowKeepsClearOfTheActionsPill() {
        page(BackButtonStyle.TopFloatingCircle, withActions = true)
        scrollTo(1)
        val share = compose.onNodeWithContentDescription("Share").getBoundsInRoot().toPx()
        val circle = back()
        assertTrue(share.left > circle.right + 40f, "there is room between the circle and the pill for the title")
        assertTrue(rowInk() > 200f, "and it is there")
    }

    @Test
    fun theOtherStyles_keepTheirAppBar() {
        for (style in listOf(BackButtonStyle.StandardTopBar, BackButtonStyle.BottomDock)) {
            page(style)
            assertTrue(compose.onAllNodes(hasText("Appearance") and isHeading()).fetchSemanticsNodes().isEmpty(), "$style: the title is in the bar")
            // The bar's title, not the bottom back bar (in the bottom dock style), which names the page too.
            val title = compose.onNode(hasText("Appearance") and !hasClickAction()).getBoundsInRoot().toPx()
            val red = compose.onNodeWithTag("red").getBoundsInRoot().toPx()
            assertTrue(red.top >= title.bottom, "$style: the page starts below the bar")
            scrollRedUnderTheControls()
            assertTrue(compose.onAllNodes(hasText("Appearance")).fetchSemanticsNodes().isNotEmpty(), "$style: which stays")
        }
    }
}
