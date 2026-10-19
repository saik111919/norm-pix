package com.example.normpix.finance.share

import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.PhoneReceipts.picture
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Kredit.Pe's Share of a payment, read by [KreditPeReceipt] ([PhoneReceipts.kreditPeBells]: ₹150 to a shop, a date with no
 * year, the user only by a partly hidden UPI id, and an advertisement at the foot).
 */
class KreditPeReceiptTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val bells = PhoneReceipts.kreditPeBells

    private fun read(lines: List<PictureLine>, on: LocalDate = today): ParsedTransactionDraft =
        assertIs<SharedTextReading.Draft>(PaymentAppReceipts.read("", picture(lines), CurrencySpec.INR, on)).draft

    @Test
    fun `a Kredit Pe payment - amount, payee, status, date, reference and the credit line`() {
        val d = read(bells)
        assertEquals("Kredit.Pe", d.app)
        assertEquals(TransactionType.Expense, d.type)
        assertEquals(SharedKind.Payment, d.kind)
        // The first figure of the screen: the advertisement's "3%" below is no amount.
        assertEquals(15_000L, d.amount)
        assertEquals(emptyList(), d.ambiguousAmounts)
        assertEquals(SharedStatus.Completed, d.status)
        // The "To:" side, not the shop's UPI id under it and not the user's handle after "From".
        assertEquals("Bells And Peppers", d.counterparty)
        assertEquals(LocalDate.of(2026, 10, 7), d.date)
        assertEquals(LocalTime.of(13, 25), d.time)
        assertEquals(false, d.dateUnreadable)
        assertEquals("628000000001", d.reference)
        assertEquals("Kredit.Pe", d.accountText)
    }

    @Test
    fun `a handle's digits are not an account number`() {
        // "xxxxxx7777-1@kphdfc" is a UPI handle: nothing on this screen names an account's digits.
        assertEquals(emptyList(), read(bells).maskedNumbers)
    }

    @Test
    fun `a date with no year is the latest one that is not after today`() {
        // 7 Oct has not come yet in the year of 5 Oct, so it is last year's.
        assertEquals(LocalDate.of(2025, 10, 7), read(bells, on = LocalDate.of(2026, 10, 5)).date)
        // And on the day itself it is that day.
        assertEquals(LocalDate.of(2026, 10, 7), read(bells, on = LocalDate.of(2026, 10, 7)).date)
    }

    @Test
    fun `the account is named only when the screen says what it was paid with`() {
        val layout = ReceiptLayout.of(picture(bells.filterNot { it.text == "Paid with" }))
        assertEquals("", KreditPeReceipt.read(layout, CurrencySpec.INR, today).accountText)
    }
}
