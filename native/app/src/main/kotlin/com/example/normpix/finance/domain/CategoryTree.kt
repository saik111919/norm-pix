package com.example.normpix.finance.domain

/*
 * Categories are two levels deep at most: a top-level category, and under it its sub-categories. A transaction may be
 * recorded against either; where spending is added up by category, a sub-category's counts toward its parent as well.
 */

/** Child id to parent id, for the sub-categories among these. */
fun List<Category>.parentMap(): Map<Long, Long> = buildMap {
    for (c in this@parentMap) {
        val id = c.id ?: continue
        val parent = c.parentId ?: continue
        put(id, parent)
    }
}

/** [category]'s sub-categories, in this list's order. */
fun List<Category>.subCategoriesOf(category: Category): List<Category> =
    if (category.id == null) emptyList() else filter { it.parentId == category.id }

/** The parent of [category]; null for a top-level category (or one whose parent is not in this list). */
fun List<Category>.parentOf(category: Category): Category? = category.parentId?.let { id -> firstOrNull { it.id == id } }

/**
 * This list in the order a screen shows it: each top-level category, then its own sub-categories under it. A sub-category
 * whose parent is not in the list (it was filtered out, say) stands as a top-level one rather than vanishing.
 */
fun List<Category>.inTreeOrder(): List<Category> {
    val ids = mapNotNullTo(mutableSetOf()) { it.id }
    val children = groupBy { it.parentId }
    return filter { it.parentId == null || it.parentId !in ids }.flatMap { top -> listOf(top) + top.id?.let { children[it] }.orEmpty() }
}

/** "Food & dining › Groceries" for a sub-category; just the name for a top-level one. */
fun List<Category>.pathName(category: Category): String {
    val parent = parentOf(category) ?: return category.name
    return "${parent.name} › ${category.name}"
}

/** A top-level category with the sub-categories under it. */
class CategoryFamily(val category: Category, val subs: List<Category>)

/**
 * This list grouped into families, in this list's order: each top-level category with its own sub-categories. A
 * sub-category whose parent is not in the list stands as a family of its own rather than vanishing.
 */
fun List<Category>.families(): List<CategoryFamily> {
    val ids = mapNotNullTo(mutableSetOf()) { it.id }
    val children = groupBy { it.parentId }
    return filter { it.parentId == null || it.parentId !in ids }
        .map { top -> CategoryFamily(top, top.id?.let { children[it] }.orEmpty()) }
}

/**
 * The categories of this list that answer [query], in tree order. Every word typed has to be in the category's path
 * ("Food & dining › Pizza"), in any order and whatever the case - so "pizza", "food piz" and "dining pizza" all find it - and
 * a matching parent brings its sub-categories with it. A blank query is everything. [pathOf] names a category by where it
 * sits; pass the full list's [pathName] so a sub-category is found by its parent's name even when the parent is not shown.
 */
fun List<Category>.matching(query: String, pathOf: (Category) -> String = { it.name }): List<Category> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return inTreeOrder()
    return inTreeOrder().filter { category ->
        val path = pathOf(category).lowercase()
        words.all { it in path }
    }
}

/**
 * [totals] with every sub-category's folded into its parent's - the parent's own, and all of its sub-categories', as one
 * line - largest first. The number of transactions adds up the same way. Categories with no parent stay as they are.
 */
fun rollUpToParents(totals: List<CategoryTotal>, parents: Map<Long, Long>): List<CategoryTotal> =
    totals
        .groupBy { parents[it.categoryId] ?: it.categoryId }
        .map { (id, group) -> CategoryTotal(categoryId = id, amount = group.sumOf { it.amount }, count = group.sumOf { it.count }) }
        .sortedByDescending { it.amount }

/**
 * What each category has spent when a budget on a parent covers its sub-categories: every category keeps its own amount, and
 * each parent also gets its sub-categories' - so a budget on "Food" sees what was spent on "Groceries", and a budget on
 * "Groceries" sees only that.
 */
fun foldIntoParents(spent: Map<Long, Long>, parents: Map<Long, Long>): Map<Long, Long> {
    val result = HashMap<Long, Long>(spent)
    for ((id, amount) in spent) {
        val parent = parents[id] ?: continue
        result[parent] = (result[parent] ?: 0L) + amount
    }
    return result
}
