package com.example.normpix.updates

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.example.normpix.BuildConfig
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** All the updater keeps between launches: when GitHub last answered a check, and the release the user said Later to. */
interface UpdateRecords {
    /** Epoch millis; 0 for never. */
    var lastCheckAt: Long

    /** The versionCode of the release the user said Later to; 0 for none. */
    var dismissedVersionCode: Long
}

class PrefsUpdateRecords(private val prefs: SharedPreferences) : UpdateRecords {
    override var lastCheckAt: Long
        get() = prefs.getLong(K_LAST_CHECK, 0)
        set(value) = prefs.edit().putLong(K_LAST_CHECK, value).apply()

    override var dismissedVersionCode: Long
        get() = prefs.getLong(K_DISMISSED, 0)
        set(value) = prefs.edit().putLong(K_DISMISSED, value).apply()

    private companion object {
        const val K_LAST_CHECK = "last_check_at"
        const val K_DISMISSED = "dismissed_version_code"
    }
}

class MemoryUpdateRecords(override var lastCheckAt: Long = 0, override var dismissedVersionCode: Long = 0) : UpdateRecords

/**
 * Keeps NormPix up to date from its GitHub releases. It checks at launch (at most once a day, when the user lets it) or
 * when asked in Settings. Then it downloads and checks the APK, and hands it to Android's installer, which asks the user.
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
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** When GitHub last answered a check (epoch millis), 0 for never. */
    val lastCheckAt: Long get() = records.lastCheckAt

    private var checking: Deferred<UpdateState>? = null
    private var launchCheck: Deferred<UpdateRelease?>? = null
    private var launchOffered = false
    private var downloading: Job? = null

    /** The install handed to Android's installer, set from [io] as it starts. */
    @Volatile
    private var session: Int? = null
    private var waitingForConfirm: Intent? = null
    private var waitingForPermission = false
    private var inForeground = false

    /** At launch: deletes downloads the installed version has made unnecessary (the update just installed, or an older one). */
    fun start() {
        scope.launch(io) { downloads.clearInstalled(installed.versionCode) }
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
            // A clock set back (since < 0) checks, rather than waiting for a day that may never come.
            if (since in 0 until CHECK_INTERVAL_MS) null else (check() as? UpdateState.Available)?.release
        }.also { launchCheck = it }
        val release = check.await() ?: return null
        if (launchOffered || _state.value !is UpdateState.Available) return null
        launchOffered = true
        return release.takeIf { it.mandatory || it.versionCode != records.dismissedVersionCode }
    }

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
            when (val found = withContext(io) { checker.latest() }) {
                // A release already downloaded (or further along) stays where it is.
                is ReleaseCheck.Newer -> if (before.release == found.release && before !is UpdateState.Failed) before else UpdateState.Available(found.release)
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

    /** Update now: downloads the release, checks the file, and hands it to Android's installer. */
    fun download() {
        val release = _state.value.release ?: return
        if (downloading?.isActive == true) return
        downloading = scope.launch {
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
                _state.value = UpdateState.Cancelled(release, null)
                throw e
            }
            _state.value = UpdateState.Ready(release, apk)
            install()
        }
    }

    fun cancelDownload() {
        downloading?.cancel()
    }

    /** Hands the downloaded update to Android's installer, first asking for the user's leave where Android needs it. */
    fun install() {
        val (release, apk) = when (val current = _state.value) {
            is UpdateState.Ready -> current.release to current.apk
            is UpdateState.NeedsPermission -> current.release to current.apk
            is UpdateState.Cancelled -> current.release to (current.apk ?: return download())
            is UpdateState.Failed -> (current.release ?: return) to (current.apk ?: return download())
            else -> return
        }
        // The system may have cleared the cache since: download it again.
        if (!apk.isFile) {
            _state.value = UpdateState.Available(release)
            return download()
        }
        if (!installer.canInstall()) {
            _state.value = UpdateState.NeedsPermission(release, apk)
            return
        }
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

    /** Later: this release isn't offered at launch again. A newer one is, and Settings still shows this one. */
    fun later() {
        val release = _state.value.release ?: return
        if (!release.mandatory) records.dismissedVersionCode = release.versionCode
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
        if (confirm == null) return onInstallFinished(id, InstallOutcome.Failed(UpdateProblem.InstallFailed(null)))
        waitingForConfirm = if (inForeground && installer.confirm(confirm)) null else confirm
    }

    fun onInstallFinished(id: Int, outcome: InstallOutcome) {
        if (id != session) return
        session = null
        waitingForConfirm = null
        val current = _state.value as? UpdateState.Installing ?: return
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
                if (waitingForConfirm != null) return
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
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
        const val CONFIRM_SETTLE_MS = 2_000L

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
            )
        }
    }
}

/** Whether GitHub heard the check at all: one that never got there doesn't count as asked. */
private val UpdateProblem.reachedGitHub: Boolean
    get() = this != UpdateProblem.Offline && this != UpdateProblem.TimedOut && this != UpdateProblem.Insecure
