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

/** A mutual fund order, read by [MutualFundOrderReceipt]: the real Groww SIP the owner's phone read, then variations of it. */
class MutualFundOrderReceiptTest {
    private val today = LocalDate.of(2026, 10, 14)

    private fun read(lines: List<PictureLine>): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture(lines), CurrencySpec.INR, today)).draft

    private fun replaced(text: String, with: String) = PhoneReceipts.mutualFundSip.map { if (it.text == text) it.copy(text = with) else it }

    @Test
    fun `a real SIP instalment`() {
        val d = read(PhoneReceipts.mutualFundSip)
        assertEquals("mutual fund", d.app)
        assertEquals(50_000L, d.amount)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SharedStatus.Completed, d.status)
        // The fund, without the arrow that opens it.
        assertEquals("Example Small Cap Fund Direct Growth", d.counterparty)
        // When the auto-payment went through - the bank's entry falls then - not when the order was placed or completed.
        assertEquals(LocalDate.of(2026, 10, 12), d.date)
        assertEquals(LocalTime.of(0, 0), d.time)
        assertEquals("HDFC BANK XX", d.accountText)
        // The folio number is the user's, not the payment's.
        assertNull(d.reference)
    }

    @Test
    fun `a redemption is income`() {
        assertEquals(TransactionType.Income, read(replaced("New SIP • Completed", "Redemption • Completed")).type)
        // The buttons at the bottom offer to invest more on every order: they don't make a redemption a purchase.
        assertEquals(TransactionType.Expense, read(PhoneReceipts.mutualFundSip).type)
    }

    @Test
    fun `without the payment's step, the order's date is when it was placed`() {
        val noStep = PhoneReceipts.mutualFundSip.filterNot { it.text == "Auto-payment confirmed" }
        val d = read(noStep)
        assertEquals(LocalDate.of(2026, 10, 8), d.date)
        assertEquals(LocalTime.of(10, 37), d.time)
    }

    @Test
    fun `a one-time order paid now, and one that failed`() {
        val oneTime = replaced("Auto-payment confirmed", "Payment successful")
        assertEquals(LocalDate.of(2026, 10, 12), read(oneTime).date)
        assertEquals(SharedStatus.Failed, read(replaced("New SIP • Completed", "New SIP • Failed")).status)
    }
}
