package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.ui.Hero
import com.example.normpix.finance.ui.HeroAmount
import com.example.normpix.finance.ui.HeroFigure
import com.example.normpix.finance.ui.HeroTile
import com.example.normpix.finance.ui.HeroTone
import com.example.normpix.screens.BudgetTile
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The tiles under Home's hero are half the card wide, so what they say has to be short: the budget tile's label reads on one
 * line on 360dp and 411dp phones, stays tidy with larger text, says it in full to a screen reader, and shows the value in the
 * error colour, without a minus sign, once the budget is over.
 */
// Native graphics: Robolectric's own text measurement never wraps a line, and wrapping is what is under test here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HeroTilesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "hero_tiles_test")
    private val money = MoneyFormatter(CurrencySpec.INR)

    // A phone 360dp wide leaves 328dp for the hero (a page gutter of 16dp each side); one of 411dp, 379dp.
    private val narrow = 328.dp
    private val wide = 379.dp

    private val within = BudgetStatus(allocated = 1_000_000, spent = 125_000)
    private val over = BudgetStatus(allocated = 1_000_000, spent = 1_125_000)

    private var width by mutableStateOf(narrow)
    private var fontScale by mutableStateOf(1f)
    private var status by mutableStateOf(within)
    private var scheme: ColorScheme? = null
    private var errorTone = Color.Unspecified

    @AfterTest
    fun tearDown() = env.close()

    private fun show(initial: BudgetStatus = within) {
        status = initial
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                env.Content {
                    scheme = MaterialTheme.colorScheme
                    errorTone = StatusTone.Error.resolve().color
                    Box(Modifier.width(width)) {
                        Hero(
                            tiles = listOf(
                                { slot -> BudgetTile(slot, status, money, onClick = {}) },
                                { slot -> HeroTile(slot, HeroTone.Secondary) { HeroFigure(label = "Total balance", value = money.format(12_450_000)) } },
                            ),
                        ) { HeroAmount(money.format(1_125_000)) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val node = compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun linesOf(text: String) = layoutOf(text).lineCount

    private fun textsContaining(text: String) = compose.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes()

    private fun change(width: Dp, fontScale: Float, status: BudgetStatus) {
        this.width = width
        this.fontScale = fontScale
        this.status = status
        compose.waitForIdle()
    }

    @Test
    fun `the labels of the tiles read on one line on a 360dp and a 411dp phone`() {
        show()
        for (phone in listOf(narrow, wide)) {
            for (state in listOf(within, over)) {
                change(phone, 1f, state)
                val label = if (state == over) "Over budget" else "Left to spend"
                assertEquals(1, linesOf(label), "\"$label\" on one line in $phone")
                assertEquals(1, linesOf("Total balance"), "\"Total balance\" on one line in $phone")
            }
        }
    }

    @Test
    fun `with larger text the tile still reads well`() {
        show()
        for (phone in listOf(narrow, wide)) {
            for (state in listOf(within, over)) {
                change(phone, 1.3f, state)
                val label = if (state == over) "Over budget" else "Left to spend"
                assertEquals(1, linesOf(label), "\"$label\" at 1.3x in $phone")
                val value = money.format(if (state == over) -state.remaining else state.remaining)
                assertFalse(layoutOf(value).didOverflowWidth, "$value fits its tile at 1.3x in $phone")
            }
        }
    }

    @Test
    fun `over budget the value is in the error colour and has no minus sign`() {
        show(over)
        val value = money.format(125_000)
        assertEquals("₹1,250.00", value)
        compose.onNode(hasText(value), useUnmergedTree = true).assertIsDisplayed()
        assertTrue(textsContaining("−").isEmpty() && textsContaining("-").isEmpty(), "no minus sign anywhere on the tile")
        assertEquals(errorTone, layoutOf(value).layoutInput.style.color)
        assertNotEquals(scheme!!.onTertiaryContainer, layoutOf(value).layoutInput.style.color)

        // Within budget, the value is in the tile's own ink.
        change(narrow, 1f, within)
        assertEquals(scheme!!.onTertiaryContainer, layoutOf(money.format(875_000)).layoutInput.style.color)
    }

    @Test
    fun `a screen reader hears the whole sentence`() {
        show(over)
        compose.onNodeWithContentDescription("Over budget by ₹1,250.00, 112% of ₹10,000.00 used").assertIsDisplayed()
        change(narrow, 1f, within)
        compose.onNodeWithContentDescription("Left to spend ₹8,750.00, 12% of ₹10,000.00 used").assertIsDisplayed()
        // The words on the tile are not read a second time.
        assertEquals(1, compose.onAllNodes(hasContentDescription("Left to spend", substring = true)).fetchSemanticsNodes().size)
    }
}
