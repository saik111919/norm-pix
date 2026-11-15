package com.example.normpix.finance.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.SelfTransferPair
import com.example.normpix.finance.domain.SplitPart
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.PictureEdit
import java.io.File
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Split payments in the ledger (2026-11-15, database 17): one payment saved as two to ten rows that share a group, an account, a date and
 * time, a payee, a person, tags and a picture, and each have a category, an amount and a note of their own. Saved in one SQL
 * transaction - all of it or none - from a new payment or from an existing row; edited as a whole; one part edited alone changes what the
 * parts share in every part; unsplit back into one row; deleted as a whole payment (and put back by an Undo); and every total - by
 * category, by budget - counts each part under its own category.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitRepositoryTest {
    private val now = LocalDateTime.of(2026, 11, 15, 9, 0)
    private val at = LocalDateTime.of(2026, 11, 12, 18, 30)
    private lateinit var database: FinanceDatabase
    private lateinit var repo: FinanceRepository
    private lateinit var context: Context

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = FinanceDatabase(context, name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun cash(): Long = repo.accounts().first { it.kind == AccountKind.Cash }.id!!

    private suspend fun bank(): Long = repo.accounts().firstOrNull { it.name == "HDFC Savings" }?.id
        ?: repo.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, openingBalance = 10_000_00, createdAt = now, updatedAt = now)).id!!

    private suspend fun category(name: String): Long = repo.categories().first { it.name == name }.id!!

    private suspend fun tag(name: String): Long = repo.saveTag(Tag(name = name, createdAt = now)).id!!

    private suspend fun person(name: String): Long = repo.savePerson(Person(name = name, createdAt = now, updatedAt = now)).id!!

    /** What the parts share, as a payment of [total]: the form's fields, and no category or note of its own. */
    private suspend fun payment(
        total: Long,
        type: TransactionType = TransactionType.Expense,
        payee: String? = "DMart",
        tags: Set<Long> = emptySet(),
        person: Long? = null,
        picture: String? = null,
        on: Long? = null,
    ) = FinanceTransaction(
        type = type, amount = total, accountId = on ?: cash(), occurredAt = at, payee = payee, createdAt = now, updatedAt = now,
        personId = person, picture = picture, tagIds = tags,
    )

    private suspend fun groceriesAndHousehold(): List<SplitPart> =
        listOf(SplitPart(category("Groceries"), 82_000, "Rice and dal"), SplitPart(category("Shopping"), 30_000, "Detergent"))

    private suspend fun plain(amount: Long, categoryName: String = "Groceries", payee: String? = "DMart"): FinanceTransaction = repo.saveTransaction(
        FinanceTransaction(
            type = TransactionType.Expense, amount = amount, accountId = cash(), categoryId = category(categoryName), occurredAt = at, payee = payee,
            note = "a note", createdAt = now, updatedAt = now,
        ),
    )

    private fun count(sql: String): Long = database.writableDatabase.query(sql).use { it.moveToFirst(); it.getLong(0) }

    private fun rowsCount() = count("SELECT COUNT(*) FROM transactions")

    private val pic = "pic-11111111-2222-3333-4444-555555555555.jpg"

    // ============================================================================================== saving a new split

    @Test
    fun `a new payment is saved as one row for each part, sharing everything but category, amount and note`() = runDb {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val mom = person("Mom")
        val saved = repo.saveSplit(payment(112_000, tags = setOf(tax, trip), person = mom, picture = pic), groceriesAndHousehold())

        assertEquals(2, saved.size)
        assertEquals(2L, rowsCount())
        val group = saved.first().splitGroup
        assertTrue(!group.isNullOrBlank() && group.length == 36, "a random UUID: $group")
        assertEquals(1, saved.map { it.splitGroup }.toSet().size, "one group")
        val rows = repo.splitParts(group!!)
        assertEquals(saved.map { it.id }, rows.map { it.id }, "in the order they were entered")
        assertEquals(listOf(82_000L, 30_000L), rows.map { it.amount })
        assertEquals(listOf(category("Groceries"), category("Shopping")), rows.map { it.categoryId })
        assertEquals(listOf("Rice and dal", "Detergent"), rows.map { it.note })
        assertEquals(112_000L, rows.sumOf { it.amount }, "the total is the parts' sum")
        // What the parts share.
        for (row in rows) {
            assertEquals(TransactionType.Expense, row.type)
            assertEquals(cash(), row.accountId)
            assertEquals(at, row.occurredAt)
            assertEquals("DMart", row.payee)
            assertEquals(mom, row.personId)
            assertEquals(pic, row.picture, "every part names the picture")
            assertEquals(setOf(tax, trip), row.tagIds, "the tags are on every part")
            assertNull(row.toAccountId)
            assertEquals(group, row.splitGroup)
        }
        // The same read by id and by the history.
        assertEquals(group, repo.transactionById(rows[1].id!!)!!.splitGroup)
        assertEquals(setOf(group), repo.transactions(TransactionQuery(), limit = 10).items.map { it.splitGroup }.toSet())
    }

    @Test
    fun `an income can be split too, and a part with no payee or person is fine`() = runDb {
        val saved = repo.saveSplit(
            payment(100_000, type = TransactionType.Income, payee = null),
            listOf(SplitPart(category("Salary"), 90_000), SplitPart(category("Freelance"), 10_000, "bonus")),
        )
        assertEquals(setOf(TransactionType.Income), saved.map { it.type }.toSet())
        assertEquals(listOf(null, "bonus"), saved.map { it.note })
        assertEquals(listOf(null, null), saved.map { it.payee })
    }

    @Test
    fun `from two parts to ten is allowed, one or eleven is not, and a transfer can't be split`() = runDb {
        val groceries = category("Groceries")
        fun parts(n: Int) = List(n) { SplitPart(groceries, 100) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(100), parts(1)) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(1_100), parts(11)) }
        assertEquals(10, repo.saveSplit(payment(1_000), parts(10)).size)
        assertEquals(10L, rowsCount())
        val toBank = bank()
        assertFailsWith<FinanceValidationException> {
            repo.saveSplit(payment(200, type = TransactionType.Transfer).copy(toAccountId = toBank), parts(2))
        }
        assertEquals(10L, rowsCount(), "the refused ones wrote nothing")
    }

    @Test
    fun `the parts have to add up to the total, each above zero with a category of the type's kind`() = runDb {
        val groceries = category("Groceries")
        val salary = category("Salary")
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(1_000), listOf(SplitPart(groceries, 400), SplitPart(groceries, 500))) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500), listOf(SplitPart(groceries, 500), SplitPart(groceries, 0))) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500), listOf(SplitPart(groceries, 600), SplitPart(groceries, -100))) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500), listOf(SplitPart(groceries, 250), SplitPart(null, 250))) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500), listOf(SplitPart(groceries, 250), SplitPart(salary, 250))) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500), listOf(SplitPart(groceries, 250), SplitPart(9_999, 250))) }
        // Income may not be spent on a person, and a person has to exist.
        val mom = person("Mom")
        assertFailsWith<FinanceValidationException> {
            repo.saveSplit(payment(500, type = TransactionType.Income, person = mom), listOf(SplitPart(salary, 250), SplitPart(salary, 250)))
        }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500, person = 9_999), listOf(SplitPart(groceries, 250), SplitPart(groceries, 250))) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500, picture = "../secret.jpg"), listOf(SplitPart(groceries, 250), SplitPart(groceries, 250))) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500, tags = setOf(77)), listOf(SplitPart(groceries, 250), SplitPart(groceries, 250))) }
        assertFailsWith<FinanceValidationException> { repo.saveSplit(payment(500, on = 9_999), listOf(SplitPart(groceries, 250), SplitPart(groceries, 250))) }
        assertEquals(0L, rowsCount(), "nothing was written by any of them")
    }

    // ================================================================================== from an existing transaction

    @Test
    fun `an existing transaction becomes the first part, keeping its id and when it was added`() = runDb {
        val tax = tag("Tax-deductible")
        val original = repo.saveTransaction(plain(112_000).copy(tagIds = setOf(tax), createdAt = now.minusDays(3)))
        val before = rowsCount()
        val saved = repo.saveSplit(
            original.copy(amount = 112_000),
            listOf(SplitPart(category("Groceries"), 82_000, "Rice and dal", id = original.id), SplitPart(category("Shopping"), 30_000)),
        )
        assertEquals(before + 1, rowsCount(), "one new row")
        assertEquals(original.id, saved[0].id)
        assertTrue(saved[1].id!! > original.id!!)
        val first = repo.transactionById(original.id!!)!!
        assertEquals(now.minusDays(3), first.createdAt, "it is the same row: when it was added is kept")
        assertEquals(82_000L, first.amount)
        assertEquals("Rice and dal", first.note, "the part's note, not the old one")
        assertEquals(setOf(tax), first.tagIds)
        assertEquals(setOf(tax), saved[1].tagIds, "the new part carries the tags too")
        assertEquals(first.splitGroup, saved[1].splitGroup)
        assertEquals(112_000L, repo.splitParts(first.splitGroup!!).sumOf { it.amount })
    }

    @Test
    fun `a row that is already in a split, or is not there, can't start another`() = runDb {
        val split = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        assertFailsWith<FinanceValidationException> {
            repo.saveSplit(payment(112_000), listOf(SplitPart(category("Groceries"), 100_000, id = split[0].id), SplitPart(category("Shopping"), 12_000)))
        }
        assertFailsWith<FinanceConflictException> {
            repo.saveSplit(payment(1_000), listOf(SplitPart(category("Groceries"), 500, id = 9_999), SplitPart(category("Shopping"), 500)))
        }
        assertFailsWith<FinanceValidationException> {
            repo.saveSplit(payment(1_000), listOf(SplitPart(category("Groceries"), 500, id = 5), SplitPart(category("Shopping"), 500, id = 5)))
        }
        assertEquals(2L, rowsCount())
    }

    @Test
    fun `a recurring payment's row keeps its rule when it is split`() = runDb {
        val rule = repo.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense, amount = 1_000, accountId = cash(), categoryId = category("Groceries"),
                frequency = RecurrenceFrequency.Monthly, startDate = at, nextDate = at.plusMonths(1), createdAt = now, updatedAt = now,
            ),
        )
        val row = repo.saveTransaction(plain(1_000).copy(recurringId = rule.id))
        val saved = repo.saveSplit(row, listOf(SplitPart(category("Groceries"), 600, id = row.id), SplitPart(category("Shopping"), 400)))
        assertEquals(listOf(rule.id, rule.id), saved.map { it.recurringId }, "both parts stay out of the everyday spending estimate")
    }

    // ================================================================================================ all or nothing

    @Test
    fun `a part that fails takes the parts before it back out - a new split writes nothing`() = runDb {
        val tax = tag("Tax-deductible")
        val before = rowsCount()
        // The third part is an income category on an expense: it is checked as it is written, after the first two were.
        assertFailsWith<FinanceValidationException> {
            repo.saveSplit(
                payment(900, tags = setOf(tax)),
                listOf(SplitPart(category("Groceries"), 300), SplitPart(category("Shopping"), 300), SplitPart(category("Salary"), 300)),
            )
        }
        assertEquals(before, rowsCount())
        assertEquals(0L, count("SELECT COUNT(*) FROM transaction_tags"))
        assertEquals(0L, count("SELECT COUNT(*) FROM transactions WHERE split_group IS NOT NULL"))
    }

    @Test
    fun `a split from an existing row that fails leaves that row exactly as it was`() = runDb {
        val original = plain(900)
        val before = repo.snapshot()
        assertFailsWith<FinanceValidationException> {
            repo.saveSplit(
                original.copy(amount = 900),
                listOf(
                    SplitPart(category("Groceries"), 300, id = original.id), SplitPart(category("Shopping"), 300), SplitPart(category("Salary"), 300),
                ),
            )
        }
        assertEquals(before, repo.snapshot(), "the ledger is exactly as it was: the row, its note, no group, no new rows")
        assertNull(repo.transactionById(original.id!!)!!.splitGroup)
    }

    @Test
    fun `an edit of a split that fails changes none of it`() = runDb {
        val saved = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        val group = saved.first().splitGroup!!
        val before = repo.snapshot()
        assertFailsWith<FinanceValidationException> {
            repo.saveSplit(
                payment(112_000, payee = "Reliance"),
                listOf(
                    SplitPart(category("Groceries"), 12_000, "changed", id = saved[0].id),
                    SplitPart(category("Shopping"), 50_000, id = saved[1].id),
                    SplitPart(category("Salary"), 50_000),
                ),
                group,
            )
        }
        assertEquals(before, repo.snapshot())
    }

    // ====================================================================================== editing a split as a whole

    @Test
    fun `editing a split keeps the parts that are named, adds new ones, and deletes the rows that are no longer parts`() = runDb {
        val tax = tag("Tax-deductible")
        val saved = repo.saveSplit(
            payment(112_000, tags = setOf(tax)),
            listOf(SplitPart(category("Groceries"), 60_000), SplitPart(category("Shopping"), 30_000), SplitPart(category("Health"), 22_000)),
        )
        val group = saved.first().splitGroup!!
        val keepFirst = saved[0].id!!
        val keepThird = saved[2].id!!
        val gone = saved[1].id!!
        val edited = repo.saveSplit(
            payment(112_000, payee = "Reliance", tags = emptySet()),
            listOf(
                SplitPart(category("Groceries"), 70_000, "more", id = keepFirst),
                SplitPart(category("Health"), 22_000, id = keepThird),
                SplitPart(category("Transport"), 20_000, "new part"),
            ),
            group,
        )
        assertEquals(listOf(keepFirst, keepThird), edited.take(2).map { it.id }, "the named rows stay")
        assertNotNull(edited[2].id)
        assertFalse(edited[2].id in saved.map { it.id }, "a part without a row is a new one")
        assertNull(repo.transactionById(gone), "the row no part names is deleted")
        val rows = repo.splitParts(group)
        assertEquals(3, rows.size)
        assertEquals(112_000L, rows.sumOf { it.amount })
        assertEquals(setOf(group), rows.map { it.splitGroup }.toSet())
        assertEquals(setOf("Reliance"), rows.map { it.payee }.toSet(), "what is shared was changed in every part")
        assertEquals(setOf(emptySet<Long>()), rows.map { it.tagIds }.toSet(), "the tags too")
        assertEquals(0L, count("SELECT COUNT(*) FROM transaction_tags"), "the deleted row's links went with it")
        assertEquals(rowsCount(), 3L)
    }

    @Test
    fun `an edit can't take a part from another split or name a row that is gone`() = runDb {
        val one = repo.saveSplit(payment(1_000), listOf(SplitPart(category("Groceries"), 600), SplitPart(category("Shopping"), 400)))
        val other = repo.saveSplit(payment(2_000, payee = "Other"), listOf(SplitPart(category("Groceries"), 1_500), SplitPart(category("Shopping"), 500)))
        val group = one.first().splitGroup!!
        assertFailsWith<FinanceConflictException> {
            repo.saveSplit(payment(1_000), listOf(SplitPart(category("Groceries"), 600, id = one[0].id), SplitPart(category("Shopping"), 400, id = other[0].id)), group)
        }
        assertFailsWith<FinanceConflictException> {
            repo.saveSplit(payment(1_000), listOf(SplitPart(category("Groceries"), 600), SplitPart(category("Shopping"), 400)), "no-such-group")
        }
        assertEquals(4L, rowsCount())
        assertEquals(2, repo.splitParts(group).size)
    }

    // ================================================================= one part edited alone: what is shared follows

    @Test
    fun `editing what the parts share on one part changes every part, in one step`() = runDb {
        val tax = tag("Tax-deductible")
        val trip = tag("Goa trip")
        val mom = person("Mom")
        val saved = repo.saveSplit(payment(112_000, tags = setOf(tax)), groceriesAndHousehold())
        val group = saved.first().splitGroup!!
        val part = repo.transactionById(saved[1].id!!)!!
        val newAt = at.plusDays(1).withHour(9)
        repo.saveTransaction(
            part.copy(accountId = bank(), occurredAt = newAt, payee = "Big Bazaar", personId = mom, tagIds = setOf(trip), picture = pic, amount = 31_000, note = "own note"),
        )
        val rows = repo.splitParts(group)
        for (row in rows) {
            assertEquals(bank(), row.accountId)
            assertEquals(newAt, row.occurredAt)
            assertEquals("Big Bazaar", row.payee)
            assertEquals(mom, row.personId)
            assertEquals(setOf(trip), row.tagIds)
            assertEquals(pic, row.picture)
            assertEquals(group, row.splitGroup)
        }
        // What is the part's own stayed its own.
        assertEquals(listOf(82_000L, 31_000L), rows.map { it.amount })
        assertEquals(listOf("Rice and dal", "own note"), rows.map { it.note })
        assertEquals(listOf(category("Groceries"), category("Shopping")), rows.map { it.categoryId })
    }

    @Test
    fun `a part can't be changed between spending and income, made a transfer, a repeating payment or a card bill`() = runDb {
        val saved = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        val part = repo.transactionById(saved[0].id!!)!!
        val salary = category("Salary")
        assertFailsWith<FinanceValidationException> { repo.saveTransaction(part.copy(type = TransactionType.Income, categoryId = salary, personId = null)) }
        assertFailsWith<FinanceValidationException> {
            repo.saveTransaction(part.copy(type = TransactionType.Transfer, categoryId = null, toAccountId = bank(), personId = null))
        }
        assertFailsWith<FinanceValidationException> { repo.saveTransactionAndRepeat(part.copy(recurringId = null), RepeatSchedule(RecurrenceFrequency.Monthly)) }
        assertEquals(saved.map { it.splitGroup }, repo.splitParts(part.splitGroup!!).map { it.splitGroup })
        assertEquals(TransactionType.Expense, repo.transactionById(part.id!!)!!.type)
        // And the two ledger repairs that turn an expense into a transfer refuse a part too.
        val income = repo.saveTransaction(
            FinanceTransaction(type = TransactionType.Income, amount = 82_000, accountId = bank(), categoryId = salary, occurredAt = at, createdAt = now, updatedAt = now),
        )
        assertFailsWith<FinanceValidationException> { repo.combineSelfTransfer(SelfTransferPair(part, income)) }
    }

    @Test
    fun `a transaction saved on its own never gets a group by being saved, and a new row's group is ignored`() = runDb {
        val row = plain(500)
        // A group named on a new row, or on an edit of a row that has none, is not written: only the split functions make one.
        val fresh = repo.saveTransaction(row.copy(id = null, splitGroup = "made-up"))
        assertNull(repo.transactionById(fresh.id!!)!!.splitGroup)
        repo.saveTransaction(row.copy(amount = 600, splitGroup = "made-up"))
        assertNull(repo.transactionById(row.id!!)!!.splitGroup)
        repo.saveTransactions(listOf(row.copy(id = null, splitGroup = "made-up")))
        assertEquals(0L, count("SELECT COUNT(*) FROM transactions WHERE split_group IS NOT NULL"))
    }

    // ====================================================================================================== unsplit

    @Test
    fun `unsplit makes one payment of the total, under the largest part's category, with the notes joined`() = runDb {
        val tax = tag("Tax-deductible")
        val saved = repo.saveSplit(payment(112_000, tags = setOf(tax), picture = pic), groceriesAndHousehold())
        val group = saved.first().splitGroup!!
        val whole = repo.unsplit(group)
        assertEquals(saved[0].id, whole.id, "the first part's row is the payment")
        assertEquals(112_000L, whole.amount)
        assertEquals(category("Groceries"), whole.categoryId, "the largest part's category")
        assertEquals("Rice and dal · Detergent", whole.note)
        assertNull(whole.splitGroup)
        assertEquals(1L, rowsCount(), "the other part's row is gone")
        val stored = repo.transactionById(whole.id!!)!!
        assertEquals(112_000L, stored.amount)
        assertEquals(category("Groceries"), stored.categoryId)
        assertEquals(setOf(tax), stored.tagIds)
        assertEquals(pic, stored.picture)
        assertEquals("DMart", stored.payee)
        assertEquals(0L, count("SELECT COUNT(*) FROM transactions WHERE split_group IS NOT NULL"))
        assertFailsWith<FinanceConflictException> { repo.unsplit(group) }
    }

    @Test
    fun `unsplit files the payment under the category that is chosen, and refuses one of the wrong kind`() = runDb {
        val saved = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        val group = saved.first().splitGroup!!
        assertFailsWith<FinanceValidationException> { repo.unsplit(group, category("Salary")) }
        assertEquals(2L, rowsCount(), "refused: nothing changed")
        val whole = repo.unsplit(group, category("Health"))
        assertEquals(category("Health"), whole.categoryId)
        assertEquals(112_000L, whole.amount)
    }

    @Test
    fun `when two parts are as large the first one's category is the default, and parts with no notes leave no note`() = runDb {
        val saved = repo.saveSplit(payment(1_000), listOf(SplitPart(category("Shopping"), 500), SplitPart(category("Groceries"), 500)))
        val whole = repo.unsplit(saved.first().splitGroup!!)
        assertEquals(category("Shopping"), whole.categoryId)
        assertNull(whole.note)
    }

    @Test
    fun `unsplit and its Undo bring the parts back exactly`() = runDb {
        val saved = repo.saveSplit(payment(112_000, tags = setOf(tag("Tax-deductible"))), groceriesAndHousehold())
        val before = repo.snapshot()
        repo.unsplit(saved.first().splitGroup!!)
        assertEquals(1L, rowsCount())
        repo.restore(before)
        assertEquals(before, repo.snapshot())
        assertEquals(2, repo.splitParts(saved.first().splitGroup!!).size)
    }

    // ================================================================================== deleting the whole payment

    @Test
    fun `deleting one part deletes the whole payment, and an Undo puts every part back`() = runDb {
        val tax = tag("Tax-deductible")
        val saved = repo.saveSplit(payment(112_000, tags = setOf(tax)), groceriesAndHousehold())
        val keep = plain(700, payee = "Other")
        val before = repo.snapshot()
        assertEquals(2, repo.deleteTransaction(saved[1].id!!), "both parts went")
        assertEquals(1L, rowsCount())
        assertNull(repo.transactionById(saved[0].id!!))
        assertNotNull(repo.transactionById(keep.id!!), "another payment is untouched")
        assertEquals(0L, count("SELECT COUNT(*) FROM transaction_tags"))
        repo.restore(before)
        assertEquals(before, repo.snapshot(), "every part is back, with its group, tags and ids")
        assertEquals(2, repo.splitParts(saved.first().splitGroup!!).size)
    }

    @Test
    fun `deleting a payment that is not split deletes that one row only`() = runDb {
        repo.saveSplit(payment(1_000), listOf(SplitPart(category("Groceries"), 500), SplitPart(category("Shopping"), 500)))
        val row = plain(300, payee = "Other")
        assertEquals(1, repo.deleteTransaction(row.id!!))
        assertEquals(2L, rowsCount())
    }

    // ============================================================================ the totals see each part's category

    @Test
    fun `category totals and a budget count each part under its own category`() = runDb {
        val month = MonthKey.of(at)
        repo.setBudget(category("Groceries"), month, 100_000)
        repo.setBudget(category("Shopping"), month, 20_000)
        repo.setBudget(null, month, 500_000)
        repo.saveSplit(payment(112_000), groceriesAndHousehold())
        val totals = repo.categoryTotals(month.range, TransactionType.Expense).associate { it.categoryId to (it.amount to it.count) }
        assertEquals(82_000L to 1, totals[category("Groceries")])
        assertEquals(30_000L to 1, totals[category("Shopping")])
        val overview = repo.budgetOverview(month, asOf = now)
        assertEquals(82_000L, overview.categories.getValue(category("Groceries")).spent)
        assertEquals(30_000L, overview.categories.getValue(category("Shopping")).spent, "over its 20,000 limit")
        assertTrue(overview.categories.getValue(category("Shopping")).spent > overview.categories.getValue(category("Shopping")).allocated)
        assertEquals(112_000L, overview.totalExpense, "the month's spending counts the payment once, as its total")
        assertEquals(112_000L, repo.summary(month.range).expense)
        assertEquals(-112_000L, repo.accountBalances(asOf = now)[cash()], "the account is down by the total")
    }

    @Test
    fun `a category filter finds the part in that category, and the text search finds every part`() = runDb {
        repo.saveSplit(payment(112_000), groceriesAndHousehold())
        val shopping = repo.transactions(TransactionQuery(categoryIds = setOf(category("Shopping"))), limit = 10).items
        assertEquals(listOf(30_000L), shopping.map { it.amount })
        assertEquals(2, repo.transactions(TransactionQuery(text = "DMart"), limit = 10).items.size)
        assertEquals(1, repo.transactions(TransactionQuery(text = "Detergent"), limit = 10).items.size, "a part's own note")
    }

    // ============================================================================ a group that does not hold, in a snapshot

    @Test
    fun `a snapshot never carries the mark of a group that does not hold, so it can always be restored`() = runDb {
        val saved = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        // Nothing the app writes does this: a second account on one part, written straight to the table.
        database.writableDatabase.execSQL("UPDATE transactions SET account_id = ${bank()} WHERE id = ${saved[1].id}")
        val snapshot = repo.snapshot()
        assertEquals(setOf<String?>(null), snapshot.transactions.map { it.splitGroup }.toSet(), "both rows are plain rows in it")
        assertEquals(listOf(30_000L, 82_000L).sorted(), snapshot.transactions.map { it.amount }.sorted(), "every amount as it was")
        assertEquals(emptyList(), FinanceBackupCodec.validate(snapshot))
        assertFalse("splitGroup" in FinanceBackupCodec.encode(snapshot, now))
        // A group that doesn't hold that is put into a snapshot by hand is left out of the file all the same: the file must read back.
        val marked = snapshot.copy(transactions = snapshot.transactions.map { it.copy(splitGroup = "g") })
        assertTrue(FinanceBackupCodec.validate(marked).isNotEmpty())
        val written = FinanceBackupCodec.encode(marked, now)
        assertFalse("splitGroup" in written)
        assertTrue(FinanceBackupCodec.decode(written).isValid)
        repo.restore(snapshot)
        assertEquals(0L, count("SELECT COUNT(*) FROM transactions WHERE split_group IS NOT NULL"))
        assertEquals(2L, rowsCount())
    }

    @Test
    fun `releasing a split takes the mark off and nothing else`() = runDb {
        val saved = repo.saveSplit(payment(112_000, tags = setOf(tag("Tax-deductible"))), groceriesAndHousehold())
        val group = saved.first().splitGroup!!
        assertEquals(2, repo.releaseSplit(group))
        val rows = repo.transactions(TransactionQuery(), limit = 10).items
        assertEquals(setOf<String?>(null), rows.map { it.splitGroup }.toSet())
        assertEquals(setOf(82_000L, 30_000L), rows.map { it.amount }.toSet())
        assertEquals(setOf(saved[0].tagIds), rows.map { it.tagIds }.toSet())
        assertFailsWith<FinanceConflictException> { repo.releaseSplit(group) }
    }

    // ======================================================================================= check and recalculate

    @Test
    fun `check and recalculate finds a split that no longer holds, offers to end it, and ends it when asked`() = runDb {
        val saved = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        val group = saved.first().splitGroup!!
        // Nothing the app writes does this: parts left on two accounts (written straight to the table).
        database.writableDatabase.execSQL("UPDATE transactions SET account_id = ${bank()} WHERE id = ${saved[1].id}")
        val found = repo.checkAndRepair()
        assertEquals(listOf(group), found.issues.brokenSplits.map { it.group })
        assertEquals(listOf(com.example.normpix.finance.domain.SplitFault.MixedAccount), found.issues.brokenSplits.map { it.fault })
        assertEquals(0, found.issues.repairCount, "found, not changed: the user is asked")
        assertEquals(2, repo.splitParts(group).size, "the check changed nothing")

        // Unsplit: the mark goes, every amount, account and category stays.
        val before = repo.transactions(TransactionQuery(), limit = 10).items
        assertEquals(2, repo.releaseSplit(group))
        val after = repo.transactions(TransactionQuery(), limit = 10).items
        assertEquals(before.map { it.copy(splitGroup = null, updatedAt = now) }, after.map { it.copy(updatedAt = now) })
        assertTrue(repo.checkAndRepair().issues.brokenSplits.isEmpty())
    }

    @Test
    fun `a group with one row left is found too, and unsplit makes it a plain payment`() = runDb {
        val saved = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        val group = saved.first().splitGroup!!
        database.writableDatabase.execSQL("DELETE FROM transactions WHERE id = ${saved[1].id}")
        assertEquals(listOf(com.example.normpix.finance.domain.SplitFault.TooFew), repo.checkAndRepair().issues.brokenSplits.map { it.fault })
        // The one row can be unsplit like any group (under its own category), or released: either way it is a payment of its own.
        val whole = repo.unsplit(group)
        assertEquals(82_000L, whole.amount)
        assertNull(whole.splitGroup)
        assertTrue(repo.checkAndRepair().issues.brokenSplits.isEmpty())
    }

    @Test
    fun `a group that does not hold can't be unsplit into one payment, which would have to choose an account`() = runDb {
        val saved = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        database.writableDatabase.execSQL("UPDATE transactions SET account_id = ${bank()} WHERE id = ${saved[1].id}")
        assertFailsWith<FinanceValidationException> { repo.unsplit(saved.first().splitGroup!!) }
        assertEquals(2L, rowsCount())
    }

    @Test
    fun `an Undo of a change still works while a group that does not hold is in the ledger`() = runDb {
        val (controller, _) = controller()
        val saved = repo.saveSplit(payment(112_000), groceriesAndHousehold())
        database.writableDatabase.execSQL("UPDATE transactions SET account_id = ${bank()} WHERE id = ${saved[1].id}")
        val other = plain(700, payee = "Other")
        val undo = controller.deleteTransactionUndoably(other.id!!)
        assertNull(repo.transactionById(other.id!!))
        assertTrue(controller.undo(undo), "the snapshot that is put back is restorable: its broken mark is not in it")
        assertNotNull(repo.transactionById(other.id!!))
        assertEquals(3L, rowsCount())
    }

    // ================================================================================================= the pictures

    private lateinit var kept: File
    private lateinit var drafts: File

    private fun controller(): Pair<FinanceController, TransactionPictures> {
        kept = File(context.cacheDir, "split-kept-${System.nanoTime()}").also { it.mkdirs() }
        drafts = File(context.cacheDir, "split-drafts-${System.nanoTime()}").also { it.mkdirs() }
        val pictures = TransactionPictures(context, kept = kept, drafts = drafts)
        return FinanceController(repo, pictures) to pictures
    }

    /** A draft picture as the form has one: a file in the drafts folder, which saving files a copy of. */
    private fun draftFile(): File = File(drafts, "draft-${System.nanoTime()}.jpg").also { it.writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun `every part names the same picture file, and the sweep keeps it while any row names it`() = runDb {
        val (controller, pictures) = controller()
        val draft = draftFile()
        val saved = controller.saveSplit(payment(112_000), groceriesAndHousehold(), picture = PictureEdit.Attach(draft))
        val name = saved[0].picture
        assertNotNull(name)
        assertEquals(name, saved[1].picture)
        assertTrue(pictures.exists(name))
        assertEquals(setOf(name), repo.pictureNames(), "one file, named by both rows")
        pictures.sweep(repo.pictureNames())
        assertTrue(pictures.exists(name), "kept while the rows name it")

        // Edited with the picture left as it is: still one file, on every part.
        val group = saved.first().splitGroup!!
        controller.saveSplit(saved.first().copy(amount = 112_000), groceriesAndHousehold().mapIndexed { i, p -> p.copy(id = saved[i].id) }, group)
        assertEquals(setOf(name), repo.splitParts(group).map { it.picture }.toSet())

        // Taking it off takes it off every part, and the file goes.
        controller.saveSplit(
            saved.first().copy(amount = 112_000, picture = name), groceriesAndHousehold().mapIndexed { i, p -> p.copy(id = saved[i].id) }, group, PictureEdit.Remove,
        )
        assertEquals(setOf<String?>(null), repo.splitParts(group).map { it.picture }.toSet(), "off every part")
        assertFalse(pictures.exists(name))
    }

    @Test
    fun `deleting the whole payment keeps the picture until the delete is settled, and an Undo gets it back`() = runDb {
        val (controller, pictures) = controller()
        val saved = controller.saveSplit(payment(112_000), groceriesAndHousehold(), picture = PictureEdit.Attach(draftFile()))
        val name = saved[0].picture!!
        val undo = controller.deleteTransactionUndoably(saved[1].id!!)
        assertEquals(0L, rowsCount())
        assertTrue(pictures.exists(name), "kept while the delete can still be undone")
        assertTrue(controller.undo(undo))
        assertEquals(2L, rowsCount())
        assertEquals(setOf(name), repo.pictureNames())
        controller.settle(undo)
        assertTrue(pictures.exists(name), "the rows name it again, so settling leaves it")
        val second = controller.deleteTransactionUndoably(saved[0].id!!)
        controller.settle(second)
        assertFalse(pictures.exists(name), "deleted and settled: the file goes")
    }

    @Test
    fun `a split that fails to save deletes the picture it kept`() = runDb {
        val (controller, pictures) = controller()
        val draft = draftFile()
        assertFailsWith<FinanceValidationException> {
            controller.saveSplit(payment(900), listOf(SplitPart(category("Groceries"), 300), SplitPart(category("Salary"), 600)), picture = PictureEdit.Attach(draft))
        }
        assertEquals(0L, rowsCount())
        assertTrue(kept.listFiles().orEmpty().isEmpty(), "no kept picture was left behind")
    }
}
