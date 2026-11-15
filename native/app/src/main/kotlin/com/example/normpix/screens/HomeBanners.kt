package com.example.normpix.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.normpix.about.ui.CrashLogSheet
import com.example.normpix.backup.AutoBackupProblem
import com.example.normpix.backup.BackupFreshness
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.diagnostics.CrashLog
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.BudgetOverview
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.domain.CardLimitAlert
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.cardLimitAlerts
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.budgetIcon
import com.example.normpix.finance.ui.budgetTone
import com.example.normpix.finance.ui.monthLabel
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.PlanSection
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.notifications.LocalNotifications
import com.example.normpix.notifications.NotificationKind
import com.example.normpix.notifications.Notifications
import com.example.normpix.notifications.attentionBanner
import com.example.normpix.notifications.notificationItems
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.reminders.formatDueMoment
import com.example.normpix.reminders.formatOverdueLong
import com.example.normpix.reminders.icon
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.BannerBar
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.BannerStack
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SectionHeader
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.components.rememberDeckStackState
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.updates.release
import com.example.normpix.updates.ui.updateActionsFor
import com.example.normpix.updates.ui.updateBannerFor
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.expiry.cardExpiryBanner
import com.example.normpix.wallet.rememberCardFlows
import kotlinx.coroutines.launch

/**
 * What needs attention, right under the month: **one banner** saying how many things are waiting and in what (overdue reminders,
 * budgets over or nearly used, cards about to expire, a crash report, a backup due), which opens the Notifications page where
 * each is dealt with (owner, 2026-11-09). An update to install is the exception and stays its own banner, with its own buttons,
 * as it always was. Nothing at all when nothing needs it.
 */
@Composable
internal fun NeedsAttention(budgets: BudgetOverview) {
    val navigator = LocalNavigator.current
    // What the app has already gathered for the bell, where it has (the running app provides it above every page): until
    // 2026-11-12 Home gathered the same six sources a second time - each with its own reads of the reminders, the cards, the
    // settings and the crash log - to show one banner. On its own (a test, a preview) Home still gathers them itself.
    val gathered = LocalNotifications.current
    val items = if (gathered !== Notifications.None) gathered.items else notificationItems(budgets)
    val waiting = items.filter { it.kind != NotificationKind.Update }
    val update = items.filter { it.kind == NotificationKind.Update }.map { it.banner }
    // Each banner is a stack of its own (of one), so each has its own state.
    val waitingDeck = rememberDeckStackState()
    val updateDeck = rememberDeckStackState()
    if (items.isEmpty()) return
    SectionHeader(title = "Needs attention", padding = HomeSectionPadding)
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        if (waiting.isNotEmpty()) BannerStack(listOf(attentionBanner(waiting, open = navigator::openNotifications)), waitingDeck)
        if (update.isNotEmpty()) BannerStack(update, updateDeck)
    }
}

/** Every overdue reminder, the longest overdue first. */
@Composable
internal fun reminderBanners(): List<Banner> {
    val remindersState by LocalReminders.current.state.collectAsState()
    val flows = rememberReminderFlows()
    return remindersState.overdue().map { reminder ->
        Banner(
            key = "reminder-${reminder.id ?: reminder.title}",
            tone = StatusTone.Error,
            icon = reminder.category.icon,
            eyebrow = "Overdue",
            title = reminder.title,
            message = reminder.category.label,
            figure = BannerFigure(
                title = "Late by",
                figure = formatOverdueLong(reminder.effectiveDueAt),
                note = "Was due ${formatDueMoment(reminder.effectiveDueAt)}",
            ),
            primary = StatusAction(reminder.doneLabel, onClick = { flows.complete(reminder) }),
            secondary = StatusAction("Snooze", onClick = { flows.snooze(reminder) }),
            onOpen = { flows.edit(reminder) },
        )
    }
}

/** Every budget that is over or nearly used: exceeded before approaching, then the most used first. */
@Composable
internal fun budgetBanners(budgets: BudgetOverview): List<Banner> {
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    val open: () -> Unit = { navigator.openPlans(PlanSection.Budgets) }
    val month = monthLabel(budgets.month)
    val named = buildList<Triple<String, String, BudgetStatus>> {
        budgets.overall?.let { add(Triple("overall", "Monthly budget", it)) }
        for ((id, status) in budgets.categories) add(Triple("category-$id", "${finance.category(id)?.name ?: "Category"} budget", status))
    }
    return named
        .filter { it.third.health != BudgetHealth.Healthy }
        .sortedByDescending { it.third.utilization }
        .map { (key, name, status) ->
            val over = status.health == BudgetHealth.Exceeded
            Banner(
                key = "budget-$key",
                tone = budgetTone(status.health),
                icon = budgetIcon(status.health),
                eyebrow = if (over) "Over budget" else "Nearly used",
                title = name,
                message = "${finance.money.format(status.allocated, trimZeroFraction = true)} for $month",
                // How much of it is used, as the ring ("120%" past the budget, the ring then full); what is over or left, as the figure.
                bar = BannerBar(
                    fraction = status.utilization.toFloat().coerceIn(0f, 1f),
                    label = "${status.usedPercent}% used",
                    short = "${status.usedPercent}%",
                ),
                figure = BannerFigure(
                    title = if (over) "Over by" else "Left",
                    figure = finance.money.format(if (over) -status.remaining else status.remaining),
                ),
                primary = StatusAction("View budgets", onClick = open),
                onOpen = open,
            )
        }
}

/**
 * Every credit card that has used the share of its limit the user chose to hear about (Settings > Wallet & Cards > Card limit
 * warning, 30% unless changed; none at all when it is Off), the most used first. They are the account's own balance against its own
 * limit, so they are there with Store cards off too, and a card paid down under the level is not listed at the next look: nothing
 * about it is stored. The button opens the card's account page.
 */
@Composable
internal fun cardLimitBanners(): List<Banner> {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    return cardLimitAlerts(finance.accounts, finance::balanceOf, prefs.cardLimitWarning.percent).map { alert ->
        cardLimitBanner(alert, finance.money, open = { navigator.openAccountHistory(alert.account.id!!) })
    }
}

/**
 * One card's limit warning, made as a budget's is: a warning, or an error once the card is over its limit; the share used as the
 * ring; what is owed against the limit as the message; and what is left (or by how much it is over) as the figure. Every amount
 * goes through [money], so it is hidden when amounts are.
 */
internal fun cardLimitBanner(alert: CardLimitAlert, money: MoneyFormatter, open: () -> Unit): Banner {
    val room = alert.room
    val over = alert.over
    val percent = alert.usedPercent
    return Banner(
        key = "card-limit-${alert.account.id}",
        tone = if (over) StatusTone.Error else StatusTone.Warning,
        icon = Icons.Outlined.CreditCard,
        // Short, because the panel beside the figure is narrow: the ring says the share, so the words need not ("63% of the limit
        // used" and "...of ₹1,00,000 used" were cut with an ellipsis at 360 dp, seen in a render).
        eyebrow = if (over) "Over the limit" else "Limit used",
        title = alert.account.name,
        message = "${money.format(room.owed, trimZeroFraction = true)} of ${money.format(room.limit, trimZeroFraction = true)}",
        // The ring is full past the limit and says the real figure ("120%"); a screen reader hears "120% used".
        bar = BannerBar(fraction = room.used.toFloat(), label = "$percent% used", short = "$percent%"),
        figure = BannerFigure(
            title = if (over) "Over by" else "Available",
            figure = money.format(if (over) -room.available else room.available),
        ),
        primary = StatusAction("View card", onClick = open),
        onOpen = open,
    )
}

/**
 * Every card at or past its expiry that was not marked done, the soonest to expire first. Behind the wallet lock it is one
 * banner asking to unlock instead - which card, by bank and last digits, is the wallet's to say, not Home's.
 */
@Composable
internal fun cardExpiryBanners(): List<Banner> {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val cardsState by container.cards.state.collectAsState()
    val lockVersion by container.lock.version.collectAsState()
    val expiryStates by container.expiry.states.collectAsState()
    val flows = rememberCardFlows()
    val haptics = rememberAppHaptics()
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()

    // The app keeps the expiry reminders in step with the card list on its own; this covers the sample-data cards, which have
    // their own, silent tracker (the Wallet tab does the same).
    LaunchedEffect(cardsState, container.expiry, prefs.walletCardsEnabled) {
        if (prefs.walletCardsEnabled) (cardsState as? CardsState.Ready)?.let { container.expiry.sync(it.cards) }
    }
    var notificationsOn by remember { mutableStateOf(container.expiry.notificationsEnabled()) }
    LifecycleResumeEffect(container.expiry) {
        notificationsOn = container.expiry.notificationsEnabled() // may have been changed in system settings
        onPauseOrDispose {}
    }
    @Suppress("UNUSED_EXPRESSION")
    lockVersion // re-evaluate the lock whenever it may have changed

    // With Store cards off (Settings > Wallet & Cards) no card is expiring: there are none, even in a sample set.
    val due = (if (prefs.walletCardsEnabled) (cardsState as? CardsState.Ready)?.cards.orEmpty() else emptyList())
        .mapNotNull { card -> expiryStates[card.id]?.takeIf { it.needsAttention }?.let { card to it } }
        .sortedBy { (_, state) -> state.expiry }
    if (due.isEmpty()) return emptyList()

    if (container.lock.isWalletLocked(prefs)) {
        val unlock: () -> Unit = {
            scope.launch {
                if (!container.lock.authenticate("Unlock your wallet")) snackbars.show("Couldn't unlock. Try again.", SnackbarKind.Error)
            }
        }
        return listOf(
            Banner(
                key = "cards-locked",
                tone = StatusTone.Warning,
                icon = Icons.Outlined.Lock,
                eyebrow = "Card expiry",
                title = if (due.size == 1) "A card needs attention" else "${due.size} cards need attention",
                message = "Unlock your wallet to see which",
                primary = StatusAction("Unlock", onClick = unlock),
                onOpen = unlock,
            ),
        )
    }
    // The same banner the Wallet page shows above the card (see cardExpiryBanner).
    return due.map { (card, state) ->
        cardExpiryBanner(
            key = "card-${card.id}",
            card = "${card.bankName} •••• ${card.last4}",
            state = state,
            notificationsOn = notificationsOn,
            onMarkDone = {
                haptics.success()
                container.expiry.markDone(card.id)
            },
            onTurnOnNotifications = flows::turnOnExpiryNotifications,
            onOpen = { flows.showCard(card) },
        )
    }
}

/**
 * The last time NormPix closed unexpectedly, offered on the next start (see `diagnostics/CrashLog.kt`): one banner while there is a
 * report newer than the last one seen. View opens the reports (which counts as seeing them); Dismiss just says they were seen.
 */
@Composable
internal fun crashBanners(): List<Banner> {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val context = LocalContext.current
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    // Read from storage once, and again after the user has seen what is there.
    val newest = remember(prefs.crashLogSeenAt) { CrashLog.of(context).newestAt() }
    if (newest == 0L || newest <= prefs.crashLogSeenAt) return emptyList()
    val open: () -> Unit = { scope.launch { overlays.sheet<Unit>(expandable = true) { done -> CrashLogSheet { done(Unit) } } } }
    return listOf(
        Banner(
            key = "crash-$newest",
            tone = StatusTone.Warning,
            icon = Icons.Outlined.ReportProblem,
            eyebrow = "Closed unexpectedly",
            title = "NormPix stopped working last time",
            message = "A report was kept on this phone",
            primary = StatusAction("View", onClick = open),
            secondary = StatusAction("Dismiss", onClick = { container.preferences.markCrashLogSeen(newest) }),
            onOpen = open,
        ),
    )
}

/**
 * "Back up your data": one banner, or none (see [BackupFreshness.nudge] for when). Everything NormPix keeps is on this one phone,
 * so a month without a copy is the way to lose it all. Its button opens Settings > Data & backup, where the three kinds of backup
 * are (each is saved where the user chooses, and asks whether to lock it); "Later" puts it off for a few days. Never for the
 * sample set, and never while there is nothing worth keeping.
 */
@Composable
internal fun backupBanners(): List<Banner> {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val installedAt = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }.getOrDefault(0L)
    }
    // What went wrong with the last automatic backup comes first, and alone: it says more than "back up your data", and opens the
    // same page. It is about the user's own data, so it is shown whichever set the screens show.
    autoBackupProblemBanner(prefs, System.currentTimeMillis(), open = { navigator.openSettings(SettingsPage.Data) })?.let { return listOf(it) }
    if (prefs.dummyDataEnabled || finance.status != FinanceStatus.Ready) return emptyList()
    // Worth keeping: the starting Cash account alone, with nothing in it, is not.
    val hasData = finance.accounts.size > 1 || finance.people.isNotEmpty() || finance.balances.values.any { it != 0L }
    val nudge = BackupFreshness.nudge(prefs.lastBackupAt, installedAt, System.currentTimeMillis(), prefs.backupReminderDays, hasData, prefs.backupNudgeSnoozedUntil)
    val open: () -> Unit = { navigator.openSettings(SettingsPage.Data) }
    val (tone, eyebrow, message) = when (nudge) {
        BackupFreshness.Nudge.None -> return emptyList()
        is BackupFreshness.Nudge.Never -> Triple(StatusTone.Warning, "Never backed up", "Nothing is saved elsewhere")
        is BackupFreshness.Nudge.Stale ->
            Triple(if (nudge.overdue) StatusTone.Warning else StatusTone.Info, "Backup", "Last backup ${BackupFreshness.ago(prefs.lastBackupAt, System.currentTimeMillis())}")
    }
    return listOf(
        Banner(
            key = "backup",
            tone = tone,
            icon = Icons.Outlined.Backup,
            eyebrow = eyebrow,
            title = "Back up your data",
            message = message,
            figure = BannerFigure(
                title = "Last backup",
                figure = if (nudge is BackupFreshness.Nudge.Stale) "${nudge.days} days" else "Never",
                note = "Asks ${BackupFreshness.words(prefs.backupReminderDays)}",
            ),
            primary = StatusAction("Back up", onClick = open),
            secondary = StatusAction("Later", onClick = { container.preferences.snoozeBackupNudge(BackupFreshness.snoozeUntil(System.currentTimeMillis())) }),
            onOpen = open,
        ),
    )
}

/**
 * What went wrong with the last automatic backup (see `backup/AutoBackup.kt`), as a banner under Backup, while automatic backups are
 * on and until one goes well; null when there is nothing to say. Its button opens Settings > Data & backup ([open]), where the
 * folder or the password is set again. Keyed on the problem and when it happened, so a new one is new to the bell.
 */
internal fun autoBackupProblemBanner(prefs: AppPrefsState, now: Long, open: () -> Unit): Banner? {
    if (!prefs.autoBackupEnabled) return null
    val problem = prefs.autoBackupProblem ?: return null
    return Banner(
        key = "auto-backup-${problem.name}-${prefs.autoBackupProblemAt}",
        tone = if (problem == AutoBackupProblem.CardsLeftOut) StatusTone.Info else StatusTone.Warning,
        icon = Icons.Outlined.Backup,
        eyebrow = "Automatic backup",
        title = problem.title,
        message = problem.short,
        figure = BannerFigure(
            title = "Last backup",
            figure = if (prefs.autoBackupLastAt > 0) BackupFreshness.ago(prefs.autoBackupLastAt, now).replaceFirstChar(Char::uppercase) else "None yet",
            note = prefs.autoBackupEvery.label,
        ),
        primary = StatusAction("Open settings", onClick = open),
        onOpen = open,
    )
}

/**
 * A new version of NormPix, while there is one to act on, as one banner that follows the update through its steps (see
 * [updateBannerFor]). Its buttons do what the update sheet's do, and pressing the banner opens the App update page. Nothing in a
 * build that doesn't update itself, and nothing for a release the user said Later to (as at launch).
 */
@Composable
internal fun updateBanners(): List<Banner> {
    val updater = LocalAppContainer.current.updates?.takeIf { it.enabled } ?: return emptyList()
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val state by updater.state.collectAsState()
    val laterCode by updater.laterVersionCode.collectAsState()
    val release = state.release ?: return emptyList()
    // Keyed on what Later last said, so putting the update off takes the banner down at once.
    val offered = remember(release, laterCode) { updater.isOffered(release) }
    val banner = updateBannerFor(
        state = state,
        offered = offered,
        installedVersion = updater.installed.versionName,
        bytes = { Formatter.formatShortFileSize(context, it) },
        actions = updateActionsFor(updater, onClose = {}),
        open = { navigator.openUpdates() },
    )
    return listOfNotNull(banner)
}
