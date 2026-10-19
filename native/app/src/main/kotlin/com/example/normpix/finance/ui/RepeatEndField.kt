package com.example.normpix.finance.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.MAX_REPEAT_COUNT
import com.example.normpix.finance.domain.MIN_REPEAT_COUNT
import com.example.normpix.finance.domain.REPEAT_COUNT_LIMIT
import com.example.normpix.finance.domain.RepeatEnd
import com.example.normpix.finance.domain.RepeatEndKind
import com.example.normpix.finance.domain.RepeatStep
import com.example.normpix.finance.domain.kind
import com.example.normpix.finance.domain.notBefore
import com.example.normpix.finance.domain.repeatRunThrough
import com.example.normpix.finance.domain.resolve
import com.example.normpix.finance.domain.switchedTo
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime

/** What one occurrence of a repeat is called, and what its last-date field says: "payment" for money, "time" for a reminder. */
@Immutable
class RepeatNoun(val one: String, val many: String, val lastBy: String) {
    /** "1 payment", "12 payments". */
    fun count(n: Int): String = if (n == 1) "$n $one" else "$n $many"
}

internal val PaymentNoun = RepeatNoun(one = "payment", many = "payments", lastBy = "Last payment by")

/**
 * The line that says what an end comes to, or null for no end:
 * - after a count: "After 12 payments · last on 5 Oct 2027";
 * - on a day: "12 payments · last on 1 Oct 2027" (the last one that falls on or before the day, which may be earlier than it),
 *   "Just the first payment" when the day comes before the second, and "1000+ payments" when there are too many to count.
 *
 * [start] is the first occurrence and [next] the step to the following one. Dates read as everywhere else ([shortDate]).
 */
internal fun repeatEndSummary(
    ends: RepeatEnd,
    start: LocalDateTime,
    next: RepeatStep,
    noun: RepeatNoun,
    now: LocalDateTime = LocalDateTime.now(),
): String? = when (ends) {
    RepeatEnd.Never -> null
    is RepeatEnd.After -> {
        val count = ends.count.coerceIn(1, MAX_REPEAT_COUNT)
        val last = ends.resolve(start, next)
        "After ${noun.count(count)}" + (last?.let { " · last on ${shortDate(it, now)}" } ?: "")
    }
    is RepeatEnd.OnDate -> {
        val run = repeatRunThrough(start, ends.date, next)
        when {
            run.capped -> "$REPEAT_COUNT_LIMIT+ ${noun.many}"
            run.count <= 1 -> "Just the first ${noun.one}"
            else -> "${noun.count(run.count)} · last on ${run.last?.let { shortDate(it, now) }.orEmpty()}"
        }
    }
}

/**
 * "Ends": how a repeat stops, in one place for every editor that has a repeat - Never, On a date or After a number of times,
 * Never being what it starts as. A date is picked with the date sheet; a number with a stepper that starts at the same 12 a
 * switch from "On a date" would give if the date held that many. Under the choice, the line that says what it comes to
 * ([repeatEndSummary]), which a screen reader hears again as it changes.
 *
 * [start] is the first occurrence (an end can't come before it) and [next] the step from one occurrence to the next, which
 * is what turns a number of times into the day of the last one.
 */
@Composable
internal fun RepeatEndField(
    ends: RepeatEnd,
    onChange: (RepeatEnd) -> Unit,
    start: LocalDateTime,
    next: RepeatStep,
    noun: RepeatNoun,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var pickingDate by remember { mutableStateOf(false) }
    val options = remember {
        listOf(
            ChoiceOption(RepeatEndKind.Never, "Never", Icons.Outlined.AllInclusive),
            ChoiceOption(RepeatEndKind.OnDate, "On a date", Icons.Outlined.Event),
            ChoiceOption(RepeatEndKind.After, "After", Icons.Outlined.EventRepeat),
        )
    }
    val summary = remember(ends, start, next) { repeatEndSummary(ends, start, next, noun) }

    Column(modifier.fillMaxWidth().animateContentSize(AppMotion.spatial())) {
        Text("Ends", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpacing.sm))
        ConnectedChoiceRow(options, selected = ends.kind, onSelect = { onChange(ends.switchedTo(it, start, next)) })
        when (ends) {
            RepeatEnd.Never -> Unit
            is RepeatEnd.OnDate -> {
                Spacer(Modifier.height(AppSpacing.md))
                PickerFieldRow(PickerField(noun.lastBy, shortDate(ends.date.atStartOfDay())) { pickingDate = true })
            }
            is RepeatEnd.After -> {
                Spacer(Modifier.height(AppSpacing.md))
                CountStepper(
                    count = ends.count,
                    noun = noun,
                    min = MIN_REPEAT_COUNT,
                    max = MAX_REPEAT_COUNT,
                    onChanged = {
                        haptics.selection()
                        onChange(RepeatEnd.After(it))
                    },
                )
            }
        }
        if (summary != null) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = AppSpacing.lg, top = AppSpacing.sm, end = AppSpacing.lg)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    if (pickingDate && ends is RepeatEnd.OnDate) {
        DatePickerDialogFor(
            initial = ends.date,
            onPicked = { date ->
                pickingDate = false
                onChange(RepeatEnd.OnDate(date).notBefore(start.toLocalDate()))
            },
            onDismiss = { pickingDate = false },
            yearsAhead = 50,
        )
    }
}

/** "[-] 12 [+] payments": a whole number of times between [min] and [max], with the word that goes with it. */
@Composable
private fun CountStepper(count: Int, noun: RepeatNoun, min: Int, max: Int, onChanged: (Int) -> Unit) {
    val text = MaterialTheme.typography
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = { onChanged((count - 1).coerceAtLeast(min)) }, enabled = count > min) {
            Icon(Icons.Rounded.Remove, contentDescription = "Fewer")
        }
        Text(
            "$count",
            modifier = Modifier
                .width(56.dp)
                .clearAndSetSemantics {
                    contentDescription = noun.count(count)
                    liveRegion = LiveRegionMode.Polite
                },
            textAlign = TextAlign.Center,
            style = text.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
        )
        OutlinedIconButton(onClick = { onChanged((count + 1).coerceAtMost(max)) }, enabled = count < max) {
            Icon(Icons.Rounded.Add, contentDescription = "More")
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Text(if (count == 1) noun.one else noun.many, style = text.bodyLarge, modifier = Modifier.clearAndSetSemantics {})
    }
}
