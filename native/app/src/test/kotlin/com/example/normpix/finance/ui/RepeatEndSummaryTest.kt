package com.example.normpix.finance.ui

import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RepeatEnd
import com.example.normpix.finance.domain.recurrenceStep
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Before
import org.junit.Test

/** The line under "Ends" that says what the end comes to. */
class RepeatEndSummaryTest {
    private val now = LocalDateTime.of(2026, 10, 19, 12, 0)
    private val start = LocalDateTime.of(2026, 11, 5, 9, 0)
    private val reminderNoun = RepeatNoun(one = "time", many = "times", lastBy = "Last reminder by")

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    private fun monthly(from: LocalDateTime = start) = recurrenceStep(from, RecurrenceFrequency.Monthly, 1)

    private fun summary(ends: RepeatEnd, from: LocalDateTime = start, noun: RepeatNoun = PaymentNoun) =
        repeatEndSummary(ends, from, monthly(from), noun, now)

    @Test
    fun `never says nothing`() {
        assertNull(summary(RepeatEnd.Never))
    }

    @Test
    fun `after N says the number and the day of the last`() {
        assertEquals("After 12 payments · last on 5 Oct 2027", summary(RepeatEnd.After(12)))
        assertEquals("After 2 payments · last on 5 Dec", summary(RepeatEnd.After(2)))
    }

    @Test
    fun `one is one payment`() {
        assertEquals("After 1 payment · last on 5 Nov", summary(RepeatEnd.After(1)))
    }

    @Test
    fun `a day counts the payments up to it and names the last, which may be before the day`() {
        assertEquals("12 payments · last on 5 Oct 2027", summary(RepeatEnd.OnDate(LocalDate.of(2027, 10, 5))))
        assertEquals("12 payments · last on 5 Oct 2027", summary(RepeatEnd.OnDate(LocalDate.of(2027, 10, 20))))
        assertEquals("3 payments · last on 5 Jan 2027", summary(RepeatEnd.OnDate(LocalDate.of(2027, 1, 31))))
    }

    @Test
    fun `a day before the second payment says it is only the first`() {
        assertEquals("Just the first payment", summary(RepeatEnd.OnDate(LocalDate.of(2026, 11, 20))))
        assertEquals("Just the first payment", summary(RepeatEnd.OnDate(LocalDate.of(2026, 11, 5))))
    }

    @Test
    fun `too many to count are said so, not counted out`() {
        val daily = recurrenceStep(start, RecurrenceFrequency.Daily, 1)
        assertEquals("1000+ payments", repeatEndSummary(RepeatEnd.OnDate(LocalDate.of(2077, 1, 1)), start, daily, PaymentNoun, now))
    }

    @Test
    fun `a reminder counts in times`() {
        assertEquals("After 3 times · last on 5 Jan 2027", summary(RepeatEnd.After(3), noun = reminderNoun))
        assertEquals("4 times · last on 5 Feb 2027", summary(RepeatEnd.OnDate(LocalDate.of(2027, 2, 10)), noun = reminderNoun))
        assertEquals("Just the first time", summary(RepeatEnd.OnDate(LocalDate.of(2026, 11, 6)), noun = reminderNoun))
    }

    @Test
    fun `the nouns count`() {
        assertEquals("1 payment", PaymentNoun.count(1))
        assertEquals("2 payments", PaymentNoun.count(2))
        assertEquals("1 time", reminderNoun.count(1))
        assertEquals("12 times", reminderNoun.count(12))
    }
}
