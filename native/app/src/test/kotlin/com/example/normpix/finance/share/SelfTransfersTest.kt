package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PhoneReceipts.line
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * How a self transfer is told from what was shared: the words, the names on both sides, and which numbers are the other
 * side's. No real receipt of a self transfer has been seen yet, so the pictures here are stand-ins laid out like the real
 * receipts in [PhoneReceipts], with the other side showing the user's name and another account of theirs.
 */
class SelfTransfersTest {
    private val today = LocalDate.of(2026, 10, 25)

    private fun parse(text: String) = assertIs<SharedTextReading.Draft>(SharedTransactionParser.read(text, today = today)).draft

    private fun read(lines: List<PictureLine>) =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", PhoneReceipts.picture(lines), CurrencySpec.INR, today)).draft

    // --------------------------------------------------------------- words

    @Test
    fun `the ways a message says it was a self transfer`() {
        listOf(
            "Self Transfer", "SELF TRF", "Self-transfer successful", "Transferred to self", "Sent to Self Account", "Paid to self",
            "Money sent to yourself", "Transfer to your own account", "Own account transfer", "Between your accounts", "To: RAHUL MEHTA (Self)",
        ).forEach { assertTrue(SelfTransferWords.saidIn(it), it) }
    }

    @Test
    fun `other words with self or own in them are not one`() {
        listOf(
            "Paid to Swiggy", "Selfridges", "Self care salon", "Receiver's bank has confirmed deposit of money to Mr RAVI's bank account",
            "Unknown account", "Your account XX1234 is debited", "Shell petrol pump",
        ).forEach { assertFalse(SelfTransferWords.saidIn(it), it) }
    }

    // --------------------------------------------------------------- names

    @Test
    fun `the same person whatever the case, the order or a title in front`() {
        assertTrue(samePerson("RAHUL MEHTA", "Rahul Mehta"))
        assertTrue(samePerson("Mr RAHUL MEHTA", "RAHUL MEHTA"))
        assertTrue(samePerson("MEHTA RAHUL", "Rahul Mehta"))
        assertFalse(samePerson("Rahul Mehta", "Rahul Sharma"))
        assertFalse(samePerson("Rahul Mehta", null))
        // Too little of a name to tell anyone apart by.
        assertFalse(samePerson("Al", "AL"))
    }

    // ------------------------------------------------------------ messages

    @Test
    fun `a bank's message that shows both accounts says which one is the other side's`() {
        val out = parse("Rs.5000.00 debited from A/c XX1234 to A/c XX5678 on 24-10-26. IMPS Ref No 612345678901")
        assertEquals(TransactionType.Expense, out.type)
        assertEquals(listOf(MaskedNumber("", "1234")), out.maskedNumbers)
        assertEquals(listOf(MaskedNumber("", "5678")), out.otherMaskedNumbers)
        assertNull(out.selfTransfer, "the words don't say so: only the numbers can")

        val into = parse("Rs.5000.00 credited to your A/c XX5678 from A/c XX1234 on 24-10-26. IMPS Ref No 612345678901")
        assertEquals(TransactionType.Income, into.type)
        assertEquals(listOf(MaskedNumber("", "5678")), into.maskedNumbers)
        assertEquals(listOf(MaskedNumber("", "1234")), into.otherMaskedNumbers)
    }

    @Test
    fun `the user's own account is never the other side's`() {
        // "to your A/c" is the user's; and "to self" is not followed across "from" to the account it came from.
        val credit = parse("Rs 250 credited to your A/c XX1234 on 24-10-26")
        assertEquals(listOf(MaskedNumber("", "1234")), credit.maskedNumbers)
        assertTrue(credit.otherMaskedNumbers.isEmpty())
        val self = parse("Rs.2,000.00 sent to self from HDFC Bank A/c XX1234 on 24-10-26. UPI Ref 612345678902")
        assertEquals(SelfTransferCue.Words, self.selfTransfer)
        assertEquals(listOf(MaskedNumber("", "1234")), self.maskedNumbers)
        assertTrue(self.otherMaskedNumbers.isEmpty())
        assertNull(self.counterparty)
        // An ordinary payment's numbers are where they always were.
        val paid = parse("Sent Rs.250.00 From HDFC Bank A/C *1234 To SWIGGY On 10/10/26 Ref 428312345678")
        assertEquals(listOf(MaskedNumber("", "1234")), paid.maskedNumbers)
        assertTrue(paid.otherMaskedNumbers.isEmpty())
    }

    @Test
    fun `a phone number or a UPI id on the other side is not an account of theirs`() {
        assertTrue(otherSideNumbers(listOf("+91 ••••• •5678", "Mobile XXXXXX5678", "XXXX5678@ybl")).isEmpty())
        assertEquals(listOf(MaskedNumber("", "5678")), otherSideNumbers(listOf("Rahul Mehta", "XXXXXXXX5678")))
    }

    // ------------------------------------------------------------ receipts

    @Test
    fun `PhonePe's self transfer shows the user's other account under their name`() {
        val draft = read(phonePeSelf)
        assertEquals("PhonePe", draft.app)
        assertEquals(TransactionType.Expense, draft.type)
        assertEquals(500_000L, draft.amount)
        assertEquals("Rahul Mehta", draft.counterparty)
        assertEquals(listOf(MaskedNumber("", "1234")), draft.maskedNumbers)
        assertEquals(listOf(MaskedNumber("", "5678")), draft.otherMaskedNumbers)
        assertEquals("523456789012", draft.reference)
    }

    @Test
    fun `Google Pay's self transfer names the user on both sides`() {
        val draft = read(googlePaySelf)
        assertEquals("Google Pay", draft.app)
        assertEquals(TransactionType.Expense, draft.type)
        assertEquals(500_000L, draft.amount)
        assertEquals(SelfTransferCue.SameName, draft.selfTransfer)
        assertEquals(listOf(MaskedNumber("", "1234")), draft.maskedNumbers)
        assertTrue("State Bank of India" in draft.otherAccountText.orEmpty(), draft.otherAccountText)
    }

    @Test
    fun `Paytm's self transfer shows a bank and its digits on both sides`() {
        val draft = read(paytmSelf)
        assertEquals("Paytm", draft.app)
        assertEquals(TransactionType.Expense, draft.type)
        assertEquals(200_000L, draft.amount)
        // The bank under the name is that side's account, not the rest of the name.
        assertEquals("Rahul Mehta", draft.counterparty)
        assertEquals(SelfTransferCue.SameName, draft.selfTransfer)
        assertEquals(listOf(MaskedNumber("", "1234")), draft.maskedNumbers)
        assertEquals(listOf(MaskedNumber("", "5678")), draft.otherMaskedNumbers)
    }

    @Test
    fun `a receipt that says self transfer in so many words is one, whichever app`() {
        val worded = phonePeSelf.map { if (it.text == "Paid to") line("Transferred to Self", it.left, it.top, it.right + 200, it.bottom) else it }
        assertEquals(SelfTransferCue.Words, read(worded).selfTransfer)
    }

    @Test
    fun `everyday receipts are not self transfers, and show no account of the other side's`() {
        for (lines in listOf(PhoneReceipts.phonePeTen, PhoneReceipts.phonePeUber, PhoneReceipts.googlePayUber, PhoneReceipts.googlePayTen, PhoneReceipts.paytmAuto)) {
            val draft = read(lines)
            assertNull(draft.selfTransfer, draft.app)
            assertTrue(draft.otherMaskedNumbers.isEmpty(), "${draft.app}: ${draft.otherMaskedNumbers}")
        }
        // PhonePe's money received: the payer's phone number is under their name, and it is not an account.
        val received = PhoneReceipts.phonePeReceived.map { if (it.text == "+91 ee 0000") line("+91 ••••• •5678", it.left, it.top, it.right, it.bottom) else it }
        assertTrue(read(received).otherMaskedNumbers.isEmpty())
    }

    companion object {
        /** PhonePe's Share of ₹5,000 sent to the user's own SBI account (laid out as [PhoneReceipts.phonePeTen]). */
        val phonePeSelf = listOf(
            line("Transaction Successful", 191, 37, 651, 70),
            line("10:15 am on 24 Oct 2026", 190, 86, 563, 110),
            line("Paid to", 65, 205, 189, 238),
            line("RAHUL MEHTA", 213, 311, 510, 342),
            line("XXXXXXXX5678", 212, 368, 524, 405),
            line("Transfer Details", 171, 504, 447, 546),
            line("PhonePe Transaction ID", 66, 602, 423, 627),
            line("T2610241015000000000009", 65, 659, 560, 687),
            line("Debited from", 66, 744, 259, 769),
            line("H", 64, 811, 219, 859),
            line("XXXXXXXXXX1234", 212, 820, 600, 850),
            line("UTR: 523456789012", 213, 908, 577, 937),
            line("Powered by", 452, 1054, 628, 1089),
            line("UPI AXIS BANK", 402, 1100, 672, 1135),
            line("₹5,000", 880, 311, 1014, 342),
            line("₹5,000", 880, 820, 1013, 850),
        )

        /** Google Pay's Share of ₹5,000 from the user's HDFC account to their SBI one (laid out as [PhoneReceipts.googlePayUber]). */
        val googlePaySelf = listOf(
            line("To RAHUL MEHTA", 380, 433, 700, 466),
            line("R", 559, 290, 578, 355),
            line("₹5,000", 360, 542, 720, 665),
            line("Completed", 473, 747, 666, 782),
            line("24 Oct 2026, 10:15 am", 362, 861, 716, 900),
            line("HDFC Bank 1234", 277, 1056, 601, 1087),
            line("Payment of ₹5,000 completed", 213, 1215, 747, 1256),
            line("Receiver's bank has confirmed deposit of", 213, 1298, 928, 1333),
            line("money to RAHUL MEHTA's bank account", 213, 1342, 904, 1385),
            line("UPI transaction ID", 134, 1556, 422, 1592),
            line("131100000009", 106, 1624, 331, 1651),
            line("To: RAHUL MEHTA (State Bank of India)", 106, 1718, 855, 1747),
            line("From: RAHUL MEHTA (HDFC Bank)", 108, 1871, 769, 1907),
            line("***00-0@okhdfcbank on Google Pay", 108, 1939, 735, 1974),
            line("Google transaction ID", 107, 2029, 492, 2068),
            line("CICAgLtestId09", 107, 2096, 437, 2132),
            line("G Pay", 513, 2507, 612, 2573),
        )

        /** Paytm's Share of ₹2,000 from the user's HDFC account to their SBI one (laid out as [PhoneReceipts.paytmAuto]). */
        val paytmSelf = listOf(
            line("UPI Ref No: 611500000009", 106, 1383, 568, 1420),
            line("Paid Successfully", 243, 316, 671, 360),
            line("₹2,000", 335, 420, 540, 485),
            line("Rupees Two Thousand Only", 246, 538, 668, 572),
            line("To: Rahul Mehta", 106, 920, 453, 962),
            line("State Bank Of India - 5678", 106, 990, 584, 1028),
            line("From: Rahul Mehta", 106, 1156, 570, 1198),
            line("UPI ID: ******0000@ptyes", 106, 1220, 521, 1258),
            line("HDFC Bank - 1234", 106, 1272, 403, 1306),
            line("02:25 PM, 24 Oct 2026", 106, 1445, 483, 1480),
            line("paytm", 253, 1643, 328, 1668),
            line("Powered by UPI", 340, 1645, 535, 1668),
            line("YES BANK", 546, 1645, 656, 1668),
        )
    }
}
