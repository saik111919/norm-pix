package com.example.normpix.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AccountButtonTarget
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.ui.components.PROFILE_BUTTON_LABEL
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The account circle, the Profile page and Settings, in the real navigation graph: they lead to each other without piling up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ProfileNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("profile_navigation_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        container = AppContainer(
            preferences = AppPreferences(prefs),
            finance = FinanceController(FinanceRepository(FinanceDatabase(context, name = null))),
            reminders = RemindersController(ReminderDao(RemindersDatabase(context, name = null)), scheduler = NoOpReminderScheduler),
            vault = InMemoryVault(),
        )
        runBlocking {
            container.finance.load()
            container.reminders.load()
        }
        container.preferences.setBackButtonStyle(BackButtonStyle.TopFloatingCircle)
        container.preferences.setAccountButtonTarget(AccountButtonTarget.Profile)
    }

    private fun on(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun theAccountCircleOpensTheProfile_whichLeadsOnToSettings_andBackAgain_withoutPilingUp() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()

        compose.onNode(hasContentDescription(PROFILE_BUTTON_LABEL) and hasClickAction()).performClick()
        compose.waitForIdle()
        assertTrue(on("Add your name"), "the circle opened the Profile")

        compose.onNode(hasText("Settings") and hasClickAction() and !isSelectable()).performClick()
        compose.waitForIdle()
        assertTrue(on("Search settings") && !on("Add your name"), "and its Settings row opened Settings")

        // Settings' own Profile row goes back to the Profile that is already under it...
        compose.onNodeWithText("Set up your profile").performClick()
        compose.waitForIdle()
        assertTrue(on("Add your name") && !on("Search settings"), "back at the Profile")

        // ...so one back leaves for Home, instead of walking through Settings, Profile, Settings.
        back()
        assertTrue(!on("Add your name") && !on("Search settings"), "one back left both pages")
        assertTrue(compose.onAllNodes(hasContentDescription(PROFILE_BUTTON_LABEL)).fetchSemanticsNodes().isNotEmpty(), "on a tab again")
    }
}
