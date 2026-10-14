package com.example.normpix.finance.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.dueOccurrences
import com.example.normpix.finance.domain.firstOccurrenceAfter
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.finance.domain.startOfDay
import com.example.normpix.finance.domain.upcomingOccurrences
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal const val MAX_INTERVAL = 99

/**
 * A transaction that repeats: what it is (same fields as a transaction) and when it happens.
 * Occurrences are recorded on this device when they come due.
 *
 * The transaction editor's layout and controls (see [TransactionEditorSheet]), shown as a bottom
 * sheet: type, amount, category and account first, then the schedule. Calls [onDone] with true once
 * saved or deleted, or null when closed without saving.
 */
@Composable
fun RecurringEditorSheet(existingId: Long?, onDone: (Boolean?) -> Unit, onRemind: ((RecurringRule) -> Unit)? = null) {
    if (existingId == null) {
        RecurringEditor(existing = null, onDone = onDone, onRemind = null)
        return
    }
    val controller = LocalFinance.current
    val loaded by produceState<AsyncValue<RecurringRule?>>(AsyncValue.Loading, existingId) {
        value = try {
            AsyncValue.Data(controller.repository.recurringRules().firstOrNull { it.id == existingId })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AsyncValue.Failure(e)
        }
    }
    when (val state = loaded) {
        is AsyncValue.Data -> {
            val rule = state.value
            if (rule == null) LaunchedEffect(Unit) { onDone(null) } else RecurringEditor(existing = rule, onDone = onDone, onRemind = onRemind)
        }
        else -> AppLoadingState(Modifier.fillMaxWidth().height(280.dp))
    }
}

@Composable
private fun RecurringEditor(existing: RecurringRule?, onDone: (Boolean?) -> Unit, onRemind: ((RecurringRule) -> Unit)?) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val isEditing = existing != null

    var type by remember { mutableStateOf(existing?.type ?: TransactionType.Expense) }
    var amount by remember { mutableStateOf(existing?.let { minorToInputText(it.amount, finance.currency.decimals) }.orEmpty()) }
    var payee by remember { mutableStateOf(existing?.payee.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var categoryId by remember { mutableStateOf(existing?.categoryId) }
    var accountId by remember { mutableStateOf(existing?.accountId ?: finance.defaultAccount?.id) }
    var toAccountId by remember { mutableStateOf(existing?.toAccountId) }
    // Who the expenses it records are spent on; only an expense has someone.
    var personId by remember { mutableStateOf(existing?.personId) }
    var frequency by remember { mutableStateOf(existing?.frequency ?: RecurrenceFrequency.Monthly) }
    var interval by remember { mutableStateOf(existing?.interval ?: 1) }
    var start by remember { mutableStateOf(existing?.startDate ?: startOfDay(LocalDateTime.now())) }
    var end by remember { mutableStateOf(existing?.endDate) }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var saving by remember { mutableStateOf(false) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    var amountError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val isTransfer = type == TransactionType.Transfer
    val role = type.role()

    fun snapshot() = listOf(type, amount, payee, note, categoryId, accountId, toAccountId, frequency, interval, start, end, active, personId)
        .joinToString("|")
    val initialSnapshot = remember { snapshot() }
    val dirty = snapshot() != initialSnapshot

    DiscardChangesGuard(dirty = dirty, saving = saving, isEditing = isEditing, keptMessage = "The recurring payment stays as it was.")

    fun setType(next: TransactionType) {
        type = next
        categoryError = null
        accountError = null
        if (next != TransactionType.Expense) personId = null
        if (next == TransactionType.Transfer) {
            categoryId = null
            if (toAccountId == null || toAccountId == accountId) toAccountId = finance.activeAccounts.firstOrNull { it.id != accountId }?.id
        } else {
            toAccountId = null
            if (finance.category(categoryId)?.type != CategoryType.forTransaction(next)) categoryId = null
        }
    }

    /**
     * The rule as currently entered, with the next date it would record.
     *
     * New rules start at their start date (so a start in the past records
     * those payments on save - the form says so first). Edits never
     * back-fill: if the schedule changed, or the rule was paused or ended,
     * the next date moves to the first occurrence after now.
     */
    fun draft(amountMinor: Long = 1): RecurringRule {
        val now = LocalDateTime.now()
        val base = RecurringRule(
            id = existing?.id,
            type = type,
            amount = amountMinor,
            accountId = accountId ?: 0,
            toAccountId = if (isTransfer) toAccountId else null,
            categoryId = if (isTransfer) null else categoryId,
            payee = if (isTransfer) null else payee,
            note = note,
            frequency = frequency,
            interval = interval,
            startDate = start,
            endDate = end,
            nextDate = start,
            active = active,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            personId = if (type == TransactionType.Expense) personId else null,
        )
        if (existing == null) return base
        val scheduleChanged = existing.frequency != frequency || existing.interval != interval || existing.startDate != start
        val stale = existing.nextDate.isBefore(now) || !existing.active
        return base.copy(nextDate = if (scheduleChanged || stale) firstOccurrenceAfter(base, now) else existing.nextDate)
    }

    fun save() {
        val parsed = parseMinorUnits(amount, finance.currency.decimals)
        formError = null
        amountError = if (parsed == null || parsed <= 0) "Enter an amount greater than zero" else null
        categoryError = if (!isTransfer && categoryId == null) "Choose a category" else null
        accountError = when {
            accountId == null -> "Choose an account"
            isTransfer && toAccountId == null -> "Choose where the money goes"
            isTransfer && toAccountId == accountId -> "Choose two different accounts"
            else -> null
        }
        if (amountError != null || categoryError != null || accountError != null || parsed == null) return
        saving = true
        scope.launch {
            try {
                controller.saveRecurringRule(draft(parsed))
                haptics.success()
                snackbars.show(if (isEditing) "Changes saved" else "Recurring payment saved")
                onDone(true)
            } catch (e: FinanceValidationException) {
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

    fun delete() {
        scope.launch {
            val confirmed = overlays.confirm(
                title = "Delete this recurring payment?",
                message = "Future payments won't be recorded. Payments already recorded stay in your history.",
                confirmLabel = "Delete",
                destructive = true,
                icon = Icons.Outlined.DeleteOutline,
            )
            if (!confirmed) return@launch
            controller.deleteRecurringRule(existing!!.id!!)
            snackbars.show("Recurring payment deleted", SnackbarKind.Deleted)
            onDone(true)
        }
    }

    fun newCategory() {
        scope.launch {
            val created = overlays.sheet<Category> { dismiss ->
                CategoryFormSheet(type = CategoryType.forTransaction(type), existing = null, onDone = dismiss)
            }
            if (created != null) {
                categoryId = created.id
                categoryError = null
            }
        }
    }

    fun newPerson() {
        scope.launch {
            val created = overlays.sheet<Person> { dismiss -> PersonFormSheet(existing = null, onDone = dismiss) }
            if (created != null) personId = created.id
        }
    }

    val accounts = finance.accounts.filter { !it.archived || it.id == accountId || it.id == toAccountId }
    val categories = finance.categories.filter { it.type == CategoryType.forTransaction(type) && (!it.archived || it.id == categoryId) }
    val now = LocalDateTime.now()
    val preview = draft()
    val upcoming = if (active) upcomingOccurrences(preview, 3) else emptyList()
    val backfill = if (!isEditing && active) dueOccurrences(preview, now).dates.size else 0
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (!isEditing) runCatching { amountFocus.requestFocus() } }
    val (startShape, endShape) = remember { connectedFieldShapes() }
    val reduced = rememberReducedMotion()
    val frequencies = remember { RecurrenceFrequency.entries.map { ChoiceOption(it, it.label, it.icon) } }

    EditorSheet(
        title = if (isEditing) "Edit recurring" else "New recurring",
        saveLabel = if (isEditing) "Save changes" else "Save recurring",
        onSave = ::save,
        saving = saving,
        saveColors = ButtonDefaults.buttonColors(containerColor = role.color, contentColor = role.onColor),
        titleTrailing = if (isEditing) {
            {
                Row {
                    // A reminder about this payment: attached to it, due when it next is, repeating the way it does.
                    if (onRemind != null && existing != null) {
                        IconButton(onClick = { onRemind(existing) }) {
                            Icon(Icons.Outlined.NotificationAdd, contentDescription = "Remind me about this payment")
                        }
                    }
                    IconButton(onClick = ::delete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete") }
                }
            }
        } else {
            null
        },
    ) {
        TransactionTypeSelector(type = type, onSelect = ::setType)
        Spacer(Modifier.height(AppSpacing.xxl))
        HeroAmountField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = null
            },
            currency = finance.currency,
            accent = role,
            errorText = amountError,
            modifier = Modifier.focusRequester(amountFocus),
        )
        Spacer(Modifier.height(AppSpacing.xxl))
        // What changes with the type fades in place rather than snapping.
        AnimatedContent(
            targetState = isTransfer,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(AppMotion.SHORT_MS, delayMillis = 60, easing = AppMotion.enter)) togetherWith
                        fadeOut(tween(90))
                }
            },
            modifier = Modifier.animateContentSize(AppMotion.spatial()),
            label = "type_fields",
        ) { transfer ->
            Column {
                if (transfer) {
                    AccountSelector(
                        label = "From",
                        accounts = accounts,
                        selected = accountId,
                        onSelect = {
                            accountId = it
                            if (toAccountId == it) toAccountId = null
                            accountError = null
                        },
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    AccountSelector(
                        label = "To",
                        accounts = accounts,
                        selected = toAccountId,
                        exclude = accountId,
                        onSelect = {
                            toAccountId = it
                            accountError = null
                        },
                        errorText = accountError,
                    )
                } else {
                    TransactionCategoryField(
                        categories = categories,
                        selected = categoryId,
                        onSelect = {
                            categoryId = it
                            categoryError = null
                        },
                        onCreate = ::newCategory,
                        errorText = categoryError,
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    if (accounts.isEmpty()) {
                        Text(
                            "Add an account in Wallet first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        AccountSelector(
                            label = if (type == TransactionType.Income) "Into" else "Paid from",
                            accounts = accounts,
                            selected = accountId,
                            onSelect = {
                                accountId = it
                                accountError = null
                            },
                            errorText = accountError,
                        )
                    }
                    RevealVisibility(visible = type == TransactionType.Expense) {
                        Column {
                            Spacer(Modifier.height(AppSpacing.lg))
                            PersonSelector(
                                people = finance.people.filter { !it.archived || it.id == personId },
                                selected = personId,
                                onSelect = { personId = it },
                                onCreate = ::newPerson,
                            )
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.lg))
                    OutlinedTextField(
                        value = payee,
                        onValueChange = { payee = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (type == TransactionType.Income) "Received from" else "Paid to") },
                        placeholder = { Text(if (type == TransactionType.Income) "e.g. Employer" else "e.g. Landlord, Netflix") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        colors = typedFieldColors(),
                        shape = TonalFieldShape,
                    )
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.xxl))
        Text("Repeats", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(AppSpacing.md))
        ConnectedChoiceRow(frequencies, selected = frequency, onSelect = { frequency = it })
        Spacer(Modifier.height(AppSpacing.md))
        IntervalStepper(
            interval = interval,
            unit = frequency.unit(interval),
            max = MAX_INTERVAL,
            onChanged = {
                haptics.selection()
                interval = it
            },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        // First and last payment read as one connected pair once there is an end date.
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TonalPickerField(
                label = if (isEditing) "Schedule starts" else "First payment",
                value = shortDate(start),
                icon = Icons.Outlined.CalendarToday,
                onClick = { pickingStart = true },
                modifier = Modifier.weight(1f),
                shape = if (end == null) TonalFieldShape else startShape,
            )
            end?.let { endDate ->
                TonalPickerField(
                    label = "Last payment by",
                    value = shortDate(endDate),
                    icon = Icons.Outlined.Event,
                    onClick = { pickingEnd = true },
                    modifier = Modifier.weight(1f),
                    shape = endShape,
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        SwitchRow(
            title = "Has an end date",
            checked = end != null,
            onChange = { on ->
                haptics.selection()
                end = if (on) startOfDay(start.plusDays(365)) else null
            },
        )
        if (isEditing) {
            SwitchRow(
                title = "Active",
                subtitle = "Paused payments aren't recorded. Missed ones are skipped when you resume.",
                checked = active,
                onChange = {
                    haptics.selection()
                    active = it
                },
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        SchedulePreview(upcoming = upcoming, active = active, backfill = backfill, since = start, isEditing = isEditing)
        Spacer(Modifier.height(AppSpacing.lg))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        formError?.let {
            Spacer(Modifier.height(AppSpacing.lg))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
    }

    if (pickingStart) {
        DatePickerDialogFor(
            initial = start.toLocalDate(),
            onPicked = { date ->
                pickingStart = false
                start = date.atStartOfDay()
                end?.let { if (it.isBefore(start)) end = start }
            },
            onDismiss = { pickingStart = false },
        )
    }
    if (pickingEnd) {
        DatePickerDialogFor(
            initial = (end ?: start.plusDays(365)).toLocalDate(),
            onPicked = { date ->
                pickingEnd = false
                end = date.atStartOfDay()
            },
            onDismiss = { pickingEnd = false },
            yearsAhead = 50,
        )
    }
}

/** Icon for a repeat frequency in the connected choice row. */
internal val RecurrenceFrequency.icon: ImageVector
    get() = when (this) {
        RecurrenceFrequency.Daily -> Icons.Outlined.Today
        RecurrenceFrequency.Weekly -> Icons.Outlined.ViewWeek
        RecurrenceFrequency.Monthly -> Icons.Outlined.CalendarMonth
        RecurrenceFrequency.Yearly -> Icons.Outlined.Event
    }

/** "Every [-] 2 [+] months". */
@Composable
internal fun IntervalStepper(interval: Int, unit: String, max: Int, onChanged: (Int) -> Unit) {
    val text = MaterialTheme.typography
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = "Every $interval $unit" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Every", style = text.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
        Spacer(Modifier.width(AppSpacing.sm))
        OutlinedIconButton(onClick = { onChanged(interval - 1) }, enabled = interval > 1) {
            Icon(Icons.Rounded.Remove, contentDescription = "Less often")
        }
        Text(
            "$interval",
            modifier = Modifier.width(40.dp).clearAndSetSemantics {},
            textAlign = TextAlign.Center,
            style = text.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
        )
        OutlinedIconButton(onClick = { onChanged(interval + 1) }, enabled = interval < max) {
            Icon(Icons.Rounded.Add, contentDescription = "More apart")
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Text(unit, style = text.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
    }
}

/** What will happen when the rule is saved: the next few dates, and any past payments a new rule will record right away. */
@Composable
private fun SchedulePreview(upcoming: List<LocalDateTime>, active: Boolean, backfill: Int, since: LocalDateTime, isEditing: Boolean) {
    val text = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val lines = buildList {
        add(
            when {
                !active -> "Paused. Nothing will be recorded until you turn it back on."
                upcoming.isEmpty() -> "No payments left before the end date."
                else -> "Next: ${upcoming.joinToString(" · ") { shortDate(it) }}"
            },
        )
        if (backfill > 0) {
            add(
                if (backfill == 1) {
                    "The payment on ${shortDate(since)} is already due and will be recorded when you save."
                } else {
                    "$backfill payments since ${shortDate(since)} are already due and will be recorded when you save."
                },
            )
        }
        if (isEditing) add("Changes apply to future payments. Ones already recorded stay as they are.")
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(AppRadius.lg),
    ) {
        Row(Modifier.padding(AppSpacing.lg), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.EventRepeat, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(AppIconSizes.md))
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                lines.forEachIndexed { i, line ->
                    Text(
                        line,
                        style = if (i == 0) text.bodyLarge else text.bodyMedium,
                        color = if (i == 0) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
