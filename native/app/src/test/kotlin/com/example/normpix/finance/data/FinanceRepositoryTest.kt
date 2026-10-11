package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinanceRepositoryTest {
    private fun runDb(block: suspend () -> Unit) {
        runBlocking { block() }
    }

    private var now = LocalDateTime.of(2026, 9, 15, 12, 0)
    private lateinit var database: FinanceDatabase
    private lateinit var repo: FinanceRepository

    private val september = MonthKey(2026, 9)

    @BeforeTest
    fun setUp() {
        now = LocalDateTime.of(2026, 9, 15, 12, 0)
        database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun dt(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    private suspend fun cash(): Account = repo.accounts().first { it.name == "Cash" }

    private suspend fun category(name: String): Category = repo.categories().first { it.name == name }

    private suspend fun newAccount(name: String, opening: Long = 0): Account = repo.saveAccount(
        Account(name = name, kind = AccountKind.Bank, openingBalance = opening, createdAt = now, updatedAt = now),
    )

    private suspend fun add(
        type: TransactionType,
        amount: Long,
        accountId: Long,
        toAccountId: Long? = null,
        categoryId: Long? = null,
        at: LocalDateTime? = null,
        payee: String? = null,
        note: String? = null,
    ): FinanceTransaction = repo.saveTransaction(
        FinanceTransaction(
            type = type,
            amount = amount,
            accountId = accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
            occurredAt = at ?: now,
            payee = payee,
            note = note,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private suspend fun categoryTotal(name: String): Long {
        val id = category(name).id
        return repo.categoryTotals(september.range, TransactionType.Expense)
            .filter { it.categoryId == id }
            .sumOf { it.amount }
    }

    @Test
    fun `first run seeds a Cash account, default categories and INR`() = runDb {
        assertEquals(listOf("Cash"), repo.accounts().map { it.name })
        val categories = repo.categories()
        assertTrue(categories.any { it.type == CategoryType.Expense })
        assertTrue(categories.any { it.type == CategoryType.Income })
        assertEquals("INR", repo.currencyCode())
    }

    @Test
    fun `scenarios 1 to 5 - expense, income, transfer, edit, delete keep every total consistent`() = runDb {
        // Scenario 1: Cash with 5,000, add 500 Food expense.
        val cashAccount = repo.saveAccount(cash().copy(openingBalance = 500_000))
        val bank = newAccount("Bank", 2_000_000)
        val food = category("Food & dining")
        val shopping = category("Shopping")
        val salary = category("Salary")

        repo.setBudget(from = september, amount = 100_000)
        repo.setBudget(categoryId = food.id, from = september, amount = 60_000)

        val lunch = add(TransactionType.Expense, 50_000, accountId = cashAccount.id!!, categoryId = food.id)
        var balances = repo.accountBalances()
        assertEquals(450_000L, balances[cashAccount.id])
        assertEquals(50_000L, repo.summary(september.range).expense)
        assertEquals(50_000L, categoryTotal("Food & dining"))

        var budgets = repo.budgetOverview(september)
        assertEquals(50_000L, budgets.overall!!.spent)
        assertEquals(50_000L, budgets.categories[food.id]!!.spent)

        // Scenario 2: 50,000 salary into Cash.
        add(TransactionType.Income, 5_000_000, accountId = cashAccount.id!!, categoryId = salary.id)
        balances = repo.accountBalances()
        assertEquals(5_450_000L, balances[cashAccount.id])
        assertEquals(5_000_000L, repo.summary(september.range).income)

        // Scenario 3: transfer 10,000 Bank -> Cash.
        val before = repo.summary(september.range)
        add(TransactionType.Transfer, 1_000_000, accountId = bank.id!!, toAccountId = cashAccount.id)
        balances = repo.accountBalances()
        assertEquals(1_000_000L, balances[bank.id])
        assertEquals(6_450_000L, balances[cashAccount.id])
        val after = repo.summary(september.range)
        assertEquals(before.income, after.income)
        assertEquals(before.expense, after.expense)

        // Scenario 4: edit the 500 Food expense into 700 Shopping.
        repo.saveTransaction(lunch.copy(amount = 70_000, categoryId = shopping.id))
        assertEquals(0L, categoryTotal("Food & dining"))
        assertEquals(70_000L, categoryTotal("Shopping"))
        assertEquals(70_000L, repo.summary(september.range).expense)
        balances = repo.accountBalances()
        assertEquals(500_000L - 70_000 + 5_000_000 + 1_000_000, balances[cashAccount.id])
        budgets = repo.budgetOverview(september)
        assertEquals(70_000L, budgets.overall!!.spent)
        assertEquals(0L, budgets.categories[food.id]!!.spent)
        val edited = repo.transactionById(lunch.id!!)
        assertEquals(lunch.createdAt, edited!!.createdAt, "identity preserved")

        // Scenario 5: delete it.
        repo.deleteTransaction(lunch.id!!)
        balances = repo.accountBalances()
        assertEquals(500_000L + 5_000_000 + 1_000_000, balances[cashAccount.id])
        assertEquals(0L, repo.summary(september.range).expense)
        assertEquals(0L, categoryTotal("Shopping"))
        budgets = repo.budgetOverview(september)
        assertEquals(0L, budgets.overall!!.spent)
        assertEquals(100_000L, budgets.overall!!.remaining)
    }

    @Test
    fun `scenario 6 - export, reset, import restores an identical ledger`() = runDb {
        val bank = newAccount("Bank", 123_456)
        val food = category("Food & dining")
        val salary = category("Salary")
        add(
            TransactionType.Expense,
            4_550,
            accountId = bank.id!!,
            categoryId = food.id,
            payee = "Café \"Madras\", Ltd",
            note = "=cmd",
        )
        add(TransactionType.Income, 990_000, accountId = bank.id!!, categoryId = salary.id)
        add(TransactionType.Transfer, 10_000, accountId = bank.id!!, toAccountId = cash().id)
        repo.setBudget(categoryId = food.id, from = september, amount = 20_000)
        repo.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense,
                amount = 49_900,
                accountId = bank.id!!,
                categoryId = food.id,
                frequency = RecurrenceFrequency.Monthly,
                startDate = dt(2026, 10, 1),
                nextDate = dt(2026, 10, 1),
                createdAt = now,
                updatedAt = now,
            ),
        )
        repo.setDefaultAccountId(bank.id)

        val balancesBefore = repo.accountBalances()
        val summaryBefore = repo.summary(september.range)
        val json = FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now)

        repo.resetAll()
        assertTrue(repo.snapshot().transactions.isEmpty())

        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.errors.isEmpty(), read.errors.toString())
        repo.restore(read.snapshot!!)

        assertEquals(balancesBefore, repo.accountBalances())
        val summaryAfter = repo.summary(september.range)
        assertEquals(summaryBefore.income, summaryAfter.income)
        assertEquals(summaryBefore.expense, summaryAfter.expense)
        assertEquals(bank.id, repo.defaultAccountId())
        assertEquals(49_900L, repo.recurringRules().single().amount)
        assertEquals(20_000L, repo.budgetOverview(september).categories[food.id]!!.allocated)
        val restored = repo.snapshot()
        assertTrue(restored.transactions.map { it.payee }.contains("Café \"Madras\", Ltd"))

        // Importing the same file again replaces rather than duplicates.
        repo.restore(FinanceBackupCodec.decode(json).snapshot!!)
        assertEquals(3, repo.snapshot().transactions.size)
    }

    @Test
    fun `rejects impossible transactions`() = runDb {
        val cashId = cash().id!!
        val food = category("Food & dining")
        val salary = category("Salary")

        suspend fun expectInvalid(op: suspend () -> Unit) {
            try {
                op()
                fail("expected a FinanceValidationException")
            } catch (_: FinanceValidationException) {
            }
        }

        expectInvalid { add(TransactionType.Expense, 0, accountId = cashId, categoryId = food.id) }
        expectInvalid { add(TransactionType.Expense, 100, accountId = 999, categoryId = food.id) }
        expectInvalid { add(TransactionType.Expense, 100, accountId = cashId) }
        expectInvalid { add(TransactionType.Expense, 100, accountId = cashId, categoryId = salary.id) }
        expectInvalid { add(TransactionType.Transfer, 100, accountId = cashId, toAccountId = cashId) }
        val bank = newAccount("Bank")
        expectInvalid {
            add(TransactionType.Transfer, 100, accountId = cashId, toAccountId = bank.id, categoryId = food.id)
        }
        assertTrue(repo.snapshot().transactions.isEmpty())
    }

    @Test
    fun `accounts and categories with history cannot be deleted`() = runDb {
        val bank = newAccount("Bank")
        val food = category("Food & dining")
        add(TransactionType.Expense, 100, accountId = bank.id!!, categoryId = food.id)

        try {
            repo.deleteAccount(bank.id!!)
            fail("expected a conflict")
        } catch (_: FinanceConflictException) {
        }
        try {
            repo.deleteCategory(food.id!!)
            fail("expected a conflict")
        } catch (_: FinanceConflictException) {
        }

        // Archiving keeps history intact.
        repo.setAccountArchived(bank.id!!, archived = true)
        assertEquals(-100L, repo.accountBalances()[bank.id])

        // Unused ones can be deleted.
        val unused = newAccount("Old wallet")
        repo.deleteAccount(unused.id!!)
        assertFalse(repo.accounts().any { it.id == unused.id })
    }

    @Test
    fun `currency can only change freely between same-precision currencies`() = runDb {
        repo.setCurrency("USD")
        assertEquals("USD", repo.currencyCode())
        val food = category("Food & dining")
        add(TransactionType.Expense, 100, accountId = cash().id!!, categoryId = food.id)
        try {
            repo.setCurrency("JPY")
            fail("expected a conflict")
        } catch (_: FinanceConflictException) {
        }
        repo.setCurrency("INR")
        assertEquals("INR", repo.currencyCode())
    }

    @Test
    fun `filters and keyset pagination return every row exactly once`() = runDb {
        val cashId = cash().id!!
        val bank = newAccount("Bank")
        val food = category("Food & dining")
        val transport = category("Transport")
        for (i in 0 until 25) {
            add(
                TransactionType.Expense,
                100L * (i + 1),
                accountId = if (i % 2 == 0) cashId else bank.id!!,
                categoryId = if (i % 3 == 0) transport.id else food.id,
                // Several share a timestamp to exercise the id tiebreaker.
                at = dt(2026, 9, 1 + i / 3, 10),
                payee = if (i == 7) "Metro 50% off_card" else null,
            )
        }

        val seen = mutableListOf<Long>()
        var cursor: TransactionCursor? = null
        do {
            val page = repo.transactions(TransactionQuery(), after = cursor, limit = 10)
            seen += page.items.map { it.id!! }
            cursor = page.next
        } while (cursor != null)
        assertEquals(25, seen.size)
        assertEquals(25, seen.toSet().size)

        assertEquals(12, repo.transactions(TransactionQuery(accountIds = setOf(bank.id!!))).items.size)
        assertEquals(9, repo.transactions(TransactionQuery(categoryIds = setOf(transport.id!!))).items.size)

        val byAmount = repo.transactions(TransactionQuery(minAmount = 1000, maxAmount = 1500))
        assertEquals(setOf(1000L, 1100L, 1200L, 1300L, 1400L, 1500L), byAmount.items.map { it.amount }.toSet())

        // LIKE wildcards in the search text are matched literally.
        assertEquals("Metro 50% off_card", repo.transactions(TransactionQuery(text = "50%")).items.single().payee)
        assertEquals(9, repo.transactions(TransactionQuery(text = "transport")).items.size)

        val range = repo.transactions(TransactionQuery(range = DateRange(dt(2026, 9, 1), dt(2026, 9, 2))))
        assertEquals(3, range.items.size)
    }

    @Test
    fun `monthly and daily totals exclude transfers`() = runDb {
        val cashId = cash().id!!
        val bank = newAccount("Bank")
        val food = category("Food & dining")
        val salary = category("Salary")
        add(TransactionType.Expense, 300, accountId = cashId, categoryId = food.id, at = dt(2026, 8, 31, 23, 59))
        add(TransactionType.Expense, 200, accountId = cashId, categoryId = food.id, at = dt(2026, 9, 1, 0, 0))
        add(TransactionType.Income, 1000, accountId = cashId, categoryId = salary.id, at = dt(2026, 9, 3))
        add(TransactionType.Transfer, 5000, accountId = cashId, toAccountId = bank.id, at = dt(2026, 9, 3))

        val months = repo.monthlyTotals(MonthKey(2026, 7), september)
        assertEquals(listOf(0L, 300L, 200L), months.map { it.expense })
        assertEquals(listOf(0L, 0L, 1000L), months.map { it.income })

        val days = repo.dailyTotals(september.range, TransactionType.Expense)
        assertEquals(LocalDate.of(2026, 9, 1), days.single().day)
        assertEquals(200L, days.single().amount)
    }

    @Test
    fun `changing a budget keeps earlier months in history`() = runDb {
        val food = category("Food & dining")
        repo.setBudget(from = MonthKey(2026, 7), amount = 100_000)
        repo.setBudget(from = september, amount = 150_000)
        add(TransactionType.Expense, 120_000, accountId = cash().id!!, categoryId = food.id, at = dt(2026, 8, 10))

        val history = repo.budgetHistory(until = september)
        assertEquals(listOf(september, MonthKey(2026, 8), MonthKey(2026, 7)), history.map { it.month })
        val august = history[1].status
        assertEquals(100_000L, august.allocated)
        assertEquals(120_000L, august.spent)
        assertEquals(BudgetHealth.Exceeded, august.health)
        assertEquals(150_000L, history.first().status.allocated)

        // Clearing from October on doesn't touch September.
        repo.setBudget(from = MonthKey(2026, 10), amount = null)
        assertFalse(repo.budgetOverview(MonthKey(2026, 10)).hasAnyBudget)
        assertEquals(150_000L, repo.budgetOverview(september).overall!!.allocated)
    }

    @Test
    fun `budgets are only for expense categories`() = runDb {
        val salary = category("Salary")
        assertFailsWith<FinanceValidationException> {
            repo.setBudget(categoryId = salary.id, from = september, amount = 100)
        }
    }

    @Test
    fun `recurring catches up due occurrences once and is idempotent`() = runDb {
        val cashId = cash().id!!
        val bills = category("Bills & utilities")
        repo.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense,
                amount = 99_900,
                accountId = cashId,
                categoryId = bills.id,
                payee = "Internet",
                frequency = RecurrenceFrequency.Monthly,
                startDate = dt(2026, 7, 5, 9),
                nextDate = dt(2026, 7, 5, 9),
                createdAt = now,
                updatedAt = now,
            ),
        )

        assertEquals(3, repo.processRecurring()) // Jul 5, Aug 5, Sep 5
        assertEquals(0, repo.processRecurring())
        val rule = repo.recurringRules().single()
        assertEquals(dt(2026, 10, 5, 9), rule.nextDate)
        assertEquals(-299_700L, repo.accountBalances()[cashId])

        now = dt(2026, 10, 5, 9)
        assertEquals(1, repo.processRecurring())

        // Deleting the rule keeps the history it created.
        repo.deleteRecurringRule(rule.id!!)
        val history = repo.transactions(TransactionQuery())
        assertEquals(4, history.items.size)
        assertTrue(history.items.all { it.recurringId == null })
    }

    @Test
    fun `paused recurring rules record nothing`() = runDb {
        val bills = category("Bills & utilities")
        repo.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense,
                amount = 100,
                accountId = cash().id!!,
                categoryId = bills.id,
                frequency = RecurrenceFrequency.Daily,
                startDate = dt(2026, 9, 1),
                nextDate = dt(2026, 9, 1),
                active = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertEquals(0, repo.processRecurring())
    }

    @Test
    fun `backup validation rejects broken files without touching data`() = runDb {
        val food = category("Food & dining")
        add(TransactionType.Expense, 100, accountId = cash().id!!, categoryId = food.id)
        assertFalse(FinanceBackupCodec.decode("not json").isValid)
        assertFalse(FinanceBackupCodec.decode("{\"format\":\"other\"}").isValid)
        assertTrue(
            FinanceBackupCodec.decode("{\"format\":\"normpix-finance-backup\",\"version\":99}")
                .errors.single().contains("newer version"),
        )

        val good = FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now)
        val broken = good.replaceFirst("\"amount\": 100", "\"amount\": -5")
        val result = FinanceBackupCodec.decode(broken)
        assertFalse(result.isValid)
        assertTrue(result.errors.first().contains("zero or negative"))
        assertEquals(1, repo.snapshot().transactions.size)
    }

    @Test
    fun `backup rejects wrongly typed fields`() = runDb {
        val food = category("Food & dining")
        add(TransactionType.Expense, 100, accountId = cash().id!!, categoryId = food.id)
        val good = FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now)
        // A string where a number belongs, and a fractional "integer".
        assertFalse(FinanceBackupCodec.decode(good.replaceFirst("\"amount\": 100", "\"amount\": \"100\"")).isValid)
        assertFalse(FinanceBackupCodec.decode(good.replaceFirst("\"amount\": 100", "\"amount\": 100.5")).isValid)
        assertNotNull(FinanceBackupCodec.decode(good).snapshot)
    }

    @Test
    fun `CSV export escapes text and neutralizes formulas`() = runDb {
        val food = category("Food & dining")
        add(
            TransactionType.Expense,
            4_550,
            accountId = cash().id!!,
            categoryId = food.id,
            payee = "Café \"Madras\", Ltd",
            note = "=HYPERLINK(\"x\")",
        )
        val csv = FinanceBackupCodec.encodeCsv(repo.snapshot()) { it.toString() }
        val line = csv.trim().split('\n').last()
        assertTrue(line.contains("45.50"), line)
        assertTrue(line.contains("\"Café \"\"Madras\"\", Ltd\""), line)
        assertTrue(line.contains("'=HYPERLINK(\"\"x\"\")"), line)
    }

    @Test
    fun `wall clock times survive a round trip regardless of time zone`() = runDb {
        val previous = java.util.TimeZone.getDefault()
        try {
            val food = category("Food & dining")
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
            val saved = add(
                TransactionType.Expense,
                100,
                accountId = cash().id!!,
                categoryId = food.id,
                at = dt(2026, 9, 30, 23, 30),
            )
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Auckland"))
            assertEquals(dt(2026, 9, 30, 23, 30), repo.transactionById(saved.id!!)!!.occurredAt)
            // Still counted in September, not shifted into October.
            assertEquals(100L, repo.summary(september.range).expense)
        } finally {
            java.util.TimeZone.setDefault(previous)
        }
    }
}
