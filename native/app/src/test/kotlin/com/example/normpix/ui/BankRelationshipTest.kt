package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Accounts and cards through the one thing they share, the bank: one list in both forms, banks added once, the Wallet's
 * logos and filters read off the real links, and a bank that outlives the last card that used it.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BankRelationshipTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val vault = InMemoryVault()
    private val env = TestEnvironment(prefsName = "bank_relationship_test", vault = vault)
    private val finance = env.container.finance
    private val now = LocalDateTime.of(2026, 9, 15, 12, 0)

    @After
    fun tearDown() = env.close()

    private fun card(id: String, bank: String, bankKey: String?, last4: String = "4242") = StoredCard(
        id = id,
        bankName = bank,
        cardholderName = "Asha Rao",
        cardNumber = "411111111111$last4",
        expiryMonth = "11",
        expiryYear = "29",
        cvv = "737",
        kind = CardKind.Credit,
        createdAt = now,
        bankKey = bankKey,
    )

    private fun account(name: String, kind: AccountKind = AccountKind.Bank, bankKey: String? = null) =
        Account(name = name, kind = kind, createdAt = now, updatedAt = now, bankKey = bankKey)

    private fun seed(accounts: List<Account> = emptyList(), cards: List<StoredCard> = emptyList()) = runBlocking {
        accounts.forEach { finance.saveAccount(it) }
        vault.cards = cards
        env.container.cards.load()
    }

    private fun openWallet() {
        // These tests read an account's bank off its compact row; the card layouts have their own (AccountCardsTest).
        env.preferences.setAccountsLayout(AccountsLayout.Rows)
        compose.setContent { NormPixRoot(container = env.container) }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Wallet") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).performClick()
        compose.waitForIdle()
    }

    private fun waitFor(text: String) = compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitGone(text: String) = compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun chooseType(label: String) {
        compose.onNodeWithText("Type").performClick()
        compose.waitForIdle()
        // The menu is a popup; an account row of the same name sits behind the sheet.
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
    }

    /** Adds a bank from the open form's bank field, the way a user would: type, "Add new bank", name it, save. */
    private fun addBankFromField(fieldLabel: String, name: String) {
        compose.onNodeWithText(fieldLabel).performTextInput("zz")
        compose.waitForIdle()
        compose.onNodeWithText("Add new bank").performClick()
        waitFor("Add bank")
        compose.onNodeWithText("Bank name").performTextReplacement(name)
        compose.waitForIdle()
        compose.onNode(hasText("Add bank") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        waitGone("Shared by your accounts and cards")
    }

    private fun hasText(text: String) = androidx.compose.ui.test.hasText(text)

    // ------------------------------------------------------------ the wallet

    @Test
    fun walletShowsAnAccountsBankOnItsRowAndFiltersCardsByTheirBanks() {
        seed(
            accounts = listOf(account("Salary", bankKey = "hdfc")),
            cards = listOf(card("a", "HDFC Bank", "hdfc", "1111"), card("b", "ICICI Bank", "icici", "2222")),
        )
        openWallet()

        // The account names its bank, and how many of the wallet's cards belong to that bank too.
        compose.onNodeWithText("HDFC Bank · Bank account · 1 card").assertIsDisplayed()
        // Two banks among the cards: a chip for each, taken from the cards' links.
        waitFor("All")
        val filterChip = { bank: String -> compose.onNode(hasText(bank) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)) }
        filterChip("HDFC Bank").assertIsDisplayed()
        filterChip("ICICI Bank").assertIsDisplayed()
        val cardOf = { bank: String -> compose.onAllNodesWithContentDescription("$bank credit card", substring = true) }
        compose.waitUntil(10_000) { cardOf("HDFC Bank").fetchSemanticsNodes().isNotEmpty() }

        // Filtering by ICICI leaves only ICICI's card; "All" brings the other back.
        filterChip("ICICI Bank").performClick()
        compose.waitUntil(10_000) { cardOf("HDFC Bank").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) { cardOf("ICICI Bank").fetchSemanticsNodes().isNotEmpty() }
        filterChip("All").performClick()
        compose.waitUntil(10_000) { cardOf("HDFC Bank").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun cardsOfOneBankShowNoFilter() {
        seed(cards = listOf(card("a", "HDFC Bank", "hdfc", "1111"), card("b", "HDFC Bank", "hdfc", "2222")))
        openWallet()
        waitFor("Cards")
        assertEquals(0, count("All"), "one bank is nothing to filter by")
    }

    @Test
    fun changingACardsBankMovesItBetweenTheFilterGroups() {
        seed(cards = listOf(card("a", "HDFC Bank", "hdfc", "1111"), card("b", "ICICI Bank", "icici", "2222")))
        openWallet()
        waitFor("All")
        val chip = { bank: String -> compose.onAllNodes(hasText(bank) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)) }
        assertEquals(1, chip("HDFC Bank").fetchSemanticsNodes().size)

        runBlocking { env.container.cards.update(card("a", "Axis Bank", "axis", "1111")) }
        compose.waitUntil(10_000) { chip("Axis Bank").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, chip("HDFC Bank").fetchSemanticsNodes().size, "no card is left under the old bank")
    }

    @Test
    fun changingAnAccountsBankUpdatesTheRowAtOnce() {
        seed(accounts = listOf(account("Salary", bankKey = "hdfc")))
        openWallet()
        compose.onNodeWithText("HDFC Bank · Bank account").assertIsDisplayed()

        runBlocking { finance.saveAccount(finance.state.value.accounts.first { it.name == "Salary" }.copy(bankKey = "axis")) }
        waitFor("Axis Bank · Bank account")
        assertEquals(0, count("HDFC Bank · Bank account"))

        runBlocking { finance.saveAccount(finance.state.value.accounts.first { it.name == "Salary" }.copy(bankKey = null)) }
        waitGone("Axis Bank · Bank account")
        compose.onNodeWithText("Bank account").assertIsDisplayed()
    }

    @Test
    fun deletingTheLastCardOfABankKeepsTheBankForTheAccountThatStillUsesIt() {
        val key = runBlocking { finance.bankKeyFor("My Credit Union") }
        seed(
            accounts = listOf(account("Co-op", AccountKind.Savings, bankKey = key), account("Salary", bankKey = "hdfc")),
            cards = listOf(card("a", "My Credit Union", key), card("b", "HDFC Bank", "hdfc")),
        )
        openWallet()
        waitFor("My Credit Union · Savings · 1 card")
        compose.onNodeWithText("HDFC Bank · Bank account · 1 card").assertIsDisplayed()

        runBlocking {
            env.container.cards.delete("a")
            env.container.cards.delete("b")
        }
        waitGone("My Credit Union · Savings · 1 card")

        // The cards are gone; the banks - and the accounts' links to them - are not.
        compose.onNodeWithText("My Credit Union · Savings").assertIsDisplayed()
        compose.onNodeWithText("HDFC Bank · Bank account").assertIsDisplayed()
        assertEquals(listOf(key), finance.state.value.customBanks.map { it.key })
        assertNotNull(finance.state.value.catalog.byKey(key))
    }

    // ------------------------------------------------------------ the forms

    @Test
    fun aBankAddedInTheAccountFormIsInTheCardFormsListAndIsNeverAddedTwice() {
        seed()
        openWallet()

        compose.onNodeWithText("Add account").performClick()
        waitFor("New account")
        compose.onNodeWithText("Name").performTextInput("Co-op savings")
        addBankFromField("Bank (optional)", "My Credit Union")
        waitFor("My Credit Union")
        compose.onNodeWithText("Save account").performClick()
        waitGone("New account")

        val key = finance.state.value.customBanks.single().key
        assertEquals(key, finance.state.value.accounts.first { it.name == "Co-op savings" }.bankKey)

        // The card form lists that bank - no second list, no re-adding.
        compose.onNodeWithText("Add card").performClick()
        waitFor("Add card") // the sheet's title
        compose.onNodeWithText("Bank").performTextInput("my cred")
        waitFor("My Credit Union")
        compose.onNodeWithText("My Credit Union").performClick()
        compose.waitForIdle()

        // Typing the same bank into "Add new bank" again finds it instead of making another.
        compose.onNodeWithText("Bank").performTextReplacement("zz")
        compose.onNodeWithText("Add new bank").performClick()
        waitFor("Add bank")
        compose.onNodeWithText("Bank name").performTextReplacement("MY CREDIT UNION LTD")
        compose.waitForIdle()
        compose.onNodeWithText("My Credit Union is already in your list, so it will be used.").assertIsDisplayed()
        compose.onNodeWithText("Use My Credit Union").performClick()
        waitGone("Shared by your accounts and cards")

        assertEquals(1, finance.state.value.customBanks.size)
    }

    @Test
    fun cashHasNoBankField() {
        seed()
        openWallet()
        compose.onNodeWithText("Add account").performClick()
        waitFor("New account")
        compose.onNodeWithText("Bank (optional)").assertIsDisplayed()

        chooseType("Cash")
        waitGone("Bank (optional)")
    }

    @Test
    fun anAccountCannotBeSavedWithATypedNameThatIsNotABank() {
        seed()
        openWallet()
        compose.onNodeWithText("Add account").performClick()
        waitFor("New account")
        compose.onNodeWithText("Name").performTextInput("Mystery")
        compose.onNodeWithText("Bank (optional)").performTextInput("Not A Real Bank")
        compose.waitForIdle()
        compose.onNodeWithText("Save account").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pick a bank from the list, or add it as a new bank").assertIsDisplayed()
        assertNull(finance.state.value.accounts.firstOrNull { it.name == "Mystery" })
    }

    @Test
    fun aCardNeedsABank() {
        seed()
        openWallet()
        compose.onNodeWithText("Add card").performClick()
        waitFor("Save card")
        compose.onNodeWithText("Save card").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Choose a bank").assertIsDisplayed()
        assertTrue(vault.cards.isEmpty())
    }
}
