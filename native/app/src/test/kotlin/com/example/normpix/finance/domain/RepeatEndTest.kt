package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** How a repeat ends: the dates "after N" and "on a date" come to, which are plain calendar maths and so the same in every zone. */
class RepeatEndTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    private fun monthly(start: LocalDateTime) = recurrenceStep(start, RecurrenceFrequency.Monthly, 1)

    // --- the Nth occurrence --------------------------------------------------------------------------------------

    @Test
    fun `the first occurrence is the start itself`() {
        val start = at(2027, 1, 5, 18, 30)
        assertEquals(start, nthOccurrence(start, 1, monthly(start)))
    }

    @Test
    fun `a monthly repeat from the 31st lands on the last day of shorter months and comes back to the 31st`() {
        val start = at(2027, 1, 31, 18, 30)
        val step = monthly(start)
        assertEquals(at(2027, 2, 28, 18, 30), nthOccurrence(start, 2, step))
        assertEquals(at(2027, 3, 31, 18, 30), nthOccurrence(start, 3, step))
        assertEquals(at(2027, 4, 30, 18, 30), nthOccurrence(start, 4, step))
    }

    @Test
    fun `a leap February keeps the 29th for a monthly repeat from the 31st`() {
        val start = at(2028, 1, 31)
        assertEquals(at(2028, 2, 29), nthOccurrence(start, 2, monthly(start)))
    }

    @Test
    fun `a yearly repeat from the 29th of February lands on the 28th in other years and the 29th again in the next leap one`() {
        val start = at(2028, 2, 29)
        val step = recurrenceStep(start, RecurrenceFrequency.Yearly, 1)
        assertEquals(at(2029, 2, 28), nthOccurrence(start, 2, step))
        assertEquals(at(2030, 2, 28), nthOccurrence(start, 3, step))
        assertEquals(at(2032, 2, 29), nthOccurrence(start, 5, step))
    }

    @Test
    fun `every second week counts in fortnights, and every day in days`() {
        val start = at(2027, 5, 3, 7, 15)
        assertEquals(at(2027, 5, 31, 7, 15), nthOccurrence(start, 3, recurrenceStep(start, RecurrenceFrequency.Weekly, 2)))
        assertEquals(at(2027, 5, 12, 7, 15), nthOccurrence(start, 10, recurrenceStep(start, RecurrenceFrequency.Daily, 1)))
        assertEquals(at(2027, 11, 3, 7, 15), nthOccurrence(start, 3, recurrenceStep(start, RecurrenceFrequency.Monthly, 3)))
    }

    @Test
    fun `a repeat that runs out before the Nth has no Nth`() {
        val start = at(2027, 1, 1)
        val twice: RepeatStep = { from -> from.plusDays(1).takeIf { it.isBefore(at(2027, 1, 3)) } }
        assertEquals(at(2027, 1, 2), nthOccurrence(start, 2, twice))
        assertNull(nthOccurrence(start, 3, twice))
    }

    @Test
    fun `daylight saving moves nothing - the time of day is kept on a day that skips it`() {
        val before = TimeZone.getDefault()
        try {
            // 2027-03-14 02:30 does not exist in New York; the wall-clock maths ignores that, as the repeat's own dates do.
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val start = at(2027, 3, 13, 2, 30)
            val step = recurrenceStep(start, RecurrenceFrequency.Daily, 1)
            assertEquals(at(2027, 3, 14, 2, 30), nthOccurrence(start, 2, step))
            assertEquals(at(2027, 3, 15, 2, 30), nthOccurrence(start, 3, step))
            // And back in the autumn, when 01:30 happens twice.
            val autumn = at(2027, 10, 30, 1, 30)
            assertEquals(at(2027, 11, 2, 1, 30), nthOccurrence(autumn, 4, recurrenceStep(autumn, RecurrenceFrequency.Daily, 1)))
        } finally {
            TimeZone.setDefault(before)
        }
    }

    @Test
    fun `the dates are the same in every zone`() {
        val before = TimeZone.getDefault()
        try {
            val start = at(2027, 1, 31, 23, 30)
            val results = listOf("Pacific/Kiritimati", "UTC", "America/Los_Angeles").map { id ->
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                nthOccurrence(start, 12, monthly(start))
            }
            assertEquals(1, results.toSet().size)
            assertEquals(at(2027, 12, 31, 23, 30), results.first())
        } finally {
            TimeZone.setDefault(before)
        }
    }

    // --- resolving an end to the date to store ------------------------------------------------------------------

    @Test
    fun `never is no end date`() {
        val start = at(2027, 1, 5)
        assertNull(RepeatEnd.Never.resolve(start, monthly(start)))
    }

    @Test
    fun `after N is the date of the Nth occurrence`() {
        val start = at(2026, 11, 5)
        assertEquals(at(2027, 10, 5), RepeatEnd.After(12).resolve(start, monthly(start)))
        assertEquals(start, RepeatEnd.After(1).resolve(start, monthly(start)))
    }

    @Test
    fun `a count is held to what a repeat can be`() {
        val start = at(2027, 1, 1)
        val daily = recurrenceStep(start, RecurrenceFrequency.Daily, 1)
        assertEquals(start, RepeatEnd.After(0).resolve(start, daily))
        assertEquals(start, RepeatEnd.After(-4).resolve(start, daily))
        assertEquals(start.plusDays(MAX_REPEAT_COUNT - 1L), RepeatEnd.After(50_000).resolve(start, daily))
    }

    @Test
    fun `on a date ends at the time of day of the payments, so a payment on the last day still counts`() {
        val start = at(2027, 1, 5, 18, 30)
        val end = RepeatEnd.OnDate(LocalDate.of(2027, 3, 5)).resolve(start, monthly(start))
        assertEquals(at(2027, 3, 5, 18, 30), end)

        val rule = RecurringRule(
            type = TransactionType.Expense,
            amount = 1,
            accountId = 1,
            frequency = RecurrenceFrequency.Monthly,
            startDate = start,
            endDate = end,
            nextDate = start,
            createdAt = start,
            updatedAt = start,
        )
        val due = dueOccurrences(rule, at(2027, 12, 1))
        assertEquals(listOf(start, at(2027, 2, 5, 18, 30), at(2027, 3, 5, 18, 30)), due.dates)
        assertFalse(rule.isFinished)
        // A day stored as midnight would have left the last payment out - which is why the time of day is kept.
        assertEquals(2, dueOccurrences(rule.copy(endDate = at(2027, 3, 5)), at(2027, 12, 1)).dates.size)
    }

    @Test
    fun `a date before the first payment is read as the first payment's day`() {
        val start = at(2027, 6, 10, 9, 0)
        assertEquals(start, RepeatEnd.OnDate(LocalDate.of(2027, 1, 1)).resolve(start, monthly(start)))
    }

    @Test
    fun `an end before the first day is moved up to it`() {
        val first = LocalDate.of(2027, 6, 10)
        assertEquals(RepeatEnd.OnDate(first), RepeatEnd.OnDate(LocalDate.of(2027, 1, 1)).notBefore(first))
        val later = RepeatEnd.OnDate(LocalDate.of(2027, 9, 1))
        assertSame(later, later.notBefore(first))
        assertSame(RepeatEnd.Never, RepeatEnd.Never.notBefore(first))
        val after = RepeatEnd.After(3)
        assertSame(after, after.notBefore(first))
    }

    // --- counting up to a day ------------------------------------------------------------------------------------

    @Test
    fun `occurrences up to a day are counted with the last of them`() {
        val start = at(2027, 1, 31, 18, 30)
        val run = repeatRunThrough(start, LocalDate.of(2027, 4, 29), monthly(start))
        assertEquals(3, run.count)
        assertEquals(at(2027, 3, 31, 18, 30), run.last)
        assertFalse(run.capped)
    }

    @Test
    fun `the day of an occurrence counts it, and a day before the start counts none`() {
        val start = at(2027, 1, 5, 18, 30)
        val step = monthly(start)
        assertEquals(1, repeatRunThrough(start, LocalDate.of(2027, 1, 5), step).count)
        assertEquals(2, repeatRunThrough(start, LocalDate.of(2027, 2, 5), step).count)
        val none = repeatRunThrough(start, LocalDate.of(2027, 1, 4), step)
        assertEquals(0, none.count)
        assertNull(none.last)
    }

    @Test
    fun `counting stops at the limit and says it did`() {
        val start = at(2027, 1, 1)
        val daily = recurrenceStep(start, RecurrenceFrequency.Daily, 1)
        val run = repeatRunThrough(start, LocalDate.of(2077, 1, 1), daily, limit = 5)
        assertEquals(5, run.count)
        assertTrue(run.capped)
        // Exactly the limit is not capped: nothing more falls before the day.
        assertFalse(repeatRunThrough(start, LocalDate.of(2027, 1, 5), daily, limit = 5).capped)
        // A fifty-year daily repeat is stopped at the default limit, not walked to the end.
        assertEquals(REPEAT_COUNT_LIMIT, repeatRunThrough(start, LocalDate.of(2077, 1, 1), daily).count)
    }

    // --- switching between the three ----------------------------------------------------------------------------

    @Test
    fun `switching from never starts a date a year out and a count at twelve`() {
        val start = at(2027, 3, 5, 9, 0)
        val step = monthly(start)
        assertEquals(RepeatEnd.OnDate(LocalDate.of(2028, 3, 5)), RepeatEnd.Never.switchedTo(RepeatEndKind.OnDate, start, step))
        assertEquals(RepeatEnd.After(DEFAULT_REPEAT_COUNT), RepeatEnd.Never.switchedTo(RepeatEndKind.After, start, step))
    }

    @Test
    fun `switching from a count to a date keeps the day of its last occurrence, and back keeps the number`() {
        val start = at(2027, 1, 1)
        val step = monthly(start)
        val date = RepeatEnd.After(4).switchedTo(RepeatEndKind.OnDate, start, step)
        assertEquals(RepeatEnd.OnDate(LocalDate.of(2027, 4, 1)), date)
        assertEquals(RepeatEnd.After(4), date.switchedTo(RepeatEndKind.After, start, step))
    }

    @Test
    fun `a date that holds fewer than two payments still gives a count of two`() {
        val start = at(2027, 1, 1)
        val held = RepeatEnd.OnDate(LocalDate.of(2027, 1, 20)).switchedTo(RepeatEndKind.After, start, monthly(start))
        assertEquals(RepeatEnd.After(MIN_REPEAT_COUNT), held)
    }

    @Test
    fun `switching to never or to what it already is changes nothing else`() {
        val start = at(2027, 1, 1)
        val step = monthly(start)
        assertEquals(RepeatEnd.Never, RepeatEnd.After(3).switchedTo(RepeatEndKind.Never, start, step))
        val date = RepeatEnd.OnDate(LocalDate.of(2027, 8, 1))
        assertSame(date, date.switchedTo(RepeatEndKind.OnDate, start, step))
        assertEquals(RepeatEndKind.After, RepeatEnd.After(3).kind)
    }

    // --- the plan, as the editors hold it ------------------------------------------------------------------------

    @Test
    fun `a plan follows the first payment's date, so a count is counted from the date that is saved`() {
        val plan = RepeatPlan(RecurrenceFrequency.Monthly, ends = RepeatEnd.After(3))
        assertEquals(at(2027, 3, 31, 9, 0), plan.schedule(at(2027, 1, 31, 9, 0)).endDate)
        assertEquals(at(2027, 8, 15, 20, 0), plan.schedule(at(2027, 6, 15, 20, 0)).endDate)
        assertEquals(RecurrenceFrequency.Monthly, plan.schedule(at(2027, 1, 31)).frequency)
    }

    @Test
    fun `a plan follows how often it repeats too`() {
        val start = at(2027, 1, 1, 8, 0)
        assertEquals(at(2027, 1, 3, 8, 0), RepeatPlan(RecurrenceFrequency.Daily, ends = RepeatEnd.After(3)).schedule(start).endDate)
        assertEquals(at(2027, 1, 29, 8, 0), RepeatPlan(RecurrenceFrequency.Weekly, 2, RepeatEnd.After(3)).schedule(start).endDate)
        assertNull(RepeatPlan(RecurrenceFrequency.Weekly).schedule(start).endDate)
    }

    @Test
    fun `a stored schedule reads back as a plan with its day, or none`() {
        assertEquals(RepeatEnd.Never, RepeatEnd.of(null))
        assertEquals(RepeatEnd.OnDate(LocalDate.of(2027, 10, 5)), RepeatEnd.of(at(2027, 10, 5, 18, 30)))
        val schedule = RepeatSchedule(RecurrenceFrequency.Yearly, 2, at(2031, 1, 1))
        assertEquals(RepeatPlan(RecurrenceFrequency.Yearly, 2, RepeatEnd.OnDate(LocalDate.of(2031, 1, 1))), RepeatPlan.of(schedule))
    }
}
