package com.example.normpix.wallet

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.screens.WalletScreen
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Wallet's overview and its sections: the total and the two figures it is made of, named, as one thing and one sentence
 * for a screen reader; sections that are headings saying what is in them; layout choices that are named; and, with large
 * text, a layout that gives way (the figures stack, the choice takes two rows) rather than cutting words short.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class WalletOverviewTest {
    @get:Rule
    val compose = createComposeRule()

    private var env: TestEnvironment? = null
    private val now = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private val cards = listOf(
        StoredCard("a", "HDFC Bank", "Asha Rao", "4111111111114242", "07", "29", "737", CardKind.Debit, LocalDateTime.of(2026, 1, 1, 0, 0)),
        StoredCard("b", "ICICI Bank", "Asha Rao", "5555555555554444", "11", "28", "123", CardKind.Credit, LocalDateTime.of(2026, 2, 1, 0, 0)),
    )

    @After
    fun tearDown() {
        env?.close()
    }

    private fun account(name: String, kind: AccountKind, opening: Long) {
        runBlocking {
            env!!.container.finance.saveAccount(Account(name = name, kind = kind, openingBalance = opening, createdAt = now, updatedAt = now))
        }
    }

    private fun money(minor: Long) = env!!.container.finance.state.value.money.format(minor)

    /** The Wallet with Cash and "Bank" (Rs 38,000), and a "Credit Card" owing Rs 12,000 when [owing]; text is [fontScale] times the usual when given. */
    private fun show(owing: Boolean = true, fontScale: Float? = null, hidden: Boolean = false) {
        Locale.setDefault(Locale.US)
        val environment = TestEnvironment("wallet_overview_test", InMemoryVault(cards))
        env = environment
        account("Bank", AccountKind.Bank, 38_000_00)
        if (owing) account("Credit Card", AccountKind.Card, -12_000_00)
        if (hidden) environment.preferences.setAmountsHidden(true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density) {
                environment.Content { WalletScreen() }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add account").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @Test
    fun theOverviewIsOneThingForAScreenReader_aSentenceWithTheFigures() {
        show()

        compose.onNode(
            hasContentDescription("Total balance ${money(26_000_00)}: ${money(38_000_00)} in accounts, minus ${money(12_000_00)} owed."),
        ).assertIsDisplayed()
    }

    @Test
    fun withNothingOwedThereIsNoOwedFigure_andTheSentenceSaysSo() {
        show(owing = false)

        compose.onAllNodesWithText("You owe").assertCountEquals(0)
        compose.onNode(hasContentDescription("Total balance ${money(38_000_00)}: ${money(38_000_00)} in accounts.")).assertIsDisplayed()
    }

    @Test
    fun hiddenAmountsAreSaidToBeHidden_notReadOutAsDots() {
        show(hidden = true)

        compose.onNode(
            hasContentDescription("Total balance hidden. Amounts are hidden: show them with the eye button at the top."),
        ).assertIsDisplayed()
    }

    @Test
    fun theSectionsAreHeadings_eachSayingWhatIsInIt() {
        show()

        compose.onNode(isHeading() and hasText("Accounts")).assertIsDisplayed()
        compose.onNodeWithText("3 accounts").assertIsDisplayed()
        compose.onNode(isHeading() and hasText("Cards")).assertIsDisplayed()
        compose.onNodeWithText("2 cards, encrypted and kept on this device").assertIsDisplayed()
    }

    @Test
    fun theLayoutChoicesAreNamedForAScreenReader() {
        show()

        compose.onNodeWithContentDescription("How the accounts are shown").assertIsDisplayed()
        compose.onNodeWithContentDescription("How the cards are shown").assertIsDisplayed()
    }

    @Test
    fun withTheUsualTextTheFiguresShareARow_andTheLayoutChoiceIsOneRow() {
        show()

        val held = compose.onNodeWithText("In accounts", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val owed = compose.onNodeWithText("You owe", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(held.top.value, owed.top.value, 2f, "side by side")

        val stack = compose.onAllNodes(hasText("Stack") and radio)[0].getUnclippedBoundsInRoot()
        val rows = compose.onAllNodes(hasText("Rows") and radio)[0].getUnclippedBoundsInRoot()
        assertEquals(stack.top.value, rows.top.value, 2f, "one row of four")
    }

    @Test
    fun withLargeTextTheFiguresStack_andTheLayoutChoiceTakesTwoRows() {
        show(fontScale = 1.7f)

        val held = compose.onNodeWithText("In accounts", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val owed = compose.onNodeWithText("You owe", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(owed.top > held.bottom, "one under the other: $held, $owed")
        assertEquals(held.left.value, owed.left.value, 2f, "in one column")

        val stack = compose.onAllNodes(hasText("Stack") and radio)[0].getUnclippedBoundsInRoot()
        val rows = compose.onAllNodes(hasText("Rows") and radio)[0].getUnclippedBoundsInRoot()
        assertTrue(rows.top > stack.bottom - 4.dp, "Rows is on the second row: $stack, $rows")
        // Every label is still there to be chosen, in whichever row.
        for (label in listOf("Stack", "Cards", "Rows", "Carousel")) {
            compose.onAllNodes(hasText(label) and radio)[0].assertIsDisplayed()
        }
    }
}
