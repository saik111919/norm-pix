package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.normpix.finance.domain.RepeatEnd
import com.example.normpix.finance.domain.RepeatPlan
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.dueOccurrences
import com.example.normpix.finance.domain.nextRecurrence
import com.example.normpix.finance.domain.upcomingOccurrences
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/*
 * Repeating a payment from where it is entered. The editor offers the usual choices in one dropdown - the same "Repeat"
 * the reminder form has - and "Custom" for every-N. Whichever is chosen, "Ends" says when it stops: never, on a date or after
 * a number of payments. Saving then records this transaction as the first of a repeating payment, which appears under
 * Plans > Recurring like any other.
 */

private enum class RepeatPreset { None, Daily, Weekly, Monthly, Yearly, Custom }

/** The dropdown's choice for a plan. How it ends is not part of it: "Every month" stays chosen with an end set. */
private fun RepeatPlan?.preset(): RepeatPreset = when {
    this == null -> RepeatPreset.None
    interval == 1 -> when (frequency) {
        RecurrenceFrequency.Daily -> RepeatPreset.Daily
        RecurrenceFrequency.Weekly -> RepeatPreset.Weekly
        RecurrenceFrequency.Monthly -> RepeatPreset.Monthly
        RecurrenceFrequency.Yearly -> RepeatPreset.Yearly
    }
    else -> RepeatPreset.Custom
}

/** "Every month", "Every 2 weeks": how often, without the end. */
private fun everyLabel(frequency: RecurrenceFrequency, interval: Int): String =
    if (interval == 1) "Every ${frequency.unit(1)}" else "Every $interval ${frequency.unit(interval)}"

/** "Every month", "Every 2 weeks", "Every day until 3 Mar 2027" - what a schedule means, in a line. */
fun describeRepeat(schedule: RepeatSchedule, now: LocalDateTime = LocalDateTime.now()): String {
    val every = everyLabel(schedule.frequency, schedule.interval)
    return schedule.endDate?.let { "$every until ${shortDate(it, now)}" } ?: every
}

/** [describeRepeat] for a plan whose first payment is on [from]: a count has become the day of its last payment by then. */
fun describeRepeat(plan: RepeatPlan, from: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String =
    describeRepeat(plan.schedule(from), now)

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
 * occurrence. Once it repeats, "Ends" says when it stops (never, unless the user says so) and, under them, the next dates it
 * will be recorded on - and, when [from] is in the past, how many repeats are recorded straight away, so nothing is a surprise
 * after Save. A transaction that already belongs to a repeating payment ([ruleId]) is not offered another: it says so, and
 * leads to that payment's own editor.
 *
 * The plan keeps "after 12 payments" as a count, not a date, so it still means 12 if the date or the frequency changes; the
 * editor turns it into the schedule to save with [RepeatPlan.schedule] once the date is final.
 */
@Composable
fun RepeatField(
    repeat: RepeatPlan?,
    onChange: (RepeatPlan?) -> Unit,
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
        if (current == RepeatPreset.Custom && repeat != null) {
            add(ChoiceOption(RepeatPreset.Custom, everyLabel(repeat.frequency, repeat.interval), Icons.Rounded.Tune))
        }
    }

    fun openCustom() {
        scope.launch {
            val picked = overlays.sheet<RepeatPlan> { dismiss -> RepeatSheet(initial = repeat, from = from, onDone = dismiss) }
            if (picked != null) onChange(picked)
        }
    }

    Column(modifier.fillMaxWidth()) {
        ChoiceDropdownField(
            label = "Repeat",
            options = options,
            selected = current,
            onSelect = { choice ->
                // Changing how often it repeats leaves the end as it was set.
                val ends = repeat?.ends ?: RepeatEnd.Never
                onChange(
                    when (choice) {
                        RepeatPreset.None -> null
                        RepeatPreset.Daily -> RepeatPlan(RecurrenceFrequency.Daily, ends = ends)
                        RepeatPreset.Weekly -> RepeatPlan(RecurrenceFrequency.Weekly, ends = ends)
                        RepeatPreset.Monthly -> RepeatPlan(RecurrenceFrequency.Monthly, ends = ends)
                        RepeatPreset.Yearly -> RepeatPlan(RecurrenceFrequency.Yearly, ends = ends)
                        RepeatPreset.Custom -> repeat
                    },
                )
            },
            createLabel = "Custom…",
            onCreate = ::openCustom,
            emptyIcon = Icons.Outlined.EventRepeat,
        )
        if (repeat != null) {
            val step = remember(repeat.frequency, repeat.interval, from) { repeat.step(from) }
            Spacer(Modifier.height(AppSpacing.lg))
            RepeatEndField(
                ends = repeat.ends,
                onChange = { onChange(repeat.copy(ends = it)) },
                start = from,
                next = step,
                noun = PaymentNoun,
            )
            RepeatPreview(
                schedule = repeat.schedule(from),
                from = from,
                modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.sm, end = AppSpacing.lg),
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
 * "Custom repeat": how often (every N days / weeks / months / years) and when it ends (never, on a date, or after a number of
 * payments). Calls [onDone] with the plan, or null when dismissed.
 */
@Composable
fun RepeatSheet(initial: RepeatPlan?, from: LocalDateTime, onDone: (RepeatPlan?) -> Unit) {
    val haptics = rememberAppHaptics()
    var frequency by remember { mutableStateOf(initial?.frequency ?: RecurrenceFrequency.Monthly) }
    var interval by remember { mutableIntStateOf(initial?.interval ?: 1) }
    var ends by remember { mutableStateOf(initial?.ends ?: RepeatEnd.Never) }
    val frequencies = remember { RecurrenceFrequency.entries.map { ChoiceOption(it, it.label, it.icon) } }
    val plan = RepeatPlan(frequency, interval, ends)
    val step = remember(frequency, interval, from) { plan.step(from) }

    EditorSheet(
        title = "Custom repeat",
        subtitle = describeRepeat(plan, from),
        saveLabel = "Done",
        onSave = { onDone(plan) },
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
        Spacer(Modifier.height(AppSpacing.xl))
        RepeatEndField(ends = ends, onChange = { ends = it }, start = from, next = step, noun = PaymentNoun)
        Spacer(Modifier.height(AppSpacing.lg))
        RepeatPreview(plan.schedule(from), from)
    }
}
