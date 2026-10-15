package com.example.normpix.wallet

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.screens.WalletScreen
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Wallet's accounts as cards - a stack with one in front, or a list of them - and as the compact rows they were
 * before. The three show the same accounts with the same balances and standing; the choice is the user's, kept in the
 * preferences, and made from the Wallet itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class AccountCardsTest {
    @get:Rule
    val compose = createComposeRule()

    private var env: TestEnvironment? = null
    private val now = LocalDateTime.of(2026, 10, 1, 9, 0)

    @After
    fun tearDown() {
        env?.close()
    }

    private fun account(name: String, kind: AccountKind, openingBalance: Long = 0, bankKey: String? = null, archived: Boolean = false): Account =
        runBlocking {
            env!!.container.finance.saveAccount(
                Account(name = name, kind = kind, openingBalance = openingBalance, bankKey = bankKey, archived = archived, createdAt = now, updatedAt = now),
            )
        }

    /**
     * The Wallet with Cash (there from the start, the default), "HDFC Savings" with Rs 38,000 and "Credit Card" owing
     * Rs 12,000; [layout] is the choice made before it opens.
     */
    private fun showWallet(layout: AccountsLayout? = null, seed: () -> Unit = {}): TestEnvironment {
        Locale.setDefault(Locale.US)
        val environment = TestEnvironment("account_cards_test")
        env = environment
        account("HDFC Savings", AccountKind.Bank, openingBalance = 38_000_00, bankKey = "hdfc")
        account("Credit Card", AccountKind.Card, openingBalance = -12_000_00)
        seed()
        if (layout != null) environment.preferences.setAccountsLayout(layout)
        compose.setContent { environment.Content { WalletScreen() } }
        // "Add account" is offered in every layout, so it marks the accounts as loaded and drawn.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add account").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        return environment
    }

    /** Every account printed as a card, as a screen reader hears it. */
    private fun accountCards(): List<String> = compose.onAllNodes(hasContentDescription(", balance ", substring = true))
        .fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.ContentDescription].first() }

    private fun card(name: String) = compose.onNode(hasContentDescription("$name, ", substring = true) and hasContentDescription(", balance ", substring = true))

    private fun layoutChoice(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    private fun money(minor: Long) = env!!.container.finance.state.value.money.format(minor)

    // ------------------------------------------------------------ the stack

    @Test
    fun byDefaultTheAccountsAreAStack_withOneInFrontAndTheOthersOnlyDepth() {
        showWallet()

        val shown = accountCards()
        assertEquals(1, shown.size, "only the front account is printed: $shown")
        assertTrue(shown.single().endsWith("account 1 of 3"), "it is the first of three: ${shown.single()}")
        assertEquals(AccountsLayout.Stack, env!!.preferences.state.value.accountsLayout)
        compose.onNodeWithText("Add account").assertIsDisplayed()
    }

    @Test
    fun swipingTheFrontAccountBringsTheNextOneForward() {
        showWallet()
        val first = accountCards().single()

        compose.onNode(hasContentDescription(first)).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        val second = accountCards().single()
        assertNotEquals(first, second, "another account is in front")
        assertTrue(second.endsWith("account 2 of 3"), second)
    }

    @Test
    fun tappingTheFrontAccountOpensItsHistory() {
        val environment = showWallet()
        val front = accountCards().single()

        compose.onNode(hasContentDescription(front)).performClick()
        compose.waitForIdle()

        val opened = environment.navigator.accountHistories.single()
        val name = front.substringBefore(", ")
        assertEquals(name, environment.container.finance.state.value.accounts.single { it.id == opened }.name)
    }

    @Test
    fun pressingAndHoldingTheStackSpreadsEveryAccountOut_asTheSavedCardsDo() {
        showWallet()
        val front = accountCards().single()
        layoutChoice("Stack").assertIsSelected()

        compose.onNode(hasContentDescription(front)).performTouchInput { longClick() }
        compose.waitUntil(10_000) { accountCards().size == 3 }

        // Spread out, they are no longer the stack: none of the layouts is chosen, and the selector's own "Stack" is the way
        // back - the Wallet has no second "Stack" button for it.
        compose.onAllNodesWithContentDescription("Stack them again").assertCountEquals(0)
        layoutChoice("Stack").assertIsNotSelected()
        layoutChoice("Stack").performClick()
        compose.waitUntil(10_000) { accountCards().size == 1 }
        assertEquals(front, accountCards().single(), "the same account is in front again")
        layoutChoice("Stack").assertIsSelected()
        assertEquals(AccountsLayout.Stack, env!!.preferences.state.value.accountsLayout, "and the choice was never touched")
    }

    @Test
    fun spreadOutArchivedAccounts_areGatheredByAStackButtonBesideHidingThem() {
        // They have no selector of their own, so the button sits with the "Hide archived accounts" row that opens them.
        showWallet {
            account("Old savings", AccountKind.Bank, archived = true)
            account("Old wallet", AccountKind.Wallet, archived = true)
        }
        compose.onNodeWithText("Show 2 archived accounts").performClick()
        compose.waitUntil(10_000) { accountCards().size == 2 }
        val archivedFront = accountCards().single { it.endsWith("account 1 of 2") }

        compose.onNode(hasContentDescription(archivedFront)).performTouchInput { longClick() }
        compose.waitUntil(10_000) { accountCards().size == 3 }
        compose.onNodeWithContentDescription("Stack them again").assertIsDisplayed()
        layoutChoice("Stack").assertIsSelected()

        compose.onNodeWithContentDescription("Stack them again").performClick()
        compose.waitUntil(10_000) { accountCards().size == 2 }
        compose.onAllNodesWithContentDescription("Stack them again").assertCountEquals(0)
    }

    @Test
    fun holdingASpreadOutAccountOffersToManageIt() {
        showWallet()
        compose.onNode(hasContentDescription(accountCards().single())).performTouchInput { longClick() }
        compose.waitUntil(10_000) { accountCards().size == 3 }

        card("HDFC Savings").performTouchInput { longClick() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Archive").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Archive").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun theStackedFrontAccountCanStillBeManagedWithAScreenReader() {
        showWallet()
        val front = accountCards().single()

        val actions = compose.onNode(hasContentDescription(front)).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { actions.single { it.label == "Manage account" }.action() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Archive").fetchSemanticsNodes().isNotEmpty() }
    }

    // ------------------------------------------------------------ the card list

    @Test
    fun theCardListShowsEveryAccountAsACard_withItsBalanceAndItsStanding() {
        showWallet(AccountsLayout.Cards)

        val shown = accountCards()
        assertEquals(3, shown.size, "every account is a card: $shown")
        val credit = shown.single { it.startsWith("Credit Card, ") }
        assertTrue("balance ${money(-12_000_00)}" in credit, credit)
        assertTrue(", Owed" in credit, "an owed card says so in words: $credit")
        val bank = shown.single { it.startsWith("HDFC Savings, ") }
        assertTrue("balance ${money(38_000_00)}" in bank, bank)
        assertTrue(", HDFC Bank" in bank, "it names its bank: $bank")
        assertTrue(shown.single { it.startsWith("Cash, ") }.contains(", default account"), "the default account says so")
        compose.onNodeWithText("Add account").assertIsDisplayed()
    }

    @Test
    fun aCardFromTheListOpensItsHistory() {
        val environment = showWallet(AccountsLayout.Cards)

        card("Credit Card").performClick()
        compose.waitForIdle()

        val creditCard = environment.container.finance.state.value.accounts.single { it.name == "Credit Card" }
        assertEquals(listOf(creditCard.id!!), environment.navigator.accountHistories)
    }

    @Test
    fun aCardFromTheListCanBeManagedWithAPressAndHold() {
        showWallet(AccountsLayout.Cards)

        card("HDFC Savings").performTouchInput { longClick() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Make default").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Make default").assertIsDisplayed()
    }

    @Test
    fun anArchivedAccountIsAnArchivedCard() {
        showWallet(AccountsLayout.Cards) { account("Old Wallet", AccountKind.Wallet, archived = true) }
        assertEquals(3, accountCards().size, "archived accounts wait behind the toggle")

        compose.onNodeWithText("Show 1 archived account").performClick()
        compose.waitUntil(10_000) { accountCards().size == 4 }

        assertTrue(accountCards().single { it.startsWith("Old Wallet, ") }.contains(", archived"))
    }

    // ------------------------------------------------------------ the compact list

    @Test
    fun theCompactListIsTheRowsThereWereBefore() {
        showWallet(AccountsLayout.Rows)

        compose.onNodeWithText("Owed").assertIsDisplayed()
        compose.onNodeWithText("HDFC Savings").assertIsDisplayed()
        compose.onNodeWithText("Add account").assertIsDisplayed()
        assertTrue(accountCards().isEmpty(), "no account is drawn as a card")
    }

    // ------------------------------------------------------------ the place for the next account

    private val accountTileNote = "Cash, a bank, a card or a wallet"

    @Test
    fun theStackIsFollowedByASlimAddAccountTile_thatOpensTheNewAccountForm() {
        showWallet(AccountsLayout.Stack)

        compose.onNodeWithText(accountTileNote).assertIsDisplayed()
        val tile = compose.onNode(hasText(accountTileNote)).getUnclippedBoundsInRoot()
        assertTrue((tile.bottom - tile.top).value < 120f, "slim, under the stack: ${tile.bottom - tile.top}")
        compose.onNodeWithText("Add account").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Save account").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theCardListEndsWithAFullSizeAddAccountTile_shapedLikeTheCardsAboveIt() {
        showWallet(AccountsLayout.Cards)

        val tile = compose.onNode(hasText(accountTileNote)).getUnclippedBoundsInRoot()
        val width = (tile.right - tile.left).value
        val height = (tile.bottom - tile.top).value
        assertEquals(1.586f, width / height, 0.05f, "a card's proportions: ${width}x$height")
        // And it comes after the last account, not before.
        val last = accountCards().size
        assertEquals(3, last, "all three accounts are printed above it")
    }

    @Test
    fun theRowsKeepTheirAddAccountRow() {
        showWallet(AccountsLayout.Rows)

        assertTrue(compose.onAllNodesWithText(accountTileNote).fetchSemanticsNodes().isEmpty(), "no tile in the compact list")
        compose.onNodeWithText("Add account").assertIsDisplayed()
    }

    // ------------------------------------------------------------ the choice

    @Test
    fun theWalletOffersTheChoice_andKeepsIt() {
        val environment = showWallet()

        layoutChoice("Cards").performClick()
        compose.waitUntil(10_000) { accountCards().size == 3 }
        assertEquals(AccountsLayout.Cards, environment.preferences.state.value.accountsLayout)

        layoutChoice("Rows").performClick()
        compose.waitUntil(10_000) { accountCards().isEmpty() }
        compose.onNodeWithText("HDFC Savings").assertIsDisplayed()
        assertEquals(AccountsLayout.Rows, environment.preferences.state.value.accountsLayout)

        layoutChoice("Stack").performClick()
        compose.waitUntil(10_000) { accountCards().size == 1 }
        assertEquals(AccountsLayout.Stack, environment.preferences.state.value.accountsLayout)
    }
}

/** What an account card says, and what colours it wears. */
class AccountCardDataTest {
    private fun data(
        kind: AccountKind = AccountKind.Bank,
        bankName: String? = null,
        standing: String? = null,
        isDefault: Boolean = false,
        archived: Boolean = false,
        cards: Int = 0,
    ) = AccountCardData(
        id = 1, name = "Salary", kind = kind, bankName = bankName, balance = "Rs 1,000.00",
        standing = standing, isDefault = isDefault, archived = archived, cards = cards,
    )

    @Test
    fun aPlainAccountIsReadAsItsNameKindAndBalance() {
        assertEquals("Salary, Bank account, balance Rs 1,000.00", data().spoken)
    }

    @Test
    fun everythingTheCardShowsIsSpoken() {
        val spoken = data(AccountKind.Card, "HDFC Bank", "Owed", isDefault = true, archived = true, cards = 2).spoken
        assertEquals("Salary, Card, HDFC Bank, balance Rs 1,000.00, Owed, default account, archived, 2 saved cards", spoken)
        assertTrue(data(cards = 1).spoken.endsWith("1 saved card"), "one card is not 'cards'")
    }

    @Test
    fun anAccountWearsItsBanksColours_andOneWithNoBankWearsItsKinds() {
        assertEquals("hdfc", AccountCardDesign.themeFor("HDFC Bank", AccountKind.Bank).id)
        assertEquals("hdfc", AccountCardDesign.themeFor("HDFC Bank", AccountKind.Savings).id, "the kind does not change a bank's colours")

        val kinds = AccountKind.entries.map { AccountCardDesign.themeFor(null, it) }
        assertEquals(AccountKind.entries.size, kinds.map { it.id }.distinct().size, "each kind has a look of its own")
        assertEquals(AccountKind.entries.size, kinds.map { it.gradientColors.first() }.distinct().size, "and a colour of its own")
        for (theme in kinds) assertTrue(theme.gradientColors.size == 3, theme.id)
    }
}
