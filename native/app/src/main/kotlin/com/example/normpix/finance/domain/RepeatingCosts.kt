package com.example.normpix.finance.domain

/*
 * What the payments that repeat cost (2026-11-14): every repeating expense as a cost per month and per year, so a subscription that
 * is "only 199" reads as what it adds up to. Pure and exact: integer minor units, no floating point.
 *
 * Only an expense is a cost. A transfer (a credit card's bill paid from the bank, a SIP into savings) moves money and is not spent
 * (the money rules), and an income is not a cost, so neither is counted - the same line the Recurring page's "Next 30 days"
 * draws. A payment that is paused is listed, so it can be resumed, but costs nothing while it is; one that has ended is gone.
 */

/** How many times a year a payment on [frequency] happens when it repeats every one [frequency] (every 2 months = half of 12). */
private fun timesAYear(frequency: RecurrenceFrequency): Long = when (frequency) {
    RecurrenceFrequency.Daily -> 365L
    RecurrenceFrequency.Weekly -> 52L
    RecurrenceFrequency.Monthly -> 12L
    RecurrenceFrequency.Yearly -> 1L
}

/** [numerator] over [denominator] rounded to the nearest whole unit, a half going up; both positive. */
private fun roundedDivide(numerator: Long, denominator: Long): Long = (2 * numerator + denominator) / (2 * denominator)

/**
 * What a payment of [amount] repeating every [interval] [frequency]s costs in a year: daily x 365, weekly x 52, monthly x 12,
 * yearly x 1, each divided by the interval, rounded to the nearest minor unit (a half up).
 */
fun costPerYear(amount: Long, frequency: RecurrenceFrequency, interval: Int): Long =
    roundedDivide(amount * timesAYear(frequency), interval.coerceAtLeast(1).toLong())

/** A year's cost spread over twelve months, rounded to the nearest minor unit (a half up). */
fun costPerMonth(perYear: Long): Long = roundedDivide(perYear, 12L)

/** One repeating expense and what it costs. */
data class RepeatingCost(val rule: RecurringRule, val perYear: Long, val perMonth: Long)

/**
 * The repeating expenses, costliest first. [active] are the ones being recorded and make up the totals; [paused] are listed after
 * them (so they can be resumed) and cost nothing while they are. [perYear] is the sum of the active ones' years and [perMonth] that
 * year over twelve - not the sum of each month, so a month times twelve is the year to the nearest minor unit.
 */
data class RepeatingCosts(
    val active: List<RepeatingCost>,
    val paused: List<RepeatingCost>,
    val perMonth: Long,
    val perYear: Long,
)

/** What each of [rules] costs, and the totals. Income and transfers are left out, and so are rules that have ended. */
fun repeatingCosts(rules: List<RecurringRule>): RepeatingCosts {
    val costs = rules
        .filter { it.type == TransactionType.Expense && !it.isFinished }
        .map { rule ->
            val year = costPerYear(rule.amount, rule.frequency, rule.interval)
            RepeatingCost(rule, perYear = year, perMonth = costPerMonth(year))
        }
        .sortedWith(compareByDescending<RepeatingCost> { it.perYear }.thenBy { it.rule.id ?: Long.MAX_VALUE })
    val active = costs.filter { it.rule.active }
    val year = active.sumOf { it.perYear }
    return RepeatingCosts(
        active = active,
        paused = costs.filter { !it.rule.active },
        perMonth = costPerMonth(year),
        perYear = year,
    )
}
