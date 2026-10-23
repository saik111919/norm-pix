package com.example.normpix.finance.data

import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.emojiIcon
import com.example.normpix.finance.domain.parentMap
import com.example.normpix.finance.domain.rollUpToParents
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
 * Sub-categories: a category under another one, two levels at most and of the same kind. What that means for saving,
 * archiving and deleting, for filtering a history by a parent, for the budgets that cover a parent's sub-categories, and for
 * a backup that carries the whole tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubCategoryRepositoryTest {
    private val now = LocalDateTime.of(2026, 9, 15, 12, 0)
    private val september = MonthKey(2026, 9)
    private lateinit var repo: FinanceRepository

    @BeforeTest
    fun setUp() {
        val database = FinanceDatabase(ApplicationProvider.getApplicationContext(), name = null, clock = { now })
        repo = FinanceRepository(database, clock = { now }, dispatcher = Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDown() = repo.close()

    private suspend fun saved(
        name: String,
        parent: Category? = null,
        type: CategoryType = CategoryType.Expense,
        color: Int = 2,
        icon: String = "other",
    ): Category = repo.saveCategory(
        Category(name = name, type = type, icon = icon, color = color, parentId = parent?.id, createdAt = now, updatedAt = now),
    )

    private suspend fun category(id: Long?): Category = repo.categories().first { it.id == id }

    private suspend fun spend(amount: Long, category: Category) {
        val cash = repo.accounts().first { it.name == "Cash" }
        repo.saveTransaction(
            FinanceTransaction(
                type = TransactionType.Expense,
                amount = amount,
                accountId = cash.id!!,
                categoryId = category.id,
                occurredAt = now.minusHours(1),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Test
    fun aSubCategoryTakesItsParentsColor_andFollowsWhenThatChanges() = runBlocking<Unit> {
        val food = saved("Eating out", color = 1)
        val pizza = saved("Pizzeria", parent = food, color = 5)
        assertEquals(1, pizza.color, "a family reads as one")
        assertEquals(food.id, category(pizza.id).parentId)

        repo.saveCategory(food.copy(color = 6))
        assertEquals(6, category(pizza.id).color)
        assertEquals(6, category(food.id).color)
    }

    @Test
    fun aNewInstallStartsWithAReadyMadeTree_twoLevelsOfTheSameKind_inItsParentsColour() = runBlocking<Unit> {
        // (An upgraded database keeps every category it had at the top level: see BankMigrationTest.)
        val all = repo.categories()
        val byId = all.associateBy { it.id }
        val subs = all.filter { it.parentId != null }
        assertTrue(subs.isNotEmpty(), "a new user starts with sub-categories to rename, archive or extend")
        for (sub in subs) {
            val parent = byId.getValue(sub.parentId!!)
            assertNull(parent.parentId, "${sub.name} sits under a top-level category, never under another sub-category")
            assertEquals(parent.type, sub.type, "${sub.name} is the same kind as ${parent.name}")
            assertEquals(parent.color, sub.color, "${sub.name} takes ${parent.name}'s colour")
        }
    }

    @Test
    fun twoLevelsAtMost_theSameKind_andNeverUnderItself() = runBlocking<Unit> {
        val food = saved("Eating out")
        val pizza = saved("Pizzeria", parent = food)

        assertFailsWith<FinanceValidationException> { saved("Margherita", parent = pizza) }
        assertFailsWith<FinanceValidationException> { repo.saveCategory(food.copy(parentId = food.id)) }
        assertFailsWith<FinanceValidationException> { saved("Bonus", parent = food, type = CategoryType.Income) }
        assertFailsWith<FinanceValidationException> {
            repo.saveCategory(
                Category(name = "Lost", type = CategoryType.Expense, icon = "other", parentId = 9_999, createdAt = now, updatedAt = now),
            )
        }
    }

    @Test
    fun aCategoryWithSubCategoriesCannotGoUnderAnotherOne() = runBlocking<Unit> {
        val food = saved("Eating out")
        saved("Pizzeria", parent = food)
        val travel = saved("Travel")

        assertFailsWith<FinanceConflictException> { repo.saveCategory(food.copy(parentId = travel.id)) }
        assertNull(category(food.id).parentId)
    }

    @Test
    fun aSubCategoryCanMoveOutToTopLevel_orToAnotherParent() = runBlocking<Unit> {
        val food = saved("Eating out")
        val travel = saved("Travel", color = 4)
        val pizza = saved("Pizzeria", parent = food)

        val moved = repo.saveCategory(pizza.copy(parentId = travel.id))
        assertEquals(travel.id, moved.parentId)
        assertEquals(4, moved.color, "and takes the new parent's color")

        val out = repo.saveCategory(moved.copy(parentId = null))
        assertNull(out.parentId)
    }

    @Test
    fun deletingAParentWithSubCategoriesIsRefused_untilTheyAreGone() = runBlocking<Unit> {
        val food = saved("Eating out")
        val pizza = saved("Pizzeria", parent = food)

        assertFailsWith<FinanceConflictException> { repo.deleteCategory(food.id!!) }
        repo.deleteCategory(pizza.id!!)
        repo.deleteCategory(food.id!!)
        assertTrue(repo.categories().none { it.name == "Eating out" || it.name == "Pizzeria" })
    }

    @Test
    fun archivingAParentTakesItsSubCategories_andOnlyTheParentCanComeBackFirst() = runBlocking<Unit> {
        val food = saved("Eating out")
        val pizza = saved("Pizzeria", parent = food)

        repo.setCategoryArchived(food.id!!, true)
        assertTrue(category(food.id).archived)
        assertTrue(category(pizza.id).archived, "one left showing under a hidden parent would have nowhere to be")

        assertFailsWith<FinanceConflictException> { repo.setCategoryArchived(pizza.id!!, false) }
        assertFailsWith<FinanceValidationException> { saved("Burger", parent = category(food.id)) }

        repo.setCategoryArchived(food.id, false)
        assertFalse(category(food.id).archived)
        assertTrue(category(pizza.id).archived, "restoring the parent does not guess which sub-categories to bring back")
        repo.setCategoryArchived(pizza.id!!, false)
        assertFalse(category(pizza.id).archived)
    }

    @Test
    fun filteringAHistoryByAParentIncludesItsSubCategories() = runBlocking<Unit> {
        val food = saved("Eating out")
        val pizza = saved("Pizzeria", parent = food)
        val travel = saved("Travel")
        spend(100_00, food)
        spend(200_00, pizza)
        spend(400_00, travel)

        val byFood = TransactionQuery(categoryIds = setOf(food.id!!))
        assertEquals(setOf(100_00L, 200_00L), repo.transactions(byFood).items.map { it.amount }.toSet())
        assertEquals(300_00L, repo.summaryFor(byFood).expense)
        assertEquals(listOf(200_00L), repo.transactions(TransactionQuery(categoryIds = setOf(pizza.id!!))).items.map { it.amount })
    }

    @Test
    fun totalsStayPerCategory_andRollUpToTheParentWhenAsked() = runBlocking<Unit> {
        val food = saved("Eating out")
        val pizza = saved("Pizzeria", parent = food)
        spend(100_00, food)
        spend(200_00, pizza)

        val totals = repo.categoryTotals(september.range, TransactionType.Expense)
        assertEquals(setOf(food.id, pizza.id), totals.map { it.categoryId }.toSet(), "the books are per category")

        val rolled = rollUpToParents(totals, repo.categories().parentMap())
        assertEquals(1, rolled.size)
        assertEquals(food.id, rolled.single().categoryId)
        assertEquals(300_00L, rolled.single().amount)
        assertEquals(2, rolled.single().count)
    }

    @Test
    fun aParentsBudgetCountsItsSubCategories_andTheHeadlineDoesNotCountThemTwice() = runBlocking<Unit> {
        val food = saved("Eating out")
        val pizza = saved("Pizzeria", parent = food)
        spend(100_00, food)
        spend(200_00, pizza)
        repo.setBudget(food.id, september, 1_000_00)
        repo.setBudget(pizza.id, september, 500_00)

        val overview = repo.budgetOverview(september)
        assertEquals(300_00L, overview.categories.getValue(food.id!!).spent, "the parent sees its own and its sub-categories'")
        assertEquals(200_00L, overview.categories.getValue(pizza.id!!).spent, "the sub-category sees only its own")
        assertEquals(setOf(pizza.id), overview.nested)
        val headline = assertNotNull(overview.headline)
        assertEquals(1_000_00L, headline.allocated, "the sub-category's limit lies inside the parent's")
        assertEquals(300_00L, headline.spent)

        // With only the sub-category budgeted there is nothing to leave out.
        repo.setBudget(food.id, september, null)
        val alone = repo.budgetOverview(september)
        assertTrue(alone.nested.isEmpty())
        assertEquals(500_00L, assertNotNull(alone.headline).allocated)
        assertEquals(200_00L, assertNotNull(alone.headline).spent)
    }

    @Test
    fun anEmojiIconIsKept_andAnythingElseWithThePrefixIsRefused() = runBlocking<Unit> {
        val pizza = saved("Pizzeria", icon = emojiIcon("🍕"))
        assertEquals("emoji:🍕", category(pizza.id).icon)
        val family = saved("Family", icon = emojiIcon("👨‍👩‍👧"))
        assertEquals(emojiIcon("👨‍👩‍👧"), category(family.id).icon)

        for (bad in listOf("emoji:", "emoji:pizza", "emoji:🍕🍔", "emoji: 🍕")) {
            assertFailsWith<FinanceValidationException>(bad) { saved("Bad", icon = bad) }
        }
    }

    @Test
    fun aBackupCarriesTheTree_andRestoringItBringsItBack() = runBlocking<Unit> {
        val food = saved("Eating out", color = 3)
        val pizza = saved("Pizzeria", parent = food, icon = emojiIcon("🍕"))
        spend(250_00, pizza)

        val text = FinanceBackupCodec.encode(repo.snapshot(), now)
        val read = FinanceBackupCodec.decode(text)
        val snapshot = assertNotNull(read.snapshot, read.errors.toString())
        assertEquals(food.id, snapshot.categories.first { it.name == "Pizzeria" }.parentId)

        repo.resetAll()
        repo.restore(snapshot)
        val restored = repo.categories()
        val restoredFood = restored.first { it.name == "Eating out" }
        val restoredPizza = restored.first { it.name == "Pizzeria" }
        assertEquals(restoredFood.id, restoredPizza.parentId)
        assertEquals(emojiIcon("🍕"), restoredPizza.icon)
        assertEquals(3, restoredPizza.color)
    }

    @Test
    fun aBackupWithABrokenTreeIsRejected() = runBlocking<Unit> {
        val food = saved("Eating out")
        val pizza = saved("Pizzeria", parent = food)
        val snapshot = repo.snapshot()

        fun problems(change: (Category) -> Category) =
            FinanceBackupCodec.validate(snapshot.copy(categories = snapshot.categories.map { if (it.id == pizza.id) change(it) else it }))

        assertTrue(problems { it.copy(parentId = 12_345) }.any { "not in the backup" in it })
        assertTrue(problems { it.copy(type = CategoryType.Income) }.any { "same kind" in it })
        assertTrue(problems { it.copy(icon = "emoji:abc") }.any { "not one emoji" in it })
        assertTrue(problems { it.copy(parentId = pizza.id) }.any { "one level" in it }, "under itself")
        assertTrue(FinanceBackupCodec.validate(snapshot).isEmpty())
    }
}
