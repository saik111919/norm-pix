package com.example.normpix.updates.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.ui.automaticUpdatesSummary
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.rememberAppHaptics
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
 * Under that come the changelog pill (which opens [ChangelogPage] on tap) and the way to Automatic updates, so this one page
 * is where everything about updating is.
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
    val prefs by LocalAppContainer.current.preferences.state.collectAsStateWithLifecycle()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val navigator = LocalNavigator.current
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
            UpdateScreenBody(state, updater.installed, Modifier.padding(horizontal = AppSpacing.xl))
        }
        item(key = "changelog") {
            Box(Modifier.padding(horizontal = AppSpacing.xl).padding(top = AppSpacing.lg)) {
                ChangelogPill(onClick = navigator::openChangelog)
            }
        }
        if (updater.enabled) {
            item(key = "settings") {
                ListSection(title = "Update settings", modifier = Modifier.padding(AppSpacing.page)) {
                    item {
                        ListRow(
                            headline = "Automatic updates",
                            supporting = automaticUpdatesSummary(prefs, is24Hour),
                            leading = { LeadingIcon(Icons.Outlined.Update) },
                            onClick = { navigator.openSettings(SettingsPage.Updates) },
                        )
                    }
                }
            }
            item(key = "privacy") {
                Text(
                    "Only GitHub is contacted. Every update is checked against its release, and Android installs it only if it's " +
                        "signed with the same key as the NormPix on this phone. Your data stays as it is.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.xl).padding(top = AppSpacing.lg),
                )
            }
        }
    }
}

/** A pill button that opens the full changelog page. */
@Composable
fun ChangelogPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        shape = CircleShape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.heightIn(min = 36.dp).padding(start = AppSpacing.md, end = AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.sm))
            Text("Changelog", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
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
