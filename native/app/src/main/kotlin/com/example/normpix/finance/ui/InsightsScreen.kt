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
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
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
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.MonthTotals
import com.example.normpix.finance.domain.PeopleSpending
import com.example.normpix.finance.domain.PeriodSummary
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.categoryChanges
import com.example.normpix.finance.domain.parentMap
import com.example.normpix.finance.domain.percentLabel
import com.example.normpix.finance.domain.rollUpToParents
import com.example.normpix.finance.domain.comparisonRange
import com.example.normpix.finance.domain.isInProgress
import com.example.normpix.finance.domain.largestDecrease
import com.example.normpix.finance.domain.largestIncrease
import com.example.normpix.finance.domain.relativeChange
import com.example.normpix.finance.domain.shareOf
import com.example.normpix.navigation.AmountsVisibilityButton
import com.example.normpix.navigation.AppNavigator
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
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** What Insights shows: one month (the default), or days the user picked. */
private sealed interface InsightsPeriod {
    /** The days shown. */
    val range: DateRange

    /** How many days are shown. */
    val days: Int

    /** As many days, just before [range] (the month before, for a month): the hero's dashed line is drawn from them. */
    val before: DateRange

    /** How many days [before] has. */
    val beforeDays: Int

    /** The part of [before] the figures are set against: the same number of days while the period is still going. */
    fun comparison(today: LocalDate): DateRange

    /** True while [today] is one of the days shown. */
    fun stillGoing(today: LocalDate): Boolean

    data class Month(val month: MonthKey) : InsightsPeriod {
        override val range: DateRange get() = month.range
        override val days: Int get() = month.dayCount
        override val before: DateRange get() = month.previous.range
        override val beforeDays: Int get() = month.previous.dayCount
        override fun comparison(today: LocalDate): DateRange = comparisonRange(month, today)
        override fun stillGoing(today: LocalDate): Boolean = isInProgress(month, today)
    }

    data class Days(override val range: DateRange) : InsightsPeriod {
        override val days: Int get() = dayCount(range)
        override val before: DateRange get() = DateRange(range.start.minusDays(days.toLong()), range.start)
        override val beforeDays: Int get() = days

        // The first as many days of [before] as have passed of the range, as a month in progress is set against the
        // same days of the month before - so the pill says what the hero's two lines show where they end.
        override fun comparison(today: LocalDate): DateRange =
            DateRange(before.start, before.start.plusDays(elapsedDays(range.start, days, today).toLong()))

        override fun stillGoing(today: LocalDate): Boolean = today.atStartOfDay() in range
    }
}

/** Everything Insights shows for one period, read in one pass. */
private class InsightsData(
    val period: InsightsPeriod,
    val summary: PeriodSummary,
    /** The comparable slice of the period before (see [InsightsPeriod.comparison]): the same days while [inProgress], else all of it. */
    val previous: PeriodSummary,
    val previousExpenses: List<CategoryTotal>,
    val inProgress: Boolean,
    val expenses: List<CategoryTotal>,
    val income: List<CategoryTotal>,
    val daily: List<DailyTotal>,
    /** What went out on each day of the whole period before: the hero's reference line. */
    val previousDaily: List<DailyTotal>,
    /** The months up to the one shown; nothing while days that were picked are shown. */
    val trend: List<MonthTotals>,
    val hasAnyData: Boolean,
    /** What the period's spending came to on each person; nothing when no expense names one. */
    val people: PeopleSpending = PeopleSpending.Empty,
) {
    /** The month shown, or null while days that were picked are. */
    val month: MonthKey? get() = (period as? InsightsPeriod.Month)?.month

    /** The first day shown. */
    val start: LocalDateTime get() = period.range.start

    /** "in October", or "from 3 Oct to 15 Oct" ("on 15 Oct" for one day): the period after a verb. */
    val within: String
        get() = when (val p = period) {
            is InsightsPeriod.Month -> "in ${monthLabel(p.month)}"
            is InsightsPeriod.Days ->
                if (p.days == 1) "on ${shortDate(p.range.start)}" else "from ${shortDate(p.range.start)} to ${shortDate(p.range.end.minusDays(1))}"
        }

    /** One category's transactions over the period shown. */
    fun openCategory(navigator: AppNavigator, categoryId: Long) {
        when (val p = period) {
            is InsightsPeriod.Month -> navigator.openCategoryHistory(categoryId, p.month)
            is InsightsPeriod.Days -> navigator.openCategoryHistory(categoryId, p.range)
        }
    }

    companion object {
        suspend fun load(repo: FinanceRepository, period: InsightsPeriod): InsightsData {
            val today = LocalDate.now()
            val range = period.range
            val compare = period.comparison(today)
            // Spending is shown per top-level category: a sub-category's counts toward its parent (its own detail is a tap away).
            val parents = repo.categories().parentMap()
            return InsightsData(
            period = period,
            summary = repo.summary(range),
            previous = repo.summary(compare),
            previousExpenses = rollUpToParents(repo.categoryTotals(compare, TransactionType.Expense), parents),
            inProgress = period.stillGoing(today),
            expenses = rollUpToParents(repo.categoryTotals(range, TransactionType.Expense), parents),
            income = rollUpToParents(repo.categoryTotals(range, TransactionType.Income), parents),
            daily = repo.dailyTotals(range, TransactionType.Expense),
            previousDaily = repo.dailyTotals(period.before, TransactionType.Expense),
            // Spending by month is about months: it is left out while days that were picked are shown.
            trend = if (period is InsightsPeriod.Month) repo.monthlyTotals(period.month.addMonths(-5), period.month) else emptyList(),
            hasAnyData = repo.firstActiveMonth() != null,
            people = repo.peopleSpending(range),
            )
        }
    }
}

/**
 * The Insights tab: answers "where did my money go?", "am I spending more than
 * usual?" and "how did this month build up?" for one month at a time - or, when
 * asked, for days picked from the calendar ("Dates" beside the month).
 * Transfers are never counted as income or spending.
 */
@Composable
fun InsightsScreen(listState: LazyListState = rememberLazyListState()) {
    val actions = rememberFinanceActions()
    var month by remember { mutableStateOf(MonthKey.of(LocalDateTime.now())) }
    // Days picked from the calendar, shown instead of the month until "By month"; the month is kept for coming back to.
    var picked by remember { mutableStateOf<DateRange?>(null) }
    var picking by remember { mutableStateOf(false) }
    val chosen = picked
    val period: InsightsPeriod = if (chosen != null) InsightsPeriod.Days(chosen) else InsightsPeriod.Month(month)
    val result by rememberFinanceQuery(period) { InsightsData.load(this, period) }

    AppPage(
        title = "Insights",
        actions = { AmountsVisibilityButton() },
        maxWidth = AppLayout.maxWideContentWidth,
        listState = listState,
        insetBottom = false,
    ) {
        item(key = "month") {
            // Clear of the hero under it by the gap the hero keeps from what follows it on Home.
            Row(
                Modifier.fillMaxWidth().padding(AppSpacing.page).padding(bottom = AppSpacing.md),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (chosen == null) {
                    MonthSwitcher(
                        month = month,
                        latest = MonthKey.of(LocalDateTime.now()),
                        onChanged = { month = it },
                        modifier = Modifier.widthIn(max = 480.dp),
                        trailing = { MonthScopeButton("Dates", onClick = { picking = true }) },
                    )
                } else {
                    DateRangeSwitcher(
                        range = chosen,
                        onChange = { picking = true },
                        onByMonth = { picked = null },
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                }
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

    if (picking) {
        DateRangePickerFor(
            initial = picked,
            onPicked = {
                picked = it
                picking = false
            },
            onDismiss = { picking = false },
            latest = LocalDate.now(),
        )
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

/** Which of the days from [start] [day] is, counting [start] as 1 (a month's day of the month). */
private fun dayNumber(start: LocalDateTime, day: LocalDate): Int = ChronoUnit.DAYS.between(start.toLocalDate(), day).toInt() + 1

/** How many of the [days] from [start] have passed by [today]: all of them once they are over, none before they begin. */
private fun elapsedDays(start: LocalDateTime, days: Int, today: LocalDate = LocalDate.now()): Int =
    dayNumber(start, today).coerceIn(0, days)

/** Running totals at the end of each of the first [days] days from [start], from what went out on each day. */
private fun runningTotalsFrom(daily: List<DailyTotal>, start: LocalDateTime, days: Int): List<Long> {
    val totals = LongArray(days)
    for (d in daily) {
        val i = dayNumber(start, d.day) - 1
        if (i in 0 until days) totals[i] += d.amount
    }
    for (i in 1 until days) totals[i] += totals[i - 1]
    return totals.toList()
}

/**
 * What the comparison is against, in words: "the same days of September" while this month is still going, else
 * "September"; for days that were picked, "the 13 days before" (or the same days of them, while they are still going).
 */
private fun comparisonBasis(data: InsightsData): String = when (val p = data.period) {
    is InsightsPeriod.Month ->
        if (data.inProgress) "the same days of ${monthLabel(p.month.previous)}" else monthLabel(p.month.previous)
    is InsightsPeriod.Days -> when {
        p.days == 1 -> "the day before"
        elapsedDays(p.range.start, p.days) in 1 until p.days -> "the same days of the ${p.days} days before"
        else -> "the ${p.days} days before"
    }
}

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
        val period = data.period
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            HeroEyebrow(
                when (period) {
                    is InsightsPeriod.Month -> if (data.inProgress) "Spent this month" else "Spent in ${monthLabel(period.month)}"
                    is InsightsPeriod.Days -> "Spent ${data.within}"
                },
            )
            HeroAmount(money.format(s.expense))
        }
        if (change != null) {
            Spacer(Modifier.height(AppSpacing.sm))
            HeroChangePill(
                trendIcon(change),
                comparedWith(
                    change,
                    when (period) {
                        is InsightsPeriod.Month -> if (data.inProgress) "this time last month" else monthLabel(period.month.previous)
                        is InsightsPeriod.Days -> comparisonBasis(data)
                    },
                ),
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
    val period = data.period
    val start = data.start
    val beforeStart = period.before.start
    // Up to today in a period still going (or to the latest day anything is dated, if later); all of a finished one.
    val shown = maxOf(elapsedDays(start, period.days), data.daily.maxOfOrNull { dayNumber(start, it.day) } ?: 0)
    val current = remember(data) { runningTotalsFrom(data.daily, start, shown) }
    val previous = remember(data) {
        if (data.previousDaily.isEmpty()) emptyList() else runningTotalsFrom(data.previousDaily, beforeStart, period.beforeDays)
    }
    if ((current.lastOrNull() ?: 0L) <= 0L) return
    var day by remember(data) { mutableStateOf<Int?>(null) }

    val nowLabel = when (period) {
        is InsightsPeriod.Month -> if (data.inProgress) "This month" else monthLabel(period.month)
        is InsightsPeriod.Days -> "These days"
    }
    val spokenBefore = when (period) {
        is InsightsPeriod.Month ->
            if (data.inProgress) "Same days of ${monthLabel(period.month.previous)}" else monthLabel(period.month.previous)
        is InsightsPeriod.Days -> "${daysLabel(period.days)} before"
    }
    // The dashed line's name in the key when no day is touched.
    val beforeKey = when (period) {
        is InsightsPeriod.Month -> if (data.inProgress) "Last month" else monthLabel(period.month.previous)
        is InsightsPeriod.Days -> "Days before"
    }
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
        days = maxOf(period.days, period.beforeDays),
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
            label = if (d == null) nowLabel else "${shortDate(start.plusDays(d - 1L))} · ${money.compact(current[d - 1])}",
            textColor = keyText,
        )
        if (previous.isNotEmpty()) {
            val sameDay = d?.coerceAtMost(previous.size)
            ChartKey(
                color = reference,
                label = if (sameDay != null) {
                    "${shortDate(beforeStart.plusDays(sameDay - 1L))} · ${money.compact(previous[sameDay - 1])}"
                } else {
                    beforeKey
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

/** A month (or days) with nothing recorded: say so, and still show the trend around it so the screen isn't blank. */
@Composable
private fun EmptyMonth(data: InsightsData, onAdd: () -> Unit) {
    val month = data.month
    AppEmptyState(
        icon = Icons.Outlined.EventBusy,
        title = if (month != null) "Nothing recorded in ${monthLabelFull(month)}" else "Nothing recorded ${data.within}",
        message = if (month != null) {
            "Use the arrows above to look at another month, or add an expense."
        } else {
            "Choose other dates above, or add an expense."
        },
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

    val biggestShare = percentLabel(shareOf(biggest.amount, total))
    val month = data.month
    ListSection(
        title = "Where your money went",
        intro = if (month != null) {
            "$biggestName is your biggest spend: $biggestShare of ${monthLabel(month)}."
        } else {
            "$biggestName is your biggest spend: $biggestShare of spending ${data.within}."
        },
        footer = "Tap a category to see its transactions.",
    ) {
        for (t in rows) item {
            val category = finance.category(t.categoryId)
            val name = category?.name ?: "Deleted category"
            val percent = percentLabel(shareOf(t.amount, total))
            val count = "${t.count} ${if (t.count == 1) "transaction" else "transactions"}"
            val open = { data.openCategory(navigator, t.categoryId) }
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
            "${spending.byPerson.size} ${if (spending.byPerson.size == 1) "person" else "people"} ${data.within}.",
        footer = "The rest wasn't for anyone in particular. Tap a person to see their expenses.",
    ) {
        for (entry in top) item {
            val person = finance.person(entry.personId)
            val name = person?.name ?: "Deleted person"
            ListRow(
                headline = name,
                supporting = "${entry.count} ${if (entry.count == 1) "expense" else "expenses"} · " +
                    "${percentLabel(shareOf(entry.amount, spending.totalExpense))} of spending",
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
    // Only a month sits among months; days that were picked have no trend here.
    val month = data.month ?: return
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
    var selected by remember(month, months.size) { mutableIntStateOf(months.lastIndex) }
    val pick = months[selected.coerceIn(0, months.lastIndex)]
    val stillGoing = data.inProgress && pick.month == month
    val peak = months.maxBy { it.expense }

    Panel(
        title = "Spending by month",
        footer = buildString {
            append("The last bar is ${monthLabelFull(month)}")
            if (data.inProgress) append(" so far, left out of the average")
            append(". Tap a bar to see its month.")
        },
    ) {
        // The chart says all of this to a screen reader already.
        ChartReadout(
            label = if (stillGoing) "${monthLabelFull(pick.month)} so far" else monthLabelFull(pick.month),
            value = money.format(pick.expense),
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.height(AppSpacing.lg))
        BarChart(
            values = months.map { it.expense },
            money = money,
            semanticsLabel = label,
            labels = months.indices.associateWith { monthShort(months[it].month) },
            selected = selected,
            onSelect = { it?.let { index -> selected = index } },
            highlight = months.lastIndex.takeIf { data.inProgress },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        ChartStats(
            first = { m ->
                ChartStatTile(
                    Icons.AutoMirrored.Rounded.TrendingFlat,
                    "Average",
                    if (usual > 0) money.format(usual, trimZeroFraction = true) else "None yet",
                    m,
                    detail = "a month",
                )
            },
            second = { m ->
                ChartStatTile(Icons.Rounded.Bolt, "Peak", money.format(peak.expense, trimZeroFraction = true), m, detail = "in ${monthShort(peak.month)}")
            },
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
 * Each day of the month (or of the days picked) as a column, the daily average dashed across them. Above the chart it reads
 * the average until a day is touched, then that day (touching it again goes back). Days still to come are dots on the
 * baseline.
 */
@Composable
private fun DailySpending(data: InsightsData) {
    val money = rememberFinanceState().money
    val start = data.start
    val days = data.period.days
    val values = MutableList(days) { 0L }
    for (d in data.daily) {
        val i = dayNumber(start, d.day) - 1
        if (i in 0 until days) values[i] = d.amount
    }
    val elapsed = elapsedDays(start, days)
    // A day dated later in the period than today is drawn too: it is part of the period's total, so the average and the
    // count of days are taken over the same days the total is - otherwise a payment dated ahead would raise the average
    // with no day to spread it over ("spent on 6 of 4 days").
    val drawn = maxOf(elapsed, data.daily.maxOf { dayNumber(start, it.day) })
    val average = average(data.summary.expense, drawn)
    val peak = data.daily.maxBy { it.amount }
    val spendDays = data.daily.size
    var selected by remember(data.period) { mutableStateOf<Int?>(null) }
    val month = data.month
    // Today, while the period is still going: the column the chart marks until another is picked.
    val today = (dayNumber(start, LocalDate.now()) - 1).takeIf { data.inProgress && it in 0 until days }
    val focus = selected ?: today
    // Weekly marks (1, 8, 15, 22 and the last day for a month), and the day in focus; a mark too close to it gives way.
    val marks = listOf(0, 7, 14, 21, days - 1).filter { it in 0 until days }.distinct()
        .filter { focus == null || it == focus || kotlin.math.abs(it - focus) >= 3 }
    val labelled = (marks + listOfNotNull(focus)).distinct()

    Panel(
        title = "Day by day",
        footer = "Spent on $spendDays of $drawn days. Tap a day to see what went out on it.",
    ) {
        val day = focus
        ChartReadout(
            label = when {
                selected != null -> shortDate(start.plusDays(selected!!.toLong()))
                day != null -> "Today"
                month != null -> "Spent in ${monthLabel(month)}"
                else -> "Spent ${data.within}"
            },
            value = if (day != null) money.format(values[day]) else money.format(data.summary.expense),
            modifier = Modifier.clearAndSetSemantics {},
        )
        Spacer(Modifier.height(AppSpacing.lg))
        BarChart(
            values = values,
            money = money,
            semanticsLabel = "Spending per day ${if (month != null) "in ${monthLabelFull(month)}" else data.within}. " +
                "Daily average ${money.format(average)}. Biggest day ${shortDate(peak.day.atStartOfDay())}, ${money.format(peak.amount)}.",
            labels = labelled.associateWith { "${start.plusDays(it.toLong()).dayOfMonth}" },
            selected = selected,
            onSelect = { selected = it },
            clearable = true,
            elapsed = drawn,
            maxBarWidth = 14.dp,
            height = 168.dp,
            highlight = today,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        ChartStats(
            first = { m ->
                ChartStatTile(Icons.AutoMirrored.Rounded.TrendingFlat, "Average", money.format(average, trimZeroFraction = true), m, detail = "a day")
            },
            second = { m ->
                ChartStatTile(
                    Icons.Rounded.Bolt,
                    "Peak",
                    money.format(peak.amount, trimZeroFraction = true),
                    m,
                    detail = "on ${shortDate(peak.day.atStartOfDay())}",
                )
            },
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
                    supporting = "${percentLabel(shareOf(t.amount, total))} of income",
                    leading = { CategoryAvatar(finance.category(t.categoryId)) },
                    trailing = {
                        Text(
                            finance.money.format(t.amount),
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                        )
                    },
                    onClick = { data.openCategory(navigator, t.categoryId) },
                )
            }
        }
    }
}
