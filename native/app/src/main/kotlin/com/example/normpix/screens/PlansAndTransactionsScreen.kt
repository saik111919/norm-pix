package com.example.normpix.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.BudgetsView
import com.example.normpix.finance.ui.RecurringView
import com.example.normpix.finance.ui.TransactionHistory
import com.example.normpix.navigation.AmountsVisibilityButton
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.LocalPageAnimation
import com.example.normpix.navigation.LocalShellState
import com.example.normpix.navigation.PlanSection
import com.example.normpix.navigation.PlansAndTransactionsTab
import com.example.normpix.navigation.TabHost
import com.example.normpix.reminders.OverdueBadge
import com.example.normpix.reminders.OverdueNotice
import com.example.normpix.reminders.RemindersView
import com.example.normpix.reminders.rememberOverdueCount
import com.example.normpix.ui.components.AppPage
import com.example.normpix.ui.components.AppPrimaryTabs
import com.example.normpix.ui.components.AppSecondaryTabs
import com.example.normpix.ui.components.AppTab
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.sectionTransition
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion

/**
 * The Plans & Transactions tab: the transaction history, and Plans - monthly
 * budgets, recurring payments and reminders - behind a primary tab switch held
 * under the app bar. Plans adds a lighter secondary tab row for its three sections.
 *
 * The chosen view and Plans section live in the shell state, so the shell's
 * floating action button offers the matching "add" action and other screens
 * can open either view directly. Like the shell's tabs, each view is kept once
 * visited, so its month, filters and scroll position survive switching.
 */
@Composable
fun PlansAndTransactionsScreen(
    transactionsListState: LazyListState = rememberLazyListState(),
    plansListState: LazyListState = rememberLazyListState(),
    /** Overrides the live count (tests); normally read from the reminders state. */
    overdueReminders: Int? = null,
) {
    val shell = LocalShellState.current
    val overdue = overdueReminders ?: rememberOverdueCount()
    val current = shell.tab
    // Not snapshot state: switching views already recomposes, so a plain set is enough.
    val visited = remember { mutableSetOf<PlansAndTransactionsTab>() }
    visited += current
    // The view you came from (read before it is updated), so the next one arrives from the side it sits on.
    val cameFrom = remember { intArrayOf(current.ordinal) }
    val from = cameFrom[0]
    SideEffect { cameFrom[0] = current.ordinal }
    val switch: @Composable () -> Unit = { ViewSwitch(current, overdue) { shell.plansTab = it.ordinal } }

    Box(Modifier.fillMaxSize()) {
        for (tab in PlansAndTransactionsTab.entries) {
            if (tab !in visited) continue
            TabHost(
                visible = tab == current,
                enterOffset = when {
                    tab.ordinal > from -> ViewShift
                    tab.ordinal < from -> -ViewShift
                    else -> 0.dp
                },
            ) {
                when (tab) {
                    PlansAndTransactionsTab.Transactions -> TransactionHistory(
                        title = AppDestination.PlansAndTransactions.label,
                        actions = { AmountsVisibilityButton() },
                        allTime = shell.transactionsAllTime,
                        onAllTimeChange = { shell.transactionsAllTime = it },
                        pinnedHeader = switch,
                        listState = transactionsListState,
                        insetBottom = false,
                    )
                    PlansAndTransactionsTab.Plans -> Plans(plansListState, switch)
                }
            }
        }
    }
}

/** How far a view travels in as it arrives: a nudge toward the side it came from, not a slide across the screen. */
private val ViewShift = 24.dp

/** Transactions | Plans; Plans carries the number of overdue reminders. */
@Composable
private fun ViewSwitch(selected: PlansAndTransactionsTab, overdueReminders: Int, onSelect: (PlansAndTransactionsTab) -> Unit) {
    val tabs = remember(overdueReminders) {
        PlansAndTransactionsTab.entries.map { tab ->
            AppTab(
                label = tab.label,
                icon = tab.icon,
                selectedIcon = tab.selectedIcon,
                badge = if (tab == PlansAndTransactionsTab.Plans && overdueReminders > 0) {
                    { OverdueBadge(overdueReminders) }
                } else {
                    null
                },
            )
        }
    }
    AppPrimaryTabs(
        tabs = tabs,
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(PlansAndTransactionsTab.entries[it]) },
        modifier = Modifier.padding(AppSpacing.page).padding(bottom = AppSpacing.sm),
    )
}

private val PlansAndTransactionsTab.icon
    get() = when (this) {
        PlansAndTransactionsTab.Transactions -> Icons.Outlined.SwapVert
        PlansAndTransactionsTab.Plans -> Icons.Outlined.CalendarMonth
    }

private val PlansAndTransactionsTab.selectedIcon
    get() = when (this) {
        PlansAndTransactionsTab.Transactions -> Icons.Rounded.SwapVert
        PlansAndTransactionsTab.Plans -> Icons.Rounded.CalendarMonth
    }

private val PlanSection.icon
    get() = when (this) {
        PlanSection.Budgets -> Icons.Outlined.PieChart
        PlanSection.Recurring -> Icons.Outlined.Repeat
        PlanSection.Reminders -> Icons.Outlined.Notifications
    }

private val PlanSection.selectedIcon
    get() = when (this) {
        PlanSection.Budgets -> Icons.Rounded.PieChart
        PlanSection.Recurring -> Icons.Rounded.Repeat
        PlanSection.Reminders -> Icons.Rounded.Notifications
    }

/** Budgets | Recurring | Reminders: the level under the primary switch - pills with their icons, lighter than the switch above. */
@Composable
private fun PlanSectionTabs() {
    val shell = LocalShellState.current
    val overdue = rememberOverdueCount()
    val tabs = remember(overdue) {
        PlanSection.entries.map { section ->
            AppTab(
                section.label,
                icon = section.icon,
                selectedIcon = section.selectedIcon,
                badge = if (section == PlanSection.Reminders && overdue > 0) {
                    { OverdueBadge(overdue) }
                } else {
                    null
                },
            )
        }
    }
    AppSecondaryTabs(
        tabs = tabs,
        selectedIndex = shell.section.ordinal,
        onSelect = { shell.planSection = it },
        modifier = Modifier.padding(AppSpacing.page).padding(bottom = AppSpacing.xs),
    )
}

/** Plans: monthly budgets, recurring payments and reminders. */
@Composable
private fun Plans(listState: LazyListState, switch: @Composable () -> Unit) {
    val shell = LocalShellState.current
    AppPage(
        title = AppDestination.PlansAndTransactions.label,
        actions = { AmountsVisibilityButton() },
        // Both levels stay pinned, so you can switch section from anywhere in a long list.
        pinnedHeader = {
            Column {
                switch()
                PlanSectionTabs()
            }
        },
        listState = listState,
        bottomPadding = AppLayout.fabClearance,
        insetBottom = false,
    ) {
        item(key = "overdue") {
            // Budgets and Recurring don't list reminders, so say it here; on Reminders the Overdue section is the first thing you see.
            val overdue = rememberOverdueCount()
            RevealVisibility(visible = overdue > 0 && shell.section != PlanSection.Reminders) {
                OverdueNotice(
                    count = overdue,
                    onReview = { shell.planSection = PlanSection.Reminders.ordinal },
                    modifier = Modifier.padding(AppSpacing.page).padding(top = AppSpacing.sm),
                )
            }
        }
        item(key = "section") { PlanSectionContent(shell.section) }
    }
}

/**
 * The section's content, arriving in the user's page style ([sectionTransition]): in the sliding styles a later section
 * comes in from the right and an earlier one from the left; in the others it grows into place.
 */
@Composable
private fun PlanSectionContent(section: PlanSection) {
    val reduced = rememberReducedMotion()
    val style = LocalPageAnimation.current
    val shift = with(LocalDensity.current) { SectionShift.roundToPx() }
    AnimatedContent(
        targetState = section,
        transitionSpec = { sectionTransition(forward = targetState.ordinal > initialState.ordinal, reduced = reduced, shiftPx = shift, style = style) },
        label = "plan_section",
    ) { target ->
        Column {
            when (target) {
                PlanSection.Budgets -> BudgetsView()
                PlanSection.Recurring -> RecurringView()
                PlanSection.Reminders -> RemindersView()
            }
        }
    }
}

private val SectionShift = 24.dp
