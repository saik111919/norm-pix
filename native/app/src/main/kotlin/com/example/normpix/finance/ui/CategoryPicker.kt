package com.example.normpix.finance.ui

import com.example.normpix.ui.theme.AppMotion
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
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
        // The same avatar as in the lists, in the shape chosen in Settings > Appearance (none: the bare icon).
        leading = { if (current != null) CategoryAvatar(current) else NeutralAvatar(Icons.Outlined.Category) },
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
 * picks it, its sub-categories as chips under it, closed until the row's chevron (or "Expand all") opens them - and "New category". Typing in the search box swaps the families for the
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
    // Which families show their sub-categories. They start closed, so the sheet is a short list of families to look down - except
    // the one a picked sub-category sits in, which starts open, so the choice is seen where it is.
    var open by remember(families) {
        mutableStateOf(families.filter { family -> family.subs.any { it.id in selected } }.map { it.key }.toSet())
    }
    val expandable = families.filter { it.subs.isNotEmpty() }
    val allOpen = expandable.isNotEmpty() && expandable.all { it.key in open }

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
                        }
                    }
                }
                item(key = "all-header") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionSubheader("All categories", Modifier.weight(1f))
                        if (expandable.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    haptics.selection()
                                    open = if (allOpen) emptySet() else expandable.map { it.key }.toSet()
                                },
                            ) { Text(if (allOpen) "Collapse all" else "Expand all") }
                        }
                    }
                }
                items(families, key = { it.category.id ?: it.category.name }) { family ->
                    FamilyCard(
                        family = family,
                        picked = picked,
                        multiple = multiple,
                        expanded = family.key in open,
                        onToggle = {
                            haptics.selection()
                            open = if (family.key in open) open - family.key else open + family.key
                        },
                        onChoose = ::choose,
                    )
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

/** A family's own key: what [CategoryPickerSheet] remembers it open by. */
private val CategoryFamily.key: String get() = (category.id ?: category.name).toString()

/**
 * One family in one rounded card: its category as a row that picks it, and - closed or open, by the chevron at the row's
 * end - its sub-categories as chips under it. Closed, the row says what is inside (the first few names and how many more)
 * or, if one is picked, which: so nothing chosen is out of sight. Open, it says how many there are.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamilyCard(
    family: CategoryFamily,
    picked: Set<Long>,
    multiple: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChoose: (Category) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val pickedSubs = family.subs.filter { it.id in picked }
    val count = family.subs.size
    val supporting = when {
        count == 0 -> null
        expanded -> "$count sub-categor${if (count == 1) "y" else "ies"}"
        pickedSubs.size == 1 -> "${pickedSubs.single().name} selected"
        pickedSubs.isNotEmpty() -> "${pickedSubs.size} selected"
        else -> family.subs.take(PREVIEW_NAMES).joinToString(", ") { it.name } + if (count > PREVIEW_NAMES) " +${count - PREVIEW_NAMES}" else ""
    }
    Surface(shape = AppRadius.rLg, color = scheme.surfaceContainerHigh) {
        Column {
            PickerRow(
                category = family.category,
                name = family.category.name,
                supporting = supporting,
                supportingEmphasis = !expanded && pickedSubs.isNotEmpty(),
                selected = family.category.id in picked,
                multiple = multiple,
                onClick = { onChoose(family.category) },
                trailingExtra = if (count > 0) ({ ExpandToggle(expanded, family.category.name, onToggle) }) else null,
            )
            AnimatedVisibility(
                visible = expanded && count > 0,
                enter = expandVertically(tween(AppMotion.duration(AppMotion.MEDIUM_MS), easing = AppMotion.standard)) +
                    fadeIn(tween(AppMotion.duration(AppMotion.MEDIUM_MS), easing = AppMotion.standard)),
                exit = shrinkVertically(tween(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard)) +
                    fadeOut(tween(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard)),
            ) {
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

/** How many of a closed family's sub-categories are named on its row. */
private const val PREVIEW_NAMES = 2

/** The chevron that opens and closes a family: a touch target of its own at the row's end, turning over as it opens. */
@Composable
private fun ExpandToggle(expanded: Boolean, name: String, onClick: () -> Unit) {
    val angle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard),
        label = "family_chevron",
    )
    IconButton(onClick = onClick) {
        Icon(
            Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Hide sub-categories of $name" else "Show sub-categories of $name",
            modifier = Modifier.rotate(angle),
        )
    }
}

/**
 * A category as a full-width row: its avatar and name, and whether it is picked (a tick, or a checkbox when several can be).
 * [trailingExtra] goes after that, at the row's end; [supportingEmphasis] puts the supporting line in the accent colour.
 */
@Composable
private fun PickerRow(
    category: Category,
    name: String,
    selected: Boolean,
    multiple: Boolean,
    onClick: () -> Unit,
    supporting: String? = null,
    supportingEmphasis: Boolean = false,
    trailingExtra: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val mark: (@Composable () -> Unit)? = when {
        multiple -> ({ Checkbox(checked = selected, onCheckedChange = null) })
        selected -> ({ Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.primary) })
        else -> null
    }
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
        supportingContent = supporting?.let {
            {
                Text(
                    it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (supportingEmphasis) scheme.primary else Color.Unspecified,
                )
            }
        },
        trailingContent = if (mark == null && trailingExtra == null) {
            null
        } else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    mark?.invoke()
                    trailingExtra?.invoke()
                }
            }
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
