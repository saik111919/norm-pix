package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyTest {
    @Test
    fun `parses whole and fractional amounts without floating point`() {
        assertEquals(50000L, parseMinorUnits("500", 2))
        assertEquals(123450L, parseMinorUnits("1,234.5", 2))
        assertEquals(5L, parseMinorUnits("0.05", 2))
        assertEquals(50L, parseMinorUnits(".5", 2))
        assertEquals(10010L, parseMinorUnits("100.10", 2))
        assertEquals(1200L, parseMinorUnits(" 12 ", 2))
        assertEquals(1200L, parseMinorUnits("1200", 0))
    }

    @Test
    fun `rejects invalid input instead of rounding`() {
        assertNull(parseMinorUnits("", 2))
        assertNull(parseMinorUnits(".", 2))
        assertNull(parseMinorUnits("1.234", 2))
        assertNull(parseMinorUnits("1.5", 0))
        assertNull(parseMinorUnits("-5", 2))
        assertNull(parseMinorUnits("12abc", 2))
        assertNull(parseMinorUnits("1.2.3", 2))
        assertNull(parseMinorUnits("9".repeat(13), 2))
    }

    @Test
    fun `100_10 plus 0_20 is exactly 100_30`() {
        val a = parseMinorUnits("100.10", 2)!!
        val b = parseMinorUnits("0.20", 2)!!
        assertEquals(10030L, a + b)
        assertEquals("100.30", minorToDecimalString(a + b, 2))
    }

    @Test
    fun `round trips through input text`() {
        for (minor in listOf(0L, 5L, 50L, 12345L, 100000L, 999999999L)) {
            val text = minorToInputText(minor, 2)
            assertEquals(minor, parseMinorUnits(text, 2), text)
        }
    }

    @Test
    fun `uses Indian digit grouping for INR`() {
        val f = MoneyFormatter(CurrencySpec.INR)
        assertEquals("₹1,00,000.00", f.format(10_000_000))
        assertEquals("₹500", f.format(50_000, trimZeroFraction = true))
        assertEquals("−₹500.50", f.format(-50_050))
        assertEquals("+₹500.50", f.format(50_050, AmountSign.Explicit))
        assertEquals("₹0.01", f.format(1))
        assertEquals("₹12,34,56,789.00", f.format(123_456_789_00))
    }

    @Test
    fun `western currencies group in thousands`() {
        val usd = MoneyFormatter(CurrencySpec.byCode("USD"))
        assertEquals("$1,234,567.89", usd.format(123_456_789))
        assertEquals("1,234,567.89", usd.plain(123_456_789))
        assertEquals("¥1,200", MoneyFormatter(CurrencySpec.byCode("JPY")).format(1200))
    }

    @Test
    fun `compact amounts use the local unit names`() {
        val inr = MoneyFormatter(CurrencySpec.INR)
        assertEquals("₹500", inr.compact(50_000))
        assertEquals("₹1.2K", inr.compact(123_456))
        assertEquals("₹1.2L", inr.compact(12_345_678))
        assertEquals("₹12L", inr.compact(123_456_789))
        assertEquals("₹1Cr", inr.compact(1_000_000_000))
        val usd = MoneyFormatter(CurrencySpec.byCode("USD"))
        assertEquals("$12K", usd.compact(1_234_567))
        assertEquals("$1.2M", usd.compact(123_456_789))
    }

    @Test
    fun `every supported currency formats`() {
        for (currency in CurrencySpec.all) {
            val f = MoneyFormatter(currency)
            assertTrue(f.format(123_456).isNotEmpty(), currency.code)
            assertTrue(f.compact(123_456_789).isNotEmpty(), currency.code)
        }
    }
}

class LedgerTest {
    @Test
    fun `account balance combines opening balance and every flow`() {
        assertEquals(
            6_250_000L,
            accountBalance(
                500_000,
                AccountFlows(income = 5_000_000, expense = 50_000, transferIn = 1_000_000, transferOut = 200_000),
            ),
        )
    }

    @Test
    fun `budget health uses integer thresholds`() {
        fun health(spent: Long) = BudgetStatus(allocated = 10_000, spent = spent).health
        assertEquals(BudgetHealth.Healthy, health(0))
        assertEquals(BudgetHealth.Healthy, health(7_999))
        assertEquals(BudgetHealth.Approaching, health(8_000))
        assertEquals(BudgetHealth.Approaching, health(10_000))
        assertEquals(BudgetHealth.Exceeded, health(10_001))
        assertEquals(-2_500L, BudgetStatus(allocated = 10_000, spent = 12_500).remaining)
    }

    @Test
    fun `budgets resolve to the latest row effective for the month`() {
        val rows = listOf(
            Budget(categoryId = null, amount = 100, effectiveMonth = MonthKey(2026, 1)),
            Budget(categoryId = null, amount = 200, effectiveMonth = MonthKey(2026, 5)),
            Budget(categoryId = 3, amount = 50, effectiveMonth = MonthKey(2026, 3)),
            Budget(categoryId = 3, amount = null, effectiveMonth = MonthKey(2026, 6)),
        )
        assertTrue(resolveBudgets(rows, MonthKey(2025, 12)).isEmpty())
        assertEquals(mapOf<Long?, Long>(null to 100), resolveBudgets(rows, MonthKey(2026, 2)))
        assertEquals(mapOf<Long?, Long>(null to 100, 3L to 50), resolveBudgets(rows, MonthKey(2026, 4)))
        assertEquals(mapOf<Long?, Long>(null to 200, 3L to 50), resolveBudgets(rows, MonthKey(2026, 5)))
        assertEquals(mapOf<Long?, Long>(null to 200), resolveBudgets(rows, MonthKey(2026, 9)))
    }

    @Test
    fun `headline budget falls back to the sum of category budgets`() {
        val overview = buildBudgetOverview(
            month = MonthKey(2026, 9),
            limits = mapOf(1L to 1000L, 2L to 3000L),
            spentByCategory = mapOf(1L to 500L, 2L to 1000L, 9L to 7777L),
            totalExpense = 9277,
        )
        assertNull(overview.overall)
        assertEquals(4000L, overview.headline!!.allocated)
        assertEquals(1500L, overview.headline!!.spent)
    }

    @Test
    fun `relative change needs a baseline`() {
        assertEquals(0.18, relativeChange(118, 100)!!, 1e-9)
        assertEquals(-0.18, relativeChange(82, 100)!!, 1e-9)
        assertNull(relativeChange(10, 0))
    }

    @Test
    fun `share is clamped and safe for empty totals`() {
        assertEquals(0.0, shareOf(5, 0))
        assertEquals(1.0, shareOf(12, 10))
        assertEquals(0.25, shareOf(1, 4))
    }
}

class MonthKeyTest {
    @Test
    fun `navigates across year boundaries`() {
        assertEquals(MonthKey(2025, 12), MonthKey(2026, 1).previous)
        assertEquals(MonthKey(2027, 1), MonthKey(2026, 12).next)
        assertEquals(MonthKey(2024, 12), MonthKey(2026, 3).addMonths(-15))
        assertEquals(MonthKey(2026, 9), MonthKey.fromInt(202609))
        assertEquals(29, MonthKey(2024, 2).dayCount)
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), MonthKey(2026, 12).end)
    }
}

class RecurrenceTest {
    private fun rule(
        frequency: RecurrenceFrequency,
        start: LocalDateTime,
        interval: Int = 1,
        end: LocalDateTime? = null,
    ) = RecurringRule(
        type = TransactionType.Expense,
        amount = 100,
        accountId = 1,
        categoryId = 1,
        frequency = frequency,
        interval = interval,
        startDate = start,
        endDate = end,
        nextDate = start,
        createdAt = start,
        updatedAt = start,
    )

    private fun dt(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    @Test
    fun `monthly rules clamp to short months and return to the anchor`() {
        val r = rule(RecurrenceFrequency.Monthly, dt(2026, 1, 31, 9))
        val due = dueOccurrences(r, dt(2026, 4, 29, 23))
        assertEquals(listOf(dt(2026, 1, 31, 9), dt(2026, 2, 28, 9), dt(2026, 3, 31, 9)), due.dates)
        assertEquals(dt(2026, 4, 30, 9), due.nextDate)
    }

    @Test
    fun `weekly daily and yearly steps respect the interval`() {
        assertEquals(
            dt(2026, 9, 15),
            nextRecurrence(dt(2026, 9, 1), RecurrenceFrequency.Weekly, 2, dt(2026, 9, 1)),
        )
        assertEquals(
            dt(2027, 1, 1),
            nextRecurrence(dt(2026, 12, 31), RecurrenceFrequency.Daily, 1, dt(2026, 12, 31)),
        )
        assertEquals(
            dt(2025, 2, 28),
            nextRecurrence(dt(2024, 2, 29), RecurrenceFrequency.Yearly, 1, dt(2024, 2, 29)),
        )
    }

    @Test
    fun `resuming skips occurrences missed while paused`() {
        val r = rule(RecurrenceFrequency.Monthly, dt(2026, 1, 31))
        // Paused since February; resumed on 15 May.
        assertEquals(dt(2026, 5, 31), firstOccurrenceAfter(r, dt(2026, 5, 15, 10)))
        // An occurrence exactly at "now" has already happened.
        assertEquals(dt(2026, 5, 31), firstOccurrenceAfter(r, dt(2026, 4, 30)))
        // Before the start date, the start date itself is next.
        assertEquals(r.startDate, firstOccurrenceAfter(r, dt(2025, 12, 1)))
    }

    @Test
    fun `occurrences stop at the end date and at the cap`() {
        val ended = rule(RecurrenceFrequency.Daily, dt(2026, 1, 1), end = dt(2026, 1, 3))
        assertEquals(3, dueOccurrences(ended, dt(2026, 2, 1)).dates.size)
        val capped = rule(RecurrenceFrequency.Daily, dt(2020, 1, 1))
        assertEquals(10, dueOccurrences(capped, dt(2026, 1, 1), cap = 10).dates.size)
    }

    @Test
    fun `finished rules report it`() {
        val r = rule(RecurrenceFrequency.Daily, dt(2026, 1, 1), end = dt(2026, 1, 3))
        assertFalse(r.isFinished)
        assertTrue(r.copy(nextDate = dt(2026, 1, 4)).isFinished)
        assertNotNull(upcomingOccurrences(r, 5))
        assertEquals(3, upcomingOccurrences(r, 5).size)
    }
}
