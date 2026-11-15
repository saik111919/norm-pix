package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.data.repeatSuggestions
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
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

/**
 * Payees that look like a repeating payment are offered on Plans > Recurring: a card under the banner saying what was seen, with Add
 * (the repeating-payment form, filled in) and Not this one (remembered for good).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@OptIn(ExperimentalTestApi::class)
class RepeatSuggestionCardsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()
    private val today = LocalDate.now()
    private val repository get() = env.container.finance.repository

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("repeat_suggestion_cards")
    }

    @After
    fun tearDown() = env.close()

    /** Netflix paid on the same account every 30 days, the newest [newestAgo] days ago; and a coffee that was a one-off. */
    private fun seed(newestAgo: Long = 10): Account = runBlocking {
        val finance = env.container.finance
        val bank = finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, openingBalance = 5_000_000, createdAt = now, updatedAt = now))
        val category = finance.state.value.categories.first { it.name == "Entertainment" }
        for (i in 0 until 4) {
            val at = today.minusDays(newestAgo + 30L * (3 - i)).atTime(21, 0)
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 649_00,
                    accountId = bank.id!!,
                    categoryId = category.id,
                    occurredAt = at,
                    payee = "Netflix",
                    createdAt = at,
                    updatedAt = at,
                ),
            )
        }
        val at = today.minusDays(3).atTime(9, 0)
        finance.saveTransaction(
            FinanceTransaction(type = TransactionType.Expense, amount = 90_00, accountId = bank.id!!, categoryId = category.id, occurredAt = at, payee = "Chai Point", createdAt = at, updatedAt = at),
        )
        bank
    }

    private fun show() {
        compose.setContent { env.Content { RecurringView() } }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Forecast").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForCard() = compose.waitUntil(timeoutMillis = 10_000) {
        compose.waitForIdle()
        compose.onAllNodesWithText("Looks like a repeating payment").fetchSemanticsNodes().isNotEmpty()
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun aPayeePaidOnARhythmIsOfferedInTheAppsWordsAndMoney() {
        seed()
        show()
        waitForCard()
        val nextExpected = shortDate(today.minusDays(10).plusMonths(1).atStartOfDay())
        compose.onNode(
            hasContentDescription("Looks like a repeating payment: Netflix, ₹649.00 every month, seen 4 times. Next one expected $nextExpected."),
        ).assertIsDisplayed()
        compose.onNodeWithText("Netflix").assertIsDisplayed()
        compose.onNodeWithText("₹649.00 every month · seen 4 times").assertIsDisplayed()
        // The coffee was paid once: no rhythm, no card.
        assertEquals(0, count("Chai Point"))
        assertEquals(1, count("Add"))
        assertEquals(1, count("Not this one"))
    }

    @Test
    fun nothingIsOfferedWhenNothingFitsARhythm() {
        runBlocking {
            val finance = env.container.finance
            val bank = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
            val category = finance.state.value.categories.first { it.name == "Entertainment" }
            // Netflix twice, and not on a rhythm that has gone on long enough.
            for (daysAgo in listOf(40L, 10L)) {
                val at = today.minusDays(daysAgo).atTime(21, 0)
                finance.saveTransaction(
                    FinanceTransaction(type = TransactionType.Expense, amount = 649_00, accountId = bank.id!!, categoryId = category.id, occurredAt = at, payee = "Netflix", createdAt = at, updatedAt = at),
                )
            }
        }
        show()
        assertEquals(0, count("Looks like a repeating payment"))
        assertEquals(0, count("These look like repeating payments"))
        assertEquals(0, count("Not this one"))
    }

    @Test
    fun addOpensTheRepeatingPaymentFormFilledIn_andSavingItEndsTheSuggestion() {
        val bank = seed()
        show()
        waitForCard()
        compose.onNodeWithText("Add").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New recurring expense").assertIsDisplayed()
        // What the payments say: the payee, the amount, how often, on the account and category of the newest one, from the next day.
        compose.onNode(hasText("Netflix") and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertIsDisplayed()
        compose.onNode(hasContentDescription("Amount in INR") and hasText("649", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("Monthly") and radio).assertIsSelected()
        compose.onNodeWithText("HDFC Savings").assertIsDisplayed()
        compose.onNodeWithText("Entertainment").assertIsDisplayed()
        // The newest payment was 10 days ago, so the next is expected in 20 days: that day is the first payment.
        val first = today.minusDays(10).plusMonths(1)
        compose.onNodeWithContentDescription("First payment, ${shortDate(first.atStartOfDay())}").assertExists()
        // Nothing is saved until the form is.
        assertTrue(runBlocking { repository.recurringRules() }.isEmpty())

        compose.onNodeWithText("Save recurring expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            runBlocking { repository.recurringRules() }.isNotEmpty()
        }
        val saved = runBlocking { repository.recurringRules() }.single()
        assertEquals("Netflix", saved.payee)
        assertEquals(649_00, saved.amount)
        assertEquals(RecurrenceFrequency.Monthly, saved.frequency)
        assertEquals(bank.id, saved.accountId)
        assertEquals(first.atStartOfDay(), saved.nextDate)
        // A rule for the payee now exists, so it is not suggested again.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            count("Looks like a repeating payment") == 0
        }
    }

    @Test
    fun aSuggestionWhoseDayHasGoneByStartsOnTheNextOne() {
        // The newest payment was 33 days ago: its next, a month on, was 3 days ago, so the form starts on the one after.
        seed(newestAgo = 33)
        show()
        waitForCard()
        compose.onNodeWithText("Add").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("First payment, ${shortDate(today.minusDays(33).plusMonths(2).atStartOfDay())}").assertExists()
    }

    @Test
    fun notThisOneRemovesItForGood() {
        seed()
        show()
        waitForCard()
        compose.onNodeWithText("Not this one").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            count("Looks like a repeating payment") == 0
        }
        assertEquals(setOf("netflix"), runBlocking { repository.dismissedRepeatPayees() })
        // It says so ...
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            count("Won't suggest Netflix again") > 0
        }
        // ... and a new look at the page (leaving and coming back) does not bring it back, nor does more being recorded.
        assertEquals(0, runBlocking { repository.repeatSuggestions(today) }.size)
        runBlocking {
            val finance = env.container.finance
            val bank = finance.state.value.accounts.first { it.name == "HDFC Savings" }
            val category = finance.state.value.categories.first { it.name == "Entertainment" }
            val at = today.atTime(8, 0)
            finance.saveTransaction(
                FinanceTransaction(type = TransactionType.Expense, amount = 649_00, accountId = bank.id!!, categoryId = category.id, occurredAt = at, payee = "Netflix", createdAt = at, updatedAt = at),
            )
        }
        assertEquals(0, runBlocking { repository.repeatSuggestions(today) }.size)
        assertEquals(0, count("Looks like a repeating payment"))
    }
}
