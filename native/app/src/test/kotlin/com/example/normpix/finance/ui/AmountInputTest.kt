package com.example.normpix.finance.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.groupWholeDigits
import com.example.normpix.finance.domain.groupingCommaIndices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How an amount reads while it is typed: grouping commas and a symbol drawn around plain digits, with the cursor kept honest. */
class AmountInputTest {
    private val inr = CurrencySpec.INR
    private val usd = CurrencySpec.byCode("USD")

    @Test
    fun `commas go where the money formatter puts them, western and Indian`() {
        for (currency in listOf(inr, usd)) {
            val formatter = MoneyFormatter(currency)
            for (whole in listOf(0L, 7L, 42L, 999L, 1_000L, 12_345L, 99_999L, 100_000L, 1_234_567L, 12_345_678L, 123_456_789_012L)) {
                val shownByFormatter = formatter.plain(whole * currency.minorPerMajor).substringBefore('.')
                assertEquals(shownByFormatter, groupWholeDigits(whole.toString(), currency.indianGrouping), "$whole in ${currency.code}")
            }
        }
        assertEquals(listOf(1, 4), groupingCommaIndices(7, indianGrouping = false))
        assertEquals(listOf(2, 4), groupingCommaIndices(7, indianGrouping = true))
        assertEquals(emptyList(), groupingCommaIndices(3, indianGrouping = true))
    }

    private fun transform(raw: String, currency: CurrencySpec = inr, prefix: String = "₹ "): androidx.compose.ui.text.input.TransformedText =
        AmountTransformation(prefix, currency.indianGrouping, Color.Red).filter(AnnotatedString(raw))

    @Test
    fun `typed digits are drawn grouped with the symbol in front and the fraction as typed`() {
        assertEquals("₹ 12,34,567.5", transform("1234567.5").text.text)
        assertEquals("₹ 999", transform("999").text.text)
        assertEquals("₹ 1,000.", transform("1000.").text.text, "a trailing point is kept so typing the decimals can go on")
        assertEquals("$ 1,234,567", transform("1234567", usd, "$ ").text.text)
        assertEquals("1,00,000", transform("100000", inr, prefix = "").text.text, "no symbol when the field has its own")
    }

    @Test
    fun `nothing typed draws nothing, so the placeholder is all there is`() {
        assertEquals("", transform("").text.text)
    }

    @Test
    fun `the cursor maps both ways and never lands inside the symbol or in front of a comma`() {
        val raw = "1234567.50"
        val result = transform(raw)
        val shown = result.text.text
        val mapping = result.offsetMapping
        var last = -1
        for (original in 0..raw.length) {
            val inShown = mapping.originalToTransformed(original)
            assertTrue(inShown in 0..shown.length, "offset $original -> $inShown is inside the drawn text")
            assertTrue(inShown > last, "offsets only move forward")
            last = inShown
            assertEquals(original, mapping.transformedToOriginal(inShown), "round trip at $original")
            assertTrue(inShown >= "₹ ".length, "never inside the symbol")
            // The character right after the cursor is the digit that follows it in the text being edited - never a comma, so a
            // digit typed at the cursor lands where the user put it.
            if (original < raw.length) assertEquals(raw[original], shown[inShown], "cursor before '${raw[original]}'")
        }
        // A tap somewhere in the symbol or on a comma resolves to a real position in the edited text.
        for (inShown in 0..shown.length) {
            assertTrue(mapping.transformedToOriginal(inShown) in 0..raw.length)
        }
    }

    @Test
    fun `placeholder shows the currency's decimals`() {
        assertEquals("₹ 0.00", amountPlaceholder("₹ ", 2))
        assertEquals("¥ 0", amountPlaceholder("¥ ", 0))
    }
}
