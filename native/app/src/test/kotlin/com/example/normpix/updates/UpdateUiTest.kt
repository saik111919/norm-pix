package com.example.normpix.updates

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
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
import com.example.normpix.settings.AppLockScope
import com.example.normpix.settings.AppLockTimeout
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
import com.example.normpix.updates.ui.ChangelogPage
import com.example.normpix.updates.ui.DOWNLOAD_STEPS
import com.example.normpix.updates.ui.OfferAppUpdates
import com.example.normpix.updates.ui.UpdateBody
import com.example.normpix.updates.ui.UpdatePage
import com.example.normpix.updates.ui.UpdateSheetActions
import com.example.normpix.updates.ui.UpdateSheetContent
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.AppLockService
import java.time.Instant
import java.time.LocalTime
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
import org.robolectric.Shadows.shadowOf
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
    private val taps = mutableListOf<String>()
    private val actions = UpdateSheetActions(
        download = { taps += "download" },
        later = { taps += "later" },
        retry = { taps += "retry" },
        close = { taps += "close" },
        checkAgain = { taps += "check" },
    )

    private val network = FakeNetwork()
    private val records = MemoryUpdateRecords()
    private val opener = FakeOpener()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var env: TestEnvironment? = null

    @AfterTest
    fun tearDown() {
        scope.cancel()
        env?.close()
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
        opener: UpdateDownloadOpener = this.opener,
        changelogs: ChangelogStore = MemoryChangelogStore(),
    ) = AppUpdater(
        installed = Releases.installed,
        checker = UpdateChecker(GitHubAccess(network, "NormPix/test"), Releases.REPO, Releases.installed),
        opener = opener,
        records = records,
        scope = scope,
        io = Dispatchers.Main.immediate,
        enabled = enabled,
        changelogs = changelogs,
    )

    private fun environment(updater: AppUpdater?) = TestEnvironment(prefsName = "update_ui_test", updates = updater).also { env = it }

    // ------------------------------------------------------------ the sheet

    @Test
    fun `an update shows both versions, when it came out, what's new, and Download update or Later`() {
        show(UpdateState.Available(release))
        compose.onNodeWithText("Update available").assertIsDisplayed()
        compose.onNodeWithText("1.0.2").assertIsDisplayed()
        compose.onNodeWithText("1.0.3").assertIsDisplayed()
        compose.onNodeWithText("Released", substring = true).assertIsDisplayed()
        compose.onNodeWithText("What's new").assertIsDisplayed()
        // Each change on a bullet of its own: the dash of the commit line is not shown as text.
        compose.onNodeWithText("Faster receipts").assertIsDisplayed()
        absent("- Faster receipts")
        absent("Update now")
        tap("Download update")
        tap("Later")
        assertEquals(listOf("download", "later"), taps)
    }

    @Test
    fun `under the buttons, short steps say what happens after Download update`() {
        assertEquals(
            "Your browser downloads it. Open the download and tap Update. The first time, Android asks you to let your browser " +
                "install apps. Your data stays.",
            DOWNLOAD_STEPS,
        )
        show(UpdateState.Available(release))
        compose.onNodeWithText(DOWNLOAD_STEPS).assertIsDisplayed()
        val button = boundsOf("Download update")
        val later = boundsOf("Later")
        val steps = boundsOf(DOWNLOAD_STEPS)
        assertTrue(steps.top >= button.bottom && steps.top >= later.bottom, "the steps $steps are under the buttons $later $button")
        // Said for an update waiting to be downloaded, and for nothing else.
        show(UpdateState.UpToDate("1.0.2"))
        absent(DOWNLOAD_STEPS)
        show(UpdateState.Failed(UpdateProblem.NoBrowser, release))
        absent(DOWNLOAD_STEPS)
        show(UpdateState.Checking)
        absent(DOWNLOAD_STEPS)
    }

    @Test
    fun `Later is a text button on the left, Download update a filled button on the right`() {
        show(UpdateState.Available(release))
        val later = boundsOf("Later")
        val update = boundsOf("Download update")
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue(later.right <= update.left, "Later $later is left of Download update $update")
        assertTrue(later.left < screen.left + screen.width / 2, "Later is in the left half")
        assertTrue(update.right > screen.left + screen.width / 2, "Download update is in the right half")
        assertTrue(screen.right - update.right <= 32.dp, "Download update sits at the right edge: $update in $screen")
        assertEquals(0f, abs((later.top + later.bottom).value - (update.top + update.bottom).value), 2f, "on one line")
    }

    @Test
    fun `a single action sits on the right`() {
        val screen = { compose.onRoot().getBoundsInRoot() }
        // Up to date has Done on the left and Check for update, the one thing to do next, on the right.
        show(UpdateState.UpToDate("1.0.2"))
        assertTrue(boundsOf("Done").right <= boundsOf("Check for update").left)
        assertTrue(screen().right - boundsOf("Check for update").right <= 32.dp, "Check for update is on the right")
        show(UpdateState.Checking)
        assertTrue(screen().right - boundsOf("Close").right <= 32.dp, "Close is on the right")
        // A failure that can't be tried again has Close alone; one that can has Close on the left and Try again on the right.
        show(UpdateState.Failed(UpdateProblem.BadLink, release))
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
    fun `nothing is shown of a download or an install, for NormPix does neither`() {
        show(UpdateState.Available(release))
        for (gone in listOf("Install", "Cancel download", "Download again", "Open settings", "Downloading update", "Ready to install")) absent(gone)
        assertTrue(compose.onAllNodesWithText("%", substring = true).fetchSemanticsNodes().isEmpty(), "no progress")
    }

    @Test
    fun `a failure says why, and offers Try again only where it can help`() {
        show(UpdateState.Failed(UpdateProblem.Offline))
        compose.onNodeWithText("Couldn't check for updates").assertIsDisplayed()
        compose.onNodeWithText("Couldn't reach GitHub", substring = true).assertIsDisplayed()
        tap("Try again")
        // No browser: said in the sheet, and trying again can help.
        show(UpdateState.Failed(UpdateProblem.NoBrowser, release))
        compose.onNodeWithText("Couldn't open the download").assertIsDisplayed()
        compose.onNodeWithText("couldn't open a browser", substring = true).assertIsDisplayed()
        tap("Try again")
        // A link that isn't GitHub's for this app: said, and trying again can't help.
        show(UpdateState.Failed(UpdateProblem.BadLink, release))
        compose.onNodeWithText("Couldn't open the download").assertIsDisplayed()
        compose.onNodeWithText("isn't a file on NormPix's own GitHub releases", substring = true).assertIsDisplayed()
        absent("Try again")
        assertEquals(listOf("retry", "retry"), taps)
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
    fun `an update waiting is a pill at the end of the row, saying Update`() {
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
        for (quiet in listOf(UpdateState.Idle, UpdateState.UpToDate("1.0.2"), UpdateState.Failed(UpdateProblem.NoBrowser, release))) {
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
        compose.onNodeWithText("Download update").assertIsDisplayed()
        compose.onNodeWithText("Later").assertIsDisplayed()
        compose.onNodeWithText(DOWNLOAD_STEPS).assertIsDisplayed()
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
    fun `the update page's buttons are two pills stacked across the foot of the screen, Download update over Later`() {
        Releases.publish(network)
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        val screen = compose.onRoot().getBoundsInRoot()
        val update = boundsOf("Download update")
        val later = boundsOf("Later")
        assertTrue(update.bottom <= later.top, "Download update $update is over Later $later")
        assertEquals(update.left.value, later.left.value, 1f, "the same width, one under the other")
        assertEquals(update.right.value, later.right.value, 1f)
        assertTrue(update.left - screen.left <= 32.dp && screen.right - update.right <= 32.dp, "across the page: $update in $screen")
        assertTrue(update.width > screen.width * 0.8f, "full width: ${update.width} of ${screen.width}")
        assertTrue(later.top > screen.top + screen.height / 2, "at the foot of the screen: $later")
        // The steps are under them, at the very foot.
        assertTrue(boundsOf(DOWNLOAD_STEPS).top >= later.bottom, "the steps are under the buttons")
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
        absent(DOWNLOAD_STEPS)
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
        compose.onNodeWithText("Download update").assertIsDisplayed()
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
        val pinned = boundsOf("Download update")
        val later = boundsOf("Later")
        val steps = boundsOf(DOWNLOAD_STEPS)
        assertTrue(pinned.top > screen.top + screen.height / 2, "the buttons are in the lower half, without scrolling: $pinned")
        assertTrue(pinned.bottom <= later.top, "Download update over Later")
        assertTrue(screen.right - pinned.right <= 32.dp, "the buttons run across the page")

        // The page scrolls all the way to its last line while the buttons stay where they are. (Its last line is a list item of
        // its own, composed only once the list has come near it: the list is scrolled to it, rather than the line to the screen.)
        compose.onNode(hasScrollToNodeAction())
            .performScrollToNode(hasText("Only GitHub is contacted", substring = true))
        compose.onNodeWithText("Only GitHub is contacted", substring = true).assertIsDisplayed()
        assertEquals(pinned, boundsOf("Download update"), "the bar doesn't move with the page")
        assertEquals(later, boundsOf("Later"))
        assertEquals(steps, boundsOf(DOWNLOAD_STEPS), "nor do the steps under it")
        tap("Download update")
        assertEquals(1, opener.opened.size)
        assertIs<UpdateState.Available>(environment.container.updates?.state?.value, "still an update waiting")
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
    fun `Download update on the update page hands the link to the browser, asks GitHub nothing more, and no sheet comes up`() {
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
        tap("Download update")
        assertEquals(1, opener.opened.size)
        assertEquals(Releases.download("v1.0.3", Releases.apkName("1.0.3")), opener.opened.single().apkUrl)
        assertEquals(1, compose.onAllNodesWithText("Update available").fetchSemanticsNodes().size, "the page says so, with no sheet over it")
        assertEquals(emptyList(), network.asked.drop(asked), "nothing more was asked of GitHub: the browser downloads it")
        // The tab's look follows the app's theme: its toolbar is the surface colour.
        assertTrue(opener.looks.single().toolbarColor != 0)
    }

    @Test
    fun `Download update opens a Custom Tab on the release's APK, and the app lock is told`() {
        Releases.publish(network)
        val context = ApplicationProvider.getApplicationContext<Application>()
        val lock = AppLockService()
        // No screen of the app to launch from here, so the tab starts in a task of its own.
        val real = BrowserDownloadOpener(context, ActivityHost(), lock, Releases.REPO)
        val environment = environment(updater(opener = real))
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        lock.unlockAll()
        tap("Download update")

        val launched = assertNotNull(shadowOf(context).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, launched.action)
        assertEquals(Releases.download("v1.0.3", Releases.apkName("1.0.3")), launched.dataString)
        assertTrue(launched.hasExtra(CustomTabsIntent.EXTRA_SESSION), "a Custom Tab")
        // The app's own surface colour, light or dark with the app: the sheet's theme here is the default (light).
        assertTrue(launched.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0) != 0)
        // Still the update, on its page.
        compose.onNodeWithText("Update available").assertIsDisplayed()
        // The trip to the browser was allowed for: leaving NormPix now doesn't lock it.
        val strict = AppPrefsState(walletLockEnabled = true, appLockScope = AppLockScope.EntireApp, appLockTimeout = AppLockTimeout.Immediate)
        lock.onAppPaused(strict)
        assertFalse(lock.isAppLocked(strict))
    }

    @Test
    fun `a download link that is not this app's on GitHub is refused, said on the page, and not opened`() {
        Releases.publish(network)
        val context = ApplicationProvider.getApplicationContext<Application>()
        // The release came from owner/normpix, but this opener only trusts another repository's links.
        val picky = BrowserDownloadOpener(context, ActivityHost(), AppLockService(), "someone/else")
        val environment = environment(updater(opener = picky))
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        tap("Download update")
        waitFor("Couldn't open the download")
        compose.onNodeWithText("isn't a file on NormPix's own GitHub releases", substring = true).assertIsDisplayed()
        absent("Try again")
        assertNull(shadowOf(context).nextStartedActivity, "nothing was opened")
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun `with no browser it is said on the page, and Try again opens the download once there is one`() {
        Releases.publish(network)
        opener.failWith = UpdateProblem.NoBrowser
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        tap("Download update")
        waitFor("Couldn't open the download")
        compose.onNodeWithText("couldn't open a browser", substring = true).assertIsDisplayed()
        assertEquals(emptyList(), opener.opened)

        opener.failWith = null
        tap("Try again")
        waitFor("Update available")
        assertEquals(1, opener.opened.size)
        absent("Couldn't open the download")
    }

    @Test
    fun `the update page leads to Automatic updates, saying how often`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        compose.onNodeWithText("Automatic updates").performScrollTo()
        compose.onNodeWithText("Daily").assertIsDisplayed()
        tap("Automatic updates")
        assertEquals(listOf<SettingsPage?>(SettingsPage.Updates), environment.navigator.settingsPages)
    }

    @Test
    fun `the summary says how often and at what time, and nothing of what an earlier version did`() {
        assertEquals("Daily", automaticUpdatesSummary(AppPrefsState()))
        val weekly = AppPrefsState(updateCheckEvery = UpdateCheckEvery.OnceAWeek, updateCheckWhen = UpdateCheckWhen.AtSetTime)
        assertEquals("Weekly at 9:00 PM", automaticUpdatesSummary(weekly))
        assertEquals("Weekly at 21:00", automaticUpdatesSummary(weekly, is24Hour = true))
        assertEquals(
            "Every 3 days at 7:30 AM",
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
            "Twice daily at 9:00 AM and 9:00 PM",
            automaticUpdatesSummary(weekly.copy(updateCheckEvery = UpdateCheckEvery.TwiceADay, updateCheckMinuteOfDay = 9 * 60)),
        )
        assertEquals("Twice daily", automaticUpdatesSummary(AppPrefsState(updateCheckEvery = UpdateCheckEvery.TwiceADay)))
        assertEquals("Off · only when you check", automaticUpdatesSummary(AppPrefsState(updateChecksEnabled = false)))
    }

    @Test
    fun `the update page shows the summary of the choices made`() {
        val environment = environment(updater())
        environment.preferences.setUpdateCheckEvery(UpdateCheckEvery.OnceAWeek)
        environment.preferences.setUpdateCheckWhen(UpdateCheckWhen.AtSetTime)
        compose.setContent { environment.Content { UpdatePage() } }
        compose.onNodeWithText("Automatic updates").performScrollTo()
        compose.onNodeWithText("Weekly at 9:00 PM").assertIsDisplayed()
    }

    @Test
    fun `automatic checks can be turned off, and with them everything done in the background`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        assertTrue(environment.preferences.state.value.updateChecksEnabled)
        compose.onNodeWithText("How often").performScrollTo().assertIsDisplayed()
        scrollTap("Check for updates automatically")
        assertFalse(environment.preferences.state.value.updateChecksEnabled)
        absent("How often")
        absent("Check when you open NormPix")
    }

    @Test
    fun `what to do with an update found, downloading on mobile data and Android's leave are gone from Automatic updates`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        compose.onNodeWithText("Check when you open NormPix").performScrollTo().assertIsDisplayed()
        for (gone in listOf(
            "When an update is found", "Tell me", "Download it, then ask", "Install it automatically", "Network", "Download on mobile data",
            "Android's leave", "Allow NormPix to install updates",
        )) {
            absent(gone)
        }
        assertTrue(compose.onAllNodesWithText("mobile data", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("Wi-Fi", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `the update files row ends the page and says the browser leaves the files, and stays when checking is off`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        compose.onNodeWithText("Update files").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete downloaded APK files").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pick NormPix files your browser left in Downloads after an update").assertIsDisplayed()
        compose.onNodeWithText("Your browser saves an update in Downloads", substring = true).performScrollTo().assertIsDisplayed()
        // After the groups that choose how NormPix checks, not before them.
        assertTrue(boundsOf("Update files").top > boundsOf("Check when you open NormPix").top, "the last group of the page")

        scrollTap("Check for updates automatically")
        assertFalse(environment.preferences.state.value.updateChecksEnabled)
        compose.onNodeWithText("Delete downloaded APK files").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the update files row is on the page of a build that does not update itself`() {
        val environment = environment(updater(enabled = false))
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        compose.onNodeWithText("Updates come with release builds").assertIsDisplayed()
        compose.onNodeWithText("Update files").assertIsDisplayed()
        compose.onNodeWithText("Delete downloaded APK files").assertIsDisplayed()
    }

    @Test
    fun `settings search finds the update files row on the Automatic updates page`() {
        for (query in listOf("apk", "delete apk", "leftover", "installer file", "clean up", "update file", "browser")) {
            val found = SettingsCatalog.search(query).filter { it.title == "Delete downloaded APK files" }
            assertEquals(listOf<SettingsPage?>(SettingsPage.Updates), found.map { it.page }, "\"$query\" finds it")
        }
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
        compose.onNodeWithText("once the phone has a network and its battery isn't low", substring = true).performScrollTo().assertIsDisplayed()

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
    fun `a debug build's update page has no Automatic updates, but still shows the changelog pill`() {
        val environment = environment(updater(enabled = false))
        compose.setContent { environment.Content { UpdatePage() } }
        compose.onNodeWithText("Changelog").performScrollTo().assertIsDisplayed()
        absent("Automatic updates")
        assertTrue(compose.onAllNodesWithText("Only GitHub is contacted", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(network.asked.isEmpty(), "it does not load changelog history on open")
        compose.onNodeWithText("Changelog").performClick()
        assertEquals(1, environment.navigator.changelogOpened)
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
    fun `the update page shows a changelog pill that opens the changelog page and does not load history on open`() {
        Releases.publish(network)
        publishChangelog()
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatePage() } }
        waitFor("Update available")
        compose.onNodeWithText("Changelog").performScrollTo().assertIsDisplayed()
        assertFalse(network.asked.contains(Releases.HISTORY), "history is not loaded on update page open")
        compose.onNodeWithText("Changelog").performClick()
        assertEquals(1, environment.navigator.changelogOpened)
    }

    @Test
    fun `the changelog page lists what every version changed, the installed one open and marked`() {
        publishChangelog()
        val environment = environment(updater())
        compose.setContent { environment.Content { ChangelogPage() } }
        waitFor("Version 1.0.2")
        compose.onNodeWithText("Changelog").assertIsDisplayed()
        // What the phone has is read first: open, as a list of its changes under their heading.
        compose.onNodeWithText("Notes for 1.0.2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("A second change in 1.0.2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("What's new").assertIsDisplayed()
        compose.onNodeWithText("2 changes", substring = true).assertExists()
        compose.onNodeWithText("Installed").assertExists()
        // The newer one is marked and shut: its notes are a tap away.
        compose.onNodeWithText("New").assertExists()
        absent("Notes for 1.0.3")
        absent("Notes for 1.0.1")
    }

    @Test
    fun `a version opens to its changes when tapped, and shuts again`() {
        publishChangelog()
        val environment = environment(updater())
        compose.setContent { environment.Content { ChangelogPage() } }
        waitFor("Version 1.0.1")
        scrollTap("Version 1.0.1")
        waitFor("Notes for 1.0.1")
        scrollTap("Version 1.0.1")
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Notes for 1.0.1").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun `up to date, the changelog page says what the version installed changed`() {
        network.answer(Releases.HISTORY, Releases.list(Releases.note("1.0.2", "- What 1.0.2 brought")))
        val environment = environment(updater())
        compose.setContent { environment.Content { ChangelogPage() } }
        waitFor("What 1.0.2 brought")
        compose.onNodeWithText("What 1.0.2 brought").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `with no connection the changelog says why, and a tap asks again`() {
        // GitHub can't be reached at all: no check was heard either, so nothing was saved for the changelog to fall back on.
        network.fail(Releases.HISTORY, java.net.UnknownHostException("api.github.com"))
        val environment = environment(updater())
        compose.setContent { environment.Content { ChangelogPage() } }
        waitFor("Couldn't load the changelog")
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
        network.fail(Releases.HISTORY, java.net.UnknownHostException("api.github.com"))
        val environment = environment(updater(changelogs = MemoryChangelogStore(listOf(saved))))
        compose.setContent { environment.Content { ChangelogPage() } }
        waitFor("Version 1.0.2")
        compose.onNodeWithText("Saved words").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Couldn't refresh the changelog just now", substring = true).performScrollTo().assertIsDisplayed()
        absent("Couldn't load the changelog")
    }

    @Test
    fun `a version that came with no notes says so`() {
        network.answer(Releases.HISTORY, Releases.list(Releases.note("1.0.2", "")))
        val environment = environment(updater())
        compose.setContent { environment.Content { ChangelogPage() } }
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
            "Automatic updates" to listOf("automatic updates", "auto update", "automatic checks", "check automatically"),
        )
        for ((title, queries) in searches) {
            for (query in queries) {
                val found = SettingsCatalog.search(query).filter { it.title == title }
                assertEquals(listOf<SettingsPage?>(SettingsPage.Updates), found.map { it.page }, "\"$query\" finds \"$title\"")
            }
        }
    }

    @Test
    fun `settings search no longer offers what is gone from the Automatic updates page`() {
        for (query in listOf("mobile data", "metered", "hotspot", "install automatically", "download automatically", "wi-fi", "wifi", "cellular")) {
            val onThePage = SettingsCatalog.search(query).filter { it.page == SettingsPage.Updates }
            assertEquals(emptyList(), onThePage.map { it.title }, "\"$query\" finds nothing on Automatic updates")
        }
    }

    // ------------------------------------------------------------ at launch

    @Test
    fun `a newer release is found at launch once unlocked, and no sheet comes up over the screen`() {
        Releases.publish(network)
        val updater = updater()
        val environment = environment(updater)
        var locked by mutableStateOf(true)
        compose.setContent { environment.Content { OfferAppUpdates(updater, appLocked = locked) } }
        compose.waitForIdle()
        assertEquals(emptyList(), network.asked, "nothing is asked while the app is locked")

        locked = false
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            updater.state.value is UpdateState.Available
        }
        // It is Home's update banner that says so; the user updates when they choose to.
        compose.waitForIdle()
        absent("Update available")
        absent("Later")
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
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            updater.state.value is UpdateState.Available
        }
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
        val store = store()
        val preferences = AppPreferences(store)
        preferences.setUpdateChecksEnabled(false)
        store.edit().putString("background_updates", "Install").putBoolean("update_on_mobile_data", true).commit()
        preferences.setUpdateCheckEvery(UpdateCheckEvery.OnceAWeek)
        preferences.setUpdateCheckWhen(UpdateCheckWhen.AtSetTime)
        preferences.setUpdateCheckTime(6, 45)
        preferences.setUpdateCheckOnOpen(false)
        preferences.resetToDefaults()
        assertTrue(preferences.state.value.updateChecksEnabled)
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
    fun `what an earlier version stored about downloading is still read, and nothing acts on it`() {
        val store = store()
        val defaults = AppPreferences(store).state.value
        assertEquals(BackgroundUpdates.Download, defaults.backgroundUpdates)
        assertFalse(defaults.updateOnMobileData)
        // As an earlier version wrote them (the keys are never renamed).
        store.edit().putString("background_updates", "Install").putBoolean("update_on_mobile_data", true).commit()
        val read = AppPreferences(store).state.value
        assertEquals(BackgroundUpdates.Install, read.backgroundUpdates, "read back as stored")
        assertTrue(read.updateOnMobileData)
        assertEquals(AppPrefsState().updateCheckPlan(), read.updateCheckPlan(), "and ignored by the check in the background")
        // A value from a version that is no more reads as the default rather than failing.
        store.edit().putString("background_updates", "Teleport").commit()
        assertEquals(BackgroundUpdates.Download, AppPreferences(store).state.value.backgroundUpdates)
    }
}
