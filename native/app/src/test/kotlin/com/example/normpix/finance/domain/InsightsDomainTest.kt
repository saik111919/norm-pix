package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsDomainTest {
    private val october = MonthKey(2026, 10)

    @Test
    fun aMonthInProgressIsComparedWithTheSameDaysOfThePreviousMonth() {
        val range = comparisonRange(october, today = LocalDate.of(2026, 10, 5))
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), range.start)
        // Days 1-5 of September: the end is the start of the 6th.
        assertEquals(LocalDateTime.of(2026, 9, 6, 0, 0), range.end)
        assertTrue(isInProgress(october, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun aFinishedMonthIsComparedWithTheWholePreviousMonth() {
        val today = LocalDate.of(2026, 10, 5)
        assertFalse(isInProgress(MonthKey(2026, 9), today))
        assertEquals(MonthKey(2026, 8).range, comparisonRange(MonthKey(2026, 9), today))
    }

    @Test
    fun theComparisonNeverRunsPastTheEndOfAShorterPreviousMonth() {
        // 31 March against February (28 days in 2026): all of February, not into March.
        val range = comparisonRange(MonthKey(2026, 3), today = LocalDate.of(2026, 3, 31))
        assertEquals(MonthKey(2026, 2).range, range)
    }

    @Test
    fun largestIncreaseAndDecreaseAreFoundAcrossBothPeriods() {
        val now = listOf(CategoryTotal(1, 5_000, 4), CategoryTotal(2, 1_000, 1), CategoryTotal(4, 700, 1))
        val before = listOf(CategoryTotal(1, 3_000, 3), CategoryTotal(2, 4_000, 6), CategoryTotal(3, 900, 2))
        val changes = categoryChanges(now, before)

        assertEquals(1L, changes.largestIncrease()?.categoryId)
        assertEquals(2_000L, changes.largestIncrease()?.delta)
        assertEquals(2L, changes.largestDecrease()?.categoryId)
        assertEquals(-3_000L, changes.largestDecrease()?.delta)
        // A category that only existed before (3) or only now (4) still counts, against zero.
        assertEquals(0L, changes.first { it.categoryId == 3L }.current)
        assertEquals(0L, changes.first { it.categoryId == 4L }.previous)
    }

    @Test
    fun noMovementMeansNoLargestChange() {
        val same = listOf(CategoryTotal(1, 5_000, 4))
        val changes = categoryChanges(same, same)
        assertNull(changes.largestIncrease())
        assertNull(changes.largestDecrease())
        assertNull(categoryChanges(emptyList(), emptyList()).largestIncrease())
    }
}
