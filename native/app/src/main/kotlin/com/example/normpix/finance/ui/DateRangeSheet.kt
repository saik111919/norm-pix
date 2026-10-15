package com.example.normpix.finance.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.ui.components.InnerCorner
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.SplitGap
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

private val StepWidth = 48.dp
private val DayHeight = 44.dp
private val DayCircle = 40.dp
private val DayBand = 36.dp

/** The earliest month the calendar goes back to. */
private val FirstMonth = YearMonth.of(2000, 1)

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
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }
    val presets = remember(today, latest) { dayPresets(if (latest != null && latest.isBefore(today)) latest else today, firstDayOfWeek) }
    val lastMonth = YearMonth.from(latest ?: today.plusYears(10))

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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            for (preset in presets) {
                PresetTile(
                    preset = preset,
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

        // The month, with a step either side: the month sheet's year control.
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
            val back = shown.isAfter(FirstMonth)
            Surface(
                onClick = {
                    haptics.selection()
                    shown = shown.minusMonths(1)
                },
                enabled = back,
                modifier = Modifier.width(StepWidth).fillMaxHeight().semantics { contentDescription = "Previous month" },
                shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
                color = scheme.surfaceContainerHighest,
                contentColor = if (back) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) } }
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { heading() },
                shape = RoundedCornerShape(inner),
                color = scheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(monthTitle(shown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            val forward = shown.isBefore(lastMonth)
            Surface(
                onClick = {
                    haptics.selection()
                    shown = shown.plusMonths(1)
                },
                enabled = forward,
                modifier = Modifier.width(StepWidth).fillMaxHeight().semantics { contentDescription = "Next month" },
                shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
                color = scheme.surfaceContainerHighest,
                contentColor = if (forward) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChevronRight, contentDescription = null) } }
        }
        Spacer(Modifier.height(AppSpacing.md))

        // The weekdays, in the order this phone's calendar starts its week. Each day says its own name to a screen reader.
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
            for (i in 0 until 7) {
                Text(
                    firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.xs))

        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                if (reduced) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    // A later month comes in from the right, an earlier one from the left, like the pages of a calendar.
                    val direction = if (targetState.isBefore(initialState)) -1 else 1
                    (
                        slideInHorizontally(tween(AppMotion.MEDIUM_MS, easing = AppMotion.enter)) { direction * it / 4 } +
                            fadeIn(tween(AppMotion.MEDIUM_MS, easing = AppMotion.enter))
                        ) togetherWith (
                        slideOutHorizontally(tween(AppMotion.SHORT_MS, easing = AppMotion.exit)) { -direction * it / 4 } +
                            fadeOut(tween(AppMotion.SHORT_MS, easing = AppMotion.exit))
                        ) using SizeTransform(clip = false)
                }
            },
            label = "range_month",
        ) { month ->
            MonthDays(
                month = month,
                first = first,
                last = if (choosingLast && end == null) null else last,
                today = today,
                latest = latest,
                firstDayOfWeek = firstDayOfWeek,
                onPick = ::pick,
            )
        }

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

@Composable
private fun PresetTile(preset: DayPreset, selected: Boolean, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // The run chosen morphs to a full pill, as the month showing does in the month sheet.
    val corner by animateDpAsState(if (selected) 20.dp else AppRadius.md, AppMotion.spatial<Dp>(), label = "preset_corner")
    Surface(
        selected = selected,
        onClick = onPick,
        modifier = Modifier
            .height(40.dp)
            .clearAndSetSemantics {
                contentDescription = "${preset.label}, ${dayLabel(preset.first)} to ${dayLabel(preset.last)}"
                role = Role.RadioButton
                this.selected = selected
                onClick { onPick(); true }
            },
        shape = RoundedCornerShape(corner),
        color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
    ) {
        Box(Modifier.padding(horizontal = AppSpacing.lg), contentAlignment = Alignment.Center) {
            Text(
                preset.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/**
 * [month]'s days under the weekdays, always six weeks tall so the sheet keeps its height from month to month. [last] null
 * while the last day is still to be tapped.
 */
@Composable
private fun MonthDays(
    month: YearMonth,
    first: LocalDate?,
    last: LocalDate?,
    today: LocalDate,
    latest: LocalDate?,
    firstDayOfWeek: DayOfWeek,
    onPick: (LocalDate) -> Unit,
) {
    val lead = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val length = month.lengthOfMonth()
    Column(Modifier.fillMaxWidth()) {
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val n = week * 7 + column - lead + 1
                    if (n in 1..length) {
                        val day = month.atDay(n)
                        DayTile(
                            day = day,
                            first = first,
                            last = last,
                            today = day == today,
                            enabled = latest == null || !day.isAfter(latest),
                            onPick = { onPick(day) },
                        )
                    } else {
                        Spacer(Modifier.weight(1f).height(DayHeight))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayTile(day: LocalDate, first: LocalDate?, last: LocalDate?, today: Boolean, enabled: Boolean, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val isFirst = day == first
    val isLast = day == last
    val ends = isFirst || isLast
    val spans = first != null && last != null && last.isAfter(first)
    val between = first != null && last != null && day.isAfter(first) && day.isBefore(last)
    val spoken = buildString {
        append(SpokenDay.format(day))
        when {
            isFirst && isLast -> append(", chosen")
            isFirst -> append(", first day")
            isLast -> append(", last day")
            between -> append(", in the range")
        }
        if (today) append(", today")
    }
    Box(
        Modifier
            .weight(1f)
            .height(DayHeight)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                selected = ends || between
                if (enabled) onClick { onPick(); true } else disabled()
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = DayCircle / 2),
                enabled = enabled,
                onClick = onPick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The band joining the first day to the last: across the days between, and from the middle of each end inwards.
        if (spans && (between || ends)) {
            val band = when {
                isFirst -> Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.5f)
                isLast -> Modifier.align(Alignment.CenterStart).fillMaxWidth(0.5f)
                else -> Modifier.fillMaxWidth()
            }
            Box(band.height(DayBand).background(scheme.secondaryContainer))
        }
        Box(
            Modifier
                .size(DayCircle)
                .clip(CircleShape)
                .background(if (ends) scheme.primary else Color.Transparent)
                .then(if (today && !ends) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (ends || today) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    ends -> scheme.onPrimary
                    !enabled -> scheme.onSurface.copy(alpha = 0.38f)
                    between -> scheme.onSecondaryContainer
                    else -> scheme.onSurface
                },
            )
        }
    }
}

private val SpokenDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
private val MonthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

private fun monthTitle(month: YearMonth): String = MonthTitle.format(month)

/** "3 Oct", or "3 Oct 2025" for another year. */
private fun dayLabel(day: LocalDate): String = shortDate(day.atStartOfDay())
