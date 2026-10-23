package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.AmountSign
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.AccountHistoryPage
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.screens.PlansAndTransactionsScreen
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
 * Accounts and transfers as the screens say them: a transfer is money leaving one account and arriving in another, so each
 * account's own history signs it; the Wallet says what is held and what is owed; and the account form turns "I owe this
 * amount" into a negative starting balance.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AccountsAndTransfersDisplayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "accounts_and_transfers_display_test")
    private val finance = env.container.finance
    private val now = LocalDateTime.now().withNano(0)

    private val offText = "Turn on for a credit card bill or a loan you still have to pay"

    @After
    fun tearDown() = env.close()

    // ------------------------------------------------------------ seeding

    private fun account(name: String, kind: AccountKind, openingBalance: Long = 0): Account = runBlocking {
        finance.saveAccount(Account(name = name, kind = kind, openingBalance = openingBalance, createdAt = now, updatedAt = now))
    }

    private fun transfer(from: Account, to: Account, amount: Long) = runBlocking {
        finance.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Transfer,
                amount = amount,
                accountId = from.id!!,
                toAccountId = to.id,
                occurredAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** A bank with Rs 50,000 and a credit card that already owes Rs 10,000. */
    private fun seedBankAndCard(): Pair<Account, Account> {
        val bank = account("Bank", AccountKind.Bank, openingBalance = 50_000_00)
        val card = account("Credit Card", AccountKind.Card, openingBalance = -10_000_00)
        return bank to card
    }

    // ------------------------------------------------------------ screens and lookups

    private fun showAccountHistory(accountId: Long) {
        compose.setContent { env.Content { AccountHistoryPage(accountId) } }
        compose.waitForIdle()
    }

    private fun showAllAccountsHistory() {
        compose.setContent { env.Content { PlansAndTransactionsScreen() } }
        compose.waitForIdle()
    }

    private fun openWallet() {
        // These tests read the accounts as the compact rows; the card layouts have their own (AccountCardsTest).
        env.preferences.setAccountsLayout(AccountsLayout.Rows)
        compose.setContent { NormPixRoot(container = env.container) }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Wallet") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
        waitFor("Total balance")
    }

    private fun waitFor(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitGone(text: String, substring: Boolean = false) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty()
    }

    /**
     * How many nodes show [text], including ones inside a row that merges its own semantics (a transaction's amount is
     * one of those, so the merged tree alone would not find it).
     */
    private fun shown(text: String): Int =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun waitUntilShown(text: String) = compose.waitUntil(10_000) { shown(text) > 0 }

    /**
     * A transaction row announces itself in one content description that starts with its direction ("Transfer out, ...")
     * or, outside an account's own list, with its type ("Transfer, ...").
     */
    private fun rowsStarting(prefix: String): Int = compose.onAllNodes(
        SemanticsMatcher("a row whose description starts with \"$prefix\"") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(prefix) } == true
        },
    ).fetchSemanticsNodes().size

    // ------------------------------------------------------------ an account's history

    @Test
    fun aTransferIsAMinusOnTheAccountItLeaves() {
        val (bank, card) = seedBankAndCard()
        transfer(bank, card, 5_000_00)
        showAccountHistory(bank.id!!)
        val money = finance.state.value.money

        waitUntilShown(money.format(-5_000_00))
        assertEquals(1, shown(money.format(-5_000_00)), "Bank's history shows the transfer as money out")
        assertEquals(0, shown(money.format(5_000_00, AmountSign.Explicit)), "nothing on Bank's history is shown with a plus")
        assertEquals(1, rowsStarting("Transfer out, "), "the row announces itself as a transfer out")
        assertEquals(0, rowsStarting("Transfer in, "), "and not as a transfer in")
    }

    @Test
    fun aTransferIsAPlusOnTheAccountItArrivesIn() {
        val (bank, card) = seedBankAndCard()
        transfer(bank, card, 5_000_00)
        showAccountHistory(card.id!!)
        val money = finance.state.value.money

        waitUntilShown(money.format(5_000_00, AmountSign.Explicit))
        assertEquals(1, shown(money.format(5_000_00, AmountSign.Explicit)), "Credit Card's history shows the transfer as money in")
        assertEquals(1, rowsStarting("Transfer in, "), "the row announces itself as a transfer in")
        assertEquals(0, rowsStarting("Transfer out, "), "and not as a transfer out")
    }

    // ------------------------------------------------------------ all accounts

    @Test
    fun theAllAccountsHistoryShowsATransferWithoutADirection() {
        val (bank, card) = seedBankAndCard()
        transfer(bank, card, 5_000_00)
        showAllAccountsHistory()
        val money = finance.state.value.money

        compose.waitUntil(10_000) { rowsStarting("Transfer, ") > 0 }
        assertEquals(1, rowsStarting("Transfer, ${money.format(5_000_00)}"), "the row is a plain transfer of the amount")
        assertEquals(1, shown(money.format(5_000_00)), "the amount is unsigned outside an account's own history")
        assertEquals(0, shown(money.format(-5_000_00)), "no minus")
        assertEquals(0, shown(money.format(5_000_00, AmountSign.Explicit)), "no plus")
        assertEquals(0, rowsStarting("Transfer in, ") + rowsStarting("Transfer out, "), "no direction in the row")
    }

    // ------------------------------------------------------------ the wallet

    @Test
    fun walletSaysWhatIsHeldMinusWhatIsOwed() {
        account("Bank", AccountKind.Bank, openingBalance = 38_000_00)
        account("Credit Card", AccountKind.Card, openingBalance = -12_000_00)
        openWallet()
        val money = finance.state.value.money

        // The overview: the total, and the two figures it is made of, each named - one thing, with a sentence for a screen reader.
        compose.onNode(hasText("Total balance") and hasText(money.format(26_000_00))).assertIsDisplayed()
        compose.onNode(hasText("In accounts") and hasText(money.format(38_000_00))).assertIsDisplayed()
        compose.onNode(hasText("You owe") and hasText(money.format(12_000_00))).assertIsDisplayed()
        compose.onNode(
            hasContentDescription(
                "Total balance ${money.format(26_000_00)}: ${money.format(38_000_00)} in accounts, minus ${money.format(12_000_00)} owed.",
            ),
        ).assertIsDisplayed()
        compose.onNodeWithText("Owed").assertIsDisplayed()
        // The card's supporting line is just its kind; the owed status sits under the amount, not in that line.
        compose.onNodeWithText("Card").assertIsDisplayed()
        // Only the card is owed; the bank's row says nothing of it.
        compose.onAllNodesWithText("Owed", substring = true).assertCountEquals(1)
    }

    @Test
    fun aCardPaidPastItsBillIsInCreditAndNothingIsOwedAnymore() {
        val bank = account("Bank", AccountKind.Bank, openingBalance = 38_000_00)
        val card = account("Credit Card", AccountKind.Card, openingBalance = -12_000_00)
        openWallet()
        val money = finance.state.value.money
        compose.onNode(hasText("You owe") and hasText(money.format(12_000_00))).assertIsDisplayed()

        // 15,000 moved from Bank onto the card: the card goes from owing 12,000 to being 3,000 in credit.
        transfer(bank, card, 15_000_00)
        waitGone("You owe")

        compose.onNodeWithText("In credit").assertIsDisplayed()
        compose.onNodeWithText(money.format(3_000_00)).assertIsDisplayed()
        compose.onNode(hasText("Total balance") and hasText(money.format(26_000_00))).assertIsDisplayed()
    }

    @Test
    fun noOwedLineWhenNoAccountIsBelowZero() {
        // Money is held here, but nothing is owed, so the line that adds owed money is absent.
        account("Bank", AccountKind.Bank, openingBalance = 10_000_00)
        openWallet()
        compose.onNodeWithText("Cash · Default").assertIsDisplayed()

        compose.onAllNodesWithText("You owe").assertCountEquals(0)
        compose.onNode(hasText("In accounts") and hasText(finance.state.value.money.format(10_000_00))).assertIsDisplayed()
    }

    // ------------------------------------------------------------ the new account form

    @Test
    fun theNewAccountFormSavesAnOwedBalanceAsNegative() {
        openWallet()
        val money = finance.state.value.money

        compose.onNodeWithText("Add account").performClick()
        waitFor("New account")
        compose.onNodeWithText("New account").assertIsDisplayed()
        compose.onNodeWithText(offText).assertIsDisplayed()

        compose.onNodeWithText("Name").performTextInput("Test Card")
        compose.onNode(hasText("I owe this amount") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performClick()
        compose.onNodeWithText("Counts against your total balance").assertIsDisplayed()
        compose.onAllNodesWithText(offText).assertCountEquals(0)

        compose.onNodeWithContentDescription("Starting balance in INR").performTextInput("5000")
        compose.onNodeWithText("Counts as ${money.format(-5_000_00)} in your total balance").assertIsDisplayed()

        compose.onNodeWithText("Save account").performClick()
        compose.waitUntil(10_000) { finance.state.value.accounts.any { it.name == "Test Card" } }
        val saved = finance.state.value.accounts.single { it.name == "Test Card" }
        assertEquals(-5_000_00L, saved.openingBalance, "owing the amount stores it as a negative opening balance")
        assertEquals(-5_000_00L, finance.state.value.balanceOf(saved.id!!))
    }
}
