package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.ui.components.AppSplitButton
import com.example.normpix.ui.components.SplitButtonColors
import com.example.normpix.ui.components.SplitButtonSize
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Material 3's split button: a main action and, a hair apart, a button that opens a menu. Small is 40dp tall with a 48dp
 * menu button, Medium 56dp with a 56dp one; the corners where the two meet are small, and the menu button goes round
 * while its menu is open. One pixel = one dp here (the test density is 160dpi).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SplitButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private var main = 0
    private var picked = 0
    private var expanded by mutableStateOf(false)

    private fun show(size: SplitButtonSize) {
        compose.setContent {
            NormPixTheme {
                Box(Modifier.background(Color.Black).testTag("host")) {
                    AppSplitButton(
                        onClick = { main++ },
                        leadingDescription = "Main",
                        menuExpanded = expanded,
                        onMenuExpandedChange = { expanded = it },
                        size = size,
                        colors = SplitButtonColors(Color.White, Color.Black),
                        menuDescription = "More",
                        leading = { Text("Go") },
                        menu = { DropdownMenuItem(text = { Text("Option") }, onClick = { picked++; expanded = false }) },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theMainButtonActs_andTheOtherOnlyOpensTheMenu() {
        show(SplitButtonSize.Small)
        compose.onNodeWithContentDescription("Main").performClick()
        assertEquals(1, main)
        assertTrue(!expanded, "the main action does not open the menu")

        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        assertEquals(1, main, "and opening the menu is not the main action")
        assertTrue(expanded)
        compose.onNodeWithText("Option").performClick()
        compose.waitForIdle()
        assertEquals(1, picked)
        assertTrue(!expanded, "choosing closes the menu")
    }

    @Test
    fun theMenuButtonSaysWhetherItsMenuIsOpen() {
        show(SplitButtonSize.Small)
        fun config() = compose.onNodeWithContentDescription("More").fetchSemanticsNode().config
        assertEquals("Collapsed", config()[SemanticsProperties.StateDescription])
        assertEquals(Role.Button, config()[SemanticsProperties.Role])
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        assertEquals("Expanded", config()[SemanticsProperties.StateDescription])
    }

    @Test
    fun aSmallSplitButtonIsFortyDpTall_withAMenuButtonOfFortyEight_twoDpFromTheMain() {
        show(SplitButtonSize.Small)
        val main = compose.onNodeWithContentDescription("Main").getBoundsInRoot()
        val menu = compose.onNodeWithContentDescription("More").getBoundsInRoot()
        assertEquals(40f, main.height.value, 0.5f)
        assertEquals(40f, menu.height.value, 0.5f)
        assertEquals(48f, menu.width.value, 0.5f, "its chevron and the space either side of it")
        assertEquals(2f, (menu.left - main.right).value, 0.5f, "a hair apart")
    }

    @Test
    fun aMediumSplitButtonIsFiftySixDpTall_withASquareMenuButton() {
        show(SplitButtonSize.Medium)
        val menu = compose.onNodeWithContentDescription("More").getBoundsInRoot()
        assertEquals(56f, menu.height.value, 0.5f)
        assertEquals(56f, menu.width.value, 0.5f)
    }

    @Test
    fun theMenuButtonGoesRoundWhileItsMenuIsOpen() {
        show(SplitButtonSize.Small)
        // The corner where the two meet: small while closed, so its very corner is filled; round open, so it is not.
        fun cornerIsFilled(): Boolean {
            val px = compose.onNodeWithContentDescription("More").captureToImage().toPixelMap()
            return px[1, 1].red > 0.5f
        }
        assertTrue(cornerIsFilled(), "closed: a small corner")
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        assertTrue(!cornerIsFilled(), "open: fully round")
        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        assertTrue(cornerIsFilled(), "and back")
        // The outer end is round either way.
        val px = compose.onNodeWithContentDescription("More").captureToImage().toPixelMap()
        assertTrue(px[px.width - 2, 1].red < 0.5f, "the outer corner is round")
    }
}
