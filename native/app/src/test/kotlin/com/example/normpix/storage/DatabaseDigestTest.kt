package com.example.normpix.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fingerprint the move into an encrypted file is checked by: the same data gives the same digest, anything else does not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseDigestTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** A database in memory at [version], made by [schema]. */
    private fun db(version: Int = 3, schema: SupportSQLiteDatabase.() -> Unit = ::ledger): SupportSQLiteDatabase =
        DatabaseEngine.Plain.open(
            context,
            null,
            object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = db.schema()

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            },
        ).writableDatabase

    private fun ledger(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE spends (id INTEGER PRIMARY KEY AUTOINCREMENT, payee TEXT, amount INTEGER NOT NULL, rate REAL, note BLOB)")
        // No INTEGER PRIMARY KEY: copying it renumbers its hidden row ids.
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value)")
    }

    private fun SupportSQLiteDatabase.fill(order: List<Int> = listOf(1, 2, 3)) = apply {
        for (n in order) execSQL("INSERT INTO spends (id, payee, amount, rate, note) VALUES (?, ?, ?, ?, ?)", arrayOf<Any?>(n, "Payee $n", n * 100, n / 4.0, byteArrayOf(n.toByte())))
        execSQL("INSERT INTO meta VALUES ('currency', 'INR')")
        execSQL("INSERT INTO meta VALUES ('default_account', '1')")
    }

    @Test
    fun `the same data gives the same digest, whatever order it was written in`() {
        assertEquals(DatabaseDigest.of(db().fill()), DatabaseDigest.of(db().fill(listOf(3, 1, 2))))
    }

    @Test
    fun `a table's renumbered row ids don't change it`() {
        val renumbered = db().fill().apply {
            execSQL("DELETE FROM meta")
            execSQL("INSERT INTO meta VALUES ('x', 'y')")
            execSQL("DELETE FROM meta")
            execSQL("INSERT INTO meta VALUES ('currency', 'INR')")
            execSQL("INSERT INTO meta VALUES ('default_account', '1')")
        }
        assertEquals(DatabaseDigest.of(db().fill()), DatabaseDigest.of(renumbered))
    }

    @Test
    fun `a changed value, a value of another type, a row more or less, or another version all change it`() {
        val original = DatabaseDigest.of(db().fill())
        val changes = mapOf<String, SupportSQLiteDatabase.() -> Unit>(
            "a value" to { execSQL("UPDATE spends SET amount = 101 WHERE id = 1") },
            "a type" to { execSQL("UPDATE meta SET value = 1 WHERE key = 'default_account'") },
            "a null" to { execSQL("UPDATE spends SET payee = NULL WHERE id = 2") },
            "a blob" to { execSQL("UPDATE spends SET note = x'00' WHERE id = 3") },
            "a row less" to { execSQL("DELETE FROM spends WHERE id = 3") },
            "a row more" to { execSQL("INSERT INTO meta VALUES ('theme', 'dark')") },
        )
        for ((what, change) in changes) assertNotEquals(original, DatabaseDigest.of(db().fill().apply(change)), what)
        assertNotEquals(original, DatabaseDigest.of(db(version = 4).fill()), "a version")
    }

    @Test
    fun `another schema changes it, statistics don't`() {
        val original = DatabaseDigest.of(db().fill())
        assertNotEquals(original, DatabaseDigest.of(db().fill().apply { execSQL("CREATE INDEX spends_payee ON spends(payee)") }))
        assertEquals(original, DatabaseDigest.of(db().fill().apply { execSQL("ANALYZE") }))
    }
}
