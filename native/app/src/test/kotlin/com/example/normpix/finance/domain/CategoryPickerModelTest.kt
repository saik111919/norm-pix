package com.example.normpix.finance.domain

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the category picker shows: the categories grouped into families, and what a search finds. */
class CategoryPickerModelTest {
    private fun category(id: Long, name: String, parentId: Long? = null, archived: Boolean = false) = Category(
        id = id,
        name = name,
        type = CategoryType.Expense,
        icon = "other",
        archived = archived,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        updatedAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        parentId = parentId,
    )

    private val food = category(1, "Food & dining")
    private val pizza = category(2, "Pizza", parentId = 1)
    private val coffee = category(3, "Coffee & tea", parentId = 1)
    private val rent = category(4, "Rent & housing")
    private val repairs = category(5, "Repairs", parentId = 4)
    private val travel = category(6, "Travel")
    private val all = listOf(food, rent, travel, pizza, coffee, repairs)
    private val path = { c: Category -> all.pathName(c) }

    @Test
    fun `families group each category with its own sub-categories`() {
        val families = all.families()
        assertEquals(listOf("Food & dining", "Rent & housing", "Travel"), families.map { it.category.name })
        assertEquals(listOf("Pizza", "Coffee & tea"), families[0].subs.map { it.name })
        assertEquals(listOf("Repairs"), families[1].subs.map { it.name })
        assertTrue(families[2].subs.isEmpty())
    }

    @Test
    fun `a sub-category whose parent is not offered stands as a family of its own`() {
        val families = listOf(pizza, rent).families()
        assertEquals(listOf("Pizza", "Rent & housing"), families.map { it.category.name })
    }

    @Test
    fun `a blank search is everything, in tree order`() {
        assertEquals(listOf("Food & dining", "Pizza", "Coffee & tea", "Rent & housing", "Repairs", "Travel"), all.matching("   ", path).map { it.name })
    }

    @Test
    fun `a search finds a category by its own name, ignoring case`() {
        assertEquals(listOf("Pizza"), all.matching("PIZ", path).map { it.name })
        assertEquals(listOf("Coffee & tea"), all.matching("tea", path).map { it.name })
    }

    @Test
    fun `a search by a parent's name brings the parent and all its sub-categories`() {
        assertEquals(listOf("Food & dining", "Pizza", "Coffee & tea"), all.matching("food", path).map { it.name })
    }

    @Test
    fun `every word typed has to match, in any order, so a sub-category is found by parent and name together`() {
        assertEquals(listOf("Pizza"), all.matching("food pizza", path).map { it.name })
        assertEquals(listOf("Pizza"), all.matching("pizza food", path).map { it.name })
        assertEquals(emptyList(), all.matching("rent pizza", path).map { it.name })
    }

    @Test
    fun `nothing matches nothing`() {
        assertEquals(emptyList(), all.matching("zzz", path))
    }
}
