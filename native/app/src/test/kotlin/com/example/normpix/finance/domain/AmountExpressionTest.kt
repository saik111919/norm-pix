package com.example.normpix.finance.domain

import com.example.normpix.finance.domain.AmountExpression.DIVIDE
import com.example.normpix.finance.domain.AmountExpression.MINUS
import com.example.normpix.finance.domain.AmountExpression.PLUS
import com.example.normpix.finance.domain.AmountExpression.TIMES
import com.example.normpix.finance.ui.filterAmountInput
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/**
 * The amount field's calculator (2026-11-18): what a sum is kept as while it is typed, what it comes to (exact, × ÷ before + −,
 * rounded half up to the paisa once at the end), what the form is handed, and what each key of the app's keypad does at the cursor.
 */
class AmountExpressionTest {
    private val inr = 2

    private fun sum(vararg parts: Any): String = parts.joinToString("")

    private fun value(text: String, decimals: Int = inr) = AmountExpression.evaluate(text, decimals)

    // ============================================================================================ what is kept while typing

    @Test
    fun `a keyboard's own signs and a pasted amount's commas become the field's`() {
        assertEquals("1250", AmountExpression.normalize("1,250"))
        assertEquals(sum(12, MINUS, 3), AmountExpression.normalize("12-3"))
        assertEquals(sum(2, TIMES, 3), AmountExpression.normalize("2*3"))
        assertEquals(sum(2, TIMES, 3), AmountExpression.normalize("2x3"))
        assertEquals(sum(6, DIVIDE, 2), AmountExpression.normalize("6/2"))
        assertEquals(sum(1, MINUS, 2), AmountExpression.normalize("1–2"), "an en dash is a minus")
        assertEquals("1000", AmountExpression.normalize("1 000"))
    }

    @Test
    fun `a sum being typed is kept, and one that can't be a sum is refused`() {
        val old = "12"
        for (kept in listOf("", "120", "120.5", sum(120, PLUS), sum(120, PLUS, 45), sum("12.5", TIMES, 2, DIVIDE, 4), sum(5, PLUS, "."))) {
            assertEquals(kept, AmountExpression.filter(old, kept, inr), "kept: $kept")
        }
        for (refused in listOf(sum(PLUS, 5), sum(5, PLUS, PLUS, 3), sum(5, PLUS, MINUS, 3), "1.2.3", "12.345", "1234567890123", sum(1, PLUS, "1234567890123"), "12a")) {
            assertEquals(old, AmountExpression.filter(old, refused, inr), "refused: $refused")
        }
        assertEquals(old, AmountExpression.filter(old, "1.5", decimals = 0), "no point where the currency has no decimals")
        assertEquals(old, AmountExpression.filter(old, "1+".repeat(40) + "1", inr), "not a spreadsheet")
    }

    @Test
    fun `a plain amount is kept exactly as the plain field keeps it`() {
        val inputs = listOf("", "0", "007", "12.", ".5", "12.34", "12.345", "1,250", "123456789012", "1234567890123", "1.2.3", "abc", "999999999999.99")
        for (old in listOf("", "12")) {
            for (input in inputs) {
                assertEquals(filterAmountInput(old, input, inr), AmountExpression.filter(old, input, inr), "\"$input\" after \"$old\"")
            }
        }
    }

    // ============================================================================================ what a sum comes to

    @Test
    fun `a sum comes to its total, times and divide first`() {
        assertEquals(AmountOutcome.Value(19_500), value(sum(120, PLUS, 45, PLUS, 30)))
        assertEquals(AmountOutcome.Value(50_000), value(sum(1500, DIVIDE, 3)))
        assertEquals(AmountOutcome.Value(1_400), value(sum(2, PLUS, 3, TIMES, 4)), "2 + 12, not 5 × 4")
        assertEquals(AmountOutcome.Value(400), value(sum(10, MINUS, 2, TIMES, 3)))
        assertEquals(AmountOutcome.Value(400), value(sum(24, DIVIDE, 2, DIVIDE, 3)), "left to right")
        assertEquals(AmountOutcome.Value(75), value(sum("0.5", PLUS, "0.25")))
        assertEquals(AmountOutcome.Value(70_000), value(sum(2, TIMES, 350)))
    }

    @Test
    fun `a division is rounded half up to the paisa, once, at the end`() {
        assertEquals(AmountOutcome.Value(3_333), value(sum(100, DIVIDE, 3)))
        assertEquals(AmountOutcome.Value(67), value(sum(2, DIVIDE, 3)))
        assertEquals(AmountOutcome.Value(13), value(sum(1, DIVIDE, 8)), "0.125 is 0.13")
        assertEquals(AmountOutcome.Value(10_000), value(sum(100, DIVIDE, 3, TIMES, 3)), "not 99.99: rounded at the end only")
        assertEquals(AmountOutcome.Value(33), value(sum(100, DIVIDE, 3), decimals = 0))
    }

    @Test
    fun `a sign at the end waits for its number, and nothing typed is nothing`() {
        assertEquals(AmountOutcome.Value(12_000), value(sum(120, PLUS)))
        assertEquals(AmountOutcome.Value(12_000), value(sum(120, TIMES)))
        assertEquals(AmountOutcome.Value(500), value(sum(5, PLUS, ".")), "a point alone is nothing yet")
        assertEquals(AmountOutcome.Empty, value(""))
        assertEquals(AmountOutcome.Empty, value("."))
        assertEquals(AmountOutcome.Value(0), value(sum(5, MINUS, 5)), "zero is a total; the form says it must be more")
    }

    @Test
    fun `a sum that can't be an amount says why`() {
        assertEquals(AmountOutcome.Problem(AmountExpression.DIVIDE_BY_ZERO), value(sum(5, DIVIDE, 0)))
        assertEquals(AmountOutcome.Problem(AmountExpression.DIVIDE_BY_ZERO), value(sum(5, DIVIDE, "0.00")))
        assertEquals(AmountOutcome.Problem(AmountExpression.BELOW_ZERO), value(sum(5, MINUS, 10)))
        assertEquals(AmountOutcome.Problem(AmountExpression.TOO_LARGE), value(sum("999999999999", TIMES, 10)))
        assertEquals(AmountOutcome.Value(99_999_999_999_999), value(sum("999999999998.99", PLUS, 1)), "twelve whole digits are still an amount")
    }

    @Test
    fun `the form is handed a plain amount as typed and a sum as its total, or nothing`() {
        assertEquals("12.", AmountExpression.formValue("12.", inr))
        assertEquals("", AmountExpression.formValue("", inr))
        assertEquals("165", AmountExpression.formValue(sum(120, PLUS, 45), inr))
        assertEquals("33.33", AmountExpression.formValue(sum(100, DIVIDE, 3), inr))
        assertEquals("120", AmountExpression.formValue(sum(120, PLUS), inr))
        assertEquals("", AmountExpression.formValue(sum(5, DIVIDE, 0), inr))
        assertEquals("", AmountExpression.formValue(sum(5, MINUS, 10), inr))
    }

    // ============================================================================================ the keypad's keys

    private fun key(key: AmountKey, text: String, start: Int = text.length, end: Int = start) = applyAmountKey(key, AmountEdit(text, start, end), inr)

    @Test
    fun `digits go in at the cursor, over a selection`() {
        assertEquals(AmountEdit("1", 1), key(AmountKey.Digit(1), ""))
        assertEquals(AmountEdit("125", 2), key(AmountKey.Digit(2), "15", start = 1))
        assertEquals(AmountEdit("19", 2), key(AmountKey.Digit(9), "1234", start = 1, end = 4))
        assertEquals(AmountEdit("1500", 4), key(AmountKey.DoubleZero, "15"))
        assertNull(key(AmountKey.Digit(1), "123456789012"), "a thirteenth whole digit")
        assertEquals(AmountEdit("123456789010", 12), key(AmountKey.DoubleZero, "12345678901"), "00 where only one fits types one")
        assertNull(key(AmountKey.Digit(1), "12.34"), "a third decimal")
    }

    @Test
    fun `a sign can't come first, and a sign after a sign takes its place`() {
        assertNull(key(AmountKey.Sign(PLUS), ""))
        assertEquals(AmountEdit(sum(120, PLUS), 4), key(AmountKey.Sign(PLUS), "120"))
        assertEquals(AmountEdit(sum(120, TIMES), 4), key(AmountKey.Sign(TIMES), sum(120, PLUS)))
        assertNull(key(AmountKey.Sign(PLUS), sum(120, PLUS, 45), start = 3), "not next to a sign after it")
        assertNull(key(AmountKey.Sign('%'), "120"), "only the four")
    }

    @Test
    fun `a point goes in once a number, with a zero before it when nothing is`() {
        assertEquals(AmountEdit("0.", 2), key(AmountKey.Point, ""))
        assertEquals(AmountEdit("12.", 3), key(AmountKey.Point, "12"))
        assertEquals(AmountEdit(sum(12, PLUS, "0."), 5), key(AmountKey.Point, sum(12, PLUS)))
        assertEquals(AmountEdit(sum("1.5", PLUS, "2."), 6), key(AmountKey.Point, sum("1.5", PLUS, 2)), "each number its own point")
        assertNull(key(AmountKey.Point, "12.5"))
        assertNull(applyAmountKey(AmountKey.Point, AmountEdit("12", 2), decimals = 0))
    }

    @Test
    fun `delete takes the character before the cursor or the selection, and clear empties it`() {
        assertEquals(AmountEdit("12", 2), key(AmountKey.Backspace, "123"))
        assertEquals(AmountEdit("13", 1), key(AmountKey.Backspace, "123", start = 2))
        assertEquals(AmountEdit("1", 1), key(AmountKey.Backspace, "123", start = 1, end = 3))
        assertNull(key(AmountKey.Backspace, "123", start = 0))
        assertEquals(AmountEdit("", 0), key(AmountKey.Clear, sum(12, PLUS, 3)))
        assertNull(key(AmountKey.Clear, ""))
    }

    @Test
    fun `equals leaves what the sum comes to, and refuses one that is no amount`() {
        assertEquals(AmountEdit("165", 3), key(AmountKey.Equals, sum(120, PLUS, 45)))
        assertEquals(AmountEdit("33.33", 5), key(AmountKey.Equals, sum(100, DIVIDE, 3)))
        assertNull(key(AmountKey.Equals, "120"), "nothing to work out")
        assertNull(key(AmountKey.Equals, sum(5, DIVIDE, 0)))
    }
}
