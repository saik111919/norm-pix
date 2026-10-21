package com.example.normpix.wallet.expiry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.screens.WalletScreen
import com.example.normpix.settings.CardsLayout
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.time.LocalDateTime
import java.time.YearMonth
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

private class SwitchableNotifier(var enabled: Boolean) : ExpiryNotifier {
    override fun areEnabled() = enabled

    override fun show(record: CardExpiryRecord, expired: Boolean) = enabled

    override fun cancel(cardId: String) = Unit
}

/** The Wallet tab says which card is expiring, and "Mark as done" is for that card alone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class WalletExpiryNoticeTest {
    @get:Rule
    val compose = createComposeRule()

    private val notifier = SwitchableNotifier(enabled = true)
    private lateinit var env: TestEnvironment

    private fun card(id: String, bank: String, last4: String, expiry: YearMonth) = StoredCard(
        id = id,
        bankName = bank,
        cardholderName = "Asha Rao",
        cardNumber = "411111111111$last4",
        expiryMonth = "%02d".format(expiry.monthValue),
        expiryYear = "%02d".format(expiry.year % 100),
        cvv = "737",
        kind = CardKind.Debit,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
    )

    private val soon = YearMonth.now().plusMonths(1) // its window opened on the first of this month
    private val later = YearMonth.now().plusYears(4)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = env.close()

    private fun show(vararg cards: StoredCard, layout: CardsLayout? = null) {
        env = TestEnvironment(
            "wallet_expiry_notice",
            InMemoryVault(cards.toList()),
            expiry = CardExpiryTracker(InMemoryCardExpiryStore(), NoExpiryScheduler, notifier),
        )
        if (layout != null) env.preferences.setCardsLayout(layout)
        compose.setContent { env.Content { WalletScreen() } }
        compose.waitForIdle()
    }

    @Test
    fun inTheCarouselACardNotInFrontStillHasItsNoticeListed() {
        // The first card is in front and isn't expiring; the second, beside it, is. Its notice isn't waiting for a swipe.
        show(card("a", "HDFC Bank", "4242", later), card("b", "ICICI Bank", "0001", soon), layout = CardsLayout.Carousel)
        compose.onNodeWithText("ICICI Bank •••• 0001 · Expires soon").performScrollTo().assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("Mark as done").fetchSemanticsNodes().size)
    }

    @Test
    fun onlyTheCardInItsWindowShowsANoticeAndItNamesTheCard() {
        show(card("a", "HDFC Bank", "4242", soon), card("b", "ICICI Bank", "0001", later))
        compose.onNodeWithText("HDFC Bank •••• 4242 · Expires soon").performScrollTo().assertIsDisplayed()
        assertEquals(1, compose.onAllNodesWithText("Mark as done").fetchSemanticsNodes().size, "the other card has none")
        assertEquals(0, compose.onAllNodesWithText("ICICI Bank •••• 0001", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun markAsDoneClearsThatCardsNoticeOnly() {
        show(card("a", "HDFC Bank", "4242", soon), card("b", "ICICI Bank", "0001", soon))
        assertEquals(2, compose.onAllNodesWithText("Mark as done").fetchSemanticsNodes().size)

        compose.onAllNodesWithText("Mark as done")[0].performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(1, compose.onAllNodesWithText("Mark as done").fetchSemanticsNodes().size)
        assertEquals(CardExpiryPhase.Completed, env.expiry.states.value["a"]!!.phase)
        assertEquals(CardExpiryPhase.ExpiringSoon, env.expiry.states.value["b"]!!.phase)
    }

    @Test
    fun anExpiredCardSaysSo() {
        show(card("a", "HDFC Bank", "4242", YearMonth.now().minusMonths(3)))
        compose.onNodeWithText("HDFC Bank •••• 4242 · This card has expired").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withNotificationsOffTheNoticeAdmitsItIsTheOnlyReminder() {
        notifier.enabled = false
        show(card("a", "HDFC Bank", "4242", soon))
        compose.onNodeWithText("Notifications are off, so this is the only reminder.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Turn on").assertIsDisplayed()
    }

    @Test
    fun withNotificationsOnItDoesNotNag() {
        notifier.enabled = true
        show(card("a", "HDFC Bank", "4242", soon))
        assertEquals(0, compose.onAllNodesWithText("Notifications are off", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("Turn on").fetchSemanticsNodes().size)
    }

    @Test
    fun theNoticeNeverPrintsTheNumberOrCvv() {
        show(card("a", "HDFC Bank", "4242", soon))
        assertTrue(compose.onAllNodesWithText("4111", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("737", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
