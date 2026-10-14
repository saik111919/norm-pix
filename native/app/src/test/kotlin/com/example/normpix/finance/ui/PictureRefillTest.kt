package com.example.normpix.finance.ui

import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.SharedTransactionPrefill
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading an attached picture again: what the picture says takes the place of what the form has - a picture an earlier
 * NormPix read wrongly is put right - and only what a picture can't say stays.
 */
class PictureRefillTest {
    private val at = LocalDateTime.of(2026, 10, 5, 11, 34)
    private val found = SharedTransactionPrefill(
        type = TransactionType.Expense,
        amount = 1_000,
        payee = "Asha Verma",
        note = "Ref 223456789012",
        occurredAt = at,
        timeRead = true,
        accountId = 7,
        categoryId = 3,
    )

    /** The same payment, read wrongly before: ₹10 taken as ₹710, the wrong name. */
    private val misread = FormValues(
        type = TransactionType.Expense,
        amount = 71_000,
        payee = "Asha",
        note = "",
        categoryId = 5,
        occurredAt = at,
        accountId = 7,
    )

    @Test
    fun `what the picture says replaces what the form has`() {
        val fill = pictureRefill(found, misread)
        assertEquals(PictureFill(amount = 1_000, payee = "Asha Verma", note = "Ref 223456789012"), fill)
        assertEquals("the amount, payee and reference", fill.summary)
    }

    @Test
    fun `a form that already says it all changes nothing`() {
        val same = misread.copy(amount = 1_000, payee = "Asha Verma", note = "Paid back", categoryId = 3)
        assertNull(pictureRefill(found, same).summary)
    }

    @Test
    fun `the category the user chose stays, unless the type changes from under it`() {
        assertNull(pictureRefill(found, misread).categoryId)
        val income = pictureRefill(found.copy(type = TransactionType.Income, categoryId = 9), misread)
        assertEquals(TransactionType.Income, income.type)
        assertEquals(9L, income.categoryId)
        // An empty category is filled in.
        assertEquals(3L, pictureRefill(found, misread.copy(categoryId = null)).categoryId)
    }

    @Test
    fun `a transfer stays a transfer, with no payee or category`() {
        val fill = pictureRefill(found, misread.copy(type = TransactionType.Transfer, payee = "", categoryId = null))
        assertNull(fill.type)
        assertNull(fill.payee)
        assertNull(fill.categoryId)
        assertEquals(1_000L, fill.amount)
    }

    @Test
    fun `a picture that shows the day but not the time keeps the form's time`() {
        val dayOnly = found.copy(occurredAt = LocalDateTime.of(2026, 10, 6, 23, 59), timeRead = false)
        assertEquals(LocalDateTime.of(2026, 10, 6, 11, 34), pictureRefill(dayOnly, misread).occurredAt)
        // A date still to be chosen takes the picture's as it is.
        assertEquals(dayOnly.occurredAt, pictureRefill(dayOnly, misread.copy(occurredAt = null)).occurredAt)
    }

    @Test
    fun `a type not yet chosen is taken from the picture`() {
        assertEquals(TransactionType.Expense, pictureRefill(found, misread.copy(type = null)).type)
    }
}
