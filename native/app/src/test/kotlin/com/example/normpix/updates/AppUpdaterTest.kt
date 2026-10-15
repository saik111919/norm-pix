package com.example.normpix.updates

import android.content.Intent
import com.example.normpix.settings.BackgroundUpdates
import com.example.normpix.updates.Releases.LATEST
import java.io.File
import java.io.InputStream
import java.net.UnknownHostException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The whole of updating, as the user meets it: the daily check at launch and Later, Update now through to Android's
 * installer, the leave Android asks for, and every way a download or an install can stop - each ending somewhere the user
 * can go on from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdaterTest {
    private val network = FakeNetwork()
    private val dir: File = createTempDirectory("updates").toFile()
    private val records = MemoryUpdateRecords()
    private val installer = FakeInstaller()
    private val inspector = FakeInspector()
    private val notifier = FakeNotifier()
    private var clock = 1_800_000_000_000L

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /**
     * A fresh updater, as a new launch of the app makes one: only [records] carry over. [onScreen] false is a process
     * started for the background run, with NormPix never on screen.
     */
    private fun TestScope.updater(
        enabled: Boolean = true,
        onScreen: Boolean = true,
        installed: InstalledApp = Releases.installed,
    ): AppUpdater {
        val github = GitHubAccess(network, "NormPix/test")
        return AppUpdater(
            installed = installed,
            checker = UpdateChecker(github, Releases.REPO, installed),
            downloads = UpdateDownloader(github, dir),
            inspector = inspector,
            installer = installer,
            records = records,
            // The test's own scope, not its background one: advanceUntilIdle runs (and runTest awaits) what the updater starts.
            scope = this,
            io = StandardTestDispatcher(testScheduler),
            now = { clock },
            enabled = enabled,
            notifier = notifier,
        ).also { if (onScreen) it.onForeground() }
    }

    /** An updater that has found 1.0.3 and been told Update now, run until it stops (at Ready, when all went well). */
    private suspend fun TestScope.downloaded(): AppUpdater {
        Releases.publish(network)
        val updater = updater()
        assertIs<UpdateState.Available>(updater.check())
        updater.download()
        advanceUntilIdle()
        return updater
    }

    /** The same, then told Install: handed to Android's installer (or stopped on the way, run until it stops). */
    private suspend fun TestScope.installing(): AppUpdater = downloaded().also { updater ->
        assertIs<UpdateState.Ready>(updater.state.value)
        updater.install()
        advanceUntilIdle()
    }

    private val apkUrl = Releases.download("v1.0.3", Releases.apkName("1.0.3"))

    private fun downloads() = dir.list().orEmpty().toList()

    // ------------------------------------------------------------ checking

    @Test
    fun `a launch asks GitHub at most once a day`() = runTest {
        Releases.publish(network)
        records.lastCheckAt = clock - 60 * 60 * 1000
        assertNull(updater().launchOffer(allowed = true))
        assertEquals(emptyList(), network.asked, "an hour after the last check, nothing is asked")

        records.lastCheckAt = clock - AppUpdater.CHECK_INTERVAL_MS
        val next = updater()
        assertEquals("1.0.3", next.launchOffer(allowed = true)?.versionName)
        assertEquals(clock, records.lastCheckAt)
        assertNull(next.launchOffer(allowed = true), "a launch offers once")
    }

    @Test
    fun `nothing is asked at launch with automatic checks off, nor ever by a debug build`() = runTest {
        Releases.publish(network)
        assertNull(updater().launchOffer(allowed = false))
        assertNull(updater(enabled = false).launchOffer(allowed = true))
        assertEquals(UpdateState.Failed(UpdateProblem.Disabled), updater(enabled = false).check())
        assertEquals(emptyList(), network.asked)
    }

    @Test
    fun `Later keeps a release from coming back at launch, but not a newer one, nor one marked mandatory`() = runTest {
        Releases.publish(network)
        val first = updater()
        assertNotNull(first.launchOffer(allowed = true))
        first.later()
        assertEquals(10003L, records.dismissedVersionCode)

        clock += AppUpdater.CHECK_INTERVAL_MS
        val second = updater()
        assertNull(second.launchOffer(allowed = true))
        assertIs<UpdateState.Available>(second.state.value, "Settings still shows it")
        // Asked in Settings, it is shown.
        assertIs<UpdateState.Available>(second.check())

        clock += AppUpdater.CHECK_INTERVAL_MS
        Releases.publish(network, "1.0.4", 10004)
        assertEquals("1.0.4", updater().launchOffer(allowed = true)?.versionName)

        // Marked mandatory by its release: offered though the user said Later to that very version...
        clock += AppUpdater.CHECK_INTERVAL_MS
        records.dismissedVersionCode = 10004
        Releases.publish(network, "1.0.4", 10004, changes = mapOf("mandatory" to JsonPrimitive(true)))
        val mandatory = updater()
        assertEquals("1.0.4", mandatory.launchOffer(allowed = true)?.versionName)
        // ...and Later can't put it off.
        records.dismissedVersionCode = 0
        mandatory.later()
        assertEquals(0L, records.dismissedVersionCode)
    }

    @Test
    fun `up to date, and checks GitHub never heard don't count as asked`() = runTest {
        Releases.publish(network, "1.0.2", 10002)
        val updater = updater()
        assertEquals(UpdateState.UpToDate("1.0.2"), updater.check())
        assertEquals(clock, records.lastCheckAt)

        val asked = clock
        clock += 1_000
        network.fail(LATEST, UnknownHostException("api.github.com"))
        assertEquals(UpdateState.Failed(UpdateProblem.Offline), updater.check())
        assertEquals(asked, records.lastCheckAt, "offline: the next launch asks again")

        network.answer(LATEST, code = 403, headers = mapOf("X-RateLimit-Remaining" to "0"))
        assertEquals(UpdateState.Failed(UpdateProblem.RateLimited(null)), updater.check())
        assertEquals(clock, records.lastCheckAt, "GitHub answered: no asking again for a day")
    }

    @Test
    fun `a failed check is tried again`() = runTest {
        network.fail(LATEST, UnknownHostException("api.github.com"))
        val updater = updater()
        updater.check()
        Releases.publish(network)
        updater.retry()
        advanceUntilIdle()
        assertIs<UpdateState.Available>(updater.state.value)
    }

    // ------------------------------------------------------------ download and install

    @Test
    fun `Update now downloads the release and checks it, and only Install hands it to Android, which asks the user`() = runTest {
        val apk = Releases.publish(network)
        val updater = updater()
        updater.check()
        val states = mutableListOf<UpdateState>()
        val announced = mutableListOf<UpdateRelease>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { updater.state.toList(states) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { updater.downloaded.toList(announced) }
        updater.download()
        advanceUntilIdle()

        // Downloaded and checked, it waits for the user: nothing is handed to Android yet.
        val ready = assertIs<UpdateState.Ready>(updater.state.value)
        assertContentEquals(apk, ready.apk.readBytes())
        assertEquals(emptyList(), installer.installs)
        assertEquals(listOf(ready.release), announced)
        assertTrue(states.any { it is UpdateState.Downloading && it.done == 0L })
        assertTrue(states.any { it is UpdateState.Downloading && it.done == apk.size.toLong() })

        updater.install()
        advanceUntilIdle()
        val installing = assertIs<UpdateState.Installing>(updater.state.value)
        assertEquals(listOf(installing.apk), installer.installs)

        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL")
        updater.onInstallNeedsUser(1, confirm)
        assertEquals(listOf(confirm), installer.confirmations)
        updater.onInstallFinished(1, InstallOutcome.Success)
        assertEquals(UpdateState.UpToDate("1.0.3"), updater.state.value)
        assertEquals(emptyList(), downloads())
    }

    @Test
    fun `the installer's word about another install is ignored`() = runTest {
        val updater = installing()
        updater.onInstallFinished(99, InstallOutcome.Cancelled)
        updater.onInstallNeedsUser(99, Intent("x"))
        assertIs<UpdateState.Installing>(updater.state.value)
        assertEquals(emptyList(), installer.confirmations)
    }

    @Test
    fun `a download that isn't the release is never installed, and trying again downloads it afresh`() = runTest {
        val apk = Releases.publish(network)
        Releases.serveFile(network, "v1.0.3", Releases.apkName("1.0.3"), apk.copyOf().also { it[5] = (it[5] + 1).toByte() })
        val updater = updater()
        updater.check()
        updater.download()
        advanceUntilIdle()
        val failed = assertIs<UpdateState.Failed>(updater.state.value)
        assertEquals(UpdateProblem.ChecksumMismatch, failed.problem)
        assertNull(failed.apk)
        assertEquals(emptyList(), installer.installs)
        assertEquals(emptyList(), downloads())

        Releases.serveFile(network, "v1.0.3", Releases.apkName("1.0.3"), apk)
        updater.retry()
        advanceUntilIdle()
        assertIs<UpdateState.Ready>(updater.state.value)
        assertEquals(emptyList(), installer.installs)
    }

    @Test
    fun `an APK that isn't NormPix, isn't newer, or is signed by someone else is refused and deleted`() = runTest {
        for ((setUp, expected) in listOf(
            { inspector.packageName = "com.example.other" } to UpdateProblem.WrongPackage("com.example.other"),
            { inspector.versionCode = 10002L } to UpdateProblem.NotNewer(10002),
            { inspector.signers = setOf("someone-else") } to UpdateProblem.SignatureMismatch,
        )) {
            inspector.reset()
            setUp()
            val updater = downloaded()
            val failed = assertIs<UpdateState.Failed>(updater.state.value)
            assertEquals(expected, failed.problem)
            assertEquals(emptyList(), downloads())
            assertEquals(emptyList(), installer.installs)
            updater.retry()
            advanceUntilIdle()
            assertEquals(failed, updater.state.value, "trying again can't help, so it isn't offered")
        }
    }

    @Test
    fun `a stopped download keeps nothing and can be started again`() = runTest {
        val apk = Releases.publish(network)
        val updater = updater()
        updater.check()
        network.stream(apkUrl, apk.size.toLong()) { CancellingStream(apk) { updater.cancelDownload() } }
        updater.download()
        advanceUntilIdle()
        assertEquals(UpdateState.Cancelled(updater.state.value.release!!, null), updater.state.value)
        assertEquals(emptyList(), downloads())

        network.answer(apkUrl, body = apk)
        updater.retry()
        advanceUntilIdle()
        assertIs<UpdateState.Ready>(updater.state.value)
    }

    @Test
    fun `a broken connection fails the download, and trying again finishes it`() = runTest {
        val apk = Releases.publish(network)
        val updater = updater()
        updater.check()
        network.stream(apkUrl, apk.size.toLong()) { DroppingStream(apk.copyOf(5_000)) }
        updater.download()
        advanceUntilIdle()
        assertEquals(UpdateProblem.Interrupted, assertIs<UpdateState.Failed>(updater.state.value).problem)
        network.answer(apkUrl, body = apk)
        updater.retry()
        advanceUntilIdle()
        assertIs<UpdateState.Ready>(updater.state.value)
    }

    @Test
    fun `without leave to install apps, Android's setting opens and the update carries on once it is given`() = runTest {
        installer.allowed = false
        val updater = installing()
        assertIs<UpdateState.NeedsPermission>(updater.state.value)
        assertEquals(emptyList(), installer.installs)

        updater.allowInstalls()
        updater.onBackground()
        assertEquals(1, installer.settingOpened)
        // Back without allowing it: still waiting.
        updater.onForeground()
        advanceUntilIdle()
        assertIs<UpdateState.NeedsPermission>(updater.state.value)
        // Back having allowed it: on to the installer, without another tap.
        updater.onBackground()
        installer.allowed = true
        updater.onForeground()
        advanceUntilIdle()
        assertIs<UpdateState.Installing>(updater.state.value)
        assertEquals(1, installer.installs.size)
    }

    @Test
    fun `the confirmation waits until NormPix is on screen to show it`() = runTest {
        val updater = installing()
        updater.onBackground()
        updater.onInstallNeedsUser(1, Intent("confirm"))
        assertEquals(emptyList(), installer.confirmations)
        updater.onForeground()
        assertEquals(1, installer.confirmations.size)
    }

    @Test
    fun `turning down the install keeps the download, to install later`() = runTest {
        val updater = installing()
        updater.onInstallFinished(1, InstallOutcome.Cancelled)
        val cancelled = assertIs<UpdateState.Cancelled>(updater.state.value)
        assertNotNull(cancelled.apk)
        updater.install()
        advanceUntilIdle()
        assertIs<UpdateState.Installing>(updater.state.value)
        assertEquals(2, installer.installs.size)
    }

    @Test
    fun `backing out of Android's confirmation without a word from it counts as turned down`() = runTest {
        val updater = installing()
        updater.onInstallNeedsUser(1, Intent("confirm"))
        updater.onBackground()
        // Back while the install is still under way (the user said yes): it is left alone.
        updater.onForeground()
        advanceTimeBy(AppUpdater.CONFIRM_SETTLE_MS + 1)
        runCurrent()
        assertIs<UpdateState.Installing>(updater.state.value)
        // Back with the install gone and nothing said.
        updater.onBackground()
        installer.active.clear()
        updater.onForeground()
        advanceTimeBy(AppUpdater.CONFIRM_SETTLE_MS + 1)
        runCurrent()
        assertIs<UpdateState.Cancelled>(updater.state.value)
    }

    @Test
    fun `an install that fails says why, and keeps the download only where trying again can help`() = runTest {
        val updater = installing()
        updater.onInstallFinished(1, InstallOutcome.Failed(UpdateProblem.InstallBlocked))
        assertNotNull(assertIs<UpdateState.Failed>(updater.state.value).apk)
        updater.retry()
        advanceUntilIdle()
        assertIs<UpdateState.Installing>(updater.state.value)

        updater.onInstallFinished(2, InstallOutcome.Failed(UpdateProblem.SignatureMismatch))
        val failed = assertIs<UpdateState.Failed>(updater.state.value)
        assertNull(failed.apk)
        assertEquals(emptyList(), downloads())
    }

    @Test
    fun `an install Android won't start says why`() = runTest {
        installer.failWith = UpdateProblem.InstallFailed("Session commit failed")
        val updater = installing()
        val failed = assertIs<UpdateState.Failed>(updater.state.value)
        assertEquals(UpdateProblem.InstallFailed("Session commit failed"), failed.problem)
        assertNotNull(failed.apk)
        installer.failWith = null
        updater.retry()
        advanceUntilIdle()
        assertIs<UpdateState.Installing>(updater.state.value)
    }

    @Test
    fun `a download the system cleared from the cache is downloaded again, and the Install that found it gone goes on`() = runTest {
        val updater = installing()
        updater.onInstallFinished(1, InstallOutcome.Cancelled)
        dir.listFiles()!!.forEach { it.delete() }
        updater.install()
        advanceUntilIdle()
        assertIs<UpdateState.Installing>(updater.state.value)
        assertTrue(downloads().isNotEmpty())
    }

    @Test
    fun `a finished download waits for Install however long it takes, and Later doesn't lose it`() = runTest {
        val updater = downloaded()
        advanceTimeBy(60 * 60 * 1000L)
        runCurrent()
        assertIs<UpdateState.Ready>(updater.state.value)
        assertEquals(emptyList(), installer.installs)
        updater.later()
        assertIs<UpdateState.Ready>(updater.state.value, "Later only keeps it from being offered at launch")
        updater.install()
        advanceUntilIdle()
        assertIs<UpdateState.Installing>(updater.state.value)
        assertEquals(1, installer.installs.size)
    }

    // ------------------------------------------------------------ the download's notification

    @Test
    fun `a download shows its progress in a notification, and takes it down when it ends`() = runTest {
        val updater = downloaded()
        assertIs<UpdateState.Ready>(updater.state.value)
        assertEquals(0L to false, notifier.progress.first(), "shown as it starts, as the user's own")
        assertTrue(notifier.progress.size > 1, "and as it goes")
        assertEquals(1, notifier.stopped)
        assertEquals(emptyList(), notifier.posted, "NormPix is on screen: the sheet says it's ready, not a notification")
    }

    @Test
    fun `a download that finishes with NormPix off screen says it's ready in a notification`() = runTest {
        Releases.publish(network)
        val updater = updater()
        assertIs<UpdateState.Available>(updater.check())
        updater.download()
        updater.onBackground()
        advanceUntilIdle()
        assertIs<UpdateState.Ready>(updater.state.value)
        assertEquals(listOf("ready 1.0.3"), notifier.posted)
        assertEquals(1, notifier.stopped)
    }

    @Test
    fun `Download on a notification finds the release in a fresh process and downloads it`() = runTest {
        Releases.publish(network)
        val updater = updater(onScreen = false)
        updater.downloadNow()
        advanceUntilIdle()
        assertIs<UpdateState.Ready>(updater.state.value)
        assertEquals(listOf("ready 1.0.3"), notifier.posted)
        assertEquals(false, notifier.progress.first().second, "the user's own download, not the background's")

        // Again, with it downloaded: nothing more.
        val before = network.asked.size
        updater.downloadNow()
        advanceUntilIdle()
        assertTrue(network.asked.drop(before).isEmpty(), "nothing asked again: ${network.asked.drop(before)}")
    }

    @Test
    fun `a stopped download takes its notification down`() = runTest {
        val apk = Releases.publish(network)
        val updater = updater()
        network.stream(apkUrl, apk.size.toLong()) { CancellingStream(apk) { updater.cancelDownload() } }
        assertIs<UpdateState.Available>(updater.check())
        updater.download()
        advanceUntilIdle()
        assertIs<UpdateState.Cancelled>(updater.state.value)
        assertEquals(1, notifier.stopped)
    }

    // ------------------------------------------------------------ in the background

    @Test
    fun `Tell me says an update is out, once, and downloads nothing`() = runTest {
        Releases.publish(network)
        assertEquals(BackgroundRun.Done, updater(onScreen = false).runInBackground(BackgroundUpdates.Notify))
        assertEquals(listOf("available 1.0.3"), notifier.posted)
        assertEquals(emptyList(), downloads())

        clock += AppUpdater.CHECK_INTERVAL_MS
        assertEquals(BackgroundRun.Done, updater(onScreen = false).runInBackground(BackgroundUpdates.Notify))
        assertEquals(1, notifier.posted.size, "said once per release")
    }

    @Test
    fun `Download, then ask downloads in the background, says it's ready once, and installs nothing`() = runTest {
        val apk = Releases.publish(network)
        val updater = updater(onScreen = false)
        val announced = mutableListOf<UpdateRelease>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { updater.downloaded.toList(announced) }
        assertEquals(BackgroundRun.Done, updater.runInBackground(BackgroundUpdates.Download))
        val ready = assertIs<UpdateState.Ready>(updater.state.value)
        assertContentEquals(apk, ready.apk.readBytes())
        assertEquals(listOf("ready 1.0.3"), notifier.posted)
        assertEquals(emptyList(), installer.installs)
        assertEquals(emptyList(), announced, "the notification says it, not a sheet over whatever is on screen")

        clock += AppUpdater.CHECK_INTERVAL_MS
        assertEquals(BackgroundRun.Done, updater.runInBackground(BackgroundUpdates.Download))
        assertEquals(1, notifier.posted.size, "said once per release")

        // NormPix opened later the same day, in a new process: offered at once, ready to install, without downloading again.
        clock += 60 * 60 * 1000
        val opened = updater()
        val asked = network.asked.size
        assertEquals("1.0.3", opened.launchOffer(allowed = true)?.versionName)
        assertIs<UpdateState.Ready>(opened.state.value)
        assertTrue(network.asked.drop(asked).none { "NormPix-1.0.3.apk" in it }, "not downloaded again: ${network.asked.drop(asked)}")
        // Install from the sheet: the notification goes, and Android asks the user as always.
        opened.install()
        advanceUntilIdle()
        assertEquals(listOf(false), installer.quiet)
        assertTrue(notifier.withdrawn > 0)
    }

    @Test
    fun `an update put off with Later is left alone in the background`() = runTest {
        Releases.publish(network)
        records.dismissedVersionCode = 10003
        assertEquals(BackgroundRun.Done, updater(onScreen = false).runInBackground(BackgroundUpdates.Install))
        assertEquals(emptyList(), downloads())
        assertEquals(emptyList(), notifier.posted)
        assertEquals(emptyList(), installer.installs)
    }

    @Test
    fun `Install automatically installs without asking while NormPix is closed, and the new version says so`() = runTest {
        Releases.publish(network)
        val updater = updater(onScreen = false)
        val run = async { updater.runInBackground(BackgroundUpdates.Install) }
        runCurrent()
        assertIs<UpdateState.Installing>(updater.state.value)
        assertEquals(listOf(true), installer.quiet)
        assertEquals(10003L, records.quietInstallVersionCode)
        assertEquals(emptyList(), notifier.posted, "nothing to say until it's installed")
        updater.onInstallFinished(1, InstallOutcome.Success)
        assertEquals(BackgroundRun.Done, run.await())

        // The new version starts: it says it came by itself, once.
        updater(onScreen = false, installed = Releases.installed.copy(versionCode = 10003, versionName = "1.0.3")).start()
        assertEquals(listOf("installed 1.0.3"), notifier.posted)
        assertEquals(0L, records.quietInstallVersionCode)
        updater(onScreen = false, installed = Releases.installed.copy(versionCode = 10003, versionName = "1.0.3")).start()
        assertEquals(1, notifier.posted.size)
    }

    @Test
    fun `an automatic install waits while NormPix is on screen, and a while after it was left`() = runTest {
        Releases.publish(network)
        val updater = updater()
        assertEquals(BackgroundRun.Again, updater.runInBackground(BackgroundUpdates.Install))
        assertIs<UpdateState.Ready>(updater.state.value)
        assertEquals(emptyList(), installer.installs)

        updater.onBackground()
        clock += 60_000
        assertEquals(BackgroundRun.Again, updater.runInBackground(BackgroundUpdates.Install))
        assertEquals(emptyList(), installer.installs)

        clock += AppUpdater.QUIET_AFTER_MS
        val run = async { updater.runInBackground(BackgroundUpdates.Install) }
        runCurrent()
        assertEquals(listOf(true), installer.quiet)
        updater.onInstallFinished(1, InstallOutcome.Success)
        assertEquals(BackgroundRun.Done, run.await())
    }

    @Test
    fun `when Android wants the user to confirm, an automatic install waits for their Install and says it's ready`() = runTest {
        Releases.publish(network)
        val updater = updater(onScreen = false)
        val run = async { updater.runInBackground(BackgroundUpdates.Install) }
        runCurrent()
        updater.onInstallNeedsUser(1, Intent("confirm"))
        assertEquals(listOf(1), installer.abandoned)
        assertEquals(emptyList(), installer.confirmations, "nothing is put in front of the user from the background")
        assertIs<UpdateState.Ready>(updater.state.value)
        assertEquals(listOf("ready 1.0.3"), notifier.posted)
        assertEquals(BackgroundRun.Done, run.await())
        assertEquals(0L, records.quietInstallVersionCode)

        // The next day it isn't tried again without asking: it waits for the user.
        clock += AppUpdater.CHECK_INTERVAL_MS
        assertEquals(BackgroundRun.Done, updater.runInBackground(BackgroundUpdates.Install))
        assertEquals(1, installer.installs.size)
        updater.install()
        advanceUntilIdle()
        assertEquals(listOf(true, false), installer.quiet)
    }

    @Test
    fun `an automatic install Android refuses for now is left for the user's Install`() = runTest {
        Releases.publish(network)
        val updater = updater(onScreen = false)
        val run = async { updater.runInBackground(BackgroundUpdates.Install) }
        runCurrent()
        updater.onInstallFinished(1, InstallOutcome.Failed(UpdateProblem.InstallBlocked))
        assertEquals(BackgroundRun.Done, run.await())
        assertIs<UpdateState.Ready>(updater.state.value)
        assertEquals(listOf("ready 1.0.3"), notifier.posted)
    }

    @Test
    fun `without leave to install apps, or before Android 12, Install automatically asks like Download`() = runTest {
        for (setUp in listOf({ installer.allowed = false }, { installer.installsQuietly = false })) {
            installer.allowed = true
            installer.installsQuietly = true
            records.readyAnnouncedVersionCode = 0
            notifier.posted.clear()
            setUp()
            Releases.publish(network)
            val updater = updater(onScreen = false)
            assertEquals(BackgroundRun.Done, updater.runInBackground(BackgroundUpdates.Install))
            assertIs<UpdateState.Ready>(updater.state.value)
            assertEquals(emptyList(), installer.installs)
            assertEquals(listOf("ready 1.0.3"), notifier.posted)
        }
    }

    @Test
    fun `a background download Android stops leaves the update to download again, and says nothing`() = runTest {
        val apk = Releases.publish(network)
        val updater = updater(onScreen = false)
        network.stream(apkUrl, apk.size.toLong()) { CancellingStream(apk) { updater.stopBackgroundWork() } }
        assertEquals(BackgroundRun.Again, updater.runInBackground(BackgroundUpdates.Download))
        assertIs<UpdateState.Available>(updater.state.value, "not \"stopped by the user\"")
        assertEquals(emptyList(), downloads())
        assertEquals(emptyList(), notifier.posted)

        network.answer(apkUrl, body = apk)
        assertEquals(BackgroundRun.Done, updater.runInBackground(BackgroundUpdates.Download))
        assertIs<UpdateState.Ready>(updater.state.value)
    }

    @Test
    fun `offline, the background run asks to go again soon, and a debug build never asks at all`() = runTest {
        network.fail(LATEST, UnknownHostException("api.github.com"))
        assertEquals(BackgroundRun.Again, updater(onScreen = false).runInBackground(BackgroundUpdates.Download))
        network.asked.clear()
        assertEquals(BackgroundRun.Done, updater(enabled = false, onScreen = false).runInBackground(BackgroundUpdates.Install))
        assertEquals(emptyList(), network.asked)
    }

    @Test
    fun `with notifications off nothing counts as said, so it is said once they can be`() = runTest {
        Releases.publish(network)
        notifier.shows = false
        updater(onScreen = false).runInBackground(BackgroundUpdates.Download)
        assertEquals(0L, records.readyAnnouncedVersionCode)
        notifier.shows = true
        clock += AppUpdater.CHECK_INTERVAL_MS
        updater(onScreen = false).runInBackground(BackgroundUpdates.Download)
        assertEquals(listOf("ready 1.0.3"), notifier.posted)
    }

    @Test
    fun `Later takes the notification down`() = runTest {
        Releases.publish(network)
        val updater = updater(onScreen = false)
        updater.runInBackground(BackgroundUpdates.Download)
        updater.later()
        assertEquals(1, notifier.withdrawn)
    }
}

class FakeInstaller : UpdateInstaller {
    var allowed = true
    override var installsQuietly = true
    var settingOpened = 0
    var failWith: UpdateProblem? = null
    val installs = mutableListOf<File>()

    /** For each of [installs], whether Android was asked not to ask the user. */
    val quiet = mutableListOf<Boolean>()
    val abandoned = mutableListOf<Int>()
    val confirmations = mutableListOf<Intent>()
    val active = mutableSetOf<Int>()
    private var nextId = 1

    override fun canInstall() = allowed

    override fun openInstallSetting(): Boolean {
        settingOpened++
        return true
    }

    override fun install(apk: File, release: UpdateRelease, quiet: Boolean, started: (id: Int) -> Unit) {
        failWith?.let { throw UpdateException(it) }
        val id = nextId++
        started(id)
        installs += apk
        this.quiet += quiet
        active += id
    }

    override fun abandon(id: Int) {
        abandoned += id
        active -= id
    }

    override fun confirm(intent: Intent): Boolean {
        confirmations += intent
        return true
    }

    override fun isActive(id: Int) = id in active
}

/** Remembers what it was asked to post; [shows] false is notifications being off. */
class FakeNotifier(var shows: Boolean = true) : UpdateNotifier {
    val posted = mutableListOf<String>()
    var withdrawn = 0

    override fun available(release: UpdateRelease) = post("available ${release.versionName}")

    override fun ready(release: UpdateRelease) = post("ready ${release.versionName}")

    override fun installed(versionName: String) = post("installed $versionName")

    override fun withdraw() {
        withdrawn++
    }

    /** Each progress report: the bytes done, and whether the daily check started the download. */
    val progress = mutableListOf<Pair<Long, Boolean>>()
    var stopped = 0

    override fun downloading(release: UpdateRelease, done: Long, total: Long, background: Boolean) {
        progress += done to background
    }

    override fun downloadStopped() {
        stopped++
    }

    private fun post(what: String): Boolean {
        if (shows) posted += what
        return shows
    }
}

/** Reads a downloaded APK as the release it was meant to be, unless a test says otherwise. */
class FakeInspector : ApkInspector {
    var packageName = Releases.APP_ID
    var versionCode: Long? = null
    var signers = setOf("release-key")

    fun reset() {
        packageName = Releases.APP_ID
        versionCode = null
        signers = setOf("release-key")
    }

    override fun inspect(apk: File) =
        ApkFacts(packageName, versionCode ?: apk.name.removePrefix("NormPix-").removeSuffix(".apk").toLong(), signers)

    override fun installedSigners() = setOf("release-key")
}

/** Hands out [bytes] a chunk at a time and, half way, calls [halfWay] - as a user tapping Cancel download would. */
private class CancellingStream(private val bytes: ByteArray, private val halfWay: () -> Unit) : InputStream() {
    private var at = 0
    private var called = false

    override fun read(): Int = if (at < bytes.size) bytes[at++].toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (at >= bytes.size) return -1
        val n = minOf(len, 8 * 1024, bytes.size - at)
        System.arraycopy(bytes, at, b, off, n)
        at += n
        if (!called && at > bytes.size / 2) {
            called = true
            halfWay()
        }
        return n
    }
}
