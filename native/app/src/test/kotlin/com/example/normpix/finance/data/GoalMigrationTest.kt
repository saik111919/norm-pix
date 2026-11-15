package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.SavingsGoal
import com.example.normpix.storage.DatabaseEngine
import com.example.normpix.storage.rawQuery
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Version 16 (2026-11-15) adds one empty table for savings goals, and nothing else: a version 15 database - hand-built here as version 15
 * left it, with accounts (a card and a loan among them), transactions of every type, a category, a person, a repeating payment, a budget,
 * a bank, two tags with their links and the ledger settings - is opened by this release and every row of every table is exactly as it was,
 * the goals table there and empty. A new install has it from the start, and the step is safe to run twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalMigrationTest {
    private val dbName = "goal_migration_test.db"
    private lateinit var context: Context

    private val day = LocalDateTime.of(2026, 9, 1, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** The finance schema exactly as version 15 left it: the loan columns on accounts and the two tag tables, and no goals table. */
    private class VersionFifteen(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 15) {
        override fun onConfigure(db: SQLiteDatabase) {
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
                  updated_at INTEGER NOT NULL,
                  bank_key TEXT,
                  credit_limit INTEGER,
                  statement_day INTEGER,
                  due_day INTEGER,
                  number_ending TEXT,
                  card_id TEXT,
                  number_start TEXT,
                  loan_principal INTEGER,
                  loan_rate_bp INTEGER,
                  loan_tenure_months INTEGER,
                  loan_emi INTEGER,
                  loan_start INTEGER
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
            db.execSQL("CREATE UNIQUE INDEX idx_people_name ON people(name COLLATE NOCASE)")
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
            db.execSQL(
                """
                CREATE TABLE tags (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE UNIQUE INDEX idx_tags_name ON tags(name COLLATE NOCASE)")
            db.execSQL(
                """
                CREATE TABLE transaction_tags (
                  transaction_id INTEGER NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
                  tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                  PRIMARY KEY (transaction_id, tag_id)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_transaction_tags_tag ON transaction_tags(tag_id)")
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

    private fun seedVersionFifteen() {
        VersionFifteen(context, dbName).use { helper ->
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
                "accounts", "id" to 3, "name" to "Home Loan", "kind" to "loan", "opening_balance" to -100_000_000L, "sort_order" to 2,
                "created_at" to day, "updated_at" to day, "bank_key" to "custom-1", "loan_principal" to 100_000_000L, "loan_rate_bp" to 875,
                "loan_tenure_months" to 180, "loan_emi" to 999_461L, "loan_start" to day,
            )
            db.add(
                "accounts", "id" to 4, "name" to "SBI Savings", "kind" to "savings", "opening_balance" to 4_500_000L, "sort_order" to 3,
                "created_at" to day, "updated_at" to day,
            )
            db.add("people", "id" to 1, "name" to "Mom", "emoji" to "👩", "color" to 6, "created_at" to day, "updated_at" to day)
            db.add(
                "categories", "id" to 1, "name" to "Health", "type" to "expense", "icon" to "health", "color" to 3,
                "sort_order" to 1, "created_at" to day, "updated_at" to day,
            )
            db.add(
                "categories", "id" to 2, "name" to "Salary", "type" to "income", "icon" to "salary", "sort_order" to 2,
                "created_at" to day, "updated_at" to day,
            )
            db.add(
                "recurring_rules", "id" to 1, "type" to "expense", "amount" to 64_900L, "account_id" to 2, "category_id" to 1,
                "payee" to "Insurance", "frequency" to "monthly", "interval" to 1, "start_date" to day, "next_date" to day + 86_400_000L,
                "created_at" to day, "updated_at" to day,
            )
            db.add(
                "transactions", "id" to 1, "type" to "expense", "amount" to 45_000L, "account_id" to 1, "category_id" to 1,
                "occurred_at" to day, "payee" to "Apollo Pharmacy", "note" to "for Mom", "created_at" to day, "updated_at" to day, "person_id" to 1,
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
                "occurred_at" to day + 3_000L, "payee" to "Insurance", "recurring_id" to 1, "created_at" to day, "updated_at" to day,
                "picture" to "pic-00000000-0000-0000-0000-000000000000.jpg",
            )
            db.add(
                "transactions", "id" to 5, "type" to "transfer", "amount" to 250_000L, "account_id" to 1, "to_account_id" to 3,
                "occurred_at" to day + 4_000L, "note" to "EMI Sep 2026 - principal", "created_at" to day, "updated_at" to day,
            )
            db.add("budgets", "id" to 1, "category_id" to 1, "amount" to 1_200_000L, "effective_month" to 202609, "created_at" to day, "updated_at" to day)
            db.add("meta", "key" to "currency", "value" to "INR")
            db.add("meta", "key" to "default_account_id", "value" to "1")
            db.add("meta", "key" to "dismissed_repeat_payees", "value" to "netflix")
            db.add("tags", "id" to 1, "name" to "Tax-deductible", "created_at" to day)
            db.add("tags", "id" to 2, "name" to "Goa trip", "created_at" to day)
            db.add("transaction_tags", "transaction_id" to 1, "tag_id" to 1)
            db.add("transaction_tags", "transaction_id" to 1, "tag_id" to 2)
            db.add("transaction_tags", "transaction_id" to 2, "tag_id" to 2)
        }
    }

    /** Every row of this cursor, every column as it is stored, as a map by column name. */
    private fun Cursor.allRows(): List<Map<String, Any?>> = use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    buildMap {
                        for (i in 0 until cursor.columnCount) {
                            put(
                                cursor.getColumnName(i),
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

    private val tables = listOf(
        "accounts", "banks", "people", "categories", "recurring_rules", "transactions", "budgets", "meta", "tags", "transaction_tags",
    )

    private fun orderOf(table: String) = when (table) {
        "banks", "meta" -> "key"
        "transaction_tags" -> "transaction_id, tag_id"
        else -> "id"
    }

    private fun SupportSQLiteDatabase.rows(table: String): List<Map<String, Any?>> =
        rawQuery("SELECT * FROM $table ORDER BY ${orderOf(table)}", null).allRows()

    private fun schemaNames(db: SupportSQLiteDatabase, type: String): Set<String> =
        db.rawQuery("SELECT name FROM sqlite_master WHERE type = ?", arrayOf(type)).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): List<String> =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    private fun count(db: SupportSQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { it.moveToFirst(); it.getLong(0) }

    /** The SQL of every table and index the schema had, by name: what "nothing else changed" is checked against. */
    private fun schemaSql(db: SupportSQLiteDatabase): Map<String, String?> =
        db.rawQuery("SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name", null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1)) }
        }

    @Test
    fun everyRowSurvivesTheUpgradeExactlyAsItWas_andTheGoalsTableIsThereAndEmpty() {
        seedVersionFifteen()
        // The rows and the schema before: read through the version 15 helper itself.
        val (before, schemaBefore) = VersionFifteen(context, dbName).use { helper ->
            val db = helper.writableDatabase
            tables.associateWith { table -> db.rawQuery("SELECT * FROM $table ORDER BY ${orderOf(table)}", null).allRows() } to
                db.rawQuery("SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name", null).use { cursor ->
                    buildMap { while (cursor.moveToNext()) put(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1)) }
                }
        }

        val upgraded = FinanceDatabase(context, dbName)
        try {
            val db = upgraded.writableDatabase
            // Later versions keep the goals table, so this reads the current version instead of pinning 16.
            assertTrue(FinanceDatabase.VERSION >= 16)
            assertEquals(FinanceDatabase.VERSION, db.version, "opened at the current version")
            // Every row exactly as it was - the transactions with the column version 17 added (null on each) set aside.
            for (table in tables) {
                val now = db.rows(table)
                assertEquals(before.getValue(table), if (table == "transactions") now.map { it - "split_group" } else now, "$table is exactly as it was")
            }
            // The table is there, and empty, with the columns the format has.
            assertTrue("goals" in schemaNames(db, "table"))
            assertEquals(0, count(db, "goals"))
            assertEquals(
                listOf("id", "name", "target", "target_date", "emoji", "account_id", "saved", "sort_order", "created_at", "updated_at"),
                columnsOf(db, "goals"),
            )
            // Nothing that was there changed shape: every table and index of version 15 has the same SQL - but for what version 17 added
            // to transactions (the split group column, and its index), which is the only other change to the schema since.
            val after = schemaSql(db)
            for ((name, sql) in schemaBefore) {
                if (name == "transactions") {
                    // SQLite writes the new column into the table's own SQL; compared without whitespace, nothing else is different.
                    fun String.squashed() = replace(Regex("\\s+"), "")
                    assertEquals(sql!!.squashed().replace("pictureTEXT)", "pictureTEXT,split_groupTEXT)"), after[name]!!.squashed(), "transactions: as it was, with the split group")
                } else {
                    assertEquals(sql, after[name], "$name is as it was")
                }
            }
            assertEquals(schemaBefore.keys + "goals" + "idx_tx_split", after.keys, "only the goals table and the split index are new")
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun theUpgradedLedgerReadsAsItDid_withNoGoals_andAGoalCanThenBeSavedAndRead(): Unit = runBlocking {
        seedVersionFifteen()
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        val repo = FinanceRepository(FinanceDatabase(context, dbName), clock = { now }, dispatcher = Dispatchers.Unconfined)
        try {
            val all = repo.transactions(TransactionQuery(), limit = 10).items
            assertEquals(listOf(5L, 4L, 3L, 2L, 1L), all.map { it.id })
            assertEquals(setOf(1L, 2L), repo.transactionById(1)!!.tagIds, "the tags are as they were")
            assertTrue(repo.goals().isEmpty())
            assertTrue(repo.snapshot().goals.isEmpty())

            val goal = repo.saveGoal(
                SavingsGoal(name = "Emergency fund", target = 30_000_000L, targetDate = LocalDate.of(2027, 12, 31), accountId = 4, createdAt = now, updatedAt = now),
            )
            assertEquals(listOf(goal), repo.goals())
            assertEquals(listOf(goal), repo.snapshot().goals)
            assertEquals(4_500_000L, repo.accountBalances()[4L], "the account it counts is as it was")
        } finally {
            repo.close()
        }
    }

    @Test
    fun theMigrationStepAloneMakesTheTableAndTouchesNothing() {
        // The safety net in onOpen would make the table even if the step were missing, so the step is run on its own here: on the
        // version 15 file, opened at 15 by a helper that migrates nothing.
        seedVersionFifteen()
        val helper = DatabaseEngine.Plain.open(
            context,
            dbName,
            object : SupportSQLiteOpenHelper.Callback(15) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }

                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            },
        )
        try {
            val db = helper.writableDatabase
            assertEquals(15, db.version)
            assertFalse("goals" in schemaNames(db, "table"))
            val before = tables.associateWith { db.rows(it) }
            val schemaBefore = schemaSql(db)

            FinanceDatabase.addGoals(db)

            assertTrue("goals" in schemaNames(db, "table"))
            for (table in tables) assertEquals(before.getValue(table), db.rows(table), "$table: every row as it was")
            val after = schemaSql(db)
            for ((name, sql) in schemaBefore) assertEquals(sql, after[name], "$name is as it was")
            assertEquals(schemaBefore.keys + "goals", after.keys)
            // Dates are kept as every date here is - an integer - and the table says what it holds.
            val types = db.rawQuery("PRAGMA table_info(goals)", null).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow("type"))) }
            }
            assertEquals(
                mapOf(
                    "id" to "INTEGER", "name" to "TEXT", "target" to "INTEGER", "target_date" to "INTEGER", "emoji" to "TEXT", "account_id" to "INTEGER",
                    "saved" to "INTEGER", "sort_order" to "INTEGER", "created_at" to "INTEGER", "updated_at" to "INTEGER",
                ),
                types,
            )
        } finally {
            helper.close()
        }
    }

    @Test
    fun theStepCanRunTwice_andAgainOnADatabaseWithGoalsInIt(): Unit = runBlocking {
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        val database = FinanceDatabase(context, name = null, clock = { now })
        val repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
        try {
            val goal = repo.saveGoal(SavingsGoal(name = "Trip", target = 4_000_000L, saved = 1_800_000L, createdAt = now, updatedAt = now))
            val db = database.writableDatabase
            val schemaBefore = schemaSql(db)
            // A retried migration (or the safety net on open) finds the table there and leaves it, and what is in it.
            FinanceDatabase.addGoals(db)
            FinanceDatabase.addGoals(db)
            assertEquals(schemaBefore, schemaSql(db))
            assertEquals(listOf(goal), repo.goals())
        } finally {
            repo.close()
        }
    }

    @Test
    fun aNewInstallHasTheTable_withItsRules() {
        val database = FinanceDatabase(context, name = null)
        try {
            val db = database.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)
            assertTrue("goals" in schemaNames(db, "table"))
            assertEquals(0, count(db, "goals"))
            // A target is above zero and what is saved is not below it, in the database itself too.
            assertFailsWith<Exception> { db.execSQL("INSERT INTO goals (name, target, created_at, updated_at) VALUES ('Trip', 0, 0, 0)") }
            assertFailsWith<Exception> { db.execSQL("INSERT INTO goals (name, target, saved, created_at, updated_at) VALUES ('Trip', 100, -1, 0, 0)") }
            // A goal names an account that exists (foreign keys are on), or none.
            assertFailsWith<Exception> { db.execSQL("INSERT INTO goals (name, target, account_id, created_at, updated_at) VALUES ('Trip', 100, 999, 0, 0)") }
            db.execSQL("INSERT INTO goals (name, target, created_at, updated_at) VALUES ('Trip', 100, 0, 0)")
            assertEquals(1, count(db, "goals"))
            // The columns that may be empty are, and saved starts at nothing.
            val row = db.rows("goals").single()
            assertNull(row["target_date"])
            assertNull(row["emoji"])
            assertNull(row["account_id"])
            assertEquals(0L, row["saved"])
            assertEquals(0L, row["sort_order"])
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingTheLinkedAccount_leavesTheGoalOnItsOwn_withNothingSaved(): Unit = runBlocking {
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        val database = FinanceDatabase(context, name = null, clock = { now })
        val repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
        try {
            val account = repo.saveAccount(
                com.example.normpix.finance.domain.Account(
                    name = "Old savings", kind = com.example.normpix.finance.domain.AccountKind.Savings, createdAt = now, updatedAt = now,
                ),
            )
            // Even a goal that was stored with an amount of its own while linked (a file, a hand edit) is left with nothing once it is
            // on its own: the repository keeps a linked goal's amount at zero.
            val goal = repo.saveGoal(SavingsGoal(name = "Fund", target = 5_000_000L, accountId = account.id, saved = 99_900L, createdAt = now, updatedAt = now))
            assertEquals(account.id, goal.accountId)
            assertEquals(0L, goal.saved)
            repo.deleteAccount(account.id!!)
            val left = repo.goals().single()
            assertNull(left.accountId, "the goal is on its own now")
            assertEquals(0L, left.saved, "with nothing saved")
            assertEquals("Fund", left.name)
            assertEquals(5_000_000L, left.target)
        } finally {
            repo.close()
        }
    }
}
