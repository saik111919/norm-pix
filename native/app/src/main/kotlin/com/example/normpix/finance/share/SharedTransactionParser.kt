package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.parseMinorUnits
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

/** What a shared message says happened to the money, as far as its words go. */
enum class SharedKind {
    /** Money went out: a payment, a purchase, money sent to someone. */
    Payment,

    /** Money came in from someone. */
    Received,

    /** Money came back for something bought. Recorded as income: the ledger's amounts are never negative. */
    Refund,

    /** A debit the bank undid (usually a payment that didn't go through). Also income. */
    Reversal,

    /** The message doesn't say which way the money went. */
    Unknown,
}

/** Whether the message says the money has actually moved. */
enum class SharedStatus { Completed, Pending, Failed, Requested, Unknown }

/** What the money moved through, when the message names it; used to find which of the user's accounts it was. */
enum class SharedInstrument { CreditCard, DebitCard, Card, Wallet, BankAccount, Upi }

/**
 * What could be read from a shared message. Everything is optional: a field the message doesn't state - or states in a
 * way that can't be read with confidence - is null, never a guess.
 */
data class ParsedTransactionDraft(
    /** In minor units of the ledger's currency; null when no amount was found, or [ambiguousAmounts] / [foreignCurrency] say why not. */
    val amount: Long? = null,
    /** The amounts the message mentions when it couldn't tell which one moved (then [amount] is null). */
    val ambiguousAmounts: List<Long> = emptyList(),
    /** The currency the message's amount is in when that isn't the ledger's (then [amount] is null: nothing is converted). */
    val foreignCurrency: String? = null,
    /** Expense or income; null when the message doesn't say. Never a transfer. */
    val type: TransactionType? = null,
    val kind: SharedKind = SharedKind.Unknown,
    val status: SharedStatus = SharedStatus.Unknown,
    /** The merchant, recipient or sender, as the message writes it. */
    val counterparty: String? = null,
    val date: LocalDate? = null,
    /** Only with a [date]. */
    val time: LocalTime? = null,
    /** True when the message gives a date that isn't a real one (31-02-26, 10/25/26): [date] is then null and must be chosen. */
    val dateUnreadable: Boolean = false,
    /** A UPI reference, UTR or app transaction id - letters and digits only. */
    val reference: String? = null,
    val instrument: SharedInstrument? = null,
) {
    /** A short description for the note: what kind of entry this is, and its reference. Null when there is nothing to say. */
    val note: String?
        get() = listOfNotNull(
            when (kind) {
                SharedKind.Refund -> "Refund"
                SharedKind.Reversal -> "Reversal"
                else -> null
            },
            reference?.let { "Ref $it" },
        ).joinToString(" · ").ifEmpty { null }
}

/** One line of a picture's text, as the recognizer found it, and how tall it is on the picture in pixels. */
data class PictureLine(val text: String, val height: Int)

/** The outcome of reading shared text. */
sealed interface SharedTextReading {
    /** Nothing to read: no text, or only whitespace (a shared picture or file). */
    data object Empty : SharedTextReading

    /** Longer than any transaction message ([SharedTransactionParser.MAX_LENGTH]); not read at all. */
    data object TooLong : SharedTextReading

    /** Text without an amount, a payment word or a reference - a link, a chat message. */
    data object NotATransaction : SharedTextReading

    data class Draft(val draft: ParsedTransactionDraft) : SharedTextReading
}

/**
 * Reads the text a payment or bank app shares (a payment confirmation, a bank SMS forwarded from Messages) into a
 * [ParsedTransactionDraft]. Runs on the device only; the text is never stored or logged.
 *
 * Built from rules, not message templates, so a new wording usually needs a keyword or a pattern added to one of the
 * lists below rather than a new parser:
 * - **Amounts** need evidence: a currency mark next to the number (`₹`, `Rs.`, `INR`, `500/-`) or a verb right before
 *   it (`debited by 250.0`). Bare numbers - UPI references, `XX1234`, phone numbers - are never amounts.
 * - Each amount's **role** comes from the words just before it in the same clause: balances, limits, dues, fees and
 *   cashback are left out. When several different amounts remain, the one a payment word sits next to is taken; if
 *   that still leaves more than one, the amount is left for the user (see [ParsedTransactionDraft.ambiguousAmounts]).
 * - **Direction** comes from the payment word nearest the amount ("debited", "credited", "sent you"), then from the
 *   message as a whole; refunds and reversals are income. Without either, it is unknown - an amount alone is not an
 *   expense.
 * - **Dates** are read day first (Indian convention): 10-10-26, 10/10/2026, 10-Oct-26, 10Oct26, 10 Oct 2026,
 *   Oct 10, 2026, 2026-10-10; a time near the date is read too. A date that isn't real is reported as such, never
 *   replaced by today.
 * - Everything after the bank's "Not you? Call..." footer is ignored.
 */
object SharedTransactionParser {
    /** A bank SMS or a payment app's message is a few hundred characters; anything longer is not one. */
    const val MAX_LENGTH = 4_000

    /** Longest merchant / sender name taken from a message. */
    private const val MAX_NAME = 60

    /**
     * Reads [raw]. [headline] is, for text read from a picture, the line printed largest ([headline] of its lines): payment
     * apps print the amount far bigger than anything else, so when the words give no amount - the recognizer often misreads
     * the `₹` in front of it - that figure is the amount.
     */
    fun read(
        raw: String?,
        ledger: CurrencySpec = CurrencySpec.INR,
        today: LocalDate = LocalDate.now(),
        headline: String? = null,
    ): SharedTextReading {
        if (raw == null || raw.isBlank()) return SharedTextReading.Empty
        if (raw.length > MAX_LENGTH) return SharedTextReading.TooLong
        val text = normalize(raw).trim()
        if (text.isEmpty()) return SharedTextReading.Empty

        val all = amountCandidates(text, ledger.decimals)
        // The bank's footer ("Not you? Call 1800..., SMS BLOCK to 9215...") only ever follows the transaction itself.
        val body = if (all.isEmpty()) text else text.substring(0, footerStart(text, after = all.minOf { it.start }))
        val candidates = all.filter { it.start < body.length }
        val words = chooseAmount(body, candidates, ledger)
        val printed = headlineAmount(headline, ledger.decimals)
        // The printed figure settles it only where the words don't: no amount, or several of which it is one.
        val choice = if (words.amount == null && words.foreign == null && printed != null && (words.ambiguous.isEmpty() || printed in words.ambiguous)) {
            AmountChoice(printed, emptyList(), null, null)
        } else {
            words
        }
        val reference = reference(body) ?: reference(text)
        if (candidates.isEmpty() && printed == null && reference == null && !movementCue.containsMatchIn(body)) {
            return SharedTextReading.NotATransaction
        }

        val credits = cues(creditCue, body)
        val debits = cues(debitCue, body).filter { debit -> credits.none { it.overlaps(debit) } }
        val moneyBack = (refundCue.containsMatchIn(body) || reversalCue.containsMatchIn(body)) && credits.isNotEmpty()
        val failed = failCue.containsMatchIn(body) && !moneyBack
        val requested = !failed && requestCue.containsMatchIn(body)
        val status = when {
            failed -> SharedStatus.Failed
            requested -> SharedStatus.Requested
            pendingCue.containsMatchIn(body) -> SharedStatus.Pending
            completedCue.containsMatchIn(body) -> SharedStatus.Completed
            else -> SharedStatus.Unknown
        }
        val refund = !failed && refundCue.containsMatchIn(body)
        val reversal = !failed && !refund && reversalCue.containsMatchIn(body)
        val type = when {
            requested -> null
            refund || reversal -> TransactionType.Income
            else -> direction(body, choice.at, credits, debits)
        }
        val kind = when {
            refund -> SharedKind.Refund
            reversal -> SharedKind.Reversal
            type == TransactionType.Income -> SharedKind.Received
            type == TransactionType.Expense -> SharedKind.Payment
            else -> SharedKind.Unknown
        }
        val when_ = dateIn(body, today)
        return SharedTextReading.Draft(
            ParsedTransactionDraft(
                amount = choice.amount,
                ambiguousAmounts = choice.ambiguous,
                foreignCurrency = choice.foreign,
                type = type,
                kind = kind,
                status = status,
                counterparty = counterparty(body, type),
                date = when_.date,
                time = when_.time,
                dateUnreadable = when_.unreadable,
                reference = reference,
                instrument = instrument(body),
            ),
        )
    }

    // ------------------------------------------------------------------ text

    /** Plain spaces, ASCII digits and hyphens, no control characters - so the patterns below see one spelling of each. */
    internal fun normalize(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when {
                ch in '०'..'९' -> append('0' + (ch - '०'))
                ch == '\r' -> append('\n')
                ch == '\n' -> append('\n')
                ch == '\u200B' || ch == '\uFEFF' -> Unit
                ch in '\u2010'..'\u2015' || ch == '\u2212' -> append('-')
                Character.isWhitespace(ch) || Character.isSpaceChar(ch) || Character.isISOControl(ch) -> append(' ')
                else -> append(ch)
            }
        }
    }

    private val clauseBreak = Regex("""[;!?|\n]|\.\s""")

    /** Up to [max] characters before [at], from the start of their clause. */
    private fun clauseBefore(text: String, at: Int, max: Int): String {
        val window = text.substring(maxOf(0, at - max), at)
        val last = clauseBreak.findAll(window).lastOrNull() ?: return window
        return window.substring(last.range.last + 1)
    }

    /** Up to [max] characters after [at], to the end of their clause. */
    private fun clauseAfter(text: String, at: Int, max: Int): String {
        val window = text.substring(at, minOf(text.length, at + max))
        val first = clauseBreak.find(window) ?: return window
        return window.substring(0, first.range.first)
    }

    private val footerCue = Regex(
        """\bnot\s+(?:you|u|done\s+by\s+you|initiated\s+by\s+you)\b|\bif\s+not\s+(?:done\s+by\s+)?(?:you|u)\b|\bsms\s+block\b|""" +
            """\bcall\s+(?:us\s+)?(?:on\s+|at\s+)?\+?\d|\bto\s+report\b|\breport\s+(?:fraud|unauthori[sz]ed)\b|\bfor\s+dispute\b|""" +
            """\bnever\s+share\b|\bdo\s+not\s+share\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun footerStart(text: String, after: Int): Int =
        footerCue.findAll(text).firstOrNull { it.range.first > after }?.range?.first ?: text.length

    // --------------------------------------------------------------- amounts

    /** A whole number token, taken in one go (no backtracking into it), then checked by [toMinor]. */
    private const val NUMBER = """(?>\d[\d,]*(?:\.\d+)?)"""

    private const val PREFIX_MARKS = """₹|rs\.?|inr|रु\.?|us\$|s\$|a\$|ca\$|\$|usd|eur|€|gbp|£|aed|sgd|aud|cad|lkr|npr|bdt|৳|jpy|¥"""

    private val prefixAmount = Regex("""(?<![\p{L}\d])($PREFIX_MARKS)\s*:?\s*($NUMBER)""", RegexOption.IGNORE_CASE)
    private val suffixAmount = Regex(
        """(?<![\p{L}\d.,/])($NUMBER)\s?(inr|rupees?|rs\.?|usd|eur|gbp|aed|sgd|aud|cad|lkr|npr|bdt|jpy|/-)(?!\p{L})""",
        RegexOption.IGNORE_CASE,
    )

    /** "debited by 250.0" (SBI writes no currency at all): the verb is the evidence. */
    private val verbAmount = Regex("""\b(?:debited|credited)\s+(?:by|for|with)\s+($NUMBER)(?![\d%])""", RegexOption.IGNORE_CASE)

    /** The currency a mark stands for, and the ledger currencies it fits ("Rs" is a rupee of India, Sri Lanka or Nepal). */
    private class Mark(val label: String, val ledgers: Set<String>)

    private fun mark(token: String): Mark = when (token.lowercase().trimEnd('.')) {
        "₹", "inr", "रु" -> Mark("INR", setOf("INR"))
        "rs", "rupee", "rupees", "/-" -> Mark("Rs", setOf("INR", "LKR", "NPR"))
        "$" -> Mark("$", setOf("USD", "SGD", "AUD", "CAD"))
        "usd", "us$" -> Mark("USD", setOf("USD"))
        "€", "eur" -> Mark("EUR", setOf("EUR"))
        "£", "gbp" -> Mark("GBP", setOf("GBP"))
        "s$", "sgd" -> Mark("SGD", setOf("SGD"))
        "a$", "aud" -> Mark("AUD", setOf("AUD"))
        "ca$", "cad" -> Mark("CAD", setOf("CAD"))
        "৳", "bdt" -> Mark("BDT", setOf("BDT"))
        "¥", "jpy" -> Mark("JPY", setOf("JPY"))
        else -> token.uppercase().let { Mark(it, setOf(it)) }
    }

    private enum class Role { Movement, Balance, Limit, Due, Fee, Reward }

    private class Candidate(
        /** Null when the number isn't a well-formed amount ("1,2,3", "250.123"). */
        val minor: Long?,
        /** Null for an amount found by its verb alone: it is in whatever currency the account is. */
        val mark: Mark?,
        val start: Int,
        val end: Int,
        val role: Role,
    )

    private val balanceCue = Regex("""\bbal(?:ance)?\b|शेष""", RegexOption.IGNORE_CASE)
    private val limitCue = Regex("""\b(?:lmt|limit)\b""", RegexOption.IGNORE_CASE)
    private val dueCue = Regex("""\bdue\b(?!\s+to\b)|\boutstanding\b|\bmin(?:imum)?\.?\s+(?:amt|amount)\b""", RegexOption.IGNORE_CASE)
    private val feeCue = Regex("""\b(?:fee|fees|charges?|gst|tax|surcharge)\b""", RegexOption.IGNORE_CASE)
    private val rewardBefore = Regex(
        """\b(?:cashback|rewards?|points|won|earned|discount|saved|savings|offer)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val rewardAfter = Regex("""^\s*(?:cashback|off\b|discount|reward)""", RegexOption.IGNORE_CASE)

    private fun roleAt(text: String, start: Int, end: Int): Role {
        val before = clauseBefore(text, start, 24)
        return when {
            balanceCue.containsMatchIn(before) -> Role.Balance
            limitCue.containsMatchIn(before) -> Role.Limit
            dueCue.containsMatchIn(before) -> Role.Due
            feeCue.containsMatchIn(before) -> Role.Fee
            rewardBefore.containsMatchIn(before) || rewardAfter.containsMatchIn(clauseAfter(text, end, 12)) -> Role.Reward
            else -> Role.Movement
        }
    }

    private fun amountCandidates(text: String, decimals: Int): List<Candidate> {
        val found = mutableListOf<Candidate>()
        val prefixMarks = mutableListOf<IntRange>()
        for (m in prefixAmount.findAll(text)) {
            val number = m.groups[2]!!
            prefixMarks += m.groups[1]!!.range
            found += Candidate(toMinor(number.value, decimals), mark(m.groupValues[1]), m.range.first, m.range.last + 1, roleAt(text, m.range.first, m.range.last + 1))
        }
        for (m in suffixAmount.findAll(text)) {
            val markRange = m.groups[2]!!.range
            val number = m.groups[1]!!.range
            // "A/c 1234 INR 250": the INR belongs to the 250 after it, not to the account number before it.
            if (prefixMarks.any { it.overlaps(markRange) } || found.any { number.first in it.start until it.end }) continue
            found += Candidate(toMinor(m.groupValues[1], decimals), mark(m.groupValues[2]), m.range.first, m.range.last + 1, roleAt(text, m.range.first, m.range.last + 1))
        }
        for (m in verbAmount.findAll(text)) {
            val number = m.groups[1]!!.range
            if (found.any { number.first in it.start until it.end }) continue
            found += Candidate(toMinor(m.groupValues[1], decimals), null, number.first, number.last + 1, Role.Movement)
        }
        return found.sortedBy { it.start }
    }

    /**
     * A number token as minor units, or null when it isn't written like money: grouping commas in the wrong places
     * ("1,2,3"), more than two decimals ("250.123"), or zero.
     */
    private fun toMinor(token: String, decimals: Int): Long? {
        val t = token.trimEnd(',')
        if (t.count { it == '.' } > 1 || t.contains(",,") || t.contains(",.")) return null
        val whole = t.substringBefore('.')
        val fraction = t.substringAfter('.', "")
        if (whole.contains(',')) {
            val groups = whole.split(',')
            if (groups.first().isEmpty() || groups.first().length > 3 || groups.last().length != 3) return null
            if (groups.subList(1, groups.size - 1).any { it.length != 2 && it.length != 3 }) return null
        }
        if (fraction.length > 2) return null
        return parseMinorUnits(t, decimals)?.takeIf { it > 0 }
    }

    private class AmountChoice(val amount: Long?, val ambiguous: List<Long>, val foreign: String?, val at: Candidate?)

    /**
     * A line that is just a figure, perhaps behind a currency mark - or behind what the recognizer made of a `₹` (`%`, `*`,
     * `{`, `Z`, `R`). A bare year is not one.
     */
    private val headlineShape = Regex(
        """^\s*(?:₹|rs\.?|inr|[zr]|[^\p{L}\p{N}\s]{1,2})?\s*(\d[\d,]*(?:\.\d{1,2})?)\s*(?:/-)?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val yearLike = Regex("""(?:19|20)\d\d""")

    /** The amount a picture's [line] shows, in minor units, or null when the line isn't just a well-written figure. */
    internal fun headlineAmount(line: String?, decimals: Int): Long? {
        val m = headlineShape.matchEntire(normalize(line ?: return null).trim()) ?: return null
        val number = m.groupValues[1]
        if (yearLike.matches(number) && m.value.trim() == number) return null
        return toMinor(number, decimals)
    }

    /**
     * The figure printed largest on a picture - on a payment app's receipt (Google Pay, PhonePe, Paytm, CRED, a bank's app),
     * the amount - when it clearly stands out: well above the usual line height, with no other figure as large. Null
     * otherwise. [lines] are the picture's text lines with their heights, as the recognizer found them.
     */
    fun headline(lines: List<PictureLine>): String? {
        val figures = lines.filter { it.height > 0 && headlineAmount(it.text, 2) != null }
        val tallest = figures.maxByOrNull { it.height } ?: return null
        val heights = lines.map { it.height }.filter { it > 0 }.sorted()
        if (heights.size > 1 && tallest.height < heights[heights.size / 2] * 1.4) return null
        val value = headlineAmount(tallest.text, 2)
        val rival = figures.any { it !== tallest && it.height >= tallest.height * 0.85 && headlineAmount(it.text, 2) != value }
        return if (rival) null else tallest.text
    }

    private val movementCue = Regex(
        """\b(?:debited|credited|paid|sent|spent|received|refund(?:ed)?|revers(?:ed|al)|transferred|withdrawn|withdrawal|""" +
            """deposited|purchase|payment|txn|transaction|amount|amt)\b|डेबिट|क्रेडिट|भुगतान|प्राप्त|जमा""",
        RegexOption.IGNORE_CASE,
    )

    private fun hasMovementCueNear(text: String, c: Candidate): Boolean =
        movementCue.containsMatchIn(clauseBefore(text, c.start, 30)) || movementCue.containsMatchIn(clauseAfter(text, c.end, 30))

    private fun chooseAmount(text: String, candidates: List<Candidate>, ledger: CurrencySpec): AmountChoice {
        val moving = candidates.filter { it.role == Role.Movement }
        if (moving.isEmpty()) return AmountChoice(null, emptyList(), null, null)
        var pool = moving
        if (moving.distinctBy { it.minor }.size > 1) {
            val cued = moving.filter { hasMovementCueNear(text, it) }
            if (cued.isNotEmpty()) pool = cued
        }
        val values = pool.distinctBy { it.minor }
        if (values.size > 1) {
            return AmountChoice(null, values.mapNotNull { it.minor }.distinct(), null, null)
        }
        val chosen = pool.first()
        val mark = chosen.mark
        if (mark != null && ledger.code !in mark.ledgers) return AmountChoice(null, emptyList(), mark.label, chosen)
        return AmountChoice(chosen.minor, emptyList(), null, chosen)
    }

    // ------------------------------------------------------------- direction

    private val creditCue = Regex(
        """\b(?:sent|paid|transferred)\s+you\b|\b(?:payment|money)\s+received\b|\breceived\s+(?:a\s+)?(?:payment|money)\b|""" +
            """\b(?:transferred|sent|added)\s+to\s+your\b|\bcredited\b(?!\s+to\s+(?:vpa|beneficiary|payee)\b)|""" +
            """\breceived\b(?!\s+by\b)|\bdeposited\b|प्राप्त|जमा|क्रेडिट""",
        RegexOption.IGNORE_CASE,
    )
    private val debitCue = Regex(
        """\bdebited\b|\bdebit\b|\bpaid\b|\bsent\b|\bspent\b|\bpurchased?\b|\bwithdrawn\b|\bwithdrawal\b|\btransferred\b|""" +
            """\btrf\b|\bpayment\b|डेबिट|भुगतान|निकासी""",
        RegexOption.IGNORE_CASE,
    )

    private fun cues(regex: Regex, text: String): List<IntRange> = regex.findAll(text).map { it.range }.toList()

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

    /**
     * Expense or income: the payment word nearest the amount in its clause decides ("Acct debited for Rs 250; SWIGGY
     * credited" is an expense); without one, a message that only speaks one way does. Otherwise null.
     */
    private fun direction(text: String, amount: Candidate?, credits: List<IntRange>, debits: List<IntRange>): TransactionType? {
        if (amount != null) {
            fun distance(cue: IntRange): Int? {
                val gap = when {
                    cue.last < amount.start -> text.substring(cue.last + 1, amount.start)
                    cue.first >= amount.end -> text.substring(amount.end, cue.first)
                    else -> return null
                }
                return gap.length.takeIf { it <= 40 && !clauseBreak.containsMatchIn(gap) }
            }
            val credit = credits.mapNotNull(::distance).minOrNull()
            val debit = debits.mapNotNull(::distance).minOrNull()
            when {
                credit != null && (debit == null || credit < debit) -> return TransactionType.Income
                debit != null && (credit == null || debit < credit) -> return TransactionType.Expense
            }
        }
        return when {
            credits.isNotEmpty() && debits.isEmpty() -> TransactionType.Income
            debits.isNotEmpty() && credits.isEmpty() -> TransactionType.Expense
            else -> headerDirection(text)
        }
    }

    /** A screen's title line: "To SWIGGY" (Google Pay's paid screen) or "From Rahul Sharma" (its received one). No colon. */
    private val headerTo = Regex("""(?m)^[ \t]*(?:paid\s+)?to[ \t]+([^:\n][^\n]*)$""", RegexOption.IGNORE_CASE)
    private val headerFrom = Regex("""(?m)^[ \t]*(?:received\s+)?from[ \t]+([^:\n][^\n]*)$""", RegexOption.IGNORE_CASE)

    /**
     * Payment apps that print no payment word at all title the screen with who the money went to or came from. Only a
     * title naming someone counts ("From HDFC Bank A/C *1234" is the user's own account, not a sender), and only when
     * the titles all point one way.
     */
    private fun headerDirection(text: String): TransactionType? {
        val to = headerTo.findAll(text).any { cleanName(it.groupValues[1]) != null }
        val from = headerFrom.findAll(text).any { cleanName(it.groupValues[1]) != null }
        return when {
            to && !from -> TransactionType.Expense
            from && !to -> TransactionType.Income
            else -> null
        }
    }

    private val refundCue = Regex("""\brefund(?:ed|s)?\b""", RegexOption.IGNORE_CASE)
    private val reversalCue = Regex("""\brevers(?:ed|al)\b|\bcredited\s+back\b""", RegexOption.IGNORE_CASE)
    private val failCue = Regex(
        """\b(?:failed|failure|declined|unsuccessful|not\s+successful|rejected|could\s+not\s+be\s+(?:processed|completed)|""" +
            """was\s+not\s+completed)\b|विफल""",
        RegexOption.IGNORE_CASE,
    )
    private val requestCue = Regex("""\b(?:has\s+)?requested\b|\bcollect\s+request\b|\bpayment\s+request\b""", RegexOption.IGNORE_CASE)
    private val pendingCue = Regex(
        """\bpending\b|\bprocessing\b(?!\s+(?:fee|charges?))|\bin\s+progress\b|\binitiated\b|\bawaiting\b|\bscheduled\b|""" +
            """\bunder\s+process\b|\bwill\s+be\s+(?:debited|credited|refunded|processed|deducted|reversed)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val completedCue = Regex(
        """\b(?:successful(?:ly)?|success|completed|done|debited|credited|paid|received|sent|spent|refunded|reversed)\b|""" +
            """डेबिट|क्रेडिट|प्राप्त|जमा""",
        RegexOption.IGNORE_CASE,
    )

    // ----------------------------------------------------- merchant / sender

    /** A name: letters, digits and the few marks names and UPI ids use, at most [MAX_NAME] long, as short as its end allows. */
    private const val NAME = """([\p{L}\d@&'_*][\p{L}\d@&'._*\- ]{0,59}?)"""

    /** Where a name ends: the next word that isn't part of it, punctuation, or the end. */
    private const val NAME_END = """(?=\s+(?:on|via|using|ref|refno|upi|txn|from|for|at|is|was|has|have|will|with|avl|bal|info|not|""" +
        """thru|through|by|towards|vide|successfully|successful|credited|debited|refunded|failed|declined|pending)\b|""" +
        """\s*[,;:()\[\]|!\n]|(?<!\b(?:mr|mrs|ms|dr|st|jr))\.(?:\s|$)|\s*$|\s+-\s)"""

    /** "UPI/P2M/428312345678/SWIGGY" (Axis and others): the reference, then the name. */
    private val upiPath = Regex("""\bUPI/(?:[A-Z0-9]{2,4}/)?(\d{6,16})/([^/\n]{2,60})""", RegexOption.IGNORE_CASE)

    /** Where money went to, for an expense. */
    private val payeeRules = listOf(
        // ICICI: "Acct XX123 debited for Rs 250.00 on 10-Oct-26; SWIGGY credited."
        Regex("""[;:]\s*$NAME\s+credited\b""", RegexOption.IGNORE_CASE),
        Regex("""(?<!due\s)\bto\s*:?\s+(?:vpa\s+|beneficiary\s+|payee\s+)?$NAME$NAME_END""", RegexOption.IGNORE_CASE),
        Regex("""\bat\s+$NAME$NAME_END""", RegexOption.IGNORE_CASE),
        // ICICI card: "spent using ICICI Bank Card XX1234 on 10-Oct-26 on AMAZON."
        Regex(
            """\bon\s+(?:\d{1,2}[-/ ]?[a-z]{3,9}[-/ ]?\d{2,4}|\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})\s+on\s+$NAME$NAME_END""",
            RegexOption.IGNORE_CASE,
        ),
    )

    /** Where money came from, for income. */
    private val payerRules = listOf(
        Regex("""^\s*$NAME\s+(?:has\s+)?(?:sent|paid|transferred)\s+you\b""", RegexOption.IGNORE_CASE),
        Regex("""\bfrom\s*:?\s+(?:vpa\s+)?$NAME$NAME_END""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:by|linked\s+to)\s+(?:vpa\s+)?([\w.\-]{2,64}@[a-z]{2,64})\b""", RegexOption.IGNORE_CASE),
    )

    /** What a name can't be: the user's own account or card, a masked number, a phone number. */
    private val notAName = Regex(
        """^(?:your|you|yours|self|me|my|own|beneficiary|vpa|upi|merchant)\b|\b(?:a/?c|acct|account|card|wallet)\b|""" +
            """^[x*]+\d|\b[x*]{2,}\d|\d{6,}""",
        RegexOption.IGNORE_CASE,
    )

    private fun cleanName(raw: String): String? {
        val name = raw.trim().trim('.', ',', '-', ':', '*', '\'', '"', '/').replace(Regex("""\s+"""), " ").trim()
        if (name.length < 2 || name.length > MAX_NAME) return null
        if (name.first().isDigit() || name.none { it.isLetter() }) return null
        if (notAName.containsMatchIn(name)) return null
        return name
    }

    private fun counterparty(text: String, type: TransactionType?): String? {
        upiPath.find(text)?.let { m ->
            cleanName(m.groupValues[2].substringBefore(" Not ").substringBefore("  "))?.let { return it }
        }
        val rules = when (type) {
            TransactionType.Income -> payerRules
            TransactionType.Expense -> payeeRules
            else -> payeeRules + payerRules
        }
        for (rule in rules) {
            for (m in rule.findAll(text)) cleanName(m.groupValues[1])?.let { return it }
        }
        return null
    }

    // ------------------------------------------------------------- reference

    private val referenceCue = Regex(
        """\b(?:upi\s*(?:ref(?:erence)?|txn|transaction)?\.?\s*(?:no|number|id)?|ref(?:erence)?\.?\s*(?:no|number|id)?|refno|rrn|""" +
            """utr(?:\s*no)?|txn\.?\s*(?:id|no|ref)?|transaction\s*(?:id|ref(?:erence)?|no|number)|order\s*id)\s*[.:#\-]*\s*(?:is\s+)?""" +
            """([A-Za-z0-9]{6,35})\b""",
        RegexOption.IGNORE_CASE,
    )

    private fun reference(text: String): String? {
        upiPath.find(text)?.let { return it.groupValues[1] }
        return referenceCue.findAll(text).map { it.groupValues[1] }.firstOrNull { token -> token.count(Char::isDigit) >= 6 }
    }

    // ------------------------------------------------------------ instrument

    private val instrumentCues = listOf(
        SharedInstrument.CreditCard to Regex("""\bcredit\s+card\b""", RegexOption.IGNORE_CASE),
        SharedInstrument.DebitCard to Regex("""\bdebit\s+card\b""", RegexOption.IGNORE_CASE),
        SharedInstrument.Card to Regex("""\bcard\b""", RegexOption.IGNORE_CASE),
        SharedInstrument.Wallet to Regex("""\bwallet\b""", RegexOption.IGNORE_CASE),
        SharedInstrument.BankAccount to Regex("""\b(?:a/c|ac|acct|account)\b""", RegexOption.IGNORE_CASE),
        SharedInstrument.Upi to Regex("""\bupi\b""", RegexOption.IGNORE_CASE),
    )

    private fun instrument(text: String): SharedInstrument? = instrumentCues.firstOrNull { it.second.containsMatchIn(text) }?.first

    // ------------------------------------------------------------------ date

    private class When(val date: LocalDate?, val time: LocalTime?, val unreadable: Boolean)

    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private const val MONTH = """(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?"""

    private val isoDate = Regex("""(?<![\d/.-])(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
    private val numericDate = Regex("""(?<![\d/.:,-])(\d{1,2})([-/.])(\d{1,2})\2(\d{4}|\d{2})(?![\d])""")
    private val dayMonthDate = Regex("""(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[\s-]?$MONTH(?:[\s,-]*(\d{4}|\d{2})(?![\d:]))?""", RegexOption.IGNORE_CASE)
    private val monthDayDate = Regex("""\b$MONTH\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})(?![\d:])""", RegexOption.IGNORE_CASE)
    private val relativeDay = Regex("""\b(today|yesterday)\b""", RegexOption.IGNORE_CASE)
    private val time = Regex("""(?<![\d.])(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\s*([ap])\.?\s?m\b\.?)?""", RegexOption.IGNORE_CASE)

    /** Dates that aren't when the money moved - a card's due date, an offer's end, an expiry: the words right before them. */
    private val notTheDateCue = Regex(
        """\b(?:due(?:\s+date)?|pay\s+by|valid(?:\s+(?:till|until|upto|up\s+to))?|validity|expir\w*|expiry\s+date|till|until|before|""" +
            """statement\s+date)\b[\s:.-]*(?:on\s+)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** A date written in the message: where it is, and the day it names (null when it isn't a real day). */
    private class Found(val range: IntRange, val date: LocalDate?)

    private fun dateIn(text: String, today: LocalDate): When {
        val found = buildList {
            isoDate.findAll(text).forEach { add(Found(it.range, day(it.groupValues[1], it.groupValues[2], it.groupValues[3], today))) }
            numericDate.findAll(text).forEach {
                add(Found(it.range, day(it.groupValues[4], it.groupValues[3], it.groupValues[1], today)))
            }
            dayMonthDate.findAll(text).forEach {
                add(Found(it.range, day(it.groupValues[3], months[it.groupValues[2].lowercase()].toString(), it.groupValues[1], today)))
            }
            monthDayDate.findAll(text).forEach {
                add(Found(it.range, day(it.groupValues[3], months[it.groupValues[1].lowercase()].toString(), it.groupValues[2], today)))
            }
        }.filterNot { notTheDateCue.containsMatchIn(clauseBefore(text, it.range.first, 24)) }
        // The first date written is the transaction's; a later valid one is never swapped in for a first one that isn't real.
        val first = found.minByOrNull { it.range.first }
        if (first == null) {
            val relative = relativeDay.find(text) ?: return When(null, null, unreadable = false)
            val date = if (relative.value.equals("today", ignoreCase = true)) today else today.minusDays(1)
            return When(date, timeNear(text, relative.range), unreadable = false)
        }
        if (first.date == null) return When(null, null, unreadable = true)
        return When(first.date, timeNear(text, first.range), unreadable = false)
    }

    /**
     * The date [year]-[month]-[day] names, or null when it isn't a real one. A two-digit year is this century's; a date
     * written without a year is the latest one that isn't after [today].
     */
    private fun day(year: String, month: String, day: String, today: LocalDate): LocalDate? {
        val m = month.toIntOrNull() ?: return null
        val d = day.toIntOrNull() ?: return null
        return try {
            when {
                year.isEmpty() -> {
                    val thisYear = LocalDate.of(today.year, m, d)
                    if (thisYear.isAfter(today)) LocalDate.of(today.year - 1, m, d) else thisYear
                }
                else -> {
                    val y = year.toInt().let { if (year.length == 2) 2000 + it else it }
                    if (y < 2000 || y > today.year + 1) null else LocalDate.of(y, m, d)
                }
            }
        } catch (_: DateTimeException) {
            null
        }
    }

    /** A time written just after (or else just before) the date at [dateAt]. */
    private fun timeNear(text: String, dateAt: IntRange): LocalTime? {
        val after = text.substring(dateAt.last + 1, minOf(text.length, dateAt.last + 1 + 30))
        val before = text.substring(maxOf(0, dateAt.first - 30), dateAt.first)
        val match = time.find(after) ?: time.findAll(before).lastOrNull() ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues[3].toIntOrNull() ?: 0
        when (match.groupValues[4].lowercase()) {
            "a" -> if (hour in 1..12) hour %= 12 else return null
            "p" -> if (hour in 1..12) hour = hour % 12 + 12 else return null
        }
        if (hour > 23 || minute > 59 || second > 59) return null
        return LocalTime.of(hour, minute, second)
    }
}
