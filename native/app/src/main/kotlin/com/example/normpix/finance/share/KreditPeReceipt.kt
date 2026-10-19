package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate

/**
 * Kredit.Pe's payment screen (UPI on a credit line), and the receipt picture its Share makes of it - white on black:
 *
 * ```
 *   ₹150                                        the amount, large, alone
 *   (✓) Paid successfully                       the status
 *   Oct 07, 1:25 PM                             when - with no year
 *   To: BELLS AND PEPPERS                       the payee, in capitals
 *       UPI ID: paytmqr1evtpx1q27@paytm         their UPI id
 *   From                                        the user: no name...
 *       UPI ID: xxxxxx4864-1@kphdfc             ...only their UPI id, partly hidden - "4864" is a handle's, not an account's
 *   UPI Transaction ID                          the bank's reference, over its number
 *       628055734733
 *   Paid with
 *       [Kredit.Pe]                             the credit line the payment was made on
 *   Earn 3% cashback on UPI payments            an ad: no part of the payment
 * ```
 *
 * It shows no account number at all, so the payment is matched to the user's account by "Kredit.Pe" alone. There is no
 * year, so the date is the latest one with that day and month that is not after today.
 */
internal object KreditPeReceipt : PaymentAppReceipt {
    override val name = "Kredit.Pe"

    private val paidTitle = Regex("""(?i)^\W*(?:[\p{L}\d]{1,2}\s+)?(?:paid|payment)\s+(?:successfully|successful)\W*$""")
    private val paidWith = Regex("""(?i)^\W*paid\s+(?:with|using|via)\W*$""")
    private val kreditPe = Regex("""(?i)\bkredit\s?\.?\s?pe\b""")

    override val tells = listOf(
        Tell.brand(2, """kredit\s?\.?\s?pe"""),
        Tell.row(1, Regex("""(?i)cashback\s+on\s+upi\s+payments""")),
        // Its own UPI handles: "…@kphdfc", "kp" and then the bank's.
        Tell.row(1, Regex("""(?i)@kp[a-z]{2,}\b""")),
    )

    override fun read(receipt: ReceiptLayout, ledger: CurrencySpec, today: LocalDate): ParsedTransactionDraft {
        val rows = receipt.rows
        val to = receipt.labelled(ReceiptWords.to)
        val paid = rows.any { it.has(paidTitle) }

        // Only money going out is paid from Kredit.Pe: a "To:" with a name, or the title, says so.
        val payee = to?.let { found ->
            val beside = found.beside.takeIf { it.isNotEmpty() }?.let(::partyName)
            val below = rows.drop(found.at + 1)
            when {
                // The name beside "To:", run on to the next line when it is a long one.
                beside != null -> found.besideCell?.let { receipt.runOn(beside, it, below.take(1)) } ?: beside
                else -> below.firstOrNull()?.text?.let(::partyName)
            }
        }
        val type = if (paid || payee != null) TransactionType.Expense else null

        // The amount: the first figure of the screen, above the sides.
        val printed = rows.take(to?.at ?: rows.size).firstNotNullOfOrNull { row -> row.cells.firstNotNullOfOrNull { amountSighting(it.text) } }
            ?: receipt.headline?.let(::amountSighting)
        val amount = receipt.settleAmount(listOfNotNull(printed), ledger.decimals, rupeePrinted = true)

        // The credit line the payment was made on, named under "Paid with": all the screen says of the user's account.
        val creditLine = rows.indexOfFirst { it.has(paidWith) }.takeIf { it >= 0 }
            ?.let { at -> rows.drop(at + 1).take(2).firstOrNull { kreditPe.containsMatchIn(it.text) } }
        val accountText = if (creditLine != null) "Kredit.Pe" else ""

        val date = SharedTransactionParser.dateIn(receipt.text, today)
        return ParsedTransactionDraft(
            amount = amount.amount,
            ambiguousAmounts = amount.ambiguous,
            type = type,
            kind = kindOf(type),
            status = receipt.status(),
            counterparty = payee?.let(::tidyName),
            date = date.date,
            time = date.time,
            dateUnreadable = date.unreadable,
            reference = receipt.reference(),
            maskedNumbers = emptyList(),
            accountText = accountText,
        )
    }
}
