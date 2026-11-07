package com.example.normpix.finance.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What is due on a card: the unpaid part of its last statement, against that statement's own due date - not everything owed on the
 * card, and nothing once it is paid. Past the due date what is left unpaid is late. A card with no statement day keeps the old reading
 * (everything owed so far). Every date is fixed, so nothing depends on the clock.
 */
class CardBillTest {
    private fun date(month: Int, day: Int) = LocalDate.of(2026, month, day)

    /** Statement on the 5th, due on the 20th; the statement was made up on 5 November. */
    private fun bill(balance: Long, owedAtStatement: Long, paidSince: Long, today: LocalDate, statementDate: LocalDate = date(11, 5)) =
        cardBill(5, 20, balance, StatementFigures(statementDate, owedAtStatement, paidSince), today)

    @Test
    fun `the bill is the statement less what was paid since, and what was charged after is not in it`() {
        // Statement 3,750; 400 charged since and 1,600 paid in: owed 2,550 now, 2,150 of the statement still to pay.
        val bill = bill(balance = -255_000, owedAtStatement = 375_000, paidSince = 160_000, today = date(11, 12))!!
        assertEquals(BillKind.Statement, bill.kind)
        assertEquals(215_000, bill.amount)
        assertEquals(255_000, bill.owedNow)
        assertEquals(date(11, 20), bill.dueDate)
        assertEquals(8, bill.daysAway)
        assertEquals(date(11, 5), bill.start)
        assertEquals(7f / 15f, bill.fraction, 0.0001f)
    }

    @Test
    fun `a paid statement has no bill even while the card owes the next one's spending`() {
        assertNull(bill(balance = -40_000, owedAtStatement = 100_000, paidSince = 100_000, today = date(11, 12)))
        assertNull(bill(balance = -40_000, owedAtStatement = 100_000, paidSince = 150_000, today = date(11, 12)), "paid more than billed")
    }

    @Test
    fun `a card that owes nothing, or has no due day, has no bill`() {
        assertNull(bill(balance = 0, owedAtStatement = 100_000, paidSince = 0, today = date(11, 12)))
        assertNull(bill(balance = 5_000, owedAtStatement = 100_000, paidSince = 0, today = date(11, 12)), "in credit")
        assertNull(cardBill(5, null, -100_000, StatementFigures(date(11, 5), 100_000, 0), date(11, 12)))
    }

    @Test
    fun `what is unpaid never exceeds what the card owes (a refund can have cleared some of it)`() {
        // Billed 1,000 and nothing paid, but a 700 refund after it left only 300 owed.
        val bill = bill(balance = -30_000, owedAtStatement = 100_000, paidSince = 0, today = date(11, 12))!!
        assertEquals(30_000, bill.amount)
    }

    @Test
    fun `the due date is the statement's own, a month on when the due day is earlier in the month`() {
        // Statement on the 25th, due on the 12th: the 25 November statement is due on 12 December.
        val bill = cardBill(25, 12, -80_000, StatementFigures(date(11, 25), 80_000, 0), date(11, 26))!!
        assertEquals(LocalDate.of(2026, 12, 12), bill.dueDate)
        assertEquals(16, bill.daysAway)
        assertEquals(1f / 17f, bill.fraction, 0.0001f)
    }

    @Test
    fun `on the due day it is due today, and the day after it is late`() {
        val today = bill(balance = -100_000, owedAtStatement = 100_000, paidSince = 0, today = date(11, 20))!!
        assertEquals(BillKind.Statement, today.kind)
        assertEquals(0, today.daysAway)
        assertEquals(1f, today.fraction)
        assertEquals("today", today.inWords)

        val late = bill(balance = -100_000, owedAtStatement = 100_000, paidSince = 0, today = date(11, 21))!!
        assertEquals(BillKind.Overdue, late.kind)
        assertEquals(-1, late.daysAway)
        assertEquals(date(11, 20), late.dueDate)
        assertEquals(100_000, late.amount)
        assertEquals(1f, late.fraction)
        assertEquals("yesterday", late.inWords)
        assertEquals("5 days ago", bill(balance = -100_000, owedAtStatement = 100_000, paidSince = 0, today = date(11, 25))!!.inWords)
    }

    @Test
    fun `once the late part is paid the bill is gone, even past the due date`() {
        assertNull(bill(balance = -20_000, owedAtStatement = 100_000, paidSince = 100_000, today = date(11, 25)))
    }

    @Test
    fun `a card with no statement day, or no figures, keeps the old reading of everything owed`() {
        val open = cardBill(null, 20, -123_000, null, date(11, 12))!!
        assertEquals(BillKind.Open, open.kind)
        assertEquals(123_000, open.amount)
        assertEquals(123_000, open.owedNow)
        assertEquals(date(11, 20), open.dueDate)
        assertEquals(8, open.daysAway)
        // With a statement day but no figures yet (still loading), likewise.
        assertEquals(BillKind.Open, cardBill(5, 20, -123_000, null, date(11, 12))!!.kind)
    }

    @Test
    fun `a bill within three days is soon, and a late one is too`() {
        assertTrue(bill(balance = -1, owedAtStatement = 1, paidSince = 0, today = date(11, 17))!!.soon)
        assertTrue(!bill(balance = -1, owedAtStatement = 1, paidSince = 0, today = date(11, 16))!!.soon)
        assertTrue(bill(balance = -1, owedAtStatement = 1, paidSince = 0, today = date(11, 22))!!.soon)
    }
}
