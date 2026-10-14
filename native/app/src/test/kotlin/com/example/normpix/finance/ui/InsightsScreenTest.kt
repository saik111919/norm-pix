package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

/** Insights reads as a story - snapshot, trend, categories, what changed - and says what it measures. */
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
        // 1,500 against 1,300 over the same days: 15% more - and the hero's pill says what it is compared with.
        compose.onNodeWithText("15% more than this time last month").assertIsDisplayed()
        compose.onNode(hasText("Amounts in Indian rupee", substring = true)).assertIsDisplayed()
    }

    @Test
    fun theHeroChartsBothPeriodsOnOneScale() {
        val seed = seed()
        val lastMonth = MonthKey.of(now).previous
        record(seed, TransactionType.Expense, seed.food, 150_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Expense, seed.food, 130_000, lastMonth.start.plusHours(2))
        show()
        compose.waitForIdle()

        // One element speaks both lines of the pace chart, so a screen reader hears the comparison rather than two loose amounts.
        compose.onNodeWithContentDescription("This month ₹1,500.00, Same days of ${monthLabel(lastMonth)} ₹1,300.00").assertIsDisplayed()
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
    fun whatChangedNamesTheLargestIncreaseAndDecrease() {
        val seed = seed()
        val lastMonth = MonthKey.of(now).previous
        record(seed, TransactionType.Expense, seed.food, 100_000, LocalDate.now().atStartOfDay().plusMinutes(1))
        record(seed, TransactionType.Expense, seed.transport, 50_000, LocalDate.now().atStartOfDay().plusMinutes(2))
        record(seed, TransactionType.Expense, seed.food, 40_000, lastMonth.start.plusHours(2))
        record(seed, TransactionType.Expense, seed.transport, 90_000, lastMonth.start.plusHours(3))
        show()
        compose.waitForIdle()

        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(hasText("What changed"))
        compose.onNodeWithText("What changed").assertIsDisplayed()
        compose.onAllNodesWithText("Largest increase", substring = true).onFirst().assertExists()
        compose.onAllNodesWithText("Largest decrease", substring = true).onFirst().assertExists()
        // Food rose 600 and Transport fell 400.
        assertTrue(compose.onAllNodesWithText("₹600.00 more", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("₹400.00 less", substring = true).fetchSemanticsNodes().isNotEmpty())
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
