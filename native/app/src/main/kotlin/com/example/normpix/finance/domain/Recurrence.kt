package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/* Pure recurrence math for [RecurringRule]s. */

private fun at(date: LocalDate, from: LocalDateTime): LocalDateTime =
    LocalDateTime.of(date, LocalTime.of(from.hour, from.minute))

/**
 * The occurrence after [from]. Monthly/yearly rules keep the day of month of
 * [anchor] (the rule's start date), clamped to shorter months - a rule
 * starting on the 31st lands on Feb 28/29, then back on Mar 31.
 */
fun nextRecurrence(
    from: LocalDateTime,
    frequency: RecurrenceFrequency,
    interval: Int,
    anchor: LocalDateTime,
): LocalDateTime {
    val step = if (interval < 1) 1 else interval
    return when (frequency) {
        RecurrenceFrequency.Daily -> at(from.toLocalDate().plusDays(step.toLong()), from)
        RecurrenceFrequency.Weekly -> at(from.toLocalDate().plusDays(7L * step), from)
        RecurrenceFrequency.Monthly -> {
            val zeroBased = from.year * 12 + (from.monthValue - 1) + step
            val year = zeroBased / 12
            val month = zeroBased % 12 + 1
            val day = anchor.dayOfMonth.coerceIn(1, YearMonth.of(year, month).lengthOfMonth())
            at(LocalDate.of(year, month, day), from)
        }
        RecurrenceFrequency.Yearly -> {
            val year = from.year + step
            val day = anchor.dayOfMonth.coerceIn(1, YearMonth.of(year, anchor.monthValue).lengthOfMonth())
            at(LocalDate.of(year, anchor.monthValue, day), from)
        }
    }
}

private fun RecurringRule.following(date: LocalDateTime): LocalDateTime =
    nextRecurrence(date, frequency, interval, anchor = startDate)

/** Result of catching a rule up to "now". */
class DueOccurrences(
    /** Occurrences to record, oldest first. */
    val dates: List<LocalDateTime>,
    /** The first occurrence still in the future (the rule's new next date). */
    val nextDate: LocalDateTime,
)

/**
 * Every occurrence of [rule] due on or before [now] (and not after its end
 * date), capped at [cap] so a rule left inactive for years can't flood the
 * ledger in one go.
 */
fun dueOccurrences(rule: RecurringRule, now: LocalDateTime, cap: Int = 400): DueOccurrences {
    val dates = mutableListOf<LocalDateTime>()
    var next = rule.nextDate
    val end = rule.endDate
    while (!next.isAfter(now) && (end == null || !next.isAfter(end)) && dates.size < cap) {
        dates += next
        next = rule.following(next)
    }
    return DueOccurrences(dates, next)
}

/**
 * The next [count] dates a rule will produce after its current next date
 * (inclusive), for previews like "Next: 1 Oct, 1 Nov, 1 Dec".
 */
fun upcomingOccurrences(rule: RecurringRule, count: Int): List<LocalDateTime> {
    val dates = mutableListOf<LocalDateTime>()
    var next = rule.nextDate
    val end = rule.endDate
    while (dates.size < count && (end == null || !next.isAfter(end))) {
        dates += next
        next = rule.following(next)
    }
    return dates
}

/**
 * The first occurrence of [rule]'s schedule (anchored at its start date)
 * strictly after [after]. Used when a rule is resumed or its schedule
 * changes, so occurrences missed while paused are skipped rather than
 * back-filled.
 */
fun firstOccurrenceAfter(rule: RecurringRule, after: LocalDateTime): LocalDateTime {
    var next = rule.startDate
    // Bounded: a daily rule started ~270 years ago would be the worst case.
    var i = 0
    while (i < 100_000 && !next.isAfter(after)) {
        next = rule.following(next)
        i++
    }
    return next
}
