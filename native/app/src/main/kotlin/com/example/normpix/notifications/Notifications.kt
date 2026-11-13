package com.example.normpix.notifications

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.normpix.finance.domain.BudgetOverview
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
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.theme.StatusTone
import java.time.LocalDateTime

/**
 * What a notification is about: the order they are listed in, the heading and picture each kind has, and how a count of them is
 * said in a sentence ([one] / [many]).
 */
enum class NotificationKind(val label: String, val icon: ImageVector, private val one: String, private val many: String) {
    Reminders("Reminders", Icons.Outlined.Notifications, "reminder", "reminders"),
    Budgets("Budgets", Icons.Outlined.PieChart, "budget", "budgets"),
    Cards("Cards", Icons.Outlined.CreditCard, "card", "cards"),
    Backup("Backup", Icons.Outlined.Backup, "backup due", "backups due"),
    App("NormPix", Icons.Outlined.Info, "crash report", "crash reports"),

    /**
     * A new version: the one kind Home keeps as its own banner instead of folding it into "Needs attention". On the Notifications page
     * it is listed with the crash report, under the same "NormPix" heading (its own row already says "App update").
     */
    Update("NormPix", Icons.Outlined.SystemUpdate, "update", "updates");

    /** "1 reminder", "3 budgets". */
    fun count(n: Int): String = "$n ${if (n == 1) one else many}"
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
    val items = notificationItems((overview as? AsyncValue.Data)?.value)
    val onSeen = remember(container) { { keys: Set<String> -> container.preferences.markNotificationsSeen(keys) } }
    return Notifications(items, prefs.notificationsSeen, onSeen)
}

/**
 * Everything that needs the user right now, one [AppNotification] for each banner, in the order they are listed (see
 * [NotificationKind]). Home's "Needs attention" reads the same list, so the two can never disagree. [budgets] is the month's
 * overview, or null while it is still being read (no budget notifications until then).
 */
@Composable
internal fun notificationItems(budgets: BudgetOverview?): List<AppNotification> = buildList {
    reminderBanners().forEach { add(AppNotification(NotificationKind.Reminders, it)) }
    if (budgets != null) budgetBanners(budgets).forEach { add(AppNotification(NotificationKind.Budgets, it)) }
    cardExpiryBanners().forEach { add(AppNotification(NotificationKind.Cards, it)) }
    backupBanners().forEach { add(AppNotification(NotificationKind.Backup, it)) }
    crashBanners().forEach { add(AppNotification(NotificationKind.App, it)) }
    updateBanners().forEach { add(AppNotification(NotificationKind.Update, it)) }
}

/** The most this says of what is waiting before "and n more": three kinds is a line, a fourth is a list. */
private const val SUMMARY_KINDS = 3

/** What is waiting in a sentence, kind by kind in the page's order: "2 reminders, 1 budget and 1 card". */
fun attentionSummary(items: List<AppNotification>): String {
    val counts = NotificationKind.entries.mapNotNull { kind -> items.count { it.kind == kind }.takeIf { it > 0 }?.let { kind to it } }
    val shown = counts.take(SUMMARY_KINDS).map { (kind, n) -> kind.count(n) }
    val rest = counts.drop(SUMMARY_KINDS).sumOf { it.second }
    return when {
        rest > 0 -> shown.joinToString(", ") + " and $rest more"
        shown.size > 1 -> shown.dropLast(1).joinToString(", ") + " and " + shown.last()
        else -> shown.firstOrNull().orEmpty()
    }
}

/**
 * The one banner Home shows for everything but an update: how many things are waiting, and in what, as the colour of the worst of
 * them (an overdue reminder or a budget over is an error, anything else a warning, the rest information). It opens the
 * Notifications page ([open]), where each is dealt with.
 */
fun attentionBanner(items: List<AppNotification>, open: () -> Unit): Banner {
    val tone = items.map { it.banner.tone }.let { tones ->
        when {
            StatusTone.Error in tones -> StatusTone.Error
            StatusTone.Warning in tones -> StatusTone.Warning
            else -> StatusTone.Info
        }
    }
    val n = items.size
    return Banner(
        key = "attention",
        tone = tone,
        icon = Icons.Outlined.Notifications,
        eyebrow = "Notifications",
        title = if (n == 1) "1 thing needs attention" else "$n things need attention",
        message = attentionSummary(items),
        // No figure panel: the count is the title, and the whole width is for what it is made of.
        primary = StatusAction("Review", onClick = open),
        onOpen = open,
    )
}

/**
 * The figure a notification's row shows at its end, beside its lines ("Late by / 3 days", "Left / Rs 1,200") - where a row has room
 * to spare (owner, 2026-11-11: "utilize space well"; it was a line of its own under the text). None when the row's words already say
 * it: a backup's "Last backup 30 days ago" is its figure too.
 */
fun Banner.rowFigure(): BannerFigure? = figure?.takeUnless { message.contains(it.figure, ignoreCase = true) }

/**
 * The buttons a notification's row shows, the quieter one first. None when its only button does what pressing the row does (a
 * budget's "View budgets" opens the budgets, as the row does): the row is the button then, and a line of the page is saved.
 */
fun Banner.rowActions(): List<StatusAction> =
    if (secondary == null && primary.onClick === onOpen) emptyList() else listOfNotNull(secondary, primary)
