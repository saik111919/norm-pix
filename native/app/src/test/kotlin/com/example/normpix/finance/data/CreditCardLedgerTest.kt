package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.FinanceState
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A credit card kept the way the app keeps one: an account whose balance is negative while a bill is owed, paid from the
 * bank it belongs to by a transfer. The numbers below are in minor units (paise): 1_000_00 is Rs 1,000.00.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreditCardLedgerTest {
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

    private fun run(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun account(name: String, kind: AccountKind, opening: Long) = repo.saveAccount(
        Account(name = name, kind = kind, openingBalance = opening, createdAt = now, updatedAt = now),
    )

    private suspend fun spendOn(accountId: Long, amount: Long) = repo.saveTransaction(
        FinanceTransaction(
            type = TransactionType.Expense,
            amount = amount,
            accountId = accountId,
            categoryId = repo.categories().first { it.name == "Shopping" }.id,
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private suspend fun pay(from: Long, to: Long, amount: Long) = repo.saveTransaction(
        FinanceTransaction(
            type = TransactionType.Transfer,
            amount = amount,
            accountId = from,
            toAccountId = to,
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
        ),
    )

    private suspend fun total() = repo.accountBalances().values.sum()

    @Test
    fun `a card that owes, spent on and paid from the bank - the bill falls, the bank falls, nothing is counted twice`() = run {
        val bank = account("ICICI Savings", AccountKind.Bank, opening = 50_000_00)
        // The "I owe this amount" switch on the account form: the starting balance is entered as a debt.
        val card = account("ICICI Credit Card", AccountKind.Card, opening = -10_000_00)
        repo.setBudget(from = september, amount = 30_000_00)
        assertEquals(40_000_00L, total(), "what is held minus what is owed")

        // A purchase on the card: the bill grows, the bank is untouched, and it is spending.
        spendOn(card.id!!, 2_000_00)
        assertEquals(-12_000_00L, repo.accountBalances()[card.id])
        assertEquals(50_000_00L, repo.accountBalances()[bank.id])
        assertEquals(38_000_00L, total())
        assertEquals(2_000_00L, repo.summary(september.range).expense)

        // Part of the bill paid from the bank: both move, the total does not, and it is not spending.
        pay(bank.id!!, card.id, 5_000_00)
        assertEquals(-7_000_00L, repo.accountBalances()[card.id])
        assertEquals(45_000_00L, repo.accountBalances()[bank.id])
        assertEquals(38_000_00L, total(), "paying a bill moves money, it does not make or lose any")
        assertEquals(2_000_00L, repo.summary(september.range).expense, "the payment is not an expense")
        assertEquals(0L, repo.summary(september.range).income, "nor income on the card's side")
        assertEquals(2_000_00L, repo.budgetOverview(september).overall!!.spent, "nor against the budget")

        // The rest: the card is square.
        pay(bank.id, card.id, 7_000_00)
        assertEquals(0L, repo.accountBalances()[card.id])
        assertEquals(38_000_00L, repo.accountBalances()[bank.id])
        assertEquals(38_000_00L, total())
    }

    @Test
    fun `paying more than the bill leaves a credit balance on the card, and it counts as money held`() = run {
        val bank = account("HDFC Savings", AccountKind.Bank, opening = 20_000_00)
        val card = account("HDFC Credit Card", AccountKind.Card, opening = -3_000_00)

        pay(bank.id!!, card.id!!, 4_500_00)
        assertEquals(1_500_00L, repo.accountBalances()[card.id], "overpaid: the card owes the difference back")
        assertEquals(15_500_00L, repo.accountBalances()[bank.id])
        assertEquals(17_000_00L, total(), "20,000 - 3,000: the credit balance is held, not owed")

        // New spending eats the credit before it becomes a bill again.
        spendOn(card.id, 1_000_00)
        assertEquals(500_00L, repo.accountBalances()[card.id])
        spendOn(card.id, 1_000_00)
        assertEquals(-500_00L, repo.accountBalances()[card.id], "and past it, the card is owed on again")
        assertEquals(15_000_00L, total())
    }

    @Test
    fun `a bill paid at the same moment from two places adds up`() = run {
        val bank = account("Axis Savings", AccountKind.Bank, opening = 10_000_00)
        val wallet = account("Paytm Wallet", AccountKind.Wallet, opening = 1_000_00)
        val card = account("Axis Credit Card", AccountKind.Card, opening = -6_000_00)

        pay(bank.id!!, card.id!!, 4_000_00)
        pay(wallet.id!!, card.id, 1_000_00)
        assertEquals(-1_000_00L, repo.accountBalances()[card.id])
        assertEquals(6_000_00L, repo.accountBalances()[bank.id])
        assertEquals(0L, repo.accountBalances()[wallet.id])
        assertEquals(5_000_00L, total())
    }

    @Test
    fun `the total is what is held minus what is owed`() {
        val state = FinanceState(balances = mapOf(1L to 38_000_00L, 2L to -12_000_00L, 3L to 1_500_00L, 4L to 0L))
        assertEquals(39_500_00L, state.heldBalance)
        assertEquals(12_000_00L, state.owedBalance)
        assertEquals(27_500_00L, state.totalBalance)
        assertEquals(state.heldBalance - state.owedBalance, state.totalBalance)

        val clear = FinanceState(balances = mapOf(1L to 1_000_00L, 2L to 0L))
        assertEquals(0L, clear.owedBalance)
        assertEquals(clear.totalBalance, clear.heldBalance)
    }
}
