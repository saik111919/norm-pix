package com.example.normpix.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.BannerBar
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.ConnectedButtonGroup
import com.example.normpix.ui.components.GroupButton
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.readableOn
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.resolve

/**
 * Everything that needs the user, as the pages of this app list things: a titled group for each kind (Reminders, Budgets, Cards...)
 * and, in it, one tonal row for each notification - what it is in the status colour, what it is about, and the buttons to deal with
 * it. The row opens what it is about. Opening the page is seeing it all: the bell goes quiet, and anything that comes back later is
 * new. With nothing to show it says so; the way to the settings of what NormPix may notify about is at the foot.
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
            // By heading, in the kinds' order: the crash report and the update share "NormPix".
            for (heading in NotificationKind.entries.map { it.label }.distinct()) {
                val group = notifications.items.filter { it.kind.label == heading }
                if (group.isEmpty()) continue
                item(key = "group-$heading") {
                    Column(Modifier.padding(AppSpacing.page)) {
                        ListSection(title = heading) {
                            // The last row of a group has the group's large bottom corners, the others the small ones between rows:
                            // its buttons follow whichever it is (see NotificationRow).
                            group.forEachIndexed { index, notification ->
                                item { NotificationRow(notification, last = index == group.lastIndex) }
                            }
                        }
                    }
                }
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

/**
 * One notification as a row of its group, using the row's whole width (owner, 2026-11-11: "utilize space well"): the picture, then
 * the word that says what it is in the status colour (a status is never colour alone), what it is about and one line of detail, with
 * its figure at the end of the row ("Late by / 3 days") where there was only empty tone. A [Banner]'s bar (a budget's use, an
 * update's download) runs under the text. Its buttons are one connected group across the row - the quieter one first - as a banner's
 * are; a row whose only button would do what pressing the row does has none ([rowActions]).
 */
@Composable
private fun NotificationRow(item: AppNotification, last: Boolean) {
    val banner = item.banner
    val role = banner.tone.resolve()
    val scheme = MaterialTheme.colorScheme
    // The word is checked against the row's own surface, as Home's banners check theirs (4.5:1, else plain ink).
    val word = remember(role, scheme) { readableOn(scheme.surfaceContainer, role.color, scheme.onSurface) }
    val figure = banner.rowFigure()
    val actions = banner.rowActions()
    Column {
        ListRow(
            headline = banner.title,
            overline = banner.eyebrow,
            overlineColor = word,
            supporting = banner.message,
            leading = { LeadingIcon(banner.icon, background = role.color, foreground = role.onColor) },
            trailing = figure?.let { { RowFigure(it) } },
            onClick = banner.onOpen,
        )
        banner.bar?.let { NotificationBar(it, role.color) }
        if (actions.isNotEmpty()) {
            ConnectedButtonGroup(
                buttons = actions.map { action ->
                    // The main one (last) in the tonal accent, the quieter one in the row's neutral: one group, two weights.
                    val main = action === banner.primary
                    GroupButton(
                        label = action.label,
                        icon = action.icon,
                        container = if (main) scheme.secondaryContainer else scheme.surfaceContainerHighest,
                        content = if (main) scheme.onSecondaryContainer else scheme.onSurface,
                        onClick = action.onClick,
                    )
                },
                // A bar along the row's foot, set in by [BarInset] and following the row's own corners (owner, 2026-11-13: "follow the
                // background shape", as the banners' bar does): each end is the row's corner less the inset - the large corner of a
                // group's last row, the small one between rows - and along the top, the small corner of a bar under text.
                modifier = Modifier.fillMaxWidth().padding(start = BarInset, end = BarInset, bottom = BarInset),
                outerTop = CornerSize(AppRadius.sm),
                outerBottom = CornerSize(if (last) AppRadius.xl - BarInset else AppRadius.xs),
            )
        }
    }
}

/** The room round a row's buttons, between them and the edge of the row. */
private val BarInset = AppSpacing.xs

/** A row's figure, at its end: what the figure is, small, over the figure itself. */
@Composable
private fun RowFigure(figure: BannerFigure) {
    val scheme = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.End) {
        Text(figure.title, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
        Text(
            figure.figure,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            color = scheme.onSurface,
            maxLines = 1,
        )
    }
}

/** What a row's bar says, under its text and in line with it: the line, and in words how far along it is ("85% used"). */
@Composable
private fun NotificationBar(bar: BannerBar, color: Color) {
    val scheme = MaterialTheme.colorScheme
    Row(
        // In line with the text above, which starts after the 40 dp picture and the row's own gutters.
        Modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.lg + LEADING_SIZE + AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.md)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        // Calm: a row's bar is not a standalone progress (see the design system), and the wave is for those.
        AppLinearProgress(
            progress = { bar.fraction },
            color = color,
            trackColor = scheme.surfaceContainerHighest,
            calm = true,
            modifier = Modifier.weight(1f),
        )
        Text(bar.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

/** The width of a row's picture ([LeadingIcon]'s own default), so what hangs under the text lines up with it. */
private val LEADING_SIZE = 40.dp

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
