package com.example.normpix.finance.statement

import com.example.normpix.finance.domain.parseMinorUnits

/** Which way a statement says money went, in a "Dr" / "Cr" marker. */
enum class StatementSide { Debit, Credit }

/**
 * An amount as a statement cell wrote it, in minor units. [negative] says it carried a minus sign or brackets; [side] is the "Dr" or
 * "Cr" that came with it, when one did.
 */
class StatementAmount(val minor: Long, val negative: Boolean, val side: StatementSide?)

/** Reading the amounts in a bank statement. */
object StatementAmounts {
    /**
     * [cell] as an amount - "1,23,456.78" (Indian grouping), "Rs. 500", "INR 1,200.00", "₹99", "1,234.50 Dr", "Cr 800", "-250",
     * "(250.00)" - or null when it is blank or is not an amount (so a cell of text is never read as zero). Only a decimal point
     * separates the paise: a comma that is followed by one or two digits and nothing more is a decimal comma ("12,50", "1.234,50"),
     * never a thousands mark, since Indian and Western grouping both put three digits (or the last three) after a comma.
     */
    fun parse(cell: String, decimals: Int = 2): StatementAmount? {
        var text = cell.trim()
        if (text.isEmpty()) return null
        var negative = false
        var side: StatementSide? = null
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true
            text = text.substring(1, text.length - 1).trim()
        }
        // What can wrap the figure - a sign, a currency, a Dr or Cr - in any order: peeled off until nothing more comes off.
        var changed = true
        while (changed && text.isNotEmpty()) {
            changed = false
            val before = text
            if (text.first() in MINUS) {
                negative = true
                text = text.substring(1).trim()
            } else if (text.last() in MINUS) {
                negative = true
                text = text.substring(0, text.length - 1).trim()
            }
            if (text.startsWith("+")) text = text.substring(1).trim()
            currencyStart.find(text)?.let { text = text.substring(it.value.length).trim() }
            currencyEnd.find(text)?.let { text = text.substring(0, text.length - it.value.length).trim() }
            sideStart.find(text)?.let {
                side = sideOf(it.groupValues[1])
                text = text.substring(it.value.length).trim()
            }
            sideEnd.find(text)?.let {
                side = sideOf(it.groupValues[1])
                text = text.substring(0, text.length - it.value.length).trim()
            }
            if (text != before) changed = true
        }
        if (text.isEmpty()) return null
        val minor = parseMinorUnits(decimalPoint(text), decimals) ?: return null
        return StatementAmount(minor, negative, side)
    }

    /** Whether [cell] says nothing: empty, or a dash or "NA" the bank puts where there is no amount. */
    fun isBlank(cell: String): Boolean {
        val text = cell.trim()
        return text.isEmpty() || text.all { it in MINUS || it == '.' || it == '_' || it == '*' } || text.uppercase() in NONE
    }

    /** The side a Dr / Cr marker ("Dr", "CR", "Debit", "D", "Withdrawal", "W", ...) names; null for anything else. */
    fun sideOf(marker: String): StatementSide? = when (marker.trim().trimEnd('.').uppercase()) {
        "DR", "D", "DEBIT", "DEBITED", "WITHDRAWAL", "W", "WDL", "DB" -> StatementSide.Debit
        "CR", "C", "CREDIT", "CREDITED", "DEPOSIT", "CD" -> StatementSide.Credit
        else -> null
    }

    /** [text] with its digits grouped by commas and its paise after a point: a decimal comma is turned into a point. */
    private fun decimalPoint(text: String): String {
        val comma = text.lastIndexOf(',')
        if (comma < 0 || comma < text.lastIndexOf('.')) return text
        val after = text.substring(comma + 1).trim()
        if (after.length !in 1..2 || !after.all(Char::isDigit)) return text
        return text.substring(0, comma).replace(".", "").replace(",", "") + "." + after
    }

    private val MINUS = charArrayOf('-', '−', '–')
    private val NONE = setOf("NA", "N/A", "NIL", "NULL")
    private val currencyStart = Regex("""(?i)^(?:rs\.?|inr|₹)\s*""")
    private val currencyEnd = Regex("""(?i)\s*(?:rs\.?|inr|₹)$""")
    private val sideStart = Regex("""(?i)^(dr|cr|debit|credit)(?![a-z])\.?\s*""")
    private val sideEnd = Regex("""(?i)\s*(?<![a-z])(dr|cr|debit|credit)\.?$""")
}
