package com.example.normpix.finance.ui

import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.SharedTransactionPrefill
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a picture read in the form fills in: only what is still empty, never what the user entered or chose. */
class PictureFillTest {
    private val at = LocalDateTime.of(2026, 10, 10, 12, 34)
    private val found = SharedTransactionPrefill(
        type = TransactionType.Expense,
        amount = 25_000,
        payee = "Swiggy",
        note = "Ref 428312345678",
        occurredAt = at,
        accountId = 7,
        categoryId = 3,
    )
    private val blank = FormGaps(
        type = TransactionType.Expense,
        typeOpen = true,
        amountEmpty = true,
        payeeEmpty = true,
        noteEmpty = true,
        categoryEmpty = true,
        dateOpen = true,
        accountOpen = true,
    )

    @Test
    fun `a blank form takes everything the picture says`() {
        val fill = pictureFill(found, blank)
        assertEquals(PictureFill(amount = 25_000, payee = "Swiggy", note = "Ref 428312345678", categoryId = 3, occurredAt = at, accountId = 7), fill)
        assertEquals("the amount, payee, category, date, account and reference", fill.summary)
        assertFalse(fill.kept)
    }

    @Test
    fun `what the user entered stays`() {
        val fill = pictureFill(
            found,
            blank.copy(amountEmpty = false, payeeEmpty = false, noteEmpty = false, categoryEmpty = false, dateOpen = false, accountOpen = false),
        )
        assertEquals(PictureFill(kept = true), fill)
        assertNull(fill.summary)
    }

    @Test
    fun `one value the user entered is kept, and the rest is filled in`() {
        val fill = pictureFill(found, blank.copy(amountEmpty = false))
        assertNull(fill.amount)
        assertTrue(fill.kept)
        assertEquals("the payee, category, date, account and reference", fill.summary)
    }

    @Test
    fun `the picture's direction changes an open form's type, and its category comes with it`() {
        val income = found.copy(type = TransactionType.Income, categoryId = 9)
        val fill = pictureFill(income, blank)
        assertEquals(TransactionType.Income, fill.type)
        assertEquals(9L, fill.categoryId)
        assertEquals("the type, amount, payee, category, date, account and reference", fill.summary)
    }

    @Test
    fun `a type the user chose stays, and a category of the other direction doesn't fit it`() {
        val fill = pictureFill(found.copy(type = TransactionType.Income, categoryId = 9), blank.copy(typeOpen = false))
        assertNull(fill.type)
        assertNull(fill.categoryId)
        assertEquals(25_000L, fill.amount)
    }

    @Test
    fun `a transfer takes no payee or category`() {
        val fill = pictureFill(found, blank.copy(type = TransactionType.Transfer, typeOpen = false))
        assertNull(fill.payee)
        assertNull(fill.categoryId)
        assertEquals(25_000L, fill.amount)
    }

    @Test
    fun `one thing filled is named alone`() {
        assertEquals("the amount", PictureFill(amount = 1).summary)
        assertEquals("the amount and date", PictureFill(amount = 1, occurredAt = at).summary)
    }

    @Test
    fun `a number no account has clears the account the form started with, for the user to choose`() {
        val unmatched = found.copy(accountId = null, accountNeedsChoice = true)
        assertTrue(pictureFill(unmatched, blank).chooseAccount)
        assertNull(pictureFill(unmatched, blank).accountId)
        // An account the user chose, or the form was opened on, stays.
        assertFalse(pictureFill(unmatched, blank.copy(accountOpen = false)).chooseAccount)
        // An account the number picked is filled in, not cleared.
        val matched = pictureFill(found.copy(accountNeedsChoice = true), blank)
        assertFalse(matched.chooseAccount)
        assertEquals(7L, matched.accountId)
        // Nothing to clear when the picture shows no number.
        assertFalse(pictureFill(found.copy(accountId = null), blank).chooseAccount)
        // A transfer's accounts are not chosen from a payment's number.
        assertFalse(pictureFill(unmatched, blank.copy(type = TransactionType.Transfer, typeOpen = false)).chooseAccount)
    }

    @Test
    fun `a picture of a transfer between the user's accounts fills in where it went, or clears a guess`() {
        val transfer = found.copy(type = TransactionType.Transfer, categoryId = null, toAccountId = 9)
        val fill = pictureFill(transfer, blank)
        assertEquals(TransactionType.Transfer, fill.type)
        assertEquals(7L, fill.accountId)
        assertEquals(9L, fill.toAccountId)
        assertNull(fill.payee)
        assertFalse(fill.chooseToAccount)
        assertEquals("the type, amount, date, account, account it went to and reference", fill.summary)

        // Where it went couldn't be told: the account the form picks for a new transfer is a guess, so it is cleared.
        val unknown = pictureFill(transfer.copy(toAccountId = null, toAccountNeedsChoice = true), blank)
        assertNull(unknown.toAccountId)
        assertTrue(unknown.chooseToAccount)
        // So is where it came from, when that couldn't be told either.
        assertTrue(pictureFill(transfer.copy(accountId = null, accountNeedsChoice = true), blank).chooseAccount)

        // A form the user already made an expense of stays one, with no account to send it to.
        val expense = pictureFill(transfer, blank.copy(typeOpen = false))
        assertNull(expense.toAccountId)
        assertFalse(expense.chooseToAccount)
    }

    @Test
    fun `reading a picture again makes an expense the transfer it shows`() {
        val transfer = found.copy(type = TransactionType.Transfer, categoryId = null, toAccountId = 9)
        val now = FormValues(TransactionType.Expense, 25_000, "Swiggy", "", 3, at, 7)
        val fill = pictureRefill(transfer, now)
        assertEquals(TransactionType.Transfer, fill.type)
        assertEquals(9L, fill.toAccountId)
        // Already going there: nothing to change.
        assertNull(pictureRefill(transfer, now.copy(type = TransactionType.Transfer, toAccountId = 9)).toAccountId)
    }
}
