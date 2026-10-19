package com.example.normpix.finance.share

/**
 * An amount written out in words, as Indian receipts print it under the figure - "Rupees Seventy Four Only", "One Lakh
 * Twenty Five Thousand Rupees and Fifty Paise" - read as a number. It is the receipt's own second copy of its amount, in
 * a different shape from the digits, so it is a good check on them: a ₹ the recognizer read as a 7 ("774") is still
 * "Seventy Four" in words.
 *
 * Indian English: hundred, thousand, lakh (or lac) and crore, with an "and" wherever people put one. A line counts only
 * when every word on it is a number word, a currency word ("Rupees", "Rs", "Paise", "Only") or "and", with at least one
 * number word and a currency word among them; anything else is not an amount in words, whatever else it says. Words
 * the recognizer misspelled by a letter ("Sevnty", "Thousnad") or ran together ("SeventyFour") are put right first, but
 * only to a word that is the one nearest to them.
 */
internal object AmountWords {
    private val units = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
        "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val tens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    /** Every number word below a hundred, by its value. */
    private val numbers: Map<String, Int> = buildMap {
        units.forEachIndexed { value, word -> put(word, value) }
        tens.forEachIndexed { index, word -> put(word, 20 + 10 * index) }
        put("fourty", 40)
    }

    /** What multiplies the number before it: "hundred" within a thousand, the others for what came before them. */
    private val scales: Map<String, Long> = mapOf(
        "hundred" to 100L, "thousand" to 1_000L, "lakh" to 100_000L, "lakhs" to 100_000L, "lac" to 100_000L, "lacs" to 100_000L,
        "crore" to 10_000_000L, "crores" to 10_000_000L, "million" to 1_000_000L,
    )
    private val rupeeWords = setOf("rupees", "rupee", "rs", "inr")
    private val paiseWords = setOf("paise", "paisa", "ps")
    private val glue = setOf("and", "only")

    private val vocabulary: Set<String> = numbers.keys + scales.keys + rupeeWords + paiseWords + glue

    /** The words a fuzzy match may land on: not the short ones, which sit one letter from half the language. */
    private val fuzzyTargets: List<String> = (numbers.keys + scales.keys + setOf("rupees", "rupee", "paise", "paisa")).filter { it.length >= 4 }

    /** Never more than this many lines are put together for one amount: a long amount wraps to a second or third line. */
    private const val MAX_LINES = 3

    private const val MAX_RUPEES = 99_99_99_99_999L

    private val notLetters = Regex("""[^a-z]+""")

    /**
     * The amount [lines] spell out, in minor units of a currency with [decimals] decimals, or null when no line does. The first
     * line - or first run of up to three lines, for an amount that wraps - that is an amount in words and nothing else.
     */
    fun find(lines: List<String>, decimals: Int = 2): Long? {
        for (start in lines.indices) {
            if (lines[start].isBlank() || lines[start].any { it.isDigit() }) continue
            var joined = ""
            for (end in start until minOf(lines.size, start + MAX_LINES)) {
                if (lines[end].any { it.isDigit() }) break
                joined = if (joined.isEmpty()) lines[end] else "$joined ${lines[end]}"
                parse(joined, decimals)?.let { return it }
                // A line that is not words at all ends the run: what follows is not part of this amount.
                if (words(lines[end]) == null) break
            }
        }
        return null
    }

    /** [text] as an amount in words, in minor units, or null when it is not one. */
    fun parse(text: String, decimals: Int = 2): Long? {
        val tokens = words(text) ?: return null
        if (tokens.none { it in numbers || it in scales }) return null
        if (tokens.none { it in rupeeWords || it in paiseWords || it == "only" }) return null

        val paiseAt = tokens.lastIndexOfAny(paiseWords)
        var rupeeTokens = tokens
        var paise = 0L
        if (paiseAt >= 0) {
            // The paise are the number just before the word: back to the "and" or the rupees that part it from the rupees.
            var from = paiseAt
            while (from > 0 && tokens[from - 1] !in rupeeWords && tokens[from - 1] != "and") from--
            paise = number(tokens.subList(from, paiseAt)) ?: return null
            if (paise !in 0..99) return null
            rupeeTokens = tokens.subList(0, from)
        }
        val rupees = if (rupeeTokens.any { it in numbers || it in scales }) number(rupeeTokens) ?: return null else 0L
        if (rupees > MAX_RUPEES) return null
        val minor = when {
            decimals >= 2 -> rupees * pow10(decimals) + paise * pow10(decimals - 2)
            else -> if (paise == 0L) rupees * pow10(decimals) else return null
        }
        return minor.takeIf { it > 0 }
    }

    private fun pow10(n: Int): Long {
        var value = 1L
        repeat(n) { value *= 10 }
        return value
    }

    /** [text] as the vocabulary words it is made of, or null when any word on it isn't one (or can't be put right to one). */
    private fun words(text: String): List<String>? {
        val raw = text.lowercase().split(notLetters).filter { it.isNotEmpty() }
        if (raw.isEmpty()) return null
        val out = mutableListOf<String>()
        for (token in raw) out += resolve(token) ?: return null
        return out
    }

    /** The vocabulary word(s) [token] stands for: itself, a run-together pair or more, or the one word nearest to it. */
    private fun resolve(token: String): List<String>? {
        if (token in vocabulary) return listOf(token)
        if (token.length >= 6) segment(token)?.let { return it }
        if (token.length >= 4) nearest(token)?.let { return listOf(it) }
        return null
    }

    /** [token] split into vocabulary words ("seventyfour"), the fewest of them; null when it can't be. */
    private fun segment(token: String): List<String>? {
        val best = arrayOfNulls<List<String>>(token.length + 1)
        best[0] = emptyList()
        for (end in 1..token.length) {
            for (start in 0 until end) {
                val before = best[start] ?: continue
                val word = token.substring(start, end)
                if (word.length < 3 || word !in vocabulary) continue
                val candidate = before + word
                if (best[end] == null || candidate.size < best[end]!!.size) best[end] = candidate
            }
        }
        return best[token.length]?.takeIf { it.size > 1 }
    }

    /** The only [fuzzyTargets] word within a letter (two, for a long word) of [token] - a swapped pair counts as one - or null. */
    private fun nearest(token: String): String? {
        val limit = if (token.length >= 8) 2 else 1
        val near = fuzzyTargets.filter { kotlin.math.abs(it.length - token.length) <= limit && distance(it, token) <= limit }
        return near.singleOrNull()
    }

    /** Edit distance between [a] and [b] where swapping two neighbouring letters is one edit. */
    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
        }
        return d[a.length][b.length]
    }

    /** The whole number [tokens] say, in units: "one lakh twenty five thousand" is 125000. Null when they say none. */
    private fun number(tokens: List<String>): Long? {
        var total = 0L
        var current = 0L
        var seen = false
        for (token in tokens) {
            val value = numbers[token]
            val scale = scales[token]
            when {
                value != null -> {
                    current += value
                    seen = true
                }
                scale == 100L -> {
                    current = (if (current == 0L) 1L else current) * 100L
                    seen = true
                }
                scale != null -> {
                    total += (if (current == 0L) 1L else current) * scale
                    current = 0L
                    seen = true
                }
            }
        }
        return if (seen) total + current else null
    }
}
