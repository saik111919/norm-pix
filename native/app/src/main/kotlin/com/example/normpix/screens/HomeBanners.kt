package com.example.normpix.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.BudgetOverview
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.ui.budgetIcon
import com.example.normpix.finance.ui.budgetTone
import com.example.normpix.finance.ui.monthLabel
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.PlanSection
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.reminders.formatDueMoment
import com.example.normpix.reminders.formatOverdueBy
import com.example.normpix.reminders.icon
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.BannerStack
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SectionHeader
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.expiry.printed
import com.example.normpix.wallet.expiry.title
import com.example.normpix.wallet.rememberCardFlows
import kotlinx.coroutines.launch

/**
 * What needs attention, right under the month: one stack of banners for each kind of thing - overdue reminders, budgets that
 * are over or nearly used, cards about to expire - the most pressing in front of each stack. Pressing and holding a stack lays
 * its banners out, each with its own buttons, the way the cards and the top spending do. Nothing at all when nothing needs it.
 */
@Composable
internal fun NeedsAttention(budgets: BudgetOverview) {
    val stacks = listOf(reminderBanners(), budgetBanners(budgets), cardExpiryBanners()).filter { it.isNotEmpty() }
    if (stacks.isEmpty()) return
    SectionHeader(title = "Needs attention", padding = HomeSectionPadding)
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        for (banners in stacks) BannerStack(banners)
    }
}

/** Every overdue reminder, the longest overdue first. */
@Composable
private fun reminderBanners(): List<Banner> {
    val remindersState by LocalReminders.current.state.collectAsState()
    val flows = rememberReminderFlows()
    return remindersState.overdue().map { reminder ->
        Banner(
            key = "reminder-${reminder.id ?: reminder.title}",
            tone = StatusTone.Error,
            icon = reminder.category.icon,
            eyebrow = formatOverdueBy(reminder.effectiveDueAt),
            title = reminder.title,
            message = "Was due ${formatDueMoment(reminder.effectiveDueAt)}",
            primary = StatusAction("Mark as done", onClick = { flows.complete(reminder) }),
            secondary = StatusAction("Snooze", onClick = { flows.snooze(reminder) }),
            onOpen = { flows.edit(reminder) },
        )
    }
}

/** Every budget that is over or nearly used: exceeded before approaching, then the most used first. */
@Composable
private fun budgetBanners(budgets: BudgetOverview): List<Banner> {
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
                message = if (over) {
                    "${finance.money.format(-status.remaining)} over for $month"
                } else {
                    "${finance.money.format(status.remaining)} left for $month"
                },
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
private fun cardExpiryBanners(): List<Banner> {
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
    LaunchedEffect(cardsState, container.expiry) {
        (cardsState as? CardsState.Ready)?.let { container.expiry.sync(it.cards) }
    }
    var notificationsOn by remember { mutableStateOf(container.expiry.notificationsEnabled()) }
    LifecycleResumeEffect(container.expiry) {
        notificationsOn = container.expiry.notificationsEnabled() // may have been changed in system settings
        onPauseOrDispose {}
    }
    @Suppress("UNUSED_EXPRESSION")
    lockVersion // re-evaluate the lock whenever it may have changed

    val due = (cardsState as? CardsState.Ready)?.cards.orEmpty()
        .mapNotNull { card -> expiryStates[card.id]?.takeIf { it.needsAttention }?.let { card to it } }
        .sortedBy { (_, state) -> state.expiry }
    if (due.isEmpty()) return emptyList()

    if (container.lock.isWalletLocked(prefs)) {
        val unlock: () -> Unit = {
            scope.launch {
                if (!container.lock.authenticate("Unlock your wallet")) snackbars.show("Couldn't unlock. Try again.")
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
    return due.map { (card, state) ->
        val valid = "Valid thru ${state.printed()}"
        Banner(
            key = "card-${card.id}",
            tone = StatusTone.Warning,
            icon = if (state.expired) Icons.Outlined.EventBusy else Icons.Outlined.Schedule,
            eyebrow = state.title(),
            title = "${card.bankName} •••• ${card.last4}",
            // With notifications off this banner is the only reminder there is, and says so.
            message = if (notificationsOn) valid else "$valid · Notifications are off",
            primary = StatusAction(
                "Mark as done",
                onClick = {
                    haptics.success()
                    container.expiry.markDone(card.id)
                },
            ),
            secondary = if (notificationsOn) null else StatusAction("Turn on", onClick = flows::turnOnExpiryNotifications),
            onOpen = { flows.showCard(card) },
        )
    }
}
