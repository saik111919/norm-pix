package com.example.normpix.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.WalletSettingsPage
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.StoredCard
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Wallet & Cards > Save CVV and Store cards: both default to on; turning one off asks first, deletes what it covers
 * and only then moves (say no, or let the deletion fail, and it stays on); with Store cards off the card-only settings leave
 * the page; and the catalog finds both.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CardSwitchesSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 12, 9, 0)
    private val vault = InMemoryVault(
        listOf(
            StoredCard("rupay", "HDFC Bank", "THE USER", "6528505555554455", "08", "29", "123", CardKind.Credit, now),
            StoredCard("millennia", "HDFC Bank", "THE USER", "5282000011112222", "03", "30", "", CardKind.Debit, now),
        ),
    )
    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("card_switches_settings", vault = vault)
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent { env.Content { WalletSettingsPage() } }
        compose.waitForIdle()
    }

    private val prefs get() = env.preferences.state.value

    private fun waitForText(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun waitFor(condition: () -> Boolean) = compose.waitUntil(10_000) { condition() }

    private fun flip(headline: String) {
        compose.onNodeWithText(headline).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun linkedAccount() = runBlocking {
        env.container.finance.saveAccount(
            Account(name = "HDFC RuPay", kind = AccountKind.Card, cardId = "rupay", numberEnding = "4455", createdAt = now, updatedAt = now),
        )
    }

    private val cardOnlyRows = listOf("Export cards", "Import cards", "Card actions", "Cards layout", "Card tilt", "Clear copied card details after", "Save CVV")

    // ----- defaults -----

    @Test
    fun `both switches are on by default, and stay on across a restart`() {
        show()
        compose.onNodeWithText("Store cards").performScrollTo().assertIsOn()
        compose.onNodeWithText("Save CVV").performScrollTo().assertIsOn()
        assertTrue(prefs.walletCardsEnabled && prefs.cardCvvEnabled)
        // A fresh AppPreferences over the same file reads the defaults the same way.
        assertTrue(AppPreferences(env.prefsStore).state.value.let { it.walletCardsEnabled && it.cardCvvEnabled })
    }

    @Test
    fun `the preference keys are the ones the brief names`() {
        env.preferences.setCardCvvEnabled(false)
        env.preferences.setWalletCardsEnabled(false)
        assertEquals(false, env.prefsStore.getBoolean("card_cvv_enabled", true))
        assertEquals(false, env.prefsStore.getBoolean("wallet_cards_enabled", true))
        assertEquals(false, AppPreferences(env.prefsStore).state.value.cardCvvEnabled)
        assertEquals(false, AppPreferences(env.prefsStore).state.value.walletCardsEnabled)
    }

    // ----- Save CVV -----

    @Test
    fun `turning Save CVV off asks first, and no keeps the switch on and every CVV`() {
        show()
        flip("Save CVV")
        waitForText("Delete saved CVVs?")

        compose.onNodeWithText("Keep saving").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Save CVV").performScrollTo().assertIsOn()
        assertTrue(prefs.cardCvvEnabled)
        assertEquals(listOf("123", ""), vault.cards.map { it.cvv })
    }

    @Test
    fun `yes deletes the CVV from every stored card, then turns Save CVV off`() {
        show()
        flip("Save CVV")
        waitForText("Delete saved CVVs?")

        compose.onNodeWithText("Delete CVVs").performClick()
        waitFor { !prefs.cardCvvEnabled }
        compose.waitForIdle()

        assertTrue(vault.cards.none { it.hasCvv })
        assertEquals(listOf("6528505555554455", "5282000011112222"), vault.cards.map { it.cardNumber }, "only the CVVs go")
        assertTrue((env.container.cards.state.value as CardsState.Ready).cards.none { it.hasCvv })
        compose.onNodeWithText("Save CVV").performScrollTo().assertIsOff()
    }

    @Test
    fun `with no CVV saved there is nothing to ask, and Save CVV just turns off and on`() {
        vault.cards = vault.cards.map { it.copy(cvv = "") }
        env.container.let { runBlocking { it.cards.load() } }
        show()

        flip("Save CVV")
        waitFor { !prefs.cardCvvEnabled }
        compose.onAllNodesWithText("Delete saved CVVs?").assertCountEquals(0)

        flip("Save CVV")
        waitFor { prefs.cardCvvEnabled }
        compose.onNodeWithText("Save CVV").performScrollTo().assertIsOn()
    }

    @Test
    fun `when the CVVs can't be deleted the switch stays on and says so`() {
        show()
        vault.failToSave = true
        flip("Save CVV")
        waitForText("Delete saved CVVs?")

        compose.onNodeWithText("Delete CVVs").performClick()
        waitForText("Couldn't delete the CVVs")

        assertTrue(prefs.cardCvvEnabled)
        assertEquals("123", vault.cards.first().cvv)
    }

    // ----- Store cards -----

    @Test
    fun `turning Store cards off explains, and no keeps the switch on and every card`() {
        linkedAccount()
        show()
        flip("Store cards")
        waitForText("Stop storing cards?")
        compose.onNodeWithText("Keep cards").assertIsDisplayed()
        compose.onNodeWithText("Delete cards").assertIsDisplayed()

        compose.onNodeWithText("Keep cards").performClick()
        compose.waitForIdle()

        assertTrue(prefs.walletCardsEnabled)
        assertEquals(2, vault.cards.size)
        compose.onNodeWithText("Store cards").performScrollTo().assertIsOn()
        compose.onNodeWithText("Export cards").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `yes deletes the cards and their links, keeps the accounts, and the card-only settings leave the page`() {
        linkedAccount()
        env.container.expiry.sync(vault.cards)
        show()
        for (row in cardOnlyRows) compose.onNodeWithText(row).performScrollTo()

        flip("Store cards")
        waitForText("Stop storing cards?")
        compose.onNodeWithText("Delete cards").performClick()
        waitFor { !prefs.walletCardsEnabled }
        compose.waitForIdle()

        assertTrue(vault.cards.isEmpty())
        assertEquals(CardsState.Ready(emptyList()), env.container.cards.state.value)
        val accounts = runBlocking { env.container.finance.repository.accounts() }
        // Cash is there from the start; the card's account is the one that must not go.
        assertTrue("HDFC RuPay" in accounts.map { it.name }, "the account stays")
        assertTrue(accounts.all { it.cardId == null })
        assertEquals(0, env.container.expiry.states.value.size, "no expiry reminders are left")

        compose.onNodeWithText("Store cards").performScrollTo().assertIsOff()
        for (row in cardOnlyRows) compose.onAllNodesWithText(row).assertCountEquals(0)
        // What belongs to accounts stays.
        compose.onNodeWithText("Accounts layout").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bank logos").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Card numbers on accounts").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `turning Store cards back on brings the settings back, with no cards`() {
        env.preferences.setWalletCardsEnabled(false)
        vault.cards = emptyList()
        show()
        for (row in cardOnlyRows) compose.onAllNodesWithText(row).assertCountEquals(0)

        flip("Store cards")
        waitFor { prefs.walletCardsEnabled }
        compose.waitForIdle()

        compose.onNodeWithText("Store cards").performScrollTo().assertIsOn()
        for (row in cardOnlyRows) compose.onNodeWithText(row).performScrollTo().assertIsDisplayed()
        assertTrue(vault.cards.isEmpty())
    }

    @Test
    fun `when the vault can't be erased nothing is deleted, the switch stays on, and it says so`() {
        linkedAccount()
        vault.failToClear = true
        show()

        flip("Store cards")
        waitForText("Stop storing cards?")
        compose.onNodeWithText("Delete cards").performClick()
        waitForText("Couldn't delete your cards")

        assertTrue(prefs.walletCardsEnabled)
        assertEquals(2, vault.cards.size)
        assertEquals("rupay", runBlocking { env.container.finance.repository.accounts() }.single { it.name == "HDFC RuPay" }.cardId)
    }

    @Test
    fun `while sample data is showing neither switch can be changed`() {
        env.preferences.setDummyDataEnabled(true)
        show()
        compose.onNodeWithText("Store cards").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Save CVV").performScrollTo().assertIsNotEnabled()

        env.preferences.setDummyDataEnabled(false)
        compose.waitForIdle()
        compose.onNodeWithText("Store cards").performScrollTo().assertIsEnabled()
    }

    // ----- search -----

    @Test
    fun `the catalog finds both settings by the words people use`() {
        for (query in listOf("cvv", "save cvv", "security code")) {
            assertTrue("Save CVV" in SettingsCatalog.search(query).map { it.title }, query)
        }
        for (query in listOf("store cards", "don't store cards", "delete cards", "erase cards")) {
            assertTrue("Store cards" in SettingsCatalog.search(query).map { it.title }, query)
        }
        for (query in listOf("card number on account", "first 6 digits", "last 4 digits")) {
            assertTrue("Card number on account" in SettingsCatalog.search(query).map { it.title }, query)
        }
        assertTrue(SettingsCatalog.search("cvv").all { it.page != null })
    }

    @Test
    fun `with Store cards off, search offers no card-only setting but still the way back and the account number`() {
        val offered = listOf("cvv", "store cards", "card number on account", "tilt", "export cards", "clipboard", "nfc", "buttons")
            .flatMap { SettingsCatalog.search(it, cardsEnabled = false) }
            .map { it.title }
            .toSet()
        assertTrue("Store cards" in offered)
        assertTrue("Card number on account" in offered)
        for (hidden in listOf("Save CVV", "Card tilt", "Export and import cards", "Clear copied card details after", "NFC tag reveal gate", "Card actions style")) {
            assertFalse(hidden in offered, hidden)
        }
        // And with it on, they are all there.
        val all = listOf("cvv", "tilt", "export cards", "clipboard", "nfc", "buttons").flatMap { SettingsCatalog.search(it) }.map { it.title }.toSet()
        for (shown in listOf("Save CVV", "Card tilt", "Export and import cards", "Clear copied card details after", "NFC tag reveal gate", "Card actions style")) {
            assertTrue(shown in all, shown)
        }
    }
}
