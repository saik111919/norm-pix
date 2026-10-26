package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate

/**
 * A payment app's receipt read by the labels it prints ([ReceiptWords]) - "Paid to", "Debited from", "UPI Ref No",
 * "Date & Time" - beside or above their values, wherever on the receipt they are: the reader for every app whose layout
 * is the common one. The apps differ only in the [tells] that say a receipt is theirs; one that names the other side
 * without a label gives the sentences it names them in ([nameSentences]: CRED's "send money to <name> instantly").
 */
internal class LabelledReceipt(
    override val name: String,
    override val tells: List<Tell>,
    private val nameSentences: List<Regex> = emptyList(),
) : PaymentAppReceipt {
    override fun read(receipt: ReceiptLayout, ledger: CurrencySpec, today: LocalDate): ParsedTransactionDraft {
        val words = ReceiptWords
        val paidTo = receipt.labelled(words.paidTo)
        val receivedFrom = receipt.labelled(words.receivedFrom)
        val debited = receipt.labelled(words.debitedFrom)
        val credited = receipt.labelled(words.creditedTo)
        val to = receipt.labelled(words.to)
        val from = receipt.labelled(words.from)
        val narration = receipt.narration()

        // The other side, and so which way the money went: "Paid to" / "Received from" say both, whichever comes first...
        val payee = paidTo?.let { receipt.partyAt(it) }
        val payer = receivedFrom?.let { receipt.partyAt(it) }
        var type: TransactionType? = null
        var partyLabel: Labelled? = null
        when {
            payee != null && (payer == null || paidTo.at <= receivedFrom.at) -> {
                type = TransactionType.Expense
                partyLabel = paidTo
            }
            payer != null -> {
                type = TransactionType.Income
                partyLabel = receivedFrom
            }
        }
        var party = if (type == TransactionType.Expense) payee else payer
        // ...a bare "To" / "From" only where the other one is the user's own account (a bank and its last digits: "From: State Bank Of India - 1234").
        val toIsOwn = to?.let { ownAccountWords.containsMatchIn(receipt.valueOf(it)) } == true
        val fromIsOwn = from?.let { ownAccountWords.containsMatchIn(receipt.valueOf(it)) } == true
        if (type == null) {
            val toParty = to?.takeIf { !toIsOwn }?.let { receipt.partyAt(it) }
            val fromParty = from?.takeIf { !fromIsOwn }?.let { receipt.partyAt(it) }
            if (toParty != null && fromParty == null) {
                type = TransactionType.Expense
                party = toParty
                partyLabel = to
            } else if (fromParty != null && toParty == null) {
                type = TransactionType.Income
                party = fromParty
                partyLabel = from
            }
        }
        // Then the label of the user's own account, the bank's narration, the title.
        type = type ?: when {
            debited != null -> TransactionType.Expense
            credited != null -> TransactionType.Income
            else -> narration?.type ?: receipt.titleType()
        }
        party = party ?: narration?.name ?: nameInSentences(receipt)

        // The user's own account: beside its label - unless that names this app ("Paid via CRED") - or the rows that show
        // a number partly hidden, with the bank above them.
        val ownLabel = if (type == TransactionType.Income) credited ?: debited else debited ?: credited
        val shown = listOfNotNull(
            ownLabel?.let(receipt::valueOf)?.takeUnless { it.isBlank() || it.equals(name, ignoreCase = true) },
            when (type) {
                TransactionType.Expense -> from?.takeIf { fromIsOwn }?.let(receipt::valueOf)
                TransactionType.Income -> to?.takeIf { toIsOwn }?.let(receipt::valueOf)
                else -> null
            },
            receipt.labelled(words.accountNumber)?.let(receipt::valueOf),
        )
        val ownRows = if (shown.any { SharedTransactionParser.maskedNumbers(it).isNotEmpty() }) emptyList() else receipt.ownAccountRows(partyLabel?.at)
        val accountText = (shown + ownRows).distinct().joinToString("\n").ifEmpty { null }
        val masks = accountText?.let(SharedTransactionParser::maskedNumbers).orEmpty()

        // The other side's account, where the receipt shows one: just below their name, a bare "To" (or "From") that names an
        // account rather than a person, and - of money paid - a row about where it went ("TRANSFER TO XXXX2094").
        val otherLines = buildList {
            partyLabel?.let { found -> receipt.rows.drop(found.at + 1).take(2).forEach { add(it.text) } }
            when (type) {
                TransactionType.Expense -> {
                    to?.takeIf { toIsOwn }?.let { add(receipt.valueOf(it)) }
                    receipt.rows.filter { otherSideRow.containsMatchIn(it.text) && SharedTransactionParser.maskedNumbers(it.text).isNotEmpty() }
                        .forEach { add(it.text) }
                }
                TransactionType.Income -> from?.takeIf { fromIsOwn }?.let { add(receipt.valueOf(it)) }
                else -> Unit
            }
        }.distinct().filterNot { it in shown }

        // The amount: the figure printed largest and the one labelled "Amount"; without either, every figure with a ₹ in front.
        val sightings = buildList {
            receipt.headline?.let(::amountSighting)?.let(::add)
            receipt.labelled(words.amount)?.let { amountSighting(rupeeInFront(receipt.valueOf(it))) }?.let(::add)
            if (isEmpty()) addAll(receipt.markedFigures())
        }
        val amount = receipt.settleAmount(sightings, ledger.decimals)

        val date = receipt.labelled(words.date)?.let { SharedTransactionParser.dateIn(receipt.valueOf(it), today) }
            ?.takeIf { it.date != null || it.unreadable }
            ?: SharedTransactionParser.dateIn(receipt.text, today)
        return ParsedTransactionDraft(
            amount = amount.amount,
            ambiguousAmounts = amount.ambiguous,
            type = type,
            kind = kindOf(type),
            status = receipt.status(),
            counterparty = party,
            date = date.date,
            time = date.time,
            dateUnreadable = date.unreadable,
            reference = receipt.reference(),
            instrument = accountText?.let { SharedTransactionParser.instrument(it, masks) },
            maskedNumbers = masks,
            accountText = accountText,
            otherAccountText = otherLines.joinToString("\n").ifEmpty { null },
            otherMaskedNumbers = otherSideNumbers(otherLines).filterNot { it in masks },
        )
    }

    private fun nameInSentences(receipt: ReceiptLayout): String? {
        for (pattern in nameSentences) {
            for (row in receipt.rows) pattern.find(row.text)?.groupValues?.get(1)?.let(::partyName)?.let { return it }
        }
        return null
    }

    private companion object {
        /** How many rows below a bare "Paid to" its name is looked for: the status and the time can come between, read out of place. */
        const val PARTY_REACH = 3

        /** "Suman Sharma ₹10": the amount printed at the end of the name's row. */
        val amountAfter = Regex("""\s+(?:₹|rs\.?|inr)\s*\d[\d,]*(?:\.\d{1,2})?\s*$""", RegexOption.IGNORE_CASE)

        /** The other side's name at [found]: beside its label, or on the rows below it (past a status, the time, an id). */
        fun ReceiptLayout.partyAt(found: Labelled): String? =
            if (found.beside.isNotEmpty()) {
                labelledPartyName(found.beside.replace(amountAfter, ""))
            } else {
                nameBelow(found.at, PARTY_REACH) { it.isLabel() }.name
            }

        val paidTitle = Regex("""(?i)^\W*(?:paid|sent|money\s+sent|payment\s+sent|debited)(?:\s+successfully)?\W*$""")
        val receivedTitle = Regex("""(?i)^\W*(?:received|money\s+received|payment\s+received|credited)(?:\s+successfully)?\W*$""")

        /** Which way the money went, as the receipt's title says it: "Paid Successfully", "Money Received". */
        fun ReceiptLayout.titleType(): TransactionType? = when {
            rows.any { it.has(paidTitle) } -> TransactionType.Expense
            rows.any { it.has(receivedTitle) } -> TransactionType.Income
            else -> null
        }

        /** What isn't the payment's amount: a balance, a reward, a fee, a limit. */
        val notTheAmount = Regex("""(?i)\b(?:bal(?:ance)?|cashback|reward|discount|off|saved?|savings|fee|charges?|limit|due|offer)\b""")

        /** Every piece that is a figure behind a ₹ (or what the recognizer made of one), on a row that isn't about another amount. */
        fun ReceiptLayout.markedFigures(): List<AmountSighting> = rows.filterNot { notTheAmount.containsMatchIn(it.text) }
            .flatMap { row -> row.cells.mapNotNull { amountSighting(it.text)?.takeIf { s -> s.marked } } }

        /** "z 25.00": a ₹ read as a letter, apart from its figure. Only beside an "Amount" label is that letter a ₹. */
        fun rupeeInFront(value: String) = value.replace(Regex("""^(?:[zZ]|rs\.?|inr)\s+(?=\d)""", RegexOption.IGNORE_CASE), "₹")

        /** Rows about the other side's account, not the user's: "TRANSFER TO XXXX2094", "Card Number XXXX-7755". */
        val otherSideRow = Regex("""(?i)\bto\b|\bbeneficiary\b|\bpayee\b|\brecipient\b|\breceiver\b|card\s+number""")
        val bankName = Regex("""(?i)\bbank\b""")

        /**
         * The rows that show one of the user's numbers partly hidden - not the other side's, just below their name, or on a
         * row about them - each with the bank's name when that is on the row above.
         */
        fun ReceiptLayout.ownAccountRows(partyAt: Int?): List<String> = buildList {
            for ((i, row) in rows.withIndex()) {
                if (partyAt != null && i in partyAt + 1..partyAt + 2) continue
                if (SharedTransactionParser.maskedNumbers(row.text).isEmpty() || otherSideRow.containsMatchIn(row.text)) continue
                rows.getOrNull(i - 1)?.text?.takeIf { bankName.containsMatchIn(it) && !otherSideRow.containsMatchIn(it) }?.let(::add)
                add(row.text)
            }
        }
    }
}

/**
 * CRED's payment screen: the other side named at the top with no label (and again in its sentences), the amount with
 * "SUCCESSFUL" beside it, "Paid via CRED", then the user's bank over their account's last digits.
 */
internal val CredReceipt = LabelledReceipt(
    name = "CRED",
    tells = listOf(
        Tell.row(2, Regex("""(?i)\bpaid\s+via\s+cred\b""")),
        Tell.row(2, Regex("""(?i)\bcred\s+transaction\s+id\b""")),
        Tell.row(1, Regex("""(?i)^\W*pay(?:ing)?\s+(?:them\s+)?again\b""")),
    ),
    nameSentences = listOf(
        Regex("""(?i)\bsend\s+money\s+to\s+(.+?)\s+instantly\b"""),
        Regex("""(?i)\bbank\s+details\s+of\s+(.+?)\s+have\s+been\b"""),
    ),
)

/** HDFC Bank's PayZapp: "Paid to <payee>" under the amount, "Transaction ID  txn_…", "Paid using <the user's bank> UPI". */
internal val PayZappReceipt = LabelledReceipt(
    name = "PayZapp",
    tells = listOf(
        // The logo, read in two pieces: "EPAY APP".
        Tell.row(2, Regex("""(?i)pay\s?z?\s?app\b""")),
        Tell.piece(2, Regex("""^txn_[0-9a-f]{6}.*""")),
        Tell.row(1, Regex("""(?i)^\W*paid\s+using\b""")),
    ),
)

/** SBI's transaction details: a table of "Account Number:", "Amount:", "Transaction Date:", then the narration. */
internal val SbiReceipt = LabelledReceipt(
    name = "SBI",
    tells = listOf(
        // The logo: "SBI", its keyhole read as an O.
        Tell.piece(2, Regex("""(?i)^\W*o?sbi\W*$""")),
        Tell.row(1, Regex("""(?i)^\W*narration\b""")),
        Tell.row(1, Regex("""(?i)^\W*mode\s+of\s+transfer\b""")),
        Tell.row(1, Regex("""(?i)^\W*account\s+number\b""")),
    ),
)

/**
 * India's other UPI apps, by their share of payments (NPCI, 2026): ones NormPix has no real receipt of yet. Each is known
 * by its own name or logo on the receipt, and read by the labels every app uses; when that finds neither an amount nor a
 * name, the general rules read it ([PaymentAppReceipts.read]). One with a layout of its own gets a reader of its own once
 * a real receipt of it is seen.
 */
internal object BrandedReceipts {
    val all = listOf(
        LabelledReceipt("Navi", listOf(Tell.piece(2, Regex("""(?i)^\W*navi\W*$""")))),
        LabelledReceipt("super.money", listOf(Tell.brand(2, """super\.?\s?money"""))),
        LabelledReceipt("BHIM", listOf(Tell.brand(2, "bhim"))),
        LabelledReceipt("FamPay", listOf(Tell.brand(2, """fam\s?(?:pay|app)"""))),
        LabelledReceipt("WhatsApp", listOf(Tell.brand(2, """whats\s?app"""))),
        LabelledReceipt("Amazon Pay", listOf(Tell.brand(2, """amazon\s?pay"""))),
        LabelledReceipt("MobiKwik", listOf(Tell.brand(2, """mobi\s?kwik"""))),
        LabelledReceipt("Freecharge", listOf(Tell.brand(2, """free\s?charge"""))),
        LabelledReceipt("Airtel Thanks", listOf(Tell.brand(2, """airtel\s+(?:thanks|payments\s+bank)"""))),
        LabelledReceipt("JioFinance", listOf(Tell.brand(2, """jio\s?finance"""))),
        LabelledReceipt("iMobile Pay", listOf(Tell.brand(2, """imobile(?:\s+pay)?"""))),
        LabelledReceipt("YONO SBI", listOf(Tell.brand(2, "yono"))),
    )
}
