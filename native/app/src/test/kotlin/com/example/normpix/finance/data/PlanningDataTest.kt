package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.BillKind
import com.example.normpix.finance.domain.ForecastSource
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.buildForecast
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the planning screens read from the ledger: the payees dismissed as repeating payments (kept in `meta`, outliving a restore),
 * the spending the forecast reads a day's share of, the expenses the detector reads, and the facts the forecast is worked out from.
 * Today is 14 November 2026.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanningDataTest {
    private val now = LocalDateTime.of(2026, 11, 14, 12, 0)
    private val today = LocalDate.of(2026, 11, 14)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private suspend fun bank(name: String = "HDFC", opening: Long = 1_000_000) =
        repo.saveAccount(Account(name = name, kind = AccountKind.Bank, openingBalance = opening, createdAt = now, updatedAt = now))

    private suspend fun spend(
        account: Account,
        amount: Long,
        on: LocalDate,
        payee: String? = "Shop",
        recurringId: Long? = null,
        type: TransactionType = TransactionType.Expense,
        to: Long? = null,
    ): FinanceTransaction {
        val category = if (type == TransactionType.Transfer) null else repo.categories().first { it.name == "Shopping" }.id
        return repo.saveTransaction(
            FinanceTransaction(
                type = type,
                amount = amount,
                accountId = account.id!!,
                toAccountId = to,
                categoryId = category,
                occurredAt = on.atTime(18, 0),
                payee = payee,
                recurringId = recurringId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    // ------------------------------------------------------------------------------------- dismissed payees

    @Test
    fun `a dismissed payee is remembered once, whatever it is dismissed again`() = runBlocking<Unit> {
        assertEquals(emptySet(), repo.dismissedRepeatPayees())
        repo.dismissRepeatPayee("netflix")
        repo.dismissRepeatPayee("amazon prime")
        repo.dismissRepeatPayee("netflix")
        repo.dismissRepeatPayee("  ")
        assertEquals(setOf("netflix", "amazon prime"), repo.dismissedRepeatPayees())
    }

    @Test
    fun `the dismissed payees outlive a restore, as an Undo does one, but a reset forgets them`() = runBlocking<Unit> {
        repo.dismissRepeatPayee("netflix")
        val snapshot = repo.snapshot()
        repo.restore(snapshot)
        assertEquals(setOf("netflix"), repo.dismissedRepeatPayees(), "restoring the ledger leaves what was said about suggestions alone")
        repo.resetAll()
        assertEquals(emptySet(), repo.dismissedRepeatPayees())
        // A restore with none dismissed adds none.
        repo.restore(snapshot)
        assertEquals(emptySet(), repo.dismissedRepeatPayees())
    }

    // ------------------------------------------------------------------------------------------- the queries

    @Test
    fun `spending outside the repeats is expenses no rule recorded, in the window, on every account - not transfers`() = runBlocking<Unit> {
        val a = bank("A")
        val b = bank("B")
        val ruleId = repo.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense,
                amount = 1_000,
                accountId = a.id!!,
                categoryId = repo.categories().first { it.name == "Shopping" }.id,
                frequency = RecurrenceFrequency.Monthly,
                startDate = now.plusDays(30),
                nextDate = now.plusDays(30),
                createdAt = now,
                updatedAt = now,
            ),
        ).id!!
        spend(a, 100_00, today.minusDays(10))
        spend(b, 50_00, today.minusDays(3))
        spend(a, 7_00, today) // today is in
        spend(a, 900_00, today.minusDays(10), recurringId = ruleId) // recorded by a rule: not everyday spending
        spend(a, 400_00, today.minusDays(120)) // before the window
        spend(a, 200_00, today.plusDays(2)) // after it: upcoming
        spend(a, 300_00, today.minusDays(5), type = TransactionType.Transfer, to = b.id, payee = null) // moves money, spends none
        val since = today.minusDays(89).atStartOfDay()
        assertEquals(100_00 + 50_00 + 7_00, repo.spentOutsideRepeats(since, today.plusDays(1).atStartOfDay()))
        assertEquals(0, repo.spentOutsideRepeats(today.plusDays(10).atStartOfDay(), today.plusDays(20).atStartOfDay()), "nothing is none")
    }

    @Test
    fun `the expenses the detector reads have a payee and no rule behind them`() = runBlocking<Unit> {
        val a = bank()
        val ruleId = repo.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense,
                amount = 100,
                accountId = a.id!!,
                categoryId = repo.categories().first { it.name == "Shopping" }.id,
                frequency = RecurrenceFrequency.Monthly,
                startDate = now.plusDays(30),
                nextDate = now.plusDays(30),
                createdAt = now,
                updatedAt = now,
            ),
        ).id!!
        spend(a, 100, today.minusDays(10), payee = "Netflix")
        spend(a, 100, today.minusDays(9), payee = "  ")
        spend(a, 100, today.minusDays(8), payee = null)
        spend(a, 100, today.minusDays(7), payee = "Via rule", recurringId = ruleId)
        spend(a, 100, today.minusDays(400), payee = "Old")
        val read = repo.expensesOutsideRepeats(today.minusDays(100).atStartOfDay(), today.plusDays(1).atStartOfDay())
        assertEquals(listOf("Netflix"), read.map { it.payee })
    }

    @Test
    fun `what is dated in a range is read oldest first, the start in and the end out`() = runBlocking<Unit> {
        val a = bank()
        spend(a, 1, today.plusDays(3), payee = "Third")
        spend(a, 1, today.plusDays(1), payee = "First")
        spend(a, 1, today.plusDays(2), payee = "Second")
        spend(a, 1, today.plusDays(9), payee = "Beyond")
        val read = repo.transactionsBetween(today.plusDays(1).atStartOfDay(), today.plusDays(4).atStartOfDay())
        assertEquals(listOf("First", "Second", "Third"), read.map { it.payee })
    }

    // ------------------------------------------------------------------------------------- the two gathers

    @Test
    fun `payments that look like a repeating payment are found, until a rule or a dismissal takes them away`() = runBlocking<Unit> {
        val a = bank()
        for (daysAgo in listOf(95L, 65L, 35L, 5L)) spend(a, 649_00, today.minusDays(daysAgo), payee = "Netflix")
        spend(a, 1_00, today.minusDays(2), payee = "Chai") // once is no rhythm
        val found = repo.repeatSuggestions(today).single()
        assertEquals("Netflix", found.payee)
        assertEquals(RecurrenceFrequency.Monthly, found.frequency)
        assertEquals(649_00, found.amount)
        assertEquals(4, found.seen)

        repo.dismissRepeatPayee(found.key)
        assertTrue(repo.repeatSuggestions(today).isEmpty(), "not this one, for good")
    }

    @Test
    fun `a repeating payment made for the payee ends the suggestion`() = runBlocking<Unit> {
        val a = bank()
        for (daysAgo in listOf(95L, 65L, 35L, 5L)) spend(a, 649_00, today.minusDays(daysAgo), payee = "Netflix")
        assertEquals(1, repo.repeatSuggestions(today).size)
        repo.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense,
                amount = 649_00,
                accountId = a.id!!,
                categoryId = repo.categories().first { it.name == "Shopping" }.id,
                payee = "netflix",
                frequency = RecurrenceFrequency.Monthly,
                startDate = now.plusDays(25),
                nextDate = now.plusDays(25),
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertTrue(repo.repeatSuggestions(today).isEmpty())
    }

    @Test
    fun `the forecast facts carry the balances, what is dated ahead, the rules, each card's bill and the everyday spending`() = runBlocking<Unit> {
        val a = bank(opening = 2_000_000)
        val card = repo.saveAccount(
            Account(
                name = "Sapphire",
                kind = AccountKind.Card,
                openingBalance = -100_000,
                statementDay = 5,
                dueDay = 20,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val noDueDay = repo.saveAccount(Account(name = "Gift card", kind = AccountKind.Card, openingBalance = -5_000, createdAt = now, updatedAt = now))
        spend(card, 40_000, today.minusDays(2), payee = "Zara") // after the 5 Nov statement
        spend(a, 120_00, today.minusDays(1), payee = "Grocer")
        spend(a, 999_00, today.plusDays(4), payee = "Dentist") // upcoming
        val facts = repo.forecastFacts(today)

        assertEquals(today, facts.today)
        assertEquals(2_000_000 - 120_00, facts.balances.getValue(a.id!!))
        assertEquals(listOf("Dentist"), facts.upcoming.map { it.payee }, "only what is dated after today")
        // The card with a due day has its bill; the one without is not among the cards.
        val cardFacts = facts.cards.single()
        assertEquals(card.id, cardFacts.account.id)
        assertTrue(noDueDay.id !in facts.cards.map { it.account.id })
        assertEquals(140_000, cardFacts.owedNow)
        val bill = assertNotNull(cardFacts.bill)
        assertEquals(BillKind.Statement, bill.kind)
        assertEquals(100_000, bill.amount, "what was owed at the 5 Nov statement; the 400 charged since is the next one's")
        assertEquals(LocalDate.of(2026, 11, 20), bill.dueDate)
        // The 400 spent on the card and the 120 at the grocer's are everyday spending: both outside any rule.
        assertEquals(40_000 + 120_00, facts.everydayTotal)

        // ... and the forecast built from it says so.
        val forecast = buildForecast(facts, today.plusDays(60), everyday = false)
        assertEquals(
            listOf(ForecastSource.Entered, ForecastSource.CardBill, ForecastSource.CardBill),
            forecast.items.map { it.source },
        )
        assertEquals(listOf(-999_00L, -100_000L, -40_000L), forecast.items.map { it.effect })
    }
}
