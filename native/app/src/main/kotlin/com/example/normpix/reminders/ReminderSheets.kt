package com.example.normpix.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Work
import com.example.normpix.finance.ui.ChoiceDropdownField
import com.example.normpix.finance.ui.ChoicePickerField
import com.example.normpix.finance.ui.IconBadge
import com.example.normpix.ui.components.BankGlyph
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.domain.RepeatEnd
import com.example.normpix.finance.domain.notBefore
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.finance.ui.RepeatEndField
import com.example.normpix.finance.ui.RepeatNoun
import com.example.normpix.finance.ui.TonalFieldShape
import com.example.normpix.finance.ui.PickerField
import com.example.normpix.finance.ui.PickerFieldRow
import com.example.normpix.finance.ui.tonalFieldColors
import com.example.normpix.finance.ui.typedFieldColors
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.finance.ui.DatePickerDialogFor
import com.example.normpix.finance.ui.TimePickerDialogFor
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.StoredCard
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.launch

/** A snooze-duration picker. Calls [onDone] with how long to snooze for, or null if dismissed. */
@Composable
fun SnoozeOptionsSheet(onDone: (Duration?) -> Unit) {
    val haptics = rememberAppHaptics()
    val now = remember { LocalDateTime.now() }
    val tomorrowMorning = Duration.between(now, LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(9, 0)))
    val options = listOf(
        "15 minutes" to Duration.ofMinutes(15),
        "1 hour" to Duration.ofHours(1),
        "3 hours" to Duration.ofHours(3),
        "Tomorrow morning" to tomorrowMorning,
    )
    Column {
        Box(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm)) {
            SheetHeader(title = "Snooze")
        }
        for ((label, duration) in options) {
            ListItem(
                modifier = Modifier.clickable {
                    haptics.selection()
                    onDone(duration)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(Icons.Rounded.Snooze, contentDescription = null) },
                headlineContent = { Text(label) },
                // The resulting time, so "3 hours" never needs mental math.
                supportingContent = { Text(formatDueMoment(now.plus(duration))) },
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
    }
}

/** What a reminder's occurrences are called by "Ends": "After 12 times". */
private val ReminderNoun = RepeatNoun(one = "time", many = "times", lastBy = "Last reminder by")

private val WEEKDAY_SHORT = listOf("M", "T", "W", "T", "F", "S", "S")
private val WEEKDAY_FULL = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** Configures a weekly, monthly or interval repeat. Calls [onDone] with the rule, or null if dismissed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatRuleSheet(initial: RepeatRule?, onDone: (RepeatRule?) -> Unit) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val startingKind = initial?.kind?.takeIf { it == RepeatKind.Weekly || it == RepeatKind.Monthly || it == RepeatKind.Custom }
    var kind by remember { mutableStateOf(startingKind ?: RepeatKind.Weekly) }
    var weekdays by remember {
        mutableStateOf(initial?.weekdays?.takeIf { it.isNotEmpty() } ?: setOf(LocalDate.now().dayOfWeek.value))
    }
    var dayOfMonth by remember { mutableStateOf(initial?.dayOfMonth ?: LocalDate.now().dayOfMonth) }
    var intervalDays by remember { mutableStateOf(initial?.intervalDays ?: 2) }

    val rule = when (kind) {
        RepeatKind.Weekly -> RepeatRule(RepeatKind.Weekly, weekdays = weekdays)
        RepeatKind.Monthly -> RepeatRule(RepeatKind.Monthly, dayOfMonth = dayOfMonth)
        RepeatKind.Custom -> RepeatRule(RepeatKind.Custom, intervalDays = intervalDays)
        else -> RepeatRule.Once
    }
    val labelStyle = MaterialTheme.typography.labelLarge
    val kinds = remember {
        listOf(
            ChoiceOption(RepeatKind.Weekly, "Weekly", Icons.Outlined.ViewWeek),
            ChoiceOption(RepeatKind.Monthly, "Monthly", Icons.Outlined.CalendarMonth),
            ChoiceOption(RepeatKind.Custom, "Interval", Icons.Rounded.Tune),
        )
    }

    EditorSheet(title = "Custom repeat", subtitle = rule.label, saveLabel = "Done", onSave = { onDone(rule) }) {
        ConnectedChoiceRow(kinds, selected = kind, onSelect = { kind = it })
        Spacer(Modifier.height(AppSpacing.xl))
        when (kind) {
            RepeatKind.Weekly -> {
                Text("Repeats on", style = labelStyle, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(AppSpacing.sm))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    for (day in 1..7) {
                        WeekdayToggle(
                            label = WEEKDAY_SHORT[day - 1],
                            semanticLabel = WEEKDAY_FULL[day - 1],
                            selected = day in weekdays,
                            onClick = {
                                haptics.selection()
                                weekdays = if (day in weekdays && weekdays.size > 1) weekdays - day else weekdays + day
                            },
                        )
                    }
                }
            }
            RepeatKind.Monthly -> {
                Text("Day of the month", style = labelStyle, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(AppSpacing.sm))
                Stepper(
                    value = "$dayOfMonth",
                    spoken = "Day $dayOfMonth",
                    onDecrement = if (dayOfMonth > 1) ({ dayOfMonth-- }) else null,
                    onIncrement = if (dayOfMonth < 31) ({ dayOfMonth++ }) else null,
                )
            }
            else -> {
                Text("Repeat every", style = labelStyle, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(AppSpacing.sm))
                Stepper(
                    value = "$intervalDays days",
                    spoken = "Every $intervalDays days",
                    onDecrement = if (intervalDays > 2) ({ intervalDays-- }) else null,
                    onIncrement = if (intervalDays < 90) ({ intervalDays++ }) else null,
                )
            }
        }
    }
}

@Composable
private fun WeekdayToggle(label: String, semanticLabel: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (selected) scheme.primary else scheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = semanticLabel
                this.selected = selected
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant)
    }
}

@Composable
private fun Stepper(value: String, spoken: String, onDecrement: (() -> Unit)?, onIncrement: (() -> Unit)?) {
    val haptics = rememberAppHaptics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = {
                haptics.selection()
                onDecrement?.invoke()
            },
            enabled = onDecrement != null,
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease") }
        Text(
            value,
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
        FilledTonalIconButton(
            onClick = {
                haptics.selection()
                onIncrement?.invoke()
            },
            enabled = onIncrement != null,
        ) { Icon(Icons.Rounded.Add, contentDescription = "Increase") }
    }
}

private fun defaultInitialDueAt(): LocalDateTime =
    LocalDateTime.now().withMinute(0).withSecond(0).withNano(0).plusHours(1)

private fun dateLabel(d: LocalDateTime): String {
    val diff = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), d.toLocalDate())
    return when (diff) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "${d.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())} ${d.dayOfMonth}, ${d.year}"
    }
}

/**
 * The add/edit reminder form, laid out like the transaction editor (see `EditorSheet`). Calls
 * [onDone] with the created / edited [Reminder], or null if the user closes it. The caller owns
 * persistence.
 *
 * A new reminder can start from [initial] - a draft already filled in (see [ReminderDrafts]) for something the user asked to
 * be reminded of; it is still a new reminder, and nothing is saved until the form is.
 */
@Composable
fun AddReminderSheet(
    existing: Reminder?,
    cards: List<StoredCard>,
    onAddCard: suspend () -> StoredCard?,
    onDone: (Reminder?) -> Unit,
    initial: Reminder? = null,
) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val isEditing = existing != null
    val seed = existing ?: initial
    var title by remember { mutableStateOf(seed?.title.orEmpty()) }
    var titleError by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf(seed?.notes.orEmpty()) }
    var customLabel by remember { mutableStateOf(seed?.customCategoryLabel.orEmpty()) }
    var category by remember { mutableStateOf(seed?.category ?: ReminderCategory.Personal) }
    var dueAt by remember { mutableStateOf(seed?.dueAt ?: defaultInitialDueAt()) }
    // How often it repeats and where it stops are held apart: changing the rhythm leaves the end as it was set, and a count
    // ("after 12 times") is worked out into the day of the last one from the date as it is when the reminder is saved.
    var repeat by remember { mutableStateOf(seed?.repeatRule?.unending ?: RepeatRule.Once) }
    var ends by remember { mutableStateOf(seed?.repeatRule?.ends ?: RepeatEnd.Never) }
    var linked by remember { mutableStateOf(seed?.link) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // A draft already has its title; typing is not what is wanted first.
    LaunchedEffect(Unit) { if (seed == null) runCatching { focus.requestFocus() } }

    val is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    val attached = rememberResolvedLink(linked)

    fun snapshot() = listOf(title, notes, customLabel, category, dueAt, repeat, ends, linked).joinToString("|")
    // A draft as it was handed in is unchanged: leaving it untouched asks nothing.
    val initialSnapshot = remember { snapshot() }
    DiscardChangesGuard(dirty = snapshot() != initialSnapshot, isEditing = isEditing, keptMessage = "The reminder stays as it was.")

    fun setRepeatPreset(kind: RepeatKind) {
        repeat = when (kind) {
            RepeatKind.Once -> RepeatRule.Once
            RepeatKind.Daily -> RepeatRule(RepeatKind.Daily)
            RepeatKind.Weekdays -> RepeatRule(RepeatKind.Weekdays)
            RepeatKind.Weekly -> RepeatRule(RepeatKind.Weekly, weekdays = setOf(dueAt.dayOfWeek.value))
            RepeatKind.Monthly -> RepeatRule(RepeatKind.Monthly, dayOfMonth = dueAt.dayOfMonth)
            // Custom intervals are only configured through the detail sheet.
            RepeatKind.Custom -> repeat
        }
    }

    val overlays = com.example.normpix.ui.components.LocalOverlays.current
    fun openCustomRepeat() {
        scope.launch {
            val result = overlays.sheet<RepeatRule> { dismiss -> RepeatRuleSheet(initial = repeat, onDone = dismiss) }
            if (result != null) repeat = result
        }
    }

    fun submit() {
        if (title.isBlank()) {
            titleError = "Give the reminder a title"
            return
        }
        val now = LocalDateTime.now()
        onDone(
            Reminder(
                id = existing?.id,
                title = title.trim(),
                notes = notes.trim().ifEmpty { null },
                category = category,
                customCategoryLabel = if (category == ReminderCategory.Custom) customLabel.trim() else null,
                dueAt = dueAt,
                repeatRule = repeat.ending(ends, dueAt),
                status = existing?.status ?: ReminderStatus.Active,
                snoozedUntil = existing?.snoozedUntil,
                link = linked,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                completedAt = existing?.completedAt,
                lastFiredAt = existing?.lastFiredAt,
            ),
        )
    }

    val categories = remember { ReminderCategory.entries.map { ChoiceOption(it, it.label, it.icon) } }
    val repeats = buildList {
        add(ChoiceOption(RepeatKind.Once, "Once", Icons.Outlined.LooksOne))
        add(ChoiceOption(RepeatKind.Daily, "Daily", Icons.Outlined.Today))
        add(ChoiceOption(RepeatKind.Weekdays, "Weekdays", Icons.Outlined.Work))
        add(ChoiceOption(RepeatKind.Weekly, "Weekly", Icons.Outlined.ViewWeek))
        add(ChoiceOption(RepeatKind.Monthly, "Monthly", Icons.Outlined.CalendarMonth))
        // A custom interval shows as its own choice once set; it's made through "Custom repeat".
        if (repeat.kind == RepeatKind.Custom) add(ChoiceOption(RepeatKind.Custom, repeat.label, Icons.Rounded.Tune))
    }
    val overlaysForAttach = com.example.normpix.ui.components.LocalOverlays.current
    fun openAttach() {
        scope.launch {
            val choice = overlaysForAttach.sheet<AttachChoice> { dismiss ->
                ReminderAttachSheet(current = linked, cards = cards, onAddCard = onAddCard, onDone = dismiss)
            }
            // A card added from the sheet is already saved, so the field finds it by id like any other.
            if (choice != null) linked = choice.link
        }
    }

    EditorSheet(
        title = if (isEditing) "Edit reminder" else "New reminder",
        saveLabel = if (isEditing) "Save changes" else "Save reminder",
        onSave = ::submit,
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                titleError = null
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text("What do you want to remember?") },
            placeholder = { Text("e.g. Pay HDFC card bill") },
            isError = titleError != null,
            supportingText = titleError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        PickerFieldRow(
            first = PickerField("Date", dateLabel(dueAt)) { pickingDate = true },
            second = PickerField("Time", timeText(dueAt, is24Hour)) { pickingTime = true },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        ChoiceDropdownField(
            label = "Category",
            options = categories,
            selected = category,
            onSelect = { category = it },
        )
        if (category == ReminderCategory.Custom) {
            Spacer(Modifier.height(AppSpacing.sm))
            OutlinedTextField(
                value = customLabel,
                onValueChange = { customLabel = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Category name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = typedFieldColors(),
                shape = TonalFieldShape,
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        ChoiceDropdownField(
            label = "Repeat",
            options = repeats,
            selected = repeat.kind,
            onSelect = ::setRepeatPreset,
            createLabel = "Custom repeat",
            onCreate = ::openCustomRepeat,
        )
        if (repeat.repeats) {
            val rule = repeat.ending(ends, dueAt)
            Text(
                rule.label,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.xs),
            )
            Spacer(Modifier.height(AppSpacing.lg))
            // The same "Ends" as a repeating payment has: never unless it is set to stop.
            RepeatEndField(ends = ends, onChange = { ends = it }, start = dueAt, next = repeat::nextOccurrence, noun = ReminderNoun)
        }
        Spacer(Modifier.height(AppSpacing.lg))
        // What it is about: an expense, an income, a repeating payment, an account or a card - not only a card.
        ChoicePickerField(
            label = "Attached to",
            // Said in the field, not as a placeholder (which only shows once the field is focused), like "Does not repeat".
            value = attached?.line ?: if (linked == null) "Nothing attached" else null,
            placeholder = "Nothing attached",
            onClick = ::openAttach,
            onClear = if (linked != null) ({ linked = null }) else null,
            leading = {
                val link = attached
                when {
                    link == null -> IconBadge(Icons.Outlined.AttachFile)
                    link.bankName != null -> IconBadge(null, glyph = { BankGlyph(link.bankName, tint = scheme.onSecondaryContainer, size = 22.dp) })
                    else -> IconBadge(link.icon)
                }
            },
        )
        Spacer(Modifier.height(AppSpacing.lg))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Notes (optional)") },
            minLines = 1,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
    }

    if (pickingDate) {
        DatePickerDialogFor(
            initial = dueAt.toLocalDate(),
            onPicked = { date ->
                pickingDate = false
                dueAt = LocalDateTime.of(date, dueAt.toLocalTime())
                // An end can't come before the first reminder.
                ends = ends.notBefore(date)
            },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime) {
        TimePickerDialogFor(
            initial = dueAt,
            onPicked = { hour, minute ->
                pickingTime = false
                dueAt = dueAt.withHour(hour).withMinute(minute)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

