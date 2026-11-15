package com.example.normpix.finance.statement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The payee, reference and note a bank's narration makes (`StatementNarration`). Every narration here is made up. */
class StatementNarrationTest {
    private fun read(description: String, reference: String? = null) = StatementNarration.read(description, reference)

    @Test
    fun `a UPI narration with dashes names its payee, and its padded reference is the one a payment app shows`() {
        val entry = read("UPI-SAMPLE CAFE-samplecafe@bank-BANK0000001-428312345678-UPI", "0000428312345678")
        assertEquals("Sample Cafe", entry.payee)
        assertEquals("428312345678", entry.reference)
        assertEquals("Ref 428312345678 · UPI-SAMPLE CAFE-samplecafe@bank-BANK0000001-428312345678-UPI", entry.note)
    }

    @Test
    fun `a UPI narration with slashes is read the way a shared receipt's is`() {
        val entry = read("UPI/DR/428312345678/SAMPLE SHOP/SBIN/sample@sbi/Payment")
        assertEquals("Sample Shop", entry.payee)
        assertEquals("428312345678", entry.reference)
        assertEquals("Sample Foods", read("UPI/P2M/428312345678/SAMPLE FOODS").payee)
        assertEquals("Sample Foods", read("UPI/428312345678/SAMPLE FOODS/more").payee)
    }

    @Test
    fun `a NEFT or IMPS narration names the other side, not the codes around it`() {
        assertEquals("Example Employer Ltd", read("NEFT CR-BANK0000001-EXAMPLE EMPLOYER LTD-A SAMPLE PERSON-N123456789012", "N123456789012").payee)
        assertEquals("N123456789012", read("NEFT CR-BANK0000001-EXAMPLE EMPLOYER LTD-A SAMPLE PERSON-N123456789012", "N123456789012").reference)
        val imps = read("IMPS-431234567890-JOHN DOE-BANK-XXXXXXXX1234-GIFT")
        assertEquals("John Doe", imps.payee)
        assertEquals("431234567890", imps.reference, "a lone 12 digit number in a narration is its reference")
        assertEquals("John Doe", read("IMPS 431234567890 JOHN DOE").payee)
    }

    @Test
    fun `a card swipe's masked number and a cash withdrawal`() {
        assertEquals("Example Store", read("POS 416021XXXXXX1234 EXAMPLE STORE").payee)
        assertEquals("ATM withdrawal", read("ATW-416021XXXXXX1234-S1ACBD01-BANGALORE").payee)
        assertEquals("ATM withdrawal", read("CASH WDL SAMPLE BRANCH").payee)
    }

    @Test
    fun `a narration with no codes is its own payee, cut to the length of a name`() {
        assertEquals("Swiggy order BLR", read("Swiggy order BLR").payee)
        assertEquals("Rent for November", read("  Rent   for\nNovember ").payee)
        val long = read("A".repeat(10) + " " + "word ".repeat(30))
        assertTrue(long.payee!!.length <= StatementNarration.MAX_PAYEE)
        assertEquals("Int.PD:01-08-2026 To 31-10-2026", read("INT.PD:01-08-2026 TO 31-10-2026").payee)
    }

    @Test
    fun `a narration made only of codes names no one`() {
        assertNull(read("UPI-123456789012-9876543210@ybl").payee)
        assertNull(read("").payee)
    }

    @Test
    fun `the reference column's value is kept as letters and digits, six to thirty five of them`() {
        assertEquals("REF000111", read("x", "REF000111").reference)
        assertEquals("428312345678", read("x", "0000428312345678").reference)
        assertEquals("000012", read("x", "000012").reference, "a cheque number keeps its zeros when taking them off leaves too little")
        assertEquals("UPI428312345678", read("x", "UPI-428312345678").reference)
        assertNull(read("x", "-").reference)
        assertNull(read("x", "0000000000000").reference)
        assertNull(read("x", "12345").reference)
        assertNull(read("x", "A".repeat(36)).reference)
        assertNull(read("x", "").reference)
        assertNull(read("x", null).reference)
    }

    @Test
    fun `the note is the reference first, then the narration as the bank wrote it`() {
        assertEquals("Ref REF000111 · BILL PAYMENT", read("BILL  PAYMENT", "REF000111").note)
        assertEquals("BILL PAYMENT", read("BILL PAYMENT").note)
        assertEquals("Ref REF000111", read("", "REF000111").note)
        assertNull(read("").note)
        assertEquals(StatementNarration.MAX_NOTE, read("N".repeat(500)).note!!.length)
    }

    @Test
    fun `a reference inside a narration is found only as a lone number of twelve digits`() {
        assertEquals("428312345678", read("Transfer 428312345678 to someone").reference)
        assertNull(read("Card 4160210123456789 swiped").reference)
        assertNull(read("Order 12345678901 shipped").reference)
        // The column wins over the narration.
        assertEquals("REF000111", read("Transfer 428312345678 to someone", "REF000111").reference)
    }
}
