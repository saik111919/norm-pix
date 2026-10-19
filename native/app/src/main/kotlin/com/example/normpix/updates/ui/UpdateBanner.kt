package com.example.normpix.updates.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.updates.UpdateRelease
import com.example.normpix.updates.UpdateState

/** The one banner an update has on Home: it keeps its place in its stack as the update moves from step to step. */
internal const val UPDATE_BANNER_KEY = "update"

/** What the banner says it is, whichever step the update is at (the App update page's own name). */
internal const val UPDATE_BANNER_EYEBROW = "App update"

/**
 * The banner Home's "Needs attention" shows for an update at [state], or null when there is nothing to act on. It follows the
 * update through its steps, with the buttons of the update sheet ([actions]): found (Update, or Later), downloading (how far,
 * and Cancel), downloaded (Install, or Later), and waiting for Android's leave to install (Allow installs, or Later). Pressing
 * the banner itself calls [open], which shows the App update page.
 *
 * [offered] is whether the user is to be shown this release unasked (see `AppUpdater.isOffered`): a release they said Later to
 * is left off. A download under way is always shown, as the user has started it since. [bytes] writes a size ("12 MB"). A release
 * marked mandatory has no Later, as it is only ever closed in the sheet.
 */
internal fun updateBannerFor(
    state: UpdateState,
    offered: Boolean,
    installedVersion: String,
    bytes: (Long) -> String,
    actions: UpdateSheetActions,
    open: () -> Unit,
): Banner? {
    fun banner(
        release: UpdateRelease,
        icon: ImageVector,
        title: String,
        message: String,
        primary: StatusAction,
        secondary: StatusAction? = if (release.mandatory) null else StatusAction("Later", onClick = actions.later),
    ) = Banner(
        key = UPDATE_BANNER_KEY,
        tone = StatusTone.Info,
        icon = icon,
        eyebrow = UPDATE_BANNER_EYEBROW,
        title = title,
        message = message,
        primary = primary,
        secondary = secondary,
        onOpen = open,
    )

    fun found(release: UpdateRelease) = banner(
        release,
        Icons.Outlined.SystemUpdate,
        title = "NormPix ${release.versionName} is available",
        message = "You have $installedVersion · ${bytes(release.size)}",
        primary = StatusAction("Update", onClick = actions.update),
    )

    fun downloaded(release: UpdateRelease) = banner(
        release,
        Icons.Outlined.InstallMobile,
        title = "Ready to install",
        message = "NormPix ${release.versionName} is downloaded and checked",
        primary = StatusAction("Install", onClick = actions.install),
    )

    return when (state) {
        is UpdateState.Available -> if (offered) found(state.release) else null
        is UpdateState.Ready -> if (offered) downloaded(state.release) else null
        is UpdateState.NeedsPermission -> if (!offered) null else banner(
            state.release,
            Icons.Outlined.Security,
            title = "Allow NormPix to install",
            message = "Android asks this once, for version ${state.release.versionName}",
            primary = StatusAction("Allow installs", onClick = actions.allow),
        )
        // Stopped by the user, or turned down: a downloaded update waits for Install, a stopped download for Update again.
        is UpdateState.Cancelled -> when {
            !offered -> null
            state.apk != null -> downloaded(state.release)
            else -> found(state.release)
        }
        // Followed to the end, even if the user once said Later: they have started it since.
        is UpdateState.Downloading -> banner(
            state.release,
            Icons.Outlined.Download,
            title = "Downloading ${state.release.versionName} · ${(state.fraction * 100).toInt()}%",
            message = "${bytes(state.done)} of ${bytes(state.total)}",
            primary = StatusAction("Details", onClick = open),
            secondary = StatusAction("Cancel", onClick = actions.cancelDownload),
        )
        UpdateState.Idle, UpdateState.Checking, is UpdateState.UpToDate, is UpdateState.Installing, is UpdateState.Failed -> null
    }
}
