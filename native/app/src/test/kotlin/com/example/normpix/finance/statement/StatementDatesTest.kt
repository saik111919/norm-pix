package com.example.normpix.finance.statement

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How a statement's dates are read: each format, with and without a time, and the day-first / month-first question. */
class StatementDatesTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun read(format: StatementDateFormat, text: String) = format.parse(text, today)

    @Test
    fun `each format reads its own dates`() {
        val day = LocalDate.of(2026, 3, 4)
        assertEquals(day, read(StatementDateFormat.DaySlashMonth, "04/03/2026")?.date)
        assertEquals(day, read(StatementDateFormat.DaySlashMonthShort, "04/03/26")?.date)
        assertEquals(day, read(StatementDateFormat.DayDashMonth, "04-03-2026")?.date)
        assertEquals(day, read(StatementDateFormat.DayDashMonthShort, "04-03-26")?.date)
        assertEquals(day, read(StatementDateFormat.DayDashName, "04-Mar-2026")?.date)
        assertEquals(day, read(StatementDateFormat.DaySpaceName, "04 Mar 2026")?.date)
        assertEquals(day, read(StatementDateFormat.DayDashNameShort, "04-Mar-26")?.date)
        assertEquals(day, read(StatementDateFormat.Iso, "2026-03-04")?.date)
        assertEquals(LocalDate.of(2026, 4, 3), read(StatementDateFormat.MonthSlashDay, "04/03/2026")?.date)
    }

    @Test
    fun `a day or month may have one digit, a month may be spelt out, and the case does not matter`() {
        assertEquals(LocalDate.of(2026, 3, 4), read(StatementDateFormat.DaySlashMonth, "4/3/2026")?.date)
        assertEquals(LocalDate.of(2026, 9, 4), read(StatementDateFormat.DaySpaceName, "4 SEPT 2026")?.date)
        assertEquals(LocalDate.of(2026, 9, 4), read(StatementDateFormat.DayDashName, "04-september-2026")?.date)
        assertEquals(LocalDate.of(2026, 12, 31), read(StatementDateFormat.DayDashName, "31-dec-2026")?.date)
        assertEquals(LocalDate.of(2026, 3, 4), read(StatementDateFormat.Iso, "2026/03/04")?.date)
    }

    @Test
    fun `a time after the date is read too, in 24 hours or with am and pm`() {
        assertEquals(LocalTime.of(14, 35, 20), read(StatementDateFormat.DaySlashMonth, "15/11/2026 14:35:20")?.time)
        assertEquals(LocalTime.of(14, 35), read(StatementDateFormat.DayDashName, "15-Nov-2026 14:35")?.time)
        assertEquals(LocalTime.of(14, 35), read(StatementDateFormat.DayDashNameShort, "15-Nov-26 02:35 PM")?.time)
        assertEquals(LocalTime.of(0, 5), read(StatementDateFormat.Iso, "2026-11-15T12:05 am")?.time)
        assertEquals(LocalTime.of(9, 0), read(StatementDateFormat.Iso, "2026-11-15 09:00:00.000")?.time)
        assertNull(read(StatementDateFormat.DaySlashMonth, "15/11/2026")?.time)
    }

    @Test
    fun `what is not a real date is not read`() {
        assertNull(read(StatementDateFormat.DaySlashMonth, "31/02/2026"))
        assertNull(read(StatementDateFormat.DaySlashMonth, "15/13/2026"))
        assertNull(read(StatementDateFormat.DaySlashMonth, "15/11/2026 25:00"))
        assertNull(read(StatementDateFormat.DaySlashMonth, "15/11/2026 13:00 PM"))
        assertNull(read(StatementDateFormat.DaySlashMonth, "15/11/26"))
        assertNull(read(StatementDateFormat.DaySlashMonth, "Closing Balance"))
        assertNull(read(StatementDateFormat.DaySlashMonth, ""))
        assertNull(read(StatementDateFormat.DayDashName, "15-Xyz-2026"))
        // A year the bank can't have meant.
        assertNull(read(StatementDateFormat.DaySlashMonth, "15/11/1850"))
        assertNull(read(StatementDateFormat.DaySlashMonth, "15/11/2090"))
    }

    @Test
    fun `a two digit year is this century unless that is past next year`() {
        assertEquals(2026, read(StatementDateFormat.DaySlashMonthShort, "15/11/26")?.date?.year)
        assertEquals(2027, read(StatementDateFormat.DaySlashMonthShort, "15/11/27")?.date?.year)
        assertEquals(1999, read(StatementDateFormat.DaySlashMonthShort, "15/11/99")?.date?.year)
    }

    @Test
    fun `a column is read in the one format that reads it`() {
        val cells = listOf("15/11/2026", "16/11/2026", "", "Closing Balance")
        val found = StatementDates.detect(cells, today)
        assertEquals(StatementDateFormat.DaySlashMonth, found.format)
        assertFalse(found.ambiguous)
        assertEquals(listOf(StatementDateFormat.DaySlashMonth), found.candidates.take(1))
    }

    @Test
    fun `every format is told from the others`() {
        val samples = mapOf(
            StatementDateFormat.DaySlashMonthShort to "15/11/26",
            StatementDateFormat.DayDashMonth to "15-11-2026",
            StatementDateFormat.DayDashMonthShort to "15-11-26",
            StatementDateFormat.DayDashName to "15-Nov-2026",
            StatementDateFormat.DaySpaceName to "15 Nov 2026",
            StatementDateFormat.DayDashNameShort to "15-Nov-26",
            StatementDateFormat.Iso to "2026-11-15",
            StatementDateFormat.DaySlashMonth to "15/11/2026",
        )
        for ((format, text) in samples) {
            val found = StatementDates.detect(listOf(text, text), today)
            assertEquals(format, found.format, text)
            assertFalse(found.ambiguous, text)
        }
    }

    @Test
    fun `a day past the twelfth says which way round it is`() {
        // 25/12 can only be day first; 12/25 only month first.
        assertEquals(StatementDateFormat.DaySlashMonth, StatementDates.detect(listOf("01/02/2026", "25/12/2025"), today).format)
        val monthFirst = StatementDates.detect(listOf("01/02/2026", "12/25/2025"), today)
        assertEquals(StatementDateFormat.MonthSlashDay, monthFirst.format)
        assertFalse(monthFirst.ambiguous)
    }

    @Test
    fun `when both readings fit every date the user is asked, with two dates read both ways`() {
        val found = StatementDates.detect(listOf("04/03/2026", "05/03/2026", "06/03/2026"), today)
        assertTrue(found.ambiguous)
        assertNull(found.format)
        assertTrue(StatementDateFormat.DaySlashMonth in found.candidates && StatementDateFormat.MonthSlashDay in found.candidates)
        assertEquals(2, found.samples.size)
        assertEquals("04/03/2026", found.samples[0].text)
        assertEquals(LocalDate.of(2026, 3, 4), found.samples[0].dayFirst)
        assertEquals(LocalDate.of(2026, 4, 3), found.samples[0].monthFirst)
    }

    @Test
    fun `dates that read the same both ways are not a question`() {
        val found = StatementDates.detect(listOf("05/05/2026", "11/11/2026"), today)
        assertFalse(found.ambiguous)
        assertEquals(StatementDateFormat.DaySlashMonth, found.format)
    }

    @Test
    fun `a column of no dates has no format`() {
        val found = StatementDates.detect(listOf("Opening Balance", "Total"), today)
        assertNull(found.format)
        assertFalse(found.ambiguous)
        assertTrue(found.candidates.isEmpty())
        assertNull(StatementDates.detect(emptyList(), today).format)
    }
}
