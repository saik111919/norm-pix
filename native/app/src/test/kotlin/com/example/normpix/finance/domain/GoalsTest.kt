package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Savings goals, pure (2026-11-15): what a goal has saved - a linked account's balance as of today, or what it keeps itself - what is left,
 * how far along it is, whether it is reached, and what a month must put by to get there by the date: whole calendar months, at least one,
 * rounded up to the rupee; and what the goals come to together.
 */
class GoalsTest {
    private val now = LocalDateTime.of(2026, 11, 15, 9, 0)
    private val today = LocalDate.of(2026, 11, 15)

    private fun rupees(amount: Long) = amount * 100

    private fun goal(
        target: Long = rupees(40_000),
        date: LocalDate? = null,
        account: Long? = null,
        saved: Long = 0,
        name: String = "Trip",
        id: Long? = 1,
        order: Int = 0,
    ) = SavingsGoal(id = id, name = name, target = target, targetDate = date, accountId = account, saved = saved, sortOrder = order, createdAt = now, updatedAt = now)

    private fun account(id: Long, kind: AccountKind = AccountKind.Savings, archived: Boolean = false, loan: LoanTerms? = null) =
        Account(id = id, name = "Account $id", kind = kind, archived = archived, createdAt = now, updatedAt = now, loan = loan)

    // ------------------------------------------------------------------------------------------------------------ what is saved

    @Test
    fun `a goal on its own has what it keeps, and a linked goal has its account's balance as of today`() {
        assertEquals(rupees(18_000), goalSaved(goal(saved = rupees(18_000)), mapOf(7L to rupees(5))))
        // Linked: the account's balance, whatever the goal itself carries.
        assertEquals(rupees(25_000), goalSaved(goal(account = 7, saved = rupees(18_000)), mapOf(7L to rupees(25_000))))
        // Below zero (an overdrawn account) or an account that is not there is nothing saved - never a negative amount.
        assertEquals(0L, goalSaved(goal(account = 7), mapOf(7L to -rupees(300))))
        assertEquals(0L, goalSaved(goal(account = 7), emptyMap()))
    }

    @Test
    fun `the balances a linked goal reads are the ones as of today, so an entry dated ahead is not counted`() {
        // The ledger's own rule (balances are worked out as of the end of today): an upcoming entry is not in the balance.
        val opening = rupees(10_000)
        val upToday = accountBalance(opening, AccountFlows(income = rupees(5_000)))
        val withUpcoming = accountBalance(opening, AccountFlows(income = rupees(5_000) + rupees(9_000)))
        val g = goal(account = 7)
        assertEquals(rupees(15_000), goalSaved(g, mapOf(7L to upToday)))
        assertEquals(rupees(24_000), goalSaved(g, mapOf(7L to withUpcoming)), "what is in the balances is what counts")
    }

    // ---------------------------------------------------------------------------------------------------------------- progress

    @Test
    fun `saved, left, the fraction and reached`() {
        val p = goalProgress(goal(target = rupees(40_000)), rupees(18_000), today)
        assertEquals(rupees(18_000), p.saved)
        assertEquals(rupees(22_000), p.left)
        assertEquals(0.45, p.fraction, 1e-9)
        assertFalse(p.reached)
        assertNull(p.monthsLeft)
        assertNull(p.monthlyNeeded)
        assertFalse(p.datePassed)

        val exactly = goalProgress(goal(target = rupees(40_000)), rupees(40_000), today)
        assertTrue(exactly.reached)
        assertEquals(0L, exactly.left)
        assertEquals(1.0, exactly.fraction, 1e-9)

        // Over the target: left never goes below zero and the bar never goes past full, but saved says what there is.
        val over = goalProgress(goal(target = rupees(40_000)), rupees(55_000), today)
        assertTrue(over.reached)
        assertEquals(0L, over.left)
        assertEquals(1.0, over.fraction, 1e-9)
        assertEquals(rupees(55_000), over.saved)

        val none = goalProgress(goal(), 0, today)
        assertEquals(0.0, none.fraction, 1e-9)
        assertEquals(rupees(40_000), none.left)
        assertFalse(none.reached)
    }

    @Test
    fun `progress reads a linked goal's saved from the balances and a goal on its own from itself`() {
        val linked = goal(account = 3, target = rupees(1_00_000))
        assertEquals(rupees(25_000), goalProgress(linked, mapOf(3L to rupees(25_000)), today).saved)
        val own = goal(saved = rupees(18_000))
        assertEquals(rupees(18_000), goalProgress(own, mapOf(3L to rupees(25_000)), today).saved)
        // A balance below zero is no negative progress.
        val negative = goalProgress(linked, mapOf(3L to -rupees(50)), today)
        assertEquals(0L, negative.saved)
        assertEquals(0.0, negative.fraction, 1e-9)
    }

    // ------------------------------------------------------------------------------------------------------ the month needed

    @Test
    fun `a month needed is what is left over the whole calendar months to the date, rounded up to the rupee`() {
        // 15 Nov 2026 to 15 Mar 2027: Nov -> Mar is 4 calendar months; Rs 1,00,000 left over 4 is Rs 25,000 a month.
        val p = goalProgress(goal(target = rupees(1_00_000), date = LocalDate.of(2027, 3, 15)), 0, today)
        assertEquals(4, p.monthsLeft)
        assertEquals(rupees(25_000), p.monthlyNeeded)
        assertFalse(p.datePassed)
        assertFalse(p.reached)

        // Rs 1,000 over 3 months is Rs 333.33...: rounded up to the rupee, Rs 334.
        val third = goalProgress(goal(target = rupees(1_000), date = LocalDate.of(2027, 2, 20)), 0, today)
        assertEquals(3, third.monthsLeft)
        assertEquals(rupees(334), third.monthlyNeeded)
        // Putting that by every month always gets there: 3 x Rs 334 is at least Rs 1,000.
        assertTrue(third.monthlyNeeded!! * third.monthsLeft!! >= third.left)

        // Whatever is saved comes off first: Rs 40,000 target, Rs 18,000 saved, Rs 22,000 over 5 months (to April) is Rs 4,400.
        val some = goalProgress(goal(target = rupees(40_000), date = LocalDate.of(2027, 4, 15)), rupees(18_000), today)
        assertEquals(5, some.monthsLeft)
        assertEquals(rupees(4_400), some.monthlyNeeded)
    }

    @Test
    fun `a paisa left is rounded up to a whole rupee`() {
        val p = goalProgress(goal(target = rupees(1_000) + 1, date = LocalDate.of(2026, 12, 1)), rupees(1_000), today)
        assertEquals(1, p.monthsLeft)
        assertEquals(rupees(1), p.monthlyNeeded, "1 paisa left is a rupee a month")
    }

    @Test
    fun `months left is at least one - a date this month, or tomorrow, is one month away`() {
        assertEquals(1, monthsToGoalDate(today, LocalDate.of(2026, 11, 30)))
        assertEquals(1, monthsToGoalDate(today, LocalDate.of(2026, 11, 16)))
        assertEquals(1, monthsToGoalDate(today, today), "today itself")
        assertEquals(1, monthsToGoalDate(today, LocalDate.of(2026, 12, 1)), "next month")
        assertEquals(2, monthsToGoalDate(today, LocalDate.of(2027, 1, 1)))
        assertEquals(12, monthsToGoalDate(today, LocalDate.of(2027, 11, 15)))
        assertEquals(13, monthsToGoalDate(today, LocalDate.of(2027, 12, 31)))

        val thisMonth = goalProgress(goal(target = rupees(9_000), date = LocalDate.of(2026, 11, 30)), rupees(3_000), today)
        assertEquals(1, thisMonth.monthsLeft)
        assertEquals(rupees(6_000), thisMonth.monthlyNeeded, "all of what is left, this month")
    }

    @Test
    fun `a date that has passed says so, and a reached goal or one with no date has no month needed`() {
        val passed = goalProgress(goal(date = LocalDate.of(2026, 11, 14)), rupees(1_000), today)
        assertTrue(passed.datePassed)
        assertNull(passed.monthsLeft)
        assertNull(passed.monthlyNeeded)
        assertFalse(passed.reached)

        // Today is not yet passed.
        assertFalse(goalProgress(goal(date = today), 0, today).datePassed)

        // Reached by then, or reached after the date: it is reached, and nothing more is needed.
        val reached = goalProgress(goal(target = rupees(1_000), date = LocalDate.of(2026, 1, 1)), rupees(1_000), today)
        assertTrue(reached.reached)
        assertFalse(reached.datePassed, "a goal that was reached is not 'late'")
        assertNull(reached.monthlyNeeded)

        val undated = goalProgress(goal(), rupees(1_000), today)
        assertNull(undated.monthsLeft)
        assertNull(undated.monthlyNeeded)
        assertFalse(undated.datePassed)
    }

    @Test
    fun `a currency with no minor units rounds to the unit it has`() {
        // Yen: one unit is one minor unit, so a share is only rounded up to a whole one.
        val p = goalProgress(goal(target = 1_000, date = LocalDate.of(2027, 2, 20)), 0, today, minorPerMajor = 1)
        assertEquals(3, p.monthsLeft)
        assertEquals(334L, p.monthlyNeeded)
    }

    // ---------------------------------------------------------------------------------------------------------------- the rules

    @Test
    fun `a goal needs a name of 1 to 40 characters, a target above zero, nothing saved below zero and one emoji or none`() {
        assertNull(goal().fault())
        assertEquals(GoalFault.Name, goal(name = "   ").fault())
        assertNull(goal(name = "x".repeat(MAX_GOAL_NAME)).fault())
        assertEquals(GoalFault.NameLength, goal(name = "x".repeat(MAX_GOAL_NAME + 1)).fault())
        // Spaces round it are not part of it, and an emoji is one character.
        assertNull(goal(name = "  " + "x".repeat(MAX_GOAL_NAME - 1) + "🌴  ").fault())
        assertEquals(GoalFault.Target, goal(target = 0).fault())
        assertEquals(GoalFault.Target, goal(target = -5).fault())
        assertEquals(GoalFault.Saved, goal(saved = -1).fault())
        assertEquals(GoalFault.Emoji, goal().copy(emoji = "ab").fault())
        assertNull(goal().copy(emoji = "🌴").fault())
        assertEquals("Trip to Goa", cleanGoalName("  Trip   to  Goa "))
        assertEquals(DEFAULT_GOAL_EMOJI, goal().symbol)
        assertEquals("🌴", goal().copy(emoji = "🌴").symbol)
    }

    @Test
    fun `a goal can count a cash, bank, wallet, savings or other account, not a card, a loan, or an archived one`() {
        val loanTerms = LoanTerms(rupees(1_00_000), 900, 12, rupees(9_000), today.plusMonths(1))
        val accounts = listOf(
            account(1, AccountKind.Cash),
            account(2, AccountKind.Bank),
            account(3, AccountKind.Wallet),
            account(4, AccountKind.Savings),
            account(5, AccountKind.Other),
            account(6, AccountKind.Card),
            account(7, AccountKind.Loan, loan = loanTerms),
            account(8, AccountKind.Bank, loan = loanTerms),
            account(9, AccountKind.Savings, archived = true),
        )
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), goalAccounts(accounts).map { it.id })
    }

    // ---------------------------------------------------------------------------------------------------------------- the totals

    @Test
    fun `the summary counts each goal up to its own target, so saved and left make what the goals want`() {
        val goals = listOf(
            goal(id = 1, target = rupees(40_000), saved = rupees(18_000)),
            // A linked account holding far more than its goal asks for does not make the goals look richer than they are.
            goal(id = 2, target = rupees(1_00_000), account = 9),
            goal(id = 3, target = rupees(10_000), saved = rupees(10_000)),
        )
        val s = goalsSummary(goals, mapOf(9L to rupees(10_00_000)))
        assertEquals(3, s.count)
        assertEquals(2, s.reached)
        assertEquals(rupees(18_000) + rupees(1_00_000) + rupees(10_000), s.saved)
        assertEquals(rupees(40_000) + rupees(1_00_000) + rupees(10_000), s.target)
        assertEquals(rupees(22_000), s.left)
        assertEquals(s.target, s.saved + s.left)

        val empty = goalsSummary(emptyList(), emptyMap())
        assertEquals(GoalsSummary(0, 0, 0, 0), empty)
    }

    @Test
    fun `the list puts the goals still being saved for first, then the reached ones, each in the order the goals were kept`() {
        val goals = listOf(
            goal(id = 1, name = "A", target = rupees(100), saved = rupees(100), order = 0),
            goal(id = 2, name = "B", target = rupees(100), saved = rupees(10), order = 2),
            goal(id = 3, name = "C", target = rupees(100), saved = rupees(50), order = 1),
            goal(id = 4, name = "D", target = rupees(100), account = 8, order = 3),
        )
        // D is reached through its account's balance, so it goes with A, after the two still being saved for.
        assertEquals(listOf("C", "B", "A", "D"), goalsInOrder(goals, mapOf(8L to rupees(150))).map { it.name })
        // With that account empty, D is still being saved for and takes its place by its order.
        assertEquals(listOf("C", "B", "D", "A"), goalsInOrder(goals, emptyMap()).map { it.name })
    }
}
