package com.example.normpix.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.availability.AvailabilityMode
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.nfc.ExchangeTab
import com.example.normpix.wallet.nfc.NfcCardOutletService
import com.example.normpix.wallet.nfc.NfcExchangeSheet
import com.example.normpix.wallet.nfc.NfcInletState
import com.example.normpix.wallet.nfc.NfcPairing
import com.example.normpix.wallet.nfc.NfcTapPanel
import com.example.normpix.wallet.nfc.PhoneNfcArea
import com.example.normpix.wallet.nfc.ShareProblem
import com.example.normpix.wallet.nfc.toNfcExchangePayload
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The card exchange's sheet with a reader on the other end: Share shows the code both phones derived and lets the card go only on
 * Match; Doesn't match shares nothing; Receive shows the same code while it waits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NfcShareSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val card = StoredCard(
        id = "c1",
        bankName = "HDFC Bank",
        cardholderName = "Asha Rao",
        cardNumber = "4111111111111111",
        expiryMonth = "09",
        expiryYear = "29",
        cvv = "123",
        kind = CardKind.Debit,
        createdAt = LocalDateTime.of(2026, 1, 1, 9, 0),
    )
    private lateinit var env: TestEnvironment
    private val service = NfcCardOutletService()
    private val allowedByTheApp = NfcCardOutletService.isAllowed

    @BeforeTest
    fun setUp() {
        env = TestEnvironment(vault = InMemoryVault(listOf(card)))
        env.preferences.setAvailability(ScheduledFeature.NfcExchange, AvailabilityRule(AvailabilityMode.Always))
        // As if NormPix were in front: the app's own rule also needs its activity visible.
        NfcCardOutletService.isAllowed = { true }
        NfcCardOutletService.clearOutlet()
    }

    @AfterTest
    fun tearDown() {
        NfcCardOutletService.clearOutlet()
        NfcCardOutletService.isAllowed = allowedByTheApp
        env.close()
    }

    private fun showShareTab() {
        compose.setContent { env.Content { NfcExchangeSheet(initialTab = ExchangeTab.Outlet, initialCard = card, onDismiss = {}) } }
        compose.waitForIdle()
    }

    private fun send(apdu: ByteArray): ByteArray = service.processCommandApdu(apdu, null)

    private fun cardAt(offset: Int) = byteArrayOf(0x80.toByte(), 0x14, (offset shr 8).toByte(), offset.toByte(), 0x00)

    /** The other phone, on Receive: SELECT, KEYS and NONCE through the service. Its own session, so its own code. */
    private fun aReaderPairs(): NfcPairing.Session {
        assertEquals(0x90.toByte(), send(NfcCardOutletService.SELECT_AID_COMMAND).let { it[it.size - 2] })
        val own = NfcPairing.newKey()
        val keys = send(byteArrayOf(0x80.toByte(), 0x10, 0x00, 0x00, 0x41) + own.point + byteArrayOf(0x00))
        val senderPoint = keys.copyOfRange(0, NfcPairing.POINT_BYTES)
        val readerNonce = NfcPairing.newNonce()
        val nonce = send(byteArrayOf(0x80.toByte(), 0x12, 0x00, 0x00, 0x10) + readerNonce + byteArrayOf(0x00))
        val senderNonce = nonce.copyOfRange(0, NfcPairing.NONCE_BYTES)
        val session = NfcPairing.session(own, senderPoint, senderPoint, own.point, senderNonce, readerNonce)
        compose.waitForIdle()
        return assertNotNull(session)
    }

    private fun spoken(code: String) = "Code " + code.toList().joinToString(" ")

    @Test
    fun shareShowsTheSameCode_andOnlyMatchLetsTheCardGo() {
        showShareTab()
        assertTrue(NfcCardOutletService.isArmed)
        val reader = aReaderPairs()

        compose.onNodeWithText("Does the other phone show this code?").assertIsDisplayed()
        compose.onNodeWithContentDescription(spoken(reader.code)).assertIsDisplayed()
        compose.onNodeWithText("Only tap Match for a phone you can see.").performScrollTo().assertIsDisplayed()
        // Until Match, the reader is told to wait.
        assertContentEquals(byteArrayOf(0x6F, 0x10), send(cardAt(0)))

        compose.onNodeWithText("Match - share the card").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sending the card. Keep the phones together.").assertIsDisplayed()

        // The reader fetches the sealed card and opens it with the key only it agreed.
        val first = send(cardAt(0))
        val total = ((first[0].toInt() and 0xFF) shl 8) or (first[1].toInt() and 0xFF)
        var sealed = first.copyOfRange(2, first.size - 2)
        while (sealed.size < total) {
            val next = send(cardAt(sealed.size))
            sealed += next.copyOfRange(0, next.size - 2)
        }
        assertEquals(card.toNfcExchangePayload(), String(assertNotNull(NfcPairing.open(reader, sealed)), Charsets.UTF_8))
        compose.waitForIdle()

        compose.onNodeWithText("Card shared").assertIsDisplayed()
        assertFalse(NfcCardOutletService.isArmed, "one reader, once")
    }

    @Test
    fun doesntMatch_sharesNothing_andShareAgainArmsTheCardAgain() {
        showShareTab()
        aReaderPairs()

        compose.onNodeWithText("Doesn't match").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Not shared").assertIsDisplayed()
        assertFalse(NfcCardOutletService.isArmed)
        // The reader learns it was refused.
        assertContentEquals(byteArrayOf(0x6F, 0x11), send(cardAt(0)))

        compose.onNodeWithText("Share again").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue(NfcCardOutletService.isArmed)
        compose.onNodeWithText("This device is ready to share NFC data.").assertIsDisplayed()
    }

    @Test
    fun theCodeGoesBackToWaitingWhenThePhonesPart() {
        showShareTab()
        val reader = aReaderPairs()
        compose.onNodeWithContentDescription(spoken(reader.code)).assertIsDisplayed()

        service.onDeactivated(android.nfc.cardemulation.HostApduService.DEACTIVATION_LINK_LOSS)
        compose.waitForIdle()

        compose.onNodeWithText("This device is ready to share NFC data.").assertIsDisplayed()
        compose.onNodeWithText("Match - share the card").assertDoesNotExist()
        assertTrue(NfcCardOutletService.isArmed)
    }

    @Test
    fun receiveShowsTheCodeWhileItWaitsForMatch() {
        // The Receive tab's panel (the reader itself needs NFC hardware), in the state a paired reader puts it in.
        compose.setContent {
            env.Content {
                NfcTapPanel(
                    state = NfcInletState.Comparing("042917"),
                    area = PhoneNfcArea.Unknown,
                    acceptsPhones = true,
                    onRetry = {},
                    onOpenSettings = {},
                    onUsePaymentCard = {},
                    onEnterManually = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Compare the codes").assertIsDisplayed()
        compose.onNodeWithText("Check the other phone shows this code, then tap Match on that phone. Keep the phones together.")
            .assertIsDisplayed()
        // Grouped on screen; spoken digit by digit.
        compose.onNodeWithContentDescription(spoken("042917")).assertIsDisplayed()
        compose.onNodeWithText("042 917", useUnmergedTree = true).assertExists()
    }

    @Test
    fun aShareThatEndedWithoutACard_saysWhy_andOffersToTryAgain() {
        var retried = 0
        compose.setContent {
            env.Content {
                NfcTapPanel(
                    state = NfcInletState.ShareFailed(ShareProblem.OlderPhone),
                    area = PhoneNfcArea.Unknown,
                    acceptsPhones = true,
                    onRetry = { retried++ },
                    onOpenSettings = {},
                    onUsePaymentCard = {},
                    onEnterManually = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("That phone has an older NormPix. Update it there to share cards securely.").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }
}
