package com.example.normpix.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
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
import com.example.normpix.backup.BackupFreshness
import com.example.normpix.diagnostics.CrashLog
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.BudgetOverview
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.budgetIcon
import com.example.normpix.finance.ui.budgetTone
import com.example.normpix.finance.ui.monthLabel
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.PlanSection
import com.example.normpix.navigation.SettingsPage
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
import com.example.normpix.ui.components.StackButton
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
 * What needs attention, right under the month: one stack of banners for each kind of thing - overdue reminders, budgets that
 * are over or nearly used, cards about to expire, an update to install - the most pressing in front of each stack. Pressing and
 * holding a stack lays its banners out, each with its own buttons, the way the cards and the top spending do. Nothing at all
 * when nothing needs it.
 */
@Composable
internal fun NeedsAttention(budgets: BudgetOverview) {
    val stacks = listOf(reminderBanners(), budgetBanners(budgets), cardExpiryBanners(), crashBanners(), backupBanners(), updateBanners())
    // One for each kind of banner, since each is its own stack: the header's button gathers whichever are spread out.
    val decks = List(stacks.size) { rememberDeckStackState() }
    if (stacks.all { it.isEmpty() }) return
    SectionHeader(title = "Needs attention", padding = HomeSectionPadding, trailing = { StackButton(*decks.toTypedArray()) })
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        stacks.forEachIndexed { index, banners ->
            if (banners.isNotEmpty()) BannerStack(banners, decks[index])
        }
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
                // How much of it is used, as the pill; what is over or left, as the figure.
                bar = BannerBar(
                    fraction = status.utilization.toFloat().coerceIn(0f, 1f),
                    label = "${status.usedPercent}% used",
                    from = finance.money.format(0L),
                    to = finance.money.format(status.allocated, trimZeroFraction = true),
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
