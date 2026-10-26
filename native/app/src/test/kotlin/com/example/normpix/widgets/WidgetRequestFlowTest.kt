package com.example.normpix.widgets

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a widget asked for is done once the app is unlocked, and once: a new transaction, one transaction, or Activity. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class WidgetRequestFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now().withSecond(0).withNano(0)
    private var held by mutableStateOf(false)
    private var sample by mutableStateOf(false)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("widget_request_flow_test")
        runBlocking { env.container.finance.load() }
    }

    @After
    fun tearDown() = env.close()

    private fun shown(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() ||
        compose.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(15_000) {
        compose.waitForIdle()
        shown(text)
    }

    private fun show() {
        compose.setContent { env.Content { ReceiveWidgetRequests(env.container.widgetRequests, held = held, sampleData = sample) } }
        compose.waitForIdle()
    }

    @Test
    fun `an add button opens the new-transaction sheet of its kind, once the app is unlocked`() {
        held = true
        env.container.widgetRequests.value = WidgetRequest.Add(TransactionType.Income)
        show()
        assertTrue(!shown("New income"), "nothing over the lock")
        held = false
        waitFor("New income")
        assertNull(env.container.widgetRequests.value, "taken once")
    }

    @Test
    fun `a row opens its transaction over Activity, and the heading opens Activity`() {
        val swiggy = runBlocking {
            val finance = env.container.finance
            val bank = finance.saveAccount(Account(name = "HDFC Bank", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
            val category = finance.state.value.categories.first { it.type == CategoryType.Expense }
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense, amount = 250_00, accountId = bank.id!!, categoryId = category.id, payee = "Swiggy",
                    occurredAt = now.minusHours(1), createdAt = now, updatedAt = now,
                ),
            )
        }
        env.container.widgetRequests.value = WidgetRequest.Show(swiggy.id!!)
        show()
        waitFor("Swiggy")
        assertEquals(1, env.navigator.transactionsOpened)

        env.container.widgetRequests.value = WidgetRequest.Activity
        compose.waitUntil(10_000) { env.navigator.transactionsOpened == 2 }
    }

    @Test
    fun `with sample data showing, adding asks to go back to the user's own data first`() {
        sample = true
        env.container.widgetRequests.value = WidgetRequest.Add(TransactionType.Expense)
        show()
        waitFor("Sample data is on")
        assertTrue(!shown("New expense"))
    }
}
