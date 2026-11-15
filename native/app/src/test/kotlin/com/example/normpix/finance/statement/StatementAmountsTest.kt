package com.example.normpix.finance.statement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The amounts in a statement: Indian grouping, currency marks, Dr / Cr, signs and brackets - and what is not an amount. */
class StatementAmountsTest {
    private fun minor(cell: String) = StatementAmounts.parse(cell)?.minor

    @Test
    fun `plain and grouped figures are read to the paisa`() {
        assertEquals(50_000L, minor("500"))
        assertEquals(50_050L, minor("500.5"))
        assertEquals(12_345_678L, minor("1,23,456.78"))
        assertEquals(12_345_678L, minor("123,456.78"))
        assertEquals(99L, minor("0.99"))
        assertEquals(99L, minor(".99"))
        assertEquals(125_000L, minor("1,250"))
        assertEquals(12_000_000_000L, minor("12,00,00,000.00"))
    }

    @Test
    fun `a currency before or after the figure is dropped`() {
        assertEquals(50_000L, minor("Rs 500"))
        assertEquals(50_000L, minor("Rs. 500.00"))
        assertEquals(50_000L, minor("RS.500"))
        assertEquals(120_000L, minor("INR 1,200.00"))
        assertEquals(120_000L, minor("1,200.00 INR"))
        assertEquals(9_900L, minor("₹99"))
        assertEquals(9_900L, minor("₹ 99.00"))
    }

    @Test
    fun `a minus sign or brackets make it negative`() {
        assertTrue(StatementAmounts.parse("-250")!!.negative)
        assertTrue(StatementAmounts.parse("250-")!!.negative)
        assertTrue(StatementAmounts.parse("−250")!!.negative)
        assertTrue(StatementAmounts.parse("(250.00)")!!.negative)
        assertTrue(StatementAmounts.parse("-Rs. 250")!!.negative)
        assertEquals(25_000L, minor("(250.00)"))
        assertFalse(StatementAmounts.parse("250")!!.negative)
        assertFalse(StatementAmounts.parse("+250")!!.negative)
    }

    @Test
    fun `a Dr or Cr beside it says the side`() {
        assertEquals(StatementSide.Debit, StatementAmounts.parse("1,234.50 Dr")?.side)
        assertEquals(StatementSide.Debit, StatementAmounts.parse("1,234.50Dr")?.side)
        assertEquals(StatementSide.Credit, StatementAmounts.parse("1,234.50 CR")?.side)
        assertEquals(StatementSide.Credit, StatementAmounts.parse("Cr 800")?.side)
        assertEquals(StatementSide.Debit, StatementAmounts.parse("Dr. 800")?.side)
        assertEquals(StatementSide.Debit, StatementAmounts.parse("Rs 800 Debit")?.side)
        assertEquals(123_450L, minor("1,234.50 Dr"))
        assertEquals(80_000L, minor("Cr 800"))
        assertNull(StatementAmounts.parse("800")?.side)
    }

    @Test
    fun `a decimal comma is a decimal point, and a thousands comma is not`() {
        assertEquals(1_250L, minor("12,50"))
        assertEquals(123_450L, minor("1.234,50"))
        assertEquals(125_000L, minor("1,250"))
        assertEquals(12_345_600L, minor("1,23,456"))
    }

    @Test
    fun `what is not an amount is not one`() {
        assertNull(minor(""))
        assertNull(minor("   "))
        assertNull(minor("Closing Balance"))
        assertNull(minor("12.345"))
        assertNull(minor("1/2"))
        assertNull(minor("-"))
        assertNull(minor("Rs"))
        assertNull(minor("Dr"))
        // Zero is an amount; a cell of text is never read as one.
        assertEquals(0L, minor("0.00"))
    }

    @Test
    fun `a blank cell and the dashes banks put in an empty one say nothing`() {
        for (cell in listOf("", "  ", "-", "--", "NA", "N/A", "nil", ".")) assertTrue(StatementAmounts.isBlank(cell), cell)
        for (cell in listOf("0.00", "10", "Dr")) assertFalse(StatementAmounts.isBlank(cell), cell)
    }

    @Test
    fun `a marker is told from text`() {
        assertEquals(StatementSide.Debit, StatementAmounts.sideOf("Dr"))
        assertEquals(StatementSide.Debit, StatementAmounts.sideOf(" DEBIT "))
        assertEquals(StatementSide.Debit, StatementAmounts.sideOf("D"))
        assertEquals(StatementSide.Debit, StatementAmounts.sideOf("Withdrawal"))
        assertEquals(StatementSide.Credit, StatementAmounts.sideOf("cr."))
        assertEquals(StatementSide.Credit, StatementAmounts.sideOf("C"))
        assertEquals(StatementSide.Credit, StatementAmounts.sideOf("Deposit"))
        assertNull(StatementAmounts.sideOf("UPI"))
        assertNull(StatementAmounts.sideOf(""))
    }
}
