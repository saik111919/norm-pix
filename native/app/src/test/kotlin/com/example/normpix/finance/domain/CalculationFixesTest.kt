package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Percentages, budget use, hidden amounts and card bills, as every screen writes them. */
class CalculationFixesTest {
    @Test
    fun `a share is rounded but never says none or all when it isn't`() {
        assertEquals("0%", percentLabel(0.0))
        assertEquals("<1%", percentLabel(shareOf(1, 1_000)))
        assertEquals("1%", percentLabel(shareOf(5, 1_000)))
        assertEquals("34%", percentLabel(shareOf(337, 1_000)))
        assertEquals(">99%", percentLabel(shareOf(996, 1_000)))
        assertEquals("100%", percentLabel(shareOf(1_000, 1_000)))
    }

    @Test
    fun `budget use never reads 100 percent while some is left or once it is over`() {
        assertEquals(99, BudgetStatus(allocated = 1_000, spent = 996).usedPercent, "under budget: rounded down")
        assertEquals(100, BudgetStatus(allocated = 1_000, spent = 1_000).usedPercent)
        assertEquals(101, BudgetStatus(allocated = 1_000, spent = 1_004).usedPercent, "a little over: past 100")
        assertEquals(150, BudgetStatus(allocated = 1_000, spent = 1_500).usedPercent)
        assertEquals(0, BudgetStatus(allocated = 0, spent = 500).usedPercent)
    }

    @Test
    fun `hidden amounts show no digits and the same length whatever they are`() {
        val shown = MoneyFormatter(CurrencySpec.INR)
        val hidden = MoneyFormatter(CurrencySpec.INR, hidden = true)
        assertEquals("₹1,23,456.50", shown.format(12_345_650))
        assertEquals("₹••••", hidden.format(12_345_650))
        assertEquals(hidden.format(5), hidden.format(999_999_999))
        assertEquals("₹••••", hidden.compact(12_345_650))
        assertEquals("••••", hidden.plain(100))
    }

    private val now = LocalDateTime.of(2026, 10, 15, 9, 0)

    private fun account(id: Long, name: String, kind: AccountKind, bank: String? = null, ending: String? = null, archived: Boolean = false) =
        Account(id = id, name = name, kind = kind, bankKey = bank, numberEnding = ending, archived = archived, createdAt = now, updatedAt = now)

    private val bank = account(1, "SBI Savings", AccountKind.Bank, bank = "sbi")
    private val hdfcCard = account(2, "HDFC Regalia", AccountKind.Card, bank = "hdfc", ending = "3588")
    private val iciciCard = account(3, "ICICI Amazon Pay", AccountKind.Card, bank = "icici", ending = "1004")
    private val accounts = listOf(bank, hdfcCard, iciciCard)
    private val banks = mapOf("hdfc" to "hdfc", "icici" to "icici", "sbi" to "sbi")
    private fun bankIn(text: String): String? = banks.entries.firstOrNull { text.lowercase().contains(it.key) }?.value

    @Test
    fun `a payment to one of the user's cards is that card's bill`() {
        assertEquals(hdfcCard, cardBillTarget("HDFC Bank Credit Card", accounts, paidFrom = bank.id, ::bankIn))
        assertEquals(iciciCard, cardBillTarget("Credit card bill XX1004", accounts, paidFrom = bank.id, ::bankIn))
        assertEquals(hdfcCard, cardBillTarget("HDFC Regalia card payment", accounts, paidFrom = bank.id, ::bankIn))
    }

    @Test
    fun `anything less clear is not taken for a card bill`() {
        // No card spoken of: a shop named like a card is a purchase there.
        assertNull(cardBillTarget("ICICI Amazon Pay", accounts, paidFrom = bank.id, ::bankIn))
        assertNull(cardBillTarget("Swiggy", accounts, paidFrom = bank.id, ::bankIn))
        // Two cards and nothing to tell them apart.
        assertNull(cardBillTarget("Credit card bill", accounts, paidFrom = bank.id, ::bankIn))
        // Spending on the card itself is spending.
        assertNull(cardBillTarget("HDFC Bank Credit Card", accounts, paidFrom = hdfcCard.id, ::bankIn))
        // A bank with no card of the user's.
        assertNull(cardBillTarget("SBI Card", accounts, paidFrom = bank.id, ::bankIn))
    }

    @Test
    fun `with one card a plain card bill is enough, and an archived card is never chosen`() {
        val one = listOf(bank, hdfcCard)
        assertEquals(hdfcCard, cardBillTarget("Credit card bill", one, paidFrom = bank.id, ::bankIn))
        assertNull(cardBillTarget("Card", one, paidFrom = bank.id, ::bankIn), "just the word card isn't a bill")
        assertNull(cardBillTarget("Credit card bill", listOf(bank, hdfcCard.copy(archived = true)), paidFrom = bank.id, ::bankIn))
    }
}
