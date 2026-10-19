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
 * An account can say what its number ends with and which Wallet card is its card, so a shared receipt that shows those digits
 * lands on the right account. They are kept with the account, are four digits or nothing, belong to no cash account, and a
 * card is the card of one account at most - and they survive a backup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountNumberRepositoryTest {
    private val now = LocalDateTime.of(2026, 10, 12, 12, 0)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private suspend fun save(
        name: String,
        kind: AccountKind = AccountKind.Bank,
        numberEnding: String? = null,
        cardId: String? = null,
        numberStart: String? = null,
    ): Account = repo.saveAccount(
        Account(name = name, kind = kind, numberEnding = numberEnding, numberStart = numberStart, cardId = cardId, createdAt = now, updatedAt = now),
    )

    private suspend fun stored(id: Long?): Account = repo.accounts().first { it.id == id }

    @Test
    fun `an account keeps its number's ending and its card, and one without has neither`() = runBlocking<Unit> {
        val salary = save("HDFC Salary", numberEnding = "3588")
        val card = save("HDFC Credit", AccountKind.Card, numberEnding = "4455", cardId = "wallet-1")
        assertEquals("3588", stored(salary.id).numberEnding)
        assertNull(stored(salary.id).cardId)
        assertEquals("4455", stored(card.id).numberEnding)
        assertEquals("wallet-1", stored(card.id).cardId)

        val plain = save("Plain")
        assertNull(stored(plain.id).numberEnding)
        assertNull(stored(plain.id).cardId)
    }

    @Test
    fun `the ending is four digits or nothing`() = runBlocking<Unit> {
        for (bad in listOf("358", "35888", "35 88", "abcd", "35-8", "٣٥٨٨")) {
            val e = assertFailsWith<FinanceValidationException>(bad) { save("Salary", numberEnding = bad) }
            assertTrue("4 digits" in e.message.orEmpty(), e.message)
        }
        assertTrue(repo.accounts().none { it.name == "Salary" })

        // Blank is no ending; spaces around four digits are trimmed.
        assertNull(stored(save("Blank", numberEnding = "  ").id).numberEnding)
        assertEquals("0042", stored(save("Padded", numberEnding = " 0042 ").id).numberEnding)
    }

    @Test
    fun `cash has neither a number nor a card`() = runBlocking<Unit> {
        val cash = save("Wallet cash", AccountKind.Cash, numberEnding = "3588", cardId = "wallet-1")
        assertNull(stored(cash.id).numberEnding)
        assertNull(stored(cash.id).cardId)

        // And an account that becomes cash loses them.
        val bank = save("Bank", numberEnding = "3588", cardId = "wallet-2")
        repo.saveAccount(stored(bank.id).copy(kind = AccountKind.Cash))
        assertNull(stored(bank.id).numberEnding)
        assertNull(stored(bank.id).cardId)
    }

    @Test
    fun `a card is the card of one account at most`() = runBlocking<Unit> {
        val first = save("HDFC Credit", AccountKind.Card, cardId = "wallet-1")
        val e = assertFailsWith<FinanceValidationException> { save("Duplicate", AccountKind.Card, cardId = "wallet-1") }
        assertTrue("HDFC Credit" in e.message.orEmpty(), e.message)
        assertTrue(repo.accounts().none { it.name == "Duplicate" })

        // The account itself may be saved again with it, or have it taken away.
        repo.saveAccount(stored(first.id).copy(name = "HDFC Credit Card"))
        assertEquals("wallet-1", stored(first.id).cardId)
        repo.saveAccount(stored(first.id).copy(cardId = null))
        assertNull(stored(first.id).cardId)

        // Once it is free another account can have it.
        assertEquals("wallet-1", stored(save("Taken over", AccountKind.Card, cardId = "wallet-1").id).cardId)
    }

    @Test
    fun `an archived account does not hold on to its card`() = runBlocking<Unit> {
        val old = save("Old card", AccountKind.Card, cardId = "wallet-1")
        repo.setAccountArchived(old.id!!, true)
        assertEquals("wallet-1", stored(old.id).cardId)
        assertEquals("wallet-1", stored(save("New card", AccountKind.Card, cardId = "wallet-1").id).cardId)
    }

    @Test
    fun `a backup carries the ending and the card`() = runBlocking<Unit> {
        save("HDFC Salary", numberEnding = "3588")
        save("HDFC Credit", AccountKind.Card, numberEnding = "4455", cardId = "wallet-1")
        save("Plain")
        val read = FinanceBackupCodec.decode(FinanceBackupCodec.encode(repo.snapshot(), now))
        val snapshot = assertNotNull(read.snapshot, read.errors.toString())
        assertEquals("3588", snapshot.accounts.first { it.name == "HDFC Salary" }.numberEnding)
        assertEquals("wallet-1", snapshot.accounts.first { it.name == "HDFC Credit" }.cardId)
        assertNull(snapshot.accounts.first { it.name == "Plain" }.numberEnding)

        repo.restore(snapshot)
        assertEquals("3588", repo.accounts().first { it.name == "HDFC Salary" }.numberEnding)
        assertEquals("4455", repo.accounts().first { it.name == "HDFC Credit" }.numberEnding)
        assertEquals("wallet-1", repo.accounts().first { it.name == "HDFC Credit" }.cardId)
    }

    @Test
    fun `a backup from before numbers existed restores the accounts with none`() = runBlocking<Unit> {
        save("HDFC Salary", numberEnding = "3588", cardId = "wallet-1")
        val older = FinanceBackupCodec.encode(repo.snapshot(), now).replace(Regex(",\\s*\"(numberEnding|cardId)\": [^,\\n}]+"), "")
        assertTrue("numberEnding" !in older && "cardId" !in older)
        val snapshot = assertNotNull(FinanceBackupCodec.decode(older).snapshot)
        val salary = snapshot.accounts.first { it.name == "HDFC Salary" }
        assertNull(salary.numberEnding)
        assertNull(salary.cardId)
    }

    @Test
    fun `a backup with a number that is not four digits, or on cash, is rejected`() = runBlocking<Unit> {
        val bank = save("HDFC Salary", numberEnding = "3588")
        val cash = save("Cash", AccountKind.Cash)
        val snapshot = repo.snapshot()

        fun problems(change: (Account) -> Account) =
            FinanceBackupCodec.validate(snapshot.copy(accounts = snapshot.accounts.map { if (it.id == bank.id || it.id == cash.id) change(it) else it }))

        assertTrue(problems { if (it.id == bank.id) it.copy(numberEnding = "358") else it }.any { "4 digits" in it })
        assertTrue(problems { if (it.id == cash.id) it.copy(cardId = "wallet-1") else it }.any { "cash" in it })
        assertTrue(FinanceBackupCodec.validate(snapshot).isEmpty())

        val text = FinanceBackupCodec.encode(snapshot, now).replace("\"numberEnding\": \"3588\"", "\"numberEnding\": \"35x8\"")
        val read = FinanceBackupCodec.decode(text)
        assertNull(read.snapshot)
        assertTrue(read.errors.any { "4 digits" in it }, read.errors.toString())
    }
    // ----- the start of a card's number (its first six digits) -----

    @Test
    fun `a card keeps the first six digits of its number beside the last four`() = runBlocking<Unit> {
        val card = save("HDFC Credit", AccountKind.Card, numberEnding = "1111", numberStart = "411111")
        assertEquals("411111", stored(card.id).numberStart)
        assertEquals("1111", stored(card.id).numberEnding)

        // It is optional, and saving again without it clears it.
        val plain = save("Plain card", AccountKind.Card, numberEnding = "4455")
        assertNull(stored(plain.id).numberStart)
        repo.saveAccount(stored(card.id).copy(numberStart = null))
        assertNull(stored(card.id).numberStart)
        assertEquals("1111", stored(card.id).numberEnding)
    }

    @Test
    fun `the start is six digits or nothing`() = runBlocking<Unit> {
        for (bad in listOf("41111", "4111111", "41 111", "abcdef", "41111x", "٤١١١١١")) {
            val e = assertFailsWith<FinanceValidationException>(bad) { save("Card", AccountKind.Card, numberStart = bad) }
            assertTrue("first 6 digits" in e.message.orEmpty(), e.message)
        }
        assertTrue(repo.accounts().none { it.name == "Card" })

        // Blank is no start; spaces around six digits are trimmed.
        assertNull(stored(save("Blank", AccountKind.Card, numberStart = "  ").id).numberStart)
        assertEquals("004200", stored(save("Padded", AccountKind.Card, numberStart = " 004200 ").id).numberStart)
    }

    @Test
    fun `cash has no number start either`() = runBlocking<Unit> {
        val cash = save("Wallet cash", AccountKind.Cash, numberStart = "411111")
        assertNull(stored(cash.id).numberStart)

        val bank = save("Bank", numberStart = "411111")
        repo.saveAccount(stored(bank.id).copy(kind = AccountKind.Cash))
        assertNull(stored(bank.id).numberStart)
    }

    @Test
    fun `a backup carries the start, and an older backup without it still restores`() = runBlocking<Unit> {
        save("HDFC Credit", AccountKind.Card, numberEnding = "1111", numberStart = "411111")
        save("HDFC Salary", numberEnding = "3588")
        val text = FinanceBackupCodec.encode(repo.snapshot(), now)
        val read = FinanceBackupCodec.decode(text)
        val snapshot = assertNotNull(read.snapshot, read.errors.toString())
        assertEquals("411111", snapshot.accounts.first { it.name == "HDFC Credit" }.numberStart)
        assertNull(snapshot.accounts.first { it.name == "HDFC Salary" }.numberStart)

        repo.restore(snapshot)
        assertEquals("411111", repo.accounts().first { it.name == "HDFC Credit" }.numberStart)
        assertEquals("1111", repo.accounts().first { it.name == "HDFC Credit" }.numberEnding)

        // A backup from before the start existed has no such field at all.
        val older = text.replace(Regex(",\\s*\"numberStart\": [^,\\n}]+"), "")
        assertTrue("numberStart" !in older, "the field is gone from the older file")
        val olderSnapshot = assertNotNull(FinanceBackupCodec.decode(older).snapshot)
        val card = olderSnapshot.accounts.first { it.name == "HDFC Credit" }
        assertNull(card.numberStart)
        assertEquals("1111", card.numberEnding)
        repo.restore(olderSnapshot)
        assertNull(repo.accounts().first { it.name == "HDFC Credit" }.numberStart)
        assertEquals("1111", repo.accounts().first { it.name == "HDFC Credit" }.numberEnding)
    }

    @Test
    fun `a backup with a start that is not six digits, or on cash, is rejected`() = runBlocking<Unit> {
        val card = save("HDFC Credit", AccountKind.Card, numberEnding = "1111", numberStart = "411111")
        val cash = save("Cash", AccountKind.Cash)
        val snapshot = repo.snapshot()

        fun problems(change: (Account) -> Account) =
            FinanceBackupCodec.validate(snapshot.copy(accounts = snapshot.accounts.map { if (it.id == card.id || it.id == cash.id) change(it) else it }))

        assertTrue(problems { if (it.id == card.id) it.copy(numberStart = "4111") else it }.any { "6 digits" in it })
        assertTrue(problems { if (it.id == cash.id) it.copy(numberStart = "411111") else it }.any { "cash" in it })
        assertTrue(FinanceBackupCodec.validate(snapshot).isEmpty())

        val text = FinanceBackupCodec.encode(snapshot, now).replace("\"numberStart\": \"411111\"", "\"numberStart\": \"41x111\"")
        val read = FinanceBackupCodec.decode(text)
        assertNull(read.snapshot)
        assertTrue(read.errors.any { "6 digits" in it }, read.errors.toString())
    }

}

/**
 * A finance database as the previous release left it (version 9, no account numbers) opened by this one: every account is
 * still there, exactly as it was and with neither, and an account can be given them from then on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountNumberMigrationTest {
    private val dbName = "account_number_migration_test.db"
    private lateinit var context: Context

    /** The version 9 schema of the tables the open of the database touches: accounts without the number columns. */
    private class VersionNine(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 9) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE accounts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, kind TEXT NOT NULL,
                  opening_balance INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                  bank_key TEXT, credit_limit INTEGER, statement_day INTEGER, due_day INTEGER
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

    /** The version 11 schema: account numbers (ending and card) but not yet the number's start. */
    private class VersionEleven(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 11) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE accounts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, kind TEXT NOT NULL,
                  opening_balance INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                  bank_key TEXT, credit_limit INTEGER, statement_day INTEGER, due_day INTEGER,
                  number_ending TEXT, card_id TEXT
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

    private fun seedVersionNine() {
        VersionNine(context, dbName).use { helper ->
            val db = helper.writableDatabase
            for ((id, name, kind) in listOf(Triple(1, "Cash", "cash"), Triple(2, "ICICI Credit Card", "card"))) {
                db.insertOrThrow(
                    "accounts",
                    null,
                    ContentValues().apply {
                        put("id", id); put("name", name); put("kind", kind); put("opening_balance", -845_000)
                        put("statement_day", 5); put("created_at", at); put("updated_at", at)
                    },
                )
            }
            db.version = 9
        }
    }

    @Test
    fun `upgrading keeps every account as it was, with no number and no card`() {
        seedVersionNine()
        val upgraded = FinanceDatabase(context, name = dbName)
        try {
            val db = upgraded.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)
            val rows = db.rawQuery("SELECT name, kind, statement_day, number_ending, card_id FROM accounts ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(listOf(c.getString(0), c.getString(1), c.getInt(2), c.isNull(3), c.isNull(4))) }
            }
            assertEquals(
                listOf(listOf("Cash", "cash", 5, true, true), listOf("ICICI Credit Card", "card", 5, true, true)),
                rows,
            )
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun `from version 11 the account keeps its ending and card, and gains an empty start`() = runBlocking<Unit> {
        VersionEleven(context, dbName).use { helper ->
            helper.writableDatabase.apply {
                insertOrThrow(
                    "accounts",
                    null,
                    ContentValues().apply {
                        put("name", "ICICI Credit Card"); put("kind", "card"); put("opening_balance", 0)
                        put("created_at", at); put("updated_at", at); put("number_ending", "4455"); put("card_id", "wallet-1")
                    },
                )
                version = 11
            }
        }
        val repo = FinanceRepository(FinanceDatabase(context, name = dbName), dispatcher = Dispatchers.Unconfined)
        try {
            val card = repo.accounts().single()
            assertEquals("4455", card.numberEnding)
            assertEquals("wallet-1", card.cardId)
            assertNull(card.numberStart)
            repo.saveAccount(card.copy(numberStart = "652850"))
            assertEquals("652850", repo.accounts().single().numberStart)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `upgrading adds the number start column, empty for every account`() {
        seedVersionNine()
        val upgraded = FinanceDatabase(context, name = dbName)
        try {
            assertTrue(FinanceDatabase.VERSION >= 12, "the start column arrived with version 12")
            val db = upgraded.writableDatabase
            val starts = db.rawQuery("SELECT number_start FROM accounts ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(c.isNull(0)) }
            }
            assertEquals(listOf(true, true), starts)
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun `a card account on the upgraded database can be given its start`() = runBlocking<Unit> {
        seedVersionNine()
        val repo = FinanceRepository(FinanceDatabase(context, name = dbName), dispatcher = Dispatchers.Unconfined)
        try {
            val card = repo.accounts().first { it.name == "ICICI Credit Card" }
            assertNull(card.numberStart)
            repo.saveAccount(card.copy(numberStart = "411111", numberEnding = "1111"))
            val saved = repo.accounts().first { it.name == "ICICI Credit Card" }
            assertEquals("411111", saved.numberStart)
            assertEquals("1111", saved.numberEnding)
            assertEquals(5, saved.statementDay)
        } finally {
            repo.close()
        }
    }

    @Test
    fun `an account can be given its number and card on the upgraded database`() = runBlocking<Unit> {
        seedVersionNine()
        val repo = FinanceRepository(FinanceDatabase(context, name = dbName), dispatcher = Dispatchers.Unconfined)
        try {
            val card = repo.accounts().first { it.name == "ICICI Credit Card" }
            assertNull(card.numberEnding)
            assertNull(card.cardId)
            repo.saveAccount(card.copy(numberEnding = "4455", cardId = "wallet-1"))
            val saved = repo.accounts().first { it.name == "ICICI Credit Card" }
            assertEquals("4455", saved.numberEnding)
            assertEquals("wallet-1", saved.cardId)
            assertEquals(5, saved.statementDay)
        } finally {
            repo.close()
        }
    }
}
