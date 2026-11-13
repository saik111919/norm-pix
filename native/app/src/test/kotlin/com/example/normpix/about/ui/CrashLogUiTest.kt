package com.example.normpix.about.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.diagnostics.CrashLog
import com.example.normpix.notifications.NotificationsPage
import com.example.normpix.notifications.ProvideNotifications
import com.example.normpix.testing.TestEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The crash log in the app: About says what is kept, the sheet shows it (and counts as seeing it), the notifications (Home's Needs attention
 * banner, and the page it opens) offer the last one on the next start until it has been seen or dismissed, and Clear empties it after asking. Nothing is sent: Share only opens Android's own sheet.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class CrashLogUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "crash_log_ui_test")
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val log get() = CrashLog.of(context)

    @After
    fun tearDown() {
        log.clear()
        env.close()
    }

    private fun keepACrash(at: Long = System.currentTimeMillis()) {
        log.record(CrashLog.Entry(at, CrashLog.Kind.Crash, "=== 2026-11-07 17:20:31 ===\nNormPix 1.5.0 (10500)\njava.lang.IllegalStateException: boom\n    at com.example.Thing.go(Thing.kt:12)"))
    }

    @Test
    fun `the summary says nothing went wrong, or how many reports there are and when the last was`() {
        assertTrue("Nothing has gone wrong" in crashLogSummary(emptyList()))
        val one = listOf(CrashLog.Entry(1_800_000_000_000L, CrashLog.Kind.Crash, "a"))
        assertTrue(crashLogSummary(one).startsWith("1 report, from "), crashLogSummary(one))
        val two = listOf(CrashLog.Entry(2_000, CrashLog.Kind.Crash, "a"), CrashLog.Entry(1_000, CrashLog.Kind.Anr, "b"))
        assertTrue(crashLogSummary(two).startsWith("2 reports, the last from "), crashLogSummary(two))
    }

    @Test
    fun `sharing sends the newest few reports, newest first`() {
        val entries = (1..8).map { CrashLog.Entry(it * 1_000L, CrashLog.Kind.Crash, "report $it") }.reversed()
        val text = crashLogShareText(entries)
        assertEquals((8 downTo 4).map { "report $it" }, text.split("\n\n"))
    }

    @Test
    fun `the sheet shows what was kept and counts as seeing it`() {
        keepACrash(at = 1_800_000_000_000L)
        compose.setContent { env.Content { CrashLogSheet(onClose = {}) } }
        compose.waitForIdle()

        compose.onNodeWithText("Crash log").assertExists()
        compose.onNodeWithText("java.lang.IllegalStateException: boom", substring = true).assertExists()
        assertEquals(1_800_000_000_000L, env.preferences.state.value.crashLogSeenAt)
    }

    @Test
    fun `an empty log says so and has nothing to share or clear`() {
        compose.setContent { env.Content { CrashLogSheet(onClose = {}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Nothing has gone wrong.").assertExists()
        compose.onAllNodesWithText("Share").assertCountEquals(0)
        compose.onAllNodesWithText("Clear").assertCountEquals(0)
    }

    @Test
    fun `Clear asks first, and then empties the log`() {
        keepACrash()
        compose.setContent { env.Content { CrashLogSheet(onClose = {}) } }
        compose.waitForIdle()

        compose.onNodeWithText("Clear").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Clear the crash log?").assertExists()
        assertEquals(1, log.count(), "nothing is deleted until it is confirmed")

        compose.onAllNodesWithText("Clear").onLast().performClick()
        compose.waitUntil(10_000) { compose.waitForIdle(); log.count() == 0 }
    }

    @Test
    fun `the notifications offer the last crash until it is seen, and Dismiss says it was`() {
        val at = System.currentTimeMillis()
        keepACrash(at)
        compose.setContent { env.Content { ProvideNotifications { NotificationsPage() } } }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("NormPix stopped working last time").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Dismiss").performClick()
        compose.waitForIdle()

        assertEquals(at, env.preferences.state.value.crashLogSeenAt)
        assertEquals(1, log.count(), "dismissed, not deleted")
        assertTrue(compose.onAllNodesWithText("NormPix stopped working last time").fetchSemanticsNodes().isEmpty(), "gone once seen")
    }

    @Test
    fun `a crash that was already seen is not offered again, a newer one is`() {
        val earlier = System.currentTimeMillis() - 60_000
        keepACrash(earlier)
        env.preferences.markCrashLogSeen(earlier)
        compose.setContent { env.Content { ProvideNotifications { NotificationsPage() } } }
        compose.waitUntil(10_000) { compose.waitForIdle(); compose.onAllNodesWithText("Notification settings").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("NormPix stopped working", substring = true).fetchSemanticsNodes().isEmpty())

        compose.runOnIdle { keepACrash(System.currentTimeMillis()) }
        // The notification reads the log when what it has seen changes (or the page is opened): a new crash is for the next start.
        compose.runOnIdle { env.preferences.markCrashLogSeen(earlier + 1) }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("NormPix stopped working", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
