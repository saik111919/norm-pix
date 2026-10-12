package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fixtures are illustrative: written in the style of common Indian bank SMS and payment-app confirmations (HDFC, SBI,
 * ICICI, Axis, Kotak, UPI apps), with made-up numbers. None is a verified copy of what a particular app shares today; add
 * real samples here as they turn up.
 */
class SharedTransactionParserTest {
    private val today = LocalDate.of(2026, 10, 11)

    private fun read(text: String?, ledger: CurrencySpec = CurrencySpec.INR) = SharedTransactionParser.read(text, ledger, today)

    private fun draft(text: String, ledger: CurrencySpec = CurrencySpec.INR): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(read(text, ledger)).draft

    // ------------------------------------------------------------ payments out

    @Test
    fun `HDFC style UPI debit`() {
        val d = draft(
            "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 10/10/26\nRef 428312345678\n" +
                "Not You?\nCall 18002586161/SMS BLOCK UPI to 7308080808",
        )
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SharedKind.Payment, d.kind)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals("SWIGGY", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals("428312345678", d.reference)
        assertEquals(SharedInstrument.BankAccount, d.instrument)
        assertEquals("Ref 428312345678", d.note)
    }

    @Test
    fun `SBI style debit without a currency mark`() {
        val d = draft(
            "Dear UPI user A/C X1234 debited by 250.0 on date 10Oct26 trf to SWIGGY Refno 428312345678. " +
                "If not u? call 1800111109. -SBI",
        )
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("SWIGGY", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals("428312345678", d.reference)
    }

    @Test
    fun `ICICI style debit names the payee before credited`() {
        val d = draft(
            "ICICI Bank Acct XX123 debited for Rs 250.00 on 10-Oct-26; SWIGGY credited. UPI:428312345678. " +
                "Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
        )
        assertEquals(25_000L, d.amount)
        // "credited" is the payee's side, in the next clause: the account was debited.
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("SWIGGY", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals("428312345678", d.reference)
    }

    @Test
    fun `Axis style UPI path carries the reference and the merchant`() {
        val d = draft(
            "INR 250.00 debited\nA/c no. XX1234\n10-10-26, 12:34:56\nUPI/P2M/428312345678/SWIGGY\n" +
                "Not you? SMS BLOCKUPI Cust ID to 919951860002\nAxis Bank",
        )
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("SWIGGY", d.counterparty)
        assertEquals("428312345678", d.reference)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(12, 34, 56), d.time)
    }

    @Test
    fun `Kotak style debit to a UPI id`() {
        val d = draft(
            "Sent Rs.250.00 from Kotak Bank AC X1234 to swiggy@icici on 10-10-26.UPI Ref 428312345678. " +
                "Not you, https://kotak.com/KBANKT/Fraud",
        )
        assertEquals(25_000L, d.amount)
        assertEquals("swiggy@icici", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals("428312345678", d.reference)
    }

    @Test
    fun `credit card spend leaves the available limit out`() {
        val d = draft(
            "Rs.1,250.00 spent on HDFC Bank Card x1234 at AMAZON on 2026-10-10:12:34:56. Avl Lmt: Rs.48,750.00. " +
                "Not you? Call 18002586161",
        )
        assertEquals(125_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("AMAZON", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(12, 34, 56), d.time)
        assertEquals(SharedInstrument.Card, d.instrument)
    }

    @Test
    fun `card spend with the merchant after the date`() {
        val d = draft(
            "INR 1,250.00 spent using ICICI Bank Credit Card XX1234 on 10-Oct-26 on AMAZON. Avl Limit: INR 48,750.00. " +
                "If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766",
        )
        assertEquals(125_000L, d.amount)
        assertEquals("AMAZON", d.counterparty)
        assertEquals(SharedInstrument.CreditCard, d.instrument)
    }

    @Test
    fun `payment app confirmation`() {
        val d = draft("Payment successful\n₹1,250 paid to Swiggy\nUPI transaction ID: 428312345678\n10 Oct 2026, 12:34 pm")
        assertEquals(125_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SharedStatus.Completed, d.status)
        assertEquals("Swiggy", d.counterparty)
        assertEquals("428312345678", d.reference)
        assertEquals(LocalDate.of(2026, 10, 10), d.date)
        assertEquals(LocalTime.of(12, 34), d.time)
    }

    // ------------------------------------------------------------- money in

    @Test
    fun `bank credit from a UPI id`() {
        val d = draft("Rs.5000.00 credited to HDFC Bank A/c XX1234 on 10-10-26 from VPA john.doe@okaxis (UPI 428312345678)")
        assertEquals(500_000L, d.amount)
        assertEquals(TransactionType.Income, d.type)
        assertEquals(SharedKind.Received, d.kind)
        assertEquals("john.doe@okaxis", d.counterparty)
        assertEquals("428312345678", d.reference)
    }

    @Test
    fun `bank credit from a named sender`() {
        val d = draft("Dear Customer, Acct XX123 is credited with Rs 5,000.00 on 10-Oct-26 from JOHN DOE. UPI:428312345678-ICICI Bank.")
        assertEquals(500_000L, d.amount)
        assertEquals(TransactionType.Income, d.type)
        assertEquals("JOHN DOE", d.counterparty)
        assertEquals("428312345678", d.reference)
    }

    @Test
    fun `money received in a payment app`() {
        val d = draft("Received ₹500 from Rahul Sharma\nTransaction ID: T2610101234567890123")
        assertEquals(50_000L, d.amount)
        assertEquals(TransactionType.Income, d.type)
        assertEquals("Rahul Sharma", d.counterparty)
        assertEquals("T2610101234567890123", d.reference)
    }

    @Test
    fun `sent you is money in, not out`() {
        val d = draft("Rahul Sharma has sent you ₹500 on Google Pay")
        assertEquals(TransactionType.Income, d.type)
        assertEquals("Rahul Sharma", d.counterparty)
    }

    // --------------------------------------------------- refunds and reversals

    @Test
    fun `refund is income`() {
        val d = draft("Refund of ₹499 from Amazon credited to your ICICI Bank account on 09-10-26. Ref 428399999999")
        assertEquals(49_900L, d.amount)
        assertEquals(TransactionType.Income, d.type)
        assertEquals(SharedKind.Refund, d.kind)
        assertEquals("Amazon", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 9), d.date)
        assertEquals("Refund · Ref 428399999999", d.note)
    }

    @Test
    fun `reversal of a failed payment is money back, not a failure`() {
        val d = draft("Rs 250.00 credited to your A/c XX1234 towards reversal of failed UPI txn 428312345678")
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Income, d.type)
        assertEquals(SharedKind.Reversal, d.kind)
        assertEquals(SharedStatus.Completed, d.status)
    }

    // ------------------------------------------------------ failed and pending

    @Test
    fun `failed payment says so and is not a refund`() {
        val d = draft("Your payment of ₹250 to Swiggy failed. Any amount debited will be refunded within 3-5 working days.")
        assertEquals(SharedStatus.Failed, d.status)
        assertEquals(SharedKind.Payment, d.kind)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(25_000L, d.amount)
        assertEquals("Swiggy", d.counterparty)
        assertNull(d.date)
    }

    @Test
    fun `pending payment`() {
        val d = draft("Payment of ₹250 to Swiggy is pending. We will update you in 48 hours.")
        assertEquals(SharedStatus.Pending, d.status)
        assertEquals("Swiggy", d.counterparty)
    }

    @Test
    fun `a request is not a payment and has no direction`() {
        val d = draft("Rahul Sharma has requested ₹500 from you on Google Pay")
        assertEquals(SharedStatus.Requested, d.status)
        assertNull(d.type)
        assertEquals(SharedKind.Unknown, d.kind)
    }

    // ------------------------------------------------------------ amounts

    @Test
    fun `amount formats`() {
        assertEquals(125_000L, draft("₹1,250 paid to X").amount)
        assertEquals(125_000L, draft("INR 1250.00 debited").amount)
        assertEquals(25_000L, draft("Rs. 250 paid").amount)
        assertEquals(25_000L, draft("Rs 250/- paid").amount)
        assertEquals(25_050L, draft("Rs.250.5 debited").amount)
        assertEquals(1_23_456_78L, draft("Rs 1,23,456.78 debited").amount)
        assertEquals(50_000L, draft("Paid 500 INR to X").amount)
    }

    @Test
    fun `balances limits fees and cashback are not the amount`() {
        assertEquals(25_000L, draft("Rs.250.00 debited from A/c XX1234 on 10-10-26. Avl Bal Rs.12,345.67").amount)
        assertEquals(25_000L, draft("Avl. Bal: INR 9,000.00. Rs 250 debited from your account").amount)
        assertEquals(1_00_000L, draft("Rs 1,000 paid to Airtel. Convenience fee Rs 10").amount)
        assertEquals(25_000L, draft("₹250 paid to Swiggy. You won ₹10 cashback!").amount)
        assertEquals(49_900L, draft("Rs 499 spent on card. Total due Rs 5,000. Min amt due Rs 250").amount)
    }

    @Test
    fun `two different amounts that both moved are left for the user`() {
        val d = draft("Rs 100 and Rs 200 debited from your account")
        assertNull(d.amount)
        assertEquals(listOf(10_000L, 20_000L), d.ambiguousAmounts)
    }

    @Test
    fun `references, account numbers and phone numbers are never amounts`() {
        val d = draft("Payment successful. Ref No 1250000. Order ID 500100. A/c XX9999. Call 18002586161")
        assertNull(d.amount)
        assertEquals("1250000", d.reference)
        assertEquals(25_000L, draft("A/c no. 1234 INR 250 debited").amount)
    }

    @Test
    fun `malformed numbers are not read as amounts`() {
        assertNull(draft("Rs 1,2,3 debited").amount)
        assertNull(draft("Rs 250.123 debited").amount)
        assertNull(draft("Rs 0.00 debited").amount)
    }

    @Test
    fun `an amount in another currency is not converted`() {
        val d = draft("Paid $25.00 to Netflix")
        assertNull(d.amount)
        assertEquals("$", d.foreignCurrency)
        assertEquals(2_500L, draft("Paid $25.00 to Netflix", CurrencySpec.byCode("USD")).amount)
        // "Rs" is a rupee of India, Sri Lanka or Nepal.
        assertEquals(25_000L, draft("Rs 250 paid", CurrencySpec.byCode("LKR")).amount)
    }

    // ------------------------------------------------------- missing fields

    @Test
    fun `no amount still reads the rest`() {
        val d = draft("UPI payment to Swiggy successful. Ref 428312345678")
        assertNull(d.amount)
        assertTrue(d.ambiguousAmounts.isEmpty())
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("Swiggy", d.counterparty)
    }

    @Test
    fun `an amount alone is not an expense`() {
        val d = draft("Rs 500 on 10-10-26 Ref 428312345678")
        assertEquals(50_000L, d.amount)
        assertNull(d.type)
        assertEquals(SharedKind.Unknown, d.kind)
        assertNull(d.counterparty)
    }

    @Test
    fun `no date, no merchant`() {
        val d = draft("Rs 250 debited")
        assertNull(d.date)
        assertNull(d.time)
        assertFalse(d.dateUnreadable)
        assertNull(d.counterparty)
        assertNull(d.reference)
        assertNull(d.note)
    }

    // --------------------------------------------------------------- dates

    @Test
    fun `date formats`() {
        val oct10 = LocalDate.of(2026, 10, 10)
        assertEquals(oct10, draft("Rs 1 debited on 10-10-2026").date)
        assertEquals(oct10, draft("Rs 1 debited on 10.10.26").date)
        assertEquals(oct10, draft("Rs 1 debited on 10 October 2026").date)
        assertEquals(oct10, draft("Rs 1 debited on Oct 10, 2026").date)
        assertEquals(oct10, draft("Rs 1 debited on 10th Oct").date)
        // Without a year: the latest such day that isn't after today.
        assertEquals(LocalDate.of(2025, 12, 15), draft("Rs 1 debited on 15 Dec").date)
        assertEquals(oct10, draft("Paid ₹1 to X yesterday").date)
    }

    @Test
    fun `a date that isn't real is reported, never replaced`() {
        val feb31 = draft("Rs 250 debited on 31-02-26")
        assertNull(feb31.date)
        assertTrue(feb31.dateUnreadable)
        // Day first: 10/25/26 has no 25th month.
        assertTrue(draft("Rs 250 debited on 10/25/26").dateUnreadable)
        // The first date written is the transaction's: a later valid one is not swapped in.
        assertTrue(draft("Rs 250 debited on 32-10-26, statement 10-10-26").dateUnreadable)
    }

    @Test
    fun `a due date is not the transaction date`() {
        val d = draft("Bill of Rs 499 paid. Next due date 15-11-26")
        assertNull(d.date)
        assertFalse(d.dateUnreadable)
        assertEquals(LocalDate.of(2026, 10, 10), draft("Rs 250 debited by card on 10-10-26").date)
    }

    @Test
    fun `times with am and pm`() {
        assertEquals(LocalTime.of(21, 5), draft("Paid ₹1 to X on 10 Oct 2026 at 9:05 PM").time)
        assertEquals(LocalTime.of(0, 30), draft("Paid ₹1 to X on 10 Oct 2026 12:30 am").time)
        assertNull(draft("Paid ₹1 to X on 10 Oct 2026 25:61").time)
    }

    // -------------------------------------------------- other languages, input

    @Test
    fun `Hindi bank message with Devanagari digits`() {
        val d = draft("आपके खाते XX1234 से रु. २५०.०० डेबिट किए गए। UPI Ref 428312345678")
        assertEquals(25_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals("428312345678", d.reference)
        assertEquals(TransactionType.Income, draft("आपके खाते में रु. 500 जमा किए गए").type)
    }

    @Test
    fun `odd whitespace and control characters`() {
        val d = draft("\uFEFFSent\u00A0Rs.250.00\r\nTo\u2007SWIGGY\u0007\nOn 10/10/26")
        assertEquals(25_000L, d.amount)
        assertEquals("SWIGGY", d.counterparty)
    }

    @Test
    fun `empty, too long and unrelated text`() {
        assertEquals(SharedTextReading.Empty, read(null))
        assertEquals(SharedTextReading.Empty, read(""))
        assertEquals(SharedTextReading.Empty, read(" \n\t "))
        assertEquals(SharedTextReading.Empty, read("\u200B\uFEFF"))
        assertEquals(SharedTextReading.TooLong, read("Rs 250 paid ".repeat(400)))
        assertEquals(SharedTextReading.NotATransaction, read("Check out this link https://example.com/page"))
        assertEquals(SharedTextReading.NotATransaction, read("See you at 7 tomorrow!"))
    }

    // ------------------------------------------------------- masked numbers

    private fun masked(text: String) = SharedTransactionParser.maskedNumbers(SharedTransactionParser.normalize(text))

    @Test
    fun `the digits a bank message shows of an account or card`() {
        assertEquals(listOf(MaskedNumber("", "1234")), masked("Rs 250 debited from A/c XX1234 on 10-10-26"))
        assertEquals(listOf(MaskedNumber("", "1234")), masked("Sent from HDFC Bank A/C *1234"))
        assertEquals(listOf(MaskedNumber("", "4455")), masked("spent on ICICI Bank Card XX4455"))
        assertEquals(listOf(MaskedNumber("", "4455")), masked("Card ****4455 used"))
        assertEquals(listOf(MaskedNumber("", "4455")), masked("Card ••••4455 used"))
        assertEquals(listOf(MaskedNumber("", "3588")), masked("Debited from XXXXXXXXXX3588"))
        assertEquals(listOf(MaskedNumber("652850", "55")), masked("HDFC Bank 652850XXXXXXXX55"))
        assertEquals(listOf(MaskedNumber("5282", "1234")), masked("Card 5282 XXXX XXXX 1234"))
        assertEquals(listOf(MaskedNumber("5282", "1234")), masked("Card 5282-XXXX-XXXX-1234"))
    }

    @Test
    fun `digits said in words`() {
        assertEquals(listOf(MaskedNumber("", "4455")), masked("Your card ending 4455 was used"))
        assertEquals(listOf(MaskedNumber("", "4455")), masked("account ending with 4455"))
        assertEquals(listOf(MaskedNumber("", "4455")), masked("Card ending in 4455."))
        assertEquals(listOf(MaskedNumber("", "4455")), masked("the last 4 digits of your card: 4455"))
        // Other things that end: an offer, a time.
        assertEquals(emptyList(), masked("Offer ending soon. Ends tomorrow"))
    }

    @Test
    fun `things that are not the user's account number`() {
        // A UPI id's hidden part, with or without a space before the @.
        assertEquals(emptyList(), masked("To: Mr AKBAR ••••5486@ybl on PhonePe"))
        assertEquals(emptyList(), masked("Q505076279 @ybl\n••••5486 @ybl"))
        assertEquals(emptyList(), masked("From: SAI ••••10-5@okhdfcbank on Google Pay"))
        // A masked mobile number, whichever way it is written.
        assertEquals(emptyList(), masked("Rahul Sharma\n+91 98XXXXXX10"))
        assertEquals(emptyList(), masked("+91 XXXXXX3210"))
        assertEquals(emptyList(), masked("Mobile: XXXXXX3210"))
        assertEquals(emptyList(), masked("98XXXXXX10"))
        // Whole numbers - references, amounts, dates - and words.
        assertEquals(emptyList(), masked("UTR: 225197484310 Rs 250.00 on 10-10-2026 at 12:34 pm"))
        assertEquals(emptyList(), masked("MAXX1234 and xxxx and ****"))
    }

    @Test
    fun `several masked numbers are all reported once`() {
        assertEquals(
            listOf(MaskedNumber("", "1234"), MaskedNumber("", "5678")),
            masked("Rs 250 debited from A/c XX1234 and credited to A/c XX5678. A/c XX1234"),
        )
    }

    @Test
    fun `a draft carries the masked numbers`() {
        val d = draft("Rs 250.00 debited from A/c XX1234 on 10-10-26. UPI Ref 428312345678")
        assertEquals(listOf(MaskedNumber("", "1234")), d.maskedNumbers)
    }

    @Test
    fun `hostile input stays fast`() {
        // Long runs that could make a careless pattern backtrack for ages.
        val texts = listOf(
            "Rs " + "1,".repeat(1_900),
            "to " + "a ".repeat(1_900),
            "1".repeat(3_999),
            "Rs.".repeat(1_300),
            "X".repeat(3_999),
            "••••".repeat(990),
            "1X".repeat(1_990),
            "ending ".repeat(560),
            "last 4 digits of ".repeat(230),
            "\n".repeat(3_999),
            "to\n".repeat(1_300),
        )
        for (text in texts) {
            val started = System.nanoTime()
            read(text)
            val ms = (System.nanoTime() - started) / 1_000_000
            assertTrue(ms < 2_000, "took $ms ms")
        }
    }
}
