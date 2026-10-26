package com.example.normpix.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
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
 * Settings > Data & backup > Check and recalculate: it runs under a loading sheet, then says what it checked and what is left
 * to look at - here, an entry dated after today.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class LedgerCheckFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("ledger_check_flow_test")
    }

    @After
    fun tearDown() = env.close()

    // A transaction's row reads as one phrase (its content description), so what it says is looked for there too.
    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() ||
        compose.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.waitForIdle()
        shown(text)
    }

    private fun open() {
        // The app's environment hosts the sheets and decisions itself.
        compose.setContent { env.Content { DataSettingsPage() } }
        compose.waitForIdle()
        compose.onNodeWithText("Check and recalculate").performScrollTo().performClick()
    }

    @Test
    fun `a ledger that holds together adds up`() {
        runBlocking { env.container.finance.load() }
        open()
        waitFor("Everything adds up")
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.waitForIdle()
            !shown("Everything adds up")
        }
    }

    @Test
    fun `an entry dated after today is left to look at, with the entry itself`() {
        runBlocking {
            val finance = env.container.finance
            finance.load()
            val bank = finance.saveAccount(Account(name = "Bank", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
            val category = finance.state.value.categories.first { it.type == CategoryType.Expense }
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense, amount = 5_000_00, accountId = bank.id!!, categoryId = category.id, payee = "Rent",
                    occurredAt = LocalDate.now().plusDays(4).atTime(9, 0), createdAt = now, updatedAt = now,
                ),
            )
        }
        open()
        waitFor("1 thing to look at")
        waitFor("Dated after today")
        waitFor("Rent")
    }
}
