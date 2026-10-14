package com.example.normpix.finance.ui

import com.example.normpix.finance.domain.DailyTotal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The numbers the Insights and Home charts are drawn from: clean gridline values, and a month's running totals. */
class ChartScaleTest {
    @Test
    fun `gridlines land on clean whole amounts above the biggest value`() {
        // ₹39,677.51 at most: ₹20K steps up to ₹40K, not ₹13,226 steps.
        assertEquals(listOf(0L, 2_000_000L, 4_000_000L), niceTicks(3_967_751, minorPerMajor = 100))
        // ₹73,000: 2.5 steps are allowed once they stay whole (₹25K).
        assertEquals(listOf(0L, 2_500_000L, 5_000_000L, 7_500_000L), niceTicks(7_300_000, minorPerMajor = 100))
        // ₹2.40: never a ₹2.5 step, which an axis would have to round.
        assertEquals(listOf(0L, 100L, 200L, 300L), niceTicks(240, minorPerMajor = 100))
        // Nothing at all still gives the chart a scale to draw on.
        assertEquals(listOf(0L, 100L), niceTicks(0, minorPerMajor = 100))
    }

    @Test
    fun `the top gridline is never below the biggest value`() {
        for (max in listOf(1L, 99L, 101L, 12_345L, 999_999L, 1_000_001L, 123_456_789L)) {
            val ticks = niceTicks(max, minorPerMajor = 100)
            assertEquals(0L, ticks.first())
            assertTrue(ticks.last() >= max, "$max -> $ticks")
            assertTrue(ticks.size in 2..5, "$max -> $ticks")
        }
    }

    @Test
    fun `running totals add each day to the ones before and skip days outside the month so far`() {
        val oct = { day: Int -> LocalDate.of(2026, 10, day) }
        val daily = listOf(DailyTotal(oct(1), 100), DailyTotal(oct(3), 250), DailyTotal(oct(4), 50), DailyTotal(oct(9), 999))
        // Four days so far: day 2 adds nothing, day 9 is not reached yet.
        assertEquals(listOf(100L, 100L, 350L, 400L), runningTotals(daily, days = 4))
    }
}
