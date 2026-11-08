package com.example.normpix.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.BannerCard
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SectionHeader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing

/**
 * Everything that needs the user, as Home's banners are but all in one place, grouped by what it is about and each laid out in
 * full with its own buttons. Opening the page is seeing it all: the bell goes quiet, and anything that comes back later is new.
 * With nothing to show it says so; the way to the settings of what NormPix may notify about is at the foot.
 */
@Composable
fun NotificationsPage() {
    val navigator = LocalNavigator.current
    val notifications = LocalNotifications.current
    // Keyed on what is listed, so a notification arriving while the page is open is seen as well.
    LaunchedEffect(notifications.keys) { notifications.markAllSeen() }

    AppSubPage(title = "Notifications", onBack = navigator::back, maxWidth = AppLayout.maxListWidth) {
        if (notifications.items.isEmpty()) {
            item(key = "empty") {
                AppEmptyState(
                    icon = Icons.Outlined.NotificationsNone,
                    title = "You're all caught up",
                    message = "Overdue reminders, budgets that are over or nearly used, cards about to expire, a backup that is due " +
                        "and a new version of NormPix show up here when they need you.",
                )
            }
        } else {
            for (kind in NotificationKind.entries) {
                val group = notifications.items.filter { it.kind == kind }
                if (group.isEmpty()) continue
                item(key = "group-${kind.name}") { NotificationGroup(kind, group) }
            }
        }
        item(key = "settings") {
            Column(Modifier.padding(AppSpacing.page).padding(top = AppSpacing.lg)) {
                ListSection {
                    item {
                        ListRow(
                            headline = "Notification settings",
                            supporting = "What NormPix may notify you about, and how",
                            leading = { LeadingIcon(Icons.Outlined.Settings) },
                            onClick = { navigator.openSettings(SettingsPage.Notifications) },
                        )
                    }
                }
            }
        }
    }
}

/** One kind's notifications, each laid out in full (not stacked): a banner with its own lines and buttons. */
@Composable
private fun NotificationGroup(kind: NotificationKind, items: List<AppNotification>) {
    Column {
        SectionHeader(title = kind.label)
        Column(
            Modifier.padding(AppSpacing.page),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            for (item in items) BannerCard(item.banner)
        }
    }
}

/**
 * The bell on a page's top row: the way to the Notifications page from anywhere, with how many are new on it. Nothing new and it
 * is a plain bell.
 */
@Composable
fun NotificationBell() {
    val navigator = LocalNavigator.current
    val haptics = rememberAppHaptics()
    val unread = LocalNotifications.current.unread.size
    IconButton(
        onClick = {
            haptics.selection()
            navigator.openNotifications()
        },
    ) {
        BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 9) "9+" else "$unread") } }) {
            Icon(
                Icons.Outlined.Notifications,
                contentDescription = if (unread == 0) NOTIFICATIONS_LABEL else "$NOTIFICATIONS_LABEL, $unread new",
            )
        }
    }
}

/** What a screen reader calls the bell. */
const val NOTIFICATIONS_LABEL = "Notifications"

/** The bell as the design system's pages take it on their top row (see `LocalTopBarExtras`). */
val NotificationBellExtra: @Composable () -> Unit = { NotificationBell() }
