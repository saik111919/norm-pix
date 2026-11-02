package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A database whose version says it is current but whose accounts table lacks some of the columns an account is saved with
 * would refuse every account write ("no column named number_start"). Opening it gives the columns back, empty, and keeps
 * every row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountColumnsGuardTest {
    private val dbName = "account_columns_guard_test.db"
    private lateinit var context: Context

    /** Current version number, an accounts table with only some of its columns, and the categories table opening reads. */
    private class Short(context: Context, name: String) : SQLiteOpenHelper(context, name, null, FinanceDatabase.VERSION) {
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
                  bank_key TEXT
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, icon TEXT NOT NULL)")
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

    private fun columnsOf(db: SQLiteDatabase): Set<String> =
        db.rawQuery("PRAGMA table_info(accounts)", null).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }

    @Test
    fun `missing account columns are added on open and no row is lost`() {
        Short(context, dbName).writableDatabase.use { db ->
            db.insertOrThrow(
                "accounts",
                null,
                ContentValues().apply {
                    put("name", "HDFC Salary")
                    put("kind", "bank")
                    put("created_at", 1L)
                    put("updated_at", 1L)
                    put("bank_key", "hdfc")
                },
            )
        }

        val opened = FinanceDatabase(context, dbName).writableDatabase
        try {
            val columns = columnsOf(opened)
            for (column in listOf("credit_limit", "statement_day", "due_day", "number_ending", "card_id", "number_start")) {
                assertTrue(column in columns, "$column is back")
            }
            val row = opened.rawQuery("SELECT name, bank_key, number_start FROM accounts", null).use {
                assertTrue(it.moveToFirst())
                Triple(it.getString(0), it.getString(1), it.isNull(2))
            }
            assertEquals(Triple("HDFC Salary", "hdfc", true), row, "the account is as it was, with the new column empty")
        } finally {
            opened.close()
        }
    }

    @Test
    fun `a database that has every column is left alone`() {
        val db = FinanceDatabase(context, dbName).writableDatabase
        try {
            val before = columnsOf(db)
            FinanceDatabase.ensureAccountColumns(db)
            assertEquals(before, columnsOf(db))
        } finally {
            db.close()
        }
    }
}
