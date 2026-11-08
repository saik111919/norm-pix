package com.example.normpix.notifications

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.settledCutoff
import com.example.normpix.finance.ui.AsyncValue
import com.example.normpix.finance.ui.rememberFinanceQuery
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.screens.backupBanners
import com.example.normpix.screens.budgetBanners
import com.example.normpix.screens.cardExpiryBanners
import com.example.normpix.screens.crashBanners
import com.example.normpix.screens.reminderBanners
import com.example.normpix.screens.updateBanners
import com.example.normpix.ui.components.Banner
import java.time.LocalDateTime

/** What a notification is about: the order they are listed in, and the heading and picture each kind has. */
enum class NotificationKind(val label: String, val icon: ImageVector) {
    Reminders("Reminders", Icons.Outlined.Notifications),
    Budgets("Budgets", Icons.Outlined.PieChart),
    Cards("Cards", Icons.Outlined.CreditCard),
    Backup("Backup", Icons.Outlined.Backup),
    App("NormPix", Icons.Outlined.Info),
}

/** One thing that needs the user: its [banner] says what and how to deal with it, [kind] where it is listed. */
@Immutable
class AppNotification(val kind: NotificationKind, val banner: Banner) {
    /** Stable while the thing is true, so it is seen once (see [Notifications.unread]). */
    val key: String get() = banner.key
}

/**
 * Everything that needs the user, in one place: the same facts Home's "Needs attention" shows (overdue reminders, budgets over or
 * nearly used, cards to renew, a crash report, a backup, an update), as one list, the most pressing kind first. There is no
 * history: a notification is as long-lived as the thing it says, and goes when it is dealt with.
 *
 * [seen] are the keys the user had seen the last time the Notifications page was open; the rest are [unread] - what the bell
 * counts and what the "+" menu shows. Seeing the page is seeing them all ([markAllSeen]).
 */
@Stable
class Notifications(
    val items: List<AppNotification>,
    val seen: Set<String>,
    private val onSeen: (Set<String>) -> Unit,
) {
    val keys: Set<String> = items.mapTo(LinkedHashSet()) { it.key }
    val unread: List<AppNotification> = items.filter { it.key !in seen }

    /** Says what is listed now was seen, and forgets what is not listed any more, so it is new again if it comes back. */
    fun markAllSeen() = onSeen(keys)

    companion object {
        val None = Notifications(emptyList(), emptySet()) {}
    }
}

/** The notifications, from where the app's pages are: read by the bell, the page and the "+" menu. */
val LocalNotifications = compositionLocalOf { Notifications.None }

/**
 * Provides [LocalNotifications] to [content]. Kept in a composable of its own so that a notification coming or going recomposes
 * what reads it - the bell, the page, the menu - and not everything under it.
 */
@Composable
fun ProvideNotifications(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNotifications provides rememberNotifications(), content = content)
}

/** The notifications of this moment, gathered from the sources Home's "Needs attention" is made of. */
@Composable
fun rememberNotifications(): Notifications {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    // The month's budgets as Home reads them: this month, as of today (an entry dated ahead is upcoming, not spent).
    val overview by rememberFinanceQuery(Unit) {
        val now = LocalDateTime.now()
        budgetOverview(MonthKey.of(now), asOf = settledCutoff(now))
    }
    val budgets = (overview as? AsyncValue.Data)?.value

    val items = buildList {
        reminderBanners().forEach { add(AppNotification(NotificationKind.Reminders, it)) }
        if (budgets != null) budgetBanners(budgets).forEach { add(AppNotification(NotificationKind.Budgets, it)) }
        cardExpiryBanners().forEach { add(AppNotification(NotificationKind.Cards, it)) }
        backupBanners().forEach { add(AppNotification(NotificationKind.Backup, it)) }
        crashBanners().forEach { add(AppNotification(NotificationKind.App, it)) }
        updateBanners().forEach { add(AppNotification(NotificationKind.App, it)) }
    }
    val onSeen = remember(container) { { keys: Set<String> -> container.preferences.markNotificationsSeen(keys) } }
    return Notifications(items, prefs.notificationsSeen, onSeen)
}
