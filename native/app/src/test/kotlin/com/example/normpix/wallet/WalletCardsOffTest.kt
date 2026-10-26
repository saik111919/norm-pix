package com.example.normpix.wallet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.normpix.availability.AvailabilityMode
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.screens.WalletScreen
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** With Store cards off the Wallet shows accounts only: no cards section, no add-card action, whatever the vault still holds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class WalletCardsOffTest {
    @get:Rule
    val compose = createComposeRule()

    private var env: TestEnvironment? = null
    private val now = LocalDateTime.of(2026, 10, 12, 9, 0)

    @After
    fun tearDown() {
        env?.close()
    }

    /**
     * [appSwitch] is the app's own switch for the card exchange (Settings > Availability & Schedules): on unless a test says otherwise
     * (null leaves what a fresh install has, which is off).
     */
    private fun showWallet(
        storeCards: Boolean,
        vararg cards: StoredCard,
        nfc: NfcAvailability = NfcAvailability.Available,
        appSwitch: AvailabilityMode? = AvailabilityMode.Always,
    ): TestEnvironment {
        Locale.setDefault(Locale.US)
        val environment = TestEnvironment("wallet_cards_off_test", InMemoryVault(cards.toList()))
        env = environment
        environment.nfc.state = nfc
        appSwitch?.let { environment.preferences.setAvailability(ScheduledFeature.NfcExchange, AvailabilityRule(it)) }
        environment.preferences.setWalletCardsEnabled(storeCards)
        // The default stack draws only the front account (Cash), as a card read out by its description; the compact list prints every name.
        environment.preferences.setAccountsLayout(AccountsLayout.Rows)
        runBlocking {
            environment.container.finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        }
        compose.setContent { environment.Content { WalletScreen() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("HDFC Savings").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        return environment
    }

    private val saved = StoredCard("hdfc", "HDFC Bank", "Asha Rao", "4111111111114242", "07", "29", "737", CardKind.Debit, now, bankKey = "hdfc")

    @Test
    fun `with cards off the Wallet shows the accounts and no cards section or add-card action`() {
        showWallet(storeCards = false, saved)

        compose.onNodeWithText("HDFC Savings").assertIsDisplayed()
        compose.onAllNodesWithText("Add card").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("card, ending in", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("HDFC Bank •••• 4242").assertCountEquals(0)
    }

    @Test
    fun `with cards on and none saved the Wallet offers to add one`() {
        showWallet(storeCards = true)

        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add card").assertIsDisplayed()
    }

    // ------------------------------------------------------------ NFC

    private val exchange = "NFC Card Exchange (Inlet / Outlet)"

    @Test
    fun `with NFC on the Wallet offers the card exchange at the top and among the ways to add a first card`() {
        showWallet(storeCards = true)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add your first card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(exchange).assertCountEquals(1)
        compose.onAllNodesWithText("Tap to add (NFC)").assertCountEquals(1)
    }

    @Test
    fun `with NFC switched off nothing offers the card exchange`() {
        showWallet(storeCards = true, nfc = NfcAvailability.Disabled)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add your first card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(exchange).assertCountEquals(0)
        compose.onAllNodesWithText("Tap to add (NFC)").assertCountEquals(0)
    }

    @Test
    fun `a phone without NFC is never offered the card exchange`() {
        showWallet(storeCards = true, nfc = NfcAvailability.Unsupported)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add your first card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(exchange).assertCountEquals(0)
    }

    @Test
    fun `with the app's own NFC switch off nothing offers the card exchange even though the phone's NFC is on`() {
        showWallet(storeCards = true, appSwitch = AvailabilityMode.Off)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add your first card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(exchange).assertCountEquals(0)
        compose.onAllNodesWithText("Tap to add (NFC)").assertCountEquals(0)
    }

    @Test
    fun `a fresh install has the card exchange off`() {
        showWallet(storeCards = true, appSwitch = null)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add your first card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(exchange).assertCountEquals(0)
        compose.onAllNodesWithText("Tap to add (NFC)").assertCountEquals(0)
    }

    @Test
    fun `a saved card has Share while the card exchange is on`() {
        showWallet(storeCards = true, saved)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Edit card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Share via NFC").assertCountEquals(1)
    }

    @Test
    fun `a saved card has no Share while the app's NFC switch is off`() {
        showWallet(storeCards = true, saved, appSwitch = AvailabilityMode.Off)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Edit card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription("Share via NFC").assertCountEquals(0)
    }

    @Test
    fun `switching the app's NFC switch on or off shows or hides the button without leaving the Wallet`() {
        val environment = showWallet(storeCards = true, appSwitch = AvailabilityMode.Off)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add your first card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithContentDescription(exchange).assertCountEquals(0)

        environment.preferences.setAvailability(ScheduledFeature.NfcExchange, AvailabilityRule(AvailabilityMode.Always))
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(exchange).fetchSemanticsNodes().isNotEmpty() }

        environment.preferences.setAvailability(ScheduledFeature.NfcExchange, AvailabilityRule(AvailabilityMode.Off))
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(exchange).fetchSemanticsNodes().isEmpty() }
    }
}
