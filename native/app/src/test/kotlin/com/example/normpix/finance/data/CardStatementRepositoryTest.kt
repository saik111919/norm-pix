package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.BillKind
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.StatementFigures
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.cardBill
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the ledger tells about a card at its last statement: what was owed on the day it was made up (the day itself counts), what
 * has been put onto it since - payments and refunds, not spending - and nothing dated after today.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardStatementRepositoryTest {
    private val now = LocalDateTime.of(2026, 11, 12, 12, 0)
    private val today = LocalDate.of(2026, 11, 12)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private suspend fun account(name: String, kind: AccountKind, opening: Long, statementDay: Int? = null, dueDay: Int? = null) = repo.saveAccount(
        Account(name = name, kind = kind, openingBalance = opening, statementDay = statementDay, dueDay = dueDay, createdAt = now, updatedAt = now),
    )

    private suspend fun entry(type: TransactionType, amount: Long, on: Long, at: LocalDateTime, to: Long? = null) {
        val category = when (type) {
            TransactionType.Expense -> repo.categories().first { it.name == "Shopping" }.id
            TransactionType.Income -> repo.categories().first { it.type == CategoryType.Income }.id
            TransactionType.Transfer -> null
        }
        repo.saveTransaction(
            FinanceTransaction(type = type, amount = amount, accountId = on, toAccountId = to, categoryId = category, occurredAt = at, createdAt = now, updatedAt = now),
        )
    }

    /** Statement on the 5th, due on the 20th; owed 1,000 to begin with. Today is 12 November. */
    private suspend fun scenario(): Pair<Account, Account> {
        val bank = account("Savings", AccountKind.Bank, opening = 5_000_000)
        val card = account("Sapphire", AccountKind.Card, opening = -100_000, statementDay = 5, dueDay = 20)
        val id = card.id!!
        entry(TransactionType.Expense, 200_000, id, LocalDateTime.of(2026, 10, 20, 10, 0)) // before the statement
        entry(TransactionType.Expense, 50_000, id, LocalDateTime.of(2026, 11, 3, 10, 0)) // before the statement
        entry(TransactionType.Expense, 25_000, id, LocalDateTime.of(2026, 11, 5, 10, 0)) // on the statement day: in it
        entry(TransactionType.Expense, 40_000, id, LocalDateTime.of(2026, 11, 8, 10, 0)) // after: the next statement's
        entry(TransactionType.Transfer, 150_000, bank.id!!, LocalDateTime.of(2026, 11, 9, 10, 0), to = id) // a payment
        entry(TransactionType.Income, 10_000, id, LocalDateTime.of(2026, 11, 10, 10, 0)) // a refund
        entry(TransactionType.Expense, 99_900, id, LocalDateTime.of(2026, 11, 15, 10, 0)) // ahead: not yet
        return bank to card
    }

    @Test
    fun `money that moved through an account counts from the start of the range up to, not including, its end`() = runBlocking<Unit> {
        val (bank, card) = scenario()
        val id = card.id!!
        val flows = repo.accountFlowsBetween(id, LocalDateTime.of(2026, 11, 5, 10, 0), LocalDateTime.of(2026, 11, 9, 10, 0))
        // 5 Nov 10:00 is in, 9 Nov 10:00 (the payment) is not.
        assertEquals(25_000 + 40_000, flows.expense)
        assertEquals(0, flows.transferIn)
        val withPayment = repo.accountFlowsBetween(id, LocalDateTime.of(2026, 11, 5, 10, 0), LocalDateTime.of(2026, 11, 9, 10, 1))
        assertEquals(150_000, withPayment.transferIn)
        assertEquals(150_000, repo.accountFlowsBetween(bank.id!!, LocalDateTime.of(2026, 11, 9, 0, 0), LocalDateTime.of(2026, 11, 10, 0, 0)).transferOut)
        assertEquals(0, repo.accountFlowsBetween(id, LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 0, 0)).expense, "an empty range")
    }

    @Test
    fun `a card's figures are what was owed on its statement day and what was paid in since`() = runBlocking<Unit> {
        val (_, card) = scenario()
        val figures = repo.cardStatementFigures(listOf(card), today).getValue(card.id!!)
        assertEquals(LocalDate.of(2026, 11, 5), figures.statementDate)
        // 1,000 + 2,000 + 500 + 250 owed at the end of the 5th: spending on the day counts, later spending does not.
        assertEquals(375_000, figures.owedAtStatement)
        // The payment (1,500) and the refund (100): put onto the card. The entry dated the 15th is not yet.
        assertEquals(160_000, figures.paidSince)
    }

    @Test
    fun `the bill is what is unpaid of the statement, not everything on the card`() = runBlocking<Unit> {
        val (_, card) = scenario()
        val figures = repo.cardStatementFigures(listOf(card), today)
        val balance = repo.accountBalances(asOf = today.plusDays(1).atStartOfDay()).getValue(card.id!!)
        // Owed now: 3,750 + 400 spent since - 1,500 paid - 100 refunded = 2,550.
        assertEquals(-255_000, balance)
        val bill = cardBill(card.statementDay, card.dueDay, balance, figures[card.id], today)!!
        assertEquals(BillKind.Statement, bill.kind)
        assertEquals(375_000 - 160_000, bill.amount, "3,750 billed less the 1,600 paid in since")
        assertEquals(255_000, bill.owedNow)
        assertEquals(LocalDate.of(2026, 11, 20), bill.dueDate)
        assertEquals(8, bill.daysAway)
    }

    @Test
    fun `a card with no statement day has no figures, and a card that is not given is left out`() = runBlocking<Unit> {
        val plain = account("Plain", AccountKind.Card, opening = -50_000, dueDay = 20)
        val figures = repo.cardStatementFigures(listOf(plain), today)
        assertNull(figures[plain.id])
        assertEquals(emptyMap<Long, StatementFigures>(), repo.cardStatementFigures(emptyList(), today))
    }

    @Test
    fun `two cards on different statement days are each read at their own`() = runBlocking<Unit> {
        val first = account("First", AccountKind.Card, opening = -100_000, statementDay = 5, dueDay = 20)
        val second = account("Second", AccountKind.Card, opening = -200_000, statementDay = 10, dueDay = 28)
        entry(TransactionType.Expense, 30_000, first.id!!, LocalDateTime.of(2026, 11, 7, 10, 0)) // after First's statement, before Second's
        val figures = repo.cardStatementFigures(listOf(first, second), today)
        assertEquals(LocalDate.of(2026, 11, 5), figures.getValue(first.id!!).statementDate)
        assertEquals(100_000, figures.getValue(first.id!!).owedAtStatement)
        assertEquals(LocalDate.of(2026, 11, 10), figures.getValue(second.id!!).statementDate)
        assertEquals(200_000, figures.getValue(second.id!!).owedAtStatement)
    }
}
