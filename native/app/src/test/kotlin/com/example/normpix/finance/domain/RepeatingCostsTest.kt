package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the repeating payments cost: per year (daily x 365, weekly x 52, monthly x 12, yearly x 1, each divided by the interval) and
 * per month (the year over 12), in exact minor units rounded half up; only active expenses make the totals.
 */
class RepeatingCostsTest {
    private val at = LocalDateTime.of(2026, 11, 14, 9, 0)

    private fun rule(
        amount: Long,
        frequency: RecurrenceFrequency = RecurrenceFrequency.Monthly,
        interval: Int = 1,
        type: TransactionType = TransactionType.Expense,
        active: Boolean = true,
        end: LocalDateTime? = null,
        next: LocalDateTime = at.plusDays(3),
        id: Long? = null,
    ) = RecurringRule(
        id = id,
        type = type,
        amount = amount,
        accountId = 1,
        toAccountId = if (type == TransactionType.Transfer) 2 else null,
        categoryId = if (type == TransactionType.Transfer) null else 5,
        frequency = frequency,
        interval = interval,
        startDate = at,
        endDate = end,
        nextDate = next,
        active = active,
        createdAt = at,
        updatedAt = at,
    )

    @Test
    fun `a year is the times it happens in a year, whatever the frequency`() {
        assertEquals(365_000, costPerYear(1_000, RecurrenceFrequency.Daily, 1))
        assertEquals(520_000, costPerYear(10_000, RecurrenceFrequency.Weekly, 1))
        assertEquals(778_800, costPerYear(64_900, RecurrenceFrequency.Monthly, 1))
        assertEquals(778_800, costPerYear(778_800, RecurrenceFrequency.Yearly, 1))
    }

    @Test
    fun `an interval divides the year, and a share of a paisa rounds to the nearest with a half going up`() {
        // Every 2 months is 6 a year; every 3 weeks is 52/3.
        assertEquals(389_400, costPerYear(64_900, RecurrenceFrequency.Monthly, 2))
        assertEquals(173_333, costPerYear(10_000, RecurrenceFrequency.Weekly, 3)) // 173,333.33
        assertEquals(52_143, costPerYear(1_000, RecurrenceFrequency.Daily, 7)) // 52,142.86
        assertEquals(7, costPerYear(1, RecurrenceFrequency.Weekly, 8), "52/8 = 6.5 rounds up")
        assertEquals(6, costPerYear(1, RecurrenceFrequency.Weekly, 9), "52/9 = 5.78 rounds to 6")
        assertEquals(5, costPerYear(1, RecurrenceFrequency.Weekly, 10), "52/10 = 5.2 rounds down")
        // A year or more between payments: every 2 years is half a payment a year.
        assertEquals(500, costPerYear(1_000, RecurrenceFrequency.Yearly, 2))
        assertEquals(1, costPerYear(1, RecurrenceFrequency.Yearly, 2), "a half rounds up")
        // An interval below 1 is read as 1, as the schedule itself does.
        assertEquals(778_800, costPerYear(64_900, RecurrenceFrequency.Monthly, 0))
    }

    @Test
    fun `a month is the year over twelve, rounded to the nearest with a half going up`() {
        assertEquals(64_900, costPerMonth(778_800))
        assertEquals(43_333, costPerMonth(520_000)) // 43,333.33
        assertEquals(30_417, costPerMonth(365_000)) // 30,416.67
        assertEquals(0, costPerMonth(5)) // 0.42
        assertEquals(1, costPerMonth(6), "exactly half a paisa rounds up")
        assertEquals(2, costPerMonth(18), "1.5 rounds up")
        assertEquals(1, costPerMonth(17), "1.42 rounds down")
        assertEquals(0, costPerMonth(0))
    }

    @Test
    fun `each cost carries its own year and month`() {
        val costs = repeatingCosts(
            listOf(
                rule(64_900),
                rule(10_000, RecurrenceFrequency.Weekly),
                rule(199_900, RecurrenceFrequency.Yearly),
                rule(1_000, RecurrenceFrequency.Daily, interval = 7),
            ),
        )
        val byAmount = costs.active.associateBy { it.rule.amount }
        assertEquals(778_800L to 64_900L, byAmount.getValue(64_900).let { it.perYear to it.perMonth })
        assertEquals(520_000L to 43_333L, byAmount.getValue(10_000).let { it.perYear to it.perMonth })
        assertEquals(199_900L to 16_658L, byAmount.getValue(199_900).let { it.perYear to it.perMonth }) // 16,658.33
        assertEquals(52_143L to 4_345L, byAmount.getValue(1_000).let { it.perYear to it.perMonth }) // 4,345.25
    }

    @Test
    fun `the totals are the sum of the years, and the month is that year over twelve`() {
        val costs = repeatingCosts(listOf(rule(64_900), rule(10_000, RecurrenceFrequency.Weekly)))
        assertEquals(778_800 + 520_000, costs.perYear)
        // 1,298,800 / 12 = 108,233.33.
        assertEquals(108_233, costs.perMonth)
        // Three weekly payments of one paisa: each month rounds to 4, but the year (156) over twelve is 13, the figure to trust.
        val tiny = repeatingCosts(List(3) { rule(1, RecurrenceFrequency.Weekly) })
        assertEquals(156, tiny.perYear)
        assertEquals(13, tiny.perMonth)
        assertEquals(12, tiny.active.sumOf { it.perMonth }, "the rows round on their own, so they need not add up to the total")
    }

    @Test
    fun `only active expenses are costs - income, transfers, paused and ended ones are not`() {
        val costs = repeatingCosts(
            listOf(
                rule(64_900),
                rule(5_000_000, type = TransactionType.Income),
                rule(1_000_000, type = TransactionType.Transfer),
                rule(19_900, active = false),
                rule(
                    9_900,
                    end = at.minusDays(10),
                    next = at.plusDays(20), // past its last day: finished
                ),
            ),
        )
        assertEquals(listOf(64_900L), costs.active.map { it.rule.amount }, "a card bill or a SIP is not spending, and an income is not a cost")
        assertEquals(778_800, costs.perYear)
        assertEquals(listOf(19_900L), costs.paused.map { it.rule.amount }, "a paused one is listed so it can be resumed, but costs nothing while it is")
        assertTrue(costs.paused.all { !it.rule.active })
        assertEquals(64_900, costs.perMonth)
    }

    @Test
    fun `the dearest a year comes first, and a tie keeps the rules' own order`() {
        val costs = repeatingCosts(
            listOf(
                rule(10_000, id = 1),
                rule(10_000, RecurrenceFrequency.Weekly, id = 2),
                rule(64_900, id = 3),
                rule(64_900, id = 4),
            ),
        )
        // 2: 520,000 a year; 3 and 4: 778,800; 1: 120,000.
        assertEquals(listOf(3L, 4L, 2L, 1L), costs.active.map { it.rule.id })
    }

    @Test
    fun `nothing repeating costs nothing`() {
        val none = repeatingCosts(emptyList())
        assertEquals(0, none.perYear)
        assertEquals(0, none.perMonth)
        assertTrue(none.active.isEmpty() && none.paused.isEmpty())
    }

    @Test
    fun `a very large payment does not overflow`() {
        // 12 digits of rupees is the most the app allows: 10^14 paise, daily.
        val huge = costPerYear(100_000_000_000_000, RecurrenceFrequency.Daily, 1)
        assertEquals(36_500_000_000_000_000, huge)
    }
}
