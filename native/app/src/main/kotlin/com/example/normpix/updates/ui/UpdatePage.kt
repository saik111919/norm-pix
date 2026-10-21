package com.example.normpix.updates.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.updates.AppUpdater
import com.example.normpix.updates.UpdateState
import com.example.normpix.updates.release

/**
 * Check for updates (Settings, and Automatic updates' Check now) on a page of its own: opened, it asks GitHub for the latest
 * release - unless an update is already found, downloading or waiting - and shows where updating stands as the update sheet
 * does: the versions, what's new, the download's progress, and the one thing to do next. A tapped update notification opens
 * it too. While it is on screen no update sheet comes up over it.
 *
 * It is laid out as a Pixel's system update screen is ([UpdateScreenBody]): a bare icon and a large headline open the page, with
 * no title of the page's own above them, and all that scrolls while the buttons stay pinned at the foot of the screen
 * ([UpdateActionStack]): Update now - or Install, or whatever is next - filled and across the page, and Later, or whatever
 * the way out is, outlined under it.
 *
 * Leaving it never stops a download: that carries on with NormPix closed, its progress in a notification.
 */
@Composable
fun UpdatePage() {
    val navigator = LocalNavigator.current
    val updater = LocalAppContainer.current.updates
    if (updater == null) {
        AppSubPage(title = PAGE_TITLE, onBack = navigator::back) {
            item(key = "update") {
                AppEmptyState(
                    icon = Icons.Outlined.UpdateDisabled,
                    title = "Updates come with release builds",
                    message = "Only the release build of NormPix updates itself from GitHub.",
                )
            }
        }
        return
    }
    UpdatePageFor(updater, onClose = navigator::back)
}

private const val PAGE_TITLE = "App update"

@Composable
private fun UpdatePageFor(updater: AppUpdater, onClose: () -> Unit) {
    val state by updater.state.collectAsStateWithLifecycle()
    DisposableEffect(updater) {
        markUpdateShowing(updater, true)
        onDispose { markUpdateShowing(updater, false) }
    }
    // Opened to check: GitHub is asked, unless there is an update in hand already (or a check under way).
    LaunchedEffect(updater) {
        val current = updater.state.value
        if (current.release == null && current !is UpdateState.Checking) updater.check()
    }
    val actions = updateActionsFor(updater, onClose)
    val listState = rememberLazyListState()
    AppSubPage(
        title = PAGE_TITLE,
        onBack = onClose,
        listState = listState,
        // The headline is the page's title: the page's name isn't set above it.
        largeTitle = false,
        bottomBar = { UpdateBottomBar(state, actions, listState) },
    ) {
        item(key = "update") {
            Column {
                UpdateScreenBody(state, updater.installed, Modifier.padding(horizontal = AppSpacing.xl))
                if (updater.enabled) {
                    Spacer(Modifier.height(AppSpacing.lg))
                    Text(
                        "Only GitHub is contacted. Every update is checked against its release, and Android installs it only if it's " +
                            "signed with the same key as the NormPix on this phone. Your data stays as it is.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AppSpacing.xl),
                    )
                }
            }
        }
    }
}

/** The buttons pinned at the foot of the page, with a line above them while there is more of the page to scroll to. */
@Composable
private fun UpdateBottomBar(state: UpdateState, actions: UpdateSheetActions, listState: LazyListState) {
    val moreBelow by remember(listState) { derivedStateOf { listState.canScrollForward } }
    Column(Modifier.fillMaxWidth()) {
        if (moreBelow) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        UpdateActionStack(
            state,
            actions,
            Modifier.padding(start = AppSpacing.xl, top = AppSpacing.md, end = AppSpacing.xl, bottom = AppSpacing.md),
        )
    }
}
