package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.LoanTerms
import com.example.normpix.finance.domain.SavingsGoal
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.goalProgress
import com.example.normpix.finance.domain.goalSaved
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Savings goals in the ledger (2026-11-15): a goal is validated and kept (a name of 1 to 40 characters, a target above zero, one emoji or
 * none, an account a goal can count); a linked goal keeps no amount of its own; Add money and Take out change what a goal on its own has
 * saved - never below zero, never on a linked goal, making no ledger entry; deleting a goal changes no account; deleting - or archiving -
 * the account a goal counts leaves the goal as the app says it does; and a snapshot, a restore and an undo carry the goals.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalRepositoryTest {
    private val now = LocalDateTime.of(2026, 11, 15, 9, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private fun rupees(amount: Long) = amount * 100

    private fun draft(
        name: String = "Trip to Goa",
        target: Long = rupees(40_000),
        saved: Long = 0,
        account: Long? = null,
        date: LocalDate? = null,
        emoji: String? = null,
    ) = SavingsGoal(name = name, target = target, targetDate = date, emoji = emoji, accountId = account, saved = saved, createdAt = now, updatedAt = now)

    private suspend fun account(name: String = "SBI Savings", kind: AccountKind = AccountKind.Savings, opening: Long = rupees(45_000), loan: LoanTerms? = null): Account =
        repo.saveAccount(Account(name = name, kind = kind, openingBalance = opening, createdAt = now, updatedAt = now, loan = loan))

    private suspend fun refused(goal: SavingsGoal): String = assertFailsWith<FinanceValidationException> { repo.saveGoal(goal) }.message

    // ----------------------------------------------------------------------------------------------- saving a goal

    @Test
    fun `a goal is kept as it is given, with its place and when it was made`() = runDb {
        val savings = account()
        val own = repo.saveGoal(draft(name = "  Trip   to Goa ", saved = rupees(18_000), date = LocalDate.of(2027, 3, 15), emoji = "🌴"))
        val linked = repo.saveGoal(draft(name = "Emergency fund", target = rupees(3_00_000), account = savings.id, emoji = null))

        assertEquals("Trip to Goa", own.name, "the name is tidied")
        assertEquals(0, own.sortOrder)
        assertEquals(1, linked.sortOrder, "a new goal goes last")
        assertEquals(listOf(own, linked), repo.goals())
        val read = repo.goals().first()
        assertEquals(LocalDate.of(2027, 3, 15), read.targetDate)
        assertEquals("🌴", read.emoji)
        assertEquals(rupees(18_000), read.saved)
        assertEquals(now, read.createdAt)
        assertNull(repo.goals().last().emoji)
        assertEquals("🎯", repo.goals().last().symbol, "no emoji shows the default")
        assertEquals(savings.id, repo.goals().last().accountId)
    }

    @Test
    fun `an edited goal keeps its place and when it was made, and a blank emoji is none`() = runDb {
        val first = repo.saveGoal(draft(name = "A"))
        repo.saveGoal(draft(name = "B"))
        val later = LocalDateTime.of(2026, 12, 1, 8, 0)
        // The goal as the form holds it, with a place and a date of making that are not the stored ones: what is stored wins.
        val edited = repo.saveGoal(first.copy(name = "A renamed", target = rupees(50_000), emoji = "  ", createdAt = later, sortOrder = 9))
        assertEquals("A renamed", edited.name)
        assertEquals(rupees(50_000), edited.target)
        assertNull(edited.emoji)
        assertEquals(0, edited.sortOrder)
        assertEquals(now, edited.createdAt)
        assertEquals(listOf("A renamed", "B"), repo.goals().map { it.name })
    }

    @Test
    fun `a goal that doesn't hold is refused with what is wrong, and nothing is saved`() = runDb {
        assertEquals("Give the goal a name.", refused(draft(name = "   ")))
        assertEquals("Keep the goal's name to 40 characters.", refused(draft(name = "x".repeat(41))))
        assertEquals("Enter a target above zero.", refused(draft(target = 0)))
        assertEquals("Enter a target above zero.", refused(draft(target = -100)))
        assertEquals("What is saved can't be below zero.", refused(draft(saved = -1)))
        assertEquals("Choose a single emoji for the goal.", refused(draft(emoji = "ab")))
        assertEquals("Choose an account from the list.", refused(draft(account = 999)))
        assertTrue(repo.goals().isEmpty())
        // Forty characters is a name, spaces round it aside.
        repo.saveGoal(draft(name = "  " + "x".repeat(40) + "  "))
        assertEquals(1, repo.goals().size)
    }

    @Test
    fun `a goal counts a cash, bank, wallet, savings or other account - not a card, a loan or an archived account`() = runDb {
        for (kind in listOf(AccountKind.Cash, AccountKind.Bank, AccountKind.Wallet, AccountKind.Savings, AccountKind.Other)) {
            repo.saveGoal(draft(name = kind.key, account = account(name = "A ${kind.key}", kind = kind).id))
        }
        assertEquals(5, repo.goals().size)

        val card = account(name = "Visa", kind = AccountKind.Card, opening = -rupees(1_000))
        assertEquals("A goal can count a cash, bank, wallet, savings or other account - not a card or a loan.", refused(draft(account = card.id)))
        val terms = LoanTerms(rupees(1_00_000), 900, 12, rupees(9_000), LocalDate.of(2026, 12, 5))
        val loan = account(name = "Loan", kind = AccountKind.Loan, opening = -rupees(1_00_000), loan = terms)
        assertEquals("A goal can count a cash, bank, wallet, savings or other account - not a card or a loan.", refused(draft(account = loan.id)))
        val aBankLoan = account(name = "Car loan", kind = AccountKind.Bank, opening = -rupees(50_000), loan = terms)
        assertEquals("A goal can count a cash, bank, wallet, savings or other account - not a card or a loan.", refused(draft(account = aBankLoan.id)))
        val archived = account(name = "Old")
        repo.setAccountArchived(archived.id!!, true)
        assertEquals("That account is archived. Choose another one, or restore it first.", refused(draft(account = archived.id)))
        assertEquals(5, repo.goals().size)
    }

    @Test
    fun `a linked goal keeps no amount of its own`() = runDb {
        val savings = account()
        val goal = repo.saveGoal(draft(account = savings.id, saved = rupees(9_999)))
        assertEquals(0L, goal.saved)
        assertEquals(0L, repo.goals().single().saved)
        // Moving a goal from its own amount to an account, and back: what it had is not kept while it counts the account.
        val own = repo.saveGoal(draft(name = "Own", saved = rupees(500)))
        val linked = repo.saveGoal(own.copy(accountId = savings.id))
        assertEquals(0L, linked.saved)
        val back = repo.saveGoal(linked.copy(accountId = null, saved = rupees(700)))
        assertNull(back.accountId)
        assertEquals(rupees(700), back.saved)
    }

    @Test
    fun `a goal that counts an account keeps counting it when the account is archived or has become a card`() = runDb {
        val savings = account()
        val goal = repo.saveGoal(draft(account = savings.id))
        repo.setAccountArchived(savings.id!!, true)
        assertEquals(savings.id, repo.goals().single().accountId, "archiving the account leaves the goal on it")
        // It can still be renamed (the link is unchanged, so it is not asked about again).
        repo.saveGoal(goal.copy(name = "Renamed"))
        assertEquals("Renamed", repo.goals().single().name)
        // And it still counts the account's balance (it is held on to: the money is there).
        assertEquals(rupees(45_000), goalSaved(repo.goals().single(), repo.accountBalances()))

        // The account turned into a card since: the goal counts nothing (a card's balance is what is owed), and can still be edited.
        val other = account(name = "Wallet one", kind = AccountKind.Wallet, opening = rupees(100))
        val second = repo.saveGoal(draft(name = "Second", account = other.id))
        repo.saveAccount(other.copy(kind = AccountKind.Card, openingBalance = -rupees(100)))
        assertEquals(0L, goalSaved(repo.goals().first { it.id == second.id }, repo.accountBalances()))
        repo.saveGoal(second.copy(name = "Second renamed"))
        assertTrue(repo.goals().any { it.name == "Second renamed" })
    }

    @Test
    fun `a goal that has gone can't be edited, and deleting it twice says so`() = runDb {
        val goal = repo.saveGoal(draft())
        repo.deleteGoal(goal.id!!)
        assertEquals("This goal no longer exists.", assertFailsWith<FinanceConflictException> { repo.saveGoal(goal.copy(name = "Late")) }.message)
        assertEquals("This goal no longer exists.", assertFailsWith<FinanceConflictException> { repo.deleteGoal(goal.id!!) }.message)
        assertTrue(repo.goals().isEmpty())
    }

    // ------------------------------------------------------------------------------------- the account's balance as of today

    @Test
    fun `a linked goal counts the account's balance as of today, so an entry dated ahead is not in it`() = runDb {
        val savings = account(opening = rupees(10_000))
        val salary = repo.categories().first { it.name == "Salary" }
        suspend fun income(amount: Long, at: LocalDateTime) = repo.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Income, amount = amount, accountId = savings.id!!, categoryId = salary.id, occurredAt = at,
                createdAt = now, updatedAt = now,
            ),
        )
        income(rupees(5_000), now.minusDays(2))
        income(rupees(9_000), now.plusDays(10))
        val goal = repo.saveGoal(draft(target = rupees(1_00_000), account = savings.id))

        val today = repo.accountBalances(asOf = repo.settledCutoff())
        assertEquals(rupees(15_000), goalSaved(goal, today), "the upcoming Rs 9,000 is not counted yet")
        val progress = goalProgress(goal, today, now.toLocalDate())
        assertEquals(rupees(15_000), progress.saved)
        assertEquals(rupees(85_000), progress.left)
        // Counted from its own day: ten days on it is.
        val later = repo.accountBalances(asOf = now.plusDays(11).toLocalDate().plusDays(1).atStartOfDay())
        assertEquals(rupees(24_000), goalSaved(goal, later))
    }

    // ------------------------------------------------------------------------------------------- add money, take out

    @Test
    fun `add money and take out change what a goal on its own has saved, and make no ledger entry`() = runDb {
        val goal = repo.saveGoal(draft(saved = rupees(18_000)))
        val transactions = repo.transactions(TransactionQuery(), limit = 10).items.size
        val balances = repo.accountBalances()

        val added = repo.adjustGoalSaved(goal.id!!, rupees(2_000))
        assertEquals(rupees(20_000), added.saved)
        assertEquals(rupees(20_000), repo.goals().single().saved)
        val taken = repo.adjustGoalSaved(goal.id!!, -rupees(5_500))
        assertEquals(rupees(14_500), taken.saved)
        assertEquals(rupees(14_500), repo.goals().single().saved)
        // Down to nothing is allowed; the rest is the user's.
        assertEquals(0L, repo.adjustGoalSaved(goal.id!!, -rupees(14_500)).saved)

        assertEquals(transactions, repo.transactions(TransactionQuery(), limit = 10).items.size, "no entry was made")
        assertEquals(balances, repo.accountBalances(), "no account moved")
    }

    @Test
    fun `taking out more than is saved is refused with a message, and the amount is unchanged`() = runDb {
        val goal = repo.saveGoal(draft(saved = rupees(1_000)))
        val failure = assertFailsWith<FinanceValidationException> { repo.adjustGoalSaved(goal.id!!, -rupees(1_000) - 1) }
        assertEquals("You can't take out more than is saved here.", failure.message)
        assertEquals(rupees(1_000), repo.goals().single().saved)
        assertEquals(0L, repo.adjustGoalSaved(goal.id!!, -rupees(1_000)).saved, "exactly what is saved is fine")
        // Nothing saved at all.
        assertEquals(
            "You can't take out more than is saved here.",
            assertFailsWith<FinanceValidationException> { repo.adjustGoalSaved(goal.id!!, -1) }.message,
        )
    }

    @Test
    fun `an amount of zero, a linked goal, a missing goal and a sum too large are refused`() = runDb {
        val own = repo.saveGoal(draft(saved = rupees(100)))
        assertEquals("Enter an amount above zero.", assertFailsWith<FinanceValidationException> { repo.adjustGoalSaved(own.id!!, 0) }.message)

        val linked = repo.saveGoal(draft(name = "Linked", account = account().id))
        val message = assertFailsWith<FinanceValidationException> { repo.adjustGoalSaved(linked.id!!, rupees(10)) }.message
        assertTrue("counts the balance of an account" in message, message)
        assertEquals(0L, repo.goals().first { it.id == linked.id }.saved)

        assertEquals("This goal no longer exists.", assertFailsWith<FinanceConflictException> { repo.adjustGoalSaved(9999, 100) }.message)

        repo.adjustGoalSaved(own.id!!, Long.MAX_VALUE - rupees(100))
        assertEquals("That is more than NormPix can keep.", assertFailsWith<FinanceValidationException> { repo.adjustGoalSaved(own.id!!, 1) }.message)
        assertEquals(Long.MAX_VALUE, repo.goals().first { it.id == own.id }.saved, "the refused amount changed nothing")
    }

    // ------------------------------------------------------------------------------------------------- deleting

    @Test
    fun `deleting a goal changes no account, balance or entry`() = runDb {
        val savings = account()
        val goal = repo.saveGoal(draft(account = savings.id))
        repo.saveGoal(draft(name = "Other", saved = rupees(300)))
        val balances = repo.accountBalances()
        val accounts = repo.accounts()
        repo.deleteGoal(goal.id!!)
        assertEquals(listOf("Other"), repo.goals().map { it.name })
        assertEquals(balances, repo.accountBalances())
        assertEquals(accounts, repo.accounts())
    }

    @Test
    fun `deleting the account a goal counts leaves the goal on its own with nothing saved`() = runDb {
        val savings = account()
        val goal = repo.saveGoal(draft(target = rupees(3_00_000), account = savings.id, date = LocalDate.of(2027, 6, 1), emoji = "💰"))
        repo.deleteAccount(savings.id!!)
        val left = repo.goals().single()
        assertNull(left.accountId)
        assertEquals(0L, left.saved)
        assertEquals(goal.name, left.name)
        assertEquals(goal.target, left.target)
        assertEquals(goal.targetDate, left.targetDate)
        assertEquals("💰", left.emoji)
        // From here it is a goal on its own: it can be added to.
        assertEquals(rupees(500), repo.adjustGoalSaved(left.id!!, rupees(500)).saved)
    }

    // ------------------------------------------------------------------------------------- snapshot, restore, undo, reset

    @Test
    fun `a snapshot carries the goals, and restoring it puts them back with their ids - an undo of a deleted account too`() = runDb {
        val savings = account()
        val linked = repo.saveGoal(draft(name = "Fund", account = savings.id, date = LocalDate.of(2027, 6, 1)))
        val own = repo.saveGoal(draft(name = "Trip", saved = rupees(1_800), emoji = "🌴"))
        val before = repo.snapshot()
        assertEquals(listOf(linked, own), before.goals)

        // Delete the account (its goal falls back) and the other goal, then undo as the app does: put the snapshot back.
        repo.deleteAccount(savings.id!!)
        repo.deleteGoal(own.id!!)
        assertEquals(1, repo.goals().size)
        repo.restore(before)
        assertEquals(listOf(linked, own), repo.goals(), "both goals as they were, the link too")
        assertEquals(before, repo.snapshot())
    }

    @Test
    fun `restoring replaces the goals that were there, and a snapshot with none leaves none`() = runDb {
        val kept = repo.snapshot()
        repo.saveGoal(draft(name = "Made after"))
        repo.restore(kept)
        assertTrue(repo.goals().isEmpty())

        repo.saveGoal(draft(name = "One"))
        val one = repo.snapshot()
        repo.resetAll()
        assertTrue(repo.goals().isEmpty(), "a reset erases the goals with the rest")
        repo.saveGoal(draft(name = "Two"))
        repo.restore(one)
        assertEquals(listOf("One"), repo.goals().map { it.name }, "what was there goes, as with every restore")
    }
}
