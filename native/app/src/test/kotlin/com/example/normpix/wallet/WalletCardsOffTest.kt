package com.example.normpix.wallet

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.screens.WalletScreen
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

    private fun showWallet(storeCards: Boolean, vararg cards: StoredCard): TestEnvironment {
        Locale.setDefault(Locale.US)
        val environment = TestEnvironment("wallet_cards_off_test", InMemoryVault(cards.toList()))
        env = environment
        environment.preferences.setWalletCardsEnabled(storeCards)
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
}
