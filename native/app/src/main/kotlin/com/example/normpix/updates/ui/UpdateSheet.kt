package com.example.normpix.updates.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.BuildConfig
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.updates.AppUpdater
import com.example.normpix.updates.InstalledApp
import com.example.normpix.updates.UpdateProblem
import com.example.normpix.updates.UpdateRelease
import com.example.normpix.updates.UpdateState
import com.example.normpix.updates.release
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Opens the update sheet over whatever is on screen; it follows [updater] until the user closes it. */
suspend fun showUpdateSheet(overlays: AppOverlays, updater: AppUpdater) {
    overlays.sheet<Unit>(expandable = true) { dismiss -> UpdateSheet(updater, onClose = { dismiss(null) }) }
}

/**
 * At launch, once the app is unlocked: a newer release found by the daily check is offered in the update sheet (see
 * [AppUpdater.launchOffer]). Nothing at all while the user has automatic checks off.
 */
@Composable
fun OfferAppUpdates(updater: AppUpdater?, appLocked: Boolean) {
    if (updater == null) return
    val overlays = LocalOverlays.current
    val preferences = LocalAppPreferences.current
    LaunchedEffect(updater, appLocked) {
        if (appLocked) return@LaunchedEffect
        val allowed = preferences?.state?.value?.updateChecksEnabled ?: false
        updater.launchOffer(allowed) ?: return@LaunchedEffect
        showUpdateSheet(overlays, updater)
    }
}

@Composable
fun UpdateSheet(updater: AppUpdater, onClose: () -> Unit) {
    val state by updater.state.collectAsStateWithLifecycle()
    UpdateSheetContent(
        state = state,
        installed = updater.installed,
        actions = UpdateSheetActions(
            update = updater::download,
            later = {
                updater.later()
                onClose()
            },
            cancelDownload = updater::cancelDownload,
            install = updater::install,
            allow = updater::allowInstalls,
            retry = updater::retry,
            close = onClose,
        ),
    )
}

/** What the update sheet's buttons do. */
class UpdateSheetActions(
    val update: () -> Unit,
    val later: () -> Unit,
    val cancelDownload: () -> Unit,
    val install: () -> Unit,
    val allow: () -> Unit,
    val retry: () -> Unit,
    val close: () -> Unit,
)

/**
 * The update sheet, laid out like the app's decisions: an icon, what is happening as a heading (announced as it changes),
 * what it means, the release (versions, date, size, what's new), progress while downloading, then the one action to
 * take at thumb height with the way out under it.
 */
@Composable
fun UpdateSheetContent(state: UpdateState, installed: InstalledApp, actions: UpdateSheetActions, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val view = sheetView(state, installed, actions)
    val release = state.release
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.xxl, top = AppSpacing.xs, end = AppSpacing.xxl, bottom = AppSpacing.md),
    ) {
        if (view.busy) {
            AppLoadingIndicator(Modifier.size(48.dp))
        } else {
            val error = state is UpdateState.Failed
            LeadingIcon(
                view.icon,
                background = if (error) scheme.errorContainer else scheme.secondaryContainer,
                foreground = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer,
                size = 48.dp,
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            view.title,
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
            modifier = Modifier.semantics { heading() }.politeLiveRegion(),
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(view.message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        if (release != null && state !is UpdateState.Failed) {
            Spacer(Modifier.height(AppSpacing.lg))
            ReleaseSummary(release, installed)
        }
        if (state is UpdateState.Downloading) {
            Spacer(Modifier.height(AppSpacing.lg))
            DownloadProgress(state)
        }
        if (release != null && release.notes.isNotBlank() && view.showsNotes) {
            Spacer(Modifier.height(AppSpacing.lg))
            Text("What's new", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
            Spacer(Modifier.height(AppSpacing.xs))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .background(scheme.surfaceContainerHigh, AppRadius.rMd)
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacing.md),
            ) {
                Text(release.notes, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(AppSpacing.xl))
        view.primary?.let { (label, onClick) ->
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(AppSpacing.xs))
        }
        TextButton(onClick = view.secondary.second, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(view.secondary.first, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** The installed version beside the new one, and when the new one came out and how big it is. */
@Composable
private fun ReleaseSummary(release: UpdateRelease, installed: InstalledApp) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        VersionLabel("Installed", installed.versionName)
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "to", tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        VersionLabel("New", release.versionName, emphasized = true)
    }
    Spacer(Modifier.height(AppSpacing.xs))
    val details = listOfNotNull(
        release.publishedAt?.let { "Released ${releaseDate(it)}" },
        Formatter.formatShortFileSize(context, release.size),
    ).joinToString(" · ")
    Text(details, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
}

@Composable
private fun VersionLabel(label: String, version: String, emphasized: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            version,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DownloadProgress(state: UpdateState.Downloading) {
    val context = LocalContext.current
    AppLinearProgress(Modifier.fillMaxWidth(), progress = { state.fraction })
    Spacer(Modifier.height(AppSpacing.xs))
    val done = Formatter.formatShortFileSize(context, state.done)
    val total = Formatter.formatShortFileSize(context, state.total)
    Text(
        "$done of $total · ${(state.fraction * 100).toInt()}%",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** What the sheet shows for a state: the icon (or a loader, when [busy]), the words, and its buttons. */
private class SheetView(
    val icon: ImageVector,
    val title: String,
    val message: String,
    val primary: Pair<String, () -> Unit>?,
    val secondary: Pair<String, () -> Unit>,
    val busy: Boolean = false,
    val showsNotes: Boolean = false,
)

private fun sheetView(state: UpdateState, installed: InstalledApp, actions: UpdateSheetActions): SheetView {
    val close = "Close" to actions.close
    // A release the user can put off; one marked as needed is only closed, so it comes back at the next launch.
    fun laterFor(release: UpdateRelease) = if (release.mandatory) "Not now" to actions.close else "Later" to actions.later
    return when (state) {
        UpdateState.Idle, UpdateState.Checking -> SheetView(
            Icons.Outlined.SystemUpdate, "Checking for updates", "Asking GitHub for the latest release.", null, close, busy = true,
        )
        is UpdateState.UpToDate -> SheetView(
            Icons.Outlined.CheckCircle,
            "NormPix is up to date",
            "Version ${installed.versionName} is the latest release.",
            null,
            "Done" to actions.close,
        )
        is UpdateState.Available -> SheetView(
            Icons.Outlined.SystemUpdate,
            "Update available",
            buildString {
                append("NormPix ${state.release.versionName} is ready to download. Your data stays as it is.")
                if (state.release.mandatory) append(" This release is marked as one to install.")
            },
            "Update now" to actions.update,
            laterFor(state.release),
            showsNotes = true,
        )
        is UpdateState.Downloading -> SheetView(
            Icons.Outlined.Download,
            "Downloading update",
            "You can close this: the download carries on, and Android asks you to install it when it's done.",
            null,
            "Cancel download" to actions.cancelDownload,
        )
        is UpdateState.Ready -> SheetView(
            Icons.Outlined.InstallMobile,
            "Ready to install",
            "Android asks you to confirm. NormPix closes while it updates; your data stays as it is.",
            "Install" to actions.install,
            laterFor(state.release),
            showsNotes = true,
        )
        is UpdateState.NeedsPermission -> SheetView(
            Icons.Outlined.Security,
            "Allow NormPix to install updates",
            "Android asks this once. Turn on \"Allow from this source\" for NormPix, then come back: the update carries on.",
            "Open settings" to actions.allow,
            "Not now" to actions.close,
        )
        is UpdateState.Installing -> SheetView(
            Icons.Outlined.InstallMobile,
            "Installing",
            "Confirm on the screen Android shows. NormPix closes while it updates; your data stays as it is.",
            null,
            close,
            busy = true,
        )
        is UpdateState.Cancelled -> if (state.apk != null) {
            SheetView(
                Icons.Outlined.InstallMobile,
                "Update not installed",
                "It's downloaded, so you can install it whenever you like.",
                "Install" to actions.install,
                close,
            )
        } else {
            SheetView(Icons.Outlined.Download, "Download stopped", "Nothing was kept.", "Download again" to actions.retry, close)
        }
        is UpdateState.Failed -> SheetView(
            if (state.problem == UpdateProblem.Disabled) Icons.Outlined.UpdateDisabled else Icons.Outlined.ErrorOutline,
            when {
                state.problem == UpdateProblem.Disabled -> "Updates come with releases"
                state.release == null -> "Couldn't check for updates"
                else -> "Couldn't update"
            },
            state.problem.describe(),
            if (state.problem.canRetry) "Try again" to actions.retry else null,
            close,
        )
    }
}

/** What went wrong, in words the user can act on. */
fun UpdateProblem.describe(zone: ZoneId = ZoneId.systemDefault()): String = when (this) {
    UpdateProblem.Disabled -> "This is a ${BuildConfig.BUILD_TYPE} build. Only the release build updates itself from GitHub."
    UpdateProblem.Offline -> "Couldn't reach GitHub. Check your connection and try again."
    UpdateProblem.TimedOut -> "GitHub took too long to answer. Try again in a moment."
    is UpdateProblem.RateLimited -> "GitHub has had too many requests from your network for now. Try again " +
        (until?.let { "after ${DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone).format(it)}" } ?: "in an hour") + "."
    UpdateProblem.NoRelease -> "No release was found on GitHub. Releases have to be published, and public, for the app to see them."
    is UpdateProblem.ServerError -> "GitHub couldn't answer just now (error $code). Try again later."
    UpdateProblem.Untrusted -> "The update pointed somewhere other than GitHub, so NormPix stopped."
    UpdateProblem.Insecure -> "Couldn't make a secure connection to GitHub. Check the phone's date and time, or try another network."
    is UpdateProblem.BadRelease -> "The latest release isn't set up to be installed from the app, so it isn't offered."
    is UpdateProblem.OtherApp -> "The latest release is for another app ($applicationId)."
    is UpdateProblem.NeedsAndroid -> "This update needs a newer version of Android than this phone has."
    UpdateProblem.Storage -> "Not enough free space to download the update. Free some up and try again."
    UpdateProblem.Interrupted -> "The download stopped before it finished. Try again."
    UpdateProblem.ChecksumMismatch -> "The downloaded file wasn't the one the release describes, so it was deleted. Try again."
    UpdateProblem.NotAnApk -> "The downloaded file isn't an app Android can install."
    is UpdateProblem.WrongPackage -> "The downloaded app isn't NormPix, so it won't be installed."
    is UpdateProblem.NotNewer -> "The downloaded app isn't newer than the one you have."
    UpdateProblem.SignatureMismatch -> "This update is signed with a different key from the NormPix on this phone, so Android " +
        "won't install it over it. Nothing was changed, and your data is untouched."
    UpdateProblem.InstallBlocked -> "Installing was blocked on this phone, by a setting or a device policy."
    UpdateProblem.InstallStorage -> "Not enough free space to install the update. Free some up and try again."
    UpdateProblem.InstallIncompatible -> "This update isn't compatible with this phone."
    is UpdateProblem.InstallFailed -> "Android couldn't install the update." + detail?.takeIf { it.isNotBlank() }?.let { " It said: $it" }.orEmpty()
}

/** One line on where updating stands, under Settings' "Check for updates". */
fun updateSummary(state: UpdateState, updater: AppUpdater, now: Long = System.currentTimeMillis()): String {
    val version = "Version ${updater.installed.versionName}"
    if (!updater.enabled) return "$version · updates come with release builds"
    return when (state) {
        UpdateState.Idle -> updater.lastCheckAt.takeIf { it > 0 }?.let {
            "$version · checked ${DateUtils.getRelativeTimeSpanString(it, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase(Locale.getDefault())}"
        } ?: version
        UpdateState.Checking -> "Checking for updates…"
        is UpdateState.UpToDate -> "$version · up to date"
        is UpdateState.Available -> "Version ${state.release.versionName} is available"
        is UpdateState.Downloading -> "Downloading version ${state.release.versionName} · ${(state.fraction * 100).toInt()}%"
        is UpdateState.Ready -> "Version ${state.release.versionName} is ready to install"
        is UpdateState.NeedsPermission -> "Version ${state.release.versionName} · allow installs to update"
        is UpdateState.Installing -> "Installing version ${state.release.versionName}"
        is UpdateState.Cancelled -> "Update to ${state.release.versionName} not installed"
        is UpdateState.Failed -> if (state.release == null) "Couldn't check for updates" else "Couldn't update to ${state.release.versionName}"
    }
}

private fun releaseDate(at: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(at)
