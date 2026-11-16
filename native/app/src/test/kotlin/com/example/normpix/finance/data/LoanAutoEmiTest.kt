package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.LoanEmi
import com.example.normpix.finance.domain.LoanTerms
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.emiFor
import com.example.normpix.finance.domain.interestOn
import com.example.normpix.finance.domain.loanSchedule
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A loan's EMIs recorded each month (owner, 2026-11-16: "If I entered EMI amount it can be given on monthly based amount"): turning
 * it on makes a monthly transfer of the EMI onto the loan; each that falls due is recorded split into principal and interest by what is
 * owed that day, marked as the repeating payment's; one recorded by hand isn't recorded again; it ends once nothing is owed; turning
 * it off deletes the repeating payment and keeps what it recorded; a new EMI on the loan is what it pays from then on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanAutoEmiTest {
    private var now = LocalDateTime.of(2026, 11, 16, 12, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private val lakh10 = 100_000_000L
    private val terms = LoanTerms(lakh10, 900, 240, emiFor(lakh10, 900, 240), LocalDate.of(2026, 12, 5))
    private val schedule = loanSchedule(lakh10, terms.rateBp, terms.emi) { terms.dateOf(it) }!!

    private suspend fun savings(): Account = repo.saveAccount(
        Account(name = "HDFC Savings", kind = AccountKind.Bank, openingBalance = 50_000_000L, createdAt = now, updatedAt = now),
    )

    private suspend fun homeLoan(opening: Long = -lakh10, loan: LoanTerms = terms): Account =
        repo.saveAccount(Account(name = "Home loan", kind = AccountKind.Loan, openingBalance = opening, createdAt = now, updatedAt = now, loan = loan))

    private suspend fun entries(): List<FinanceTransaction> = repo.snapshot().transactions.sortedWith(compareBy({ it.occurredAt }, { it.type }))

    private suspend fun owed(loan: Account): Long = -(repo.accountBalances()[loan.id] ?: 0L)

    private suspend fun loansCategoryId(): Long = repo.categories().first { it.name == "Loans & EMI" && it.parentId == null }.id!!

    /** Moves the clock to [at] and records whatever repeating payments are due, as opening the app does. */
    private suspend fun openAppOn(at: LocalDateTime): Int {
        now = at
        return repo.processRecurring()
    }

    @Test
    fun `turning it on makes a monthly transfer of the EMI onto the loan, from the next EMI to the last`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        assertNull(repo.emiRuleOf(loan.id!!))

        val rule = repo.startLoanEmis(loan.id!!, bank.id!!)
        assertEquals(TransactionType.Transfer, rule.type)
        assertEquals(terms.emi, rule.amount)
        assertEquals(bank.id, rule.accountId)
        assertEquals(loan.id, rule.toAccountId)
        assertEquals(RecurrenceFrequency.Monthly, rule.frequency)
        assertEquals(LocalDateTime.of(2026, 12, 5, 9, 0), rule.nextDate, "the first EMI's day")
        assertEquals(schedule.last().date, rule.endDate?.toLocalDate(), "it ends on the day the schedule pays the loan off")
        assertEquals(rule, repo.emiRuleOf(loan.id!!))
        assertEquals(1, repo.recurringRules().size)

        // Turning it on again changes nothing: there is one repeating payment for a loan's EMIs.
        assertEquals(rule.id, repo.startLoanEmis(loan.id!!, bank.id!!).id)
        assertEquals(1, repo.recurringRules().size)
        assertTrue(entries().isEmpty(), "nothing is recorded before the first EMI's day")
    }

    @Test
    fun `each EMI that falls due is recorded split into principal and interest, as the schedule has it`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        val rule = repo.startLoanEmis(loan.id!!, bank.id!!)

        assertEquals(0, openAppOn(LocalDateTime.of(2026, 12, 5, 8, 0)), "not before its time of day")
        assertEquals(2, openAppOn(LocalDateTime.of(2026, 12, 5, 10, 0)))
        val first = entries()
        assertEquals(2, first.size)
        val principal = first.single { it.type == TransactionType.Transfer }
        val interest = first.single { it.type == TransactionType.Expense }
        assertEquals(schedule[0].principal, principal.amount)
        assertEquals(interestOn(lakh10, 900), interest.amount)
        assertEquals(schedule[0].interest, interest.amount)
        assertEquals(bank.id, principal.accountId)
        assertEquals(loan.id, principal.toAccountId)
        assertEquals(bank.id, interest.accountId)
        assertEquals(loansCategoryId(), interest.categoryId)
        assertEquals("Home loan", interest.payee)
        assertEquals("EMI Dec 2026 - principal", principal.note)
        assertEquals("EMI Dec 2026 - interest", interest.note)
        assertEquals(LocalDateTime.of(2026, 12, 5, 9, 0), principal.occurredAt)
        assertTrue(first.all { it.recurringId == rule.id }, "both are the repeating payment's")
        assertEquals(lakh10 - schedule[0].principal, owed(loan), "what is owed drops by the principal only")

        // Two months later, opened once: January's and February's, each split by what was owed just before it.
        assertEquals(4, openAppOn(LocalDateTime.of(2027, 2, 20, 12, 0)))
        val all = entries()
        assertEquals(6, all.size)
        for (month in 1..2) {
            val day = schedule[month].date
            val made = all.filter { it.occurredAt.toLocalDate() == day }
            assertEquals(schedule[month].principal, made.single { it.type == TransactionType.Transfer }.amount, "principal of $day")
            assertEquals(schedule[month].interest, made.single { it.type == TransactionType.Expense }.amount, "interest of $day")
        }
        assertEquals(schedule[2].outstandingAfter, owed(loan))
        assertEquals(LocalDateTime.of(2027, 3, 5, 9, 0), repo.emiRuleOf(loan.id!!)?.nextDate)
    }

    @Test
    fun `an EMI recorded by hand is not recorded again, but a payment that isn't an EMI doesn't stop one`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        repo.startLoanEmis(loan.id!!, bank.id!!)
        // December's paid two days early with Record EMI.
        now = LocalDateTime.of(2026, 12, 3, 18, 0)
        repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, now, schedule[0].principal, schedule[0].interest, loansCategoryId(), "Home loan"))
        assertEquals(0, openAppOn(LocalDateTime.of(2026, 12, 5, 10, 0)), "December's is already in")
        assertEquals(2, entries().size)
        assertEquals(LocalDateTime.of(2027, 1, 5, 9, 0), repo.emiRuleOf(loan.id!!)?.nextDate, "and the next is January's")

        // A transfer onto the loan that isn't an EMI (paying some of it off early) leaves January's to be recorded.
        now = LocalDateTime.of(2027, 1, 2, 12, 0)
        repo.saveTransaction(
            FinanceTransaction(type = TransactionType.Transfer, amount = 1_000_000L, accountId = bank.id!!, toAccountId = loan.id, occurredAt = now, note = "Part payment", createdAt = now, updatedAt = now),
        )
        assertEquals(2, openAppOn(LocalDateTime.of(2027, 1, 5, 10, 0)))
        val january = entries().filter { it.recurringId != null }
        val owedBefore = lakh10 - schedule[0].principal - 1_000_000L
        assertEquals(interestOn(owedBefore, 900), january.single { it.type == TransactionType.Expense }.amount, "split by what was really owed")
        assertEquals(terms.emi - interestOn(owedBefore, 900), january.single { it.type == TransactionType.Transfer }.amount)
    }

    @Test
    fun `once nothing is owed the repeating payment ends and records nothing more`() = runDb {
        val bank = savings()
        // An older loan with Rs 15,000 left: one EMI and a bit.
        val loan = homeLoan(opening = -1_500_000L)
        val rule = repo.startLoanEmis(loan.id!!, bank.id!!)
        assertEquals(LocalDate.of(2027, 1, 5), rule.endDate?.toLocalDate(), "the schedule pays it off in January")
        // Paid off by hand in full before December's.
        now = LocalDateTime.of(2026, 11, 30, 12, 0)
        repo.saveTransaction(
            FinanceTransaction(type = TransactionType.Transfer, amount = 1_500_000L, accountId = bank.id!!, toAccountId = loan.id, occurredAt = now, note = "Closed", createdAt = now, updatedAt = now),
        )
        assertEquals(0, openAppOn(LocalDateTime.of(2027, 3, 1, 12, 0)))
        assertEquals(0L, owed(loan))
        val ended = repo.recurringRules().single()
        assertTrue(ended.isFinished, "it has ended")
        assertTrue(ended.endDate!!.isBefore(LocalDateTime.of(2026, 12, 5, 9, 0)))
        assertNull(repo.emiRuleOf(loan.id!!))
        assertEquals(1, entries().size, "only the payment that closed it")
    }

    @Test
    fun `the last EMI pays exactly what is left`() = runDb {
        val bank = savings()
        val loan = homeLoan(opening = -1_500_000L)
        repo.startLoanEmis(loan.id!!, bank.id!!)
        openAppOn(LocalDateTime.of(2027, 3, 1, 12, 0))
        assertEquals(0L, owed(loan))
        val principal = entries().filter { it.type == TransactionType.Transfer }.sumOf { it.amount }
        assertEquals(1_500_000L, principal, "never more than was owed")
        assertTrue(repo.recurringRules().single().isFinished)
    }

    @Test
    fun `the interest goes under Other when Loans & EMI is archived`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        repo.startLoanEmis(loan.id!!, bank.id!!)
        repo.setCategoryArchived(loansCategoryId(), true)
        openAppOn(LocalDateTime.of(2026, 12, 5, 10, 0))
        val other = repo.categories().first { it.name == "Other" && it.parentId == null && it.type.name == "Expense" }
        assertEquals(other.id, entries().single { it.type == TransactionType.Expense }.categoryId)
    }

    @Test
    fun `turning it off deletes the repeating payment and keeps what it recorded`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        repo.startLoanEmis(loan.id!!, bank.id!!)
        openAppOn(LocalDateTime.of(2026, 12, 5, 10, 0))
        val stopped = repo.stopLoanEmis(loan.id!!)
        assertNotNull(stopped)
        assertNull(repo.emiRuleOf(loan.id!!))
        assertTrue(repo.recurringRules().isEmpty())
        assertEquals(2, entries().size, "December's EMI stays")
        assertEquals(0, openAppOn(LocalDateTime.of(2027, 2, 1, 12, 0)), "and no more are recorded")
        assertNull(repo.stopLoanEmis(loan.id!!), "nothing to stop twice")
    }

    @Test
    fun `a new EMI on the loan is what the repeating payment pays from then on`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        repo.startLoanEmis(loan.id!!, bank.id!!)
        val newEmi = terms.emi + 100_000L
        repo.saveAccount(loan.copy(loan = terms.copy(emi = newEmi), updatedAt = now))
        val rule = repo.emiRuleOf(loan.id!!)!!
        assertEquals(newEmi, rule.amount)
        assertNull(rule.endDate, "the new EMI pays it off on another day; it ends when nothing is owed")

        // An amount set by hand on the repeating payment is the user's: a later EMI change leaves it.
        repo.saveRecurringRule(rule.copy(amount = 1_200_000L))
        repo.saveAccount(loan.copy(loan = terms.copy(emi = newEmi + 5_000L), updatedAt = now))
        assertEquals(1_200_000L, repo.emiRuleOf(loan.id!!)!!.amount)
    }

    @Test
    fun `turning it on is refused for an account that isn't a loan, a loan with nothing owed, and paying from the loan`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        assertEquals("This account isn't a loan.", assertFailsWith<FinanceValidationException> { repo.startLoanEmis(bank.id!!, loan.id!!) }.message)
        assertEquals("Pay the EMI from another account.", assertFailsWith<FinanceValidationException> { repo.startLoanEmis(loan.id!!, loan.id!!) }.message)
        val paidOff = repo.saveAccount(Account(name = "Old loan", kind = AccountKind.Loan, openingBalance = 0L, createdAt = now, updatedAt = now, loan = terms))
        assertEquals("Nothing is owed on this loan.", assertFailsWith<FinanceValidationException> { repo.startLoanEmis(paidOff.id!!, bank.id!!) }.message)
        assertFailsWith<FinanceConflictException> { repo.startLoanEmis(9_999L, bank.id!!) }
        assertTrue(repo.recurringRules().isEmpty())
    }

    @Test
    fun `EMIs missed before today are not recorded all at once`() = runDb {
        val bank = savings()
        // The first EMI was in September and none was recorded: turning it on today starts with December's.
        val loan = homeLoan(loan = terms.copy(start = LocalDate.of(2026, 9, 5)))
        val rule = repo.startLoanEmis(loan.id!!, bank.id!!)
        assertEquals(LocalDateTime.of(2026, 12, 5, 9, 0), rule.nextDate)
        assertEquals(0, repo.processRecurring())
    }

    @Test
    fun `a loan's EMI interest is not offered as a payment that looks like it repeats`() = runDb {
        val bank = savings()
        val loan = homeLoan(loan = terms.copy(start = LocalDate.of(2026, 7, 5)))
        var owed = lakh10
        for (month in 0 until 5) {
            val at = LocalDateTime.of(2026, 7, 5, 10, 0).plusMonths(month.toLong())
            val interest = interestOn(owed, 900)
            repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at, terms.emi - interest, interest, loansCategoryId(), "Home loan"))
            owed -= terms.emi - interest
        }
        val suggested = repo.repeatSuggestions(LocalDate.of(2026, 11, 16)).map { it.payee }
        assertTrue("Home loan" !in suggested, "suggested: $suggested")
    }
}
