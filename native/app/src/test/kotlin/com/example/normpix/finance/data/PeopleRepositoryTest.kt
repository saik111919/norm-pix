package com.example.normpix.finance.data

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.PeopleSpending
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.rankedBy
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
 * Spending by person: people as labels on expenses. Managing them (names, pictures, archive, delete), linking an expense to one,
 * the per-person totals over a period, and above all that none of it creates, duplicates or moves money - the same expenses
 * add up the same everywhere, whoever they are for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeopleRepositoryTest {
    private val now = LocalDateTime.of(2026, 9, 15, 12, 0)
    private val september = MonthKey(2026, 9)
    private val august = MonthKey(2026, 8)
    private lateinit var database: FinanceDatabase
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun person(name: String, emoji: String? = null, color: Int = 0): Person =
        repo.savePerson(Person(name = name, emoji = emoji, color = color, createdAt = now, updatedAt = now))

    private suspend fun cash() = repo.accounts().first { it.name == "Cash" }

    private suspend fun category(name: String) = repo.categories().first { it.name == name }

    private suspend fun spend(
        amount: Long,
        personId: Long? = null,
        at: LocalDateTime = now,
        type: TransactionType = TransactionType.Expense,
        categoryName: String = "Food & dining",
        payee: String? = null,
    ): FinanceTransaction = repo.saveTransaction(
        FinanceTransaction(
            type = type,
            amount = amount,
            accountId = cash().id!!,
            categoryId = category(if (type == TransactionType.Income) "Salary" else categoryName).id,
            occurredAt = at,
            payee = payee,
            createdAt = now,
            updatedAt = now,
            personId = personId,
        ),
    )

    // ------------------------------------------------------------- managing people

    @Test
    fun `a person is saved with a stable id and listed by name`() = runDb {
        val mom = person("Mom", emoji = "👩", color = 3)
        val alex = person("alex")
        assertNotNull(mom.id)
        assertEquals(listOf("alex", "Mom"), repo.people().map { it.name }, "alphabetical, whatever the case")
        val read = repo.people().first { it.id == mom.id }
        assertEquals("👩", read.emoji)
        assertEquals(3, read.color)
        assertFalse(read.archived)
        assertTrue(alex.id != mom.id)
    }

    @Test
    fun `names are tidied and must be filled in and not too long`() = runDb {
        assertEquals("Mom Jones", person("   Mom    Jones ").name)
        assertFailsWith<FinanceValidationException> { person("   ") }
        assertFailsWith<FinanceValidationException> { person("x".repeat(41)) }
        assertEquals(40, person("y".repeat(40)).name.length)
    }

    @Test
    fun `two people cannot share a name whatever its case, archived ones included`() = runDb {
        val mom = person("Mom")
        assertFailsWith<FinanceConflictException> { person("mom") }
        assertFailsWith<FinanceConflictException> { person("  MOM ") }
        // Editing a person to keep their own name is fine.
        repo.savePerson(mom.copy(emoji = "👩"))
        repo.setPersonArchived(mom.id!!, true)
        val error = assertFailsWith<FinanceConflictException> { person("Mom") }
        assertTrue("archived" in error.message, error.message)
    }

    @Test
    fun `a picture is nothing or exactly one emoji`() = runDb {
        assertNull(person("A", emoji = "  ").emoji)
        assertEquals("🙂", person("B", emoji = "🙂").emoji)
        assertFailsWith<FinanceValidationException> { person("C", emoji = "ab") }
        assertFailsWith<FinanceValidationException> { person("D", emoji = "🙂🙂") }
    }

    @Test
    fun `renaming a person renames them on every expense because the link is the id`() = runDb {
        val mom = person("Mom")
        val lunch = spend(40_000, mom.id)
        repo.savePerson(mom.copy(name = "Mother"))
        assertEquals(mom.id, repo.transactionById(lunch.id!!)!!.personId)
        assertEquals(listOf("Mother"), repo.people().map { it.name })
    }

    @Test
    fun `saving a person that no longer exists is a conflict, not a silent insert`() = runDb {
        val mom = person("Mom")
        repo.deletePerson(mom.id!!)
        assertFailsWith<FinanceConflictException> { repo.savePerson(mom.copy(name = "Mother")) }
        assertTrue(repo.people().isEmpty())
    }

    // ------------------------------------------------------- linking an expense

    @Test
    fun `an expense can be spent on a person, and an expense without one is unchanged`() = runDb {
        val mom = person("Mom")
        val forMom = spend(40_000, mom.id)
        val forNoOne = spend(10_000)
        assertEquals(mom.id, repo.transactionById(forMom.id!!)!!.personId)
        assertNull(repo.transactionById(forNoOne.id!!)!!.personId)
    }

    @Test
    fun `only an expense can name a person`() = runDb {
        val mom = person("Mom")
        val salary = assertFailsWith<FinanceValidationException> { spend(50_000, mom.id, type = TransactionType.Income) }
        assertTrue("expense" in salary.message, salary.message)
        val bank = repo.saveAccount(
            com.example.normpix.finance.domain.Account(name = "Bank", createdAt = now, updatedAt = now),
        )
        assertFailsWith<FinanceValidationException> {
            repo.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Transfer,
                    amount = 1_000,
                    accountId = cash().id!!,
                    toAccountId = bank.id,
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now,
                    personId = mom.id,
                ),
            )
        }
    }

    @Test
    fun `an expense cannot name someone who is not on the list`() = runDb {
        assertFailsWith<FinanceValidationException> { spend(1_000, personId = 999) }
    }

    @Test
    fun `an archived person cannot be given a new expense but keeps the ones they have`() = runDb {
        val mom = person("Mom")
        val old = spend(20_000, mom.id)
        repo.setPersonArchived(mom.id!!, true)

        assertFailsWith<FinanceValidationException> { spend(5_000, mom.id) }
        // Editing an expense that already names her still works, and keeps her.
        val edited = repo.saveTransaction(old.copy(amount = 25_000))
        assertEquals(mom.id, repo.transactionById(edited.id!!)!!.personId)
        // Moving an old expense onto her is a new link, so it is refused.
        val other = spend(7_000)
        assertFailsWith<FinanceValidationException> { repo.saveTransaction(other.copy(personId = mom.id)) }
        repo.setPersonArchived(mom.id, false)
        repo.saveTransaction(other.copy(personId = mom.id))
        assertEquals(mom.id, repo.transactionById(other.id!!)!!.personId)
    }

    @Test
    fun `changing an expense to income takes the person off it only if the caller clears it`() = runDb {
        val mom = person("Mom")
        val lunch = spend(20_000, mom.id)
        // The editor clears the person when the type changes; the repository refuses a stale one rather than guessing.
        assertFailsWith<FinanceValidationException> { repo.saveTransaction(lunch.copy(type = TransactionType.Income, categoryId = category("Salary").id)) }
        repo.saveTransaction(lunch.copy(type = TransactionType.Income, categoryId = category("Salary").id, personId = null))
        assertNull(repo.transactionById(lunch.id!!)!!.personId)
    }

    // ------------------------------------------------------------------- totals

    @Test
    fun `totals per person over a month, with the whole and the part that is for no one`() = runDb {
        val mom = person("Mom")
        val riya = person("Riya")
        spend(40_000, mom.id)
        spend(10_000, mom.id)
        spend(25_000, riya.id)
        spend(5_000) // for no one
        spend(90_000, mom.id, at = LocalDateTime.of(2026, 8, 20, 9, 0)) // last month
        spend(70_000, type = TransactionType.Income) // never spending

        val sept = repo.peopleSpending(september.range)
        assertEquals(80_000L, sept.totalExpense, "every expense in September")
        assertEquals(4, sept.expenseCount)
        assertEquals(75_000L, sept.assigned)
        assertEquals(5_000L, sept.unassigned)
        assertEquals(sept.totalExpense, sept.assigned + sept.unassigned)
        assertEquals(50_000L, sept.of(mom.id!!)!!.amount)
        assertEquals(2, sept.of(mom.id)!!.count)
        assertEquals(25_000L, sept.of(riya.id!!)!!.amount)
        assertEquals(listOf(mom.id, riya.id), sept.byPerson.map { it.personId }, "largest first")
        assertEquals(50_000.0 / 80_000.0, sept.shareOfPerson(mom.id), 1e-9)

        val aug = repo.peopleSpending(august.range)
        assertEquals(90_000L, aug.totalExpense)
        assertEquals(90_000L, aug.of(mom.id)!!.amount)
        assertNull(aug.of(riya.id), "nothing for Riya in August")

        val all = repo.peopleSpending(null)
        assertEquals(170_000L, all.totalExpense)
        assertEquals(140_000L, all.of(mom.id)!!.amount)
        assertEquals(3, all.of(mom.id)!!.count)
    }

    @Test
    fun `a custom date range includes its first day and excludes the day after its last`() = runDb {
        val mom = person("Mom")
        spend(1_000, mom.id, at = LocalDateTime.of(2026, 9, 10, 0, 0))
        spend(2_000, mom.id, at = LocalDateTime.of(2026, 9, 12, 23, 59))
        spend(4_000, mom.id, at = LocalDateTime.of(2026, 9, 13, 0, 0))
        spend(8_000, mom.id, at = LocalDateTime.of(2026, 9, 9, 23, 59))
        val range = DateRange(LocalDateTime.of(2026, 9, 10, 0, 0), LocalDateTime.of(2026, 9, 13, 0, 0))
        val spending = repo.peopleSpending(range)
        assertEquals(3_000L, spending.of(mom.id!!)!!.amount)
        assertEquals(3_000L, spending.totalExpense)
        assertEquals(PeopleSpending.Empty, repo.peopleSpending(DateRange(now.plusYears(1), now.plusYears(2))))
    }

    @Test
    fun `totals follow an expense being added, edited, reassigned and deleted`() = runDb {
        val mom = person("Mom")
        val riya = person("Riya")
        val lunch = spend(10_000, mom.id)
        assertEquals(10_000L, repo.peopleSpending(september.range).of(mom.id!!)!!.amount)

        // Edit the amount.
        repo.saveTransaction(lunch.copy(amount = 16_000))
        assertEquals(16_000L, repo.peopleSpending(september.range).of(mom.id)!!.amount)

        // Reassign to Riya: Mom loses it entirely (no entry at all), Riya gains it.
        repo.saveTransaction(repo.transactionById(lunch.id!!)!!.copy(personId = riya.id))
        var spending = repo.peopleSpending(september.range)
        assertNull(spending.of(mom.id))
        assertEquals(16_000L, spending.of(riya.id!!)!!.amount)
        assertEquals(16_000L, spending.totalExpense, "the whole did not change when it changed hands")

        // Take the person off.
        repo.saveTransaction(repo.transactionById(lunch.id)!!.copy(personId = null))
        spending = repo.peopleSpending(september.range)
        assertTrue(spending.byPerson.isEmpty())
        assertEquals(16_000L, spending.unassigned)

        // Give it back, then delete it.
        repo.saveTransaction(repo.transactionById(lunch.id)!!.copy(personId = riya.id))
        repo.deleteTransaction(lunch.id)
        spending = repo.peopleSpending(september.range)
        assertEquals(PeopleSpending.Empty, spending)
    }

    @Test
    fun `moving the date of an expense moves it between periods for its person`() = runDb {
        val mom = person("Mom")
        val lunch = spend(10_000, mom.id, at = LocalDateTime.of(2026, 9, 3, 12, 0))
        repo.saveTransaction(lunch.copy(occurredAt = LocalDateTime.of(2026, 8, 31, 23, 0)))
        assertNull(repo.peopleSpending(september.range).of(mom.id!!))
        assertEquals(10_000L, repo.peopleSpending(august.range).of(mom.id)!!.amount)
    }

    @Test
    fun `people are ranked by the period's spending, then by name, nothing last`() = runDb {
        val ana = person("Ana")
        val ben = person("Ben")
        val cy = person("Cy")
        val dee = person("Dee")
        spend(5_000, ben.id)
        spend(5_000, ana.id)
        spend(9_000, cy.id)
        val spending = repo.peopleSpending(september.range)
        assertEquals(listOf("Cy", "Ana", "Ben", "Dee"), repo.people().rankedBy(spending).map { it.name })
        assertNotNull(dee.id)
    }

    // ------------------------------------------- nothing is created or inflated

    @Test
    fun `giving expenses people changes no balance, summary, category total or count`() = runDb {
        val mom = person("Mom")
        val riya = person("Riya")
        val food = spend(40_000)
        val shopping = spend(25_000, categoryName = "Shopping")
        spend(70_000, type = TransactionType.Income)

        val balances = repo.accountBalances()
        val summary = repo.summary(september.range)
        val categories = repo.categoryTotals(september.range, TransactionType.Expense)
        val daily = repo.dailyTotals(september.range, TransactionType.Expense)
        val monthly = repo.monthlyTotals(august, september)
        val count = repo.transactions(TransactionQuery()).items.size

        repo.saveTransaction(food.copy(personId = mom.id))
        repo.saveTransaction(shopping.copy(personId = riya.id))

        assertEquals(balances, repo.accountBalances())
        assertEquals(summary, repo.summary(september.range))
        assertEquals(categories, repo.categoryTotals(september.range, TransactionType.Expense))
        assertEquals(daily, repo.dailyTotals(september.range, TransactionType.Expense))
        assertEquals(monthly, repo.monthlyTotals(august, september))
        assertEquals(count, repo.transactions(TransactionQuery()).items.size, "no expense was duplicated")
        assertEquals(summary.expense, repo.peopleSpending(september.range).totalExpense, "the people page agrees with the dashboard")
    }

    // ---------------------------------------------------- archive and delete

    @Test
    fun `archiving hides a person from the main list but every expense keeps them`() = runDb {
        val mom = person("Mom")
        val riya = person("Riya")
        spend(40_000, mom.id)
        repo.setPersonArchived(mom.id!!, true)

        assertEquals(listOf("Riya", "Mom"), repo.people().map { it.name }, "archived last")
        assertTrue(repo.people().first { it.name == "Mom" }.archived)
        assertEquals(40_000L, repo.peopleSpending(september.range).of(mom.id)!!.amount, "still counted")
        repo.setPersonArchived(mom.id, false)
        assertEquals(listOf("Mom", "Riya"), repo.people().map { it.name })
        assertNotNull(riya.id)
    }

    @Test
    fun `deleting a person keeps every expense and every total, and says how many were unlinked`() = runDb {
        val mom = person("Mom")
        val riya = person("Riya")
        val a = spend(40_000, mom.id)
        val b = spend(10_000, mom.id, at = LocalDateTime.of(2026, 8, 3, 9, 0))
        val c = spend(25_000, riya.id)
        val balances = repo.accountBalances()
        val summary = repo.summary(september.range)
        val count = repo.transactions(TransactionQuery()).items.size

        assertEquals(2, repo.deletePerson(mom.id!!))

        assertEquals(listOf("Riya"), repo.people().map { it.name })
        assertNull(repo.transactionById(a.id!!)!!.personId)
        assertNull(repo.transactionById(b.id!!)!!.personId)
        assertEquals(riya.id, repo.transactionById(c.id!!)!!.personId, "other people are untouched")
        assertEquals(balances, repo.accountBalances())
        assertEquals(summary, repo.summary(september.range))
        assertEquals(count, repo.transactions(TransactionQuery()).items.size)
        assertEquals(25_000L, repo.peopleSpending(september.range).assigned)
    }

    @Test
    fun `deleting a person with no expenses removes only them`() = runDb {
        val mom = person("Mom")
        assertEquals(0, repo.deletePerson(mom.id!!))
        assertTrue(repo.people().isEmpty())
        assertEquals(0, repo.deletePerson(12345), "a person who is already gone is not an error")
    }

    @Test
    fun `a deleted person's name can be used again for someone new`() = runDb {
        val mom = person("Mom")
        repo.deletePerson(mom.id!!)
        val again = person("Mom")
        assertTrue(again.id != mom.id, "a new person, not the old one back")
    }

    // ------------------------------------------------------- history queries

    @Test
    fun `the history can be narrowed to one or more people`() = runDb {
        val mom = person("Mom")
        val riya = person("Riya")
        val a = spend(40_000, mom.id)
        val b = spend(25_000, riya.id)
        spend(5_000)

        assertEquals(listOf(a.id), repo.transactions(TransactionQuery(personIds = setOf(mom.id!!))).items.map { it.id })
        assertEquals(setOf(a.id, b.id), repo.transactions(TransactionQuery(personIds = setOf(mom.id, riya.id!!))).items.map { it.id }.toSet())
        assertEquals(3, repo.transactions(TransactionQuery()).items.size)
        assertTrue(TransactionQuery(personIds = setOf(1)).hasFilters)
        // The totals for the same filter are the person's, and only their expenses.
        assertEquals(40_000L, repo.summaryFor(TransactionQuery(personIds = setOf(mom.id))).expense)
        assertEquals(0L, repo.summaryFor(TransactionQuery(personIds = setOf(mom.id))).income)
    }

    @Test
    fun `a person's history respects a date range`() = runDb {
        val mom = person("Mom")
        spend(1_000, mom.id, at = LocalDateTime.of(2026, 9, 2, 8, 0))
        spend(2_000, mom.id, at = LocalDateTime.of(2026, 8, 30, 8, 0))
        val query = TransactionQuery(range = september.range, personIds = setOf(mom.id!!))
        assertEquals(listOf(1_000L), repo.transactions(query).items.map { it.amount })
        assertEquals(1_000L, repo.summaryFor(query).expense)
    }

    @Test
    fun `searching finds an expense by the name of its person`() = runDb {
        val mom = person("Mom")
        val riya = person("Riya")
        val a = spend(40_000, mom.id, payee = "Apollo")
        spend(25_000, riya.id, payee = "PVR")
        spend(5_000, payee = "Chai")
        assertEquals(listOf(a.id), repo.transactions(TransactionQuery(text = "mom")).items.map { it.id })
        assertTrue(repo.transactions(TransactionQuery(text = "nobody")).items.isEmpty())
        // A person's name is also found on the person, not on the other rows.
        assertEquals(1, repo.transactions(TransactionQuery(text = "riya")).items.size)
    }

    // ----------------------------------------------------- the database itself

    @Test
    fun `the database refuses a person on anything but an expense even if the repository were bypassed`() = runDb {
        val mom = person("Mom")
        val db = database.writableDatabase
        val income = ContentValues().apply {
            put("type", "income")
            put("amount", 100)
            put("account_id", cash().id!!)
            put("category_id", category("Salary").id!!)
            put("occurred_at", 0)
            put("created_at", 0)
            put("updated_at", 0)
            put("person_id", mom.id!!)
        }
        assertFailsWith<Exception> { db.insertOrThrow(FinanceDatabase.TRANSACTIONS, null, income) }

        // Nor can an expense that has one be turned into income without dropping the person.
        val lunch = spend(1_000, mom.id)
        assertFailsWith<Exception> {
            db.execSQL("UPDATE ${FinanceDatabase.TRANSACTIONS} SET type = 'income' WHERE id = ${lunch.id}")
        }
    }

    @Test
    fun `the database keeps names unique whatever their case`() = runDb {
        person("Mom")
        val db = database.writableDatabase
        val clash = ContentValues().apply {
            put("name", "MOM")
            put("color", 0)
            put("archived", 0)
            put("created_at", 0)
            put("updated_at", 0)
        }
        assertFailsWith<SQLiteConstraintException> { db.insertOrThrow(FinanceDatabase.PEOPLE, null, clash) }
    }

    @Test
    fun `deleting a person row directly unlinks their expenses instead of leaving a dangling link`() = runDb {
        val mom = person("Mom")
        val lunch = spend(1_000, mom.id)
        database.writableDatabase.execSQL("DELETE FROM ${FinanceDatabase.PEOPLE} WHERE id = ${mom.id}")
        assertNull(repo.transactionById(lunch.id!!)!!.personId)
    }

    // ----------------------------------------------------- reset and restore

    @Test
    fun `resetting all data removes the people too`() = runDb {
        person("Mom")
        repo.resetAll()
        assertTrue(repo.people().isEmpty())
    }
}
