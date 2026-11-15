package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/*
 * The bills calendar (2026-11-15): what is due on each day of one month - the credit cards' bills, the repeating payments, the
 * reminders and the entries already dated ahead - and what it adds up to. Pure and exact (integer minor units); nothing is stored.
 *
 * It is a view over what the sources already know, not a tracker: a card bill is there while the card still owes it (Home's banners),
 * a repeating payment from the day it is next due, a reminder until it is done, an entry from its own day ahead. A payment that was
 * made is in the ledger and in Activity, not here, and nothing says "paid" or "unpaid" beyond what a source says of itself (a bill
 * that is past due, a reminder that is late).
 */

/** The calendar looks one month back from the month [today] is in. */
fun calendarFirstMonth(today: LocalDate): YearMonth = YearMonth.from(today).minusMonths(1)

/** The calendar looks as far ahead as the forecast does: twelve months past this one. */
fun calendarLastMonth(today: LocalDate): YearMonth = YearMonth.from(today).plusMonths(12)

/** A credit card with a bill to pay, as Home's banners show it. */
class CalendarBill(val account: Account, val bill: CardBill)

/**
 * A reminder as the calendar needs it: when it is due ([dueAt] is its `effectiveDueAt`, so a snoozed one is where it was put), whether it
 * is done, and what it is about - a repeating payment ([ruleId]) or an account ([accountId], a card's bill) - when it is tied to one.
 */
class CalendarReminder(
    val id: Long,
    val title: String,
    val dueAt: LocalDateTime,
    val done: Boolean = false,
    val ruleId: Long? = null,
    val accountId: Long? = null,
)

/** What a month is worked out from: gathered by the screen, so the working-out itself can stay pure. */
class CalendarFacts(
    val today: LocalDate,
    val accounts: List<Account>,
    val bills: List<CalendarBill>,
    val rules: List<RecurringRule>,
    /** What is entered already and dated after [today] (the calendar leaves out any that is not). */
    val upcoming: List<FinanceTransaction>,
    val reminders: List<CalendarReminder>,
)

/** What a day's dot in the grid says: money going out, money coming in, or a reminder. */
enum class DayMark { Out, In, Reminder }

/** Where an item came from, and the thing a tap on it opens. */
sealed interface CalendarRef {
    class Bill(val bill: CalendarBill) : CalendarRef

    class Rule(val rule: RecurringRule) : CalendarRef

    class Entry(val transaction: FinanceTransaction) : CalendarRef

    class Reminder(val reminder: CalendarReminder) : CalendarRef
}

/**
 * One thing on a day. A card bill and a repeating payment or entry have an [amount]; a reminder has none. [type] is the money's own
 * (null for a bill and a reminder), [toCard] a transfer onto a credit card (a card payment). A bill is [overdue] when it is past due, a
 * reminder when its day has gone and it is not done. [reminded] says a reminder about the same payment or bill is folded into it, so it
 * is not listed twice.
 */
class CalendarItem(
    val date: LocalDate,
    val ref: CalendarRef,
    val type: TransactionType?,
    val amount: Long?,
    val title: String?,
    val accountId: Long?,
    val toAccountId: Long?,
    val categoryId: Long?,
    val toCard: Boolean,
    val overdue: Boolean,
    val reminded: Boolean,
) {
    val mark: DayMark
        get() = when {
            ref is CalendarRef.Reminder -> DayMark.Reminder
            type == TransactionType.Income -> DayMark.In
            else -> DayMark.Out
        }

    /** The order inside a day: bills, then money going out, money coming in, reminders. */
    internal val rank: Int
        get() = when {
            ref is CalendarRef.Bill -> 0
            ref is CalendarRef.Reminder -> 3
            type == TransactionType.Income -> 2
            else -> 1
        }
}

/**
 * One month of the calendar: its [items], by day and in order, and what they add up to. [due] is what has to be paid out - from
 * [from] to the month's end: today onwards in the month [today] is in, the whole month in any other - and [comingIn] what is to arrive
 * in the same days. A card's bill counts what is still unpaid of it once, whether or not a repeating transfer onto the card is also
 * listed; an unpaid bill that is past due counts too, in the month it was due.
 */
class BillsMonth(
    val month: YearMonth,
    val from: LocalDate,
    val items: List<CalendarItem>,
    val due: Long,
    val comingIn: Long,
) {
    private val byDay: Map<LocalDate, List<CalendarItem>> = items.groupBy { it.date }

    /** The days that have something. */
    val days: Set<LocalDate> get() = byDay.keys

    /** What is on [day], in order. */
    fun on(day: LocalDate): List<CalendarItem> = byDay[day].orEmpty()

    /** The earliest day with something, or null for an empty month. */
    val firstDay: LocalDate? get() = byDay.keys.minOrNull()
}

/**
 * The day the page opens on: today in the month [today] is in, else the first day with something (the month's first day when it is
 * empty).
 */
fun BillsMonth.defaultDay(today: LocalDate): LocalDate = when {
    month == YearMonth.from(today) -> today
    else -> firstDay ?: month.atDay(1)
}

/** One thing that moves money on a day - an occurrence of a repeating payment or an entry dated ahead - before it is known what it adds to. */
private class Money(
    val date: LocalDate,
    val ref: CalendarRef,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val payee: String?,
    val categoryId: Long?,
)

/**
 * [month] of the calendar, from [facts].
 *
 * - Card bills: each bill on its due date when that is in the month.
 * - Repeating payments: the active ones' occurrences in the month ([occurrencesUntil], with interval and end date). A rule that is
 *   paused or finished has none.
 * - Entries: those dated after today (not today's, nor the past's) in the month.
 * - Reminders: those not done, on their due day. One about a repeating payment that has an occurrence that day, or about a card that
 *   has a bill that day, is folded into that item ([CalendarItem.reminded]) instead of being listed beside it.
 *
 * The totals follow the money rules. A transfer counts as money going out when it goes to an account that is not one for spending
 * (a card, savings, a loan), and not at all between two accounts for spending; an expense counts unless it is charged to a credit card
 * that has a due day - it is in the card's bill, which is counted - and an income counts as money coming in. **A transfer onto a card dated
 * by its bill's due date pays that much of the bill**, so the transfer counts in full and the bill counts what is left: the same money is
 * never counted twice.
 */
fun billsMonth(facts: CalendarFacts, month: YearMonth): BillsMonth {
    val today = facts.today
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val from = if (month == YearMonth.from(today)) today else first
    val spendable = spendableAccounts(facts.accounts, emptyMap())
    val byId = facts.accounts.associateBy { it.id }
    // The cards whose spending goes into a bill: active, with a due day.
    val billed = facts.accounts.filter { it.kind == AccountKind.Card && !it.archived && it.dueDay != null }.mapNotNull { it.id }.toSet()

    // Everything that moves money, up to the month's end: what a card's bill is paid by may be dated before the month.
    val money = mutableListOf<Money>()
    for (rule in facts.rules) {
        if (!rule.active || rule.isFinished) continue
        for (occurrence in occurrencesUntil(rule, last)) {
            money += Money(occurrence.toLocalDate(), CalendarRef.Rule(rule), rule.type, rule.amount, rule.accountId, rule.toAccountId, rule.payee, rule.categoryId)
        }
    }
    for (t in facts.upcoming) {
        val date = t.occurredAt.toLocalDate()
        if (date.isAfter(today) && !date.isAfter(last)) {
            money += Money(date, CalendarRef.Entry(t), t.type, t.amount, t.accountId, t.toAccountId, t.payee, t.categoryId)
        }
    }

    // The reminders that are about something on the calendar that day fold into it.
    val reminders = facts.reminders.filter { !it.done && YearMonth.from(it.dueAt) == month }
    val remindedRules = reminders.mapNotNull { r -> r.ruleId?.let { it to r.dueAt.toLocalDate() } }.toSet()
    val remindedCards = reminders.mapNotNull { r -> r.accountId?.let { it to r.dueAt.toLocalDate() } }.toSet()
    val ruleDays = money.filter { it.ref is CalendarRef.Rule && it.date in first..last }
        .mapNotNull { m -> (m.ref as CalendarRef.Rule).rule.id?.let { it to m.date } }.toSet()
    val billDays = facts.bills.mapNotNull { b -> b.account.id?.let { it to b.bill.dueDate } }.toSet()

    val items = mutableListOf<CalendarItem>()
    var due = 0L
    var comingIn = 0L

    for (b in facts.bills) {
        val date = b.bill.dueDate
        if (date !in first..last) continue
        val id = b.account.id
        items += CalendarItem(
            date = date,
            ref = CalendarRef.Bill(b),
            type = null,
            amount = b.bill.amount,
            title = b.account.name,
            accountId = id,
            toAccountId = null,
            categoryId = null,
            toCard = false,
            overdue = b.bill.kind == BillKind.Overdue,
            reminded = id != null && (id to date) in remindedCards,
        )
        // What is still to pay of it counts in the window - and when it is late, whenever in the month it fell due.
        if (date >= from || b.bill.kind == BillKind.Overdue) {
            val by = if (date.isBefore(today)) today else date
            val paid = money.filter { it.type == TransactionType.Transfer && it.toAccountId == id && !it.date.isAfter(by) }.sumOf { it.amount }
            due += (b.bill.amount - paid).coerceAtLeast(0L)
        }
    }

    for (m in money) {
        if (m.date !in first..last) continue
        val toCard = byId[m.toAccountId]?.kind == AccountKind.Card
        val ref = m.ref
        val rule = (ref as? CalendarRef.Rule)?.rule
        items += CalendarItem(
            date = m.date,
            ref = ref,
            type = m.type,
            amount = m.amount,
            title = m.payee,
            accountId = m.accountId,
            toAccountId = m.toAccountId,
            categoryId = m.categoryId,
            toCard = toCard,
            overdue = false,
            reminded = rule?.id?.let { (it to m.date) in remindedRules } == true,
        )
        if (m.date < from) continue
        when (m.type) {
            TransactionType.Income -> comingIn += m.amount
            TransactionType.Expense -> if (m.accountId !in billed) due += m.amount
            TransactionType.Transfer -> if (m.toAccountId == null || m.toAccountId !in spendable) due += m.amount
        }
    }

    for (r in reminders) {
        val date = r.dueAt.toLocalDate()
        if (r.ruleId != null && (r.ruleId to date) in ruleDays) continue
        if (r.accountId != null && (r.accountId to date) in billDays) continue
        items += CalendarItem(
            date = date,
            ref = CalendarRef.Reminder(r),
            type = null,
            amount = null,
            title = r.title,
            accountId = r.accountId,
            toAccountId = null,
            categoryId = null,
            toCard = false,
            overdue = date.isBefore(today),
            reminded = false,
        )
    }

    val ordered = items.sortedWith(
        compareBy<CalendarItem> { it.date }
            .thenBy { it.rank }
            .thenByDescending { it.amount ?: 0L }
            .thenBy { (it.ref as? CalendarRef.Reminder)?.reminder?.dueAt ?: LocalDateTime.MIN }
            .thenBy { it.title.orEmpty().lowercase() },
    )
    return BillsMonth(month = month, from = from, items = ordered, due = due, comingIn = comingIn)
}
