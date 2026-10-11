package com.example.normpix.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.AppNavigator
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.LocalShellState
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.navigation.PlanSection
import com.example.normpix.navigation.PlansAndTransactionsTab
import com.example.normpix.navigation.ProvideAppEnvironment
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.navigation.ShellState
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.screens.HomeScreen
import com.example.normpix.screens.PlansAndTransactionsScreen
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.ui.SettingsScreen
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.CardsController
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.wallet.StoredCard
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeAppNavigator : AppNavigator {
    var lastDestination: AppDestination? = null
    var lastSection: PlanSection? = null
    var lastTab: PlansAndTransactionsTab? = null

    override fun back() {}
    override fun openCategories() {}
    override fun openBudgetHistory() {}
    override fun openPeople() {}
    override fun openPerson(personId: Long) {}
    override fun openCategoryHistory(categoryId: Long, month: MonthKey) {}
    override fun openAccountHistory(accountId: Long) {}
    override fun openSettings(page: SettingsPage?) {}
    override fun openProfile() {}
    override fun openSearch() {}
    override fun openReminderAlert(reminderId: Long) {}
    override fun go(destination: AppDestination) {
        lastDestination = destination
    }
    override fun openTransactions() {
        lastDestination = AppDestination.PlansAndTransactions
        lastTab = PlansAndTransactionsTab.Transactions
    }
    override fun openPlans(section: PlanSection?) {
        lastDestination = AppDestination.PlansAndTransactions
        lastTab = PlansAndTransactionsTab.Plans
        lastSection = section
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ComposeUiFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var financeDb: FinanceDatabase
    private lateinit var remindersDb: RemindersDatabase
    private lateinit var container: AppContainer
    private val fakeNavigator = FakeAppNavigator()
    private val fakeShellState = ShellState()

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        financeDb = FinanceDatabase(context, name = null)
        remindersDb = RemindersDatabase(context, name = null)

        val prefs = context.getSharedPreferences("compose_ui_test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val fakeVault = object : CardVault {
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
            vault = fakeVault,
            cards = CardsController(fakeVault),
        )
        container.finance.load()
        container.reminders.load()
    }

    @After
    fun tearDown() {
        financeDb.close()
        remindersDb.close()
    }

    @Test
    fun homeDashboard_rendersSectionsAndQuickAdd() {
        val prefs = container.preferences.state.value
        compose.setContent {
            NormPixTheme(Appearance(prefs.mode, prefs.colorSource, prefs.accent)) {
                ProvideAppEnvironment(container) {
                    CompositionLocalProvider(
                        LocalNavigator provides fakeNavigator,
                        LocalShellState provides fakeShellState,
                    ) {
                        HomeScreen()
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 5000) {
            compose.onAllNodesWithText("Add income").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add income").assertIsDisplayed()
        compose.onNodeWithText("Transfer").assertIsDisplayed()
        compose.onNodeWithText("Total balance").assertIsDisplayed()
    }

    @Test
    fun plansAndTransactions_switchesViewsAndPlanSections() {
        val prefs = container.preferences.state.value
        compose.setContent {
            NormPixTheme(Appearance(prefs.mode, prefs.colorSource, prefs.accent)) {
                ProvideAppEnvironment(container) {
                    CompositionLocalProvider(
                        LocalNavigator provides fakeNavigator,
                        LocalShellState provides fakeShellState,
                    ) {
                        PlansAndTransactionsScreen()
                    }
                }
            }
        }

        compose.waitForIdle()
        // Opens on the transaction history, with the switch to Plans held under the app bar.
        compose.onNodeWithText("Transactions").assertIsDisplayed()
        compose.onNodeWithText("Plans").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Budgets").assertIsDisplayed()
        compose.onNodeWithText("Recurring").assertIsDisplayed()
        compose.onNodeWithText("Reminders").assertIsDisplayed()

        // Switch to Reminders tab
        compose.onNodeWithText("Reminders").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No reminders yet").assertIsDisplayed()

        // Switch to Recurring tab
        compose.onNodeWithText("Recurring").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No recurring payments").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_rendersAndSearches() {
        val prefs = container.preferences.state.value
        compose.setContent {
            NormPixTheme(Appearance(prefs.mode, prefs.colorSource, prefs.accent)) {
                ProvideAppEnvironment(container) {
                    CompositionLocalProvider(
                        LocalNavigator provides fakeNavigator,
                        LocalShellState provides fakeShellState,
                    ) {
                        SettingsScreen()
                    }
                }
            }
        }

        compose.waitForIdle()
        compose.onAllNodesWithText("Settings").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
        compose.onNodeWithText("Finance").assertIsDisplayed()

        // Search for "dark"
        compose.onNodeWithText("Search settings").performTextInput("dark")
        compose.waitForIdle()
        compose.onNodeWithText("Theme").assertIsDisplayed()
    }

    @Test
    fun normPixRoot_rendersFullApp() {
        compose.setContent {
            NormPixRoot(container = container)
        }

        compose.waitForIdle()
        // Shell navigation bar displays tabs; Plans and Transactions share one item.
        compose.onAllNodesWithText("Home").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Plans & Transactions").onFirst().assertIsDisplayed()
    }
}
