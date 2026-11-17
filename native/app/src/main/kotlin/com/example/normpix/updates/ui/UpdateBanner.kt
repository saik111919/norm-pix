package com.example.normpix.updates.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.updates.UpdateRelease
import com.example.normpix.updates.UpdateState

/** The one banner an update has on Home: it keeps its place in its stack whatever the update is doing. */
internal const val UPDATE_BANNER_KEY = "update"

/** What the banner says it is (the App update page's own name). */
internal const val UPDATE_BANNER_EYEBROW = "App update"

/**
 * The banner Home's "Needs attention" shows for an update at [state], or null when there is nothing to act on: an update that is
 * out (Download update, or Later), and one whose download could not be opened (no browser) - which offers Download update again,
 * the page that opens on pressing the banner saying what went wrong. Its buttons are those of the update sheet ([actions]); pressing
 * the banner itself calls [open], which shows the App update page.
 *
 * [offered] is whether the user is to be shown this release unasked (see `AppUpdater.isOffered`): a release they said Later to is
 * left off. [bytes] writes a size ("12 MB"). A release marked mandatory has no Later, as it is only ever closed in the sheet.
 */
internal fun updateBannerFor(
    state: UpdateState,
    offered: Boolean,
    installedVersion: String,
    bytes: (Long) -> String,
    actions: UpdateSheetActions,
    open: () -> Unit,
): Banner? {
    fun found(release: UpdateRelease) = Banner(
        key = UPDATE_BANNER_KEY,
        tone = StatusTone.Info,
        icon = Icons.Outlined.SystemUpdate,
        eyebrow = UPDATE_BANNER_EYEBROW,
        title = "NormPix ${release.versionName} is available",
        message = "You have $installedVersion",
        primary = StatusAction(UpdateAction.Download.label, onClick = actions.download),
        secondary = if (release.mandatory) null else StatusAction("Later", onClick = actions.later),
        figure = BannerFigure(title = "Size", figure = bytes(release.size), note = "Update to ${release.versionName}"),
        onOpen = open,
    )

    return when (state) {
        is UpdateState.Available -> if (offered) found(state.release) else null
        // The download could not be opened (no browser): the way to try again stays, and the page says why.
        is UpdateState.Failed -> state.release?.takeIf { offered && state.problem.canRetry }?.let(::found)
        UpdateState.Idle, UpdateState.Checking, is UpdateState.UpToDate -> null
    }
}
