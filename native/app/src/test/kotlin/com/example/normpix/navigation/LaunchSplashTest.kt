package com.example.normpix.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AppPreferences
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A cold start writes the app's name out, then opens into the app; with animations removed it goes straight in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LaunchSplashTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("launch_splash_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        container = AppContainer(
            preferences = AppPreferences(prefs),
            finance = FinanceController(FinanceRepository(FinanceDatabase(context, name = null))),
            reminders = RemindersController(ReminderDao(RemindersDatabase(context, name = null)), scheduler = NoOpReminderScheduler),
            vault = InMemoryVault(),
        )
        runBlocking { container.reminders.load() }
    }

    private fun splashShowing() = compose.onAllNodes(hasContentDescription("NormPix")).fetchSemanticsNodes().isNotEmpty()

    private fun appShowing() = compose.onAllNodes(hasContentDescription("Home")).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aColdStartWritesTheNameThenOpensIntoTheApp() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides false) { NormPixRoot(container, showSplash = true) }
        }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(splashShowing(), "the splash is up at first")

        compose.mainClock.autoAdvance = true
        compose.waitUntil(timeoutMillis = 10_000) { !splashShowing() }
        compose.waitUntil(timeoutMillis = 10_000) { appShowing() }
    }

    @Test
    fun withAnimationsRemovedTheAppOpensAtOnce() {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) { NormPixRoot(container, showSplash = true) }
        }
        compose.waitForIdle()
        assertTrue(!splashShowing(), "no splash")
    }
}
