package com.example.normpix.updates.ui

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.BuildConfig
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.navigation.LocalNavigator
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
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** How many update sheets and pages are on screen, for each updater (only ever touched on the main thread). */
private val showing = WeakHashMap<AppUpdater, Int>()

/** True while an update sheet or the update page for [updater] is on screen: no other comes up over it. */
fun isUpdateShowing(updater: AppUpdater): Boolean = (showing[updater] ?: 0) > 0

/** Counts [updater]'s update shown ([shown] true) or gone again. */
internal fun markUpdateShowing(updater: AppUpdater, shown: Boolean) {
    val count = (showing[updater] ?: 0) + if (shown) 1 else -1
    if (count > 0) showing[updater] = count else showing.remove(updater)
}

/** Opens the update sheet over whatever is on screen; it follows [updater] until the user closes it. */
suspend fun showUpdateSheet(overlays: AppOverlays, updater: AppUpdater) {
    markUpdateShowing(updater, true)
    try {
        overlays.sheet<Unit>(expandable = true) { dismiss -> UpdateSheet(updater, onClose = { dismiss(null) }) }
    } finally {
        markUpdateShowing(updater, false)
    }
}

/**
 * At launch, once the app is unlocked: a newer release found by the check at launch is offered in the update sheet (see
 * [AppUpdater.launchOffer]), at most once per the wait the user chose in Settings > Automatic updates; nothing is asked while
 * they have automatic checks, or checking when NormPix opens, off. Whenever a download the user
 * started finishes with no update sheet or page on screen (it was left to carry on), the sheet comes back, once the app is
 * unlocked, ready to install: nothing is installed until the user taps Install. And a tapped update notification
 * ([requests]) opens the update page, once unlocked - installing what is ready when it was the notification's Install
 * ([installRequests]), which Android then asks the user to confirm as always.
 */
@Composable
fun OfferAppUpdates(
    updater: AppUpdater?,
    appLocked: Boolean,
    requests: MutableStateFlow<Boolean>? = null,
    installRequests: MutableStateFlow<Boolean>? = null,
) {
    if (updater == null) return
    val overlays = LocalOverlays.current
    val navigator = LocalNavigator.current
    val preferences = LocalAppPreferences.current
    val locked by rememberUpdatedState(appLocked)
    LaunchedEffect(updater, appLocked) {
        if (appLocked) return@LaunchedEffect
        val prefs = preferences?.state?.value
        val allowed = prefs != null && prefs.updateChecksEnabled && prefs.updateCheckOnOpen
        updater.launchOffer(allowed, prefs?.updateCheckEvery?.intervalMs ?: AppUpdater.CHECK_INTERVAL_MS) ?: return@LaunchedEffect
        if (!isUpdateShowing(updater)) showUpdateSheet(overlays, updater)
    }
    if (requests != null) {
        LaunchedEffect(updater, requests) {
            requests.collect { asked ->
                if (!asked) return@collect
                snapshotFlow { locked }.first { !it }
                requests.value = false
                val install = installRequests?.value == true
                installRequests?.value = false
                navigator.openUpdates()
                if (install) {
                    // A fresh start after the notification: GitHub is asked again, and the download is found ready.
                    if (updater.state.value.release == null) updater.check()
                    updater.install()
                }
            }
        }
    }
    LaunchedEffect(updater) {
        updater.downloaded.collect {
            snapshotFlow { locked }.first { !it }
            if (updater.state.value is UpdateState.Ready && !isUpdateShowing(updater)) showUpdateSheet(overlays, updater)
        }
    }
}

@Composable
fun UpdateSheet(updater: AppUpdater, onClose: () -> Unit) {
    val state by updater.state.collectAsStateWithLifecycle()
    UpdateSheetContent(state = state, installed = updater.installed, actions = updateActionsFor(updater, onClose))
}

/** The buttons of the update sheet and page, doing their work on [updater]; [onClose] closes the sheet or leaves the page. */
internal fun updateActionsFor(updater: AppUpdater, onClose: () -> Unit) = UpdateSheetActions(
    update = { updater.download() },
    later = {
        updater.later()
        onClose()
    },
    cancelDownload = updater::cancelDownload,
    install = updater::install,
    allow = updater::allowInstalls,
    retry = updater::retry,
    close = onClose,
)

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
 * what it means, the release (versions, date, size, what's new), progress while downloading, then the buttons at thumb
 * height ([UpdateActionRow]: the way out on the left, the one action to take on the right). The update page is made of the
 * same two parts ([UpdateBody] and [UpdateActionRow]), the buttons pinned at the foot of the screen.
 */
@Composable
fun UpdateSheetContent(state: UpdateState, installed: InstalledApp, actions: UpdateSheetActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = AppSpacing.xs, bottom = AppSpacing.md)) {
        UpdateBody(state, installed, Modifier.padding(horizontal = AppSpacing.xxl), notesMaxHeight = SheetNotesMaxHeight)
        Spacer(Modifier.height(AppSpacing.xl))
        // Less on the left: a text button's label sits inside its touch target, and should line up with the text above.
        UpdateActionRow(state, actions, Modifier.padding(start = AppSpacing.md, end = AppSpacing.xxl))
    }
}

/** The most the "What's new" box takes in the sheet, which has no room to grow: longer notes scroll inside it. */
private val SheetNotesMaxHeight = 220.dp

/**
 * What the update screen says about [state]: the icon (a loader while busy), what is happening as a heading (announced as it
 * changes), what it means, the release's versions, the download's progress and what's new. The sheet puts it above its
 * buttons; the page puts it in its scrolling list. [notesMaxHeight] caps the "What's new" box, which then scrolls inside
 * itself - for the sheet; null lets it take all its notes needs, for the page, which scrolls as a whole.
 */
@Composable
fun UpdateBody(state: UpdateState, installed: InstalledApp, modifier: Modifier = Modifier, notesMaxHeight: Dp? = null) {
    val scheme = MaterialTheme.colorScheme
    val view = sheetView(state, installed)
    val release = state.release
    Column(modifier.fillMaxWidth()) {
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
            WhatsNew(release, notesMaxHeight)
        }
    }
}

/** "What's new": the release's changes, as a bulleted list in a tonal box, which scrolls inside itself when [notesMaxHeight] caps it. */
@Composable
private fun WhatsNew(release: UpdateRelease, notesMaxHeight: Dp?) {
    val scheme = MaterialTheme.colorScheme
    Text("What's new", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(AppSpacing.xs))
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (notesMaxHeight != null) Modifier.heightIn(max = notesMaxHeight) else Modifier)
            .background(scheme.surfaceContainerHigh, AppRadius.rMd)
            .then(if (notesMaxHeight != null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(AppSpacing.md),
    ) {
        ReleaseNotesList(release.notes)
    }
}

/**
 * The update page's body, laid out as a Pixel's system update screen is: the icon on its own, centred and bare, a large
 * headline under it (announced as it changes), what it means, then the release as plain "label: value" lines - the installed
 * version, the new one, the date and "Update size" - the download's progress and what's new. The buttons are not part of it:
 * they are pinned at the foot of the page ([UpdateActionStack]).
 */
@Composable
fun UpdateScreenBody(state: UpdateState, installed: InstalledApp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val view = sheetView(state, installed)
    val release = state.release
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (view.busy) {
                AppLoadingIndicator(Modifier.size(ScreenIconSize))
            } else {
                Icon(
                    view.icon,
                    contentDescription = null,
                    tint = if (state is UpdateState.Failed) scheme.error else scheme.primary,
                    modifier = Modifier.size(ScreenIconSize),
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.xl))
        Text(
            view.title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            modifier = Modifier.semantics { heading() }.politeLiveRegion(),
        )
        Spacer(Modifier.height(AppSpacing.md))
        Text(view.message, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        if (release != null && state !is UpdateState.Failed) {
            Spacer(Modifier.height(AppSpacing.xl))
            val facts = listOfNotNull(
                "Installed version: ${installed.versionName}",
                "New version: ${release.versionName}",
                release.publishedAt?.let { "Released: ${releaseDate(it)}" },
                "Update size: ${Formatter.formatShortFileSize(context, release.size)}",
            )
            for (fact in facts) Text(fact, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
        }
        if (state is UpdateState.Downloading) {
            Spacer(Modifier.height(AppSpacing.xl))
            DownloadProgress(state)
        }
        if (release != null && release.notes.isNotBlank() && view.showsNotes) {
            Spacer(Modifier.height(AppSpacing.xl))
            WhatsNew(release, notesMaxHeight = null)
        }
    }
}

/** The page's icon, as large as the Pixel's - a phone with its download arrow - is against its headline. */
private val ScreenIconSize = 56.dp

/**
 * The update screen's buttons, as on a Pixel's system update screen: the secondary action (Later, Not now, Cancel download,
 * Close) as a text button on the left, and the primary one (Update now, Install, Try again, Open settings) as a filled,
 * fully rounded button on the right. When there is only one action it sits on the right, in the primary style. Shared by
 * the sheet, which puts it under its content, and the page, which pins it at the foot of the screen.
 */
@Composable
fun UpdateActionRow(state: UpdateState, actions: UpdateSheetActions, modifier: Modifier = Modifier) {
    val buttons = updateButtons(state)
    val primary = buttons.primary
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (primary != null) Arrangement.SpaceBetween else Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primary != null) {
            TextButton(onClick = { actions.perform(buttons.secondary) }, modifier = Modifier.heightIn(min = ActionHeight)) {
                Text(buttons.secondary.label, style = MaterialTheme.typography.titleMedium)
            }
        }
        PillButton((primary ?: buttons.secondary).label, Modifier.height(ActionHeight)) { actions.perform(primary ?: buttons.secondary) }
    }
}

/**
 * The update page's buttons, as on a Pixel's system update screen ("Restart now" over "Restart after 2:00 AM"): the primary
 * action (Update now, Install, Try again, Open settings) as a filled, fully rounded button across the page, and the way out
 * (Later, Not now, Close) as an outlined one under it. With only one action, it is the filled one, alone.
 */
@Composable
fun UpdateActionStack(state: UpdateState, actions: UpdateSheetActions, modifier: Modifier = Modifier) {
    val buttons = updateButtons(state)
    val primary = buttons.primary
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        PillButton((primary ?: buttons.secondary).label, Modifier.fillMaxWidth().height(StackedActionHeight)) {
            actions.perform(primary ?: buttons.secondary)
        }
        if (primary != null) {
            OutlinedButton(
                onClick = { actions.perform(buttons.secondary) },
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().height(StackedActionHeight),
            ) {
                Text(buttons.secondary.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
    }
}

/** The filled, fully rounded button of [UpdateActionRow] and [UpdateActionStack]. */
@Composable
private fun PillButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, shape = CircleShape, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

private val ActionHeight = 48.dp

/** Taller than the row's, as the Pixel's are: they run across the page, and are the whole of the foot of it. */
private val StackedActionHeight = 52.dp

/** One of the buttons of the update sheet and page: what it says, and which of the [UpdateSheetActions] it does. */
internal enum class UpdateAction(val label: String) {
    UpdateNow("Update now"),
    Later("Later"),
    NotNow("Not now"),
    CancelDownload("Cancel download"),
    Install("Install"),
    OpenSettings("Open settings"),
    Close("Close"),
    Done("Done"),
    TryAgain("Try again"),
    DownloadAgain("Download again"),
}

/** The buttons for a state: the way out, always, and what to do next when there is something to do. */
internal class UpdateButtons(val primary: UpdateAction?, val secondary: UpdateAction)

internal fun updateButtons(state: UpdateState): UpdateButtons {
    // A release the user can put off; one marked as needed is only closed, so it comes back at the next launch.
    fun laterFor(release: UpdateRelease) = if (release.mandatory) UpdateAction.NotNow else UpdateAction.Later
    return when (state) {
        UpdateState.Idle, UpdateState.Checking -> UpdateButtons(null, UpdateAction.Close)
        is UpdateState.UpToDate -> UpdateButtons(null, UpdateAction.Done)
        is UpdateState.Available -> UpdateButtons(UpdateAction.UpdateNow, laterFor(state.release))
        is UpdateState.Downloading -> UpdateButtons(null, UpdateAction.CancelDownload)
        is UpdateState.Ready -> UpdateButtons(UpdateAction.Install, laterFor(state.release))
        is UpdateState.NeedsPermission -> UpdateButtons(UpdateAction.OpenSettings, UpdateAction.NotNow)
        is UpdateState.Installing -> UpdateButtons(null, UpdateAction.Close)
        is UpdateState.Cancelled ->
            UpdateButtons(if (state.apk != null) UpdateAction.Install else UpdateAction.DownloadAgain, UpdateAction.Close)
        is UpdateState.Failed -> UpdateButtons(if (state.problem.canRetry) UpdateAction.TryAgain else null, UpdateAction.Close)
    }
}

private fun UpdateSheetActions.perform(action: UpdateAction) {
    when (action) {
        UpdateAction.UpdateNow -> update()
        UpdateAction.Later -> later()
        UpdateAction.CancelDownload -> cancelDownload()
        UpdateAction.Install -> install()
        UpdateAction.OpenSettings -> allow()
        UpdateAction.TryAgain, UpdateAction.DownloadAgain -> retry()
        UpdateAction.NotNow, UpdateAction.Close, UpdateAction.Done -> close()
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

/** What the sheet shows for a state: the icon (or a loader, when [busy]) and the words. Its buttons are [updateButtons]. */
private class SheetView(
    val icon: ImageVector,
    val title: String,
    val message: String,
    val busy: Boolean = false,
    val showsNotes: Boolean = false,
)

private fun sheetView(state: UpdateState, installed: InstalledApp): SheetView = when (state) {
    UpdateState.Idle, UpdateState.Checking -> SheetView(
        Icons.Outlined.SystemUpdate, "Checking for updates", "Asking GitHub for the latest release.", busy = true,
    )
    is UpdateState.UpToDate -> SheetView(
        Icons.Outlined.CheckCircle,
        "NormPix is up to date",
        "Version ${installed.versionName} is the latest release.",
    )
    is UpdateState.Available -> SheetView(
        Icons.Outlined.SystemUpdate,
        "Update available",
        buildString {
            append("NormPix ${state.release.versionName} is ready to download. Your data stays as it is.")
            if (state.release.mandatory) append(" This release is marked as one to install.")
        },
        showsNotes = true,
    )
    is UpdateState.Downloading -> SheetView(
        Icons.Outlined.Download,
        "Downloading update",
        "You can leave this, or close NormPix: the download carries on, its notification shows how far it is, and " +
            "says when it's ready to install.",
    )
    is UpdateState.Ready -> SheetView(
        Icons.Outlined.InstallMobile,
        "Ready to install",
        "Downloaded and checked. Tap Install, then confirm on the screen Android shows. NormPix closes while it " +
            "updates; your data stays as it is.",
        showsNotes = true,
    )
    is UpdateState.NeedsPermission -> SheetView(
        Icons.Outlined.Security,
        "Allow NormPix to install updates",
        "Android asks this once. Turn on \"Allow from this source\" for NormPix, then come back: the update carries on.",
    )
    is UpdateState.Installing -> SheetView(
        Icons.Outlined.InstallMobile,
        "Installing",
        "Confirm on the screen Android shows. NormPix closes while it updates; your data stays as it is.",
        busy = true,
    )
    is UpdateState.Cancelled -> if (state.apk != null) {
        SheetView(Icons.Outlined.InstallMobile, "Update not installed", "It's downloaded, so you can install it whenever you like.")
    } else {
        SheetView(Icons.Outlined.Download, "Download stopped", "Nothing was kept.")
    }
    is UpdateState.Failed -> SheetView(
        if (state.problem == UpdateProblem.Disabled) Icons.Outlined.UpdateDisabled else Icons.Outlined.ErrorOutline,
        when {
            state.problem == UpdateProblem.Disabled -> "Updates come with releases"
            state.release == null -> "Couldn't check for updates"
            else -> "Couldn't update"
        },
        state.problem.describe(),
    )
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

internal fun releaseDate(at: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(at)
