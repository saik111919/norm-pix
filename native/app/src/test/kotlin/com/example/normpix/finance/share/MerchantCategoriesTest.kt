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

    @Test
    fun `Swiggy and Zomato file under their own sub-category, and on a phone without it under Delivery apps`() {
        val swiggy = category("Swiggy", food)
        val zomato = category("Zomato", food)
        val both = all + swiggy + zomato
        assertEquals(swiggy.id, of("SWIGGY", categories = both))
        assertEquals(swiggy.id, of("swiggy.in@axisbank", categories = both))
        assertEquals(zomato.id, of("Zomato Ltd", categories = both))
        // A phone that has only one of them keeps the broader sub-category for the other, as it always did.
        assertEquals(swiggy.id, of("Swiggy", categories = all + swiggy))
        assertEquals(delivery.id, of("Zomato", categories = all + swiggy))
        assertEquals(delivery.id, of("Swiggy"))
        assertEquals(delivery.id, of("Zomato"))
        // The other food apps stay in Delivery apps.
        assertEquals(delivery.id, of("EatSure", categories = both))
        assertEquals(delivery.id, of("Box8", categories = both))
        // And a phone with neither Swiggy nor Delivery apps still gets Food & dining.
        assertEquals(food.id, of("Zomato", categories = listOf(food)))
    }

    @Test
    fun `quick-commerce apps file under their own sub-category of Groceries, and without it under Groceries`() {
        val instamart = category("Instamart", groceries)
        val blinkit = category("Blinkit", groceries)
        val zepto = category("Zepto", groceries)
        val bigbasket = category("BigBasket", groceries)
        val dmart = category("DMart", groceries)
        val withSubs = all + instamart + blinkit + zepto + bigbasket + dmart
        // Swiggy's grocery arm is Instamart, not Swiggy's food sub-category.
        assertEquals(instamart.id, of("Swiggy Instamart", categories = withSubs))
        assertEquals(instamart.id, of("SWIGGYINSTAMART", categories = withSubs))
        assertEquals(blinkit.id, of("Blinkit Commerce Pvt Ltd", categories = withSubs))
        assertEquals(zepto.id, of("Zepto Marketplace", categories = withSubs))
        assertEquals(bigbasket.id, of("BigBasket", categories = withSubs))
        assertEquals(bigbasket.id, of("BB Now", categories = withSubs))
        assertEquals(dmart.id, of("Avenue Supermarts Ltd", categories = withSubs))
        // The grocers without a sub-category of their own are plain Groceries.
        assertEquals(groceries.id, of("JioMart", categories = withSubs))
        // On a phone that has not added them, it is exactly what it was before they existed.
        for (payee in listOf("Swiggy Instamart", "Blinkit", "Zepto", "BigBasket", "DMart")) assertEquals(groceries.id, of(payee), payee)
    }

    @Test
    fun `Uber, Ola and Rapido file under their own sub-category, and without it under Cabs & rideshare`() {
        val cabs = category("Cabs & rideshare", transport)
        val uber = category("Uber", transport)
        val ola = category("Ola", transport)
        val rapido = category("Rapido", transport)
        val withBrands = all + cabs + uber + ola + rapido
        assertEquals(uber.id, of("Uber India Systems Private Limited", categories = withBrands))
        assertEquals(ola.id, of("OLA CABS", categories = withBrands))
        assertEquals(ola.id, of("Olacabs", categories = withBrands))
        assertEquals(rapido.id, of("Rapido", categories = withBrands))
        assertEquals(cabs.id, of("BluSmart", categories = withBrands))
        // Without the brand's own: the broader one; without that: Transport.
        assertEquals(cabs.id, of("Uber", categories = all + cabs))
        assertEquals(cabs.id, of("Ola", categories = all + cabs + uber))
        assertEquals(transport.id, of("Rapido"))
        assertNull(of("Olaf Stores", categories = withBrands))
    }

    @Test
    fun `a FASTag recharge files under its own sub-category, and without it under Tolls`() {
        val tolls = category("Tolls", transport)
        val recharge = category("FASTag recharge", transport)
        assertEquals(recharge.id, of("Paytm FASTag", categories = all + tolls + recharge))
        assertEquals(tolls.id, of("Paytm FASTag", categories = all + tolls))
        assertEquals(tolls.id, of("NHAI toll plaza", categories = all + tolls + recharge), "a toll itself is still a toll")
        assertEquals(transport.id, of("Paytm FASTag"))
    }

    @Test
    fun `a meal word files under its meal, and on a phone without it under Food & dining`() {
        val breakfast = category("Breakfast", food)
        val lunch = category("Lunch", food)
        val dinner = category("Dinner", food)
        val snacks = category("Snacks", food)
        val tiffin = category("Tiffin & mess", food)
        val withMeals = all + breakfast + lunch + dinner + snacks + tiffin
        fun said(message: String, categories: List<Category> = all) =
            MerchantCategories.categoryForMessage(message, TransactionType.Expense, categories)
        assertEquals(breakfast.id, said("Breakfast", withMeals))
        assertEquals(lunch.id, said("lunch", withMeals))
        assertEquals(dinner.id, said("Dinner", withMeals))
        assertEquals(snacks.id, said("Snacks", withMeals))
        assertEquals(tiffin.id, said("Tiffin", withMeals))
        assertEquals(food.id, said("Food", withMeals))
        for (word in listOf("Breakfast", "Lunch", "Dinner", "Snacks", "Tiffin")) assertEquals(food.id, said(word), word)
        // Only the whole message counts, and only for a payment.
        assertNull(said("Lunch with Rahul", withMeals))
        assertNull(MerchantCategories.categoryForMessage("Lunch", TransactionType.Income, withMeals))
    }
}
