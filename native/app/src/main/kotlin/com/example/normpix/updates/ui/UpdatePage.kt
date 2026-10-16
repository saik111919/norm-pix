package com.example.normpix.updates.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
 * Leaving it never stops a download: that carries on with NormPix closed, its progress in a notification.
 */
@Composable
fun UpdatePage() {
    val navigator = LocalNavigator.current
    val updater = LocalAppContainer.current.updates
    AppSubPage(title = "App update", onBack = navigator::back) {
        item(key = "update") {
            if (updater == null) {
                AppEmptyState(
                    icon = Icons.Outlined.UpdateDisabled,
                    title = "Updates come with release builds",
                    message = "Only the release build of NormPix updates itself from GitHub.",
                )
            } else {
                UpdatePageBody(updater, onClose = navigator::back)
            }
        }
    }
}

@Composable
private fun UpdatePageBody(updater: AppUpdater, onClose: () -> Unit) {
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
    Column(Modifier.padding(AppSpacing.page)) {
        UpdateSheetContent(
            state = state,
            installed = updater.installed,
            actions = UpdateSheetActions(
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
            ),
        )
        if (updater.enabled) {
            Spacer(Modifier.height(AppSpacing.lg))
            Text(
                "Only GitHub is contacted. Every update is checked against its release, and Android installs it only if it's " +
                    "signed with the same key as the NormPix on this phone. Your data stays as it is.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.xxl),
            )
        }
    }
}
