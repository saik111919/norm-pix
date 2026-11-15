package com.example.normpix.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.BudgetsView
import com.example.normpix.finance.ui.GoalsView
import com.example.normpix.finance.ui.RecurringView
import com.example.normpix.finance.ui.TransactionHistory
import com.example.normpix.navigation.AmountsVisibilityButton
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.rememberBackLook
import com.example.normpix.navigation.LocalPageAnimation
import com.example.normpix.navigation.LocalShellState
import com.example.normpix.navigation.PlanSection
import com.example.normpix.navigation.PlansAndTransactionsTab
import com.example.normpix.navigation.ShownPart
import com.example.normpix.navigation.TabBackPreview
import com.example.normpix.navigation.TabHost
import com.example.normpix.navigation.WithinTabBack
import com.example.normpix.navigation.applyBackPose
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
internal fun PlansAndTransactionsScreen(
    transactionsListState: LazyListState = rememberLazyListState(),
    plansListState: LazyListState = rememberLazyListState(),
    /** Overrides the live count (tests); normally read from the reminders state. */
    overdueReminders: Int? = null,
    /** The back gesture as it takes this tab up a step (the shell's): the Plans view closing over Activity, a section over Budgets. */
    back: WithinTabBack? = null,
) {
    val shell = LocalShellState.current
    val overdue = overdueReminders ?: rememberOverdueCount()
    val current = shell.tab
    // Not snapshot state: switching views already recomposes, so a plain set is enough.
    val visited = remember { mutableSetOf<PlansAndTransactionsTab>() }
    visited += current
    // The view the back gesture goes back to is laid out under the one it leaves, even if it was never opened (Plans can be the
    // first thing seen, from the "+" menu or a link).
    back?.view?.target?.let { target -> PlansAndTransactionsTab.entries.getOrNull(target)?.let { visited += it } }
    // The view you came from (read before it is updated), so the next one arrives from the side it sits on.
    val cameFrom = remember { intArrayOf(current.ordinal) }
    val from = cameFrom[0]
    SideEffect { cameFrom[0] = current.ordinal }
    // While a back gesture shows one view under the other, each one's switch says that view - the one beneath is drawn as it will be
    // once it is back. Otherwise both say the view chosen, so a tap slides the switch over in the view being left as it always did.
    val switchOf: @Composable (PlansAndTransactionsTab) -> Unit = { view ->
        val previewing = back != null && back.view.target != TabBackPreview.NO_TAB
        ViewSwitch(if (previewing) view else current, overdue) { shell.plansTab = it.ordinal }
    }

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
                // The view a finished back gesture returns to is already there, whole: it does not arrive again.
                animateArrival = back?.handoff != true,
                back = back?.view,
                ordinal = tab.ordinal,
            ) {
                when (tab) {
                    PlansAndTransactionsTab.Transactions -> TransactionHistory(
                        title = AppDestination.PlansAndTransactions.label,
                        actions = { AmountsVisibilityButton() },
                        allTime = shell.transactionsAllTime,
                        onAllTimeChange = { shell.transactionsAllTime = it },
                        pinnedHeader = { switchOf(PlansAndTransactionsTab.Transactions) },
                        listState = transactionsListState,
                        insetBottom = false,
                    )
                    PlansAndTransactionsTab.Plans -> Plans(plansListState, { switchOf(PlansAndTransactionsTab.Plans) }, back)
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
        PlanSection.Goals -> Icons.Outlined.Savings
    }

private val PlanSection.selectedIcon
    get() = when (this) {
        PlanSection.Budgets -> Icons.Rounded.PieChart
        PlanSection.Recurring -> Icons.Rounded.Repeat
        PlanSection.Reminders -> Icons.Rounded.Notifications
        PlanSection.Goals -> Icons.Rounded.Savings
    }

/** Budgets | Recurring | Reminders | Goals: the level under the primary switch - pills with their icons, lighter than the switch above. */
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

/** Plans: monthly budgets, recurring payments, reminders and savings goals. */
@Composable
private fun Plans(listState: LazyListState, switch: @Composable () -> Unit, back: WithinTabBack?) {
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
        maxWidth = AppLayout.maxListWidth,
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
        item(key = "section") { PlanSectionContent(shell.section, back) }
    }
}

/**
 * The section's content, arriving in the user's page style ([sectionTransition]): in the sliding styles a later section
 * comes in from the right and an earlier one from the left; in the others it grows into place.
 *
 * While the back gesture takes it up to Budgets ([back]'s section preview), it closes under the finger in the Back gesture style
 * over Budgets laid out beneath it, as a tab closes over the tab before it - the part of it on screen, not the whole long list
 * ([ShownPart]). Letting go finishes the close and Budgets is then simply there.
 */
@Composable
private fun PlanSectionContent(section: PlanSection, back: WithinTabBack?) {
    val reduced = rememberReducedMotion()
    val style = LocalPageAnimation.current
    val look = rememberBackLook()
    val shift = with(LocalDensity.current) { SectionShift.roundToPx() }
    val preview = back?.section?.takeIf { !reduced && it.target != TabBackPreview.NO_TAB }
    val handoff = back?.handoff == true
    val surface = MaterialTheme.colorScheme.surface
    val shown = remember { ShownPart() }
    Box(Modifier.onPlaced(shown::update)) {
        if (preview != null) {
            PlanSection.entries.getOrNull(preview.target)?.let { beneath ->
                Column(
                    Modifier
                        .graphicsLayer { applyBackPose(look.revealed(preview.progress, preview.direction), shown) }
                        .clearAndSetSemantics {},
                ) { SectionBody(beneath) }
            }
        }
        AnimatedContent(
            targetState = section,
            modifier = if (preview != null) {
                Modifier
                    .graphicsLayer { applyBackPose(look.leaving(preview.progress, preview.direction), shown) }
                    .background(surface)
            } else {
                Modifier
            },
            transitionSpec = {
                if (handoff) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    sectionTransition(forward = targetState.ordinal > initialState.ordinal, reduced = reduced, shiftPx = shift, style = style)
                }
            },
            label = "plan_section",
        ) { target ->
            Column { SectionBody(target) }
        }
    }
}

@Composable
private fun SectionBody(section: PlanSection) {
    when (section) {
        PlanSection.Budgets -> BudgetsView()
        PlanSection.Recurring -> RecurringView()
        PlanSection.Reminders -> RemindersView()
        PlanSection.Goals -> GoalsView()
    }
}

private val SectionShift = 24.dp
