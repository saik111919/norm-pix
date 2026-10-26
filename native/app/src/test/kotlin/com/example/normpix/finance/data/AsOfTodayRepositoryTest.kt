package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.SelfTransferPair
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.settledBy
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every figure is the money that has moved by the end of today: an entry dated ahead (a bill entered before its day) is
 * upcoming, and moves nothing until its day. And the check that works it all out again puts right what it safely can.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AsOfTodayRepositoryTest {
    private val now = LocalDateTime.of(2026, 10, 26, 13, 9)
    private val october = MonthKey(2026, 10)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private suspend fun account(name: String, kind: AccountKind = AccountKind.Bank, opening: Long = 0): Account =
        repo.saveAccount(Account(name = name, kind = kind, openingBalance = opening, createdAt = now, updatedAt = now))

    private suspend fun category(type: CategoryType): Category = repo.categories().first { it.type == type && it.parentId == null && !it.archived }

    private suspend fun record(type: TransactionType, amount: Long, account: Account, at: LocalDateTime, category: Category? = null, to: Account? = null) =
        repo.saveTransaction(
            FinanceTransaction(
                type = type, amount = amount, accountId = account.id!!, toAccountId = to?.id, categoryId = category?.id, occurredAt = at,
                createdAt = now, updatedAt = now,
            ),
        )

    @Test
    fun `balances and totals count what is dated up to today, and an entry dated ahead from its own day`() = runBlocking<Unit> {
        val bank = account("HDFC Salary", opening = 100_000)
        val food = category(CategoryType.Expense)
        val salary = category(CategoryType.Income)
        record(TransactionType.Expense, 10_000, bank, now.minusHours(1), food)
        record(TransactionType.Expense, 2_000, bank, now.withHour(23).withMinute(30), food) // later today: today's
        record(TransactionType.Expense, 5_000, bank, now.plusDays(1), food)
        record(TransactionType.Income, 50_000, bank, LocalDateTime.of(2026, 10, 31, 9, 0), salary)
        val cutoff = repo.settledCutoff()
        assertEquals(LocalDateTime.of(2026, 10, 27, 0, 0), cutoff)

        assertEquals(133_000L, repo.accountBalances()[bank.id], "everything recorded")
        assertEquals(88_000L, repo.accountBalances(asOf = cutoff)[bank.id], "as of today")

        val soFar = repo.summaryFor(TransactionQuery(range = october.range, before = cutoff))
        assertEquals(12_000L, soFar.expense)
        assertEquals(0L, soFar.income)
        val ahead = repo.summaryFor(TransactionQuery(range = october.range, notBefore = cutoff))
        assertEquals(5_000L, ahead.expense)
        assertEquals(50_000L, ahead.income)
        assertEquals(2, repo.countFrom(cutoff))
        assertEquals(2, repo.transactions(TransactionQuery(before = cutoff)).items.size)

        assertEquals(12_000L, repo.monthlyTotals(october, october, asOf = cutoff).single().expense)
        assertEquals(17_000L, repo.monthlyTotals(october, october).single().expense, "without a cutoff, as before")
        assertEquals(12_000L, repo.budgetOverview(october, asOf = cutoff).totalExpense)
        assertEquals(12_000L, repo.categoryTotals(october.range.settledBy(cutoff), TransactionType.Expense).sumOf { it.amount })
        assertEquals(12_000L, repo.peopleSpending(null, asOf = cutoff).totalExpense)
    }

    @Test
    fun `a budget on an archived category counts toward nothing from this month on, and past months keep it`() = runBlocking<Unit> {
        val food = category(CategoryType.Expense)
        val other = repo.categories().first { it.type == CategoryType.Expense && it.parentId == null && it.id != food.id }
        repo.setBudget(food.id, from = MonthKey(2026, 9), amount = 500_000)
        repo.setBudget(other.id, from = MonthKey(2026, 9), amount = 200_000)
        repo.setCategoryArchived(food.id!!, archived = true)

        val overview = repo.budgetOverview(october)
        assertEquals(setOf(other.id), overview.categories.keys, "the archived category's budget is gone from October")
        assertEquals(200_000L, overview.headline!!.allocated, "and from what is left to spend")
        assertEquals(setOf(food.id, other.id), repo.budgetOverview(MonthKey(2026, 9)).categories.keys, "September keeps both")
    }

    @Test
    fun `the check moves a repeating payment on past what it recorded, and lets go of an archived default account`() = runBlocking<Unit> {
        val bank = account("HDFC Salary")
        val food = category(CategoryType.Expense)
        val first = LocalDateTime.of(2026, 8, 5, 9, 0)
        val rule = repo.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense, amount = 64_900, accountId = bank.id!!, categoryId = food.id, payee = "Netflix",
                frequency = RecurrenceFrequency.Monthly, startDate = first, nextDate = LocalDateTime.of(2026, 11, 5, 9, 0),
                createdAt = now, updatedAt = now,
            ),
        )
        for (month in 8..10) {
            repo.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense, amount = 64_900, accountId = bank.id!!, categoryId = food.id, payee = "Netflix",
                    occurredAt = LocalDateTime.of(2026, month, 5, 9, 0), recurringId = rule.id, createdAt = now, updatedAt = now,
                ),
            )
        }
        // Its next date put back to one it already recorded: left so, it would record October's again.
        repo.saveRecurringRule(rule.copy(nextDate = LocalDateTime.of(2026, 9, 5, 9, 0)))
        // A default that is archived (archiving clears it; a backup from elsewhere can bring one back).
        repo.setAccountArchived(bank.id!!, archived = true)
        repo.setDefaultAccountId(bank.id)

        val result = repo.checkAndRepair()
        assertEquals(1, result.issues.ruleCatchUps.size)
        assertTrue(result.issues.staleDefaultAccount)
        assertEquals(0, result.recordedRepeats, "nothing recorded twice")
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), repo.recurringRules().single().nextDate)
        assertNull(repo.defaultAccountId())
        assertEquals(3, repo.transactions(TransactionQuery()).items.size)

        val again = repo.checkAndRepair()
        assertEquals(0, again.issues.repairCount, "a second check finds nothing more to put right")
    }

    @Test
    fun `an expense and the same income on the other side become one transfer, the money counted once`() = runBlocking<Unit> {
        val bank = account("HDFC Salary", opening = 1_000_000)
        val savings = account("SBI Savings")
        val out = record(TransactionType.Expense, 500_000, bank, now.minusDays(2), category(CategoryType.Expense))
        val into = record(TransactionType.Income, 500_000, savings, now.minusDays(2).plusMinutes(3), category(CategoryType.Income))

        val pair = repo.checkAndRepair().issues.selfTransfers.single()
        assertEquals(out.id, pair.expense.id)
        repo.combineSelfTransfer(SelfTransferPair(pair.expense, pair.income))

        val left = repo.transactions(TransactionQuery()).items.single()
        assertEquals(TransactionType.Transfer, left.type)
        assertEquals(bank.id, left.accountId)
        assertEquals(savings.id, left.toAccountId)
        assertNull(repo.transactionById(into.id!!))
        assertEquals(500_000L, repo.accountBalances()[bank.id])
        assertEquals(500_000L, repo.accountBalances()[savings.id])
        val month = repo.summaryFor(TransactionQuery(range = october.range))
        assertEquals(0L, month.expense, "no longer spending")
        assertEquals(0L, month.income, "nor income")
    }
}
