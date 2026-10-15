package com.example.normpix.updates

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.example.normpix.BuildConfig
import com.example.normpix.settings.BackgroundUpdates
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.AppLockService
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * All the updater keeps between launches: when GitHub last answered a check, the release the user said Later to, and
 * what updating in the background has told the user and done. Every version code is 0 for none.
 */
interface UpdateRecords {
    /** Epoch millis; 0 for never. */
    var lastCheckAt: Long

    /** The versionCode of the release the user said Later to. */
    var dismissedVersionCode: Long

    /** The release a notification last said is out (each is said once). */
    var announcedVersionCode: Long

    /** The release a notification last said is downloaded and ready to install (each is said once). */
    var readyAnnouncedVersionCode: Long

    /** The release last installed without asking, so the version it brings can say it came that way. */
    var quietInstallVersionCode: Long

    /** The release Android wouldn't install without asking: it waits for the user's Install instead. */
    var quietFailedVersionCode: Long
}

class PrefsUpdateRecords(private val prefs: SharedPreferences) : UpdateRecords {
    override var lastCheckAt: Long by long(K_LAST_CHECK)
    override var dismissedVersionCode: Long by long(K_DISMISSED)
    override var announcedVersionCode: Long by long(K_ANNOUNCED)
    override var readyAnnouncedVersionCode: Long by long(K_READY_ANNOUNCED)
    override var quietInstallVersionCode: Long by long(K_QUIET_INSTALL)
    override var quietFailedVersionCode: Long by long(K_QUIET_FAILED)

    private fun long(key: String) = object : kotlin.properties.ReadWriteProperty<Any?, Long> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): Long = prefs.getLong(key, 0)

        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: Long) = prefs.edit().putLong(key, value).apply()
    }

    private companion object {
        const val K_LAST_CHECK = "last_check_at"
        const val K_DISMISSED = "dismissed_version_code"
        const val K_ANNOUNCED = "announced_version_code"
        const val K_READY_ANNOUNCED = "ready_announced_version_code"
        const val K_QUIET_INSTALL = "quiet_install_version_code"
        const val K_QUIET_FAILED = "quiet_failed_version_code"
    }
}

class MemoryUpdateRecords(
    override var lastCheckAt: Long = 0,
    override var dismissedVersionCode: Long = 0,
    override var announcedVersionCode: Long = 0,
    override var readyAnnouncedVersionCode: Long = 0,
    override var quietInstallVersionCode: Long = 0,
    override var quietFailedVersionCode: Long = 0,
) : UpdateRecords

/** The notifications updating in the background posts (see [SystemUpdateNotifier]). Each says whether it was shown. */
interface UpdateNotifier {
    /** [release] is out. */
    fun available(release: UpdateRelease): Boolean

    /** [release] is downloaded and checked, and waits for the user's Install. */
    fun ready(release: UpdateRelease): Boolean

    /** NormPix updated itself without asking, to [versionName]. */
    fun installed(versionName: String): Boolean

    /** Takes down the notification about an update waiting: it was installed, put off, or is being installed. */
    fun withdraw()

    /** Posts nothing (tests, and builds that don't update). */
    object None : UpdateNotifier {
        override fun available(release: UpdateRelease) = false

        override fun ready(release: UpdateRelease) = false

        override fun installed(versionName: String) = false

        override fun withdraw() = Unit
    }
}

/** How a run in the background ended: [Again] asks to be run again before the next day's (see [UpdateJobService]). */
enum class BackgroundRun { Done, Again }

/**
 * Keeps NormPix up to date from its GitHub releases. It checks once a day when the user lets it - in the background
 * ([runInBackground]) and at launch - or when asked in Settings. In the app, Update now downloads and checks the APK, and
 * only Install hands it to Android's installer, which asks the user to confirm. In the background it goes as far as the
 * user chose ([BackgroundUpdates]): saying an update is out, downloading it, or installing it without asking.
 *
 * It lives as long as the process, so the update sheet can close and open again, the screen rotate or the app go to the
 * background without losing a download or an install under way. [state] is what the UI shows. Network and file work run
 * on [io]; every entry point is called on the main thread.
 */
class AppUpdater(
    val installed: InstalledApp,
    private val checker: UpdateChecker,
    private val downloads: UpdateDownloader,
    private val inspector: ApkInspector,
    private val installer: UpdateInstaller,
    private val records: UpdateRecords,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    /** False for debug and qa builds: no release would install over them (see `UPDATES_ENABLED` in build.gradle.kts). */
    val enabled: Boolean = true,
    private val notifier: UpdateNotifier = UpdateNotifier.None,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _downloaded = Channel<UpdateRelease>(Channel.CONFLATED)

    /**
     * Each release whose download has just finished and passed its checks, now waiting for the user's Install - so the UI
     * can say so even when the sheet was closed while it downloaded. Kept until collected, the latest only.
     */
    val downloaded: Flow<UpdateRelease> = _downloaded.receiveAsFlow()

    /** When GitHub last answered a check (epoch millis), 0 for never. */
    val lastCheckAt: Long get() = records.lastCheckAt

    private var checking: Deferred<UpdateState>? = null
    private var launchCheck: Deferred<UpdateRelease?>? = null
    private var launchOffered = false
    private var downloading: Job? = null

    /** The download under way was started by [runInBackground], not by the user. */
    private var backgroundDownload = false

    /** The install handed to Android's installer, set from [io] as it starts. */
    @Volatile
    private var session: Int? = null
    private var waitingForConfirm: Intent? = null
    private var waitingForPermission = false
    private var inForeground = false

    /** When NormPix last left the screen (epoch millis), null while it is on screen or hasn't been in this process. */
    private var backgroundSince: Long? = null

    /** The install under way ([session]) was asked of Android without asking the user. */
    private var quietSession = false

    /**
     * At launch: deletes downloads the installed version has made unnecessary (the update just installed, or an older one),
     * takes down notifications about them, and - the first time a version installed without asking starts - says so.
     */
    fun start() {
        scope.launch(io) { downloads.clearInstalled(installed.versionCode) }
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
     * Once the app is unlocked at launch: the release to offer, when the user lets the app check ([allowed]), a day has
     * passed since GitHub last answered, and the user hasn't said Later to it (unless the release is mandatory). Each
     * launch offers at most once. The check runs in the updater's own scope, so a screen that leaves doesn't waste it.
     */
    suspend fun launchOffer(allowed: Boolean): UpdateRelease? {
        if (!enabled || !allowed || launchOffered) return null
        val check = launchCheck ?: scope.async {
            val since = now() - records.lastCheckAt
            when {
                // Found in the background while this process ran: offered without asking GitHub again.
                _state.value.offerable -> _state.value.release
                // A clock set back (since < 0) checks, rather than waiting for a day that may never come. A download made
                // in the background is waiting: asked again (it is quick), so it is offered ready to install.
                since !in 0 until CHECK_INTERVAL_MS || withContext(io) { downloads.holdsNewerThan(installed.versionCode) } ->
                    check().takeIf { it.offerable }?.release
                else -> null
            }
        }.also { launchCheck = it }
        val release = check.await() ?: return null
        if (launchOffered || !_state.value.offerable) return null
        launchOffered = true
        return release.takeIf { it.mandatory || it.versionCode != records.dismissedVersionCode }
    }

    /** A state the launch offer shows: an update to download, or one downloaded and waiting for Install. */
    private val UpdateState.offerable: Boolean get() = this is UpdateState.Available || this is UpdateState.Ready

    /** Asks GitHub now. A download or an install under way stands as it is, and a check already running is joined. */
    suspend fun check(): UpdateState {
        if (!enabled) return UpdateState.Failed(UpdateProblem.Disabled).also { _state.value = it }
        when (val current = _state.value) {
            is UpdateState.Downloading, is UpdateState.Installing -> return current
            else -> Unit
        }
        val running = checking?.takeIf { it.isActive } ?: scope.async { fetch() }.also { checking = it }
        return running.await()
    }

    private suspend fun fetch(): UpdateState {
        val before = _state.value
        _state.value = UpdateState.Checking
        val result = try {
            val (found, onDisk) = withContext(io) {
                val latest = checker.latest()
                latest to (latest as? ReleaseCheck.Newer)?.let { kept(it.release) }
            }
            when (found) {
                is ReleaseCheck.Newer -> when {
                    // A release already downloaded (or further along) stays where it is.
                    before.release == found.release && before !is UpdateState.Failed -> before
                    // Downloaded earlier (in the background, or before NormPix was closed): ready to install.
                    onDisk != null -> UpdateState.Ready(found.release, onDisk)
                    else -> UpdateState.Available(found.release)
                }
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

    /** [release]'s APK when an earlier download holds it and it would still install over this version; null otherwise. Blocking. */
    private fun kept(release: UpdateRelease): File? {
        val apk = downloads.existing(release) ?: return null
        if (apkProblem(inspector.inspect(apk), release, installed, inspector.installedSigners()) == null) return apk
        apk.delete()
        return null
    }

    /**
     * Update now: downloads the release and checks the file, then waits at [UpdateState.Ready] for the user's Install.
     * [thenInstall] is for an Install that found its download gone: the user has already asked for it, so it goes on.
     */
    fun download(thenInstall: Boolean = false) {
        val release = _state.value.release ?: return
        startDownload(release, thenInstall, background = false)
    }

    /**
     * Downloads [release] (or joins the download under way). One the user started says it is ready, so the update sheet can
     * come back for it; one the [background] started leaves that to its notification, and if it is stopped (the network
     * went, or Android's time for it ran out) the update simply waits to be downloaded again.
     */
    private fun startDownload(release: UpdateRelease, thenInstall: Boolean, background: Boolean): Job {
        downloading?.takeIf { it.isActive }?.let { running ->
            // The user asking for a download the background started makes it theirs.
            if (!background) backgroundDownload = false
            return running
        }
        backgroundDownload = background
        return scope.launch {
            _state.value = UpdateState.Downloading(release, 0, release.size)
            val apk = try {
                withContext(io) {
                    var shown = -1L
                    val apk = downloads.download(release) { done, total ->
                        // A step per percent: enough for the bar, without a state for every chunk read.
                        val percent = if (total > 0) done * 100 / total else 0
                        if (percent != shown) {
                            shown = percent
                            _state.value = UpdateState.Downloading(release, done, total)
                        }
                    }
                    apkProblem(inspector.inspect(apk), release, installed, inspector.installedSigners())?.let { problem ->
                        downloads.clear()
                        throw UpdateException(problem)
                    }
                    apk
                }
            } catch (e: UpdateException) {
                _state.value = UpdateState.Failed(e.problem, release)
                return@launch
            } catch (e: CancellationException) {
                _state.value = if (backgroundDownload) UpdateState.Available(release) else UpdateState.Cancelled(release, null)
                throw e
            }
            _state.value = UpdateState.Ready(release, apk)
            when {
                thenInstall -> install()
                !backgroundDownload -> _downloaded.trySend(release)
            }
        }.also { downloading = it }
    }

    fun cancelDownload() {
        // Stopped by the user, whoever started it: it says so.
        backgroundDownload = false
        downloading?.cancel()
    }

    /** Hands the downloaded update to Android's installer, first asking for the user's leave where Android needs it. */
    fun install() {
        val (release, apk) = when (val current = _state.value) {
            is UpdateState.Ready -> current.release to current.apk
            is UpdateState.NeedsPermission -> current.release to current.apk
            is UpdateState.Cancelled -> current.release to (current.apk ?: return download(thenInstall = true))
            is UpdateState.Failed -> (current.release ?: return) to (current.apk ?: return download(thenInstall = true))
            else -> return
        }
        // The system may have cleared the cache since: download it again.
        if (!apk.isFile) {
            _state.value = UpdateState.Available(release)
            return download(thenInstall = true)
        }
        if (!installer.canInstall()) {
            _state.value = UpdateState.NeedsPermission(release, apk)
            return
        }
        notifier.withdraw()
        quietSession = false
        _state.value = UpdateState.Installing(release, apk)
        scope.launch {
            try {
                // The id is known before the session is committed: the installer may answer before this returns.
                withContext(io) { installer.install(apk, release) { id -> session = id } }
            } catch (e: UpdateException) {
                session = null
                val keep = e.problem.canRetry && e.problem != UpdateProblem.ChecksumMismatch
                if (!keep) downloads.clear()
                _state.value = UpdateState.Failed(e.problem, release, apk.takeIf { keep })
            }
        }
    }

    /** Opens Android's "Install unknown apps" setting at NormPix; the update carries on once the user comes back with it allowed. */
    fun allowInstalls() {
        val current = _state.value as? UpdateState.NeedsPermission ?: return
        waitingForPermission = installer.openInstallSetting()
        if (!waitingForPermission) _state.value = UpdateState.Failed(UpdateProblem.InstallBlocked, current.release, current.apk)
    }

    /** Whether Android lets NormPix install updates (its "Install unknown apps" leave), which installing automatically needs. */
    val installsAllowed: Boolean get() = installer.canInstall()

    /** Whether this Android can install an update without asking (Android 12 and later). */
    val installsQuietly: Boolean get() = installer.installsQuietly

    /** Opens Android's "Install unknown apps" setting at NormPix, from Settings; false when no screen could show it. */
    fun openInstallSetting(): Boolean = installer.openInstallSetting()

    /** Later: this release isn't offered at launch again. A newer one is, and Settings still shows this one. */
    fun later() {
        val release = _state.value.release ?: return
        if (!release.mandatory) {
            records.dismissedVersionCode = release.versionCode
            notifier.withdraw()
        }
    }

    /** Try again, from wherever it stopped: the check, the download, or the install. */
    fun retry() {
        when (val current = _state.value) {
            is UpdateState.Failed -> when {
                !current.problem.canRetry -> Unit
                current.release == null -> scope.launch { check() }
                current.apk != null -> install()
                else -> download()
            }
            is UpdateState.Cancelled -> if (current.apk != null) install() else download()
            else -> Unit
        }
    }

    /** Android's installer needs the user to confirm install [id]: shown now when NormPix is on screen, or as soon as it is again. */
    fun onInstallNeedsUser(id: Int, confirm: Intent?) {
        if (id != session) return
        if (quietSession) {
            // Android wants the user to confirm an install meant to happen without them: it is left for their Install.
            installer.abandon(id)
            session = null
            quietSession = false
            val current = _state.value as? UpdateState.Installing ?: return
            return quietInstallRefused(current.release, current.apk)
        }
        if (confirm == null) return onInstallFinished(id, InstallOutcome.Failed(UpdateProblem.InstallFailed(null)))
        waitingForConfirm = if (inForeground && installer.confirm(confirm)) null else confirm
    }

    fun onInstallFinished(id: Int, outcome: InstallOutcome) {
        if (id != session) return
        session = null
        waitingForConfirm = null
        val quiet = quietSession
        quietSession = false
        val current = _state.value as? UpdateState.Installing ?: return
        // An install without asking that didn't happen, but could with the user's say-so: left for their Install.
        if (quiet && outcome != InstallOutcome.Success && (outcome !is InstallOutcome.Failed || outcome.problem.canRetry)) {
            return quietInstallRefused(current.release, current.apk)
        }
        // A successful one keeps its record: the version it brings says it came that way (see [start]).
        if (quiet && outcome != InstallOutcome.Success) records.quietInstallVersionCode = 0
        _state.value = when (outcome) {
            // Rarely heard: Android usually replaces the app (and ends this process) first.
            InstallOutcome.Success -> UpdateState.UpToDate(current.release.versionName).also { downloads.clear() }
            InstallOutcome.Cancelled -> UpdateState.Cancelled(current.release, current.apk)
            is InstallOutcome.Failed -> {
                val keep = outcome.problem.canRetry
                if (!keep) downloads.clear()
                UpdateState.Failed(outcome.problem, current.release, current.apk.takeIf { keep })
            }
        }
    }

    /** NormPix is on screen again (MainActivity resumed): carries on with whatever was waiting for the user's return. */
    fun onForeground() {
        inForeground = true
        backgroundSince = null
        waitingForConfirm?.let { if (installer.confirm(it)) waitingForConfirm = null }
        when (val current = _state.value) {
            is UpdateState.NeedsPermission -> if (waitingForPermission && installer.canInstall()) {
                waitingForPermission = false
                install()
            }
            // Some Android versions say nothing when the user backs out of the confirmation. An install no longer under
            // way, with no word from the installer once it has had a moment to speak, was turned down.
            is UpdateState.Installing -> {
                val id = session ?: return
                if (waitingForConfirm != null || quietSession) return
                scope.launch {
                    delay(CONFIRM_SETTLE_MS)
                    if (session == id && _state.value == current && !withContext(io) { installer.isActive(id) }) {
                        onInstallFinished(id, InstallOutcome.Cancelled)
                    }
                }
            }
            else -> Unit
        }
    }

    fun onBackground() {
        inForeground = false
        backgroundSince = now()
    }

    // ------------------------------------------------------------ in the background

    /**
     * The daily check in the background (see [UpdateJobService]), going as far as the user chose with what it finds:
     * [BackgroundUpdates.Notify] says it is out; [BackgroundUpdates.Download] downloads it and says it is ready to install;
     * [BackgroundUpdates.Install] downloads it and installs it without asking, where Android allows that, once NormPix has
     * been off screen a while - and where Android won't, leaves it ready for the user's Install like Download does.
     *
     * A release the user said Later to is left alone (unless it is marked mandatory), and so is anything the user has under
     * way in the app. Each notification is posted once per release. [BackgroundRun.Again] asks to be run again soon: the
     * network failed, the download was stopped, or an install without asking waits for NormPix to be closed.
     */
    suspend fun runInBackground(mode: BackgroundUpdates): BackgroundRun {
        if (!enabled) return BackgroundRun.Done
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Installing, is UpdateState.NeedsPermission -> return BackgroundRun.Done
            else -> Unit
        }
        val found = check()
        val release = found.release ?: return again(found)
        if (!release.mandatory && release.versionCode == records.dismissedVersionCode) return BackgroundRun.Done
        if (mode == BackgroundUpdates.Notify) {
            announceAvailable(release)
            return BackgroundRun.Done
        }
        val before = _state.value
        if (before is UpdateState.Available || before is UpdateState.Cancelled && before.apk == null) {
            startDownload(release, thenInstall = false, background = true).join()
        }
        val apk = when (val current = _state.value) {
            is UpdateState.Ready -> current.apk
            is UpdateState.Cancelled -> current.apk ?: return BackgroundRun.Done
            // Stopped part way: it is downloaded again on the next run.
            is UpdateState.Available -> return BackgroundRun.Again
            else -> return again(current)
        }
        // The user turned down installing this one in the app: not installed behind their back, only waiting for them.
        val declined = _state.value is UpdateState.Cancelled
        val quietly = mode == BackgroundUpdates.Install && !declined && records.quietFailedVersionCode != release.versionCode &&
            installer.installsQuietly && installer.canInstall()
        if (!quietly) {
            announceReady(release)
            return BackgroundRun.Done
        }
        // Not while NormPix is in use, nor just after: an update closes the app, and whatever was being done in it.
        val away = backgroundSince
        if (inForeground || away != null && now() - away < QUIET_AFTER_MS) return BackgroundRun.Again
        installQuietly(release, apk)
        return BackgroundRun.Done
    }

    /** Android stopped the background run (its time ran out, or the network it needed went): a download it started stops too. */
    fun stopBackgroundWork() {
        if (backgroundDownload && !inForeground) downloading?.cancel()
    }

    private fun again(state: UpdateState): BackgroundRun =
        if (state is UpdateState.Failed && state.problem.passing) BackgroundRun.Again else BackgroundRun.Done

    private fun announceAvailable(release: UpdateRelease) {
        if (records.announcedVersionCode != release.versionCode && notifier.available(release)) records.announcedVersionCode = release.versionCode
    }

    private fun announceReady(release: UpdateRelease) {
        if (records.readyAnnouncedVersionCode != release.versionCode && notifier.ready(release)) {
            records.readyAnnouncedVersionCode = release.versionCode
        }
    }

    /** Hands [apk] to Android to install without asking, and waits (a while) for its word: it may well end this process. */
    private suspend fun installQuietly(release: UpdateRelease, apk: File) {
        notifier.withdraw()
        quietSession = true
        records.quietInstallVersionCode = release.versionCode
        _state.value = UpdateState.Installing(release, apk)
        try {
            withContext(io) { installer.install(apk, release, quiet = true) { id -> session = id } }
        } catch (_: UpdateException) {
            session = null
            quietSession = false
            return quietInstallRefused(release, apk)
        }
        withTimeoutOrNull(QUIET_WAIT_MS) { state.first { it !is UpdateState.Installing } }
    }

    /** An install without asking that Android wouldn't do: the update waits for the user's Install, and a notification says so. */
    private fun quietInstallRefused(release: UpdateRelease, apk: File) {
        records.quietInstallVersionCode = 0
        records.quietFailedVersionCode = release.versionCode
        _state.value = UpdateState.Ready(release, apk)
        announceReady(release)
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val CONFIRM_SETTLE_MS = 2_000L

        /** How long NormPix has to have been off screen before an update is installed without asking. */
        const val QUIET_AFTER_MS = 10L * 60 * 1000

        /** How long a background run waits for Android's word on an install without asking. */
        const val QUIET_WAIT_MS = 2L * 60 * 1000

        /** The updater the app runs with: GitHub over HTTPS, the cache for downloads, Android's own installer. */
        fun forApp(context: Context, host: ActivityHost, lock: AppLockService): AppUpdater {
            val app = context.applicationContext
            val installed = InstalledApp(BuildConfig.APPLICATION_ID, BuildConfig.VERSION_CODE.toLong(), BuildConfig.VERSION_NAME, Build.VERSION.SDK_INT)
            val github = GitHubAccess(HttpsUpdateNetwork(), userAgent = "NormPix/${BuildConfig.VERSION_NAME}")
            return AppUpdater(
                installed = installed,
                checker = UpdateChecker(github, BuildConfig.UPDATE_REPOSITORY, installed),
                downloads = UpdateDownloader(github, File(app.cacheDir, "updates")),
                inspector = PackageManagerApkInspector(app),
                installer = SessionUpdateInstaller(app, host, lock),
                records = PrefsUpdateRecords(app.getSharedPreferences("normpix_updates", Context.MODE_PRIVATE)),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                enabled = BuildConfig.UPDATES_ENABLED,
                notifier = SystemUpdateNotifier(app),
            )
        }
    }
}

/** Whether GitHub heard the check at all: one that never got there doesn't count as asked. */
private val UpdateProblem.reachedGitHub: Boolean
    get() = this != UpdateProblem.Offline && this != UpdateProblem.TimedOut && this != UpdateProblem.Insecure

/** A problem of the moment (the connection), which a background run tries again soon rather than the next day. */
private val UpdateProblem.passing: Boolean
    get() = this == UpdateProblem.Offline || this == UpdateProblem.TimedOut || this == UpdateProblem.Interrupted
