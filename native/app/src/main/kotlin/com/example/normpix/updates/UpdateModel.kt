package com.example.normpix.updates

import java.io.File
import java.time.Instant

/** The app as installed: what an update has to be for, and what it has to be newer than. */
data class InstalledApp(
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    /** The phone's Android version (API level), which a release may need to be at least. */
    val sdk: Int,
)

/** A release the app can update to: what its `update.json` says, checked against its GitHub release (see [ReleaseMetadata]). */
data class UpdateRelease(
    val versionCode: Long,
    val versionName: String,
    val tag: String,
    val publishedAt: Instant?,
    /** What changed, as plain text; may be empty. */
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    /** The APK's exact size in bytes. */
    val size: Long,
    /** The APK's SHA-256, lowercase hex. */
    val sha256: String,
    /** Set by the release itself: offered on every launch, even after Later. Never forced on anyone. */
    val mandatory: Boolean,
)

/**
 * Why checking, downloading or installing an update didn't work. Each says something the user can act on (see
 * `describe` in the update UI); [canRetry] is false where trying again can't help.
 */
sealed interface UpdateProblem {
    val canRetry: Boolean get() = true

    /** A debug or qa build: no release can install over it. */
    data object Disabled : UpdateProblem {
        override val canRetry get() = false
    }

    /** No connection, or GitHub couldn't be reached. */
    data object Offline : UpdateProblem

    data object TimedOut : UpdateProblem

    /** GitHub limits how often a network may ask without signing in; [until] is when it may ask again, when GitHub said. */
    data class RateLimited(val until: Instant?) : UpdateProblem

    /** No release is published where the app looks (or the repository isn't public). */
    data object NoRelease : UpdateProblem

    data class ServerError(val code: Int) : UpdateProblem

    /** A redirect away from GitHub, or off HTTPS: refused. */
    data object Untrusted : UpdateProblem

    /** The secure connection failed (a bad certificate, or something in the way). */
    data object Insecure : UpdateProblem

    /** The release's details are missing, malformed or contradict each other: it can't be installed from the app. */
    data class BadRelease(val detail: String) : UpdateProblem

    /** The release is published for another app (another package name). */
    data class OtherApp(val applicationId: String) : UpdateProblem {
        override val canRetry get() = false
    }

    /** The release needs a newer Android than the phone has. */
    data class NeedsAndroid(val minSdk: Int) : UpdateProblem {
        override val canRetry get() = false
    }

    /** No room for the download, or it couldn't be saved. */
    data object Storage : UpdateProblem

    /** The download stopped before all of it came. */
    data object Interrupted : UpdateProblem

    /** The downloaded file isn't the one the release describes. It was deleted. */
    data object ChecksumMismatch : UpdateProblem

    /** The downloaded file isn't an app Android can read. */
    data object NotAnApk : UpdateProblem

    /** The downloaded app is another app (another package name). */
    data class WrongPackage(val packageName: String) : UpdateProblem {
        override val canRetry get() = false
    }

    /** The downloaded app isn't newer than the one installed. */
    data class NotNewer(val versionCode: Long) : UpdateProblem {
        override val canRetry get() = false
    }

    /** The update is signed with another key than the installed app, so Android won't install it over it. */
    data object SignatureMismatch : UpdateProblem {
        override val canRetry get() = false
    }

    /** A device policy or a setting blocked the install. */
    data object InstallBlocked : UpdateProblem

    data object InstallStorage : UpdateProblem

    data object InstallIncompatible : UpdateProblem {
        override val canRetry get() = false
    }

    /** Android's installer failed for a reason of its own; [detail] is what it said, for the curious. */
    data class InstallFailed(val detail: String?) : UpdateProblem
}

/** An [UpdateProblem] on its way up from the network, the file system or the installer. */
class UpdateException(val problem: UpdateProblem, cause: Throwable? = null) : Exception(problem.toString(), cause)

/**
 * Where updating stands, for the update sheet and Settings. Held by [AppUpdater] for the whole process, so closing the
 * sheet, rotating, or leaving the app never loses a download or an install that is under way.
 */
sealed interface UpdateState {
    /** Nothing asked yet in this session. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** The installed version is the latest; [latest] is the latest release's version name. */
    data class UpToDate(val latest: String) : UpdateState

    data class Available(val release: UpdateRelease) : UpdateState

    data class Downloading(val release: UpdateRelease, val done: Long, val total: Long) : UpdateState {
        val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }

    /** Downloaded and checked: ready to hand to Android's installer. */
    data class Ready(val release: UpdateRelease, val apk: File) : UpdateState

    /** Android needs the user's leave (Install unknown apps) before NormPix may hand it the update. */
    data class NeedsPermission(val release: UpdateRelease, val apk: File) : UpdateState

    /** With Android's installer, which asks the user to confirm. */
    data class Installing(val release: UpdateRelease, val apk: File) : UpdateState

    /** The user stopped the download ([apk] null) or turned down the install ([apk] kept, to install later). */
    data class Cancelled(val release: UpdateRelease, val apk: File?) : UpdateState

    /** [release] is null when the check itself failed; [apk] is kept when only installing it failed. */
    data class Failed(val problem: UpdateProblem, val release: UpdateRelease? = null, val apk: File? = null) : UpdateState
}

/** The release the state is about, if any. */
val UpdateState.release: UpdateRelease?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.Downloading -> release
        is UpdateState.Ready -> release
        is UpdateState.NeedsPermission -> release
        is UpdateState.Installing -> release
        is UpdateState.Cancelled -> release
        is UpdateState.Failed -> release
        UpdateState.Idle, UpdateState.Checking, is UpdateState.UpToDate -> null
    }
