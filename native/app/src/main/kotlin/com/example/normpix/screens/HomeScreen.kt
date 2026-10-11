package com.example.normpix.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.theme.resolve
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.BudgetOverview
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.domain.CategoryTotal
import com.example.normpix.finance.domain.parentMap
import com.example.normpix.finance.domain.rollUpToParents
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.PeriodSummary
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.relativeChange
import com.example.normpix.finance.domain.shareOf
import com.example.normpix.finance.domain.upcomingOccurrences
import com.example.normpix.finance.ui.AsyncValue
import com.example.normpix.finance.ui.CategoryAvatar
import com.example.normpix.finance.ui.CategorySpend
import com.example.normpix.finance.ui.CategorySpendSheet
import com.example.normpix.finance.ui.CategoryStack
import com.example.normpix.finance.ui.HeroShape
import com.example.normpix.finance.ui.HeroStats
import com.example.normpix.finance.ui.SummaryStat
import com.example.normpix.finance.ui.SwipeableTransactionSection
import com.example.normpix.finance.ui.TABULAR_FIGURES
import com.example.normpix.finance.ui.TransactionAmount
import com.example.normpix.finance.ui.TransactionTile
import com.example.normpix.finance.ui.TransferAvatar
import com.example.normpix.finance.ui.budgetIcon
import com.example.normpix.finance.ui.budgetTone
import com.example.normpix.finance.ui.dayHeading
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.monthLabel
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.finance.ui.rememberFinanceQuery
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.ui.spendingComparison
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.AppSearchButton
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.PlanSection
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.formatOverdueBy
import com.example.normpix.reminders.icon
import com.example.normpix.reminders.reminderDueText
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SectionHeader
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.components.StatusSurface
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.wallet.CardStack
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.rememberCardFlows
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** How far ahead "Next 7 days" looks. */
private const val COMING_UP_DAYS = 7L
private const val COMING_UP_MAX = 5

/** How many categories the Top spending stack holds. */
private const val TOP_SPENDING = 5

/** What Home shows, read in one pass whenever finance data changes. */
private class HomeData(
    val month: MonthKey,
    val summary: PeriodSummary,
    val previous: PeriodSummary,
    val recent: List<FinanceTransaction>,
    val topSpending: List<CategoryTotal>,
    /** What each category came to the month before, to say whether it is up or down. */
    val previousSpending: Map<Long, Long>,
    val budgets: BudgetOverview,
    /** Recurring occurrences in the next 7 days, soonest first. */
    val upcoming: List<Pair<LocalDateTime, RecurringRule>>,
) {
    companion object {
        suspend fun load(repo: FinanceRepository): HomeData {
            val now = LocalDateTime.now()
            val month = MonthKey.of(now)
            val horizon = now.plusDays(COMING_UP_DAYS)
            // The biggest are per top-level category: a sub-category's spending counts toward its parent.
            val parents = repo.categories().parentMap()
            val upcoming = repo.recurringRules()
                .filter { it.active && !it.isFinished }
                .flatMap { rule -> upcomingOccurrences(rule, 8).filter { !it.isAfter(horizon) }.map { it to rule } }
                .sortedBy { it.first }
            return HomeData(
                month = month,
                summary = repo.summary(month.range),
                previous = repo.summary(month.previous.range),
                recent = repo.transactions(TransactionQuery(), limit = 5).items,
                topSpending = rollUpToParents(repo.categoryTotals(month.range, TransactionType.Expense), parents).take(TOP_SPENDING),
                previousSpending = rollUpToParents(repo.categoryTotals(month.previous.range, TransactionType.Expense), parents)
                    .associate { it.categoryId to it.amount },
                budgets = repo.budgetOverview(month),
                upcoming = upcoming,
            )
        }
    }
}

/**
 * Home: this month at a glance, whatever needs attention, recent activity and
 * what's coming up - each block links to the tab that owns it.
 */
@Composable
fun HomeScreen(listState: LazyListState = rememberLazyListState()) {
    val navigator = LocalNavigator.current
    val result by rememberFinanceQuery(Unit) { HomeData.load(this) }

    AppPage(
        title = "Home",
        actions = { AppSearchButton() },
        listState = listState,
        bottomPadding = AppLayout.fabClearance,
        insetBottom = false,
    ) {
        item(key = "content") {
            Column(Modifier.padding(AppSpacing.page)) {
                when (val value = result) {
                    is AsyncValue.Loading -> AppLoadingState(label = "Loading your month")
                    is AsyncValue.Failure -> AppErrorState(
                        title = "Couldn't open your finances",
                        message = "Your data is safe on this device. Try again.",
                    )
                    is AsyncValue.Data -> {
                        val data = value.value
                        Attention(
                            budgets = data.budgets,
                            onOpenBudgets = { navigator.openPlans(PlanSection.Budgets) },
                        )
                        CardsOnHome()
                        MonthOverview(data, onClick = { navigator.go(AppDestination.Insights) })
                        QuickAdd()
                        Recent(data.recent, onSeeAll = navigator::openTransactions)
                        if (data.topSpending.isNotEmpty()) {
                            TopSpending(data, onSeeAll = { navigator.go(AppDestination.Insights) })
                        }
                        ComingUp(data.upcoming, onSeeAll = { section -> navigator.openPlans(section) })
                    }
                }
            }
        }
    }
}

/**
 * The one thing that most needs attention right now, if anything: an overdue
 * reminder first, then a budget that's over or nearly used.
 */
@Composable
private fun Attention(budgets: BudgetOverview, onOpenBudgets: () -> Unit) {
    val finance = rememberFinanceState()
    val remindersState by LocalReminders.current.state.collectAsState()
    val flows = rememberReminderFlows()
    val navigator = LocalNavigator.current
    val overdue = remindersState.overdue()

    if (overdue.isNotEmpty()) {
        val r = overdue.first()
        val late = formatOverdueBy(r.effectiveDueAt)
        StatusSurface(
            tone = StatusTone.Error,
            icon = r.category.icon,
            eyebrow = if (overdue.size == 1) late else "$late · ${overdue.size - 1} more overdue",
            title = r.title,
            onClick = { flows.edit(r) },
            actions = buildList {
                if (overdue.size > 1) add(StatusAction("View all ${overdue.size}", onClick = { navigator.openPlans(PlanSection.Reminders) }))
                add(StatusAction("Snooze", onClick = { flows.snooze(r) }))
                add(StatusAction("Mark as done", onClick = { flows.complete(r) }, isPrimary = true))
            },
            modifier = Modifier.padding(bottom = AppSpacing.lg),
        )
        return
    }

    // Worst budget first: exceeded before approaching, then most used.
    val candidates = buildList<Pair<String, BudgetStatus>> {
        budgets.overall?.let { add("Monthly budget" to it) }
        for ((id, status) in budgets.categories) add("${finance.category(id)?.name ?: "Category"} budget" to status)
    }.filter { it.second.health != BudgetHealth.Healthy }.sortedByDescending { it.second.utilization }
    if (candidates.isEmpty()) return

    val (name, status) = candidates.first()
    val over = status.health == BudgetHealth.Exceeded
    val others = candidates.size - 1
    val message = buildList {
        add(
            if (over) {
                "${finance.money.format(-status.remaining)} over for ${monthLabel(budgets.month)}"
            } else {
                "${finance.money.format(status.remaining)} left for ${monthLabel(budgets.month)}"
            },
        )
        if (others > 0) add("$others other ${if (others == 1) "budget needs" else "budgets need"} a look")
    }.joinToString(". ")
    StatusSurface(
        tone = budgetTone(status.health),
        icon = budgetIcon(status.health),
        eyebrow = if (over) "Over budget" else "Nearly used",
        title = name,
        message = message,
        onClick = onOpenBudgets,
        actions = listOf(StatusAction("View budgets", onClick = onOpenBudgets)),
        modifier = Modifier.padding(bottom = AppSpacing.lg),
    )
}

/**
 * The saved cards as a stack: the one in front in full, the others peeking out behind it. Tapping the front card opens it
 * in a sheet. Nothing at all while there are no cards (Wallet is where the first is added). Behind the wallet lock it is
 * one row asking to unlock instead - a card's bank and last digits are the wallet's to show, not Home's.
 */
@Composable
internal fun CardsOnHome() {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val cardsState by container.cards.state.collectAsState()
    val lockVersion by container.lock.version.collectAsState()
    val flows = rememberCardFlows()
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()

    // The app loads the vault at start; this only covers Home being up before that finished.
    LaunchedEffect(Unit) { if (container.cards.state.value is CardsState.Loading) container.cards.load() }
    val cards = (cardsState as? CardsState.Ready)?.cards.orEmpty()
    if (cards.isEmpty()) return

    @Suppress("UNUSED_EXPRESSION")
    lockVersion // re-evaluate the lock whenever it may have changed
    if (container.lock.isWalletLocked(prefs)) {
        ListSection(Modifier.padding(bottom = AppSpacing.lg)) {
            item {
                ListRow(
                    headline = "Cards are locked",
                    supporting = "Unlock to see them here",
                    leading = { LeadingIcon(Icons.Outlined.Lock) },
                    onClick = {
                        scope.launch {
                            if (!container.lock.authenticate("Unlock your wallet")) snackbars.show("Couldn't unlock. Try again.")
                        }
                    },
                )
            }
        }
        return
    }
    CardStack(
        cards = cards,
        onOpen = flows::showCard,
        modifier = Modifier.padding(bottom = AppSpacing.lg),
        bankIconStyle = prefs.bankIconStyle,
    )
}

/**
 * Where this month stands, as the one expressive object on Home: a large tonal surface with an
 * asymmetric (diagonal) corner shape, the month's spending as the loudest number on the screen,
 * a thick progress bar for the budget, and - on a quieter inner surface - the supporting figures:
 * what's left, what came in, what you have in total.
 */
@Composable
private fun MonthOverview(data: HomeData, onClick: () -> Unit) {
    val finance = rememberFinanceState()
    val money = finance.money
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val s = data.summary
    val comparison = spendingComparison(relativeChange(s.expense, data.previous.expense), data.month.previous)
    val budget = data.budgets.headline
    val onHero = scheme.onPrimaryContainer

    Surface(
        onClick = onClick,
        color = scheme.primaryContainer,
        contentColor = onHero,
        shape = HeroShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = AppSpacing.xxl, top = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xl)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text("Spent in ${monthLabel(data.month)}", style = type.titleMedium, color = onHero.copy(alpha = 0.8f))
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    money.format(s.expense),
                    style = type.displayMedium.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = type.displayMedium.fontSize),
                )
                if (comparison != null) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(comparison, style = type.bodyMedium, color = onHero.copy(alpha = 0.8f))
                }
            }
            if (budget != null) {
                Spacer(Modifier.height(AppSpacing.xl))
                HeroBudgetBar(budget, money)
            }
            Spacer(Modifier.height(AppSpacing.xl))
            // Supporting figures sit on the page surface, so the hero keeps one idea: this month's spending.
            Surface(color = scheme.surface, shape = AppRadius.rXl, modifier = Modifier.fillMaxWidth()) {
                HeroStats(
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                    stats = listOfNotNull<@Composable (Modifier) -> Unit>(
                        if (budget != null) {
                            { m: Modifier ->
                                val over = budget.health == BudgetHealth.Exceeded
                                SummaryStat(
                                    label = if (over) "Over by" else "Left",
                                    value = money.format(if (over) -budget.remaining else budget.remaining),
                                    valueStyle = type.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (over) scheme.error else null,
                                    modifier = m,
                                )
                            }
                        } else {
                            null
                        },
                        { m: Modifier ->
                            SummaryStat(
                                label = "Income",
                                value = money.format(s.income),
                                valueStyle = type.titleMedium,
                                modifier = m,
                            )
                        },
                        { m: Modifier ->
                            SummaryStat(
                                label = "Total balance",
                                value = money.format(finance.totalBalance),
                                valueStyle = type.titleMedium,
                                modifier = m,
                            )
                        },
                    ),
                )
            }
        }
    }
}

/** The budget as a thick bar with "N% used" and the total underneath; words carry the state, color only reinforces it. */
@Composable
private fun HeroBudgetBar(status: BudgetStatus, money: MoneyFormatter) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val percent = (status.utilization * 100).roundToInt()
    val tone = if (status.health == BudgetHealth.Healthy) scheme.primary else budgetTone(status.health).resolve().color
    val spoken = "Budget ${money.format(status.allocated)}, spent ${money.format(status.spent)}, $percent% used"
    Column(Modifier.clearAndSetSemantics { contentDescription = spoken }) {
        AppLinearProgress(
            progress = { status.utilization.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            thickness = 12.dp,
            color = tone,
            trackColor = scheme.onPrimaryContainer.copy(alpha = 0.14f),
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (status.health != BudgetHealth.Healthy) {
                Icon(budgetIcon(status.health), contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(AppSpacing.xs))
            }
            Text("$percent% used", style = type.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "of ${money.format(status.allocated, trimZeroFraction = true)}",
                style = type.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/** Connected pair (large outside, small where they meet): Add income is the main action, Transfer its companion. */
private val QuickStartShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp)
private val QuickEndShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp)

/** Income and transfer shortcuts (the floating button adds expenses). */
@Composable
private fun QuickAdd() {
    val actions = rememberFinanceActions()
    Row(
        Modifier.fillMaxWidth().padding(top = AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Button(
            onClick = { actions.addTransaction(TransactionType.Income) },
            shape = QuickStartShape,
            modifier = Modifier.weight(1.25f).heightIn(min = 56.dp),
        ) {
            Icon(TransactionType.Income.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Add income", maxLines = 1)
        }
        FilledTonalButton(
            onClick = { actions.addTransaction(TransactionType.Transfer) },
            shape = QuickEndShape,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp),
        ) {
            Icon(TransactionType.Transfer.icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Transfer", maxLines = 1)
        }
    }
}

@Composable
private fun Recent(transactions: List<FinanceTransaction>, onSeeAll: () -> Unit) {
    val actions = rememberFinanceActions()
    if (transactions.isEmpty()) {
        SectionHeader(title = "Recent")
        ListSection {
            item {
                ListRow(
                    headline = "Record your first expense",
                    supporting = "Everything you add stays on this device. No sign-in, no bank access.",
                    leading = { LeadingIcon(Icons.Rounded.EditNote) },
                    onClick = { actions.addTransaction() },
                )
            }
        }
        return
    }
    SectionHeader(title = "Recent", actionLabel = "See all", onAction = onSeeAll)
    SwipeableTransactionSection(transactions)
}

/**
 * The month's biggest spending categories as a stack, like the cards: the biggest in front, the rest behind it. Tapping
 * the front one opens its month in a sheet, which can go on to every transaction in it.
 */
@Composable
private fun TopSpending(data: HomeData, onSeeAll: () -> Unit) {
    val finance = rememberFinanceState()
    val spends = data.topSpending.map { total ->
        CategorySpend(
            categoryId = total.categoryId,
            category = finance.category(total.categoryId),
            month = data.month,
            amount = total.amount,
            count = total.count,
            share = shareOf(total.amount, data.summary.expense),
            previous = data.previousSpending[total.categoryId] ?: 0L,
            budget = data.budgets.categories[total.categoryId],
        )
    }
    TopSpendingStack(spends, onSeeAll)
}

/** The stack itself, from what the month came to per category; nothing at all when there was no spending. */
@Composable
internal fun TopSpendingStack(spends: List<CategorySpend>, onSeeAll: () -> Unit) {
    if (spends.isEmpty()) return
    val overlays = LocalOverlays.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    SectionHeader(title = "Top spending", actionLabel = "Insights", onAction = onSeeAll)
    CategoryStack(
        spends = spends,
        onOpen = { spend ->
            scope.launch {
                // The sheet sits above the navigation graph: it closes, and this does the navigating.
                val seeAll = overlays.sheet<Boolean>(expandable = true) { dismiss ->
                    CategorySpendSheet(spend, onSeeAll = { dismiss(true) })
                }
                if (seeAll == true) navigator.openCategoryHistory(spend.categoryId, spend.month)
            }
        },
        modifier = Modifier.padding(top = AppSpacing.xs, bottom = AppSpacing.lg),
    )
}

/** Recurring payments and reminders in the next 7 days. */
@Composable
private fun ComingUp(recurring: List<Pair<LocalDateTime, RecurringRule>>, onSeeAll: (PlanSection) -> Unit) {
    val finance = rememberFinanceState()
    val financeActions = rememberFinanceActions()
    val flows = rememberReminderFlows()
    val remindersState by LocalReminders.current.state.collectAsState()
    val horizon = LocalDateTime.now().plusDays(COMING_UP_DAYS)
    val reminders = remindersState.upcoming().filter { !it.effectiveDueAt.isAfter(horizon) }

    val items: List<Pair<LocalDateTime, @Composable () -> Unit>> = buildList<Pair<LocalDateTime, @Composable () -> Unit>> {
        for ((date, rule) in recurring) {
            add(
                date to {
                    val category = finance.category(rule.categoryId)
                    ListRow(
                        headline = rule.payee ?: category?.name ?: rule.type.label,
                        supporting = "${dayHeading(date)} · Recurring",
                        leading = { if (rule.type == TransactionType.Transfer) TransferAvatar() else CategoryAvatar(category) },
                        trailing = {
                            TransactionAmount(rule.type, rule.amount, finance.money, style = MaterialTheme.typography.titleSmall)
                        },
                        onClick = { rule.id?.let(financeActions::editRecurring) },
                        supportingMaxLines = 1,
                    )
                },
            )
        }
        for (r: Reminder in reminders) {
            add(
                r.effectiveDueAt to {
                    ListRow(
                        headline = r.title,
                        supporting = "${reminderDueText(r)} · Reminder",
                        leading = { LeadingIcon(r.category.icon) },
                        onClick = { flows.edit(r) },
                        supportingMaxLines = 1,
                    )
                },
            )
        }
    }.sortedBy { it.first }
    if (items.isEmpty()) return

    SectionHeader(
        title = "Next 7 days",
        actionLabel = "Plans",
        onAction = { onSeeAll(if (recurring.isNotEmpty()) PlanSection.Recurring else PlanSection.Reminders) },
    )
    ListSection {
        for ((_, row) in items.take(COMING_UP_MAX)) item { row() }
    }
}
