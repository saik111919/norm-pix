package com.example.normpix.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings > Data & backup > Backup reminder: when the last backup was, and how often Home asks for the next. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class BackupReminderPageTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: TestEnvironment
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("backup_reminder_page_test")
    }

    @After
    fun tearDown() = env.close()

    private fun open() {
        compose.setContent { env.Content { DataSettingsPage() } }
        compose.waitForIdle()
    }

    @Test
    fun saysWhenThereWasNoBackup_andHowLongAgoThereWas() {
        open()
        compose.onNodeWithText("Backup reminder").performScrollTo()
        compose.onNodeWithText("Never, on this phone").performScrollTo()

        compose.runOnIdle { env.preferences.recordBackup(System.currentTimeMillis() - 5 * day) }
        compose.waitForIdle()
        compose.onNodeWithText("5 days ago").performScrollTo()

        compose.runOnIdle { env.preferences.recordBackup(System.currentTimeMillis()) }
        compose.waitForIdle()
        compose.onNodeWithText("Today").performScrollTo()
    }

    @Test
    fun everyTwoWeeksIsChosenToStartWith_andAnotherChoiceTakesEffectAtOnce() {
        open()
        compose.onNodeWithText("Every 2 weeks").performScrollTo().assertIsSelected()

        compose.onNodeWithText("Every month").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(30, env.preferences.state.value.backupReminderDays)
        compose.onNodeWithText("Every month").assertIsSelected()

        compose.onNodeWithText("Every week").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(7, env.preferences.state.value.backupReminderDays)

        compose.onNodeWithText("Don't remind me").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, env.preferences.state.value.backupReminderDays)
        compose.onNodeWithText("Don't remind me").assertIsSelected()
    }
}
