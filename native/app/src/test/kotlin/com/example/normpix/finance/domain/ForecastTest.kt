package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cash-flow forecast: which accounts' money counts, what each kind of dated thing does to it (the money rules - an expense on
 * a card goes through its bill, a bill payment is a transfer), card bills counted once even where a repeating transfer pays them,
 * the everyday estimate on and off, the lowest point, and the helpers that find an end date. Today is 14 November 2026.
 */
class ForecastTest {
    private val today = LocalDate.of(2026, 11, 14)
    private val at = LocalDateTime.of(2026, 11, 14, 9, 0)

    private val cash = account(1, "Cash", AccountKind.Cash)
    private val bank = account(2, "HDFC", AccountKind.Bank)
    private val card = account(3, "ICICI card", AccountKind.Card, statementDay = 5, dueDay = 20)
    private val savings = account(4, "Savings jar", AccountKind.Savings)

    private fun account(
        id: Long,
        name: String,
        kind: AccountKind,
        statementDay: Int? = null,
        dueDay: Int? = null,
        archived: Boolean = false,
        openingBalance: Long = 0,
    ) = Account(
        id = id, name = name, kind = kind, openingBalance = openingBalance, archived = archived, statementDay = statementDay, dueDay = dueDay,
        createdAt = at, updatedAt = at,
    )

    private fun entry(
        type: TransactionType,
        amount: Long,
        on: LocalDate,
        account: Long,
        to: Long? = null,
        payee: String? = null,
    ) = FinanceTransaction(
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

    private fun rule(
        type: TransactionType,
        amount: Long,
        account: Long,
        next: LocalDateTime,
        frequency: RecurrenceFrequency = RecurrenceFrequency.Monthly,
        interval: Int = 1,
        to: Long? = null,
        end: LocalDateTime? = null,
        active: Boolean = true,
        start: LocalDateTime = next,
        payee: String? = null,
        id: Long? = null,
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
        startDate = start,
        endDate = end,
        nextDate = next,
        active = active,
        createdAt = at,
        updatedAt = at,
    )

    private fun bill(amount: Long, owedNow: Long, due: LocalDate, kind: BillKind = BillKind.Statement) =
        CardBill(kind, amount, owedNow, due, ChronoUnit.DAYS.between(today, due).toInt(), LocalDate.of(2026, 11, 5), 0.5f)

    private fun facts(
        accounts: List<Account> = listOf(cash, bank, card, savings),
        balances: Map<Long, Long> = mapOf(1L to 50_000, 2L to 500_000, 3L to 0, 4L to 900_000),
        upcoming: List<FinanceTransaction> = emptyList(),
        rules: List<RecurringRule> = emptyList(),
        cards: List<CardFacts> = listOf(CardFacts(card, bill = null, owedNow = 0)),
        everydayTotal: Long = 0,
    ) = ForecastFacts(today, accounts, balances, upcoming, rules, cards, everydayTotal)

    private fun forecast(facts: ForecastFacts, days: Long = 60, everyday: Boolean = false) = buildForecast(facts, today.plusDays(days), everyday)

    private fun Forecast.balanceOn(day: LocalDate) = points.first { it.date == day }.balance

    private fun day(month: Int, dayOfMonth: Int, year: Int = 2026) = LocalDate.of(year, month, dayOfMonth)

    // ------------------------------------------------------------------------------------------- start

    @Test
    fun `only cash, a bank, a wallet and other accounts that hold money count - not a card, savings, an archived or an owed one`() {
        val accounts = listOf(
            cash,
            bank,
            card,
            savings,
            account(5, "Paytm", AccountKind.Wallet),
            account(6, "Misc", AccountKind.Other),
            account(7, "Old bank", AccountKind.Bank, archived = true),
            // Marked owed in its form: the opening balance is kept below zero.
            account(8, "Car loan", AccountKind.Other, openingBalance = -400_000),
        )
        val balances = mapOf(1L to 100_000L, 2L to 500_000L, 3L to -30_000L, 4L to 900_000L, 5L to 20_000L, 6L to 10_000L, 7L to 800_000L, 8L to -250_000L)
        assertEquals(setOf(1L, 2L, 5L, 6L), spendableAccounts(accounts, balances))
        assertEquals(630_000, buildForecast(facts(accounts, balances), today.plusDays(1), false).startBalance)
        // An account that holds nothing yet still counts: what comes into it is money to spend.
        assertEquals(setOf(1L, 2L), spendableAccounts(listOf(cash, bank), mapOf(1L to 0L)))
    }

    @Test
    fun `a bank account that is overdrawn today still counts, its shortfall with it - only one marked owed is left out`() {
        val overdrawn = account(9, "Current account", AccountKind.Bank)
        val accounts = listOf(cash, overdrawn)
        val balances = mapOf(1L to 100_000L, 9L to -40_000L)
        assertEquals(setOf(1L, 9L), spendableAccounts(accounts, balances))
        assertEquals(60_000, buildForecast(facts(accounts, balances), today.plusDays(1), false).startBalance)
    }

    @Test
    fun `today is the first point and the end date the last, one a day`() {
        val f = forecast(facts(), days = 30)
        assertEquals(31, f.points.size)
        assertEquals(today, f.points.first().date)
        assertEquals(today.plusDays(30), f.points.last().date)
        assertEquals(f.startBalance, f.points.first().balance)
        assertTrue(f.items.isEmpty() && f.lowest.balance == f.startBalance)
        // An end that is not after today is today alone.
        assertEquals(1, buildForecast(facts(), today.minusDays(3), false).points.size)
    }

    // ------------------------------------------------------------------------------------- what moves it

    @Test
    fun `an entry dated ahead moves the money on its own day - an income adds, an expense takes away`() {
        val f = forecast(
            facts(
                upcoming = listOf(
                    entry(TransactionType.Income, 80_000, day(11, 20), account = 2, payee = "Freelance"),
                    entry(TransactionType.Expense, 30_000, day(11, 17), account = 1, payee = "Plumber"),
                ),
            ),
        )
        val start = f.startBalance
        assertEquals(start, f.balanceOn(day(11, 16)), "nothing before its day")
        assertEquals(start - 30_000, f.balanceOn(day(11, 17)))
        assertEquals(start - 30_000, f.balanceOn(day(11, 19)))
        assertEquals(start + 50_000, f.balanceOn(day(11, 20)))
        assertEquals(listOf(day(11, 17), day(11, 20)), f.items.map { it.date })
        assertEquals(listOf(-30_000L, 80_000L), f.items.map { it.effect })
        assertEquals(listOf(ForecastSource.Entered, ForecastSource.Entered), f.items.map { it.source })
    }

    @Test
    fun `an entry dated today is already in today's balance, and one dated after the end is not counted`() {
        val f = forecast(
            facts(
                upcoming = listOf(
                    entry(TransactionType.Expense, 10_000, today, account = 1),
                    entry(TransactionType.Expense, 20_000, today.plusDays(31), account = 1),
                ),
            ),
            days = 30,
        )
        assertTrue(f.items.isEmpty())
        assertEquals(f.startBalance, f.last.balance)
    }

    @Test
    fun `income into a card, savings or an owed account is not money to spend`() {
        val f = forecast(
            facts(
                upcoming = listOf(
                    entry(TransactionType.Income, 40_000, day(11, 20), account = 4), // interest into savings
                    entry(TransactionType.Income, 15_000, day(11, 21), account = 3), // a refund onto the card
                ),
            ),
        )
        assertTrue(f.items.isEmpty())
        assertEquals(f.startBalance, f.last.balance)
    }

    @Test
    fun `a transfer takes away what leaves the money to spend, adds what comes in and is nothing between two of them`() {
        fun effectOf(from: Long, to: Long): Long? {
            val f = forecast(facts(upcoming = listOf(entry(TransactionType.Transfer, 10_000, day(11, 20), account = from, to = to))))
            return f.items.singleOrNull()?.effect
        }
        assertEquals(-10_000L, effectOf(from = 2, to = 3), "a bill paid onto a card leaves what can be spent")
        assertEquals(-10_000L, effectOf(from = 2, to = 4), "money put into savings")
        assertEquals(10_000L, effectOf(from = 4, to = 2), "money taken out of savings")
        assertNull(effectOf(from = 2, to = 1), "bank to cash is still money to spend")
        assertNull(effectOf(from = 4, to = 3), "savings onto a card never was money to spend")
    }

    @Test
    fun `an expense on a card does not touch the money to spend - it comes out when the card's bill is due`() {
        val charge = entry(TransactionType.Expense, 10_000, day(11, 20), account = 3, payee = "Swiggy")
        // Charged on 20 November, a statement day of the 5th: it is on the 5 December statement, due on 20 December.
        val long = forecast(facts(upcoming = listOf(charge)), days = 60)
        assertEquals(long.startBalance, long.balanceOn(day(11, 20)), "not on the day it is charged")
        assertEquals(long.startBalance, long.balanceOn(day(12, 19)))
        assertEquals(long.startBalance - 10_000, long.balanceOn(day(12, 20)))
        val item = long.items.single()
        assertEquals(ForecastSource.CardBill, item.source)
        assertEquals(day(12, 20), item.date)
        assertEquals(-10_000, item.effect)
        assertEquals(3L, item.accountId)
        // Looking only to the end of November, the bill is not in view yet.
        val short = forecast(facts(upcoming = listOf(charge)), days = 16)
        assertTrue(short.items.isEmpty())
        assertEquals(short.startBalance, short.last.balance)
    }

    @Test
    fun `a card with no due day has no bill, so what is charged to it is counted on its day`() {
        val unbilled = account(3, "Old card", AccountKind.Card)
        val f = forecast(
            facts(
                accounts = listOf(cash, bank, unbilled),
                balances = mapOf(1L to 50_000, 2L to 500_000, 3L to -10_000),
                upcoming = listOf(entry(TransactionType.Expense, 7_000, day(11, 20), account = 3)),
                cards = emptyList(),
            ),
        )
        assertEquals(f.startBalance - 7_000, f.balanceOn(day(11, 20)))
        assertEquals(ForecastSource.Entered, f.items.single().source)
    }

    // ----------------------------------------------------------------------------------- repeating payments

    @Test
    fun `the next occurrences of the active repeating payments are counted up to the end date`() {
        val rules = listOf(
            rule(TransactionType.Income, 95_000, account = 2, next = day(12, 1).atTime(9, 0), payee = "Salary"),
            rule(TransactionType.Expense, 24_000, account = 2, next = day(11, 25).atTime(10, 0), payee = "Rent"),
            rule(TransactionType.Expense, 9_900, account = 2, next = day(11, 20).atTime(10, 0), active = false, payee = "Paused"),
            rule(
                TransactionType.Expense,
                5_000,
                account = 2,
                next = day(11, 28).atTime(10, 0),
                start = day(9, 28).atTime(10, 0),
                end = day(10, 28).atTime(10, 0), // finished: its next date is past its last day
                payee = "Ended",
            ),
        )
        val f = forecast(facts(rules = rules), days = 60) // to 13 January
        // Rent on 25 Nov, 25 Dec and 25 Jan is beyond 13 Jan; salary on 1 Dec and 1 Jan.
        assertEquals(
            listOf(day(11, 25) to -24_000L, day(12, 1) to 95_000L, day(12, 25) to -24_000L, day(1, 1, 2027) to 95_000L),
            f.items.map { it.date to it.effect },
        )
        assertTrue(f.items.all { it.source == ForecastSource.Repeating })
        assertEquals(f.startBalance - 24_000 + 95_000 - 24_000 + 95_000, f.last.balance)
    }

    @Test
    fun `a repeating payment due later today counts today`() {
        val f = forecast(facts(rules = listOf(rule(TransactionType.Expense, 12_000, account = 1, next = today.atTime(23, 0)))), days = 5)
        assertEquals(f.startBalance - 12_000, f.points.first().balance)
        assertEquals(today, f.items.single().date)
    }

    @Test
    fun `a repeating transfer to a card, savings or from savings is read the way an entered one is`() {
        val rules = listOf(
            rule(TransactionType.Transfer, 5_000, account = 2, to = 4, next = day(11, 16).atTime(9, 0), frequency = RecurrenceFrequency.Weekly), // a SIP
            rule(TransactionType.Transfer, 3_000, account = 4, to = 1, next = day(11, 17).atTime(9, 0)), // pocket money out of savings
        )
        val f = forecast(facts(rules = rules), days = 20)
        // Weekly from 16 Nov to 4 Dec: 16, 23 and 30 Nov = 3 x -5,000; monthly 17 Nov: one +3,000 (the next is 17 Dec).
        assertEquals(setOf(-5_000L, 3_000L), f.items.map { it.effect }.toSet())
        assertEquals(-5_000L * 3 + 3_000, f.last.balance - f.startBalance, "to 4 December: three SIPs and one pocket money")
    }

    // ------------------------------------------------------------------------------------------ card bills

    @Test
    fun `a card's bill comes out on its due date, and what was charged since the statement on the next one`() {
        // Owes 30,000: 20,000 is the statement (due 20 Nov), the 10,000 charged since is on the 5 Dec statement, due 20 Dec.
        val withBill = facts(cards = listOf(CardFacts(card, bill(20_000, 30_000, day(11, 20)), owedNow = 30_000)))
        val f = forecast(withBill, days = 60)
        assertEquals(listOf(day(11, 20) to -20_000L, day(12, 20) to -10_000L), f.items.map { it.date to it.effect })
        assertTrue(f.items.all { it.source == ForecastSource.CardBill && it.accountId == 3L })
        assertEquals(f.startBalance, f.balanceOn(day(11, 19)))
        assertEquals(f.startBalance - 20_000, f.balanceOn(day(11, 20)))
        assertEquals(f.startBalance - 30_000, f.balanceOn(day(12, 20)))
        // Ending before the second is due leaves it out.
        assertEquals(listOf(-20_000L), forecast(withBill, days = 30).items.map { it.effect })
    }

    @Test
    fun `a bill that is late is counted today`() {
        val late = facts(cards = listOf(CardFacts(card, bill(15_000, 15_000, day(11, 10), BillKind.Overdue), owedNow = 15_000)))
        val f = forecast(late, days = 10)
        assertEquals(f.startBalance - 15_000, f.points.first().balance)
        assertEquals(today, f.items.single().date)
    }

    @Test
    fun `a card with no statement day has its bill and nothing more charged by it until the next due day`() {
        val noStatement = account(3, "Plain card", AccountKind.Card, dueDay = 20)
        val facts = facts(
            accounts = listOf(cash, bank, noStatement),
            cards = listOf(CardFacts(noStatement, bill(12_000, 12_000, day(11, 20), BillKind.Open), owedNow = 12_000)),
            upcoming = listOf(entry(TransactionType.Expense, 4_000, day(11, 28), account = 3)),
        )
        val f = forecast(facts, days = 60)
        // 12,000 owed so far on its due day; the 28 Nov charge is on the next due date, 20 December.
        assertEquals(listOf(day(11, 20) to -12_000L, day(12, 20) to -4_000L), f.items.map { it.date to it.effect })
    }

    @Test
    fun `a transfer that pays the card by its due date is the payment - the bill is not counted twice`() {
        val withBill = listOf(CardFacts(card, bill(20_000, 30_000, day(11, 20)), owedNow = 30_000))
        // A monthly 20,000 transfer from the bank to the card, on the 18th: it pays the 20 Nov bill, and then the 10,000 due 20 Dec.
        val pays = rule(TransactionType.Transfer, 20_000, account = 2, to = 3, next = day(11, 18).atTime(9, 0))
        val f = forecast(facts(cards = withBill, rules = listOf(pays)), days = 60)
        assertTrue(f.items.none { it.source == ForecastSource.CardBill }, "no bill on top of the transfer: ${f.items.map { it.source }}")
        // Two transfers (18 Nov, 18 Dec) and nothing else: 40,000 out, not 70,000.
        assertEquals(-40_000, f.last.balance - f.startBalance)
    }

    @Test
    fun `a transfer that pays only part of a bill leaves the rest`() {
        val withBill = listOf(CardFacts(card, bill(20_000, 30_000, day(11, 20)), owedNow = 30_000))
        val partly = rule(TransactionType.Transfer, 5_000, account = 2, to = 3, next = day(11, 18).atTime(9, 0))
        val f = forecast(facts(cards = withBill, rules = listOf(partly)), days = 60)
        val bills = f.items.filter { it.source == ForecastSource.CardBill }
        // 5,000 of the first bill is paid by the first transfer, 5,000 of the second by the second: 15,000 and 5,000 are left.
        assertEquals(listOf(day(11, 20) to -15_000L, day(12, 20) to -5_000L), bills.map { it.date to it.effect })
        // All of it is still paid for once: 30,000 (the bills) in total, 10,000 of it as transfers.
        assertEquals(-30_000, f.last.balance - f.startBalance)
    }

    @Test
    fun `a transfer to the card after the due date does not pay that bill`() {
        val withBill = listOf(CardFacts(card, bill(20_000, 30_000, day(11, 20)), owedNow = 30_000))
        val late = entry(TransactionType.Transfer, 5_000, day(11, 25), account = 2, to = 3)
        val f = forecast(facts(cards = withBill, upcoming = listOf(late)), days = 60)
        assertEquals(
            listOf(day(11, 20) to -20_000L, day(11, 25) to -5_000L, day(12, 20) to -5_000L),
            f.items.map { it.date to it.effect },
            "the 20 Nov bill in full, the transfer, and the 20 Dec bill less what the transfer put on the card",
        )
    }

    @Test
    fun `a transfer onto a card from savings pays its bill without costing the money to spend`() {
        val withBill = listOf(CardFacts(card, bill(20_000, 20_000, day(11, 20)), owedNow = 20_000))
        val fromSavings = entry(TransactionType.Transfer, 20_000, day(11, 18), account = 4, to = 3)
        val f = forecast(facts(cards = withBill, upcoming = listOf(fromSavings)), days = 30)
        assertTrue(f.items.isEmpty())
        assertEquals(f.startBalance, f.last.balance)
    }

    // ------------------------------------------------------------------------------------- everyday spending

    @Test
    fun `a day's share is the last 90 days' spending over 90, rounded to the nearest paisa with a half up`() {
        assertEquals(1_000, facts(everydayTotal = 90_000).everydayPerDay)
        assertEquals(1, facts(everydayTotal = 100).everydayPerDay) // 1.11
        assertEquals(1, facts(everydayTotal = 134).everydayPerDay) // 1.49
        assertEquals(2, facts(everydayTotal = 135).everydayPerDay) // 1.5
        assertEquals(0, facts(everydayTotal = 0).everydayPerDay)
    }

    @Test
    fun `everyday spending, when on, comes off every day after today - off, only what is scheduled counts`() {
        val f = facts(everydayTotal = 90_000)
        val on = forecast(f, days = 10, everyday = true)
        val off = forecast(f, days = 10, everyday = false)
        assertEquals(on.startBalance, on.points.first().balance, "today's balance already has today's spending in it")
        for (n in 0..10) {
            assertEquals(on.startBalance - 1_000L * n, on.points[n].balance, "day $n")
            assertEquals(off.startBalance, off.points[n].balance, "day $n, off")
        }
        assertEquals(1_000, on.everydayPerDay)
        assertEquals(10_000, on.everydayTotal)
        assertEquals(0, off.everydayTotal)
        assertTrue(on.items.isEmpty(), "an estimate is not a dated item")
    }

    // ---------------------------------------------------------------------------- lowest point, end, below zero

    @Test
    fun `the lowest point and the end balance are read from the days`() {
        val f = forecast(
            facts(
                balances = mapOf(1L to 0, 2L to 100_000, 3L to 0, 4L to 0),
                upcoming = listOf(
                    entry(TransactionType.Expense, 60_000, day(11, 17), account = 2, payee = "Rent"),
                    entry(TransactionType.Income, 80_000, day(12, 1), account = 2, payee = "Salary"),
                ),
            ),
            days = 20, // to 4 December
        )
        assertEquals(day(11, 17), f.lowest.date, "the earliest of the days that are as low")
        assertEquals(40_000, f.lowest.balance)
        assertEquals(day(12, 4), f.last.date)
        assertEquals(120_000, f.last.balance)
        assertTrue(!f.goesBelowZero)
    }

    @Test
    fun `the lowest point is today when only money comes in`() {
        val f = forecast(facts(upcoming = listOf(entry(TransactionType.Income, 1_000, day(11, 20), account = 2))))
        assertEquals(today, f.lowest.date)
        assertEquals(f.startBalance, f.lowest.balance)
    }

    @Test
    fun `the money runs out when what goes out is more than there is`() {
        val f = forecast(
            facts(
                balances = mapOf(1L to 10_000, 2L to 0, 3L to 0, 4L to 0),
                upcoming = listOf(entry(TransactionType.Expense, 25_000, day(11, 20), account = 1)),
            ),
            days = 10,
        )
        assertTrue(f.goesBelowZero)
        assertEquals(-15_000, f.lowest.balance)
        assertEquals(day(11, 20), f.lowest.date)
        assertEquals(-15_000, f.last.balance)
    }

    @Test
    fun `the dated items are listed oldest first`() {
        val f = forecast(
            facts(
                upcoming = listOf(
                    entry(TransactionType.Expense, 1_000, day(11, 30), account = 1),
                    entry(TransactionType.Income, 2_000, day(11, 16), account = 1),
                    entry(TransactionType.Expense, 3_000, day(11, 16), account = 1),
                ),
            ),
        )
        assertEquals(listOf(day(11, 16), day(11, 16), day(11, 30)), f.items.map { it.date })
        assertEquals(listOf(-3_000L, 2_000L, -1_000L), f.items.map { it.effect }, "a day's money out before what comes in")
    }

    // ------------------------------------------------------------------------------------------ occurrences

    @Test
    fun `occurrences up to a date include that day and stop after the end date of the rule`() {
        val weekly = rule(TransactionType.Expense, 100, account = 1, next = day(11, 16).atTime(8, 30), frequency = RecurrenceFrequency.Weekly, interval = 2)
        // Every two weeks from 16 Nov: 16 Nov, 30 Nov, 14 Dec, 28 Dec.
        assertEquals(
            listOf(day(11, 16), day(11, 30), day(12, 14)),
            occurrencesUntil(weekly, day(12, 14)).map { it.toLocalDate() },
            "the day asked for is in",
        )
        assertEquals(listOf(day(11, 16), day(11, 30)), occurrencesUntil(weekly, day(12, 13)).map { it.toLocalDate() })
        assertEquals(LocalDateTime.of(2026, 11, 16, 8, 30), occurrencesUntil(weekly, day(11, 16)).single(), "with its time of day")
        assertTrue(occurrencesUntil(weekly, day(11, 15)).isEmpty())
        // An end date is the last day an occurrence may fall on.
        val ends = weekly.copy(endDate = day(11, 30).atTime(8, 30))
        assertEquals(listOf(day(11, 16), day(11, 30)), occurrencesUntil(ends, day(12, 31)).map { it.toLocalDate() })
        val before = weekly.copy(endDate = day(11, 29).atTime(8, 30))
        assertEquals(listOf(day(11, 16)), occurrencesUntil(before, day(12, 31)).map { it.toLocalDate() })
        // A rule that has finished has none.
        assertTrue(occurrencesUntil(weekly.copy(endDate = day(11, 1).atTime(0, 0)), day(12, 31)).isEmpty())
    }

    @Test
    fun `a monthly interval of two skips a month, and a short month clamps the day`() {
        val every2 = rule(TransactionType.Expense, 100, account = 1, next = day(1, 31, 2027).atTime(9, 0), interval = 2, start = day(1, 31, 2027).atTime(9, 0))
        assertEquals(
            listOf(day(1, 31, 2027), day(3, 31, 2027), day(5, 31, 2027)),
            occurrencesUntil(every2, day(6, 30, 2027)).map { it.toLocalDate() },
        )
        val monthly = every2.copy(interval = 1)
        assertEquals(
            listOf(day(1, 31, 2027), day(2, 28, 2027), day(3, 31, 2027)),
            occurrencesUntil(monthly, day(3, 31, 2027)).map { it.toLocalDate() },
            "February's last day, then back to the 31st",
        )
        val everyThreeDays = rule(TransactionType.Expense, 1, account = 1, next = day(11, 15).atTime(9, 0), frequency = RecurrenceFrequency.Daily, interval = 3)
        assertEquals(
            listOf(day(11, 15), day(11, 18), day(11, 21)),
            occurrencesUntil(everyThreeDays, day(11, 22)).map { it.toLocalDate() },
        )
        val yearly = rule(TransactionType.Expense, 1, account = 1, next = day(12, 25).atTime(9, 0), frequency = RecurrenceFrequency.Yearly, interval = 1)
        assertEquals(listOf(day(12, 25), day(12, 25, 2027)), occurrencesUntil(yearly, day(12, 25, 2027)).map { it.toLocalDate() })
    }

    @Test
    fun `a rule that repeats every day is cut at the cap`() {
        val daily = rule(TransactionType.Expense, 1, account = 1, next = day(11, 15).atTime(9, 0), frequency = RecurrenceFrequency.Daily)
        assertEquals(5, occurrencesUntil(daily, day(12, 31, 2030), cap = 5).size)
        assertEquals(365, occurrencesUntil(daily, today.plusMonths(12)).size, "15 Nov 2026 to 14 Nov 2027: a year of days, well under the cap")
    }

    @Test
    fun `an interval and an end date shape what the forecast counts`() {
        val rules = listOf(
            rule(
                TransactionType.Expense,
                1_000,
                account = 2,
                next = day(11, 20).atTime(9, 0),
                frequency = RecurrenceFrequency.Weekly,
                interval = 2,
                end = day(12, 18).atTime(9, 0),
            ),
        )
        val f = forecast(facts(rules = rules), days = 90)
        // 20 Nov, 4 Dec and 18 Dec (the end date itself); 1 Jan is after it.
        assertEquals(listOf(day(11, 20), day(12, 4), day(12, 18)), f.items.map { it.date })
    }

    // ----------------------------------------------------------------------------------------------- ends

    @Test
    fun `payday is the next occurrence of the biggest active income`() {
        val rules = listOf(
            rule(TransactionType.Income, 95_000, account = 2, next = day(12, 1).atTime(9, 0), id = 1),
            rule(TransactionType.Income, 20_000, account = 2, next = day(11, 20).atTime(9, 0), id = 2),
            rule(TransactionType.Expense, 500_000, account = 2, next = day(11, 16).atTime(9, 0), id = 3),
            rule(TransactionType.Income, 300_000, account = 2, next = day(11, 18).atTime(9, 0), active = false, id = 4),
            rule(TransactionType.Transfer, 400_000, account = 2, to = 4, next = day(11, 19).atTime(9, 0), id = 5),
        )
        assertEquals(day(12, 1), nextPayday(rules, today), "not the paused income, the expense or the transfer, and not the nearer small one")
        assertNull(nextPayday(rules.filter { it.type != TransactionType.Income }, today), "no income repeating, no payday")
        assertNull(nextPayday(emptyList(), today))
    }

    @Test
    fun `a tie goes to the payday that comes first, and one that has passed is moved on`() {
        val rules = listOf(
            rule(TransactionType.Income, 50_000, account = 2, next = day(12, 5).atTime(9, 0), id = 1),
            rule(TransactionType.Income, 50_000, account = 2, next = day(11, 30).atTime(9, 0), id = 2),
        )
        assertEquals(day(11, 30), nextPayday(rules, today))
        // Salary on the 14th, recorded already: the next one is a month on. (Its next date is after today, as a recorded one's is.)
        val today14 = rule(TransactionType.Income, 50_000, account = 2, next = day(11, 14).atTime(9, 0), start = day(10, 14).atTime(9, 0))
        assertEquals(day(12, 14), nextPayday(listOf(today14), today), "payday today is not 'until payday'")
        // Beyond the furthest the forecast looks there is none.
        val far = rule(TransactionType.Income, 50_000, account = 2, next = day(2, 1, 2028).atTime(9, 0), frequency = RecurrenceFrequency.Yearly)
        assertNull(nextPayday(listOf(far), today))
        // An income that has ended has none.
        val ended = rule(TransactionType.Income, 50_000, account = 2, next = day(12, 5).atTime(9, 0), end = day(11, 30).atTime(9, 0))
        assertNull(nextPayday(listOf(ended), today))
    }

    @Test
    fun `the end of a forecast is days on, payday, or a day picked from tomorrow to twelve months ahead`() {
        val payday = day(12, 1)
        assertEquals(day(12, 14), ForecastEnd.Days(30).endDate(today, payday))
        assertEquals(day(1, 13, 2027), ForecastEnd.Days(60).endDate(today, null))
        assertEquals(day(2, 12, 2027), ForecastEnd.Days(90).endDate(today, null))
        assertEquals(payday, ForecastEnd.Payday.endDate(today, payday))
        assertEquals(day(12, 14), ForecastEnd.Payday.endDate(today, null), "with no payday, a month")
        assertEquals(day(3, 1, 2027), ForecastEnd.Date(day(3, 1, 2027)).endDate(today, payday))
        assertEquals(day(11, 15), ForecastEnd.Date(day(11, 2)).endDate(today, payday), "not before tomorrow")
        assertEquals(day(11, 15), ForecastEnd.Date(today).endDate(today, payday))
        assertEquals(day(11, 14, 2027), forecastLimit(today))
        assertEquals(day(11, 14, 2027), ForecastEnd.Date(day(5, 5, 2030)).endDate(today, payday), "not past twelve months")
        assertEquals(day(11, 14, 2027), ForecastEnd.Date(day(11, 14, 2027)).endDate(today, payday), "twelve months is allowed")
    }

    @Test
    fun `what is charged to a card is due on the due date of the statement it falls in`() {
        // Statement on the 5th, due on the 20th.
        assertEquals(day(11, 20), dueDateFor(5, 20, day(11, 4)))
        assertEquals(day(11, 20), dueDateFor(5, 20, day(11, 5)), "a charge on the statement day is in that statement")
        assertEquals(day(12, 20), dueDateFor(5, 20, day(11, 6)))
        assertEquals(day(12, 20), dueDateFor(5, 20, day(12, 5)))
        // Statement on the 25th, due on the 12th of the month after.
        assertEquals(day(12, 12), dueDateFor(25, 12, day(11, 20)))
        assertEquals(day(1, 12, 2027), dueDateFor(25, 12, day(11, 26)))
        // A short month's last day stands in for a day it lacks.
        assertEquals(day(3, 28, 2027), dueDateFor(31, 28, day(2, 10, 2027)), "the statement on 28 Feb, due 28 Mar")
        // No statement day: the next due date.
        assertEquals(day(11, 20), dueDateFor(null, 20, day(11, 14)))
        assertEquals(day(11, 20), dueDateFor(null, 20, day(11, 20)))
        assertEquals(day(12, 20), dueDateFor(null, 20, day(11, 21)))
    }
}
