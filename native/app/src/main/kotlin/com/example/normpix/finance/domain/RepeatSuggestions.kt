package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/*
 * Payments that look like they repeat (2026-11-14): nothing in the ledger says a payee is paid every month unless the user made a
 * repeating payment for it, so the expenses that were typed in one by one are read for a rhythm - the same payee, about the same
 * amount, a regular gap - and offered as a repeating payment to add. A suggestion only offers: nothing is saved until the user adds it.
 *
 * Pure: it takes the past expenses that no repeating payment recorded, the repeating payments there are, the payees the user said
 * "not this one" to, and today.
 */

/** A payee that looks like a repeating payment, and what its repeating payment would be. */
data class RepeatSuggestion(
    /** The payee as [payeeKey] reads it: what "not this one" remembers. */
    val key: String,
    /** The payee as it was written on the newest payment. */
    val payee: String,
    /** What the newest payment was. */
    val amount: Long,
    val frequency: RecurrenceFrequency,
    /** The account and category of the newest payment. */
    val accountId: Long,
    val categoryId: Long?,
    /** How many payments make the pattern. */
    val seen: Int,
    /** The day of the newest payment. */
    val lastDate: LocalDate,
    /** The newest payment's day and one period on: when the next one is expected. */
    val nextDate: LocalDate,
) {
    /** What it would cost in a year, to put the dearest first. */
    val perYear: Long get() = costPerYear(amount, frequency, 1)
}

/** [payee] as the detector compares payees: trimmed, lower case, runs of spaces made one; null for none. */
fun payeeKey(payee: String?): String? =
    payee?.trim()?.lowercase()?.replace(WHITESPACE, " ")?.takeIf { it.isNotEmpty() }

private val WHITESPACE = Regex("\\s+")

/** What makes a pattern (see [suggestRepeats]). */
object RepeatDetection {
    /** Payments a weekly or monthly pattern needs. */
    const val MIN_PAYMENTS = 3

    /**
     * Payments a yearly pattern needs. The brief's three would take two years of history to show and a 13-month window can hold
     * two at most, so a yearly payment would never be offered; two a year apart, the newest not yet a year and a half old, is the
     * least that is a rhythm at all.
     */
    const val MIN_YEARLY_PAYMENTS = 2

    /** How far back a weekly or monthly pattern is looked for. */
    const val LOOKBACK_MONTHS = 13L

    /** How far back a yearly one is: two payments a year apart, the newer up to a year and a half old. */
    const val YEARLY_LOOKBACK_MONTHS = 36L

    /** How far from the median the amounts of a pattern may be, in percent. */
    const val AMOUNT_BAND_PERCENT = 20L

    /** The most suggestions shown at once. */
    const val MOST_SHOWN = 3

    /** The gaps in days that count as each frequency. */
    val WEEKLY_GAP = 6L..8L
    val MONTHLY_GAP = 26L..35L
    val YEARLY_GAP = 350L..380L

    /** The length of one period in days, for "the newest payment is no older than one and a half periods". */
    fun periodDays(frequency: RecurrenceFrequency): Long = when (frequency) {
        RecurrenceFrequency.Weekly -> 7L
        RecurrenceFrequency.Monthly -> 30L
        RecurrenceFrequency.Yearly -> 365L
        RecurrenceFrequency.Daily -> 1L
    }

    /** Which frequency a gap of [days] is, or null for one that is none of them. */
    fun frequencyOfGap(days: Long): RecurrenceFrequency? = when (days) {
        in WEEKLY_GAP -> RecurrenceFrequency.Weekly
        in MONTHLY_GAP -> RecurrenceFrequency.Monthly
        in YEARLY_GAP -> RecurrenceFrequency.Yearly
        else -> null
    }
}

/** Whether every amount is within [RepeatDetection.AMOUNT_BAND_PERCENT] of the median. Integer sums only: twice the median is whole. */
private fun amountsAgree(amounts: List<Long>): Boolean {
    val sorted = amounts.sorted()
    val mid = sorted.size / 2
    val twiceMedian = if (sorted.size % 2 == 1) 2 * sorted[mid] else sorted[mid - 1] + sorted[mid]
    // |a - median| <= band% of median, with everything doubled so a half stays whole.
    return sorted.all { Math.abs(2 * it - twiceMedian) * 100 <= twiceMedian * RepeatDetection.AMOUNT_BAND_PERCENT }
}

/**
 * The payees in [transactions] that look like a repeating payment, the dearest a year first, at most [RepeatDetection.MOST_SHOWN].
 *
 * Only expenses that no repeating payment recorded count, and only those with a payee (the payee is what says it is the same
 * payment). Payments are grouped by [payeeKey]; a group qualifies when
 * - the gaps between its newest payments fit one frequency (weekly 6-8 days, monthly 26-35, yearly 350-380), going back from the
 *   newest, for at least [RepeatDetection.MIN_PAYMENTS] payments in the last 13 months - the older ones that no longer fit are left
 *   out, so a price change or a missed month long ago does not hide a pattern that is plain now;
 * - those payments' amounts are all within 20% of their median (the run is cut back from its oldest end until they are);
 * - the newest payment is no more than one and a half periods ago, so a payment that stopped is not suggested;
 * - no active repeating payment in [rules] is for that payee, and the user has not said "not this one" ([dismissed], [payeeKey]s).
 *
 * The suggestion carries the newest payment's amount, account and category, and the day one period after it.
 */
fun suggestRepeats(
    transactions: List<FinanceTransaction>,
    rules: List<RecurringRule>,
    dismissed: Set<String>,
    today: LocalDate,
): List<RepeatSuggestion> {
    val ruled = rules.filter { it.active && !it.isFinished }.mapNotNull { payeeKey(it.payee) }.toSet()
    val groups = transactions
        .filter { it.type == TransactionType.Expense && it.recurringId == null && !it.occurredAt.toLocalDate().isAfter(today) }
        .groupBy { payeeKey(it.payee) }
    val found = mutableListOf<RepeatSuggestion>()
    for ((key, group) in groups) {
        if (key == null || key in dismissed || key in ruled) continue
        suggestionFor(key, group.sortedWith(compareBy({ it.occurredAt }, { it.id ?: 0L })), today)?.let { found += it }
    }
    return found
        .sortedWith(compareByDescending<RepeatSuggestion> { it.perYear }.thenBy { it.key })
        .take(RepeatDetection.MOST_SHOWN)
}

/** The suggestion for one payee's [payments], oldest first, or null when they are no rhythm. */
private fun suggestionFor(key: String, payments: List<FinanceTransaction>, today: LocalDate): RepeatSuggestion? {
    if (payments.size < 2) return null
    val newest = payments.last()
    val newestDay = newest.occurredAt.toLocalDate()
    // The newest gap says which frequency this would be: the three do not overlap.
    val frequency = RepeatDetection.frequencyOfGap(ChronoUnit.DAYS.between(payments[payments.size - 2].occurredAt.toLocalDate(), newestDay))
        ?: return null
    if (ChronoUnit.DAYS.between(newestDay, today) > RepeatDetection.periodDays(frequency) * 3 / 2) return null

    val yearly = frequency == RecurrenceFrequency.Yearly
    val since = today.minusMonths(if (yearly) RepeatDetection.YEARLY_LOOKBACK_MONTHS else RepeatDetection.LOOKBACK_MONTHS)
    val recent = payments.filter { !it.occurredAt.toLocalDate().isBefore(since) }
    // Back from the newest while the gaps keep fitting.
    var run = 1
    while (run < recent.size) {
        val later = recent[recent.size - run].occurredAt.toLocalDate()
        val earlier = recent[recent.size - run - 1].occurredAt.toLocalDate()
        if (RepeatDetection.frequencyOfGap(ChronoUnit.DAYS.between(earlier, later)) != frequency) break
        run++
    }
    val least = if (yearly) RepeatDetection.MIN_YEARLY_PAYMENTS else RepeatDetection.MIN_PAYMENTS
    // The longest stretch ending now whose amounts agree.
    var seen = run
    while (seen >= least && !amountsAgree(recent.takeLast(seen).map { it.amount })) seen--
    if (seen < least) return null

    val next = when (frequency) {
        RecurrenceFrequency.Weekly -> newestDay.plusWeeks(1)
        RecurrenceFrequency.Monthly -> newestDay.plusMonths(1)
        else -> newestDay.plusYears(1)
    }
    return RepeatSuggestion(
        key = key,
        payee = newest.payee?.trim().orEmpty(),
        amount = newest.amount,
        frequency = frequency,
        accountId = newest.accountId,
        categoryId = newest.categoryId,
        seen = seen,
        lastDate = newestDay,
        nextDate = next,
    )
}

/**
 * The day a repeating payment made from [suggestion] should start on: the day it is expected ([RepeatSuggestion.nextDate]), or - when
 * that has already gone by, as it has for a payment a little late - the first one after it that is not before [today]. A rule
 * starting in the past would record payments that were already made, which are in the ledger as they are.
 */
fun RepeatSuggestion.startOnOrAfter(today: LocalDate): LocalDate {
    var day = nextDate
    var step = 1L
    while (day.isBefore(today) && step < 1_000) {
        day = when (frequency) {
            RecurrenceFrequency.Weekly -> lastDate.plusWeeks(1 + step)
            RecurrenceFrequency.Monthly -> lastDate.plusMonths(1 + step)
            else -> lastDate.plusYears(1 + step)
        }
        step++
    }
    return day
}
