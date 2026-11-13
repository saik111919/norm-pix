package com.example.normpix.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.notifications.NotificationsPage
import com.example.normpix.notifications.ProvideNotifications
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
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
 * The "Back up your data" notification (counted in Home's one Needs attention banner, and a row of its own on the Notifications page,
 * which is what these look at): there when the last backup is older than the reminder (or there never was one) and there is something
 * to keep; not for a recent backup, a reminder that is off, a phone with nothing in it or the sample set; and "Later" puts it off. Its
 * button opens Settings > Data & backup.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeBackupBannerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "home_backup_banner_test")
    private val finance = env.container.finance
    private val now = LocalDateTime.now().withNano(0)
    private val day = 24L * 60 * 60 * 1000

    @After
    fun tearDown() = env.close()

    /** A second account: with the starting Cash alone there is nothing worth a backup. */
    private fun seedSomethingToKeep() = runBlocking {
        finance.load()
        finance.saveAccount(Account(name = "Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
    }

    /** The Notifications page on the real gathering (`ProvideNotifications`), once it has drawn its foot: the rows come before it. */
    private fun showHome() {
        compose.setContent { env.Content { ProvideNotifications { NotificationsPage() } } }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Notification settings").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun bannerShown(text: String): Boolean =
        compose.onAllNodes(hasText(text, substring = true) or hasContentDescription(text, substring = true)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun anOldBackupIsSaidWithItsAge_andAFreshOneIsNot() {
        seedSomethingToKeep()
        env.preferences.recordBackup(System.currentTimeMillis() - 20 * day)
        showHome()
        assertTrue(bannerShown("Last backup 20 days ago"))

        compose.runOnIdle { env.preferences.recordBackup(System.currentTimeMillis() - 2 * day) }
        compose.waitForIdle()
        assertTrue(!bannerShown("Last backup "), "a backup two days ago is recent")
    }

    @Test
    fun laterPutsItOff_andANewBackupBringsTheCountBack() {
        seedSomethingToKeep()
        env.preferences.recordBackup(System.currentTimeMillis() - 20 * day)
        showHome()
        assertTrue(bannerShown("Last backup 20 days ago"))

        // The row's own Later button.
        compose.onNodeWithText("Later").performClick()
        compose.waitForIdle()
        assertTrue(env.preferences.state.value.backupNudgeSnoozedUntil > System.currentTimeMillis(), "put off into the future")
        assertTrue(!bannerShown("Last backup "), "and gone for now")
    }

    @Test
    fun aReminderThatIsOff_orAPhoneWithNothingInIt_sayNothing() {
        env.preferences.recordBackup(System.currentTimeMillis() - 90 * day)
        runBlocking { finance.load() } // the starting Cash account alone
        showHome()
        assertTrue(!bannerShown("Last backup "), "nothing worth keeping")

        runBlocking {
            finance.saveAccount(Account(name = "Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        }
        compose.waitForIdle()
        compose.waitUntil(10_000) { compose.waitForIdle(); bannerShown("Last backup 90 days ago") }

        compose.runOnIdle { env.preferences.setBackupReminderDays(0) }
        compose.waitForIdle()
        assertTrue(!bannerShown("Last backup "), "the reminder is off")
    }

    @Test
    fun theSampleSetIsNeverNaggedAbout() {
        env.preferences.recordBackup(System.currentTimeMillis() - 90 * day)
        env.preferences.setDummyDataEnabled(true)
        showHome()
        compose.waitForIdle()
        assertEquals(false, bannerShown("Last backup "))
    }
}
