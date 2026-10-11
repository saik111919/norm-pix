package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.dueOccurrences
import com.example.normpix.finance.domain.nextRecurrence
import com.example.normpix.finance.domain.startOfDay
import com.example.normpix.finance.domain.upcomingOccurrences
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/*
 * Repeating a payment from where it is entered. The editor offers the usual choices in one dropdown - the same "Repeat"
 * the reminder form has - and "Custom" for every-N and an end date. Saving then records this transaction as the first of
 * a repeating payment, which appears under Plans > Recurring like any other.
 */

private enum class RepeatPreset { None, Daily, Weekly, Monthly, Yearly, Custom }

private fun RepeatSchedule?.preset(): RepeatPreset = when {
    this == null -> RepeatPreset.None
    interval == 1 && endDate == null -> when (frequency) {
        RecurrenceFrequency.Daily -> RepeatPreset.Daily
        RecurrenceFrequency.Weekly -> RepeatPreset.Weekly
        RecurrenceFrequency.Monthly -> RepeatPreset.Monthly
        RecurrenceFrequency.Yearly -> RepeatPreset.Yearly
    }
    else -> RepeatPreset.Custom
}

/** "Every month", "Every 2 weeks", "Every day until 3 Mar 2027" - what a schedule means, in a line. */
fun describeRepeat(schedule: RepeatSchedule, now: LocalDateTime = LocalDateTime.now()): String {
    val every = if (schedule.interval == 1) {
        "Every ${schedule.frequency.unit(1)}"
    } else {
        "Every ${schedule.interval} ${schedule.frequency.unit(schedule.interval)}"
    }
    return schedule.endDate?.let { "$every until ${shortDate(it, now)}" } ?: every
}

/** The schedule as the rule it would become when [from] is its first occurrence, for working out the dates it would give. */
private fun previewRule(schedule: RepeatSchedule, from: LocalDateTime) = RecurringRule(
    type = TransactionType.Expense,
    amount = 1,
    accountId = 0,
    frequency = schedule.frequency,
    interval = schedule.interval,
    startDate = from,
    endDate = schedule.endDate,
    nextDate = nextRecurrence(from, schedule.frequency, schedule.interval, anchor = from),
    createdAt = from,
    updatedAt = from,
)

/**
 * The editor's "Repeat" choice. [repeat] is null for a one-off; [from] is the transaction's own date, which is the first
 * occurrence. Under the field, the next dates it will be recorded on - and, when [from] is in the past, how many repeats are
 * recorded straight away, so nothing is a surprise after Save. A transaction that already belongs to a repeating payment
 * ([ruleId]) is not offered another: it says so, and leads to that payment's own editor.
 */
@Composable
fun RepeatField(
    repeat: RepeatSchedule?,
    onChange: (RepeatSchedule?) -> Unit,
    from: LocalDateTime,
    modifier: Modifier = Modifier,
    ruleId: Long? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()

    if (ruleId != null) {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EventRepeat, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(AppSpacing.md))
            Text(
                "Part of a repeating payment",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { scope.launch { overlays.sheet<Boolean> { dismiss -> RecurringEditorSheet(existingId = ruleId, onDone = dismiss) } } },
            ) { Text("Edit schedule") }
        }
        return
    }

    val current = repeat.preset()
    val options = buildList {
        add(ChoiceOption(RepeatPreset.None, "Does not repeat", Icons.Outlined.EventBusy))
        add(ChoiceOption(RepeatPreset.Daily, "Every day", Icons.Outlined.EventRepeat))
        add(ChoiceOption(RepeatPreset.Weekly, "Every week", Icons.Outlined.EventRepeat))
        add(ChoiceOption(RepeatPreset.Monthly, "Every month", Icons.Outlined.EventRepeat))
        add(ChoiceOption(RepeatPreset.Yearly, "Every year", Icons.Outlined.EventRepeat))
        // A schedule made with "Custom" shows as its own choice, so the field can say what it is.
        if (current == RepeatPreset.Custom && repeat != null) add(ChoiceOption(RepeatPreset.Custom, describeRepeat(repeat), Icons.Rounded.Tune))
    }

    fun openCustom() {
        scope.launch {
            val picked = overlays.sheet<RepeatSchedule> { dismiss -> RepeatSheet(initial = repeat, from = from, onDone = dismiss) }
            if (picked != null) onChange(picked)
        }
    }

    Column(modifier.fillMaxWidth()) {
        ChoiceDropdownField(
            label = "Repeat",
            options = options,
            selected = current,
            onSelect = { choice ->
                onChange(
                    when (choice) {
                        RepeatPreset.None -> null
                        RepeatPreset.Daily -> RepeatSchedule(RecurrenceFrequency.Daily)
                        RepeatPreset.Weekly -> RepeatSchedule(RecurrenceFrequency.Weekly)
                        RepeatPreset.Monthly -> RepeatSchedule(RecurrenceFrequency.Monthly)
                        RepeatPreset.Yearly -> RepeatSchedule(RecurrenceFrequency.Yearly)
                        RepeatPreset.Custom -> repeat
                    },
                )
            },
            createLabel = "Custom…",
            onCreate = ::openCustom,
            emptyIcon = Icons.Outlined.EventRepeat,
        )
        if (repeat != null) {
            RepeatPreview(
                schedule = repeat,
                from = from,
                modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.xs, end = AppSpacing.lg),
            )
        }
    }
}

/** What a schedule will do from [from]: the next dates it is recorded on, and any that are already due. */
@Composable
private fun RepeatPreview(schedule: RepeatSchedule, from: LocalDateTime, modifier: Modifier = Modifier) {
    val now = remember { LocalDateTime.now() }
    val rule = remember(schedule, from) { previewRule(schedule, from) }
    // The repeats already due (a first date in the past) are recorded on save; what is shown as "Next" starts after those.
    val caughtUp = remember(rule, now) { dueOccurrences(rule, now) }
    val upcoming = remember(caughtUp, rule) { upcomingOccurrences(rule.copy(nextDate = caughtUp.nextDate), 3) }
    val due = caughtUp.dates.size
    val text = buildString {
        if (due > 0) {
            append(
                if (due == 1) {
                    "1 more since ${shortDate(from, now)} is already due and is recorded when you save. "
                } else {
                    "$due more since ${shortDate(from, now)} are already due and are recorded when you save. "
                },
            )
        }
        append(
            when {
                upcoming.isNotEmpty() -> "Next: ${upcoming.joinToString(" · ") { shortDate(it, now) }}."
                due == 0 -> "No repeats left before the end date."
                else -> "No more after that."
            },
        )
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * "Custom repeat": how often (every N days / weeks / months / years) and whether it stops on a date. Calls [onDone] with the
 * schedule, or null when dismissed.
 */
@Composable
fun RepeatSheet(initial: RepeatSchedule?, from: LocalDateTime, onDone: (RepeatSchedule?) -> Unit) {
    val haptics = rememberAppHaptics()
    var frequency by remember { mutableStateOf(initial?.frequency ?: RecurrenceFrequency.Monthly) }
    var interval by remember { mutableStateOf(initial?.interval ?: 1) }
    var end by remember { mutableStateOf(initial?.endDate) }
    var pickingEnd by remember { mutableStateOf(false) }
    val frequencies = remember { RecurrenceFrequency.entries.map { ChoiceOption(it, it.label, it.icon) } }
    val schedule = RepeatSchedule(frequency, interval, end)

    EditorSheet(
        title = "Custom repeat",
        subtitle = describeRepeat(schedule),
        saveLabel = "Done",
        onSave = { onDone(schedule) },
    ) {
        ConnectedChoiceRow(frequencies, selected = frequency, onSelect = { frequency = it })
        Spacer(Modifier.height(AppSpacing.lg))
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
        SwitchRow(
            title = "Stops on a date",
            checked = end != null,
            onChange = { on ->
                haptics.selection()
                end = if (on) startOfDay(from.plusYears(1)) else null
            },
        )
        end?.let { endDate ->
            Spacer(Modifier.height(AppSpacing.sm))
            TonalPickerField(
                label = "Last repeat by",
                value = shortDate(endDate),
                icon = Icons.Outlined.Event,
                onClick = { pickingEnd = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        RepeatPreview(schedule, from)
    }

    if (pickingEnd) {
        DatePickerDialogFor(
            initial = (end ?: from.plusYears(1)).toLocalDate(),
            onPicked = { date ->
                pickingEnd = false
                // Never before the first payment: that would be a repeat that never happens.
                end = maxOf(date.atStartOfDay(), startOfDay(from))
            },
            onDismiss = { pickingEnd = false },
            yearsAhead = 50,
        )
    }
}
