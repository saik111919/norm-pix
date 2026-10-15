package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.ui.BankBadge
import com.example.normpix.finance.data.TransactionCursor
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.PeriodSummary
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.pathName
import com.example.normpix.finance.domain.startOfDay
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.TransactionSwipeActions
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SearchFilterBar
import com.example.normpix.ui.components.SwipeRevealHost
import com.example.normpix.ui.components.segmentShape
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** Transactions for one account, pushed from Wallet. */
@Composable
fun AccountHistoryPage(accountId: Long) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val navigator = LocalNavigator.current
    val account = finance.account(accountId)
    TransactionHistory(
        title = account?.name ?: "Account",
        onBack = navigator::back,
        lockedAccountId = accountId,
        actions = if (account == null) {
            null
        } else {
            {
                IconButton(onClick = { actions.manageAccount(account) }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Account options")
                }
            }
        },
        header = if (account == null) null else ({ AccountBalanceHeader(accountId) }),
    )
}

/** Transactions in one category for one month, pushed from Insights. */
@Composable
fun CategoryHistoryPage(categoryId: Long, month: MonthKey, range: DateRange? = null) {
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    TransactionHistory(
        title = finance.category(categoryId)?.name ?: "Category",
        onBack = navigator::back,
        initialMonth = month,
        // Opened from days picked on Insights: those days, as a date filter that can be removed to get back to the month.
        initialFilters = TransactionQuery(range = range, categoryIds = setOf(categoryId)),
    )
}

@Composable
private fun AccountBalanceHeader(accountId: Long) {
    val finance = rememberFinanceState()
    val account = finance.account(accountId) ?: return
    Row(Modifier.padding(bottom = AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        BankBadge(finance.catalog.byKey(account.bankKey), account.kind.icon)
        Spacer(Modifier.width(AppSpacing.lg))
        SummaryStat(
            label = if (account.archived) "Balance · archived" else "${account.kind.label} balance",
            value = finance.money.format(finance.balanceOf(accountId)),
            valueStyle = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val PAGE_SIZE = 40

/**
 * What a [TransactionHistory] is showing right now, for a header that wants to say what it adds up to: the totals of [query]
 * (the month or range, the search and every filter applied), the query itself, and [periodLabel], the period in words.
 */
class HistoryView(val summary: PeriodSummary, val query: TransactionQuery, val periodLabel: String)

/**
 * Searchable, filterable transaction timeline, grouped by day and loaded page
 * by page from the database as the user scrolls. It shows one month at a time
 * or - the month being only a filter over the one history - everything.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionHistory(
    title: String,
    modifier: Modifier = Modifier,
    /** The page was pushed over another: the way back (see [AppPage]). */
    onBack: (() -> Unit)? = null,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    /** Held under the app bar, outside the scrolling list (see [AppPage]). */
    pinnedHeader: (@Composable () -> Unit)? = null,
    /** Restricts the history to one account (the account filter is hidden). */
    lockedAccountId: Long? = null,
    /** Restricts the history to the expenses spent on one person (the people filter is hidden). */
    lockedPersonId: Long? = null,
    header: (@Composable () -> Unit)? = null,
    /** A header that shows what the list below it adds up to - its [HistoryView] follows the month, search and filters. */
    summaryHeader: (@Composable (HistoryView) -> Unit)? = null,
    /** Whether the "Spent ... Received ..." line shows under the controls (a header that already says it can turn it off). */
    showSpentLine: Boolean = true,
    /** Month shown first (defaults to the current month). */
    initialMonth: MonthKey? = null,
    initialFilters: TransactionQuery = TransactionQuery(),
    /** Whether every month is shown at once. Null keeps that choice inside the history; give both to own it (see the shell). */
    allTime: Boolean? = null,
    onAllTimeChange: ((Boolean) -> Unit)? = null,
    listState: LazyListState = rememberLazyListState(),
    insetBottom: Boolean = true,
) {
    val finance = rememberFinanceState()
    val controller = LocalFinance.current
    val overlays = LocalOverlays.current
    val financeActions = rememberFinanceActions()
    val scope = rememberCoroutineScope()
    val prefs by LocalAppContainer.current.preferences.state.collectAsState()
    val swipe = if (prefs.swipeActionsEnabled) prefs.transactionSwipeActions else null

    var month by remember { mutableStateOf(initialMonth ?: MonthKey.of(LocalDateTime.now())) }
    var ownAllTime by remember { mutableStateOf(false) }
    val showAll = allTime ?: ownAllTime
    val setAllTime = { all: Boolean -> if (onAllTimeChange != null) onAllTimeChange(all) else ownAllTime = all }
    var filters by remember { mutableStateOf(initialFilters) }
    var searchInput by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }

    var state by remember { mutableStateOf<AsyncValue<Unit>>(AsyncValue.Loading) }
    var items by remember { mutableStateOf(emptyList<FinanceTransaction>()) }
    var next by remember { mutableStateOf<TransactionCursor?>(null) }
    var summary by remember { mutableStateOf(PeriodSummary.Empty) }
    var hasAnyData by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(searchInput) {
        if (searchInput != searchText) {
            delay(250)
            searchText = searchInput
        }
    }

    val searching = searchText.isNotBlank()

    /** Search and "All time" look across every month; otherwise the list shows one month - unless a custom date range was chosen. */
    val query = remember(filters, searchText, month, lockedAccountId, lockedPersonId, showAll) {
        filters.copy(
            text = if (searching) searchText else null,
            range = filters.range ?: if (searching || showAll) null else month.range,
            accountIds = if (lockedAccountId == null) filters.accountIds else setOf(lockedAccountId),
            personIds = if (lockedPersonId == null) filters.personIds else setOf(lockedPersonId),
        )
    }
    val hasUserFilters = filters.hasFilters || filters.range != null || searching

    LaunchedEffect(query, finance.revision, retry) {
        try {
            val repo = controller.repository
            val page = repo.transactions(query, limit = PAGE_SIZE)
            val totals = repo.summaryFor(query)
            // Whether there is anything to show at all: for one person's history, whether they have any expense.
            val anyData = page.items.isNotEmpty() || if (lockedPersonId != null) {
                repo.transactions(TransactionQuery(personIds = setOf(lockedPersonId)), limit = 1).items.isNotEmpty()
            } else {
                repo.firstActiveMonth() != null
            }
            items = page.items
            next = page.next
            summary = totals
            hasAnyData = anyData
            state = AsyncValue.Data(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state = AsyncValue.Failure(e)
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, next, query) {
        val cursor = next
        if (nearEnd && cursor != null && !loadingMore) {
            loadingMore = true
            try {
                val page = controller.repository.transactions(query, after = cursor, limit = PAGE_SIZE)
                items = items + page.items
                next = page.next
            } finally {
                loadingMore = false
            }
        }
    }

    val filterCount = filters.types.size +
        (if (lockedAccountId == null) filters.accountIds.size else 0) +
        (if (lockedPersonId == null) filters.personIds.size else 0) +
        filters.categoryIds.size +
        (if (filters.minAmount != null || filters.maxAmount != null) 1 else 0) +
        (if (filters.range != null) 1 else 0)

    fun openFilters() {
        scope.launch {
            val result = overlays.sheet<TransactionQuery> { dismiss ->
                TransactionFilterSheet(
                    current = filters,
                    showAccounts = lockedAccountId == null,
                    showPeople = lockedPersonId == null,
                    onDone = dismiss,
                )
            }
            if (result != null) filters = result
        }
    }

    fun clearAll() {
        searchInput = ""
        searchText = ""
        filters = TransactionQuery()
    }

    val today = LocalDateTime.now()
    // What the list covers, in words: the custom range, every date a search looks across, all time, or the month shown.
    val periodLabel = filters.range?.let { "${shortDate(it.start)} – ${shortDate(it.end.minusDays(1))}" }
        ?: if (searching) "All dates" else if (showAll) "All time" else monthLabel(month)

    SwipeRevealHost(modifier) {
    AppPage(
        title = title,
        onBack = onBack,
        actions = actions,
        pinnedHeader = pinnedHeader,
        listState = listState,
        bottomPadding = AppLayout.fabClearance,
        insetBottom = insetBottom,
    ) {
        item(key = "controls") {
            Column(Modifier.padding(AppSpacing.page)) {
                header?.invoke()
                summaryHeader?.invoke(HistoryView(summary, query, periodLabel))
                // Search and filters are one control; what is filtered follows as removable chips.
                HistorySearchBar(
                    search = searchInput,
                    onSearchChange = { searchInput = it },
                    filters = filters,
                    filterCount = filterCount,
                    onOpenFilters = ::openFilters,
                    onChange = { filters = it },
                )
                FilterChips(
                    chips = activeFilterChips(finance, filters, lockedAccountId) { filters = it },
                    modifier = Modifier.padding(top = AppSpacing.sm),
                )
                if (!searching && filters.range == null) {
                    if (showAll) {
                        AllTimeSwitcher(onByMonth = { setAllTime(false) }, modifier = Modifier.padding(top = AppSpacing.sm))
                    } else {
                        MonthSwitcher(
                            month = month,
                            latest = MonthKey.of(today),
                            onChanged = { month = it },
                            modifier = Modifier.padding(top = AppSpacing.sm),
                            trailing = { MonthScopeButton("All time", onClick = { setAllTime(true) }) },
                        )
                    }
                } else {
                    Spacer(Modifier.height(AppSpacing.md))
                }
                if (showSpentLine && state is AsyncValue.Data && items.isNotEmpty()) {
                    Text(
                        listOfNotNull(
                            "Spent ${finance.money.format(summary.expense)}",
                            // Only expenses are ever spent on a person, so a list of one person's has nothing received to report.
                            if (query.personIds.isEmpty()) "Received ${finance.money.format(summary.income)}" else null,
                            if (searching) "all dates" else if (showAll && filters.range == null) "all time" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = AppSpacing.xs, end = AppSpacing.xs, bottom = AppSpacing.xs),
                    )
                }
            }
        }

        when (val current = state) {
            is AsyncValue.Loading -> item(key = "loading") { AppLoadingState(label = "Loading transactions") }
            is AsyncValue.Failure -> item(key = "failure") {
                AppErrorState(
                    title = "Couldn't load transactions",
                    message = "Your data is safe on this device. Try again.",
                    onRetry = { retry++ },
                )
            }
            is AsyncValue.Data -> if (items.isEmpty()) {
                item(key = "empty") {
                    val addExpense = { financeActions.addTransaction(TransactionType.Expense, personId = lockedPersonId) }
                    val personName = finance.person(lockedPersonId)?.name
                    when {
                        !hasAnyData && personName != null -> AppEmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "Nothing spent on $personName yet",
                            message = "Expenses you record for $personName will appear here, with what they add up to.",
                            actionLabel = "Add an expense",
                            actionIcon = Icons.Rounded.Add,
                            onAction = addExpense,
                        )
                        !hasAnyData -> AppEmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "Your spending history will appear here",
                            message = "Record what you spend and earn. Everything stays on this device.",
                            actionLabel = "Add your first expense",
                            actionIcon = Icons.Rounded.Add,
                            onAction = { financeActions.addTransaction() },
                        )
                        hasUserFilters -> AppEmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = "No matching transactions",
                            message = "Try other words or remove some filters.",
                            actionLabel = "Clear search and filters",
                            onAction = ::clearAll,
                        )
                        else -> AppEmptyState(
                            icon = Icons.Outlined.EventBusy,
                            title = if (personName != null) {
                                "Nothing spent on $personName in ${monthLabel(month)}"
                            } else {
                                "Nothing recorded in ${monthLabel(month)}"
                            },
                            actionLabel = "Add expense",
                            actionIcon = Icons.Rounded.Add,
                            onAction = addExpense,
                        )
                    }
                }
            } else {
                timeline(
                    items,
                    today,
                    swipe,
                    perspectiveAccountId = lockedAccountId,
                    onOpen = financeActions::showDetails,
                    onEdit = financeActions::editTransaction,
                    onDelete = financeActions::deleteTransaction,
                )
                if (loadingMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(AppSpacing.lg), contentAlignment = Alignment.Center) {
                            AppLoadingIndicator(Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
    }
}

/** Day headers followed by the day's transactions as tonal segments. */
private fun androidx.compose.foundation.lazy.LazyListScope.timeline(
    items: List<FinanceTransaction>,
    now: LocalDateTime,
    swipe: TransactionSwipeActions?,
    perspectiveAccountId: Long?,
    onOpen: (FinanceTransaction) -> Unit,
    onEdit: (FinanceTransaction) -> Unit,
    onDelete: (FinanceTransaction) -> Unit,
) {
    val groups = items.groupBy { startOfDay(it.occurredAt) }
    for ((day, rows) in groups) {
        // Rows glide to their new places when one is deleted, edited into another day or filtered out, rather than jumping.
        item(key = "day-$day") { DayHeader(day, now, Modifier.animateItem()) }
        rows.forEachIndexed { index, t ->
            item(key = "tx-${t.id}") {
                Box(Modifier.animateItem().padding(horizontal = AppSpacing.lg).padding(top = if (index == 0) 0.dp else 2.dp)) {
                    SwipeableTransactionRow(
                        transaction = t,
                        shape = segmentShape(isFirst = index == 0, isLast = index == rows.lastIndex),
                        swipe = swipe,
                        onOpen = { onOpen(t) },
                        onEdit = { onEdit(t) },
                        onDelete = { onDelete(t) },
                        perspectiveAccountId = perspectiveAccountId,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: LocalDateTime, now: LocalDateTime, modifier: Modifier = Modifier) {
    Text(
        dayHeading(day, now),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = AppSpacing.xl, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.sm)
            .semantics { heading() },
    )
}

/**
 * The search field with the filter button joined to it (see [SearchFilterBar]). The chevron opens the quick filters - the way
 * on to the whole set, the three kinds of transaction, and clearing them all.
 */
@Composable
private fun HistorySearchBar(
    search: String,
    onSearchChange: (String) -> Unit,
    filters: TransactionQuery,
    filterCount: Int,
    onOpenFilters: () -> Unit,
    onChange: (TransactionQuery) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    SearchFilterBar(
        value = search,
        onValueChange = onSearchChange,
        placeholder = "Search",
        filterCount = filterCount,
        menuExpanded = menuOpen,
        onMenuExpandedChange = { menuOpen = it },
    ) {
        DropdownMenuItem(
            text = { Text("All filters") },
            leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null) },
            onClick = {
                menuOpen = false
                onOpenFilters()
            },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
        for (type in TransactionType.entries) {
            val on = type in filters.types
            DropdownMenuItem(
                text = { Text(type.label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) },
                leadingIcon = { Icon(type.icon, contentDescription = null) },
                trailingIcon = if (on) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
                onClick = {
                    menuOpen = false
                    onChange(filters.copy(types = if (on) filters.types - type else filters.types + type))
                },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .then(if (on) Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) else Modifier)
                    .semantics { selected = on },
            )
        }
        HorizontalDivider(Modifier.padding(vertical = AppSpacing.xs))
        DropdownMenuItem(
            text = { Text("Clear filters") },
            leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
            enabled = filterCount > 0,
            onClick = {
                menuOpen = false
                onChange(TransactionQuery(text = filters.text))
            },
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

/** The removable chips of what is filtered - nothing at all, not even a gap, while nothing is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(chips: List<@Composable () -> Unit>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        chips.forEach { it() }
    }
}

/** The removable chips for every active filter. */
private fun activeFilterChips(
    finance: FinanceState,
    filters: TransactionQuery,
    lockedAccountId: Long?,
    update: (TransactionQuery) -> Unit,
): List<@Composable () -> Unit> {
    val chips = mutableListOf<@Composable () -> Unit>()

    @Composable
    fun Chip(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        label: String,
        removeLabel: String,
        glyph: (@Composable () -> Unit)? = null,
        onRemove: () -> Unit,
    ) {
        InputChip(
            selected = false,
            onClick = onRemove,
            label = { Text(label) },
            leadingIcon = { if (glyph != null) glyph() else Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                Icon(Icons.Rounded.Close, contentDescription = removeLabel, modifier = Modifier.size(18.dp))
            },
        )
    }

    filters.range?.let { range ->
        chips += {
            Chip(
                Icons.Outlined.DateRange,
                "${shortDate(range.start)} – ${shortDate(range.end.minusDays(1))}",
                "Remove date filter",
            ) { update(filters.copy(range = null)) }
        }
    }
    for (type in filters.types) {
        chips += { Chip(type.icon, type.label, "Remove ${type.label} filter") { update(filters.copy(types = filters.types - type)) } }
    }
    if (lockedAccountId == null) {
        for (id in filters.accountIds) {
            chips += {
                Chip(
                    finance.account(id)?.kind?.icon ?: Icons.Outlined.Wallet,
                    finance.account(id)?.name ?: "Account",
                    "Remove account filter",
                ) { update(filters.copy(accountIds = filters.accountIds - id)) }
            }
        }
    }
    for (id in filters.personIds) {
        chips += {
            val person = finance.person(id)
            Chip(
                Icons.Outlined.Person,
                person?.name ?: "Person",
                "Remove person filter",
                glyph = if (person != null) ({ PersonGlyph(person, LocalContentColor.current, 18.dp) }) else null,
            ) { update(filters.copy(personIds = filters.personIds - id)) }
        }
    }
    for (id in filters.categoryIds) {
        chips += {
            val category = finance.category(id)
            Chip(
                categoryIcon(category?.icon ?: "other"),
                category?.let { finance.categories.pathName(it) } ?: "Category",
                "Remove category filter",
                glyph = { CategoryGlyph(category?.icon ?: "other", LocalContentColor.current, 18.dp) },
            ) { update(filters.copy(categoryIds = filters.categoryIds - id)) }
        }
    }
    if (filters.minAmount != null || filters.maxAmount != null) {
        chips += {
            Chip(
                Icons.Rounded.Tune,
                listOfNotNull(
                    filters.minAmount?.let { "≥ ${finance.money.format(it, trimZeroFraction = true)}" },
                    filters.maxAmount?.let { "≤ ${finance.money.format(it, trimZeroFraction = true)}" },
                ).joinToString("  "),
                "Remove amount filter",
            ) { update(filters.copy(minAmount = null, maxAmount = null)) }
        }
    }
    return chips
}
