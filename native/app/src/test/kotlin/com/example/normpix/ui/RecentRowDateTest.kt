package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.shortDate
import com.example.normpix.screens.HomeScreen
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row that is not under a day heading (Home's Recent) shows its date under the amount, so the supporting line keeps
 * the category and account whole. A screen reader still hears the date in the row's own place.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RecentRowDateTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "recent_row_date_test")
    private val finance = env.container.finance
    private val now = LocalDateTime.now().withNano(0)
    private val occurredAt = LocalDateTime.now().minusDays(2)
    private val date = shortDate(occurredAt)

    @After
    fun tearDown() = env.close()

    // ------------------------------------------------------------ seeding and screens

    /** One Rs 1,402 expense from Bank at Amazon, in the seeded Shopping category, two days ago. */
    private fun seedAmazonExpense() = runBlocking {
        finance.load()
        val bank = finance.saveAccount(Account(name = "Bank", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val shopping = finance.state.value.categories.first { it.name == "Shopping" && it.type == CategoryType.Expense }
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = 1_402_00,
                accountId = bank.id!!,
                categoryId = shopping.id,
                payee = "Amazon",
                occurredAt = occurredAt,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun showHome() {
        compose.setContent { env.Content { HomeScreen() } }
        // Home reads its recent activity in several steps; wait for the row's date (idling on every check) rather than racing it.
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText(date, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * How many rows announce themselves with both [date] and [payee] in one content description. A row reads as one
     * phrase ("Expense, ..., Amazon, ..., 8 Oct"), which is what a screen reader says.
     */
    private fun rowsSpeaking(date: String, payee: String): Int = compose.onAllNodes(
        SemanticsMatcher("a row whose description has \"$date\" and \"$payee\"") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains(date) && it.contains(payee) } == true
        },
    ).fetchSemanticsNodes().size

    // ------------------------------------------------------------ the row

    @Test
    fun theDateSitsUnderTheAmountAndTheAccountLineHasNoDate() {
        seedAmazonExpense()
        showHome()

        compose.onAllNodesWithText(date, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Shopping · Bank", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun theSpokenRowStillCarriesTheDate() {
        seedAmazonExpense()
        showHome()

        assertEquals(1, rowsSpeaking(date, "Amazon"), "the row's spoken description has the date and the payee")
    }
}
