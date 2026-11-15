package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/*
 * Savings goals (2026-11-15, round-2 recommendation 9), pure: what a goal is, how it counts what is saved, and what is worked out
 * from that - what is left, how far along it is, and what a month would have to be put by to reach it in time.
 *
 * A goal moves no money and changes no balance, total or budget: it is a target the user sets and a way of counting. Two ways of
 * counting what is saved, never both:
 *
 *  - **Linked to an account** ([SavingsGoal.accountId] set): the account's balance as of today is what is saved - there is no
 *    second set of books to keep in step. A balance below zero counts as nothing saved.
 *  - **On its own** ([SavingsGoal.accountId] null): a saved amount kept on the goal itself ([SavingsGoal.saved]), changed by the
 *    user with Add money and Take out. That makes no ledger entry: it does not move money between accounts.
 *
 * Everything is integer minor units (paise), as the rest of the ledger is.
 */

/** The longest a goal's name may be: a few words, short enough for a row beside its picture. */
const val MAX_GOAL_NAME = 40

/** The picture a goal has until the user chooses another. */
const val DEFAULT_GOAL_EMOJI = "🎯"

data class SavingsGoal(
    val id: Long? = null,
    val name: String,
    /** What the user wants to have saved, in minor units: above zero. */
    val target: Long,
    /** The day by which the user wants to have it, or null for no date. A date that has gone by is kept and said so ([GoalProgress.datePassed]). */
    val targetDate: LocalDate? = null,
    /** The goal's picture: exactly one emoji, or null for the default ([symbol]). */
    val emoji: String? = null,
    /** The account whose balance is what is saved; null when the goal counts what it keeps itself ([saved]). */
    val accountId: Long? = null,
    /**
     * What the goal keeps itself, in minor units: never below zero. Only a goal on its own has any - a linked goal keeps none (it is
     * stored as zero, so one whose account goes away falls back to "on its own" with nothing saved).
     */
    val saved: Long = 0,
    val sortOrder: Int = 0,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    /** Whether the goal counts an account's balance rather than an amount of its own. */
    val isLinked: Boolean get() = accountId != null

    /** The picture to draw: its emoji, or the default one. */
    val symbol: String get() = emoji ?: DEFAULT_GOAL_EMOJI
}

/** A goal's name as it is kept: no spaces at its ends and one space between words. */
fun cleanGoalName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

/** How long [name] is as it reads: an emoji is one, as a letter is. */
private fun lengthOf(name: String): Int = name.codePointCount(0, name.length)

/** What is wrong with a goal, in the words a form says it ([advice]) and a refused backup does ([reason], after the goal's name). */
enum class GoalFault(val advice: String, val reason: String) {
    Name("Give the goal a name.", "has no name"),
    NameLength("Keep the goal's name to $MAX_GOAL_NAME characters.", "has a name longer than $MAX_GOAL_NAME characters"),
    Target("Enter a target above zero.", "has a target that is not above zero"),
    Saved("What is saved can't be below zero.", "has a saved amount below zero"),
    Emoji("Choose a single emoji for the goal.", "has a picture that is not one emoji"),
}

/** The first thing wrong with this goal, or null when it holds: a name of 1 to [MAX_GOAL_NAME] characters, a target above zero, nothing saved below zero, one emoji or none. */
fun SavingsGoal.fault(): GoalFault? {
    val clean = cleanGoalName(name)
    return when {
        clean.isEmpty() -> GoalFault.Name
        lengthOf(clean) > MAX_GOAL_NAME -> GoalFault.NameLength
        target <= 0 -> GoalFault.Target
        saved < 0 -> GoalFault.Saved
        emoji != null && !isSingleEmoji(emoji) -> GoalFault.Emoji
        else -> null
    }
}

/**
 * The accounts a goal may count the balance of: any that is not archived, not a card (what is owed on it is not saved) and not a loan.
 * Cash, Bank, Wallet, Savings and Other all qualify.
 */
fun goalAccounts(accounts: List<Account>): List<Account> = accounts.filter { !it.archived && it.kind != AccountKind.Card && !it.isLoan }

/**
 * What [goal] has saved: the linked account's balance in [balances] (as of today - the balances an entry dated ahead is not in yet),
 * or nothing when that is below zero or the account is not there; for a goal on its own, what it keeps itself.
 */
fun goalSaved(goal: SavingsGoal, balances: Map<Long, Long>): Long {
    val account = goal.accountId ?: return goal.saved
    return maxOf(0L, balances[account] ?: 0L)
}

/**
 * Where a goal stands. [saved] is what it has (uncapped: a goal can be over its target); [left] is what is still wanted and never
 * below zero; [fraction] is how far along it is, 0 to 1; [reached] is [saved] at or past the target.
 *
 * With a target date still ahead and the goal not reached: [monthsLeft] counts the calendar months from this one to the date's (at
 * least 1, so a date this month is one) and [monthlyNeeded] is what it takes each of them - [left] over those months, rounded up to
 * the whole currency unit (the rupee), so putting it by every month always gets there. A goal that has [reached] its target, or has
 * no date, has neither; one whose date has gone by without reaching it is [datePassed].
 */
data class GoalProgress(
    val saved: Long,
    val target: Long,
    val left: Long,
    val fraction: Double,
    val reached: Boolean,
    val monthsLeft: Int? = null,
    val monthlyNeeded: Long? = null,
    val datePassed: Boolean = false,
)

/** The whole calendar months from [today]'s to [date]'s, and at least 1: a date this month is one month away, the next month's the same. */
fun monthsToGoalDate(today: LocalDate, date: LocalDate): Int =
    ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(date)).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

/** [amount] divided by [by] (both above zero) and rounded up to a multiple of [unit]: 3 into 1,000 paise is 334, to the rupee (unit 100) 400. */
private fun roundedUp(amount: Long, by: Int, unit: Long): Long {
    val share = -Math.floorDiv(-amount, by.toLong())
    return -Math.floorDiv(-share, unit) * unit
}

/**
 * [goal]'s progress with [saved] in hand (see [goalSaved]) as of [today]. [minorPerMajor] is the currency's minor units to one whole
 * unit - what [GoalProgress.monthlyNeeded] is rounded up to (100 for the rupee).
 */
fun goalProgress(goal: SavingsGoal, saved: Long, today: LocalDate, minorPerMajor: Long = CurrencySpec.INR.minorPerMajor): GoalProgress {
    val have = maxOf(0L, saved)
    val left = maxOf(0L, goal.target - have)
    val reached = have >= goal.target
    val date = goal.targetDate
    val passed = !reached && date != null && date.isBefore(today)
    val ahead = !reached && date != null && !passed
    val months = if (ahead) monthsToGoalDate(today, date!!) else null
    return GoalProgress(
        saved = have,
        target = goal.target,
        left = left,
        fraction = shareOf(have, goal.target),
        reached = reached,
        monthsLeft = months,
        monthlyNeeded = months?.let { roundedUp(left, it, minorPerMajor.coerceAtLeast(1L)) },
        datePassed = passed,
    )
}

/** [goalProgress] for a goal in the ledger: what it has saved is read from [balances] (a linked goal) or from the goal itself. */
fun goalProgress(goal: SavingsGoal, balances: Map<Long, Long>, today: LocalDate, minorPerMajor: Long = CurrencySpec.INR.minorPerMajor): GoalProgress =
    goalProgress(goal, goalSaved(goal, balances), today, minorPerMajor)

/**
 * What all the goals come to together: how many there are and how many are [reached], what they want in all ([target]) and what is
 * [saved] towards it - **each goal counted up to its own target at most**, so that [saved] and [left] always make [target] (a linked
 * account holding far more than its goal asks for does not make the goals look richer than they are).
 */
data class GoalsSummary(val count: Int, val reached: Int, val saved: Long, val target: Long) {
    /** What is still to go across all the goals. */
    val left: Long get() = target - saved
}

/** The goals' totals as of the balances in [balances]. */
fun goalsSummary(goals: List<SavingsGoal>, balances: Map<Long, Long>): GoalsSummary {
    var saved = 0L
    var target = 0L
    var reached = 0
    for (goal in goals) {
        val have = goalSaved(goal, balances)
        saved += minOf(have, goal.target)
        target += goal.target
        if (have >= goal.target) reached++
    }
    return GoalsSummary(count = goals.size, reached = reached, saved = saved, target = target)
}

/** The goals as the list shows them: those still being saved for first, the reached ones after, each group in the order the goals were kept. */
fun goalsInOrder(goals: List<SavingsGoal>, balances: Map<Long, Long>): List<SavingsGoal> =
    goals.sortedWith(compareBy<SavingsGoal> { goalSaved(it, balances) >= it.target }.thenBy { it.sortOrder }.thenBy { it.id ?: Long.MAX_VALUE })
