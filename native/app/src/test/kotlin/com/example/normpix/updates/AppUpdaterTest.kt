package com.example.normpix.updates

import com.example.normpix.settings.UpdateCheckEvery
import com.example.normpix.updates.Releases.LATEST
import java.io.File
import java.net.UnknownHostException
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The whole of updating, as the user meets it: the daily check at launch and Later, Download update handing the release's link to
 * the browser (and saying so when it can't), what the check in the background does, and what a start of the app clears away of
 * the old in-app downloads - each ending somewhere the user can go on from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdaterTest {
    private val network = FakeNetwork()
    private val dir: File = createTempDirectory("updates").toFile()
    private val records = MemoryUpdateRecords()
    private val opener = FakeOpener()
    private val notifier = FakeNotifier()
    private val changelogs = MemoryChangelogStore()
    private var clock = 1_800_000_000_000L
    private val look = DownloadTabLook(toolbarColor = 0xFF1B1B1F.toInt(), dark = true)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    /** A fresh updater, as a new launch of the app makes one: only [records] carry over. */
    private fun TestScope.updater(
        enabled: Boolean = true,
        installed: InstalledApp = Releases.installed,
        oldDownloads: File? = null,
    ): AppUpdater {
        val github = GitHubAccess(network, "NormPix/test")
        return AppUpdater(
            installed = installed,
            checker = UpdateChecker(github, Releases.REPO, installed),
            opener = opener,
            records = records,
            // The test's own scope, not its background one: advanceUntilIdle runs (and runTest awaits) what the updater starts.
            scope = this,
            io = StandardTestDispatcher(testScheduler),
            now = { clock },
            enabled = enabled,
            notifier = notifier,
            changelogs = changelogs,
            oldDownloads = oldDownloads,
        )
    }

    /** What was fetched from the network that is a file of a release (the APK, or update.json). */
    private fun apkFetches() = network.asked.filter { it.contains(".apk") }

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
    fun `a launch asks GitHub once per the wait the user chose`() = runTest {
        Releases.publish(network)
        val hour = 60L * 60 * 1000
        // 13 hours on: too soon for once a day, time for twice a day.
        records.lastCheckAt = clock - 13 * hour
        assertNull(updater().launchOffer(allowed = true))
        assertEquals(emptyList(), network.asked, "once a day isn't due yet")
        assertEquals("1.0.3", updater().launchOffer(allowed = true, intervalMs = UpdateCheckEvery.TwiceADay.intervalMs)?.versionName)
        assertEquals(clock, records.lastCheckAt)

        // Four days on: too soon for once a week, time for every 3 days.
        records.lastCheckAt = clock - 4 * 24 * hour
        val asked = network.asked.size
        assertNull(updater().launchOffer(allowed = true, intervalMs = UpdateCheckEvery.OnceAWeek.intervalMs))
        assertEquals(asked, network.asked.size, "once a week isn't due yet")
        assertNotNull(updater().launchOffer(allowed = true, intervalMs = UpdateCheckEvery.EveryThreeDays.intervalMs))
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
        updater.retry(look)
        advanceUntilIdle()
        assertIs<UpdateState.Available>(updater.state.value)
        assertEquals(emptyList(), opener.opened, "Try again after a failed check checks; it opens nothing")
    }

    // ------------------------------------------------------------ Download update

    @Test
    fun `Download update hands the release's link to the browser, and the update stays as it was`() = runTest {
        Releases.publish(network)
        val updater = updater()
        val found = assertIs<UpdateState.Available>(updater.check())

        updater.openDownload(look)
        assertEquals(listOf(found.release), opener.opened)
        assertEquals(listOf(look), opener.looks)
        assertEquals(Releases.download("v1.0.3", Releases.apkName("1.0.3")), opener.opened.single().apkUrl)
        assertEquals(found, updater.state.value, "still there until the user installs it and the app is replaced")
        assertEquals(emptyList(), apkFetches(), "NormPix fetched no APK itself")

        // Again, as the user may after coming back from the browser.
        updater.openDownload(look)
        assertEquals(2, opener.opened.size)
    }

    @Test
    fun `without a browser it says so, and Try again opens it once there is one`() = runTest {
        Releases.publish(network)
        val updater = updater()
        val release = assertIs<UpdateState.Available>(updater.check()).release
        opener.failWith = UpdateProblem.NoBrowser

        updater.openDownload(look)
        assertEquals(UpdateState.Failed(UpdateProblem.NoBrowser, release), updater.state.value)
        assertTrue(UpdateProblem.NoBrowser.canRetry)
        assertEquals(emptyList(), opener.opened)

        opener.failWith = null
        updater.retry(look)
        advanceUntilIdle()
        assertEquals(listOf(release), opener.opened)
        assertEquals(UpdateState.Available(release), updater.state.value, "the update is what it was")
    }

    @Test
    fun `a link that is not GitHub's for this app is refused, said, and not tried again`() = runTest {
        Releases.publish(network)
        val updater = updater()
        val release = assertIs<UpdateState.Available>(updater.check()).release
        opener.failWith = UpdateProblem.BadLink

        updater.openDownload(look)
        assertEquals(UpdateState.Failed(UpdateProblem.BadLink, release), updater.state.value)
        assertFalse(UpdateProblem.BadLink.canRetry)

        opener.failWith = null
        updater.retry(look)
        advanceUntilIdle()
        assertEquals(emptyList(), opener.opened, "trying again can't help, so it isn't done")
        assertIs<UpdateState.Failed>(updater.state.value)
    }

    @Test
    fun `there is nothing to open before an update is found, when up to date, or after a failed check`() = runTest {
        Releases.publish(network, "1.0.2", 10002)
        val updater = updater()
        updater.openDownload(look)
        assertEquals(UpdateState.Idle, updater.state.value)
        updater.check()
        updater.openDownload(look)
        assertEquals(UpdateState.UpToDate("1.0.2"), updater.state.value)

        network.fail(LATEST, UnknownHostException("api.github.com"))
        updater.check()
        updater.openDownload(look)
        assertEquals(UpdateState.Failed(UpdateProblem.Offline), updater.state.value)
        assertEquals(emptyList(), opener.opened)
    }

    @Test
    fun `Later still works after the download could not be opened`() = runTest {
        Releases.publish(network)
        val updater = updater()
        updater.check()
        opener.failWith = UpdateProblem.NoBrowser
        updater.openDownload(look)
        updater.later()
        assertEquals(10003L, records.dismissedVersionCode)
        assertEquals(10003L, updater.laterVersionCode.value)
    }

    // ------------------------------------------------------------ in the background

    @Test
    fun `the check in the background says an update is out, once, and downloads nothing`() = runTest {
        Releases.publish(network)
        assertEquals(BackgroundRun.Done, updater().runInBackground())
        assertEquals(listOf("available 1.0.3"), notifier.posted)
        assertEquals(emptyList(), apkFetches(), "only the check: no APK was asked for")
        assertEquals(emptyList(), opener.opened, "and nothing was opened in a browser")

        clock += AppUpdater.CHECK_INTERVAL_MS
        assertEquals(BackgroundRun.Done, updater().runInBackground())
        assertEquals(1, notifier.posted.size, "said once per release")
    }

    @Test
    fun `an update put off with Later is left alone in the background`() = runTest {
        Releases.publish(network)
        records.dismissedVersionCode = 10003
        assertEquals(BackgroundRun.Done, updater().runInBackground())
        assertEquals(emptyList(), notifier.posted)
    }

    @Test
    fun `one marked as needed is said again in the background though Later was said to it`() = runTest {
        Releases.publish(network, changes = mapOf("mandatory" to JsonPrimitive(true)))
        records.dismissedVersionCode = 10003
        updater().runInBackground()
        assertEquals(listOf("available 1.0.3"), notifier.posted)
    }

    @Test
    fun `up to date, the check in the background says nothing`() = runTest {
        Releases.publish(network, "1.0.2", 10002)
        assertEquals(BackgroundRun.Done, updater().runInBackground())
        assertEquals(emptyList(), notifier.posted)
    }

    @Test
    fun `offline, the background run asks to go again soon, and a debug build never asks at all`() = runTest {
        network.fail(LATEST, UnknownHostException("api.github.com"))
        assertEquals(BackgroundRun.Again, updater().runInBackground())
        network.asked.clear()
        assertEquals(BackgroundRun.Done, updater(enabled = false).runInBackground())
        assertEquals(emptyList(), network.asked)
    }

    @Test
    fun `with notifications off nothing counts as said, so it is said once they can be`() = runTest {
        Releases.publish(network)
        notifier.shows = false
        updater().runInBackground()
        assertEquals(0L, records.announcedVersionCode)
        notifier.shows = true
        clock += AppUpdater.CHECK_INTERVAL_MS
        updater().runInBackground()
        assertEquals(listOf("available 1.0.3"), notifier.posted)
    }

    @Test
    fun `Later takes the notification down`() = runTest {
        Releases.publish(network)
        val updater = updater()
        updater.runInBackground()
        updater.later()
        assertEquals(1, notifier.withdrawn)
    }

    // ------------------------------------------------------------ at start

    @Test
    fun `at start the folder earlier versions downloaded updates into is deleted, whatever is in it`() = runTest {
        val old = File(dir, "updates").apply { mkdirs() }
        File(old, "NormPix-10003.apk").writeBytes(Releases.apk(2_000))
        File(old, "NormPix-10004.apk.part").writeBytes(Releases.apk(500))
        File(old, "other").apply { mkdirs() }.let { File(it, "x").writeText("x") }

        updater(oldDownloads = old).start()
        advanceUntilIdle()
        assertFalse(old.exists(), "nothing can install from it any more")
        assertTrue(dir.exists(), "only that folder goes")
    }

    @Test
    fun `at start without an old folder, or with one already gone, nothing happens`() = runTest {
        updater().start()
        updater(oldDownloads = File(dir, "never-there")).start()
        advanceUntilIdle()
        assertTrue(dir.exists())
    }

    @Test
    fun `at start the notification of an update the installed version has made unnecessary comes down, once`() = runTest {
        // An earlier version said 1.0.2 was out, then that it was downloaded and ready: both for the version now installed.
        records.announcedVersionCode = 10002
        records.readyAnnouncedVersionCode = 10002
        updater().start()
        assertEquals(1, notifier.withdrawn)
        assertEquals(0L, records.announcedVersionCode)
        assertEquals(0L, records.readyAnnouncedVersionCode)

        updater().start()
        assertEquals(1, notifier.withdrawn, "nothing to take down the next time")
    }

    @Test
    fun `at start a notification about a newer release than the installed one stays`() = runTest {
        records.announcedVersionCode = 10003
        updater().start()
        assertEquals(0, notifier.withdrawn)
        assertEquals(10003L, records.announcedVersionCode)
    }

    @Test
    fun `a version an earlier one installed without asking says so once`() = runTest {
        val installed = Releases.installed.copy(versionCode = 10003, versionName = "1.0.3")
        records.quietInstallVersionCode = 10003
        updater(installed = installed).start()
        assertEquals(listOf("installed 1.0.3"), notifier.posted)
        assertEquals(0L, records.quietInstallVersionCode)
        updater(installed = installed).start()
        assertEquals(1, notifier.posted.size)

        // A record for a version older than the one now installed is only cleared, not announced.
        records.quietInstallVersionCode = 10002
        updater(installed = installed).start()
        assertEquals(1, notifier.posted.size)
        assertEquals(0L, records.quietInstallVersionCode)
    }

    // ------------------------------------------------------------ the changelog

    private fun publishHistory(vararg releases: Pair<String, String>) =
        network.answer(Releases.HISTORY, Releases.list(*releases.map { (version, notes) -> Releases.note(version, notes) }.toTypedArray()))

    @Test
    fun `the changelog is what each published version changed, newest first, and is kept`() = runTest {
        publishHistory("1.0.2" to "- Second", "1.0.3" to "- Third", "1.0.1" to "- First")
        val updater = updater()
        assertTrue(updater.changelog.value.loading, "until the page has asked, it is about to")
        updater.loadChangelog()
        val state = updater.changelog.value
        assertEquals(listOf("1.0.3", "1.0.2", "1.0.1"), state.entries.map { it.versionName })
        assertEquals("- Third", state.entries.first().notes)
        assertFalse(state.loading)
        assertNull(state.problem)
        assertEquals(state.entries, changelogs.load(), "kept for next time")
    }

    @Test
    fun `without a connection the changelog saved earlier stays, and says why it is not fresh`() = runTest {
        publishHistory("1.0.2" to "- Second")
        updater().loadChangelog()
        network.fail(Releases.HISTORY, UnknownHostException("api.github.com"))
        val later = updater()
        later.loadChangelog()
        assertEquals(listOf("1.0.2"), later.changelog.value.entries.map { it.versionName })
        assertEquals(UpdateProblem.Offline, later.changelog.value.problem)
        assertFalse(later.changelog.value.loading)
    }

    @Test
    fun `with nothing saved and no connection the changelog is empty, with the reason`() = runTest {
        network.fail(Releases.HISTORY, UnknownHostException("api.github.com"))
        val updater = updater()
        updater.loadChangelog()
        assertEquals(emptyList(), updater.changelog.value.entries)
        assertEquals(UpdateProblem.Offline, updater.changelog.value.problem)
    }

    @Test
    fun `an empty answer doesn't wipe the changelog saved`() = runTest {
        publishHistory("1.0.2" to "- Second")
        updater().loadChangelog()
        network.answer(Releases.HISTORY, "[]")
        val later = updater()
        later.loadChangelog()
        assertEquals(listOf("1.0.2"), later.changelog.value.entries.map { it.versionName })
        assertNull(later.changelog.value.problem)
    }

    @Test
    fun `asking twice at once is one look`() = runTest {
        publishHistory("1.0.2" to "- Second")
        val updater = updater()
        val both = listOf(async { updater.loadChangelog() }, async { updater.loadChangelog() })
        both.forEach { it.await() }
        assertEquals(1, network.asked.count { it == Releases.HISTORY })
    }

    @Test
    fun `a debug build reads the changelog too, though it never checks for an update`() = runTest {
        publishHistory("1.0.2" to "- Second")
        val updater = updater(enabled = false)
        updater.loadChangelog()
        assertEquals(listOf("1.0.2"), updater.changelog.value.entries.map { it.versionName })
        assertEquals(listOf(Releases.HISTORY), network.asked, "only the list of releases is read")
    }

    @Test
    fun `a check keeps the notes of the release it finds, up to date or not`() = runTest {
        Releases.publish(network, "1.0.3", 10003, changes = mapOf("notes" to JsonPrimitive("- New in 1.0.3")))
        assertIs<UpdateState.Available>(updater().check())
        assertEquals(listOf("1.0.3"), changelogs.load().map { it.versionName })
        assertEquals("- New in 1.0.3", changelogs.load().single().notes)

        // The release the phone is on: its notes are there to read once an update has taken the page's newest slot.
        Releases.publish(network, "1.0.2", 10002, changes = mapOf("notes" to JsonPrimitive("- Words of 1.0.2")))
        assertIs<UpdateState.UpToDate>(updater().check())
        assertEquals(listOf("1.0.3", "1.0.2"), changelogs.load().map { it.versionName })
        assertEquals("- Words of 1.0.2", changelogs.load().last().notes)
    }
}
