package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * A credit card's month, as its bank sets it: the day the bill is made up (the statement) and the day it must be paid by.
 * The user gives each as a day of the month, 1-31, and it comes round every month. Where a month is shorter than the day -
 * the 31st in April, the 30th in February - the date falls on that month's last day, so "31" also reads as "the last day".
 * Nothing here is stored: the next date is worked out from the day and today each time it is shown.
 */

/** The days of the month a statement or a due date can be set to. */
val BILLING_DAYS: IntRange = 1..31

/** [day] of this date's month, or the month's last day where it is shorter. */
fun LocalDate.onDayOfMonth(day: Int): LocalDate = withDayOfMonth(day.coerceIn(1, lengthOfMonth()))

/** The first date on or after [from] that falls on [day] of its month. */
fun nextOnDay(day: Int, from: LocalDate): LocalDate {
    val thisMonth = from.onDayOfMonth(day)
    return if (thisMonth.isBefore(from)) from.withDayOfMonth(1).plusMonths(1).onDayOfMonth(day) else thisMonth
}

/** When a card's bill falls due: the [date], and how many days away that is from the day it was worked out (0 is today). */
data class BillDue(val date: LocalDate, val daysAway: Int) {
    /** Near enough to be worth saying so loudly. */
    val soon: Boolean get() = daysAway <= DUE_SOON_DAYS

    /** "today", "tomorrow" or "in 8 days": how far away it is, in words. */
    val inWords: String
        get() = when (daysAway) {
            0 -> "today"
            1 -> "tomorrow"
            else -> "in $daysAway days"
        }

    /** "Due today", "Due tomorrow", or "Due 20 Nov, in 8 days": the date, once it is further away than tomorrow. */
    fun line(locale: Locale = Locale.getDefault()): String =
        if (daysAway <= 1) "Due $inWords" else "Due ${billDateText(date, locale)}, $inWords"

    companion object {
        const val DUE_SOON_DAYS = 3
    }
}

/** The next due date for a bill due on [dueDay] (today counts), or null for a card with no due day. */
fun nextBillDue(dueDay: Int?, today: LocalDate): BillDue? = dueDay?.let {
    val date = nextOnDay(it, today)
    BillDue(date, ChronoUnit.DAYS.between(today, date).toInt())
}

/** "1st", "2nd", "3rd", "4th" ... "11th", "12th", "13th", "21st", "22nd", "23rd", "31st". */
fun ordinalDay(day: Int): String {
    val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

/** A date as a billing line writes it: "20 Nov". */
fun billDateText(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofPattern("d MMM", locale))

/** The latest date on or before [from] that falls on [day] of its month (a short month's last day stands in for a day it lacks). */
fun previousOnDay(day: Int, from: LocalDate): LocalDate {
    val thisMonth = from.onDayOfMonth(day)
    return if (thisMonth.isAfter(from)) from.withDayOfMonth(1).minusMonths(1).onDayOfMonth(day) else thisMonth
}

/**
 * Where a card's bill stands in its month, for a bar that fills as the due date comes near: it runs from [start] (the last
 * statement, or - with no statement day, or one so far back that a due date has passed since - a month before the due date) to the
 * [due] date, and [fraction] of it has gone by today. Not stored: worked out from the card's two days and today each time.
 */
data class BillCycle(val start: LocalDate, val due: BillDue, val fraction: Float) {
    /** The whole days left to pay it in: 0 on the due day itself. */
    val daysLeft: Int get() = due.daysAway

    companion object {
        /** The longest a bill's bar spans: past this the bank's statement is not this bill's (a due date came between). */
        const val LONGEST_CYCLE_DAYS = 31L
    }
}

/** The bar for a card's next bill, or null for a card with no due day. */
fun billCycle(statementDay: Int?, dueDay: Int?, today: LocalDate): BillCycle? {
    val due = nextBillDue(dueDay, today) ?: return null
    val statement = statementDay?.let { previousOnDay(it, today) }
    val start = statement
        ?.takeIf { it.isBefore(due.date) && ChronoUnit.DAYS.between(it, due.date) <= BillCycle.LONGEST_CYCLE_DAYS }
        ?: due.date.minusMonths(1)
    val span = ChronoUnit.DAYS.between(start, due.date).toFloat().coerceAtLeast(1f)
    val gone = ChronoUnit.DAYS.between(start, today).toFloat()
    return BillCycle(start, due, (gone / span).coerceIn(0f, 1f))
}

/**
 * The card's two days in one line - "Statement on the 5th · due on the 20th" - or null when it has neither. Says "every
 * month" once at the end so the line reads as a rhythm, not as one date.
 */
fun billingCycleSummary(statementDay: Int?, dueDay: Int?): String? {
    val parts = listOfNotNull(
        statementDay?.let { "statement on the ${ordinalDay(it)}" },
        dueDay?.let { "due on the ${ordinalDay(it)}" },
    )
    if (parts.isEmpty()) return null
    return parts.joinToString(", ").replaceFirstChar { it.uppercase() } + " of every month"
}
