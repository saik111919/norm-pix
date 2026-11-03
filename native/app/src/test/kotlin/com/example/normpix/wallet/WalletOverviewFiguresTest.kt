package com.example.normpix.wallet

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.testing.TestEnvironment
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two figures under the Wallet's total ("In accounts", "You owe") each get half a row, and the amounts are the longest
 * thing in it: an Indian lakh figure with its paise is a dozen characters. The tile promises the figure "is never cut: it
 * shrinks to fit", so the amount must be printed whole - its drawn width within its box - on a 360dp and a 411dp phone, with
 * the usual text and larger.
 */
// Native graphics: Robolectric's own text measurement is narrower than a real font's, so overflow would never show.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class WalletOverviewFiguresTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "wallet_overview_figures_test")
    private val money = MoneyFormatter(CurrencySpec.INR)

    // A phone 360dp wide leaves 328dp for the overview card (a page gutter of 16dp each side); one of 411dp, 379dp.
    private val narrow = 328.dp
    private val wide = 379.dp

    private var width by mutableStateOf(narrow)
    private var fontScale by mutableStateOf(1f)

    private val held = money.format(40_886_900)
    private val owed = money.format(14_066_600)

    // What the tiles say: first the short figures of a page still loading, then the real ones.
    private var heldShown by mutableStateOf(money.format(0))
    private var owedShown by mutableStateOf(money.format(0))

    @AfterTest
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                env.Content {
                    // A scrolling page, as the Wallet is: its children get all the height they ask for.
                    Box(Modifier.width(width).verticalScroll(rememberScrollState())) {
                        WalletOverview(
                            total = money.format(26_820_300),
                            held = heldShown,
                            owed = owedShown,
                            negative = false,
                            spoken = "Total balance",
                            note = "Balances are worked out from the starting balance and every transaction up to today.",
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        heldShown = held
        owedShown = owed
        compose.waitForIdle()
    }

    private fun change(width: Dp, fontScale: Float) {
        this.width = width
        this.fontScale = fontScale
        compose.waitForIdle()
    }

    private val problems = mutableListOf<String>()

    /** Notes a problem unless [text] is printed whole: what the font draws of it fits the box the text was given, and the layout did not overflow. */
    private fun checkPrintedWhole(text: String, where: String) {
        val node = compose.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val drawn = layout.getLineRight(0) - layout.getLineLeft(0)
        val size = layout.layoutInput.style.fontSize
        if (layout.didOverflowWidth || drawn > node.size.width + 1f) {
            problems += "$text $where: drawn ${drawn}px, box ${node.size.width}px, font $size, overflow ${layout.didOverflowWidth}"
        }
    }

    @Test
    fun `an amount of lakhs with its paise is printed whole beside its icon on a 360dp and a 411dp phone`() {
        show()
        for (phone in listOf(narrow, wide)) {
            change(phone, 1f)
            checkPrintedWhole(held, "in $phone")
            checkPrintedWhole(owed, "in $phone")
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test
    fun `with larger text the amounts are still printed whole, side by side and stacked`() {
        show()
        for (phone in listOf(narrow, wide)) {
            for (scale in listOf(1.3f, 1.7f)) {
                change(phone, scale)
                checkPrintedWhole(held, "in $phone at ${scale}x")
                checkPrintedWhole(owed, "in $phone at ${scale}x")
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }
}
