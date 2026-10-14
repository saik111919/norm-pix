package com.example.normpix.finance.share

import com.example.normpix.finance.data.CategorySuggestions
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MerchantCategoriesTest {
    private val at = LocalDateTime.of(2026, 10, 11, 9, 0)
    private var nextId = 1L

    private fun category(name: String, parent: Category? = null, archived: Boolean = false, type: CategoryType = CategoryType.Expense) =
        Category(id = nextId++, name = name, type = type, icon = "other", archived = archived, createdAt = at, updatedAt = at, parentId = parent?.id)

    private val food = category("Food & dining")
    private val delivery = category("Delivery apps", food)
    private val groceries = category("Groceries")
    private val bills = category("Bills & utilities")
    private val recharge = category("Mobile recharge", bills)
    private val dth = category("DTH / cable", bills)
    private val shopping = category("Shopping")
    private val footwear = category("Footwear", shopping)
    private val transport = category("Transport")
    private val metro = category("Bus & metro", transport)
    private val all = listOf(food, delivery, groceries, bills, recharge, dth, shopping, footwear, transport, metro)

    private fun of(payee: String?, type: TransactionType? = TransactionType.Expense, categories: List<Category> = all) =
        MerchantCategories.categoryFor(payee, type, categories)

    @Test
    fun `a known merchant files under its sub-category`() {
        assertEquals(delivery.id, of("SWIGGY"))
        assertEquals(delivery.id, of("Zomato Ltd"))
        assertEquals(delivery.id, of("swiggy.in@axisbank"))
        assertEquals(recharge.id, of("Bharti Airtel Ltd"))
        assertEquals(transport.id, of("Uber India"))
    }

    @Test
    fun `the more specific name wins`() {
        assertEquals(groceries.id, of("Swiggy Instamart"))
        assertEquals(groceries.id, of("SWIGGYINSTAMART"))
        assertEquals(dth.id, of("Airtel DTH"))
        assertEquals(footwear.id, of("Metro Shoes"))
        assertEquals(metro.id, of("Bangalore Metro BMRCL"))
    }

    @Test
    fun `without the sub-category the parent is used, and without the parent nothing`() {
        assertEquals(food.id, of("Swiggy", categories = listOf(food, groceries)))
        assertEquals(food.id, of("Swiggy", categories = listOf(food, category("Delivery apps", food, archived = true))))
        assertNull(of("Swiggy", categories = listOf(groceries)))
        assertNull(of("Swiggy", categories = listOf(category("Food & dining", archived = true))))
    }

    @Test
    fun `only payments, only known names, only whole words`() {
        assertNull(of("Swiggy", TransactionType.Income))
        assertNull(of("Swiggy", null))
        assertNull(of("Rahul Sharma"))
        assertNull(of(null))
        assertNull(of(" "))
        // "jio" inside another word is not Jio; short names never match inside words.
        assertNull(of("Jiora Traders"))
        assertNull(of("Olaf Stores"))
    }

    @Test
    fun `every category the table files into is one the app starts with`() {
        val parents = FinanceDatabase.defaultCategories.filter { it.type == "expense" }.map { it.name }.toSet()
        for ((parent, sub) in MerchantCategories.targets) {
            assertTrue(parent in parents, "no top-level category $parent")
            if (sub != null) {
                assertTrue(CategorySuggestions.byParent[parent].orEmpty().any { it.name == sub }, "no sub-category $parent › $sub")
            }
        }
    }

    @Test
    fun `insurance goes in Insurance, or for an install from before it, where it went then`() {
        val insurance = category("Insurance")
        val premium = category("Insurance premium", bills)
        assertEquals(insurance.id, of("LIC of India", categories = all + insurance + premium))
        assertEquals(premium.id, of("LIC of India", categories = all + premium))
        assertEquals(bills.id, of("Acko General Insurance"), "an older install without the sub-category: its parent")
        assertNull(of("Acko", categories = listOf(food)))
    }
}
