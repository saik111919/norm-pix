package com.example.normpix.finance.domain

import java.time.LocalDateTime

/*
 * Money lent and borrowed between the user and a person (owner, 2026-11-19, from the suggestions list: "Lent and borrowed with people -
 * 'Ravi owes me ₹500', then settle up with one tap, which records the transfer"). Each record is one movement of what is owed, in one of
 * four kinds; a person's balance is the sum of their records, positive when they owe the user. A record may name the transaction that
 * moved the money through one of the user's accounts (lending ₹500 from Cash is an expense in "Lent to someone", as the app already
 * files it), or none (they owe half of a bill the user already recorded). Kept in the finance database (table `debts`, version 18) and
 * in a backup (format 8). Pure: no Android.
 */

/** What a record says happened. [sign] is its effect on what the person owes the user. */
enum class DebtKind(val key: String, val sign: Int) {
    /** The user gave them money: they owe the user that much more. */
    Lent("lent", 1),

    /** They gave the user money: the user owes them that much more. */
    Borrowed("borrowed", -1),

    /** They paid back money the user lent them: they owe that much less. */
    RepaidToMe("repaid_to_me", -1),

    /** The user paid back money they borrowed: the user owes that much less. */
    RepaidByMe("repaid_by_me", 1),
    ;

    /** Whether the money came to the user (an income when it is recorded in an account) rather than went from them (an expense). */
    val moneyIn: Boolean get() = this == Borrowed || this == RepaidToMe

    /** Whether this pays back what is owed, rather than making a new debt. */
    val settles: Boolean get() = this == RepaidToMe || this == RepaidByMe

    /** The record in words, with [name] the person's: "You lent Ravi", "Ravi paid you back". */
    fun phrase(name: String): String = when (this) {
        Lent -> "You lent $name"
        Borrowed -> "You borrowed from $name"
        RepaidToMe -> "$name paid you back"
        RepaidByMe -> "You paid $name back"
    }

    /** The default category this kind is filed under when the money moved through an account, by name (see [debtCategoryFor]). */
    val categoryName: String
        get() = when (this) {
            Lent -> "Lent to someone"
            RepaidByMe -> "Repaid to someone"
            Borrowed -> "Borrowed money"
            RepaidToMe -> "Loan repaid to me"
        }

    /** The category family [categoryName] is under in the app's defaults. */
    val familyName: String get() = if (moneyIn) "Money received" else "Money sent"

    companion object {
        fun of(key: String): DebtKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One record of money owed between the user and person [personId]: [amount] (above zero) of [kind], on [occurredAt]. [transactionId]
 * is the transaction that moved the money through one of the user's accounts, or null for a record that is only what is owed (half of a
 * bill already recorded) - or whose transaction has since been deleted (the record stays: `ON DELETE SET NULL`).
 */
data class DebtEntry(
    val id: Long? = null,
    val personId: Long,
    val kind: DebtKind,
    val amount: Long,
    val occurredAt: LocalDateTime,
    val note: String? = null,
    val transactionId: Long? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    /** Its effect on what the person owes the user: positive adds to it, negative takes from it. */
    val signed: Long get() = kind.sign * amount
}

/** What [entries] (all about one person) come to: positive when they owe the user, negative when the user owes them, zero when settled. */
fun debtBalance(entries: List<DebtEntry>): Long = entries.sumOf { it.signed }

/** Every person's balance in [entries], by person id; a person who is settled (zero) is left out. */
fun debtBalances(entries: List<DebtEntry>): Map<Long, Long> =
    entries.groupBy { it.personId }.mapValues { (_, list) -> debtBalance(list) }.filterValues { it != 0L }

/** What is owed across [balances]: to the user ([owedToMe], above zero) and by the user ([iOwe], above zero). */
data class DebtTotals(val owedToMe: Long, val iOwe: Long) {
    val isEmpty: Boolean get() = owedToMe == 0L && iOwe == 0L
}

fun debtTotals(balances: Map<Long, Long>): DebtTotals =
    DebtTotals(owedToMe = balances.values.filter { it > 0 }.sum(), iOwe = -balances.values.filter { it < 0 }.sum())

/** How a balance is settled up: who pays whom (as a record's kind) and how much - null when there is nothing to settle. */
fun settleUpFor(balance: Long): Pair<DebtKind, Long>? = when {
    balance > 0 -> DebtKind.RepaidToMe to balance
    balance < 0 -> DebtKind.RepaidByMe to -balance
    else -> null
}

/** A person's balance in words, with [name] theirs and [money] the formatted amount: "Ravi owes you ₹500", "You owe Ravi ₹200", "All settled". */
fun debtBalancePhrase(name: String, balance: Long, money: (Long) -> String): String = when {
    balance > 0 -> "$name owes you ${money(balance)}"
    balance < 0 -> "You owe $name ${money(-balance)}"
    else -> "All settled"
}

/** The longest note a record keeps. */
const val MAX_DEBT_NOTE = 200

/** What is wrong with a record, if anything, in the words a form says it ([advice]) and a refused backup does ([reason]). */
enum class DebtFault(val advice: String, val reason: String) {
    Amount("Enter an amount greater than zero.", "has an amount that is not above zero"),
    Note("Keep the note to $MAX_DEBT_NOTE characters.", "has a note longer than $MAX_DEBT_NOTE characters"),
}

fun DebtEntry.fault(): DebtFault? = when {
    amount <= 0 -> DebtFault.Amount
    (note?.length ?: 0) > MAX_DEBT_NOTE -> DebtFault.Note
    else -> null
}

/**
 * The category a record of [kind] is filed under when its money moved through an account, from the user's [categories]: the default
 * sub-category by name ("Lent to someone" under "Money sent"...), else that family, else none (the user picks one). Archived ones are
 * not offered.
 */
fun debtCategoryFor(kind: DebtKind, categories: List<Category>): Category? {
    val type = if (kind.moneyIn) CategoryType.Income else CategoryType.Expense
    val live = categories.filter { !it.archived && it.type == type }
    return live.firstOrNull { it.name.equals(kind.categoryName, ignoreCase = true) }
        ?: live.firstOrNull { it.name.equals(kind.familyName, ignoreCase = true) }
}
