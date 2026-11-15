package com.example.normpix.finance.statement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finding a statement's table among the lines around it, and guessing which column is which from the headings banks use. The three
 * layouts are made up in the style of Indian banks' downloads: no real names, numbers or amounts.
 */
class StatementLayoutTest {
    private val layoutWithWithdrawals = StatementFixtures.withWithdrawals
    private val layoutWithDebitCredit = StatementFixtures.withDebitCredit
    private val layoutWithAmountAndMarker = StatementFixtures.withAmountAndMarker

    private fun sheet(text: String) = StatementFixtures.sheet(text)

    @Test
    fun `the table starts at the first row with a date name and an amount name`() {
        val sheet = sheet(layoutWithWithdrawals)
        assertEquals(4, sheet.preamble.size)
        assertEquals(listOf("Date", "Narration", "Chq./Ref.No.", "Value Dt", "Withdrawal Amt.", "Deposit Amt.", "Closing Balance"), sheet.header)
        // The three transactions, then what the bank printed after them.
        assertEquals(3 + 4, sheet.body.size)
        assertEquals("01/10/26", sheet.body.first().first())
    }

    @Test
    fun `a file with no table is not a statement`() {
        assertNull(StatementLayout.find(StatementCsv.parse("Name,City\nSomeone,Somewhere\n")))
        // A date alone, or an amount alone, is not enough.
        assertNull(StatementLayout.find(StatementCsv.parse("Date,Note\n01/10/26,x\n")))
        assertNull(StatementLayout.find(StatementCsv.parse("Amount,Note\n10,x\n")))
    }

    @Test
    fun `withdrawal and deposit columns are read as debit and credit, the date column wins over the value date, balance is ignored`() {
        val sheet = sheet(layoutWithWithdrawals)
        val mapping = StatementLayout.guess(sheet)
        assertEquals(StatementMapping(date = 0, description = 1, debit = 4, credit = 5, amount = null, direction = null, reference = 2), mapping)
        assertTrue(mapping.twoColumns)
        assertTrue(mapping.isUsable)
    }

    @Test
    fun `a Txn Date, Debit, Credit layout`() {
        val mapping = StatementLayout.guess(sheet(layoutWithDebitCredit))
        assertEquals(StatementMapping(date = 0, description = 1, debit = 3, credit = 4, reference = 2), mapping)
    }

    @Test
    fun `a single amount with a Dr Cr column`() {
        val mapping = StatementLayout.guess(sheet(layoutWithAmountAndMarker))
        assertEquals(StatementMapping(date = 0, description = 1, amount = 2, direction = 3), mapping)
        assertTrue(!mapping.twoColumns)
    }

    @Test
    fun `a type column whose values are not debit or credit is not taken for a marker`() {
        val text = """
            Date,Description,Amount,Type
            01/10/26,Coffee,-120.00,UPI
            02/10/26,Salary,5000.00,NEFT
        """.trimIndent()
        val mapping = StatementLayout.guess(sheet(text))
        assertEquals(2, mapping.amount)
        assertNull(mapping.direction)
    }

    @Test
    fun `headings are matched whatever the case, spacing and punctuation, and without what is in brackets`() {
        val text = "TRANSACTION DATE,PARTICULARS,AMOUNT (INR),Cheque No,Closing Balance (INR)\n01/10/26,Coffee,-120.00,123456,10.00\n"
        val mapping = StatementLayout.guess(sheet(text))
        assertEquals(StatementMapping(date = 0, description = 1, amount = 2, reference = 3), mapping)
    }

    @Test
    fun `a file with no description heading takes the widest text column`() {
        val text = "Date,Amount,Ref,What it was\n01/10/26,-120.00,123456,Morning coffee at the corner shop\n02/10/26,-40.00,123457,Tea\n"
        val mapping = StatementLayout.guess(sheet(text))
        assertEquals(3, mapping.description)
    }

    @Test
    fun `picking a column for one field takes it from another, and the two ways of giving money exclude each other`() {
        val sheet = sheet(layoutWithWithdrawals)
        var mapping = StatementLayout.guess(sheet)
        // The description moves onto the reference's column: the reference lets go of it.
        mapping = mapping.with(StatementField.Description, 2)
        assertEquals(2, mapping.description)
        assertNull(mapping.reference)
        // One amount column replaces the debit and credit columns.
        mapping = mapping.with(StatementField.Amount, 4)
        assertEquals(4, mapping.amount)
        assertNull(mapping.debit)
        assertNull(mapping.credit)
        // And a debit column takes the amount away again.
        mapping = mapping.with(StatementField.Debit, 4).with(StatementField.Credit, 5)
        assertNull(mapping.amount)
        assertEquals(4, mapping.debit)
        assertEquals(5, mapping.credit)
        // None is a choice too.
        assertNull(mapping.with(StatementField.Reference, null).reference)
    }

    @Test
    fun `the first values of a column are there to tell it from another`() {
        val sheet = sheet(layoutWithWithdrawals)
        assertEquals(listOf("01/10/26", "05/10/26", "09/10/26"), sheet.samples(0))
        assertEquals("Chq./Ref.No.", sheet.columnName(2))
        assertEquals("Column 9", sheet.columnName(8))
    }
}
