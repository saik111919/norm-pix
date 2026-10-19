package com.example.normpix.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.rounded.CallReceived
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
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
import com.example.normpix.screens.WalletScreen
import com.example.normpix.ui.components.LocalFrostedBackdrop
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.frostedSource
import com.example.normpix.ui.components.rememberFrostedBackdrop
import com.example.normpix.ui.theme.AppIcons
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.WindowSize
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.ui.theme.rememberWindowSize
import com.example.normpix.wallet.rememberCardFlows
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
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

/** How far the content shrinks and dims at the end of a back gesture: a hint of recession, not a zoom. */
private const val BACK_SCALE = 0.05f
private const val BACK_DIM = 0.25f

/** How far into the back gesture (0..1) the previewed content's corners are fully round. */
private const val BACK_CORNERS_BY = 0.25f

/** The shadow under the previewed content at the end of the gesture. */
private val BACK_LIFT = 8.dp

/**
 * The app frame: the top-level destinations with a floating navigation bar on
 * phones and a navigation rail on larger windows - each also holding the one
 * way into Settings - and contextual actions surfaced through the bottom More control.
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
    val pageAnimation = LocalPageAnimation.current

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
    val backProgress = remember { Animatable(0f) }
    val backSettle = tween<Float>(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard)

    // Inside Plans back steps up first - section to Budgets, then to the transaction history - and only then
    // falls back to the previous tab. The steps live on ShellState so the rule has one definition.
    fun canClimbWithinTab(): Boolean = shell.canStepBackInPlans

    fun climbWithinTab() {
        shell.stepBackInPlans()
    }

    val hasHistory = tabHistory.size > 1
    val handlesBack = canClimbWithinTab() || hasHistory

    PredictiveBackHandler(enabled = handlesBack) { progress ->
        try {
            progress.collect { backEvent ->
                backProgress.snapTo(backEvent.progress)
            }
            // Gesture completed: carry out the navigation, while the content eases back to full size under it.
            scope.launch { backProgress.animateTo(0f, backSettle) }
            if (canClimbWithinTab()) {
                climbWithinTab()
            } else if (hasHistory) {
                tabHistory.removeAt(tabHistory.lastIndex)
                shell.selected = tabHistory.last()
            }
        } catch (_: CancellationException) {
            // Finger dragged back to the edge and released: the content eases back to rest instead of jumping.
            scope.launch { backProgress.animateTo(0f, backSettle) }
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

    // Contextual actions surfaced inside the More / Actions bottom action area:
    val contextualActions = remember(current, shell.tab, shell.section, walletLocked, prefs.walletCardsEnabled) {
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
            // Settings are always accessible as part of the More / Actions menu
            add(
                FooterAction(
                    label = "Settings",
                    icon = Icons.Outlined.Settings,
                    isPrimary = false,
                    onClick = { navigator.openSettings() },
                ),
            )
        }
    }

    val compact = size == WindowSize.Compact
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
    val goneFraction by animateFloatAsState(if (barGone) 1f else 0f, AppMotion.spatial(), label = "nav_bar_gone")
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
            modifier = tabModifier
                .fillMaxSize()
                .nestedScroll(barScroll.connection)
                .graphicsLayer {
                    // Back gesture feedback: the content is previewed as a window, in the chosen style - it shrinks
                    // (towards where that style closes a page) and dims as you pull, its corners rounding and a shadow
                    // lifting it off the background, as a pushed page's do.
                    val progress = backProgress.value
                    val scale = 1f - progress * BACK_SCALE
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - progress * BACK_DIM
                    transformOrigin = TabMotion.origin(pageAnimation)
                    val corner = PageCornerRadius.toPx() * (progress / BACK_CORNERS_BY).coerceAtMost(1f)
                    clip = corner > 0f
                    shape = if (corner > 0f) RoundedCornerShape(corner) else RectangleShape
                    shadowElevation = progress * BACK_LIFT.toPx()
                },
        ) {
            if (visited.contains(0)) {
                TabHost(visible = current == AppDestination.Home, enterOffset = enterOffset, animateArrival = shellSettled) {
                    HomeScreen(listState = listStates[0])
                }
            }
            if (visited.contains(1)) {
                TabHost(visible = current == AppDestination.PlansAndTransactions, enterOffset = enterOffset, animateArrival = shellSettled) {
                    PlansAndTransactionsScreen(
                        transactionsListState = listStates[1],
                        plansListState = plansListState,
                    )
                }
            }
            if (visited.contains(2)) {
                TabHost(visible = current == AppDestination.Insights, enterOffset = enterOffset, animateArrival = shellSettled) {
                    InsightsScreen(listState = listStates[2])
                }
            }
            if (visited.contains(3)) {
                TabHost(visible = current == AppDestination.Wallet, enterOffset = enterOffset, animateArrival = shellSettled) {
                    WalletScreen(listState = listStates[3])
                }
            }
        }
    }

    if (!compact) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail {
                for (d in AppDestination.entries) {
                    NavigationRailItem(
                        selected = d == current,
                        onClick = { shell.selected = d.ordinal },
                        icon = { DestinationIcon(d, selected = d == current, overdue = overdue) },
                        label = { Text(d.label, textAlign = TextAlign.Center, maxLines = 2) },
                        alwaysShowLabel = true,
                    )
                }
                Spacer(Modifier.weight(1f))
                NavigationRailItem(
                    selected = false,
                    onClick = { navigator.openSettings() },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    alwaysShowLabel = true,
                    modifier = Modifier.padding(bottom = AppSpacing.lg),
                )
            }
            tabs(Modifier.weight(1f))
        }
    } else {
        // The dock is glass: it blurs a recording of the tabs it floats over (it must stay out of the recording itself).
        val backdrop = rememberFrostedBackdrop()
        Box(Modifier.fillMaxSize()) {
            tabs(Modifier.frostedSource(backdrop))

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
internal fun TabHost(visible: Boolean, enterOffset: Dp = 0.dp, animateArrival: Boolean = true, content: @Composable () -> Unit) {
    val animation = LocalPageAnimation.current
    val reduced = rememberReducedMotion()
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
                    visible -> Modifier.graphicsLayer {
                        val t = arrival.value
                        val p = TabMotion.ease(animation, t)
                        alpha = TabMotion.arrivalAlpha(animation, t)
                        val scale = TabMotion.scale(animation, p)
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TabMotion.origin(animation)
                        translationX = if (TabMotion.slides(animation)) (1f - p) * travel else 0f
                    }
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
