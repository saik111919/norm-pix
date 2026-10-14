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

/** PhonePe's receipts, read by [PhonePeReceipt]: the real ones the owner's phone read ([PhoneReceipts]), then variations of them. */
class PhonePeReceiptTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun read(lines: List<PictureLine>, words: String = ""): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read(words, picture(lines), CurrencySpec.INR, today)).draft

    @Test
    fun `a real PhonePe receipt of a payment to a business`() {
        val d = read(PhoneReceipts.phonePeUber)
        assertEquals("PhonePe", d.app)
        assertEquals(3_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        // The name runs on to a second line; the avatar's "UL" between the lines and the note "Uber" below are not part of it.
        assertEquals("UBER INDIA SYSTEMS PRIVATE LIMITED", d.counterparty)
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
    fun `a PhonePe receipt is told apart from a Google Pay one that names PhonePe`() {
        assertSame(PhonePeReceipt, PaymentAppReceipts.appOf(ReceiptLayout.of(picture(PhoneReceipts.phonePeUber))))
        // Google Pay prints "…@ybl on PhonePe" for a payee who uses PhonePe.
        assertSame(GooglePayReceipt, PaymentAppReceipts.appOf(ReceiptLayout.of(picture(PhoneReceipts.googlePayTen))))
    }
}
