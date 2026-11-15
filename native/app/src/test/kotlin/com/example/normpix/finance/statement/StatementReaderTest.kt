package com.example.normpix.finance.statement

import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A statement's table read into transactions (`StatementReader`): which way each went, how much, on what day - and what is skipped and
 * counted: the lines around the table, a row whose money can't be told.
 */
class StatementReaderTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun read(text: String, positiveIsDebit: Boolean = false): StatementReading {
        val sheet = StatementFixtures.sheet(text)
        val mapping = StatementLayout.guess(sheet)
        val format = assertNotNull(StatementDates.detect(sheet.values(mapping.date), today).format)
        return StatementReader.read(sheet, mapping, format, today, positiveIsDebit)
    }

    @Test
    fun `withdrawals and deposits read to expenses and income, and the lines around the table are skipped and counted`() {
        val reading = read(StatementFixtures.withWithdrawals)
        assertEquals(3, reading.rows.size)
        assertEquals(4, reading.skipped, "the summary heading, its two lines and the footer")

        val coffee = reading.rows[0]
        assertEquals(TransactionType.Expense, coffee.type)
        assertEquals(125_000L, coffee.amount)
        assertEquals(LocalDate.of(2026, 10, 1), coffee.date)
        assertNull(coffee.time)
        assertEquals("UPI-SAMPLE CAFE-samplecafe@bank-BANK0000001-428312345678-UPI", coffee.description)
        assertEquals("0000428312345678", coffee.reference)

        val salary = reading.rows[1]
        assertEquals(TransactionType.Income, salary.type)
        assertEquals(8_500_050L, salary.amount)
        assertEquals(LocalDate.of(2026, 10, 5), salary.date)

        assertEquals(TransactionType.Expense, reading.rows[2].type)
        assertEquals(49_900L, reading.rows[2].amount)
        assertEquals(listOf(0, 1, 2), reading.rows.map { it.index })
    }

    @Test
    fun `a debit and credit layout with a dd-MMM-yyyy date`() {
        val reading = read(StatementFixtures.withDebitCredit)
        assertEquals(0, reading.skipped)
        assertEquals(listOf(TransactionType.Income, TransactionType.Expense), reading.rows.map { it.type })
        assertEquals(listOf(500_000L, 120_000L), reading.rows.map { it.amount })
        assertEquals(listOf(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 16)), reading.rows.map { it.date })
        assertEquals("REF000111", reading.rows[0].reference)
    }

    @Test
    fun `a single amount with a Dr Cr column`() {
        val reading = read(StatementFixtures.withAmountAndMarker)
        assertEquals(listOf(TransactionType.Expense, TransactionType.Income), reading.rows.map { it.type })
        assertEquals(listOf(125_050L, 5_000_000L), reading.rows.map { it.amount })
        assertEquals("Grocery shop", reading.rows[0].description)
    }

    @Test
    fun `a row with both a debit and a credit, or neither, or a figure that is no amount, is skipped`() {
        val reading = read(
            """
            Date,Description,Debit,Credit
            13/10/2026,Both,100.00,50.00
            14/10/2026,Neither,,
            15/10/2026,Zeros,0.00,0.00
            16/10/2026,Dashes,-,--
            17/10/2026,Not a number,abc,
            18/10/2026,Fine out,75.50,0.00
            19/10/2026,Fine in,-,60.00
            """.trimIndent(),
        )
        assertEquals(listOf("Fine out", "Fine in"), reading.rows.map { it.description })
        assertEquals(listOf(TransactionType.Expense, TransactionType.Income), reading.rows.map { it.type })
        assertEquals(5, reading.skipped)
    }

    @Test
    fun `a single amount's side comes from Dr and Cr beside it, then the marker column, then its sign`() {
        val reading = read(
            """
            Date,Description,Amount
            13/10/2026,Suffix debit,"500.00 Dr"
            14/10/2026,Suffix credit,500.00Cr
            15/10/2026,Negative,-500.00
            16/10/2026,Brackets,(500.00)
            17/10/2026,Positive,500.00
            18/10/2026,Zero,0.00
            19/10/2026,Blank,
            """.trimIndent(),
        )
        assertEquals(
            listOf(TransactionType.Expense, TransactionType.Income, TransactionType.Expense, TransactionType.Expense, TransactionType.Income),
            reading.rows.map { it.type },
        )
        assertEquals(2, reading.skipped)
    }

    @Test
    fun `on a credit card statement a plus sign is what was spent`() {
        val text = """
            Date,Description,Amount
            13/10/2026,Shop,500.00
            14/10/2026,Payment received,-2000.00
        """.trimIndent()
        assertEquals(listOf(TransactionType.Income, TransactionType.Expense), read(text, positiveIsDebit = false).rows.map { it.type })
        assertEquals(listOf(TransactionType.Expense, TransactionType.Income), read(text, positiveIsDebit = true).rows.map { it.type })
    }

    @Test
    fun `a time in the date is kept, and a description that runs over lines is one line`() {
        val reading = read("Date,Description,Debit,Credit\n15/11/2026 14:35,\"Two\n  lines\",10.00,\n")
        assertEquals(LocalTime.of(14, 35), reading.rows.single().time)
        assertEquals("Two lines", reading.rows.single().description)
    }

    @Test
    fun `a heading printed again at a page break is just a skipped line`() {
        val reading = read(
            """
            Date,Description,Debit,Credit
            13/10/2026,One,10.00,
            Date,Description,Debit,Credit
            14/10/2026,Two,,20.00
            """.trimIndent(),
        )
        assertEquals(listOf("One", "Two"), reading.rows.map { it.description })
        assertEquals(1, reading.skipped)
    }

    @Test
    fun `columns that are not there read as empty`() {
        val sheet = StatementFixtures.sheet("Date,Description,Debit\n01/10/2026,Rent,100.00\n")
        val reading = StatementReader.read(sheet, StatementMapping(date = 0, debit = 2), StatementDateFormat.DaySlashMonth, today)
        assertEquals("", reading.rows.single().description)
        assertNull(reading.rows.single().reference)
    }
}
