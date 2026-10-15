package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate

/**
 * A mutual fund order, as investment apps show it (Groww's "Order details"; Zerodha Coin, Paytm Money and the like word
 * theirs much the same):
 *
 * ```
 * Order details
 * ₹500.00                                       the amount, printed largest
 * New SIP • Completed                           what kind of order, and its status
 * Abakkus Small Cap Fund Direct Growth >        the fund: where the money went
 * Completed on  14 Oct '26      NAV date  12 Oct '26
 * Status
 *   Order approved by exchange   08 Oct '26, 10:37 AM
 *   Auto-payment confirmed       12 Oct '26, 12:00 AM      when the money left the account
 *   Units allocated              14 Oct '26, 1:28 PM
 * Placed On  08 Oct '26 10:37 AM
 * Paid via   HDFC BANK XX                       the user's own account
 * Folio no   …
 * [One-time]  [Start SIP]
 * ```
 *
 * A purchase (a SIP instalment, a one-time investment) is an expense from the account it was paid from; a redemption is
 * income. Its date is the day the payment went through - the bank's entry for it falls then - not the day the order was
 * placed or the units came.
 */
internal object MutualFundOrderReceipt : PaymentAppReceipt {
    override val name = "mutual fund"

    private val navDate = Regex("""(?i)\bnav\s+date\b""")
    private val folio = Regex("""(?i)\bfolio\b""")
    private val units = Regex("""(?i)\bunits?\s+(?:allocated|allotted|allotment|credited)\b""")
    private val sip = Regex("""(?i)\bsip\b""")
    private val exchange = Regex("""(?i)\border\s+(?:approved|placed|sent)\b|\border\s+details\b""")

    override val tells = listOf(Tell.row(2, navDate), Tell.row(1, folio), Tell.row(1, units), Tell.row(1, sip), Tell.row(1, exchange))

    /** The fund: a name with "Fund" in it, perhaps with the arrow that opens it. */
    private val fund = Regex("""(?i)\bfund\b""")
    private val opener = Regex("""[\s>›»]+$""")

    private val redemption = Regex("""(?i)\b(?:redeem(?:ed)?|redemption|withdraw(?:al|n)?|sell|sold|switch\s+out)\b""")

    /** "New SIP • Completed": the order's status, after its kind. */
    private val orderStatus = Regex("""(?i)\b(completed?|successful|success|failed|cancell?ed|rejected|pending|processing|in\s+progress)\b""")

    /** The step at which the money moved. */
    private val paymentStep = Regex("""(?i)\b(?:auto-?\s*)?payment\s+(?:confirmed|successful|received|done|completed|made)\b""")
    private val placedOn = Label("""placed\s+on|order\s+date|order\s+placed\s+on""")

    override fun read(receipt: ReceiptLayout, ledger: CurrencySpec, today: LocalDate): ParsedTransactionDraft {
        val rows = receipt.rows
        val fundAt = receipt.indexOf { row -> fund.containsMatchIn(row.text) && !navDate.containsMatchIn(row.text) }
        val fundName = rows.getOrNull(fundAt)?.text?.replace(opener, "")?.let(SharedTransactionParser::cleanName)
        // What kind of order it is, said above the fund (the buttons at the bottom offer to invest more on every order).
        val heading = if (fundAt >= 0) rows.subList(0, fundAt) else rows.take(HEADING_ROWS)
        val type = if (heading.any { redemption.containsMatchIn(it.text) }) TransactionType.Income else TransactionType.Expense

        val printed = receipt.headline?.let(::amountSighting)
            ?: heading.flatMap { it.cells }.firstNotNullOfOrNull { cell -> amountSighting(cell.text)?.takeIf { it.marked } }
        val amount = settle(listOfNotNull(printed), ledger.decimals)

        val status = heading.firstNotNullOfOrNull { orderStatus.find(it.text)?.groupValues?.get(1) }?.let(::statusOf) ?: SharedStatus.Unknown

        // When the money moved: the payment's step, or else when the order was placed.
        val paid = receipt.indexOf { paymentStep.containsMatchIn(it.text) }
        val stepDate = if (paid >= 0) {
            listOfNotNull(rows[paid], rows.getOrNull(paid + 1))
                .map { SharedTransactionParser.dateIn(it.text, today) }.firstOrNull { it.date != null }
        } else {
            null
        }
        val date = stepDate
            ?: receipt.labelled(placedOn)?.let { SharedTransactionParser.dateIn(receipt.valueOf(it), today) }?.takeIf { it.date != null }
            ?: SharedTransactionParser.dateIn(receipt.text, today)

        val accountText = receipt.labelled(ReceiptWords.debitedFrom)?.let(receipt::valueOf)
        val masks = accountText?.let(SharedTransactionParser::maskedNumbers).orEmpty()
        return ParsedTransactionDraft(
            amount = amount.amount,
            ambiguousAmounts = amount.ambiguous,
            type = type,
            kind = kindOf(type),
            status = status,
            counterparty = fundName,
            date = date.date,
            time = date.time,
            dateUnreadable = date.unreadable,
            reference = receipt.reference(),
            instrument = accountText?.let { SharedTransactionParser.instrument(it, masks) },
            maskedNumbers = masks,
            accountText = accountText,
        )
    }

    /** Where no fund is named, how many rows from the top say what kind of order it is. */
    private const val HEADING_ROWS = 6
}
