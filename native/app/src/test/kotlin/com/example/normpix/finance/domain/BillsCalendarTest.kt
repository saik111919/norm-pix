package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bills calendar: which thing lands on which day of a month (a card's bill, a repeating payment with its interval and end date,
 * a reminder, an entry dated ahead), what is left out (a paused rule, a done reminder, an entry from today or the past, a card with no
 * bill), reminders folded into what they are about, the day's order, and the summary - from today in this month, the whole month in
 * any other - with a card's bill counted once beside the transfer that pays it. Today is Saturday 14 November 2026.
 */
class BillsCalendarTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val at = LocalDateTime.of(2026, 11, 14, 9, 0)
    private val november = YearMonth.of(2026, 11)
    private val december = YearMonth.of(2026, 12)

    private val cash = account(1, "Cash", AccountKind.Cash)
    private val bank = account(2, "HDFC", AccountKind.Bank)
    private val card = account(3, "ICICI card", AccountKind.Card, statementDay = 5, dueDay = 20)
    private val savings = account(4, "Savings jar", AccountKind.Savings)
    private val looseCard = account(5, "Store card", AccountKind.Card)

    private fun account(id: Long, name: String, kind: AccountKind, statementDay: Int? = null, dueDay: Int? = null) = Account(
        id = id, name = name, kind = kind, statementDay = statementDay, dueDay = dueDay, createdAt = at, updatedAt = at,
    )

    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)

    private fun rule(
        type: TransactionType,
        amount: Long,
        on: LocalDate,
        account: Long = 2,
        to: Long? = null,
        frequency: RecurrenceFrequency = RecurrenceFrequency.Monthly,
        interval: Int = 1,
        end: LocalDate? = null,
        active: Boolean = true,
        payee: String? = null,
        id: Long = 100,
    ) = RecurringRule(
        id = id,
        type = type,
        amount = amount,
        accountId = account,
        toAccountId = to,
        categoryId = if (type == TransactionType.Transfer) null else 5,
        payee = payee,
        frequency = frequency,
        interval = interval,
        startDate = on.atTime(9, 0),
        endDate = end?.atTime(23, 0),
        nextDate = on.atTime(9, 0),
        active = active,
        createdAt = at,
        updatedAt = at,
    )

    private fun entry(type: TransactionType, amount: Long, on: LocalDate, account: Long = 2, to: Long? = null, payee: String? = null) =
        FinanceTransaction(
            id = 900,
            type = type,
            amount = amount,
            accountId = account,
            toAccountId = to,
            categoryId = if (type == TransactionType.Transfer) null else 5,
            occurredAt = on.atTime(12, 0),
            payee = payee,
            createdAt = at,
            updatedAt = at,
        )

    private fun bill(amount: Long, due: LocalDate, kind: BillKind = BillKind.Statement, owner: Account = card) = CalendarBill(
        owner,
        CardBill(kind, amount, amount, due, ChronoUnit.DAYS.between(today, due).toInt(), day(11, 5), 0.5f),
    )

    private fun reminder(
        id: Long,
        on: LocalDateTime,
        title: String = "Reminder $id",
        done: Boolean = false,
        rule: Long? = null,
        account: Long? = null,
    ) = CalendarReminder(id, title, on, done, rule, account)

    private fun facts(
        bills: List<CalendarBill> = emptyList(),
        rules: List<RecurringRule> = emptyList(),
        upcoming: List<FinanceTransaction> = emptyList(),
        reminders: List<CalendarReminder> = emptyList(),
    ) = CalendarFacts(today, listOf(cash, bank, card, savings, looseCard), bills, rules, upcoming, reminders)

    private fun BillsMonth.refs(day: LocalDate) = on(day).map { it.ref::class.simpleName }

    // ------------------------------------------------------------------------------------------- each source

    @Test
    fun `a card's bill is on its due date`() {
        val month = billsMonth(facts(bills = listOf(bill(10_000, day(11, 20)))), november)
        val item = month.on(day(11, 20)).single()
        assertTrue(item.ref is CalendarRef.Bill)
        assertEquals(10_000L, item.amount)
        assertEquals("ICICI card", item.title)
        assertEquals(3L, item.accountId)
        assertFalse(item.overdue)
        assertEquals(DayMark.Out, item.mark)
        assertEquals(setOf(day(11, 20)), month.days)
    }

    @Test
    fun `a card with no due day has no bill, and a bill due in another month is not on this one`() {
        // No bill is made for a card without a due day (Home shows none), so there is nothing to put on any day.
        assertTrue(billsMonth(facts(), november).items.isEmpty())
        val december20 = facts(bills = listOf(bill(10_000, day(12, 20))))
        assertTrue(billsMonth(december20, november).items.isEmpty())
        assertEquals(1, billsMonth(december20, december).items.size)
    }

    @Test
    fun `a repeating payment is on each of its days in the month`() {
        val weekly = rule(TransactionType.Expense, 500, day(11, 16), frequency = RecurrenceFrequency.Weekly)
        val month = billsMonth(facts(rules = listOf(weekly)), november)
        assertEquals(listOf(day(11, 16), day(11, 23), day(11, 30)), month.items.map { it.date })
        assertTrue(month.items.all { it.ref is CalendarRef.Rule })
    }

    @Test
    fun `a rule that repeats every other month is on its months only, and not after its end date`() {
        val everyOther = rule(TransactionType.Expense, 1_200, day(11, 25), interval = 2, end = day(3, 20, 2027))
        val f = facts(rules = listOf(everyOther))
        assertEquals(listOf(day(11, 25)), billsMonth(f, november).items.map { it.date })
        assertTrue(billsMonth(f, december).items.isEmpty())
        assertEquals(listOf(day(1, 25, 2027)), billsMonth(f, YearMonth.of(2027, 1)).items.map { it.date })
        // The one after is 25 March, past the end date of the 20th.
        assertTrue(billsMonth(f, YearMonth.of(2027, 3)).items.isEmpty())
    }

    @Test
    fun `a paused or finished rule has no days`() {
        val paused = rule(TransactionType.Expense, 500, day(11, 25), active = false)
        val finished = rule(TransactionType.Expense, 500, day(11, 25), end = day(11, 20), id = 101)
        assertTrue(billsMonth(facts(rules = listOf(paused, finished)), november).items.isEmpty())
    }

    @Test
    fun `an entry dated after today is on its day, one from today or the past is not`() {
        val ahead = entry(TransactionType.Expense, 3_000, day(11, 18), payee = "Croma")
        val todays = entry(TransactionType.Expense, 100, today)
        val past = entry(TransactionType.Expense, 100, day(11, 10))
        val month = billsMonth(facts(upcoming = listOf(ahead, todays, past)), november)
        val item = month.items.single()
        assertEquals(day(11, 18), item.date)
        assertTrue(item.ref is CalendarRef.Entry)
        assertEquals("Croma", item.title)
        // And in a later month only its own day.
        assertTrue(billsMonth(facts(upcoming = listOf(ahead)), december).items.isEmpty())
    }

    @Test
    fun `a reminder is on its due day - not when it is done, and not in another month`() {
        val open = reminder(1, day(11, 21).atTime(9, 0), "Pay rent")
        val done = reminder(2, day(11, 22).atTime(9, 0), done = true)
        val later = reminder(3, day(12, 1).atTime(9, 0))
        val month = billsMonth(facts(reminders = listOf(open, done, later)), november)
        val item = month.items.single()
        assertEquals(day(11, 21), item.date)
        assertEquals("Pay rent", item.title)
        assertNull(item.amount)
        assertEquals(DayMark.Reminder, item.mark)
        assertTrue(item.ref is CalendarRef.Reminder)
        assertEquals(listOf(day(12, 1)), billsMonth(facts(reminders = listOf(open, done, later)), december).items.map { it.date })
    }

    @Test
    fun `a reminder whose day has gone and is not done is late`() {
        val late = reminder(1, day(11, 10).atTime(9, 0))
        val soon = reminder(2, day(11, 20).atTime(9, 0))
        val items = billsMonth(facts(reminders = listOf(late, soon)), november).items
        assertTrue(items.first { it.date == day(11, 10) }.overdue)
        assertFalse(items.first { it.date == day(11, 20) }.overdue)
    }

    @Test
    fun `an overdue bill is on the day it was due and says so`() {
        val month = billsMonth(facts(bills = listOf(bill(8_000, day(11, 10), BillKind.Overdue))), november)
        val item = month.on(day(11, 10)).single()
        assertTrue(item.overdue)
        assertEquals(8_000L, item.amount)
    }

    // ------------------------------------------------------------------------------------------- reminders folded in

    @Test
    fun `a reminder about a repeating payment that falls that day is folded into it`() {
        val rent = rule(TransactionType.Expense, 24_000, day(11, 25), payee = "Rent", id = 7)
        val about = reminder(1, day(11, 25).atTime(9, 0), "Rent", rule = 7)
        val month = billsMonth(facts(rules = listOf(rent), reminders = listOf(about)), november)
        val item = month.on(day(11, 25)).single()
        assertTrue(item.ref is CalendarRef.Rule)
        assertTrue(item.reminded)
        assertEquals(1, month.items.size)
    }

    @Test
    fun `a reminder about a repeating payment on another day stays beside it`() {
        val rent = rule(TransactionType.Expense, 24_000, day(11, 25), payee = "Rent", id = 7)
        val moved = reminder(1, day(11, 23).atTime(9, 0), "Rent", rule = 7)
        val month = billsMonth(facts(rules = listOf(rent), reminders = listOf(moved)), november)
        assertEquals(listOf("Reminder"), month.refs(day(11, 23)))
        assertFalse(month.on(day(11, 25)).single().reminded)
    }

    @Test
    fun `a reminder about a card on the day its bill is due is folded into the bill`() {
        val about = reminder(1, day(11, 20).atTime(10, 0), "Pay ICICI", account = 3)
        val month = billsMonth(facts(bills = listOf(bill(10_000, day(11, 20))), reminders = listOf(about)), november)
        val item = month.on(day(11, 20)).single()
        assertTrue(item.ref is CalendarRef.Bill)
        assertTrue(item.reminded)
        // About an account with no bill that day it is its own item.
        val other = reminder(2, day(11, 21).atTime(10, 0), "Look at the card", account = 3)
        assertEquals(listOf("Reminder"), billsMonth(facts(bills = listOf(bill(10_000, day(11, 20))), reminders = listOf(other)), november).refs(day(11, 21)))
    }

    // ------------------------------------------------------------------------------------------- order and the default day

    @Test
    fun `a day lists the bill first, then what goes out, what comes in and the reminders`() {
        val d = day(11, 20)
        val month = billsMonth(
            facts(
                bills = listOf(bill(10_000, d)),
                rules = listOf(
                    rule(TransactionType.Income, 5_000, d, id = 1),
                    rule(TransactionType.Expense, 300, d, id = 2),
                    rule(TransactionType.Expense, 900, d, id = 3),
                ),
                reminders = listOf(reminder(1, d.atTime(8, 0), "Note")),
            ),
            november,
        )
        assertEquals(listOf("Bill", "Rule", "Rule", "Rule", "Reminder"), month.refs(d))
        // Out before in, the dearer out first.
        assertEquals(listOf(900L, 300L, 5_000L), month.on(d).filter { it.ref is CalendarRef.Rule }.map { it.amount })
        assertEquals(listOf(DayMark.Out, DayMark.Out, DayMark.Out, DayMark.In, DayMark.Reminder), month.on(d).map { it.mark })
    }

    @Test
    fun `the day it opens on is today in this month and the first day with something in another`() {
        val f = facts(
            rules = listOf(rule(TransactionType.Expense, 500, day(11, 25))),
            upcoming = listOf(entry(TransactionType.Income, 100, day(12, 9))),
        )
        assertEquals(today, billsMonth(f, november).defaultDay(today))
        // December has the rule's 25th and the entry on the 9th: the first is the 9th.
        assertEquals(day(12, 9), billsMonth(f, december).defaultDay(today))
        // A month with nothing opens on its first day.
        assertEquals(day(2, 1, 2027), billsMonth(facts(), YearMonth.of(2027, 2)).defaultDay(today))
        assertNull(billsMonth(facts(), YearMonth.of(2027, 2)).firstDay)
    }

    @Test
    fun `it looks one month back and twelve ahead`() {
        assertEquals(YearMonth.of(2026, 10), calendarFirstMonth(today))
        assertEquals(YearMonth.of(2027, 11), calendarLastMonth(today))
    }

    // ------------------------------------------------------------------------------------------- the summary

    private val scenario
        get() = facts(
            bills = listOf(bill(10_000, day(11, 20))),
            rules = listOf(
                rule(TransactionType.Expense, 20_000, day(11, 25), payee = "Rent", id = 1),
                rule(TransactionType.Income, 50_000, day(11, 30), payee = "Salary", id = 2),
            ),
            upcoming = listOf(
                entry(TransactionType.Expense, 3_000, day(11, 18)),
                entry(TransactionType.Income, 1_000, day(11, 22)),
            ),
        )

    @Test
    fun `this month adds up what is due and what comes in from today to the month's end`() {
        val month = billsMonth(scenario, november)
        assertEquals(today, month.from)
        // The bill, the rent and the entry.
        assertEquals(10_000L + 20_000 + 3_000, month.due)
        assertEquals(50_000L + 1_000, month.comingIn)
    }

    @Test
    fun `what was due before today in this month is on the calendar but not in the summary`() {
        // A repeating payment that is due on the 5th and not yet recorded: listed on its day, outside the days from today.
        val f = facts(rules = listOf(rule(TransactionType.Expense, 20_000, day(11, 5), id = 1), rule(TransactionType.Income, 7_000, day(11, 6), id = 2)))
        val month = billsMonth(f, november)
        assertEquals(setOf(day(11, 5), day(11, 6)), month.days)
        assertEquals(0L, month.due)
        assertEquals(0L, month.comingIn)
    }

    @Test
    fun `another month adds up the whole month`() {
        val month = billsMonth(scenario, december)
        assertEquals(day(12, 1), month.from)
        // The rent of the 25th and the salary of the 30th: the bill and the entries are November's.
        assertEquals(20_000L, month.due)
        assertEquals(50_000L, month.comingIn)
    }

    @Test
    fun `a bill that is past due and unpaid still counts in the month it fell due`() {
        val f = facts(bills = listOf(bill(8_000, day(11, 10), BillKind.Overdue)))
        assertEquals(8_000L, billsMonth(f, november).due)
        val lastMonth = facts(bills = listOf(bill(8_000, day(10, 28), BillKind.Overdue)))
        assertEquals(8_000L, billsMonth(lastMonth, YearMonth.of(2026, 10)).due)
        assertEquals(0L, billsMonth(lastMonth, november).due)
    }

    @Test
    fun `a transfer onto a card pays its bill, so the bill is not counted twice`() {
        // The bill is 10,000, due on the 20th; a repeating transfer of 10,000 from the bank onto the card is dated the 19th.
        val pays = rule(TransactionType.Transfer, 10_000, day(11, 19), to = 3, payee = "ICICI card bill", id = 5)
        val f = facts(bills = listOf(bill(10_000, day(11, 20))), rules = listOf(pays))
        val month = billsMonth(f, november)
        assertEquals(10_000L, month.due, "the transfer counts, and the bill counts what it leaves: nothing")
        val item = month.on(day(11, 19)).single()
        assertTrue(item.toCard)
        assertEquals(TransactionType.Transfer, item.type)
        assertEquals(DayMark.Out, item.mark)
        // A transfer that pays part of it leaves the rest.
        val part = rule(TransactionType.Transfer, 4_000, day(11, 19), to = 3, id = 5)
        assertEquals(10_000L, billsMonth(facts(bills = listOf(bill(10_000, day(11, 20))), rules = listOf(part)), november).due)
        // One entered ahead does the same.
        val entered = entry(TransactionType.Transfer, 10_000, day(11, 20), to = 3)
        assertEquals(10_000L, billsMonth(facts(bills = listOf(bill(10_000, day(11, 20))), upcoming = listOf(entered)), november).due)
    }

    @Test
    fun `a transfer dated after the bill's due date does not pay it`() {
        val late = rule(TransactionType.Transfer, 10_000, day(11, 22), to = 3, id = 5)
        val month = billsMonth(facts(bills = listOf(bill(10_000, day(11, 20))), rules = listOf(late)), november)
        assertEquals(20_000L, month.due)
    }

    @Test
    fun `a transfer made the month before pays the next month's bill`() {
        // The bill falls due on 2 December; the repeating transfer of 28 November pays it, and the one of 28 December is December's own.
        val pays = rule(TransactionType.Transfer, 10_000, day(11, 28), to = 3, id = 5)
        val f = facts(bills = listOf(bill(10_000, day(12, 2))), rules = listOf(pays))
        assertEquals(10_000L, billsMonth(f, december).due)
        assertEquals(10_000L, billsMonth(f, november).due)
    }

    @Test
    fun `a transfer counts as money out unless it goes to an account for spending`() {
        val toSavings = rule(TransactionType.Transfer, 2_000, day(11, 20), to = 4, id = 1)
        val toCash = rule(TransactionType.Transfer, 700, day(11, 21), to = 1, id = 2)
        val fromSavings = rule(TransactionType.Transfer, 5_000, day(11, 22), account = 4, to = 2, id = 3)
        val month = billsMonth(facts(rules = listOf(toSavings, toCash, fromSavings)), november)
        assertEquals(2_000L, month.due)
        assertEquals(0L, month.comingIn)
        // They are all listed, with the transfer's own mark.
        assertEquals(3, month.items.size)
    }

    @Test
    fun `an expense charged to a card is in its bill, one on a card with no due day counts on its day`() {
        val onCard = entry(TransactionType.Expense, 1_500, day(11, 18), account = 3)
        val onLoose = entry(TransactionType.Expense, 800, day(11, 19), account = 5)
        val month = billsMonth(facts(upcoming = listOf(onCard, onLoose)), november)
        assertEquals(800L, month.due)
        assertEquals(2, month.items.size)
    }

    @Test
    fun `income counts as coming in whichever account it goes to`() {
        val month = billsMonth(
            facts(upcoming = listOf(entry(TransactionType.Income, 4_000, day(11, 18), account = 1), entry(TransactionType.Income, 6_000, day(11, 19), account = 4))),
            november,
        )
        assertEquals(10_000L, month.comingIn)
        assertEquals(0L, month.due)
    }

    @Test
    fun `reminders carry no amount into the summary`() {
        val month = billsMonth(facts(reminders = listOf(reminder(1, day(11, 20).atTime(9, 0)))), november)
        assertEquals(0L, month.due)
        assertEquals(0L, month.comingIn)
        assertEquals(1, month.items.size)
    }
}
