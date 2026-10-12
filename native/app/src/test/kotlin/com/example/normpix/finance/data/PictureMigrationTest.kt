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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A finance database as the previous release left it (version 7, no pictures) opened by this one: every transaction is
 * still there, exactly as it was and with no picture, and a picture can be attached from then on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PictureMigrationTest {
    private val dbName = "picture_migration_test.db"
    private lateinit var context: Context

    /** The version 7 schema of the tables a picture touches: transactions without one, and what they refer to. */
    private class VersionSeven(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 7) {
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
                CREATE TABLE people (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, emoji TEXT, color INTEGER NOT NULL DEFAULT 0,
                  archived INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
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
                  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
                  person_id INTEGER REFERENCES people(id) ON DELETE SET NULL
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

    private fun seedVersionSeven() {
        VersionSeven(context, dbName).use { helper ->
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
                        put("occurred_at", at); put("payee", "Swiggy"); put("created_at", at); put("updated_at", at)
                    },
                )
            }
            db.version = 7
        }
    }

    @Test
    fun `upgrading keeps every transaction as it was, with no picture`() {
        seedVersionSeven()
        val upgraded = FinanceDatabase(context, name = dbName)
        try {
            val db = upgraded.writableDatabase
            assertEquals(FinanceDatabase.VERSION, db.version)
            val rows = db.rawQuery("SELECT amount, payee, picture FROM transactions ORDER BY id", null).use { c ->
                buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getString(1), c.getString(2))) }
            }
            assertEquals(listOf(Triple(45_000L, "Swiggy", null), Triple(12_000L, "Swiggy", null)), rows)
        } finally {
            upgraded.close()
        }
    }

    @Test
    fun `a picture can be attached on the upgraded database`() = runBlocking {
        seedVersionSeven()
        val repo = FinanceRepository(FinanceDatabase(context, name = dbName), dispatcher = Dispatchers.Unconfined)
        try {
            val existing = repo.transactionById(1)!!
            assertNull(existing.picture)
            val name = "pic-0f8fad5b-d9cb-469f-a165-70867728950e.jpg"
            repo.saveTransaction(existing.copy(picture = name))
            assertEquals(name, repo.transactionById(1)!!.picture)
            assertEquals(setOf(name), repo.pictureNames())
        } finally {
            repo.close()
        }
    }
}
