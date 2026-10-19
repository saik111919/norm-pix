package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime

/*
 * How a repeat stops, and the date maths behind it. Pure and free of the clock and the time zone: every step is plain calendar
 * arithmetic on local dates and times, so a daylight-saving change on the way never moves an occurrence by an hour (or a day).
 *
 * The same three ways to end serve a repeating payment ([RepeatPlan]) and a repeating reminder: both are given the step that
 * takes one occurrence to the next, so this file knows nothing about either.
 */

/** When a repeat stops: never, on a day, or once it has happened a number of times. */
sealed interface RepeatEnd {
    /** It goes on until the user stops it. */
    data object Never : RepeatEnd

    /** The last occurrence is the latest one on or before [date]. */
    data class OnDate(val date: LocalDate) : RepeatEnd

    /** It happens [count] times in all, the first one (the one the repeat starts from) counted. */
    data class After(val count: Int) : RepeatEnd

    companion object {
        /** The end a stored end date stands for: a day, or none. (A count is kept as the date of its last occurrence.) */
        fun of(stored: LocalDateTime?): RepeatEnd = stored?.let { OnDate(it.toLocalDate()) } ?: Never
    }
}

/** The three choices the user is offered; the [RepeatEnd] each makes is built by [RepeatEnd.switchedTo]. */
enum class RepeatEndKind { Never, OnDate, After }

val RepeatEnd.kind: RepeatEndKind
    get() = when (this) {
        RepeatEnd.Never -> RepeatEndKind.Never
        is RepeatEnd.OnDate -> RepeatEndKind.OnDate
        is RepeatEnd.After -> RepeatEndKind.After
    }

/** The fewest times "After" offers: once is not a repeat. */
const val MIN_REPEAT_COUNT = 2

/** The most times a repeat can be set to happen. */
const val MAX_REPEAT_COUNT = 999

/** What "After" starts at when it is first chosen. */
const val DEFAULT_REPEAT_COUNT = 12

/** Counting occurrences stops here, so a daily repeat set to run for fifty years is not walked day by day on every redraw. */
const val REPEAT_COUNT_LIMIT = 1000

/** The step from one occurrence to the next; null when there is none (a repeat that has run out). */
typealias RepeatStep = (LocalDateTime) -> LocalDateTime?

/** The occurrence number [n] (1 is [start] itself), or null when the repeat runs out before getting there. */
fun nthOccurrence(start: LocalDateTime, n: Int, next: RepeatStep): LocalDateTime? {
    var at = start
    repeat(n - 1) { at = next(at) ?: return null }
    return at
}

/**
 * How many occurrences there are up to and including [lastDay], [start] being the first, and the last of them. None when [start]
 * is already after [lastDay]. Counting stops at [limit] ([capped] says it did, so the count is "at least that many").
 */
class RepeatRun(val count: Int, val last: LocalDateTime?, val capped: Boolean)

fun repeatRunThrough(start: LocalDateTime, lastDay: LocalDate, next: RepeatStep, limit: Int = REPEAT_COUNT_LIMIT): RepeatRun {
    var count = 0
    var last: LocalDateTime? = null
    var at: LocalDateTime? = start
    while (at != null && !at.toLocalDate().isAfter(lastDay)) {
        if (count == limit) return RepeatRun(count, last, capped = true)
        count++
        last = at
        at = next(at)
    }
    return RepeatRun(count, last, capped = false)
}

/**
 * The moment the repeat is over after, to store as its end date, for a repeat that starts at [start] and steps with [next]:
 * null for [RepeatEnd.Never]. A day ends it at that day's own time of the occurrences (so an occurrence on the last day is
 * still inside it); a count ends it at the last occurrence itself, which is how "after 12 payments" is kept without a column
 * for the count. A day before the start is read as the start's day, and a count is held to 1..[MAX_REPEAT_COUNT].
 */
fun RepeatEnd.resolve(start: LocalDateTime, next: RepeatStep): LocalDateTime? = when (this) {
    RepeatEnd.Never -> null
    is RepeatEnd.OnDate -> {
        val first = start.toLocalDate()
        LocalDateTime.of(if (date.isBefore(first)) first else date, start.toLocalTime())
    }
    is RepeatEnd.After -> nthOccurrence(start, count.coerceIn(1, MAX_REPEAT_COUNT), next)
}

/** This end with a day before [day] moved up to it: an end can't come before the first occurrence. */
fun RepeatEnd.notBefore(day: LocalDate): RepeatEnd = if (this is RepeatEnd.OnDate && date.isBefore(day)) RepeatEnd.OnDate(day) else this

/**
 * The end that [kind] stands for when the user switches to it, carrying over what the current end already says so a switch
 * doesn't throw it away: a count becomes the day of its last occurrence, a day becomes the number of occurrences it holds.
 * From none, a day starts a year after [start] and a count at [DEFAULT_REPEAT_COUNT].
 */
fun RepeatEnd.switchedTo(kind: RepeatEndKind, start: LocalDateTime, next: RepeatStep): RepeatEnd {
    if (this.kind == kind) return this
    return when (kind) {
        RepeatEndKind.Never -> RepeatEnd.Never
        RepeatEndKind.OnDate -> {
            val day = (this as? RepeatEnd.After)?.let { resolve(start, next) }?.toLocalDate() ?: start.toLocalDate().plusYears(1)
            RepeatEnd.OnDate(day)
        }
        RepeatEndKind.After -> {
            val held = (this as? RepeatEnd.OnDate)?.let { repeatRunThrough(start, it.date, next).count }
            RepeatEnd.After((held ?: DEFAULT_REPEAT_COUNT).coerceIn(MIN_REPEAT_COUNT, MAX_REPEAT_COUNT))
        }
    }
}

/** The step a payment schedule takes from one payment to the next, the days of the month kept as [start]'s. */
fun recurrenceStep(start: LocalDateTime, frequency: RecurrenceFrequency, interval: Int): RepeatStep =
    { from -> nextRecurrence(from, frequency, interval, anchor = start) }

/**
 * A payment's repeat as the editors hold it while it is being set: how often, and how it [ends]. It becomes the
 * [RepeatSchedule] to save once the first payment's date is final ([schedule]), so a count follows the date, the
 * frequency and the interval the user changes after choosing it.
 */
data class RepeatPlan(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val ends: RepeatEnd = RepeatEnd.Never,
) {
    /** The step from one payment to the next, when [start] is the first. */
    fun step(start: LocalDateTime): RepeatStep = recurrenceStep(start, frequency, interval)

    /** The schedule this plan makes when [start] is the first payment. */
    fun schedule(start: LocalDateTime): RepeatSchedule = RepeatSchedule(frequency, interval, ends.resolve(start, step(start)))

    companion object {
        /** The plan a stored schedule stands for. */
        fun of(schedule: RepeatSchedule): RepeatPlan = RepeatPlan(schedule.frequency, schedule.interval, RepeatEnd.of(schedule.endDate))
    }
}
