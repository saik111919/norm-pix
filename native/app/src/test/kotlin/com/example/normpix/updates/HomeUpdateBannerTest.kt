package com.example.normpix.updates

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
import org.robolectric.annotation.Config

/** Home's "Needs attention" shows an update that is out, and follows it through downloading to Install. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeUpdateBannerTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val network = FakeNetwork()
    private val dir: File = createTempDirectory("home_updates").toFile()
    private val records = MemoryUpdateRecords()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var env: TestEnvironment? = null

    @AfterTest
    fun tearDown() {
        scope.cancel()
        env?.close()
        dir.deleteRecursively()
    }

    /** An updater on the fake network that does everything on the main thread, as the screens' own work does under test. */
    private fun updater(enabled: Boolean = true) = AppUpdater(
        installed = Releases.installed,
        checker = UpdateChecker(GitHubAccess(network, "NormPix/test"), Releases.REPO, Releases.installed),
        downloads = UpdateDownloader(GitHubAccess(network, "NormPix/test"), dir),
        inspector = FakeInspector(),
        installer = FakeInstaller(),
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
    fun `an update that is out is under Needs attention, with Update and Later`() {
        Releases.publish(network)
        val updater = updater()
        home(updater)
        compose.onAllNodesWithText("Needs attention").assertCountEquals(0)
        find(updater)
        waitForBanner("NormPix 1.0.3 is available")
        compose.onNodeWithText("Needs attention").assertIsDisplayed()
        compose.onNodeWithContentDescription("App update", substring = true).assertIsDisplayed()
        assertEquals(listOf("Later", "Update"), actionsOf("NormPix 1.0.3 is available"))
    }

    @Test
    fun `Update starts the download as the sheet's Update now does, and the banner follows it to Install`() {
        Releases.publish(network)
        val updater = updater()
        home(updater)
        find(updater)
        waitForBanner("NormPix 1.0.3 is available")

        press("NormPix 1.0.3 is available", "Update")
        waitForBanner("Ready to install")
        assertIs<UpdateState.Ready>(updater.state.value)
        assertTrue(network.asked.any { "NormPix-1.0.3.apk" in it }, "the APK was fetched: ${network.asked}")
        assertEquals(listOf("Later", "Install"), actionsOf("Ready to install"))
        assertEquals(0L, records.dismissedVersionCode)
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
        assertEquals(listOf("Update"), actionsOf("NormPix 1.0.3 is available"))
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

/** The update banner for each step of an update, without a screen. */
class UpdateBannerTest {
    private val release = UpdateRelease(
        versionCode = 10003, versionName = "1.0.3", tag = "v1.0.3", publishedAt = Instant.parse("2026-10-15T09:00:00Z"),
        notes = "- Faster receipts", apkName = "NormPix-1.0.3.apk",
        apkUrl = "https://github.com/owner/normpix/releases/download/v1.0.3/NormPix-1.0.3.apk", size = 12L * 1024 * 1024,
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
    fun `an update that is out says so, with Update on the right and Later before it`() {
        val banner = assertNotNull(bannerFor(UpdateState.Available(release)))
        assertEquals("App update", banner.eyebrow)
        assertEquals("NormPix 1.0.3 is available", banner.title)
        assertEquals("You have 1.0.2 · 12 MB", banner.message)
        assertEquals(StatusTone.Info, banner.tone)
        assertEquals("Update", banner.primary.label)
        assertEquals("Later", banner.secondary?.label)
        banner.primary.onClick()
        banner.secondary?.onClick?.invoke()
        banner.onOpen()
        assertEquals(listOf("update", "later", "open"), taps)
    }

    @Test
    fun `one put off, or one that is not an update, has no banner`() {
        assertNull(bannerFor(UpdateState.Available(release), offered = false))
        assertNull(bannerFor(UpdateState.Ready(release, apk), offered = false))
        assertNull(bannerFor(UpdateState.NeedsPermission(release, apk), offered = false))
        assertNull(bannerFor(UpdateState.Cancelled(release, apk), offered = false))
        assertNull(bannerFor(UpdateState.Cancelled(release, null), offered = false))
        for (state in listOf(UpdateState.Idle, UpdateState.Checking, UpdateState.UpToDate("1.0.2"), UpdateState.Installing(release, apk))) {
            assertNull(bannerFor(state), state.toString())
        }
        assertNull(bannerFor(UpdateState.Failed(UpdateProblem.Offline)))
        assertNull(bannerFor(UpdateState.Failed(UpdateProblem.Storage, release)))
    }

    @Test
    fun `a download says how far it is, offers Cancel, and is shown even after Later`() {
        val banner = assertNotNull(bannerFor(UpdateState.Downloading(release, release.size / 4, release.size), offered = false))
        assertEquals("Downloading 1.0.3 · 25%", banner.title)
        assertEquals("3 MB of 12 MB", banner.message)
        assertEquals("Cancel", banner.secondary?.label)
        assertEquals("Details", banner.primary.label)
        banner.secondary?.onClick?.invoke()
        banner.primary.onClick()
        assertEquals(listOf("cancel", "open"), taps)
    }

    @Test
    fun `a downloaded update, or one turned down, waits for Install`() {
        for (state in listOf(UpdateState.Ready(release, apk), UpdateState.Cancelled(release, apk))) {
            taps.clear()
            val banner = assertNotNull(bannerFor(state), state.toString())
            assertEquals("Ready to install", banner.title)
            assertEquals("NormPix 1.0.3 is downloaded and checked", banner.message)
            assertEquals("Install", banner.primary.label)
            assertEquals("Later", banner.secondary?.label)
            banner.primary.onClick()
            assertEquals(listOf("install"), taps)
        }
    }

    @Test
    fun `a download the user stopped can be started again`() {
        val banner = assertNotNull(bannerFor(UpdateState.Cancelled(release, null)))
        assertEquals("NormPix 1.0.3 is available", banner.title)
        assertEquals("Update", banner.primary.label)
    }

    @Test
    fun `an update waiting for Android's leave offers Allow installs`() {
        val banner = assertNotNull(bannerFor(UpdateState.NeedsPermission(release, apk)))
        assertEquals("Allow installs", banner.primary.label)
        assertEquals("Later", banner.secondary?.label)
        banner.primary.onClick()
        assertEquals(listOf("allow"), taps)
    }

    @Test
    fun `an update marked as needed has no Later at any step`() {
        val needed = release.copy(mandatory = true)
        for (state in listOf(UpdateState.Available(needed), UpdateState.Ready(needed, apk), UpdateState.NeedsPermission(needed, apk))) {
            assertNull(assertNotNull(bannerFor(state), state.toString()).secondary, state.toString())
        }
    }

    @Test
    fun `every step is the one banner, so it keeps its place in its stack`() {
        val keys = listOf(
            UpdateState.Available(release),
            UpdateState.Downloading(release, 1, 2),
            UpdateState.Ready(release, apk),
            UpdateState.NeedsPermission(release, apk),
        ).map { assertNotNull(bannerFor(it)).key }
        assertEquals(1, keys.toSet().size)
    }
}
