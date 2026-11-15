package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MAX_TAG_NAME
import com.example.normpix.finance.domain.REIMBURSABLE_TAG
import com.example.normpix.finance.domain.SUGGESTED_TAGS
import com.example.normpix.finance.domain.TAX_DEDUCTIBLE_TAG
import com.example.normpix.finance.domain.Tag
import com.example.normpix.finance.domain.cleanTagName
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.ListSegment
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.components.readableOn
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Tags (2026-11-15, round-2 recommendation 8): free labels the user names and puts on expenses and income, any number on one. The
 * transaction form has a Tags field (its chips; a tap opens the picker), the picker finds, adds and ticks them and leads on to "Edit
 * tags" (rename, delete), and a transaction's details show its tags as chips that open its tag's transactions. A transfer has none.
 */

/** Test tag of the Tags field in the transaction form. */
const val TAGS_FIELD_TAG = "tags_field"

/** Test tag of the picker's list. */
const val TAG_PICKER_LIST_TAG = "tag_picker_list"

/** Test tag of the picker's find-or-add field. */
const val TAG_PICKER_SEARCH_TAG = "tag_picker_search"

/** Why each ready-made tag is offered, said under it in the picker. */
private val SUGGESTION_REASONS = mapOf(
    TAX_DEDUCTIBLE_TAG to "What you can claim against your tax",
    REIMBURSABLE_TAG to "What someone will pay you back",
)

/**
 * The transaction form's Tags field: a tonal picking field like Date's - the tag mark on its badge, "Tags" and the transaction's tags as
 * chips (or "Add tags") - that opens the [TagPickerSheet]. The whole field is one button to a screen reader, which reads the tags.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TagsField(tagIds: Set<Long>, onChange: (Set<Long>) -> Unit, modifier: Modifier = Modifier) {
    val finance = rememberFinanceState()
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val chosen = finance.tagsOf(tagIds)
    val open: () -> Unit = {
        scope.launch {
            val picked = overlays.sheet<Set<Long>>(expandable = true) { dismiss -> TagPickerSheet(selected = tagIds, onDone = dismiss) }
            if (picked != null && picked != tagIds) {
                haptics.selection()
                onChange(picked)
            }
        }
    }
    val said = if (chosen.isEmpty()) "none" else chosen.joinToString(", ") { it.name }
    Box(
        modifier
            .fillMaxWidth()
            .testTag(TAGS_FIELD_TAG)
            .heightIn(min = 64.dp)
            .clip(TonalFieldShape)
            .background(scheme.surfaceContainerHighest)
            .clickable(onClick = open)
            .clearAndSetSemantics {
                contentDescription = "Tags, $said"
                role = Role.Button
                onClick(label = "Choose tags") { open(); true }
            }
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.AutoMirrored.Outlined.Label)
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text("Tags", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                if (chosen.isEmpty()) {
                    Text("Add tags", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                } else {
                    FlowRow(
                        modifier = Modifier.padding(top = AppSpacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    ) {
                        for (tag in chosen) TagPill(tag.name)
                    }
                }
            }
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = false)
        }
    }
}

/** A tag's name on a small tonal pill: how a tag is shown where it is only read (the form's field). */
@Composable
fun TagPill(name: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = AppRadius.rSm, color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        )
    }
}

/**
 * A transaction's tags as chips, in its details: each opens that tag's transactions ([onOpen]). Nothing at all when it has none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagChips(tags: List<Tag>, onOpen: (Tag) -> Unit, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        for (tag in tags) {
            AssistChip(
                onClick = { onOpen(tag) },
                label = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                },
                modifier = Modifier.semantics { contentDescription = "Tag ${tag.name}, show its transactions" },
            )
        }
    }
}

/**
 * Choosing a transaction's tags, in a sheet: a field to find one or type a new one ("Add “Goa trip”"), the tags to tick, and - while
 * there are none yet - the two ready-made ones, offered and made only when picked. "Edit tags" opens [TagManagerSheet] over it. A tag
 * is made as soon as it is added here (it is the user's list, not this transaction's); the ticks are applied by the check button.
 * [onDone] gets the ticked ids, or null when the sheet is dismissed.
 */
@Composable
fun TagPickerSheet(selected: Set<Long>, onDone: (Set<Long>?) -> Unit) {
    val finance = rememberFinanceState()
    val controller = LocalFinance.current
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(selected) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    val tags = finance.tags
    // A tag deleted from Edit tags is no longer one that can be ticked.
    val known = tags.mapNotNullTo(HashSet()) { it.id }
    val ticked = picked.filterTo(LinkedHashSet()) { it in known }
    val typed = cleanTagName(query)
    val matches = if (typed.isEmpty()) tags else tags.filter { it.name.contains(typed, ignoreCase = true) }
    // The ready-made ones are offered only while the user has no tag at all: the first time the picker is opened, and until one is made.
    val suggestions = if (tags.isEmpty()) SUGGESTED_TAGS.filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) } else emptyList()
    val canAdd = typed.isNotEmpty() && tags.none { it.name.equals(typed, ignoreCase = true) } &&
        suggestions.none { it.equals(typed, ignoreCase = true) }

    fun add(name: String) {
        if (adding) return
        adding = true
        error = null
        scope.launch {
            try {
                val tag = controller.saveTag(Tag(name = name, createdAt = LocalDateTime.now()))
                haptics.selection()
                picked = picked + tag.id!!
                query = ""
            } catch (e: FinanceValidationException) {
                error = e.message
            } catch (e: FinanceConflictException) {
                error = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't add the tag. Your other data is unchanged."
            } finally {
                adding = false
            }
        }
    }

    fun toggle(tag: Tag) {
        val id = tag.id ?: return
        haptics.selection()
        picked = if (id in ticked) picked - id else picked + id
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.lg, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Tags",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    if (ticked.isEmpty()) "None selected" else "${ticked.size} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            FilledIconButton(
                onClick = { onDone(ticked) },
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer),
                modifier = Modifier.padding(start = AppSpacing.sm).size(48.dp),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = if (ticked.isEmpty()) "No tags" else "Apply (${ticked.size})")
            }
        }
        SearchField(
            value = query,
            onValueChange = {
                query = it
                error = null
            },
            placeholder = "Find or add a tag",
            modifier = Modifier.fillMaxWidth().padding(start = AppSpacing.xxl, end = AppSpacing.xxl).testTag(TAG_PICKER_SEARCH_TAG),
        )
        FieldError(error, Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl))
        Spacer(Modifier.height(AppSpacing.md))
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth().testTag(TAG_PICKER_LIST_TAG),
            contentPadding = PaddingValues(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            if (canAdd) {
                item(key = "add") {
                    ListSegment(color = scheme.surfaceContainerHigh) {
                        AddRow(
                            title = "Add “$typed”",
                            supporting = if (typed.length > MAX_TAG_NAME) "Up to $MAX_TAG_NAME characters" else "A new tag",
                            onClick = { add(typed) },
                        )
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                item(key = "suggested-header") { SectionSubheader("Ready-made") }
                itemsIndexed(suggestions, key = { _, name -> "suggest-$name" }) { index, name ->
                    ListSegment(isFirst = index == 0, isLast = index == suggestions.lastIndex, color = scheme.surfaceContainerHigh) {
                        AddRow(title = name, supporting = SUGGESTION_REASONS[name], onClick = { add(name) })
                    }
                }
            }
            if (matches.isNotEmpty()) {
                if (canAdd || suggestions.isNotEmpty()) item(key = "yours-header") { SectionSubheader("Your tags") }
                itemsIndexed(matches, key = { _, tag -> "tag-${tag.id}" }) { index, tag ->
                    ListSegment(isFirst = index == 0, isLast = index == matches.lastIndex, color = scheme.surfaceContainerHigh) {
                        TagCheckRow(tag = tag, checked = tag.id in ticked, onToggle = { toggle(tag) })
                    }
                }
            } else if (tags.isEmpty() && suggestions.isEmpty() && !canAdd) {
                item(key = "empty") {
                    Text(
                        "Type a name to make your first tag.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(AppSpacing.lg),
                    )
                }
            }
        }
        if (tags.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.sm, bottom = AppSpacing.md)) {
                FilledTonalButton(
                    onClick = { scope.launch { overlays.sheet<Unit>(expandable = true) { dismiss -> TagManagerSheet(onDone = dismiss) } } },
                    shape = AppRadius.rFull,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("Edit tags", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** A row that makes something new: a "+" in front, the name, why. */
@Composable
private fun AddRow(title: String, supporting: String?, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it) } },
    )
}

/** A tag that can be ticked: a checkbox and its name, the label in the accent once ticked (as the option sheets do). */
@Composable
private fun TagCheckRow(tag: Tag, checked: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val selectedLabel = readableOn(scheme.surfaceContainerHigh, scheme.primary, scheme.onSurface)
    ListItem(
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        headlineContent = {
            Text(
                tag.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                color = if (checked) selectedLabel else scheme.onSurface,
            )
        },
    )
}

/**
 * Edit tags: every tag with how many transactions it is on, each with Rename and Delete, and "New tag". Deleting asks first, saying how
 * many transactions it is on: the tag comes off them, and they stay as they are. Opened from the picker and from Settings > Finance.
 */
@Composable
fun TagManagerSheet(onDone: (Unit?) -> Unit) {
    val finance = rememberFinanceState()
    val controller = LocalFinance.current
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val usage by rememberFinanceQuery(Unit) { tagUsage() }
    val counts = (usage as? AsyncValue.Data)?.value.orEmpty()
    val tags = finance.tags

    fun rename(tag: Tag) {
        scope.launch { overlays.sheet<Tag> { dismiss -> TagNameSheet(existing = tag, onDone = dismiss) } }
    }

    fun create() {
        scope.launch { overlays.sheet<Tag> { dismiss -> TagNameSheet(existing = null, onDone = dismiss) } }
    }

    fun delete(tag: Tag) {
        val id = tag.id ?: return
        scope.launch {
            // Read again as it is asked, so the number said is the one that is true now.
            val on = try {
                controller.repository.tagUsage()[id] ?: 0
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                counts[id] ?: 0
            }
            val sure = overlays.confirm(
                title = "Delete “${tag.name}”?",
                message = deleteTagMessage(on),
                confirmLabel = "Delete",
                destructive = true,
                icon = Icons.Outlined.DeleteOutline,
            )
            if (!sure) return@launch
            try {
                controller.deleteTag(id)
                haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                overlays.alert("Couldn't delete the tag", "Your data is unchanged. Try again.")
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.xxl, end = AppSpacing.sm, top = AppSpacing.lg, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Edit tags",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    "Rename a tag, or delete it. A deleted tag comes off its transactions; they stay as they are.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDone(Unit) }) { Icon(Icons.Rounded.Close, contentDescription = "Done") }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
            contentPadding = PaddingValues(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            if (tags.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No tags yet. Add one here, or from a transaction's Tags field.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(AppSpacing.lg),
                    )
                }
            }
            itemsIndexed(tags, key = { _, tag -> "tag-${tag.id}" }) { index, tag ->
                ListSegment(isFirst = index == 0, isLast = index == tags.lastIndex, color = scheme.surfaceContainerHigh) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
                        headlineContent = { Text(tag.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(usageLabel(counts[tag.id] ?: 0)) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { rename(tag) }) { Icon(Icons.Outlined.Edit, contentDescription = "Rename ${tag.name}") }
                                IconButton(onClick = { delete(tag) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete ${tag.name}", tint = scheme.error)
                                }
                            }
                        },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(start = AppSpacing.xxl, end = AppSpacing.xxl, top = AppSpacing.sm, bottom = AppSpacing.md)) {
            FilledTonalButton(onClick = ::create, shape = AppRadius.rFull, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AppSpacing.sm))
                Text("New tag", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** "On 3 transactions", "On 1 transaction", "Not on any transaction". */
internal fun usageLabel(count: Int): String = when (count) {
    0 -> "Not on any transaction"
    1 -> "On 1 transaction"
    else -> "On $count transactions"
}

/** What deleting a tag on [count] transactions does, said before it is done. */
internal fun deleteTagMessage(count: Int): String = when (count) {
    0 -> "It isn't on any transaction."
    1 -> "It's on 1 transaction. The tag comes off it; the transaction stays as it is."
    else -> "It's on $count transactions. The tag comes off them; the transactions stay as they are."
}

/**
 * A tag's name, new or renamed, as a small form: the name (1 to [MAX_TAG_NAME] characters, new to the list whatever its case) and Save.
 * A rename changes it on every transaction that has it. [onDone] gets the saved tag, or null when closed without saving.
 */
@Composable
fun TagNameSheet(existing: Tag?, onDone: (Tag?) -> Unit) {
    val controller = LocalFinance.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DiscardChangesGuard(
        dirty = name != existing?.name.orEmpty(),
        saving = saving,
        isEditing = existing != null,
        keptMessage = "The tag keeps its name.",
    )

    fun save() {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                val saved = controller.saveTag((existing ?: Tag(name = name, createdAt = LocalDateTime.now())).copy(name = name))
                haptics.success()
                onDone(saved)
            } catch (e: FinanceValidationException) {
                error = e.message
            } catch (e: FinanceConflictException) {
                error = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Couldn't save. Your other data is unchanged."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = if (existing == null) "New tag" else "Rename tag",
        subtitle = if (existing == null) "A label for expenses and income, such as Goa trip." else "It changes on every transaction that has it.",
        saveLabel = if (existing == null) "Save tag" else "Save name",
        onSave = ::save,
        saving = saving,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(MAX_TAG_NAME + 10)
                error = null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("Name") },
            placeholder = { Text("e.g. Goa trip, Office") },
            isError = error != null,
            supportingText = { Text(error ?: "${cleanTagName(name).length}/$MAX_TAG_NAME") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
    }
}

/**
 * The filter sheet's Tags row: what is picked (or "Any tag"), clearing it, or picking from every tag in an option sheet (any of them:
 * a transaction with one of the ticked tags is shown).
 */
@Composable
fun TagFilterRow(selected: Set<Long>, onChange: (Set<Long>) -> Unit) {
    val finance = rememberFinanceState()
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val chosen = finance.tags.filter { it.id in selected }
    val summary = when {
        chosen.isEmpty() -> "Any tag"
        chosen.size <= 2 -> chosen.joinToString(", ") { it.name }
        else -> "${chosen.take(2).joinToString(", ") { it.name }} and ${chosen.size - 2} more"
    }
    ListItem(
        modifier = Modifier.clickable {
            scope.launch {
                val picked = overlays.sheet<Set<Long>> { dismiss ->
                    com.example.normpix.ui.components.OptionSheet(
                        title = "Tags",
                        subtitle = "Any of these",
                        options = finance.tags.map { tag ->
                            com.example.normpix.ui.components.OptionSheetItem(
                                key = tag.id!!,
                                label = tag.name,
                                leading = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            )
                        },
                        selected = selected,
                        multiple = true,
                        onConfirm = { dismiss(it) },
                    )
                }
                if (picked != null) onChange(picked)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null) },
        headlineContent = { Text("Tags") },
        supportingContent = { Text(summary) },
        trailingContent = if (chosen.isEmpty()) {
            { Icon(Icons.Rounded.ChevronRight, contentDescription = null) }
        } else {
            {
                IconButton(onClick = { onChange(emptySet()) }) { Icon(Icons.Rounded.Close, contentDescription = "Clear tags") }
            }
        },
    )
}
