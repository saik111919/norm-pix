package com.example.normpix.updates

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.BackgroundUpdates
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.SettingsScreen
import com.example.normpix.settings.ui.SettingsSpecial
import com.example.normpix.settings.ui.UpdatesSettingsPage
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.updates.ui.OfferAppUpdates
import com.example.normpix.updates.ui.UpdatePage
import com.example.normpix.updates.ui.UpdateSheetActions
import com.example.normpix.updates.ui.UpdateSheetContent
import java.io.File
import java.time.Instant
import kotlin.io.path.createTempDirectory
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

    private fun absent(text: String) = assertTrue(compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(), "\"$text\" is not shown")

    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    /** An updater on the fake network that does everything on the main thread, as the screen's own work does under test. */
    private fun updater(enabled: Boolean = true, installer: FakeInstaller = FakeInstaller()) = AppUpdater(
        installed = Releases.installed,
        checker = UpdateChecker(GitHubAccess(network, "NormPix/test"), Releases.REPO, Releases.installed),
        downloads = UpdateDownloader(GitHubAccess(network, "NormPix/test"), dir),
        inspector = FakeInspector(),
        installer = installer,
        records = records,
        scope = scope,
        io = Dispatchers.Main.immediate,
        enabled = enabled,
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
        compose.onNodeWithText("- Faster receipts").assertIsDisplayed()
        tap("Update now")
        tap("Later")
        assertEquals(listOf("update", "later"), taps)
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
        tap("Done")
        assertEquals(listOf("close"), taps)
    }

    // ------------------------------------------------------------ Settings

    @Test
    fun `Check for updates in Settings opens the update page`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { SettingsScreen() } }
        compose.onNodeWithText("Check for updates").performScrollTo()
        compose.onNodeWithText("Version 1.0.2").assertIsDisplayed()
        tap("Check for updates")
        assertEquals(1, environment.navigator.updatesOpened)
        assertEquals(emptyList(), network.asked, "the page asks GitHub, not the row")
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
        compose.onNodeWithText("1.0.3").assertIsDisplayed()
        compose.onNodeWithText("Update now").performScrollTo().assertIsDisplayed()
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
        scrollTap("Update now")
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            updater.state.value is UpdateState.Ready
        }
        compose.waitForIdle()
        assertEquals(1, compose.onAllNodesWithText("Ready to install").fetchSemanticsNodes().size, "the page says so, with no sheet over it")
        assertTrue(network.asked.drop(asked).none { "releases/latest" in it }, "not asked again: ${network.asked.drop(asked)}")
    }

    @Test
    fun `Settings leads to Automatic updates, saying what they do`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { SettingsScreen() } }
        compose.onNodeWithText("Automatic updates").performScrollTo()
        compose.onNodeWithText("Daily · Download, then ask · Wi-Fi only").assertIsDisplayed()
        tap("Automatic updates")
        assertEquals(listOf<SettingsPage?>(SettingsPage.Updates), environment.navigator.settingsPages)
    }

    @Test
    fun `automatic checks can be turned off, and with them everything done in the background`() {
        val environment = environment(updater())
        compose.setContent { environment.Content { UpdatesSettingsPage() } }
        assertTrue(environment.preferences.state.value.updateChecksEnabled)
        compose.onNodeWithText("Download it, then ask").performScrollTo().assertIsDisplayed()
        scrollTap("Check for updates automatically")
        assertFalse(environment.preferences.state.value.updateChecksEnabled)
        absent("When an update is found")
        absent("Download it, then ask")
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
        compose.onNodeWithText("Check for updates").performScrollTo()
        compose.onNodeWithText("Version 1.0.2 · updates come with release builds").assertIsDisplayed()
        absent("Check for updates automatically")
        absent("Automatic updates")
        assertEquals(emptyList(), network.asked)
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
        preferences.resetToDefaults()
        assertTrue(preferences.state.value.updateChecksEnabled)
        assertEquals(BackgroundUpdates.Download, preferences.state.value.backgroundUpdates)
        assertFalse(preferences.state.value.updateOnMobileData)
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
