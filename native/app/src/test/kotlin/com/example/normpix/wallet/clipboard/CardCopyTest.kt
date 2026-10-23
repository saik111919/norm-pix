package com.example.normpix.wallet.clipboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.screens.WalletScreen
import com.example.normpix.settings.ClipboardTimeout
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.RecordingClipboard
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
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

/** The copy sheet on its own: what it shows, what a tap asks for, and what it says afterwards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class CardCopySheetTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("card_copy_sheet")
    }

    @After
    fun tearDown() = env.close()

    private fun show(timeout: ClipboardTimeout, copies: MutableList<CardField>, works: Boolean = true) = compose.setContent {
        env.Content { CardCopySheet(CARD, timeout, onCopy = { copies += it; works }, onDone = {}) }
    }

    @Test
    fun itOffersEachDetailAndNeverShowsTheNumberOrCvvInFull() {
        show(ClipboardTimeout.Sec10, mutableListOf())
        for (label in listOf("Card number", "Cardholder name", "Expiry date", "CVV")) compose.onNodeWithText(label).assertIsDisplayed()
        compose.onNodeWithText("•••• •••• •••• 4242").assertIsDisplayed()
        compose.onNodeWithText("•••").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("4111", substring = true).fetchSemanticsNodes().size, "the number is masked")
        assertEquals(0, compose.onAllNodesWithText("737", substring = true).fetchSemanticsNodes().size, "the CVV is masked")
        compose.onNodeWithText("Asha Rao").assertIsDisplayed()
        compose.onNodeWithText("07/29").assertIsDisplayed()
    }

    @Test
    fun eachRowCopiesOnlyItsOwnDetail() {
        val copies = mutableListOf<CardField>()
        show(ClipboardTimeout.Sec10, copies)
        compose.onNodeWithText("Card number").performClick()
        compose.onNodeWithText("CVV").performClick()
        compose.onNodeWithText("Expiry date").performClick()
        compose.onNodeWithText("Cardholder name").performClick()
        assertEquals(listOf(CardField.Number, CardField.Cvv, CardField.Expiry, CardField.Holder), copies)
    }

    @Test
    fun afterACopyItSaysWhatWasCopiedAndWhenItClears() {
        show(ClipboardTimeout.Sec30, mutableListOf())
        compose.onNodeWithText("Card number").performClick()
        compose.onNodeWithText("Card number copied. It clears from the clipboard in 30 seconds.").assertIsDisplayed()
        compose.onNode(hasContentDescription("Copied")).assertIsDisplayed()
    }

    @Test
    fun withNeverItSaysTheDetailStays() {
        show(ClipboardTimeout.Never, mutableListOf())
        compose.onNodeWithText("Copied details stay on the clipboard until you copy something else.").assertIsDisplayed()
        compose.onNodeWithText("CVV").performClick()
        compose.onNodeWithText("CVV copied.").assertIsDisplayed()
    }

    @Test
    fun aFailedCopyIsSaidSoAndNothingIsClaimedCopied() {
        show(ClipboardTimeout.Sec10, mutableListOf(), works = false)
        compose.onNodeWithText("Expiry date").performClick()
        compose.onNodeWithText("Couldn't copy to the clipboard.").assertIsDisplayed()
    }

    @Test
    fun everyOneOfTheFourFieldsHasTheRightValue() {
        assertEquals("4111111111114242", CardField.Number.valueOf(CARD))
        assertEquals("Asha Rao", CardField.Holder.valueOf(CARD))
        assertEquals("07/29", CardField.Expiry.valueOf(CARD))
        assertEquals("737", CardField.Cvv.valueOf(CARD))
        assertEquals("07/29", CardField.Expiry.valueOf(CARD.copy(expiryYear = "2029")), "a four-digit year is shortened")
    }
}

/** Copying from the Wallet tab itself: the card's Copy button, the sheet, the clipboard it actually reaches. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class WalletCopyFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val clipboard = RecordingClipboard()

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("wallet_copy_flow", InMemoryVault(listOf(CARD)), clipboard)
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent { env.Content { WalletScreen() } }
        compose.waitForIdle()
    }

    @Test
    fun theCardsCopyButtonOpensTheSheetAndACopyReachesTheClipboardWithTheTimeout() {
        env.preferences.setClipboardTimeout(ClipboardTimeout.Min1)
        show()
        compose.onNodeWithContentDescription("Copy card details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Copy card details").assertIsDisplayed()

        compose.onNodeWithText("Card number").performClick()
        compose.waitForIdle()

        assertEquals(listOf(RecordingClipboard.Copy("Card number", "4111111111114242", ClipboardTimeout.Min1)), clipboard.copies)
    }

    @Test
    fun copyingNeverRevealsTheCardOnScreen() {
        show()
        compose.onNodeWithContentDescription("Copy card details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("CVV").performClick()
        compose.waitForIdle()
        assertTrue(clipboard.copies.single().value == "737")
        assertEquals(0, compose.onAllNodesWithText("4111 1111 1111 4242", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun theTimeoutSettingAtTheMomentOfCopyingIsTheOneUsed() {
        show()
        env.preferences.setClipboardTimeout(ClipboardTimeout.Never)
        compose.onNodeWithContentDescription("Copy card details").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Expiry date").performClick()
        assertEquals(ClipboardTimeout.Never, clipboard.copies.single().timeout)
    }
}
