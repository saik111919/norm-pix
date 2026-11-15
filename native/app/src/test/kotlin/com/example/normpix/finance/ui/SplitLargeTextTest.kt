package com.example.normpix.finance.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.SplitPart
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A split payment's details at large text (native graphics: without them Robolectric measures text with narrower, made-up widths): a
 * part's line has a long category path ("Groceries > Household supplies") and an amount, and the amount must stay on one line - it was
 * broken one character to a line, the long label having taken the row, until the amount was given the width it needs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xhdpi")
class SplitLargeTextTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("split_large_text")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun `a part's amount stays on one line at 1_6 times text, the long category giving way`() {
        val finance = env.container.finance
        val parts = runBlocking {
            finance.load()
            val cash = finance.repository.accounts().first { it.kind == AccountKind.Cash }
            val bank = finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
            val categories = finance.repository.categories()
            val saved = finance.repository.saveSplit(
                FinanceTransaction(
                    type = TransactionType.Expense, amount = 202_000, accountId = bank.id ?: cash.id!!, occurredAt = now.minusDays(2), payee = "DMart",
                    createdAt = now, updatedAt = now,
                ),
                listOf(
                    SplitPart(categories.first { it.name == "Staples & grains" }.id, 138_000, "Rice, atta and dal"),
                    SplitPart(categories.first { it.name == "Household supplies" }.id, 64_000, "Detergent"),
                ),
            )
            finance.load()
            saved
        }
        compose.setContent {
            env.Content {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                    TransactionDetailsSheet(parts[1], parts = parts, onAction = {})
                }
            }
        }
        compose.waitForIdle()
        for (amount in listOf("₹1,380.00", "₹640.00")) {
            val bounds = compose.onNodeWithText(amount, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(bounds.height < 48.dp, "$amount is on one line, not broken up: ${bounds.height} high")
            assertTrue(bounds.width > 60.dp, "$amount has the width it needs: ${bounds.width} wide")
        }
    }
}
