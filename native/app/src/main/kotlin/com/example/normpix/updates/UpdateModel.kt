package com.example.normpix.updates

import java.time.Instant

/** The app as installed: what an update has to be for, and what it has to be newer than. */
data class InstalledApp(
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    /** The phone's Android version (API level), which a release may need to be at least. */
    val sdk: Int,
    /** The phone's CPU types, the one it runs best first (`Build.SUPPORTED_ABIS`): which of a release's APKs is the one to open. */
    val abis: List<String> = emptyList(),
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
    /** Where GitHub serves the APK: what the browser is sent to (see [DownloadLink]). */
    val apkUrl: String,
    /** The APK's exact size in bytes. */
    val size: Long,
    /** The APK's SHA-256, lowercase hex. */
    val sha256: String,
    /** Set by the release itself: offered on every launch, even after Later. Never forced on anyone. */
    val mandatory: Boolean,
)

/**
 * Why checking for an update, or opening its download, didn't work. Each says something the user can act on (see `describe` in
 * the update UI); [canRetry] is false where trying again can't help.
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

    /** The release's details are missing, malformed or contradict each other: it isn't offered. */
    data class BadRelease(val detail: String) : UpdateProblem

    /** The release is published for another app (another package name). */
    data class OtherApp(val applicationId: String) : UpdateProblem {
        override val canRetry get() = false
    }

    /** The release needs a newer Android than the phone has. */
    data class NeedsAndroid(val minSdk: Int) : UpdateProblem {
        override val canRetry get() = false
    }

    /** The phone has no browser to open the download in (none installed, or all turned off). */
    data object NoBrowser : UpdateProblem

    /** The release's download link isn't a file of this app's GitHub releases, so it was not opened. */
    data object BadLink : UpdateProblem {
        override val canRetry get() = false
    }
}

/** An [UpdateProblem] on its way up from the network or from opening the download. */
class UpdateException(val problem: UpdateProblem, cause: Throwable? = null) : Exception(problem.toString(), cause)

/**
 * Where updating stands, for the update sheet, the update page and Settings. Held by [AppUpdater] for the whole process, so
 * closing the sheet or rotating never loses what a check found. NormPix does not download or install anything itself: an
 * update is its release's download link, opened in the user's browser (see [BrowserDownloadOpener]).
 */
sealed interface UpdateState {
    /** Nothing asked yet in this session. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** The installed version is the latest; [latest] is the latest release's version name. */
    data class UpToDate(val latest: String) : UpdateState

    data class Available(val release: UpdateRelease) : UpdateState

    /** [release] is null when the check itself failed; set when only opening the release's download failed. */
    data class Failed(val problem: UpdateProblem, val release: UpdateRelease? = null) : UpdateState
}

/** The release the state is about, if any. */
val UpdateState.release: UpdateRelease?
    get() = when (this) {
        is UpdateState.Available -> release
        is UpdateState.Failed -> release
        UpdateState.Idle, UpdateState.Checking, is UpdateState.UpToDate -> null
    }
