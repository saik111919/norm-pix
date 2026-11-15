package com.example.normpix.finance.statement

/** What a column of a statement is. The labels are the words the import's sheet uses. */
enum class StatementField(val label: String) {
    Date("Date"),
    Description("Description"),
    Debit("Debit (money out)"),
    Credit("Credit (money in)"),
    Amount("Amount (one column)"),
    Direction("Debit / credit marker"),
    Reference("Reference"),
}

/**
 * A statement's table found in its records: what came before the header (the account holder, the period, the address - [preamble]),
 * the [header] row, and every record after it ([body]: the transactions and, after them, whatever summary the bank printed).
 */
class StatementSheet(val preamble: List<List<String>>, val header: List<String>, val body: List<List<String>>) {
    /** How many columns the table has: the widest record. */
    val width: Int = maxOf(header.size, body.maxOfOrNull { it.size } ?: 0)

    /** The header of column [index] as the bank wrote it, or "Column 3" for a column with no header. */
    fun columnName(index: Int): String = header.getOrNull(index)?.takeIf { it.isNotBlank() } ?: "Column ${index + 1}"

    /** The cell of [record] in [column]; empty where the record is shorter. */
    fun cell(record: List<String>, column: Int?): String = column?.let { record.getOrNull(it) }.orEmpty()

    /** The first [count] values of [column] that are not blank: what is in it, to tell it from another one. */
    fun samples(column: Int, count: Int = 3): List<String> =
        body.asSequence().mapNotNull { it.getOrNull(column)?.takeIf(String::isNotBlank) }.take(count).toList()

    /** Every non-blank value in [column] of the table's body. */
    fun values(column: Int?): List<String> =
        if (column == null) emptyList() else body.mapNotNull { it.getOrNull(column)?.takeIf(String::isNotBlank) }
}

/**
 * Which column of a [StatementSheet] holds what, by index (null: none). Money is either two columns, [debit] and [credit] (a bank's
 * "Withdrawal" and "Deposit"), or one signed [amount] - then [direction] may be a Dr / Cr column; [with] keeps the two ways apart.
 */
data class StatementMapping(
    val date: Int? = null,
    val description: Int? = null,
    val debit: Int? = null,
    val credit: Int? = null,
    val amount: Int? = null,
    val direction: Int? = null,
    val reference: Int? = null,
) {
    operator fun get(field: StatementField): Int? = when (field) {
        StatementField.Date -> date
        StatementField.Description -> description
        StatementField.Debit -> debit
        StatementField.Credit -> credit
        StatementField.Amount -> amount
        StatementField.Direction -> direction
        StatementField.Reference -> reference
    }

    /** Whether money is read from a debit and a credit column, rather than from one amount. */
    val twoColumns: Boolean get() = debit != null || credit != null

    /** Whether there is enough to read a transaction from: a date and some way to tell how much and which way. */
    val isUsable: Boolean get() = date != null && (debit != null || credit != null || amount != null)

    /**
     * This mapping with [field] set to [column] (null: none). A column holds one thing, so any other field that was reading it lets
     * go of it; and the two ways of giving money exclude each other - a debit or credit column takes the amount and its marker away,
     * an amount takes the debit and credit away.
     */
    fun with(field: StatementField, column: Int?): StatementMapping {
        var next = this
        if (column != null) {
            for (other in StatementField.entries) if (other != field && next[other] == column) next = next.set(other, null)
            when (field) {
                StatementField.Debit, StatementField.Credit -> next = next.copy(amount = null, direction = null)
                StatementField.Amount -> next = next.copy(debit = null, credit = null)
                else -> Unit
            }
        }
        return next.set(field, column)
    }

    private fun set(field: StatementField, column: Int?): StatementMapping = when (field) {
        StatementField.Date -> copy(date = column)
        StatementField.Description -> copy(description = column)
        StatementField.Debit -> copy(debit = column)
        StatementField.Credit -> copy(credit = column)
        StatementField.Amount -> copy(amount = column)
        StatementField.Direction -> copy(direction = column)
        StatementField.Reference -> copy(reference = column)
    }
}

/**
 * Finding a statement's table and guessing which column is which, from the header names banks use (case and punctuation ignored:
 * "Txn Date", "Chq./Ref.No.", "Withdrawal Amt.").
 */
object StatementLayout {
    /**
     * The table in [records]: the first record that has a date-like column name and an amount-like one (or a debit or a credit
     * name). Everything before it is the [StatementSheet.preamble]; null when there is no such record.
     */
    fun find(records: List<List<String>>): StatementSheet? {
        for ((at, record) in records.withIndex()) {
            val roles = record.map(::roleOf)
            val hasDate = roles.any { it?.field == StatementField.Date }
            val hasMoney = roles.any { it?.field == StatementField.Debit || it?.field == StatementField.Credit || it?.field == StatementField.Amount }
            if (hasDate && hasMoney) return StatementSheet(records.take(at), record, records.drop(at + 1))
        }
        return null
    }

    /**
     * The columns of [sheet] as their names say, with what the names cannot say worked out from the values: a debit / credit marker
     * only where its values are Dr / Cr, and a description - when no header names one - the widest text column left.
     */
    fun guess(sheet: StatementSheet): StatementMapping {
        val best = HashMap<StatementField, Pair<Int, Int>>() // field -> (priority, column)
        for ((column, name) in sheet.header.withIndex()) {
            val role = roleOf(name) ?: continue
            val current = best[role.field]
            if (current == null || role.priority < current.first) best[role.field] = role.priority to column
        }
        var mapping = StatementMapping(
            date = best[StatementField.Date]?.second,
            description = best[StatementField.Description]?.second,
            debit = best[StatementField.Debit]?.second,
            credit = best[StatementField.Credit]?.second,
            reference = best[StatementField.Reference]?.second,
        )
        if (mapping.twoColumns) {
            // A file with a debit and a credit column reads money from them, whatever else is called "Amount".
            return mapping.withDescriptionFallback(sheet)
        }
        mapping = mapping.copy(amount = best[StatementField.Amount]?.second)
        val marker = best[StatementField.Direction]?.second
        if (mapping.amount != null && marker != null && sheet.values(marker).let { values -> values.isNotEmpty() && values.all { StatementAmounts.sideOf(it) != null } }) {
            mapping = mapping.copy(direction = marker)
        }
        return mapping.withDescriptionFallback(sheet)
    }

    /** With no header that says "Narration" or "Description", the unclaimed column whose values are the longest text. */
    private fun StatementMapping.withDescriptionFallback(sheet: StatementSheet): StatementMapping {
        if (description != null) return this
        val taken = setOfNotNull(date, debit, credit, amount, direction, reference)
        val column = (0 until sheet.width).filter { it !in taken }.maxByOrNull { column ->
            val values = sheet.samples(column, 12).filter { value -> value.count(Char::isLetter) > value.count(Char::isDigit) }
            if (values.isEmpty()) 0.0 else values.map { it.length }.average()
        } ?: return this
        return if (sheet.samples(column, 12).any { value -> value.count(Char::isLetter) > value.count(Char::isDigit) }) copy(description = column) else this
    }

    // ------------------------------------------------------------- the names

    private class Role(val field: StatementField, val priority: Int)

    /** A header cell as letters and digits only, lower case, without what is in brackets ("Amount (INR)" is "amount"). */
    internal fun keyOf(header: String): String = header.lowercase().replace(Regex("""\(.*?\)"""), " ").replace(Regex("[^a-z0-9]"), "")

    private val dateKeys = mapOf(
        "date" to 0, "txndate" to 0, "transactiondate" to 0, "trandate" to 0, "transdate" to 0, "txndt" to 0, "trandt" to 0,
        "dateoftransaction" to 0, "transactiondatetime" to 0,
        "postingdate" to 1, "postdate" to 1, "postingdt" to 1, "bookingdate" to 1,
        "valuedate" to 2, "valuedt" to 2, "valdate" to 2,
    )
    private val notADate = listOf("due", "maturity", "statement", "opening", "closing", "balance", "birth")

    private val descriptionKeys = mapOf(
        "narration" to 0, "description" to 0, "particulars" to 0, "transactiondetails" to 0, "transactiondescription" to 0,
        "narrations" to 0, "transactionnarration" to 0, "transactionparticulars" to 0, "descriptionoftransaction" to 0,
        "details" to 1, "remarks" to 1, "transactionremarks" to 1, "remark" to 1,
    )

    private val debitKeys = setOf(
        "withdrawalamt", "withdrawal", "withdrawals", "debit", "dr", "debitamount", "amountdebited", "withdrawalamount", "debits",
        "debitamt", "moneyout", "paidout", "amountdr", "dramount", "withdrawalamtdr", "debitamountdr",
    )
    private val creditKeys = setOf(
        "depositamt", "deposit", "deposits", "credit", "cr", "creditamount", "amountcredited", "depositamount", "credits",
        "creditamt", "moneyin", "paidin", "amountcr", "cramount", "depositamtcr", "creditamountcr",
    )
    private val amountKeys = setOf(
        "amount", "transactionamount", "txnamount", "tranamount", "amt", "transactionamt", "txnamt", "amountinr", "amountrs", "amountinrs",
    )
    private val directionKeys = setOf(
        "drcr", "crdr", "debitcredit", "creditdebit", "type", "transactiontype", "txntype", "trantype", "dc", "cd",
        "debitcreditindicator", "drcrindicator", "crdrindicator", "indicator", "drcrflag", "crdrflag",
    )
    private val referenceKeys = setOf(
        "chqrefno", "refno", "reference", "referenceno", "referencenumber", "chequeno", "chequenumber", "chqno", "utr", "utrno", "utrnumber",
        "transactionid", "txnid", "tranid", "transid", "rrn", "chqrefnumber", "refnumber", "refnochequeno", "refnochqno", "chequerefno",
        "instrumentno", "instrumentid", "bankref", "bankreference", "refchqno", "chqnorefno", "chequerefnumber",
    )

    private fun roleOf(header: String): Role? {
        val key = keyOf(header)
        if (key.isEmpty()) return null
        // A balance is never what is read, and it is what "Amount" columns most often sit beside.
        if (key.contains("balance") || key == "bal" || key.startsWith("bal") && key.length <= 6) return null
        if (key in directionKeys) return Role(StatementField.Direction, 0)
        if (key in debitKeys) return Role(StatementField.Debit, 0)
        if (key in creditKeys) return Role(StatementField.Credit, 0)
        if (key.contains("withdrawal") && !key.contains("date")) return Role(StatementField.Debit, 1)
        if (key.contains("deposit") && !key.contains("date")) return Role(StatementField.Credit, 1)
        if (key.startsWith("debit") && !key.contains("date") && !key.contains("card")) return Role(StatementField.Debit, 1)
        if (key.startsWith("credit") && !key.contains("date") && !key.contains("card") && !key.contains("limit")) return Role(StatementField.Credit, 1)
        if (key in amountKeys) return Role(StatementField.Amount, 0)
        if (key.startsWith("amount") && !key.contains("date")) return Role(StatementField.Amount, 1)
        if (key in referenceKeys) return Role(StatementField.Reference, 0)
        if ((key.startsWith("ref") && !key.startsWith("refund")) || key.startsWith("chq") || key.startsWith("cheque") || key.contains("utr")) {
            return Role(StatementField.Reference, 1)
        }
        dateKeys[key]?.let { return Role(StatementField.Date, it) }
        if ((key.endsWith("date") || key.endsWith("dt")) && notADate.none { key.contains(it) }) return Role(StatementField.Date, 3)
        descriptionKeys[key]?.let { return Role(StatementField.Description, it) }
        if (key.contains("narration") || key.contains("description") || key.contains("particulars")) return Role(StatementField.Description, 1)
        return null
    }
}
