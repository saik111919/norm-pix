package com.example.normpix.finance.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/*
 * The amount field's calculator (owner, 2026-11-18: "1, 2 and ... APP's own keyboard"). An amount can be typed as a sum - "120+45+30",
 * "1500÷3", "2×350" - and the field saves what it comes to. The text the user edits keeps the signs as they are drawn (+ − × ÷);
 * whatever a phone keyboard sends for them ("-", "*", "/", "x") is turned into those as it arrives. Worked out exactly, in
 * BigDecimal: × and ÷ before + and −, then rounded half up to the currency's smallest unit, once, at the end. Pure: no Android.
 */

/** What a typed amount comes to. */
sealed interface AmountOutcome {
    /** Nothing typed yet (or only a point). */
    data object Empty : AmountOutcome

    /** It comes to [minor] (in the currency's smallest unit; never below zero). */
    data class Value(val minor: Long) : AmountOutcome

    /** A sum that can't be an amount, and why, in words for under the field. */
    data class Problem(val message: String) : AmountOutcome
}

object AmountExpression {
    const val PLUS = '+'

    /** The minus sign (U+2212), not the hyphen a keyboard types: it is the width of the plus beside it. */
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'

    private val operators = charArrayOf(PLUS, MINUS, TIMES, DIVIDE)

    /** The longest sum the field takes: room for a few long amounts, not a spreadsheet. */
    const val MAX_LENGTH = 64

    const val DIVIDE_BY_ZERO = "Can't divide by zero"
    const val BELOW_ZERO = "The total can't be below zero"
    const val TOO_LARGE = "The total is too large"

    fun isOperator(c: Char): Boolean = c in operators

    /** Whether [text] is a sum (has a sign in it) rather than a plain amount. */
    fun isSum(text: String): Boolean = text.any(::isOperator)

    /**
     * [text] as the field keeps it: a keyboard's own signs as the field's ("-" and the dashes are minus, "*" and "x" times, "/"
     * divide), and the grouping commas and spaces of a pasted amount dropped.
     */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                ',', ' ', ' ', ' ', '_' -> Unit
                '-', '–', '—' -> append(MINUS)
                '*', 'x', 'X', '✕' -> append(TIMES)
                '/', '∕' -> append(DIVIDE)
                else -> append(c)
            }
        }
    }

    /**
     * What the field keeps when its text would become [new]: [new] tidied ([normalize]) if it is an amount or a sum still being
     * typed, otherwise [old]. A sum never starts with a sign or has two signs together; each number in it is a plain amount - at
     * most [MAX_MAJOR_DIGITS] whole digits and [decimals] after the point - and only the last may be empty ("120+" as the next is
     * typed). A plain amount is kept exactly as `filterAmountInput` keeps one.
     */
    fun filter(old: String, new: String, decimals: Int): String {
        val text = normalize(new)
        if (text.length > MAX_LENGTH) return old
        val numbers = split(text) ?: return old
        numbers.forEachIndexed { i, number ->
            if (number.isEmpty() && i != numbers.lastIndex) return old
            if (!isAmount(number, decimals)) return old
        }
        return text
    }

    /** What [text] comes to, in the smallest unit of a currency with [decimals]. A sign at the end is not counted yet. */
    fun evaluate(text: String, decimals: Int): AmountOutcome {
        val numbers = split(text) ?: return AmountOutcome.Empty
        val signs = text.filter(::isOperator)
        // A sign at the end waits for its number: "120+" is 120 so far.
        val counted = if (numbers.size > 1 && numbers.last().isEmpty()) numbers.size - 1 else numbers.size
        if (counted == 1 && numbers[0].trim('.').isEmpty()) return AmountOutcome.Empty
        val values = numbers.take(counted).map { if (it.trim('.').isEmpty()) BigDecimal.ZERO else BigDecimal(it.trimEnd('.')) }

        // × and ÷ first: a run of them becomes one term; the terms are then added up with their + and −.
        var total = BigDecimal.ZERO
        var term = values[0]
        var termSign = PLUS
        for (i in 1 until counted) {
            val sign = signs[i - 1]
            val next = values[i]
            when (sign) {
                TIMES -> term = term.multiply(next, precision)
                DIVIDE -> {
                    if (next.signum() == 0) return AmountOutcome.Problem(DIVIDE_BY_ZERO)
                    term = term.divide(next, precision)
                }
                else -> {
                    total = if (termSign == PLUS) total.add(term) else total.subtract(term)
                    term = next
                    termSign = sign
                }
            }
        }
        total = if (termSign == PLUS) total.add(term) else total.subtract(term)

        val rounded = total.setScale(decimals, RoundingMode.HALF_UP)
        if (rounded.signum() < 0) return AmountOutcome.Problem(BELOW_ZERO)
        if (rounded.setScale(0, RoundingMode.DOWN).toPlainString().length > MAX_MAJOR_DIGITS) return AmountOutcome.Problem(TOO_LARGE)
        return AmountOutcome.Value(rounded.movePointRight(decimals).longValueExact())
    }

    /**
     * What the field hands to its form for [text]: a plain amount as it is (so "12." still reads as being typed), a sum as what it
     * comes to ("120+45" -> "165"), and nothing ("") for a sum that can't be an amount - the form then refuses to save it.
     */
    fun formValue(text: String, decimals: Int): String {
        if (!isSum(text)) return text
        return when (val outcome = evaluate(text, decimals)) {
            is AmountOutcome.Value -> minorToInputText(outcome.minor, decimals)
            else -> ""
        }
    }

    /** The numbers between the signs, or null for text with two signs together or one at the start (a sum can't be made of it). */
    private fun split(text: String): List<String>? {
        if (text.isEmpty()) return listOf("")
        if (isOperator(text[0])) return null
        val numbers = mutableListOf<String>()
        var start = 0
        for (i in text.indices) {
            if (isOperator(text[i])) {
                if (i > 0 && isOperator(text[i - 1])) return null
                numbers += text.substring(start, i)
                start = i + 1
            }
        }
        numbers += text.substring(start)
        return numbers
    }

    private fun isAmount(number: String, decimals: Int): Boolean {
        if (!amountShape.matches(number)) return false
        if (number.contains('.') && (decimals == 0 || number.substringAfter('.').length > decimals)) return false
        return true
    }

    private val amountShape = Regex("^\\d{0,$MAX_MAJOR_DIGITS}(\\.\\d*)?$")

    /** Enough digits that a division rounded to the currency's unit at the end is exact for any amount the field takes. */
    private val precision = MathContext(34, RoundingMode.HALF_UP)
}

/** A key on the app's own amount keypad. */
sealed interface AmountKey {
    data class Digit(val digit: Int) : AmountKey

    /** "00": quicker hundreds. */
    data object DoubleZero : AmountKey

    data object Point : AmountKey

    data class Sign(val sign: Char) : AmountKey

    data object Backspace : AmountKey

    data object Clear : AmountKey

    /** Works the sum out, leaving what it comes to. */
    data object Equals : AmountKey
}

/** The field's text and its cursor (or selection, [start] to [end]) after a key. */
data class AmountEdit(val text: String, val start: Int, val end: Int = start)

/**
 * What [key] does to [edit] in a field for a currency with [decimals]: typing goes in at the cursor (over a selection), a sign
 * typed after a sign takes its place, a point goes in once a number ("0." when nothing is before it), and Equals leaves what the
 * sum comes to. Null when the key can't be used there (a twelfth whole digit, a second point, a sign first, Equals on a sum
 * that is no amount): the field stays as it is.
 */
fun applyAmountKey(key: AmountKey, edit: AmountEdit, decimals: Int): AmountEdit? {
    val text = edit.text
    val from = minOf(edit.start, edit.end).coerceIn(0, text.length)
    val to = maxOf(edit.start, edit.end).coerceIn(0, text.length)

    fun insert(piece: String, at: Int = from, until: Int = to): AmountEdit? {
        val next = text.substring(0, at) + piece + text.substring(until)
        if (AmountExpression.filter(text, next, decimals) != next) return null
        return AmountEdit(next, at + piece.length)
    }

    return when (key) {
        is AmountKey.Digit -> insert(key.digit.toString())
        AmountKey.DoubleZero -> insert("00") ?: insert("0")
        AmountKey.Point -> {
            if (decimals == 0) return null
            val numberStart = text.lastIndexOfAny(charArrayOf(AmountExpression.PLUS, AmountExpression.MINUS, AmountExpression.TIMES, AmountExpression.DIVIDE), from - 1) + 1
            insert(if (from == numberStart) "0." else ".")
        }
        is AmountKey.Sign -> {
            if (!AmountExpression.isOperator(key.sign)) return null
            // A sign right after a sign changes it: "120+" then × is "120×".
            if (from == to && from > 0 && AmountExpression.isOperator(text[from - 1])) insert(key.sign.toString(), at = from - 1) else insert(key.sign.toString())
        }
        AmountKey.Backspace -> when {
            from != to -> AmountEdit(text.removeRange(from, to), from)
            from == 0 -> null
            else -> AmountEdit(text.removeRange(from - 1, from), from - 1)
        }
        AmountKey.Clear -> if (text.isEmpty()) null else AmountEdit("", 0)
        AmountKey.Equals -> {
            if (!AmountExpression.isSum(text)) return null
            when (val outcome = AmountExpression.evaluate(text, decimals)) {
                is AmountOutcome.Value -> minorToInputText(outcome.minor, decimals).let { AmountEdit(it, it.length) }
                AmountOutcome.Empty -> AmountEdit("", 0)
                is AmountOutcome.Problem -> null
            }
        }
    }
}
