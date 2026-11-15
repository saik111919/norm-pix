package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.LoanTerms
import com.example.normpix.finance.domain.emiFor
import com.example.normpix.storage.DatabaseEngine
import com.example.normpix.storage.rawQuery
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Version 14 (2026-11-15) gives an account five empty loan columns, and nothing else: a version 13 database - hand-built here, with
 * accounts, a card, transactions of every type, a category, a person, a repeating payment, a budget, a bank and the ledger settings -
 * is opened by this release and every row and value in it is exactly as it was, the new columns empty, and a loan can then be saved
 * and read back. A new install has the columns from the start.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoanMigrationTest {
    private val dbName = "loan_migration_test.db"
    private lateinit var context: Context

    private val day = LocalDateTime.of(2026, 9, 1, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** The finance schema exactly as version 13 left it: the accounts table without the loan columns. */
    private class VersionThirteen(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 13) {
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
                  updated_at INTEGER NOT NULL,
                  bank_key TEXT,
                  credit_limit INTEGER,
                  statement_day INTEGER,
                  due_day INTEGER,
                  number_ending TEXT,
                  card_id TEXT,
                  number_start TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE TABLE banks (key TEXT PRIMARY KEY, name TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL(
                """
                CREATE TABLE people (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, emoji TEXT, color INTEGER NOT NULL DEFAULT 0,
                  archived INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
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
                CREATE TABLE recurring_rules (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  type TEXT NOT NULL CHECK (type IN ('expense', 'income', 'transfer')),
                  amount INTEGER NOT NULL CHECK (amount > 0),
                  account_id INTEGER NOT NULL REFERENCES accounts(id),
                  to_account_id INTEGER REFERENCES accounts(id),
                  category_id INTEGER REFERENCES categories(id),
                  payee TEXT, note TEXT,
                  frequency TEXT NOT NULL CHECK (frequency IN ('daily', 'weekly', 'monthly', 'yearly')),
                  interval INTEGER NOT NULL DEFAULT 1 CHECK (interval >= 1),
                  start_date INTEGER NOT NULL, end_date INTEGER, next_date INTEGER NOT NULL,
                  active INTEGER NOT NULL DEFAULT 1, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                  person_id INTEGER REFERENCES people(id) ON DELETE SET NULL
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
                  occurred_at INTEGER NOT NULL, payee TEXT, note TEXT,
                  recurring_id INTEGER REFERENCES recurring_rules(id) ON DELETE SET NULL,
                  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                  person_id INTEGER REFERENCES people(id) ON DELETE SET NULL,
                  picture TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE budgets (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  category_id INTEGER REFERENCES categories(id) ON DELETE CASCADE,
                  amount INTEGER CHECK (amount IS NULL OR amount > 0),
                  effective_month INTEGER NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @AfterTest
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun SQLiteDatabase.add(table: String, vararg values: Pair<String, Any?>) {
        insertOrThrow(
            table,
            null,
            ContentValues().apply {
                for ((key, value) in values) {
                    when (value) {
                        null -> putNull(key)
                        is Long -> put(key, value)
                        is Int -> put(key, value)
                        is String -> put(key, value)
                        else -> error("$key: ${value::class}")
                    }
                }
            },
        )
    }

    private fun seedVersionThirteen() {
        VersionThirteen(context, dbName).use { helper ->
            val db = helper.writableDatabase
            db.add("banks", "key" to "custom-1", "name" to "My Credit Union", "created_at" to day)
            db.add(
                "accounts", "id" to 1, "name" to "Cash", "kind" to "cash", "opening_balance" to 250_000L, "sort_order" to 0,
                "created_at" to day, "updated_at" to day,
            )
            db.add(
                "accounts", "id" to 2, "name" to "HDFC Credit", "kind" to "card", "opening_balance" to -845_000L, "sort_order" to 1,
                "created_at" to day, "updated_at" to day, "bank_key" to "hdfc", "credit_limit" to 5_000_000L, "statement_day" to 5,
                "due_day" to 20, "number_ending" to "3588", "card_id" to "card-1", "number_start" to "652850",
            )
            db.add(
                "accounts", "id" to 3, "name" to "Old Car Loan", "kind" to "other", "opening_balance" to -30_000_000L, "archived" to 1,
                "sort_order" to 2, "created_at" to day, "updated_at" to day, "bank_key" to "custom-1",
            )
            db.add("people", "id" to 1, "name" to "Mom", "emoji" to "👩", "color" to 6, "created_at" to day, "updated_at" to day)
            db.add(
                "categories", "id" to 1, "name" to "Food", "type" to "expense", "icon" to "restaurant", "color" to 3,
                "sort_order" to 1, "created_at" to day, "updated_at" to day,
            )
            db.add(
                "categories", "id" to 2, "name" to "Salary", "type" to "income", "icon" to "salary", "sort_order" to 2,
                "created_at" to day, "updated_at" to day,
            )
            db.add(
                "recurring_rules", "id" to 1, "type" to "expense", "amount" to 64_900L, "account_id" to 2, "category_id" to 1,
                "payee" to "Netflix", "frequency" to "monthly", "interval" to 1, "start_date" to day, "next_date" to day + 86_400_000L,
                "created_at" to day, "updated_at" to day,
            )
            db.add(
                "transactions", "id" to 1, "type" to "expense", "amount" to 45_000L, "account_id" to 1, "category_id" to 1,
                "occurred_at" to day, "payee" to "Lunch", "note" to "with Mom", "created_at" to day, "updated_at" to day, "person_id" to 1,
            )
            db.add(
                "transactions", "id" to 2, "type" to "income", "amount" to 9_500_000L, "account_id" to 1, "category_id" to 2,
                "occurred_at" to day + 1_000L, "payee" to "Acme", "created_at" to day, "updated_at" to day,
            )
            db.add(
                "transactions", "id" to 3, "type" to "transfer", "amount" to 845_000L, "account_id" to 1, "to_account_id" to 2,
                "occurred_at" to day + 2_000L, "note" to "Card bill payment", "created_at" to day, "updated_at" to day,
            )
            db.add(
                "transactions", "id" to 4, "type" to "expense", "amount" to 64_900L, "account_id" to 2, "category_id" to 1,
                "occurred_at" to day + 3_000L, "payee" to "Netflix", "recurring_id" to 1, "created_at" to day, "updated_at" to day,
                "picture" to "pic-00000000-0000-0000-0000-000000000000.jpg",
            )
            db.add("budgets", "id" to 1, "category_id" to 1, "amount" to 1_200_000L, "effective_month" to 202609, "created_at" to day, "updated_at" to day)
            db.add("meta", "key" to "currency", "value" to "INR")
            db.add("meta", "key" to "default_account_id", "value" to "1")
        }
    }

    /** Every row of this cursor, every column as it is stored (columns named in [skip] left out), as a map by column name. */
    private fun Cursor.allRows(skip: Set<String> = emptySet()): List<Map<String, Any?>> = use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    buildMap {
                        for (i in 0 until cursor.columnCount) {
                            val name = cursor.getColumnName(i)
                            if (name in skip) continue
                            put(
                                name,
                                when (cursor.getType(i)) {
                                    Cursor.FIELD_TYPE_NULL -> null
                                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                    else -> cursor.getString(i)
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun orderOf(table: String) = if (table == "banks" || table == "meta") "key" else "id"

    private fun SupportSQLiteDatabase.rows(table: String, skip: Set<String> = emptySet()): List<Map<String, Any?>> =
        rawQuery("SELECT * FROM $table ORDER BY ${orderOf(table)}", null).allRows(skip)

    private val loanColumns = setOf("loan_principal", "loan_rate_bp", "loan_tenure_months", "loan_emi", "loan_start")

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): List<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    @Test
    fun everyRowSurvivesTheUpgradeExactlyAsItWas_andTheNewColumnsAreEmpty() {
        seedVersionThirteen()
        val tables = listOf("accounts", "banks", "people", "categories", "recurring_rules", "transactions", "budgets", "meta")
        // The rows before: read through the version 13 helper itself.
        val before = VersionThirteen(context, dbName).use { helper ->
            val db = helper.writableDatabase
            tables.associateWith { table -> db.rawQuery("SELECT * FROM $table ORDER BY ${orderOf(table)}", null).allRows() }
        }

        val upgraded = FinanceDatabase(context, dbName)
        try {
            val db = upgraded.writableDatabase
            // Opened at this release's version (14 when loans came; later steps, such as version 15's tags, run on after it).
            assertEquals(FinanceDatabase.VERSION, db.version, "opened at the current version")
            for (table in tables) {
                // (Version 17 added the split group column to transactions, empty on every row: set aside with the loan columns.)
                assertEquals(before.getValue(table), db.rows(table, skip = loanColumns + "split_group"), "$table is exactly as it was")
            }
            // The five columns are there, and empty for every account.
            val columns = columnsOf(db, "accounts")
            for (column in loanColumns) assertTrue(column in columns, "$column was added")
            val accounts = db.rows("accounts")
            assertEquals(3, accounts.size)
            for (row in accounts) for (column in loanColumns) assertNull(row[column], "${row["name"]}: $column is empty")
            // Nothing else was added to the accounts table, and no other table changed shape.
            assertEquals(15 + 5, columns.size)
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun theMigrationStepAloneAddsTheFiveColumnsAndTouchesNothing() {
        // The safety net that runs when a database is opened would add the columns even if the step were missing, so the step is run on
        // its own here: on the version 13 file, opened at 13 by a helper that migrates nothing.
        seedVersionThirteen()
        val tables = listOf("accounts", "banks", "people", "categories", "recurring_rules", "transactions", "budgets", "meta")
        val helper = DatabaseEngine.Plain.open(
            context,
            dbName,
            object : SupportSQLiteOpenHelper.Callback(13) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            },
        )
        try {
            val db = helper.writableDatabase
            assertEquals(13, db.version)
            assertTrue(columnsOf(db, "accounts").none { it in loanColumns })
            val before = tables.associateWith { db.rows(it) }

            FinanceDatabase.addLoanColumns(db)

            assertEquals(loanColumns, columnsOf(db, "accounts").filter { it in loanColumns }.toSet(), "the five columns are there")
            assertEquals(15 + 5, columnsOf(db, "accounts").size)
            for (table in tables) {
                val expected = before.getValue(table)
                val actual = db.rows(table, skip = loanColumns)
                assertEquals(expected, actual, "$table: every row as it was")
            }
            for (row in db.rows("accounts")) for (column in loanColumns) assertNull(row[column])
            // And the types are what the other dates and amounts are: integers, the day as the wall clock in milliseconds.
            val types = db.rawQuery("PRAGMA table_info(accounts)", null).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow("type"))) }
            }
            for (column in loanColumns) assertEquals("INTEGER", types[column], column)
        } finally {
            helper.close()
        }
    }

    @Test
    fun theUpgradedLedgerReadsAsItDid_noAccountIsALoan_andALoanCanBeSavedThenRead(): Unit = runBlocking {
        seedVersionThirteen()
        val database = FinanceDatabase(context, dbName)
        val repo = FinanceRepository(database, clock = { LocalDateTime.of(2026, 11, 15, 9, 0) }, dispatcher = Dispatchers.Unconfined)
        try {
            val accounts = repo.accounts()
            assertEquals(listOf("Cash", "HDFC Credit", "Old Car Loan"), accounts.map { it.name })
            assertTrue(accounts.none { it.isLoan || it.loan != null }, "none of the old accounts became a loan")
            assertEquals(listOf(250_000L, -845_000L, -30_000_000L), accounts.map { it.openingBalance })
            val card = accounts[1]
            assertEquals("652850", card.numberStart)
            assertEquals(5_000_000L, card.creditLimit)
            assertEquals(5, card.statementDay)

            // An old account can become a loan afterwards, and the others are untouched by that.
            val now = LocalDateTime.of(2026, 11, 15, 9, 0)
            val terms = LoanTerms(30_000_000L, 1050, 60, emiFor(30_000_000L, 1050, 60), LocalDate.of(2026, 12, 5))
            repo.saveAccount(accounts[2].copy(loan = terms, updatedAt = now))
            val after = repo.accounts()
            assertEquals(terms, after[2].loan)
            assertTrue(after[2].isLoan)
            assertNull(after[0].loan)
            assertNull(after[1].loan)
            assertEquals(card, after[1].copy(updatedAt = card.updatedAt), "the card is as it was")
        } finally {
            repo.close()
        }
    }

    @Test
    fun aNewInstallHasTheColumnsFromTheStart() {
        val database = FinanceDatabase(context, name = null)
        try {
            val db = database.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)
            val columns = columnsOf(db, "accounts")
            for (column in loanColumns) assertTrue(column in columns, "$column is in the new schema")
            assertEquals(20, columns.size)
            // The seeded Cash account is no loan.
            db.rawQuery("SELECT loan_principal, loan_start FROM accounts", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0) && cursor.isNull(1))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun theMigrationStepIsSafeToRunAgain() {
        val database = FinanceDatabase(context, name = null)
        try {
            val db = database.writableDatabase
            val before = columnsOf(db, "accounts")
            // A retried migration (or the safety net on open) finds the columns there and leaves them: no "duplicate column name".
            FinanceDatabase.addLoanColumns(db)
            FinanceDatabase.addLoanColumns(db)
            FinanceDatabase.ensureAccountColumns(db)
            assertEquals(before, columnsOf(db, "accounts"))
        } finally {
            database.close()
        }
    }

    @Test
    fun anAccountOfTheLoanKindIsStoredByItsKey_andEveryOldKeyIsUnchanged(): Unit = runBlocking {
        assertEquals("loan", AccountKind.Loan.key)
        assertEquals(
            listOf("cash", "bank", "card", "wallet", "savings", "other"),
            AccountKind.entries.filter { it != AccountKind.Loan }.map { it.key },
            "the keys that were there are the same",
        )
        val database = FinanceDatabase(context, name = null)
        val repo = FinanceRepository(database, clock = { LocalDateTime.of(2026, 11, 15, 9, 0) }, dispatcher = Dispatchers.Unconfined)
        try {
            val now = LocalDateTime.of(2026, 11, 15, 9, 0)
            val saved = repo.saveAccount(
                Account(
                    name = "Home loan", kind = AccountKind.Loan, openingBalance = -100_000_000L, createdAt = now, updatedAt = now,
                    loan = LoanTerms(100_000_000L, 900, 240, 899_726L, LocalDate.of(2026, 12, 5)),
                ),
            )
            val kindStored = database.readableDatabase.rawQuery("SELECT kind, loan_principal, loan_rate_bp, loan_tenure_months, loan_emi FROM accounts WHERE id = ${saved.id}", null)
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    listOf(cursor.getString(0), cursor.getLong(1), cursor.getLong(2), cursor.getLong(3), cursor.getLong(4))
                }
            assertEquals(listOf<Any>("loan", 100_000_000L, 900L, 240L, 899_726L), kindStored)
            // The first EMI's day is stored as every date is: the wall-clock day as milliseconds, so SQL dates agree with the rest.
            val start = database.readableDatabase.rawQuery("SELECT loan_start FROM accounts WHERE id = ${saved.id}", null)
                .use { it.moveToFirst(); it.getLong(0) }
            assertEquals(LocalDate.of(2026, 12, 5).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), start)
            assertNotNull(repo.accounts().firstOrNull { it.kind == AccountKind.Loan })
        } finally {
            repo.close()
        }
    }
}
