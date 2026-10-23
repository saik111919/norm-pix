package com.example.normpix.finance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.normpix.finance.data.missingSuggestions
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.pathName
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

private val categoryTypeOptions = listOf(
    ChoiceOption(CategoryType.Expense, "Expense", TransactionType.Expense.icon),
    ChoiceOption(CategoryType.Income, "Income", TransactionType.Income.icon),
)

/** A top-level category with the sub-categories under it (none that are archived). */
private class Family(val category: Category, val subs: List<Category>)

/**
 * Settings > Finance > Categories: add, rename, recolor, archive and delete expense and income categories. Each top-level
 * category is one card; the ones with sub-categories fold them away (tap to open or close, or all at once), so a long,
 * ready-made tree stays as quick to scan as a short list.
 */
@Composable
fun CategoriesPage() {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val navigator = LocalNavigator.current
    var type by rememberSaveable { mutableStateOf(CategoryType.Expense) }
    // Which families are open; kept across rotation and while a sheet is up.
    var expanded by rememberSaveable(stateSaver = listSaver<Set<Long>, Long>(save = { it.toList() }, restore = { it.toSet() })) {
        mutableStateOf(emptySet<Long>())
    }
    val all = finance.categoriesOf(type, includeArchived = true)
    val ids = all.mapNotNullTo(mutableSetOf()) { it.id }
    val active = all.filter { !it.archived }
    // A sub-category whose parent is gone stands as a top-level one rather than vanishing.
    val families = active
        .filter { it.parentId == null || it.parentId !in ids }
        .map { top -> Family(top, active.filter { it.parentId == top.id }) }
    val archived = all.filter { it.archived }
    val withSubs = families.filter { it.subs.isNotEmpty() }
    val subCount = withSubs.sumOf { it.subs.size }
    val allOpen = withSubs.isNotEmpty() && withSubs.all { it.category.id in expanded }
    val suggestions = missingSuggestions(finance.categories).size

    AppSubPage(title = "Categories", onBack = navigator::back, maxWidth = AppLayout.maxListWidth) {
        item {
            Column(Modifier.padding(AppSpacing.page)) {
                // The same Expense | Income control (and primary tonal pill) as the transaction editor.
                ConnectedChoiceRow(
                    options = categoryTypeOptions,
                    selected = type,
                    onSelect = { type = it },
                )
                if (withSubs.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = AppSpacing.lg, top = AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${families.size} categories · $subCount sub-categories",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                expanded = if (allOpen) emptySet() else withSubs.mapNotNullTo(mutableSetOf()) { it.category.id }
                            },
                        ) { Text(if (allOpen) "Collapse all" else "Expand all") }
                    }
                }
                ListSection(
                    title = "In use",
                    footer = "Sub-categories sit under a category and share its color; what is spent in them counts toward it. " +
                        "Categories with transactions can be archived instead of deleted. Archived ones stay on past transactions.",
                ) {
                    for (family in families) {
                        item {
                            CategoryFamily(
                                family = family,
                                expanded = family.category.id in expanded,
                                onToggle = {
                                    val id = family.category.id ?: return@CategoryFamily
                                    expanded = if (id in expanded) expanded - id else expanded + id
                                },
                                onManage = actions::manageCategory,
                                onAddSub = {
                                    family.category.id?.let { expanded = expanded + it }
                                    actions.addSubCategory(family.category)
                                },
                            )
                        }
                    }
                    if (suggestions > 0) {
                        item {
                            ListRow(
                                headline = "Add suggested sub-categories",
                                supporting = "$suggestions ready-made ones - Pizza under Food & dining, Fuel under Transport, " +
                                    "and more. Only what's missing is added.",
                                leading = { LeadingIcon(Icons.Outlined.AutoAwesome) },
                                onClick = actions::addSuggestedSubCategories,
                            )
                        }
                    }
                    item {
                        ListRow(
                            headline = "Add category",
                            leading = { LeadingIcon(Icons.Rounded.Add) },
                            onClick = { actions.addCategory(type) },
                        )
                    }
                }
                if (archived.isNotEmpty()) {
                    ListSection(title = "Archived") {
                        for (c in archived) {
                            item {
                                ListRow(
                                    // Where it sits, since it is shown apart from its parent: "Food > Pizza".
                                    headline = finance.categories.pathName(c),
                                    leading = { CategoryAvatar(c) },
                                    trailing = { Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${c.name}") },
                                    onClick = { actions.manageCategory(c) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One category and, under it, its sub-categories. A category with sub-categories opens and closes on a tap (the chevron turns);
 * its options are behind the "more" button. One without them opens its options on a tap, as every row used to.
 */
@Composable
private fun CategoryFamily(
    family: Family,
    expanded: Boolean,
    onToggle: () -> Unit,
    onManage: (Category) -> Unit,
    onAddSub: () -> Unit,
) {
    val category = family.category
    val subs = family.subs
    val expandable = subs.isNotEmpty()
    val turn by animateFloatAsState(if (expanded) 180f else 0f, AppMotion.spatial(), label = "family_chevron")
    Column {
        ListItem(
            modifier = Modifier
                .clickable(
                    onClickLabel = when {
                        !expandable -> "Options"
                        expanded -> "Collapse ${category.name}"
                        else -> "Expand ${category.name}"
                    },
                    onClick = if (expandable) onToggle else ({ onManage(category) }),
                )
                .semantics { if (expandable) stateDescription = if (expanded) "Expanded" else "Collapsed" },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { CategoryAvatar(category) },
            headlineContent = { Text(category.name) },
            supportingContent = if (expandable) {
                { Text("${subs.size} sub-categor${if (subs.size == 1) "y" else "ies"}") }
            } else {
                null
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expandable) {
                        Icon(
                            Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(turn),
                        )
                    }
                    IconButton(onClick = { onManage(category) }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${category.name}")
                    }
                }
            },
        )
        AnimatedVisibility(
            visible = expandable && expanded,
            enter = expandVertically(AppMotion.spatial()) + fadeIn(tween(AppMotion.duration(AppMotion.SHORT_MS))),
            exit = shrinkVertically(AppMotion.spatial()) + fadeOut(tween(AppMotion.duration(AppMotion.SHORT_MS / 2))),
        ) {
            Column(
                Modifier.padding(start = AppSpacing.sm, end = AppSpacing.sm, bottom = AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                subs.forEachIndexed { index, sub ->
                    Surface(
                        shape = nestedShape(isFirst = index == 0, isLast = false),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        ListRow(
                            headline = sub.name,
                            leading = { CategoryAvatar(sub, size = 32.dp) },
                            trailing = { Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${sub.name}") },
                            onClick = { onManage(sub) },
                        )
                    }
                }
                Surface(
                    shape = nestedShape(isFirst = false, isLast = true),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    ListRow(
                        headline = "Add sub-category",
                        headlineColor = MaterialTheme.colorScheme.primary,
                        leading = {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        onClick = onAddSub,
                    )
                }
            }
        }
    }
}

/** A row nested inside a family's card: big corners at the ends of the stack, small where rows meet. */
private fun nestedShape(isFirst: Boolean, isLast: Boolean) = RoundedCornerShape(
    topStart = if (isFirst) AppRadius.lg else AppRadius.xs,
    topEnd = if (isFirst) AppRadius.lg else AppRadius.xs,
    bottomStart = if (isLast) AppRadius.lg else AppRadius.xs,
    bottomEnd = if (isLast) AppRadius.lg else AppRadius.xs,
)
