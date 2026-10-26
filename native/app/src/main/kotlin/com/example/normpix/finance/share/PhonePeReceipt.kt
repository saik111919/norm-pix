package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime

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
 * The amount is printed twice, at the size of the text around it, so it is read from both places (see [settle]); PhonePe
 * puts a ₹ before it every time, so a first digit of 7 or 2 with no ₹ in front is that ₹ misread or a digit, and a reading
 * where it can't be told is left to the user. A name is printed in capitals and is written back as a name ([tidyName]).
 * The date is the one printed beside the status; where that can't be read, the one inside PhonePe's own transaction id
 * (`T` and the year, month, day, hour and minute, then digits: T2610152233… is 15 Oct 2026, 10:33 pm) - only when that is a
 * real date, not after today.
 */
internal object PhonePeReceipt : PaymentAppReceipt {
    override val name = "PhonePe"

    private val brand = Regex("""(?i)\bphone\s?pe\b""")

    /** "…@ybl on PhonePe": another app's receipt saying which app the other side uses. */
    private val otherSidesApp = Regex("""(?i)\bon\s+phone\s?pe\b""")

    /** PhonePe's own transaction id: a T and twenty-odd digits. */
    private val ownId = Regex("""^T\d{18,}$""")

    /** "Paid to" (a UPI id, a shop), "Transfer to" (a bank account by its number: someone's, or the user's own). */
    private val paidTo = Regex("""(?i)^\W*(?:paid|sent|transfer(?:red)?)\s+to\W*$""")

    /** "Banking Name : Rahul Mehta": the name the receiving bank has for the account a transfer went to. */
    private val bankingName = Regex("""(?i)^\W*banking\s+name\s*[:\-]?\s*(.+)$""")
    private val receivedFrom = Regex("""(?i)^\W*(?:money\s+)?received\s+from\W*$""")
    private val debitedFrom = Regex("""(?i)^\W*(?:debited|paid)\s+from\W*$""")
    private val creditedTo = Regex("""(?i)^\W*credited\s+to\W*$""")
    private val utr = Regex("""(?i)\butr\b(?:\s*no\.?)?\s*[:.\-]?\s*([A-Za-z0-9]{6,35})?""")
    private val loneReference = Regex("""^[A-Za-z0-9]{8,35}$""")
    private val status = Regex(
        """(?i)^\W*(?:[\p{L}\d]{1,2}\s+)?(?:transaction|payment|transfer)\s+(succ\w*|completed|failed|declined|pending|processing|in\s+progress)\b.*""",
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

        val below = if (heading >= 0) receipt.nameBelow(heading, NAME_REACH, rupeePrinted = true) { it.has(partTitle) } else NamedRow(null, null)
        // A transfer to an account shows its number and bank under "Transfer to", and the account holder's name after them: that
        // name is the other side, not the bank's ("HDFC Bank"). A person's own name, shown at the top, stays the one used.
        val holder = if (heading >= 0) {
            rows.drop(heading + 1).take(NAME_REACH + 1).takeWhile { !it.has(partTitle) }
                .firstNotNullOfOrNull { row -> bankingName.matchEntire(row.text.trim())?.groupValues?.get(1)?.let(::partyName) }
        } else {
            null
        }
        val party = if (holder != null && below.name?.let(ownAccountWords::containsMatchIn) != false) NamedRow(holder, below.amount) else below
        // The other side's rows - the name, then their UPI id, phone number or account - down to the next part. In a transfer
        // to the user's own account, that account.
        val otherRows = if (heading >= 0) {
            rows.drop(heading + 1).take(NAME_REACH + 1).takeWhile { !it.has(partTitle) }.map { amountAtEnd(it, rupeePrinted = true).second.joinToString(" ") }
        } else {
            emptyList()
        }

        // The user's account: below "Debited from", down to the UTR. Its last digits, and the amount once more.
        val accountRows = if (accountHeading >= 0) {
            rows.drop(accountHeading + 1).take(ACCOUNT_REACH).takeWhile { !it.has(partTitle) && utr.find(it.text) == null }
        } else {
            emptyList()
        }
        val accountRead = accountRows.map { amountAtEnd(it, rupeePrinted = true) }
        val accountText = accountRead.joinToString("\n") { (_, rest) -> rest.joinToString(" ") }
        val masks = SharedTransactionParser.maskedNumbers(accountText)

        val amount = receipt.settleAmount(listOfNotNull(party.amount) + accountRead.mapNotNull { it.first }, ledger.decimals, rupeePrinted = true)
        val printed = SharedTransactionParser.dateIn(receipt.text, today)
        val date = if (printed.date == null && !printed.unreadable) idDate(receipt, today) ?: printed else printed
        return ParsedTransactionDraft(
            amount = amount.amount,
            ambiguousAmounts = amount.ambiguous,
            type = type,
            kind = kindOf(type),
            status = statusOf(rows),
            counterparty = party.name?.let(::tidyName),
            date = date.date,
            time = date.time,
            dateUnreadable = date.unreadable,
            reference = reference(receipt),
            instrument = SharedTransactionParser.instrument(accountText, masks),
            maskedNumbers = masks,
            accountText = accountText,
            otherAccountText = otherRows.joinToString("\n").ifEmpty { null },
            otherMaskedNumbers = otherSideNumbers(otherRows),
        )
    }

    private fun statusOf(rows: List<ReceiptRow>): SharedStatus {
        val word = rows.firstNotNullOfOrNull { row ->
            (listOf(row.text) + row.cells.map { it.text.trim() }).firstNotNullOfOrNull { status.matchEntire(it)?.groupValues?.get(1)?.lowercase() }
        } ?: return SharedStatus.Unknown
        return when {
            word.startsWith("fail") || word == "declined" -> SharedStatus.Failed
            word == "pending" || word == "processing" || word.startsWith("in") -> SharedStatus.Pending
            else -> SharedStatus.Completed
        }
    }

    /**
     * When PhonePe's own transaction id says the payment was made: `T` + yyMMddHHmm + digits. Only an id read cleanly, naming
     * a real moment from 2016 on that is not after [today]; the printed date is always preferred to it.
     */
    private fun idDate(receipt: ReceiptLayout, today: LocalDate): SharedTransactionParser.When? {
        val id = receipt.rows.firstNotNullOfOrNull { row -> row.cells.map { it.text.trim() }.firstOrNull(ownId::matches) } ?: return null
        val part = { from: Int -> id.substring(from, from + 2).toInt() }
        return try {
            val date = LocalDate.of(2000 + part(1), part(3), part(5))
            val time = LocalTime.of(part(7), part(9))
            if (date.year < 2016 || date.isAfter(today)) null else SharedTransactionParser.When(date, time, unreadable = false)
        } catch (_: DateTimeException) {
            null
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
