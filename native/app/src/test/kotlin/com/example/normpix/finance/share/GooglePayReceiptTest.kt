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
import kotlin.test.assertTrue

/** Google Pay's receipts, read by [GooglePayReceipt]: the real ones the owner's phone read ([PhoneReceipts]), then variations. */
class GooglePayReceiptTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun read(lines: List<PictureLine>, name: String? = null): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture(lines, name), CurrencySpec.INR, today)).draft

    @Test
    fun `a real Google Pay receipt of a payment to a business`() {
        val d = read(PhoneReceipts.googlePayUber)
        assertEquals("Google Pay", d.app)
        assertEquals(3_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("UBER INDIA SYSTEMS PRIVATE LIMITED", d.counterparty)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 12), d.date)
        assertEquals(LocalTime.of(20, 55), d.time)
        assertEquals("131100000001", d.reference)
        // "HDFC Bank 1234": the account's last digits are printed after its bank, with nothing hidden.
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
        // The user's side: the account card, and the bank the "From:" row names.
        assertEquals("HDFC Bank 1234\nHDFC Bank", d.accountText)
    }

    @Test
    fun `the account's digits are read whatever the card's expand arrow came out as`() {
        // The card's chevron, far right on the same row, is a piece of its own and can come out as a letter or a sign.
        for (arrow in listOf("v", "V", "~", "∨", "^")) {
            val lines = PhoneReceipts.googlePayUber.map { if (it.text == "HDFC Bank 1234") it.copy(text = "HDFC Bank 3588") else it } +
                line(arrow, 905, 1056, 940, 1087)
            assertEquals(listOf(MaskedNumber("", "3588")), read(lines).maskedNumbers, "arrow $arrow")
        }
    }

    @Test
    fun `a real Google Pay receipt paid with a RuPay credit card`() {
        val d = read(PhoneReceipts.googlePaySwiggy)
        assertEquals(36_000L, d.amount)
        assertEquals("SWIGGY", d.counterparty)
        assertEquals(LocalTime.of(16, 39), d.time)
        assertEquals("131100000003", d.reference)
        assertEquals(listOf(MaskedNumber("608001", "42")), d.maskedNumbers)
        assertEquals(SharedInstrument.Card, d.instrument)
    }

    @Test
    fun `a real Google Pay receipt whose bell icon came out as a letter`() {
        val d = read(PhoneReceipts.googlePayTen)
        assertEquals(1_000L, d.amount)
        assertEquals("Mr RAVI", d.counterparty)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals("131000000002", d.reference)
        // The payee's "•0000@ybl" is not a number of the user's.
        assertEquals(listOf(MaskedNumber("608001", "42")), d.maskedNumbers)
    }

    @Test
    fun `a ₹ read as a 7 in the sentence is settled by the big figure`() {
        // Before the ₹ was put back: "Payment of 730 completed", and the big figure "30" without its ₹.
        val raw = PhoneReceipts.raw(PhoneReceipts.googlePayUber, "Payment of ₹30 completed", "Payment of 730 completed")
        assertEquals(3_000L, read(raw).amount)
        val swiggy = PhoneReceipts.raw(PhoneReceipts.googlePaySwiggy, "Payment of ₹360 completed", "Payment of 7360 completed")
        assertEquals(36_000L, read(swiggy).amount)
    }

    @Test
    fun `the file name Google Pay gives its picture settles the amount and the payee`() {
        // Even where the picture's figures were read badly, and disagree.
        val garbled = PhoneReceipts.googlePayUber.map {
            when (it.text) {
                "30" -> it.copy(text = "80")
                "Payment of ₹30 completed" -> it.copy(text = "Payment of ₹38 completed")
                "To UBER INDIA SYSTEMS PRIVATE LIMITED" -> it.copy(text = "To UBER INDIA SYSTEMS PRIVATE LIMTED")
                else -> it
            }
        }
        assertNull(read(garbled).amount)
        val d = read(garbled, name = PhoneReceipts.GOOGLE_PAY_UBER_FILE)
        assertEquals(3_000L, d.amount)
        assertEquals("UBER INDIA SYSTEMS PRIVATE LIMITED", d.counterparty)
        assertEquals(TransactionType.Expense, d.type)
        // Paise, as the file writes them.
        assertEquals(4_550L, read(garbled, name = "1791818718 - 45.50 To UBER INDIA SYSTEMS PRIVATE LIMITED on Google Pay.png").amount)
    }

    @Test
    fun `paise read without their point are put back when the other figure has them`() {
        val paise = PhoneReceipts.googlePayUber.map {
            when (it.text) {
                "30" -> it.copy(text = "45.50")
                "Payment of ₹30 completed" -> it.copy(text = "Payment of ₹4550 completed")
                else -> it
            }
        }
        assertEquals(4_550L, read(paise).amount)
    }

    @Test
    fun `money received is income from the sender the title names`() {
        val received = listOf(
            line("R", 514, 296, 566, 349),
            line("From Rahul Sharma", 380, 434, 700, 465),
            line("₹500", 420, 550, 660, 661),
            line("Completed", 472, 754, 665, 791),
            line("9 Oct 2026, 7:12 am", 350, 872, 727, 908),
            line("HDFC Bank 5678", 287, 1030, 650, 1070),
            line("UPI transaction ID", 135, 1314, 422, 1350),
            line("130900000004", 106, 1382, 348, 1409),
            line("To: ACCOUNT HOLDER (HDFC Bank)", 106, 1476, 769, 1504),
            line("From: Rahul Sharma", 108, 1630, 548, 1667),
            line("rahul.s@okaxis on Google Pay", 108, 1690, 700, 1727),
            line("Google transaction ID", 107, 1785, 489, 1827),
            line("CICAgLtestId04", 107, 1847, 400, 1890),
        )
        val d = read(received)
        assertEquals(TransactionType.Income, d.type)
        assertEquals("Rahul Sharma", d.counterparty)
        assertEquals(50_000L, d.amount)
        assertEquals(listOf(MaskedNumber("", "5678")), d.maskedNumbers)
        assertEquals(LocalTime.of(7, 12), d.time)
        assertTrue(d.accountText!!.contains("HDFC Bank"))
    }

    @Test
    fun `without its title, the detail rows say who and which way`() {
        val untitled = PhoneReceipts.googlePayUber.filterNot { it.text.startsWith("To UBER") }
        val d = read(untitled)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("UBER INDIA SYSTEMS PRIVATE LIMITED", d.counterparty)
        assertEquals(3_000L, d.amount)
    }

    @Test
    fun `a failed payment says so`() {
        val failed = PhoneReceipts.googlePayTen.map {
            when (it.text) {
                "Completed" -> it.copy(text = "Failed")
                "n Payment of 10 completed" -> it.copy(text = "n Payment of ₹10 failed")
                else -> it
            }
        }
        assertEquals(SharedStatus.Failed, read(failed).status)
    }

    @Test
    fun `text whose places are not known is read a line at a time`() {
        // The recognizer's text alone, as an older reader handed it on: still Google Pay's, still read by its reader.
        val text = PhoneReceipts.googlePayUber.joinToString("\n") { it.text }
        val d = assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", PictureText(text, headline = "30"), CurrencySpec.INR, today)).draft
        assertEquals("Google Pay", d.app)
        assertEquals(3_000L, d.amount)
        assertEquals("UBER INDIA SYSTEMS PRIVATE LIMITED", d.counterparty)
        assertEquals("131100000001", d.reference)
    }

    @Test
    fun `a Google Pay self transfer is read as money out of the user's account to another of theirs`() {
        val lines = listOf(
            line("Self transfer", 0, 10, 100, 20),
            line("Transferred", 0, 30, 80, 40),
            line("₹4,800", 0, 50, 90, 70),
            line("25 Oct 2026, 11:16 am", 0, 80, 120, 90),
            line("HDFC Bank ••••3588", 0, 100, 150, 110),
            line("UPI transaction ID: 131826136987", 0, 130, 200, 140),
            line("To: Kotak Mahindra Bank ••••7890", 0, 150, 200, 160),
            line("From: HDFC Bank ••••3588", 0, 170, 200, 180),
            line("Google transaction ID: CICAgPjhq5mwIQ", 0, 190, 220, 200),
        )
        val d = read(lines)
        assertEquals("Google Pay", d.app)
        // Which way it went for the user's own side; the import makes it a transfer (see SelfTransfers).
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SelfTransferCue.Words, d.selfTransfer)
        assertEquals(480_000L, d.amount)
        assertEquals(SharedStatus.Completed, d.status)
        // The other side is the user's other account, not a person to name as the payee.
        assertNull(d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 25), d.date)
        assertEquals(LocalTime.of(11, 16), d.time)
        assertEquals("131826136987", d.reference)
        assertEquals(listOf(MaskedNumber("", "3588")), d.maskedNumbers)
        assertTrue("Kotak Mahindra Bank" in d.otherAccountText.orEmpty(), d.otherAccountText)
        assertEquals(listOf(MaskedNumber("", "7890")), d.otherMaskedNumbers)
    }
}
