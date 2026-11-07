package com.example.normpix.wallet.expiry

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    /** The notice is the Home page's banner, so it is one node for a screen reader: the lines as its description, the buttons as custom actions. */
    private fun notice(text: String) = compose.onNode(hasContentDescription(text, substring = true))

    private fun anywhere(text: String) =
        compose.onAllNodes(hasText(text, substring = true) or hasContentDescription(text, substring = true)).fetchSemanticsNodes().size

    private fun actionLabels(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    /** How many notices offer "Mark as done" (one for each card that is expiring). */
    private fun markDoneCount() = compose.onAllNodes(
        SemanticsMatcher("offers Mark as done") { "Mark as done" in actionLabels(it) },
    ).fetchSemanticsNodes().size

    private fun press(text: String, label: String) {
        val action = notice(text).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    @Test
    fun inTheCarouselACardNotInFrontStillHasItsNoticeListed() {
        // The first card is in front and isn't expiring; the second, beside it, is. Its notice isn't waiting for a swipe.
        show(card("a", "HDFC Bank", "4242", later), card("b", "ICICI Bank", "0001", soon), layout = CardsLayout.Carousel)
        notice("Expires soon. ICICI Bank •••• 0001").performScrollTo().assertIsDisplayed()
        assertEquals(1, markDoneCount())
    }

    @Test
    fun onlyTheCardInItsWindowShowsANoticeAndItNamesTheCard() {
        show(card("a", "HDFC Bank", "4242", soon), card("b", "ICICI Bank", "0001", later))
        notice("Expires soon. HDFC Bank •••• 4242").performScrollTo().assertIsDisplayed()
        assertEquals(1, markDoneCount(), "the other card has none")
        assertEquals(0, anywhere("ICICI Bank •••• 0001"))
    }

    @Test
    fun theNoticeSaysWhenTheCardExpires() {
        show(card("a", "HDFC Bank", "4242", soon))
        val printed = "%02d/%02d".format(soon.monthValue, soon.year % 100)
        notice("Valid thru $printed, next month").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun markAsDoneClearsThatCardsNoticeOnly() {
        show(card("a", "HDFC Bank", "4242", soon), card("b", "ICICI Bank", "0001", soon))
        assertEquals(2, markDoneCount())

        press("HDFC Bank •••• 4242", "Mark as done")

        assertEquals(1, markDoneCount())
        assertEquals(0, anywhere("HDFC Bank •••• 4242"), "its notice is gone")
        assertEquals(CardExpiryPhase.Completed, env.expiry.states.value["a"]!!.phase)
        assertEquals(CardExpiryPhase.ExpiringSoon, env.expiry.states.value["b"]!!.phase)
    }

    @Test
    fun anExpiredCardSaysSo() {
        show(card("a", "HDFC Bank", "4242", YearMonth.now().minusMonths(3)))
        notice("This card has expired. HDFC Bank •••• 4242").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withNotificationsOffTheNoticeAdmitsItIsTheOnlyReminder() {
        notifier.enabled = false
        show(card("a", "HDFC Bank", "4242", soon))
        notice("Notifications are off").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("Turn on", "Mark as done"), actionLabels(notice("HDFC Bank •••• 4242").fetchSemanticsNode()))
    }

    @Test
    fun withNotificationsOnItDoesNotNag() {
        notifier.enabled = true
        show(card("a", "HDFC Bank", "4242", soon))
        assertEquals(0, anywhere("Notifications are off"))
        assertEquals(listOf("Mark as done"), actionLabels(notice("HDFC Bank •••• 4242").fetchSemanticsNode()))
    }

    @Test
    fun theNoticeNeverPrintsTheNumberOrCvv() {
        show(card("a", "HDFC Bank", "4242", soon))
        assertEquals(0, anywhere("4111"))
        assertEquals(0, anywhere("737"))
        assertTrue(compose.onAllNodesWithText("4111", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
