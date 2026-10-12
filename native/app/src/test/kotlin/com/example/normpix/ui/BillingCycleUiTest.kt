package com.example.normpix.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.nextBillDue
import com.example.normpix.finance.ui.AccountFormSheet
import com.example.normpix.finance.ui.PayBillSheet
import com.example.normpix.screens.WalletScreen
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

/**
 * A card's billing cycle: its statement and due days are asked for when a card is made or edited (and for nothing else),
 * and the Wallet counts down to the due date - only while money is owed on the card.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class BillingCycleUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.of(2026, 10, 1, 9, 0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("billing_cycle_ui_test")
    }

    @After
    fun tearDown() = env.close()

    private fun account(
        name: String,
        kind: AccountKind,
        opening: Long = 0,
        statementDay: Int? = null,
        dueDay: Int? = null,
    ): Account = runBlocking {
        env.container.finance.saveAccount(
            Account(
                name = name,
                kind = kind,
                openingBalance = opening,
                statementDay = statementDay,
                dueDay = dueDay,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun showForm(existing: Account?, onDone: (Account?) -> Unit = {}) {
        compose.setContent { env.Content { AccountFormSheet(existing, onDone) } }
        compose.waitForIdle()
    }

    /** The day of the month [daysAhead] days from today: a due day that is that far away whatever today is. */
    private fun dayIn(daysAhead: Long): Int = LocalDate.now().plusDays(daysAhead).dayOfMonth

    private fun showWallet(layout: AccountsLayout) {
        env.preferences.setAccountsLayout(layout)
        compose.setContent { env.Content { WalletScreen() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add account").fetchSemanticsNodes().isNotEmpty() }
    }

    // ----- the editor -----

    @Test
    fun `a card is asked for its statement and due dates, and they are kept`() {
        val card = account("HDFC Credit", AccountKind.Card)
        var saved: Account? = null
        showForm(card) { saved = it }

        compose.onNodeWithText("Statement date (optional)").performClick()
        compose.onNodeWithText("5th of every month").performClick()
        compose.onNodeWithText("Payment due date (optional)").performClick()
        compose.onNodeWithText("20th of every month").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals(5, assertNotNull(saved).statementDay)
        assertEquals(20, assertNotNull(saved).dueDay)
        val stored = runBlocking { env.container.finance.repository.accounts() }.first { it.id == card.id }
        assertEquals(5, stored.statementDay)
        assertEquals(20, stored.dueDay)
    }

    @Test
    fun `the dates are optional`() {
        val card = account("HDFC Credit", AccountKind.Card)
        var saved: Account? = null
        showForm(card) { saved = it }

        compose.onNodeWithText("Statement date (optional)").assertIsDisplayed()
        compose.onNodeWithText("Payment due date (optional)").assertIsDisplayed()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertNull(assertNotNull(saved).statementDay)
        assertNull(assertNotNull(saved).dueDay)
    }

    @Test
    fun `a card shows the days it has, and one of them can be cleared`() {
        val card = account("HDFC Credit", AccountKind.Card, statementDay = 5, dueDay = 20)
        var saved: Account? = null
        showForm(card) { saved = it }

        compose.onNodeWithText("5th of every month").assertIsDisplayed()
        compose.onNodeWithText("20th of every month").assertIsDisplayed()

        compose.onNodeWithText("Statement date (optional)").performClick()
        compose.onNodeWithText("Not set").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertNull(assertNotNull(saved).statementDay)
        assertEquals(20, assertNotNull(saved).dueDay)
    }

    @Test
    fun `the last days of the month say what a short month does`() {
        val card = account("HDFC Credit", AccountKind.Card, dueDay = 31)
        showForm(card)
        compose.onNodeWithText("31st of every month · last day in shorter months").assertIsDisplayed()
    }

    @Test
    fun `other accounts are not asked for them`() {
        showForm(account("HDFC Savings", AccountKind.Bank))
        compose.onAllNodesWithText("Statement date (optional)").assertCountEquals(0)
        compose.onAllNodesWithText("Payment due date (optional)").assertCountEquals(0)
    }

    // ----- the Wallet -----

    @Test
    fun `a card that owes money counts down to its due date, in rows`() {
        account("HDFC Credit", AccountKind.Card, opening = -12_000_00, dueDay = dayIn(8))
        showWallet(AccountsLayout.Rows)

        val line = nextBillDue(dayIn(8), LocalDate.now())!!.line()
        compose.onNodeWithText(line, substring = true).assertIsDisplayed()
    }

    @Test
    fun `a card that owes money counts down to its due date, as a card`() {
        account("HDFC Credit", AccountKind.Card, opening = -12_000_00, dueDay = dayIn(8))
        showWallet(AccountsLayout.Cards)

        // The card face is read as one description, so that is where the due date is checked.
        val line = nextBillDue(dayIn(8), LocalDate.now())!!.line().replaceFirstChar(Char::lowercase)
        assertTrue(spokenCards().any { it.startsWith("HDFC Credit, ") && ", bill $line" in it }, "a screen reader is told the bill is due: ${spokenCards()}")
    }

    @Test
    fun `a bill due today says so`() {
        account("HDFC Credit", AccountKind.Card, opening = -12_000_00, dueDay = LocalDate.now().dayOfMonth)
        showWallet(AccountsLayout.Cards)
        assertTrue(spokenCards().any { it.startsWith("HDFC Credit, ") && ", bill due today" in it }, spokenCards().toString())
    }

    @Test
    fun `a card with nothing owed is not told as due, as a card`() {
        account("HDFC Credit", AccountKind.Card, opening = 0, dueDay = dayIn(8))
        showWallet(AccountsLayout.Cards)
        assertTrue(spokenCards().none { ", bill due" in it }, spokenCards().toString())
    }

    private fun spokenCards(): List<String> = compose.onAllNodes(hasContentDescription(", balance ", substring = true))
        .fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.ContentDescription].first() }

    @Test
    fun `a card with nothing owed has no bill to count down to`() {
        account("HDFC Credit", AccountKind.Card, opening = 0, dueDay = dayIn(8))
        showWallet(AccountsLayout.Rows)

        val line = nextBillDue(dayIn(8), LocalDate.now())!!.line()
        compose.onAllNodesWithText(line, substring = true).assertCountEquals(0)
    }

    @Test
    fun `a card that owes money but has no due day says nothing about one`() {
        account("HDFC Credit", AccountKind.Card, opening = -12_000_00)
        showWallet(AccountsLayout.Rows)
        compose.onAllNodesWithText("Due ", substring = true).assertCountEquals(0)
    }

    // ----- paying the bill -----

    @Test
    fun `paying a bill says what is owed and when it is due`() {
        val card = account("HDFC Credit", AccountKind.Card, opening = -12_000_00, dueDay = dayIn(8))
        account("HDFC Savings", AccountKind.Bank, opening = 40_000_00)
        compose.setContent { env.Content { PayBillSheet(card) {} } }

        val line = nextBillDue(dayIn(8), LocalDate.now())!!.line()
        val subtitle = "${money(12_000_00)} owed · $line"
        compose.waitUntil(10_000) { compose.onAllNodesWithText(subtitle).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(subtitle).assertIsDisplayed()
    }

    @Test
    fun `paying a bill with no due day says only what is owed`() {
        val card = account("HDFC Credit", AccountKind.Card, opening = -12_000_00)
        account("HDFC Savings", AccountKind.Bank, opening = 40_000_00)
        compose.setContent { env.Content { PayBillSheet(card) {} } }

        val subtitle = "${money(12_000_00)} owed"
        compose.waitUntil(10_000) { compose.onAllNodesWithText(subtitle).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(subtitle).assertIsDisplayed()
    }

    private fun money(minor: Long) = env.container.finance.state.value.money.format(minor)
}
