package com.example.normpix.finance.domain

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "As of today" (an entry dated ahead counts from its own day) and the check that Settings > Data & backup runs. */
class LedgerCheckTest {
    private val now = LocalDateTime.of(2026, 10, 26, 13, 9)
    private val at = LocalDateTime.of(2026, 1, 1, 0, 0)

    private val bank = Account(id = 1, name = "HDFC Salary", kind = AccountKind.Bank, createdAt = at, updatedAt = at)
    private val savings = Account(id = 2, name = "SBI Savings", kind = AccountKind.Bank, createdAt = at, updatedAt = at)
    private val cash = Account(id = 3, name = "Cash", kind = AccountKind.Cash, createdAt = at, updatedAt = at)
    private val card = Account(id = 4, name = "HDFC Millennia", kind = AccountKind.Card, createdAt = at, updatedAt = at, numberEnding = "4455")
    private val accounts = listOf(bank, savings, cash, card)

    private fun category(id: Long, name: String, type: CategoryType = CategoryType.Expense, parent: Long? = null, archived: Boolean = false) =
        Category(id = id, name = name, type = type, icon = "other", parentId = parent, archived = archived, createdAt = at, updatedAt = at)

    private val food = category(10, "Food")
    private val salary = category(20, "Salary", CategoryType.Income)
    private val categories = listOf(food, salary)

    private var nextId = 100L
    private fun tx(
        type: TransactionType,
        amount: Long,
        account: Long,
        on: LocalDateTime,
        category: Long? = if (type == TransactionType.Income) salary.id else food.id,
        payee: String? = null,
        to: Long? = null,
        recurring: Long? = null,
    ) = FinanceTransaction(
        id = nextId++, type = type, amount = amount, accountId = account, toAccountId = to, categoryId = if (type == TransactionType.Transfer) null else category,
        occurredAt = on, payee = payee, recurringId = recurring, createdAt = at, updatedAt = at,
    )

    private fun check(
        transactions: List<FinanceTransaction> = emptyList(),
        categories: List<Category> = this.categories,
        rules: List<RecurringRule> = emptyList(),
        budgets: List<Budget> = emptyList(),
        balances: Map<Long, Long> = emptyMap(),
        accounts: List<Account> = this.accounts,
        defaultAccountId: Long? = null,
    ) = checkLedger(accounts, categories, transactions, rules, budgets, balances, defaultAccountId, now)

    // ------------------------------------------------------------- as of today

    @Test
    fun `today counts whole and what is dated after it is upcoming`() {
        val cutoff = settledCutoff(now)
        assertEquals(LocalDateTime.of(2026, 10, 27, 0, 0), cutoff)
        val october = MonthKey(2026, 10)
        assertEquals(DateRange(october.start, cutoff), october.range.settledBy(cutoff), "October so far: through the end of today")
        assertEquals(MonthKey(2026, 9).range, MonthKey(2026, 9).range.settledBy(cutoff), "a month that is over is all of it")
        assertTrue(MonthKey(2026, 11).range.settledBy(cutoff).isEmpty, "a month still to come has nothing in it yet")
        assertEquals(DateRange(LocalDate.of(2026, 10, 26).atStartOfDay(), cutoff), dayRange(LocalDate.of(2026, 10, 26)))
    }

    // ------------------------------------------------------------ put right

    @Test
    fun `a sub-category whose parent is gone, archived or of the other kind stands on its own`() {
        val gone = category(11, "Snacks", parent = 99)
        val underIncome = category(12, "Tips", parent = salary.id)
        val underArchived = category(13, "Pizza", parent = 14)
        val archivedParent = category(14, "Takeaway", archived = true)
        val fine = category(15, "Groceries", parent = food.id)
        val issues = check(categories = categories + listOf(gone, underIncome, underArchived, archivedParent, fine))
        assertEquals(listOf(gone, underIncome, underArchived), issues.looseSubCategories.map { it.category })
        assertTrue("gone" in issues.looseSubCategories.first().reason)
    }

    @Test
    fun `a budget on an income category is removed, one on spending stays`() {
        val onIncome = Budget(id = 1, categoryId = salary.id, amount = 5_000, effectiveMonth = MonthKey(2026, 10))
        val onFood = Budget(id = 2, categoryId = food.id, amount = 5_000, effectiveMonth = MonthKey(2026, 10))
        val overall = Budget(id = 3, categoryId = null, amount = 50_000, effectiveMonth = MonthKey(2026, 10))
        assertEquals(listOf(onIncome), check(budgets = listOf(onIncome, onFood, overall)).incomeBudgets)
    }

    @Test
    fun `a repeating payment behind what it has recorded moves on past it, never to record a day twice`() {
        val start = LocalDateTime.of(2026, 6, 5, 9, 0)
        val rule = RecurringRule(
            id = 7, type = TransactionType.Expense, amount = 64_900, accountId = bank.id!!, categoryId = food.id, frequency = RecurrenceFrequency.Monthly,
            startDate = start, nextDate = LocalDateTime.of(2026, 9, 5, 9, 0), createdAt = at, updatedAt = at,
        )
        val recorded = listOf(6, 7, 8, 9, 10).map { m -> tx(TransactionType.Expense, 64_900, bank.id!!, LocalDateTime.of(2026, m, 5, 9, 0), recurring = 7) }
        val catchUp = check(recorded, rules = listOf(rule)).ruleCatchUps.single()
        assertEquals(LocalDateTime.of(2026, 11, 5, 9, 0), catchUp.nextDate)
        // In step: nothing to do.
        assertTrue(check(recorded, rules = listOf(rule.copy(nextDate = LocalDateTime.of(2026, 11, 5, 9, 0)))).ruleCatchUps.isEmpty())
        // Paused: left as it is.
        assertTrue(check(recorded, rules = listOf(rule.copy(active = false))).ruleCatchUps.isEmpty())
    }

    @Test
    fun `a default account that is archived or gone is no longer the default`() {
        assertTrue(check(defaultAccountId = 99).staleDefaultAccount)
        assertTrue(check(accounts = accounts.map { if (it.id == bank.id) it.copy(archived = true) else it }, defaultAccountId = bank.id).staleDefaultAccount)
        assertFalse(check(defaultAccountId = bank.id).staleDefaultAccount)
        assertFalse(check().staleDefaultAccount)
    }

    // ------------------------------------------------------------ to look at

    @Test
    fun `entries dated after today are pointed out, soonest first`() {
        val later = tx(TransactionType.Expense, 1_000, bank.id!!, LocalDateTime.of(2026, 10, 30, 9, 0))
        val tomorrow = tx(TransactionType.Expense, 2_000, bank.id!!, LocalDateTime.of(2026, 10, 27, 8, 0))
        val tonight = tx(TransactionType.Expense, 3_000, bank.id!!, LocalDateTime.of(2026, 10, 26, 23, 30))
        assertEquals(listOf(tomorrow, later), check(listOf(later, tomorrow, tonight)).upcoming, "tonight is today's")
    }

    @Test
    fun `the same payment entered twice on a day is pointed out, two different ones are not`() {
        val day = LocalDateTime.of(2026, 10, 20, 12, 0)
        val first = tx(TransactionType.Expense, 25_000, bank.id!!, day, payee = "Swiggy")
        val again = tx(TransactionType.Expense, 25_000, bank.id!!, day.plusHours(3), payee = "swiggy ")
        val otherDay = tx(TransactionType.Expense, 25_000, bank.id!!, day.plusDays(1), payee = "Swiggy")
        val noPayee = tx(TransactionType.Expense, 9_000, bank.id!!, day)
        val noPayeeLater = tx(TransactionType.Expense, 9_000, bank.id!!, day.plusMinutes(40))
        assertEquals(listOf(listOf(first, again)), check(listOf(first, again, otherDay, noPayee, noPayeeLater)).duplicates)
    }

    @Test
    fun `an expense and the same income into another account within a day look like one transfer`() {
        val out = tx(TransactionType.Expense, 500_000, bank.id!!, LocalDateTime.of(2026, 10, 24, 10, 0))
        val into = tx(TransactionType.Income, 500_000, savings.id!!, LocalDateTime.of(2026, 10, 24, 10, 2))
        val pair = check(listOf(out, into)).selfTransfers.single()
        assertEquals(out, pair.expense)
        assertEquals(into, pair.income)

        // Not: cash, the same account, two days apart, or a repeating payment's entry.
        val cashOut = tx(TransactionType.Expense, 500_000, cash.id!!, LocalDateTime.of(2026, 10, 24, 10, 0))
        assertTrue(check(listOf(cashOut, into)).selfTransfers.isEmpty())
        val sameAccount = tx(TransactionType.Income, 500_000, bank.id!!, LocalDateTime.of(2026, 10, 24, 10, 2))
        assertTrue(check(listOf(out, sameAccount)).selfTransfers.isEmpty())
        val muchLater = tx(TransactionType.Income, 500_000, savings.id!!, LocalDateTime.of(2026, 10, 26, 10, 2))
        assertTrue(check(listOf(out, muchLater)).selfTransfers.isEmpty())
        val salaryIn = tx(TransactionType.Income, 500_000, savings.id!!, LocalDateTime.of(2026, 10, 24, 10, 2), recurring = 3)
        assertTrue(check(listOf(out, salaryIn)).selfTransfers.isEmpty())
    }

    @Test
    fun `the one transfer it becomes goes out of the expense's account, into the income's, counted as neither`() {
        val out = tx(TransactionType.Expense, 500_000, bank.id!!, LocalDateTime.of(2026, 10, 24, 10, 0), payee = "Rahul Mehta")
        val into = tx(TransactionType.Income, 500_000, savings.id!!, LocalDateTime.of(2026, 10, 24, 10, 2))
        val transfer = SelfTransferPair(out, into).asTransfer(now)
        assertEquals(TransactionType.Transfer, transfer.type)
        assertEquals(bank.id, transfer.accountId)
        assertEquals(savings.id, transfer.toAccountId)
        assertEquals(null, transfer.categoryId)
        assertEquals(null, transfer.payee)
        assertEquals("Self transfer · Rahul Mehta", transfer.note)
        assertEquals(out.id, transfer.id, "the expense's own entry, so its picture and place stay")
    }

    @Test
    fun `a card's bill recorded as spending is pointed out with its card`() {
        val bill = tx(TransactionType.Expense, 1_200_000, bank.id!!, LocalDateTime.of(2026, 10, 15, 9, 0), payee = "HDFC Millennia credit card bill")
        val shopping = tx(TransactionType.Expense, 2_000, card.id!!, LocalDateTime.of(2026, 10, 15, 9, 0), payee = "Amazon")
        val found = check(listOf(bill, shopping)).cardBills.single()
        assertEquals(bill, found.expense)
        assertEquals(card, found.card)
        val payment = found.asTransfer(now)
        assertEquals(TransactionType.Transfer, payment.type)
        assertEquals(card.id, payment.toAccountId)
    }

    @Test
    fun `an archived account still holding money, and an entry under a category of the other kind`() {
        val closed = savings.copy(archived = true)
        val held = check(accounts = listOf(bank, closed, cash, card), balances = mapOf(savings.id!! to 120_000L, bank.id!! to 5_000L)).archivedBalances.single()
        assertEquals(closed, held.account)
        assertEquals(120_000L, held.balance)

        val misfiled = tx(TransactionType.Expense, 5_000, bank.id!!, LocalDateTime.of(2026, 10, 2, 9, 0), category = salary.id)
        assertEquals(listOf(misfiled), check(listOf(misfiled)).wrongCategory)
    }

    @Test
    fun `a ledger that holds together has nothing to put right or look at`() {
        val issues = check(
            listOf(
                tx(TransactionType.Income, 5_000_000, bank.id!!, LocalDateTime.of(2026, 10, 1, 9, 0)),
                tx(TransactionType.Expense, 25_000, bank.id!!, LocalDateTime.of(2026, 10, 2, 9, 0), payee = "Swiggy"),
                tx(TransactionType.Transfer, 100_000, bank.id!!, LocalDateTime.of(2026, 10, 3, 9, 0), to = savings.id),
            ),
            balances = mapOf(bank.id!! to 4_875_000L, savings.id!! to 100_000L),
            defaultAccountId = bank.id,
        )
        assertEquals(0, issues.repairCount)
        assertEquals(0, issues.findingCount)
    }
}
