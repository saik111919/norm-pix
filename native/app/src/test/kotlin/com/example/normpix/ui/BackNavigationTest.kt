package com.example.normpix.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.example.normpix.settings.AppPreferences
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.StoredCard
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
 * System back through the whole app: it climbs out of Plans & Transactions one level at a
 * time, and pushed pages (forms) hand you back to the view you opened them from - with the
 * system gesture and the page's own close button agreeing.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BackNavigationTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var financeDb: FinanceDatabase
    private lateinit var remindersDb: RemindersDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        financeDb = FinanceDatabase(context, name = null)
        remindersDb = RemindersDatabase(context, name = null)
        val prefs = context.getSharedPreferences("back_navigation_test", Context.MODE_PRIVATE)
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

    private fun navItem(label: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    /**
     * A tab on the page itself. While a view is selected the floating bar names it too, so plain
     * text would match both; the bar's items carry a content description, the page's tabs don't.
     */
    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(
        hasText(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
    )

    private fun click(label: String) {
        tab(label).performClick()
        compose.waitForIdle()
    }

    /** What the system back button (or a completed back gesture) does. */
    private fun systemBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun start() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()
        navItem("Plans & Activity").performClick()
        compose.waitForIdle()
    }

    @Test
    fun back_climbsOutOfPlans_sectionThenView_thenLeavesTheTab() {
        start()
        click("Plans")
        click("Reminders")
        tab("Reminders").assertIsSelected()

        // Reminders -> Budgets, still inside Plans.
        systemBack()
        tab("Budgets").assertIsSelected()
        tab("Plans").assertIsSelected()

        // Budgets -> Transactions, the root of Plans & Transactions.
        systemBack()
        tab("Activity").assertIsSelected()
        // The history is showing (this wallet is empty, so it offers the first entry).
        compose.onNodeWithText("Add your first expense").assertIsDisplayed()
        navItem("Plans & Activity").assertIsSelected()

        // Only now does back go to where you came from.
        systemBack()
        navItem("Home").assertIsSelected()
    }

    @Test
    fun back_fromRecurring_returnsToBudgets_notStraightOutOfPlans() {
        start()
        click("Plans")
        click("Recurring")

        systemBack()

        tab("Plans").assertIsSelected()
        tab("Budgets").assertIsSelected()
        navItem("Plans & Activity").assertIsSelected()
    }

    @Test
    fun tabs_exposeTheirSelection_toAccessibility() {
        start()
        tab("Activity").assertIsSelected()
        click("Plans")
        tab("Plans").assertIsSelected()
        tab("Budgets").assertIsSelected()
        click("Reminders")
        tab("Reminders").assertIsSelected()
        // The secondary row is its own level: picking a section never changes the primary view.
        tab("Plans").assertIsSelected()
        compose.onAllNodesWithText("Add reminder").onFirst().assertIsDisplayed()
    }
}
