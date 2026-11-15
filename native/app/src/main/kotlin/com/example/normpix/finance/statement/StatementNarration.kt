package com.example.normpix.finance.statement

import com.example.normpix.finance.share.narrationOf
import com.example.normpix.finance.share.tidyName

/**
 * What a transaction of a statement becomes in the ledger: the short [payee] its narration names (null when it names none), the
 * bank's [reference] (null when there is none) and the [note] - "Ref 428312345678 · " and the whole narration, as written.
 */
class StatementEntry(val payee: String?, val reference: String?, val note: String?)

/**
 * Reading a bank's narration ("UPI-SWIGGY-swiggy@icici-ICIC0000001-428312345678-UPI", "NEFT CR-HDFC0000001-ACME CORP LTD-JOHN-N123",
 * "POS 416021XXXXXX1234 AMAZON PAY") the way the app reads the narration of a shared receipt: a payee short enough to be a payee, and
 * a reference to find the same payment by when it is in the ledger already. Pure; nothing it reads is kept or logged.
 */
object StatementNarration {
    /** The longest payee kept: what the app takes of a name read from a receipt. */
    const val MAX_PAYEE = 60

    /** The longest narration kept in a note. */
    const val MAX_NOTE = 300

    /** The entry [description] (a narration, as the bank wrote it) and the statement's [referenceCell] make. */
    fun read(description: String, referenceCell: String?): StatementEntry {
        val text = description.replace(spaces, " ").trim()
        val reference = referenceOf(referenceCell) ?: referenceIn(text)
        val note = listOfNotNull(reference?.let { "Ref $it" }, text.take(MAX_NOTE).trim().ifEmpty { null }).joinToString(" · ").ifEmpty { null }
        return StatementEntry(payeeOf(text), reference, note)
    }

    // ------------------------------------------------------------- reference

    /**
     * The reference in a statement's reference column: its letters and digits (6 to 35 of them, as a payment's reference is kept in a
     * note), with the zeros some banks pad a number with taken off the front - HDFC's "0000428312345678" is the UPI reference
     * "428312345678" a payment app shows. Null for a dash, for nothing but zeros, and for what is too short or long to be one.
     */
    internal fun referenceOf(cell: String?): String? {
        val raw = cell?.trim().orEmpty()
        if (StatementAmounts.isBlank(raw)) return null
        var ref = raw.filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }
        if (ref.all { it == '0' }) return null
        if (ref.all { it in '0'..'9' }) ref = ref.trimStart('0').takeIf { it.length >= MIN_REFERENCE } ?: ref
        return ref.takeIf { it.length in MIN_REFERENCE..MAX_REFERENCE }
    }

    /** The reference a narration carries itself: a UPI reference in its path ("UPI/DR/428312345678/...") or a lone 12-digit number. */
    private fun referenceIn(text: String): String? {
        if (text.isEmpty()) return null
        narrationOf(text)?.reference?.let { return it.trimStart('0').takeIf { stripped -> stripped.length >= MIN_REFERENCE } ?: it }
        return lone12Digits.find(text)?.value
    }

    private const val MIN_REFERENCE = 6
    private const val MAX_REFERENCE = 35
    private val lone12Digits = Regex("""(?<![0-9A-Za-z])\d{12}(?![0-9A-Za-z])""")

    // ----------------------------------------------------------------- payee

    private val spaces = Regex("""\s+""")
    private val cashWithdrawal = Regex("""(?i)^(?:ATW|ATM|NWD|EAW)\b|\bcash\s+(?:wdl|withdrawal)\b""")
    private val channelWords = setOf(
        "UPI", "IMPS", "NEFT", "RTGS", "ACH", "NACH", "ECS", "MMT", "POS", "ATM", "ATW", "NWD", "BIL", "ONL", "INB", "TPT", "CMS", "EBA",
        "MB", "IB", "DR", "CR", "P2A", "P2M", "P2P", "PAYMENT", "TRANSFER", "TRF", "UPIINTENT", "INTENT",
    )
    private val leadWords = channelWords + setOf("TO", "BY", "FROM")
    private val structured = Regex("""(?i)^(?:UPI|IMPS|NEFT|RTGS|ACH|NACH|ECS|MMT|POS|BIL|INB|TPT|CMS|EBA|MB|IB)\b""")
    private val separators = Regex("""\s*[/|\-]\s*""")
    private val ifsc = Regex("""^[A-Za-z]{4}0[A-Za-z0-9]{6}$""")
    private val masked = Regex("""^[X*]{2,}\d*$|^\d+[X*]{2,}\d*$""", RegexOption.IGNORE_CASE)

    /**
     * The payee a narration names, as short as one: the other side's name in a UPI, IMPS or NEFT narration (the way a shared receipt's
     * is read), else the first part of a narration that could be a name - not a reference, a UPI id, a bank's branch code or a masked
     * number - and a narration with no such structure as it is, cut to [MAX_PAYEE]. Words in capitals are written as names are.
     */
    private fun payeeOf(text: String): String? {
        if (text.isEmpty()) return null
        if (cashWithdrawal.containsMatchIn(text)) return "ATM withdrawal"
        narrationOf(text)?.name?.let { name -> finish(name)?.let { return it } }
        val coded = structured.containsMatchIn(text)
        val parts = if (coded) text.split(separators) else listOf(text)
        for (part in parts) nameIn(part)?.let { name -> finish(name)?.let { return it } }
        // A narration with no codes in it is its own best description; one made of codes names no one.
        return if (coded) null else finish(text)
    }

    /** [part] as a name: without the channel and number words it starts with; null when what is left cannot be one. */
    private fun nameIn(part: String): String? {
        val words = part.trim().split(' ').filter { it.isNotEmpty() }.dropWhile { word ->
            word.uppercase().trimEnd('.', ':') in leadWords || word.count(Char::isDigit) >= 4 || masked.matches(word)
        }
        val name = words.joinToString(" ")
        if (name.isEmpty() || '@' in name || ifsc.matches(name)) return null
        val letters = name.count(Char::isLetter)
        val digits = name.count(Char::isDigit)
        if (letters < 2 || digits * 10 > (letters + digits) * 3) return null
        if (name.uppercase() in channelWords) return null
        return name
    }

    private fun finish(name: String): String? {
        val trimmed = name.trim().trim('.', ',', '-', ':', '*', '/', '\'', '"').replace(spaces, " ").trim()
        if (trimmed.length < 2) return null
        return tidyName(trimmed).take(MAX_PAYEE).trimEnd()
    }
}
