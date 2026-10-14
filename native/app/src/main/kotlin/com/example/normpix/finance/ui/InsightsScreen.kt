package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.domain.CategoryChange
import com.example.normpix.finance.domain.CategoryTotal
import com.example.normpix.finance.domain.DailyTotal
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.MonthTotals
import com.example.normpix.finance.domain.PeopleSpending
import com.example.normpix.finance.domain.PeriodSummary
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.categoryChanges
import com.example.normpix.finance.domain.parentMap
import com.example.normpix.finance.domain.rollUpToParents
import com.example.normpix.finance.domain.comparisonRange
import com.example.normpix.finance.domain.isInProgress
import com.example.normpix.finance.domain.largestDecrease
import com.example.normpix.finance.domain.largestIncrease
import com.example.normpix.finance.domain.relativeChange
import com.example.normpix.finance.domain.shareOf
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything Insights shows for one month, read in one pass. */
private class InsightsData(
    val month: MonthKey,
    val summary: PeriodSummary,
    /** The comparable slice of the previous month (see [comparisonRange]): the same days while [inProgress], else all of it. */
    val previous: PeriodSummary,
    val previousExpenses: List<CategoryTotal>,
    val inProgress: Boolean,
    val expenses: List<CategoryTotal>,
    val income: List<CategoryTotal>,
    val daily: List<DailyTotal>,
    /** What went out on each day of the whole previous month: the hero's reference line. */
    val previousDaily: List<DailyTotal>,
    val trend: List<MonthTotals>,
    val hasAnyData: Boolean,
    /** What the month's spending came to on each person; nothing when no expense names one. */
    val people: PeopleSpending = PeopleSpending.Empty,
) {
    companion object {
        suspend fun load(repo: FinanceRepository, month: MonthKey): InsightsData {
            val today = LocalDate.now()
            val compare = comparisonRange(month, today)
            // Spending is shown per top-level category: a sub-category's counts toward its parent (its own detail is a tap away).
            val parents = repo.categories().parentMap()
            return InsightsData(
            month = month,
            summary = repo.summary(month.range),
            previous = repo.summary(compare),
            previousExpenses = rollUpToParents(repo.categoryTotals(compare, TransactionType.Expense), parents),
            inProgress = isInProgress(month, today),
            expenses = rollUpToParents(repo.categoryTotals(month.range, TransactionType.Expense), parents),
            income = rollUpToParents(repo.categoryTotals(month.range, TransactionType.Income), parents),
            daily = repo.dailyTotals(month.range, TransactionType.Expense),
            previousDaily = repo.dailyTotals(month.previous.range, TransactionType.Expense),
            trend = repo.monthlyTotals(month.addMonths(-5), month),
            hasAnyData = repo.firstActiveMonth() != null,
            people = repo.peopleSpending(month.range),
            )
        }
    }
}

/**
 * The Insights tab: answers "where did my money go?", "am I spending more than
 * usual?" and "how did this month build up?" for one month at a time.
 * Transfers are never counted as income or spending.
 */
@Composable
fun InsightsScreen(listState: LazyListState = rememberLazyListState()) {
    val actions = rememberFinanceActions()
    var month by remember { mutableStateOf(MonthKey.of(LocalDateTime.now())) }
    val result by rememberFinanceQuery(month) { InsightsData.load(this, month) }

    AppPage(
        title = "Insights",
        maxWidth = AppLayout.maxWideContentWidth,
        listState = listState,
        insetBottom = false,
    ) {
        item(key = "month") {
            Row(Modifier.fillMaxWidth().padding(AppSpacing.page), horizontalArrangement = Arrangement.Center) {
                MonthSwitcher(
                    month = month,
                    latest = MonthKey.of(LocalDateTime.now()),
                    onChanged = { month = it },
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            }
        }
        item(key = "content") {
            Column(Modifier.padding(AppSpacing.page)) {
                when (val value = result) {
                    is AsyncValue.Loading -> AppLoadingState(label = "Loading insights")
                    is AsyncValue.Failure -> AppErrorState(
                        title = "Couldn't load this",
                        message = "Your data is safe on this device. Try again.",
                    )
                    is AsyncValue.Data -> {
                        val data = value.value
                        if (!data.hasAnyData) {
                            AppEmptyState(
                                icon = Icons.Outlined.Insights,
                                title = "Nothing to show yet",
                                message = "Add a few transactions to see your spending patterns.",
                                actionLabel = "Add expense",
                                actionIcon = Icons.Rounded.Add,
                                onAction = { actions.addTransaction() },
                            )
                        } else {
                            // Reading order: the answer, the months it sits among, what it is made of, what moved,
                            // and - one tap away - the supporting detail.
                            val hasActivity = data.summary.count > 0
                            val hasDetail = data.daily.isNotEmpty() || data.income.isNotEmpty()
                            Columns(
                                narrow = {
                                    if (hasActivity) Snapshot(data) else EmptyMonth(data, onAdd = { actions.addTransaction() })
                                    Trend(data)
                                    if (data.expenses.isNotEmpty()) WhereMoneyWent(data)
                                    if (data.expenses.isNotEmpty()) ByPerson(data)
                                    if (hasActivity) WhatChanged(data)
                                    if (hasDetail) MoreDetail(data)
                                    Footnote()
                                },
                                left = {
                                    if (hasActivity) Snapshot(data) else EmptyMonth(data, onAdd = { actions.addTransaction() })
                                    if (data.expenses.isNotEmpty()) WhereMoneyWent(data)
                                    if (data.expenses.isNotEmpty()) ByPerson(data)
                                    if (hasActivity) WhatChanged(data)
                                },
                                right = {
                                    Trend(data)
                                    if (hasDetail) MoreDetail(data)
                                    Footnote()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One column in reading order on phones ([narrow]); two balanced columns on wide windows. */
@Composable
private fun Columns(narrow: @Composable () -> Unit, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 760.dp) {
            Column { narrow() }
        } else {
            Row {
                Column(Modifier.weight(1f)) { left() }
                Spacer(Modifier.width(AppSpacing.xl))
                Column(Modifier.weight(1f)) { right() }
            }
        }
    }
}

/** A titled block on one tonal segment. */
@Composable
private fun Panel(title: String, footer: String? = null, content: @Composable () -> Unit) {
    ListSection(title = title, footer = footer) {
        item { Column(Modifier.padding(AppSpacing.lg)) { content() } }
    }
}

/** Rounded integer average (no floating point in money). */
private fun average(total: Long, count: Int): Long = if (count <= 0) 0 else (total + count / 2) / count

/** Days of [month] that have passed (all of them for past months). */
private fun elapsedDays(month: MonthKey): Int {
    val today = LocalDate.now()
    return if (month == MonthKey.of(today)) today.dayOfMonth else month.dayCount
}

/** What the comparison is against, in words: "the same days of September" while this month is still going, else "September". */
private fun comparisonBasis(data: InsightsData): String =
    if (data.inProgress) "the same days of ${monthLabel(data.month.previous)}" else monthLabel(data.month.previous)

/**
 * The one answer the screen exists for, as the hero (the same group as Home's): how much went out, a pill saying whether
 * that is more or less than last month, and the month building up day by day against the one before. Under it, what came
 * in and what was left over.
 */
@Composable
private fun Snapshot(data: InsightsData) {
    val finance = rememberFinanceState()
    val money = finance.money
    val s = data.summary
    val net = s.net
    val change = relativeChange(s.expense, data.previous.expense)

    Hero(
        tiles = listOf(
            { slot ->
                HeroTile(slot, HeroTone.Secondary) { HeroFigure(label = "Income", value = money.format(s.income)) }
            },
            { slot ->
                HeroTile(slot, HeroTone.Tertiary) {
                    HeroFigure(label = if (net < 0) "Overspent" else "Saved", value = money.format(abs(net)))
                }
            },
        ),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            HeroEyebrow(if (data.inProgress) "Spent this month" else "Spent in ${monthLabel(data.month)}")
            HeroAmount(money.format(s.expense))
        }
        if (change != null) {
            Spacer(Modifier.height(AppSpacing.sm))
            HeroChangePill(
                trendIcon(change),
                comparedWith(change, if (data.inProgress) "this time last month" else monthLabel(data.month.previous)),
            )
        }
        SpendingPace(data, money)
    }
}

/**
 * The hero's chart: the month's spending building up day by day, the month before dashed behind it, so "more or less than
 * usual" is a gap you can see on any day. A key under it names the two lines; touching the chart reads the day there
 * instead. Nothing when the month has nothing to draw.
 */
@Composable
private fun SpendingPace(data: InsightsData, money: MoneyFormatter) {
    val scheme = MaterialTheme.colorScheme
    val onHero = LocalContentColor.current
    val month = data.month
    val before = month.previous
    // Up to today in a month still going (or to the latest day anything is dated, if later); all of a finished one.
    val shown = maxOf(elapsedDays(month), data.daily.maxOfOrNull { it.day.dayOfMonth } ?: 0)
    val current = remember(data) { runningTotals(data.daily, shown) }
    val previous = remember(data) { if (data.previousDaily.isEmpty()) emptyList() else runningTotals(data.previousDaily, before.dayCount) }
    if ((current.lastOrNull() ?: 0L) <= 0L) return
    var day by remember(data) { mutableStateOf<Int?>(null) }

    val nowLabel = if (data.inProgress) "This month" else monthLabel(month)
    val spokenBefore = if (data.inProgress) "Same days of ${monthLabel(before)}" else monthLabel(before)
    val spoken = buildString {
        append("$nowLabel ${money.format(data.summary.expense)}")
        if (previous.isNotEmpty()) append(", $spokenBefore ${money.format(data.previous.expense)}")
    }
    val reference = onHero.copy(alpha = 0.55f)
    val keyText = onHero.copy(alpha = 0.85f)

    Spacer(Modifier.height(AppSpacing.xl))
    PaceChart(
        current = current,
        previous = previous,
        days = maxOf(month.dayCount, before.dayCount),
        semanticsLabel = spoken,
        lineColor = onHero,
        referenceColor = reference,
        guideColor = onHero.copy(alpha = 0.3f),
        ringColor = scheme.primary,
        scrubDay = day,
        onScrub = { day = it },
    )
    Spacer(Modifier.height(AppSpacing.md))
    // The chart already says this to a screen reader; the key is for the eye.
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
        val d = day
        ChartKey(
            color = onHero,
            label = if (d == null) nowLabel else "${shortDate(month.start.plusDays(d - 1L))} · ${money.compact(current[d - 1])}",
            textColor = keyText,
        )
        if (previous.isNotEmpty()) {
            val sameDay = d?.coerceAtMost(previous.size)
            ChartKey(
                color = reference,
                label = when {
                    sameDay != null -> "${shortDate(before.start.plusDays(sameDay - 1L))} · ${money.compact(previous[sameDay - 1])}"
                    data.inProgress -> "Last month"
                    else -> monthLabel(before)
                },
                dashed = true,
                textColor = keyText,
            )
        }
    }
}

/** What the numbers are, said once, at the bottom instead of repeated under every block. */
@Composable
private fun Footnote() {
    val currency = rememberFinanceState().currency
    Text(
        "Amounts in ${currency.name} (${currency.symbol.trim()}). Transfers aren't counted. Comparisons only describe, they aren't targets.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.xl),
    )
}

/** A month with nothing recorded: say so, and still show the trend around it so the screen isn't blank. */
@Composable
private fun EmptyMonth(data: InsightsData, onAdd: () -> Unit) {
    AppEmptyState(
        icon = Icons.Outlined.EventBusy,
        title = "Nothing recorded in ${monthLabelFull(data.month)}",
        message = "Use the arrows above to look at another month, or add an expense.",
        actionLabel = "Add expense",
        actionIcon = Icons.Rounded.Add,
        onAction = onAdd,
    )
}

/** What moved compared with the same period last month: the category that grew most and the one that shrank most. */
@Composable
private fun WhatChanged(data: InsightsData) {
    val finance = rememberFinanceState()
    val money = finance.money
    val changes = categoryChanges(data.expenses, data.previousExpenses)
    val up = changes.largestIncrease()
    val down = changes.largestDecrease()
    val basis = comparisonBasis(data)

    @Composable
    fun Change(label: String, change: CategoryChange, icon: ImageVector, word: String) {
        val category = finance.category(change.categoryId)
        ListRow(
            headline = category?.name ?: "Deleted category",
            supporting = "$label: ${money.format(abs(change.delta))} $word than $basis (${money.format(change.previous)} to ${money.format(change.current)})",
            supportingMaxLines = 3,
            leading = { CategoryAvatar(category) },
            trailing = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }

    ListSection(
        title = "What changed",
        footer = if (up == null && down == null) {
            "Nothing to compare yet: there was no spending in $basis."
        } else {
            "Spending categories compared with $basis."
        },
    ) {
        up?.let { item { Change("Largest increase", it, Icons.Rounded.TrendingUp, "more") } }
        down?.let { item { Change("Largest decrease", it, Icons.Rounded.TrendingDown, "less") } }
        if (up == null && down == null) {
            item { ListRow(headline = "No category changes", supporting = "Spending in each category matches $basis.") }
        }
    }
}

private const val COLLAPSED_ROWS = 5

/** Where it went: the biggest spend named in a sentence, then the categories ranked, each with its share. */
@Composable
private fun WhereMoneyWent(data: InsightsData) {
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    val money = finance.money
    var expanded by remember { mutableStateOf(false) }
    val totals = data.expenses
    val total = data.summary.expense

    val rows = if (expanded) totals else totals.take(COLLAPSED_ROWS)
    val biggest = totals.first()
    val biggestName = finance.category(biggest.categoryId)?.name ?: "Deleted category"

    ListSection(
        title = "Where your money went",
        intro = "$biggestName is your biggest spend: ${(shareOf(biggest.amount, total) * 100).roundToInt()}% of ${monthLabel(data.month)}.",
        footer = "Tap a category to see its transactions.",
    ) {
        for (t in rows) item {
            val category = finance.category(t.categoryId)
            val name = category?.name ?: "Deleted category"
            val percent = "${(shareOf(t.amount, total) * 100).roundToInt()}%"
            val count = "${t.count} ${if (t.count == 1) "transaction" else "transactions"}"
            val open = { navigator.openCategoryHistory(t.categoryId, data.month) }
            CategoryShareRow(
                category = category,
                amount = t.amount,
                share = shareOf(t.amount, total),
                onClick = open,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "$name, ${money.format(t.amount)}, $percent, $count. Show transactions"
                    role = Role.Button
                    onClick { open(); true }
                },
            )
        }
        if (totals.size > COLLAPSED_ROWS) item {
            ListRow(
                headline = if (expanded) "Show fewer" else "Show all ${totals.size} categories",
                leading = { LeadingIcon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore) },
                onClick = { expanded = !expanded },
            )
        }
    }
}

/**
 * How much of the month went on each person - the top few, with a way on to everyone. These are the same expenses as above,
 * grouped by who they were for, so they are part of the spending shown, never added to it. Where no one has been added yet
 * it is a single quiet row that explains the idea and opens People.
 */
@Composable
private fun ByPerson(data: InsightsData) {
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    val money = finance.money
    val spending = data.people
    if (spending.byPerson.isEmpty()) {
        // Offered only to someone who has not started: a row that explains the idea and opens People.
        if (finance.people.isEmpty()) {
            ListSection(title = "By person") {
                item {
                    ListRow(
                        headline = "See what you spend on each person",
                        supporting = "Add the people you spend on, then pick one when you record an expense.",
                        leading = { LeadingIcon(Icons.Outlined.Groups) },
                        onClick = navigator::openPeople,
                    )
                }
            }
        }
        return
    }
    val top = spending.byPerson.take(PEOPLE_ROWS)
    ListSection(
        title = "Spent on people",
        intro = "${money.format(spending.assigned)} of ${money.format(spending.totalExpense)} went on " +
            "${spending.byPerson.size} ${if (spending.byPerson.size == 1) "person" else "people"} in ${monthLabel(data.month)}.",
        footer = "The rest wasn't for anyone in particular. Tap a person to see their expenses.",
    ) {
        for (entry in top) item {
            val person = finance.person(entry.personId)
            val name = person?.name ?: "Deleted person"
            ListRow(
                headline = name,
                supporting = "${entry.count} ${if (entry.count == 1) "expense" else "expenses"} · " +
                    "${(shareOf(entry.amount, spending.totalExpense) * 100).roundToInt()}% of spending",
                leading = { PersonAvatar(person) },
                trailing = {
                    Text(
                        money.format(entry.amount),
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    )
                },
                onClick = { navigator.openPerson(entry.personId) },
            )
        }
        item {
            ListRow(
                headline = if (spending.byPerson.size > top.size) "See all ${spending.byPerson.size} people" else "Open People",
                leading = { LeadingIcon(Icons.Outlined.Groups) },
                onClick = navigator::openPeople,
            )
        }
    }
}

/** How many people the Insights summary lists before sending you on to the People page. */
private const val PEOPLE_ROWS = 3

/**
 * Spending month by month, from the first month with any: one series with the month on screen emphasised and the usual
 * month dashed across it, so "am I spending more than usual?" is one look. Touching a bar reads that month above the
 * chart. Months before there was anything are left out rather than drawn as empty space; with fewer than two months there
 * is no trend to show.
 */
@Composable
private fun Trend(data: InsightsData) {
    val money = rememberFinanceState().money
    val scheme = MaterialTheme.colorScheme
    val first = data.trend.indexOfFirst { it.expense > 0 }
    if (first < 0) return
    val months = data.trend.drop(first)
    if (months.size < 2) return
    // "Usual" is what the finished months came to; the month in progress is still filling up.
    val finished = if (data.inProgress) months.dropLast(1) else months
    val usual = average(finished.sumOf { it.expense }, finished.count { it.expense > 0 })
    val label = buildList {
        add("Spending by month, last ${months.size} months")
        for (m in months) add("${monthLabelFull(m.month)}: ${money.format(m.expense)}")
        if (usual > 0) add("Usual month: ${money.format(usual)}")
    }.joinToString(". ")
    var selected by remember(data.month, months.size) { mutableIntStateOf(months.lastIndex) }
    val pick = months[selected.coerceIn(0, months.lastIndex)]
    val stillGoing = data.inProgress && pick.month == data.month

    Panel(
        title = "Spending by month",
        footer = buildString {
            if (usual > 0) append("You usually spend about ${money.format(usual, trimZeroFraction = true)} a month. ")
            append("The last bar is ${monthLabelFull(data.month)}")
            if (data.inProgress) append(" so far")
            append(". Tap a bar to see its month.")
        },
    ) {
        // The chart says all of this to a screen reader already.
        ChartReadout(
            label = if (stillGoing) "${monthLabelFull(pick.month)} so far" else monthLabelFull(pick.month),
            value = money.format(pick.expense),
            modifier = Modifier.clearAndSetSemantics {},
            key = if (usual > 0) {
                { ChartKey(color = scheme.onSurfaceVariant, label = "Usual", dashed = true) }
            } else {
                null
            },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        BarChart(
            values = months.map { it.expense },
            money = money,
            semanticsLabel = label,
            labels = months.indices.associateWith { monthShort(months[it].month) },
            selected = selected,
            onSelect = { it?.let { index -> selected = index } },
            reference = usual.takeIf { it > 0 },
        )
    }
}

/** The supporting detail, one tap away: day by day, and where income came from. */
@Composable
private fun MoreDetail(data: InsightsData) {
    var open by remember { mutableStateOf(false) }
    // No subheader of its own to give it room, so it keeps its distance from the footer above it.
    ListSection(modifier = Modifier.padding(top = AppSpacing.lg)) {
        item {
            ListRow(
                headline = "More detail",
                supporting = if (data.income.isNotEmpty() && data.daily.isNotEmpty()) "Day by day and income sources" else if (data.daily.isNotEmpty()) "Day by day" else "Income sources",
                trailing = { Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null) },
                onClick = { open = !open },
                modifier = Modifier.semantics { stateDescription = if (open) "Expanded" else "Collapsed" },
            )
        }
    }
    RevealVisibility(visible = open) {
        Column {
            if (data.daily.isNotEmpty()) DailySpending(data)
            if (data.income.isNotEmpty()) IncomeSources(data)
        }
    }
}

/**
 * Each day of the month as a column, the daily average dashed across them. Above the chart it reads the average until a
 * day is touched, then that day (touching it again goes back). Days still to come are dots on the baseline.
 */
@Composable
private fun DailySpending(data: InsightsData) {
    val money = rememberFinanceState().money
    val month = data.month
    val values = MutableList(month.dayCount) { 0L }
    for (d in data.daily) values[d.day.dayOfMonth - 1] = d.amount
    val elapsed = elapsedDays(month)
    // A day dated later in the month than today is drawn too: it is part of the month's total.
    val drawn = maxOf(elapsed, data.daily.maxOf { it.day.dayOfMonth })
    val average = average(data.summary.expense, elapsed)
    val peak = data.daily.maxBy { it.amount }
    val spendDays = data.daily.size
    val scheme = MaterialTheme.colorScheme
    var selected by remember(data.month) { mutableStateOf<Int?>(null) }
    val last = month.dayCount

    Panel(
        title = "Day by day",
        footer = "Spent on $spendDays of $elapsed days. Biggest day: ${shortDate(peak.day.atStartOfDay())}, ${money.format(peak.amount)}.",
    ) {
        val day = selected
        ChartReadout(
            label = if (day == null) "Daily average" else shortDate(month.start.plusDays(day.toLong())),
            value = if (day == null) money.format(average, trimZeroFraction = true) else money.format(values[day]),
            modifier = Modifier.clearAndSetSemantics {},
            key = { ChartKey(color = scheme.onSurfaceVariant, label = "Average", dashed = true) },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        BarChart(
            values = values,
            money = money,
            semanticsLabel = "Spending per day in ${monthLabelFull(month)}. Daily average ${money.format(average)}. " +
                "Biggest day ${shortDate(peak.day.atStartOfDay())}, ${money.format(peak.amount)}.",
            labels = listOf(0, 7, 14, 21, last - 1).associateWith { "${it + 1}" },
            selected = selected,
            onSelect = { selected = it },
            clearable = true,
            reference = average,
            elapsed = drawn,
            maxBarWidth = 12.dp,
            height = 148.dp,
        )
    }
}

@Composable
private fun IncomeSources(data: InsightsData) {
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    val total = data.summary.income
    ListSection(title = "Income sources") {
        for (t in data.income) {
            item {
                ListRow(
                    headline = finance.category(t.categoryId)?.name ?: "Deleted category",
                    supporting = "${(shareOf(t.amount, total) * 100).roundToInt()}% of income",
                    leading = { CategoryAvatar(finance.category(t.categoryId)) },
                    trailing = {
                        Text(
                            finance.money.format(t.amount),
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                        )
                    },
                    onClick = { navigator.openCategoryHistory(t.categoryId, data.month) },
                )
            }
        }
    }
}
