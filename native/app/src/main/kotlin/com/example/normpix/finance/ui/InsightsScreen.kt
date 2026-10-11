package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
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
 * "₹1,200 less than the same days of September" - how much more or less, and what it is compared with; or why there is
 * nothing to compare with.
 */
private fun spendingChangeSentence(data: InsightsData, money: MoneyFormatter): String {
    val basis = comparisonBasis(data)
    val previous = data.previous.expense
    if (previous == 0L) return "No spending in $basis to compare with."
    val delta = data.summary.expense - previous
    if (delta == 0L) return "The same as $basis."
    return "${money.format(abs(delta))} ${if (delta < 0) "less" else "more"} than $basis."
}

/**
 * The one answer the screen exists for: how much went out this month and whether that is more or less than usual,
 * as a single hero (the same shape and tones as Home's) - the amount, one sentence saying how it compares and with
 * what, a pair of bars that show it, and the supporting figures quietly underneath.
 */
@Composable
private fun Snapshot(data: InsightsData) {
    val finance = rememberFinanceState()
    val money = finance.money
    val s = data.summary
    val net = s.net
    val days = elapsedDays(data.month)
    val perDay = average(s.expense, days)
    val change = relativeChange(s.expense, data.previous.expense)
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val onHero = scheme.onPrimaryContainer
    val eyebrow = if (data.inProgress) "Spent this month" else "Spent in ${monthLabel(data.month)}"

    Surface(color = scheme.primaryContainer, contentColor = onHero, shape = HeroShape, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = AppSpacing.xxl, top = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xl)) {
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(eyebrow, style = type.titleMedium, color = onHero.copy(alpha = 0.8f))
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    money.format(s.expense),
                    style = type.displayMedium.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = type.displayMedium.fontSize),
                )
            }
            Spacer(Modifier.height(AppSpacing.sm))
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Icon(
                    when {
                        change == null || change == 0.0 -> Icons.Rounded.TrendingFlat
                        change < 0 -> Icons.Rounded.TrendingDown
                        else -> Icons.Rounded.TrendingUp
                    },
                    contentDescription = null,
                    tint = onHero,
                    modifier = Modifier.padding(top = 2.dp).size(AppIconSizes.md),
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(spendingChangeSentence(data, money), style = type.bodyLarge, color = onHero)
            }
            if (data.previous.expense > 0) {
                Spacer(Modifier.height(AppSpacing.lg))
                CompareBars(
                    now = s.expense,
                    before = data.previous.expense,
                    nowLabel = if (data.inProgress) "This month" else monthLabel(data.month),
                    beforeLabel = if (data.inProgress) "Same days of ${monthLabel(data.month.previous)}" else monthLabel(data.month.previous),
                    money = money,
                )
            }
            Spacer(Modifier.height(AppSpacing.xl))
            // Supporting figures sit on the page surface, so the hero keeps one idea: this month's spending.
            Surface(color = scheme.surface, shape = AppRadius.rXl, modifier = Modifier.fillMaxWidth()) {
                HeroStats(
                    modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                    stats = listOf<@Composable (Modifier) -> Unit>(
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
                                label = if (net < 0) "Overspent" else "Saved",
                                value = money.format(abs(net)),
                                valueStyle = type.titleMedium,
                                color = if (net < 0) scheme.error else null,
                                modifier = m,
                            )
                        },
                        { m: Modifier ->
                            SummaryStat(
                                label = if (data.inProgress) "Per day so far" else "Per day",
                                value = money.format(perDay, trimZeroFraction = true),
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

/** This period's spending against the comparable one, as two bars on one scale: bigger or smaller is visible at a glance. */
@Composable
private fun CompareBars(now: Long, before: Long, nowLabel: String, beforeLabel: String, money: MoneyFormatter) {
    val scheme = MaterialTheme.colorScheme
    val scale = maxOf(now, before).coerceAtLeast(1L).toFloat()
    val spoken = "$nowLabel ${money.format(now)}, $beforeLabel ${money.format(before)}"

    @Composable
    fun Bar(label: String, value: Long, color: Color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimaryContainer, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                money.format(value),
                style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = scheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(AppSpacing.xs))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(AppRadius.rFull).background(scheme.onPrimaryContainer.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth((value / scale).coerceIn(0.01f, 1f)).height(8.dp).clip(AppRadius.rFull).background(color))
        }
    }

    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spoken }) {
        Bar(nowLabel, now, scheme.onPrimaryContainer)
        Spacer(Modifier.height(AppSpacing.md))
        Bar(beforeLabel, before, scheme.onPrimaryContainer.copy(alpha = 0.4f))
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
 * Spending month by month, from the first month with any: one series, this month emphasised, so "am I spending more
 * than usual?" is one look. Months before there was anything are left out rather than drawn as empty space; with fewer
 * than two months there is no trend to show.
 */
@Composable
private fun Trend(data: InsightsData) {
    val money = rememberFinanceState().money
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
    }.joinToString(". ")

    Panel(
        title = "Spending by month",
        footer = buildString {
            if (usual > 0) append("You usually spend about ${money.format(usual, trimZeroFraction = true)} a month. ")
            append("The last bar is ${monthLabelFull(data.month)}")
            if (data.inProgress) append(" so far")
            append(".")
        },
    ) {
        GroupedBarChart(
            groups = months.map { BarGroup(monthShort(it.month), listOf(it.expense)) },
            colors = listOf(MaterialTheme.colorScheme.primary),
            highlight = months.lastIndex,
            semanticsLabel = label,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailySpending(data: InsightsData) {
    val money = rememberFinanceState().money
    val month = data.month
    val values = MutableList(month.dayCount) { 0L }
    for (d in data.daily) values[d.day.dayOfMonth - 1] = d.amount
    val elapsed = elapsedDays(month)
    val average = average(data.summary.expense, elapsed)
    val peak = data.daily.maxBy { it.amount }
    val spendDays = data.daily.size
    val scheme = MaterialTheme.colorScheme

    Panel(
        title = "Day by day",
        footer = "Spent on $spendDays of $elapsed days. Biggest day: ${shortDate(peak.day.atStartOfDay())}, ${money.format(peak.amount)}.",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            LegendItem(scheme.primary, "Spent that day")
            LegendItem(scheme.outline, "Daily average ${money.format(average, trimZeroFraction = true)}")
        }
        Spacer(Modifier.height(AppSpacing.md))
        DailyBarChart(
            values = values,
            color = scheme.primary,
            average = average,
            semanticsLabel = "Spending per day in ${monthLabelFull(month)}. Daily average ${money.format(average)}. " +
                "Biggest day ${shortDate(peak.day.atStartOfDay())}, ${money.format(peak.amount)}.",
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
