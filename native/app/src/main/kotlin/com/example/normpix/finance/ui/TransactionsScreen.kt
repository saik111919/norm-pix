package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.components.ListSegment
import com.example.normpix.ui.components.ListRow
import com.example.normpix.finance.domain.settledCutoff
import com.example.normpix.finance.domain.dayRange
import com.example.normpix.finance.domain.MoneyFormatter
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AllInclusive
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SearchFilterBar
import com.example.normpix.ui.components.SwipeRevealHost
import com.example.normpix.ui.components.rememberBottomControls
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
    /** One day shown instead of the month (see [DaySwitcher]), or null. */
    var day by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var ownAllTime by remember { mutableStateOf(false) }
    val showAll = (allTime ?: ownAllTime) && day == null
    val setAllTime = { all: Boolean -> if (onAllTimeChange != null) onAllTimeChange(all) else ownAllTime = all }
    val showDay = { d: java.time.LocalDate ->
        day = d
        month = MonthKey.of(d)
        setAllTime(false)
    }
    // Entries dated after today are upcoming: they head the list in a group of their own, folded, rather than above today's
    // spending, and count toward nothing until their day (see settledCutoff).
    val cutoff = finance.asOf ?: settledCutoff(LocalDateTime.now())
    var upcomingOpen by remember { mutableStateOf(false) }
    var filters by remember { mutableStateOf(initialFilters) }
    var searchInput by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }

    var state by remember { mutableStateOf<AsyncValue<Unit>>(AsyncValue.Loading) }
    var items by remember { mutableStateOf(emptyList<FinanceTransaction>()) }
    var next by remember { mutableStateOf<TransactionCursor?>(null) }
    var summary by remember { mutableStateOf(PeriodSummary.Empty) }
    var upcoming by remember { mutableStateOf(emptyList<FinanceTransaction>()) }
    var upcomingSummary by remember { mutableStateOf(PeriodSummary.Empty) }
    var hasAnyData by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    LaunchedEffect(searchInput) {
        if (searchInput != searchText) {
            delay(250)
            searchText = searchInput
        }
    }

    val searching = searchText.isNotBlank()

    /** Search and "All time" look across every month; otherwise the list shows one month - unless a custom date range was chosen. */
    val shownDay = day
    val query = remember(filters, searchText, month, shownDay, lockedAccountId, lockedPersonId, showAll, cutoff) {
        filters.copy(
            text = if (searching) searchText else null,
            range = filters.range ?: when {
                searching || showAll -> null
                shownDay != null -> dayRange(shownDay)
                else -> month.range
            },
            accountIds = if (lockedAccountId == null) filters.accountIds else setOf(lockedAccountId),
            personIds = if (lockedPersonId == null) filters.personIds else setOf(lockedPersonId),
            before = cutoff,
        )
    }
    // The same search and filters over what is dated after today. Days that are all over by then have none; this month has
    // whatever is still to come, in later months too, as no month after this one can be shown; dates picked keep their bounds.
    val upcomingQuery = remember(query, cutoff, filters.range) {
        val range = query.range
        when {
            range == null || filters.range != null && range.end.isAfter(cutoff) -> query.copy(before = null, notBefore = cutoff)
            !range.end.isAfter(cutoff) -> null
            else -> query.copy(range = null, before = null, notBefore = cutoff)
        }
    }
    val hasUserFilters = filters.hasFilters || filters.range != null || searching

    LaunchedEffect(query, finance.revision, retry) {
        try {
            val repo = controller.repository
            val page = repo.transactions(query, limit = PAGE_SIZE)
            val totals = repo.summaryFor(query)
            // Soonest first: the next thing to come is the first one read.
            val ahead = upcomingQuery?.let { repo.transactions(it, limit = UPCOMING_MAX).items.asReversed() }.orEmpty()
            val aheadTotals = if (ahead.isEmpty()) PeriodSummary.Empty else repo.summaryFor(upcomingQuery!!)
            // Whether there is anything to show at all: for one person's history, whether they have any expense.
            val anyData = page.items.isNotEmpty() || ahead.isNotEmpty() || if (lockedPersonId != null) {
                repo.transactions(TransactionQuery(personIds = setOf(lockedPersonId)), limit = 1).items.isNotEmpty()
            } else {
                repo.firstActiveMonth() != null
            }
            items = page.items
            next = page.next
            summary = totals
            upcoming = ahead
            upcomingSummary = aheadTotals
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
        ?: if (searching) "All dates" else if (showAll) "All time" else shownDay?.let { dayPillLabel(it, today.toLocalDate()) } ?: monthLabel(month)

    // Search and filters are one control. They head the list - or, with Bottom controls on, sit in a card at the foot of the
    // page, within reach of a thumb, and what is filtered still follows in the list as removable chips.
    val bottomControls = rememberBottomControls()
    val searchBar: @Composable () -> Unit = {
        HistorySearchBar(
            search = searchInput,
            onSearchChange = { searchInput = it },
            filters = filters,
            filterCount = filterCount,
            onOpenFilters = ::openFilters,
            onChange = { filters = it },
        )
    }
    val listContent: LazyListScope.() -> Unit = {
        item(key = "controls") {
            Column(Modifier.padding(AppSpacing.page)) {
                header?.invoke()
                summaryHeader?.invoke(HistoryView(summary, query, periodLabel))
                if (!bottomControls) searchBar()
                FilterChips(
                    chips = activeFilterChips(finance, filters, lockedAccountId) { filters = it },
                    modifier = Modifier.padding(top = AppSpacing.sm),
                )
                if (!searching && filters.range == null) {
                    when {
                        shownDay != null -> DaySwitcher(
                            day = shownDay,
                            onChanged = showDay,
                            onByMonth = { day = null },
                            modifier = Modifier.padding(top = AppSpacing.sm),
                            latest = today.toLocalDate(),
                        )
                        showAll -> AllTimeSwitcher(onByMonth = { setAllTime(false) }, modifier = Modifier.padding(top = AppSpacing.sm))
                        else -> MonthSwitcher(
                            month = month,
                            latest = MonthKey.of(today),
                            onChanged = { month = it },
                            modifier = Modifier.padding(top = AppSpacing.sm),
                            trailing = { MonthScopeButton("All time", onClick = { setAllTime(true) }, icon = Icons.Rounded.AllInclusive) },
                            onPickDay = {
                                scope.launch {
                                    val latestDay = today.toLocalDate()
                                    val initial = if (month == MonthKey.of(latestDay)) latestDay else month.end.toLocalDate().minusDays(1)
                                    val picked = overlays.sheet<java.time.LocalDate>(expandable = true) { done -> DateSheet(initial, latestDay, onPick = done) }
                                    if (picked != null) showDay(picked)
                                }
                            },
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

        // What is dated after today, folded into one row at the head of the list until it is opened.
        fun upcomingGroup() {
            item(key = "upcoming") {
                UpcomingHeader(
                    count = upcoming.size,
                    summary = upcomingSummary,
                    open = upcomingOpen,
                    onToggle = { upcomingOpen = !upcomingOpen },
                    money = finance.money,
                    modifier = Modifier.animateItem(),
                )
            }
            if (upcomingOpen) {
                timeline(
                    upcoming,
                    today,
                    swipe,
                    perspectiveAccountId = lockedAccountId,
                    onOpen = financeActions::showDetails,
                    onEdit = financeActions::editTransaction,
                    onDelete = financeActions::deleteTransaction,
                    keyPrefix = "up-",
                    complete = true,
                    money = finance.money,
                )
            }
        }

        when (state) {
            is AsyncValue.Loading -> item(key = "loading") { AppLoadingState(label = "Loading transactions") }
            is AsyncValue.Failure -> item(key = "failure") {
                AppErrorState(
                    title = "Couldn't load transactions",
                    message = "Your data is safe on this device. Try again.",
                    onRetry = { retry++ },
                )
            }
            is AsyncValue.Data -> {
                if (upcoming.isNotEmpty()) upcomingGroup()
                if (items.isEmpty()) {
                item(key = "empty") {
                    val addExpense = { financeActions.addTransaction(TransactionType.Expense, personId = lockedPersonId) }
                    val personName = finance.person(lockedPersonId)?.name
                    when {
                        !hasAnyData && personName != null -> AppEmptyState(
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                            title = "Nothing spent on $personName yet",
                            message = "Expenses you record for $personName will appear here, with what they add up to.",
                            actionLabel = "Add an expense",
                            actionIcon = Icons.Rounded.Add,
                            onAction = addExpense,
                        )
                        !hasAnyData -> AppEmptyState(
                            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
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
                            title = when {
                                personName != null && shownDay != null -> "Nothing spent on $personName on ${dayPillLabel(shownDay, today.toLocalDate())}"
                                personName != null -> "Nothing spent on $personName in ${monthLabel(month)}"
                                shownDay != null -> "Nothing recorded on ${dayPillLabel(shownDay, today.toLocalDate())}"
                                else -> "Nothing recorded in ${monthLabel(month)}"
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
                    // A day's total is only said once all of its entries are here: the last day of a page may go on in the next.
                    complete = next == null,
                    money = finance.money,
                    onDay = if (shownDay == null) showDay else null,
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

    SwipeRevealHost(modifier) {
        if (bottomControls && onBack != null) {
            // Pushed over another page: the bottom dock's page, with the way back, the actions and the search at the bottom.
            AppSubPage(
                title = title,
                onBack = onBack,
                actions = actions,
                maxWidth = AppLayout.maxListWidth,
                listState = listState,
                bottomSearch = searchBar,
                content = listContent,
            )
        } else {
            AppPage(
                title = title,
                onBack = onBack,
                actions = actions,
                pinnedHeader = pinnedHeader,
                maxWidth = AppLayout.maxListWidth,
                listState = listState,
                bottomPadding = AppLayout.fabClearance,
                insetBottom = insetBottom,
                bottomSearch = if (bottomControls) searchBar else null,
                content = listContent,
            )
        }
    }
}

/**
 * Day headers followed by the day's transactions as tonal segments. Each header says what went out that day - once the day is
 * [complete]ly here, or the last day of a page would say less than it was - and, with [onDay], opens that day on its own.
 * [keyPrefix] keeps a second timeline in the same list (the upcoming one) apart from this one.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.timeline(
    items: List<FinanceTransaction>,
    now: LocalDateTime,
    swipe: TransactionSwipeActions?,
    perspectiveAccountId: Long?,
    onOpen: (FinanceTransaction) -> Unit,
    onEdit: (FinanceTransaction) -> Unit,
    onDelete: (FinanceTransaction) -> Unit,
    money: MoneyFormatter,
    complete: Boolean,
    keyPrefix: String = "",
    onDay: ((java.time.LocalDate) -> Unit)? = null,
) {
    val groups = items.groupBy { startOfDay(it.occurredAt) }
    val lastDay = groups.keys.lastOrNull()
    for ((day, rows) in groups) {
        val whole = complete || day != lastDay
        val spent = rows.filter { it.type == TransactionType.Expense }.sumOf { it.amount }
        // Rows glide to their new places when one is deleted, edited into another day or filtered out, rather than jumping.
        item(key = "${keyPrefix}day-$day") {
            DayHeader(
                day = day,
                now = now,
                spent = if (whole && spent > 0) money.format(spent) else null,
                onOpen = onDay?.let { open -> { open(day.toLocalDate()) } },
                modifier = Modifier.animateItem(),
            )
        }
        rows.forEachIndexed { index, t ->
            item(key = "${keyPrefix}tx-${t.id}") {
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

/**
 * A day's heading: its name ("Today", "Fri, 23 Oct"), what went out on it at the end of the line, and - when the list can show
 * the day on its own ([onOpen]) - a chevron saying a tap does that.
 */
@Composable
private fun DayHeader(day: LocalDateTime, now: LocalDateTime, spent: String?, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val name = dayHeading(day, now)
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.md, top = AppSpacing.sm, end = AppSpacing.md)
            .clip(AppRadius.rFull)
            .then(if (onOpen != null) Modifier.clickable(onClickLabel = "Show only this day", onClick = onOpen) else Modifier)
            .heightIn(min = 40.dp)
            .padding(start = AppSpacing.sm, end = AppSpacing.sm)
            .semantics(mergeDescendants = true) {
                heading()
                if (spent != null) contentDescription = "$name, spent $spent"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, color = scheme.primary, modifier = Modifier.weight(1f))
        if (spent != null) {
            Text(
                spent,
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (onOpen != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.xs).size(AppIconSizes.md),
            )
        }
    }
}

/** At most this many entries dated after today are listed in the upcoming group: what is coming soonest. */
private const val UPCOMING_MAX = 50

/**
 * The head of the entries dated after today: how many, and what they will come to, as one tonal row that opens and folds them.
 * They are listed here rather than above today's, and are in no total yet - each counts from its own day.
 */
@Composable
private fun UpcomingHeader(
    count: Int,
    summary: PeriodSummary,
    open: Boolean,
    onToggle: () -> Unit,
    money: MoneyFormatter,
    modifier: Modifier = Modifier,
) {
    val entries = if (count == 1) "1 entry" else if (count >= UPCOMING_MAX) "$UPCOMING_MAX or more entries" else "$count entries"
    val amounts = listOfNotNull(
        summary.expense.takeIf { it > 0 }?.let { "${money.format(it)} to go out" },
        summary.income.takeIf { it > 0 }?.let { "${money.format(it)} to come in" },
    ).joinToString(" · ")
    Box(modifier.padding(horizontal = AppSpacing.lg).padding(top = AppSpacing.sm)) {
        ListSegment {
            ListRow(
                headline = "Upcoming",
                supporting = listOf("$entries dated after today", amounts.ifEmpty { null }, "not counted until their day")
                    .filterNotNull().joinToString(" · "),
                supportingMaxLines = 3,
                leading = { LeadingIcon(Icons.Outlined.Schedule) },
                trailing = { Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null) },
                onClick = onToggle,
                modifier = Modifier.semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            )
        }
    }
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
