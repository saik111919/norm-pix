package com.example.normpix.finance.domain

import java.time.LocalDateTime

/*
 * "Frequent" in a new expense or income (owner, 2026-11-18, from the suggestions list: "Add again and favourites - your most-used
 * payees appear as chips at the top of the Add sheet"). Worked out from what was entered by hand lately, not kept anywhere: nothing to
 * set up, nothing in the database or a backup, and a payee that stops being used drops off by itself. Pure.
 */

/** How far back the picks look: about four months, long enough for a monthly habit to count, short enough to follow a change. */
const val FREQUENT_DAYS = 120L

/** How many picks the form offers at most. */
const val FREQUENT_LIMIT = 6

/**
 * Something entered again and again: a payee (or, with none, just a category) with the category, the account and the amount it had
 * the last time. [uses] is how often it was entered in the time looked at, [lastAt] when it last was.
 */
data class FrequentEntry(
    val type: TransactionType,
    val categoryId: Long,
    /** The payee as it was last written, or null for a category used with no payee. */
    val payee: String?,
    val accountId: Long,
    val amount: Long,
    val uses: Int,
    val lastAt: LocalDateTime,
)

/**
 * The expenses or income ([type]) entered most often among [transactions], most used first (the latest first between equals): each
 * the same category with the same payee - its spelling and spaces aside - entered at least [minUses] times. Only what the user
 * entered: a repeating payment's own entries (`recurringId`) are already recorded for them, and a part of a split payment is not a
 * payment of its own. Transfers have no picks, and nothing dated after [now] (entered ahead) counts.
 */
fun frequentEntries(
    transactions: List<FinanceTransaction>,
    type: TransactionType,
    now: LocalDateTime,
    limit: Int = FREQUENT_LIMIT,
    minUses: Int = 2,
): List<FrequentEntry> {
    if (type == TransactionType.Transfer || limit <= 0) return emptyList()
    return transactions
        .asSequence()
        .filter { it.type == type && it.categoryId != null && it.recurringId == null && it.splitGroup == null && !it.occurredAt.isAfter(now) }
        // The payee as the repeat detector compares payees ("Swiggy", "swiggy " and "SWIGGY" are one); no payee is a group of its own.
        .groupBy { it.categoryId to payeeKey(it.payee).orEmpty() }
        .values
        .filter { it.size >= minUses }
        .map { uses ->
            val last = uses.maxWith(compareBy<FinanceTransaction> { it.occurredAt }.thenBy { it.id ?: 0L })
            FrequentEntry(
                type = type,
                categoryId = last.categoryId!!,
                payee = last.payee?.trim()?.takeIf { it.isNotEmpty() },
                accountId = last.accountId,
                amount = last.amount,
                uses = uses.size,
                lastAt = last.occurredAt,
            )
        }
        .sortedWith(compareByDescending<FrequentEntry> { it.uses }.thenByDescending { it.lastAt })
        .take(limit)
}
