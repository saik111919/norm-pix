package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Tag
import com.example.normpix.storage.DatabaseEngine
import com.example.normpix.storage.rawQuery
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Version 15 (2026-11-15) adds two empty tables for tags, and nothing else: a version 14 database - hand-built here as version 14 left
 * it, with accounts (a card and a loan among them), transactions of every type, a category, a person, a repeating payment, a budget, a
 * bank and the ledger settings - is opened by this release and every row of every table is exactly as it was, the two new tables there
 * and empty. A new install has them from the start, and the step is safe to run twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TagMigrationTest {
    private val dbName = "tag_migration_test.db"
    private lateinit var context: Context

    private val day = LocalDateTime.of(2026, 9, 1, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** The finance schema exactly as version 14 left it: the loan columns on accounts, and no tag tables. */
    private class VersionFourteen(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 14) {
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

    private fun seedVersionFourteen() {
        VersionFourteen(context, dbName).use { helper ->
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

    private val tables = listOf("accounts", "banks", "people", "categories", "recurring_rules", "transactions", "budgets", "meta")

    private fun orderOf(table: String) = if (table == "banks" || table == "meta") "key" else "id"

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
    fun everyRowSurvivesTheUpgradeExactlyAsItWas_andTheTwoNewTablesAreThereAndEmpty() {
        seedVersionFourteen()
        // The rows and the schema before: read through the version 14 helper itself.
        val (before, schemaBefore) = VersionFourteen(context, dbName).use { helper ->
            val db = helper.writableDatabase
            tables.associateWith { table -> db.rawQuery("SELECT * FROM $table ORDER BY ${orderOf(table)}", null).allRows() } to
                db.rawQuery("SELECT name, sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY name", null).use { cursor ->
                    buildMap { while (cursor.moveToNext()) put(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1)) }
                }
        }

        val upgraded = FinanceDatabase(context, dbName)
        try {
            val db = upgraded.writableDatabase
            assertTrue(FinanceDatabase.VERSION >= 15)
            assertEquals(FinanceDatabase.VERSION, db.version, "opened at the current version")
            // Every row as it was - the transactions with the column version 17 added (null on each) set aside.
            for (table in tables) {
                val now = db.rows(table)
                assertEquals(before.getValue(table), if (table == "transactions") now.map { it - "split_group" } else now, "$table is exactly as it was")
            }
            // The two tables and their indexes are there, and empty.
            assertTrue(setOf("tags", "transaction_tags").all { it in schemaNames(db, "table") })
            assertTrue(setOf("idx_tags_name", "idx_transaction_tags_tag").all { it in schemaNames(db, "index") })
            assertEquals(0, count(db, "tags"))
            assertEquals(0, count(db, "transaction_tags"))
            assertEquals(listOf("id", "name", "created_at"), columnsOf(db, "tags"))
            assertEquals(listOf("transaction_id", "tag_id"), columnsOf(db, "transaction_tags"))
            // Nothing that was there changed shape: every table and index of version 14 has the same SQL - but for the split group
            // column version 17 added to transactions (SQLite writes it into the table's own SQL; compared without whitespace).
            val after = schemaSql(db)
            for ((name, sql) in schemaBefore) {
                if (name == "transactions") {
                    fun String.squashed() = replace(Regex("\\s+"), "")
                    assertEquals(sql!!.squashed().replace("pictureTEXT)", "pictureTEXT,split_groupTEXT)"), after[name]!!.squashed(), "transactions: as it was, with the split group")
                } else {
                    assertEquals(sql, after[name], "$name is as it was")
                }
            }
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun theUpgradedLedgerReadsAsItDid_withNoTags_andATagCanThenBeAddedAndRead(): Unit = runBlocking {
        seedVersionFourteen()
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        val repo = FinanceRepository(FinanceDatabase(context, dbName), clock = { now }, dispatcher = Dispatchers.Unconfined)
        try {
            val all = repo.transactions(TransactionQuery(), limit = 10).items
            assertEquals(listOf(5L, 4L, 3L, 2L, 1L), all.map { it.id })
            assertTrue(all.all { it.tagIds.isEmpty() }, "no transaction gained a tag")
            assertTrue(repo.tags().isEmpty())
            assertTrue(repo.snapshot().tags.isEmpty())

            val tax = repo.saveTag(Tag(name = "Tax-deductible", createdAt = now))
            val pharmacy = repo.transactionById(1)!!
            repo.saveTransaction(pharmacy.copy(tagIds = setOf(tax.id!!)))
            assertEquals(setOf(tax.id), repo.transactionById(1)!!.tagIds)
            assertEquals(pharmacy.copy(tagIds = setOf(tax.id!!), updatedAt = now), repo.transactionById(1), "the rest of it is as it was")
            assertEquals(listOf(1L), repo.transactions(TransactionQuery(tagIds = setOf(tax.id!!)), limit = 10).items.map { it.id })
        } finally {
            repo.close()
        }
    }

    @Test
    fun theMigrationStepAloneMakesTheTwoTablesAndTouchesNothing() {
        // The safety net in onOpen would make the tables even if the step were missing, so the step is run on its own here: on the
        // version 14 file, opened at 14 by a helper that migrates nothing.
        seedVersionFourteen()
        val helper = DatabaseEngine.Plain.open(
            context,
            dbName,
            object : SupportSQLiteOpenHelper.Callback(14) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }

                override fun onCreate(db: SupportSQLiteDatabase) = Unit

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            },
        )
        try {
            val db = helper.writableDatabase
            assertEquals(14, db.version)
            assertFalse("tags" in schemaNames(db, "table"))
            val before = tables.associateWith { db.rows(it) }
            val schemaBefore = schemaSql(db)

            FinanceDatabase.addTags(db)

            assertTrue(setOf("tags", "transaction_tags").all { it in schemaNames(db, "table") })
            for (table in tables) assertEquals(before.getValue(table), db.rows(table), "$table: every row as it was")
            val after = schemaSql(db)
            for ((name, sql) in schemaBefore) assertEquals(sql, after[name], "$name is as it was")
            assertEquals(schemaBefore.keys + setOf("tags", "idx_tags_name", "transaction_tags", "idx_transaction_tags_tag"), after.keys)
            // created_at is kept as every moment here is: an integer.
            val types = db.rawQuery("PRAGMA table_info(tags)", null).use { cursor ->
                buildMap { while (cursor.moveToNext()) put(cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow("type"))) }
            }
            assertEquals(mapOf("id" to "INTEGER", "name" to "TEXT", "created_at" to "INTEGER"), types)
        } finally {
            helper.close()
        }
    }

    @Test
    fun theStepCanRunTwice_andAgainOnADatabaseWithTagsInIt(): Unit = runBlocking {
        val database = FinanceDatabase(context, name = null)
        val repo = FinanceRepository(database, clock = { LocalDateTime.of(2026, 11, 15, 9, 0) }, dispatcher = Dispatchers.Unconfined)
        try {
            val tag = repo.saveTag(Tag(name = "Reimbursable", createdAt = LocalDateTime.of(2026, 11, 15, 9, 0)))
            val db = database.writableDatabase
            val schemaBefore = schemaSql(db)
            // A retried migration (or the safety net on open) finds the tables there and leaves them, and what is in them.
            FinanceDatabase.addTags(db)
            FinanceDatabase.addTags(db)
            assertEquals(schemaBefore, schemaSql(db))
            assertEquals(listOf(tag), repo.tags())
        } finally {
            repo.close()
        }
    }

    @Test
    fun aNewInstallHasTheTables_withTheirKeys() {
        val database = FinanceDatabase(context, name = null)
        try {
            val db = database.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)
            assertTrue(setOf("tags", "transaction_tags").all { it in schemaNames(db, "table") })
            assertTrue(setOf("idx_tags_name", "idx_transaction_tags_tag").all { it in schemaNames(db, "index") })
            assertEquals(0, count(db, "tags"))
            // A name is unique whatever its case, in the database itself too.
            db.execSQL("INSERT INTO tags (name, created_at) VALUES ('Tax', 0)")
            assertFailsWith<Exception> { db.execSQL("INSERT INTO tags (name, created_at) VALUES ('tax', 0)") }
            // A link must name a transaction and a tag that exist (foreign keys are on).
            assertFailsWith<Exception> { db.execSQL("INSERT INTO transaction_tags (transaction_id, tag_id) VALUES (999, 1)") }
        } finally {
            database.close()
        }
    }

    @Test
    fun deletingATransactionOrATag_takesItsLinksWithIt(): Unit = runBlocking {
        val now = LocalDateTime.of(2026, 11, 15, 9, 0)
        val database = FinanceDatabase(context, name = null)
        try {
            val db = database.writableDatabase
            db.execSQL("INSERT INTO tags (id, name, created_at) VALUES (1, 'Tax', 0), (2, 'Trip', 0)")
            val category = db.rawQuery("SELECT id FROM categories WHERE type = 'expense' LIMIT 1", null).use { it.moveToFirst(); it.getLong(0) }
            val stamp = now.toInstant(ZoneOffset.UTC).toEpochMilli()
            db.execSQL("INSERT INTO transactions (id, type, amount, account_id, category_id, occurred_at, created_at, updated_at) VALUES (10, 'expense', 500, 1, $category, $stamp, $stamp, $stamp)")
            db.execSQL("INSERT INTO transactions (id, type, amount, account_id, category_id, occurred_at, created_at, updated_at) VALUES (11, 'expense', 700, 1, $category, $stamp, $stamp, $stamp)")
            db.execSQL("INSERT INTO transaction_tags (transaction_id, tag_id) VALUES (10, 1), (10, 2), (11, 1)")
            db.execSQL("DELETE FROM transactions WHERE id = 10")
            assertEquals(1, count(db, "transaction_tags"), "the deleted transaction's two links went with it")
            db.execSQL("DELETE FROM tags WHERE id = 1")
            assertEquals(0, count(db, "transaction_tags"), "the deleted tag's link went with it")
            assertEquals(1, count(db, "transactions"), "the other transaction is kept")
        } finally {
            database.close()
        }
    }
}
