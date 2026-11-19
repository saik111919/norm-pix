package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Money lent and borrowed (2026-11-19): how records add up to what a person owes, settling up, the words, the category, the checks. */
class DebtsTest {
    private val now = LocalDateTime.of(2026, 11, 19, 10, 0)
    private val ravi = 1L
    private val asha = 2L

    private fun record(kind: DebtKind, amount: Long, person: Long = ravi) =
        DebtEntry(personId = person, kind = kind, amount = amount, occurredAt = now, createdAt = now, updatedAt = now)

    @Test
    fun `lent and paid-back-by-me add to what they owe, borrowed and paid-back-to-me take from it`() {
        assertEquals(50_000, debtBalance(listOf(record(DebtKind.Lent, 50_000))))
        assertEquals(30_000, debtBalance(listOf(record(DebtKind.Lent, 50_000), record(DebtKind.RepaidToMe, 20_000))))
        assertEquals(-20_000, debtBalance(listOf(record(DebtKind.Borrowed, 20_000))))
        assertEquals(0, debtBalance(listOf(record(DebtKind.Borrowed, 20_000), record(DebtKind.RepaidByMe, 20_000))))
        assertEquals(-5_000, debtBalance(listOf(record(DebtKind.Lent, 10_000), record(DebtKind.Borrowed, 15_000))), "both ways net out")
        assertEquals(0, debtBalance(emptyList()))
    }

    @Test
    fun `balances by person leave out who is settled, and totals add each way`() {
        val records = listOf(
            record(DebtKind.Lent, 50_000, ravi),
            record(DebtKind.Borrowed, 20_000, asha),
            record(DebtKind.Lent, 1_000, 3), record(DebtKind.RepaidToMe, 1_000, 3),
        )
        assertEquals(mapOf(ravi to 50_000L, asha to -20_000L), debtBalances(records))
        assertEquals(DebtTotals(owedToMe = 50_000, iOwe = 20_000), debtTotals(debtBalances(records)))
        assertEquals(true, debtTotals(emptyMap()).isEmpty)
    }

    @Test
    fun `settling up pays the whole balance the right way, and there is nothing to settle at zero`() {
        assertEquals(DebtKind.RepaidToMe to 50_000L, settleUpFor(50_000))
        assertEquals(DebtKind.RepaidByMe to 20_000L, settleUpFor(-20_000))
        assertNull(settleUpFor(0))
        // A settle-up recorded brings the balance to zero.
        val (kind, amount) = settleUpFor(-20_000)!!
        assertEquals(0, debtBalance(listOf(record(DebtKind.Borrowed, 20_000), record(kind, amount))))
    }

    @Test
    fun `the words say who owes whom, not colour alone`() {
        val money: (Long) -> String = { "₹${it / 100}" }
        assertEquals("Ravi owes you ₹500", debtBalancePhrase("Ravi", 50_000, money))
        assertEquals("You owe Ravi ₹200", debtBalancePhrase("Ravi", -20_000, money))
        assertEquals("All settled", debtBalancePhrase("Ravi", 0, money))
        assertEquals("You lent Ravi", DebtKind.Lent.phrase("Ravi"))
        assertEquals("You borrowed from Ravi", DebtKind.Borrowed.phrase("Ravi"))
        assertEquals("Ravi paid you back", DebtKind.RepaidToMe.phrase("Ravi"))
        assertEquals("You paid Ravi back", DebtKind.RepaidByMe.phrase("Ravi"))
    }

    @Test
    fun `money that moved is filed under the app's own category for it, else its family, never an archived one`() {
        fun cat(id: Long, name: String, type: CategoryType, archived: Boolean = false) =
            Category(id = id, name = name, type = type, icon = "other", color = 0, archived = archived, createdAt = now, updatedAt = now)
        val all = listOf(
            cat(1, "Money sent", CategoryType.Expense),
            cat(2, "Lent to someone", CategoryType.Expense),
            cat(3, "Money received", CategoryType.Income),
            cat(4, "Loan repaid to me", CategoryType.Income, archived = true),
        )
        assertEquals(2L, debtCategoryFor(DebtKind.Lent, all)?.id)
        assertEquals(1L, debtCategoryFor(DebtKind.RepaidByMe, all)?.id, "no Repaid to someone: the family")
        assertEquals(3L, debtCategoryFor(DebtKind.RepaidToMe, all)?.id, "the archived one is passed over")
        assertNull(debtCategoryFor(DebtKind.Borrowed, all.filter { it.type == CategoryType.Expense }), "none of the right kind")
        assertEquals(true, DebtKind.Borrowed.moneyIn)
        assertEquals(false, DebtKind.Lent.moneyIn)
    }

    @Test
    fun `a record needs an amount above zero and a short note`() {
        assertNull(record(DebtKind.Lent, 1).fault())
        assertEquals(DebtFault.Amount, record(DebtKind.Lent, 0).fault())
        assertEquals(DebtFault.Note, record(DebtKind.Lent, 1).copy(note = "x".repeat(MAX_DEBT_NOTE + 1)).fault())
        assertEquals(DebtKind.RepaidByMe, DebtKind.of("repaid_by_me"))
        assertNull(DebtKind.of("gift"))
    }
}
