package com.example.normpix.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.monthLabel
import com.example.normpix.finance.ui.CategorySpend
import com.example.normpix.screens.TopSpendingStack
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's Top spending as a stack of the month's biggest categories - the biggest in front - and what tapping the front
 * one shows: the month's figures in a sheet, with a way on to every transaction in it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CategoryStackTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withNano(0)
    private val month = MonthKey.of(now)
    private val finance get() = env.container.finance

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("category_stack_test")
    }

    @After
    fun tearDown() = env.close()

    private var shoppingId = 0L

    /**
     * Shopping Rs 2,002 over two purchases (it was Rs 1,000 last month) and Groceries Rs 1,078 over one, recorded and then
     * summed up the way Home does: the biggest first.
     */
    private fun seedSpending(): List<CategorySpend> = runBlocking {
        finance.load()
        val bank = finance.saveAccount(Account(name = "Bank", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val categories = finance.state.value.categories.filter { it.type == CategoryType.Expense }
        val shopping = categories.first { it.name == "Shopping" }
        val groceries = categories.first { it.name == "Groceries" }
        shoppingId = shopping.id!!

        fun spend(amount: Long, payee: String, categoryId: Long?, at: LocalDateTime) = runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = amount,
                    accountId = bank.id!!,
                    categoryId = categoryId,
                    payee = payee,
                    occurredAt = at,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        spend(1_402_00, "Amazon", shopping.id, now.minusMinutes(5))
        spend(600_00, "Flipkart", shopping.id, now.minusMinutes(10))
        spend(1_078_00, "DMart", groceries.id, now.minusMinutes(15))
        spend(1_000_00, "Myntra", shopping.id, now.minusMonths(1))

        val total = 2_002_00L + 1_078_00L
        listOf(
            CategorySpend(shopping.id!!, finance.state.value.category(shopping.id), month, 2_002_00, 2, 2_002_00.toDouble() / total, 1_000_00, null),
            CategorySpend(groceries.id!!, finance.state.value.category(groceries.id), month, 1_078_00, 1, 1_078_00.toDouble() / total, 0, null),
        )
    }

    /** Home's Top spending block on its own; composing the whole page here only races its background load. */
    private fun showTopSpending(spends: List<CategorySpend>) {
        compose.setContent { env.Content { TopSpendingStack(spends, onSeeAll = {}) } }
        compose.waitForIdle()
    }

    private fun front(category: String, amount: String) =
        compose.onNodeWithContentDescription("$category, $amount spent in", substring = true)

    @Test
    fun theBiggestCategoryIsInFront_andTheNextOnesAreOnlyDepth() {
        showTopSpending(seedSpending())

        front("Shopping", "₹2,002.00").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Groceries, ₹1,078.00 spent in", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Top spending").assertIsDisplayed()
    }

    @Test
    fun theFrontCardSaysWhereItStandsInTheStack() {
        showTopSpending(seedSpending())

        // Shopping is the biggest of two, and was Rs 1,000 last month: up 100%.
        compose.onNodeWithContentDescription("100% more than ${monthLabel(month.previous)}, 1 of 2", substring = true).assertExists()
    }

    @Test
    fun withoutSpendingThereIsNoTopSpending() {
        runBlocking { finance.load() }
        showTopSpending(emptyList())

        compose.onAllNodesWithText("Top spending").assertCountEquals(0)
    }

    @Test
    fun tappingTheFrontCardOpensItsMonthInASheet() {
        showTopSpending(seedSpending())

        front("Shopping", "₹2,002.00").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Spent in ${monthLabel(month)}").fetchSemanticsNodes().isNotEmpty() }

        // Shopping has ready-made sub-categories, so the sheet also says the whole of it was spent directly in Shopping.
        compose.onAllNodesWithText("₹2,002.00")[0].assertIsDisplayed()
        compose.onNodeWithText("Directly in Shopping").assertExists()
        compose.onNodeWithText("100% more than ${monthLabel(month.previous)}").assertIsDisplayed()
        compose.onNodeWithText("See all in ${monthLabel(month)}").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Latest").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun seeAllClosesTheSheetAndOpensThatCategorysMonth() {
        showTopSpending(seedSpending())

        front("Shopping", "₹2,002.00").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("See all in ${monthLabel(month)}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("See all in ${monthLabel(month)}").performClick()
        compose.waitUntil(10_000) { env.navigator.categoryHistories.isNotEmpty() }

        assertEquals(listOf(shoppingId to month), env.navigator.categoryHistories)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("See all in ${monthLabel(month)}").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun swipingTheFrontCardBringsTheNextCategoryForward() {
        showTopSpending(seedSpending())

        front("Shopping", "₹2,002.00").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        front("Groceries", "₹1,078.00").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Shopping, ₹2,002.00 spent in", substring = true).assertCountEquals(0)
    }

    @Test
    fun pressingAndHoldingUnfoldsEveryCategory_andStackGathersThemAgain() {
        showTopSpending(seedSpending())
        compose.onAllNodesWithContentDescription("Groceries, ₹1,078.00 spent in", substring = true).assertCountEquals(0)

        front("Shopping", "₹2,002.00").performTouchInput { longClick() }
        compose.waitForIdle()
        front("Shopping", "₹2,002.00").assertIsDisplayed()
        front("Groceries", "₹1,078.00").assertIsDisplayed()

        compose.onNodeWithContentDescription("Stack them again").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Groceries, ₹1,078.00 spent in", substring = true).assertCountEquals(0)
    }
}
