package com.example.normpix.wallet

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.wallet.nfc.DetectedTag
import com.example.normpix.wallet.nfc.NfcInletState
import com.example.normpix.wallet.nfc.NfcTapPanel
import com.example.normpix.wallet.nfc.PaymentCardInfo
import com.example.normpix.wallet.nfc.PhoneNfcArea
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tap experience says what is happening, what to do next, and never more than it knows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NfcTapPanelTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val visaDebit = PaymentCardInfo("Visa", CardNetwork.Visa, CardKind.Debit, "VISA DEBIT")

    private val used = mutableListOf<PaymentCardInfo>()
    private var settingsOpened = 0
    private var retried = 0

    private fun show(state: NfcInletState, area: PhoneNfcArea = PhoneNfcArea.Unknown) {
        compose.setContent {
            NormPixTheme {
                NfcTapPanel(
                    state = state,
                    area = area,
                    acceptsPhones = false,
                    onRetry = { retried++ },
                    onOpenSettings = { settingsOpened++ },
                    onUsePaymentCard = { used += it },
                    onEnterManually = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun hasText(fragment: String) = compose.onAllNodesWithText(fragment, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun waiting_withoutAReportedAntenna_guidesWithoutClaimingASpot() {
        show(NfcInletState.Active)
        compose.onNodeWithText("Tap your card").assertIsDisplayed()
        compose.onNodeWithText("Ready for a tap").assertIsDisplayed()
        assertTrue(hasText("Where NFC sits differs from phone to phone"))
        assertTrue(!hasText("marked spot"))
    }

    @Test
    fun waiting_withAReportedAntenna_pointsAtIt() {
        show(NfcInletState.Active, PhoneNfcArea.Reported(listOf(PhoneNfcArea.Spot(0.5f, 0.2f))))
        assertTrue(hasText("Centre it over the marked spot"))
    }

    @Test
    fun nothingDetectedForAWhile_offersTips() {
        compose.mainClock.autoAdvance = false
        show(NfcInletState.Active)
        assertTrue(!hasText("Not picking it up?"))
        compose.mainClock.advanceTimeBy(11_000)
        compose.waitForIdle()
        assertTrue(hasText("Not picking it up?"))
        assertTrue(hasText("Keep other cards and phones away"))
    }

    @Test
    fun aBankCard_isNamed_explained_andMovesOnToTheFormOnce() {
        compose.mainClock.autoAdvance = false
        show(NfcInletState.PaymentCard(visaDebit))
        assertTrue(hasText("Visa debit card detected"))
        assertTrue(hasText("Banks lock the card number inside the chip"))
        compose.onNodeWithText("Enter card details").assertIsDisplayed()
        assertTrue(used.isEmpty())

        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(listOf(visaDebit), used)

        // Nothing more happens on its own.
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        assertEquals(1, used.size)
    }

    @Test
    fun nfcOff_saysSo_andOffersTheSwitch() {
        show(NfcInletState.NfcOff)
        compose.onNodeWithText("Turn on NFC").assertIsDisplayed()
        compose.onNodeWithText("Open NFC settings").performClick()
        assertEquals(1, settingsOpened)
    }

    @Test
    fun anUnsupportedCard_isTold_andTheReaderStaysReady() {
        show(NfcInletState.Unsupported(DetectedTag.OtherSmartCard))
        compose.onNodeWithText("This card can't be added").assertIsDisplayed()
        compose.onNodeWithText("Ready for a tap").assertIsDisplayed()
    }

    @Test
    fun aCardThatMovedAway_getsRepositioningHelp() {
        show(NfcInletState.Lost)
        compose.onNodeWithText("Moved too soon").assertIsDisplayed()
        assertTrue(hasText("Not picking it up?"))
    }

    @Test
    fun detection_isAcknowledgedRightAway() {
        var state by mutableStateOf<NfcInletState>(NfcInletState.Active)
        compose.setContent {
            NormPixTheme {
                NfcTapPanel(state, PhoneNfcArea.Unknown, acceptsPhones = true, onRetry = {}, onOpenSettings = {}, onUsePaymentCard = {}, onEnterManually = {})
            }
        }
        state = NfcInletState.Detected
        compose.waitForIdle()
        compose.onNodeWithText("Detected").assertIsDisplayed()
        compose.onNodeWithText("Hold still for a moment.").assertIsDisplayed()
    }
}
