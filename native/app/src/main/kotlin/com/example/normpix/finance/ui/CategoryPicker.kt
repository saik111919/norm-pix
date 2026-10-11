package com.example.normpix.finance.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryFamily
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.families
import com.example.normpix.finance.domain.matching
import com.example.normpix.finance.domain.pathName
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.launch

/*
 * Choosing a category. The ready-made tree has a hundred-odd sub-categories, which no dropdown can show at once: so the
 * field opens a sheet where the categories sit in their families (a category, and its sub-categories as chips under it),
 * with the ones used most up top and a search that finds a sub-category by its own name or its parent's.
 */

/**
 * A tonal field that shows the current choice and opens a picker when tapped: the look of [ChoiceDropdownField], for a choice
 * too long for a menu. The whole field is the tap target, and it reads as a button to a screen reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoicePickerField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    errorText: String? = null,
    /** When given (and there is a [value]), a clear button replaces the arrow, so the choice can be taken back. */
    onClear: (() -> Unit)? = null,
) {
    // A read-only text field takes a tap as a press and a release: the release is the "open".
    val interactions = remember { MutableInteractionSource() }
    val latestClick by rememberUpdatedState(onClick)
    LaunchedEffect(interactions) {
        interactions.interactions.collect { if (it is PressInteraction.Release) latestClick() }
    }
    TextField(
        value = value.orEmpty(),
        onValueChange = {},
        readOnly = true,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = leading,
        trailingIcon = {
            if (onClear != null && value != null) {
                IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, contentDescription = "Remove ${label.lowercase()}") }
            } else {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
            }
        },
        isError = errorText != null,
        supportingText = errorText?.let { { Text(it) } },
        colors = tonalFieldColors(),
        shape = TonalFieldShape,
        interactionSource = interactions,
        modifier = modifier
            .fillMaxWidth()
            // The picker is a sheet, not a keyboard: the field never takes focus (and with it, the cursor and the keyboard).
            .focusProperties { canFocus = false }
            .semantics {
                role = Role.Button
                onClick(label = "Choose ${label.lowercase()}") {
                    latestClick()
                    true
                }
            },
    )
}

/**
 * The category choice: one field (the category's icon, and its name as "Food & dining › Pizza") that opens the
 * [CategoryPickerSheet]. [categories] are the ones that can be chosen (of the transaction's type), plus the current one if it
 * has since been archived; "New category" is offered at the end of the picker when [onCreate] is given.
 */
@Composable
fun TransactionCategoryField(
    categories: List<Category>,
    selected: Long?,
    onSelect: (Long) -> Unit,
    onCreate: (() -> Unit)?,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    val finance = rememberFinanceState()
    val overlays = LocalOverlays.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val current = categories.firstOrNull { it.id == selected }
    ChoicePickerField(
        label = "Category",
        value = current?.let { finance.categories.pathName(it) },
        placeholder = "Choose a category",
        modifier = modifier,
        errorText = errorText,
        leading = {
            if (current != null) {
                val tone = paletteColor(current.color)
                IconBadge(
                    icon = null,
                    container = tone.container,
                    content = tone.onContainer,
                    glyph = { CategoryGlyph(current.icon, tone.onContainer, 22.dp) },
                )
            } else {
                IconBadge(Icons.Outlined.Category)
            }
        },
        onClick = {
            scope.launch {
                val picked = overlays.sheet<Set<Long>> { dismiss ->
                    CategoryPickerSheet(
                        categories = categories,
                        selected = setOfNotNull(selected),
                        multiple = false,
                        onDone = dismiss,
                        // Leaves the picker first, so the new category's form is the only thing up when it opens.
                        onCreate = onCreate?.let { create ->
                            {
                                dismiss(null)
                                create()
                            }
                        },
                    )
                }
                picked?.firstOrNull()?.let {
                    haptics.selection()
                    onSelect(it)
                }
            }
        },
    )
}

/**
 * The sheet: a search box, the categories used most ("Frequently used"), then every family - the category as a row that
 * picks it, its sub-categories as chips under it - and "New category". Typing in the search box swaps the families for the
 * matches, each named by where it sits. With [multiple] the rows toggle and a button applies the set; otherwise a tap picks
 * and closes. [onDone] gets the picked ids, or null when the sheet is dismissed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selected: Set<Long>,
    multiple: Boolean,
    onDone: (Set<Long>?) -> Unit,
    onCreate: (() -> Unit)? = null,
) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(selected) }

    val type = categories.firstOrNull()?.type ?: CategoryType.Expense
    val frequentResult by rememberFinanceQuery(type) {
        frequentCategoryIds(if (type == CategoryType.Income) TransactionType.Income else TransactionType.Expense)
    }
    val frequent = (frequentResult as? AsyncValue.Data)?.value.orEmpty().mapNotNull { id -> categories.firstOrNull { it.id == id } }
    val searching = query.isNotBlank()
    val matches = if (searching) categories.matching(query) { finance.categories.pathName(it) } else emptyList()
    val families = remember(categories) { categories.families() }

    fun choose(category: Category) {
        val id = category.id ?: return
        haptics.selection()
        if (multiple) {
            picked = if (id in picked) picked - id else picked + id
        } else {
            onDone(setOf(id))
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.md)) {
            SheetHeader(
                title = if (multiple) "Select categories" else "Select a category",
                subtitle = if (multiple && picked.isNotEmpty()) "${picked.size} selected" else null,
                trailing = if (multiple && picked.isNotEmpty()) {
                    { TextButton(onClick = { picked = emptySet() }) { Text("Clear") } }
                } else {
                    null
                },
            )
            Spacer(Modifier.height(AppSpacing.md))
            SearchField(value = query, onValueChange = { query = it }, placeholder = "Search categories")
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).testTag(CATEGORY_PICKER_TAG),
            contentPadding = PaddingValues(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            if (searching) {
                if (matches.isEmpty()) {
                    item(key = "none") {
                        Text(
                            "No category matches “${query.trim()}”.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(AppSpacing.lg),
                        )
                    }
                } else {
                    item(key = "matches") {
                        Surface(shape = AppRadius.rLg, color = scheme.surfaceContainerHigh) {
                            Column {
                                for (category in matches) {
                                    PickerRow(
                                        category = category,
                                        name = finance.categories.pathName(category),
                                        selected = category.id in picked,
                                        multiple = multiple,
                                        onClick = { choose(category) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Always the first item, empty until the history is read: when it arrives it grows in place. Inserted above what is
                // on screen instead, a lazy list keeps the row the user sees where it is - and scrolls right past the new section.
                item(key = "frequent") {
                    Column(Modifier.animateContentSize()) {
                        if (frequent.isNotEmpty()) {
                            SectionSubheader("Frequently used")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                modifier = Modifier.padding(start = AppSpacing.xs, top = AppSpacing.xs, bottom = AppSpacing.sm),
                            ) {
                                for (category in frequent) {
                                    CategoryChip(
                                        category = category,
                                        label = finance.categories.pathName(category),
                                        selected = category.id in picked,
                                        onClick = { choose(category) },
                                    )
                                }
                            }
                            SectionSubheader("All categories")
                        }
                    }
                }
                items(families, key = { it.category.id ?: it.category.name }) { family ->
                    FamilyCard(family, picked, multiple, onChoose = ::choose)
                }
            }
            if (onCreate != null) {
                item(key = "create") {
                    Surface(shape = AppRadius.rLg, color = scheme.surfaceContainerHigh) {
                        ListItem(
                            modifier = Modifier.clickable(onClick = onCreate),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.primary) },
                            headlineContent = { Text("New category", color = scheme.primary) },
                        )
                    }
                }
            }
        }
        if (multiple) {
            Box(Modifier.fillMaxWidth().padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.xs, bottom = AppSpacing.md)) {
                Button(onClick = { onDone(picked) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        if (picked.isEmpty()) "Any category" else "Apply (${picked.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/** One family: its category as a row that picks it, then its sub-categories as chips, all in one rounded card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyCard(family: CategoryFamily, picked: Set<Long>, multiple: Boolean, onChoose: (Category) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = AppRadius.rLg, color = scheme.surfaceContainerHigh) {
        Column {
            PickerRow(
                category = family.category,
                name = family.category.name,
                supporting = family.subs.size.takeIf { it > 0 }?.let { "$it sub-categor${if (it == 1) "y" else "ies"}" },
                selected = family.category.id in picked,
                multiple = multiple,
                onClick = { onChoose(family.category) },
            )
            if (family.subs.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    for (sub in family.subs) {
                        CategoryChip(category = sub, label = sub.name, selected = sub.id in picked, onClick = { onChoose(sub) })
                    }
                }
            }
        }
    }
}

/** A category as a full-width row: its avatar and name, and whether it is picked (a tick, or a checkbox when several can be). */
@Composable
private fun PickerRow(
    category: Category,
    name: String,
    selected: Boolean,
    multiple: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    ListItem(
        modifier = Modifier
            .background(if (selected && !multiple) scheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                role = if (multiple) Role.Checkbox else Role.RadioButton
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { CategoryAvatar(category, size = 40.dp) },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = when {
            multiple -> ({ Checkbox(checked = selected, onCheckedChange = null) })
            selected -> ({ Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary) })
            else -> null
        },
    )
}

/** A category as a chip with its icon: the way a sub-category is offered under its parent. */
@Composable
private fun CategoryChip(category: Category, label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { CategoryGlyph(category.icon, LocalContentColor.current, 18.dp) },
        modifier = Modifier.semantics { role = Role.RadioButton },
    )
}

/**
 * The filter sheet's category row: what is picked (or "Any"), with a way to clear it, opening the [CategoryPickerSheet] with
 * several choices allowed. [selected] and what [onChange] gets are the ids of this [type] only.
 */
@Composable
fun CategoryFilterRow(title: String, type: CategoryType, selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    val finance = rememberFinanceState()
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    // Archived ones too: past transactions can still be filtered by them.
    val universe = finance.categoriesOf(type, includeArchived = true)
    val chosen = universe.filter { it.id in selected }
    val summary = when {
        chosen.isEmpty() -> "Any"
        chosen.size <= 2 -> chosen.joinToString(", ") { finance.categories.pathName(it) }
        else -> "${chosen.take(2).joinToString(", ") { it.name }} and ${chosen.size - 2} more"
    }
    ListItem(
        modifier = Modifier.clickable {
            scope.launch {
                val result = overlays.sheet<Set<Long>> { dismiss ->
                    CategoryPickerSheet(categories = universe, selected = selected, multiple = true, onDone = dismiss)
                }
                if (result != null) onChange(result)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(Icons.Outlined.Category, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = if (chosen.isEmpty()) {
            { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
        } else {
            {
                IconButton(onClick = { onChange(emptySet()) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear ${title.lowercase()}")
                }
            }
        },
    )
}

/** Test tag of the picker's scrolling list of families. */
const val CATEGORY_PICKER_TAG = "category_picker"
