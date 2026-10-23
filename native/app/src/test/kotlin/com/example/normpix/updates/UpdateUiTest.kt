package com.example.normpix.updates

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.BackgroundUpdates
import com.example.normpix.settings.UpdateCheckEvery
import com.example.normpix.settings.UpdateCheckWhen
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.SettingsScreen
import com.example.normpix.settings.ui.SettingsSpecial
import com.example.normpix.settings.ui.UpdatesSettingsPage
import com.example.normpix.settings.ui.automaticUpdatesSummary
import com.example.normpix.settings.ui.updateBadgeLabel
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.updates.ui.OfferAppUpdates
import com.example.normpix.updates.ui.UpdateBody
import com.example.normpix.updates.ui.UpdatePage
import com.example.normpix.updates.ui.UpdateSheetActions
import com.example.normpix.updates.ui.UpdateSheetContent
import java.io.File
import java.time.Instant
import java.time.LocalTime
import kotlin.io.path.createTempDirectory
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The update sheet in each state (the update page shows the same), the update page, the Settings rows, and the offer at launch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class UpdateUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val release = UpdateRelease(
        versionCode = 10003, versionName = "1.0.3", tag = "v1.0.3", publishedAt = Instant.parse("2026-10-15T09:00:00Z"),
        notes = "- Faster receipts", apkName = "NormPix-1.0.3.apk",
        apkUrl = "https://github.com/owner/normpix/releases/download/v1.0.3/NormPix-1.0.3.apk", size = 12_582_912,
        sha256 = "0".repeat(64), mandatory = false,
    )
    private val apk = File("NormPix-10003.apk")
    private val taps = mutableListOf<String>()
    private val actions = UpdateSheetActions(
        update = { taps += "update" },
        later = { taps += "later" },
        cancelDownload = { taps += "cancel" },
        install = { taps += "install" },
        allow = { taps += "allow" },
        retry = { taps += "retry" },
        close = { taps += "close" },
        checkAgain = { taps += "check" },
    )

    private val network = FakeNetwork()
    private val dir: File = createTempDirectory("updates").toFile()
    private val records = MemoryUpdateRecords()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var env: TestEnvironment? = null

    @AfterTest
    fun tearDown() {
        scope.cancel()
        env?.close()
        dir.deleteRecursively()
    }

    private var shown by mutableStateOf<UpdateState?>(null)

    /** Shows the sheet in [state]: the content is set once, then follows [shown]. */
    private fun show(state: UpdateState) {
        if (shown == null) {
            compose.setContent { NormPixTheme { shown?.let { UpdateSheetContent(it, Releases.installed, actions) } } }
        }
        shown = state
        compose.waitForIdle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    /** Taps a row further down a page, scrolling it into view first. */
    private fun scrollTap(text: String) {
        compose.onNodeWithText(text).performScrollTo().performClick()
        compose.waitForIdle()
    }

    /** The text's box on screen, from the root of the composition. */
    private fun boundsOf(text: String) = compose.onNodeWithText(text).getBoundsInRoot()

    private fun absent(text: String) = assertTrue(compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(), "\"$text\" is not shown")

    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    /** An updater on the fake network that does everything on the main thread, as the screen's own work does under test. */
    private fun updater(
        enabled: Boolean = true,
        installer: FakeInstaller = FakeInstaller(),
        changelogs: ChangelogStore = MemoryChangelogStore(),
    ) = AppUpdater(
        installed = Releases.installed,
        checker = UpdateChecker(GitHubAccess(network, "NormPix/test"), Releases.REPO, Releases.installed),
        downloads = UpdateDownloader(GitHubAccess(network, "NormPix/test"), dir),
        inspector = FakeInspector(),
        installer = installer,
        records = records,
        scope = scope,
        io = Dispatchers.Main.immediate,
        enabled = enabled,
        changelogs = changelogs,
    )

    private fun environment(updater: AppUpdater?) = TestEnvironment(prefsName = "update_ui_test", updates = updater).also { env = it }

    // ------------------------------------------------------------ the sheet

    @Test
    fun `an update shows both versions, when it came out, what's new, and Update now or Later`() {
        show(UpdateState.Available(release))
        compose.onNodeWithText("Update available").assertIsDisplayed()
        compose.onNodeWithText("1.0.2").assertIsDisplayed()
        compose.onNodeWithText("1.0.3").assertIsDisplayed()
        compose.onNodeWithText("Released", substring = true).assertIsDisplayed()
        compose.onNodeWithText("What's new").assertIsDisplayed()
        // Each change on a bullet of its own: the dash of the commit line is not shown as text.
        compose.onNodeWithText("Faster receipts").assertIsDisplayed()
        absent("- Faster receipts")
        tap("Update now")
        tap("Later")
        assertEquals(listOf("update", "later"), taps)
    }

    @Test
    fun `Later is a text button on the left, Update now a filled button on the right`() {
        show(UpdateState.Available(release))
        val later = boundsOf("Later")
        val update = boundsOf("Update now")
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue(later.right <= update.left, "Later $later is left of Update now $update")
        assertTrue(later.left < screen.left + screen.width / 2, "Later is in the left half")
        assertTrue(update.right > screen.left + screen.width / 2, "Update now is in the right half")
        assertTrue(screen.right - update.right <= 32.dp, "Update now sits at the right edge: $update in $screen")
        assertEquals(0f, abs((later.top + later.bottom).value - (update.top + update.bottom).value), 2f, "on one line")
        // The same pair for an update that is ready: Later, then Install.
        show(UpdateState.Ready(release, apk))
        assertTrue(boundsOf("Later").right <= boundsOf("Install").left)
        // And Not now, then Open settings, when Android's leave is needed.
        show(UpdateState.NeedsPermission(release, apk))
        assertTrue(boundsOf("Not now").right <= boundsOf("Open settings").left)
    }

    @Test
    fun `a single action sits on the right`() {
        val screen = { compose.onRoot().getBoundsInRoot() }
        show(UpdateState.Downloading(release, release.size / 2, release.size))
        assertTrue(screen().right - boundsOf("Cancel download").right <= 32.dp, "Cancel download is on the right")
        // Up to date has Done on the left and Check for update, the one thing to do next, on the right.
        show(UpdateState.UpToDate("1.0.2"))
        assertTrue(boundsOf("Done").right <= boundsOf("Check for update").left)
        assertTrue(screen().right - boundsOf("Check for update").right <= 32.dp, "Check for update is on the right")
        show(UpdateState.Installing(release, apk))
        assertTrue(screen().right - boundsOf("Close").right <= 32.dp, "Close is on the right")
        // A failure that can't be tried again has Close alone; one that can has Close on the left and Try again on the right.
        show(UpdateState.Failed(UpdateProblem.SignatureMismatch, release))
        assertTrue(screen().right - boundsOf("Close").right <= 32.dp)
        show(UpdateState.Failed(UpdateProblem.Offline))
        assertTrue(boundsOf("Close").right <= boundsOf("Try again").left)
        assertTrue(screen().right - boundsOf("Try again").right <= 32.dp)
    }

    @Test
    fun `a mandatory update can be closed, not put off`() {
        show(UpdateState.Available(release.copy(mandatory = true)))
        absent("Later")
        tap("Not now")
        assertEquals(listOf("close"), taps)
    }

    @Test
    fun `a download shows how far it got and can be stopped`() {
        show(UpdateState.Downloading(release, release.size / 2, release.size))
        compose.onNodeWithText("Downloading update").assertIsDisplayed()
        compose.onNodeWithText("50%", substring = true).assertIsDisplayed()
        absent("Update now")
        tap("Cancel download")
        assertEquals(listOf("cancel"), taps)
    }

    @Test
    fun `ready, waiting for leave, and installing`() {
        show(UpdateState.Ready(release, apk))
        tap("Install")
        show(UpdateState.NeedsPermission(release, apk))
        compose.onNodeWithText("Allow NormPix to install updates").assertIsDisplayed()
        tap("Open settings")
        show(UpdateState.Installing(release, apk))
        compose.onNodeWithText("Confirm on the screen Android shows", substring = true).assertIsDisplayed()
        tap("Close")
        assertEquals(listOf("install", "allow", "close"), taps)
    }

    @Test
    fun `stopped and turned down updates can be taken up again`() {
        show(UpdateState.Cancelled(release, null))
        tap("Download again")
        show(UpdateState.Cancelled(release, apk))
        tap("Install")
        assertEquals(listOf("retry", "install"), taps)
    }

    @Test
    fun `a failure says why, and offers Try again only where it can help`() {
        show(UpdateState.Failed(UpdateProblem.Offline))
        compose.onNodeWithText("Couldn't check for updates").assertIsDisplayed()
        compose.onNodeWithText("Couldn't reach GitHub", substring = true).assertIsDisplayed()
        tap("Try again")
        show(UpdateState.Failed(UpdateProblem.SignatureMismatch, release))
        compose.onNodeWithText("Couldn't update").assertIsDisplayed()
        compose.onNodeWithText("signed with a different key", substring = true).assertIsDisplayed()
        absent("Try again")
        assertEquals(listOf("retry"), taps)
    }

    @Test
    fun `up to date`() {
        show(UpdateState.UpToDate("1.0.2"))
        compose.onNodeWithText("NormPix is up to date").assertIsDisplayed()
        tap("Check for update")
        tap("Done")
        assertEquals(listOf("check", "close"), taps)
    }

    // ------------------------------------------------------------ Settings

    @Test
    fun `the page's body shows all the notes, the sheet's box caps them`() {
        val long = release.copy(notes = (1..40).joinToString("\n") { "- Change number $it" })
        var cap by mutableStateOf<Dp?>(null)
        compose.setContent {
            NormPixTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Box(Modifier.testTag("body")) { UpdateBody(UpdateState.Available(long), Releases.installed, notesMaxHeight = cap) }
                }
            }
        }
        compose.waitForIdle()
        val whole = compose.onNodeWithTag("body").getBoundsInRoot().height
        cap = 220.dp
        compose.waitForIdle()
        val capped = compose.onNodeWithTag("body").getBoundsInRoot().height
        assertTrue(whole > capped + 300.dp, "40 lines of notes take $whole on the page, and at most $capped in the sheet")
    }

    @Test
    fun `Settings has one row for everything about updating, which opens the update page`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { SettingsScreen() } }
        compose.onNodeWithText("Updates & changelog").performScrollTo()
        compose.onNodeWithText("Version 1.0.2").assertIsDisplayed()
        // Not three rows: the check, and Automatic updates, are behind this one.
        absent("Check for updates")
        absent("Automatic updates")
        tap("Updates & changelog")
        assertEquals(1, environment.navigator.updatesOpened)
        assertEquals(emptyList(), network.asked, "the page asks GitHub, not the row")
    }

    @Test
    fun `an update waiting is a pill at the end of the row, and what it says is Update or Install`() {
        Releases.publish(network)
        val updater = updater()
        val environment = environment(updater)
        scope.launch { updater.check() }
        compose.waitForIdle()
        compose.setContent { environment.Content { SettingsScreen() } }
        compose.onNodeWithText("Updates & changelog").performScrollTo()
        compose.onNodeWithText("Version 1.0.3 is available").assertIsDisplayed()
        compose.onNodeWithText("Update").assertIsDisplayed()
        assertEquals("Update", updateBadgeLabel(UpdateState.Available(release)))
        assertEquals("Install", updateBadgeLabel(UpdateState.Ready(release, apk)))
        for (quiet in listOf(UpdateState.Idle, UpdateState.UpToDate("1.0.2"), UpdateState.Downloading(release, 1, 2))) {
            assertNull(updateBadgeLabel(quiet), "$quiet")
        }
    }

    @Test
    fun `no pill while nothing is waiting`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { SettingsScreen() } }
        compose.onNodeWithText("Updates & changelog").performScrollTo()
        absent("Update")
        absent("Install")
    }

    @Test
    fun `Check now in Automatic updates opens the update page too`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        scrollTap("Check now")
        assertEquals(1, environment.navigator.updatesOpened)
    }

    @Test
    fun `the update page checks as it opens and shows the update it finds`() {
        Releases.publish(network)
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        compose.onNodeWithText("Installed version: 1.0.2").assertIsDisplayed()
        compose.onNodeWithText("New version: 1.0.3").assertIsDisplayed()
        compose.onNodeWithText("Update size", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Update now").assertIsDisplayed()
        compose.onNodeWithText("Later").assertIsDisplayed()
    }

    @Test
    fun `the update page opens with its headline, not with a title of the page's own above it`() {
        Releases.publish(network)
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        // One "Update available", the headline: the page's name ("App update") isn't set over it as a second title.
        assertEquals(1, compose.onAllNodesWithText("Update available").fetchSemanticsNodes().size)
        absent("App update")
        // The page is laid out like the Pixel's: the headline is large, and starts at the page's left margin.
        val headline = boundsOf("Update available")
        assertTrue(headline.height > 30.dp, "a large headline: ${headline.height}")
        assertTrue(headline.left <= 32.dp, "at the left margin: $headline")
    }

    @Test
    fun `the update page's buttons are two pills stacked across the foot of the screen, Update now over Later`() {
        Releases.publish(network)
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        val screen = compose.onRoot().getBoundsInRoot()
        val update = boundsOf("Update now")
        val later = boundsOf("Later")
        assertTrue(update.bottom <= later.top, "Update now $update is over Later $later")
        assertEquals(update.left.value, later.left.value, 1f, "the same width, one under the other")
        assertEquals(update.right.value, later.right.value, 1f)
        assertTrue(update.left - screen.left <= 32.dp && screen.right - update.right <= 32.dp, "across the page: $update in $screen")
        assertTrue(update.width > screen.width * 0.8f, "full width: ${update.width} of ${screen.width}")
        assertTrue(later.top > screen.top + screen.height / 2, "at the foot of the screen: $later")
    }

    @Test
    fun `up to date, the update page has Check for update full width over Done`() {
        Releases.publish(network, "1.0.2", 10002)
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("NormPix is up to date")
        val screen = compose.onRoot().getBoundsInRoot()
        val check = boundsOf("Check for update")
        val done = boundsOf("Done")
        assertTrue(check.width > screen.width * 0.8f, "full width: ${check.width} of ${screen.width}")
        assertTrue(check.top > screen.top + screen.height / 2, "at the foot of the screen")
        assertTrue(done.top >= check.bottom, "Done is under it: $done under $check")
        absent("Later")
    }

    @Test
    fun `Check for update asks GitHub again and finds a release that came out since`() {
        Releases.publish(network, "1.0.2", 10002)
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("NormPix is up to date")
        // A newer release is published while the page is open; the button is the way to see it, as on a Pixel's update screen.
        Releases.publish(network, "1.0.3", 10003)
        tap("Check for update")
        waitFor("Update available")
        compose.onNodeWithText("Update now").assertIsDisplayed()
        absent("Check for update")
    }

    @Test
    fun `the update page scrolls its content above buttons pinned at the foot of the screen`() {
        val notes = (1..40).joinToString("\n") { "- Change number $it" }
        Releases.publish(network, changes = mapOf("notes" to JsonPrimitive(notes)))
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        val screen = compose.onRoot().getBoundsInRoot()
        val pinned = boundsOf("Update now")
        val later = boundsOf("Later")
        assertTrue(pinned.top > screen.top + screen.height / 2, "the buttons are in the lower half, without scrolling: $pinned")
        assertTrue(pinned.bottom <= later.top, "Update now over Later")
        assertTrue(screen.right - pinned.right <= 32.dp, "the buttons run across the page")

        // The page scrolls all the way to its last line while the buttons stay where they are. (Its last line is a list item of
        // its own, composed only once the list has come near it: the list is scrolled to it, rather than the line to the screen.)
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Only GitHub is contacted", substring = true))
        compose.onNodeWithText("Only GitHub is contacted", substring = true).assertIsDisplayed()
        assertEquals(pinned, boundsOf("Update now"), "the bar doesn't move with the page")
        assertEquals(later, boundsOf("Later"))
        tap("Update now")
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Ready to install").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Install").assertIsDisplayed()
    }

    @Test
    fun `Later on the update page puts the update off and leaves`() {
        Releases.publish(network)
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        tap("Later")
        assertEquals(10003L, records.dismissedVersionCode)
        assertEquals(1, environment.navigator.backs)
    }

    @Test
    fun `the update page says when NormPix is up to date`() {
        Releases.publish(network, "1.0.2", 10002)
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("NormPix is up to date")
        tap("Done")
        assertEquals(1, environment.navigator.backs)
    }

    @Test
    fun `the update page doesn't ask again for an update already found, and no sheet comes up over it`() {
        Releases.publish(network)
        val updater = updater()
        val environment = environment(updater)
        environment.preferences.setUpdateChecksEnabled(false)
        compose.setContent {
            environment.Content {
                UpdatePage()
                OfferAppUpdates(updater, appLocked = false)
            }
        }
        waitFor("Update available")
        val asked = network.asked.size
        tap("Update now")
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            updater.state.value is UpdateState.Ready
        }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("Ready to install").fetchSemanticsNodes().size, "the page says so, with no sheet over it")
        assertTrue(network.asked.drop(asked).none { "releases/latest" in it }, "not asked again: ${network.asked.drop(asked)}")
    }

    @Test
    fun `the update page leads to Automatic updates, saying what they do`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        compose.onNodeWithText("Automatic updates").performScrollTo()
        compose.onNodeWithText("Daily · Download, then ask · Wi-Fi only").assertIsDisplayed()
        tap("Automatic updates")
        assertEquals(listOf<SettingsPage?>(SettingsPage.Updates), environment.navigator.settingsPages)
    }

    @Test
    fun `the summary says how often, at what time, what is done and on which network`() {
        assertEquals("Daily · Download, then ask · Wi-Fi only", automaticUpdatesSummary(AppPrefsState()))
        val weekly = AppPrefsState(updateCheckEvery = UpdateCheckEvery.OnceAWeek, updateCheckWhen = UpdateCheckWhen.AtSetTime)
        assertEquals("Weekly at 9:00 PM · Download, then ask · Wi-Fi only", automaticUpdatesSummary(weekly))
        assertEquals("Weekly at 21:00 · Download, then ask · Wi-Fi only", automaticUpdatesSummary(weekly, is24Hour = true))
        assertEquals(
            "Every 3 days at 7:30 AM · Install automatically · any network",
            automaticUpdatesSummary(
                weekly.copy(
                    updateCheckEvery = UpdateCheckEvery.EveryThreeDays,
                    updateCheckMinuteOfDay = 7 * 60 + 30,
                    backgroundUpdates = BackgroundUpdates.Install,
                    updateOnMobileData = true,
                ),
            ),
        )
        assertEquals(
            "Twice daily at 9:00 AM and 9:00 PM · Tell me",
            automaticUpdatesSummary(
                weekly.copy(updateCheckEvery = UpdateCheckEvery.TwiceADay, backgroundUpdates = BackgroundUpdates.Notify, updateCheckMinuteOfDay = 9 * 60),
            ),
        )
        assertEquals("Twice daily · Tell me", automaticUpdatesSummary(AppPrefsState(updateCheckEvery = UpdateCheckEvery.TwiceADay, backgroundUpdates = BackgroundUpdates.Notify)))
        assertEquals("Off · only when you check", automaticUpdatesSummary(AppPrefsState(updateChecksEnabled = false)))
    }

    @Test
    fun `the update page shows the summary of the choices made`() {
        val environment = environment(updater())
        environment.preferences.setUpdateCheckEvery(UpdateCheckEvery.OnceAWeek)
        environment.preferences.setUpdateCheckWhen(UpdateCheckWhen.AtSetTime)
        compose.setContent { environment.Content { UpdatePage() } }
        compose.onNodeWithText("Automatic updates").performScrollTo()
        compose.onNodeWithText("Weekly at 9:00 PM · Download, then ask · Wi-Fi only").assertIsDisplayed()
    }

    @Test
    fun `automatic checks can be turned off, and with them everything done in the background`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        assertTrue(environment.preferences.state.value.updateChecksEnabled)
        compose.onNodeWithText("Download it, then ask").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("How often").performScrollTo().assertIsDisplayed()
        scrollTap("Check for updates automatically")
        assertFalse(environment.preferences.state.value.updateChecksEnabled)
        absent("When an update is found")
        absent("Download it, then ask")
        absent("How often")
        absent("Check when you open NormPix")
    }

    @Test
    fun `how often to check is the user's to choose, a day being where it starts`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        assertEquals(UpdateCheckEvery.OnceADay, environment.preferences.state.value.updateCheckEvery)
        compose.onNodeWithText("Once a day, in the background and when you open NormPix").performScrollTo().assertIsDisplayed()
        for (choice in listOf(UpdateCheckEvery.OnceAWeek, UpdateCheckEvery.EveryThreeDays, UpdateCheckEvery.TwiceADay, UpdateCheckEvery.OnceADay)) {
            scrollTap(choice.label)
            assertEquals(choice, environment.preferences.state.value.updateCheckEvery)
        }
        scrollTap("Once a week")
        compose.onNodeWithText("Once a week, in the background and when you open NormPix").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a time to check is asked for only when checking at a set time`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        assertEquals(UpdateCheckWhen.AnyTime, environment.preferences.state.value.updateCheckWhen)
        compose.onNodeWithText("Any time").performScrollTo().assertIsDisplayed()
        absent("Check time")

        scrollTap("At a set time")
        assertEquals(UpdateCheckWhen.AtSetTime, environment.preferences.state.value.updateCheckWhen)
        compose.onNodeWithText("Check time").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("9:00 PM").performScrollTo().assertIsDisplayed()

        environment.preferences.setUpdateCheckTime(7, 30)
        compose.waitForIdle()
        compose.onNodeWithText("7:30 AM").performScrollTo().assertIsDisplayed()

        // Twice a day is the time and 12 hours on: both are shown.
        scrollTap("Twice a day")
        compose.onNodeWithText("7:30 AM and 7:30 PM").performScrollTo().assertIsDisplayed()

        scrollTap("Any time")
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Check time").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `checking when NormPix opens is a switch of its own, on until turned off`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        assertTrue(environment.preferences.state.value.updateCheckOnOpen)
        compose.onNodeWithText("Check when you open NormPix").performScrollTo().assertIsDisplayed()
        scrollTap("Check when you open NormPix")
        assertFalse(environment.preferences.state.value.updateCheckOnOpen)
        assertTrue(environment.preferences.state.value.updateChecksEnabled, "the check in the background is not affected")
        compose.onNodeWithText("Once a day, in the background", substring = true).performScrollTo().assertIsDisplayed()
        absent("Once a day, in the background and when you open NormPix")
    }

    @Test
    fun `the mobile data switch has a title of its own, and says what it covers`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        compose.onNodeWithText("Network").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Background downloads wait for Wi-Fi").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A metered Wi-Fi, such as a hotspot, counts as mobile data.", substring = true).performScrollTo().assertIsDisplayed()
        scrollTap("Download on mobile data")
        compose.onNodeWithText("Background downloads may use mobile data").performScrollTo().assertIsDisplayed()
        // The switch's group begins clear of the one above it (its title's own space), not tight against it.
        val above = boundsOf("Install it automatically")
        val title = boundsOf("Network")
        assertTrue(title.top - above.bottom >= 12.dp, "Network starts clear of the group above: $above, $title")
    }

    @Test
    fun `what happens to an update found in the background is the user's to choose`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        assertEquals(BackgroundUpdates.Download, environment.preferences.state.value.backgroundUpdates)
        compose.onNodeWithText("Download on mobile data").performScrollTo().assertIsDisplayed()
        scrollTap("Download on mobile data")
        assertTrue(environment.preferences.state.value.updateOnMobileData)

        scrollTap("Install it automatically")
        assertEquals(BackgroundUpdates.Install, environment.preferences.state.value.backgroundUpdates)
        compose.onNodeWithText("never while you're using it", substring = true).performScrollTo().assertIsDisplayed()

        scrollTap("Tell me")
        assertEquals(BackgroundUpdates.Notify, environment.preferences.state.value.backgroundUpdates)
        absent("Download on mobile data")
    }

    @Test
    fun `a debug build says updates come with release builds, and has nothing to switch`() {
        val environment = environment(updater(enabled = false))
        compose.setContent { environment.Content { SettingsScreen() } }
        compose.onNodeWithText("Updates & changelog").performScrollTo()
        compose.onNodeWithText("Version 1.0.2 · updates come with release builds").assertIsDisplayed()
        absent("Check for updates automatically")
        absent("Automatic updates")
        assertEquals(emptyList(), network.asked)
    }

    @Test
    fun `a debug build's update page has no Automatic updates, but still shows the changelog`() {
        network.answer(Releases.HISTORY, Releases.list(Releases.note("1.0.2", "- Words of 1.0.2")))
        val environment = environment(updater(enabled = false))
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Version 1.0.2")
        compose.onNodeWithText("Words of 1.0.2").performScrollTo().assertIsDisplayed()
        absent("Automatic updates")
        assertTrue(compose.onAllNodesWithText("Only GitHub is contacted", substring = true).fetchSemanticsNodes().isEmpty())
        assertEquals(listOf(Releases.HISTORY), network.asked, "it reads the list of releases and checks for no update")
    }

    // ------------------------------------------------------------ the changelog

    private fun publishChangelog() = network.answer(
        Releases.HISTORY,
        Releases.list(
            Releases.note("1.0.3", "- Notes for 1.0.3"),
            Releases.note("1.0.2", "## What's new\n- Notes for 1.0.2\n- A second change in 1.0.2"),
            Releases.note("1.0.1", "- Notes for 1.0.1"),
        ),
    )

    @Test
    fun `the update page lists what every version changed, the installed one open and marked`() {
        Releases.publish(network)
        publishChangelog()
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        waitFor("Version 1.0.2")
        compose.onNodeWithText("Changelog").performScrollTo().assertIsDisplayed()
        // What the phone has is read first: open, as a list of its changes under their heading.
        compose.onNodeWithText("Notes for 1.0.2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A second change in 1.0.2").performScrollTo().assertIsDisplayed()
        // Two "What's new": the update's own, in the page's body, and the heading the installed version's notes gave themselves.
        assertEquals(2, compose.onAllNodesWithText("What's new").fetchSemanticsNodes().size)
        compose.onNodeWithText("2 changes", substring = true).assertExists()
        compose.onNodeWithText("Installed").assertExists()
        // The newer one is marked and shut: its notes are the update's own, above; the older one is a tap away.
        compose.onNodeWithText("New").assertExists()
        absent("Notes for 1.0.3")
        absent("Notes for 1.0.1")
    }

    @Test
    fun `a version opens to its changes when tapped, and shuts again`() {
        Releases.publish(network)
        publishChangelog()
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Version 1.0.1")
        scrollTap("Version 1.0.1")
        waitFor("Notes for 1.0.1")
        scrollTap("Version 1.0.1")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Notes for 1.0.1").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `up to date, the page already says what the version installed changed`() {
        Releases.publish(network, "1.0.2", 10002)
        network.answer(Releases.HISTORY, Releases.list(Releases.note("1.0.2", "- What 1.0.2 brought")))
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("NormPix is up to date")
        waitFor("What 1.0.2 brought")
        compose.onNodeWithText("What 1.0.2 brought").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `with no connection the changelog says why, and a tap asks again`() {
        // GitHub can't be reached at all: no check was heard either, so nothing was saved for the changelog to fall back on.
        network.fail(Releases.LATEST, java.net.UnknownHostException("api.github.com"))
        network.fail(Releases.HISTORY, java.net.UnknownHostException("api.github.com"))
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Couldn't load the changelog")
        // (The page's own headline says it too, about the update check: this is the changelog row's words.)
        compose.onNodeWithText("Tap to try again", substring = true).performScrollTo().assertIsDisplayed()
        val asked = network.asked.count { it == Releases.HISTORY }

        network.answer(Releases.HISTORY, Releases.list(Releases.note("1.0.2", "- Back online")))
        scrollTap("Couldn't load the changelog")
        waitFor("Version 1.0.2")
        assertTrue(network.asked.count { it == Releases.HISTORY } > asked, "the tap asked GitHub again")
        absent("Couldn't load the changelog")
    }

    @Test
    fun `without GitHub the page keeps showing the changelog saved earlier, and says so`() {
        val saved = ChangelogEntry("1.0.2", 10002, Instant.parse("2026-10-15T09:30:00Z"), "- Saved words")
        Releases.publish(network)
        network.fail(Releases.HISTORY, java.net.UnknownHostException("api.github.com"))
        val environment = environment(updater(changelogs = MemoryChangelogStore(listOf(saved))))
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Version 1.0.2")
        compose.onNodeWithText("Saved words").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Couldn't refresh the changelog just now", substring = true).performScrollTo().assertIsDisplayed()
        absent("Couldn't load the changelog")
    }

    @Test
    fun `a version that came with no notes says so`() {
        Releases.publish(network, "1.0.2", 10002)
        network.answer(Releases.HISTORY, Releases.list(Releases.note("1.0.2", "")))
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Version 1.0.2")
        compose.onNodeWithText("No notes were written for this version.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `settings search finds the changelog`() {
        for (query in listOf("changelog", "what's new", "release notes", "version history")) {
            assertEquals(SettingsSpecial.Updates, SettingsCatalog.search(query).single { it.title == "What's new & changelog" }.special, query)
        }
    }

    @Test
    fun `a tapped update notification opens the update page once unlocked`() {
        val updater = updater()
        val environment = environment(updater)
        environment.preferences.setUpdateChecksEnabled(false)
        var locked by mutableStateOf(true)
        compose.setContent {
            environment.Content { OfferAppUpdates(updater, appLocked = locked, requests = environment.container.updateRequests) }
        }
        environment.container.updateRequests.value = true
        compose.waitForIdle()
        assertEquals(0, environment.navigator.updatesOpened)

        locked = false
        // Idled each time round: under Robolectric the screen only takes in the unlock as the main looper runs.
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            environment.navigator.updatesOpened == 1
        }
        assertFalse(environment.container.updateRequests.value)
    }

    @Test
    fun `Install on the ready notification opens the update page and installs what's ready`() {
        Releases.publish(network)
        val installer = FakeInstaller()
        val updater = updater(installer = installer)
        val environment = environment(updater)
        environment.preferences.setUpdateChecksEnabled(false)
        scope.launch { updater.check() }
        compose.waitForIdle()
        updater.download()
        compose.waitForIdle()
        assertIs<UpdateState.Ready>(updater.state.value)
        compose.setContent {
            environment.Content {
                OfferAppUpdates(
                    updater,
                    appLocked = false,
                    requests = environment.container.updateRequests,
                    installRequests = environment.container.updateInstallRequests,
                )
            }
        }
        environment.container.updateInstallRequests.value = true
        environment.container.updateRequests.value = true
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            installer.installs.isNotEmpty()
        }
        assertEquals(1, environment.navigator.updatesOpened)
        assertFalse(environment.container.updateInstallRequests.value)
    }

    @Test
    fun `settings search finds it`() {
        for (query in listOf("update", "upgrade", "new version", "apk")) {
            assertEquals(SettingsSpecial.Updates, SettingsCatalog.search(query).single { it.title == "Check for updates" }.special, query)
        }
        assertTrue(SettingsCatalog.search("update").none { it.page == SettingsPage.Security })
    }

    @Test
    fun `settings search finds how often, the time and checking when opened, on the Automatic updates page`() {
        val searches = mapOf(
            "How often to check for updates" to listOf("how often", "frequency", "twice a day", "once a week", "every 3 days"),
            "Update check time" to listOf("check time", "at a set time", "time of day", "any time"),
            "Check when NormPix opens" to listOf("check when opened", "check on open", "when i open", "launch"),
            "Automatic updates" to listOf("mobile data", "metered", "hotspot", "wi-fi"),
        )
        for ((title, queries) in searches) {
            for (query in queries) {
                val found = SettingsCatalog.search(query).filter { it.title == title }
                assertEquals(listOf<SettingsPage?>(SettingsPage.Updates), found.map { it.page }, "\"$query\" finds \"$title\"")
            }
        }
    }

    // ------------------------------------------------------------ at launch

    @Test
    fun `a newer release is offered at launch once unlocked, and Later puts it off`() {
        Releases.publish(network)
        val updater = updater()
        val environment = environment(updater)
        var locked by mutableStateOf(true)
        compose.setContent { environment.Content { OfferAppUpdates(updater, appLocked = locked) } }
        compose.waitForIdle()
        assertEquals(emptyList(), network.asked, "nothing is asked while the app is locked")

        locked = false
        waitFor("Update available")
        tap("Later")
        assertEquals(10003L, records.dismissedVersionCode)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Update available").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `a download finished with the sheet closed brings it back once unlocked, and only Install installs it`() {
        Releases.publish(network)
        val installer = FakeInstaller()
        val updater = updater(installer = installer)
        val environment = environment(updater)
        // No offer at launch: only the finished download brings the sheet up.
        environment.preferences.setUpdateChecksEnabled(false)
        var locked by mutableStateOf(true)
        compose.setContent { environment.Content { OfferAppUpdates(updater, appLocked = locked) } }
        compose.waitForIdle()
        scope.launch { updater.check() }
        compose.waitForIdle()
        assertIs<UpdateState.Available>(updater.state.value)

        updater.download()
        compose.waitForIdle()
        assertIs<UpdateState.Ready>(updater.state.value)
        absent("Ready to install")

        locked = false
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            compose.onAllNodesWithText("Ready to install").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(emptyList(), installer.installs, "nothing is installed until the user says so")
        tap("Install")
        assertEquals(1, installer.installs.size)
    }

    @Test
    fun `nothing is asked at launch with checking when NormPix opens off, though automatic checks are on`() {
        Releases.publish(network)
        val updater = updater()
        val environment = environment(updater)
        environment.preferences.setUpdateCheckOnOpen(false)
        assertTrue(environment.preferences.state.value.updateChecksEnabled)
        compose.setContent { environment.Content { OfferAppUpdates(updater, appLocked = false) } }
        compose.waitForIdle()
        assertEquals(emptyList(), network.asked)
        assertNull(updater.state.value.release)
    }

    @Test
    fun `the check at launch waits as long as the user chose`() {
        Releases.publish(network)
        val updater = updater()
        val environment = environment(updater)
        // GitHub answered 13 hours ago: a day hasn't passed, so a launch doesn't ask - until the user says twice a day.
        records.lastCheckAt = System.currentTimeMillis() - 13L * 60 * 60 * 1000
        environment.preferences.setUpdateCheckEvery(UpdateCheckEvery.OnceAWeek)
        compose.setContent { environment.Content { OfferAppUpdates(updater, appLocked = false) } }
        compose.waitForIdle()
        assertEquals(emptyList(), network.asked, "13 hours into a week")
        assertNull(updater.state.value.release)
    }

    @Test
    fun `a launch asks once the wait the user chose has passed`() {
        Releases.publish(network)
        val updater = updater()
        val environment = environment(updater)
        records.lastCheckAt = System.currentTimeMillis() - 13L * 60 * 60 * 1000
        environment.preferences.setUpdateCheckEvery(UpdateCheckEvery.TwiceADay)
        compose.setContent { environment.Content { OfferAppUpdates(updater, appLocked = false) } }
        waitFor("Update available")
        assertTrue(network.asked.any { "releases/latest" in it })
    }

    @Test
    fun `nothing is asked at launch with automatic checks off`() {
        Releases.publish(network)
        val updater = updater()
        val environment = environment(updater)
        environment.preferences.setUpdateChecksEnabled(false)
        compose.setContent { environment.Content { OfferAppUpdates(updater, appLocked = false) } }
        compose.waitForIdle()
        assertEquals(emptyList(), network.asked)
        assertNull(updater.state.value.release)
    }
}

/** The automatic-check switch: on until the user turns it off, remembered, and back on with the other defaults. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdatePreferenceTest {
    private fun store() = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("update_preference_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }

    @Test
    fun `automatic checks are on until turned off, and the choice is remembered`() {
        val store = store()
        assertTrue(AppPreferences(store).state.value.updateChecksEnabled)
        AppPreferences(store).setUpdateChecksEnabled(false)
        assertFalse(AppPreferences(store).state.value.updateChecksEnabled, "read back by the next launch")
    }

    @Test
    fun `resetting settings turns them back on`() {
        val preferences = AppPreferences(store())
        preferences.setUpdateChecksEnabled(false)
        preferences.setBackgroundUpdates(BackgroundUpdates.Install)
        preferences.setUpdateOnMobileData(true)
        preferences.setUpdateCheckEvery(UpdateCheckEvery.OnceAWeek)
        preferences.setUpdateCheckWhen(UpdateCheckWhen.AtSetTime)
        preferences.setUpdateCheckTime(6, 45)
        preferences.setUpdateCheckOnOpen(false)
        preferences.resetToDefaults()
        assertTrue(preferences.state.value.updateChecksEnabled)
        assertEquals(BackgroundUpdates.Download, preferences.state.value.backgroundUpdates)
        assertFalse(preferences.state.value.updateOnMobileData)
        assertEquals(UpdateCheckEvery.OnceADay, preferences.state.value.updateCheckEvery)
        assertEquals(UpdateCheckWhen.AnyTime, preferences.state.value.updateCheckWhen)
        assertEquals(21 * 60, preferences.state.value.updateCheckMinuteOfDay)
        assertTrue(preferences.state.value.updateCheckOnOpen)
    }

    @Test
    fun `existing users keep checking once a day, any time, and when NormPix opens`() {
        val defaults = AppPreferences(store()).state.value
        assertEquals(UpdateCheckEvery.OnceADay, defaults.updateCheckEvery)
        assertEquals(24L * 60 * 60 * 1000, defaults.updateCheckEvery.intervalMs)
        assertEquals(UpdateCheckWhen.AnyTime, defaults.updateCheckWhen)
        assertEquals(LocalTime.of(21, 0), defaults.updateCheckTime)
        assertTrue(defaults.updateCheckOnOpen)
    }

    @Test
    fun `how often, when, the time and checking at launch are remembered`() {
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setUpdateCheckEvery(UpdateCheckEvery.EveryThreeDays)
        preferences.setUpdateCheckWhen(UpdateCheckWhen.AtSetTime)
        preferences.setUpdateCheckTime(7, 5)
        preferences.setUpdateCheckOnOpen(false)
        val read = AppPreferences(store).state.value
        assertEquals(UpdateCheckEvery.EveryThreeDays, read.updateCheckEvery)
        assertEquals(UpdateCheckWhen.AtSetTime, read.updateCheckWhen)
        assertEquals(7 * 60 + 5, read.updateCheckMinuteOfDay)
        assertEquals(LocalTime.of(7, 5), read.updateCheckTime)
        assertFalse(read.updateCheckOnOpen)
    }

    @Test
    fun `an unknown or out of range value reads as the default`() {
        val store = store()
        store.edit()
            .putString("update_check_every", "EveryFullMoon")
            .putString("update_check_when", "Sometimes")
            .putInt("update_check_minute_of_day", 99_999)
            .commit()
        val read = AppPreferences(store).state.value
        assertEquals(UpdateCheckEvery.OnceADay, read.updateCheckEvery)
        assertEquals(UpdateCheckWhen.AnyTime, read.updateCheckWhen)
        assertEquals(24 * 60 - 1, read.updateCheckMinuteOfDay, "kept inside the day")
        store.edit().putInt("update_check_minute_of_day", -5).commit()
        assertEquals(0, AppPreferences(store).state.value.updateCheckMinuteOfDay)
    }

    @Test
    fun `a time that isn't on the clock is kept inside the day`() {
        val preferences = AppPreferences(store())
        preferences.setUpdateCheckTime(25, 70)
        assertTrue(preferences.state.value.updateCheckMinuteOfDay in 0 until 24 * 60)
    }

    @Test
    fun `in the background an update is downloaded on Wi-Fi and then asked about, until the user chooses otherwise`() {
        val store = store()
        val defaults = AppPreferences(store).state.value
        assertEquals(BackgroundUpdates.Download, defaults.backgroundUpdates)
        assertFalse(defaults.updateOnMobileData)
        AppPreferences(store).setBackgroundUpdates(BackgroundUpdates.Install)
        AppPreferences(store).setUpdateOnMobileData(true)
        val read = AppPreferences(store).state.value
        assertEquals(BackgroundUpdates.Install, read.backgroundUpdates, "read back by the next launch")
        assertTrue(read.updateOnMobileData)
    }
}
