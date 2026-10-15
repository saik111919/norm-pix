package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PhoneReceipts.picture
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The receipts read by the labels every app prints ([LabelledReceipt]): CRED's, PayZapp's and SBI's as the owner's phone
 * read them ([PhoneReceipts]), the apps known only by their name, and the vocabulary they all share (ReceiptVocabulary.kt).
 */
class LabelledReceiptTest {
    private val today = LocalDate.of(2026, 10, 14)

    private fun read(lines: List<PictureLine>): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture(lines), CurrencySpec.INR, today)).draft

    /** A picture whose text is known but not where it was: a line to a row. */
    private fun readText(vararg lines: String): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", PictureText(lines.joinToString("\n")), CurrencySpec.INR, today)).draft

    private fun appOf(vararg lines: String) = PaymentAppReceipts.appOf(ReceiptLayout.of(PictureText(lines.joinToString("\n"))))

    // ------------------------------------------------------------ real receipts

    @Test
    fun `a real CRED screen`() {
        val d = read(PhoneReceipts.cred)
        assertEquals("CRED", d.app)
        assertEquals(4_300L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SharedStatus.Completed, d.status)
        // Named at the top with no label: taken from CRED's own sentence about them, not "recipient's bank".
        assertEquals("Imran Ali", d.counterparty)
        assertEquals(LocalDate.of(2026, 9, 23), d.date)
        assertEquals(LocalTime.of(14, 0), d.time)
        // "Paid via CRED" names the app: the user's account is the bank over its last digits.
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
        assertEquals("HDFC BANK LTD\nXXXXXXXXXX1234 LIFI»", d.accountText)
        // Its id is cut off at the foot of the screen.
        assertNull(d.reference)
    }

    @Test
    fun `a real PayZapp receipt of a card's bill`() {
        val d = read(PhoneReceipts.payZapp)
        assertEquals("PayZapp", d.app)
        assertEquals(567_930L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        // The card is what was paid; its badge, read as an "O", is not part of its name.
        assertEquals("HDFC Bank Credit Card", d.counterparty)
        assertEquals(LocalDate.of(2026, 9, 30), d.date)
        assertEquals(LocalTime.of(14, 31), d.time)
        // The id runs on to a second line.
        assertEquals("txn_1a2b3c4d-1111-4222-a333-444455566677", d.reference)
        // Paid with the bank account over UPI: the card's number is the payee's, not one of the user's.
        assertEquals("HDFC BANK LTD UPI", d.accountText)
        assertEquals(SharedInstrument.Upi, d.instrument)
        assertEquals(emptyList(), d.maskedNumbers)
    }

    @Test
    fun `a real SBI transaction`() {
        val d = read(PhoneReceipts.sbi)
        assertEquals("SBI", d.app)
        // "z 25.00" beside "Amount:".
        assertEquals(2_500L, d.amount)
        // The narration: "UPI/DR/…/Kiran/…", read as "UP/DRI…".
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("Kiran", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 14), d.date)
        // The account's number, not the one the money was transferred to.
        assertEquals(listOf(MaskedNumber("", "4321")), d.maskedNumbers)
        assertEquals("XXXXXXX4321", d.accountText)
        // The narration's reference is hidden.
        assertNull(d.reference)
    }

    @Test
    fun `each real receipt is told to be its app's`() {
        fun app(lines: List<PictureLine>) = PaymentAppReceipts.appOf(ReceiptLayout.of(picture(lines)))
        assertSame(CredReceipt, app(PhoneReceipts.cred))
        assertSame(PayZappReceipt, app(PhoneReceipts.payZapp))
        assertSame(SbiReceipt, app(PhoneReceipts.sbi))
        assertSame(MutualFundOrderReceipt, app(PhoneReceipts.mutualFundSip))
        assertSame(PhonePeReceipt, app(PhoneReceipts.phonePeAutopay))
        assertSame(PhonePeReceipt, app(PhoneReceipts.phonePeReceived))
        assertSame(GooglePayReceipt, app(PhoneReceipts.googlePayTen))
    }

    // --------------------------------------------- apps known by their name

    @Test
    fun `an app known only by its name is read by the labels every app uses`() {
        // Illustrative: Paytm's paid and received screens, its logo read as a word.
        val paid = readText(
            "paytm", "Paid Successfully", "₹1,250", "To: Swiggy Instamart", "swiggy@paytm", "From: State Bank Of India - 1234",
            "UPI Ref No: 428312345678", "10 Oct 2026, 12:34 PM",
        )
        assertEquals("Paytm", paid.app)
        assertEquals(125_000L, paid.amount)
        assertEquals(TransactionType.Expense, paid.type)
        assertEquals(SharedStatus.Completed, paid.status)
        // "To" is the payee; "From" names the user's own bank.
        assertEquals("Swiggy Instamart", paid.counterparty)
        assertEquals("State Bank Of India - 1234", paid.accountText)
        assertEquals("428312345678", paid.reference)
        assertEquals(LocalDate.of(2026, 10, 10), paid.date)
        assertEquals(LocalTime.of(12, 34), paid.time)

        val received = readText(
            "paytm", "Money Received", "₹500", "From: Rahul Sharma", "rahul@ybl", "To: State Bank Of India - 1234",
            "UPI Ref No: 428398765432", "9 Oct 2026, 8:15 PM",
        )
        assertEquals(TransactionType.Income, received.type)
        assertEquals("Rahul Sharma", received.counterparty)
        assertEquals(50_000L, received.amount)
        assertEquals("State Bank Of India - 1234", received.accountText)
    }

    @Test
    fun `an app named only as the other side's, or in a UPI id, is not the receipt's`() {
        assertNull(appOf("To: Swiggy", "swiggy@paytm", "₹250"))
        assertNull(appOf("To: Mr Ravi", "ravi@ybl on PhonePe", "₹250"))
        assertEquals("BHIM", appOf("BHIM", "Paid to SWIGGY", "₹250")?.name)
        assertEquals("Amazon Pay", appOf("amazon pay", "Paid to Bharti Airtel", "₹649")?.name)
    }

    @Test
    fun `a branded receipt the labels find nothing on is read by the general rules`() {
        // No label at all: the general rules still find the payment.
        val d = readText("paytm", "You paid ₹250 to Swiggy")
        assertEquals(25_000L, d.amount)
        assertNull(d.app)
    }

    // ----------------------------------------------------------- vocabulary

    @Test
    fun `a label's value is beside it on its row, or else on the row below`() {
        val layout = ReceiptLayout.of(PictureText("UPI Ref. No: 428312345678\nPaid to\nSWIGGY\nTransaction ID"))
        assertEquals("428312345678", layout.labelled(ReceiptWords.bankReference)?.let(layout::valueOf))
        assertEquals("SWIGGY", layout.labelled(ReceiptWords.paidTo)?.let(layout::valueOf))
        // A label at the foot with nothing below it has no value.
        assertEquals("", layout.labelled(ReceiptWords.appReference)?.let(layout::valueOf))
        // Only at the start of a row: "amount credited to…" is a sentence, "Total" is not "To".
        assertNull(ReceiptLayout.of(PictureText("Total ₹250")).labelled(ReceiptWords.to))
    }

    @Test
    fun `a status is read in any case and wording`() {
        fun status(vararg lines: String) = ReceiptLayout.of(PictureText(lines.joinToString("\n"))).status()
        assertEquals(SharedStatus.Completed, status("₹43", "SUcCEsSFUL"))
        assertEquals(SharedStatus.Completed, status("Paid Successfully"))
        assertEquals(SharedStatus.Failed, status("Transaction Failed"))
        assertEquals(SharedStatus.Failed, status("Payment unsuccessful"))
        assertEquals(SharedStatus.Pending, status("Payment in progress"))
        assertEquals(SharedStatus.Unknown, status("payment initiated from your HDFC BANK LTD"))
    }

    @Test
    fun `a bank's narration says which way the money went, to whom, and its reference`() {
        val debit = narrationOf("TRANSFER TO 4897694162094 UPI/DR/429876543210/RAVI KUMAR/SBIN/ravi@okaxis/rent")!!
        assertEquals(TransactionType.Expense, debit.type)
        assertEquals("RAVI KUMAR", debit.name)
        assertEquals("429876543210", debit.reference)
        val credit = narrationOf("BY TRANSFER-UPI/CR/429876543211/ASHA/HDFC/asha@ybl/")!!
        assertEquals(TransactionType.Income, credit.type)
        assertEquals("ASHA", credit.name)
        // As the recognizer reads it, the reference hidden.
        val misread = narrationOf("TRANSFER TO XXXX9876 UP/DRIXXXXXXXX0001/Kiran/SBIN/kirankumar/Payme")!!
        assertEquals(TransactionType.Expense, misread.type)
        assertEquals("Kiran", misread.name)
        assertNull(misread.reference)
        assertNull(narrationOf("Opening balance"))
    }

    @Test
    fun `what the recognizer adds to a name is taken off, and a phone number is no name`() {
        assertEquals("Ravi Kumar", partyName("Ravi Kumar ©"))
        assertNull(nameIn("+91 ee 6411"))
        // Labelled as the other side, a card is the payee: its bill is being paid.
        assertNull(partyName("HDFC Bank Credit CardO"))
        assertEquals("HDFC Bank Credit Card", labelledPartyName("HDFC Bank Credit CardO"))
        assertNull(labelledPartyName("Your account"))
        assertNull(labelledPartyName("XXXX1234 card"))
    }

    @Test
    fun `a two-digit year can have an apostrophe in front`() {
        assertEquals(LocalDate.of(2026, 10, 12), SharedTransactionParser.dateIn("12 Oct '26, 12:00 AM", today).date)
        assertEquals(LocalDate.of(2025, 9, 23), SharedTransactionParser.dateIn("02:00pm, 23rd sep'25", today).date)
        assertEquals(LocalTime.of(14, 0), SharedTransactionParser.dateIn("02:00pm, 23rd sep'25", today).time)
    }
}
