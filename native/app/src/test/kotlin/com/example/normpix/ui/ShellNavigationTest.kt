package com.example.normpix.ui

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onFirst
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.settings.AppPreferences
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.StoredCard
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

/** The whole app shell on a typical phone: navigation, Settings access and Dummy data. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ShellNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var financeDb: FinanceDatabase
    private lateinit var remindersDb: RemindersDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        financeDb = FinanceDatabase(context, name = null)
        remindersDb = RemindersDatabase(context, name = null)
        val prefs = context.getSharedPreferences("shell_navigation_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val vault = object : CardVault {
            private var cards = emptyList<StoredCard>()
            override suspend fun loadCards(): List<StoredCard> = cards
            override suspend fun saveCards(cards: List<StoredCard>) {
                this.cards = cards
            }
        }
        container = AppContainer(
            preferences = AppPreferences(prefs),
            finance = FinanceController(FinanceRepository(financeDb)),
            reminders = RemindersController(ReminderDao(remindersDb), scheduler = NoOpReminderScheduler),
            vault = vault,
        )
        container.finance.load()
        container.reminders.load()
    }

    @After
    fun tearDown() {
        financeDb.close()
        remindersDb.close()
    }

    /** A primary destination in the floating navigation bar. */
    private fun navItem(label: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    private fun open(label: String) {
        navItem(label).performClick()
        compose.waitForIdle()
    }

    @Test
    fun plansAndTransactions_isOneDestination_activeInBothViews() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()

        // Four primary destinations: no separate Plan or Transactions items.
        for (label in listOf("Home", "Plans & Activity", "Insights", "Wallet")) navItem(label).assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasContentDescription("Plan") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(hasContentDescription("Activity") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).fetchSemanticsNodes().isEmpty())

        // Opens on the transaction history...
        open("Plans & Activity")
        navItem("Plans & Activity").assertIsSelected()
        // More actions opens contextual actions including Add expense
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add expense").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close actions").performClick()
        compose.waitForIdle()

        // ...and Plans is one switch away, with the same item still active.
        compose.onNodeWithText("Plans").performClick()
        compose.waitForIdle()
        navItem("Plans & Activity").assertIsSelected()
        compose.onNodeWithText("Budgets").assertIsDisplayed()
        compose.onNodeWithText("Reminders").performClick()
        compose.waitForIdle()
        // The contextual actions in More follow the section (the empty state offers one too).
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Add reminder").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close actions").performClick()
        compose.waitForIdle()

        // Leaving and coming back returns to the view you were on.
        open("Home")
        open("Plans & Activity")
        navItem("Plans & Activity").assertIsSelected()
        compose.onNodeWithText("No reminders yet").assertIsDisplayed()
    }

    @Test
    fun settings_isAccessibleThroughMoreActions() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()

        for (label in listOf("Home", "Plans & Activity", "Insights", "Wallet")) {
            open(label)
            // Exactly one way into More actions on every screen: the navigation bar's companion item.
            compose.onAllNodesWithContentDescription("More actions").assertCountEquals(1)
        }

        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Search settings").assertIsDisplayed()
    }

    @Test
    fun dummyData_showsSampleData_andTurningItOffBringsBackTheUsersOwn() {
        val before = runBlocking { container.finance.repository.snapshot() }
        // The accounts are read as compact rows here; the card layouts have their own tests (AccountCardsTest).
        container.preferences.setAccountsLayout(AccountsLayout.Rows)
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()

        // Your own (empty) data: just the starting Cash account.
        open("Wallet")
        compose.onNodeWithText("Cash").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("HDFC Savings").fetchSemanticsNodes().isEmpty())

        // Turn Dummy data on from Settings > Data & backup; you stay on that page while the sample set loads.
        setDummyData(on = true)
        assertTrue(container.preferences.state.value.dummyDataEnabled)

        compose.onNode(hasContentDescription("Back", substring = true)).performClick()
        compose.waitForIdle()
        compose.waitUntilAtLeastOneExists(hasText("HDFC Savings"), timeoutMillis = 10_000)
        compose.onNodeWithText("ICICI Credit Card").assertIsDisplayed()

        // Plans & Transactions shows the sample plans too.
        open("Plans & Activity")
        compose.onNodeWithText("Plans").performClick()
        compose.onNodeWithText("Recurring").performClick()
        // Each recurring row speaks as one unit: "Expense, ₹649.00, Netflix, Monthly · …".
        compose.waitUntilAtLeastOneExists(hasContentDescription("Netflix", substring = true), timeoutMillis = 10_000)

        // Turn it off again: your own data is back, unchanged.
        setDummyData(on = false)
        assertFalse(container.preferences.state.value.dummyDataEnabled)
        compose.onNode(hasContentDescription("Back", substring = true)).performClick()
        compose.waitForIdle()
        open("Wallet")
        compose.onNodeWithText("Cash").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("HDFC Savings").fetchSemanticsNodes().isEmpty())
        assertEquals(before, runBlocking { container.finance.repository.snapshot() })
    }

    private fun setDummyData(on: Boolean) {
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Data & Backup").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sample data").performScrollTo().performClick()
        // The swap rebuilds the screens under Settings; wait for the page to settle back.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Sample data").fetchSemanticsNodes().isNotEmpty()
        }
        val isNowOn = compose.onNodeWithText("Sample data").fetchSemanticsNode().config[SemanticsProperties.ToggleableState].toString() == "On"
        if (isNowOn != on) {
            compose.onNodeWithText("Sample data").performClick()
            compose.waitForIdle()
        }
        // Back from Data & backup to Settings, where the caller goes on.
        compose.onNode(hasContentDescription("Back", substring = true)).performClick()
        compose.waitForIdle()
    }
}
