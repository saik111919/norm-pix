package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.SplitPart
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
 * Version 17 (2026-11-15) adds one empty column to transactions - `split_group`, the id the parts of one split payment share - and an index
 * on it, and nothing else: a version 16 database - hand-built here as version 16 left it, with accounts (a card and a loan among them),
 * transactions of every type, a category, a person, a repeating payment, a budget, a bank, two tags with their links, two goals and the
 * ledger settings - is opened by this release and every row of every table is exactly as it was, the new column empty on every
 * transaction. A new install has the column and the index from the start, and the step is safe to run twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitMigrationTest {
    private val dbName = "split_migration_test.db"
    private lateinit var context: Context

    private val day = LocalDateTime.of(2026, 9, 1, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** The finance schema exactly as version 16 left it: the loan columns on accounts, the tag tables and the goals table, and no split group on transactions. */
    private class VersionSixteen(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 16) {
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
            db.execSQL(
                """
                CREATE TABLE goals (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  name TEXT NOT NULL,
                  target INTEGER NOT NULL CHECK (target > 0),
                  target_date INTEGER,
                  emoji TEXT,
                  account_id INTEGER REFERENCES accounts(id) ON DELETE SET NULL,
                  saved INTEGER NOT NULL DEFAULT 0 CHECK (saved >= 0),
                  sort_order INTEGER NOT NULL DEFAULT 0,
                  created_at INTEGER NOT NULL,
                  updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
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

    private fun seedVersionSixteen() {
        VersionSixteen(context, dbName).use { helper ->
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
            db.add(
                "goals", "id" to 1, "name" to "Emergency fund", "target" to 30_000_000L, "target_date" to day, "emoji" to "\uD83D\uDCB0",
                "account_id" to 4, "saved" to 0L, "sort_order" to 0, "created_at" to day, "updated_at" to day,
            )
            db.add(
                "goals", "id" to 2, "name" to "Trip to Goa", "target" to 4_000_000L, "saved" to 1_800_000L, "sort_order" to 1,
                "created_at" to day, "updated_at" to day,
            )
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
        "accounts", "banks", "people", "categories", "recurring_rules", "transactions", "budgets", "meta", "tags", "transaction_tags", "goals",
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

    private fun String.squashed() = replace(Regex("\\s+"), "")

    @Test
    fun everyRowSurvivesTheUpgradeExactlyAsItWas_andTheSplitColumnIsThereAndEmpty() {
        seedVersionSixteen()
        // The rows and the schema before: read through the version 16 helper itself.
        val (before, schemaBefore) = VersionSixteen(context, dbName).use { helper ->
            val db = helper.writableDatabase
            tables.associateWith { table -> db.rawQuery("SELECT * FROM $table ORDER BY ${orderOf(table)}", null).allRows() } to
                db.rawQuery("SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name", null).use { cursor ->
                    buildMap { while (cursor.moveToNext()) put(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1)) }
                }
        }

        val upgraded = FinanceDatabase(context, dbName)
        try {
            val db = upgraded.writableDatabase
            assertTrue(FinanceDatabase.VERSION >= 17)
            assertEquals(FinanceDatabase.VERSION, db.version, "opened at the current version")
            // Every row of every table, exactly as it was - transactions with the one new column (null) and nothing else different.
            for (table in tables) {
                if (table == "transactions") {
                    val now = db.rows(table)
                    assertEquals(before.getValue(table).size, now.size)
                    assertTrue(now.all { it["split_group"] == null }, "no transaction is in a split")
                    assertEquals(before.getValue(table), now.map { row -> row - "split_group" }, "transactions: every column as it was")
                } else {
                    assertEquals(before.getValue(table), db.rows(table), "$table is exactly as it was")
                }
            }
            assertEquals(
                listOf(
                    "id", "type", "amount", "account_id", "to_account_id", "category_id", "occurred_at", "payee", "note", "recurring_id",
                    "created_at", "updated_at", "person_id", "picture", "split_group",
                ),
                columnsOf(db, "transactions"),
                "the column is added after the old ones",
            )
            assertTrue("idx_tx_split" in schemaNames(db, "index"))
            // Nothing else changed shape: every other table and index has the same SQL, and only the split index is new.
            val after = schemaSql(db)
            for ((name, sql) in schemaBefore) {
                if (name == "transactions") {
                    assertEquals(sql!!.squashed().replace("pictureTEXT)", "pictureTEXT,split_groupTEXT)"), after[name]!!.squashed(), "transactions: as it was, with the split group")
                } else {
                    assertEquals(sql, after[name], "$name is as it was")
                }
            }
            assertEquals(
                schemaBefore.keys + "idx_tx_split" + "debts" + "idx_debts_person" + "idx_debts_transaction",
                after.keys,
                "only the split index and, from v18, the debts table with its indexes are new",
            )
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun theUpgradedLedgerReadsAsItDid_withNothingSplit_andASplitCanThenBeSavedAndRead(): Unit = runBlocking {
        seedVersionSixteen()
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        val repo = FinanceRepository(FinanceDatabase(context, dbName), clock = { now }, dispatcher = Dispatchers.Unconfined)
        try {
            val all = repo.transactions(TransactionQuery(), limit = 10).items
            assertEquals(listOf(5L, 4L, 3L, 2L, 1L), all.map { it.id })
            assertTrue(all.all { it.splitGroup == null })
            assertEquals(setOf(1L, 2L), repo.transactionById(1)!!.tagIds, "the tags are as they were")
            assertEquals(2, repo.goals().size, "the goals are as they were")
            assertEquals(4_500_000L, repo.accountBalances()[4L])
            // Row 1 (Health, Rs 450, with tags and a person) becomes the first part of a split.
            val first = repo.transactionById(1)!!
            val parts = repo.saveSplit(
                first,
                listOf(SplitPart(1, 30_000L, "pills", id = 1), SplitPart(1, 15_000L, "cream")),
            )
            assertEquals(1L, parts.first().id)
            assertEquals(2, parts.size)
            val group = repo.transactionById(1)!!.splitGroup
            assertTrue(!group.isNullOrEmpty())
            assertEquals(parts.map { it.id }, repo.splitParts(group!!).map { it.id })
            assertEquals(setOf(1L, 2L), repo.splitParts(group).first { it.id != 1L }.tagIds, "the tags went to the new part")
        } finally {
            repo.close()
        }
    }

    @Test
    fun theMigrationStepAloneAddsTheColumnAndTouchesNothing() {
        // The safety net in onOpen would add the column even if the step were missing, so the step is run on its own here: on the
        // version 16 file, opened at 16 by a helper that migrates nothing.
        seedVersionSixteen()
        val helper = DatabaseEngine.Plain.open(
            context,
            dbName,
            object : SupportSQLiteOpenHelper.Callback(16) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }

                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            },
        )
        try {
            val db = helper.writableDatabase
            assertEquals(16, db.version)
            assertFalse("split_group" in columnsOf(db, "transactions"))
            val before = tables.associateWith { db.rows(it) }

            FinanceDatabase.addSplitGroup(db)

            assertTrue("split_group" in columnsOf(db, "transactions"))
            assertTrue("idx_tx_split" in schemaNames(db, "index"))
            for (table in tables) {
                val now = db.rows(table)
                assertEquals(before.getValue(table), if (table == "transactions") now.map { it - "split_group" } else now, "$table: every row as it was")
            }
            assertTrue(db.rows("transactions").all { it["split_group"] == null })
            val types = db.rawQuery("PRAGMA table_info(transactions)", null).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow("type"))) }
            }
            assertEquals("TEXT", types["split_group"], "the id is text, and may be empty")
        } finally {
            helper.close()
        }
    }

    @Test
    fun theStepCanRunTwice_andAgainOnADatabaseWithASplitInIt(): Unit = runBlocking {
        seedVersionSixteen()
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        val database = FinanceDatabase(context, dbName, clock = { now })
        val repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
        try {
            val first = repo.transactionById(1)!!
            repo.saveSplit(first, listOf(SplitPart(1, 30_000L, id = 1), SplitPart(1, 15_000L)))
            val db = database.writableDatabase
            val schemaBefore = schemaSql(db)
            val rowsBefore = db.rows("transactions")
            // A retried migration (or the safety net on open) finds the column and the index there and leaves them, and every row.
            FinanceDatabase.addSplitGroup(db)
            FinanceDatabase.addSplitGroup(db)
            assertEquals(schemaBefore, schemaSql(db))
            assertEquals(rowsBefore, db.rows("transactions"))
            assertEquals(2, repo.splitParts(repo.transactionById(1)!!.splitGroup!!).size)
        } finally {
            repo.close()
        }
    }

    @Test
    fun aDatabaseNumbered17WithoutTheColumnGetsItWhenOpened() {
        // The safety net: a database whose version says it is current but which lacks the column (a build that numbered its versions
        // differently, a restore of an older file) would refuse every read of a transaction. Opening it adds the column, empty.
        seedVersionSixteen()
        VersionSixteen(context, dbName).use { it.writableDatabase.execSQL("PRAGMA user_version = 17") }
        val database = FinanceDatabase(context, dbName)
        try {
            val db = database.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version, "numbered 17, opened at the current version")
            assertTrue("split_group" in columnsOf(db, "transactions"))
            assertTrue(db.rows("transactions").all { it["split_group"] == null })
            assertEquals(5, count(db, "transactions"))
        } finally {
            database.close()
        }
    }

    @Test
    fun aNewInstallHasTheColumnAndTheIndex() {
        val database = FinanceDatabase(context, name = null)
        try {
            val db = database.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)
            assertTrue("split_group" in columnsOf(db, "transactions"))
            assertTrue("idx_tx_split" in schemaNames(db, "index"))
            assertEquals(
                "split_group",
                db.rawQuery("SELECT name FROM pragma_index_info('idx_tx_split')", null).use { it.moveToFirst(); it.getString(0) },
                "the index is on the group",
            )
            val cash = db.rawQuery("SELECT id FROM accounts LIMIT 1", null).use { it.moveToFirst(); it.getLong(0) }
            val category = db.rawQuery("SELECT id FROM categories WHERE type = 'expense' LIMIT 1", null).use { it.moveToFirst(); it.getLong(0) }
            // The column may be empty (every ordinary transaction) or hold any text.
            db.execSQL("INSERT INTO transactions (type, amount, account_id, category_id, occurred_at, created_at, updated_at) VALUES ('expense', 100, $cash, $category, 0, 0, 0)")
            db.execSQL("INSERT INTO transactions (type, amount, account_id, category_id, occurred_at, created_at, updated_at, split_group) VALUES ('expense', 100, $cash, $category, 0, 0, 0, 'g1')")
            assertEquals(listOf(null, "g1"), db.rows("transactions").map { it["split_group"] })
        } finally {
            database.close()
        }
    }
}
