package com.example.normpix.finance.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
 * What the repeating payments cost, in the app: the two-panel banner on Plans > Recurring ("A month" / "A year") that opens the
 * Repeating costs page, and that page - each expense with its amount and how often, a month, when it is next charged and a switch
 * that pauses it (nothing is deleted) or resumes it. Income, transfers and paused payments are not in the totals.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@OptIn(ExperimentalTestApi::class)
class RepeatingCostsPageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()
    private val repository get() = env.container.finance.repository

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("repeating_costs_page")
    }

    @After
    fun tearDown() = env.close()

    private fun rule(
        type: TransactionType,
        amount: Long,
        accountId: Long,
        payee: String,
        frequency: RecurrenceFrequency = RecurrenceFrequency.Monthly,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        active: Boolean = true,
        daysAhead: Long = 10,
    ) = RecurringRule(
        type = type,
        amount = amount,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        payee = payee,
        frequency = frequency,
        startDate = now.toLocalDate().plusDays(daysAhead).atStartOfDay(),
        nextDate = now.toLocalDate().plusDays(daysAhead).atStartOfDay(),
        active = active,
        createdAt = now,
        updatedAt = now,
    )

    /** Netflix (monthly 649) and Google One (yearly 1,999) cost; a salary and a SIP are not costs; a gym is paused. */
    private fun seed() = runBlocking {
        val finance = env.container.finance
        val main = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val savings = finance.saveAccount(Account(name = "Savings", kind = AccountKind.Savings, createdAt = now, updatedAt = now))
        val expense = finance.state.value.categories.first { it.type == CategoryType.Expense }.id
        val income = finance.state.value.categories.first { it.type == CategoryType.Income }.id
        finance.saveRecurringRule(rule(TransactionType.Expense, 649_00, main.id!!, "Netflix", categoryId = expense))
        finance.saveRecurringRule(rule(TransactionType.Expense, 1_999_00, main.id!!, "Google One", RecurrenceFrequency.Yearly, categoryId = expense, daysAhead = 40))
        finance.saveRecurringRule(rule(TransactionType.Income, 50_000_00, main.id!!, "Employer", categoryId = income))
        finance.saveRecurringRule(rule(TransactionType.Transfer, 5_000_00, main.id!!, "SIP", toAccountId = savings.id))
        finance.saveRecurringRule(rule(TransactionType.Expense, 199_00, main.id!!, "Gym", categoryId = expense, active = false))
    }

    private fun waitFor(text: String, unmerged: Boolean = false) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.waitForIdle()
        compose.onAllNodesWithText(text, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
    }

    private fun described(spoken: String) = compose.onNode(hasContentDescription(spoken))

    private fun starting(spoken: String) = compose.onNode(hasContentDescription(spoken, substring = true))

    private fun next(daysAhead: Long) = shortDate(now.toLocalDate().plusDays(daysAhead).atStartOfDay())

    // ------------------------------------------------------------------------------------- Plans > Recurring

    @Test
    fun theBannerOnRecurringSaysWhatTheyCostAMonthAndAYear_andOpensThePage() {
        seed()
        compose.setContent { env.Content { RecurringView() } }
        waitFor("A month", unmerged = true)
        assertTrue(compose.onAllNodesWithText("A year", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        // 7,788 + 1,999 = 9,787 a year; a twelfth of it is 815.58. Income, the SIP and the paused gym are not in it.
        val banner = described("Repeating payments cost ₹815.58 a month and ₹9,787.00 a year, across 2 payments.")
        banner.assertIsDisplayed()
        assertEquals(0, env.navigator.repeatingCostsOpened)
        // Touched on a panel (the middle of the two is the seam between them).
        banner.performTouchInput { click(Offset(width * 0.25f, height / 2f)) }
        assertEquals(1, env.navigator.repeatingCostsOpened)
    }

    @Test
    fun thereIsNoBannerWhileNothingRepeatingCostsAnything() {
        runBlocking {
            val finance = env.container.finance
            val main = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
            finance.saveRecurringRule(rule(TransactionType.Income, 50_000_00, main.id!!, "Employer", categoryId = finance.state.value.categories.first { it.type == CategoryType.Income }.id))
        }
        compose.setContent { env.Content { RecurringView() } }
        waitFor("Active")
        assertEquals(0, compose.onAllNodesWithText("A month", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun theForecastIsOnRecurringWhetherOrNotAnythingRepeats() {
        compose.setContent { env.Content { RecurringView() } }
        waitFor("Forecast")
        assertEquals(0, env.navigator.forecastOpened)
        compose.onNodeWithText("Forecast").performClick()
        assertEquals(1, env.navigator.forecastOpened)
    }

    @Test
    fun withAmountsHiddenTheBannerSaysNoFigures() {
        seed()
        env.preferences.setAmountsHidden(true)
        compose.setContent { env.Content { RecurringView() } }
        waitFor("A month", unmerged = true)
        described("Repeating payments cost ₹•••• a month and ₹•••• a year, across 2 payments.").assertIsDisplayed()
    }

    // ------------------------------------------------------------------------------------------- the page

    @Test
    fun eachExpenseIsListedWithItsAmountHowOften_aMonth_andWhenItIsNext_theDearestFirst() {
        seed()
        compose.setContent { env.Content { RepeatingCostsPage() } }
        waitFor("Biggest first")
        val netflix = described("Netflix, ₹649.00 monthly, ₹649.00 a month, ₹7,788.00 a year, Next ${next(10)}")
        val google = described("Google One, ₹1,999.00 yearly, ₹166.58 a month, ₹1,999.00 a year, Next ${next(40)}")
        netflix.assertIsDisplayed()
        google.assertIsDisplayed()
        assertTrue(
            netflix.getUnclippedBoundsInRoot().top < google.getUnclippedBoundsInRoot().top,
            "Netflix costs ₹7,788 a year and Google One ₹1,999, so it is first",
        )
        // The banner is the page's too, and income and transfers are not costs.
        described("Repeating payments cost ₹815.58 a month and ₹9,787.00 a year, across 2 payments.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithContentDescription("Employer", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithContentDescription("SIP", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun aPausedPaymentIsListedAfterThem_dimmed_andSaidToBePaused() {
        seed()
        compose.setContent { env.Content { RepeatingCostsPage() } }
        waitFor("Paused")
        val gym = described("Gym, ₹199.00 monthly, ₹199.00 a month, ₹2,388.00 a year, Paused")
        gym.assertIsDisplayed()
        compose.onNode(hasContentDescription("Active: Gym")).assertIsOff()
        compose.onNode(hasContentDescription("Active: Netflix")).assertIsOn()
        assertTrue(
            described("Google One, ₹1,999.00 yearly, ₹166.58 a month, ₹1,999.00 a year, Next ${next(40)}").getUnclippedBoundsInRoot().top <
                gym.getUnclippedBoundsInRoot().top,
        )
    }

    @Test
    fun pausingKeepsThePaymentAndTakesItOutOfTheTotals_resumingBringsItBack() {
        seed()
        compose.setContent { env.Content { RepeatingCostsPage() } }
        waitFor("Biggest first")
        val before = runBlocking { repository.recurringRules() }

        compose.onNode(hasContentDescription("Active: Netflix")).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            runBlocking { repository.recurringRules() }.first { it.payee == "Netflix" }.active.not()
        }
        compose.waitForIdle()
        // Nothing was deleted: the same rules, Netflix among them, only inactive.
        val paused = runBlocking { repository.recurringRules() }
        assertEquals(before.size, paused.size)
        assertTrue(paused.first { it.payee == "Netflix" }.let { !it.active && it.amount == 649_00L })
        // It is off in the list, out of the totals (Google One alone: 1,999 a year, 166.58 a month), and says it is paused.
        compose.onNode(hasContentDescription("Active: Netflix")).assertIsOff()
        described("Repeating payments cost ₹166.58 a month and ₹1,999.00 a year, across 1 payment.").assertIsDisplayed()
        starting("Netflix, ₹649.00 monthly, ₹649.00 a month, ₹7,788.00 a year, Paused").assertIsDisplayed()

        compose.onNode(hasContentDescription("Active: Netflix")).performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            runBlocking { repository.recurringRules() }.first { it.payee == "Netflix" }.active
        }
        compose.waitForIdle()
        val resumed = runBlocking { repository.recurringRules() }.first { it.payee == "Netflix" }
        // It carries on from its next day: what was missed while it was paused is skipped, not recorded.
        assertTrue(resumed.nextDate.isAfter(LocalDateTime.now()), "resumed on ${resumed.nextDate}")
        compose.onNode(hasContentDescription("Active: Netflix")).assertIsOn()
        described("Repeating payments cost ₹815.58 a month and ₹9,787.00 a year, across 2 payments.").assertIsDisplayed()
        // And no payment was recorded by pausing and resuming.
        assertEquals(0, runBlocking { repository.transactions(TransactionQuery()).items.size })
    }

    @Test
    fun anEmptyPageInvitesAddingOne() {
        compose.setContent { env.Content { RepeatingCostsPage() } }
        waitFor("No repeating costs")
        compose.onNodeWithText("Add recurring payment").assertIsDisplayed()
    }

    @Test
    fun withAmountsHiddenTheRowsSaySoToo() {
        seed()
        env.preferences.setAmountsHidden(true)
        compose.setContent { env.Content { RepeatingCostsPage() } }
        waitFor("Biggest first")
        described("Netflix, ₹•••• monthly, ₹•••• a month, ₹•••• a year, Next ${next(10)}").assertIsDisplayed()
        assertNotNull(compose.onAllNodesWithText("₹••••", useUnmergedTree = true).fetchSemanticsNodes().firstOrNull())
        assertEquals(0, compose.onAllNodesWithText("649", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
