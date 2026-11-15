package com.example.normpix.finance.statement

import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.MerchantCategories
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A transaction of a statement as it would be saved: the [row] as read, its [payee], [note] and [reference] ([StatementNarration]), the
 * category it would be filed under ([categoryId]: null when the user has no category of its kind to file it under, and then it can't
 * be imported) and [duplicate] - the entry already in the ledger that it looks like, or null.
 */
class StatementDraft(
    val row: StatementRow,
    val payee: String?,
    val note: String?,
    val reference: String?,
    val categoryId: Long?,
    val duplicate: FinanceTransaction?,
) {
    /** Whether it can be saved at all: it has a category to go under. */
    val importable: Boolean get() = categoryId != null

    /** Whether the preview ticks it to start with: importable, and not already in the ledger. */
    val tickedByDefault: Boolean get() = importable && duplicate == null
}

/**
 * Turning a statement's rows into what the preview shows and what is saved: the payee, the category guess, the entries that are in the
 * ledger already, and finally the transactions themselves. Only reads the ledger; saving is the caller's (one SQL transaction, see
 * `FinanceRepository.saveTransactions`).
 */
object StatementImport {
    /** The time of day of an entry whose statement gave only the day: the middle of it, so it is no day's first or last thing. */
    val DAY_ONLY_TIME: LocalTime = LocalTime.NOON

    /** How many entries of the ledger one row is compared with. */
    private const val CANDIDATES = 25

    /**
     * A draft for each of [rows], in order. The category is the user's own last filing of the payee, else the app's table of merchants
     * (by the payee, then by the whole narration), else "Other" / "Other income", else the first category of its kind.
     *
     * An entry is **already in NormPix** when `FinanceRepository.possibleDuplicates` finds it: the same reference, or the same type and
     * amount on the same day (within three hours when the statement gives a time). Two passes, so a payee spelt differently by the bank
     * and by the user still matches: first with the payee as the bank names it, then - for the rows left - on amount and day alone. An
     * entry in the ledger accounts for **one** row (a statement with two ₹100 coffees in a day matches one ₹100 coffee once), so two
     * identical rows are never both put down to the same entry.
     */
    suspend fun prepare(rows: List<StatementRow>, categories: List<Category>, repository: FinanceRepository): List<StatementDraft> {
        val entries = rows.map { StatementNarration.read(it.description, it.reference) }
        val filed = HashMap<Pair<TransactionType, String>, Long?>()
        val categoryIds = rows.indices.map { i ->
            val row = rows[i]
            val payee = entries[i].payee
            var history: Long? = null
            if (payee != null) {
                val key = row.type to payee.lowercase()
                if (key !in filed) filed[key] = repository.lastCategoryForPayee(payee, row.type)
                history = filed[key]
            }
            history ?: guess(row, payee, categories)
        }
        val claimed = HashSet<Long>()
        val duplicates = arrayOfNulls<FinanceTransaction>(rows.size)
        for (strict in listOf(true, false)) {
            for (i in rows.indices) {
                if (duplicates[i] != null) continue
                val row = rows[i]
                val entry = entries[i]
                val found = repository.possibleDuplicates(
                    row.type,
                    row.amount,
                    LocalDateTime.of(row.date, row.time ?: LocalTime.MIDNIGHT),
                    if (strict) entry.payee else null,
                    entry.reference,
                    limit = CANDIDATES,
                    timeKnown = row.time != null,
                )
                val match = found.firstOrNull { other -> other.id != null && other.id !in claimed && sameAs(other, row, entry.reference) } ?: continue
                claimed += match.id!!
                duplicates[i] = match
            }
        }
        return rows.indices.map { i ->
            StatementDraft(rows[i], entries[i].payee, entries[i].note, entries[i].reference, categoryIds[i], duplicates[i])
        }
    }

    /** [draft]'s entries saved as [accountId]'s transactions, as of [now]; every draft must have a category. */
    fun transactions(drafts: List<StatementDraft>, accountId: Long, now: LocalDateTime): List<FinanceTransaction> = drafts.map { draft ->
        FinanceTransaction(
            type = draft.row.type,
            amount = draft.row.amount,
            accountId = accountId,
            categoryId = draft.categoryId,
            occurredAt = LocalDateTime.of(draft.row.date, draft.row.time ?: DAY_ONLY_TIME),
            payee = draft.payee,
            note = draft.note,
            createdAt = now,
            updatedAt = now,
        )
    }

    /**
     * Whether [other] is [row] already: the repository's search matches a reference anywhere in a note, so one that only contains the
     * reference's characters inside a longer number is not taken for it - it must carry exactly that reference, or be the same kind and
     * amount of entry.
     */
    private fun sameAs(other: FinanceTransaction, row: StatementRow, reference: String?): Boolean {
        if (other.type == row.type && other.amount == row.amount) return true
        return reference != null && referencesIn(other.note).any { it.equals(reference, ignoreCase = true) }
    }

    private val noteReference = Regex("""\bRef\s+([A-Za-z0-9]{6,35})\b""")

    private fun referencesIn(note: String?): List<String> =
        if (note == null) emptyList() else noteReference.findAll(note).map { it.groupValues[1] }.toList()

    private fun guess(row: StatementRow, payee: String?, categories: List<Category>): Long? {
        val kind = CategoryType.forTransaction(row.type)
        if (row.type == TransactionType.Expense) {
            MerchantCategories.categoryFor(payee, row.type, categories)?.let { return it }
            MerchantCategories.categoryFor(row.description, row.type, categories)?.let { return it }
        }
        val usable = categories.filter { it.type == kind && !it.archived && it.parentId == null }
        val other = if (kind == CategoryType.Income) "Other income" else "Other"
        return (usable.firstOrNull { it.name.equals(other, ignoreCase = true) } ?: usable.firstOrNull())?.id
    }
}
