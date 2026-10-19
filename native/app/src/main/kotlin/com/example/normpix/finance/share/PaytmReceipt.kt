package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate

/**
 * Paytm's payment-done screen, and the receipt picture its Share makes of it:
 *
 * ```
 *              Paid Successfully                  the status ("Money Received" when it came in)
 *                 ₹74  (✓)                        the amount, large, with a green tick beside it
 *          Rupees Seventy Four Only               the amount in words: a second copy of it
 *   (💬)  Auto                                    what the payer wrote with the payment, if anything
 *   To: Mantu Yadav                               the other side...
 *       UPI ID: paytm.s2f0czp@pty                 ...and their UPI id
 *   From: Kusumanchi Sai                          the user...
 *       UPI ID: ******0221@ptyes                  ...their UPI id, partly hidden: a handle, not an account number
 *       HDFC Bank - 3588                          their bank, and the last four digits of the account
 *   UPI Ref No: 611557718602                      the bank's reference
 *   02:25 PM, 04 Aug 2026                         when
 *   paytm | Powered by UPI | YES BANK             Paytm's own, and its bank: not the user's
 * ```
 *
 * The user's side is the one that shows a bank over its last digits - the "From" of a payment, the "To" of money received -
 * and the other side is the one with a name only. The amount is read from the figure and from the words under it (see
 * [settle]): Paytm puts a ₹ before it, which the recognizer often reads as a 7.
 */
internal object PaytmReceipt : PaymentAppReceipt {
    override val name = "Paytm"

    private val paidTitle = Regex("""(?i)^\W*(?:[\p{L}\d]{1,2}\s+)?(?:paid|money\s+sent|payment\s+sent)(?:\s+successfully)?\W*$""")
    private val receivedTitle = Regex("""(?i)^\W*(?:[\p{L}\d]{1,2}\s+)?(?:money\s+|payment\s+)?received(?:\s+successfully)?\W*$""")
    private val toColon = Label("to(?=\\s*:)")
    private val fromColon = Label("from(?=\\s*:)")
    private val referenceLabel = Regex("""(?i)^\W*upi\s*ref\.?\s*no\b""")
    private val footer = Regex("""(?i)\bpowered\s+by\b""")

    /** The words a row says for a bank, a card or a wallet: a row of the user's own account, when it has no UPI id. */
    private val accountNoun = Regex("""(?i)\b(?:bank|card|wallet)\b""")

    /** How many rows below a "To:" or "From:" belong to that side: the name's UPI id, a bank over its digits. */
    private const val SIDE_REACH = 4

    /** Most rows of the payer's message, which runs on to a second line. */
    private const val MESSAGE_ROWS = 2

    override val tells = listOf(
        Tell.brand(2, "paytm"),
        Tell.piece(1, paidTitle),
        Tell.piece(1, receivedTitle),
        Tell.row(1, referenceLabel),
    )

    /** One side of the payment: its label, and the rows under it down to the next label, the date or the footer. */
    private class Side(val label: Labelled, val rows: List<ReceiptRow>) {
        /** Everything written on that side, the text beside the label first. */
        val lines: List<String> = (listOf(label.beside) + rows.map { it.text }).map(String::trim).filter { it.isNotEmpty() }

        /** The lines that are the user's account: a bank over its last digits, a card's number partly hidden. Never a UPI id. */
        val account: List<String> = lines.filter { '@' !in it && (SharedTransactionParser.maskedNumbers(it).isNotEmpty() || accountNoun.containsMatchIn(it)) }

        /** The side's name: beside its label or on the row below; never an account line or a UPI id. */
        val person: String? = (listOf(label.beside) + rows.map { it.text }).firstNotNullOfOrNull { text ->
            text.trim().takeIf { it !in account }?.let(::partyName)
        }
    }

    /** [side]'s name, with the next line when the receipt ran a long one on to it (see [runOn]). */
    private fun nameOf(receipt: ReceiptLayout, side: Side?): String? {
        val name = side?.person ?: return null
        val beside = side.label.besideCell
        return if (beside != null && partyName(side.label.beside) == name) receipt.runOn(name, beside, side.rows.take(1)) else name
    }

    private fun sideOf(receipt: ReceiptLayout, found: Labelled?, today: LocalDate): Side? {
        found ?: return null
        val rows = receipt.rows.drop(found.at + 1).take(SIDE_REACH)
            .takeWhile { !it.isLabel() && !footer.containsMatchIn(it.text) && SharedTransactionParser.dateIn(it.text, today).date == null }
        return Side(found, rows)
    }

    override fun read(receipt: ReceiptLayout, ledger: CurrencySpec, today: LocalDate): ParsedTransactionDraft {
        val rows = receipt.rows
        // "To:" and "From:" with their colon - a payer's message that begins with "To" is not one; without it, as the recognizer may drop it.
        val to = sideOf(receipt, receipt.labelled(toColon) ?: receipt.labelled(ReceiptWords.to), today)
        val from = sideOf(receipt, receipt.labelled(fromColon) ?: receipt.labelled(ReceiptWords.from), today)

        // Which way the money went: the title says it; else the side that shows a bank over its digits is the user's.
        val paid = rows.any { it.has(paidTitle) }
        val received = rows.any { it.has(receivedTitle) }
        val type = when {
            paid && !received -> TransactionType.Expense
            received && !paid -> TransactionType.Income
            from?.account?.isNotEmpty() == true && to?.account.isNullOrEmpty() -> TransactionType.Expense
            to?.account?.isNotEmpty() == true && from?.account.isNullOrEmpty() -> TransactionType.Income
            else -> null
        }
        val own = if (type == TransactionType.Income) to else from
        val other = if (type == TransactionType.Income) from else to

        val accountText = own?.account?.joinToString("\n").orEmpty()
        val masks = SharedTransactionParser.maskedNumbers(accountText)

        // The amount: the first figure above the sides, and the amount in words under it.
        val firstSide = listOfNotNull(to?.label?.at, from?.label?.at).minOrNull() ?: rows.size
        val amountAt = rows.take(firstSide).indexOfFirst { row -> row.cells.any { amountSighting(it.text) != null } }
        val printed = rows.getOrNull(amountAt)?.cells?.firstNotNullOfOrNull { amountSighting(it.text) }
            ?: receipt.headline?.let(::amountSighting)
        val amount = receipt.settleAmount(listOfNotNull(printed), ledger.decimals, rupeePrinted = true)

        val date = SharedTransactionParser.dateIn(receipt.text, today)
        var status = receipt.status()
        if (status == SharedStatus.Unknown && (paid || received)) status = SharedStatus.Completed
        return ParsedTransactionDraft(
            amount = amount.amount,
            ambiguousAmounts = amount.ambiguous,
            type = type,
            kind = kindOf(type),
            status = status,
            counterparty = nameOf(receipt, other)?.let(::tidyName),
            date = date.date,
            time = date.time,
            dateUnreadable = date.unreadable,
            reference = receipt.reference(),
            instrument = SharedTransactionParser.instrument(accountText, masks),
            maskedNumbers = masks,
            accountText = accountText,
            message = messageBelow(receipt, firstSide, ledger),
        )
    }

    /**
     * What the payer wrote: the row(s) between the status, the amount and the words under it - whichever of them is read, the
     * last of them - and the first side. The speech-bubble icon before it is read as a letter or a mark, so a piece of fewer
     * than two letters is not part of it.
     */
    private fun messageBelow(receipt: ReceiptLayout, firstSide: Int, ledger: CurrencySpec): String? {
        val head = receipt.rows.take(firstSide)
        fun isAmount(row: ReceiptRow) = row.cells.any { amountSighting(it.text) != null } || AmountWords.parse(row.text, ledger.decimals) != null ||
            inWordsShape.containsMatchIn(row.text)
        fun isStatus(row: ReceiptRow) = row.has(paidTitle) || row.has(receivedTitle) || ReceiptLayout(listOf(row)).status() != SharedStatus.Unknown
        val anchor = head.indexOfLast { isAmount(it) || isStatus(it) }
        if (anchor < 0) return null
        val text = head.drop(anchor + 1).take(MESSAGE_ROWS).mapNotNull { row ->
            row.cells.map { bubbleIcon.replaceFirst(it.text.trim(), "") }.filter { it.count(Char::isLetterOrDigit) >= 2 }
                .joinToString(" ").takeIf { it.isNotEmpty() }
        }.joinToString(" ")
        return text.takeIf { it.length in 2..MAX_MESSAGE && '@' !in it }
    }

    private const val MAX_MESSAGE = 60

    /**
     * The speech-bubble icon when the recognizer ran it into the message's cell: one letter or mark, then a space ("Q Auto").
     * "A" and "I" are left alone, as they can be the message's own first word ("A gift").
     */
    private val bubbleIcon = Regex("""^(?![AI]\s)[^\s\d]\s+(?=\S)""")

    /** The amount in words as the row says it, however the recognizer spelled the words: not the payer's message. */
    private val inWordsShape = Regex("""(?i)^\W*rupees?\b|\bonly\W*$""")
}
