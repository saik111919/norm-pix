package com.example.normpix.navigation

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.AppContainer
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.InsightsScreen
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.screens.HomeScreen
import com.example.normpix.screens.PlansAndTransactionsScreen
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.screens.WalletScreen
import com.example.normpix.ui.components.LocalDockGone
import com.example.normpix.ui.components.LocalFloatingDock
import com.example.normpix.ui.components.LocalFrostedBackdrop
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.frostedSource
import com.example.normpix.ui.components.rememberFrostedBackdrop
import com.example.normpix.ui.theme.AppIcons
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.RailStyle
import com.example.normpix.ui.theme.WindowSize
import com.example.normpix.ui.theme.rememberRailFoldable
import com.example.normpix.ui.theme.rememberRailStyle
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.ui.theme.rememberWindowSize
import com.example.normpix.wallet.rememberCardFlows
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

val AppDestination.icon: ImageVector
    get() = when (this) {
        AppDestination.Home -> Icons.Outlined.Home
        AppDestination.PlansAndTransactions -> Icons.AutoMirrored.Outlined.ReceiptLong
        AppDestination.Insights -> Icons.Outlined.Insights
        AppDestination.Wallet -> AppIcons.Wallet
    }

val AppDestination.selectedIcon: ImageVector
    get() = when (this) {
        AppDestination.Home -> Icons.Rounded.Home
        AppDestination.PlansAndTransactions -> Icons.AutoMirrored.Rounded.ReceiptLong
        AppDestination.Insights -> Icons.Rounded.Insights
        AppDestination.Wallet -> AppIcons.WalletFilled
    }

/** The shadow under the tab the back gesture is moving, at the end of the gesture. */
private val BACK_LIFT = 8.dp

/** How long a committed back gesture takes to finish the tab's close, from wherever the finger let go. */
private const val BACK_FINISH_MS = 160

/**
 * The app frame: the top-level destinations with a floating navigation bar on
 * phones and a navigation rail on larger windows (a wide one, with each label beside its icon, on a Large window) - each
 * also holding the one way into Settings - and contextual actions surfaced through the bottom More control
 * (on the rail: its button for the screen's main action, and a More menu for the rest).
 *
 * Tabs are composed on first visit and then kept (invisible and
 * non-interactive), so each keeps its scroll position, month and filters
 * while you move around.
 */
@Composable
fun AppShell(
    container: AppContainer,
    shell: ShellState = rememberShellState(),
) {
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val current = AppDestination.entries[shell.selected.coerceIn(0, AppDestination.entries.lastIndex)]
    val size = rememberWindowSize()
    val scope = rememberCoroutineScope()

    val remindersState by LocalReminders.current.state.collectAsState()
    val overdue = remindersState.overdue().size

    val expenseFlows = rememberFinanceActions()
    val reminderFlows = rememberReminderFlows()
    val cardFlows = rememberCardFlows()

    val prefs by container.preferences.state.collectAsStateWithLifecycle()
    val lockVersion by container.lock.version.collectAsStateWithLifecycle()
    val walletLocked = remember(prefs, lockVersion) { container.lock.isWalletLocked(prefs) }

    // Each top-level destination defines the shape and destination of back:
    //   - on Plans, back first climbs up to the Plans root;
    //   - once at the root of a tab, it returns to the previously visited tab;
    //   - back on the first visited tab leaves the app.
    // The gesture tracks the system predictive-back progress and scales the content back slightly;
    // releasing early animates back to rest without navigating.
    val tabHistory = remember { mutableStateListOf(shell.selected) }
    // The back gesture as the tabs draw it (see TabBackPreview): the tab being left moves as the user's page style closes a
    // page, scrubbed by the finger, over the tab it goes back to.
    val backPreview = remember { TabBackPreview() }
    val backSettle = tween<Float>(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard)
    val backFinish = tween<Float>(AppMotion.duration(BACK_FINISH_MS), easing = AppMotion.standard)
    // The ease back to rest after a gesture is let go early, so a new gesture can stop it.
    val backSettling = remember { arrayOfNulls<Job>(1) }
    // True from a committed gesture until the tab it went back to has taken over; a gesture started then is let go by.
    var backFinishing by remember { mutableStateOf(false) }
    // True for the moment of the switch a committed gesture makes: that tab is already there, whole, so it does not arrive again.
    var tabHandoff by remember { mutableStateOf(false) }

    // Inside Plans back steps up first - section to Budgets, then to the transaction history - and only then
    // falls back to the previous tab. The steps live on ShellState so the rule has one definition.
    fun canClimbWithinTab(): Boolean = shell.canStepBackInPlans

    fun climbWithinTab() {
        shell.stepBackInPlans()
    }

    val hasHistory = tabHistory.size > 1
    val handlesBack = canClimbWithinTab() || hasHistory

    PredictiveBackHandler(enabled = handlesBack) { progress ->
        if (backFinishing) {
            progress.collect { }
            return@PredictiveBackHandler
        }
        backSettling[0]?.cancel()
        val toPreviousTab = !canClimbWithinTab() && hasHistory
        backPreview.target = if (toPreviousTab) tabHistory[tabHistory.lastIndex - 1] else TabBackPreview.NO_TAB
        fun easeBackToRest() {
            backSettling[0] = scope.launch {
                animate(backPreview.progress, 0f, animationSpec = backSettle) { value, _ -> backPreview.progress = value }
                backPreview.target = TabBackPreview.NO_TAB
            }
        }
        try {
            progress.collect { backEvent ->
                backPreview.direction = BackPreview.directionOf(backEvent.swipeEdge == BackEventCompat.EDGE_RIGHT)
                backPreview.progress = backEvent.progress
            }
            // Gesture completed.
            if (toPreviousTab) {
                // The close is finished from where the finger let go, and the switch is made when the tab is gone: the tab
                // it returns to is already on screen, whole, so it is switched to without arriving - nothing flashes.
                backFinishing = true
                scope.launch {
                    try {
                        animate(backPreview.progress, 1f, animationSpec = backFinish) { value, _ -> backPreview.progress = value }
                        tabHandoff = true
                        Snapshot.withMutableSnapshot {
                            tabHistory.removeAt(tabHistory.lastIndex)
                            shell.selected = tabHistory.last()
                            backPreview.target = TabBackPreview.NO_TAB
                            backPreview.progress = 0f
                        }
                        // Two frames: the one that draws the switch, and the one after it.
                        withFrameNanos { }
                        withFrameNanos { }
                    } finally {
                        tabHandoff = false
                        backFinishing = false
                    }
                }
            } else {
                // A step up inside the tab: its content eases back to full size while the section changes under it.
                climbWithinTab()
                easeBackToRest()
            }
        } catch (_: CancellationException) {
            // Finger dragged back to the edge and released: the content eases back to rest instead of jumping.
            easeBackToRest()
        }
    }

    // Record tab visits so back retraces your steps without duplicates when you stay on a tab.
    LaunchedEffect(shell.selected) {
        if (tabHistory.isEmpty() || tabHistory.last() != shell.selected) {
            tabHistory.add(shell.selected)
        }
    }

    // Expandable More / Actions control state
    var isMoreOpen by remember { mutableStateOf(false) }

    // Dismiss More menu when switching tabs or plans tab
    LaunchedEffect(shell.selected, shell.plansTab) { isMoreOpen = false }

    // Hardware or predictive back closes the More menu first before navigating back
    BackHandler(enabled = isMoreOpen) { isMoreOpen = false }

    // What the screen in front can add: the first of these, the main one, is the dock's button or the rail's.
    val pageActions = remember(current, shell.tab, shell.section, walletLocked, prefs.walletCardsEnabled) {
        buildList {
            when (current) {
                AppDestination.Home -> {
                    add(
                        FooterAction(
                            label = "Add expense",
                            icon = Icons.Rounded.Add,
                            isPrimary = true,
                            onClick = { expenseFlows.addTransaction(TransactionType.Expense) },
                        ),
                    )
                    add(
                        FooterAction(
                            label = "Add income",
                            icon = Icons.AutoMirrored.Rounded.CallReceived,
                            isPrimary = false,
                            onClick = { expenseFlows.addTransaction(TransactionType.Income) },
                        ),
                    )
                }
                AppDestination.PlansAndTransactions -> {
                    when (shell.tab) {
                        PlansAndTransactionsTab.Transactions -> {
                            add(
                                FooterAction(
                                    label = "Add expense",
                                    icon = Icons.Rounded.Add,
                                    isPrimary = true,
                                    onClick = { expenseFlows.addTransaction(TransactionType.Expense) },
                                ),
                            )
                            add(
                                FooterAction(
                                    label = "Add income",
                                    icon = Icons.AutoMirrored.Rounded.CallReceived,
                                    isPrimary = false,
                                    onClick = { expenseFlows.addTransaction(TransactionType.Income) },
                                ),
                            )
                        }
                        PlansAndTransactionsTab.Plans -> {
                            // On Recurring the thing to add is a recurring payment, whenever the list has some: a reminder stays one tap behind it.
                            val onRecurring = shell.section == PlanSection.Recurring
                            if (onRecurring) {
                                add(
                                    FooterAction(
                                        label = "Add recurring payment",
                                        icon = Icons.Outlined.EventRepeat,
                                        isPrimary = true,
                                        onClick = { expenseFlows.addRecurring() },
                                    ),
                                )
                            }
                            add(
                                FooterAction(
                                    label = "Add reminder",
                                    icon = Icons.Outlined.AddAlert,
                                    isPrimary = !onRecurring,
                                    onClick = { reminderFlows.add() },
                                ),
                            )
                            add(
                                FooterAction(
                                    label = "Add expense",
                                    icon = Icons.Rounded.Add,
                                    isPrimary = false,
                                    onClick = { expenseFlows.addTransaction(TransactionType.Expense) },
                                ),
                            )
                        }
                    }
                }
                AppDestination.Insights -> {
                    add(
                        FooterAction(
                            label = "Add expense",
                            icon = Icons.Rounded.Add,
                            isPrimary = true,
                            onClick = { expenseFlows.addTransaction(TransactionType.Expense) },
                        ),
                    )
                }
                AppDestination.Wallet -> {
                    if (!walletLocked) {
                        // With Store cards off there are no cards to add, and adding an account is the Wallet's main action.
                        if (prefs.walletCardsEnabled) {
                            add(
                                FooterAction(
                                    label = "Add card",
                                    icon = Icons.Outlined.CreditCard,
                                    isPrimary = true,
                                    onClick = { cardFlows.addCard() },
                                ),
                            )
                        }
                        add(
                            FooterAction(
                                label = "Add account",
                                icon = Icons.Outlined.AccountBalanceWallet,
                                isPrimary = !prefs.walletCardsEnabled,
                                onClick = { expenseFlows.addAccount() },
                            ),
                        )
                    }
                    add(
                        FooterAction(
                            label = "Add expense",
                            icon = Icons.Rounded.Add,
                            isPrimary = walletLocked,
                            onClick = { expenseFlows.addTransaction(TransactionType.Expense) },
                        ),
                    )
                }
            }
        }
    }
    // Contextual actions surfaced inside the More / Actions bottom action area; Settings are always accessible as part of it.
    val contextualActions = remember(pageActions, navigator) {
        pageActions + FooterAction(
            label = "Settings",
            icon = Icons.Outlined.Settings,
            isPrimary = false,
            onClick = { navigator.openSettings() },
        )
    }

    val compact = size == WindowSize.Compact
    val rail = rememberRailStyle()
    val foldable = rememberRailFoldable()
    DisposableEffect(compact) {
        snackbars.bottomInset = if (compact) 80.dp else 0.dp
        onDispose { snackbars.bottomInset = 0.dp }
    }

    val listStates = AppDestination.entries.map { rememberLazyListState() }
    val plansListState = rememberLazyListState()

    // The navigation bar steps out of the way while you scroll down and comes
    // back as soon as you scroll up, reach either end of the list, or the list has nowhere to go.
    val barScroll = rememberScrollAwareBarState()
    val activeList = if (current == AppDestination.PlansAndTransactions && shell.tab == PlansAndTransactionsTab.Plans) {
        plansListState
    } else {
        listStates[current.ordinal]
    }
    val listAtEdge by remember(activeList) {
        derivedStateOf { !activeList.canScrollForward || !activeList.canScrollBackward }
    }
    LaunchedEffect(shell.selected, shell.plansTab) { barScroll.show() }
    val barGone = barScroll.hidden && !listAtEdge
    LaunchedEffect(barGone) { if (barGone) isMoreOpen = false }
    val goneState = animateFloatAsState(if (barGone) 1f else 0f, AppMotion.spatial(), label = "nav_bar_gone")
    val goneFraction by goneState
    // For the tabs: a search card sitting on the dock goes down with it (see LocalDockGone). A function, so its motion recomposes nothing.
    val dockGone: () -> Float = remember(goneState) { { goneState.value } }
    var barHeightPx by remember { mutableIntStateOf(0) }

    // Keep tabs composed once visited so they remember scroll and sub-navigation state.
    // The tab that is selected when the shell appears is there from its first frame (see [shellSettled]).
    val visited = remember { mutableStateListOf(0).apply { if (shell.selected != 0) add(shell.selected) } }
    // The tabs there when the shell appears - on launch, or coming back from a page that was open over it - arrive with
    // the shell, in the page transition's own motion; fading them in as well would stack a second animation on the
    // first (Home would sit invisible while the page that covered it slides away). Only a tab opened or switched to
    // afterwards animates itself in.
    var shellSettled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shellSettled = true }
    LaunchedEffect(shell.selected) {
        if (!visited.contains(shell.selected)) visited.add(shell.selected)
    }

    // Direction tells TabHost which side to bring the new screen from.
    var previousOrdinal by remember { mutableIntStateOf(shell.selected) }
    val step = (shell.selected - previousOrdinal).coerceIn(-1, 1)
    val enterOffset = (step * 24).dp
    LaunchedEffect(shell.selected) { previousOrdinal = shell.selected }

    val tabs: @Composable (Modifier) -> Unit = { tabModifier ->
        Box(
            // The back gesture's preview is drawn by the tabs themselves (see TabHost): the tab being left over the one it returns to.
            modifier = tabModifier
                .fillMaxSize()
                .nestedScroll(barScroll.connection),
        ) {
            if (visited.contains(0)) {
                TabHost(visible = current == AppDestination.Home, enterOffset = enterOffset, animateArrival = shellSettled && !tabHandoff, back = backPreview, ordinal = 0) {
                    HomeScreen(listState = listStates[0])
                }
            }
            if (visited.contains(1)) {
                TabHost(visible = current == AppDestination.PlansAndTransactions, enterOffset = enterOffset, animateArrival = shellSettled && !tabHandoff, back = backPreview, ordinal = 1) {
                    PlansAndTransactionsScreen(
                        transactionsListState = listStates[1],
                        plansListState = plansListState,
                    )
                }
            }
            if (visited.contains(2)) {
                TabHost(visible = current == AppDestination.Insights, enterOffset = enterOffset, animateArrival = shellSettled && !tabHandoff, back = backPreview, ordinal = 2) {
                    InsightsScreen(listState = listStates[2])
                }
            }
            if (visited.contains(3)) {
                TabHost(visible = current == AppDestination.Wallet, enterOffset = enterOffset, animateArrival = shellSettled && !tabHandoff, back = backPreview, ordinal = 3) {
                    WalletScreen(listState = listStates[3])
                }
            }
        }
    }

    if (!compact) {
        Row(Modifier.fillMaxSize()) {
            if (rail == RailStyle.Wide) {
                WideRail(
                    actions = pageActions,
                    current = current,
                    overdue = overdue,
                    onSelect = { shell.selected = it.ordinal },
                    onOpenSettings = { navigator.openSettings() },
                    onFold = { container.preferences.setRailCollapsed(true) },
                )
            } else {
                // The rail's label is a word or two, wrapped to its width: unbounded, "Plans & Activity" ran out past both edges.
                val railLabel = Modifier.widthIn(max = AppLayout.railWidth - AppSpacing.sm)
                NavigationRail(
                    modifier = Modifier.width(AppLayout.railWidth),
                    header = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Folded down by hand from the wide rail: the button that opens it again is the first thing in it.
                            if (foldable) {
                                IconButton(onClick = { container.preferences.setRailCollapsed(false) }) {
                                    Icon(Icons.Rounded.Menu, contentDescription = "Expand navigation")
                                }
                            }
                            RailActions(pageActions)
                        }
                    },
                ) {
                    // The destinations scroll when the window is short (a phone on its side), so none is out of reach;
                    // Settings stays where it is under them.
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                    ) {
                        for (d in AppDestination.entries) {
                            NavigationRailItem(
                                selected = d == current,
                                onClick = { shell.selected = d.ordinal },
                                icon = { DestinationIcon(d, selected = d == current, overdue = overdue) },
                                label = { Text(d.label, textAlign = TextAlign.Center, maxLines = 2, modifier = railLabel) },
                                alwaysShowLabel = true,
                            )
                        }
                    }
                    NavigationRailItem(
                        selected = false,
                        onClick = { navigator.openSettings() },
                        icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                        label = { Text("Settings", textAlign = TextAlign.Center, maxLines = 1, modifier = railLabel) },
                        alwaysShowLabel = true,
                        modifier = Modifier.padding(bottom = AppSpacing.lg),
                    )
                }
            }
            // Nothing floats over the tabs here: their lists end under their last row (see LocalFloatingDock).
            CompositionLocalProvider(LocalFloatingDock provides false) {
                tabs(Modifier.weight(1f))
            }
        }
    } else {
        // The dock is glass: it blurs a recording of the tabs it floats over (it must stay out of the recording itself).
        val backdrop = rememberFrostedBackdrop()
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalDockGone provides dockGone) {
                tabs(Modifier.frostedSource(backdrop))
            }

            // Outside click dismissal scrim when More is open
            if (isMoreOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { isMoreOpen = false },
                )
            }

            CompositionLocalProvider(LocalFrostedBackdrop provides backdrop) {
                FloatingNavigationRow(
                    destinations = AppDestination.entries,
                    current = current,
                    overdueCount = overdue,
                    onDestinationSelected = { shell.selected = it.ordinal },
                    onOpenSettings = { navigator.openSettings() },
                    actions = contextualActions,
                    isMoreOpen = isMoreOpen,
                    onMoreToggle = { isMoreOpen = !isMoreOpen },
                    currentLabel = if (current == AppDestination.PlansAndTransactions) shell.tab.label else current.label,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { barHeightPx = it.height }
                        .offset { IntOffset(0, (goneFraction * barHeightPx).roundToInt()) }
                        .graphicsLayer { alpha = 1f - goneFraction.coerceIn(0f, 1f) },
                )
            }
        }
    }
}

/**
 * The rail's way to add: a button for the main thing the screen in front adds (an expense, a card, a reminder...), and under
 * it, when there are others, a menu of them. The dock has these in its More control; the rail has no dock to carry them.
 */
@Composable
private fun RailActions(actions: List<FooterAction>) {
    val main = actions.firstOrNull { it.isPrimary } ?: actions.firstOrNull() ?: return
    val others = actions.filter { it !== main }
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.padding(top = AppSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        FloatingActionButton(onClick = main.onClick) { Icon(main.icon, contentDescription = main.label) }
        if (others.isNotEmpty()) {
            Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreHoriz, contentDescription = "More actions") }
                RailActionMenu(others, expanded = open, onDismiss = { open = false })
            }
        }
    }
}

/** The menu of the actions a rail has no room for beside its main one. */
@Composable
private fun RailActionMenu(actions: List<FooterAction>, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        for (action in actions) {
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, contentDescription = null) },
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}

/**
 * The rail of a Large window (a tablet in landscape), where the narrow one looks lost: a column with room for the words. The
 * screen's main action is an extended button at the top, naming what it adds, and the others are a row under it that opens the
 * same menu; each destination is a pill with its label beside its icon, and Settings is the last row, kept at the foot. It is
 * the same destinations, icons, colours and shapes as the narrow rail and the dock - only laid out for the room. A button above
 * it folds it down to the narrow rail by hand ([onFold]); the narrow rail of a window with room for this one has the button that
 * opens it again, and the choice is kept (`AppPrefsState.railCollapsed`).
 */
@Composable
private fun WideRail(
    actions: List<FooterAction>,
    current: AppDestination,
    overdue: Int,
    onSelect: (AppDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onFold: () -> Unit,
) {
    Column(
        Modifier
            .width(AppLayout.wideRailWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(NavigationRailDefaults.windowInsets)
            .padding(horizontal = AppSpacing.md),
    ) {
        // Folds the rail down to the narrow one; the button that opens it again is the first thing in that.
        IconButton(onClick = onFold, modifier = Modifier.padding(top = AppSpacing.sm)) {
            Icon(Icons.AutoMirrored.Rounded.MenuOpen, contentDescription = "Collapse navigation")
        }
        val main = actions.firstOrNull { it.isPrimary } ?: actions.firstOrNull()
        if (main != null) {
            val others = actions.filter { it !== main }
            var open by remember { mutableStateOf(false) }
            Spacer(Modifier.height(AppSpacing.sm))
            // The content form, not the icon-and-text one: that hides its text from semantics, and the button would have no name.
            ExtendedFloatingActionButton(
                onClick = main.onClick,
                // Starts where the destinations' icons do, not wider than the rail's own padding allows.
                modifier = Modifier.padding(start = AppSpacing.xs),
            ) {
                Icon(main.icon, contentDescription = null)
                Spacer(Modifier.width(AppSpacing.md))
                Text(main.label, maxLines = 1)
            }
            if (others.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.sm))
                Box {
                    NavigationDrawerItem(
                        label = { Text("More actions", maxLines = 1) },
                        selected = false,
                        onClick = { open = true },
                        icon = { Icon(Icons.Rounded.MoreHoriz, contentDescription = null) },
                    )
                    RailActionMenu(others, expanded = open, onDismiss = { open = false })
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
        }
        // The destinations scroll when the window is short, so none is out of reach; Settings stays where it is under them.
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            for (d in AppDestination.entries) {
                NavigationDrawerItem(
                    label = { Text(d.label, maxLines = 1) },
                    selected = d == current,
                    onClick = { onSelect(d) },
                    icon = { DestinationIcon(d, selected = d == current, overdue = overdue) },
                )
            }
        }
        NavigationDrawerItem(
            label = { Text("Settings", maxLines = 1) },
            selected = false,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            modifier = Modifier.padding(bottom = AppSpacing.lg),
        )
    }
}

/** A destination's icon; Plans & Transactions carries a badge with the number of overdue reminders. */
@Composable
private fun DestinationIcon(destination: AppDestination, selected: Boolean, overdue: Int) {
    val icon = if (selected) destination.selectedIcon else destination.icon
    if (destination != AppDestination.PlansAndTransactions || overdue == 0) {
        Icon(icon, contentDescription = null)
        return
    }
    BadgedBox(
        badge = {
            Badge(Modifier.semantics { contentDescription = "$overdue overdue" }) { Text("$overdue") }
        },
    ) { Icon(icon, contentDescription = null) }
}

/** Whether the tab this content lives in is the one on screen. Tabs stay composed when hidden, so ongoing work (sensors) checks this. */
val LocalTabVisible = staticCompositionLocalOf { true }

/**
 * Cross-fade wrapper for a top-level tab. Stays in the tree so scroll and nested state survive;
 * zero-sized and non-interactive when hidden so it costs nothing to lay out or draw.
 *
 * A tab fades and moves in when it is switched to. With [animateArrival] false it is simply there from its first
 * frame: that is for the tabs that arrive together with the shell, whose entrance belongs to the page transition.
 */
@Composable
internal fun TabHost(
    visible: Boolean,
    enterOffset: Dp = 0.dp,
    animateArrival: Boolean = true,
    back: TabBackPreview? = null,
    ordinal: Int = -1,
    content: @Composable () -> Unit,
) {
    val animation = LocalPageAnimation.current
    val reduced = rememberReducedMotion()
    // While the back gesture is taking the app to the previous tab: the tab on screen is moved as the page style closes a page,
    // and the tab it goes back to is laid out, whole, beneath it. Reduced motion shows neither: the gesture just goes back.
    val preview = back?.takeIf { !reduced && it.target != TabBackPreview.NO_TAB }
    // What the tab being left is drawn on, so the tab beneath does not show through it as it fades or slides away.
    val surface = MaterialTheme.colorScheme.surface
    val animate = animateArrival && !reduced
    val density = LocalDensity.current
    val travel = remember(visible) { with(density) { enterOffset.toPx() } }
    // Keyed on [visible], so the first frame a tab is switched to is already at the start of its arrival - not one frame
    // at full strength before it jumps back and fades in, which read as a flicker.
    val arrival = remember(visible) { Animatable(if (visible && animate) 0f else 1f) }
    val departure = remember(visible) { Animatable(0f) }
    // The tab that was in front stays drawn under the one arriving, going away in the chosen style, until that one is
    // there: the arriving tab never fades in over an empty background.
    var onScreen by remember { mutableStateOf(visible) }
    val departing = !visible && onScreen && animate
    LaunchedEffect(visible) {
        if (visible) {
            onScreen = true
            // Linear in time: each part of the motion applies its own easing over its own share of it (see TabMotion), as
            // a pushed page's do - the window's quick fade over the first 60%, its growth over all of it.
            if (animate) arrival.animateTo(1f, tween(TabMotion.openMs(animation), easing = LinearEasing))
        } else {
            if (animate && onScreen) departure.animateTo(1f, tween(TabMotion.openMs(animation), easing = LinearEasing))
            onScreen = false
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            // The arriving tab over the leaving one, as a page opening over the one it was opened from.
            .zIndex(if (visible) 1f else 0f)
            .then(
                when {
                    visible && preview != null -> Modifier
                        .graphicsLayer { applyBackPose(BackPreview.leaving(animation, preview.progress, preview.direction)) }
                        .background(surface)
                    visible -> Modifier.graphicsLayer {
                        val t = arrival.value
                        val p = TabMotion.ease(animation, t)
                        alpha = TabMotion.arrivalAlpha(animation, t)
                        // A window is not scaled: it is uncovered from the bottom edge up (see TabMotion.reveal).
                        val scale = if (animation == PageAnimationType.LinuxWindow) 1f else TabMotion.scale(animation, p)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TabMotion.origin(animation)
                        translationX = if (TabMotion.slides(animation)) (1f - p) * travel else 0f
                        val reveal = TabMotion.reveal(animation, p)
                        if (reveal < 1f) {
                            clip = true
                            shape = BottomRevealShape(reveal, PageCornerRadius.toPx())
                        }
                    }
                    preview != null && preview.target == ordinal -> Modifier
                        .graphicsLayer { applyBackPose(BackPreview.revealed(animation, preview.progress, preview.direction)) }
                        .clearAndSetSemantics {}
                    departing -> Modifier
                        .graphicsLayer {
                            val t = departure.value
                            val q = TabMotion.ease(animation, t)
                            alpha = TabMotion.departureAlpha(animation, t)
                            val scale = TabMotion.departureScale(animation, q)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TabMotion.origin(animation)
                            translationX = if (TabMotion.slides(animation)) -q * travel / 2 else 0f
                        }
                        .clearAndSetSemantics {}
                    else -> Modifier.layout { _, _ -> layout(0, 0) {} }.clearAndSetSemantics {}
                },
            ),
        contentAlignment = Alignment.TopStart,
    ) { CompositionLocalProvider(LocalTabVisible provides visible) { content() } }
}

/**
 * The back gesture as the tabs draw it: how far it has gone ([progress], 0..1 - scrubbed by the finger, then finished or
 * eased back by the shell), the side it came from ([direction], see [BackPreview.directionOf]) and the tab it takes the
 * app back to ([target], an [AppDestination] ordinal, or [NO_TAB] when the gesture does not change tab). Read in the
 * tabs' draw phase, so a gesture moves layers, not compositions.
 */
@Stable
internal class TabBackPreview {
    var progress by mutableFloatStateOf(0f)
    var direction by mutableFloatStateOf(1f)
    var target by mutableIntStateOf(NO_TAB)

    companion object {
        const val NO_TAB = -1
    }
}

/** Draws a tab in [pose]: the shape, shadow and motion of the user's page style part-way through going back. */
private fun GraphicsLayerScope.applyBackPose(pose: BackPose) {
    scaleX = pose.scale
    scaleY = pose.scale
    alpha = pose.alpha
    translationX = pose.shiftX * size.width
    transformOrigin = pose.origin
    if (pose.reveal < 1f) {
        // A window sinking into its strip: what shows of it is the part along the bottom edge, its top corners as round as it is far gone.
        clip = true
        shape = BottomRevealShape(pose.reveal, PageCornerRadius.toPx() * pose.corners)
    } else if (pose.corners > 0f) {
        clip = true
        shape = RoundedCornerShape(PageCornerRadius.toPx() * pose.corners)
    }
    shadowElevation = BACK_LIFT.toPx() * pose.lift
}
