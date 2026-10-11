package com.example.normpix.finance.domain

import java.time.LocalDateTime

/**
 * What a transaction does to money. Amounts are always positive; the type
 * carries the direction.
 */
enum class TransactionType(val key: String) {
    /** Money leaves [FinanceTransaction.accountId]. */
    Expense("expense"),

    /** Money arrives in [FinanceTransaction.accountId]. */
    Income("income"),

    /**
     * Money moves from [FinanceTransaction.accountId] to
     * [FinanceTransaction.toAccountId]. Never counts as income or spending.
     */
    Transfer("transfer");

    companion object {
        fun fromKey(key: String): TransactionType? = entries.firstOrNull { it.key == key }
    }
}

enum class CategoryType(val key: String) {
    Expense("expense"),
    Income("income");

    companion object {
        fun forTransaction(type: TransactionType): CategoryType =
            if (type == TransactionType.Income) Income else Expense

        fun fromKey(key: String): CategoryType? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Presentation hint for an account (drives its icon). Not a business rule -
 * every kind behaves identically in the ledger, and the name is free text.
 */
enum class AccountKind(val key: String) {
    Cash("cash"),
    Bank("bank"),
    Card("card"),
    Wallet("wallet"),
    Savings("savings"),
    Other("other");

    companion object {
        fun fromKey(key: String): AccountKind? = entries.firstOrNull { it.key == key }
    }
}

data class Account(
    val id: Long? = null,
    val name: String,
    val kind: AccountKind = AccountKind.Other,
    /**
     * Balance before the first recorded transaction, in minor units. May be
     * negative (e.g. a credit card that already carries a balance).
     */
    val openingBalance: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    /**
     * The bank this account belongs to (see `BankCatalog`), or null for one that has none - cash, say - or none chosen
     * yet. A grouping, not an identity: transactions refer to the account, so changing this never touches history.
     */
    val bankKey: String? = null,
    /**
     * What a credit card lets its holder borrow, in minor units - always > 0; null where it was not given, and for anything
     * that is not a card. With it the card can say how much is left to spend (see [creditRoom]).
     */
    val creditLimit: Long? = null,
)

/** A bank the user added, for the ones the built-in directory doesn't list. Built-in banks are not stored; they are referred to by their directory id. */
data class CustomBank(
    /** `custom-...`; never changes, so accounts and cards keep pointing at it. */
    val key: String,
    val name: String,
    val createdAt: LocalDateTime,
)

data class Category(
    val id: Long? = null,
    val name: String,
    val type: CategoryType,
    /** Key into the app's curated icon set (see `CategoryVisuals`), or an emoji as "emoji:" and the emoji (see `EmojiIcon`). */
    val icon: String,
    /** Index into the app's harmonized category palette. */
    val color: Int = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    /** The category this is a sub-category of; null for a top-level one. Two levels at most, and the same [type] (see `CategoryTree`). */
    val parentId: Long? = null,
)

data class FinanceTransaction(
    val id: Long? = null,
    val type: TransactionType,
    /** Always > 0, in minor units. */
    val amount: Long,
    val accountId: Long,
    /** Destination account; set only for transfers. */
    val toAccountId: Long? = null,
    /** Set for expenses and income; null for transfers. */
    val categoryId: Long? = null,
    val occurredAt: LocalDateTime,
    /** Merchant (expense) or source (income). */
    val payee: String? = null,
    val note: String? = null,
    /** The recurring rule that generated this entry, if any. */
    val recurringId: Long? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    /**
     * The person this expense was spent on, or null for one that is not for anyone in particular. Only an expense has one -
     * the link is a label on the same record, so the transaction counts once in every total whether or not it has a person.
     */
    val personId: Long? = null,
    /**
     * A picture attached to it - a bill, a shop receipt, a payment screenshot - by the file name it is kept under in the
     * app's own storage ([isPictureName]); null for none.
     */
    val picture: String? = null,
)

/** Whether [name] is the name of an attached picture as the app files them: never a path, so it can only name its own files. */
fun isPictureName(name: String?): Boolean = name != null && PICTURE_NAME.matches(name)

private val PICTURE_NAME = Regex("""pic-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.jpg""")

/**
 * Someone the user spends money on (a parent, a partner, a friend), so that what each one costs can be seen. This is only a
 * label for expenses: there is no balance, no "owes" and nothing to settle. Referred to by [id], never by name, so renaming
 * a person renames them everywhere at once.
 */
data class Person(
    val id: Long? = null,
    val name: String,
    /** One emoji for the avatar; null shows the name's first letter instead. */
    val emoji: String? = null,
    /** Index into the app's harmonized color palette (the one categories use). */
    val color: Int = 0,
    /** Hidden from pickers and the main list, but still on the expenses already linked to them. */
    val archived: Boolean = false,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * A monthly spending limit - overall ([categoryId] null) or for one category
 * - that applies from [effectiveMonth] until a later row for the same target
 * replaces it. A null [amount] ends the budget from that month on. This keeps
 * every past month's budget intact for history.
 */
data class Budget(
    val id: Long? = null,
    val categoryId: Long? = null,
    val amount: Long?,
    val effectiveMonth: MonthKey,
) {
    val isOverall: Boolean get() = categoryId == null
}

enum class RecurrenceFrequency(val key: String) {
    Daily("daily"),
    Weekly("weekly"),
    Monthly("monthly"),
    Yearly("yearly");

    companion object {
        fun fromKey(key: String): RecurrenceFrequency? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A transaction that repeats. Occurrences are materialized as real
 * [FinanceTransaction]s on device when they come due.
 */
data class RecurringRule(
    val id: Long? = null,
    val type: TransactionType,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val payee: String? = null,
    val note: String? = null,
    val frequency: RecurrenceFrequency,
    /** Every [interval] days/weeks/months/years (>= 1). */
    val interval: Int = 1,
    val startDate: LocalDateTime,
    /** Last day an occurrence may fall on (inclusive), or null for no end. */
    val endDate: LocalDateTime? = null,
    /** The next occurrence that hasn't been recorded yet. */
    val nextDate: LocalDateTime,
    val active: Boolean = true,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    /** Who the expenses it records are spent on; only an expense can have one (see `Person`). */
    val personId: Long? = null,
) {
    /** True once no further occurrence can happen. */
    val isFinished: Boolean get() = endDate?.let { nextDate.isAfter(it) } ?: false
}

/** A user-correctable problem with input (shown in the UI as-is). */
class FinanceValidationException(override val message: String) : Exception(message)

/**
 * An operation that would break dependent data (e.g. deleting an account that
 * still has transactions). [message] explains what to do instead.
 */
class FinanceConflictException(override val message: String) : Exception(message)

/**
 * How a transaction repeats when it is saved with a repeat ([com.example.normpix.finance.data.FinanceRepository.saveTransactionAndRepeat]):
 * the schedule half of a [RecurringRule] - every [interval] [frequency]s, until [endDate] (inclusive) if there is one.
 */
data class RepeatSchedule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val endDate: LocalDateTime? = null,
)
