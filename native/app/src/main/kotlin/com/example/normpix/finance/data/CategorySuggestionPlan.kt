package com.example.normpix.finance.data

import com.example.normpix.finance.domain.Category

/** One ready-made sub-category that [missingSuggestions] found a home for. */
data class PlannedSubCategory(val parent: Category, val suggestion: SuggestedSubCategory)

/**
 * Which of the [CategorySuggestions] [categories] doesn't have yet, each with the top-level category it belongs under.
 *
 * A suggestion goes under the visible top-level category of the same name (a parent the user renamed or archived simply gets
 * none), and is skipped when any category of that kind - top-level or not - already goes by its name. That is what makes
 * adding them safe to repeat, and keeps it from doubling up what the user made themselves.
 */
fun missingSuggestions(categories: List<Category>): List<PlannedSubCategory> {
    val taken = categories.mapTo(mutableSetOf()) { it.type to it.name.trim().lowercase() }
    val planned = mutableListOf<PlannedSubCategory>()
    for ((parentName, suggestions) in CategorySuggestions.byParent) {
        val parent = categories.firstOrNull {
            it.parentId == null && !it.archived && it.name.trim().equals(parentName, ignoreCase = true)
        } ?: continue
        for (suggestion in suggestions) {
            if (taken.add(parent.type to suggestion.name.trim().lowercase())) planned += PlannedSubCategory(parent, suggestion)
        }
    }
    return planned
}
