package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A finance database as the previous release left it (version 1, accounts without banks) opened by this one: every
 * account, category and transaction is still there, untouched, and the accounts whose names start with a bank are linked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BankMigrationTest {
    private val dbName = "bank_migration_test.db"
    private lateinit var context: Context

    /** The version 1 schema of the tables a bank touches: accounts without banks, and the history that refers to them. */
    private class VersionOne(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE accounts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  opening_balance INTEGER NOT NULL DEFAULT 0,
                  archived INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE categories (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  type TEXT NOT NULL CHECK (type IN ('expense', 'income')),
                  icon TEXT NOT NULL,
                  color INTEGER NOT NULL DEFAULT 0,
                  archived INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
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
                  occurred_at INTEGER NOT NULL,
                  payee TEXT,
                  note TEXT,
                  recurring_id INTEGER,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
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

    private fun SQLiteDatabase.addAccount(id: Long, name: String, kind: String, opening: Long = 0) {
        insertOrThrow(
            FinanceDatabase.ACCOUNTS,
            null,
            ContentValues().apply {
                put("id", id)
                put("name", name)
                put("kind", kind)
                put("opening_balance", opening)
                put("archived", 0)
                put("sort_order", id)
                put("created_at", at)
                put("updated_at", at)
            },
        )
    }

    private fun seedVersionOne() {
        VersionOne(context, dbName).use { helper ->
            val db = helper.writableDatabase
            db.addAccount(1, "Cash", "cash", 2_500)
            db.addAccount(2, "HDFC Savings", "bank", 148_000)
            db.addAccount(3, "ICICI Credit Card", "card", -8_450)
            db.addAccount(4, "Paytm Wallet", "wallet")
            db.addAccount(5, "Savings for Axis bike", "savings")
            db.addAccount(6, "Rainy day fund", "other")
            db.insertOrThrow(
                FinanceDatabase.CATEGORIES,
                null,
                ContentValues().apply {
                    put("id", 1)
                    put("name", "Food")
                    put("type", "expense")
                    put("icon", "food")
                    put("created_at", at)
                    put("updated_at", at)
                },
            )
            db.insertOrThrow(
                FinanceDatabase.TRANSACTIONS,
                null,
                ContentValues().apply {
                    put("type", "expense")
                    put("amount", 45_000)
                    put("account_id", 2)
                    put("category_id", 1)
                    put("occurred_at", at)
                    put("created_at", at)
                    put("updated_at", at)
                },
            )
            db.version = 1
        }
    }

    private fun SQLiteDatabase.count(table: String, where: String = "1 = 1"): Long =
        rawQuery("SELECT COUNT(*) FROM $table WHERE $where", null).use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun upgradingLinksTheAccountsThatStartWithABankAndKeepsEverythingElse() {
        seedVersionOne()
        val upgraded = FinanceDatabase(context, name = dbName)
        try {
            val db = upgraded.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)

            val rows = db.rawQuery("SELECT id, name, kind, opening_balance, bank_key FROM accounts ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4))) }
            }
            assertEquals(
                listOf(
                    listOf(1L, "Cash", "cash", 2_500L, null),
                    listOf(2L, "HDFC Savings", "bank", 148_000L, "hdfc"),
                    listOf(3L, "ICICI Credit Card", "card", -8_450L, "icici"),
                    listOf(4L, "Paytm Wallet", "wallet", 0L, "paytm"),
                    listOf(5L, "Savings for Axis bike", "savings", 0L, null), // mentions Axis but is not Axis's: left for the user
                    listOf(6L, "Rainy day fund", "other", 0L, null),
                ),
                rows,
            )
            assertEquals(1L, db.count(FinanceDatabase.TRANSACTIONS), "nothing deleted")
            // The user's own category is as it was (the ready-made families a later release added come alongside it).
            assertEquals(1L, db.count(FinanceDatabase.CATEGORIES, "created_at = $at"))
            assertEquals(1L, db.count(FinanceDatabase.CATEGORIES, "id = 1 AND name = 'Food' AND parent_id IS NULL"), "it stays top-level")
            assertEquals(0L, db.count(FinanceDatabase.BANKS), "no bank is invented")
            assertEquals(0L, db.count(FinanceDatabase.ACCOUNTS, "credit_limit IS NOT NULL"), "no limit is invented")
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun theUpgradedDatabaseWorksAndOpeningItAgainChangesNothing() = runBlocking {
        seedVersionOne()
        FinanceDatabase(context, name = dbName).use { it.writableDatabase }

        val repo = FinanceRepository(FinanceDatabase(context, name = dbName), dispatcher = Dispatchers.Unconfined)
        try {
            val accounts = repo.accounts()
            assertEquals(6, accounts.size)
            assertEquals("hdfc", accounts.first { it.name == "HDFC Savings" }.bankKey)
            assertNull(accounts.first { it.name == "Cash" }.bankKey)

            // The user can now give the left-over accounts a bank, and add their own.
            val key = repo.bankKeyFor("My Credit Union")
            repo.saveAccount(accounts.first { it.name == "Rainy day fund" }.copy(bankKey = key))
            assertEquals(key, repo.accounts().first { it.name == "Rainy day fund" }.bankKey)
            assertEquals(listOf(key), repo.customBanks().map { it.key })

            // The ledger itself is as it was: the one expense is still on the HDFC account.
            val history = repo.transactions(TransactionQuery()).items
            assertEquals(1, history.size)
            assertEquals(2L, history.single().accountId)
            assertTrue(repo.accountBalances().getValue(2L) == 148_000L - 45_000L)
        } finally {
            repo.close()
        }
    }
}
