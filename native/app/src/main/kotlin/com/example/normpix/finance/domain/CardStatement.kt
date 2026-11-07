package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * What a credit card's bill really is (2026-11-07). A card's balance is everything owed on it, but what has to be paid by the due date
 * is the statement - what was owed on the day the bank made the bill up - less what was paid since; what was charged after that day
 * is for the next statement. Showing the whole balance as "due" overstates the bill by the days of spending since.
 *
 * Nothing is stored: the repository reads the card's balance at its last statement and the money put onto it since
 * (`FinanceRepository.cardStatementFigures`), and [cardBill] works out the rest. A card with no statement day has no statement to
 * speak of, so it keeps the old reading - everything owed so far - and is said to be that.
 */

/** What the repository knows of a card at its last statement: when it was, what was owed on it then, and what has been paid in since. */
data class StatementFigures(
    /** The last statement date on or before today. */
    val statementDate: LocalDate,
    /** What was owed at the end of that day (never negative). */
    val owedAtStatement: Long,
    /** Money put onto the card since: payments (transfers in) and anything credited to it (refunds). */
    val paidSince: Long,
)

enum class BillKind {
    /** The last statement's bill, still to pay, with its due date ahead (or today). */
    Statement,

    /** No statement day to go by: everything owed so far, against the next due date. */
    Open,

    /** The last statement's due date has passed and some of it is unpaid. */
    Overdue,
}

/**
 * A card's bill as the banners show it.
 *
 * @property amount what is to pay by the due date ([BillKind.Statement]), what is owed so far ([BillKind.Open]), or what is unpaid past it ([BillKind.Overdue]).
 * @property owedNow everything owed on the card, with what was charged since the statement.
 * @property daysAway the days from today to [dueDate]; negative once it has passed.
 * @property start where the bar runs from: the statement date, or - for [BillKind.Open] - what [billCycle] says.
 * @property fraction how far through [start]..[dueDate] today is (1 once overdue).
 */
data class CardBill(
    val kind: BillKind,
    val amount: Long,
    val owedNow: Long,
    val dueDate: LocalDate,
    val daysAway: Int,
    val start: LocalDate,
    val fraction: Float,
) {
    /** Near enough to be worth saying so loudly (or past it). */
    val soon: Boolean get() = daysAway <= BillDue.DUE_SOON_DAYS

    /** "today", "tomorrow" or "in 8 days"; for an overdue bill, "yesterday" or "5 days ago". */
    val inWords: String
        get() = when {
            daysAway == 0 -> "today"
            daysAway == 1 -> "tomorrow"
            daysAway > 1 -> "in $daysAway days"
            daysAway == -1 -> "yesterday"
            else -> "${-daysAway} days ago"
        }
}

/**
 * The bill for a card, or null when there is none to show: nothing is owed on it, it has no due day, or what was billed is paid
 * (what is owed beyond that is not due yet).
 *
 * @param balance the card's balance now (owed is negative).
 * @param figures the card's statement figures, or null for a card with no statement day.
 */
fun cardBill(statementDay: Int?, dueDay: Int?, balance: Long, figures: StatementFigures?, today: LocalDate): CardBill? {
    if (dueDay == null) return null
    val owedNow = maxOf(0L, -balance)
    if (owedNow == 0L) return null
    if (statementDay == null || figures == null) {
        val cycle = billCycle(statementDay, dueDay, today) ?: return null
        return CardBill(BillKind.Open, owedNow, owedNow, cycle.due.date, cycle.due.daysAway, cycle.start, cycle.fraction)
    }
    val statement = figures.statementDate
    // The statement's own due date: the first due day after the statement was made up.
    val dueDate = nextOnDay(dueDay, statement.plusDays(1))
    // Still to pay of the bill - never more than is owed now (a refund after the statement can have cleared some of it).
    val unpaid = (figures.owedAtStatement - figures.paidSince).coerceIn(0L, owedNow)
    if (unpaid == 0L) return null
    val days = ChronoUnit.DAYS.between(today, dueDate).toInt()
    if (days < 0) return CardBill(BillKind.Overdue, unpaid, owedNow, dueDate, days, statement, 1f)
    val span = ChronoUnit.DAYS.between(statement, dueDate).toFloat().coerceAtLeast(1f)
    val gone = ChronoUnit.DAYS.between(statement, today).toFloat()
    return CardBill(BillKind.Statement, unpaid, owedNow, dueDate, days, statement, (gone / span).coerceIn(0f, 1f))
}
