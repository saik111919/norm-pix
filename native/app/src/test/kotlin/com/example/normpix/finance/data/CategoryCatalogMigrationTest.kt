package com.example.normpix.finance.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ready-made categories a later release added reach a phone that already had NormPix (seeding only ran on its first
 * launch): each family that is missing comes with its sub-categories, nothing the user has is doubled or moved, and a
 * database that started with them doesn't get back one the user deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryCatalogMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val seeded = LocalDateTime.of(2026, 3, 1, 10, 0)
    private val later = LocalDateTime.of(2026, 10, 16, 9, 0)

    private fun stamp(at: LocalDateTime) = at.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun SQLiteDatabase.add(name: String, at: LocalDateTime, parent: Long? = null, type: String = "expense"): Long =
        insertOrThrow(
            FinanceDatabase.CATEGORIES,
            null,
            ContentValues().apply {
                put("name", name)
                put("type", type)
                put("icon", "other")
                put("created_at", stamp(at))
                put("updated_at", stamp(at))
                if (parent != null) put("parent_id", parent)
            },
        )

    private fun SQLiteDatabase.count(where: String, vararg args: String): Long =
        rawQuery("SELECT COUNT(*) FROM ${FinanceDatabase.CATEGORIES} WHERE $where", args).use { it.moveToFirst(); it.getLong(0) }

    private fun SQLiteDatabase.parentOf(name: String): String? =
        rawQuery(
            "SELECT p.name FROM ${FinanceDatabase.CATEGORIES} c LEFT JOIN ${FinanceDatabase.CATEGORIES} p ON p.id = c.parent_id WHERE c.name = ?",
            arrayOf(name),
        ).use { if (it.moveToFirst()) it.getString(0) else null }

    /** A database as the first release seeded it - a few of its rows - plus an Insurance category the user made later. */
    private fun SQLiteDatabase.firstCatalog() {
        execSQL("DELETE FROM ${FinanceDatabase.CATEGORIES}")
        add("Food & dining", seeded)
        val groceries = add("Groceries", seeded)
        add("Personal care", seeded, parent = groceries)
        val health = add("Health", seeded)
        add("Spa & massage", seeded, parent = health)
        val other = add("Other", seeded)
        add("Donations", seeded, parent = other)
        add("Salary", seeded, type = "income")
        add("Insurance", later)
    }

    @Test
    fun aDatabaseFromBeforeGetsTheMissingFamiliesWithTheirSubCategories() {
        val helper = FinanceDatabase(context, name = null)
        try {
            val db = helper.writableDatabase
            db.firstCatalog()

            FinanceDatabase.addNewDefaultCategories(db, later)

            for (family in listOf("Fitness", "Events", "Kids", "Pets", "Loans & EMI", "Taxes", "Investments", "Family support")) {
                assertEquals(1L, db.count("name = ? AND parent_id IS NULL", family), "$family is added as a category")
            }
            assertEquals("Kids", db.parentOf("Toys"), "with its sub-categories under it")
            assertEquals("Loans & EMI", db.parentOf("Gold loan"))
            assertEquals(
                db.rawQuery("SELECT color FROM ${FinanceDatabase.CATEGORIES} WHERE name = 'Kids'", null).use { it.moveToFirst(); it.getInt(0) },
                db.rawQuery("SELECT color FROM ${FinanceDatabase.CATEGORIES} WHERE name = 'Toys'", null).use { it.moveToFirst(); it.getInt(0) },
                "a sub-category takes its family's colour",
            )
        } finally {
            helper.close()
        }
    }

    @Test
    fun nothingTheUserHasIsDoubledOrMoved() {
        val helper = FinanceDatabase(context, name = null)
        try {
            val db = helper.writableDatabase
            db.firstCatalog()

            FinanceDatabase.addNewDefaultCategories(db, later)

            // The older sub-categories of the same names stay where they are, with no twin at the top.
            assertEquals(1L, db.count("name = 'Personal care'"))
            assertEquals("Groceries", db.parentOf("Personal care"))
            assertEquals(1L, db.count("name = 'Donations'"))
            assertEquals("Other", db.parentOf("Donations"))
            assertEquals(1L, db.count("name = 'Spa & massage'"))
            assertEquals("Health", db.parentOf("Spa & massage"))
            // The user's own Insurance is theirs: nothing is added to or beside it.
            assertEquals(1L, db.count("name = 'Insurance'"))
            assertEquals(0L, db.count("name = 'Life insurance'"))

            // Run again (it never is, but): nothing more.
            val total = db.count("1 = 1")
            FinanceDatabase.addNewDefaultCategories(db, later)
            assertEquals(total, db.count("1 = 1"))
        } finally {
            helper.close()
        }
    }

    @Test
    fun aDatabaseThatStartedWithThemIsLeftAlone() {
        val helper = FinanceDatabase(context, name = null)
        try {
            val db = helper.writableDatabase
            // The user deleted Pets and what was under it.
            db.execSQL(
                "DELETE FROM ${FinanceDatabase.CATEGORIES} WHERE parent_id = (SELECT id FROM ${FinanceDatabase.CATEGORIES} WHERE name = 'Pets')",
            )
            db.execSQL("DELETE FROM ${FinanceDatabase.CATEGORIES} WHERE name = 'Pets'")
            val total = db.count("1 = 1")

            FinanceDatabase.addNewDefaultCategories(db, later)

            assertEquals(total, db.count("1 = 1"), "nothing added")
            assertEquals(0L, db.count("name = 'Pets'"), "a deleted family doesn't come back")
            assertTrue(db.count("name = 'Kids' AND parent_id IS NULL") == 1L)
        } finally {
            helper.close()
        }
    }
}
