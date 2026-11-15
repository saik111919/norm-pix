package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The year the review by tag adds up over: April to March (India's financial year) or January to December, with 31 March and 1 April
 * on the right sides of the line; and the review's own rules - every tag listed, the tax-deductible one first, then the biggest total,
 * a tag's name 1 to 30 characters.
 */
class FinancialYearTest {
    @Test
    fun `an April year runs from 1 April to 31 March, and is named FY 2026-27`() {
        val year = FinancialYear(2026, Month.APRIL)
        assertEquals(LocalDate.of(2026, 4, 1), year.firstDay)
        assertEquals(LocalDate.of(2027, 3, 31), year.lastDay)
        assertEquals(DateRange(LocalDateTime.of(2026, 4, 1, 0, 0), LocalDateTime.of(2027, 4, 1, 0, 0)), year.range)
        assertEquals("FY 2026-27", year.label)
        assertEquals("Apr 2026 – Mar 2027", year.span)
        assertEquals("FY 1999-00", FinancialYear(1999).label, "the century turns as a two-digit year")
    }

    @Test
    fun `31 March is the last day of a year and 1 April the first of the next`() {
        assertEquals(FinancialYear(2026), FinancialYear.containing(LocalDate.of(2027, 3, 31)))
        assertEquals(FinancialYear(2027), FinancialYear.containing(LocalDate.of(2027, 4, 1)))
        assertEquals(FinancialYear(2025), FinancialYear.containing(LocalDate.of(2026, 1, 15)))
        assertEquals(FinancialYear(2026), FinancialYear.containing(LocalDate.of(2026, 11, 15)))
        val year = FinancialYear(2026)
        assertTrue(LocalDateTime.of(2027, 3, 31, 23, 59, 59) in year)
        assertFalse(LocalDateTime.of(2027, 4, 1, 0, 0) in year)
        assertTrue(LocalDateTime.of(2026, 4, 1, 0, 0) in year)
        assertFalse(LocalDateTime.of(2026, 3, 31, 23, 59) in year)
        assertTrue(LocalDate.of(2027, 3, 31) in year)
        assertFalse(LocalDate.of(2027, 4, 1) in year)
    }

    @Test
    fun `a January year is the calendar year, named by its number`() {
        val year = FinancialYear.containing(LocalDate.of(2026, 3, 31), Month.JANUARY)
        assertEquals(FinancialYear(2026, Month.JANUARY), year)
        assertEquals(FinancialYear(2026, Month.JANUARY), FinancialYear.containing(LocalDate.of(2026, 4, 1), Month.JANUARY))
        assertEquals(FinancialYear(2025, Month.JANUARY), FinancialYear.containing(LocalDate.of(2025, 12, 31), Month.JANUARY))
        assertEquals(DateRange(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0)), year.range)
        assertEquals(LocalDate.of(2026, 12, 31), year.lastDay)
        assertEquals("2026", year.label)
        assertEquals("Jan – Dec 2026", year.span)
    }

    @Test
    fun `years step and compare`() {
        val year = FinancialYear(2026)
        assertEquals(FinancialYear(2027), year.next)
        assertEquals(FinancialYear(2025), year.previous)
        assertTrue(year.previous < year)
        assertEquals(year.range.end, year.next.range.start, "one year ends where the next begins: nothing falls between them")
    }

    // ------------------------------------------------------------------------------------------- the review

    private val made = LocalDateTime.of(2026, 11, 1, 9, 0)

    @Test
    fun `the review lists every tag, the tax-deductible one first, then the biggest total, then by name`() {
        val tags = listOf(
            Tag(1, "Goa trip", made),
            Tag(2, "Tax-deductible", made),
            Tag(3, "Reimbursable", made),
            Tag(4, "Books", made),
            Tag(5, "Art", made),
        )
        val totals = listOf(
            TagTotal(1, spent = 50_000, received = 0, count = 4),
            TagTotal(2, spent = 1_000, received = 0, count = 1),
            TagTotal(3, spent = 30_000, received = 30_000, count = 6),
        )
        val review = tagReview(tags, totals)
        assertEquals(listOf("Tax-deductible", "Reimbursable", "Goa trip", "Art", "Books"), review.map { it.tag.name })
        assertEquals(TagTotal(4), review.first { it.tag.id == 4L }.total, "a tag with nothing this year is listed, with zeros")
        assertEquals(60_000L, review[1].total.total, "the total is what moved either way")
    }

    @Test
    fun `the tax-deductible tag leads whatever its case or total, even with nothing in the year`() {
        val review = tagReview(
            listOf(Tag(1, "Big", made), Tag(2, "tax-DEDUCTIBLE", made)),
            listOf(TagTotal(1, spent = 9_00_000, count = 9)),
        )
        assertEquals(listOf("tax-DEDUCTIBLE", "Big"), review.map { it.tag.name })
        assertTrue(isTaxDeductible(" Tax-deductible "))
        assertFalse(isTaxDeductible("Tax"))
    }

    @Test
    fun `a tag's name is 1 to 30 characters once tidied`() {
        assertEquals("Goa trip", cleanTagName("  Goa \t  trip "))
        assertEquals("Give the tag a name.", tagNameFault(cleanTagName("   ")))
        assertNull(tagNameFault("x".repeat(30)))
        assertEquals("Keep the tag's name to 30 characters.", tagNameFault("x".repeat(31)))
        assertNull(tagNameFault("x".repeat(29) + "🧾"), "an emoji is one character")
        assertEquals(listOf("Tax-deductible", "Reimbursable"), SUGGESTED_TAGS)
    }
}
