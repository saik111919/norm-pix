package com.example.normpix.finance.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A repeating payment is read as the entries it records are: the same row (avatar, title, details with a repeat mark, the
 * amount in its type's colour, a small note under it) for an expense, an income and a transfer - the note being when it is next
 * due, or that it is paused or has ended.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@OptIn(ExperimentalTestApi::class)
class RecurringRowsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("recurring_rows")
    }

    @After
    fun tearDown() = env.close()

    private fun rule(type: TransactionType, amount: Long, accountId: Long, toAccountId: Long? = null, categoryId: Long? = null, payee: String? = null, active: Boolean = true) =
        RecurringRule(
            type = type,
            amount = amount,
            accountId = accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
            payee = payee,
            frequency = RecurrenceFrequency.Monthly,
            startDate = now.toLocalDate().plusDays(5).atStartOfDay(),
            nextDate = now.toLocalDate().plusDays(5).atStartOfDay(),
            active = active,
            createdAt = now,
            updatedAt = now,
        )

    private fun seed() = runBlocking {
        val finance = env.container.finance
        val main = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val savings = finance.saveAccount(Account(name = "Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val expense = finance.state.value.categories.first { it.type == CategoryType.Expense }
        val income = finance.state.value.categories.first { it.type == CategoryType.Income }
        finance.saveRecurringRule(rule(TransactionType.Expense, 649_00, main.id!!, categoryId = expense.id, payee = "Netflix"))
        finance.saveRecurringRule(rule(TransactionType.Income, 50_000_00, main.id!!, categoryId = income.id, payee = "Employer"))
        finance.saveRecurringRule(rule(TransactionType.Transfer, 5_000_00, main.id!!, toAccountId = savings.id))
        finance.saveRecurringRule(rule(TransactionType.Expense, 199_00, main.id!!, categoryId = expense.id, payee = "Gym", active = false))
    }

    private fun show() {
        compose.setContent { env.Content { RecurringView() } }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Active").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun row(startsWith: String) = compose.onNode(hasContentDescription(startsWith, substring = true))

    @Test
    fun anExpenseAnIncomeAndATransferAreReadTheWayTheirTransactionsAre() {
        seed()
        show()
        row("Expense, ₹649.00, Netflix, repeating").assertIsDisplayed()
        row("Income, ₹50,000.00, Employer, repeating").assertIsDisplayed()
        // A transfer names where the money goes, as a transfer in the history does.
        row("Transfer, ₹5,000.00, Transfer, repeating, Monthly, Main → Savings").assertIsDisplayed()
    }

    @Test
    fun theNoteUnderTheAmountSaysWhenItIsNextDue_orThatItIsPaused() {
        seed()
        show()
        // A row is read as one sentence, which ends with where it stands.
        row("Expense, ₹199.00, Gym, repeating").assertIsDisplayed()
        compose.onNode(hasContentDescription("Expense, ₹199.00, Gym, repeating", substring = true) and hasContentDescription(", Paused", substring = true))
            .assertExists()
        val live = compose.onAllNodes(hasContentDescription(", repeating,", substring = true) and hasContentDescription(", Next ", substring = true))
            .fetchSemanticsNodes()
        kotlin.test.assertEquals(3, live.size, "the three live payments each say when they are next due")
    }
}
