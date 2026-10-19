package com.example.normpix.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
        compose.onNodeWithText("Card number (optional)").assertIsDisplayed()
        compose.onAllNodesWithText("Account number ends with (optional)").assertCountEquals(0)
    }

    @Test
    fun `cash is not asked`() {
        showForm(account("Wallet cash", AccountKind.Cash))
        compose.onAllNodesWithText("Account number ends with (optional)").assertCountEquals(0)
        compose.onAllNodesWithText("Card number (optional)").assertCountEquals(0)
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

    // ----- a card account's whole number: only its first six and last four digits are kept -----

    private fun cardAccount(numberEnding: String? = null, numberStart: String? = null) = runBlocking {
        env.container.finance.saveAccount(
            Account(
                name = "HDFC Credit", kind = AccountKind.Card, numberEnding = numberEnding, numberStart = numberStart,
                createdAt = now, updatedAt = now,
            ),
        )
    }

    @Test
    fun `a whole card number is cut down to its first six and last four digits`() {
        val credit = cardAccount()
        var saved: Account? = null
        showForm(credit) { saved = it }

        compose.onNodeWithText("Card number (optional)").performTextInput("4111111111111111")
        compose.onNodeWithText("Only the first 6 and last 4 digits are kept, to recognise this card on receipts").assertIsDisplayed()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals("411111", assertNotNull(saved).numberStart)
        assertEquals("1111", saved?.numberEnding)
        val kept = stored(credit.id)
        assertEquals("411111", kept.numberStart)
        assertEquals("1111", kept.numberEnding)
        // Nothing of the middle digits reached the account: ten digits at most.
        assertEquals(10, kept.numberStart.orEmpty().length + kept.numberEnding.orEmpty().length)
    }

    @Test
    fun `a card number is typed in groups of four`() {
        showForm(cardAccount())
        compose.onNodeWithText("Card number (optional)").performTextInput("4111111111111111")
        compose.onNodeWithText("4111 1111 1111 1111").assertIsDisplayed()
    }

    @Test
    fun `just the last four digits still work, and take the start away`() {
        val credit = cardAccount(numberEnding = "9999", numberStart = "411111")
        var saved: Account? = null
        showForm(credit) { saved = it }

        compose.onNodeWithText("Card number (optional)").performTextInput("4455")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals("4455", assertNotNull(saved).numberEnding)
        assertNull(saved?.numberStart, "a start belongs to the number it came from")
    }

    @Test
    fun `typing nothing keeps what is already kept, and shows it masked`() {
        val credit = cardAccount(numberEnding = "1111", numberStart = "411111")
        var saved: Account? = null
        showForm(credit) { saved = it }

        compose.onNodeWithText("Kept: 4111 11•• •••• 1111. Type the number again to change it.").assertIsDisplayed()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals("411111", stored(credit.id).numberStart)
        assertEquals("1111", stored(credit.id).numberEnding)
    }

    @Test
    fun `the clear button throws the kept digits away`() {
        val credit = cardAccount(numberEnding = "1111", numberStart = "411111")
        var saved: Account? = null
        showForm(credit) { saved = it }

        compose.onNodeWithContentDescription("Forget the kept digits").performClick()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertNull(stored(credit.id).numberStart)
        assertNull(stored(credit.id).numberEnding)
    }

    @Test
    fun `a number of the wrong length is not saved, and says what to do`() {
        val credit = cardAccount()
        var saved: Account? = null
        showForm(credit) { saved = it }

        compose.onNodeWithText("Card number (optional)").performTextInput("41111111")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Enter the whole card number, or just its last 4 digits, or leave it empty").assertIsDisplayed()
        assertNull(saved)
        assertNull(stored(credit.id).numberStart)
    }

    @Test
    fun `a number that fails the check is flagged but is the user's to keep`() {
        val credit = cardAccount()
        var saved: Account? = null
        showForm(credit) { saved = it }

        compose.onNodeWithText("Card number (optional)").performTextInput("4111111111111112")
        compose.onNodeWithText("That doesn't look like a card number. Check the digits.").assertIsDisplayed()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }
        assertEquals("411111", assertNotNull(saved).numberStart)
    }

    @Test
    fun `with a Wallet card to choose, the link is still there and typing a number is the other way`() {
        walletCard("rupay", "HDFC Bank", "6528505555554455")
        showForm(cardAccount())
        compose.onNodeWithText("Card in your Wallet (optional)").assertIsDisplayed()
        compose.onNodeWithText("Card number (optional)").assertIsDisplayed()
    }

    @Test
    fun `an account that is not a card keeps asking for the last four digits only`() {
        val salary = account("HDFC Salary", AccountKind.Bank)
        var saved: Account? = null
        showForm(salary) { saved = it }
        compose.onAllNodesWithText("Card number (optional)").assertCountEquals(0)
        compose.onNodeWithText("Account number ends with (optional)").performTextInput("3588")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }
        assertNull(assertNotNull(saved).numberStart)
        assertEquals("3588", saved?.numberEnding)
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
