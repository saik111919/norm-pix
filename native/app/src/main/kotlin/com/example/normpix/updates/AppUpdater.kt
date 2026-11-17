package com.example.normpix.updates

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.example.normpix.BuildConfig
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.AppLockService
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * All the updater keeps between launches: when GitHub last answered a check, the release the user said Later to, and what
 * notifications have said. Every version code is 0 for none.
 */
interface UpdateRecords {
    /** Epoch millis; 0 for never. */
    var lastCheckAt: Long

    /** The versionCode of the release the user said Later to. */
    var dismissedVersionCode: Long

    /** The release a notification last said is out (each is said once). */
    var announcedVersionCode: Long

    /**
     * The release an earlier version, which downloaded updates itself, last said was downloaded and ready to install. Nothing
     * is downloaded by the app any more; the key is kept (stored keys are never renamed) so [AppUpdater.start] can take that
     * notification down once the version it spoke of is installed.
     */
    var readyAnnouncedVersionCode: Long

    /** The release an earlier version installed without asking, so the version it brings can say it came that way (see [AppUpdater.start]). */
    var quietInstallVersionCode: Long
}

class PrefsUpdateRecords(private val prefs: SharedPreferences) : UpdateRecords {
    override var lastCheckAt: Long by long(K_LAST_CHECK)
    override var dismissedVersionCode: Long by long(K_DISMISSED)
    override var announcedVersionCode: Long by long(K_ANNOUNCED)
    override var readyAnnouncedVersionCode: Long by long(K_READY_ANNOUNCED)
    override var quietInstallVersionCode: Long by long(K_QUIET_INSTALL)

    private fun long(key: String) = object : kotlin.properties.ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Long = prefs.getLong(key, 0)

        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Long) = prefs.edit { putLong(key, value) }
    }

    private companion object {
        const val K_LAST_CHECK = "last_check_at"
        const val K_DISMISSED = "dismissed_version_code"
        const val K_ANNOUNCED = "announced_version_code"
        const val K_READY_ANNOUNCED = "ready_announced_version_code"
        const val K_QUIET_INSTALL = "quiet_install_version_code"
    }
}

class MemoryUpdateRecords(
    override var lastCheckAt: Long = 0,
    override var dismissedVersionCode: Long = 0,
    override var announcedVersionCode: Long = 0,
    override var readyAnnouncedVersionCode: Long = 0,
    override var quietInstallVersionCode: Long = 0,
) : UpdateRecords

/** The notifications updating in the background posts (see [SystemUpdateNotifier]). Each says whether it was shown. */
interface UpdateNotifier {
    /** [release] is out. */
    fun available(release: UpdateRelease): Boolean

    /** NormPix updated itself without asking (an earlier version did that), to [versionName]. */
    fun installed(versionName: String): Boolean

    /** Takes down the notification about an update waiting: it was installed or put off. */
    fun withdraw()

    /** Posts nothing (tests, and builds that don't update). */
    object None : UpdateNotifier {
        override fun available(release: UpdateRelease) = false

        override fun installed(versionName: String) = false

        override fun withdraw() = Unit
    }
}

/** How a run in the background ended: [Again] asks to be run again before the next day's (see [UpdateJobService]). */
enum class BackgroundRun { Done, Again }

/**
 * Keeps NormPix up to date from its GitHub releases, without downloading or installing anything itself. It checks as often as
 * the user chose (once a day unless they say otherwise) when they let it - in the background ([runInBackground]) and at launch -
 * or when asked in Settings, and says an update is out. To update, the user taps Download update ([openDownload]): the release's
 * APK link opens in their browser, which downloads it; they open the download, and Android's own installer asks before it
 * updates NormPix (same signing key, so the data stays).
 *
 * It lives as long as the process, so the update sheet can close and open again or the screen rotate without losing what a check
 * found. [state] is what the UI shows. Network work runs on [io]; every entry point is called on the main thread.
 */
class AppUpdater(
    val installed: InstalledApp,
    private val checker: UpdateChecker,
    private val opener: UpdateDownloadOpener,
    private val records: UpdateRecords,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    /** False for debug and qa builds: no release would install over them (see `UPDATES_ENABLED` in build.gradle.kts). */
    val enabled: Boolean = true,
    private val notifier: UpdateNotifier = UpdateNotifier.None,
    /** What each version changed, kept so it can be read offline and after an update (see [loadChangelog]). */
    private val changelogs: ChangelogStore = MemoryChangelogStore(),
    /**
     * Where earlier versions downloaded updates to (the app's cache): nothing installs from there any more, so [start] deletes
     * the folder. Null where there is none (tests).
     */
    private val oldDownloads: File? = null,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    // Loading from the start: the page asks as it opens, so it opens on "Loading", not on "nothing to show".
    private val _changelog = MutableStateFlow(ChangelogState(loading = true))

    /** What each published version changed, for the update page: empty until [loadChangelog] has looked. */
    val changelog: StateFlow<ChangelogState> = _changelog.asStateFlow()
    private var changelogLoad: Deferred<Unit>? = null

    /** When GitHub last answered a check (epoch millis), 0 for never. */
    val lastCheckAt: Long get() = records.lastCheckAt

    private val _laterVersionCode = MutableStateFlow(records.dismissedVersionCode)

    /**
     * The versionCode of the release the user said Later to (0 for none), as it changes: so Home can take down its banner for
     * the update the moment they do. Whether a release is put in front of them is [isOffered].
     */
    val laterVersionCode: StateFlow<Long> = _laterVersionCode.asStateFlow()

    /**
     * Whether [release] is to be put in front of the user without their asking - at launch, in the background, and on Home:
     * unless they said Later to it, and unless it is marked mandatory, which is only ever closed, never put off.
     */
    fun isOffered(release: UpdateRelease): Boolean = release.mandatory || release.versionCode != records.dismissedVersionCode

    private var checking: Deferred<UpdateState>? = null
    private var launchCheck: Deferred<UpdateRelease?>? = null
    private var launchOffered = false

    /**
     * At launch: deletes the folder earlier versions downloaded updates into (nothing can install from it any more), takes
     * down notifications about an update the installed version has made unnecessary, and - the first time a version that an
     * earlier one installed without asking starts - says so.
     */
    fun start() {
        oldDownloads?.let { dir -> scope.launch(io) { dir.deleteRecursively() } }
        val code = installed.versionCode
        if (records.announcedVersionCode in 1..code || records.readyAnnouncedVersionCode in 1..code) {
            notifier.withdraw()
            if (records.announcedVersionCode <= code) records.announcedVersionCode = 0
            if (records.readyAnnouncedVersionCode <= code) records.readyAnnouncedVersionCode = 0
        }
        val quiet = records.quietInstallVersionCode
        if (quiet in 1..code) {
            if (quiet == code) notifier.installed(installed.versionName)
            records.quietInstallVersionCode = 0
        }
    }

    /**
     * Once the app is unlocked at launch: the release to offer, when the user lets the app check at launch ([allowed]),
     * [intervalMs] (a day, unless the user chose a different wait) has passed since GitHub last answered, and the user hasn't
     * said Later to it (unless the release is mandatory). Each launch offers at most once. The check runs in the updater's
     * own scope, so a screen that leaves doesn't waste it.
     */
    suspend fun launchOffer(allowed: Boolean, intervalMs: Long = CHECK_INTERVAL_MS): UpdateRelease? {
        if (!enabled || !allowed || launchOffered) return null
        val check = launchCheck ?: scope.async {
            val since = now() - records.lastCheckAt
            when {
                // Found in the background while this process ran: offered without asking GitHub again.
                _state.value is UpdateState.Available -> _state.value.release
                // A clock set back (since < 0) checks, rather than waiting for a time that may never come.
                since !in 0 until intervalMs -> check().takeIf { it is UpdateState.Available }?.release
                else -> null
            }
        }.also { launchCheck = it }
        val release = check.await() ?: return null
        if (launchOffered || _state.value !is UpdateState.Available) return null
        launchOffered = true
        return release.takeIf { isOffered(it) }
    }

    /** Asks GitHub now. A check already running is joined. */
    suspend fun check(): UpdateState {
        if (!enabled) return UpdateState.Failed(UpdateProblem.Disabled).also { _state.value = it }
        val running = checking?.takeIf { it.isActive } ?: scope.async { fetch() }.also { checking = it }
        return running.await()
    }

    /**
     * Shows the changelog: what was saved from an earlier look, at once, then what GitHub says each published version changed.
     * Unlike [check] it works in builds that don't update (it only reads), and when GitHub can't be reached the saved
     * changelog stays, with the [ChangelogState.problem]. A look already under way is joined.
     */
    suspend fun loadChangelog() {
        val running = changelogLoad?.takeIf { it.isActive } ?: scope.async { fetchChangelog() }.also { changelogLoad = it }
        running.await()
    }

    private suspend fun fetchChangelog() {
        val saved = withContext(io) { changelogs.load() }
        _changelog.value = ChangelogState(saved, loading = true)
        _changelog.value = try {
            val fresh = withContext(io) { checker.history().also { if (it.isNotEmpty()) changelogs.save(it) } }
            ChangelogState(fresh.ifEmpty { saved })
        } catch (e: UpdateException) {
            ChangelogState(saved, problem = e.problem)
        } catch (e: CancellationException) {
            _changelog.value = ChangelogState(saved)
            throw e
        }
    }

    private suspend fun fetch(): UpdateState {
        val before = _state.value
        _state.value = UpdateState.Checking
        val result = try {
            val found = withContext(io) {
                val latest = checker.latest()
                // Kept for the changelog, so the notes of the version an update brings are there once it is installed.
                latest.release.let { changelogs.remember(ChangelogEntry(it.versionName, it.versionCode, it.publishedAt, it.notes)) }
                latest
            }
            when (found) {
                is ReleaseCheck.Newer -> UpdateState.Available(found.release)
                is ReleaseCheck.Current -> UpdateState.UpToDate(found.latest.versionName)
            }
        } catch (e: UpdateException) {
            UpdateState.Failed(e.problem)
        } catch (e: CancellationException) {
            _state.value = before
            throw e
        }
        // A check GitHub never heard (no connection) doesn't count: the next launch asks again.
        if (result !is UpdateState.Failed || result.problem.reachedGitHub) records.lastCheckAt = now()
        _state.value = result
        return result
    }

    /**
     * Download update: opens the release's APK link in the user's browser ([look] is how its tab looks), which downloads it. The
     * state stays as it is - the update is still there until the user installs it and the app is replaced. When it can't
     * (no browser, a link that isn't GitHub's for this app) the state is [UpdateState.Failed] with the release, which the screen
     * says in words, and Try again opens it again where that can help.
     */
    fun openDownload(look: DownloadTabLook) {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release ?: return
            else -> return
        }
        try {
            opener.open(release, look)
            // Opened at last, after a Try again: the update is what it was.
            if (_state.value is UpdateState.Failed) _state.value = UpdateState.Available(release)
        } catch (e: UpdateException) {
            _state.value = UpdateState.Failed(e.problem, release)
        }
    }

    /** Later: this release isn't offered at launch again. A newer one is, and Settings still shows this one. */
    fun later() {
        val release = _state.value.release ?: return
        if (!release.mandatory) {
            records.dismissedVersionCode = release.versionCode
            _laterVersionCode.value = release.versionCode
            notifier.withdraw()
        }
    }

    /** Asks GitHub again without waiting for it: the update page's Check for update, on "up to date". A check already running is joined. */
    fun checkAgain() {
        scope.launch { check() }
    }

    /** Try again, from wherever it stopped: the check, or opening the download ([look] as for [openDownload]). */
    fun retry(look: DownloadTabLook) {
        val current = _state.value as? UpdateState.Failed ?: return
        when {
            !current.problem.canRetry -> Unit
            current.release == null -> scope.launch { check() }
            else -> openDownload(look)
        }
    }

    // ------------------------------------------------------------ in the background

    /**
     * The check in the background (see [UpdateJobService]): asks GitHub and, when an update is out, says so in a notification
     * (once per release; the notification opens the update page, where Download update is). It downloads and installs nothing,
     * whatever the stored `background_updates` choice of an earlier version says. A release the user said Later to is left
     * alone (unless it is marked mandatory). [BackgroundRun.Again] asks to be run again soon: the network failed.
     */
    suspend fun runInBackground(): BackgroundRun {
        if (!enabled) return BackgroundRun.Done
        val found = check()
        val release = found.release ?: return again(found)
        if (isOffered(release)) announceAvailable(release)
        return BackgroundRun.Done
    }

    private fun again(state: UpdateState): BackgroundRun =
        if (state is UpdateState.Failed && state.problem.passing) BackgroundRun.Again else BackgroundRun.Done

    private fun announceAvailable(release: UpdateRelease) {
        if (records.announcedVersionCode != release.versionCode && notifier.available(release)) records.announcedVersionCode = release.versionCode
    }

    companion object {
        /** The wait between launch checks until the user chooses another (see [launchOffer]): a day. */
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        /** The updater the app runs with: GitHub over HTTPS for the check, the user's browser for the download. */
        fun forApp(context: Context, host: ActivityHost, lock: AppLockService): AppUpdater {
            val app = context.applicationContext
            val installed = InstalledApp(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList())
            val github = GitHubAccess(HttpsUpdateNetwork(), userAgent = "NormPix/${BuildConfig.VERSION_NAME}")
            return AppUpdater(
                installed = installed,
                checker = UpdateChecker(github, BuildConfig.UPDATE_REPOSITORY, installed),
                opener = BrowserDownloadOpener(app, host, lock, BuildConfig.UPDATE_REPOSITORY),
                records = PrefsUpdateRecords(app.getSharedPreferences("normpix_updates", Context.MODE_PRIVATE)),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                enabled = BuildConfig.UPDATES_ENABLED,
                notifier = SystemUpdateNotifier(app),
                changelogs = FileChangelogStore(File(app.filesDir, "changelog.json")),
                oldDownloads = File(app.cacheDir, "updates"),
            )
        }
    }
}

/** Whether GitHub heard the check at all: one that never got there doesn't count as asked. */
private val UpdateProblem.reachedGitHub: Boolean
    get() = this != UpdateProblem.Offline && this != UpdateProblem.TimedOut && this != UpdateProblem.Insecure

/** A problem of the moment (the connection), which a background run tries again soon rather than the next day. */
private val UpdateProblem.passing: Boolean
    get() = this == UpdateProblem.Offline || this == UpdateProblem.TimedOut
