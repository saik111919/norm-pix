package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.AccountHistoryPage
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The history, as of today: what is dated after today waits in a folded "Upcoming" row at the head of the list, not above
 * today's spending, and moves no balance yet; each day's heading says what went out on it and opens that day on its own.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class ActivityAsOfTodayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: TestEnvironment
    private val finance get() = env.container.finance
    private val now = LocalDateTime.now().withSecond(0).withNano(0)
    private var bankId = 0L

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment(prefsName = "activity_as_of_today_test")
    }

    @After
    fun tearDown() = env.close()

    /** Bank with Rs 10,000: Rs 250 at Swiggy today, Rs 1,200 at Uber yesterday, and a Rs 5,000 rent payment dated ahead. */
    private fun seed() = runBlocking {
        finance.load()
        val bank = finance.saveAccount(Account(name = "Bank", kind = AccountKind.Bank, openingBalance = 10_000_00, createdAt = now, updatedAt = now))
        bankId = bank.id!!
        val categories = finance.state.value.categories.filter { it.type == CategoryType.Expense }
        fun spend(amount: Long, payee: String, at: LocalDateTime) = runBlocking {
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense, amount = amount, accountId = bankId, categoryId = categories.first().id,
                    payee = payee, occurredAt = at, createdAt = now, updatedAt = now,
                ),
            )
        }
        spend(250_00, "Swiggy", LocalDate.now().atTime(0, 5))
        spend(1_200_00, "Uber", LocalDate.now().minusDays(1).atTime(9, 0))
        spend(5_000_00, "Rent", LocalDate.now().plusDays(3).atTime(9, 0))
    }

    private fun show() {
        compose.setContent { env.Content { AccountHistoryPage(bankId) } }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Upcoming", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `an entry dated after today is folded under Upcoming and moves no balance yet`() {
        seed()
        show()
        assertEquals(10_000_00L - 250_00 - 1_200_00, finance.state.value.balanceOf(bankId), "the rent counts from its own day")
        assertTrue(compose.onAllNodesWithText("Rent", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "folded away")
        // Today's spending is the first entry of the list (above yesterday's, when that is this month's too).
        if (LocalDate.now().dayOfMonth > 1) {
            val swiggy = compose.onNodeWithText("Swiggy", useUnmergedTree = true).getUnclippedBoundsInRoot()
            val uber = compose.onNodeWithText("Uber", useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue(swiggy.top < uber.top)
        }

        compose.onNodeWithText("Upcoming").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Rent", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a day's heading says what went out on it and opens that day alone`() {
        // Yesterday is last month's on the 1st, and this list shows this month.
        assumeTrue(LocalDate.now().dayOfMonth > 1)
        seed()
        show()
        compose.onNodeWithText("Yesterday", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("₹1,200.00", useUnmergedTree = true).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }

        compose.onNodeWithText("Yesterday", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Swiggy", useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("Uber", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous day").assertIsDisplayed()

        // Back to the month: everything again.
        compose.onNodeWithText("By month").performClick()
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Swiggy", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
