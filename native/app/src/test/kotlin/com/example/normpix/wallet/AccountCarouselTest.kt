package com.example.normpix.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.screens.WalletScreen
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
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
 * The Wallet's accounts as a 3D carousel, as the saved cards are: the account in front facing you with the next ones turned away
 * beside it. The card says who (bank, kind, name, flags); the panel under them says what it holds - its balance and standing, then
 * either its share of your money and saved cards or, for a card with a limit, what is left of it, how much is used and when the
 * bill is due - with a way to its history and to managing it. Nothing is said in both places.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class AccountCarouselTest {
    @get:Rule
    val compose = createComposeRule()

    private var env: TestEnvironment? = null
    private val now = LocalDateTime.of(2026, 10, 1, 9, 0)

    @After
    fun tearDown() {
        env?.close()
    }

    private fun account(name: String, kind: AccountKind, opening: Long = 0, bankKey: String? = null, limit: Long? = null, dueDay: Int? = null): Account =
        runBlocking {
            env!!.container.finance.saveAccount(
                Account(
                    name = name, kind = kind, openingBalance = opening, bankKey = bankKey, creditLimit = limit, dueDay = dueDay,
                    createdAt = now, updatedAt = now,
                ),
            )
        }

    /** The Wallet with Cash (the default), "HDFC Savings" with Rs 38,000 and a "Credit Card" owing Rs 12,000 (of a Rs 50,000 limit when [limit]). */
    private fun showWallet(layout: AccountsLayout? = AccountsLayout.Carousel, limit: Boolean = false): TestEnvironment {
        Locale.setDefault(Locale.US)
        val environment = TestEnvironment("account_carousel_test")
        env = environment
        account("HDFC Savings", AccountKind.Bank, opening = 38_000_00, bankKey = "hdfc")
        if (limit) {
            account("Credit Card", AccountKind.Card, opening = -12_000_00, limit = 50_000_00, dueDay = 12)
        } else {
            account("Credit Card", AccountKind.Card, opening = -12_000_00)
        }
        if (layout != null) environment.preferences.setAccountsLayout(layout)
        compose.setContent { environment.Content { WalletScreen() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add account").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        return environment
    }

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun money(minor: Long) = env!!.container.finance.state.value.money.format(minor)

    /** What a screen reader finds for the accounts beside the one in front: each says its balance and its place (the front one says who it is). */
    private fun sideCards(): List<String> = compose.onAllNodes(hasContentDescription(", balance ", substring = true))
        .fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.ContentDescription].first() }

    private fun bringForward(name: String) {
        compose.onNode(hasContentDescription("$name, ", substring = true) and hasContentDescription(", balance ", substring = true))
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun theCarouselIsAFourthChoiceForTheAccounts_andTheChoiceIsKept() {
        val environment = showWallet(layout = null)

        // The accounts' selector comes first on the page, the cards' second.
        val carousel = compose.onAllNodes(hasText("Carousel") and radio)[0]
        carousel.performClick()
        compose.waitForIdle()

        assertEquals(AccountsLayout.Carousel, environment.preferences.state.value.accountsLayout)
        compose.onAllNodes(hasText("Carousel") and radio)[0].assertIsSelected()
        assertEquals(AccountsLayout.Carousel, com.example.normpix.settings.AppPreferences(environment.prefsStore).state.value.accountsLayout, "kept")
    }

    @Test
    fun theFrontCardIsHeardAsWhoItIs_andTheOthersAreDescribedBesideIt() {
        showWallet()

        val sides = sideCards()
        assertEquals(2, sides.size, "the two beside the front one say their balance: $sides")
        assertTrue(sides.any { it.startsWith("HDFC Savings, ") && it.endsWith("account 2 of 3") }, sides.toString())
        assertTrue(sides.any { it.startsWith("Credit Card, ") && it.endsWith("account 3 of 3") }, sides.toString())
        // The one in front says who it is and no figures: the panel under it, read next, says those.
        compose.onNode(hasContentDescription("Cash, Cash, default account")).assertIsDisplayed()
        compose.onNodeWithContentDescription("Card 1 of 3").assertIsDisplayed()
    }

    @Test
    fun thePanelSaysTheBalance_andTheCardSaysWho() {
        showWallet()

        compose.onNodeWithText("BALANCE").assertIsDisplayed()
        compose.onNodeWithText("OF YOUR MONEY").assertIsDisplayed()
        // Cash has no bank, so no cards to count; and the panel does not say again what the card says: its kind, its bank.
        compose.onAllNodesWithText("SAVED CARDS").assertCountEquals(0)
        compose.onAllNodesWithText("TYPE").assertCountEquals(0)
        compose.onAllNodesWithText("BANK").assertCountEquals(0)
        // The flag is printed on the card (found in the unmerged tree, where printed text is), and nowhere else.
        compose.onAllNodesWithText("DEFAULT", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("Default").assertCountEquals(0)
        // The details of the other accounts are not read out: only the one in front is.
        compose.onAllNodesWithText("HDFC Savings").assertCountEquals(0)
    }

    @Test
    fun aBalanceIsSaidOnce_onThePanel_notOnTheCardAsWell() {
        showWallet()
        bringForward("HDFC Savings")

        // The panel's, and the overview's "In accounts": two, not three - the card prints no balance.
        compose.onAllNodesWithText(money(38_000_00), useUnmergedTree = true).assertCountEquals(2)
        // And its name is printed once, on its card: the panel does not head itself with it again.
        compose.onAllNodesWithText("HDFC Savings", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun swipingBringsTheNextAccountForward_andItsPanelFollows() {
        showWallet()

        compose.onNode(hasContentDescription("Cash, ", substring = true))
            .performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX - 150f, centerY), durationMillis = 300) }
        compose.waitForIdle()

        compose.onNode(hasContentDescription("HDFC Savings, Bank account, HDFC Bank")).assertIsDisplayed()
        // All of what is held is in it; it has a bank, so the cards saved for that bank are counted (none here).
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("SAVED CARDS").assertIsDisplayed()
        // The balance is on the panel - and in the overview above, as what is held.
        compose.onAllNodesWithText(money(38_000_00)).assertCountEquals(2)
        compose.onAllNodesWithText("0%").assertCountEquals(0)
    }

    @Test
    fun anAccountWithNothingInItIsZeroPercentOfYourMoney_andOneThatIsOwedHasNoShare() {
        showWallet()
        // Cash is in front, empty, with Rs 38,000 held elsewhere.
        compose.onNodeWithText("0%").assertIsDisplayed()

        bringForward("Credit Card")
        compose.onNodeWithText("BALANCE · OWED").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun hiddenAmountsGiveNoShareEither() {
        val environment = showWallet()
        environment.preferences.setAmountsHidden(true)
        compose.waitForIdle()
        bringForward("HDFC Savings")

        // A share would give away what the hidden amounts do not.
        compose.onAllNodesWithText("100%").assertCountEquals(0)
        compose.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun hiddenAmountsAreSaidToBeHidden_notReadOutAsDots() {
        val environment = showWallet()
        environment.preferences.setAmountsHidden(true)
        compose.waitForIdle()

        // The balance on the panel, and the dash where a share would be (said as what it is: hidden).
        assertTrue(compose.onAllNodes(hasContentDescription("hidden")).fetchSemanticsNodes().size >= 2, "the balance and the share are said to be hidden")
        // And the cards beside the front one, which have no panel of their own to say it.
        val sides = sideCards()
        assertTrue(sides.isNotEmpty() && sides.all { ", balance hidden, " in it }, sides.toString())
    }

    @Test
    fun aShareThatDoesNotApplyIsSaidInWords_notAsADash() {
        showWallet()
        bringForward("Credit Card")

        // The card is owed, so "of your money" has no figure: a screen reader is told that, not given a dash.
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNode(hasContentDescription("not applicable")).assertIsDisplayed()
    }

    @Test
    fun aCardWithALimitSaysWhatIsLeftHowMuchIsUsedAndWhenTheBillIsDue() {
        showWallet(limit = true)
        bringForward("Credit Card")

        compose.onNode(hasContentDescription("Credit Card, Card")).assertIsDisplayed()
        compose.onNodeWithText("BALANCE · OWED").assertIsDisplayed()
        compose.onNodeWithText("CREDIT LEFT").assertIsDisplayed()
        // What is left of the limit, without the word "left" the label already says.
        compose.onNodeWithText(env!!.container.finance.state.value.money.format(38_000_00, trimZeroFraction = true)).assertIsDisplayed()
        compose.onNodeWithText("USED").assertIsDisplayed()
        compose.onNodeWithText("24%").assertIsDisplayed()
        compose.onNodeWithText("BILL").assertIsDisplayed()
        // The name is on its card; the share and the saved cards are a card without a limit's: not here.
        compose.onAllNodesWithText("Credit Card").assertCountEquals(0)
        compose.onAllNodesWithText("SAVED CARDS").assertCountEquals(0)
        compose.onAllNodesWithText("OF YOUR MONEY").assertCountEquals(0)
    }

    @Test
    fun theHistoryButtonOpensTheFrontAccountsHistory() {
        val environment = showWallet()
        val cash = environment.container.finance.state.value.accounts.first { it.name == "Cash" }

        compose.onNodeWithText("History").performClick()
        compose.waitForIdle()

        assertEquals(listOf(cash.id), environment.navigator.accountHistories)
    }

    @Test
    fun managingIsAButtonWithItsOwnName_notOnlyAPressAndHold() {
        showWallet()

        compose.onNodeWithContentDescription("Manage account").assertIsDisplayed()
    }

    @Test
    fun anAccountBesideTheFrontOneHasOneActionToBringItForward() {
        showWallet()

        val side = compose.onNode(hasContentDescription("HDFC Savings, ", substring = true) and hasContentDescription(", balance ", substring = true))
        side.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        side.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
        side.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
    }
}
