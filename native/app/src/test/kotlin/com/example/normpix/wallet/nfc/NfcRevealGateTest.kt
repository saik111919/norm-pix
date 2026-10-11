package com.example.normpix.wallet.nfc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.screens.WalletScreen
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val CARD = StoredCard(
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

/**
 * Revealing a card behind "tap an NFC tag": by default any tag will do; limited to the user's own, only a tag they
 * registered does, and any other is told so while the sheet keeps waiting. Whether the number is on screen is read
 * from what the card announces.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class NfcRevealGateTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val mine = byteArrayOf(0x04, 0x5A, 0x1C, 0x32, 0x7B, 0x66, 0x80.toByte())
    private val stranger = byteArrayOf(0x04, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("nfc_reveal_gate", InMemoryVault(listOf(CARD)))
        env.preferences.setNfcTagRevealEnabled(true)
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent { env.Content { WalletScreen() } }
        compose.waitForIdle()
    }

    private fun tapReveal() {
        compose.onNodeWithText("Tap NFC to reveal").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun tap(id: ByteArray): Boolean {
        var accepted = false
        compose.runOnUiThread { accepted = env.nfc.tap(id) }
        compose.waitForIdle()
        return accepted
    }

    private fun numberOnScreen() =
        compose.onAllNodes(hasContentDescription("4111 1111 1111 4242", substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun byDefaultAnyTagRevealsTheCard() {
        show()
        tapReveal()
        compose.onNodeWithText("Hold an NFC tag near your phone").assertIsDisplayed()
        assertFalse(numberOnScreen(), "nothing is shown before a tap")

        assertTrue(tap(stranger))
        assertTrue(numberOnScreen(), "any tag reveals it")
        assertFalse(env.nfc.listening, "the session ends once a tag is accepted")
    }

    @Test
    fun limitedToRegisteredTagsAnotherTagDoesNotRevealAndIsToldSo() {
        env.registeredTags.register(mine)
        env.preferences.setNfcRevealRegisteredOnly(true)
        show()
        tapReveal()
        compose.onNodeWithText("Hold your registered tag near your phone").assertIsDisplayed()
        compose.onNodeWithText("Only a tag you registered in NormPix will reveal this card.").assertIsDisplayed()

        assertFalse(tap(stranger), "a tag that isn't registered is refused")
        compose.onNodeWithText("That isn't one of your registered tags. Try your own.").assertIsDisplayed()
        assertFalse(numberOnScreen(), "still hidden")
        assertTrue(env.nfc.listening, "and it keeps waiting for the right one")
    }

    @Test
    fun limitedToRegisteredTagsTheRegisteredOneReveals() {
        env.registeredTags.register(mine)
        env.preferences.setNfcRevealRegisteredOnly(true)
        show()
        tapReveal()

        assertFalse(tap(stranger))
        assertTrue(tap(mine.copyOf()), "the registered tag is accepted after a wrong one")
        assertTrue(numberOnScreen())
        assertFalse(env.nfc.listening)
    }

    @Test
    fun limitedToRegisteredTagsWithNoneRegisteredItDoesNotFallBackToAnyTag() {
        env.preferences.setNfcRevealRegisteredOnly(true)
        show()
        tapReveal()

        assertEquals(false, env.nfc.listening, "no sheet waiting for a tap that could never pass")
        assertFalse(numberOnScreen())
    }

    @Test
    fun withTheGateOffNoTapIsAskedFor() {
        env.preferences.setNfcTagRevealEnabled(false)
        env.preferences.setNfcRevealRegisteredOnly(true)
        show()
        compose.onNodeWithText("Reveal").performScrollTo().performClick()
        compose.waitForIdle()

        assertFalse(env.nfc.listening)
        assertTrue(numberOnScreen(), "revealing needs no tag when the gate is off")
    }
}
