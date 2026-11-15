package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CardBillExpense
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.FinancialYear
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.SelfTransferPair
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TagTotal
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.asTransfer
import com.example.normpix.finance.domain.settledCutoff
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tags in the ledger (2026-11-15): saved with their transaction in one step and read with a page in one query (not one a row), found by
 * filter (any of them) and by searching a tag's name, renamed everywhere at once, deleted from every transaction with the transactions
 * kept; a transfer takes none; and the yearly totals per tag - expenses and income apart, transfers and what is dated after today left out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagRepositoryTest {
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

    private suspend fun cash(): Long = repo.accounts().first { it.kind == AccountKind.Cash }.id!!

    private suspend fun savings(): Long = repo.accounts().firstOrNull { it.name == "Savings" }?.id
        ?: repo.saveAccount(Account(name = "Savings", kind = AccountKind.Savings, createdAt = now, updatedAt = now)).id!!

    private suspend fun categoryId(name: String): Long = repo.categories().first { it.name == name }.id!!

    private suspend fun tag(name: String): Tag = repo.saveTag(Tag(name = name, createdAt = now))

    private suspend fun draft(
        type: TransactionType,
        amount: Long,
        tags: Set<Long> = emptySet(),
        payee: String? = null,
        at: LocalDateTime = now.minusDays(1),
    ): FinanceTransaction = FinanceTransaction(
        type = type,
        amount = amount,
        accountId = cash(),
        toAccountId = if (type == TransactionType.Transfer) savings() else null,
        categoryId = when (type) {
            TransactionType.Expense -> categoryId("Health")
            TransactionType.Income -> categoryId("Salary")
            TransactionType.Transfer -> null
        },
        occurredAt = at,
        payee = payee,
        createdAt = now,
        updatedAt = now,
        tagIds = tags,
    )

    private suspend fun add(type: TransactionType, amount: Long, tags: Set<Long> = emptySet(), payee: String? = null, at: LocalDateTime = now.minusDays(1)) =
        repo.saveTransaction(draft(type, amount, tags, payee, at))

    private suspend fun ids(query: TransactionQuery): List<Long> = repo.transactions(query, limit = 100).items.map { it.id!! }

    // ------------------------------------------------------------------------------------ saving and reading

    @Test
    fun `a transaction's tags are saved with it and read back with it`() = runDb {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val saved = add(TransactionType.Expense, 45_000, setOf(tax.id!!, trip.id!!), payee = "Apollo")
        assertEquals(setOf(tax.id, trip.id), saved.tagIds)
        assertEquals(setOf(tax.id, trip.id), repo.transactionById(saved.id!!)!!.tagIds)
        assertEquals(setOf(tax.id, trip.id), repo.transactions(TransactionQuery(), limit = 10).items.single().tagIds)
        assertEquals(setOf(tax.id, trip.id), repo.snapshot().transactions.single().tagIds)

        // Editing makes the set exactly what is saved: one taken off, none added.
        repo.saveTransaction(saved.copy(tagIds = setOf(trip.id!!)))
        assertEquals(setOf(trip.id), repo.transactionById(saved.id!!)!!.tagIds)
        repo.saveTransaction(saved.copy(tagIds = emptySet()))
        assertEquals(emptySet(), repo.transactionById(saved.id!!)!!.tagIds)
    }

    @Test
    fun `a page of transactions reads its tags in one query, however long the page`() = runDb {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val batch = (1..90).map { i ->
            draft(
                TransactionType.Expense,
                1_000L + i,
                tags = when (i % 3) {
                    0 -> setOf(tax.id!!)
                    1 -> setOf(tax.id!!, trip.id!!)
                    else -> emptySet()
                },
                at = now.minusDays(1).minusMinutes(i.toLong()),
            )
        }
        repo.saveTransactions(batch)

        val before = repo.tagBatchReads
        val page = repo.transactions(TransactionQuery(), limit = 40)
        assertEquals(before + 1, repo.tagBatchReads, "one read of the tags for the whole page of 40")
        val next = repo.transactions(TransactionQuery(), after = page.next, limit = 40)
        assertEquals(before + 2, repo.tagBatchReads, "and one for the next")
        // Every row has its own tags, as saved.
        for (t in page.items + next.items) {
            val i = (t.amount - 1_000L).toInt()
            val expected = when (i % 3) {
                0 -> setOf(tax.id)
                1 -> setOf(tax.id, trip.id)
                else -> emptySet()
            }
            assertEquals(expected, t.tagIds, "transaction $i")
        }
        // A whole backup reads them in one query too.
        val beforeSnapshot = repo.tagBatchReads
        assertEquals(90, repo.snapshot().transactions.size)
        assertEquals(beforeSnapshot + 1, repo.tagBatchReads)
    }

    @Test
    fun `a batch save writes each transaction's tags, all or none`() = runDb {
        val tax = tag("Tax-deductible")
        repo.saveTransactions(listOf(draft(TransactionType.Expense, 500, setOf(tax.id!!)), draft(TransactionType.Income, 900, setOf(tax.id!!))))
        assertEquals(2, ids(TransactionQuery(tagIds = setOf(tax.id!!))).size)
        // One bad row (a transfer with a tag) takes the others out with it.
        val failure = assertFailsWith<FinanceValidationException> {
            repo.saveTransactions(listOf(draft(TransactionType.Expense, 501, setOf(tax.id!!)), draft(TransactionType.Transfer, 502, setOf(tax.id!!))))
        }
        assertEquals("A transfer can't have tags: it only moves money between your own accounts.", failure.message)
        assertEquals(2, repo.transactions(TransactionQuery(), limit = 10).items.size)
        assertEquals(2, repo.tagUsage()[tax.id])
    }

    @Test
    fun `a transfer can't have tags, and a tag must exist`() = runDb {
        val tax = tag("Tax-deductible")
        val failure = assertFailsWith<FinanceValidationException> { add(TransactionType.Transfer, 5_000, setOf(tax.id!!)) }
        assertEquals("A transfer can't have tags: it only moves money between your own accounts.", failure.message)
        assertTrue(repo.transactions(TransactionQuery(), limit = 10).items.isEmpty(), "nothing was saved")

        val unknown = assertFailsWith<FinanceValidationException> { add(TransactionType.Expense, 5_000, setOf(tax.id!!, 999)) }
        assertEquals("Choose tags from the list, or add them first.", unknown.message)
        assertTrue(repo.transactions(TransactionQuery(), limit = 10).items.isEmpty(), "not the row without its tags either")

        // An expense saved as a transfer loses its tags with the form's empty set, and is then fine.
        val expense = add(TransactionType.Expense, 5_000, setOf(tax.id!!))
        repo.saveTransaction(draft(TransactionType.Transfer, 5_000).copy(id = expense.id))
        assertEquals(emptySet(), repo.transactionById(expense.id!!)!!.tagIds)
        assertEquals(emptyMap(), repo.tagUsage())
    }

    @Test
    fun `a payment saved as the first of a repeating one keeps its tags, and what the rule records later has none`() = runDb {
        val tax = tag("Tax-deductible")
        val (saved, rule) = repo.saveTransactionAndRepeat(
            draft(TransactionType.Expense, 64_900, setOf(tax.id!!), payee = "Insurance", at = now.minusMonths(2)),
            RepeatSchedule(RecurrenceFrequency.Monthly),
        )
        assertEquals(setOf(tax.id), repo.transactionById(saved.id!!)!!.tagIds)
        repo.processRecurring()
        val recorded = repo.transactions(TransactionQuery(), limit = 10).items.filter { it.recurringId == rule.id && it.id != saved.id }
        assertTrue(recorded.isNotEmpty())
        assertTrue(recorded.all { it.tagIds.isEmpty() })
    }

    // ------------------------------------------------------------------------------------ filter and search

    @Test
    fun `the filter finds a transaction with any of the tags, once`() = runDb {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val a = add(TransactionType.Expense, 100, setOf(tax.id!!), at = now.minusDays(4))
        val b = add(TransactionType.Expense, 200, setOf(trip.id!!), at = now.minusDays(3))
        val c = add(TransactionType.Income, 300, setOf(tax.id!!, trip.id!!), at = now.minusDays(2))
        add(TransactionType.Expense, 400, at = now.minusDays(1))
        assertEquals(listOf(c.id, a.id), ids(TransactionQuery(tagIds = setOf(tax.id!!))))
        assertEquals(listOf(c.id, b.id, a.id), ids(TransactionQuery(tagIds = setOf(tax.id!!, trip.id!!))), "any of them, each once")
        assertTrue(TransactionQuery(tagIds = setOf(tax.id!!)).hasFilters)
        // With the others: a type and a tag.
        assertEquals(listOf(c.id), ids(TransactionQuery(tagIds = setOf(tax.id!!), types = setOf(TransactionType.Income))))
        val totals = repo.summaryFor(TransactionQuery(tagIds = setOf(tax.id!!)))
        assertEquals(100L, totals.expense)
        assertEquals(300L, totals.income)
    }

    @Test
    fun `searching a tag's name finds its transactions`() = runDb {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val a = add(TransactionType.Expense, 100, setOf(tax.id!!), payee = "Apollo", at = now.minusDays(3))
        val b = add(TransactionType.Expense, 200, setOf(tax.id!!, trip.id!!), payee = "Uber", at = now.minusDays(2))
        add(TransactionType.Expense, 300, payee = "Chai", at = now.minusDays(1))
        assertEquals(listOf(b.id, a.id), ids(TransactionQuery(text = "deduct")))
        assertEquals(listOf(b.id), ids(TransactionQuery(text = "GOA")))
        // A match on two of its tags is still one row.
        assertEquals(listOf(b.id), ids(TransactionQuery(text = "a", tagIds = setOf(trip.id!!))))
        assertEquals(listOf(b.id, a.id), ids(TransactionQuery(text = "-")), "a tag's own punctuation is matched as it is")
    }

    // ----------------------------------------------------------------------------------- rename and delete

    @Test
    fun `a tag's name is tidied, 1 to 30 characters, and new to the list whatever its case`() = runDb {
        assertEquals("Goa trip", tag("  Goa   trip ").name)
        assertEquals("Give the tag a name.", assertFailsWith<FinanceValidationException> { tag("   ") }.message)
        assertEquals("Keep the tag's name to 30 characters.", assertFailsWith<FinanceValidationException> { tag("x".repeat(31)) }.message)
        assertEquals(30, tag("y".repeat(30)).name.length)
        assertEquals("You already have a tag called Goa trip.", assertFailsWith<FinanceConflictException> { tag("goa TRIP") }.message)
        assertEquals(listOf("Goa trip", "y".repeat(30)), repo.tags().map { it.name })
    }

    @Test
    fun `renaming a tag renames it on every transaction, which keep it`() = runDb {
        val tax = tag("Tax")
        tag("Trip")
        val a = add(TransactionType.Expense, 100, setOf(tax.id!!))
        val renamed = repo.saveTag(tax.copy(name = "Tax-deductible"))
        assertEquals(tax.id, renamed.id)
        assertEquals(listOf("Tax-deductible", "Trip"), repo.tags().map { it.name })
        assertEquals(setOf(tax.id), repo.transactionById(a.id!!)!!.tagIds)
        assertEquals(listOf(a.id), ids(TransactionQuery(text = "deductible")))
        // Its own name in another case is fine; another's is not.
        assertEquals("TAX-deductible", repo.saveTag(renamed.copy(name = "TAX-deductible")).name)
        assertEquals("You already have a tag called Trip.", assertFailsWith<FinanceConflictException> { repo.saveTag(renamed.copy(name = "trip")) }.message)
        assertEquals("This tag no longer exists.", assertFailsWith<FinanceConflictException> { repo.saveTag(Tag(id = 999, name = "Gone", createdAt = now)) }.message)
    }

    @Test
    fun `deleting a tag takes it off every transaction, and keeps the transactions`() = runDb {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val a = add(TransactionType.Expense, 100, setOf(tax.id!!))
        val b = add(TransactionType.Expense, 200, setOf(tax.id!!, trip.id!!))
        val c = add(TransactionType.Income, 300, setOf(tax.id!!))
        val balanceBefore = repo.accountBalances()
        assertEquals(mapOf(tax.id!! to 3, trip.id!! to 1), repo.tagUsage())

        assertEquals(3, repo.deleteTag(tax.id!!), "it was on three")

        assertEquals(listOf(trip), repo.tags())
        assertEquals(emptySet(), repo.transactionById(a.id!!)!!.tagIds)
        assertEquals(setOf(trip.id), repo.transactionById(b.id!!)!!.tagIds)
        assertEquals(emptySet(), repo.transactionById(c.id!!)!!.tagIds)
        assertEquals(3, repo.transactions(TransactionQuery(), limit = 10).items.size, "every transaction is kept")
        assertEquals(balanceBefore, repo.accountBalances(), "and every balance")
        assertEquals(mapOf(trip.id!! to 1), repo.tagUsage())
        assertEquals(1, repo.deleteTag(trip.id!!), "the last one was on one")
        assertTrue(repo.tags().isEmpty())
    }

    @Test
    fun `an expense combined into a transfer, or recorded as a card's bill, loses its tags`() = runDb {
        val tax = tag("Tax-deductible")
        val expense = add(TransactionType.Expense, 5_000, setOf(tax.id!!), at = now.minusHours(2))
        val income = repo.saveTransaction(draft(TransactionType.Income, 5_000, at = now.minusHours(1)).copy(accountId = savings()))
        val transfer = repo.combineSelfTransfer(SelfTransferPair(expense, income))
        assertEquals(TransactionType.Transfer, repo.transactionById(transfer.id!!)!!.type)
        assertEquals(emptySet(), repo.transactionById(transfer.id!!)!!.tagIds)
        assertEquals(emptyMap(), repo.tagUsage())

        val card = repo.saveAccount(Account(name = "ICICI Card", kind = AccountKind.Card, createdAt = now, updatedAt = now))
        val bill = add(TransactionType.Expense, 9_000, setOf(tax.id!!), payee = "ICICI card bill")
        val asTransfer = CardBillExpense(repo.transactionById(bill.id!!)!!, card).asTransfer(now)
        assertEquals(emptySet(), asTransfer.tagIds, "a transfer carries no tags")
        repo.saveTransaction(asTransfer)
        assertEquals(emptySet(), repo.transactionById(bill.id!!)!!.tagIds)
    }

    @Test
    fun `check and recalculate leaves tags alone`() = runDb {
        val tax = tag("Tax-deductible")
        val a = add(TransactionType.Expense, 100, setOf(tax.id!!))
        val result = repo.checkAndRepair()
        assertEquals(1, result.transactions)
        assertEquals(setOf(tax.id), repo.transactionById(a.id!!)!!.tagIds)
    }

    // ------------------------------------------------------------------------------------------ the yearly totals

    @Test
    fun `the yearly totals per tag add expenses and income apart, and leave transfers and what is to come out`() = runDb {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val year = FinancialYear.containing(now.toLocalDate(), Month.APRIL) // FY 2026-27
        // In the year, by today.
        add(TransactionType.Expense, 45_000, setOf(tax.id!!), at = LocalDateTime.of(2026, 4, 1, 0, 0))
        add(TransactionType.Expense, 5_050, setOf(tax.id!!, trip.id!!), at = LocalDateTime.of(2026, 9, 10, 18, 0))
        add(TransactionType.Income, 20_000, setOf(trip.id!!), at = LocalDateTime.of(2026, 9, 12, 9, 0))
        add(TransactionType.Expense, 1, setOf(tax.id!!), at = now)
        // Outside it: the year before, ending 31 March at 23:59.
        add(TransactionType.Expense, 99_999, setOf(tax.id!!), at = LocalDateTime.of(2026, 3, 31, 23, 59))
        // Upcoming: dated after today, so not counted yet.
        add(TransactionType.Expense, 70_000, setOf(tax.id!!), at = now.plusDays(1))
        // A transfer - never tagged, never counted - and an untagged expense.
        add(TransactionType.Transfer, 123_456)
        add(TransactionType.Expense, 8_000)

        val totals = repo.tagTotals(year.range, asOf = settledCutoff(now)).associateBy { it.tagId }
        assertEquals(TagTotal(tax.id!!, spent = 45_000 + 5_050 + 1, received = 0, count = 3), totals[tax.id])
        assertEquals(TagTotal(trip.id!!, spent = 5_050, received = 20_000, count = 2), totals[trip.id])
        assertEquals(2, totals.size)
        // Without the cutoff, what is dated ahead in the year counts.
        assertEquals(45_000L + 5_050 + 1 + 70_000, repo.tagTotals(year.range).first { it.tagId == tax.id }.spent)
        // The year before has only its own.
        assertEquals(listOf(TagTotal(tax.id!!, spent = 99_999, count = 1)), repo.tagTotals(year.previous.range, asOf = settledCutoff(now)))
        // A calendar year cuts at 1 January instead.
        val calendar = FinancialYear.containing(now.toLocalDate(), Month.JANUARY)
        assertEquals(
            45_000L + 5_050 + 1 + 99_999,
            repo.tagTotals(calendar.range, asOf = settledCutoff(now)).first { it.tagId == tax.id }.spent,
        )
        // A tag with nothing in the range is not in the list.
        assertTrue(repo.tagTotals(DateRange(LocalDate.of(2020, 1, 1).atStartOfDay(), LocalDate.of(2021, 1, 1).atStartOfDay())).isEmpty())
        assertNotNull(totals[tax.id])
    }
}
