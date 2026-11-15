package com.example.normpix.finance.statement

import java.time.LocalDate
import java.time.LocalTime

/** A date (and, when the bank printed one, a time) read from a statement cell. */
data class StatementMoment(val date: LocalDate, val time: LocalTime?)

/**
 * How a bank writes its dates. Each is read with or without a time after it ("15/11/2026 14:35", "15-Nov-26 02:35 PM").
 * [label] is the pattern as the sheet shows it. A format is read by hand rather than by a locale's own: month names are English, the
 * year is whole or two digits, a day or month may have one digit, and nothing depends on the phone's language.
 */
enum class StatementDateFormat(val label: String, pattern: String, private val day: Int, private val month: Int, private val year: Int, val monthName: Boolean = false) {
    DaySlashMonth("dd/MM/yyyy", """(\d{1,2})/(\d{1,2})/(\d{4})""", 1, 2, 3),
    DaySlashMonthShort("dd/MM/yy", """(\d{1,2})/(\d{1,2})/(\d{2})""", 1, 2, 3),
    DayDashMonth("dd-MM-yyyy", """(\d{1,2})-(\d{1,2})-(\d{4})""", 1, 2, 3),
    DayDashMonthShort("dd-MM-yy", """(\d{1,2})-(\d{1,2})-(\d{2})""", 1, 2, 3),
    DayDashName("dd-MMM-yyyy", """(\d{1,2})-([A-Za-z]{3,9})-(\d{4})""", 1, 2, 3, monthName = true),
    DaySpaceName("dd MMM yyyy", """(\d{1,2})\s+([A-Za-z]{3,9})\.?,?\s+(\d{4})""", 1, 2, 3, monthName = true),
    DayDashNameShort("dd-MMM-yy", """(\d{1,2})-([A-Za-z]{3,9})-(\d{2})""", 1, 2, 3, monthName = true),
    Iso("yyyy-MM-dd", """(\d{4})[-/](\d{1,2})[-/](\d{1,2})""", 3, 2, 1),
    MonthSlashDay("MM/dd/yyyy", """(\d{1,2})/(\d{1,2})/(\d{4})""", 2, 1, 3),
    ;

    private val regex = Regex("""^\s*$pattern(?:[ T]+(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?\s*([AaPp][Mm])?)?\s*$""")

    /** [text] as a moment in this format, or null when it is not written so or is not a real day. */
    fun parse(text: String, today: LocalDate): StatementMoment? {
        val match = regex.matchEntire(text) ?: return null
        val groups = match.groupValues
        val d = groups[day].toIntOrNull() ?: return null
        val m = if (monthName) monthNumber(groups[month]) ?: return null else groups[month].toIntOrNull() ?: return null
        val yearText = groups[year]
        val y = yearText.toIntOrNull()?.let { if (yearText.length == 2) century(it, today) else it } ?: return null
        if (y !in EARLIEST_YEAR..today.year + 1) return null
        val date = try {
            LocalDate.of(y, m, d)
        } catch (_: java.time.DateTimeException) {
            return null
        }
        val hourText = groups[TIME_HOUR]
        if (hourText.isEmpty()) return StatementMoment(date, null)
        var hour = hourText.toInt()
        val minute = groups[TIME_HOUR + 1].toInt()
        val second = groups[TIME_HOUR + 2].ifEmpty { "0" }.toInt()
        val meridiem = groups[TIME_HOUR + 3].lowercase()
        if (meridiem.isNotEmpty()) {
            if (hour !in 1..12) return null
            hour = hour % 12 + if (meridiem == "pm") 12 else 0
        }
        if (hour > 23 || minute > 59 || second > 59) return null
        return StatementMoment(date, LocalTime.of(hour, minute, second))
    }

    private companion object {
        // The groups after the three of the date: hour, minute, second, am / pm.
        const val TIME_HOUR = 4
        const val EARLIEST_YEAR = 1990

        /** A two-digit year in this century, unless that would be past next year: then the last century's. */
        fun century(twoDigits: Int, today: LocalDate): Int {
            val thisCentury = today.year / 100 * 100 + twoDigits
            return if (thisCentury > today.year + 1) thisCentury - 100 else thisCentury
        }

        private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

        fun monthNumber(word: String): Int? {
            val name = word.lowercase()
            val at = months.indexOfFirst { it.startsWith(name) || (name == "sept" && it == "september") }
            return if (at >= 0 && name.length >= 3) at + 1 else null
        }
    }
}

/**
 * How [StatementDates.detect] read a column of dates: the [format] that fits - null when none does, or when two do and the user has
 * to say ([ambiguous]) - every format that reads at least one of them ([candidates], best first), and, for an ambiguous column, a
 * couple of its dates read both ways ([samples]).
 */
class DateDetection(
    val format: StatementDateFormat?,
    val candidates: List<StatementDateFormat>,
    val ambiguous: Boolean,
    val samples: List<AmbiguousDate>,
)

/** A date as written, and the days it is in a day-first and in a month-first reading. */
class AmbiguousDate(val text: String, val dayFirst: LocalDate, val monthFirst: LocalDate)

/** Finding the format a statement's dates are written in. */
object StatementDates {
    /**
     * The format of [cells] (a column of dates, blanks and the odd line of text allowed): the one that reads the most of them.
     * dd/MM/yyyy and MM/dd/yyyy look the same, and both read every date until one has a day past the 12th: when they do and the
     * dates come out differently the answer is [DateDetection.ambiguous] and the user is asked - with two of the dates read both ways;
     * when no date tells them apart the Indian, day-first reading is taken.
     */
    fun detect(cells: List<String>, today: LocalDate): DateDetection {
        val texts = cells.map { it.trim() }.filter { it.isNotEmpty() }
        val counts = StatementDateFormat.entries.associateWith { format -> texts.count { format.parse(it, today) != null } }
        val best = counts.values.maxOrNull() ?: 0
        val candidates = counts.filterValues { it > 0 }.entries.sortedByDescending { it.value }.map { it.key }
        if (best == 0) return DateDetection(null, emptyList(), ambiguous = false, samples = emptyList())
        val top = counts.filterValues { it == best }.keys
        val both = StatementDateFormat.DaySlashMonth in top && StatementDateFormat.MonthSlashDay in top
        if (both) {
            val samples = texts.mapNotNull { text ->
                val dayFirst = StatementDateFormat.DaySlashMonth.parse(text, today)?.date
                val monthFirst = StatementDateFormat.MonthSlashDay.parse(text, today)?.date
                if (dayFirst != null && monthFirst != null && dayFirst != monthFirst) AmbiguousDate(text, dayFirst, monthFirst) else null
            }.distinctBy { it.text }
            val sameCells = texts.all { text ->
                StatementDateFormat.DaySlashMonth.parse(text, today) == StatementDateFormat.MonthSlashDay.parse(text, today)
            }
            // Fitting the same cells but reading them differently, or fitting different cells: the user decides.
            if (!sameCells) return DateDetection(null, candidates, ambiguous = true, samples = samples.take(SAMPLES))
        }
        val format = top.firstOrNull { it == StatementDateFormat.DaySlashMonth } ?: top.first()
        return DateDetection(format, candidates, ambiguous = false, samples = emptyList())
    }

    private const val SAMPLES = 2
}
