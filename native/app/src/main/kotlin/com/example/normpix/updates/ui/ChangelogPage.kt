package com.example.normpix.updates.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.updates.AppUpdater
import kotlinx.coroutines.launch

private const val PAGE_TITLE = "Changelog"

/**
 * The changelog page: what changed across NormPix releases.
 *
 * It is opened from the Changelog pill on the update page ([UpdatePage]). When opened, it reads what
 * each published version changed from GitHub (or what was saved earlier when offline).
 */
@Composable
fun ChangelogPage() {
    val navigator = LocalNavigator.current
    val updater = LocalAppContainer.current.updates
    if (updater == null) {
        AppSubPage(title = PAGE_TITLE, onBack = navigator::back) {
            item(key = "empty") {
                AppEmptyState(
                    icon = Icons.Outlined.UpdateDisabled,
                    title = "Updates come with release builds",
                    message = "Only the release build of NormPix updates itself from GitHub.",
                )
            }
        }
        return
    }
    ChangelogPageFor(updater, onClose = navigator::back)
}

@Composable
private fun ChangelogPageFor(updater: AppUpdater, onClose: () -> Unit) {
    val changelog by updater.changelog.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(updater) {
        updater.loadChangelog()
    }
    AppSubPage(
        title = PAGE_TITLE,
        onBack = onClose,
    ) {
        item(key = "changelog") {
            ChangelogSection(
                state = changelog,
                installed = updater.installed,
                onRetry = { scope.launch { updater.loadChangelog() } },
                modifier = Modifier.padding(AppSpacing.page),
            )
        }
    }
}
