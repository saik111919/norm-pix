package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CategoryTreeTest {

    private fun category(
        id: Long?,
        name: String,
        parentId: Long? = null,
        type: CategoryType = CategoryType.Expense,
        icon: String = "other",
    ): Category = Category(
        id = id,
        name = name,
        type = type,
        icon = icon,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        parentId = parentId,
    )

    private val food = category(1L, "Food")
    private val rent = category(2L, "Rent")
    private val groceries = category(3L, "Groceries", parentId = 1L)
    private val dining = category(4L, "Dining out", parentId = 1L)
    private val utilities = category(5L, "Utilities", parentId = 2L)

    private val childToParent = mapOf(2L to 1L)

    @Test
    fun parentMapHoldsOnlySubCategories() {
        val all = listOf(food, rent, groceries, dining, utilities)
        assertEquals(mapOf(3L to 1L, 4L to 1L, 5L to 2L), all.parentMap())
    }

    @Test
    fun subCategoriesOfKeepsListOrder() {
        val all = listOf(food, rent, dining, groceries, utilities)
        assertEquals(listOf(dining, groceries), all.subCategoriesOf(food))
        assertEquals(emptyList<Category>(), all.subCategoriesOf(groceries))
    }

    @Test
    fun subCategoriesOfAnUnsavedCategoryIsEmpty() {
        val all = listOf(food, groceries)
        assertEquals(emptyList<Category>(), all.subCategoriesOf(category(null, "Draft")))
    }

    @Test
    fun parentOfFindsTheParentInTheList() {
        val all = listOf(food, rent, groceries)
        assertEquals(food, all.parentOf(groceries))
        assertNull(all.parentOf(food))
    }

    @Test
    fun parentOfIsNullWhenTheParentIsNotInTheList() {
        assertNull(listOf(groceries).parentOf(groceries))
    }

    @Test
    fun inTreeOrderPutsEachChildAfterItsTop() {
        val list = listOf(rent, groceries, food, utilities, dining)
        assertEquals(
            listOf("Rent", "Utilities", "Food", "Groceries", "Dining out"),
            list.inTreeOrder().map { it.name },
        )
    }

    @Test
    fun inTreeOrderKeepsAnOrphanOnceAsATopWithItsChildrenAfterIt() {
        val orphan = category(7L, "Orphan", parentId = 99L)
        val orphanChild = category(8L, "Orphan child", parentId = 7L)
        val list = listOf(orphan, food, orphanChild, groceries)
        assertEquals(
            listOf("Orphan", "Orphan child", "Food", "Groceries"),
            list.inTreeOrder().map { it.name },
        )
    }

    @Test
    fun pathNameOfATopLevelCategoryIsItsName() {
        assertEquals("Food", listOf(food, groceries).pathName(food))
    }

    @Test
    fun pathNameOfASubCategoryJoinsItsParentWithAChevron() {
        assertEquals("Food › Groceries", listOf(food, groceries).pathName(groceries))
    }

    @Test
    fun pathNameOfASubCategoryWhoseParentIsMissingIsItsOwnName() {
        assertEquals("Groceries", listOf(groceries).pathName(groceries))
    }

    @Test
    fun rollUpToParentsAddsSubCategoryTotalsIntoTheParent() {
        val totals = listOf(
            CategoryTotal(categoryId = 1L, amount = 100L, count = 2),
            CategoryTotal(categoryId = 2L, amount = 250L, count = 3),
            CategoryTotal(categoryId = 3L, amount = 300L, count = 1),
        )
        val result = rollUpToParents(totals, childToParent)
        assertEquals(listOf(CategoryTotal(1L, 350L, 5), CategoryTotal(3L, 300L, 1)), result)
    }

    @Test
    fun rollUpToParentsPutsAChildWithNoParentTotalUnderTheParentIdLargestFirst() {
        val totals = listOf(
            CategoryTotal(categoryId = 2L, amount = 250L, count = 3),
            CategoryTotal(categoryId = 3L, amount = 300L, count = 1),
        )
        val result = rollUpToParents(totals, childToParent)
        assertEquals(listOf(CategoryTotal(3L, 300L, 1), CategoryTotal(1L, 250L, 3)), result)
    }

    @Test
    fun foldIntoParentsAddsChildSpendingToTheParent() {
        val spent = mapOf(1L to 100L, 2L to 250L)
        assertEquals(mapOf(1L to 350L, 2L to 250L), foldIntoParents(spent, childToParent))
    }

    @Test
    fun foldIntoParentsGivesAParentWithNoOwnSpendingTheChildrenSpending() {
        assertEquals(mapOf(2L to 250L, 1L to 250L), foldIntoParents(mapOf(2L to 250L), childToParent))
    }

    @Test
    fun foldIntoParentsLeavesUnrelatedCategoriesAlone() {
        val spent = mapOf(1L to 100L, 2L to 250L, 3L to 40L)
        assertEquals(mapOf(1L to 350L, 2L to 250L, 3L to 40L), foldIntoParents(spent, childToParent))
    }

    @Test
    fun foldIntoParentsWithNoParentsReturnsAnEqualMap() {
        val spent = mapOf(1L to 100L, 2L to 250L)
        assertEquals(spent, foldIntoParents(spent, emptyMap()))
    }
}
