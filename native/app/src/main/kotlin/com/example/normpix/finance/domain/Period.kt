package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** Half-open local-time interval `[start, end)`. */
data class DateRange(val start: LocalDateTime, val end: LocalDateTime) {
    operator fun contains(moment: LocalDateTime): Boolean = !moment.isBefore(start) && moment.isBefore(end)
}

/**
 * A calendar month, used for monthly summaries, budgets, and navigation.
 * Stored as `yyyymm` integers (e.g. 202609) where it needs to be persisted.
 */
data class MonthKey(val year: Int, val month: Int) : Comparable<MonthKey> {
    init {
        require(month in 1..12) { "month out of range: $month" }
    }

    fun toInt(): Int = year * 100 + month

    val start: LocalDateTime get() = LocalDate.of(year, month, 1).atStartOfDay()

    /** Exclusive end - the first moment of the next month. */
    val end: LocalDateTime get() = next.start

    val range: DateRange get() = DateRange(start, end)

    val dayCount: Int get() = YearMonth.of(year, month).lengthOfMonth()

    fun addMonths(delta: Int): MonthKey {
        val zeroBased = year * 12 + (month - 1) + delta
        return MonthKey(Math.floorDiv(zeroBased, 12), Math.floorMod(zeroBased, 12) + 1)
    }

    val next: MonthKey get() = addMonths(1)
    val previous: MonthKey get() = addMonths(-1)

    /** Whole months from [other] to this (positive when this is later). */
    fun monthsSince(other: MonthKey): Int = (year - other.year) * 12 + (month - other.month)

    fun isAfter(other: MonthKey): Boolean = this > other
    fun isBefore(other: MonthKey): Boolean = this < other

    override fun compareTo(other: MonthKey): Int = toInt().compareTo(other.toInt())

    override fun toString(): String = "MonthKey($year-$month)"

    companion object {
        fun of(moment: LocalDateTime): MonthKey = MonthKey(moment.year, moment.monthValue)
        fun of(day: LocalDate): MonthKey = MonthKey(day.year, day.monthValue)
        fun fromInt(value: Int): MonthKey = MonthKey(value / 100, value % 100)
    }
}

/** Midnight at the start of [moment]'s day. */
fun startOfDay(moment: LocalDateTime): LocalDateTime = moment.toLocalDate().atStartOfDay()
