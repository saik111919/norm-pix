package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A year of accounts: twelve months from the first day of [startMonth] in [startYear]. India's financial year runs April to March
 * (FY 2026-27 is 1 April 2026 to 31 March 2027); a calendar year is January to December. Which one is the user's choice (Settings >
 * Finance > Year starts in); the yearly review by tag reads its totals over one of these.
 */
data class FinancialYear(val startYear: Int, val startMonth: Month = Month.APRIL) : Comparable<FinancialYear> {
    /** The year's first day. */
    val firstDay: LocalDate get() = LocalDate.of(startYear, startMonth, 1)

    /** The year's last day: the day before the next one starts (31 March for an April year). */
    val lastDay: LocalDate get() = firstDay.plusYears(1).minusDays(1)

    /** The whole year as a half-open range: from its first day's midnight up to (not including) the next year's. */
    val range: DateRange get() = DateRange(firstDay.atStartOfDay(), firstDay.plusYears(1).atStartOfDay())

    val next: FinancialYear get() = copy(startYear = startYear + 1)
    val previous: FinancialYear get() = copy(startYear = startYear - 1)

    /** The year's name: "FY 2026-27" for one that runs across two calendar years, "2026" for a calendar year. */
    val label: String
        get() = if (startMonth == Month.JANUARY) "$startYear" else "FY $startYear-${((startYear + 1) % 100).toString().padStart(2, '0')}"

    /** The months it covers: "Apr 2026 – Mar 2027", or "Jan – Dec 2026" for a calendar year. */
    val span: String
        get() {
            val first = firstDay
            val last = lastDay
            return if (first.year == last.year) {
                "${monthName(first.month)} – ${monthName(last.month)} ${last.year}"
            } else {
                "${first.format(MONTH_YEAR)} – ${last.format(MONTH_YEAR)}"
            }
        }

    /** Whether [day] falls in this year. */
    operator fun contains(day: LocalDate): Boolean = !day.isBefore(firstDay) && !day.isAfter(lastDay)

    /** Whether [moment] falls in this year. */
    operator fun contains(moment: LocalDateTime): Boolean = moment in range

    override fun compareTo(other: FinancialYear): Int = firstDay.compareTo(other.firstDay)

    companion object {
        /** The year that [day] is in, for a year that starts in [startMonth]: 31 March 2027 is in FY 2026-27, 1 April 2027 in FY 2027-28. */
        fun containing(day: LocalDate, startMonth: Month = Month.APRIL): FinancialYear =
            FinancialYear(if (day.monthValue >= startMonth.value) day.year else day.year - 1, startMonth)

        private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

        private fun monthName(month: Month): String = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    }
}
