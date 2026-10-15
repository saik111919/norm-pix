package com.example.normpix.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.navigation.AmountsVisibilityButton
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
import com.example.normpix.finance.domain.comparisonRange
import com.example.normpix.finance.domain.relativeChange
import com.example.normpix.finance.domain.shareOf
import com.example.normpix.finance.domain.upcomingOccurrences
import com.example.normpix.finance.ui.AsyncValue
import com.example.normpix.finance.ui.CategoryAvatar
import com.example.normpix.finance.ui.CategorySpend
import com.example.normpix.finance.ui.CategorySpendSheet
import com.example.normpix.finance.ui.CategoryStack
import com.example.normpix.finance.ui.Hero
import com.example.normpix.finance.ui.HeroAmount
import com.example.normpix.finance.ui.HeroChangePill
import com.example.normpix.finance.ui.HeroEyebrow
import com.example.normpix.finance.ui.HeroFigure
import com.example.normpix.finance.ui.HeroSlot
import com.example.normpix.finance.ui.HeroTile
import com.example.normpix.finance.ui.HeroTone
import com.example.normpix.finance.ui.ProgressRing
import com.example.normpix.finance.ui.comparedWith
import com.example.normpix.finance.ui.trendIcon
import com.example.normpix.finance.ui.SwipeableTransactionSection
import com.example.normpix.finance.ui.TransactionAmount
import com.example.normpix.finance.ui.TransactionTile
import com.example.normpix.finance.ui.TransferAvatar
import com.example.normpix.finance.ui.budgetTone
import com.example.normpix.finance.ui.dayHeading
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.monthLabel
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.finance.ui.rememberFinanceQuery
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.AppSearchButton
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.PlanSection
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.reminders.Reminder
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
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardStack
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.rememberCardFlows
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/** How far ahead "Next 7 days" looks. */
private const val COMING_UP_DAYS = 7L
private const val COMING_UP_MAX = 5

/** How many categories the Top spending stack holds. */
private const val TOP_SPENDING = 5

/**
 * Where every section on Home starts: its header sits one step below whatever is above it, so the sections keep one rhythm
 * whatever they hold (a stack ends on its front item, a list on its last row). Same start as the default header.
 */
internal val HomeSectionPadding = PaddingValues(
    start = AppSpacing.lg + AppSpacing.xs,
    top = AppSpacing.xxl,
    end = AppSpacing.sm,
    bottom = AppSpacing.sm,
)

/** What Home shows, read in one pass whenever finance data changes. */
private class HomeData(
    val month: MonthKey,
    val summary: PeriodSummary,
    /** The same days of the month before (all of it once this month is over), for "more or less than this time last month". */
    val previous: PeriodSummary,
    val recent: List<FinanceTransaction>,
    val topSpending: List<CategoryTotal>,
    /** What each category came to over the same days of the month before ([previous]'s days), to say whether it is up or down. */
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
            val compare = comparisonRange(month, now.toLocalDate())
            val upcoming = repo.recurringRules()
                .filter { it.active && !it.isFinished }
                .flatMap { rule -> upcomingOccurrences(rule, 8).filter { !it.isAfter(horizon) }.map { it to rule } }
                .sortedBy { it.first }
            return HomeData(
                month = month,
                summary = repo.summary(month.range),
                previous = repo.summary(compare),
                recent = repo.transactions(TransactionQuery(), limit = 5).items,
                topSpending = rollUpToParents(repo.categoryTotals(month.range, TransactionType.Expense), parents).take(TOP_SPENDING),
                // The same days of last month as the total above (all of it once this month is over): a month still going is
                // never set against the whole of the one before, which would make every category look down.
                previousSpending = rollUpToParents(repo.categoryTotals(compare, TransactionType.Expense), parents)
                    .associate { it.categoryId to it.amount },
                budgets = repo.budgetOverview(month),
                upcoming = upcoming,
            )
        }
    }
}

/**
 * Home: this month at a glance and the quick adds, then whatever needs attention (a stack of banners for each kind), then
 * sections for the cards, recent activity, top spending and what's coming up - each links to the tab that owns it.
 */
@Composable
fun HomeScreen(listState: LazyListState = rememberLazyListState()) {
    val navigator = LocalNavigator.current
    val result by rememberFinanceQuery(Unit) { HomeData.load(this) }

    AppPage(
        title = "Home",
        actions = {
            AmountsVisibilityButton()
            AppSearchButton()
        },
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
                        // The month first, then whatever needs attention (stacked by kind), then the rest in sections.
                        MonthOverview(data, onClick = { navigator.go(AppDestination.Insights) })
                        QuickAdd()
                        NeedsAttention(data.budgets)
                        CardsOnHome()
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
    SectionHeader(title = "Cards", padding = HomeSectionPadding)
    if (container.lock.isWalletLocked(prefs)) {
        ListSection {
            item {
                ListRow(
                    headline = "Cards are locked",
                    supporting = "Unlock to see them here",
                    leading = { LeadingIcon(Icons.Outlined.Lock) },
                    onClick = {
                        scope.launch {
                            if (!container.lock.authenticate("Unlock your wallet")) snackbars.show("Couldn't unlock. Try again.", SnackbarKind.Error)
                        }
                    },
                )
            }
        }
        return
    }
    CardStack(cards = cards, onOpen = flows::showCard, bankIconStyle = prefs.bankIconStyle)
}

/**
 * Where this month stands, as the hero group. The main card: what has gone out so far as the loudest number on the screen
 * and a pill saying how that compares with the same point last month; it opens Insights. Under it, two tiles with one
 * figure each: what is left of the budget (it opens Budgets) - or, without a budget, what came in - and what you hold in
 * total (it opens the Wallet).
 */
@Composable
private fun MonthOverview(data: HomeData, onClick: () -> Unit) {
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    val money = finance.money
    val s = data.summary
    val change = relativeChange(s.expense, data.previous.expense)
    val budget = data.budgets.headline

    Hero(
        onClick = onClick,
        tiles = listOf(
            { slot ->
                if (budget != null) {
                    BudgetTile(slot, budget, money, onClick = { navigator.openPlans(PlanSection.Budgets) })
                } else {
                    HeroTile(slot, HeroTone.Secondary) { HeroFigure(label = "Income", value = money.format(s.income)) }
                }
            },
            { slot ->
                HeroTile(slot, if (budget != null) HeroTone.Secondary else HeroTone.Tertiary, onClick = { navigator.go(AppDestination.Wallet) }) {
                    HeroFigure(label = "Total balance", value = money.format(finance.totalBalance))
                }
            },
        ),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            HeroEyebrow("Spent in ${monthLabel(data.month)}")
            HeroAmount(money.format(s.expense))
        }
        if (change != null) {
            Spacer(Modifier.height(AppSpacing.sm))
            HeroChangePill(trendIcon(change), comparedWith(change, "this time last month"))
        }
    }
}

/**
 * The month's budget as a tile of the hero: a ring showing how much is used, and beside it what is left (or how far over).
 * Words carry the state; the ring's color only reinforces it.
 */
@Composable
private fun BudgetTile(slot: HeroSlot, status: BudgetStatus, money: MoneyFormatter, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val percent = status.usedPercent
    val over = status.health == BudgetHealth.Exceeded
    val tone = if (status.health == BudgetHealth.Healthy) scheme.tertiary else budgetTone(status.health).resolve().color
    val label = if (over) "Over budget by" else "Left in budget"
    val value = money.format(if (over) -status.remaining else status.remaining)

    HeroTile(slot, HeroTone.Tertiary, onClick = onClick) {
        Row(
            Modifier.clearAndSetSemantics { contentDescription = "$label $value, $percent% of ${money.format(status.allocated)} used" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                progress = status.utilization.toFloat(),
                color = tone,
                trackColor = LocalContentColor.current.copy(alpha = 0.16f),
                size = 44.dp,
                thickness = 5.dp,
            )
            Spacer(Modifier.width(AppSpacing.md))
            HeroFigure(label = label, value = value)
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
        SectionHeader(title = "Recent", padding = HomeSectionPadding)
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
    SectionHeader(title = "Recent", actionLabel = "See all", onAction = onSeeAll, padding = HomeSectionPadding)
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
    SectionHeader(title = "Top spending", actionLabel = "Insights", onAction = onSeeAll, padding = HomeSectionPadding)
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
        modifier = Modifier.padding(top = AppSpacing.xs),
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
        padding = HomeSectionPadding,
    )
    ListSection {
        for ((_, row) in items.take(COMING_UP_MAX)) item { row() }
    }
}
