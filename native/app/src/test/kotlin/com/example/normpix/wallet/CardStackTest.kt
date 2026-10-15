package com.example.normpix.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.normpix.screens.CardsOnHome
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val HDFC = StoredCard(
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

private val ICICI = StoredCard(
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
 * The saved cards as a stack on Home (its cards block, composed on its own): the first in front, the rest behind it; a tap on the front one opens its details in
 * a sheet - reached through the same NFC-tag step, lock and clipboard as Wallet - and a swipe brings the next forward.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CardStackTest {
    @get:Rule
    val compose = createComposeRule()

    private var env: TestEnvironment? = null
    private val stranger = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)

    @After
    fun tearDown() {
        env?.close()
    }

    /** Home's cards block, on its own, with [cards] in the vault once it has read them; composing the whole page only races its load. */
    private fun showStack(vararg cards: StoredCard, configure: (TestEnvironment) -> Unit = {}): TestEnvironment {
        Locale.setDefault(Locale.US)
        val environment = TestEnvironment("card_stack_test", InMemoryVault(cards.toList()))
        env = environment
        configure(environment)
        compose.setContent { environment.Content { Column { CardsOnHome() } } }
        compose.waitUntil(10_000) { environment.container.cards.state.value is CardsState.Ready }
        compose.waitForIdle()
        return environment
    }

    private fun frontCard(bank: String, kind: String) =
        compose.onNodeWithContentDescription("$bank $kind card, ending in", substring = true)

    private fun numberOnScreen(digits: String) =
        compose.onAllNodes(hasContentDescription(digits, substring = true)).fetchSemanticsNodes().isNotEmpty()

    private fun openFront() {
        frontCard("HDFC Bank", "debit").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Card number, ending in 4242").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theFirstCardIsInFront_andTheOnesBehindItAreOnlyDepth() {
        showStack(HDFC, ICICI)

        frontCard("HDFC Bank", "debit").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("ICICI Bank", substring = true).assertCountEquals(0)
    }

    @Test
    fun withoutCardsHomeHasNoStack() {
        showStack()

        compose.onAllNodesWithContentDescription("card, ending in", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Cards are locked").assertCountEquals(0)
    }

    @Test
    fun tappingTheFrontCardOpensItsDetails_withTheNumberAndCvvHidden() {
        showStack(HDFC, ICICI)
        openFront()

        compose.onNodeWithText("Debit card ending 4242").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expiry date, 07/29").assertExists()
        compose.onNodeWithContentDescription("Cardholder name, Asha Rao").assertExists()
        compose.onNodeWithContentDescription("CVV, hidden").assertExists()
        assertFalse(numberOnScreen("4111 1111 1111 4242"), "the number stays hidden until revealed")
        assertFalse(numberOnScreen("737"), "and so does the CVV")
    }

    @Test
    fun tappingADetailCopiesIt_andTheSheetSaysSo() {
        val environment = showStack(HDFC)
        openFront()

        compose.onNodeWithContentDescription("Card number, ending in 4242").performClick()
        compose.waitForIdle()

        assertEquals(listOf("4111111111114242"), environment.clipboard.copies.map { it.value })
        compose.onNodeWithText("Card number copied", substring = true).assertIsDisplayed()
        assertFalse(numberOnScreen("4111 1111 1111 4242"), "copying never shows the card")
    }

    @Test
    fun revealingFromTheSheetGoesThroughTheNfcTagStep() {
        val environment = showStack(HDFC) { it.preferences.setNfcTagRevealEnabled(true) }
        openFront()

        compose.onNodeWithText("Tap NFC to reveal").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hold an NFC tag near your phone").assertIsDisplayed()
        assertFalse(numberOnScreen("4111 1111 1111 4242"), "nothing is shown before a tap")

        var accepted = false
        compose.runOnUiThread { accepted = environment.nfc.tap(stranger) }
        compose.waitForIdle()

        assertTrue(accepted)
        compose.onNodeWithContentDescription("Card number, 4111 1111 1111 4242").assertExists()
        compose.onNodeWithContentDescription("CVV, 737").assertExists()
    }

    @Test
    fun copyingFromTheSheetAlsoNeedsTheNfcTag() {
        val environment = showStack(HDFC) { it.preferences.setNfcTagRevealEnabled(true) }
        openFront()

        compose.onNodeWithContentDescription("Card number, ending in 4242").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hold an NFC tag near your phone").assertIsDisplayed()
        assertTrue(environment.clipboard.copies.isEmpty(), "nothing is copied before a tap")

        compose.runOnUiThread { environment.nfc.tap(stranger) }
        compose.waitForIdle()

        assertEquals(listOf("4111111111114242"), environment.clipboard.copies.map { it.value })
    }

    @Test
    fun swipingTheFrontCardBringsTheNextOneForward() {
        showStack(HDFC, ICICI)

        frontCard("HDFC Bank", "debit").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        frontCard("ICICI Bank", "credit").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("HDFC Bank", substring = true).assertCountEquals(0)

        // Round the stack: swiping the last one forward brings the first back.
        frontCard("ICICI Bank", "credit").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        frontCard("HDFC Bank", "debit").assertIsDisplayed()
    }

    @Test
    fun behindTheWalletLockTheStackIsOneRowAskingToUnlock() {
        showStack(HDFC, ICICI) { it.preferences.setWalletLockEnabled(true) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Cards are locked").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Unlock to see them here").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("card, ending in", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("HDFC Bank", substring = true).assertCountEquals(0)
    }

    @Test
    fun deletingTheCardFromItsSheetClosesTheSheet_andTheNextCardStepsUp() {
        val environment = showStack(HDFC, ICICI)
        openFront()

        compose.onNodeWithContentDescription("Delete card").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Delete HDFC Bank card?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(10_000) { environment.vault.cards.size == 1 }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Card number, ending in 4242").fetchSemanticsNodes().isEmpty() }

        frontCard("ICICI Bank", "credit").assertIsDisplayed()
    }

    @Test
    fun pressingAndHoldingUnfoldsEveryCard_andStackGathersThemAgain() {
        showStack(HDFC, ICICI)
        compose.onAllNodesWithContentDescription("ICICI Bank", substring = true).assertCountEquals(0)

        frontCard("HDFC Bank", "debit").performTouchInput { longClick() }
        compose.waitForIdle()
        frontCard("HDFC Bank", "debit").assertIsDisplayed()
        frontCard("ICICI Bank", "credit").assertIsDisplayed()

        compose.onNodeWithContentDescription("Stack them again").performClick()
        compose.waitForIdle()
        frontCard("HDFC Bank", "debit").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("ICICI Bank", substring = true).assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Stack them again").assertCountEquals(0)
    }

    @Test
    fun theStackButtonSitsInTheCardsHeader_atItsEnd_andMovesNothing() {
        showStack(HDFC, ICICI)
        compose.onAllNodesWithContentDescription("Stack them again").assertCountEquals(0)
        val before = compose.onNodeWithText("Cards").getUnclippedBoundsInRoot()

        frontCard("HDFC Bank", "debit").performTouchInput { longClick() }
        compose.waitForIdle()

        val header = compose.onNodeWithText("Cards").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithContentDescription("Stack them again").getUnclippedBoundsInRoot()
        val firstCard = frontCard("HDFC Bank", "debit").getUnclippedBoundsInRoot()
        assertEquals(before.top to before.bottom, header.top to header.bottom, "the button comes in the room the header keeps: the title does not move down or up")
        assertTrue(button.top < header.bottom && button.bottom > header.top, "on the header's row: $button, $header")
        assertTrue(button.left >= header.right, "after the title, at the end of the row: $button, $header")
        assertTrue(button.bottom <= firstCard.top, "above the cards, not between them: $button, $firstCard")
    }

    @Test
    fun anUnfoldedCardOpensItsDetails() {
        showStack(HDFC, ICICI)
        frontCard("HDFC Bank", "debit").performTouchInput { longClick() }
        compose.waitForIdle()

        frontCard("ICICI Bank", "credit").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Card number, ending in 4444").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aSingleCardHasNothingToUnfold() {
        showStack(HDFC)
        frontCard("HDFC Bank", "debit").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Stack them again").assertCountEquals(0)
    }
}
