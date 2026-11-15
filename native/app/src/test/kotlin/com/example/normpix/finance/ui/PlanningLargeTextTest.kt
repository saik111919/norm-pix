package com.example.normpix.finance.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
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
 * The Repeating costs and Forecast pages hold at 1.6x text: the words and figures of a row keep to whole lines (a figure is never
 * broken in two, as "₹24,000.0 / 0" was when the month's cost sat beside them), because at large text the amount goes under the name.
 * Real text widths are needed to see this, hence the native graphics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp-xxhdpi")
@OptIn(ExperimentalTestApi::class)
class PlanningLargeTextTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("planning_large_text")
    }

    @After
    fun tearDown() = env.close()

    private fun seed() = runBlocking {
        val finance = env.container.finance
        val bank = finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, openingBalance = 5_000_000, createdAt = now, updatedAt = now))
        val category = finance.state.value.categories.first { it.name == "Rent" }.id
        for ((payee, amount, ahead) in listOf(Triple("Ramesh Iyer", 24_000_00L, 5L), Triple("Netflix", 649_00L, 9L))) {
            finance.saveRecurringRule(
                RecurringRule(
                    type = TransactionType.Expense,
                    amount = amount,
                    accountId = bank.id!!,
                    categoryId = category,
                    payee = payee,
                    frequency = RecurrenceFrequency.Monthly,
                    startDate = now.toLocalDate().plusDays(ahead).atTime(9, 0),
                    nextDate = now.toLocalDate().plusDays(ahead).atTime(9, 0),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun show(fontScale: Float, waitFor: String, page: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            env.Content { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) { page() } }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText(waitFor, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun bounds(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()

    @Test
    fun aCostRowKeepsItsFigureWholeAndPutsTheMonthUnderTheName() {
        seed()
        show(1.6f, "Biggest first") { RepeatingCostsPage() }
        val name = bounds("Ramesh Iyer")
        val amount = bounds("₹24,000.00 · Monthly")
        val month = bounds("₹24,000.00 a month")
        assertTrue(amount.height < 40.dp, "the amount and how often are one line, not ${amount.height}")
        assertTrue(month.height < 40.dp, "the month's cost is one line, not ${month.height}")
        assertEquals(name.left, month.left, "the month's cost is in the name's column")
        assertTrue(month.top >= amount.bottom, "and under it")
        // The page is 411 dp wide: nothing in the row runs past it.
        assertTrue(month.right <= 411.dp && amount.right <= 411.dp)
    }

    @Test
    fun aCostRowAtNormalTextHasTheMonthBesideTheName() {
        seed()
        show(1f, "Biggest first") { RepeatingCostsPage() }
        val name = bounds("Ramesh Iyer")
        val month = compose.onAllNodesWithText("₹24,000.00", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(month.isNotEmpty())
        assertTrue(name.width < 300.dp)
        compose.onAllNodesWithText("a month", useUnmergedTree = true).fetchSemanticsNodes().let { assertEquals(2, it.size, "one 'a month' beside each row") }
    }

    @Test
    fun aScheduledRowKeepsItsFigureWholeAndPutsItUnderTheName() {
        seed()
        show(1.6f, "Day by day") { ForecastPage() }
        val name = bounds("Ramesh Iyer")
        val amount = bounds("−₹24,000.00")
        val where = bounds("HDFC Savings · Repeating")
        assertTrue(amount.height < 40.dp, "the amount is one line, not ${amount.height}")
        assertTrue(amount.top >= name.bottom, "under the name")
        assertEquals(name.left, amount.left, "in the name's column")
        assertTrue(where.top >= amount.bottom)
        assertTrue(where.right <= 411.dp)
    }
}
