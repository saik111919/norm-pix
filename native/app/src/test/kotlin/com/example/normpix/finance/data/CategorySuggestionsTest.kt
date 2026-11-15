package com.example.normpix.finance.data

import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.finance.domain.isSingleEmoji
import com.example.normpix.finance.ui.brandAsset
import com.example.normpix.finance.ui.brandKey
import com.example.normpix.finance.ui.brandLogo
import com.example.normpix.finance.ui.categoryIcons
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test

/**
 * The categories a new install starts with: every ready-made sub-category sits under a top-level category the app seeds,
 * no name is used twice (adding them again is skipped by name), and every icon is one the app can draw.
 */
class CategorySuggestionsTest {
    private val defaults = FinanceDatabase.defaultCategories

    @Test
    fun `every family of sub-categories belongs to a seeded top-level category`() {
        val parents = defaults.map { it.name }.toSet()
        for (parent in CategorySuggestions.byParent.keys) assertTrue(parent in parents, "no top-level category $parent")
    }

    @Test
    fun `no name is used twice, top-level or sub-category`() {
        val names = defaults.map { it.name.lowercase() } + CategorySuggestions.byParent.values.flatten().map { it.name.lowercase() }
        val repeated = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(emptySet(), repeated)
    }

    @Test
    fun `every icon is a built-in icon or a single emoji`() {
        val icons = defaults.map { it.name to it.icon } + CategorySuggestions.byParent.values.flatten().map { it.name to it.icon }
        for ((name, icon) in icons) {
            val emoji = emojiOf(icon)
            assertTrue(icon in categoryIcons || (emoji != null && isSingleEmoji(emoji)), "$name has an icon the app doesn't know: $icon")
        }
    }

    @Test
    fun `the spending areas a household has each get a top-level category`() {
        val expenses = defaults.filter { it.type == "expense" }.map { it.name }
        for (area in listOf("Personal care", "Fitness", "Kids", "Pets", "Insurance", "Taxes", "Investments", "Events", "Loans & EMI", "Family support", "Donations")) {
            assertTrue(area in expenses, "no top-level $area")
        }
        assertEquals("Other", expenses.last(), "Other comes last")
    }

    private fun icons(parent: String) = CategorySuggestions.byParent.getValue(parent).associate { it.name to it.icon }

    @Test
    fun `Food & dining has the meals, food apps and mess routines of an ordinary Indian week`() {
        val food = icons("Food & dining")
        for (name in listOf("Snacks", "Lunch", "Dinner", "Swiggy", "Zomato", "Tiffin & mess", "Office canteen", "Sweets & mithai")) {
            assertTrue(name in food, "no Food & dining sub-category $name")
        }
        // The older ones are still there, in front: a new install lists them first, as before.
        assertEquals(
            listOf("Restaurants", "Coffee & tea", "Fast food", "Takeaway", "Delivery apps", "Street food", "Desserts", "Pizza", "Wine & beer", "Breakfast"),
            CategorySuggestions.byParent.getValue("Food & dining").map { it.name }.take(10),
        )
        assertEquals("swiggy", food["Swiggy"])
        assertEquals("zomato", food["Zomato"])
    }

    @Test
    fun `the quick-commerce, ride and OTT brands wear their own logo`() {
        assertEquals("blinkit", icons("Groceries")["Blinkit"])
        assertEquals("zepto", icons("Groceries")["Zepto"])
        assertEquals("bigbasket", icons("Groceries")["BigBasket"])
        assertEquals("dmart", icons("Groceries")["DMart"])
        assertEquals("uber", icons("Transport")["Uber"])
        assertEquals("ola", icons("Transport")["Ola"])
        assertEquals("rapido", icons("Transport")["Rapido"])
        assertEquals("fastag", icons("Transport")["FASTag recharge"])
        assertEquals("jiohotstar", icons("Subscriptions")["JioHotstar"])
        assertEquals("jiosaavn", icons("Subscriptions")["JioSaavn"])
    }

    @Test
    fun `every brand icon a suggestion uses has a logo or a vector mark to draw`() {
        for ((parent, subs) in CategorySuggestions.byParent) {
            for (sub in subs) {
                if (brandKey(sub.icon) == null) continue
                assertNotNull(brandLogo(sub.icon) ?: brandAsset(sub.icon), "$parent › ${sub.name}: ${sub.icon} is a brand with nothing to draw")
            }
        }
    }

    @Test
    fun `no family but Subscriptions runs past eighteen sub-categories`() {
        // A family is folded away on the Categories page, but a long one is still a long list to choose from. Subscriptions is
        // the exception: it is one entry per service.
        for ((parent, subs) in CategorySuggestions.byParent) {
            if (parent == "Subscriptions") continue
            assertTrue(subs.size <= 18, "$parent has ${subs.size} sub-categories")
        }
    }

    @Test
    fun `a phone with the older sub-categories is offered only the new ones, and never a name it already has`() {
        var id = 1L
        val at = LocalDateTime.of(2026, 11, 14, 9, 0)
        fun make(name: String, parent: Category? = null) = Category(
            id = id++, name = name, type = CategoryType.Expense, icon = "other", archived = false, createdAt = at, updatedAt = at, parentId = parent?.id,
        )
        val food = make("Food & dining")
        val older = listOf("Restaurants", "Coffee & tea", "Fast food", "Takeaway", "Delivery apps", "Street food", "Desserts", "Pizza", "Wine & beer", "Breakfast")
            .map { make(it, food) }
        // The user's own "snacks", at the top level, is theirs: the ready-made one is not added beside it.
        val mine = make("snacks")
        val offered = missingSuggestions(listOf(food) + older + mine).map { it.suggestion.name }
        assertEquals(listOf("Lunch", "Dinner", "Swiggy", "Zomato", "Tiffin & mess", "Office canteen", "Sweets & mithai"), offered)

        // Once they are added, there is nothing left to offer.
        val added = offered.map { make(it, food) }
        assertEquals(emptyList(), missingSuggestions(listOf(food) + older + mine + added))
    }
}
