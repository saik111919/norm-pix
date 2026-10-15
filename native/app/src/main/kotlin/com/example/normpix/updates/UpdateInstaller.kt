package com.example.normpix.updates

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import com.example.normpix.NormPixApplication
import com.example.normpix.wallet.ActivityHost
import com.example.normpix.wallet.AppLockService
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** What an APK file says it is. [signers] are its signing certificates' SHA-256s (past ones too), null where Android can't say. */
data class ApkFacts(val packageName: String, val versionCode: Long, val signers: Set<String>?)

/** Reads an APK, and the installed app's own signing certificates, the way Android's package manager sees them. */
interface ApkInspector {
    /** What [apk] is, or null when Android can't read it as an app. */
    fun inspect(apk: File): ApkFacts?

    /** The installed app's current signing certificates (SHA-256), or null where Android can't say. */
    fun installedSigners(): Set<String>?
}

/**
 * Why Android would refuse [facts] as [release] over [installed], or null when it should take it. Signatures are only
 * compared where both are known (Android 9 and later); elsewhere the installer itself refuses a mismatch, and
 * [installOutcome] says so.
 */
fun apkProblem(facts: ApkFacts?, release: UpdateRelease, installed: InstalledApp, installedSigners: Set<String>?): UpdateProblem? = when {
    facts == null -> UpdateProblem.NotAnApk
    facts.packageName != installed.applicationId -> UpdateProblem.WrongPackage(facts.packageName)
    facts.versionCode <= installed.versionCode -> UpdateProblem.NotNewer(facts.versionCode)
    facts.versionCode != release.versionCode ->
        UpdateProblem.BadRelease("the APK is version ${facts.versionCode}, the release says ${release.versionCode}")
    facts.signers != null && installedSigners != null && facts.signers.none { it in installedSigners } -> UpdateProblem.SignatureMismatch
    else -> null
}

class PackageManagerApkInspector(private val context: Context) : ApkInspector {
    private val packages: PackageManager get() = context.packageManager

    override fun inspect(apk: File): ApkFacts? {
        val info = archiveInfo(apk) ?: return null
        return ApkFacts(info.packageName, PackageInfoCompat.getLongVersionCode(info), signersOf(info, past = true))
    }

    override fun installedSigners(): Set<String>? = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packages.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(SIGNING_FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packages.getPackageInfo(context.packageName, SIGNING_FLAGS)
        }
        signersOf(info, past = false)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun archiveInfo(apk: File): PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packages.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(SIGNING_FLAGS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        packages.getPackageArchiveInfo(apk.path, SIGNING_FLAGS)
    }

    private fun signersOf(info: PackageInfo, past: Boolean): Set<String>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val signing = info.signingInfo ?: return null
        val current = signing.apkContentsSigners.orEmpty().toList()
        // A key rotated since the installed version still vouches for the old one in its history.
        val history = if (past && !signing.hasMultipleSigners()) signing.signingCertificateHistory.orEmpty().toList() else emptyList()
        return (current + history).map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet().ifEmpty { null }
    }

    private companion object {
        val SIGNING_FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
    }
}

/** How Android's installer said an install ended. */
sealed interface InstallOutcome {
    data object Success : InstallOutcome

    /** The user turned it down. */
    data object Cancelled : InstallOutcome

    data class Failed(val problem: UpdateProblem) : InstallOutcome
}

/** What a [PackageInstaller] status (and its message, which names the reason behind a few of them) means. */
fun installOutcome(status: Int, message: String?): InstallOutcome {
    val said = message.orEmpty()
    // Android words a key mismatch differently from version to version, under more than one status.
    val otherKey = listOf("UPDATE_INCOMPATIBLE", "signatures do not match", "INCONSISTENT_CERTIFICATES").any { said.contains(it, ignoreCase = true) }
    val older = said.contains("DOWNGRADE", ignoreCase = true)
    return when (status) {
        PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Cancelled
        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallOutcome.Failed(UpdateProblem.InstallBlocked)
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallOutcome.Failed(UpdateProblem.InstallStorage)
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, PackageInstaller.STATUS_FAILURE_INVALID -> when {
            otherKey -> InstallOutcome.Failed(UpdateProblem.SignatureMismatch)
            older -> InstallOutcome.Failed(UpdateProblem.NotNewer(0))
            status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallOutcome.Failed(UpdateProblem.InstallIncompatible)
            status == PackageInstaller.STATUS_FAILURE_INVALID -> InstallOutcome.Failed(UpdateProblem.NotAnApk)
            else -> InstallOutcome.Failed(UpdateProblem.InstallFailed(message))
        }
        else -> InstallOutcome.Failed(UpdateProblem.InstallFailed(message))
    }
}

/** Hands an APK to Android's installer, which asks the user; the outcome comes back to [AppUpdater] (see [UpdateInstallReceiver]). */
interface UpdateInstaller {
    /** Whether Android lets NormPix ask it to install apps (Settings > Apps > Special app access > Install unknown apps). */
    fun canInstall(): Boolean

    /** Opens that setting at NormPix's entry; false when no screen could show it. */
    fun openInstallSetting(): Boolean

    /**
     * Starts installing [apk], checking it against [release]'s SHA-256 once more as it is handed over. [started] gets the
     * install's id before Android can report on it.
     */
    fun install(apk: File, release: UpdateRelease, started: (id: Int) -> Unit)

    /** Shows the confirmation Android's installer asks for; false when it couldn't be shown. */
    fun confirm(intent: Intent): Boolean

    /** Whether install [id] is still under way (waiting for the user, or installing). */
    fun isActive(id: Int): Boolean
}

/**
 * [UpdateInstaller] through a [PackageInstaller] session: the update is streamed into it (no file is shared with another
 * app, so no FileProvider is needed), and Android shows its own confirmation before anything is installed. The app is
 * never uninstalled: an update Android won't put over it fails and says why.
 */
class SessionUpdateInstaller(
    private val context: Context,
    private val host: ActivityHost,
    private val lock: AppLockService,
) : UpdateInstaller {
    private val sessions: PackageInstaller get() = context.packageManager.packageInstaller

    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    override fun openInstallSetting(): Boolean =
        show(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))

    override fun install(apk: File, release: UpdateRelease, started: (id: Int) -> Unit) {
        // An install left from an earlier try (turned down, or the app closed under it) goes: only the newest is committed.
        sessions.mySessions.forEach { runCatching { sessions.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // The user confirms every update. NormPix never installs one quietly, even where Android would let it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = try {
            sessions.createSession(params)
        } catch (e: IOException) {
            throw UpdateException(UpdateProblem.InstallStorage, e)
        }
        started(id)
        try {
            sessions.openSession(id).use { session ->
                val digest = MessageDigest.getInstance("SHA-256")
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                        }
                        session.fsync(out)
                    }
                }
                // The file sat in the cache since it was downloaded: it is still the release, or it isn't installed.
                if (digest.digest().toHex() != release.sha256) throw UpdateException(UpdateProblem.ChecksumMismatch)
                session.commit(statusReceiver(id).intentSender)
            }
        } catch (e: Exception) {
            runCatching { sessions.abandonSession(id) }
            throw when (e) {
                is UpdateException -> e
                is IOException -> UpdateException(UpdateProblem.InstallFailed(e.message), e)
                is SecurityException -> UpdateException(UpdateProblem.InstallBlocked, e)
                else -> UpdateException(UpdateProblem.InstallFailed(e.message), e)
            }
        }
    }

    override fun confirm(intent: Intent): Boolean = show(intent)

    override fun isActive(id: Int): Boolean = sessions.getSessionInfo(id) != null

    /** Opens another app's screen from NormPix's own, without coming back to it counting as leaving the app (see [AppLockService]). */
    private fun show(intent: Intent): Boolean {
        val activity = host.activity ?: return false
        lock.expectExternalActivity()
        return try {
            activity.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun statusReceiver(id: Int): PendingIntent {
        val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
        // Mutable: the installer adds the status to it. Safe, as it names its receiver.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }
}

/**
 * Where Android's installer reports on an update NormPix handed it: that the user has to confirm it, or how it ended. Not
 * exported: only the installer, through the app's own PendingIntent, can send it anything.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = (context.applicationContext as? NormPixApplication)?.container?.updates ?: return
        deliverInstallStatus(updater, intent)
    }
}

/** Passes what Android's installer said in [intent] (a [PackageInstaller] status broadcast) on to [updater]. */
fun deliverInstallStatus(updater: AppUpdater, intent: Intent) {
    val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
    when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
        PackageInstaller.STATUS_PENDING_USER_ACTION ->
            updater.onInstallNeedsUser(id, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java))
        else -> updater.onInstallFinished(id, installOutcome(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)))
    }
}
