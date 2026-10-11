package com.example.normpix.availability.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.example.normpix.availability.Availability
import com.example.normpix.availability.AvailabilityMode
import com.example.normpix.availability.AvailabilityRule
import com.example.normpix.availability.AvailabilityUi
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.WEEKDAYS
import com.example.normpix.availability.WEEKEND
import com.example.normpix.availability.WeeklySchedule
import com.example.normpix.availability.describeDays
import com.example.normpix.availability.describeWindow
import com.example.normpix.availability.formatTime
import com.example.normpix.availability.summary
import com.example.normpix.finance.ui.TimePickerDialogFor
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.StatusTag
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

/** The icon that stands for [feature] wherever its availability is shown. */
val ScheduledFeature.icon: ImageVector
    get() = when (this) {
        ScheduledFeature.NfcExchange -> Icons.Rounded.Contactless
    }

/**
 * One row that answers "can I use this right now, and why / why not?" for a [ScheduledFeature]: its name,
 * the plain-language state ("Available today from 6:00 PM to 8:00 PM."), and a tag that says it in words so
 * the state never rests on color alone. Put it inside a [ListSection]; pass [onClick] to make it a shortcut
 * to the schedule.
 */
@Composable
fun AvailabilityStatusRow(
    ui: AvailabilityUi,
    modifier: Modifier = Modifier,
    headline: String = ui.feature.label,
    onClick: (() -> Unit)? = null,
) {
    val (tag, tone, icon) = ui.availability.badge()
    ListRow(
        headline = headline,
        supporting = ui.message,
        supportingMaxLines = 3,
        modifier = modifier,
        leading = { LeadingIcon(ui.feature.icon) },
        trailing = { StatusTag(tag, tone = tone, icon = icon) },
        onClick = onClick,
    )
}

private fun Availability.badge(): Triple<String, StatusTone, ImageVector> = when (this) {
    is Availability.Open -> Triple("Available", StatusTone.Success, Icons.Rounded.CheckCircle)
    Availability.TurnedOff -> Triple("Off", StatusTone.Neutral, Icons.Rounded.Block)
    Availability.NoDays -> Triple("Needs days", StatusTone.Warning, Icons.Outlined.EventBusy)
    is Availability.Waiting -> Triple("Not now", StatusTone.Warning, Icons.Outlined.Schedule)
}

/**
 * The editable schedule for one feature: a mode choice (always / on a schedule / off) and, for a schedule,
 * the days and the start and end times. Every change is reported at once through [onChange] - there is no
 * Save step - and [preview] (the "what this means right now" sentence) updates beneath it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditor(
    rule: AvailabilityRule,
    featureName: String,
    is24Hour: Boolean,
    onChange: (AvailabilityRule) -> Unit,
    modifier: Modifier = Modifier,
    preview: String? = null,
) {
    val haptics = rememberAppHaptics()
    var pickingTime by remember { mutableStateOf<TimeField?>(null) }
    val schedule = rule.schedule

    Column(modifier) {
        ListSection(title = "When $featureName works", modifier = Modifier.selectableGroup()) {
            for (mode in AvailabilityMode.entries) {
                item {
                    RadioRow(
                        headline = mode.label,
                        supporting = when (mode) {
                            AvailabilityMode.Always -> "No restriction."
                            AvailabilityMode.Scheduled -> if (rule.mode == mode) rule.summary(is24Hour) else "Only on the days and times you choose."
                            AvailabilityMode.Off -> "Never: it stays disabled until you turn it back on."
                        },
                        selected = rule.mode == mode,
                        onClick = {
                            if (rule.mode != mode) haptics.selection()
                            onChange(rule.copy(mode = mode))
                        },
                    )
                }
            }
        }

        RevealVisibility(visible = rule.mode == AvailabilityMode.Scheduled) {
            Column {
                ListSection(title = "Days", footer = describeDays(schedule.days)) {
                    item {
                        Column(Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            ) {
                                val locale = LocalConfiguration.current.locales[0]
                                for (day in DayOfWeek.entries) {
                                    val selected = day in schedule.days
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            haptics.selection()
                                            val days = if (selected) schedule.days - day else schedule.days + day
                                            onChange(rule.copy(schedule = schedule.copy(days = days)))
                                        },
                                        label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
                                        leadingIcon = if (selected) {
                                            { Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                        } else {
                                            null
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = day.getDisplayName(TextStyle.FULL, locale)
                                        },
                                    )
                                }
                            }
                            DayPresets(schedule.days) { onChange(rule.copy(schedule = schedule.copy(days = it))) }
                        }
                    }
                }
                ListSection(title = "Hours", footer = timeFooter(schedule)) {
                    item {
                        ListRow(
                            headline = "Starts",
                            supporting = formatTime(schedule.start, is24Hour),
                            leading = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                            onClick = { pickingTime = TimeField.Start },
                        )
                    }
                    item {
                        ListRow(
                            headline = "Ends",
                            supporting = formatTime(schedule.end, is24Hour) + if (schedule.crossesMidnight) " (next day)" else "",
                            leading = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                            onClick = { pickingTime = TimeField.End },
                        )
                    }
                }
            }
        }

        if (preview != null) {
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.md, end = AppSpacing.lg),
            )
        }
    }

    pickingTime?.let { field ->
        val initial = if (field == TimeField.Start) schedule.start else schedule.end
        TimePickerDialogFor(
            initial = LocalDate.now().atTime(initial),
            onPicked = { hour, minute ->
                val time = LocalTime.of(hour, minute)
                val updated = if (field == TimeField.Start) schedule.copy(start = time) else schedule.copy(end = time)
                onChange(rule.copy(schedule = updated))
                pickingTime = null
            },
            onDismiss = { pickingTime = null },
        )
    }
}

private enum class TimeField { Start, End }

private fun timeFooter(schedule: WeeklySchedule): String = when {
    schedule.isAllDay -> "Same start and end time means all day."
    schedule.crossesMidnight -> "Ends after midnight, so it runs into the next day."
    else -> describeWindow(schedule)
}

@Composable
private fun DayPresets(days: Set<DayOfWeek>, onPick: (Set<DayOfWeek>) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        for ((label, set) in listOf("Every day" to WeeklySchedule.EVERY_DAY, "Weekdays" to WEEKDAYS, "Weekends" to WEEKEND)) {
            TextButton(onClick = { onPick(set) }, enabled = days != set) { Text(label) }
        }
    }
}

/**
 * What a feature shows instead of itself while its [AvailabilityUi] says no: a plain headline ("NFC is
 * unavailable right now."), the reason and the next opening ("Available today from 6:00 PM to 8:00 PM."),
 * and the way to change it. Nothing behind it is running. [extra] adds an alternative that does not
 * depend on the feature.
 */
@Composable
fun FeatureUnavailablePanel(
    availability: AvailabilityUi,
    title: String,
    onOpenSchedule: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        LeadingIcon(Icons.Outlined.Schedule, size = 72.dp)
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            availability.message,
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            "Nothing is listening or sharing until then.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AppSpacing.xl))
        if (onOpenSchedule != null) {
            FilledTonalButton(onClick = onOpenSchedule, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Open schedule settings")
            }
        } else {
            Text(
                "You can change this in Settings, under Availability & Schedules.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        extra?.invoke()
        TextButton(onClick = onClose) { Text("Close") }
    }
}
