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
