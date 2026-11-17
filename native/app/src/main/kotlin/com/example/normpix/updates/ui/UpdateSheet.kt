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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material3.Button
import com.example.normpix.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.updates.AppUpdater
import com.example.normpix.updates.DownloadTabLook
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * At launch, once the app is unlocked: the quiet check (see [AppUpdater.launchOffer]), at most once per the wait the user chose
 * in Settings > Automatic updates, and none while they have automatic checks, or checking when NormPix opens, off. What it
 * finds is shown as Home's update banner, never in a sheet put over what they are doing: they update when they choose to, from
 * the banner or Settings. And a tapped update notification ([requests]) opens the update page, once unlocked.
 */
@Composable
fun OfferAppUpdates(
    updater: AppUpdater?,
    appLocked: Boolean,
    requests: MutableStateFlow<Boolean>? = null,
) {
    if (updater == null) return
    val navigator = LocalNavigator.current
    val preferences = LocalAppPreferences.current
    val locked by rememberUpdatedState(appLocked)
    LaunchedEffect(updater, appLocked) {
        if (appLocked) return@LaunchedEffect
        val prefs = preferences?.state?.value
        val allowed = prefs != null && prefs.updateChecksEnabled && prefs.updateCheckOnOpen
        // Found, it shows on Home as a banner; nothing opens over the screen.
        updater.launchOffer(allowed, prefs?.updateCheckEvery?.intervalMs ?: AppUpdater.CHECK_INTERVAL_MS)
    }
    if (requests != null) {
        LaunchedEffect(updater, requests) {
            requests.collect { asked ->
                if (!asked) return@collect
                snapshotFlow { locked }.first { !it }
                requests.value = false
                navigator.openUpdates()
            }
        }
    }
}

@Composable
fun UpdateSheet(updater: AppUpdater, onClose: () -> Unit) {
    val state by updater.state.collectAsStateWithLifecycle()
    UpdateSheetContent(
        state = state,
        installed = updater.installed,
        actions = updateActionsFor(updater, onClose, rememberDownloadTabLook()),
    )
}

/**
 * How the browser tab Download update opens looks: its toolbar the colour of the app's own surface, light or dark as the app is
 * - the user's theme (Light, Dark, Pure, or the wallpaper's colours), which need not be the phone's.
 */
@Composable
fun rememberDownloadTabLook(): DownloadTabLook {
    val surface = MaterialTheme.colorScheme.surface
    return remember(surface) { DownloadTabLook(toolbarColor = surface.toArgb(), dark = surface.luminance() < 0.5f) }
}

/** The buttons of the update sheet and page, doing their work on [updater]; [onClose] closes the sheet or leaves the page. */
internal fun updateActionsFor(updater: AppUpdater, onClose: () -> Unit, look: DownloadTabLook) = UpdateSheetActions(
    download = { updater.openDownload(look) },
    later = {
        updater.later()
        onClose()
    },
    retry = { updater.retry(look) },
    close = onClose,
    checkAgain = updater::checkAgain,
)

/** What the update sheet's buttons do. */
class UpdateSheetActions(
    /** Download update: opens the release's APK link in the user's browser. */
    val download: () -> Unit,
    val later: () -> Unit,
    val retry: () -> Unit,
    val close: () -> Unit,
    /** Asks GitHub again, from "up to date": Check for update. */
    val checkAgain: () -> Unit = {},
)

/**
 * What happens after Download update, in a few short sentences: the browser downloads the file, the user opens it, and Android's
 * own installer asks before updating. Said under the button, in the sheet and on the page.
 */
internal const val DOWNLOAD_STEPS = "Your browser downloads it. Open the download and tap Update. " +
    "The first time, Android asks you to let your browser install apps. Your data stays."

/**
 * The update sheet, laid out like the app's decisions: an icon, what is happening as a heading (announced as it changes),
 * what it means, the release (versions, date, size, what's new), then the buttons at thumb height ([UpdateActionRow]: the way
 * out on the left, the one action to take on the right) and, for an update, what happens after Download update
 * ([DownloadSteps]). The update page is made of the same parts ([UpdateScreenBody], [UpdateActionStack]), the buttons pinned at
 * the foot of the screen.
 */
@Composable
fun UpdateSheetContent(state: UpdateState, installed: InstalledApp, actions: UpdateSheetActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = AppSpacing.xs, bottom = AppSpacing.md)) {
        UpdateBody(state, installed, Modifier.padding(horizontal = AppSpacing.xxl), notesMaxHeight = SheetNotesMaxHeight)
        Spacer(Modifier.height(AppSpacing.xl))
        // Less on the left: a text button's label sits inside its touch target, and should line up with the text above.
        UpdateActionRow(state, actions, Modifier.padding(start = AppSpacing.md, end = AppSpacing.xxl))
        DownloadSteps(state, Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.sm))
    }
}

/** The most the "What's new" box takes in the sheet, which has no room to grow: longer notes scroll inside it. */
private val SheetNotesMaxHeight = 220.dp

/** What follows Download update, as a quiet note under the buttons; nothing in any state but an update waiting to be downloaded. */
@Composable
fun DownloadSteps(state: UpdateState, modifier: Modifier = Modifier) {
    if (state !is UpdateState.Available) return
    Text(
        DOWNLOAD_STEPS,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * What the update screen says about [state]: the icon (a loader while busy), what is happening as a heading (announced as it
 * changes), what it means, the release's versions and what's new. The sheet puts it above its buttons; the page puts it in its
 * scrolling list. [notesMaxHeight] caps the "What's new" box, which then scrolls inside itself - for the sheet; null lets it take
 * all its notes needs, for the page, which scrolls as a whole.
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
 * version, the new one, the date and "Update size" - and what's new. The buttons are not part of it: they are pinned at the foot
 * of the page ([UpdateActionStack]).
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
        if (release != null && release.notes.isNotBlank() && view.showsNotes) {
            Spacer(Modifier.height(AppSpacing.xl))
            WhatsNew(release, notesMaxHeight = null)
        }
    }
}

/** The page's icon, as large as the Pixel's - a phone with its download arrow - is against its headline. */
private val ScreenIconSize = 56.dp

/**
 * The update screen's buttons, as on a Pixel's system update screen: the secondary action (Later, Not now, Close) as a text
 * button on the left, and the primary one (Download update, Try again, Check for update) as a filled, fully rounded button on the
 * right. When there is only one action it sits on the right, in the primary style. Shared by the sheet, which puts it under its
 * content, and the page, which pins it at the foot of the screen.
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
 * action (Download update, Try again, Check for update) as a filled, fully rounded button across the page, and the way out
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
    /** Opens the release's APK link in the user's browser. */
    Download("Download update"),
    Later("Later"),
    NotNow("Not now"),
    Close("Close"),
    Done("Done"),
    TryAgain("Try again"),

    /** On "up to date", as the Pixel's update screen has it: ask GitHub again, for a release that came out since. */
    CheckForUpdate("Check for update"),
}

/** The buttons for a state: the way out, always, and what to do next when there is something to do. */
internal class UpdateButtons(val primary: UpdateAction?, val secondary: UpdateAction)

internal fun updateButtons(state: UpdateState): UpdateButtons {
    // A release the user can put off; one marked as needed is only closed, so it comes back at the next launch.
    fun laterFor(release: UpdateRelease) = if (release.mandatory) UpdateAction.NotNow else UpdateAction.Later
    return when (state) {
        UpdateState.Idle, UpdateState.Checking -> UpdateButtons(null, UpdateAction.Close)
        is UpdateState.UpToDate -> UpdateButtons(UpdateAction.CheckForUpdate, UpdateAction.Done)
        is UpdateState.Available -> UpdateButtons(UpdateAction.Download, laterFor(state.release))
        is UpdateState.Failed -> UpdateButtons(if (state.problem.canRetry) UpdateAction.TryAgain else null, UpdateAction.Close)
    }
}

private fun UpdateSheetActions.perform(action: UpdateAction) {
    when (action) {
        UpdateAction.Download -> download()
        UpdateAction.Later -> later()
        UpdateAction.TryAgain -> retry()
        UpdateAction.CheckForUpdate -> checkAgain()
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
            append("NormPix ${state.release.versionName} is ready to download.")
            if (state.release.mandatory) append(" This release is marked as one to install.")
        },
        showsNotes = true,
    )
    is UpdateState.Failed -> SheetView(
        if (state.problem == UpdateProblem.Disabled) Icons.Outlined.UpdateDisabled else Icons.Outlined.ErrorOutline,
        when {
            state.problem == UpdateProblem.Disabled -> "Updates come with releases"
            state.release == null -> "Couldn't check for updates"
            else -> "Couldn't open the download"
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
    is UpdateProblem.BadRelease -> "The latest release's details are missing or don't agree with each other, so it isn't offered."
    is UpdateProblem.OtherApp -> "The latest release is for another app ($applicationId)."
    is UpdateProblem.NeedsAndroid -> "This update needs a newer version of Android than this phone has."
    UpdateProblem.NoBrowser -> "NormPix couldn't open a browser to download the update. Install or turn on a browser, then try again."
    UpdateProblem.BadLink -> "The update's download link isn't a file on NormPix's own GitHub releases, so it wasn't opened."
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
        is UpdateState.Failed -> if (state.release == null) "Couldn't check for updates" else "Couldn't open the download of ${state.release.versionName}"
    }
}

internal fun releaseDate(at: Instant): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(at)
