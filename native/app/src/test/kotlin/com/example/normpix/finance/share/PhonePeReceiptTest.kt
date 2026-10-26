package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PhoneReceipts.line
import com.example.normpix.finance.share.PhoneReceipts.picture
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** PhonePe's receipts, read by [PhonePeReceipt]: the real ones the owner's phone read ([PhoneReceipts]), then variations of them. */
class PhonePeReceiptTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun read(lines: List<PictureLine>, words: String = "", on: LocalDate = today): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read(words, picture(lines), CurrencySpec.INR, on)).draft

    /** The day the shop payment ([PhoneReceipts.phonePeShop]) was read on, four days after it was made. */
    private val afterShop = LocalDate.of(2026, 10, 19)

    private fun shop(lines: List<PictureLine> = PhoneReceipts.phonePeShop) = read(lines, on = afterShop)

    /** The shop payment's date and time row, as the recognizer read it. */
    private val shopDate = "10:33 pm on 15 Oct 2026"

    @Test
    fun `a real PhonePe receipt of a payment to a business`() {
        val d = read(PhoneReceipts.phonePeUber)
        assertEquals("PhonePe", d.app)
        assertEquals(3_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        // The name runs on to a second line; the avatar's "UL" between the lines and the note "Uber" below are not part of it.
        // PhonePe prints it in capitals; it is written back as a name.
        assertEquals("Uber India Systems Private Limited", d.counterparty)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 12), d.date)
        assertEquals(LocalTime.of(11, 29), d.time)
        // The bank's UTR, not PhonePe's own T… id.
        assertEquals("812345678901", d.reference)
        // Only the user's own account's digits: below "Debited from".
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
        assertEquals("XXXXXXXXXX1234", d.accountText)
    }

    @Test
    fun `a real PhonePe receipt of a payment to a person`() {
        val d = read(PhoneReceipts.phonePeTen)
        // "₹10" beside the name, and "10" (its ₹ swallowed) beside the account: the same amount.
        assertEquals(1_000L, d.amount)
        assertEquals("Asha Verma", d.counterparty)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(LocalDate.of(2026, 10, 5), d.date)
        assertEquals(LocalTime.of(11, 34), d.time)
        assertEquals("223456789012", d.reference)
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
        // "Powered by UPI AXIS BANK" is PhonePe's bank: it is not part of what the receipt says of the user's account.
        assertEquals("H XXXXXXXXXX1234", d.accountText)
    }

    @Test
    fun `a real PhonePe receipt of money received names the sender, not their phone number below`() {
        val d = read(PhoneReceipts.phonePeReceived)
        assertEquals(TransactionType.Income, d.type)
        assertEquals("Ravi Kumar", d.counterparty)
        assertEquals(4_000L, d.amount)
        assertEquals(LocalDate.of(2026, 10, 11), d.date)
        assertEquals(LocalTime.of(1, 23), d.time)
        assertEquals("323456789012", d.reference)
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
    }

    @Test
    fun `a real PhonePe AutoPay names the payee without its avatar's letter`() {
        val d = read(PhoneReceipts.phonePeAutopay)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("Google", d.counterparty)
        assertEquals(8_900L, d.amount)
        assertEquals(LocalTime.of(14, 14), d.time)
        assertEquals("423456789012", d.reference)
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
        assertEquals("XXXXXXXXXX1234", d.accountText)
    }

    @Test
    fun `the amount is what both of its places agree on`() {
        // A ₹ read as a "7" in one place only: the other place settles it.
        val oneMisread = PhoneReceipts.raw(PhoneReceipts.phonePeTen, "₹10", "710")
        assertEquals(1_000L, read(oneMisread).amount)
        // Paise read with a space for the point in one place, right in the other.
        val paise = PhoneReceipts.phonePeTen.map {
            when (it.text) {
                "₹10" -> it.copy(text = "₹1,250 50")
                "10" -> it.copy(text = "₹1,250.50")
                else -> it
            }
        }
        assertEquals(125_050L, read(paise).amount)
        // Two amounts that can't be the same one: neither is taken.
        val different = PhoneReceipts.phonePeTen.map { if (it.text == "10") it.copy(text = "₹45") else it }
        val d = read(different)
        assertNull(d.amount)
        assertEquals(listOf(1_000L, 4_500L), d.ambiguousAmounts.sorted())
    }

    @Test
    fun `a name the recognizer ran together with the amount is still the name`() {
        val merged = PhoneReceipts.phonePeTen.filterNot { it.text == "₹10" }
            .map { if (it.text == "Asha Verma") it.copy(text = "Asha Verma ₹10", right = 1014) else it }
        val d = read(merged)
        assertEquals("Asha Verma", d.counterparty)
        assertEquals(1_000L, d.amount)
    }

    @Test
    fun `money received names the sender and is income`() {
        val received = listOf(
            line("Transaction Successful", 191, 37, 651, 70),
            line("09:15 pm on 11 Oct 2026", 190, 86, 563, 110),
            line("Received from", 65, 205, 300, 238),
            line("Rahul Sharma", 213, 311, 510, 342),
            line("₹1,500", 900, 309, 1014, 340),
            line("rahul.s@okaxis", 212, 368, 524, 405),
            line("Transfer Details", 171, 504, 447, 546),
            line("Transaction ID", 66, 602, 300, 627),
            line("T2610112115000000000003", 65, 659, 560, 687),
            line("Credited to", 66, 744, 259, 769),
            line("XXXXXXXX5678", 212, 820, 560, 850),
            line("₹1,500", 900, 820, 1014, 850),
            line("UTR: 334455667788", 213, 908, 577, 937),
        )
        val d = read(received)
        assertEquals(TransactionType.Income, d.type)
        assertEquals(SharedKind.Received, d.kind)
        assertEquals("Rahul Sharma", d.counterparty)
        assertEquals(150_000L, d.amount)
        assertEquals(listOf(MaskedNumber("", "5678")), d.maskedNumbers)
        assertEquals("334455667788", d.reference)
        assertEquals(LocalTime.of(21, 15), d.time)
    }

    @Test
    fun `a failed or pending payment says so`() {
        fun withStatus(status: String) = PhoneReceipts.phonePeTen.map { if (it.text == "Transaction Successful") it.copy(text = status) else it }
        assertEquals(SharedStatus.Failed, read(withStatus("Transaction Failed")).status)
        assertEquals(SharedStatus.Pending, read(withStatus("Transaction Pending")).status)
    }

    @Test
    fun `a full screenshot - status bar, buttons, support - reads the same`() {
        val screen = PhoneReceipts.phonePeTen.map { it.copy(top = it.top + 300, bottom = it.bottom + 300) } + listOf(
            line("10:26", 40, 20, 130, 52),
            line("85%", 960, 20, 1040, 52),
            line("Share", 120, 1500, 260, 1540),
            line("Split Expense", 420, 1500, 680, 1540),
            line("Contact PhonePe Support", 300, 1650, 780, 1690),
        )
        val d = read(screen)
        assertEquals(1_000L, d.amount)
        assertEquals("Asha Verma", d.counterparty)
        assertEquals(LocalTime.of(11, 34), d.time)
    }

    @Test
    fun `the payee's own account number, shown under their name, is not one of the user's`() {
        val toBank = PhoneReceipts.phonePeTen.map { if (it.text == "Q500000001@ybl") it.copy(text = "XXXXXXX9876") else it }
        assertEquals(listOf(MaskedNumber("", "1234")), read(toBank).maskedNumbers)
    }

    @Test
    fun `a real PhonePe receipt of a payment to a shop - the date, the amount and the card`() {
        val d = shop()
        assertEquals("PhonePe", d.app)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(10_000L, d.amount)
        assertEquals(emptyList(), d.ambiguousAmounts)
        // The name is over three lines, with the ₹100 at the right of the first and the avatar's "SS" beside the second.
        assertEquals("Sri Lakshmi Narayana Medical And General Stores", d.counterparty)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 15), d.date)
        assertEquals(LocalTime.of(22, 33), d.time)
        assertEquals(false, d.dateUnreadable)
        // The UTR, not the T… id (which is the date again, run together) and not the handle.
        assertEquals("324900000001", d.reference)
        // A RuPay credit card on UPI: its first six and last two digits.
        assertEquals(listOf(MaskedNumber("356188", "56")), d.maskedNumbers)
        assertEquals("356188XXXXXXXX56", d.accountText)
    }

    @Test
    fun `a rupee sign read as a digit is not part of the amount when something else settles it`() {
        // One of the two places has the ₹, so the other's "7100" is ₹100.
        assertEquals(10_000L, shop(PhoneReceipts.rawNth(PhoneReceipts.phonePeShop, "₹100", 0, "7100")).amount)
        assertEquals(10_000L, shop(PhoneReceipts.rawNth(PhoneReceipts.phonePeShop, "₹100", 1, "7100")).amount)
        // Whichever character the ₹ came out as, in one place or both.
        for (misread in listOf("Z100", "R100", "*100", "F100", "2100", "7100", "100")) {
            val one = shop(PhoneReceipts.rawNth(PhoneReceipts.phonePeShop, "₹100", 1, misread))
            assertEquals(10_000L, one.amount, "second place read as $misread")
            val first = shop(PhoneReceipts.rawNth(PhoneReceipts.phonePeShop, "₹100", 0, misread))
            assertEquals(10_000L, first.amount, "first place read as $misread")
        }
        assertEquals(10_000L, shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, "₹100", "Z100")).amount)
        assertEquals(10_000L, shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, "₹100", "100")).amount)
        val twoAndSeven = PhoneReceipts.rawNth(PhoneReceipts.rawNth(PhoneReceipts.phonePeShop, "₹100", 0, "2100"), "₹100", 0, "7100")
        assertEquals(10_000L, shop(twoAndSeven).amount)
    }

    @Test
    fun `both places read the rupee sign as the same digit - no wrong amount is picked`() {
        // The case behind the wrong number: "7100" twice agrees with itself, and it used to be taken as ₹7,100.
        // Nothing here says which is right, so the receipt offers both and the person chooses.
        val d = shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, "₹100", "7100"))
        assertNull(d.amount)
        assertEquals(listOf(10_000L, 710_000L), d.ambiguousAmounts.sorted())
        // The same with a 2.
        val twos = shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, "₹100", "2100"))
        assertNull(twos.amount)
        assertEquals(listOf(10_000L, 210_000L), twos.ambiguousAmounts.sorted())
        // Where the receipt spells the amount out, that settles it (PhonePe doesn't, but another app's screen might be shared with it).
        val spelled = PhoneReceipts.raw(PhoneReceipts.phonePeShop, "₹100", "7100") + line("Rupees One Hundred Only", 250, 560, 700, 600)
        assertEquals(10_000L, shop(spelled).amount)
        // Words that fit neither reading: nothing is chosen, and the figure as read is offered beside what the words say.
        val other = PhoneReceipts.raw(PhoneReceipts.phonePeShop, "₹100", "7100") + line("Rupees Five Hundred Only", 250, 560, 700, 600)
        assertNull(shop(other).amount)
        assertEquals(listOf(50_000L, 710_000L), shop(other).ambiguousAmounts.sorted())
    }

    @Test
    fun `an amount that is printed large on a row of its own is still read`() {
        val alone = PhoneReceipts.phonePeShop.filterNot { it.text == "₹100" && it.top < 400 } + line("₹100", 440, 250, 640, 300)
        assertEquals(10_000L, shop(alone).amount)
        assertEquals("Sri Lakshmi Narayana Medical And General Stores", shop(alone).counterparty)
        // Misread there as well as at the debit line.
        val both = PhoneReceipts.raw(alone, "₹100", "7100")
        val d = shop(both)
        assertNull(d.amount)
        assertEquals(listOf(10_000L, 710_000L), d.ambiguousAmounts.sorted())
    }

    @Test
    fun `the amount run into the name's line, or alone in a block, does not change the name`() {
        val merged = PhoneReceipts.raw(PhoneReceipts.phonePeShop, "SRI LAKSHMI NARAYANA", "SRI LAKSHMI NARAYANA ₹100")
            .filterNot { it.text == "₹100" && it.top < 400 }
            .map { if (it.text == "SRI LAKSHMI NARAYANA ₹100") it.copy(right = 1007) else it }
        val d = shop(merged)
        assertEquals("Sri Lakshmi Narayana Medical And General Stores", d.counterparty)
        assertEquals(10_000L, d.amount)
        // The same with the ₹ read as a 7 on the name's line.
        val sevened = merged.map { if (it.text == "SRI LAKSHMI NARAYANA ₹100") it.copy(text = "SRI LAKSHMI NARAYANA 7100") else it }
        assertEquals(10_000L, shop(sevened).amount)
        assertEquals("Sri Lakshmi Narayana Medical And General Stores", shop(sevened).counterparty)
    }

    @Test
    fun `an avatar's initials read as something else do not cut the name short`() {
        for (avatar in listOf("55", "S5", "Ss", "5S", "SS.")) {
            val d = shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, "SS", avatar))
            assertEquals("Sri Lakshmi Narayana Medical And General Stores", d.counterparty, "avatar read as $avatar")
        }
        // A name that really is two lines stays two lines.
        assertEquals("Sri Lakshmi Narayana Medical And General", shop(PhoneReceipts.phonePeShop.filterNot { it.text == "STORES" }).counterparty)
    }

    @Test
    fun `the date and time survive the usual misreads`() {
        fun on(printed: String) = shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, shopDate, printed))
        for (printed in listOf(
            "10:33 pm on 15 Oct 2026",
            "10:33 PM on 15 Oct 2026",
            "10:33 prn on 15 Oct 2026", // "pm" read with an n
            "10.33 pm on 15 Oct 2026", // a point for the colon
            "10:33 pm on I5 Oct 2026", // a 1 read as I
            "10:33 pm on l5 Oct 2026", // or as l
            "10:33 pm on 15 0ct 2026", // an O read as a zero
            "10:33 pm on 15 Oct 2O26", // the year likewise
            "10:33pm on 15 Oct 2026", // no space
            "10:33 pm  on  15  Oct  2026",
        )) {
            val d = on(printed)
            assertEquals(LocalDate.of(2026, 10, 15), d.date, printed)
            assertEquals(LocalTime.of(22, 33), d.time, printed)
            assertEquals(false, d.dateUnreadable, printed)
        }
    }

    @Test
    fun `a date that cannot be read falls back on the date inside PhonePe's own transaction id`() {
        // "T" + yymmdd + hhmm + digits: T2610152233… is 2026-10-15 22:33. Used only when the printed date can't be read.
        val gone = shop(PhoneReceipts.phonePeShop.filterNot { it.text == shopDate })
        assertEquals(LocalDate.of(2026, 10, 15), gone.date)
        assertEquals(LocalTime.of(22, 33), gone.time)
        val garbled = shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, shopDate, "10:33 pm on 15 Qct 2026"))
        assertEquals(LocalDate.of(2026, 10, 15), garbled.date)
        // An id that says a day after today isn't a date the payment can have been made on.
        val early = read(PhoneReceipts.phonePeShop.filterNot { it.text == shopDate }, on = LocalDate.of(2026, 10, 14))
        assertNull(early.date)
        // An id that isn't PhonePe's own shape is never a date: there is none here to read, so none is made up.
        val noId = shop(PhoneReceipts.phonePeShop.filterNot { it.text == shopDate || it.text.startsWith("T26") })
        assertNull(noId.date)
    }

    @Test
    fun `the status survives a tick read as a letter or a mark`() {
        for (junk in listOf("©", "@", "Q", "O", "0")) {
            val d = shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, "Transaction Successful", "$junk Transaction Successful"))
            assertEquals(SharedStatus.Completed, d.status, junk)
        }
        // And the tick on a row of its own.
        for (junk in listOf("©", "@")) {
            val d = shop(PhoneReceipts.phonePeShop + line(junk, 120, 40, 170, 84))
            assertEquals(SharedStatus.Completed, d.status, junk)
            assertEquals(10_000L, d.amount, junk)
        }
        fun withStatus(status: String) = shop(PhoneReceipts.raw(PhoneReceipts.phonePeShop, "Transaction Successful", status)).status
        assertEquals(SharedStatus.Failed, withStatus("Transaction Failed"))
        assertEquals(SharedStatus.Pending, withStatus("Transaction Pending"))
        assertEquals(SharedStatus.Pending, withStatus("Transaction Processing"))
        assertEquals(SharedStatus.Completed, withStatus("Payment Successful"))
    }

    @Test
    fun `the lines in any order, or the time above the status, read the same`() {
        val expected = shop()
        assertEquals(expected, shop(PhoneReceipts.phonePeShop.reversed()))
        assertEquals(expected, shop(PhoneReceipts.phonePeShop.shuffled(Random(7))))
        // The time on the row above the status.
        val swapped = PhoneReceipts.phonePeShop.map {
            when (it.text) {
                "Transaction Successful" -> it.copy(top = 96, bottom = 132)
                shopDate -> it.copy(top = 40, bottom = 84)
                else -> it
            }
        }
        val d = shop(swapped)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals(LocalDate.of(2026, 10, 15), d.date)
        assertEquals(LocalTime.of(22, 33), d.time)
        assertEquals(10_000L, d.amount)
    }

    @Test
    fun `a bank's logo read as text among the card's rows is not the card number or the amount`() {
        val d = shop(PhoneReceipts.phonePeShop + line("SBI", 73, 1000, 190, 1043))
        assertEquals(10_000L, d.amount)
        assertEquals(listOf(MaskedNumber("356188", "56")), d.maskedNumbers)
        assertEquals("Sri Lakshmi Narayana Medical And General Stores", d.counterparty)
    }

    @Test
    fun `the payee's handle is not read as the reference or a number, and PhonePe's own id comes after the UTR`() {
        val d = shop()
        assertEquals("324900000001", d.reference)
        // paytmqr…@ptys is a handle: nothing in it is an account number or a date.
        assertEquals(listOf(MaskedNumber("356188", "56")), d.maskedNumbers)
        // Without the UTR, PhonePe's own id is the reference; the handle never is.
        val noUtr = shop(PhoneReceipts.phonePeShop.filterNot { it.text.startsWith("UTR") })
        assertEquals("T2610152233000000000003", noUtr.reference)
    }

    @Test
    fun `a PhonePe receipt is told apart from a Google Pay one that names PhonePe`() {
        assertSame(PhonePeReceipt, PaymentAppReceipts.appOf(ReceiptLayout.of(picture(PhoneReceipts.phonePeUber))))
        // Google Pay prints "…@ybl on PhonePe" for a payee who uses PhonePe.
        assertSame(GooglePayReceipt, PaymentAppReceipts.appOf(ReceiptLayout.of(picture(PhoneReceipts.googlePayTen))))
    }

    @Test
    fun `a PhonePe transfer to a bank account names its holder and shows the account it went to`() {
        val lines = listOf(
            line("Transaction Successful", 0, 10, 100, 20),
            line("12:27 pm on 22 Aug 2026", 0, 30, 120, 40),
            line("Transfer to", 0, 50, 80, 60),
            line("XXXXXXXXXX1234 ₹1,000", 0, 70, 150, 80),
            line("HDFC Bank", 0, 90, 80, 100),
            line("Banking Name : Kusumanchi Sai", 0, 110, 150, 120),
            line("Transfer Details", 0, 130, 100, 140),
            line("PhonePe Transaction ID T2608221227467285779046", 0, 150, 250, 160),
            line("Debited from", 0, 170, 80, 180),
            line("XXXXXXXX5678 ₹1,000", 0, 190, 150, 200),
            line("UTR: 179259041561", 0, 210, 150, 220),
        )
        val d = read(lines)
        assertEquals("PhonePe", d.app)
        // A transfer to an account is money paid out; it is the user's own only when that account's number is theirs (the import).
        assertEquals(TransactionType.Expense, d.type)
        assertNull(d.selfTransfer)
        assertEquals(100_000L, d.amount)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals("Kusumanchi Sai", d.counterparty)
        assertEquals(LocalDate.of(2026, 8, 22), d.date)
        assertEquals(LocalTime.of(12, 27), d.time)
        assertEquals("179259041561", d.reference)
        assertEquals(listOf(MaskedNumber("", "5678")), d.maskedNumbers)
        assertEquals(listOf(MaskedNumber("", "1234")), d.otherMaskedNumbers)
        assertTrue("HDFC Bank" in d.otherAccountText.orEmpty(), d.otherAccountText)
    }
}
