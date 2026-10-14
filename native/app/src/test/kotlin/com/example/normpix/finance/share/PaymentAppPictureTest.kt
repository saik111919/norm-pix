package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Text read from screenshots of Indian payment apps' transaction screens - Google Pay, PhonePe, Paytm, CRED, Kotak, BHIM,
 * Amazon Pay - one recognized line per line. Most fixtures are illustrative: laid out the way those screens are, with
 * made-up names and numbers, not verified copies of any app's current screen. The ones marked "real" are what the phone's
 * recognizer returned for the owner's own receipts (2026-10; a PhonePe payment, a Google Pay payment), kept as read - names,
 * ids and all - because their odd line order and dropped rupee signs are the point. Add more real ones (masked) as they turn up.
 */
class PaymentAppPictureTest {
    private val today = LocalDate.of(2026, 10, 11)

    private fun draft(text: String, headline: String? = null): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(SharedTransactionParser.read(text, CurrencySpec.INR, today, headline)).draft

    private fun lines(vararg lines: String) = lines.joinToString("\n")

    // -------------------------------------------------------------- the apps

    @Test
    fun `Google Pay paid screen has no payment word, only its title`() {
        val d = draft(
            lines(
                "To SWIGGY", "₹250", "Completed", "10 Oct 2026, 12:34 pm", "HDFC Bank 1234", "UPI transaction ID", "428312345678",
                "To: SWIGGY", "swiggy.in@axisbank", "From: SAI KUMAR (HDFC Bank)", "saikumar@okhdfcbank", "Google transaction ID",
                "CICAgOjzk9WRAQ",
            ),
        )
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("SWIGGY", d.counterparty)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(12, 34), d.time)
        assertEquals("428312345678", d.reference)
    }

    @Test
    fun `Google Pay received screen`() {
        val d = draft(
            lines(
                "From Rahul Sharma", "₹500", "Completed", "9 Oct 2026, 8:15 pm", "UPI transaction ID", "428398765432",
                "To: SAI KUMAR (Kotak Mahindra Bank)", "From: RAHUL SHARMA", "rahul.sharma@oksbi",
            ),
        )
        assertEquals(50_000L, d.amount)
        assertEquals(TransactionType.Income, d.type)
        assertEquals("Rahul Sharma", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 9), d.date)
        assertEquals(LocalTime.of(20, 15), d.time)
    }

    @Test
    fun `PhonePe paid screen`() {
        val d = draft(
            lines(
                "Transaction Successful", "12:34 pm on 10 Oct 2026", "Paid to", "SWIGGY", "₹250", "Transfer Details", "Transaction ID",
                "T2610101234567890123", "Debited from", "XXXXXX1234", "₹250", "UTR: 428312345678", "Powered by UPI",
            ),
        )
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("SWIGGY", d.counterparty)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(12, 34), d.time)
        // The UTR, not PhonePe's own transaction id: it is the number the bank's SMS for the payment carries too.
        assertEquals("428312345678", d.reference)
    }

    // ------------------------------------------------- real screens (2026-10)

    /** What the phone's recognizer returned for a real PhonePe receipt, which prints its amount no larger than its other text. */
    private val phonePeReceipt = lines(
        "Paid to", "Transaction Successful", "11:34 am on 05 Oct 2026", "Suman Sharma", "Q505076279 @ybl", "Payment Details",
        "PhonePe Transaction ID", "T2610051134004403220212", "Debited from", "XXXXXXXXXX3588", "UTR: 225197484310", "Powered by",
        "UPI AXIS BANK", "Z10", "10",
    )

    @Test
    fun `a real PhonePe receipt names the payee below its heading and not the status`() {
        val d = draft(phonePeReceipt)
        assertEquals("Suman Sharma", d.counterparty)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 5), d.date)
        assertEquals(LocalTime.of(11, 34), d.time)
    }

    @Test
    fun `a real PhonePe receipt's amount is the figure alone on a line, though no line stands out in size`() {
        // "₹10" was read as "Z10", and the figure beside the debited account as "10"; the picture has no headline.
        assertEquals(1_000L, draft(phonePeReceipt).amount)
        assertEquals(1_000L, draft(phonePeReceipt, headline = null).amount)
    }

    @Test
    fun `a real PhonePe receipt's reference is the UTR, not PhonePe's own id`() {
        val d = draft(phonePeReceipt)
        assertEquals("225197484310", d.reference)
        assertEquals("Ref 225197484310", d.note)
    }

    @Test
    fun `a real PhonePe receipt shows the account's last digits`() {
        assertEquals(listOf(MaskedNumber("", "3588")), draft(phonePeReceipt).maskedNumbers)
    }

    /** A Google Pay receipt of a payment to a person, as its screen reads (the recognizer's own line order may differ). */
    private val googlePayReceipt = lines(
        "To Mr AKBAR", "₹10", "Pay again", "Completed", "10 Oct 2026, 10:07pm", "HDFC Bank", "652850XXXXXXXX55", "Payment of ₹10 completed",
        "Receiver's bank has confirmed deposit of money to Mr AKBAR's bank account", "UPI transaction ID", "131074460564",
        "To: Mr AKBAR", "••••5486@ybl on PhonePe", "From: KUSUMANCHI SAI (HDFC Bank)", "••••10-5@okhdfcbank on Google Pay",
        "Google transaction ID", "CICAgLimuefacA",
    )

    @Test
    fun `a real Google Pay receipt of a payment to a person`() {
        val d = draft(googlePayReceipt)
        assertEquals(1_000L, d.amount)
        // "From: <the user>" names the payer: it makes the payment neither income nor a payment to oneself.
        assertEquals(TransactionType.Expense, d.type)
        // The payee is who it went to, not the bank account the deposit sentence mentions.
        assertEquals("Mr AKBAR", d.counterparty)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(22, 7), d.time)
        assertEquals("131074460564", d.reference)
    }

    /**
     * The same screen as the phone's recognizer really returned it (2026-10): out of reading order - the reference two lines
     * away from its label - with the rupee sign dropped and "M" from the avatar. Its biggest figure is the amount.
     */
    private val googlePayAsRead = lines(
        "To Mr AKBAR", "HDFC Bank", "131074460564", "M", "10", "UPI transaction ID", "To: Mr AKBAR", "Pay again", "10 Oct 2026, 10:07 pm",
        "652850XXXXXXXX55", "Completed", "Payment of 10 completed", "Receiver's bank has confirmed deposit of",
        "money to Mr AKBAR's bank account", "CICAgLimuefacA", ".5486@ybl on PhonePe", "From: KUSUMANCHI SAI (HDFC Bank)",
        "10-5@okhdfcbank on Google Pay", "Google transaction ID", "RuPay", "GPay",
    )

    @Test
    fun `a real Google Pay receipt as the recognizer read it`() {
        val d = draft(googlePayAsRead, headline = "10")
        assertEquals(1_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("Mr AKBAR", d.counterparty)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(22, 7), d.time)
        // The number is on a line of its own, away from "UPI transaction ID": still the reference.
        assertEquals("131074460564", d.reference)
        assertEquals(listOf(MaskedNumber("652850", "55")), d.maskedNumbers)
        assertEquals(SharedInstrument.Card, d.instrument)
    }

    @Test
    fun `a reference away from its label is found only when there is a label and one such number`() {
        // A label, and one number of twelve digits on a line of its own.
        assertEquals("131074460564", draft(lines("Paid ₹10", "131074460564", "UPI transaction ID")).reference)
        assertEquals("225197484310", draft(lines("Paid ₹10", "225197484310", "UTR")).reference)
        // No label: twelve digits alone are only a number.
        assertNull(draft(lines("Paid ₹10", "131074460564")).reference)
        // Two such numbers: can't tell which.
        assertNull(draft(lines("Paid ₹10", "131074460564", "225197484310", "UPI transaction ID")).reference)
        // Not twelve digits.
        assertNull(draft(lines("Paid ₹10", "13107446056", "UPI transaction ID")).reference)
        // A label with its number beside it wins over a stray one.
        assertEquals("428312345678", draft(lines("Paid ₹10", "131074460564", "UTR: 428312345678")).reference)
        // The bank's reference away from its label beats an app's id beside its own.
        assertEquals("131074460564", draft(lines("Paid ₹10", "Transaction ID: T2610051134004403220212", "131074460564", "UPI transaction ID")).reference)
    }

    @Test
    fun `a real Google Pay receipt shows the card's first six and last two digits and nothing from the UPI ids`() {
        assertEquals(listOf(MaskedNumber("652850", "55")), draft(googlePayReceipt).maskedNumbers)
    }

    @Test
    fun `what the money moved through is read from the user's side, not the receiver's`() {
        // The receipt's sentence about the receiver's bank account is not the user's account; the first digits of a number
        // say it is a card's.
        assertEquals(SharedInstrument.Card, draft(googlePayReceipt).instrument)
        assertEquals(SharedInstrument.Upi, draft(phonePeReceipt).instrument)
        val sentence = "Receiver's bank has confirmed deposit of money to Mr AKBAR's bank account"
        assertNull(draft(lines("Payment of ₹10 completed", sentence)).instrument)
        // The user's own account still counts, and so does a card named outright.
        assertEquals(SharedInstrument.BankAccount, draft(lines("Payment of ₹10 completed", "Debited from A/c XX3588", sentence)).instrument)
        assertEquals(SharedInstrument.CreditCard, draft(lines("Payment of ₹10 completed", "HDFC Credit Card 652850XXXXXXXX55")).instrument)
    }

    @Test
    fun `the payee is not taken from the sentence about the receiver's bank account`() {
        val d = draft(lines("Payment of ₹10 completed", "Receiver's bank has confirmed deposit of money to Mr AKBAR's bank account", "₹10"))
        assertNull(d.counterparty)
    }

    @Test
    fun `a name below a heading skips the status, the time and ids but takes the first real name`() {
        for (heading in listOf("Paid to", "To", "Sent to")) {
            val d = draft(lines(heading, "Payment Successful", "10 Oct 2026, 12:34 pm", "Q505076279@ybl", "Swiggy Instamart", "₹250", "Powered by", "UPI"))
            assertEquals("Swiggy Instamart", d.counterparty, heading)
        }
        // Nothing name-like in reach: no payee rather than a label.
        assertNull(draft(lines("Paid to", "Transaction Successful", "Payment Details", "PhonePe Transaction ID", "₹250")).counterparty)
    }

    @Test
    fun `a received screen names the sender below its heading`() {
        val d = draft(lines("Received from", "Transaction Successful", "8:15 pm on 9 Oct 2026", "Rahul Sharma", "+91 98XXXXXX10", "₹500"))
        assertEquals("Rahul Sharma", d.counterparty)
        assertEquals(TransactionType.Income, d.type)
        assertEquals(emptyList(), d.maskedNumbers)
    }

    @Test
    fun `a figure alone on a line is the amount only with a rupee sign or when written twice`() {
        fun amount(text: String) = SharedTransactionParser.figureAmount(text, 2)
        assertEquals(1_000L, amount("Paid to\nZ10\nDebited from\n10"))
        assertEquals(1_000L, amount("%10"))
        // Written twice, no sign: still the amount; once, no sign: a number.
        assertEquals(1_000L, amount("10\nUTR: 225197484310\n10"))
        assertNull(amount("10"))
        // Different marked figures compete; a year, a long id and a time are not figures.
        assertNull(amount("Z10\n₹250"))
        assertNull(amount("2026\n2026"))
        assertNull(amount("Z225197484310"))
        assertNull(amount("11:34\n11:34"))
        // Twice, but another figure is written twice as well: can't tell.
        assertNull(amount("10\n10\n85\n85"))
    }

    @Test
    fun `a figure alone does not make a message a transaction`() {
        assertIs<SharedTextReading.NotATransaction>(SharedTransactionParser.read("Z10\n10", CurrencySpec.INR, today))
        assertIs<SharedTextReading.NotATransaction>(SharedTransactionParser.read("10\n10", CurrencySpec.INR, today))
    }

    @Test
    fun `the bank's reference is chosen over an app's id wherever each is printed`() {
        assertEquals("225197484310", draft(lines("Paid ₹10", "Transaction ID", "T2610051134004403220212", "UTR: 225197484310")).reference)
        assertEquals("225197484310", draft(lines("Paid ₹10", "UTR: 225197484310", "Transaction ID: T2610051134004403220212")).reference)
        // With only an app's id, that is the reference.
        assertEquals("T2610051134004403220212", draft(lines("Paid ₹10", "Transaction ID: T2610051134004403220212")).reference)
    }

    @Test
    fun `PhonePe received screen`() {
        val d = draft(
            lines(
                "Transaction Successful", "8:15 pm on 9 Oct 2026", "Received from", "Rahul Sharma", "+91 98XXXXXX10", "₹500",
                "Transfer Details", "Transaction ID", "T2610092015123456789", "Credited to", "XXXXXX1234", "₹500", "UTR: 428398765432",
            ),
        )
        assertEquals(50_000L, d.amount)
        assertEquals(TransactionType.Income, d.type)
        assertEquals("Rahul Sharma", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 9), d.date)
    }

    @Test
    fun `Paytm paid screen`() {
        val d = draft(
            lines(
                "Paid Successfully", "₹1,250", "Rupees One Thousand Two Hundred Fifty Only", "To: Swiggy Instamart", "swiggy@paytm",
                "From: State Bank Of India - 1234", "UPI Ref No: 428312345678", "10 Oct 2026, 12:34 PM",
            ),
        )
        assertEquals(125_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("Swiggy Instamart", d.counterparty)
        assertEquals("428312345678", d.reference)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
    }

    @Test
    fun `Paytm received screen`() {
        val d = draft(
            lines(
                "Money Received", "₹500", "From: Rahul Sharma", "rahul@ybl", "To: State Bank Of India - 1234",
                "UPI Ref No: 428398765432", "9 Oct 2026, 8:15 PM",
            ),
        )
        assertEquals(TransactionType.Income, d.type)
        assertEquals("Rahul Sharma", d.counterparty)
    }

    @Test
    fun `CRED screen, lower case and no year`() {
        val d = draft(
            lines("payment successful", "₹250", "paid to Swiggy", "swiggy@icici", "10 Oct, 12:34 PM", "UPI ref no. 428312345678"),
        )
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("Swiggy", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(12, 34), d.time)
        assertEquals("428312345678", d.reference)
    }

    @Test
    fun `Kotak screen with labelled rows`() {
        val d = draft(
            lines(
                "Payment Successful", "₹250.00", "Paid to", "SWIGGY", "UPI ID: swiggy@ybl", "From Account: XXXX1234",
                "UPI Ref. No: 428312345678", "Date & Time: 10 Oct 2026, 12:34 PM",
            ),
        )
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("SWIGGY", d.counterparty)
        assertEquals("428312345678", d.reference)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(12, 34), d.time)
    }

    @Test
    fun `BHIM and Amazon Pay screens`() {
        val bhim = draft(lines("₹250", "Paid to SWIGGY", "Transaction Successful", "10/10/2026 12:34 PM", "UPI Ref ID: 428312345678"))
        assertEquals(25_000L, bhim.amount)
        assertEquals(TransactionType.Expense, bhim.type)
        assertEquals("SWIGGY", bhim.counterparty)
        assertEquals("428312345678", bhim.reference)

        val amazon = draft(
            lines("Payment successful", "₹649", "Paid to Bharti Airtel", "10 Oct 2026, 12:34 PM", "UPI transaction ID: 428312345678"),
        )
        assertEquals(64_900L, amazon.amount)
        assertEquals(TransactionType.Expense, amazon.type)
        assertEquals("Bharti Airtel", amazon.counterparty)
    }

    // ------------------------------------------------- the misread rupee sign

    @Test
    fun `the figure printed largest is the amount when its rupee sign was misread`() {
        // "₹250" often comes out as "Z250", "%250", "*250" or "R250": with no currency mark the words give no amount.
        for (misread in listOf("Z250", "%250", "*250", "R250", "250", "₹ 250", "{250")) {
            val d = draft(lines("To SWIGGY", misread, "Completed", "10 Oct 2026, 12:34 pm"), headline = misread)
            assertEquals(25_000L, d.amount, misread)
            assertEquals(TransactionType.Expense, d.type, misread)
        }
    }

    @Test
    fun `the words win over the printed figure`() {
        assertEquals(30_000L, draft("Paid ₹300 to Swiggy", headline = "250").amount)
        // Several amounts in the words: the printed one says which.
        assertEquals(20_000L, draft("Rs 100 and Rs 200 debited from your account", headline = "₹200").amount)
        // One the words don't mention settles nothing.
        val d = draft("Rs 100 and Rs 200 debited from your account", headline = "₹300")
        assertNull(d.amount)
        assertEquals(listOf(10_000L, 20_000L), d.ambiguousAmounts)
    }

    @Test
    fun `a printed figure alone is a transaction to fill in`() {
        val d = draft("Completed", headline = "₹250")
        assertEquals(25_000L, d.amount)
        assertNull(d.type)
    }

    @Test
    fun `what a headline line can be`() {
        fun amount(line: String?) = SharedTransactionParser.headlineAmount(line, 2)
        assertEquals(150_050L, amount("₹1,500.50"))
        assertEquals(9_900L, amount("Rs. 99"))
        assertEquals(25_000L, amount("250/-"))
        assertEquals(1_00_000_00L, amount("Z1,00,000"))
        // A bare year, a time, words, a badly grouped number, nothing.
        assertNull(amount("2026"))
        assertNull(amount("12:34"))
        assertNull(amount("To SWIGGY"))
        assertNull(amount("1,2,3"))
        assertNull(amount("₹0"))
        assertNull(amount(null))
        // A year behind a rupee sign is money.
        assertEquals(2_026_00L, amount("₹2026"))
    }

    @Test
    fun `the headline is the figure that clearly stands out`() {
        val screen = listOf(
            PictureLine("To SWIGGY", 40),
            PictureLine("Z250", 132),
            PictureLine("Completed", 36),
            PictureLine("10 Oct 2026, 12:34 pm", 32),
            PictureLine("HDFC Bank 1234", 32),
            PictureLine("UPI transaction ID", 30),
            PictureLine("428312345678", 34),
        )
        assertEquals("Z250", SharedTransactionParser.headline(screen))
        // Nothing much taller than the rest: no headline.
        assertNull(SharedTransactionParser.headline(screen.map { it.copy(top = 0, bottom = 34) }))
        // Two different figures about as large: can't tell which.
        assertNull(SharedTransactionParser.headline(screen + PictureLine("₹1,200", 120)))
        // The same figure twice is still that figure.
        assertEquals("Z250", SharedTransactionParser.headline(screen + PictureLine("₹250", 120)))
        // No sizes at all (an older recognizer): no headline.
        assertNull(SharedTransactionParser.headline(screen.map { it.copy(top = 0, bottom = 0) }))
        assertNull(SharedTransactionParser.headline(emptyList()))
    }

    // ------------------------------------------------------------ the title

    @Test
    fun `a title line says which way the money went`() {
        assertEquals(TransactionType.Expense, draft("To SWIGGY\n₹250").type)
        assertEquals(TransactionType.Income, draft("From Rahul Sharma\n₹500").type)
        // Split over two lines by the recognizer.
        assertEquals(TransactionType.Expense, draft("To\nSWIGGY\n₹250").type)
        // The user's own account isn't a sender.
        assertNull(draft("From HDFC Bank A/C *1234\n₹250").type)
        // Detail rows name both sides; titles pointing both ways settle nothing.
        assertNull(draft("₹250\nTo: SWIGGY\nFrom: SAI KUMAR").type)
        assertNull(draft("To SWIGGY\nFrom Rahul Sharma\n₹250").type)
        // A payment word still comes first.
        assertEquals(TransactionType.Income, draft("To SWIGGY\nRefund of ₹250 credited").type)
    }

    @Test
    fun `labelled payee and payer rows`() {
        assertEquals("Swiggy", draft("Paid ₹250\nTo: Swiggy").counterparty)
        assertEquals("Rahul Sharma", draft("Received ₹500\nFrom: Rahul Sharma").counterparty)
    }
}
