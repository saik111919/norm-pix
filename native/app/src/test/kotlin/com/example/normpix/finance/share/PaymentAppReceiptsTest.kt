package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PhoneReceipts.line
import com.example.normpix.finance.share.PhoneReceipts.picture
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** What every app's reader stands on: the layout, the amount's sightings, the ₹ put back, and which reader reads what. */
class PaymentAppReceiptsTest {
    private val today = LocalDate.of(2026, 10, 13)

    // -------------------------------------------------------------- the ₹

    @Test
    fun `a first character the recognizer is nearly sure it got wrong is the rupee sign`() {
        // As the owner's phone scored them, character by character.
        assertEquals("₹30", rupeeRestored("730", listOf(0.12f, 0.92f, 0.71f)))
        assertEquals("₹30", rupeeRestored("730", listOf(0.08f, 0.91f, 0.80f)))
        assertEquals("₹360", rupeeRestored("7360", listOf(0.11f, 0.87f, 0.92f, 0.80f)))
        assertEquals("₹10", rupeeRestored("Z10", listOf(0.07f, 0.42f, 0.60f)))
        // A ₹ swallowed into a real digit leaves that digit less sure, not that unsure: it stays.
        assertEquals("10", rupeeRestored("10", listOf(0.29f, 0.68f)))
        assertEquals("10", rupeeRestored("10", listOf(0.59f, 0.78f)))
        // A real 7.
        assertEquals("730", rupeeRestored("730", listOf(0.86f, 0.92f, 0.71f)))
        // Not a figure, too long to be an amount, or no scores to go by.
        assertEquals("2026,", rupeeRestored("2026,", listOf(0.1f, 0.95f, 0.96f, 0.93f, 0.87f)))
        assertEquals("131074460564", rupeeRestored("131074460564", List(12) { if (it == 0) 0.05f else 0.9f }))
        assertEquals("Uber", rupeeRestored("Uber", listOf(0.1f, 0.9f, 0.9f, 0.9f)))
        assertEquals("730", rupeeRestored("730", emptyList()))
    }

    @Test
    fun `a line's words part where a wide gap is between them`() {
        val words = listOf(line("Asha", 213, 311, 300, 342), line("Verma", 312, 311, 510, 342), line("₹10", 948, 309, 1014, 340))
        assertEquals(listOf("Asha Verma", "₹10"), splitAtGaps(words).map { it.text })
        assertEquals(PictureLine("Asha Verma", 213, 311, 510, 342), splitAtGaps(words).first())
    }

    // ------------------------------------------------------------ layout

    @Test
    fun `pieces are put back in reading order, row by row`() {
        val rows = ReceiptLayout.rowsOf(PhoneReceipts.phonePeTen)
        assertEquals(
            listOf(
                "Transaction Successful", "11:34 am on 05 Oct 2026", "Paid to", "Asha Verma ₹10", "Q500000001@ybl", "Payment Details",
                "PhonePe Transaction ID", "T2610051134000000000002", "Debited from", "H XXXXXXXXXX1234 10", "UTR: 223456789012",
                "Powered by", "UPIPAXIS BANK",
            ),
            rows.map { it.text },
        )
        // A small piece beside a large one, top-aligned with it, is on its row.
        val big = listOf(line("₹45", 400, 550, 600, 661), line(".50", 610, 552, 660, 590))
        assertEquals(listOf("₹45 .50"), ReceiptLayout.rowsOf(big).map { it.text })
    }

    // ----------------------------------------------------------- amounts

    @Test
    fun `what a piece can be read as an amount`() {
        fun minor(piece: String) = amountSighting(piece)?.minor(2)
        assertEquals(3_000L, minor("₹30"))
        assertEquals(1_000L, minor("Z10"))
        assertEquals(125_050L, minor("₹1,250.50"))
        assertEquals(125_050L, minor("1,250 50"))
        assertEquals(4_550L, minor("45,50"))
        assertEquals(4_550L, minor("45.5"))
        assertEquals(125_000L, minor("1.250"))
        assertEquals(10_000_000L, minor("₹1,00,000"))
        assertEquals(50_000L, minor("+₹500"))
        assertEquals(1_000L, minor("1O"))
        assertEquals(true, amountSighting("Z10")?.marked)
        assertEquals(false, amountSighting("730")?.marked)
        // Not amounts: a year, an id, a time, a percentage, grouping no money is written in, nothing but a mark.
        listOf("2026", "131074460564", "10:07", "85%", "12,34,5", "₹", "Paid", "1,2345").forEach { assertNull(minor(it), it) }
    }

    @Test
    fun `the amount is what every sighting can be read as`() {
        fun settled(vararg pieces: String) = settle(pieces.map { amountSighting(it)!! }, 2)
        // A ₹ read as a 7 (or a 2) where the other sighting has it right, or has it dropped.
        assertEquals(3_000L, settled("730", "₹30").amount)
        assertEquals(3_000L, settled("730", "30").amount)
        assertEquals(25_000L, settled("2250", "250").amount)
        // A real 7 is not taken away when nothing says it is a ₹.
        assertEquals(73_000L, settled("730").amount)
        assertEquals(75_000L, settled("750", "₹750").amount)
        // A point lost in one sighting.
        assertEquals(4_550L, settled("4550", "45.50").amount)
        // The same figure everywhere.
        assertEquals(1_000L, settled("₹10", "10").amount)
        // Sightings that can't be the same amount: left to the user.
        val apart = settled("₹30", "₹45")
        assertNull(apart.amount)
        assertEquals(listOf(3_000L, 4_500L), apart.ambiguous)
        assertNull(settle(emptyList(), 2).amount)
    }

    // ------------------------------------------------------------ readers

    @Test
    fun `a picture from no app with a reader of its own is read by the general rules`() {
        val bill = listOf(line("Paid ₹250 to Swiggy", 100, 100, 600, 140), line("UPI Ref 428312345678", 100, 160, 600, 200))
        val d = assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture(bill), CurrencySpec.INR, today)).draft
        assertNull(d.app)
        assertNull(d.accountText)
        assertEquals(25_000L, d.amount)
        assertEquals("Swiggy", d.counterparty)
        // Words alone, too.
        assertNull(assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("Paid ₹250 to Swiggy", null, CurrencySpec.INR, today)).draft.app)
        assertEquals(SharedTextReading.Empty, PaymentAppReceipts.read("", null, CurrencySpec.INR, today))
    }

    @Test
    fun `words shared with an app's receipt fill in only what the receipt didn't show`() {
        val noReference = PhoneReceipts.phonePeTen.filterNot { it.text.startsWith("UTR") || it.text.startsWith("T26") }
        val d = assertIs<SharedTextReading.Draft>(
            PaymentAppReceipts.read("Paid ₹99 to Someone Else. UPI Ref 998877665544", picture(noReference), CurrencySpec.INR, today),
        ).draft
        assertEquals("PhonePe", d.app)
        // The receipt's own amount and payee stay; the reference it lacked comes from the words.
        assertEquals(1_000L, d.amount)
        assertEquals("Asha Verma", d.counterparty)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("998877665544", d.reference)
    }

    @Test
    fun `an app's screen its reader gets nothing from goes to the general rules`() {
        // PhonePe's, by its name, but not a payment screen its reader knows: no "Paid to", no "Debited from".
        val recharge = listOf(
            line("Recharge Successful", 191, 37, 651, 70),
            line("Airtel Prepaid 98XXXXXX10", 191, 120, 700, 160),
            line("Paid ₹299 to Airtel", 191, 200, 700, 240),
            line("PhonePe Transaction ID", 66, 300, 423, 330),
            line("T2610121129280000000009", 65, 350, 553, 380),
        )
        val d = assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture(recharge), CurrencySpec.INR, today)).draft
        assertNull(d.app)
        assertEquals(29_900L, d.amount)
        assertEquals("Airtel", d.counterparty)
    }

    @Test
    fun `a picture that looks a little like an app's is not read as one`() {
        // "Paid to" alone is printed by other apps too.
        val other = listOf(line("Paid to", 65, 205, 189, 238), line("Asha Verma", 213, 311, 510, 342), line("₹10", 948, 309, 1014, 340))
        assertNull(PaymentAppReceipts.appOf(ReceiptLayout.of(picture(other))))
    }
}
