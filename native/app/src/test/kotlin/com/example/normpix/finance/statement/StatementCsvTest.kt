package com.example.normpix.finance.statement

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Reading a bank statement's file into rows (`StatementCsv`): the bytes as text, then the cells - quotes, delimiters, blank lines. All
 * the text here is made up.
 */
class StatementCsvTest {
    @Test
    fun `a plain comma file is read cell by cell, with blank lines left out`() {
        val rows = StatementCsv.parse("Date,Narration,Amount\n\n01/10/26,Coffee,120.00\n   \n02/10/26,Tea,40.00\n")
        assertEquals(
            listOf(listOf("Date", "Narration", "Amount"), listOf("01/10/26", "Coffee", "120.00"), listOf("02/10/26", "Tea", "40.00")),
            rows,
        )
    }

    @Test
    fun `a quoted cell may hold the delimiter, doubled quotes and line breaks`() {
        val text = "a,b,c\r\n\"1,23,456.78\",\"She said \"\"hi\"\"\",\"two\r\nlines\"\r\nlast,row,x\r\n"
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("1,23,456.78", "She said \"hi\"", "two\r\nlines"), listOf("last", "row", "x")),
            StatementCsv.parse(text),
        )
    }

    @Test
    fun `a quote in the middle of a cell is only a character, and blanks before an opening quote are allowed`() {
        val rows = StatementCsv.parse("Date,Description,Amount\n01/10/26,5\" Road shop,10.00\n02/10/26,  \"Quoted, here\",20.00\n")
        assertEquals(listOf("01/10/26", "5\" Road shop", "10.00"), rows[1])
        assertEquals(listOf("02/10/26", "Quoted, here", "20.00"), rows[2])
    }

    @Test
    fun `line breaks of every kind end a record, and a missing last break does not lose the last row`() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f")), StatementCsv.parse("a,b\rc,d\r\ne,f"))
    }

    @Test
    fun `a semicolon file is read as one`() {
        val rows = StatementCsv.parse("Account;Sample\nDate;Description;Debit;Credit\n01-10-2026;Rent;\"1,20,000.00\";\n02-10-2026;Refund;;50.00\n")
        assertEquals(';', StatementCsv.detectDelimiter("Date;Description;Debit;Credit\n01-10-2026;Rent;10;\n"))
        assertEquals(listOf("01-10-2026", "Rent", "1,20,000.00", ""), rows[2])
        assertEquals(listOf("02-10-2026", "Refund", "", "50.00"), rows[3])
    }

    @Test
    fun `a tab file is read as one`() {
        val rows = StatementCsv.parse("Date\tDescription\tAmount\n01-10-2026\tCoffee, large\t120.00\n02-10-2026\tTea\t40.00\n")
        assertEquals('\t', StatementCsv.detectDelimiter("Date\tDescription\tAmount\n01-10-2026\tCoffee, large\t120.00\n"))
        assertEquals(listOf("01-10-2026", "Coffee, large", "120.00"), rows[1])
    }

    @Test
    fun `commas inside the amounts of a semicolon file do not make it a comma file`() {
        val text = "Date;Description;Amount\n01-10-2026;Rent;1,20,000.00\n02-10-2026;Shop;1,250.50\n03-10-2026;Tea;40.00\n"
        assertEquals(';', StatementCsv.detectDelimiter(text))
    }

    @Test
    fun `lines of account details above a table do not hide its delimiter`() {
        val text = "Statement for the account,,\nHolder: Someone,,\nDate,Description,Amount\n01/10/26,Coffee,10.00\n02/10/26,Tea,20.00\n"
        assertEquals(',', StatementCsv.detectDelimiter(text))
    }

    @Test
    fun `a file of one column is read as comma`() {
        assertEquals(',', StatementCsv.detectDelimiter("just\nsome\ntext\n"))
    }

    @Test
    fun `UTF-8 is read as it is and a byte order mark is dropped`() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Date,Note\n01/10/26,Paid ₹250 – café\n".toByteArray(Charsets.UTF_8)
        assertEquals("Date,Note\n01/10/26,Paid ₹250 – café\n", StatementCsv.decode(bytes))
    }

    @Test
    fun `a file that is not UTF-8 is read as Windows-1252`() {
        val windows = Charset.forName("windows-1252")
        val bytes = "Date,Note\n01/10/26,Café – Müller\n".toByteArray(windows)
        assertEquals("Date,Note\n01/10/26,Café – Müller\n", StatementCsv.decode(bytes))
    }

    @Test
    fun `a pdf and a workbook are told from text`() {
        assertEquals(StatementCsv.NotText.Pdf, StatementCsv.notText("%PDF-1.7 rest".toByteArray()))
        assertEquals(StatementCsv.NotText.Workbook, StatementCsv.notText(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4, 20)))
        assertEquals(StatementCsv.NotText.Workbook, StatementCsv.notText(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte())))
        assertNull(StatementCsv.notText("Date,Amount\n".toByteArray()))
        assertNull(StatementCsv.notText(ByteArray(0)))
    }

    @Test
    fun `the largest file read is two megabytes`() {
        assertEquals(2 * 1024 * 1024, StatementCsv.MAX_BYTES)
    }
}
