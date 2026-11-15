package com.example.normpix.finance.domain

/*
 * Split payments (2026-11-15, database version 17, backup format 7). One real payment - an expense or an income, never a transfer - shown
 * as two to ten parts, each its own transaction row with its own category, amount and note, and all of them sharing a random split group
 * id and everything else: account, date and time, payee, person ("For"), tags and picture. The payment's total is the parts' sum.
 *
 * Because each part is an ordinary row, category totals, budgets, Insights and the forecast are right with no change of their own;
 * only a count of transactions goes up. This file is the one place that says what a group that holds together is, for the backup's
 * validation, the ledger check and a snapshot (which never carries the mark of a group that does not hold). Pure: nothing here reads
 * the clock or the database.
 */

/** A split payment has at least this many parts: one part is just a payment. */
const val MIN_SPLIT_PARTS = 2

/** ... and at most this many. */
const val MAX_SPLIT_PARTS = 10

/** The longest split group id a backup file may carry (the app's own are 36 characters). */
const val MAX_SPLIT_GROUP_LENGTH = 64

/**
 * What is a part's own when a split is saved: its category, its amount (above zero) and an optional note. [id] is the row it was
 * - kept, so a part that is edited stays the same row - or null for a part that is new.
 */
data class SplitPart(
    val categoryId: Long?,
    val amount: Long,
    val note: String? = null,
    val id: Long? = null,
)

/** Why a split group does not hold together; [problem] finishes the sentence "A split payment ...". */
enum class SplitFault(val problem: String) {
    TooFew("has only one part"),
    TooMany("has more than $MAX_SPLIT_PARTS parts"),
    Transfer("includes a transfer"),
    MixedType("mixes spending and income"),
    MixedAccount("is on more than one account"),
    MixedMoment("is dated at more than one time"),
    MixedPayee("names more than one payee"),
    MixedPerson("is for more than one person"),
}

/** The split group [rows] - all the rows that carry one id - is not, or null when it holds together. */
fun splitFault(rows: List<FinanceTransaction>): SplitFault? {
    if (rows.size < MIN_SPLIT_PARTS) return SplitFault.TooFew
    if (rows.size > MAX_SPLIT_PARTS) return SplitFault.TooMany
    if (rows.any { it.type == TransactionType.Transfer }) return SplitFault.Transfer
    val first = rows.first()
    return when {
        rows.any { it.type != first.type } -> SplitFault.MixedType
        rows.any { it.accountId != first.accountId } -> SplitFault.MixedAccount
        rows.any { it.occurredAt != first.occurredAt } -> SplitFault.MixedMoment
        rows.any { splitPayeeOf(it) != splitPayeeOf(first) } -> SplitFault.MixedPayee
        rows.any { it.personId != first.personId } -> SplitFault.MixedPerson
        else -> null
    }
}

/** A payee as groups compare them: the saved one is trimmed and a blank one is none, so the two spellings of "no payee" agree. */
private fun splitPayeeOf(t: FinanceTransaction): String? = t.payee?.trim()?.takeIf { it.isNotEmpty() }

/** A split group that does not hold together: its rows (oldest first) and why. */
data class BrokenSplit(val group: String, val rows: List<FinanceTransaction>, val fault: SplitFault)

/** The rows of [transactions] that carry a split group, by group, each group's rows by id (as they were entered). */
fun splitGroupsOf(transactions: List<FinanceTransaction>): Map<String, List<FinanceTransaction>> =
    transactions.filter { it.splitGroup != null }
        .groupBy { it.splitGroup!! }
        .mapValues { (_, rows) -> rows.sortedBy { it.id ?: Long.MAX_VALUE } }

/** Every split group in [transactions] that does not hold together, the first row of the oldest group first. */
fun brokenSplitGroups(transactions: List<FinanceTransaction>): List<BrokenSplit> =
    splitGroupsOf(transactions).mapNotNull { (group, rows) -> splitFault(rows)?.let { BrokenSplit(group, rows, it) } }
        .sortedBy { it.rows.first().id ?: Long.MAX_VALUE }

/** [transactions] with the split mark taken off every row of a group that does not hold together; all else exactly as it is. */
fun withoutBrokenSplits(transactions: List<FinanceTransaction>): List<FinanceTransaction> {
    val broken = brokenSplitGroups(transactions).mapTo(HashSet()) { it.group }
    if (broken.isEmpty()) return transactions
    return transactions.map { if (it.splitGroup in broken) it.copy(splitGroup = null) else it }
}

/** What a split payment adds up to. */
fun splitTotal(rows: List<FinanceTransaction>): Long = rows.sumOf { it.amount }

/**
 * The category a split payment is filed under when it becomes one payment again, if the user doesn't pick: the largest part's (the
 * first of them when two are as large).
 */
fun largestPartCategory(rows: List<FinanceTransaction>): Long? = rows.maxByOrNull { it.amount }?.categoryId

/** The notes of the parts as one note: each part's, tidied and without the empty ones, joined by " · "; null when none has one. */
fun joinedSplitNotes(rows: List<FinanceTransaction>): String? =
    rows.mapNotNull { it.note?.trim()?.takeIf { note -> note.isNotEmpty() } }.joinToString(" · ").takeIf { it.isNotEmpty() }
