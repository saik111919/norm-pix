package com.example.normpix.finance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.example.normpix.finance.domain.groupingCommaIndices

/*
 * How an amount reads while it is typed. The text the user edits stays plain digits (what `filterAmountInput` keeps and
 * `parseMinorUnits` reads); what is drawn is that text with an optional leading symbol and grouping commas. Because the
 * symbol is part of the same drawn line, one font size fits all of it and one centring puts all of it in the middle.
 */

/**
 * Draws "1234567.5" as "₹ 12,34,567.5": the [prefix] in [prefixColor] (at [prefixSize], when the symbol should be smaller
 * than the figures), then the whole digits grouped (Indian or western per [indianGrouping]) and the fraction as typed. The
 * cursor follows the digits, never landing before a comma or inside the prefix, so editing in the middle of a long number
 * works.
 *
 * With a [hintColor], what is still to come is drawn faintly in it: a "0" while nothing is typed (the cursor between the
 * symbol and it, where the first digit goes - so the empty field and the typed one are the same line), and once the point
 * is typed, the [decimals] not typed yet ("480.5" reads "₹ 480.50", which is what is saved).
 */
internal class AmountTransformation(
    val prefix: String,
    private val indianGrouping: Boolean,
    private val prefixColor: Color = Color.Unspecified,
    private val prefixSize: TextUnit = TextUnit.Unspecified,
    private val hintColor: Color = Color.Unspecified,
    private val decimals: Int = 0,
) : VisualTransformation {

    private fun AnnotatedString.Builder.appendPrefix() {
        if (prefix.isNotEmpty()) withStyle(SpanStyle(color = prefixColor, fontSize = prefixSize)) { append(prefix) }
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            // Nothing typed: nothing drawn, so the field's placeholder is all there is - or, with hints, the symbol and a faint
            // zero, the cursor in front of the zero.
            if (hintColor.isUnspecified) return TransformedText(text, OffsetMapping.Identity)
            val shown = buildAnnotatedString {
                appendPrefix()
                withStyle(SpanStyle(color = hintColor)) { append('0') }
            }
            return TransformedText(
                shown,
                object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int = prefix.length
                    override fun transformedToOriginal(offset: Int): Int = 0
                },
            )
        }
        val point = raw.indexOf('.')
        val wholeLength = if (point < 0) raw.length else point
        val commas = groupingCommaIndices(wholeLength, indianGrouping)
        val shown = buildAnnotatedString {
            appendPrefix()
            var next = 0
            for (i in raw.indices) {
                if (next < commas.size && commas[next] == i) {
                    append(',')
                    next++
                }
                append(raw[i])
            }
            // After the digits, so the cursor (which never goes past the last typed one) stays in front of it.
            val missing = if (point < 0) 0 else decimals - (raw.length - point - 1)
            if (hintColor.isSpecified && missing > 0) withStyle(SpanStyle(color = hintColor)) { append("0".repeat(missing)) }
        }
        val mapping = object : OffsetMapping {
            // A cursor between two digits sits after the comma that comes before the next digit, never before it.
            override fun originalToTransformed(offset: Int): Int = prefix.length + offset + commas.count { it <= offset }

            override fun transformedToOriginal(offset: Int): Int {
                val inside = offset - prefix.length
                if (inside <= 0) return 0
                var original = 0
                while (original < raw.length && original + commas.count { it <= original } < inside) original++
                return original
            }
        }
        return TransformedText(shown, mapping)
    }
}

/**
 * [base] shrunk, if it must be, until [shown] fits in [availablePx] on one line. The glyphs' advances do not scale perfectly
 * with the size, so the result is measured again and tightened until it really fits - which is what keeps a long amount
 * whole on screen instead of running past the field's edge and leaving the rest to be scrolled to. Only the font size
 * changes (the line height stays), so nothing around the amount moves while it is typed. [shown] is measured with its spans,
 * so a symbol drawn smaller is measured at its own size (and shrinks with the rest, being sized relative to it).
 */
@Composable
internal fun rememberFittedAmountStyle(shown: AnnotatedString, base: TextStyle, availablePx: Float): TextStyle {
    val measurer = rememberTextMeasurer()
    return remember(shown, base, availablePx, measurer) {
        fun widthAt(style: TextStyle): Int = measurer.measure(shown, style, softWrap = false, maxLines = 1).size.width

        val natural = widthAt(base)
        if (natural <= availablePx || natural <= 0 || availablePx <= 0f) return@remember base
        var scale = (availablePx / natural).coerceAtLeast(MIN_AMOUNT_SCALE)
        var fitted = base.copy(fontSize = base.fontSize * scale)
        repeat(4) {
            val width = widthAt(fitted)
            if (width <= availablePx || scale <= MIN_AMOUNT_SCALE) return@repeat
            scale = (scale * (availablePx / width) * 0.98f).coerceAtLeast(MIN_AMOUNT_SCALE)
            fitted = base.copy(fontSize = base.fontSize * scale)
        }
        fitted
    }
}

/** An amount is never drawn smaller than this fraction of its normal size; twelve digits and decimals still fit at it. */
private const val MIN_AMOUNT_SCALE = 0.34f
