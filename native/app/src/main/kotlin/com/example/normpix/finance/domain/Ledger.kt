package com.example.normpix.finance.domain

import java.time.LocalDate

/*
 * All deterministic money math in one place. Everything here works on integer
 * minor units; nothing is stored - callers derive values from the
 * transactions each time, so edits and deletes can never leave a stale
 * balance or total behind.
 */

/** Money that moved through one account, by direction. */
data class AccountFlows(
    val income: Long = 0,
    val expense: Long = 0,
    val transferIn: Long = 0,
    val transferOut: Long = 0,
) {
    companion object {
        val Zero = AccountFlows()
    }
}

/** Opening balance plus everything that came in, minus everything that went out. */
fun accountBalance(openingBalance: Long, flows: AccountFlows): Long =
    openingBalance + flows.income - flows.expense + flows.transferIn - flows.transferOut

/** Income vs. spending over a period. Transfers are excluded by definition. */
data class PeriodSummary(
    val income: Long = 0,
    val expense: Long = 0,
    /** Number of income + expense transactions in the period. */
    val count: Int = 0,
) {
    val net: Long get() = income - expense

    companion object {
        val Empty = PeriodSummary()
    }
}

data class CategoryTotal(val categoryId: Long, val amount: Long, val count: Int)

/** A day's total, for trend charts. */
data class DailyTotal(val day: LocalDate, val amount: Long)

data class MonthTotals(val month: MonthKey, val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

/** Share of [total] that [part] represents, 0..1 (0 when [total] is 0). */
fun shareOf(part: Long, total: Long): Double =
    if (total <= 0) 0.0 else (part.toDouble() / total).coerceIn(0.0, 1.0)

/**
 * Relative change from [previous] to [current] (0.18 = 18% more), or null
 * when there's no baseline to compare against.
 */
fun relativeChange(current: Long, previous: Long): Double? {
    if (previous == 0L) return null
    return (current - previous).toDouble() / previous
}

/**
 * A credit card's room: what it is allowed ([limit]) against what is owed on it. [available] is negative once the card is
 * over its limit.
 */
data class CreditRoom(val limit: Long, val owed: Long, val available: Long) {
    val overLimit: Boolean get() = available < 0

    /** How much of the limit is used, 0..1 (more than 1 is clamped: the card cannot be more than full). */
    val used: Double get() = if (limit <= 0) 0.0 else (owed.toDouble() / limit).coerceIn(0.0, 1.0)
}

/**
 * Where a card with [limit] stands at [balance] (negative while money is owed on it): null when it has no limit. A card paid
 * past its bill has money with the bank, which adds to what can be spent.
 */
fun creditRoom(limit: Long?, balance: Long): CreditRoom? =
    limit?.let { CreditRoom(limit = it, owed = maxOf(0L, -balance), available = it + balance) }

/**
 * What is left once [amount] is paid toward a card that owes [owed] (both in minor units, [owed] never negative). Paying the
 * whole bill leaves nothing owed; paying more than the bill leaves the difference with the bank as [creditAfter] - the card is
 * then "in credit", and that money counts toward what can be spent on it next.
 */
data class BillPayment(val owedAfter: Long, val creditAfter: Long) {
    /** Nothing is owed on the card any more. */
    val settled: Boolean get() = owedAfter == 0L
}

fun billPayment(owed: Long, amount: Long): BillPayment =
    BillPayment(owedAfter = maxOf(0L, owed - amount), creditAfter = maxOf(0L, amount - owed))

/** Half of [owed], rounded down to a whole minor unit but never below one unit: the "pay half" choice for a bill. */
fun halfOf(owed: Long): Long = if (owed <= 1) owed else owed / 2

// ---------------------------------------------------------------------------
// Budgets
// ---------------------------------------------------------------------------

enum class BudgetHealth {
    /** Below [BudgetStatus.APPROACHING_PERCENT] of the limit. */
    Healthy,

    /** At or above the threshold but not over the limit. */
    Approaching,

    /** Spending is over the limit. */
    Exceeded,
}

data class BudgetStatus(val allocated: Long, val spent: Long) {
    val remaining: Long get() = allocated - spent

    /** Spent / allocated, unclamped (1.25 = 25% over). */
    val utilization: Double get() = if (allocated <= 0) 0.0 else spent.toDouble() / allocated

    val health: BudgetHealth
        get() = when {
            spent > allocated -> BudgetHealth.Exceeded
            // Integer comparison: spent / allocated >= 80%.
            spent * 100 >= allocated * APPROACHING_PERCENT -> BudgetHealth.Approaching
            else -> BudgetHealth.Healthy
        }

    companion object {
        /** 80% - the point where a budget is called out as "approaching". */
        const val APPROACHING_PERCENT = 80
    }
}

/**
 * Resolves which budget applies to each target (null key = overall) in
 * [month]: for every target, the row with the latest effective month that is
 * not after [month]. Targets whose latest row clears the budget (amount null)
 * are omitted.
 */
fun resolveBudgets(rows: Iterable<Budget>, month: MonthKey): Map<Long?, Long> {
    val latest = mutableMapOf<Long?, Budget>()
    for (row in rows) {
        if (row.effectiveMonth.isAfter(month)) continue
        val current = latest[row.categoryId]
        if (current == null || row.effectiveMonth.isAfter(current.effectiveMonth)) {
            latest[row.categoryId] = row
        }
    }
    return buildMap {
        for ((target, row) in latest) {
            row.amount?.let { put(target, it) }
        }
    }
}

/** The month's budget picture. */
data class BudgetOverview(
    val month: MonthKey,
    /** The overall monthly limit against all spending, if one is set. */
    val overall: BudgetStatus?,
    /** Per-category limits, keyed by category id. */
    val categories: Map<Long, BudgetStatus>,
    val totalExpense: Long,
    /**
     * Categories with a budget whose parent has one too: the parent's spending already counts theirs, so the [headline]
     * leaves them out rather than count the same money twice.
     */
    val nested: Set<Long> = emptySet(),
) {
    val hasAnyBudget: Boolean get() = overall != null || categories.isNotEmpty()

    /**
     * The single headline status: the overall budget when set, otherwise the
     * sum of the category budgets against spending in those categories.
     */
    val headline: BudgetStatus?
        get() {
            overall?.let { return it }
            if (categories.isEmpty()) return null
            var allocated = 0L
            var spent = 0L
            for ((id, status) in categories) {
                if (id in nested) continue
                allocated += status.allocated
                spent += status.spent
            }
            return BudgetStatus(allocated = allocated, spent = spent)
        }
}

/** Builds a [BudgetOverview] from resolved limits and the month's spending. */
fun buildBudgetOverview(
    month: MonthKey,
    limits: Map<Long?, Long>,
    spentByCategory: Map<Long, Long>,
    totalExpense: Long,
    /** Child id to parent id; see [BudgetOverview.nested]. [spentByCategory] already counts a sub-category in its parent (see [foldIntoParents]). */
    parents: Map<Long, Long> = emptyMap(),
): BudgetOverview {
    val overallLimit = limits[null]
    return BudgetOverview(
        month = month,
        totalExpense = totalExpense,
        nested = limits.keys.filterNotNullTo(mutableSetOf()).filter { id -> parents[id]?.let { it in limits } == true }.toSet(),
        overall = overallLimit?.let { BudgetStatus(allocated = it, spent = totalExpense) },
        categories = buildMap {
            for ((target, limit) in limits) {
                if (target != null) put(target, BudgetStatus(allocated = limit, spent = spentByCategory[target] ?: 0))
            }
        },
    )
}
