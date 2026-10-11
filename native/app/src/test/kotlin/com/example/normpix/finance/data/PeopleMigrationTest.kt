package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Person
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A finance database as the previous release left it (version 4, no people) opened by this one: every account, category and
 * transaction is still there, untouched and linked to no one, and people can be added and linked from then on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PeopleMigrationTest {
    private val dbName = "people_migration_test.db"
    private lateinit var context: Context

    /** The version 4 schema of the tables people touch: transactions without a person, and the accounts and categories they use. */
    private class VersionFour(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 4) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

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
            db.execSQL(
                """
                CREATE TABLE transactions (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  type TEXT NOT NULL CHECK (type IN ('expense', 'income', 'transfer')),
                  amount INTEGER NOT NULL CHECK (amount > 0),
                  account_id INTEGER NOT NULL REFERENCES accounts(id),
                  to_account_id INTEGER REFERENCES accounts(id),
                  category_id INTEGER REFERENCES categories(id),
                  occurred_at INTEGER NOT NULL, payee TEXT, note TEXT, recurring_id INTEGER,
                  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE recurring_rules (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  type TEXT NOT NULL CHECK (type IN ('expense', 'income', 'transfer')),
                  amount INTEGER NOT NULL CHECK (amount > 0),
                  account_id INTEGER NOT NULL REFERENCES accounts(id),
                  to_account_id INTEGER REFERENCES accounts(id),
                  category_id INTEGER REFERENCES categories(id),
                  payee TEXT, note TEXT,
                  frequency TEXT NOT NULL, interval INTEGER NOT NULL DEFAULT 1,
                  start_date INTEGER NOT NULL, end_date INTEGER, next_date INTEGER NOT NULL,
                  active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private val at = LocalDateTime.of(2026, 3, 1, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @AfterTest
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun seedVersionFour() {
        VersionFour(context, dbName).use { helper ->
            val db = helper.writableDatabase
            db.insertOrThrow(
                "accounts",
                null,
                ContentValues().apply {
                    put("id", 1); put("name", "Cash"); put("kind", "cash"); put("opening_balance", 100_000)
                    put("created_at", at); put("updated_at", at)
                },
            )
            db.insertOrThrow(
                "categories",
                null,
                ContentValues().apply {
                    put("id", 1); put("name", "Food"); put("type", "expense"); put("icon", "restaurant")
                    put("created_at", at); put("updated_at", at)
                },
            )
            for ((id, amount) in listOf(1L to 45_000L, 2L to 12_000L)) {
                db.insertOrThrow(
                    "transactions",
                    null,
                    ContentValues().apply {
                        put("id", id); put("type", "expense"); put("amount", amount); put("account_id", 1); put("category_id", 1)
                        put("occurred_at", at); put("created_at", at); put("updated_at", at)
                    },
                )
            }
            db.version = 4
        }
    }

    private fun SQLiteDatabase.count(table: String, where: String = "1 = 1"): Long =
        rawQuery("SELECT COUNT(*) FROM $table WHERE $where", null).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun `upgrading adds people and keeps every transaction exactly as it was, linked to no one`() {
        seedVersionFour()
        val upgraded = FinanceDatabase(context, name = dbName)
        try {
            val db = upgraded.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)
            assertEquals(2L, db.count(FinanceDatabase.TRANSACTIONS), "nothing deleted")
            assertEquals(0L, db.count(FinanceDatabase.TRANSACTIONS, "person_id IS NOT NULL"), "no one is invented")
            assertEquals(0L, db.count(FinanceDatabase.PEOPLE))
            val amounts = db.rawQuery("SELECT amount FROM transactions ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0)) }
            }
            assertEquals(listOf(45_000L, 12_000L), amounts)
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun `the upgraded database works - people can be added, linked, totalled and deleted`() = runBlocking {
        seedVersionFour()
        val repo = FinanceRepository(FinanceDatabase(context, name = dbName), dispatcher = Dispatchers.Unconfined)
        try {
            val mom = repo.savePerson(Person(name = "Mom", createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
            val existing = repo.transactionById(1)!!
            assertNull(existing.personId)
            repo.saveTransaction(existing.copy(personId = mom.id))

            val spending = repo.peopleSpending(null)
            assertEquals(57_000L, spending.totalExpense)
            assertEquals(45_000L, spending.of(mom.id!!)!!.amount)
            assertEquals(100_000L - 57_000L, repo.accountBalances().getValue(1L), "the balance is what it was")

            // The guard that only an expense can name a person holds on a migrated database too.
            val db = FinanceDatabase(context, name = dbName).writableDatabase
            assertFailsWith<Exception> {
                db.insertOrThrow(
                    FinanceDatabase.TRANSACTIONS,
                    null,
                    ContentValues().apply {
                        put("type", "income"); put("amount", 1); put("account_id", 1); put("category_id", 1)
                        put("occurred_at", at); put("created_at", at); put("updated_at", at); put("person_id", mom.id!!)
                    },
                )
            }

            assertEquals(1, repo.deletePerson(mom.id!!))
            assertEquals(2, repo.transactions(TransactionQuery()).items.size)
            assertTrue(repo.people().isEmpty())
        } finally {
            repo.close()
        }
    }

    @Test
    fun `an old repeating payment keeps working and can be given a person after the upgrade`() = runBlocking {
        seedVersionFour()
        // A rule as the previous release left it - there is no person column on it yet.
        VersionFour(context, dbName).use { helper ->
            helper.writableDatabase.insertOrThrow(
                "recurring_rules",
                null,
                ContentValues().apply {
                    put("type", "expense"); put("amount", 99_900); put("account_id", 1); put("category_id", 1)
                    put("frequency", "monthly"); put("start_date", at); put("next_date", at + 86_400_000L * 40)
                    put("created_at", at); put("updated_at", at)
                },
            )
        }
        val repo = FinanceRepository(FinanceDatabase(context, name = dbName), dispatcher = Dispatchers.Unconfined)
        try {
            val rule = repo.recurringRules().single()
            assertNull(rule.personId, "no one is invented")
            assertEquals(99_900L, rule.amount)
            val mom = repo.savePerson(Person(name = "Mom", createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
            repo.saveRecurringRule(rule.copy(personId = mom.id))
            assertEquals(mom.id, repo.recurringRules().single().personId)
            // The only-an-expense guard is on the migrated rules table too.
            val db = FinanceDatabase(context, name = dbName).writableDatabase
            assertFailsWith<Exception> {
                db.insertOrThrow(
                    FinanceDatabase.RECURRING_RULES,
                    null,
                    ContentValues().apply {
                        put("type", "income"); put("amount", 1); put("account_id", 1); put("category_id", 1)
                        put("frequency", "monthly"); put("start_date", at); put("next_date", at)
                        put("created_at", at); put("updated_at", at); put("person_id", mom.id!!)
                    },
                )
            }
            Unit
        } finally {
            repo.close()
        }
    }

    @Test
    fun `opening the upgraded database again changes nothing`() = runBlocking {
        seedVersionFour()
        FinanceDatabase(context, name = dbName).use { it.writableDatabase }
        val again = FinanceDatabase(context, name = dbName)
        try {
            assertEquals(FinanceDatabase.VERSION, again.writableDatabase.version)
            assertEquals(2L, again.writableDatabase.count(FinanceDatabase.TRANSACTIONS))
        } finally {
            again.close()
        }
    }
}
