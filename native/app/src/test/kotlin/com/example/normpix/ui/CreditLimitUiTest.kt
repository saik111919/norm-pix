package com.example.normpix.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.ui.AccountFormSheet
import com.example.normpix.screens.WalletScreen
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
 * A credit card's limit: asked for when a card account is made or edited (and for nothing else), kept, and said in the
 * Wallet as what is left of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CreditLimitUiTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.of(2026, 10, 1, 9, 0)

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("credit_limit_ui_test")
    }

    @After
    fun tearDown() = env.close()

    private fun account(name: String, kind: AccountKind, opening: Long = 0, limit: Long? = null): Account = runBlocking {
        env.container.finance.saveAccount(
            Account(name = name, kind = kind, openingBalance = opening, creditLimit = limit, createdAt = now, updatedAt = now),
        )
    }

    private fun showForm(existing: Account?, onDone: (Account?) -> Unit = {}) {
        compose.setContent { env.Content { AccountFormSheet(existing, onDone) } }
        compose.waitForIdle()
    }

    @Test
    fun aCardIsAskedForItsLimit_andItIsKept() {
        val card = account("HDFC Credit", AccountKind.Card)
        var saved: Account? = null
        showForm(card) { saved = it }

        compose.onNodeWithText("Credit limit (optional)").performTextInput("50000")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertEquals(50_000_00L, assertNotNull(saved).creditLimit)
    }

    @Test
    fun theLimitIsOptional() {
        val card = account("HDFC Credit", AccountKind.Card)
        var saved: Account? = null
        showForm(card) { saved = it }

        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(10_000) { saved != null }

        assertNull(assertNotNull(saved).creditLimit)
    }

    @Test
    fun aLimitOfNothingIsRefused() {
        val card = account("HDFC Credit", AccountKind.Card)
        var saved: Account? = null
        showForm(card) { saved = it }

        compose.onNodeWithText("Credit limit (optional)").performTextInput("0")
        compose.onNodeWithText("Save changes").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Enter a limit above zero").assertIsDisplayed()
        assertNull(saved)
    }

    @Test
    fun otherAccountsAreNotAskedForALimit() {
        showForm(account("HDFC Savings", AccountKind.Bank))
        compose.onAllNodesWithText("Credit limit (optional)").assertCountEquals(0)
    }

    @Test
    fun theWalletSaysWhatIsLeftOfTheLimit_inRows() {
        account("HDFC Credit", AccountKind.Card, opening = -12_000_00, limit = 50_000_00)
        env.preferences.setAccountsLayout(AccountsLayout.Rows)
        compose.setContent { env.Content { WalletScreen() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add account").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("₹38,000 left of ₹50,000", substring = true).assertIsDisplayed()
    }

    @Test
    fun aCardOverItsLimitSaysSo() {
        account("Store Card", AccountKind.Card, opening = -12_500_00, limit = 10_000_00)
        env.preferences.setAccountsLayout(AccountsLayout.Rows)
        compose.setContent { env.Content { WalletScreen() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add account").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("Over its ₹10,000 limit", substring = true).assertIsDisplayed()
    }

    @Test
    fun theCardAsAScreenReaderHearsIt_namesTheLimitAndWhatIsAvailable() {
        account("HDFC Credit", AccountKind.Card, opening = -12_000_00, limit = 50_000_00)
        env.preferences.setAccountsLayout(AccountsLayout.Cards)
        compose.setContent { env.Content { WalletScreen() } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add account").fetchSemanticsNodes().isNotEmpty() }

        val spoken = compose.onAllNodes(hasContentDescription("credit limit", substring = true)).fetchSemanticsNodes()
        assertTrue(spoken.isNotEmpty(), "the card with a limit says it")
    }
}
