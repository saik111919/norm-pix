package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MAX_SPLIT_PARTS
import com.example.normpix.finance.domain.MIN_SPLIT_PARTS
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.SplitPart
import com.example.normpix.finance.domain.largestPartCategory
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.finance.domain.splitTotal
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Splitting one payment into categories (2026-11-15, database version 17; the rules are in `finance/domain/Splits.kt`, the saving in
 * `FinanceRepository.saveSplit`). The transaction form's "Split" field opens the split editor: the total at the top, what the parts
 * share in one line, then the parts - each a category, an amount and an optional note - with a running line above Save that says how
 * far the parts are from the total. Save is only there once they add up and every part has a category and an amount.
 */

/** Test tag of the form's Split field. */
const val SPLIT_FIELD_TAG = "split_field"

/** Test tag of the running line above Save ("₹120 left to share"). */
const val SPLIT_STATUS_TAG = "split_status"

/** Test tag of "Add a part". */
const val SPLIT_ADD_PART_TAG = "split_add_part"

/** Test tag of part [index]'s category field (from 0). */
fun splitCategoryTag(index: Int) = "split_category_$index"

/** Test tag of part [index]'s amount field (from 0). */
fun splitAmountTag(index: Int) = "split_amount_$index"

/** Test tag of part [index]'s note field (from 0). */
fun splitNoteTag(index: Int) = "split_note_$index"

/**
 * The transaction form's Split field: a tonal picking field like Date's that opens the [SplitEditorSheet]. [parts] is how many parts the
 * payment has already, or null for one that is not split yet.
 */
@Composable
fun SplitField(parts: Int?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TonalPickerField(
        label = "Split",
        value = if (parts == null) "Share between categories" else "$parts parts · Edit split",
        icon = Icons.Outlined.ContentCut,
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(SPLIT_FIELD_TAG),
    )
}

/** One part as the form keeps it while it is edited (its amount and note as typed). */
@Stable
private class PartDraft(val key: Int, val id: Long?, category: Long?, amount: String, note: String) {
    var categoryId by mutableStateOf(category)
    var amount by mutableStateOf(amount)
    var note by mutableStateOf(note)
}

/**
 * How the parts stand against the total, as the running line says it: [left] is the total less what the parts add up to (above zero:
 * still to share; below: over), [complete] whether every part has a category and an amount.
 */
internal fun splitStanding(total: Long?, left: Long, complete: Boolean, money: MoneyFormatter): String = when {
    total == null -> "Enter the total"
    left > 0 -> "${money.format(left, trimZeroFraction = true)} left to share"
    left < 0 -> "${money.format(-left, trimZeroFraction = true)} over the total"
    complete -> "Shared exactly"
    else -> "Give every part a category and an amount"
}

/**
 * The split editor, as a bottom sheet over the transaction form (or over the details of a split payment). [payment] says what the
 * parts share - type, account, date and time, payee, who it was for, tags, picture - and, as its amount, the total to start from;
 * [initialParts] are the parts to start with. [group] is the split being edited, or null for a new one. [picture] is what saving does to
 * the picture every part names (a new one chosen in the form, or taking it off). Calls [onDone] with true once saved, or null when
 * closed without saving.
 */
@Composable
fun SplitEditorSheet(
    payment: FinanceTransaction,
    group: String?,
    initialParts: List<SplitPart>,
    onDone: (Boolean?) -> Unit,
    picture: PictureEdit = PictureEdit.Unchanged,
) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val decimals = finance.currency.decimals
    val money = finance.money
    val type = payment.type
    val role = type.role()
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)

    var totalText by remember { mutableStateOf(payment.amount.takeIf { it > 0 }?.let { minorToInputText(it, decimals) }.orEmpty()) }
    var nextKey by remember { mutableIntStateOf(initialParts.size) }
    val parts = remember {
        mutableStateListOf<PartDraft>().apply {
            initialParts.forEachIndexed { index, part ->
                add(PartDraft(index, part.id, part.categoryId, part.amount.takeIf { it > 0 }?.let { minorToInputText(it, decimals) }.orEmpty(), part.note.orEmpty()))
            }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    fun snapshot() = totalText + "|" + parts.joinToString("|") { "${it.categoryId},${it.amount},${it.note}" }
    val initialSnapshot = remember { snapshot() }
    DiscardChangesGuard(
        dirty = snapshot() != initialSnapshot,
        saving = saving,
        isEditing = group != null,
        keptMessage = "The payment stays as it was.",
    )

    val total = parseMinorUnits(totalText, decimals)?.takeIf { it > 0 }
    // An amount left empty is nothing yet; one that can't be read is null, which the part's field reports and Save refuses.
    val amounts = parts.map { if (it.amount.isBlank()) 0L else parseMinorUnits(it.amount, decimals) }
    val left = (total ?: 0L) - amounts.sumOf { it ?: 0L }
    val complete = parts.indices.all { parts[it].categoryId != null && (amounts[it] ?: 0L) > 0L }
    val valid = total != null && left == 0L && complete && parts.size in MIN_SPLIT_PARTS..MAX_SPLIT_PARTS

    fun save() {
        if (saving || !valid || total == null) return
        saving = true
        formError = null
        val toSave = payment.copy(amount = total)
        val list = parts.mapIndexed { index, part -> SplitPart(part.categoryId, amounts[index] ?: 0L, part.note, part.id) }
        scope.launch {
            try {
                controller.saveSplit(toSave, list, group, picture)
                haptics.success()
                snackbars.show(
                    if (group != null) "Split updated" else "${money.format(total, trimZeroFraction = true)} split into ${list.size} parts",
                )
                onDone(true)
            } catch (e: FinanceValidationException) {
                formError = e.message
            } catch (e: FinanceConflictException) {
                formError = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                formError = "Couldn't save. Your other data is unchanged."
            } finally {
                saving = false
            }
        }
    }

    val hasPicture = when (picture) {
        is PictureEdit.Attach -> true
        PictureEdit.Remove -> false
        PictureEdit.Unchanged -> payment.picture != null
    }

    EditorSheet(
        title = if (group != null) "Edit split" else "Split payment",
        subtitle = "Share one ${type.label.lowercase()} between categories",
        saveLabel = "Save split",
        onSave = ::save,
        saving = saving,
        saveEnabled = valid,
        saveColors = ButtonDefaults.buttonColors(containerColor = role.color, contentColor = role.onColor),
        errorText = formError,
        status = { SplitStatusLine(splitStanding(total, left, complete, money), left, total != null && complete) },
    ) {
        HeroAmountField(
            value = totalText,
            onValueChange = {
                totalText = it
                formError = null
            },
            currency = finance.currency,
            accent = role,
            label = "Total",
            imeAction = ImeAction.Next,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        SharedFieldsSummary(payment, hasPicture, is24Hour)
        Spacer(Modifier.height(AppSpacing.lg))
        parts.forEachIndexed { index, part ->
            key(part.key) {
                val categories = finance.categories.filter {
                    it.type == CategoryType.forTransaction(type) && (!it.archived || it.id == part.categoryId)
                }
                PartCard(
                    number = index + 1,
                    index = index,
                    part = part,
                    categories = categories,
                    decimals = decimals,
                    canRemove = parts.size > MIN_SPLIT_PARTS,
                    // What is left to share can be put into the last part with one tap.
                    rest = if (index == parts.lastIndex && left > 0) left else null,
                    onUseRest = { amount ->
                        part.amount = minorToInputText(amount, decimals)
                        formError = null
                    },
                    onRemove = {
                        parts.removeAt(index)
                        formError = null
                    },
                    onChange = { formError = null },
                )
            }
            Spacer(Modifier.height(AppSpacing.md))
        }
        if (parts.size < MAX_SPLIT_PARTS) {
            TextButton(
                onClick = {
                    parts.add(PartDraft(nextKey++, null, null, "", ""))
                    formError = null
                },
                modifier = Modifier.testTag(SPLIT_ADD_PART_TAG),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(AppSpacing.sm))
                Text("Add a part")
            }
        } else {
            Text(
                "$MAX_SPLIT_PARTS parts is the most",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.sm),
            )
        }
    }
}

/** What every part shares, on one line each can be read from: "HDFC Savings · 12 Nov, 6:30 PM · DMart · For Mom · 2 tags". */
@Composable
private fun SharedFieldsSummary(payment: FinanceTransaction, hasPicture: Boolean, is24Hour: Boolean) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    val summary = listOfNotNull(
        finance.account(payment.accountId)?.name,
        dateTimeLabel(payment.occurredAt, is24Hour),
        payment.payee?.takeIf { it.isNotBlank() },
        finance.person(payment.personId)?.let { "For ${it.name}" },
        payment.tagIds.size.takeIf { it > 0 }?.let { if (it == 1) "1 tag" else "$it tags" },
        "Picture".takeIf { hasPicture },
    ).joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppRadius.rLg)
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
            .semantics(mergeDescendants = true) {},
    ) {
        Text("The same for every part", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Text(summary, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
    }
}

/** One part: its number (with a way to take it out while more than two are left), its category, its amount and its note. */
@Composable
private fun PartCard(
    number: Int,
    index: Int,
    part: PartDraft,
    categories: List<com.example.normpix.finance.domain.Category>,
    decimals: Int,
    canRemove: Boolean,
    rest: Long?,
    onUseRest: (Long) -> Unit,
    onRemove: () -> Unit,
    onChange: () -> Unit,
) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    val currency = finance.currency
    val typed = if (part.amount.isBlank()) 0L else parseMinorUnits(part.amount, decimals)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppRadius.rXl)
            .background(scheme.surfaceContainerLow)
            .padding(start = AppSpacing.lg, top = AppSpacing.sm, end = AppSpacing.lg, bottom = AppSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Part $number",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove part $number")
                }
            }
        }
        TransactionCategoryField(
            categories = categories,
            selected = part.categoryId,
            onSelect = {
                part.categoryId = it
                onChange()
            },
            onCreate = null,
            modifier = Modifier.testTag(splitCategoryTag(index)),
        )
        Spacer(Modifier.height(AppSpacing.md))
        AmountField(
            value = part.amount,
            onValueChange = {
                part.amount = it
                onChange()
            },
            currency = currency,
            label = "Part $number amount",
            errorText = if (typed == null) "Enter a valid amount" else null,
            modifier = Modifier.testTag(splitAmountTag(index)),
        )
        if (rest != null) {
            TextButton(onClick = { onUseRest((typed ?: 0L) + rest) }) {
                Text("Add the ${finance.money.format(rest, trimZeroFraction = true)} left")
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        OutlinedTextField(
            value = part.note,
            onValueChange = {
                part.note = it
                onChange()
            },
            modifier = Modifier.fillMaxWidth().testTag(splitNoteTag(index)),
            label = { Text("Part $number note (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
    }
}

/**
 * The running line, kept above Save: how far the parts are from the total, in words, on a tone that is only a second cue - still to
 * share (neutral), over the total (error), or shared exactly (success).
 */
@Composable
private fun SplitStatusLine(text: String, left: Long, ready: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val success = StatusTone.Success.resolve()
    val (container, content) = when {
        left < 0 -> scheme.errorContainer to scheme.onErrorContainer
        left == 0L && ready -> success.container to success.onContainer
        else -> scheme.secondaryContainer to scheme.onSecondaryContainer
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(AppRadius.rFull)
                .background(container)
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
                .testTag(SPLIT_STATUS_TAG)
                .politeLiveRegion(),
        )
    }
}

/**
 * "Unsplit this payment?": the parts become one payment again, filed under the category chosen here - the largest part's to start with.
 * Calls [onDone] with that category, or null when closed without unsplitting.
 */
@Composable
fun UnsplitSheet(parts: List<FinanceTransaction>, onDone: (Long?) -> Unit) {
    val finance = rememberFinanceState()
    val type = parts.first().type
    var categoryId by remember { mutableStateOf(largestPartCategory(parts)) }
    val categories = finance.categories.filter {
        it.type == CategoryType.forTransaction(type) && (!it.archived || it.id == categoryId)
    }
    EditorSheet(
        title = "Unsplit this payment?",
        subtitle = "The ${parts.size} parts become one ${type.label.lowercase()} of ${finance.money.format(splitTotal(parts), trimZeroFraction = true)}, " +
            "and their notes are joined into one. Nothing else changes.",
        saveLabel = "Unsplit",
        onSave = { categoryId?.let { onDone(it) } },
        saveEnabled = categoryId != null,
    ) {
        TransactionCategoryField(
            categories = categories,
            selected = categoryId,
            onSelect = { categoryId = it },
            onCreate = null,
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            "It starts on the category with the most money in it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = AppSpacing.lg),
        )
    }
}
