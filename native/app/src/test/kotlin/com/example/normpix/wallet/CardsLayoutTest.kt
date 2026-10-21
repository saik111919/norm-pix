package com.example.normpix.wallet

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.screens.WalletScreen
import com.example.normpix.settings.CardsLayout
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val HDFC_CARD = StoredCard(
    id = "card-1",
    bankName = "HDFC Bank",
    cardholderName = "Asha Rao",
    cardNumber = "4111111111114242",
    expiryMonth = "07",
    expiryYear = "29",
    cvv = "737",
    kind = CardKind.Debit,
    createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
)

private val ICICI_CARD = StoredCard(
    id = "card-2",
    bankName = "ICICI Bank",
    cardholderName = "Asha Rao",
    cardNumber = "5555555555554444",
    expiryMonth = "11",
    expiryYear = "28",
    cvv = "123",
    kind = CardKind.Credit,
    createdAt = LocalDateTime.of(2026, 2, 1, 0, 0),
)

/**
 * The Wallet's saved cards, laid out four ways (the accounts above them have three): every card in full (as before), a stack with
 * one in front, one line a card, or a carousel with the card in front's details under it. The choice is the user's, kept in the
 * preferences and made from the Wallet itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CardsLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private var env: TestEnvironment? = null

    @After
    fun tearDown() {
        env?.close()
    }

    private fun showWallet(layout: CardsLayout? = null): TestEnvironment {
        Locale.setDefault(Locale.US)
        val environment = TestEnvironment("cards_layout_test", InMemoryVault(listOf(HDFC_CARD, ICICI_CARD)))
        env = environment
        if (layout != null) environment.preferences.setCardsLayout(layout)
        compose.setContent { environment.Content { WalletScreen() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add card").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        return environment
    }

    @Test
    fun theCardsAreStillEveryCardInFull_unlessAnotherLayoutIsChosen() {
        val environment = showWallet()

        assertEquals(CardsLayout.Cards, environment.preferences.state.value.cardsLayout)
        // The Wallet offers the choice for the cards as it does for the accounts: two of each segment on the page.
        compose.onAllNodesWithText("Rows").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Stack them again").assertCountEquals(0)
    }

    @Test
    fun theStackShowsOneCardInFront_andTheChoiceIsKept() {
        val environment = showWallet()

        // The second "Stack" is the cards': the accounts' comes first on the page.
        compose.onAllNodesWithText("Stack")[1].performClick()
        compose.waitForIdle()

        assertEquals(CardsLayout.Stack, environment.preferences.state.value.cardsLayout)
        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).assertExists()
        compose.onAllNodesWithContentDescription("ICICI Bank", substring = true).assertCountEquals(0)
    }

    @Test
    fun spreadOutCards_areGatheredByTheLayoutSelectorsStackSegment() {
        val environment = showWallet(CardsLayout.Stack)
        // The second "Stack" is the cards': the accounts' comes first on the page.
        compose.onAllNodesWithText("Stack")[1].assertIsSelected()

        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("ICICI Bank credit card, ending in", substring = true).assertExists()

        // Spread out, no layout is the one in force, and the selector's "Stack" is the way back - not a button of its own.
        compose.onAllNodesWithContentDescription("Stack them again").assertCountEquals(0)
        compose.onAllNodesWithText("Stack")[1].assertIsNotSelected()
        compose.onAllNodesWithText("Stack")[1].performClick()
        compose.waitForIdle()

        compose.onAllNodesWithContentDescription("ICICI Bank", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Stack")[1].assertIsSelected()
        assertEquals(CardsLayout.Stack, environment.preferences.state.value.cardsLayout, "the choice was never touched")
    }

    @Test
    fun spreadOutCards_goBackToTheOtherLayoutsWhenOneIsPicked() {
        val environment = showWallet(CardsLayout.Stack)
        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onAllNodesWithText("Rows")[1].performClick()
        compose.waitForIdle()

        assertEquals(CardsLayout.Rows, environment.preferences.state.value.cardsLayout)
        compose.onNodeWithText("ICICI Bank •••• 4444").assertIsDisplayed()
        compose.onAllNodesWithText("Stack")[1].assertIsNotSelected()
        // The cards are out of the stack for good: the choice made shows as chosen, not "none".
        compose.onAllNodesWithText("Rows")[1].assertIsSelected()
    }

    @Test
    fun spreadOutCards_goBackToTheCarouselWhenItIsPicked() {
        val environment = showWallet(CardsLayout.Stack)
        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).performTouchInput { longClick() }
        compose.waitForIdle()

        compose.onNodeWithText("Carousel").performClick()
        compose.waitForIdle()

        assertEquals(CardsLayout.Carousel, environment.preferences.state.value.cardsLayout)
        compose.onNodeWithText("Carousel").assertIsSelected()
        // And "Stack" then stacks them, rather than only gathering cards that are no longer spread out.
        compose.onAllNodesWithText("Stack")[1].performClick()
        compose.waitForIdle()
        assertEquals(CardsLayout.Stack, environment.preferences.state.value.cardsLayout)
    }

    @Test
    fun theRowsAreOneLineACard_andOpenTheCard() {
        showWallet(CardsLayout.Rows)

        compose.onNodeWithText("HDFC Bank •••• 4242").assertIsDisplayed()
        compose.onNodeWithText("Debit card · Expires 07/29").assertIsDisplayed()
        compose.onNodeWithText("ICICI Bank •••• 4444").assertIsDisplayed()
        compose.onNodeWithText("Credit card · Expires 11/28").assertIsDisplayed()

        compose.onNodeWithText("ICICI Bank •••• 4444").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Card number, ending in 4444").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theStackOpensToTheCardsDetails() {
        showWallet(CardsLayout.Stack)

        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Card number, ending in 4242").fetchSemanticsNodes().isNotEmpty() }
    }

    // --- the place for the next card ------------------------------------------------------------------------------

    private val tileNote = "Encrypted, and kept on this device"

    @Test
    fun theCardsEndWithAnAddCardTile_thatOpensTheCardForm() {
        showWallet(CardsLayout.Cards)

        compose.onNodeWithText(tileNote).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(tileNote).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Save card").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theStackIsFollowedByASlimAddCardTile() {
        showWallet(CardsLayout.Stack)

        compose.onNodeWithText(tileNote).performScrollTo().assertIsDisplayed()
        // Slim: a card wide, nowhere near a card tall.
        val tile = compose.onNode(hasText(tileNote)).getUnclippedBoundsInRoot()
        assertTrue((tile.bottom - tile.top).value < 120f, "a slim tile under the stack, not a full card: ${tile.bottom - tile.top}")
    }

    @Test
    fun theRowsKeepAnAddCardRowInsteadOfATile() {
        showWallet(CardsLayout.Rows)

        compose.onAllNodesWithText(tileNote).assertCountEquals(0)
        compose.onAllNodesWithText("Add card").onLast().performScrollTo()
        compose.onAllNodesWithText("Add card").onLast().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Save card").fetchSemanticsNodes().isNotEmpty() }
    }

    // --- the carousel ---------------------------------------------------------------------------------------------

    @Test
    fun theCarouselIsAFourthChoice_andTheChoiceIsKept() {
        val environment = showWallet()

        compose.onNodeWithText("Carousel").performClick()
        compose.waitForIdle()

        assertEquals(CardsLayout.Carousel, environment.preferences.state.value.cardsLayout)
        compose.onNodeWithText("Carousel").assertIsSelected()
        // The card in front, with the next one peeking in beside it.
        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).assertExists()
        compose.onNodeWithContentDescription("ICICI Bank credit card, ending in", substring = true).assertExists()
    }

    @Test
    fun theCarouselListsTheDetailsOfTheCardInFront_hiddenUntilRevealed() {
        showWallet(CardsLayout.Carousel)

        compose.onNodeWithText("HDFC Bank").assertExists()
        compose.onNodeWithText("Debit card · Visa · Ending in 4242").assertExists()
        compose.onNodeWithText("Card number").assertExists()
        compose.onNodeWithText("•••• •••• •••• 4242").assertExists()
        compose.onNodeWithText("Asha Rao").assertExists()
        compose.onNodeWithText("07/29").assertExists()
        compose.onNodeWithText("•••").assertExists()
        compose.onAllNodesWithText("4111 1111 1111 4242").assertCountEquals(0)
        compose.onAllNodesWithText("737").assertCountEquals(0)
        // The other card's details are not on the page: only the one in front is listed.
        compose.onAllNodesWithText("ICICI Bank").assertCountEquals(0)
        compose.onNodeWithContentDescription("Card 1 of 2").assertExists()
    }

    @Test
    fun revealingInTheCarouselShowsTheWholeNumberAndCvv() {
        showWallet(CardsLayout.Carousel)

        compose.onNodeWithText("Reveal").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("4111 1111 1111 4242").assertExists()
        compose.onNodeWithText("737").assertExists()
        compose.onNodeWithText("Hide").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("4111 1111 1111 4242").assertCountEquals(0)
    }

    @Test
    fun swipingTheCarouselBringsTheNextCardForward_andItsDetailsFollow() {
        showWallet(CardsLayout.Carousel)

        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("ICICI Bank").assertExists()
        compose.onNodeWithText("Credit card · Mastercard · Ending in 4444").assertExists()
        compose.onNodeWithText("•••• •••• •••• 4444").assertExists()
        compose.onAllNodesWithText("HDFC Bank").assertCountEquals(0)
        compose.onNodeWithContentDescription("Card 2 of 2").assertExists()
    }

    @Test
    fun aCardAtTheSideComesForwardWhenTapped() {
        showWallet(CardsLayout.Carousel)

        // The sliver of it that shows: its middle is off the screen.
        compose.onNodeWithContentDescription("ICICI Bank credit card, ending in", substring = true)
            .performTouchInput { click(Offset(8f, centerY)) }
        compose.waitForIdle()

        compose.onNodeWithText("ICICI Bank").assertExists()
        compose.onNodeWithContentDescription("Card 2 of 2").assertExists()
    }

    @Test
    fun aCardAtTheSideOffersNoActionsOfItsOwnToAScreenReader() {
        showWallet(CardsLayout.Carousel)

        // The card in front keeps what a card has: turning over, Reveal, Copy, Edit, Delete.
        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        // A card beside it says which card it is and where it is, and has one action: bring it forward.
        val side = compose.onNodeWithContentDescription("ICICI Bank credit card, ending in 4444, card 2 of 2")
        side.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
        side.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
    }

    @Test
    fun theCarouselEndsWithTheSlimAddCardTile() {
        showWallet(CardsLayout.Carousel)

        compose.onNodeWithText(tileNote).performScrollTo().assertIsDisplayed()
        val tile = compose.onNode(hasText(tileNote)).getUnclippedBoundsInRoot()
        assertTrue((tile.bottom - tile.top).value < 120f, "a slim tile under the carousel, not a full card: ${tile.bottom - tile.top}")
        compose.onNodeWithText(tileNote).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Save card").fetchSemanticsNodes().isNotEmpty() }
    }
}
