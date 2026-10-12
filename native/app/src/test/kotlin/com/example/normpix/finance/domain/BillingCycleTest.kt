package com.example.normpix.finance.domain

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A card's statement and due days come round every month: the next date is worked out from the day and today, a day a short
 * month lacks falls on its last day, and today still counts. Every date here is fixed, so nothing depends on the clock.
 */
class BillingCycleTest {
    private fun date(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    @Test
    fun `a day a short month lacks falls on that month's last day`() {
        assertEquals(date(2026, 2, 28), date(2026, 2, 10).onDayOfMonth(31))
        assertEquals(date(2028, 2, 29), date(2028, 2, 10).onDayOfMonth(30), "a leap February has a 29th")
        assertEquals(date(2026, 4, 30), date(2026, 4, 1).onDayOfMonth(31))
        assertEquals(date(2026, 5, 31), date(2026, 5, 1).onDayOfMonth(31))
        assertEquals(date(2026, 5, 1), date(2026, 5, 20).onDayOfMonth(1))
    }

    @Test
    fun `the next date is this month's while it is still ahead, and today counts`() {
        assertEquals(date(2026, 10, 20), nextOnDay(20, date(2026, 10, 12)))
        assertEquals(date(2026, 10, 20), nextOnDay(20, date(2026, 10, 20)))
        assertEquals(date(2026, 10, 20), nextOnDay(20, date(2026, 10, 1)))
    }

    @Test
    fun `once the day has passed the next date is next month's, across a year end too`() {
        assertEquals(date(2026, 11, 20), nextOnDay(20, date(2026, 10, 21)))
        assertEquals(date(2027, 1, 20), nextOnDay(20, date(2026, 12, 21)))
        assertEquals(date(2026, 11, 1), nextOnDay(1, date(2026, 10, 2)))
    }

    @Test
    fun `the last days of a month clamp in the month that is short, in either direction`() {
        // From 1 Feb the 31st is the 28th; from the 28th it is still that day; from 1 Mar it is the 31st.
        assertEquals(date(2026, 2, 28), nextOnDay(31, date(2026, 2, 1)))
        assertEquals(date(2026, 2, 28), nextOnDay(31, date(2026, 2, 28)))
        assertEquals(date(2026, 3, 31), nextOnDay(31, date(2026, 3, 1)))
        // The 30th, asked for on the 31st of a long month: that month's 30th is behind, so next month's is.
        assertEquals(date(2026, 4, 30), nextOnDay(30, date(2026, 3, 31)))
        // On April's last day the 31st is that very day; the day after, it is May's.
        assertEquals(date(2026, 4, 30), nextOnDay(31, date(2026, 4, 30)))
        assertEquals(date(2026, 5, 31), nextOnDay(31, date(2026, 5, 1)))
    }

    @Test
    fun `a bill with no due day has no due date`() {
        assertNull(nextBillDue(null, date(2026, 10, 12)))
    }

    @Test
    fun `a due date says how far away it is`() {
        val today = date(2026, 11, 12)
        val due = nextBillDue(20, today)!!
        assertEquals(date(2026, 11, 20), due.date)
        assertEquals(8, due.daysAway)
        assertEquals("in 8 days", due.inWords)
        assertEquals("Due 20 Nov, in 8 days", due.line(Locale.ENGLISH))

        assertEquals(0, nextBillDue(12, today)!!.daysAway)
        assertEquals("today", nextBillDue(12, today)!!.inWords)
        assertEquals("Due today", nextBillDue(12, today)!!.line(Locale.ENGLISH))

        assertEquals("tomorrow", nextBillDue(13, today)!!.inWords)
        assertEquals("Due tomorrow", nextBillDue(13, today)!!.line(Locale.ENGLISH))

        // Already gone this month: next month's, with the days counted across the month end.
        val gone = nextBillDue(5, today)!!
        assertEquals(date(2026, 12, 5), gone.date)
        assertEquals(23, gone.daysAway)
    }

    @Test
    fun `a bill is soon when it is three days away or fewer`() {
        val today = date(2026, 11, 12)
        assertTrue(nextBillDue(12, today)!!.soon)
        assertTrue(nextBillDue(15, today)!!.soon)
        assertFalse(nextBillDue(16, today)!!.soon)
        assertFalse(nextBillDue(30, today)!!.soon)
    }

    @Test
    fun `days are written as ordinals`() {
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 10 to "10th", 11 to "11th", 12 to "12th", 13 to "13th",
            14 to "14th", 20 to "20th", 21 to "21st", 22 to "22nd", 23 to "23rd", 24 to "24th", 30 to "30th", 31 to "31st",
        )
        for ((day, text) in expected) assertEquals(text, ordinalDay(day), "day $day")
    }

    @Test
    fun `the cycle reads as one line`() {
        assertEquals("Statement on the 5th, due on the 20th of every month", billingCycleSummary(5, 20))
        assertEquals("Statement on the 1st of every month", billingCycleSummary(1, null))
        assertEquals("Due on the 22nd of every month", billingCycleSummary(null, 22))
        assertNull(billingCycleSummary(null, null))
    }

    @Test
    fun `every day from the 1st to the 31st is a billing day, and nothing else is`() {
        assertEquals(31, BILLING_DAYS.count())
        assertTrue(1 in BILLING_DAYS && 31 in BILLING_DAYS)
        assertFalse(0 in BILLING_DAYS)
        assertFalse(32 in BILLING_DAYS)
    }
}
