package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.categoryIcons
import com.example.normpix.storage.rawQuery
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup holds the user's categories, not the app's (owner, 2026-11-12): the default categories that nothing used and nobody
 * changed are left out and made again when it is put back, everything else is kept whole with its id, a default the user removed
 * stays removed, and a file from before - which lists every category - is restored exactly as it always was.
 *
 * And the families of 2026-11-12 (money sent and received, and the others): a new ledger has them, and one that already existed is
 * given them once, with nothing of the user's doubled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCategoriesTest {
    private val now = LocalDateTime.of(2026, 11, 12, 12, 0)
    private lateinit var database: FinanceDatabase
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private fun runDb(block: suspend () -> Unit) = runBlocking { block() }

    private suspend fun named(name: String): Category = repo.categories().first { it.name == name }

    private suspend fun spendOn(category: String, amount: Long = 10_000): FinanceTransaction = repo.saveTransaction(
        FinanceTransaction(
            type = TransactionType.Expense,
            amount = amount,
            accountId = repo.accounts().first().id!!,
            categoryId = named(category).id,
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
        ),
    )

    /** What a category is to the eye, whatever its id or its place: its kind, family, name, icon, colour and whether it is archived. */
    private suspend fun faces(): Set<String> {
        val all = repo.categories()
        val byId = all.associateBy { it.id }
        return all.mapTo(HashSet()) { c -> "${c.type.key}|${c.parentId?.let { byId[it]?.name }}|${c.name}|${c.icon}|${c.color}|${c.archived}" }
    }

    /** The same categories to the eye before and after - said as what went missing and what appeared, not as two long lists. */
    private fun assertSameFaces(before: Set<String>, after: Set<String>) {
        assertEquals(emptySet(), before - after, "gone after the restore")
        assertEquals(emptySet(), after - before, "new after the restore")
    }

    /** The ledger as a backup made now would be read back. */
    private suspend fun backedUp(): FinanceSnapshot {
        val json = FinanceBackupCodec.encode(repo.snapshot().trimmedForBackup(), exportedAt = now)
        val read = FinanceBackupCodec.decode(json)
        assertTrue(read.isValid, read.errors.toString())
        return read.snapshot!!
    }

    @Test
    fun `the plan of defaults is what a new ledger is seeded with`() = runDb {
        val seeded = repo.categories()
        val byId = seeded.associateBy { it.id }
        val plan = FinanceDatabase.defaultSeedPlan
        assertEquals(plan.size, seeded.size)
        assertEquals(
            plan.map { "${it.type}|${it.parent}|${it.name}|${it.icon}|${it.color}|${it.order}" }.toSet(),
            seeded.map { c -> "${c.type.key}|${c.parentId?.let { byId[it]?.name }}|${c.name}|${c.icon}|${c.color}|${c.sortOrder}" }.toSet(),
        )
    }

    @Test
    fun `a new ledger's backup holds none of the app's categories`() = runDb {
        val backup = repo.snapshot().trimmedForBackup()
        assertTrue(repo.categories().size > 200, "the app's own are many: ${repo.categories().size}")
        assertEquals(emptyList(), backup.categories)
        assertEquals(emptyList(), backup.removedDefaults, "and none was removed")
    }

    @Test
    fun `what is used, changed or the user's own is kept whole, and the rest is left out`() = runDb {
        spendOn("Restaurants") // a sub-category in use: it, and the family it is under
        repo.setBudget(named("Groceries").id, from = MonthKey(2026, 11), amount = 500_000)
        repo.saveCategory(named("Pets").copy(name = "Animals")) // renamed
        repo.saveCategory(named("Fitness").copy(color = 7)) // recoloured
        repo.setCategoryArchived(named("Taxes").id!!, true) // archived
        val hobbies = repo.saveCategory(Category(name = "Hobbies", type = CategoryType.Expense, icon = "other", createdAt = now, updatedAt = now))
        val trek = repo.saveCategory(
            Category(name = "Trekking", type = CategoryType.Expense, icon = "other", parentId = named("Travel").id, createdAt = now, updatedAt = now),
        )

        val kept = repo.snapshot().trimmedForBackup().categories
        val names = kept.map { it.name }.toSet()
        for (name in listOf("Restaurants", "Food & dining", "Groceries", "Animals", "Fitness", "Taxes", "Hobbies", "Trekking", "Travel")) {
            assertTrue(name in names, "$name is in the backup: $names")
        }
        for (name in listOf("Coffee & tea", "Transport", "Salary", "Kids", "Money sent")) {
            assertFalse(name in names, "$name is the app's, unused: it is left out")
        }
        // Whole, and with the ids everything else in the file refers to them by.
        val before = repo.categories().associateBy { it.id }
        for (c in kept) assertEquals(before[c.id], c)
        assertEquals(hobbies.id, kept.first { it.name == "Hobbies" && it.parentId == null }.id)
        // The app has a sub-category of that name too (under Entertainment): with a namesake it could not be made again, so it stays.
        assertEquals(1, kept.count { it.name == "Hobbies" && it.parentId != null })
        assertEquals(named("Travel").id, kept.first { it.id == trek.id }.parentId)
    }

    @Test
    fun `export, reset and restore brings back all of it, and the app's categories are there again`() = runDb {
        val meal = spendOn("Restaurants")
        val fuel = spendOn("Petrol & diesel")
        repo.setBudget(named("Groceries").id, from = MonthKey(2026, 11), amount = 500_000)
        repo.saveCategory(named("Pets").copy(name = "Animals", color = 3))
        repo.setCategoryArchived(named("Taxes").id!!, true)
        val hobbies = repo.saveCategory(Category(name = "Hobbies", type = CategoryType.Expense, icon = "other", createdAt = now, updatedAt = now))
        val facesBefore = faces()
        val keptBefore = repo.snapshot().trimmedForBackup().categories
        val orderBefore = repo.categories().map { it.name }

        val backup = backedUp()
        assertTrue(backup.categories.size < 20, "a handful, not the whole catalogue: ${backup.categories.size}")
        repo.resetAll()
        repo.restore(backup)

        // Every entry is on the category it was on, by the same id.
        assertEquals(meal.categoryId, repo.transactionById(meal.id!!)!!.categoryId)
        assertEquals("Restaurants", repo.categories().first { it.id == meal.categoryId }.name)
        assertEquals("Petrol & diesel", repo.categories().first { it.id == fuel.categoryId }.name)
        assertEquals(named("Groceries").id, repo.snapshot().budgets.single().categoryId)
        // What the file kept is exactly as it was.
        val after = repo.categories().associateBy { it.id }
        for (c in keptBefore) assertEquals(c, after[c.id], c.name)
        // The user's own "Hobbies" and the app's sub-category of the same name, under Entertainment: both, as before. (The app's
        // could not have been made again beside a namesake, so the file kept it.)
        val bothHobbies = repo.categories().filter { it.name == "Hobbies" }
        assertEquals(setOf(hobbies.id, backup.categories.first { it.name == "Hobbies" && it.parentId != null }.id), bothHobbies.map { it.id }.toSet())
        assertTrue(named("Taxes").archived)
        // And the whole catalogue is back: the same categories to the eye, in the same order, none twice.
        assertSameFaces(facesBefore, faces())
        assertEquals(orderBefore, repo.categories().map { it.name })
        assertEquals(orderBefore.size, repo.categories().size, "none twice, none missing")
    }

    @Test
    fun `a default the user removed stays removed, and a renamed one does not come back beside its new name`() = runDb {
        val kids = named("Kids")
        for (sub in repo.categories().filter { it.parentId == kids.id }) repo.deleteCategory(sub.id!!)
        repo.deleteCategory(kids.id!!)
        repo.deleteCategory(named("Pizza").id!!)
        repo.saveCategory(named("Pets").copy(name = "Animals"))
        val facesBefore = faces()

        val backup = backedUp()
        val removed = backup.removedDefaults!!.map { it.name }
        assertTrue("Kids" in removed && "Toys" in removed && "Pizza" in removed && "Pets" in removed, removed.toString())
        repo.resetAll()
        repo.restore(backup)

        val names = repo.categories().map { it.name }
        assertFalse("Kids" in names)
        assertFalse("Toys" in names, "nor its sub-categories")
        assertFalse("Pizza" in names)
        assertFalse("Pets" in names, "it is Animals now")
        assertEquals(1, names.count { it == "Animals" })
        assertSameFaces(facesBefore, faces())
    }

    @Test
    fun `a file from before lists every category and is restored exactly as it was`() = runDb {
        spendOn("Restaurants")
        repo.deleteCategory(named("Pizza").id!!)
        val whole = repo.snapshot()
        val json = FinanceBackupCodec.encode(whole, exportedAt = now)
        assertFalse("removedDefaultCategories" in json, "a file that lists everything says nothing of removed defaults")
        val read = FinanceBackupCodec.decode(json).snapshot
        assertNotNull(read)
        assertNull(read.removedDefaults)
        assertEquals(whole.categories, read.categories)

        repo.resetAll()
        repo.restore(read)
        assertEquals(whole.categories, repo.snapshot().categories, "the same categories, ids and all: nothing added, Pizza not back")
    }

    @Test
    fun `a file from a newer version of the app is refused`() = runDb {
        val json = FinanceBackupCodec.encode(repo.snapshot().trimmedForBackup(), exportedAt = now)
        assertTrue("\"version\": ${FinanceBackupCodec.FORMAT_VERSION}" in json, "written as the current version")
        val newer = json.replace("\"version\": ${FinanceBackupCodec.FORMAT_VERSION}", "\"version\": ${FinanceBackupCodec.FORMAT_VERSION + 1}")
        assertFalse(FinanceBackupCodec.decode(newer).isValid)
    }

    // ------------------------------------------------------------ the families of 2026-11-12

    @Test
    fun `a new ledger has money sent and received, and the other new families, each with a picture`() = runDb {
        val all = repo.categories()
        val byId = all.associateBy { it.id }
        for (family in CategorySuggestions.addedIn13) {
            val row = all.singleOrNull { it.name == family && it.parentId == null }
            assertNotNull(row, "$family is a top-level category")
            assertTrue(all.count { it.parentId == row.id } >= 3, "$family has sub-categories")
        }
        assertEquals(CategoryType.Expense, named("Money sent").type)
        assertEquals(CategoryType.Income, named("Money received").type)
        assertEquals("Money received", byId[named("Received from friends").parentId]?.name)
        assertEquals("Money sent", byId[named("Lent to someone").parentId]?.name)
        // No default falls back to the "other" picture: its icon is one the app has, or an emoji of its own.
        for (seed in FinanceDatabase.defaultSeedPlan) {
            assertTrue(seed.icon.startsWith("emoji:") || seed.icon in categoryIcons, "${seed.name}: no icon \"${seed.icon}\"")
        }
    }

    @Test
    fun `a ledger that already existed is given the new families once, and nothing the user has is doubled`() {
        val db = database.writableDatabase
        fun count(where: String, vararg args: String): Long =
            db.rawQuery("SELECT COUNT(*) FROM ${FinanceDatabase.CATEGORIES} WHERE $where", args).use { it.moveToFirst(); it.getLong(0) }

        // As it was before version 13: none of the new families - but the user has a "Money received" of their own, under Salary.
        val families = CategorySuggestions.addedIn13.joinToString(",") { "'$it'" }
        db.execSQL("DELETE FROM ${FinanceDatabase.CATEGORIES} WHERE parent_id IN (SELECT id FROM ${FinanceDatabase.CATEGORIES} WHERE name IN ($families))")
        db.execSQL("DELETE FROM ${FinanceDatabase.CATEGORIES} WHERE name IN ($families)")
        db.execSQL(
            "INSERT INTO ${FinanceDatabase.CATEGORIES} (name, type, icon, created_at, updated_at, parent_id) " +
                "SELECT 'Money received', 'income', 'other', 1, 1, id FROM ${FinanceDatabase.CATEGORIES} WHERE name = 'Salary'",
        )
        val before = count("1 = 1")

        FinanceDatabase.addDefaultFamilies(db, now, CategorySuggestions.addedIn13)

        for (family in CategorySuggestions.addedIn13 - "Money received") {
            assertEquals(1L, count("name = ? AND parent_id IS NULL", family), "$family is added")
        }
        assertEquals(1L, count("name = 'Sent to friends'"), "with its sub-categories")
        assertEquals(1L, count("name = 'Money received'"), "the user's own is not doubled")
        assertEquals(0L, count("name = 'Money received' AND parent_id IS NULL"), "nor moved")
        assertEquals(0L, count("name = 'Received from friends'"), "and nothing is hung under a family that was not added")
        assertTrue(count("1 = 1") > before)

        // Asked again, it adds nothing.
        val after = count("1 = 1")
        FinanceDatabase.addDefaultFamilies(db, now, CategorySuggestions.addedIn13)
        assertEquals(after, count("1 = 1"))
    }
}
