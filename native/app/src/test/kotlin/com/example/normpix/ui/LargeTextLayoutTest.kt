package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.HeroStats
import com.example.normpix.finance.ui.SummaryStat
import com.example.normpix.finance.ui.TransactionTile
import com.example.normpix.screens.PlansAndTransactionsScreen
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Large text (a font scale of 1.6): a hero's figures stack instead of cutting their values short, the history's search
 * field grows with its text, and a transaction row gives its details a second line. At normal size each stays as it was.
 */
@OptIn(ExperimentalTestApi::class)
// Native graphics: Robolectric's own text measurement never wraps a line, and wrapping is what is under test here.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class LargeTextLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "large_text_layout_test")
    private val finance = env.container.finance
    private val accountName = "ICICI Credit Card Platinum Edition"

    @After
    fun tearDown() = env.close()

    /** Composes [content] with the density's font scale set to [fontScale]. */
    private fun setContentAtFontScale(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                content()
            }
        }
    }

    // ------------------------------------------------------------ hero figures

    @Test
    fun heroStatsSitSideBySideAtNormalSizeAndStackWhenTextIsLarge() {
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                Box(Modifier.width(360.dp).testTag("box")) {
                    HeroStats(
                        stats = listOf<@Composable (Modifier) -> Unit>(
                            { m -> SummaryStat(label = "Left", value = "Rs 34,856.49", modifier = m.testTag("s1")) },
                            { m -> SummaryStat(label = "Income", value = "Rs 92,000.00", modifier = m.testTag("s2")) },
                            { m -> SummaryStat(label = "Total balance", value = "Rs 2,76,136.06", modifier = m.testTag("s3")) },
                        ),
                    )
                }
            }
        }
        compose.waitForIdle()

        val side = listOf("s1", "s2", "s3").map { compose.onNodeWithTag(it).getBoundsInRoot() }
        assertTrue(side[0].left < side[1].left && side[1].left < side[2].left, "side by side at normal size: $side")
        assertTrue(
            abs((side[0].bottom - side[2].bottom).value) < 1f && abs((side[1].bottom - side[2].bottom).value) < 1f,
            "on one baseline at normal size: $side",
        )

        compose.runOnIdle { fontScale = 1.6f }
        compose.waitForIdle()

        val box = compose.onNodeWithTag("box").getBoundsInRoot()
        val stacked = listOf("s1", "s2", "s3").map { compose.onNodeWithTag(it).getBoundsInRoot() }
        assertTrue(stacked.all { it.left == stacked[0].left }, "one column at large text: $stacked")
        assertTrue(stacked[0].top < stacked[1].top && stacked[1].top < stacked[2].top, "stacked top to bottom: $stacked")
        assertTrue(stacked.all { it.width >= box.width - 1.dp }, "each figure takes the full card width: $stacked vs $box")
    }

    // ------------------------------------------------------------ history search field

    private fun showHistory(fontScale: Float) {
        setContentAtFontScale(fontScale) { env.Content { PlansAndTransactionsScreen() } }
        compose.waitForIdle()
    }

    @Test
    fun theSearchFieldGrowsWithTheText_normalSize() {
        showHistory(fontScale = 1f)
        val height = compose.onNode(hasSetTextAction()).getBoundsInRoot().height
        assertTrue(height >= 56.dp, "the field keeps its 56dp minimum at normal size: $height")
    }

    @Test
    fun theSearchFieldGrowsWithTheText_largeSize() {
        showHistory(fontScale = 1.6f)
        val height = compose.onNode(hasSetTextAction()).getBoundsInRoot().height
        assertTrue(height > 56.dp, "at large text the field grows past 56dp rather than being fixed at it: $height")
    }

    // ------------------------------------------------------------ transaction row

    /** The lines in the row's details text (its category and account), read from the unmerged tree. */
    private fun detailsLineCount(): Int {
        val node = compose.onNode(hasText(accountName, substring = true), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single().lineCount
    }

    @Test
    fun aTransactionRowGivesItsTextTwoLinesWhenTextIsLarge() {
        val now = LocalDateTime.now().withNano(0)
        runBlocking { finance.load() }
        val card = runBlocking {
            finance.saveAccount(Account(name = accountName, kind = AccountKind.Card, createdAt = now, updatedAt = now))
        }
        val food = finance.state.value.categories.first { it.name == "Food & dining" && it.type == CategoryType.Expense }
        val tx = FinanceTransaction(
            type = TransactionType.Expense,
            amount = 1_284_00,
            accountId = card.id!!,
            categoryId = food.id,
            payee = "Meghana Foods and Dining Company Pvt Ltd",
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
        )

        setContentAtFontScale(fontScale = 1.6f) {
            env.Content {
                Box(Modifier.width(360.dp)) { TransactionTile(tx) }
            }
        }
        // The account's name reaches the row once the finance state has loaded it.
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(accountName, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, detailsLineCount(), "the details take a second line at large text instead of being cut short")
    }
}
