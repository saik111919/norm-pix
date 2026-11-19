package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.DebtEntry
import com.example.normpix.finance.domain.DebtKind
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.debtBalances

import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Money lent and borrowed in the ledger (2026-11-19): a record is kept with the transaction that moved its money, in one step, or on its
 * own; it is checked (an amount above zero, a person who exists and - for a new one - is not archived); deleting a record keeps its
 * transaction, deleting the transaction keeps the record; deleting the person takes their records; a snapshot, a restore and a backup
 * carry the records; and a version 17 database gains the table with every row as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebtRepositoryTest {
    private val now = LocalDateTime.of(2026, 11, 19, 9, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun cash(): Account = repo.accounts().first { it.kind == AccountKind.Cash }

    private suspend fun person(name: String = "Ravi", archived: Boolean = false): Person {
        val made = repo.savePerson(Person(name = name, createdAt = now, updatedAt = now))
        if (archived) repo.setPersonArchived(made.id!!, true)
        return repo.people().first { it.id == made.id }
    }

    private fun record(person: Person, kind: DebtKind, amount: Long = 50_000) =
        DebtEntry(personId = person.id!!, kind = kind, amount = amount, occurredAt = now.minusDays(1), note = " Dinner ", createdAt = now, updatedAt = now)

    private suspend fun categoryId(name: String): Long = repo.categories().first { it.name == name }.id!!

    private suspend fun moved(kind: DebtKind, amount: Long = 50_000, payee: String = "Ravi") = FinanceTransaction(
        type = if (kind.moneyIn) TransactionType.Income else TransactionType.Expense,
        amount = amount,
        accountId = cash().id!!,
        categoryId = categoryId(kind.categoryName),
        occurredAt = now.minusDays(1),
        payee = payee,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a record with its money moved keeps both, linked, and the account follows`() = runDb {
        val ravi = person()
        val before = repo.accountBalances(asOf = now)[cash().id] ?: 0L
        val kept = repo.saveDebt(record(ravi, DebtKind.Lent), moved(DebtKind.Lent))

        assertNotNull(kept.id)
        assertEquals("Dinner", kept.note, "the note is tidied")
        val tx = repo.transactionById(assertNotNull(kept.transactionId))!!
        assertEquals(TransactionType.Expense, tx.type)
        assertEquals(50_000L, tx.amount)
        assertEquals(categoryId("Lent to someone"), tx.categoryId)
        assertEquals("Ravi", tx.payee)
        assertEquals(before - 50_000, repo.accountBalances(asOf = now)[cash().id])
        assertEquals(listOf(kept), repo.debts())
        assertEquals(mapOf(ravi.id!! to 50_000L), debtBalances(repo.debts()))
    }

    @Test
    fun `a record on its own changes no account`() = runDb {
        val ravi = person()
        val before = repo.accountBalances(asOf = now)
        val kept = repo.saveDebt(record(ravi, DebtKind.Borrowed, 20_000))
        assertNull(kept.transactionId)
        assertEquals(before, repo.accountBalances(asOf = now))
        assertEquals(-20_000L, debtBalances(repo.debts())[ravi.id])
    }

    @Test
    fun `settling up brings the balance to zero, recording the repayment as income`() = runDb {
        val ravi = person()
        repo.saveDebt(record(ravi, DebtKind.Lent), moved(DebtKind.Lent))
        val back = repo.saveDebt(record(ravi, DebtKind.RepaidToMe), moved(DebtKind.RepaidToMe))
        assertEquals(TransactionType.Income, repo.transactionById(back.transactionId!!)!!.type)
        assertEquals(categoryId("Loan repaid to me"), repo.transactionById(back.transactionId!!)!!.categoryId)
        assertTrue(debtBalances(repo.debts()).isEmpty(), "all settled")
    }

    @Test
    fun `a record is checked, and one that fails writes neither it nor its transaction`() = runDb {
        val ravi = person()
        assertFailsWith<FinanceValidationException> { repo.saveDebt(record(ravi, DebtKind.Lent, 0)) }
        val archived = person("Asha", archived = true)
        assertFailsWith<FinanceValidationException> { repo.saveDebt(record(archived, DebtKind.Lent)) }
        assertFailsWith<FinanceValidationException> { repo.saveDebt(record(ravi, DebtKind.Lent).copy(personId = 999)) }
        // A transaction that can't be saved (an income category on an expense) takes the record back out with it.
        val wrong = moved(DebtKind.Lent).copy(categoryId = categoryId("Loan repaid to me"))
        assertFailsWith<FinanceValidationException> { repo.saveDebt(record(ravi, DebtKind.Lent), wrong) }
        assertTrue(repo.debts().isEmpty())
        assertTrue(repo.transactionsBetween(now.minusYears(1), now.plusDays(1)).isEmpty())
    }

    @Test
    fun `deleting a record keeps its transaction, and deleting the transaction keeps the record`() = runDb {
        val ravi = person()
        val first = repo.saveDebt(record(ravi, DebtKind.Lent), moved(DebtKind.Lent))
        repo.deleteDebt(first.id!!)
        assertTrue(repo.debts().isEmpty())
        assertNotNull(repo.transactionById(first.transactionId!!), "the money moved; the transaction stays")
        assertFailsWith<FinanceConflictException> { repo.deleteDebt(first.id!!) }

        val second = repo.saveDebt(record(ravi, DebtKind.Lent), moved(DebtKind.Lent))
        repo.deleteTransaction(second.transactionId!!)
        val kept = repo.debts().single()
        assertEquals(second.id, kept.id)
        assertNull(kept.transactionId, "what is owed is not undone by the transaction going")
    }

    @Test
    fun `deleting the person takes their records, and only theirs`() = runDb {
        val ravi = person()
        val asha = person("Asha")
        val raviRecord = repo.saveDebt(record(ravi, DebtKind.Lent), moved(DebtKind.Lent))
        repo.saveDebt(record(asha, DebtKind.Borrowed))
        repo.deletePerson(ravi.id!!)
        assertEquals(listOf(asha.id), repo.debts().map { it.personId })
        assertNotNull(repo.transactionById(raviRecord.transactionId!!), "their transaction stays, as their expenses do")
    }

    @Test
    fun `a snapshot, a restore and a backup carry the records, and a file from before them restores with none`() = runDb {
        val ravi = person()
        val lent = repo.saveDebt(record(ravi, DebtKind.Lent), moved(DebtKind.Lent))
        val onItsOwn = repo.saveDebt(record(ravi, DebtKind.RepaidToMe, 10_000))
        val snapshot = repo.snapshot()
        assertEquals(listOf(lent, onItsOwn), snapshot.debts)

        val file = FinanceBackupCodec.encode(snapshot, now)
        val read = assertNotNull(FinanceBackupCodec.decode(file).snapshot, FinanceBackupCodec.decode(file).errors.joinToString())
        assertEquals(snapshot.debts, read.debts)

        repo.restore(read.copy(debts = emptyList()))
        assertTrue(repo.debts().isEmpty(), "a backup without records (an older file) restores with none")
        repo.restore(read)
        assertEquals(listOf(lent, onItsOwn), repo.debts())
        assertEquals(40_000L, debtBalances(repo.debts())[ravi.id])
    }

    @Test
    fun `the backup format is 8, a format 7 file with no records still reads, and a newer one is refused`() {
        assertEquals(8, FinanceBackupCodec.FORMAT_VERSION)
        val empty = FinanceSnapshot("INR", accounts = emptyList(), categories = emptyList(), transactions = emptyList(), budgets = emptyList(), recurringRules = emptyList())
        val json = FinanceBackupCodec.encode(empty, now)
        assertTrue("\"version\": 8" in json)
        val older = FinanceBackupCodec.decode(json.replace("\"version\": 8", "\"version\": 7"))
        assertTrue(older.isValid, older.errors.joinToString())
        assertTrue(assertNotNull(older.snapshot).debts.isEmpty())
        val newer = FinanceBackupCodec.decode(json.replace("\"version\": 8", "\"version\": 9"))
        assertEquals(listOf("This backup was made by a newer version of NormPix. Update the app, then try again."), newer.errors)
    }

    @Test
    fun `a backup whose record names a person or a transaction it does not have is refused`() = runDb {
        val ravi = person()
        repo.saveDebt(record(ravi, DebtKind.Lent), moved(DebtKind.Lent))
        val snapshot = repo.snapshot()
        val noPerson = snapshot.copy(debts = snapshot.debts.map { it.copy(personId = 999) })
        assertTrue(FinanceBackupCodec.validate(noPerson).any { "person who is not in the backup" in it })
        val noTransaction = snapshot.copy(debts = snapshot.debts.map { it.copy(transactionId = 999) })
        assertTrue(FinanceBackupCodec.validate(noTransaction).any { "transaction that is not in the backup" in it })
        val zero = snapshot.copy(debts = snapshot.debts.map { it.copy(amount = 0) })
        assertTrue(FinanceBackupCodec.validate(zero).any { "not above zero" in it })
    }
}

/**
 * Version 18 adds one empty table for the records of money owed and nothing else: a database at version 17 (made here by building the
 * current one and taking the table out, as no version 17 phone has it) is opened by this release with every row as it was and the table
 * there and empty; the step is safe to run twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DebtMigrationTest {
    private val now = LocalDateTime.of(2026, 11, 19, 9, 0)
    private val name = "debt_migration_test.db"

    @Test
    fun `a version 17 database gains the debts table, empty, and keeps every row`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(name)
        val first = FinanceRepository(FinanceDatabase(context, name = name, clock = { now }), clock = { now }, dispatcher = Dispatchers.Unconfined)
        val ravi = first.savePerson(Person(name = "Ravi", createdAt = now, updatedAt = now))
        val cash = first.accounts().first { it.kind == AccountKind.Cash }
        val food = first.categories().first { it.name == "Food & dining" }
        first.saveTransaction(
            FinanceTransaction(type = TransactionType.Expense, amount = 12_000, accountId = cash.id!!, categoryId = food.id, occurredAt = now, createdAt = now, updatedAt = now),
        )
        val before = first.snapshot()
        first.close()

        // Back to version 17: no debts table.
        FinanceDatabase(context, name = name, clock = { now }).use { db ->
            db.writableDatabase.execSQL("DROP TABLE debts")
            db.writableDatabase.execSQL("PRAGMA user_version = 17")
        }

        val second = FinanceRepository(FinanceDatabase(context, name = name, clock = { now }), clock = { now }, dispatcher = Dispatchers.Unconfined)
        try {
            assertTrue(second.debts().isEmpty())
            val after = second.snapshot()
            assertEquals(before.copy(debts = emptyList()), after, "every row as it was")
            second.saveDebt(DebtEntry(personId = ravi.id!!, kind = DebtKind.Lent, amount = 100, occurredAt = now, createdAt = now, updatedAt = now))
            assertEquals(1, second.debts().size, "and it takes records")
            // Run again (the onOpen safety net does): nothing changes, nothing fails.
            FinanceDatabase(context, name = name, clock = { now }).use { FinanceDatabase.addDebts(it.writableDatabase) }
        } finally {
            second.close()
            context.deleteDatabase(name)
        }
    }
}
