package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate

/**
 * PhonePe's transaction screen, and the receipt picture its Share makes of it:
 *
 * ```
 * Transaction Successful                          the status
 * 11:29 am on 12 Oct 2026                         when
 * Paid to                                         or "Received from"
 * (UL)  UBER INDIA SYSTEMS PRIVATE        ₹30     who - the name may run on to a second line - and the amount
 *       LIMITED
 *       amzn-dfklpck1ewy@apl                      their UPI id, phone number or account
 * Transfer Details / Payment Details
 * Message  Uber                                   the note the payer wrote, if any
 * PhonePe Transaction ID  T2610121129…            PhonePe's own id
 * Debited from                                    or "Credited to": the user's own account
 * [bank logo]  XXXXXXXXXX3588             ₹30     its last digits, and the amount again
 *              UTR: 899354336109                  the bank's reference, the one its SMS carries too
 * Powered by UPI | AXIS BANK                      PhonePe's bank, not the user's
 * ```
 *
 * The amount is printed twice, at the size of the text around it, so it is read from both places (see [settle]).
 */
internal object PhonePeReceipt : PaymentAppReceipt {
    override val name = "PhonePe"

    private val brand = Regex("""(?i)\bphone\s?pe\b""")

    /** "…@ybl on PhonePe": another app's receipt saying which app the other side uses. */
    private val otherSidesApp = Regex("""(?i)\bon\s+phone\s?pe\b""")

    /** PhonePe's own transaction id: a T and twenty-odd digits. */
    private val ownId = Regex("""^T\d{18,}$""")

    private val paidTo = Regex("""(?i)^\W*(?:paid|sent|transferred)\s+to\W*$""")
    private val receivedFrom = Regex("""(?i)^\W*(?:money\s+)?received\s+from\W*$""")
    private val debitedFrom = Regex("""(?i)^\W*(?:debited|paid)\s+from\W*$""")
    private val creditedTo = Regex("""(?i)^\W*credited\s+to\W*$""")
    private val utr = Regex("""(?i)\butr\b(?:\s*no\.?)?\s*[:.\-]?\s*([A-Za-z0-9]{6,35})?""")
    private val loneReference = Regex("""^[A-Za-z0-9]{8,35}$""")
    private val status = Regex(
        """(?i)^\W*(?:transaction|payment|transfer)\s+(successful|success|completed|failed|declined|pending|processing|in\s+progress)\b.*""",
    )

    /**
     * The rows that start the receipt's next part: the other side's part ends at the first of them. Its icon can come out
     * as a letter in front ("E Payment Details").
     */
    private val partTitle = Regex(
        """(?i)^\W*(?:\p{Lu}\)?\s+)?(?:(?:payment|transfer|transaction)\s+details|(?:phone\s?pe\s+)?transaction\s+id|message|banking\s+name|""" +
            """(?:debited|paid)\s+from|credited\s+to|utr\b.*|powered\s+by)\W*$""",
    )

    /** How many rows below "Paid to" the name is looked for: the status and the time can come between, read out of place. */
    private const val NAME_REACH = 4

    /** How many rows below "Debited from" belong to the account. */
    private const val ACCOUNT_REACH = 2

    override val tells = listOf(
        Tell(2) { receipt -> receipt.rows.any { brand.containsMatchIn(it.text) && !otherSidesApp.containsMatchIn(it.text) } },
        Tell(2) { receipt -> receipt.rows.any { row -> row.cells.any { ownId.matches(it.text.trim()) } } },
        Tell(1) { receipt -> receipt.rows.any { it.has(debitedFrom) || it.has(creditedTo) } },
        Tell(1) { receipt -> receipt.rows.any { it.has(paidTo) || it.has(receivedFrom) } },
    )

    override fun read(receipt: ReceiptLayout, ledger: CurrencySpec, today: LocalDate): ParsedTransactionDraft {
        val rows = receipt.rows
        val heading = receipt.indexOf { it.has(paidTo) || it.has(receivedFrom) }
        val accountHeading = receipt.indexOf { it.has(debitedFrom) || it.has(creditedTo) }
        val type = when {
            heading >= 0 -> if (rows[heading].has(paidTo)) TransactionType.Expense else TransactionType.Income
            accountHeading >= 0 -> if (rows[accountHeading].has(debitedFrom)) TransactionType.Expense else TransactionType.Income
            else -> null
        }

        val party = if (heading >= 0) receipt.nameBelow(heading, NAME_REACH) { it.has(partTitle) } else NamedRow(null, null)

        // The user's account: below "Debited from", down to the UTR. Its last digits, and the amount once more.
        val accountRows = if (accountHeading >= 0) {
            rows.drop(accountHeading + 1).take(ACCOUNT_REACH).takeWhile { !it.has(partTitle) && utr.find(it.text) == null }
        } else {
            emptyList()
        }
        val accountRead = accountRows.map(::amountAtEnd)
        val accountText = accountRead.joinToString("\n") { (_, rest) -> rest.joinToString(" ") }
        val masks = SharedTransactionParser.maskedNumbers(accountText)

        val amount = settle(listOfNotNull(party.amount) + accountRead.mapNotNull { it.first }, ledger.decimals)
        val date = SharedTransactionParser.dateIn(receipt.text, today)
        return ParsedTransactionDraft(
            amount = amount.amount,
            ambiguousAmounts = amount.ambiguous,
            type = type,
            kind = kindOf(type),
            status = statusOf(rows),
            counterparty = party.name,
            date = date.date,
            time = date.time,
            dateUnreadable = date.unreadable,
            reference = reference(receipt),
            instrument = SharedTransactionParser.instrument(accountText, masks),
            maskedNumbers = masks,
            accountText = accountText,
        )
    }

    private fun statusOf(rows: List<ReceiptRow>): SharedStatus {
        val word = rows.firstNotNullOfOrNull { status.matchEntire(it.text)?.groupValues?.get(1)?.lowercase() } ?: return SharedStatus.Unknown
        return when {
            word.startsWith("fail") || word == "declined" -> SharedStatus.Failed
            word == "pending" || word == "processing" || word.startsWith("in") -> SharedStatus.Pending
            else -> SharedStatus.Completed
        }
    }

    /** The UTR, the bank's reference for the payment, wherever its number is; PhonePe's own id only when there is none. */
    private fun reference(receipt: ReceiptLayout): String? {
        val at = receipt.indexOf { utr.find(it.text) != null }
        if (at >= 0) {
            val beside = utr.find(receipt.rows[at].text)?.groups?.get(1)?.value
            val below = receipt.rows.getOrNull(at + 1)?.cells?.firstOrNull()?.text?.trim()?.takeIf { loneReference.matches(it) }
            listOfNotNull(beside, below).firstOrNull { ref -> ref.count(Char::isDigit) >= 6 }?.let { return it }
        }
        return receipt.rows.firstNotNullOfOrNull { row -> row.cells.map { it.text.trim() }.firstOrNull(ownId::matches) }
    }
}
