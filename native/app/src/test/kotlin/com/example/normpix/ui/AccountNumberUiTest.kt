package com.example.normpix.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.ui.AccountFormSheet
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What tells a shared receipt which account is which: the last digits of an account's or card's number, and the Wallet card
 * that is its card. Asked for when an account is made or edited (not for cash), kept, and never more than the user says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class AccountNumberUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.of(2026, 10, 12, 9, 0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("account_number_ui_test")
    }

    @After
    fun tearDown() = env.close()

    private fun account(name: String, kind: AccountKind, numberEnding: String? = null, cardId: String? = null): Account = runBlocking {
        env.container.finance.saveAccount(
            Account(name = name, kind = kind, numberEnding = numberEnding, cardId = cardId, createdAt = now, updatedAt = now),
        )
    }

    private fun walletCard(id: String, bank: String, number: String, kind: CardKind = CardKind.Credit) {
        runBlocking { env.container.cards.add(StoredCard(id, bank, "THE USER", number, "08", "29", "123", kind, now)) }
    }

    private fun showForm(existing: Account?, onDone: (Account?) -> Unit = {}) {
        compose.setContent { env.Content { AccountFormSheet(existing, onDone) } }
        compose.waitForIdle()
    }

    private fun stored(id: Long?): Account = runBlocking { env.container.finance.repository.accounts() }.first { it.id == id }

    // ----- the number's ending -----

    @Test
    fun `a bank account is asked what its number ends with, and it is kept`() {
        val salary = account("HDFC Salary", AccountKind.Bank)
        var saved: Account? = null
        showForm(salary) { saved = it }

        compose.onNodeWithText("Account number ends with (optional)").performTextInput("3588")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals("3588", assertNotNull(saved).numberEnding)
        assertEquals("3588", stored(salary.id).numberEnding)
    }

    @Test
    fun `a card account says card, and cash is not asked`() {
        showForm(account("HDFC Credit", AccountKind.Card))
        compose.onNodeWithText("Card number ends with (optional)").assertIsDisplayed()
    }

    @Test
    fun `cash is not asked`() {
        showForm(account("Wallet cash", AccountKind.Cash))
        compose.onAllNodesWithText("Account number ends with (optional)").assertCountEquals(0)
        compose.onAllNodesWithText("Card number ends with (optional)").assertCountEquals(0)
    }

    @Test
    fun `only the first four digits typed are taken, and only digits`() {
        val salary = account("HDFC Salary", AccountKind.Bank)
        var saved: Account? = null
        showForm(salary) { saved = it }

        compose.onNodeWithText("Account number ends with (optional)").performTextInput("35a8-8 9")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals("3588", assertNotNull(saved).numberEnding)
    }

    @Test
    fun `a few digits are not an ending`() {
        val salary = account("HDFC Salary", AccountKind.Bank)
        var saved: Account? = null
        showForm(salary) { saved = it }

        compose.onNodeWithText("Account number ends with (optional)").performTextInput("35")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Enter all 4 digits, or leave it empty").assertIsDisplayed()
        assertNull(saved)
        assertNull(stored(salary.id).numberEnding)
    }

    @Test
    fun `the ending is optional, and an existing one can be cleared`() {
        val salary = account("HDFC Salary", AccountKind.Bank, numberEnding = "3588")
        var saved: Account? = null
        showForm(salary) { saved = it }

        compose.onNodeWithText("3588").assertIsDisplayed()
        compose.onNodeWithText("3588").performTextClearance()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertNull(assertNotNull(saved).numberEnding)
        assertNull(stored(salary.id).numberEnding)
    }

    // ----- the Wallet card -----

    @Test
    fun `a Wallet card can be chosen as the account's card`() {
        walletCard("rupay", "HDFC Bank", "6528505555554455")
        val rupay = account("HDFC RuPay", AccountKind.Card)
        var saved: Account? = null
        showForm(rupay) { saved = it }

        compose.onNodeWithText("Card in your Wallet (optional)").performClick()
        // By what the Wallet shows of a card - bank, kind, last four - never its number.
        compose.onNodeWithText("HDFC Bank · Credit •••• 4455").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals("rupay", assertNotNull(saved).cardId)
        assertEquals("rupay", stored(rupay.id).cardId)
    }

    @Test
    fun `a card that is another account's card is not offered`() {
        walletCard("rupay", "HDFC Bank", "6528505555554455")
        walletCard("millennia", "HDFC Bank", "5282000011112222", CardKind.Debit)
        account("HDFC RuPay", AccountKind.Card, cardId = "rupay")
        val other = account("HDFC Savings", AccountKind.Bank)
        showForm(other)

        compose.onNodeWithText("Card in your Wallet (optional)").performClick()
        compose.onNodeWithText("HDFC Bank · Debit •••• 2222").assertIsDisplayed()
        compose.onAllNodesWithText("HDFC Bank · Credit •••• 4455").assertCountEquals(0)
    }

    @Test
    fun `an account shows its own card, and can be set to none`() {
        walletCard("rupay", "HDFC Bank", "6528505555554455")
        val rupay = account("HDFC RuPay", AccountKind.Card, cardId = "rupay")
        var saved: Account? = null
        showForm(rupay) { saved = it }

        compose.onNodeWithText("HDFC Bank · Credit •••• 4455").assertIsDisplayed()
        compose.onNodeWithText("Card in your Wallet (optional)").performClick()
        compose.onNodeWithText("No card").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertNull(assertNotNull(saved).cardId)
        assertNull(stored(rupay.id).cardId)
    }

    @Test
    fun `a link to a card that is gone shows as none and is dropped when saved`() {
        walletCard("other", "Axis Bank", "4111111111111111")
        val old = account("Old card", AccountKind.Card, cardId = "deleted-long-ago")
        var saved: Account? = null
        showForm(old) { saved = it }

        compose.onAllNodesWithText("deleted-long-ago").assertCountEquals(0)
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertNull(assertNotNull(saved).cardId)
    }

    @Test
    fun `with no cards in the Wallet there is no card to choose`() {
        showForm(account("HDFC Savings", AccountKind.Bank))
        compose.onNodeWithText("Account number ends with (optional)").assertIsDisplayed()
        compose.onAllNodesWithText("Card in your Wallet (optional)").assertCountEquals(0)
    }
}
