package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate

/**
 * Google Pay's transaction screen, and the receipt picture its Share makes of it:
 *
 * ```
 *                  (U)
 *   To UBER INDIA SYSTEMS PRIVATE LIMITED          or "From Rahul Sharma": who, and which way
 *                 ₹30                              the amount, far larger than anything else
 *              Completed                           the status
 *        12 Oct 2026, 8:55 pm                      when
 * [logo] HDFC Bank 3588                            the user's own account: its bank and last digits
 *        (or "HDFC Bank" over "652850XXXXXXXX55", a RuPay card's first six and last two)
 * Payment of ₹30 completed                         the amount again
 * Receiver's bank has confirmed deposit of money to UBER…'s bank account
 * UPI transaction ID  131182715090                 the bank's reference
 * To: UBER INDIA SYSTEMS PRIVATE LIMITED  ••••1ewy@rapl on Amazon Pay
 * From: KUSUMANCHI SAI (HDFC Bank)  ••••10-3@okhdfcbank on Google Pay      the user's side names their bank
 * Google transaction ID  CICAgLjm5MSxWQ
 *                G Pay
 * ```
 *
 * Its Share also names the picture's file after the payment: "1791818718 - 30.00 To UBER INDIA SYSTEMS PRIVATE LIMITED
 * on Google Pay.png". That name is the app's own text, not something read off the picture, so where it is known it
 * settles the amount, the other side and the direction.
 */
internal object GooglePayReceipt : PaymentAppReceipt {
    override val name = "Google Pay"

    private val googleId = Regex("""(?i)\bgoogle\s+transaction\s+id\b""")
    private val onGooglePay = Regex("""(?i)\bon\s+google\s+pay\b""")
    private val logo = Regex("""(?i)^\W*(?:g\s?pay|google\s+pay)\W*$""")
    private val upiIdLabel = Regex("""(?i)\bupi\s+(?:transaction|txn|ref(?:erence)?)\s*(?:id|no\.?|number)?\b\W*(\d{10,16})?""")
    private val lonePaymentReference = Regex("""^\d{10,16}$""")

    /** The title: "To <payee>" (paid) or "From <payer>" (received). A colon makes it a detail row instead. */
    private val titleTo = Regex("""(?i)^(?:paid\s+)?to\s+([^:\s].*)$""")
    private val titleFrom = Regex("""(?i)^(?:received\s+)?from\s+([^:\s].*)$""")
    private val detailTo = Regex("""(?i)^\W*to\s*:\s*(.+)$""")
    private val detailFrom = Regex("""(?i)^\W*from\s*:\s*(.+)$""")

    /** "(HDFC Bank)" at the end of a detail row: that side is the user's own account. */
    private val ownBank = Regex("""\(([^()]*\bbank\b[^()]*)\)\s*$""", RegexOption.IGNORE_CASE)

    /** "Payment of ₹30 completed": the amount once more. The bell icon in front can come out as a letter. */
    private val sentence = Regex(
        """(?i)\bpayment\s+of\s+(.+?)\s+(completed|successful|received|failed|declined|pending|processing|is\s+pending)\b""",
    )
    private val statusWord = Regex(
        """(?i)^\W*(completed|successful|success|failed|declined|cancell?ed|expired|pending|processing|in\s+progress)\W*$""",
    )

    /** "HDFC Bank 3588": a bank's name, then its account's last digits. */
    private val bankAndEnding = Regex("""(?i)\bbank\b\D*?(\d{4})$""")

    /** The name its Share gives the picture's file: "<id> - 30.00 To <payee> on Google Pay.png". */
    private val fileName = Regex(
        """^\d+\s*-\s*(\d[\d,]*(?:\.\d{1,2})?)\s+(to|from)\s+(.+?)\s+on\s+google\s+pay(?:\.\w{2,4})?$""",
        RegexOption.IGNORE_CASE,
    )

    /** How many rows below the title its amount is looked for, when no figure stands out by size. */
    private const val AMOUNT_REACH = 3

    override val tells = listOf(
        Tell.row(2, googleId),
        Tell.row(2, onGooglePay),
        Tell.piece(2, logo),
        Tell(3) { receipt -> receipt.name?.let { fileName.matches(it.trim()) } == true },
        Tell.row(1, upiIdLabel),
    )

    override fun read(receipt: ReceiptLayout, ledger: CurrencySpec, today: LocalDate): ParsedTransactionDraft {
        val rows = receipt.rows
        val file = receipt.name?.let { fileName.matchEntire(it.trim()) }

        // The title, above everything else: "To <payee>", or "To" on a line of its own over the name.
        var titleAt = -1
        var titleType: TransactionType? = null
        var titleName: String? = null
        for ((i, row) in rows.withIndex()) {
            val text = row.text.trim()
            val to = titleTo.matchEntire(text)
            val from = titleFrom.matchEntire(text)
            val bare = text.lowercase().removePrefix("paid ").removePrefix("received ")
            val name = (to ?: from)?.groupValues?.get(1)?.let(::nameIn)
                ?: rows.getOrNull(i + 1)?.text?.let(::nameIn)?.takeIf { bare == "to" || bare == "from" }
            if (name != null) {
                titleAt = i
                titleType = if (to != null || bare == "to") TransactionType.Expense else TransactionType.Income
                titleName = name
                break
            }
        }

        // The detail rows name both sides; the user's own side names its bank.
        val toRow = receipt.indexOf { detailTo.matches(it.text) }
        val fromRow = receipt.indexOf { detailFrom.matches(it.text) }
        val toDetail = rows.getOrNull(toRow)?.text?.let { detailTo.matchEntire(it)?.groupValues?.get(1) }
        val fromDetail = rows.getOrNull(fromRow)?.text?.let { detailFrom.matchEntire(it)?.groupValues?.get(1) }
        val detailType = when {
            fromDetail != null && ownBank.containsMatchIn(fromDetail) && toDetail?.let(ownBank::containsMatchIn) != true -> TransactionType.Expense
            toDetail != null && ownBank.containsMatchIn(toDetail) && fromDetail?.let(ownBank::containsMatchIn) != true -> TransactionType.Income
            else -> null
        }
        val fileType = file?.groupValues?.get(2)?.let { if (it.equals("to", ignoreCase = true)) TransactionType.Expense else TransactionType.Income }
        val type = fileType ?: titleType ?: detailType
        val otherDetail = if (type == TransactionType.Income) fromDetail else toDetail
        val party = file?.groupValues?.get(3)?.let(::nameIn) ?: titleName ?: otherDetail?.replace(ownBank, "")?.let(::nameIn)

        // The amount: the figure printed largest (or the first figure under the title), and the sentence repeating it.
        val sentenceAt = receipt.indexOf { sentence.containsMatchIn(it.text) }
        val printed = receipt.headline?.let(::amountSighting)
            ?: rows.drop(titleAt + 1).take(AMOUNT_REACH).firstNotNullOfOrNull { row -> row.cells.singleOrNull()?.text?.let(::amountSighting) }
        val repeated = rows.getOrNull(sentenceAt)?.text?.let { sentence.find(it)?.groupValues?.get(1) }?.let(::amountSighting)
        val fileAmount = file?.groupValues?.get(1)?.let(::amountSighting)
        // The file's name is the app's own text: what it says is the amount, whatever the picture was read as.
        val amount = if (fileAmount != null) {
            SettledAmount(fileAmount.minor(ledger.decimals))
        } else {
            receipt.settleAmount(listOfNotNull(printed, repeated), ledger.decimals)
        }

        // The user's account: the card below the date, above the sentence and the details.
        val details = listOf(sentenceAt, receipt.indexOf { upiIdLabel.containsMatchIn(it.text) }, toRow, fromRow).filter { it >= 0 }
        val end = details.minOrNull() ?: rows.size
        val dateRow = receipt.indexOf { SharedTransactionParser.dateIn(it.text, today).date != null }.takeIf { it in 0 until end }
        val start = maxOf(titleAt, dateRow ?: -1) + 1
        val ownRows = if (start < end) rows.subList(start, end).filterNot { statusWord.matches(it.text) } else emptyList()
        val ownDetail = (if (type == TransactionType.Income) toDetail else fromDetail)?.let { ownBank.find(it)?.groupValues?.get(1) }
        val accountText = (ownRows.map { it.text } + listOfNotNull(ownDetail)).joinToString("\n")
        val shown = ownRows.joinToString("\n") { it.text }
        val masks = SharedTransactionParser.maskedNumbers(shown).ifEmpty {
            ownRows.mapNotNull { row -> bankAndEnding.find(row.text)?.groupValues?.get(1) }.distinct().map { MaskedNumber("", it) }
        }

        val date = SharedTransactionParser.dateIn(receipt.text, today)
        return ParsedTransactionDraft(
            amount = amount.amount,
            ambiguousAmounts = amount.ambiguous,
            type = type,
            kind = kindOf(type),
            status = statusOf(rows, rows.getOrNull(sentenceAt)?.text),
            counterparty = party,
            date = date.date,
            time = date.time,
            dateUnreadable = date.unreadable,
            reference = reference(receipt),
            instrument = SharedTransactionParser.instrument(shown, masks),
            maskedNumbers = masks,
            accountText = accountText,
        )
    }

    private fun statusOf(rows: List<ReceiptRow>, sentenceRow: String?): SharedStatus {
        val word = rows.firstNotNullOfOrNull { statusWord.matchEntire(it.text)?.groupValues?.get(1) }
            ?: sentenceRow?.let { sentence.find(it)?.groupValues?.get(2) }
            ?: return SharedStatus.Unknown
        val w = word.lowercase()
        return when {
            w.startsWith("fail") || w == "declined" || w.startsWith("cancel") || w == "expired" -> SharedStatus.Failed
            w == "pending" || w == "processing" || w.startsWith("in") || w.startsWith("is") -> SharedStatus.Pending
            else -> SharedStatus.Completed
        }
    }

    /** The UPI transaction id - the bank's reference - beside its label or below it; Google's own id only when there is none. */
    private fun reference(receipt: ReceiptLayout): String? {
        val at = receipt.indexOf { upiIdLabel.containsMatchIn(it.text) }
        if (at >= 0) {
            upiIdLabel.find(receipt.rows[at].text)?.groups?.get(1)?.value?.let { return it }
            receipt.rows.getOrNull(at + 1)?.cells?.firstOrNull()?.text?.trim()?.takeIf { lonePaymentReference.matches(it) }?.let { return it }
            // Read out of place, with its places unknown: the one number of a reference's length is the reference.
            receipt.rows.mapNotNull { row -> row.cells.singleOrNull()?.text?.trim()?.takeIf { it.length == 12 && lonePaymentReference.matches(it) } }
                .distinct().singleOrNull()?.let { return it }
        }
        val google = receipt.indexOf { googleId.containsMatchIn(it.text) }
        return receipt.rows.getOrNull(google + 1)?.cells?.firstOrNull()?.text?.trim()
            ?.takeIf { google >= 0 && Regex("""^[A-Za-z0-9]{8,35}$""").matches(it) }
    }
}
