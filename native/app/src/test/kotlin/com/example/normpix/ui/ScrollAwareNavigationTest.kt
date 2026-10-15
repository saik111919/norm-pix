package com.example.normpix.ui

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The floating navigation bar steps aside while reading down a list and is back when you need it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h700dp")
class ScrollAwareNavigationTest {

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
        val prefs = context.getSharedPreferences("scroll_aware_nav_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val vault = object : CardVault {
            private var cards = emptyList<StoredCard>()
            override suspend fun loadCards(): List<StoredCard> = cards
            override suspend fun saveCards(cards: List<StoredCard>) {
                this.cards = cards
            }
        }
        // The databases run in place on the test's own thread, as in TestEnvironment (and the sample set follows them). On the
        // app's dedicated threads a query's result is handed back across threads, and under Robolectric Home's "load the
        // month" then now and then resumed on the database thread and stalled there, leaving Home on "Loading your month"
        // with nothing to scroll - after some other test had run in the same process first.
        container = AppContainer(
            preferences = AppPreferences(prefs),
            finance = FinanceController(FinanceRepository(financeDb, dispatcher = Dispatchers.Unconfined)),
            reminders = RemindersController(ReminderDao(remindersDb, dispatcher = Dispatchers.Unconfined), scheduler = NoOpReminderScheduler),
            vault = vault,
        )
        container.finance.load()
        container.reminders.load()
        // Sample data gives Home enough to scroll.
        container.preferences.setDummyDataEnabled(true)
    }

    @After
    fun tearDown() {
        financeDb.close()
        remindersDb.close()
    }

    private fun navItem(label: String): SemanticsNodeInteraction =
        compose.onNode(hasContentDescription(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

    @Test
    fun navigationBar_leavesOnScrollDown_returnsOnScrollUp_andIsThereAtTheEnd() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitUntil(timeoutMillis = 15_000) { compose.onAllNodesWithText("Spent in", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // "Spent in ..." is also there before the sample data has loaded, when Home is too short to scroll and the bar
        // rightly stays put. Wait for the figures to fill in (nothing reads zero any more) before reading down the page.
        compose.waitUntil(timeoutMillis = 15_000) { compose.onAllNodesWithText("₹0.00").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        navItem("Home").assertIsDisplayed()
        // Reading down the page: the bar gets out of the way.
        compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.8f - 300f, durationMillis = 120) }
        compose.waitForIdle()
        navItem("Home").assertIsNotDisplayed()
        // The dock and the More / Actions tile leave as one group, not as separate pieces.
        compose.onNodeWithContentDescription("More actions").assertIsNotDisplayed()

        // Any scroll back up and it returns.
        compose.onRoot().performTouchInput { swipeDown(startY = height * 0.4f, endY = height * 0.4f + 120f, durationMillis = 120) }
        compose.waitForIdle()
        navItem("Home").assertIsDisplayed()
        compose.onNodeWithContentDescription("More actions").assertIsDisplayed()

        // Scrolling all the way down: at the end of the list it is showing again.
        repeat(6) {
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.9f, endY = top + height * 0.1f, durationMillis = 80) }
            compose.waitForIdle()
        }
        navItem("Home").assertIsDisplayed()
    }
}
