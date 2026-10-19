package com.example.normpix.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.normpix.screens.CardsOnHome
import com.example.normpix.settings.ClipboardTimeout
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.clipboard.CardCopySheet
import com.example.normpix.wallet.clipboard.CardField
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The CVV is optional wherever a card is added or edited: a card without one saves, shows nothing in its place ("No CVV"
 * on its back, no row in its details, nothing to copy), and with Save CVV off the field isn't there at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CvvOptionalUiTest {
    @get:Rule
    val compose = createComposeRule()

    private var env: TestEnvironment? = null
    private val at = LocalDateTime.of(2026, 1, 1, 0, 0)

    private fun card(cvv: String) = StoredCard(
        id = "card-1",
        bankName = "HDFC Bank",
        cardholderName = "Asha Rao",
        cardNumber = "4111111111114242",
        expiryMonth = "07",
        expiryYear = "29",
        cvv = cvv,
        kind = CardKind.Debit,
        createdAt = at,
        bankKey = "hdfc",
    )

    @After
    fun tearDown() {
        env?.close()
    }

    private fun environment(vararg cards: StoredCard, configure: (TestEnvironment) -> Unit = {}): TestEnvironment {
        Locale.setDefault(Locale.US)
        val made = TestEnvironment("cvv_optional_ui", InMemoryVault(cards.toList()))
        env = made
        configure(made)
        return made
    }

    // ----- what a card without a CVV shows -----

    @Test
    fun `the copy sheet has no CVV row for a card without one`() {
        val e = environment()
        compose.setContent { e.Content { CardCopySheet(card(""), ClipboardTimeout.Sec10, onCopy = { true }, onDone = {}) } }
        for (label in listOf("Card number", "Cardholder name", "Expiry date")) compose.onNodeWithText(label).assertIsDisplayed()
        compose.onAllNodesWithText("CVV").assertCountEquals(0)
    }

    @Test
    fun `the copy sheet still has it for a card that has one`() {
        val e = environment()
        val copies = mutableListOf<CardField>()
        compose.setContent { e.Content { CardCopySheet(card("737"), ClipboardTimeout.Sec10, onCopy = { copies += it; true }, onDone = {}) } }
        compose.onNodeWithText("CVV").performClick()
        assertEquals(listOf(CardField.Cvv), copies)
    }

    @Test
    fun `a card without a CVV opens its details with no CVV row`() {
        val e = environment(card(""))
        compose.setContent { e.Content { Column { CardsOnHome() } } }
        compose.waitUntil(10_000) { e.container.cards.state.value is CardsState.Ready }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Card number, ending in 4242").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithContentDescription("Expiry date, 07/29").assertExists()
        compose.onAllNodesWithContentDescription("CVV, ", substring = true).assertCountEquals(0)
    }

    @Test
    fun `a card that has a CVV still shows its row`() {
        val e = environment(card("737"))
        compose.setContent { e.Content { Column { CardsOnHome() } } }
        compose.waitUntil(10_000) { e.container.cards.state.value is CardsState.Ready }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("HDFC Bank debit card, ending in", substring = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("CVV, hidden").fetchSemanticsNodes().isNotEmpty() }
    }

    // ----- the form -----

    private fun showForm(initial: StoredCard?, onDone: (StoredCard?) -> Unit) {
        val e = environment(configure = {})
        compose.setContent { e.Content { AddCardSheet(initial, onDone) } }
        compose.waitForIdle()
    }

    @Test
    fun `a card can be edited and saved with the CVV left empty`() {
        var saved: StoredCard? = null
        showForm(card("123")) { saved = it }

        compose.onNodeWithText("CVV").performTextClearance()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        val card = assertNotNull(saved)
        assertEquals("", card.cvv)
        assertEquals("4111111111114242", card.cardNumber)
    }

    @Test
    fun `a card that never had a CVV opens with the field empty, marked optional, and saves`() {
        var saved: StoredCard? = null
        showForm(card("")) { saved = it }

        compose.onNodeWithText("Optional").assertIsDisplayed()
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }
        assertEquals("", assertNotNull(saved).cvv)
    }

    @Test
    fun `a CVV that is started must be finished`() {
        var saved: StoredCard? = null
        showForm(card("")) { saved = it }

        compose.onNodeWithText("CVV").performTextInput("12")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("3–4 digits").assertIsDisplayed()
        assertNull(saved)

        compose.onNodeWithText("CVV").performTextInput("3")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }
        assertEquals("123", assertNotNull(saved).cvv)
    }

    @Test
    fun `with Save CVV off the field is gone and an edited card is saved without one`() {
        val e = environment { it.preferences.setCardCvvEnabled(false) }
        var saved: StoredCard? = null
        compose.setContent { e.Content { AddCardSheet(card("123")) { saved = it } } }
        compose.waitForIdle()

        compose.onAllNodesWithText("CVV").assertCountEquals(0)
        compose.onAllNodesWithText("Optional").assertCountEquals(0)
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals("", assertNotNull(saved).cvv)
        assertEquals("4111111111114242", saved?.cardNumber)
    }

    // ----- Store cards off -----

    @Test
    fun `with Store cards off Home shows no cards, whatever the vault holds`() {
        val e = environment(card("123")) { it.preferences.setWalletCardsEnabled(false) }
        compose.setContent { e.Content { Column { CardsOnHome() } } }
        compose.waitUntil(10_000) { e.container.cards.state.value is CardsState.Ready }
        compose.waitForIdle()

        compose.onAllNodesWithContentDescription("card, ending in", substring = true).assertCountEquals(0)
    }
}
