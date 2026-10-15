package com.example.normpix.finance.share

import com.example.normpix.finance.domain.TransactionType
import kotlin.math.abs

/*
 * What every receipt reader shares, kept in one place: how a receipt tells which app made it ([Tell]); the words receipts
 * label their parts with ([ReceiptWords]) and how a label's value is found beside or below it ([labelled]); how a
 * receipt says its status, its reference, and - in a bank's narration - which way the money went; and how a name is
 * tidied of what the recognizer added to it ([partyName]). An app's reader then says only what is its own: its tell-tale
 * lines and the order it prints things in. The picture is read once, its layout built once, and only the reader whose
 * receipt it is goes through it (see [PaymentAppReceipts.read]).
 */

// ------------------------------------------------------------------ tells

/** Something on a receipt that says which app made it, and how much it counts towards that ([PaymentAppReceipt.signature]). */
internal class Tell(val weight: Int, val found: (ReceiptLayout) -> Boolean) {
    companion object {
        /** A row [pattern] finds something in. */
        fun row(weight: Int, pattern: Regex) = Tell(weight) { receipt -> receipt.rows.any { pattern.containsMatchIn(it.text) } }

        /** A row, or a piece of one, that is all [pattern] matches. */
        fun piece(weight: Int, pattern: Regex) = Tell(weight) { receipt -> receipt.rows.any { it.has(pattern) } }

        /**
         * The app's own name or logo ([name], a pattern) - not where a receipt names the app the other side uses ("…@ybl on
         * PhonePe", "swiggy@paytm") or one it was paid through ("via Paytm").
         */
        fun brand(weight: Int, name: String): Tell {
            val pattern = Regex("""(?i)(?<!\bon\s)(?<!\bvia\s)(?<![\p{L}\d@.])(?:$name)(?![\p{L}\d])""")
            return Tell(weight) { receipt -> receipt.rows.any { row -> '@' !in row.text && pattern.containsMatchIn(row.text) } }
        }
    }
}

// ------------------------------------------------------------------ labels

/**
 * A label a receipt prints before a value - "Paid to", "UPI Ref No:", "Date & Time" - in any of the ways apps word it
 * ([words], a pattern). It is a label only at the start of a row or of a piece of one.
 */
internal class Label(words: String) {
    private val pattern = Regex("""^\W{0,2}(?:$words)(?![\p{L}\d])\s*#?\s*[:.\-–]?\s*(.*)$""", RegexOption.IGNORE_CASE)

    /** What follows the label in [piece] ("" when nothing does), or null when [piece] doesn't start with it. */
    fun after(piece: String): String? = pattern.matchEntire(piece.trim())?.groupValues?.get(1)?.trim()
}

/** The labels receipts use, whichever app prints them. */
internal object ReceiptWords {
    /** The other side, of money paid. Said this way, it is the other side whatever else the receipt says. */
    val paidTo = Label("""paid\s+to|sent\s+to|transferred\s+to|payment\s+to|money\s+sent\s+to""")

    /** The other side, of money received. */
    val receivedFrom = Label("""(?:money\s+|payment\s+)?received\s+from|paid\s+by|sent\s+by""")

    /** A bare "To" / "From": the other side - or the user's own account (Paytm: "From: State Bank Of India - 1234"). */
    val to = Label("""to""")
    val from = Label("""from""")

    /** The user's own account, money went out of. */
    val debitedFrom = Label(
        """debited\s+from|paid\s+from|paid\s+using|paid\s+via|paid\s+with|pay(?:ing)?\s+(?:using|via)|""" +
            """payment\s+(?:method|mode|source)|from\s+(?:account|a/?c)|source\s+account""",
    )

    /** The user's own account, money came into. */
    val creditedTo = Label("""credited\s+(?:to|in)|received\s+in|deposited\s+(?:to|in)|to\s+(?:account|a/?c)""")

    /** The user's own account, in a bank's table: "Account Number: XXXXXXX3638". */
    val accountNumber = Label("""(?:account|a/?c)\s*(?:number|no\.?)""")

    val amount = Label("""(?:transaction\s+|total\s+|paid\s+|txn\s+)?amount(?:\s+(?:paid|received|debited|credited))?|you\s+paid|amt""")

    val date = Label(
        """(?:transaction|txn|payment|value)\s+date(?:\s*(?:&|and)\s*time)?|date\s*(?:&|and)\s*time|date|paid\s+on|""" +
            """completed\s+on|placed\s+on""",
    )

    /** A bank's description of the entry: "TRANSFER TO … UPI/DR/…/NAME/…". */
    val narration = Label("""narration|remarks?|description|particulars""")

    /** The bank's reference for the payment - the one its SMS carries too, so a payment found twice is known. */
    val bankReference = Label(
        """utr(?:\s*(?:no|number))?|rrn|upi\s*ref(?:erence)?\.?(?:\s*(?:no|number|id))?\.?|upi\s+(?:transaction|txn)\s*(?:id|no|ref)?|""" +
            """bank\s+ref(?:erence)?\.?(?:\s*(?:no|number|id))?|ref(?:erence)?\.?\s*(?:no|number|id)\.?|reference""",
    )

    /** An app's own id for the payment, used when the bank's isn't shown: "Transaction ID", "CRED transaction ID", "Order ID". */
    val appReference = Label("""(?:[\p{L}.]+\s+)?(?:transaction|txn|order|payment)\s*(?:id|no|number|ref)""")

    /** Every label above: a row that starts with one is not a name, nor the rest of another label's value. */
    val all = listOf(paidTo, receivedFrom, to, from, debitedFrom, creditedTo, accountNumber, amount, date, narration, bankReference, appReference)
}

/** A label on a receipt: the row it is on ([at]), and what is printed beside it on that row ([beside], "" when nothing is). */
internal class Labelled(val at: Int, val beside: String, internal val besideCell: PictureLine?)

/** The first row (at or after [from]) that starts with [label], or has a piece that does. */
internal fun ReceiptLayout.labelled(label: Label, from: Int = 0): Labelled? {
    for (i in maxOf(0, from) until rows.size) {
        val cells = rows[i].cells
        for ((j, cell) in cells.withIndex()) {
            val inline = label.after(cell.text) ?: continue
            val rest = cells.drop(j + 1)
            val beside = (listOf(inline) + rest.map { it.text.trim() }).filter { it.isNotEmpty() }.joinToString(" ")
            return Labelled(i, beside, rest.lastOrNull() ?: cell.takeIf { inline.isNotEmpty() })
        }
    }
    return null
}

/** What [found] labels: what is beside it, or else the row below it. */
internal fun ReceiptLayout.valueOf(found: Labelled): String = found.beside.ifEmpty { rows.getOrNull(found.at + 1)?.text.orEmpty() }

/** A row that starts with one of [ReceiptWords]' labels. */
internal fun ReceiptRow.isLabel(): Boolean = cells.firstOrNull()?.let { first -> ReceiptWords.all.any { it.after(first.text) != null } } == true

private val idPart = Regex("""^[A-Za-z0-9_\-]{2,40}$""")

/**
 * [found]'s value with its run-on: an id too long for its row goes on below it, under its end or its start
 * (PayZapp's "txn_cb2ef183-…-f" over "916559354c2").
 */
internal fun ReceiptLayout.idOf(found: Labelled): String {
    val value = valueOf(found)
    val last = found.besideCell ?: return value
    val next = rows.getOrNull(found.at + 1)?.cells?.singleOrNull() ?: return value
    if (!placed || !idPart.matches(next.text.trim())) return value
    val aligned = abs(next.right - last.right) <= last.height || abs(next.left - last.left) <= last.height
    return if (aligned && next.top - last.bottom <= last.height) value + next.text.trim() else value
}

// ----------------------------------------------------------------- status

private val statusRow = Regex(
    """(?i)^\W*(?:(?:transaction|payment|transfer|txn|order|paid|money\s+(?:sent|received))\s+)?""" +
        """(successful(?:ly)?|success|completed?|failed|failure|declined|cancell?ed|expired|unsuccessful|rejected|""" +
        """pending|processing|in\s+progress)\b.{0,12}$""",
)

/** What a status word means: "Successful", "FAILED", "in progress". */
internal fun statusOf(word: String): SharedStatus {
    val w = word.lowercase()
    return when {
        w.startsWith("fail") || w == "declined" || w.startsWith("cancel") || w == "expired" || w == "unsuccessful" || w == "rejected" ->
            SharedStatus.Failed
        w == "pending" || w == "processing" || w.startsWith("in") -> SharedStatus.Pending
        else -> SharedStatus.Completed
    }
}

/** The status a receipt shows - "Transaction Successful", "Paid Successfully", "SUCCESSFUL" beside the amount - in any case. */
internal fun ReceiptLayout.status(): SharedStatus {
    for (row in rows) {
        val word = statusRow.matchEntire(row.text.trim())?.groupValues?.get(1)
            ?: row.cells.firstNotNullOfOrNull { statusRow.matchEntire(it.text.trim())?.groupValues?.get(1) }
        if (word != null) return statusOf(word)
    }
    return SharedStatus.Unknown
}

// --------------------------------------------------------------- reference

private val bankReferenceValue = Regex("""^[A-Za-z0-9]{6,35}\b""")
private val appReferenceValue = Regex("""^[A-Za-z0-9][A-Za-z0-9_\-]{5,47}$""")

/**
 * The payment's reference: the bank's (UTR, UPI Ref No, RRN) wherever it is labelled, or the one in a bank's narration;
 * the app's own id for it only when neither is shown.
 */
internal fun ReceiptLayout.reference(): String? {
    labelled(ReceiptWords.bankReference)?.let { found ->
        bankReferenceValue.find(valueOf(found))?.value?.takeIf { ref -> ref.count(Char::isDigit) >= 6 }?.let { return it }
    }
    narration()?.reference?.let { return it }
    return labelled(ReceiptWords.appReference)?.let { appReferenceValue.matchEntire(idOf(it).trim())?.value }
}

// ---------------------------------------------------------------- narration

/** What a bank's narration says: which way the money went, the other side's name, and the payment's reference. */
internal class Narration(val type: TransactionType?, val name: String?, val reference: String?)

/**
 * A UPI entry as banks write it: "UPI/DR/429876543210/NAME/SBIN/name@okaxis/note" - also as the recognizer reads it,
 * the I dropped and a slash read as one ("UP/DRIXXXXXXXX5817/NAME").
 */
private val upiNarration = Regex("""(?i)\bUP[I1l]?\s*/\s*(DR|CR|P2A|P2M|P2P)\s*[/I1l|]\s*([X*\d]{4,20})\s*/\s*([^/]{2,60}?)\s*(?:/|$)""")
private val narrationDebit = Regex("""(?i)\b(?:transfer\s+to|to\s+transfer|debit(?:ed)?|withdrawal|paid\s+to)\b""")
private val narrationCredit = Regex("""(?i)\b(?:transfer\s+from|by\s+transfer|credit(?:ed)?|deposit|received\s+from)\b""")

internal fun narrationOf(text: String): Narration? {
    val upi = upiNarration.find(text)
    val type = when (upi?.groupValues?.get(1)?.uppercase()) {
        "DR" -> TransactionType.Expense
        "CR" -> TransactionType.Income
        else -> when {
            narrationDebit.containsMatchIn(text) && !narrationCredit.containsMatchIn(text) -> TransactionType.Expense
            narrationCredit.containsMatchIn(text) && !narrationDebit.containsMatchIn(text) -> TransactionType.Income
            else -> null
        }
    }
    val reference = upi?.groupValues?.get(2)?.takeIf { ref -> ref.all(Char::isDigit) && ref.length >= 6 }
    val name = upi?.groupValues?.get(3)?.let(SharedTransactionParser::cleanName)
    if (type == null && name == null && reference == null) return null
    return Narration(type, name, reference)
}

/** How many rows below its label a narration runs on. */
private const val NARRATION_REACH = 3

/** The receipt's narration ([ReceiptWords.narration]) - run on over the rows below its label, up to the next label - read. */
internal fun ReceiptLayout.narration(): Narration? {
    val found = labelled(ReceiptWords.narration) ?: return null
    val more = rows.drop(found.at + 1).take(NARRATION_REACH).takeWhile { !it.isLabel() }.map { it.text.trim() }
    val text = (listOf(found.beside) + more).filter { it.isNotEmpty() }
        .fold("") { all, part -> if (all.isEmpty() || part.startsWith("/") || all.endsWith("/")) all + part else "$all $part" }
    return narrationOf(text)
}

// ------------------------------------------------------------------- names

/** A verified badge or an info icon after a name, read as a letter: "HDFC Bank Credit CardO", "Ravi Kumar ©". */
private val badgeAfter = Regex("""(?<=\p{Ll})[O0Q]$|\s+[O0©®✓]$""")

/**
 * [piece] as the other side's name, with a badge the recognizer read after it taken off, or null when it is no name
 * ([nameIn]). An avatar's letter in front of it ("G" in its circle, left of "Google") is a piece of its own already
 * ([splitAtGaps]).
 */
internal fun partyName(piece: String): String? = nameIn(piece.trim().replace(badgeAfter, "").trim())

private val ownAccountNoun = Regex("""(?i)\b(?:a/?c|acct|account|card|wallet)\b""")

/**
 * [piece] as the other side's name where a label says it is the other side ("Paid to HDFC Bank Credit Card"): there a
 * card or an account is the payee - a card's bill being paid - not the user's own, which the name is otherwise taken for.
 */
internal fun labelledPartyName(piece: String): String? {
    partyName(piece)?.let { return it }
    val name = piece.trim().replace(badgeAfter, "").trim()
    return name.takeIf { ownAccountNoun.containsMatchIn(it) && partyName(it.replace(ownAccountNoun, " ")) != null }
}

/** "HDFC BANK LTD", "XXXXXXXXXX3588", "State Bank Of India - 1234", "(HDFC Bank)": the user's own account, not a name. */
internal val ownAccountWords = Regex("""(?i)\bbank\b|\bltd\b|\ba/?c\b|\bacc(?:oun)?t\b|\bwallet\b|\bself\b|\byour\b|[x*•]{2,}\d|-\s*\d{4}\s*$""")
