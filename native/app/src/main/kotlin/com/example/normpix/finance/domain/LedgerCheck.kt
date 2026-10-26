package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/*
 * Checking the ledger again (Settings > Data & backup > Check and recalculate). Every figure the app shows is worked out
 * from the transactions each time, so there is nothing stale to recompute; what can be wrong is the data under them. This
 * reads all of it and says, in two lists:
 *
 *  - what it puts right by itself, because there is one right answer and nothing is lost: a sub-category whose parent is
 *    gone or can't hold it stands on its own; a budget on an income category goes; a repeating payment that would record an
 *    occurrence it already has moves on past it; a default account that is gone or archived is no longer the default;
 *  - what it can only point out, because only the user knows: an entry dated after today, two entries that look like one
 *    entered twice, a card's bill recorded as spending, money moved between two of the user's accounts recorded as an expense
 *    on one and income on the other, an archived account still holding money, an entry filed under a category of the other
 *    kind. Each of these makes a total read wrong, and each is shown with the entries it is about.
 *
 * Pure: nothing here reads the clock, the database or the screen.
 */

/** A sub-category to stand on its own: its parent is missing, archived while it isn't, of the other kind, or a sub-category itself. */
data class LooseSubCategory(val category: Category, val reason: String)

/** A repeating payment whose next date is not after an occurrence it has already recorded, and the date it should go on from. */
data class RuleCatchUp(val rule: RecurringRule, val nextDate: LocalDateTime)

/** Two entries that look like one transfer between the user's own accounts, recorded as an expense on one and income on the other. */
data class SelfTransferPair(val expense: FinanceTransaction, val income: FinanceTransaction)

/** An expense that looks like the bill of one of the user's credit cards, paid from another account (see [cardBillTarget]). */
data class CardBillExpense(val expense: FinanceTransaction, val card: Account)

/** An archived account that still holds (or owes) money: it counts in every total, but no longer shows in the Wallet. */
data class ArchivedBalance(val account: Account, val balance: Long)

/** What a check of the ledger found. */
data class LedgerIssues(
    // Put right by the check
    val looseSubCategories: List<LooseSubCategory> = emptyList(),
    val incomeBudgets: List<Budget> = emptyList(),
    val ruleCatchUps: List<RuleCatchUp> = emptyList(),
    val staleDefaultAccount: Boolean = false,
    // Pointed out
    val upcoming: List<FinanceTransaction> = emptyList(),
    val duplicates: List<List<FinanceTransaction>> = emptyList(),
    val cardBills: List<CardBillExpense> = emptyList(),
    val selfTransfers: List<SelfTransferPair> = emptyList(),
    val archivedBalances: List<ArchivedBalance> = emptyList(),
    val wrongCategory: List<FinanceTransaction> = emptyList(),
) {
    /** How many things the check puts right by itself. */
    val repairCount: Int get() = looseSubCategories.size + incomeBudgets.size + ruleCatchUps.size + (if (staleDefaultAccount) 1 else 0)

    /** How many things are left for the user to look at. */
    val findingCount: Int
        get() = upcoming.size + duplicates.size + cardBills.size + selfTransfers.size + archivedBalances.size + wrongCategory.size
}

/** How far apart two halves of one transfer may be dated and still be taken for one: a day, as a bank posts the other side. */
private const val SELF_TRANSFER_DAYS = 1L

/**
 * Checks the whole ledger as of [now] (see the top of this file). [balances] are the accounts' balances as of today;
 * [bankKeyIn] reads a bank from text (see [cardBillTarget]).
 */
fun checkLedger(
    accounts: List<Account>,
    categories: List<Category>,
    transactions: List<FinanceTransaction>,
    rules: List<RecurringRule>,
    budgets: List<Budget>,
    balances: Map<Long, Long>,
    defaultAccountId: Long?,
    now: LocalDateTime,
    bankKeyIn: (String) -> String? = { null },
): LedgerIssues {
    val byId = categories.associateBy { it.id }
    val accountsById = accounts.associateBy { it.id }
    val cutoff = settledCutoff(now)

    val loose = categories.mapNotNull { c ->
        val parentId = c.parentId ?: return@mapNotNull null
        val parent = byId[parentId]
        val reason = when {
            parentId == c.id -> "it was set as its own parent"
            parent == null -> "its parent category is gone"
            parent.parentId != null -> "its parent is a sub-category itself"
            parent.type != c.type -> "its parent is ${if (parent.type == CategoryType.Income) "an income" else "an expense"} category"
            parent.archived && !c.archived -> "its parent is archived"
            else -> null
        }
        reason?.let { LooseSubCategory(c, it) }
    }

    val incomeBudgets = budgets.filter { b -> b.categoryId != null && byId[b.categoryId]?.type == CategoryType.Income }

    // An active rule must never record a day it has already recorded: its next date goes on past the latest one it has.
    val latestByRule = transactions.filter { it.recurringId != null }.groupBy { it.recurringId }.mapValues { (_, list) -> list.maxOf { it.occurredAt } }
    val catchUps = rules.mapNotNull { rule ->
        val latest = latestByRule[rule.id] ?: return@mapNotNull null
        if (!rule.active || rule.nextDate.isAfter(latest)) return@mapNotNull null
        var next = rule.nextDate
        var guard = 0
        while (!next.isAfter(latest) && guard++ < MAX_CATCH_UP) next = nextRecurrence(next, rule.frequency, rule.interval, anchor = rule.startDate)
        RuleCatchUp(rule, next)
    }

    val defaultAccount = accountsById[defaultAccountId]
    val staleDefault = defaultAccountId != null && (defaultAccount == null || defaultAccount.archived)

    val upcoming = transactions.filter { !it.occurredAt.isBefore(cutoff) }.sortedBy { it.occurredAt }

    val wrongCategory = transactions.filter { t ->
        t.type != TransactionType.Transfer && t.categoryId != null &&
            byId[t.categoryId]?.let { it.type != CategoryType.forTransaction(t.type) } == true
    }

    val cardBills = transactions.mapNotNull { t ->
        if (t.type != TransactionType.Expense) return@mapNotNull null
        val text = listOfNotNull(t.payee, t.note, byId[t.categoryId]?.name).joinToString(" ")
        cardBillTarget(text, accounts, paidFrom = t.accountId, bankKeyIn = bankKeyIn)?.let { CardBillExpense(t, it) }
    }
    val inCardBills = cardBills.mapTo(mutableSetOf()) { it.expense.id }

    val duplicates = transactions
        .groupBy { t ->
            val payee = t.payee?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            // Without a payee to tell two apart, only entries at the very same minute are taken for one entered twice.
            DuplicateKey(t.type, t.amount, t.accountId, t.toAccountId, t.occurredAt.toLocalDate(), payee, if (payee == null) t.occurredAt.withSecond(0).withNano(0) else null)
        }
        .values
        .filter { it.size > 1 }
        .map { group -> group.sortedBy { it.occurredAt } }
        .sortedByDescending { group -> group.first().occurredAt }

    val selfTransfers = pairSelfTransfers(transactions.filter { it.id !in inCardBills }, accountsById)

    val archived = accounts.mapNotNull { a ->
        val id = a.id ?: return@mapNotNull null
        val balance = balances[id] ?: 0L
        if (a.archived && balance != 0L) ArchivedBalance(a, balance) else null
    }

    return LedgerIssues(
        looseSubCategories = loose,
        incomeBudgets = incomeBudgets,
        ruleCatchUps = catchUps,
        staleDefaultAccount = staleDefault,
        upcoming = upcoming,
        duplicates = duplicates,
        cardBills = cardBills,
        selfTransfers = selfTransfers,
        archivedBalances = archived,
        wrongCategory = wrongCategory,
    )
}

/** Walking a rule on past what it recorded stops here, whatever the rule (a daily one decades out of step). */
private const val MAX_CATCH_UP = 100_000

private data class DuplicateKey(
    val type: TransactionType,
    val amount: Long,
    val account: Long,
    val to: Long?,
    val day: LocalDate,
    val payee: String?,
    val minute: LocalDateTime?,
)

/**
 * The expense-and-income pairs that look like one transfer between two of the user's accounts: the same amount out of one
 * account and into another within [SELF_TRANSFER_DAYS], neither of them a cash account's (cash taken out or paid in is spending
 * and income of its own), spent on no one, and not made by a repeating payment. Each entry is in one pair at most, the closest
 * in time first.
 */
private fun pairSelfTransfers(transactions: List<FinanceTransaction>, accounts: Map<Long?, Account>): List<SelfTransferPair> {
    // A repeating payment's entry is what it says it is (a salary in, the rent out), however its amount matches another's.
    fun eligible(t: FinanceTransaction) =
        accounts[t.accountId]?.let { it.kind != AccountKind.Cash } == true && t.personId == null && t.recurringId == null
    val incomes = transactions.filter { it.type == TransactionType.Income && eligible(it) }.groupBy { it.amount }
    val used = mutableSetOf<Long?>()
    val pairs = mutableListOf<SelfTransferPair>()
    val expenses = transactions.filter { it.type == TransactionType.Expense && eligible(it) }.sortedByDescending { it.occurredAt }
    for (e in expenses) {
        val match = incomes[e.amount].orEmpty()
            .filter { it.id !in used && it.accountId != e.accountId && abs(ChronoUnit.MINUTES.between(e.occurredAt, it.occurredAt)) <= SELF_TRANSFER_DAYS * 24 * 60 }
            .minByOrNull { abs(ChronoUnit.MINUTES.between(e.occurredAt, it.occurredAt)) }
            ?: continue
        used += match.id
        pairs += SelfTransferPair(e, match)
    }
    return pairs
}

private fun abs(v: Long): Long = if (v < 0) -v else v

/**
 * The one transfer [pair] stands for: from the account the expense left, to the one the income came into, on the expense's
 * date, keeping the expense's note and the payee as words in it (a transfer has no payee).
 */
fun SelfTransferPair.asTransfer(now: LocalDateTime): FinanceTransaction = expense.copy(
    type = TransactionType.Transfer,
    toAccountId = income.accountId,
    categoryId = null,
    personId = null,
    payee = null,
    note = listOfNotNull("Self transfer", expense.note?.takeIf { it.isNotBlank() } ?: expense.payee?.takeIf { it.isNotBlank() }).joinToString(" · "),
    updatedAt = now,
)

/** The transfer onto [CardBillExpense.card] its expense stands for (see [cardBillTarget]). */
fun CardBillExpense.asTransfer(now: LocalDateTime): FinanceTransaction = expense.copy(
    type = TransactionType.Transfer,
    toAccountId = card.id,
    categoryId = null,
    personId = null,
    payee = null,
    note = listOfNotNull("Card bill payment", expense.note?.takeIf { it.isNotBlank() } ?: expense.payee?.takeIf { it.isNotBlank() }).joinToString(" · "),
    updatedAt = now,
)
