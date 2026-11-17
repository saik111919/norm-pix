package com.example.normpix.updates

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.screens.HomeScreen
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.updates.ui.UpdateSheetActions
import com.example.normpix.updates.ui.updateBannerFor
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

/** Home's "Needs attention" shows an update that is out, with Download update (which opens the browser) and Later. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeUpdateBannerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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

    /** An updater on the fake network that does everything on the main thread, as the screens' own work does under test. */
    private fun updater(enabled: Boolean = true) = AppUpdater(
        installed = Releases.installed,
        checker = UpdateChecker(GitHubAccess(network, "NormPix/test"), Releases.REPO, Releases.installed),
        opener = opener,
        records = records,
        scope = scope,
        io = Dispatchers.Main.immediate,
        enabled = enabled,
    )

    private fun home(updater: AppUpdater?): TestEnvironment {
        val environment = TestEnvironment(prefsName = "home_update_banner", updates = updater).also { env = it }
        compose.setContent { environment.Content { HomeScreen() } }
        compose.waitForIdle()
        return environment
    }

    /** GitHub is asked, as a launch or the background would, so the update is found. */
    private fun find(updater: AppUpdater) {
        scope.launch { updater.check() }
        compose.waitForIdle()
    }

    private fun banners(text: String) = compose.onAllNodesWithContentDescription(text, substring = true).fetchSemanticsNodes()

    private fun waitForBanner(text: String) = compose.waitUntil(15_000) {
        compose.waitForIdle()
        banners(text).isNotEmpty()
    }

    /** The buttons the banner offers a screen reader, in the order they sit (the quieter one first). */
    private fun actionsOf(text: String): List<String> =
        compose.onNodeWithContentDescription(text, substring = true).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    private fun press(text: String, label: String) {
        val action = compose.onNodeWithContentDescription(text, substring = true).fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].first { it.label == label }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()
    }

    @Test
    fun `without an updater, or in a build that does not update, Home has no update banner`() {
        home(null)
        compose.onAllNodesWithText("Needs attention").assertCountEquals(0)
    }

    @Test
    fun `a build that does not update itself shows nothing`() {
        Releases.publish(network)
        val updater = updater(enabled = false)
        home(updater)
        find(updater)
        compose.onAllNodesWithText("Needs attention").assertCountEquals(0)
        assertEquals(emptyList(), network.asked)
    }

    @Test
    fun `an update that is out is under Needs attention, with Download update and Later`() {
        Releases.publish(network)
        val updater = updater()
        home(updater)
        compose.onAllNodesWithText("Needs attention").assertCountEquals(0)
        find(updater)
        waitForBanner("NormPix 1.0.3 is available")
        compose.onNodeWithText("Needs attention").assertIsDisplayed()
        compose.onNodeWithContentDescription("App update", substring = true).assertIsDisplayed()
        assertEquals(listOf("Later", "Download update"), actionsOf("NormPix 1.0.3 is available"))
    }

    @Test
    fun `Download update opens the browser, and the banner stays for the update that is still waiting`() {
        Releases.publish(network)
        val updater = updater()
        home(updater)
        find(updater)
        waitForBanner("NormPix 1.0.3 is available")

        press("NormPix 1.0.3 is available", "Download update")
        assertEquals(listOf("1.0.3"), opener.opened.map { it.versionName })
        assertEquals(Releases.download("v1.0.3", Releases.apkName("1.0.3")), opener.opened.single().apkUrl)
        assertIs<UpdateState.Available>(updater.state.value)
        assertEquals(1, banners("NormPix 1.0.3 is available").size)
        assertEquals(emptyList(), network.asked.filter { ".apk" in it }, "NormPix fetched nothing itself")
        assertEquals(0L, records.dismissedVersionCode)
    }

    @Test
    fun `with no browser the banner stays, Download update tries again, and the page that opens says why`() {
        Releases.publish(network)
        val updater = updater()
        val environment = home(updater)
        find(updater)
        waitForBanner("NormPix 1.0.3 is available")

        opener.failWith = UpdateProblem.NoBrowser
        press("NormPix 1.0.3 is available", "Download update")
        assertIs<UpdateState.Failed>(updater.state.value)
        assertEquals(emptyList(), opener.opened)
        waitForBanner("NormPix 1.0.3 is available")
        assertEquals(listOf("Later", "Download update"), actionsOf("NormPix 1.0.3 is available"))

        opener.failWith = null
        press("NormPix 1.0.3 is available", "Download update")
        assertEquals(1, opener.opened.size)
        assertIs<UpdateState.Available>(updater.state.value)

        compose.onNodeWithContentDescription("NormPix 1.0.3 is available", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(1, environment.navigator.updatesOpened)
    }

    @Test
    fun `Later takes the banner down and keeps it off for that release`() {
        Releases.publish(network)
        val updater = updater()
        home(updater)
        find(updater)
        waitForBanner("NormPix 1.0.3 is available")

        press("NormPix 1.0.3 is available", "Later")
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            banners("NormPix 1.0.3 is available").isEmpty()
        }
        compose.onAllNodesWithText("Needs attention").assertCountEquals(0)
        assertEquals(10003L, records.dismissedVersionCode)
        assertIs<UpdateState.Available>(updater.state.value, "Settings still shows the update")
    }

    @Test
    fun `a release put off before stays off Home`() {
        records.dismissedVersionCode = 10003
        Releases.publish(network)
        val updater = updater()
        home(updater)
        find(updater)
        assertIs<UpdateState.Available>(updater.state.value)
        assertEquals(emptyList(), banners("NormPix 1.0.3 is available"))
        compose.onAllNodesWithText("Needs attention").assertCountEquals(0)
    }

    @Test
    fun `a newer release than the one put off comes back`() {
        records.dismissedVersionCode = 10003
        Releases.publish(network, "1.0.4", 10004)
        val updater = updater()
        home(updater)
        find(updater)
        waitForBanner("NormPix 1.0.4 is available")
    }

    @Test
    fun `an update marked as needed has no Later`() {
        Releases.publish(network, changes = mapOf("mandatory" to JsonPrimitive(true)))
        val updater = updater()
        home(updater)
        find(updater)
        waitForBanner("NormPix 1.0.3 is available")
        assertEquals(listOf("Download update"), actionsOf("NormPix 1.0.3 is available"))
    }

    @Test
    fun `pressing the banner opens the App update page`() {
        Releases.publish(network)
        val updater = updater()
        val environment = home(updater)
        find(updater)
        waitForBanner("NormPix 1.0.3 is available")
        compose.onNodeWithContentDescription("NormPix 1.0.3 is available", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(1, environment.navigator.updatesOpened)
    }
}

/** The update banner for each state of an update, without a screen. */
class UpdateBannerTest {
    private val release = UpdateRelease(
        versionCode = 10003, versionName = "1.0.3", tag = "v1.0.3", publishedAt = Instant.parse("2026-10-15T09:00:00Z"),
        notes = "- Faster receipts", apkName = "NormPix-1.0.3.apk",
        apkUrl = "https://github.com/owner/normpix/releases/download/v1.0.3/NormPix-1.0.3.apk", size = 12L * 1024 * 1024,
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

    private fun bannerFor(state: UpdateState, offered: Boolean = true) = updateBannerFor(
        state = state,
        offered = offered,
        installedVersion = "1.0.2",
        bytes = { "${it / (1024 * 1024)} MB" },
        actions = actions,
        open = { taps += "open" },
    )

    @Test
    fun `an update that is out says so, with Download update on the right and Later before it`() {
        val banner = assertNotNull(bannerFor(UpdateState.Available(release)))
        assertEquals("App update", banner.eyebrow)
        assertEquals("NormPix 1.0.3 is available", banner.title)
        assertEquals("You have 1.0.2", banner.message)
        // The size is the banner's figure, beside the lines.
        assertEquals("Size", banner.figure?.title)
        assertEquals("12 MB", banner.figure?.figure)
        assertEquals("Update to 1.0.3", banner.figure?.note)
        assertEquals(StatusTone.Info, banner.tone)
        assertEquals("Download update", banner.primary.label)
        assertEquals("Later", banner.secondary?.label)
        assertNull(banner.bar, "nothing is downloaded by the app, so there is no progress")
        banner.primary.onClick()
        banner.secondary?.onClick?.invoke()
        banner.onOpen()
        assertEquals(listOf("download", "later", "open"), taps)
    }

    @Test
    fun `one put off, or one that is not an update, has no banner`() {
        assertNull(bannerFor(UpdateState.Available(release), offered = false))
        assertNull(bannerFor(UpdateState.Failed(UpdateProblem.NoBrowser, release), offered = false))
        for (state in listOf(UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate("1.0.2"))) {
            assertNull(bannerFor(state), state.toString())
        }
        // A check that failed has no release to offer.
        assertNull(bannerFor(UpdateState.Failed(UpdateProblem.Offline)))
    }

    @Test
    fun `an update whose download could not be opened keeps its banner while trying again can help`() {
        val banner = assertNotNull(bannerFor(UpdateState.Failed(UpdateProblem.NoBrowser, release)))
        assertEquals("NormPix 1.0.3 is available", banner.title)
        assertEquals("Download update", banner.primary.label)
        banner.primary.onClick()
        assertEquals(listOf("download"), taps)
        // A refused link can't be helped by trying again: no banner offers it.
        assertNull(bannerFor(UpdateState.Failed(UpdateProblem.BadLink, release)))
    }

    @Test
    fun `an update marked as needed has no Later`() {
        val needed = release.copy(mandatory = true)
        for (state in listOf(UpdateState.Available(needed), UpdateState.Failed(UpdateProblem.NoBrowser, needed))) {
            assertNull(assertNotNull(bannerFor(state), state.toString()).secondary, state.toString())
        }
    }

    @Test
    fun `it is the one banner whatever the update is doing, so it keeps its place in its stack`() {
        val keys = listOf(
            UpdateState.Available(release),
            UpdateState.Failed(UpdateProblem.NoBrowser, release),
        ).map { assertNotNull(bannerFor(it)).key }
        assertEquals(1, keys.toSet().size)
    }
}
