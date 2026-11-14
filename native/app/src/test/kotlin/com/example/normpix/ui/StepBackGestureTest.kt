package com.example.normpix.ui

import android.content.Context
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The back gesture for a step inside a screen, in the whole app, with a real gesture (owner, 2026-11-14: "Activity & Plan screen is
 * not following the predictive back", Banks & Categories should "step back to Banks first"): while the finger is down nothing has
 * changed yet - what is left is closing over what it goes back to (pictures in `app/build/qa_back_gesture/`) - letting go makes the
 * step, and a gesture given up leaves things as they were.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class StepBackGestureTest {
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
        val prefs = context.getSharedPreferences("step_back_gesture_test", Context.MODE_PRIVATE)
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

    /** A tab of the page itself (the floating bar's items carry a content description, the page's tabs don't). */
    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(
        hasText(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
    )

    private fun click(label: String) {
        tab(label).performClick()
        compose.waitForIdle()
    }

    /** The finger half way through a back gesture from the left edge, still down. */
    private fun dragHalfWay() {
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            back.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(400f, 900f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
    }

    private fun letGo() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun giveUp() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
    }

    private fun dump(name: String) {
        val dir = java.io.File("build/qa_back_gesture").also { it.mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun startOnPlans() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()
        navItem("Plans & Activity").performClick()
        compose.waitForIdle()
        click("Plans")
    }

    @Test
    fun plansGoesBackToActivityUnderTheFinger() {
        startOnPlans()
        dragHalfWay()
        dump("plans_to_activity")
        tab("Plans").assertIsSelected()
        letGo()
        tab("Activity").assertIsSelected()
        compose.onNodeWithText("Add your first expense").assertIsDisplayed()
        navItem("Plans & Activity").assertIsSelected()
    }

    @Test
    fun aSectionGoesBackToBudgetsUnderTheFinger() {
        startOnPlans()
        click("Reminders")
        dragHalfWay()
        dump("reminders_to_budgets")
        tab("Reminders").assertIsSelected()
        letGo()
        tab("Budgets").assertIsSelected()
        tab("Plans").assertIsSelected()
    }

    @Test
    fun aStepGivenUpStaysWhereItWas() {
        startOnPlans()
        click("Recurring")
        dragHalfWay()
        giveUp()
        tab("Recurring").assertIsSelected()
        tab("Plans").assertIsSelected()
    }

    private fun openBanksAndCategories() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Account and settings").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Banks & Categories").performClick()
        compose.waitForIdle()
        tab("Banks").assertIsSelected()
    }

    @Test
    fun banksAndCategories_backFromCategoriesStepsToBanksFirst_thenLeaves() {
        openBanksAndCategories()
        click("Categories")
        dragHalfWay()
        dump("categories_to_banks")
        tab("Categories").assertIsSelected()
        letGo()
        // Still the page (only it has the switch), now on Banks: the next back leaves it.
        tab("Banks").assertIsSelected()
        letGo()
        tab("Banks").assertDoesNotExist()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }

    @Test
    fun banksAndCategories_onTheSideItOpenedOn_backLeavesThePage() {
        openBanksAndCategories()
        letGo()
        tab("Banks").assertDoesNotExist()
        compose.onNodeWithText("Appearance").assertIsDisplayed()
    }
}
