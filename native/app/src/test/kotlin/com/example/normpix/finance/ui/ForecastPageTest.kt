package com.example.normpix.finance.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.ForecastEnd
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Forecast page: where it ends (30, 60 or 90 days, until payday, a day picked), what it says in the banner and for a screen reader,
 * the switch for everyday spending, what is dated, and amounts kept hidden. The scenario, from 100,000 in the bank on [today]: rent of
 * 20,000 on day 5, a salary of 50,000 on day 20, and 9,000 of everyday spending over the last 90 days (100 a day).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h4000dp")
@OptIn(ExperimentalTestApi::class)
class ForecastPageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()
    private val today = LocalDate.now()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("forecast_page")
    }

    @After
    fun tearDown() = env.close()

    private fun rule(type: TransactionType, amount: Long, account: Long, payee: String, daysAhead: Long, category: Long?) = RecurringRule(
        type = type,
        amount = amount,
        accountId = account,
        categoryId = category,
        payee = payee,
        frequency = RecurrenceFrequency.Monthly,
        startDate = today.plusDays(daysAhead).atTime(9, 0),
        nextDate = today.plusDays(daysAhead).atTime(9, 0),
        createdAt = now,
        updatedAt = now,
    )

    private fun seed(opening: Long = 10_900_000, income: Boolean = true) = runBlocking {
        val finance = env.container.finance
        val bank = finance.saveAccount(Account(name = "HDFC", kind = AccountKind.Bank, openingBalance = opening, createdAt = now, updatedAt = now))
        val expense = finance.state.value.categories.first { it.name == "Shopping" }.id
        val salary = finance.state.value.categories.first { it.type == CategoryType.Income }.id
        for (daysAgo in listOf(10L, 20L, 30L)) {
            val at = today.minusDays(daysAgo).atTime(18, 0)
            finance.saveTransaction(
                FinanceTransaction(type = TransactionType.Expense, amount = 300_000, accountId = bank.id!!, categoryId = expense, occurredAt = at, payee = "Shop", createdAt = at, updatedAt = at),
            )
        }
        finance.saveRecurringRule(rule(TransactionType.Expense, 2_000_000, bank.id!!, "Rent", 5, expense))
        if (income) finance.saveRecurringRule(rule(TransactionType.Income, 5_000_000, bank.id!!, "Employer", 20, salary))
    }

    private fun show() {
        compose.setContent { env.Content { ForecastPage() } }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Day by day").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun day(offset: Long) = dayLabel(today.plusDays(offset))

    /** What the banner says, which is all of it for a screen reader; with [exact] false, whatever begins with [spoken]. */
    private fun banner(spoken: String, exact: Boolean = true) = compose.onNode(hasContentDescription(spoken, substring = !exact))

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun chip(label: String) = compose.onNodeWithText(label)

    // ----------------------------------------------------------------------------------------------- the banner

    @Test
    fun itOpensOnThirtyDaysWithTheEndAndTheLowestPointInTheBanner() {
        seed()
        show()
        chip("30 days").assertIsSelected()
        // 100,000 less 100 a day, less the rent on day 5, plus the salary on day 20: 127,000 on day 30; the lowest is the day before the salary.
        banner("Forecast to ${day(30)}. You would have ₹1,27,000.00 to spend then. The lowest point is ₹78,100.00, on ${day(19)}.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("End balance", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Lowest point", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Only what's scheduled is known; everyday spending is an estimate.").assertIsDisplayed()
    }

    @Test
    fun theChartSaysItsLowestPointAndItsEnd_notEveryDay() {
        seed()
        show()
        compose.onNode(
            hasContentDescription(
                "Money you can spend, day by day to ${day(30)}. The lowest point is ₹78,100.00 on ${day(19)}, and it ends at ₹1,27,000.00.",
            ),
        ).assertIsDisplayed()
    }

    // --------------------------------------------------------------------------------------------- end choices

    @Test
    fun sixtyAndNinetyDaysMoveTheEnd_andTheChoiceIsKeptForTheSession() {
        seed()
        show()
        chip("60 days").performClick()
        compose.waitForIdle()
        chip("60 days").assertIsSelected()
        banner("Forecast to ${day(60)}.", exact = false).assertIsDisplayed()
        assertEquals(ForecastEnd.Days(60), env.shell.forecastEnd)
        chip("90 days").performClick()
        compose.waitForIdle()
        banner("Forecast to ${day(90)}.", exact = false).assertIsDisplayed()
        assertEquals(ForecastEnd.Days(90), env.shell.forecastEnd)
    }

    @Test
    fun aChoiceMadeEarlierInTheSessionIsWhereThePageOpens() {
        seed()
        env.shell.forecastEnd = ForecastEnd.Days(60)
        show()
        chip("60 days").assertIsSelected()
        banner("Forecast to ${day(60)}.", exact = false).assertIsDisplayed()
    }

    @Test
    fun untilPaydayEndsOnTheNextOfTheBiggestIncome() {
        seed()
        show()
        chip("Until payday").performClick()
        compose.waitForIdle()
        chip("Until payday").assertIsSelected()
        // The salary is on day 20: 100,000 less 2,000 of everyday spending, less the rent, plus the salary.
        banner("Forecast to ${day(20)}. You would have ₹1,28,000.00 to spend then. The lowest point is ₹78,100.00, on ${day(19)}.").assertIsDisplayed()
        assertEquals(ForecastEnd.Payday, env.shell.forecastEnd)
    }

    @Test
    fun thereIsNoPaydayChipWithoutAnIncomeThatRepeats() {
        seed(income = false)
        // A session that chose it earlier, when there was an income, is back at thirty days.
        env.shell.forecastEnd = ForecastEnd.Payday
        show()
        assertEquals(0, count("Until payday"))
        assertEquals(1, count("Pick a date"))
        chip("30 days").assertIsSelected()
    }

    @Test
    fun aDayCanBePickedFromTomorrowOn() {
        seed()
        show()
        chip("Pick a date").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Choose a date").assertIsDisplayed()
        // Nothing before tomorrow is offered as a quick pick.
        assertEquals(0, compose.onAllNodesWithContentDescription("Today, ", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription("Yesterday, ", substring = true).fetchSemanticsNodes().size)
        compose.onNode(hasContentDescription("In a week, ${day(7)}")).performClick()
        compose.onNodeWithText("Use ${day(7)}").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            env.shell.forecastEnd == ForecastEnd.Date(today.plusDays(7))
        }
        // Seven days: 100,000 less 700 less the rent.
        banner("Forecast to ${day(7)}. You would have ₹79,300.00 to spend then. The lowest point is ₹79,300.00, on ${day(7)}.").assertIsDisplayed()
        chip("Until ${day(7)}").assertIsSelected()
        assertEquals(0, count("Pick a date"), "the chip now names the day")
    }

    @Test
    fun theDateChipSaysThatTheLookGoesAsFarAsTwelveMonths() {
        seed()
        show()
        compose.onNodeWithText("Pick a date up to 12 months ahead.").assertIsDisplayed()
    }

    // ------------------------------------------------------------------------------ everyday spending, items

    @Test
    fun everydaySpendingCanBeSwitchedOff_andTheFigureAndTheAssumptionAreInView() {
        seed()
        show()
        compose.onNodeWithText("Count everyday spending").assertIsDisplayed()
        compose.onNodeWithText(
            "About ₹100.00 a day, from what you spent in the last 90 days outside repeating payments. It is a guess: turn it off to see only what is scheduled.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Count everyday spending").performClick()
        compose.waitForIdle()
        assertFalse(env.shell.forecastEveryday)
        // Only what is scheduled: 100,000 less the rent plus the salary; the lowest is the day the rent goes.
        banner("Forecast to ${day(30)}. You would have ₹1,30,000.00 to spend then. The lowest point is ₹80,000.00, on ${day(5)}.").assertIsDisplayed()
        compose.onNodeWithText("Count everyday spending").performClick()
        compose.waitForIdle()
        banner("Forecast to ${day(30)}. You would have ₹1,27,000.00 to spend then. The lowest point is ₹78,100.00, on ${day(19)}.").assertIsDisplayed()
    }

    @Test
    fun whatIsDatedIsListedByDay() {
        seed()
        show()
        compose.onNodeWithText("Rent").assertIsDisplayed()
        compose.onNodeWithText("Employer").assertIsDisplayed()
        compose.onNodeWithText("−₹20,000.00").assertIsDisplayed()
        compose.onNodeWithText("+₹50,000.00").assertIsDisplayed()
        assertTrue(count(dayHeading(today.plusDays(5).atStartOfDay())) >= 1)
        assertTrue(count(dayHeading(today.plusDays(20).atStartOfDay())) >= 1)
        assertEquals(2, count("HDFC · Repeating"), "the rent and the salary, each said to be a repeating payment on its account")
    }

    @Test
    fun theMoneyRunningOutIsSaidInWords() {
        // Only 100 in the bank after the past spending, and 100 a day of everyday spending to come: 0 tomorrow, below zero the day after.
        seed(opening = 910_000)
        show()
        compose.onNodeWithText("The money to spend runs out on ${day(2)}.").assertIsDisplayed()
    }

    @Test
    fun withNothingScheduledItSaysSo() {
        runBlocking {
            val finance = env.container.finance
            finance.saveAccount(Account(name = "HDFC", kind = AccountKind.Bank, openingBalance = 100_000, createdAt = now, updatedAt = now))
        }
        show()
        compose.onNodeWithText("Nothing is scheduled in this time").assertIsDisplayed()
        compose.onNodeWithText("Nothing was spent outside repeating payments in the last 90 days, so it adds nothing yet.").assertIsDisplayed()
    }

    // -------------------------------------------------------------------------------------------- hidden amounts

    @Test
    fun withAmountsHiddenNoFigureIsShown_inTheBannerTheChartOrTheRows() {
        seed()
        env.preferences.setAmountsHidden(true)
        show()
        banner("Forecast to ${day(30)}. You would have ₹•••• to spend then. The lowest point is ₹••••, on ${day(19)}.").assertIsDisplayed()
        compose.onNode(
            hasContentDescription("Money you can spend, day by day to ${day(30)}. The lowest point is on ${day(19)}. Amounts are hidden."),
        ).assertIsDisplayed()
        assertEquals(0, count("−₹20,000.00"))
        assertTrue(compose.onAllNodesWithText("−₹••••").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, compose.onAllNodesWithText("20,000", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("100.00", substring = true).fetchSemanticsNodes().size)
    }
}
