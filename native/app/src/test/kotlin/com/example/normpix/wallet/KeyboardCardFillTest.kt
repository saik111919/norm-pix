package com.example.normpix.wallet

import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assert
import com.example.normpix.settings.ui.KEYBOARD_CARD_FILL_TITLE
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Wallet & Cards > Keyboard card fill (owner, 2026-11-14: "when adding cards ... the keyboard gives an option to scan the
 * cards like how Swiggy does"). Off by default, the card form is hidden from the phone's autofill service altogether; on, its fields
 * say which of the card's details they are, so the service can offer a saved card or "Scan card" above the keyboard. A file never
 * switches it on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class KeyboardCardFillTest {
    @get:Rule
    val compose = createComposeRule()

    private var env: TestEnvironment? = null

    @After
    fun tearDown() {
        env?.close()
    }

    private val card = StoredCard(
        id = "card-1",
        bankName = "HDFC Bank",
        cardholderName = "Asha Rao",
        cardNumber = "4111111111114242",
        expiryMonth = "07",
        expiryYear = "29",
        cvv = "",
        kind = CardKind.Debit,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        bankKey = "hdfc",
    )

    private fun showForm(fill: Boolean, onDone: (StoredCard?) -> Unit = {}): TestEnvironment {
        Locale.setDefault(Locale.US)
        val e = TestEnvironment("keyboard_card_fill", InMemoryVault(emptyList()))
        env = e
        e.preferences.setCardKeyboardFill(fill)
        compose.setContent { e.Content { AddCardSheet(card, onDone) } }
        compose.waitForIdle()
        return e
    }

    /** The text field whose label is [label] (its own node, not the label's). */
    private fun field(label: String) = compose.onNode(hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))

    private val cardFields = listOf(
        "Card number" to ContentType.CreditCardNumber,
        "Month" to ContentType.CreditCardExpirationMonth,
        "Year" to ContentType.CreditCardExpirationYear,
        "CVV" to ContentType.CreditCardSecurityCode,
        "Cardholder name" to ContentType.PersonFullName,
    )

    @Test
    fun `off by default, kept out of a backup, and found by search`() {
        val e = TestEnvironment("keyboard_card_fill_prefs", InMemoryVault(emptyList()))
        env = e
        assertFalse(e.preferences.state.value.cardKeyboardFill, "the card form stays hidden from autofill unless asked")
        e.preferences.setCardKeyboardFill(true)
        assertTrue(e.preferences.state.value.cardKeyboardFill)
        assertFalse("card_keyboard_fill" in e.preferences.exportAll(), "a backup does not carry it")

        for (query in listOf("scan card", "autofill", "keyboard")) {
            assertTrue(SettingsCatalog.search(query).any { it.title == KEYBOARD_CARD_FILL_TITLE }, "'$query' finds it")
        }
    }

    @Test
    fun `off - every card field is hidden from the autofill service`() {
        showForm(fill = false)
        for ((label, type) in cardFields) {
            // No data type is what keeps Compose from reporting the field (its isAutofillable is false). Until 2026-11-14 the CVV field
            // was a plain password field to the service (a password field gets that content type by itself) and the others plain text.
            field(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDataType, ContentDataType.None))
            field(label).assert(!SemanticsMatcher.expectValue(SemanticsProperties.ContentType, type))
        }
    }

    @Test
    fun `on - each card field says which detail it is`() {
        showForm(fill = true)
        for ((label, type) in cardFields) {
            field(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentType, type))
            field(label).assert(!SemanticsMatcher.expectValue(SemanticsProperties.ContentDataType, ContentDataType.None))
        }
    }

    @Test
    fun `a four-digit year from an autofill service is kept as its last two`() {
        assertEquals("28", expiryYearDigits("2028"))
        assertEquals("28", expiryYearDigits("28"))
        assertEquals("2", expiryYearDigits("2"))
        assertEquals("20", expiryYearDigits("20"))
        assertEquals("29", expiryYearDigits("2029 "))
    }

    @Test
    fun `on - the form still saves as before`() {
        var saved: StoredCard? = null
        showForm(fill = true) { saved = it }
        compose.onNodeWithContentDescription("Slide to save changes").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { saved != null }
        assertEquals("4111111111114242", assertNotNull(saved).cardNumber)
        assertEquals("29", saved?.expiryYear)
    }
}
