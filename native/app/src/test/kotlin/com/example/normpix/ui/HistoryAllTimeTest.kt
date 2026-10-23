package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
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
 * The month is only a filter over one history: Home's "See all" opens everything, the history can be narrowed to a
 * month and widened again, and stepping between months never hides an older transaction for good.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HistoryAllTimeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "history_all_time_test")
    private val now = LocalDateTime.now().withNano(0)

    @Before
    fun seed() = runBlocking {
        val finance = env.container.finance
        val state = finance.state.value
        val category = state.categoriesOf(CategoryType.Expense).first()
        val account = state.defaultAccount!!
        fun spend(payee: String, amount: Long, at: LocalDateTime) = runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = amount,
                    accountId = account.id!!,
                    categoryId = category.id,
                    occurredAt = at,
                    payee = payee,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        spend("Coffee shop", 12_000, now.minusMinutes(5))
        spend("Old rent", 900_000, now.minusDays(75))
        spend("Last year trip", 500_000, now.minusDays(400))
        Unit
    }

    @After
    fun tearDown() = env.close()

    /** A transaction row announces itself ("Expense, ₹120.00, Coffee shop, Food & dining, Cash, 6 Oct"), so find it by its payee. */
    private fun rows(payee: String) = compose.onAllNodesWithContentDescription(payee, substring = true)

    private fun count(payee: String) = rows(payee).fetchSemanticsNodes().size

    private fun waitFor(payee: String) = compose.waitUntil(10_000) { count(payee) > 0 }

    private fun waitGone(payee: String) = compose.waitUntil(10_000) { count(payee) == 0 }

    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun openHistoryFromHome() {
        compose.setContent { NormPixRoot(container = env.container) }
        waitFor("Coffee shop")
        compose.onAllNodesWithText("See all").onFirst().performClick()
        waitForText("By month")
    }

    @Test
    fun homeSeeAllOpensTheWholeHistory_notJustThisMonth() {
        openHistoryFromHome()

        compose.onNodeWithText("All time").assertIsDisplayed()
        waitFor("Old rent")
        waitFor("Last year trip")
        rows("Coffee shop").onFirst().assertIsDisplayed()
    }

    @Test
    fun theHistoryCanBeNarrowedToAMonthAndWidenedAgain() {
        openHistoryFromHome()
        waitFor("Old rent")

        compose.onNodeWithText("By month").performClick()
        waitGone("Old rent")
        assertEquals(0, count("Last year trip"))
        rows("Coffee shop").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous month").assertIsDisplayed()

        compose.onNodeWithText("All time").performClick()
        waitFor("Old rent")
        waitFor("Last year trip")
    }

    @Test
    fun steppingBackThroughMonthsFindsTheOlderTransactionsStillThere() {
        openHistoryFromHome()
        compose.onNodeWithText("By month").performClick()
        waitGone("Old rent")

        val back = MonthKey.of(now).let { current ->
            val old = MonthKey.of(now.minusDays(75))
            generateSequence(old) { it.next }.takeWhile { it.isBefore(current) }.count()
        }
        assertTrue(back >= 2, "the old transaction is at least two months back")
        repeat(back) {
            compose.onNodeWithContentDescription("Previous month").performClick()
            compose.waitForIdle()
        }
        waitFor("Old rent")
        assertEquals(0, count("Coffee shop"), "that month shows that month")

        // Nothing was removed on the way: everything is still one tap from the whole history.
        compose.onNodeWithText("All time").performClick()
        waitFor("Coffee shop")
        waitFor("Last year trip")
    }

    @Test
    fun homeInsightsActionOpensTheInsightsScreen() {
        compose.setContent { NormPixRoot(container = env.container) }
        waitFor("Coffee shop")
        compose.onNode(hasText("Insights") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        waitForText("Spent this month")
    }

    @Test
    fun homeMonthSummaryOpensTheInsightsScreen() {
        compose.setContent { NormPixRoot(container = env.container) }
        waitFor("Coffee shop")
        compose.onNode(hasText("Spent in", substring = true)).performClick()
        waitForText("Spent this month")
    }

    @Test
    fun theTotalsSayWhatTheyCover() {
        openHistoryFromHome()
        waitFor("Old rent")
        // 12,000 + 900,000 + 500,000 minor units = Rs 14,120.00 across the whole history.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("all time", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Spent ₹14,120.00", substring = true).onFirst().assertIsDisplayed()
    }
}
