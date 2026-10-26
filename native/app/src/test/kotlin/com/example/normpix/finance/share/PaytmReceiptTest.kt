package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PhoneReceipts.line
import com.example.normpix.finance.share.PhoneReceipts.picture
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Paytm's receipts, read by [PaytmReceipt]: the one the owner shared ([PhoneReceipts.paytmAuto]: a ₹74 payment, with a
 * message and the amount in words), then the ways the recognizer reads it wrongly.
 */
class PaytmReceiptTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val auto = PhoneReceipts.paytmAuto

    private fun read(lines: List<PictureLine>, on: LocalDate = today): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture(lines), CurrencySpec.INR, on)).draft

    @Test
    fun `a real Paytm receipt of a payment - amount, payee, message, date, reference and the bank`() {
        val d = read(auto)
        assertEquals("Paytm", d.app)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SharedKind.Payment, d.kind)
        assertEquals(7_400L, d.amount)
        assertEquals(emptyList(), d.ambiguousAmounts)
        assertEquals(SharedStatus.Completed, d.status)
        // The payee is the "To:" side, and not the user's own name after "From:".
        assertEquals("Mohan Yadav", d.counterparty)
        assertEquals(LocalDate.of(2026, 8, 4), d.date)
        assertEquals(LocalTime.of(14, 25), d.time)
        assertEquals(false, d.dateUnreadable)
        assertEquals("611500000001", d.reference)
        // What the payer wrote, under the amount in words: "Auto", without the speech bubble before it.
        assertEquals("Auto", d.message)
        assertEquals("Auto · Ref 611500000001", d.note)
    }

    @Test
    fun `the user's own account is the bank and last four digits under From - never the hidden UPI id`() {
        val d = read(auto)
        // "UPI ID: ******0000@ptyes" is a handle, not an account number; "HDFC Bank - 1234" is.
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
        assertEquals("HDFC Bank - 1234", d.accountText)
        // Without the bank row there is no account to name, and the handle's digits are not taken in its place.
        val none = read(auto.filterNot { it.text.startsWith("HDFC") })
        assertEquals(emptyList(), none.maskedNumbers)
        assertEquals("", none.accountText)
        assertEquals("Auto", none.message)
    }

    @Test
    fun `the footer's UPI bank is not the user's bank`() {
        // "Powered by UPI | YES BANK" is Paytm's own bank, at the foot of every receipt.
        val d = read(auto)
        assertEquals(false, d.accountText.orEmpty().contains("YES", ignoreCase = true))
        assertEquals(false, d.counterparty.orEmpty().contains("YES", ignoreCase = true))
    }

    @Test
    fun `a rupee sign read as a digit is dropped when the amount in words agrees`() {
        for (misread in listOf("774", "274", "Z74", "F74", "R74", "*74", "74")) {
            val d = read(PhoneReceipts.raw(auto, "₹74", misread))
            assertEquals(7_400L, d.amount, misread)
            assertEquals(emptyList(), d.ambiguousAmounts, misread)
        }
    }

    @Test
    fun `a badge read beside the amount is not part of it`() {
        // The green tick is not text, but it can come out as one: run into the figure's line, or on a line of its own.
        assertEquals(7_400L, read(PhoneReceipts.raw(auto, "₹74", "₹74 ©")).amount)
        assertEquals(7_400L, read(PhoneReceipts.raw(auto, "₹74", "774 @")).amount)
        assertEquals(7_400L, read(auto + line("©", 505, 425, 575, 487)).amount)
        assertEquals(7_400L, read(PhoneReceipts.raw(auto, "₹74", "774") + line("@", 505, 425, 575, 487)).amount)
        // The message is still the message.
        assertEquals("Auto", read(PhoneReceipts.raw(auto, "₹74", "774 @")).message)
    }

    @Test
    fun `an amount misread with nothing to check it is offered both ways, never guessed`() {
        // "774" with no words under it: ₹774, or ₹74 with its ₹ read as a 7. Paytm always prints the ₹, so both are offered.
        val noWords = auto.filterNot { it.text.startsWith("Rupees") }
        val d = read(PhoneReceipts.raw(noWords, "₹74", "774"))
        assertNull(d.amount)
        assertEquals(listOf(7_400L, 77_400L), d.ambiguousAmounts.sorted())
        // Read right, there is nothing to ask.
        assertEquals(7_400L, read(PhoneReceipts.raw(noWords, "₹74", "74")).amount)
        assertEquals(7_400L, read(noWords).amount)
    }

    @Test
    fun `the amount in words settles the amount - and a wrong number is not taken over it`() {
        // Misspelled or split over two lines, the words still say seventy-four.
        assertEquals(7_400L, read(PhoneReceipts.raw(auto, "Rupees Seventy Four Only", "Rupees Sevnty Four Only")).amount)
        val wrapped = PhoneReceipts.raw(auto, "Rupees Seventy Four Only", "Rupees Seventy") + line("Four Only", 246, 580, 668, 612)
        assertEquals(7_400L, read(wrapped).amount)
        // The figure gone: the words alone give the amount.
        assertEquals(7_400L, read(auto.filterNot { it.text == "₹74" }).amount)
        // Words that say another amount than the figure: nothing is chosen.
        val d = read(PhoneReceipts.raw(auto, "Rupees Seventy Four Only", "Rupees Seventy Five Only"))
        assertNull(d.amount)
        assertEquals(listOf(7_400L, 7_500L), d.ambiguousAmounts.sorted())
    }

    @Test
    fun `the message comes through the bubble icon in any form and over two lines`() {
        // The speech bubble before the message, read as a letter on a line of its own, or run into the message.
        assertEquals("Auto", read(auto + line("Q", 405, 636, 440, 670)).message)
        assertEquals("Auto", read(PhoneReceipts.raw(auto, "Auto", "Q Auto")).message)
        assertEquals("Auto", read(PhoneReceipts.raw(auto, "Auto", "@ Auto")).message)
        // A first word that is a word is kept.
        assertEquals("A gift", read(PhoneReceipts.raw(auto, "Auto", "A gift")).message)
        // Two lines of it.
        assertEquals("Auto to Ramu", read(auto + line("to Ramu", 453, 680, 600, 716)).message)
        // None written: none made up, and the note is only the reference.
        val d = read(auto.filterNot { it.text == "Auto" })
        assertNull(d.message)
        assertEquals("Ref 611500000001", d.note)
        // And a message is kept when the amount isn't readable at all.
        assertEquals("Auto", read(auto.filterNot { it.text == "₹74" || it.text.startsWith("Rupees") }).message)
    }

    @Test
    fun `the date and time survive the usual misreads`() {
        fun on(printed: String) = read(PhoneReceipts.raw(auto, "02:25 PM, 04 Aug 2026", printed))
        for (printed in listOf(
            "02:25 PM, 04 Aug 2026",
            "02:25 pm, 04 Aug 2026",
            "02:25 PRN, 04 Aug 2026",
            "02.25 PM, 04 Aug 2026",
            "02:25 PM, O4 Aug 2026",
            "02:25 PM, 04 Aug 2O26",
            "02:25PM, 04 Aug 2026",
            "02:25 PM 04 Aug 2026",
        )) {
            val d = on(printed)
            assertEquals(LocalDate.of(2026, 8, 4), d.date, printed)
            assertEquals(LocalTime.of(14, 25), d.time, printed)
        }
        // A month the recognizer made into another word is no date - not a wrong one.
        val garbled = on("02:25 PM, 04 Auq 2026")
        assertNull(garbled.date)
        // The reference is a twelve-digit number, never a date.
        assertEquals("611500000001", garbled.reference)
    }

    @Test
    fun `money received is income and names the sender`() {
        val received = PhoneReceipts.raw(
            PhoneReceipts.raw(
                PhoneReceipts.raw(auto, "Paid Successfully", "Received Successfully"),
                "To: Mohan Yadav",
                "From: Mohan Yadav",
            ),
            "From: Account Holder",
            "To: Account Holder",
        )
        val d = read(received)
        assertEquals("Paytm", d.app)
        assertEquals(TransactionType.Income, d.type)
        assertEquals(SharedKind.Received, d.kind)
        assertEquals("Mohan Yadav", d.counterparty)
        assertEquals(7_400L, d.amount)
        // The account that received it is the one under "To:".
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
    }

    @Test
    fun `a failed payment says so`() {
        val d = read(PhoneReceipts.raw(auto, "Paid Successfully", "Payment Failed"))
        assertEquals(SharedStatus.Failed, d.status)
    }

    @Test
    fun `Paytm is told apart from PhonePe, whose payees can be Paytm QR codes`() {
        assertSame(PaytmReceipt, PaymentAppReceipts.appOf(ReceiptLayout.of(picture(auto))))
        // The brand is the logo at the foot; a receipt with no logo but its own words is still Paytm's.
        assertSame(PaytmReceipt, PaymentAppReceipts.appOf(ReceiptLayout.of(picture(auto.filterNot { it.text == "paytm" }))))
        // A PhonePe payment to "paytmqr…@ptys" is not a Paytm receipt.
        assertSame(PhonePeReceipt, PaymentAppReceipts.appOf(ReceiptLayout.of(picture(PhoneReceipts.phonePeShop))))
        assertSame(KreditPeReceipt, PaymentAppReceipts.appOf(ReceiptLayout.of(picture(PhoneReceipts.kreditPeBells))))
        assertEquals("PhonePe", read(PhoneReceipts.phonePeShop).app)
    }

    @Test
    fun `Paytm's "To Your" receipt is a self transfer to another of the user's accounts`() {
        val lines = listOf(
            line("Money Sent Successfully", 0, 10, 100, 20),
            line("₹1,000", 0, 30, 80, 40),
            line("Rupees One Thousand Only", 0, 50, 150, 60),
            line("To Your: Union Bank Of India - 1234", 0, 70, 200, 80),
            line("From: Kusumanchi Sai", 0, 90, 150, 100),
            line("State Bank of India - 5678", 0, 110, 180, 120),
            line("UPI Ref No: 501223406492", 0, 130, 180, 140),
            line("08:13 PM, 12 Jan 2025", 0, 150, 150, 160),
            line("Powered by UPI | YES BANK", 0, 170, 180, 180),
        )
        val d = read(lines)
        assertEquals("Paytm", d.app)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SelfTransferCue.Words, d.selfTransfer)
        assertEquals(100_000L, d.amount)
        assertEquals(SharedStatus.Completed, d.status)
        assertNull(d.counterparty)
        assertEquals("501223406492", d.reference)
        assertEquals(LocalDate.of(2025, 1, 12), d.date)
        assertEquals(LocalTime.of(20, 13), d.time)
        assertEquals(listOf(MaskedNumber("", "5678")), d.maskedNumbers)
        assertEquals(listOf(MaskedNumber("", "1234")), d.otherMaskedNumbers)
        assertTrue("Union Bank Of India" in d.otherAccountText.orEmpty(), d.otherAccountText)
    }
}
