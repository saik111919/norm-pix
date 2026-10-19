package com.example.normpix.finance.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.ui.components.InnerCorner
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.SplitGap
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * While this is in composition, the [DateRangeSheet] is up to pick days, as [MonthSwitcher] brings up its month sheet:
 * [onPicked] gets the whole days chosen (from the start of the first to the end of the last), [onDismiss] is called when it
 * is closed without. [latest] is the last day it offers; null offers any.
 */
@Composable
fun DateRangePickerFor(initial: DateRange?, onPicked: (DateRange) -> Unit, onDismiss: () -> Unit, latest: LocalDate? = null) {
    val overlays = LocalOverlays.current
    val picked by rememberUpdatedState(onPicked)
    val dismissed by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        val range = overlays.sheet<DateRange>(expandable = true) { done -> DateRangeSheet(initial, latest, onPick = done) }
        if (range != null) picked(range) else dismissed()
    }
}

/** A ready-made run of days, ending today: one tap instead of two on the calendar. */
internal data class DayPreset(val label: String, val first: LocalDate, val last: LocalDate)

internal fun dayPresets(today: LocalDate, firstDayOfWeek: DayOfWeek): List<DayPreset> = listOf(
    DayPreset("Last 7 days", today.minusDays(6), today),
    DayPreset("This week", today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)), today),
    DayPreset("Last 30 days", today.minusDays(29), today),
    DayPreset("Last 90 days", today.minusDays(89), today),
    DayPreset("This year", today.withDayOfYear(1), today),
)

/** The whole days from the start of [first] to the end of [last]. */
internal fun wholeDays(first: LocalDate, last: LocalDate): DateRange = DateRange(first.atStartOfDay(), last.plusDays(1).atStartOfDay())

/**
 * Days picked from a sheet, in the month picker's family: the dates chosen as a connected From | To pair (the one the next
 * tap sets ringed; tap either to set it again), ready-made runs of days, then a month at a time - the month with a step
 * either side, its days as round tiles under the weekdays, the first and last day filled and the days between joined by a
 * band, today ringed, days after [latest] not offered. A tap sets the first day, the next the last (one before the first
 * starts there instead); the same day twice is that one day. Calls [onPick] with the days, or null.
 */
@Composable
fun DateRangeSheet(initial: DateRange?, latest: LocalDate?, onPick: (DateRange?) -> Unit, today: LocalDate = LocalDate.now()) {
    val haptics = rememberAppHaptics()
    // The phone's calendar, read so that a change to its language or region shows at once.
    val firstDayOfWeek = rememberFirstDayOfWeek()
    val presets = remember(today, latest, firstDayOfWeek) {
        dayPresets(if (latest != null && latest.isBefore(today)) latest else today, firstDayOfWeek)
    }

    var start by remember { mutableStateOf(initial?.start?.toLocalDate()) }
    var end by remember { mutableStateOf(initial?.let { it.end.minusDays(1).toLocalDate() }) }
    // Whether the next tap sets the last day; otherwise it starts again from a first day.
    var choosingLast by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(YearMonth.from(end ?: start ?: latest ?: today)) }

    fun pick(day: LocalDate) {
        haptics.selection()
        val first = start
        if (!choosingLast || first == null) {
            start = day
            end = null
            choosingLast = true
        } else if (day.isBefore(first)) {
            start = day
        } else {
            end = day
            choosingLast = false
        }
    }

    val first = start
    val last = end ?: first
    val outer = CornerSize(50)
    val inner = CornerSize(InnerCorner)

    Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xl)) {
        SheetHeader(
            title = "Choose dates",
            subtitle = initial?.let { "Showing ${rangeLabel(it)}" } ?: "Tap the first day, then the last",
        )
        Spacer(Modifier.height(AppSpacing.lg))

        // From | To: what is chosen so far, the end the next tap sets ringed.
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
            EndSegment(
                label = "From",
                value = first?.let(::dayLabel) ?: "Pick a day",
                active = !choosingLast,
                shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
                onSelect = {
                    haptics.selection()
                    choosingLast = false
                },
            )
            EndSegment(
                label = "To",
                value = when {
                    end != null -> dayLabel(end!!)
                    first != null && !choosingLast -> dayLabel(first)
                    else -> "Pick a day"
                },
                active = choosingLast,
                shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
                onSelect = {
                    if (first != null) {
                        haptics.selection()
                        choosingLast = true
                    }
                },
            )
        }
        Spacer(Modifier.height(AppSpacing.md))

        // Ready-made runs of days, as the month sheet's tiles: the one chosen filled.
        QuickTileRow {
            for (preset in presets) {
                QuickTile(
                    label = preset.label,
                    spoken = "${preset.label}, ${dayLabel(preset.first)} to ${dayLabel(preset.last)}",
                    selected = first == preset.first && last == preset.last && !choosingLast,
                    onPick = {
                        haptics.selection()
                        start = preset.first
                        end = preset.last
                        choosingLast = false
                        shown = YearMonth.from(preset.last)
                    },
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))

        // The month with its days, shared with the single-day sheet.
        MonthCalendar(
            shown = shown,
            onShown = { shown = it },
            first = first,
            last = if (choosingLast && end == null) null else last,
            today = today,
            latest = latest,
            onPick = ::pick,
        )

        Spacer(Modifier.height(AppSpacing.lg))
        val days = if (first != null && last != null) ChronoUnit.DAYS.between(first, last).toInt() + 1 else 0
        Button(
            onClick = {
                if (first != null && last != null) {
                    haptics.selection()
                    onPick(wholeDays(first, last))
                }
            },
            enabled = first != null,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(
                if (days > 0) "Show ${daysLabel(days)}" else "Show these days",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** One end of the range, as half of a connected pair: what it is, the day, and a ring while the next tap sets it. */
@Composable
private fun RowScope.EndSegment(label: String, value: String, active: Boolean, shape: RoundedCornerShape, onSelect: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (active) scheme.primary else Color.Transparent, label = "range_end_ring")
    Surface(
        onClick = onSelect,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription = if (active) "$label, $value, the next day you tap" else "$label, $value"
                role = Role.Button
                selected = active
                onClick(label = "Choose the ${label.lowercase()} day") { onSelect(); true }
            },
        shape = shape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        border = BorderStroke(2.dp, ring),
    ) {
        Column(Modifier.padding(horizontal = AppSpacing.lg), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer.copy(alpha = 0.78f), maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
