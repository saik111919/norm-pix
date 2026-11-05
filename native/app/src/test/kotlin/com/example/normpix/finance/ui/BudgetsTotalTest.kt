package com.example.normpix.finance.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
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

/** Plan > Budgets adds every category's budget together, so what is set aside across the categories is one clear figure. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@OptIn(ExperimentalTestApi::class)
class BudgetsTotalTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("budgets_total")
    }

    @After
    fun tearDown() = env.close()

    private fun seed(overall: Long? = null) = runBlocking {
        val finance = env.container.finance
        val account = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val month = MonthKey.of(now)
        val food = finance.state.value.categories.first { it.name == "Food & dining" && it.type == CategoryType.Expense }
        val transport = finance.state.value.categories.first { it.name == "Transport" && it.type == CategoryType.Expense }
        finance.setBudget(categoryId = food.id, from = month, amount = 10_000_00)
        finance.setBudget(categoryId = transport.id, from = month, amount = 5_000_00)
        if (overall != null) finance.setBudget(categoryId = null, from = month, amount = overall)
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 2_500_00,
                accountId = account.id!!,
                categoryId = food.id,
                occurredAt = LocalDate.now().atStartOfDay().plusMinutes(1),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun show() {
        compose.setContent { env.Content { BudgetsView() } }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("By category").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theCategoryBudgetsAreAddedIntoOneTotalWithWhatWasSpentInThem() {
        seed()
        show()
        compose.onNodeWithText("All category budgets").assertIsDisplayed()
        compose.onNodeWithText("2 category budgets").assertIsDisplayed()
        // 10,000 + 5,000 budgeted; 2,500 spent in them.
        compose.onNodeWithText("Budgeted").assertIsDisplayed()
        compose.onNodeWithText("Spent").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("₹15,000", substring = true).fetchSemanticsNodes().isNotEmpty(), "the combined limit")
        assertTrue(compose.onAllNodesWithText("₹2,500", substring = true).fetchSemanticsNodes().isNotEmpty(), "the spending in those categories")
        // The bar says it in one sentence for a screen reader: the limit, what was spent, what is left, how much is used.
        compose.onNode(hasContentDescription("Budget ₹15,000.00, spent ₹2,500.00, ₹12,500.00 left · 16% used")).assertExists()
    }

    @Test
    fun aCategoryOverItsBudgetIsCountedEvenWhenTheWholeIsOnTrack() {
        seed()
        // Food has 10,000 and has 2,500 spent; 9,000 more takes it to 11,500 while the two budgets together are far from used.
        runBlocking {
            val finance = env.container.finance
            val account = finance.state.value.accounts.first()
            val food = finance.state.value.categories.first { it.name == "Food & dining" && it.type == CategoryType.Expense }
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 9_000_00,
                    accountId = account.id!!,
                    categoryId = food.id,
                    occurredAt = LocalDate.now().atStartOfDay().plusMinutes(2),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        show()
        compose.onNodeWithText("2 category budgets · 1 over budget").assertIsDisplayed()
    }

    @Test
    fun withAnOverallBudgetTooItSaysWhenTheCategoriesAddUpToMore() {
        seed(overall = 12_000_00)
        show()
        compose.onNodeWithText("All category budgets").assertIsDisplayed()
        compose.onNodeWithText("The category budgets add up to more than your overall budget of ₹12,000.", substring = true).assertIsDisplayed()
    }
}
