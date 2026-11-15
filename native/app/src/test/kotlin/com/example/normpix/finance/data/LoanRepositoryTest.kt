package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.LoanEmi
import com.example.normpix.finance.domain.LoanTerms
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.emiFor
import com.example.normpix.finance.domain.spendableAccounts
import com.example.normpix.finance.share.AccountMatching
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
 * Loans in the ledger (2026-11-15): an account's loan terms are validated and kept; an EMI is two entries saved together or not at
 * all (a transfer of the principal onto the loan, an expense of the interest); what is owed drops by the principal only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanRepositoryTest {
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

    private val lakh10 = 100_000_000L
    private val terms = LoanTerms(lakh10, 900, 240, emiFor(lakh10, 900, 240), LocalDate.of(2026, 12, 5))

    private suspend fun savings(opening: Long = 50_000_000L, name: String = "HDFC Savings"): Account = repo.saveAccount(
        Account(name = name, kind = AccountKind.Bank, openingBalance = opening, createdAt = now, updatedAt = now),
    )

    private suspend fun homeLoan(opening: Long = -lakh10, loan: LoanTerms? = terms, kind: AccountKind = AccountKind.Loan): Account =
        repo.saveAccount(Account(name = "Home loan", kind = kind, openingBalance = opening, createdAt = now, updatedAt = now, loan = loan))

    private suspend fun loansCategory() = repo.categories().first { it.name == "Loans & EMI" && it.parentId == null }

    private fun at(day: Int, month: Int = 11) = LocalDateTime.of(2026, month, day, 10, 15)

    // ------------------------------------------------------------------------------------------- saving a loan

    @Test
    fun `a loan is kept with its terms, as an account of the Loan kind or as another kind with the switch on`() = runDb {
        val byKind = homeLoan()
        val bySwitch = repo.saveAccount(Account(name = "Car loan", kind = AccountKind.Other, openingBalance = -5_000_000L, createdAt = now, updatedAt = now, loan = terms.copy(principal = 5_000_000L, emi = 100_000L)))
        val read = repo.accounts()
        assertEquals(terms, read.first { it.id == byKind.id }.loan)
        assertEquals(AccountKind.Loan, read.first { it.id == byKind.id }.kind)
        assertEquals(terms.copy(principal = 5_000_000L, emi = 100_000L), read.first { it.id == bySwitch.id }.loan)
        assertEquals(AccountKind.Other, read.first { it.id == bySwitch.id }.kind)
        assertTrue(read.first { it.id == bySwitch.id }.isLoan)
        // Any kind but a card, cash included.
        assertNotNull(repo.saveAccount(Account(name = "Cash loan", kind = AccountKind.Cash, createdAt = now, updatedAt = now, loan = terms)).loan)
    }

    @Test
    fun `an account that is not a loan has none, and turning the switch off takes the terms away`() = runDb {
        val cash = repo.accounts().first { it.name == "Cash" }
        assertNull(cash.loan)
        val loan = homeLoan(kind = AccountKind.Bank)
        assertEquals(terms, repo.accounts().first { it.id == loan.id }.loan)
        repo.saveAccount(loan.copy(loan = null, updatedAt = now))
        val after = repo.accounts().first { it.id == loan.id }
        assertNull(after.loan)
        assertTrue(!after.isLoan)
        assertEquals(-lakh10, after.openingBalance, "what was owed is untouched")
    }

    private suspend fun refused(account: Account): String =
        assertFailsWith<FinanceValidationException> { repo.saveAccount(account) }.message

    private fun loanAccount(loan: LoanTerms?, kind: AccountKind = AccountKind.Loan) =
        Account(name = "Home loan", kind = kind, openingBalance = -lakh10, createdAt = now, updatedAt = now, loan = loan)

    @Test
    fun `a loan whose terms don't hold is refused in plain words, and nothing is saved`() = runDb {
        val before = repo.accounts().size
        assertEquals("Enter the amount borrowed, above zero.", refused(loanAccount(terms.copy(principal = 0))))
        assertEquals("Enter an interest rate from 0% to 100% a year.", refused(loanAccount(terms.copy(rateBp = 10_001))))
        assertEquals("Enter an interest rate from 0% to 100% a year.", refused(loanAccount(terms.copy(rateBp = -1))))
        assertEquals("Enter a number of months from 1 to 600.", refused(loanAccount(terms.copy(tenureMonths = 0))))
        assertEquals("Enter a number of months from 1 to 600.", refused(loanAccount(terms.copy(tenureMonths = 601))))
        assertEquals("Enter an EMI above zero.", refused(loanAccount(terms.copy(emi = 0))))
        assertEquals(before, repo.accounts().size)
    }

    @Test
    fun `the Loan kind needs its terms, and a card can't be a loan`() = runDb {
        assertEquals("Enter what you borrowed, the interest and the EMI.", refused(loanAccount(null)))
        assertEquals("A card can't be a loan. Choose another type, or leave the loan off.", refused(loanAccount(terms, AccountKind.Card)))
        assertTrue(repo.accounts().none { it.isLoan })
    }

    @Test
    fun `the edges of what a loan allows are saved`() = runDb {
        for (edge in listOf(terms.copy(rateBp = 0), terms.copy(rateBp = 10_000), terms.copy(tenureMonths = 1), terms.copy(tenureMonths = 600))) {
            assertEquals(edge, repo.saveAccount(loanAccount(edge)).loan)
        }
    }

    // --------------------------------------------------------------------------------------------- recording an EMI

    @Test
    fun `an EMI saves a transfer of the principal and an expense of the interest, with their notes`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        val category = loansCategory()

        val saved = repo.recordLoanEmi(
            LoanEmi(loan.id!!, bank.id!!, at(5), principal = 149_726, interest = 750_000, interestCategoryId = category.id, payee = "Home loan"),
        )

        assertEquals(2, saved.size)
        assertTrue(saved.all { it.id != null })
        val stored = repo.transactions(TransactionQuery(accountIds = setOf(loan.id!!)), limit = 10).items
        val transfer = repo.transactionById(saved[0].id!!)!!
        assertEquals(TransactionType.Transfer, transfer.type)
        assertEquals(149_726L, transfer.amount)
        assertEquals(bank.id, transfer.accountId)
        assertEquals(loan.id, transfer.toAccountId)
        assertNull(transfer.categoryId)
        assertEquals("EMI Nov 2026 - principal", transfer.note)
        assertEquals(at(5), transfer.occurredAt)
        val expense = repo.transactionById(saved[1].id!!)!!
        assertEquals(TransactionType.Expense, expense.type)
        assertEquals(750_000L, expense.amount)
        assertEquals(bank.id, expense.accountId)
        assertEquals(category.id, expense.categoryId)
        assertNull(expense.toAccountId)
        assertEquals("Home loan", expense.payee)
        assertEquals("EMI Nov 2026 - interest", expense.note)
        assertEquals(at(5), expense.occurredAt)
        assertEquals(1, stored.size, "only the transfer is on the loan's side")
    }

    @Test
    fun `what is owed drops by the principal only, and only the interest is spending`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        val category = loansCategory()
        repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5), 149_726, 750_000, category.id))

        val balances = repo.accountBalances()
        assertEquals(-lakh10 + 149_726L, balances[loan.id], "the loan owes the principal less: 99,850,274 paise")
        assertEquals(50_000_000L - 149_726L - 750_000L, balances[bank.id], "the bank paid the whole EMI")
        val month = TransactionQuery(range = com.example.normpix.finance.domain.DateRange(LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(2026, 12, 1, 0, 0)))
        val summary = repo.summaryFor(month)
        assertEquals(750_000L, summary.expense, "the interest is spending; the principal is not")
        assertEquals(0L, summary.income)
    }

    @Test
    fun `either both entries are saved or neither - a second that fails takes the first back out`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        val income = repo.categories().first { it.type == com.example.normpix.finance.domain.CategoryType.Income }
        val before = repo.transactions(TransactionQuery(), limit = 100).items.size
        val balancesBefore = repo.accountBalances()

        // The transfer is written first and is fine; the expense then names an income category, which the ledger refuses.
        val wrongKind = assertFailsWith<FinanceValidationException> {
            repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5), 149_726, 750_000, income.id))
        }
        assertEquals("Choose an expense category.", wrongKind.message)
        // ... or a category that does not exist; or an amount no ledger holds.
        assertFailsWith<FinanceValidationException> { repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5), 149_726, 750_000, 987_654)) }
        val tooBig = assertFailsWith<FinanceValidationException> {
            repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5), 149_726, 99_999_999_999_999_999L, loansCategory().id))
        }
        assertEquals("That amount is too large.", tooBig.message)

        assertEquals(before, repo.transactions(TransactionQuery(), limit = 100).items.size, "no entry of any of them is in the ledger")
        assertEquals(balancesBefore, repo.accountBalances(), "and no balance moved")
    }

    @Test
    fun `an EMI with no interest is only the transfer`() = runDb {
        val bank = savings()
        val loan = homeLoan(loan = terms.copy(rateBp = 0))
        val saved = repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5), principal = 416_667, interest = 0))
        assertEquals(listOf(TransactionType.Transfer), saved.map { it.type })
        assertEquals(-lakh10 + 416_667L, repo.accountBalances()[loan.id])
    }

    @Test
    fun `an EMI that can't be recorded says why`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        val category = loansCategory().id
        assertEquals(
            "This account isn't a loan.",
            assertFailsWith<FinanceValidationException> { repo.recordLoanEmi(LoanEmi(bank.id!!, loan.id!!, at(5), 1_000, 100, category)) }.message,
        )
        assertEquals(
            "Pay the EMI from another account.",
            assertFailsWith<FinanceValidationException> { repo.recordLoanEmi(LoanEmi(loan.id!!, loan.id!!, at(5), 1_000, 100, category)) }.message,
        )
        assertEquals(
            "This loan no longer exists.",
            assertFailsWith<FinanceConflictException> { repo.recordLoanEmi(LoanEmi(9_999, bank.id!!, at(5), 1_000, 100, category)) }.message,
        )
        assertEquals(
            "Choose an account.",
            assertFailsWith<FinanceValidationException> { repo.recordLoanEmi(LoanEmi(loan.id!!, 9_999, at(5), 1_000, 100, category)) }.message,
        )
        assertEquals("Enter the principal, the interest, or both.", assertFailsWith<FinanceValidationException> {
            repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5), 0, 0, category))
        }.message)
        assertTrue(repo.transactions(TransactionQuery(), limit = 10).items.isEmpty())
    }

    @Test
    fun `the last payment onto the loan is where the next EMI starts from`() = runDb {
        val bank = savings()
        val other = savings(name = "SBI Savings")
        val loan = homeLoan()
        val category = loansCategory().id
        val cutoff = LocalDateTime.of(2026, 11, 16, 0, 0)
        assertNull(repo.lastLoanPayment(loan.id!!, cutoff), "nothing paid yet")

        repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5, 10), 1_000, 100, category))
        repo.recordLoanEmi(LoanEmi(loan.id!!, other.id!!, at(5), 1_000, 100, category))
        repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5, 12), 1_000, 100, category)) // dated after today
        val last = assertNotNull(repo.lastLoanPayment(loan.id!!, cutoff))
        assertEquals(at(5), last.occurredAt, "the latest as of today, not the one dated ahead")
        assertEquals(other.id, last.accountId)
        assertEquals(loan.id, last.toAccountId)
        assertEquals(at(5, 12), repo.lastLoanPayment(loan.id!!, LocalDateTime.of(2027, 1, 1, 0, 0))!!.occurredAt)
        assertNull(repo.lastLoanPayment(bank.id!!, cutoff), "nothing is paid onto an account that is no loan")
    }

    // ------------------------------------------------------------------------------------------- around the loan

    @Test
    fun `checking the ledger leaves a loan and its entries as they were`() = runDb {
        val bank = savings()
        val loan = homeLoan()
        repo.recordLoanEmi(LoanEmi(loan.id!!, bank.id!!, at(5, 10), 149_726, 750_000, loansCategory().id))
        val accountsBefore = repo.accounts()
        val transactionsBefore = repo.transactions(TransactionQuery(), limit = 100).items

        val result = repo.checkAndRepair()

        assertEquals(0, result.issues.repairCount, "nothing to put right")
        assertEquals(accountsBefore, repo.accounts())
        assertEquals(transactionsBefore, repo.transactions(TransactionQuery(), limit = 100).items)
        assertEquals(terms, repo.accounts().first { it.id == loan.id }.loan)
    }

    @Test
    fun `a loan is never money that can be spent, whatever its balance says`() {
        val cash = Account(id = 1, name = "Cash", kind = AccountKind.Cash, createdAt = now, updatedAt = now)
        val bank = Account(id = 2, name = "Bank", kind = AccountKind.Bank, createdAt = now, updatedAt = now)
        val kind = Account(id = 3, name = "Loan", kind = AccountKind.Loan, createdAt = now, updatedAt = now, loan = terms)
        val switched = Account(id = 4, name = "Car", kind = AccountKind.Bank, openingBalance = 0, createdAt = now, updatedAt = now, loan = terms)
        assertEquals(setOf(1L, 2L), spendableAccounts(listOf(cash, bank, kind, switched), emptyMap()))
    }

    @Test
    fun `a loan at a bank does not make that bank's accounts ambiguous for a shared receipt`() {
        val hdfc = BankCatalog.BuiltIn.leading("HDFC Bank")!!.key
        val bank = Account(id = 1, name = "HDFC Savings", kind = AccountKind.Bank, bankKey = hdfc, createdAt = now, updatedAt = now)
        val loan = Account(id = 2, name = "HDFC Home Loan", kind = AccountKind.Loan, bankKey = hdfc, createdAt = now, updatedAt = now, loan = terms)
        val pick = AccountMatching.find(
            text = "Paid from HDFC Bank",
            counterparty = null,
            instrument = null,
            accounts = listOf(bank, loan),
            catalog = BankCatalog.BuiltIn,
            masks = emptyList(),
            cards = emptyList(),
        )
        assertEquals(bank, pick.account, "the loan is not a candidate")
    }
}
