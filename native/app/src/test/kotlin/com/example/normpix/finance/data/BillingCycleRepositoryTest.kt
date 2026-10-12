package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceValidationException
import java.time.LocalDateTime
import java.time.ZoneOffset
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
 * A credit card can say which day its statement is made up and which day the bill is due. They are kept with the card, only
 * a card has them, they are days of the month, they survive a backup - and a database from before them opens with none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingCycleRepositoryTest {
    private val now = LocalDateTime.of(2026, 9, 15, 12, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private suspend fun save(name: String, kind: AccountKind, statementDay: Int? = null, dueDay: Int? = null): Account =
        repo.saveAccount(Account(name = name, kind = kind, statementDay = statementDay, dueDay = dueDay, createdAt = now, updatedAt = now))

    private suspend fun stored(id: Long?): Account = repo.accounts().first { it.id == id }

    @Test
    fun `a card keeps its statement day and due day, and an account without them has none`() = runBlocking<Unit> {
        val card = save("HDFC Credit", AccountKind.Card, statementDay = 5, dueDay = 20)
        assertEquals(5, stored(card.id).statementDay)
        assertEquals(20, stored(card.id).dueDay)

        val plain = save("Plain card", AccountKind.Card)
        assertNull(stored(plain.id).statementDay)
        assertNull(stored(plain.id).dueDay)
        assertTrue(repo.accounts().filter { it.id != card.id }.all { it.statementDay == null && it.dueDay == null })
    }

    @Test
    fun `either day can be given without the other`() = runBlocking<Unit> {
        val onlyDue = save("Due only", AccountKind.Card, dueDay = 28)
        assertNull(stored(onlyDue.id).statementDay)
        assertEquals(28, stored(onlyDue.id).dueDay)

        val onlyStatement = save("Statement only", AccountKind.Card, statementDay = 1)
        assertEquals(1, stored(onlyStatement.id).statementDay)
        assertNull(stored(onlyStatement.id).dueDay)
    }

    @Test
    fun `the first and the last day of a month are allowed`() = runBlocking<Unit> {
        val card = save("Edges", AccountKind.Card, statementDay = 1, dueDay = 31)
        assertEquals(1, stored(card.id).statementDay)
        assertEquals(31, stored(card.id).dueDay)
    }

    @Test
    fun `only a card has them - for anything else they are dropped`() = runBlocking<Unit> {
        val bank = save("HDFC Savings", AccountKind.Bank, statementDay = 5, dueDay = 20)
        assertNull(stored(bank.id).statementDay)
        assertNull(stored(bank.id).dueDay)

        // And a card that stops being one loses them.
        val card = save("Card", AccountKind.Card, statementDay = 5, dueDay = 20)
        repo.saveAccount(stored(card.id).copy(kind = AccountKind.Wallet))
        assertNull(stored(card.id).statementDay)
        assertNull(stored(card.id).dueDay)
    }

    @Test
    fun `a day outside the month is refused, and what was saved stays`() = runBlocking<Unit> {
        for (bad in listOf(0, -1, 32, 100)) {
            val statement = assertFailsWith<FinanceValidationException>("statement $bad") { save("Card", AccountKind.Card, statementDay = bad) }
            assertTrue("statement" in statement.message.orEmpty().lowercase())
            val due = assertFailsWith<FinanceValidationException>("due $bad") { save("Card", AccountKind.Card, dueDay = bad) }
            assertTrue("due" in due.message.orEmpty().lowercase())
        }
        assertTrue(repo.accounts().none { it.name == "Card" })

        val card = save("Kept", AccountKind.Card, statementDay = 5, dueDay = 20)
        assertFailsWith<FinanceValidationException> { repo.saveAccount(stored(card.id).copy(dueDay = 40)) }
        assertEquals(20, stored(card.id).dueDay)
    }

    @Test
    fun `the days can be changed and removed`() = runBlocking<Unit> {
        val card = save("Card", AccountKind.Card, statementDay = 5, dueDay = 20)
        repo.saveAccount(stored(card.id).copy(statementDay = 8, dueDay = 25))
        assertEquals(8, stored(card.id).statementDay)
        assertEquals(25, stored(card.id).dueDay)

        repo.saveAccount(stored(card.id).copy(statementDay = null, dueDay = null))
        assertNull(stored(card.id).statementDay)
        assertNull(stored(card.id).dueDay)
    }

    @Test
    fun `a backup carries the days`() = runBlocking<Unit> {
        save("HDFC Credit", AccountKind.Card, statementDay = 5, dueDay = 20)
        save("Axis Credit", AccountKind.Card, dueDay = 3)
        val text = FinanceBackupCodec.encode(repo.snapshot(), now)
        val read = FinanceBackupCodec.decode(text)
        val snapshot = assertNotNull(read.snapshot, read.errors.toString())
        val hdfc = snapshot.accounts.first { it.name == "HDFC Credit" }
        assertEquals(5, hdfc.statementDay)
        assertEquals(20, hdfc.dueDay)
        assertNull(snapshot.accounts.first { it.name == "Axis Credit" }.statementDay)

        repo.restore(snapshot)
        assertEquals(5, repo.accounts().first { it.name == "HDFC Credit" }.statementDay)
        assertEquals(20, repo.accounts().first { it.name == "HDFC Credit" }.dueDay)
        assertEquals(3, repo.accounts().first { it.name == "Axis Credit" }.dueDay)
    }

    @Test
    fun `a backup from before the days existed restores the card with neither`() = runBlocking<Unit> {
        save("HDFC Credit", AccountKind.Card, statementDay = 5, dueDay = 20)
        val older = FinanceBackupCodec.encode(repo.snapshot(), now).replace(Regex(",\\s*\"(statementDay|dueDay)\": [^,\\n}]+"), "")
        assertTrue("statementDay" !in older && "dueDay" !in older)

        val read = FinanceBackupCodec.decode(older)
        val snapshot = assertNotNull(read.snapshot, read.errors.toString())
        val card = snapshot.accounts.first { it.name == "HDFC Credit" }
        assertNull(card.statementDay)
        assertNull(card.dueDay)
    }

    @Test
    fun `a backup with a day outside the month, or on an account that is not a card, is rejected`() = runBlocking<Unit> {
        val card = save("HDFC Credit", AccountKind.Card, statementDay = 5, dueDay = 20)
        val bank = save("HDFC Savings", AccountKind.Bank)
        val snapshot = repo.snapshot()

        fun problems(change: (Account) -> Account) =
            FinanceBackupCodec.validate(snapshot.copy(accounts = snapshot.accounts.map { if (it.id == card.id || it.id == bank.id) change(it) else it }))

        assertTrue(problems { if (it.id == card.id) it.copy(statementDay = 0) else it }.any { "statement date" in it })
        assertTrue(problems { if (it.id == card.id) it.copy(dueDay = 32) else it }.any { "due date" in it })
        assertTrue(problems { if (it.id == bank.id) it.copy(dueDay = 10) else it }.any { "not a card" in it })
        assertTrue(FinanceBackupCodec.validate(snapshot).isEmpty())

        // As the file would say it: the day is read as written, so the check can see it is out of range.
        val text = FinanceBackupCodec.encode(snapshot, now).replace("\"dueDay\": 20", "\"dueDay\": 40")
        val read = FinanceBackupCodec.decode(text)
        assertNull(read.snapshot)
        assertTrue(read.errors.any { "due date" in it }, read.errors.toString())
    }
}

/**
 * A finance database as the previous release left it (version 8, no billing days) opened by this one: every account is
 * still there, exactly as it was and with neither day, and a card can be given them from then on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BillingCycleMigrationTest {
    private val dbName = "billing_cycle_migration_test.db"
    private lateinit var context: Context

    /** The version 8 schema of the tables the open of the database touches: accounts without the days, and what refers to them. */
    private class VersionEight(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 8) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE accounts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, kind TEXT NOT NULL,
                  opening_balance INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                  bank_key TEXT, credit_limit INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE categories (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
                  type TEXT NOT NULL CHECK (type IN ('expense', 'income')), icon TEXT NOT NULL,
                  color INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                  parent_id INTEGER REFERENCES categories(id)
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val at = LocalDateTime.of(2026, 9, 1, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @AfterTest
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun seedVersionEight() {
        VersionEight(context, dbName).use { helper ->
            val db = helper.writableDatabase
            for ((id, name, kind) in listOf(Triple(1, "Cash", "cash"), Triple(2, "ICICI Credit Card", "card"))) {
                db.insertOrThrow(
                    "accounts",
                    null,
                    ContentValues().apply {
                        put("id", id); put("name", name); put("kind", kind); put("opening_balance", -845_000)
                        put("created_at", at); put("updated_at", at)
                    },
                )
            }
            db.version = 8
        }
    }

    @Test
    fun `upgrading keeps every account as it was, with no billing days`() {
        seedVersionEight()
        val upgraded = FinanceDatabase(context, name = dbName)
        try {
            val db = upgraded.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)
            val rows = db.rawQuery("SELECT name, kind, statement_day, due_day FROM accounts ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(listOf(c.getString(0), c.getString(1), c.isNull(2), c.isNull(3))) }
            }
            assertEquals(listOf(listOf("Cash", "cash", true, true), listOf("ICICI Credit Card", "card", true, true)), rows)
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun `a card can be given its days on the upgraded database`() = runBlocking<Unit> {
        seedVersionEight()
        val repo = FinanceRepository(FinanceDatabase(context, name = dbName), dispatcher = Dispatchers.Unconfined)
        try {
            val card = repo.accounts().first { it.name == "ICICI Credit Card" }
            assertNull(card.statementDay)
            assertNull(card.dueDay)
            repo.saveAccount(card.copy(statementDay = 5, dueDay = 20))
            val saved = repo.accounts().first { it.name == "ICICI Credit Card" }
            assertEquals(5, saved.statementDay)
            assertEquals(20, saved.dueDay)
        } finally {
            repo.close()
        }
    }
}
