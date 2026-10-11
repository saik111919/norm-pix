package com.example.normpix.finance.domain

import java.time.LocalDate

/** Whether [month] is the month [today] falls in, i.e. its figures are still growing. */
fun isInProgress(month: MonthKey, today: LocalDate): Boolean = month == MonthKey.of(today)

/**
 * The part of the previous month that a figure for [month] should be compared with. A finished month is
 * compared with the whole previous month; a month still in progress only with the same number of days, so
 * "5 days into October" is never set against "all of September".
 */
fun comparisonRange(month: MonthKey, today: LocalDate): DateRange {
    val previous = month.previous
    if (!isInProgress(month, today)) return previous.range
    val sameDays = previous.start.plusDays(today.dayOfMonth.toLong())
    return DateRange(previous.start, if (sameDays.isBefore(previous.end)) sameDays else previous.end)
}

/** How one category's spending moved between two comparable periods. */
data class CategoryChange(val categoryId: Long, val current: Long, val previous: Long) {
    val delta: Long get() = current - previous
}

/** Every category seen in either period, with its amount in each (0 where it had none). */
fun categoryChanges(current: List<CategoryTotal>, previous: List<CategoryTotal>): List<CategoryChange> {
    val now = current.associate { it.categoryId to it.amount }
    val before = previous.associate { it.categoryId to it.amount }
    return (now.keys + before.keys).map { id -> CategoryChange(id, now[id] ?: 0, before[id] ?: 0) }
}

fun List<CategoryChange>.largestIncrease(): CategoryChange? = filter { it.delta > 0 }.maxByOrNull { it.delta }

fun List<CategoryChange>.largestDecrease(): CategoryChange? = filter { it.delta < 0 }.minByOrNull { it.delta }
