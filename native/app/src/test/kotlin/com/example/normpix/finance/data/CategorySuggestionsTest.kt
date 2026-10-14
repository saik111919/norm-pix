package com.example.normpix.finance.data

import com.example.normpix.finance.domain.emojiOf
import com.example.normpix.finance.domain.isSingleEmoji
import com.example.normpix.finance.ui.categoryIcons
import kotlin.test.assertEquals
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
}
