package com.example.normpix.finance.domain

/**
 * A currency the ledger can be kept in. Amounts are always stored as integer
 * *minor units* (paise, cents); [decimals] says how many minor digits one
 * major unit has.
 *
 * @property indianGrouping groups digits as 1,00,000 instead of 100,000.
 */
data class CurrencySpec(
    val code: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val indianGrouping: Boolean = false,
) {
    val minorPerMajor: Long get() = pow10(decimals)

    companion object {
        val INR = CurrencySpec("INR", "₹", "Indian rupee", 2, indianGrouping = true)

        val all: List<CurrencySpec> = listOf(
            INR,
            CurrencySpec("USD", "$", "US dollar", 2),
            CurrencySpec("EUR", "€", "Euro", 2),
            CurrencySpec("GBP", "£", "British pound", 2),
            CurrencySpec("AED", "AED ", "UAE dirham", 2),
            CurrencySpec("SGD", "S$", "Singapore dollar", 2),
            CurrencySpec("AUD", "A$", "Australian dollar", 2),
            CurrencySpec("CAD", "CA$", "Canadian dollar", 2),
            CurrencySpec("LKR", "Rs ", "Sri Lankan rupee", 2),
            CurrencySpec("NPR", "Rs ", "Nepalese rupee", 2),
            CurrencySpec("BDT", "৳", "Bangladeshi taka", 2),
            CurrencySpec("JPY", "¥", "Japanese yen", 0),
        )

        fun byCode(code: String): CurrencySpec = all.firstOrNull { it.code == code } ?: INR
    }
}

internal fun pow10(n: Int): Long {
    var result = 1L
    repeat(n) { result *= 10 }
    return result
}

/**
 * Largest amount a single entry may hold, in major units. Keeps every sum
 * comfortably inside 64-bit integers.
 */
const val MAX_MAJOR_DIGITS = 12

private val separators = Regex("[,\\s_]")
private val decimalShape = Regex("^(\\d*)(?:\\.(\\d*))?$")
private val leadingZeros = Regex("^0+(?=\\d)")

/**
 * Parses user-typed amount text ("1,234.50", "99", "0.5") into integer minor
 * units **without floating point**. Returns null for anything that isn't a
 * plain non-negative decimal with at most [decimals] fraction digits - input
 * is never silently rounded.
 */
fun parseMinorUnits(input: String, decimals: Int): Long? {
    val cleaned = input.replace(separators, "")
    if (cleaned.isEmpty()) return null
    val match = decimalShape.find(cleaned) ?: return null
    val whole = match.groupValues[1]
    val fraction = match.groups[2]?.value ?: ""
    if (whole.isEmpty() && fraction.isEmpty()) return null
    if (fraction.length > decimals) return null
    val wholeDigits = whole.replaceFirst(leadingZeros, "")
    if (wholeDigits.length > MAX_MAJOR_DIGITS) return null
    val wholeValue = if (wholeDigits.isEmpty()) 0L else wholeDigits.toLong()
    val fractionValue = if (decimals == 0) 0L else fraction.padEnd(decimals, '0').toLong()
    return wholeValue * pow10(decimals) + fractionValue
}

/**
 * Plain editable text for [minor] ("1234.5" -> "1234.50"; no grouping, no
 * symbol) - used to pre-fill amount fields when editing.
 */
fun minorToInputText(minor: Long, decimals: Int): String {
    val abs = kotlin.math.abs(minor)
    val divisor = pow10(decimals)
    return buildString {
        if (minor < 0) append('-')
        append(abs / divisor)
        if (decimals > 0) {
            val fraction = (abs % divisor).toString().padStart(decimals, '0')
            if (fraction != "0".repeat(decimals)) append('.').append(fraction)
        }
    }
}

/**
 * Plain machine-readable decimal ("1234.50") for exports: always shows every
 * fraction digit, never grouped, never localized.
 */
fun minorToDecimalString(minor: Long, decimals: Int): String {
    val abs = kotlin.math.abs(minor)
    val divisor = pow10(decimals)
    val fraction = if (decimals == 0) "" else "." + (abs % divisor).toString().padStart(decimals, '0')
    return "${if (minor < 0) "-" else ""}${abs / divisor}$fraction"
}

/**
 * Where the grouping commas go in a run of [wholeLength] digits: each returned index is the digit a comma comes *before*
 * ("1234567" -> [1, 4] as 1,234,567, or [2, 4] as 12,34,567 with [indianGrouping]). Used to show a number as it is typed
 * without changing what was typed.
 */
fun groupingCommaIndices(wholeLength: Int, indianGrouping: Boolean): List<Int> = (1 until wholeLength).filter { i ->
    val right = wholeLength - i
    if (indianGrouping) right == 3 || (right > 3 && (right - 3) % 2 == 0) else right % 3 == 0
}

/** The digits of [whole] with grouping commas ("1234567" -> "1,234,567"); see [groupingCommaIndices]. */
fun groupWholeDigits(whole: String, indianGrouping: Boolean): String {
    val commas = groupingCommaIndices(whole.length, indianGrouping)
    if (commas.isEmpty()) return whole
    return buildString {
        var next = 0
        for (i in whole.indices) {
            if (next < commas.size && commas[next] == i) {
                append(',')
                next++
            }
            append(whole[i])
        }
    }
}

/** How a signed amount should be presented. */
enum class AmountSign {
    /** No sign (balances, budgets, totals). */
    None,

    /** Always show "+" or "-" (transaction rows, deltas). */
    Explicit,
}

/**
 * Display formatting for minor-unit amounts in one currency. Pure integer
 * arithmetic end to end, so output is identical on every device and locale
 * and never touches floating point.
 */
class MoneyFormatter(val currency: CurrencySpec) {
    private fun signed(minor: Long, sign: AmountSign, body: (Long) -> String): String {
        val text = body(kotlin.math.abs(minor))
        return when {
            minor < 0 -> "−$text"
            sign == AmountSign.Explicit && minor > 0 -> "+$text"
            else -> text
        }
    }

    private fun group(whole: Long): String {
        val digits = whole.toString()
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        val groupSize = if (currency.indianGrouping) 2 else 3
        val groups = head.reversed().chunked(groupSize).map { it.reversed() }.reversed()
        return groups.joinToString(",") + "," + tail
    }

    private fun number(abs: Long, trimZeroFraction: Boolean = false): String {
        val per = currency.minorPerMajor
        val whole = group(abs / per)
        if (currency.decimals == 0 || (trimZeroFraction && abs % per == 0L)) return whole
        return whole + "." + (abs % per).toString().padStart(currency.decimals, '0')
    }

    /** "₹1,23,456.50". Drops ".00" when [trimZeroFraction] and the amount is whole. */
    fun format(
        minor: Long,
        sign: AmountSign = AmountSign.None,
        trimZeroFraction: Boolean = false,
    ): String = signed(minor, sign) { abs -> currency.symbol + number(abs, trimZeroFraction) }

    /** "₹1.2L" / "$12K" - for chart axes and very tight spaces only. */
    fun compact(minor: Long): String = signed(minor, AmountSign.None) { abs ->
        val per = currency.minorPerMajor
        val major = abs / per
        val units = if (currency.indianGrouping) {
            listOf(10_000_000L to "Cr", 100_000L to "L", 1_000L to "K")
        } else {
            listOf(1_000_000_000L to "B", 1_000_000L to "M", 1_000L to "K")
        }
        val unit = units.firstOrNull { major >= it.first }
        if (unit == null) {
            currency.symbol + ((abs + per / 2) / per)
        } else {
            val tenths = major * 10 / unit.first
            val text = if (tenths >= 100 || tenths % 10 == 0L) {
                (tenths / 10).toString()
            } else {
                "${tenths / 10}.${tenths % 10}"
            }
            currency.symbol + text + unit.second
        }
    }

    /** "1,23,456.50" with no currency symbol. */
    fun plain(minor: Long): String = signed(minor, AmountSign.None) { number(it) }
}
