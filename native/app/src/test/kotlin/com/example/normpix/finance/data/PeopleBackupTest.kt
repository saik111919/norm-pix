package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.TransactionType
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

/** People in a backup: who the spending was on survives export, reset and restore; old and damaged files are handled. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeopleBackupTest {
    private val now = LocalDateTime.of(2026, 9, 15, 12, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun person(name: String, emoji: String? = null) =
        repo.savePerson(Person(name = name, emoji = emoji, color = 2, createdAt = now, updatedAt = now))

    private suspend fun spend(amount: Long, personId: Long?, payee: String? = null): FinanceTransaction = repo.saveTransaction(
        FinanceTransaction(
            type = TransactionType.Expense,
            amount = amount,
            accountId = repo.accounts().first().id!!,
            categoryId = repo.categories().first { it.name == "Food & dining" }.id,
            occurredAt = now,
            payee = payee,
            createdAt = now,
            updatedAt = now,
            personId = personId,
        ),
    )

    @Test
    fun `export, reset and import brings back the people and who each expense was for`() = runDb {
        val mom = person("Mom", "👩")
        val riya = person("Riya")
        val a = spend(40_000, mom.id)
        val b = spend(25_000, riya.id)
        val c = spend(5_000, null)
        // Archived once she has an expense: a new expense cannot be put on an archived person, but the ones there stay.
        repo.setPersonArchived(riya.id!!, true)

        val json = FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now)
        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.isValid, read.errors.toString())
        assertEquals(2, read.snapshot!!.people.size)

        repo.resetAll()
        assertTrue(repo.people().isEmpty())
        repo.restore(read.snapshot!!)

        val people = repo.people()
        assertEquals(listOf("Mom", "Riya"), people.map { it.name })
        assertEquals("👩", people.first { it.name == "Mom" }.emoji)
        assertTrue(people.first { it.name == "Riya" }.archived)
        assertEquals(mom.id, repo.transactionById(a.id!!)!!.personId, "stable ids: the same person is on the same expense")
        assertEquals(riya.id, repo.transactionById(b.id!!)!!.personId)
        assertNull(repo.transactionById(c.id!!)!!.personId)
        assertEquals(40_000L, repo.peopleSpending(null).of(mom.id!!)!!.amount)
    }

    @Test
    fun `restoring replaces the people that were there`() = runDb {
        val snapshot = repo.snapshot()
        person("Someone new")
        repo.restore(snapshot)
        assertTrue(repo.people().isEmpty())
    }

    @Test
    fun `a backup from before people existed still restores, with no people`() = runDb {
        val json = FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now)
            .replace("\"version\": 2", "\"version\": 1")
            .replace(Regex("\"people\": \\[\\s*\\],?"), "")
        assertFalse("\"people\"" in json)
        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.isValid, read.errors.toString())
        assertTrue(read.snapshot!!.people.isEmpty())
        repo.restore(read.snapshot!!)
        assertTrue(repo.people().isEmpty())
    }

    @Test
    fun `a backup from a newer version is refused rather than read without its people`() = runDb {
        val json = FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now).replace("\"version\": 2", "\"version\": 3")
        assertFalse(FinanceBackupCodec.decode(json).isValid)
    }

    @Test
    fun `a backup that names a missing person, or puts one on income, is rejected before anything is written`() = runDb {
        val mom = person("Mom")
        spend(40_000, mom.id)
        val good = FinanceBackupCodec.encode(repo.snapshot(), exportedAt = now)

        val missing = good.replace("\"personId\": ${mom.id}", "\"personId\": 999")
        assertFalse(FinanceBackupCodec.decode(missing).isValid)

        val onIncome = repo.snapshot().let { snapshot ->
            snapshot.copy(
                transactions = snapshot.transactions.map { it.copy(type = TransactionType.Income, categoryId = repo.categories().first { c -> c.name == "Salary" }.id) },
            )
        }
        assertTrue(FinanceBackupCodec.validate(onIncome).any { "person" in it }, FinanceBackupCodec.validate(onIncome).toString())

        val twins = repo.snapshot().let { it.copy(people = it.people + it.people.map { p -> p.copy(id = 77, name = "MOM") }) }
        assertTrue(FinanceBackupCodec.validate(twins).any { "same name" in it })

        // A restore of such a snapshot is refused, and the ledger is left as it was.
        val before = repo.snapshot()
        assertFailsWith<FinanceValidationException> { repo.restore(before.copy(people = emptyList())) }
        assertEquals(before.transactions.size, repo.snapshot().transactions.size)
        assertNotNull(repo.people().firstOrNull { it.name == "Mom" })
    }

    @Test
    fun `the CSV export names the person on each expense`() = runDb {
        val mom = person("Mom, \"Mum\"")
        spend(4_550, mom.id, payee = "Apollo")
        spend(1_000, null, payee = "Chai")
        val csv = FinanceBackupCodec.encodeCsv(repo.snapshot()) { it.toString() }.trim().lines()
        assertTrue(csv.first().endsWith(",Person"), csv.first())
        assertTrue(csv.any { it.contains("Apollo") && it.endsWith("\"Mom, \"\"Mum\"\"\"") }, csv.toString())
        // An expense for no one has an empty last cell.
        assertTrue(csv.any { it.contains("Chai") && it.endsWith(",") }, csv.toString())
    }
}
