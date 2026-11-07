package com.example.normpix.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's "Bill due": a banner for a credit card with a bill to pay - what is unpaid of its last statement (not everything on the card),
 * when it falls due, how far through its month it is - and none for a card that is paid, owes nothing or has no due day. A bill past its
 * due date is said to be late. With amounts hidden the total is not said.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeBillsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "home_bills_test")
    private val finance = env.container.finance
    private val now = LocalDateTime.now().withNano(0)
    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)

    @After
    fun tearDown() = env.close()

    private fun card(name: String, owed: Long, dueDay: Int?, statementDay: Int? = null) = runBlocking {
        finance.load()
        finance.saveAccount(
            Account(
                name = name,
                kind = AccountKind.Card,
                openingBalance = -owed,
                statementDay = statementDay,
                dueDay = dueDay,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun bank(): Account = runBlocking {
        finance.load()
        finance.saveAccount(Account(name = "Savings", kind = AccountKind.Bank, openingBalance = 5_000_000, createdAt = now, updatedAt = now))
    }

    /** Money spent on [account] [daysAgo] days ago, in the seeded Shopping category. */
    private fun spend(account: Account, amount: Long, daysAgo: Long) = runBlocking {
        val shopping = finance.state.value.categories.first { it.name == "Shopping" }
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = amount,
                accountId = account.id!!,
                categoryId = shopping.id,
                occurredAt = LocalDateTime.of(today.minusDays(daysAgo), LocalTime.NOON),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun pay(from: Account, to: Account, amount: Long, daysAgo: Long) = runBlocking {
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Transfer,
                amount = amount,
                accountId = from.id!!,
                toAccountId = to.id,
                occurredAt = LocalDateTime.of(today.minusDays(daysAgo), LocalTime.NOON),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun showHome() {
        compose.setContent { env.Content { HomeScreen() } }
        // Home's month is read in a step of its own: its quick actions are the sign it is up.
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Add income").fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** What every banner says to a screen reader, whole. */
    private fun spoken(): List<String> = compose.onAllNodes(SemanticsMatcher("has a description") { it.config.getOrNull(SemanticsProperties.ContentDescription) != null })
        .fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }

    /** The bill banner for [card], once Home has read the ledger for it (it does so after the screen is up). */
    private fun billOf(card: String): String {
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            spoken().any { it.startsWith("$card bill") }
        }
        return spoken().first { it.startsWith("$card bill") }
    }

    private fun noBillOf(card: String) {
        compose.waitForIdle()
        assertTrue(spoken().none { it.startsWith("$card bill") }, "no bill banner for $card")
    }

    @Test
    fun `a card that owes a bill has a banner that says when it is due and the total`() {
        card("Sapphire", owed = 8_450_00, dueDay = tomorrow.dayOfMonth, statementDay = today.dayOfMonth)
        showHome()

        compose.onNodeWithText("Bill due").assertExists()
        assertEquals("Sapphire bill, due tomorrow. Total due ₹8,450.00. 1 day left", billOf("Sapphire"))
    }

    @Test
    fun `the total is the statement, not what the card has been charged since`() {
        // Statement 10 days ago with 10,000 owed; due in 5 days; 2,000 charged since: the bill is the 10,000.
        val sapphire = card("Sapphire", owed = 10_000_00, dueDay = today.plusDays(5).dayOfMonth, statementDay = today.minusDays(10).dayOfMonth)
        spend(sapphire, 2_000_00, daysAgo = 3)
        showHome()

        val said = billOf("Sapphire")
        assertTrue("Total due ₹10,000.00" in said, said)
        assertTrue("12,000" !in said, "the 2,000 charged since belongs to the next statement: $said")
    }

    @Test
    fun `a bill past its due date is said to be late, with what is unpaid`() {
        // Statement 20 days ago with 6,000 owed; it was due 5 days ago and nothing was paid.
        card("Sapphire", owed = 6_000_00, dueDay = today.minusDays(5).dayOfMonth, statementDay = today.minusDays(20).dayOfMonth)
        showHome()

        val said = billOf("Sapphire")
        assertTrue("5 days late" in said, said)
        assertTrue("Unpaid ₹6,000.00" in said, said)
        assertTrue(said.startsWith("Sapphire bill, was due "), said)
    }

    @Test
    fun `a statement that has been paid has no banner, whatever the card was charged since`() {
        val savings = bank()
        val sapphire = card("Sapphire", owed = 10_000_00, dueDay = today.plusDays(5).dayOfMonth, statementDay = today.minusDays(10).dayOfMonth)
        card("Control", owed = 1_000_00, dueDay = tomorrow.dayOfMonth)
        spend(sapphire, 2_000_00, daysAgo = 3)
        pay(savings, sapphire, 10_000_00, daysAgo = 2)
        showHome()

        // A card that does owe a bill, so that its banner proves the ledger has been read before "nothing" is asserted.
        billOf("Control")
        noBillOf("Sapphire")
        compose.onAllNodesWithText("Bills due").assertCountEquals(0)
    }

    @Test
    fun `with amounts hidden the total is not said`() {
        card("Sapphire", owed = 8_450_00, dueDay = tomorrow.dayOfMonth)
        env.preferences.setAmountsHidden(true)
        showHome()

        // No statement day: everything owed so far.
        val said = billOf("Sapphire")
        assertEquals("Sapphire bill, due tomorrow. Owed so far ₹••••. 1 day left", said)
        assertTrue("8,450" !in said)
    }

    @Test
    fun `several bills each have a banner under Bills due`() {
        card("Later", owed = 1_000_00, dueDay = today.plusDays(10).dayOfMonth)
        card("Sooner", owed = 2_000_00, dueDay = tomorrow.dayOfMonth)
        showHome()

        billOf("Sooner")
        billOf("Later")
        compose.onNodeWithText("Bills due").assertExists()
    }

    @Test
    fun `a card nothing is owed on, and a card with no due day, have no banner`() {
        card("Paid", owed = 0, dueDay = tomorrow.dayOfMonth)
        card("No day", owed = 3_000_00, dueDay = null)
        card("Control", owed = 1_000_00, dueDay = tomorrow.dayOfMonth)
        showHome()

        billOf("Control")
        noBillOf("Paid")
        noBillOf("No day")
    }
}
