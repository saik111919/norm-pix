package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.parseMinorUnits
import java.time.LocalDate

/**
 * A payment app whose receipt NormPix reads by that app's own layout - which line is the payee, which figure the amount,
 * which part the user's own account - instead of by the rules [SharedTransactionParser] applies to any message. Each app
 * has a reader of its own ([PhonePeReceipt], [GooglePayReceipt], [MutualFundOrderReceipt]), or - where its layout is the
 * common one - a [LabelledReceipt] that knows it by its [tells]; what they all share is in ReceiptVocabulary.kt. Another app
 * is added by listing its reader in [PaymentAppReceipts.readers].
 */
internal interface PaymentAppReceipt {
    /** The app's name, as the form says it: "Read as a PhonePe receipt". */
    val name: String

    /** The lines that tell this app's receipt apart, each with how much it counts. */
    val tells: List<Tell>

    /** How strongly [receipt] looks like this app's: the weight of the [tells] it has; 0 when it has none. */
    fun signature(receipt: ReceiptLayout): Int = tells.sumOf { if (it.found(receipt)) it.weight else 0 }

    /** What [receipt] says. A field it doesn't show, or shows in a way that can't be read with confidence, is null. */
    fun read(receipt: ReceiptLayout, ledger: CurrencySpec, today: LocalDate): ParsedTransactionDraft
}

/** Reads what was shared: a payment app's receipt by that app's reader, anything else by [SharedTransactionParser]. */
object PaymentAppReceipts {
    /** The apps with a reader of their own. */
    internal val readers: List<PaymentAppReceipt> =
        listOf(PhonePeReceipt, GooglePayReceipt, CredReceipt, PayZappReceipt, SbiReceipt, MutualFundOrderReceipt) + BrandedReceipts.all

    /** Below this, a picture only looks a little like an app's receipt: one tell-tale line another app could print too. */
    private const val MIN_SIGNATURE = 2

    /** The app [receipt] is from, or null when it is none of [readers]' - or looks as much like two of them. */
    internal fun appOf(receipt: ReceiptLayout): PaymentAppReceipt? {
        val scored = readers.map { it to it.signature(receipt) }.filter { it.second >= MIN_SIGNATURE }
        val best = scored.maxOfOrNull { it.second } ?: return null
        return scored.filter { it.second == best }.singleOrNull()?.first
    }

    /**
     * Reads what another app shared: the [words] it sent (empty when none) and the text of its [picture]. A picture that is
     * one of [readers]' receipts is read by that app's reader, and the words only fill in what the picture didn't show.
     * Anything else - plain words, a picture from an app without a reader of its own - is read by [SharedTransactionParser],
     * the general rules; so is an app's picture its reader got neither an amount nor a name from (a screen of that app's
     * the reader doesn't know, such as a recharge).
     */
    fun read(words: String, picture: PictureText?, ledger: CurrencySpec, today: LocalDate): SharedTextReading {
        if (picture != null) {
            val receipt = ReceiptLayout.of(picture)
            val app = appOf(receipt)
            if (app != null) {
                val read = app.read(receipt, ledger, today).copy(app = app.name)
                if (read.amount != null || read.ambiguousAmounts.isNotEmpty() || read.counterparty != null) {
                    val fromWords = (SharedTransactionParser.read(words, ledger, today) as? SharedTextReading.Draft)?.draft
                    return SharedTextReading.Draft(if (fromWords == null) read else read.filledFrom(fromWords))
                }
            }
        }
        val all = listOf(words, picture?.text.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
        return SharedTransactionParser.read(all, ledger, today, headline = picture?.headline)
    }

    /** This draft, with what it lacks taken from [other]. */
    private fun ParsedTransactionDraft.filledFrom(other: ParsedTransactionDraft): ParsedTransactionDraft {
        val noAmount = amount == null && ambiguousAmounts.isEmpty()
        return copy(
            amount = if (noAmount) other.amount else amount,
            ambiguousAmounts = if (noAmount) other.ambiguousAmounts else ambiguousAmounts,
            foreignCurrency = if (noAmount) other.foreignCurrency else foreignCurrency,
            type = type ?: other.type,
            kind = if (type == null) other.kind else kind,
            status = if (status == SharedStatus.Unknown) other.status else status,
            counterparty = counterparty ?: other.counterparty,
            date = date ?: other.date,
            time = if (date == null) other.time else time,
            dateUnreadable = if (date == null) other.dateUnreadable else dateUnreadable,
            reference = reference ?: other.reference,
            instrument = instrument ?: other.instrument,
            maskedNumbers = maskedNumbers.ifEmpty { other.maskedNumbers },
        )
    }
}

// ------------------------------------------------------------------ layout

/** One row of a receipt: the pieces of text side by side on it, from left to right. */
class ReceiptRow internal constructor(val cells: List<PictureLine>) {
    /** The row's pieces, side by side. */
    val text: String = cells.joinToString(" ") { it.text.trim() }

    /** True when the row, or one of its pieces on its own, is all [pattern] matches. */
    internal fun has(pattern: Regex): Boolean = pattern.matches(text) || cells.any { pattern.matches(it.text.trim()) }
}

/**
 * A picture's text as it sits on the picture: [rows] from top to bottom, each one's pieces from left to right - the
 * order a person reads it in, which is not the order the recognizer returns it in (it can put a label two lines away
 * from its value). Text whose places aren't known is taken a line to a row, in the order written.
 */
class ReceiptLayout internal constructor(
    val rows: List<ReceiptRow>,
    /** The figure printed largest (see [SharedTransactionParser.headline]). */
    val headline: String? = null,
    /** The picture's file name, as the app that made it named it (see [PictureText.name]). */
    val name: String? = null,
    /** Whether the pieces' places on the picture are known. */
    val placed: Boolean = false,
) {
    /** Every row on a line of its own, its pieces side by side. */
    val text: String = rows.joinToString("\n") { it.text }

    /** Every piece on a line of its own, in reading order. */
    val readingText: String by lazy { rows.flatMap { it.cells }.joinToString("\n") { it.text.trim() } }

    /** The first row at or after [from] that [test] accepts, or -1. */
    internal fun indexOf(from: Int = 0, test: (ReceiptRow) -> Boolean): Int {
        for (i in maxOf(0, from) until rows.size) if (test(rows[i])) return i
        return -1
    }

    companion object {
        fun of(picture: PictureText): ReceiptLayout {
            val placed = picture.lines.any { it.right > it.left && it.bottom > it.top }
            val rows = if (placed) rowsOf(picture.lines) else rowsOf(picture.text)
            return ReceiptLayout(rows, picture.headline, picture.name, placed)
        }

        /**
         * [lines] as rows: a piece is on a row when its middle is within the height of the row's first piece, or that
         * piece's middle within its own height (a small "50" printed beside a large "₹45.").
         */
        internal fun rowsOf(lines: List<PictureLine>): List<ReceiptRow> {
            val rows = mutableListOf<MutableList<PictureLine>>()
            for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.top + it.bottom }) {
                val first = rows.lastOrNull()?.first()
                val sameRow = first != null &&
                    (line.top + line.bottom in 2 * first.top..2 * first.bottom || first.top + first.bottom in 2 * line.top..2 * line.bottom)
                if (sameRow) rows.last() += line else rows += mutableListOf(line)
            }
            return rows.map { row -> ReceiptRow(row.sortedBy { it.left }) }
        }

        internal fun rowsOf(text: String): List<ReceiptRow> =
            text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { ReceiptRow(listOf(PictureLine(it, 0))) }.toList()
    }
}

// ----------------------------------------------------------------- amounts

/**
 * One place a receipt prints its amount, as read: the [whole] and [fraction] digits, and whether a ₹ - or a letter or
 * mark it was read as - was in front ([marked]).
 */
internal class AmountSighting(val whole: String, val fraction: String, val marked: Boolean) {
    fun minor(decimals: Int): Long? = minorOf(whole, fraction, decimals)
}

private fun minorOf(whole: String, fraction: String, decimals: Int): Long? {
    if (whole.isEmpty() && fraction.isEmpty()) return null
    return parseMinorUnits(if (fraction.isEmpty()) whole.ifEmpty { "0" } else "${whole.ifEmpty { "0" }}.$fraction", decimals)?.takeIf { it > 0 }
}

/**
 * A piece of text that is a figure and nothing else, behind a currency mark or what the recognizer made of a ₹ (`Z`,
 * `%`, `*`), perhaps with a sign: `₹1,250.50`, `Z10`, `+₹500`, `45 50` (a point read as a space), `1O` (a zero read as
 * the letter).
 */
private val figurePiece = Regex(
    """^[+\-]?\s*(₹|rs\.?|inr|[zrf](?=[\dOo])|[^\p{L}\p{N}\s.,+\-]{1,2})?\s*(\d[\dOo]*(?:[ ,.]\s?[\dOo]+)*)\s*(?:/-)?$""",
    RegexOption.IGNORE_CASE,
)
private val figureSeparator = Regex("""([ ,.])\s?""")
private val bareYear = Regex("""(?:19|20)\d\d""")

/** Longest whole part taken as an amount: a figure of more digits is an id, not an amount. */
private const val MAX_FIGURE_DIGITS = 7

/**
 * [piece] as one sighting of an amount, or null when it isn't just a figure. Rupee amounts have two decimals at most,
 * so a last group of one or two digits is the paise, whatever separates it ("45.50", "45,50", "45 50"); a last group of
 * three is thousands ("1,250", "1.250", "1 250"). The whole part may be grouped the Indian way or the international one.
 */
internal fun amountSighting(piece: String): AmountSighting? {
    val m = figurePiece.matchEntire(SharedTransactionParser.normalize(piece).trim()) ?: return null
    val marked = m.groupValues[1].isNotEmpty()
    val number = m.groupValues[2].replace('O', '0').replace('o', '0')
    val groups = number.split(figureSeparator)
    val separators = figureSeparator.findAll(number).map { it.groupValues[1] }.toList()
    var whole = groups
    var fraction = ""
    if (groups.size > 1) {
        val last = groups.last()
        when {
            last.length == 2 || (last.length == 1 && separators.last() == ".") -> {
                fraction = last
                whole = groups.dropLast(1)
            }
            last.length != 3 -> return null
        }
    }
    if (whole.size > 1 && (whole.first().length > 3 || whole.drop(1).any { it.length !in 2..3 } || whole.last().length != 3)) return null
    val digits = whole.joinToString("")
    if (digits.length > MAX_FIGURE_DIGITS || (!marked && fraction.isEmpty() && bareYear.matches(digits))) return null
    if (minorOf(digits, fraction, 2) == null) return null
    return AmountSighting(digits, fraction, marked)
}

/** What a receipt's sightings of its amount come to: the [amount], or - when they disagree - the [ambiguous] values. */
internal class SettledAmount(val amount: Long?, val ambiguous: List<Long> = emptyList())

/** Digits the recognizer reads a ₹ as. */
private const val RUPEE_AS_DIGIT = "72"

/**
 * The amount a receipt's [sightings] agree on. A sighting could also be read without its first digit when it has no
 * mark in front and that digit is one a ₹ is read as ("730" for "₹30"), or with its point put back when it has no
 * decimals ("4550" for "45.50"); the value every sighting can be read as is the amount. When more than one fits them
 * all, the one most of them were read as; when none does, they disagree and the amount is left to the user.
 */
internal fun settle(sightings: List<AmountSighting>, decimals: Int): SettledAmount {
    if (sightings.isEmpty()) return SettledAmount(null)
    fun readings(s: AmountSighting): Set<Long> = buildSet {
        val wholes = listOfNotNull(s.whole, s.whole.drop(1).takeIf { !s.marked && s.whole.length > 1 && s.whole.first() in RUPEE_AS_DIGIT })
        for (whole in wholes) {
            minorOf(whole, s.fraction, decimals)?.let(::add)
            if (s.fraction.isEmpty() && whole.length >= 3) minorOf(whole.dropLast(2), whole.takeLast(2), decimals)?.let(::add)
        }
    }
    val asRead = sightings.mapNotNull { it.minor(decimals) }
    if (asRead.isEmpty()) return SettledAmount(null)
    val fitting = sightings.map(::readings).reduce { all, next -> all intersect next }
    return when {
        fitting.size == 1 -> SettledAmount(fitting.single())
        fitting.isEmpty() -> SettledAmount(null, asRead.distinct())
        else -> SettledAmount(asRead.filter { it in fitting }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: asRead.first())
    }
}

/**
 * The amount printed at the right end of [row] - its last piece, or the last two when the paise came out as a piece of
 * their own ("45", ".50") - and the pieces left of it. A last piece that has a name in front of a marked figure
 * ("Suman Sharma ₹10", run together by the recognizer) gives the figure, and the name stays.
 */
internal fun amountAtEnd(row: ReceiptRow): Pair<AmountSighting?, List<String>> {
    val pieces = row.cells.map { it.text.trim() }
    if (pieces.size >= 2 && paiseAlone.matches(pieces.last())) {
        amountSighting(pieces[pieces.size - 2] + pieces.last())?.let { return it to pieces.dropLast(2) }
    }
    amountSighting(pieces.last())?.let { return it to pieces.dropLast(1) }
    val trailing = trailingFigure.find(pieces.last()) ?: return null to pieces
    val sighting = amountSighting(trailing.groupValues[2])?.takeIf { it.marked } ?: return null to pieces
    return sighting to pieces.dropLast(1) + trailing.groupValues[1]
}

private val paiseAlone = Regex("""^[.,]\d{1,2}$""")
private val trailingFigure = Regex("""^(.*\S)\s+(\S+)$""")

// ------------------------------------------------------------------- names

/** An avatar's initials beside a name ("UL"): not a name. */
private val initials = Regex("""^\p{Lu}{1,2}$""")

/** [piece] as a person's or business's name, or null: no UPI id, phone number ("+91 ••••• •6411"), label or initials. */
internal fun nameIn(piece: String): String? {
    if ('@' in piece || initials.matches(piece.trim()) || piece.count(Char::isDigit) > piece.count(Char::isLetter)) return null
    return SharedTransactionParser.cleanName(piece)
}

/** A name found on a receipt, and the amount printed beside it (when one is). */
internal class NamedRow(val name: String?, val amount: AmountSighting?)

/**
 * The name on the rows just below the row at [heading] - PhonePe's "Paid to", then the payee - looked for down to
 * [reach] rows, and not past a row [stop] accepts; with the amount printed at its right. A name the receipt runs on to
 * the next line (directly below, starting where it starts) is joined back together: "UBER INDIA SYSTEMS PRIVATE" over
 * "LIMITED".
 */
internal fun ReceiptLayout.nameBelow(heading: Int, reach: Int, stop: (ReceiptRow) -> Boolean): NamedRow {
    val below = rows.drop(heading + 1).take(reach).takeWhile { !stop(it) }
    for ((i, row) in below.withIndex()) {
        val (amount, rest) = amountAtEnd(row)
        val at = rest.indexOfFirst { partyName(it) != null }
        if (at < 0) continue
        var name = partyName(rest[at])!!
        val first = row.cells.getOrNull(at)
        if (placed && first != null) {
            var last: PictureLine = first
            for (next in below.drop(i + 1)) {
                // An avatar's initials on a row of their own, left of the name: passed over.
                val pieces = next.cells.filterNot { initials.matches(it.text.trim()) && it.right <= first.left }
                if (pieces.isEmpty()) continue
                val more = pieces.singleOrNull() ?: break
                val aligned = kotlin.math.abs(more.left - first.left) <= first.height && more.top - last.bottom <= first.height
                val words = nameIn(more.text)?.takeIf { aligned && (name + " " + it).length <= MAX_NAME } ?: break
                name = "$name $words"
                last = more
            }
        }
        return NamedRow(name, amount)
    }
    return NamedRow(null, null)
}

private const val MAX_NAME = 60

/** Expense or income, as a receipt's heading says it. */
internal fun kindOf(type: TransactionType?): SharedKind = when (type) {
    TransactionType.Expense -> SharedKind.Payment
    TransactionType.Income -> SharedKind.Received
    else -> SharedKind.Unknown
}
