package com.example.normpix.finance.statement

import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate
import java.time.LocalTime

/**
 * One transaction of a statement as it was read: the day (and time, when the bank gave one), whether it was money out or in, how
 * much (minor units, above zero), the [description] as the bank wrote it and the bank's own [reference] column. [index] is its
 * place among the table's records, so a row can be told apart whatever its text.
 */
class StatementRow(
    val index: Int,
    val date: LocalDate,
    val time: LocalTime?,
    val type: TransactionType,
    val amount: Long,
    val description: String,
    val reference: String?,
)

/** What a statement came to with some columns chosen: the [rows] read and how many records ([skipped]) were not transactions. */
class StatementReading(val rows: List<StatementRow>, val skipped: Int)

/** Turning the records of a statement into its transactions, by the columns the user confirmed. */
object StatementReader {
    private val spaces = Regex("""\s+""")

    /**
     * The transactions in [sheet]'s body, read with [mapping] and [format].
     *
     * A record that has no date in the date column (the bank's closing balance, its disclaimer, a table header printed again at a page
     * break) is skipped, and so is one whose money cannot be told: both a debit and a credit, neither, a figure that is not an amount,
     * or an amount of nothing. Skipped records are counted, never read any further. Which way a single amount went comes from, in
     * order, a "Dr" / "Cr" written beside it, the marker column, and its sign - a minus is money out unless [positiveIsDebit]
     * says plus is (a credit card statement shows what was spent as a plain amount, and a payment or refund with a minus).
     */
    fun read(
        sheet: StatementSheet,
        mapping: StatementMapping,
        format: StatementDateFormat,
        today: LocalDate,
        positiveIsDebit: Boolean = false,
    ): StatementReading {
        val rows = ArrayList<StatementRow>()
        var skipped = 0
        for ((index, record) in sheet.body.withIndex()) {
            val moment = format.parse(sheet.cell(record, mapping.date), today)
            val money = if (moment == null) null else moneyOf(sheet, record, mapping, positiveIsDebit)
            if (moment == null || money == null) {
                skipped++
                continue
            }
            rows += StatementRow(
                index = index,
                date = moment.date,
                time = moment.time,
                type = if (money.first == StatementSide.Debit) TransactionType.Expense else TransactionType.Income,
                amount = money.second,
                description = sheet.cell(record, mapping.description).replace(spaces, " ").trim(),
                reference = sheet.cell(record, mapping.reference).trim().ifEmpty { null },
            )
        }
        return StatementReading(rows, skipped)
    }

    /** Which way and how much [record] moved, or null when that cannot be told (see [read]). */
    private fun moneyOf(sheet: StatementSheet, record: List<String>, mapping: StatementMapping, positiveIsDebit: Boolean): Pair<StatementSide, Long>? {
        if (mapping.twoColumns) {
            val debit = figure(sheet.cell(record, mapping.debit)) ?: return null
            val credit = figure(sheet.cell(record, mapping.credit)) ?: return null
            return when {
                debit > 0 && credit == 0L -> StatementSide.Debit to debit
                credit > 0 && debit == 0L -> StatementSide.Credit to credit
                else -> null
            }
        }
        val cell = sheet.cell(record, mapping.amount)
        if (StatementAmounts.isBlank(cell)) return null
        val amount = StatementAmounts.parse(cell) ?: return null
        if (amount.minor == 0L) return null
        val marked = amount.side ?: sheet.cell(record, mapping.direction).takeIf { mapping.direction != null }?.let(StatementAmounts::sideOf)
        val side = marked ?: if (amount.negative != positiveIsDebit) StatementSide.Debit else StatementSide.Credit
        return side to amount.minor
    }

    /**
     * The size of a debit or credit cell: 0 for one with nothing in it (or a dash, or "0.00" - the column of the other side), its
     * amount otherwise, and null for text that is not an amount. A minus or a Dr / Cr beside it is the column's own business and is
     * ignored.
     */
    private fun figure(cell: String): Long? {
        if (StatementAmounts.isBlank(cell)) return 0
        return StatementAmounts.parse(cell)?.minor
    }
}
