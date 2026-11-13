package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.settings.InsightsCategoriesLayout
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
 * Insights reads as a story - the answer and how it compares, how it built up, what it is made of with what changed in each, who it
 * was for, the months around it - and says what it measures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class InsightsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("insights_screen")
    }

    @After
    fun tearDown() = env.close()

    private data class Seed(val account: Account, val food: Category, val transport: Category, val salary: Category)

    private fun seed(): Seed = runBlocking {
        val finance = env.container.finance
        val account = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        suspend fun category(name: String, type: CategoryType) =
            finance.saveCategory(Category(name = name, type = type, icon = "category", createdAt = now, updatedAt = now))
        Seed(
            account,
            category("Food", CategoryType.Expense),
            category("Transport", CategoryType.Expense),
            category("Salary", CategoryType.Income),
        )
    }

    private fun record(seed: Seed, type: TransactionType, category: Category, amount: Long, at: LocalDateTime) = runBlocking {
        env.container.finance.saveTransaction(
            FinanceTransaction(
                type = type,
                amount = amount,
                accountId = seed.account.id!!,
                categoryId = category.id,
                occurredAt = at,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun show() {
        compose.setContent { env.Content { InsightsScreen() } }
        compose.waitForIdle()
        // The figures come from a query on the database thread, which the test clock does not wait for: under the load of a
        // whole run the screen can still be loading when the first idle comes, so wait for it to say something.
        compose.waitUntil(timeoutMillis = 30_000) {
            listOf("Spent this month", "Nothing recorded in", "Nothing to show yet").any { text ->
                compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun withNoDataItInvitesTheFirstEntry() {
        show()
        compose.waitForIdle()
        compose.onNodeWithText("Nothing to show yet").assertIsDisplayed()
        compose.onNodeWithText("Add expense").assertIsDisplayed()
    }

    @Test
    fun theSnapshotNamesWhatIsMeasuredAndWhatItIsComparedWith() {
        val seed = seed()
        val thisMonth = MonthKey.of(now)
        val lastMonth = thisMonth.previous
        // This month so far: Food 1,000 + Transport 500. The same days of last month: Food 400 + Transport 900.
        record(seed, TransactionType.Expense, seed.food, 100_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Expense, seed.transport, 50_000, LocalDate.now().atStartOfDay().plusMinutes(2))
        record(seed, TransactionType.Income, seed.salary, 500_000, LocalDate.now().atStartOfDay().plusMinutes(3))
        record(seed, TransactionType.Expense, seed.food, 40_000, lastMonth.start.plusHours(2))
        record(seed, TransactionType.Expense, seed.transport, 90_000, lastMonth.start.plusHours(3))
        show()
        compose.waitForIdle()

        compose.onNodeWithText("Spent this month").assertIsDisplayed()
        compose.onNodeWithText("₹1,500.00").assertIsDisplayed()
        // 1,500 against 1,300 over the same days: 200 more, 15% - the verdict as a sentence, saying what it is compared with.
        compose.onNodeWithText("₹200 more than this time last month (15%)").assertIsDisplayed()
        compose.onNode(hasText("Amounts in Indian rupee", substring = true)).assertExists()
    }

    @Test
    fun theVerdictIsASentenceWithTheDifferenceInMoney() {
        val money = MoneyFormatter(com.example.normpix.finance.domain.CurrencySpec.INR)
        assertEquals("₹4,100 less than this time last month (7%)", verdict(-0.07, -410_000, "this time last month", money))
        assertEquals("₹200 more than last month (15%)", verdict(0.15, 20_000, "last month", money))
        assertEquals("About the same as last month", verdict(0.001, 100, "last month", money), "a gap too small to say is not said")
        assertEquals("About the same as last month", verdict(0.0, 0, "last month", money))
    }

    @Test
    fun thePaceChartIsAPanelOfItsOwn_chartsBothPeriodsOnOneScale_andSpeaksThemAsOne() {
        val seed = seed()
        val lastMonth = MonthKey.of(now).previous
        record(seed, TransactionType.Expense, seed.food, 150_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Expense, seed.food, 130_000, lastMonth.start.plusHours(2))
        show()
        compose.waitForIdle()

        // Not squeezed into the hero any more: a titled panel under it, with the dashed line explained.
        compose.onNodeWithText("How this month built up").assertExists()
        compose.onNode(hasText("The solid line is this month; the dashed one is last month, day for day.", substring = true)).assertExists()
        // One element speaks both lines of the pace chart, so a screen reader hears the comparison rather than two loose amounts.
        compose.onNodeWithContentDescription("This month ₹1,500.00, Same days of ${monthLabel(lastMonth)} ₹1,300.00").assertExists()
    }

    @Test
    fun thisMonthComesBeforeTheMonthsAroundIt() {
        val seed = seed()
        record(seed, TransactionType.Expense, seed.food, 100_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Expense, seed.food, 40_000, MonthKey.of(now).addMonths(-1).start.plusHours(2))
        record(seed, TransactionType.Expense, seed.food, 40_000, MonthKey.of(now).addMonths(-2).start.plusHours(2))
        show()
        compose.waitForIdle()

        fun top(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        val order = listOf("Spent this month", "How this month built up", "Where your money went", "Spending by month", "More detail")
        assertEquals(order, order.sortedBy { top(it) }, "the answer, how it built up, what it is made of, then the months around it, then the detail")
    }

    @Test
    fun categoriesAreRankedWithTheirShareAndOpenTheirTransactions() {
        val seed = seed()
        record(seed, TransactionType.Expense, seed.food, 100_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Expense, seed.transport, 50_000, LocalDate.now().atStartOfDay().plusMinutes(2))
        show()
        compose.waitForIdle()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Where your money went"))
        compose.onNodeWithText("Where your money went").assertIsDisplayed()
        compose.onNodeWithText("Food is your biggest spend: 67% of ${monthLabel(MonthKey.of(now))}.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Food, ₹1,000.00, 67%, 1 transaction. Show transactions").assertHasClickAction()
        compose.onNodeWithContentDescription("Transport, ₹500.00, 33%, 1 transaction. Show transactions").assertHasClickAction()
    }

    @Test
    fun theListStaysTheDefaultAndTheCarouselIsAChoice() {
        val seed = seed()
        record(seed, TransactionType.Expense, seed.food, 100_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Expense, seed.transport, 50_000, LocalDate.now().atStartOfDay().plusMinutes(2))
        assertEquals(InsightsCategoriesLayout.List, env.preferences.state.value.insightsCategoriesLayout, "no change for anyone who never chose")
        env.preferences.setInsightsCategoriesLayout(InsightsCategoriesLayout.Carousel)
        show()
        compose.waitForIdle()

        // The carousel scrolls sideways inside the page: the page is the first scrollable.
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Where your money went"))
        compose.onNodeWithText("Food is your biggest spend: 67% of ${monthLabel(MonthKey.of(now))}.").assertIsDisplayed()
        // The carousel's panel for the category in front (its button says how many transactions), and no ranked row.
        assertTrue(compose.onAllNodesWithText("1 transaction").fetchSemanticsNodes().isNotEmpty(), "the panel's button")
        assertTrue(
            compose.onAllNodesWithContentDescription("Food, ₹1,000.00, 67%, 1 transaction. Show transactions").fetchSemanticsNodes().isEmpty(),
            "not the list",
        )
        compose.onNodeWithText("1 transaction").performClick()
        compose.waitForIdle()
        assertEquals(listOf(seed.food.id!! to MonthKey.of(now)), env.navigator.categoryHistories, "opens the category's month")
    }

    @Test
    fun theDetailIsOneTapAwayAndCollapsesAgain() {
        val seed = seed()
        record(seed, TransactionType.Expense, seed.food, 100_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Income, seed.salary, 500_000, LocalDate.now().atStartOfDay().plusMinutes(2))
        show()
        compose.waitForIdle()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("More detail"))
        // Collapsed: the row announces its state and neither block is on screen.
        compose.onNode(hasText("More detail") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed")).assertExists()
        assertTrue(compose.onAllNodesWithText("Income sources").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Day by day").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithText("More detail").performClick()
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Income sources"))
        compose.onNodeWithText("Income sources").assertIsDisplayed()
        compose.onNode(hasText("More detail") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded")).assertExists()

        compose.onNodeWithText("More detail").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(compose.onAllNodesWithText("Income sources").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun eachCategorySaysWhatChangedInIt_inTheRowItIsAlreadyIn() {
        val seed = seed()
        val lastMonth = MonthKey.of(now).previous
        record(seed, TransactionType.Expense, seed.food, 100_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Expense, seed.transport, 50_000, LocalDate.now().atStartOfDay().plusMinutes(2))
        record(seed, TransactionType.Expense, seed.food, 40_000, lastMonth.start.plusHours(2))
        record(seed, TransactionType.Expense, seed.transport, 90_000, lastMonth.start.plusHours(3))
        show()
        compose.waitForIdle()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Where your money went"))
        // Food rose 600 and Transport fell 400, said in each category's own row and to a screen reader with the rest of it.
        compose.onNodeWithContentDescription("Food, ₹1,000.00, 67%, 1 transaction, ₹600.00 more than this time last month. Show transactions").assertHasClickAction()
        compose.onNodeWithContentDescription("Transport, ₹500.00, 33%, 1 transaction, ₹400.00 less than this time last month. Show transactions").assertHasClickAction()
        // And no second list of the same categories under another name (both researchers found it, 2026-11-13).
        assertTrue(compose.onAllNodesWithText("What changed").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Largest increase", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aFirstMonthHasNothingToCompareSoNoRowSaysNew() {
        val seed = seed()
        record(seed, TransactionType.Expense, seed.food, 100_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        show()
        compose.waitForIdle()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Where your money went"))
        compose.onNodeWithContentDescription("Food, ₹1,000.00, 100%, 1 transaction. Show transactions").assertHasClickAction()
        assertTrue(compose.onAllNodesWithText("New").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aMonthWithNothingSaysSoAndStillShowsTheTrend() {
        val seed = seed()
        record(seed, TransactionType.Expense, seed.food, 100_000, MonthKey.of(now).addMonths(-3).start.plusHours(5))
        show()
        compose.waitForIdle()

        compose.onNodeWithText("Nothing recorded in ${monthLabelFull(MonthKey.of(now))}").assertIsDisplayed()
        compose.onNodeWithText("Spending by month").assertExists()
    }
}
