package com.example.normpix.finance.ui

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/*
 * Days and times are picked from sheets, all in the family of the Insights date range: a header, the choice so far as a
 * connected pair, ready-made picks as tiles, round tiles to set the exact value, and one full-width button. The sheets for
 * a single day ([DateSheet]) and a time ([TimeSheet]) are here with the calendar they share with the range sheet
 * ([MonthCalendar]); [DatePickerDialogFor] and [TimePickerDialogFor] open them for every form that asks for a date or a
 * time, and [PickerFieldRow] is the connected pair of fields those forms show.
 */

private val StepWidth = 48.dp
private val DayHeight = 44.dp
private val DayCircle = 40.dp
private val DayBand = 36.dp

/** As tall as the search bar, the split buttons and the Insights range control, so every date and time control lines up. */
private val PickerHeight = 56.dp

/** The earliest month the calendar goes back to. */
private val FirstMonth = YearMonth.of(2000, 1)

// ---------------------------------------------------------------------------------------------------------------------
// Opening the sheets
// ---------------------------------------------------------------------------------------------------------------------

/**
 * While this is in composition, the [DateSheet] is up to pick a day, as [DateRangePickerFor] brings up the range sheet:
 * [onPicked] gets the day chosen, [onDismiss] is called when it is closed without. The last day it offers is the end of the
 * year [yearsAhead] years from now. It opens over whatever is open (the transaction editor is a sheet too).
 */
@Composable
fun DatePickerDialogFor(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit, yearsAhead: Int = 10) {
    val overlays = LocalOverlays.current
    val picked by rememberUpdatedState(onPicked)
    val dismissed by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        val latest = LocalDate.of(LocalDate.now().year + yearsAhead, 12, 31)
        val date = overlays.sheet<LocalDate>(expandable = true) { done -> DateSheet(initial, latest, onPick = done) }
        if (date != null) picked(date) else dismissed()
    }
}

/**
 * While this is in composition, the [TimeSheet] is up to pick a time of day, in the phone's 12 or 24-hour style: [onPicked]
 * gets the hour (0-23) and minute, [onDismiss] is called when it is closed without. [initial]'s date is not used.
 */
@Composable
fun TimePickerDialogFor(initial: LocalDateTime, onPicked: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val overlays = LocalOverlays.current
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val picked by rememberUpdatedState(onPicked)
    val dismissed by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        val time = overlays.sheet<LocalTime>(expandable = true) { done -> TimeSheet(initial.toLocalTime(), is24Hour, onPick = done) }
        if (time != null) picked(time.hour, time.minute) else dismissed()
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// A day
// ---------------------------------------------------------------------------------------------------------------------

/** A ready-made day: one tap instead of a trip through the calendar. */
internal class DatePick(val label: String, val date: LocalDate)

/** The days offered as quick picks: today and yesterday, and - where days to come may be chosen - tomorrow and a week on. */
internal fun datePicks(today: LocalDate, latest: LocalDate?): List<DatePick> = listOf(
    DatePick("Today", today),
    DatePick("Yesterday", today.minusDays(1)),
    DatePick("Tomorrow", today.plusDays(1)),
    DatePick("In a week", today.plusWeeks(1)),
).filter { latest == null || !it.date.isAfter(latest) }

/**
 * One day picked from a sheet, in the range sheet's family: quick picks as tiles, then a month at a time - the month with a
 * step either side, its days as round tiles under the weekdays, the chosen day filled, today ringed, days after [latest]
 * not offered - and a button that uses the day. A tap on a day only chooses it; the button returns it. Calls [onPick] with
 * the day, or null when the sheet is closed without.
 */
@Composable
fun DateSheet(initial: LocalDate, latest: LocalDate?, onPick: (LocalDate?) -> Unit, today: LocalDate = LocalDate.now()) {
    val haptics = rememberAppHaptics()
    val picks = remember(today, latest) { datePicks(today, latest) }
    var chosen by remember { mutableStateOf(initial) }
    var shown by remember { mutableStateOf(YearMonth.from(initial)) }

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = AppSpacing.xxl, end = AppSpacing.xxl),
        ) {
            SheetHeader(title = "Choose a date", subtitle = "Currently ${dayLabel(initial)}")
            Spacer(Modifier.height(AppSpacing.lg))

            QuickTileRow {
                for (pick in picks) {
                    QuickTile(
                        label = pick.label,
                        spoken = "${pick.label}, ${dayLabel(pick.date)}",
                        selected = chosen == pick.date,
                        onPick = {
                            haptics.selection()
                            chosen = pick.date
                            shown = YearMonth.from(pick.date)
                        },
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))

            MonthCalendar(
                shown = shown,
                onShown = { shown = it },
                first = chosen,
                last = chosen,
                today = today,
                latest = latest,
                onPick = {
                    haptics.selection()
                    chosen = it
                },
            )
        }
        SheetButton("Use ${dayLabel(chosen)}") {
            haptics.selection()
            onPick(chosen)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// A time
// ---------------------------------------------------------------------------------------------------------------------

/** A ready-made time of day. */
internal class TimePick(val label: String, val time: LocalTime)

/** The times offered as quick picks: now (to the minute), and the usual times of day. */
internal fun timePicks(now: LocalTime): List<TimePick> = listOf(
    TimePick("Now", now.withSecond(0).withNano(0)),
    TimePick("Morning", LocalTime.of(9, 0)),
    TimePick("Noon", LocalTime.NOON),
    TimePick("Evening", LocalTime.of(18, 0)),
    TimePick("Night", LocalTime.of(21, 0)),
)

/** Which half of the time the tiles under the pair are setting. */
private enum class TimePart { Hour, Minute }

private val TimeHeight = 88.dp
private val PeriodWidth = 76.dp

/**
 * A time of day picked from a sheet, in the range sheet's family: the time as a large connected Hour | Minute pair (the one
 * the tiles below set ringed; tap either to set it), with AM | PM beside it when the phone uses 12-hour time ([is24Hour]
 * false), quick picks as tiles, and round tiles for the exact hour or minute - five minutes apart, with a step of one
 * either way. Calls [onPick] with the time, or null when the sheet is closed without. [now] is what "Now" picks.
 */
@Composable
fun TimeSheet(initial: LocalTime, is24Hour: Boolean, onPick: (LocalTime?) -> Unit, now: LocalTime = LocalTime.now()) {
    val haptics = rememberAppHaptics()
    val reduced = rememberReducedMotion()
    val picks = remember { timePicks(now) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    var editing by remember { mutableStateOf(TimePart.Hour) }
    val afternoon = hour >= 12
    val shownHour = if (is24Hour) hour else ((hour + 11) % 12) + 1
    val outer = CornerSize(50)
    val inner = CornerSize(InnerCorner)
    val big = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = TABULAR_FIGURES)

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = AppSpacing.xxl, end = AppSpacing.xxl),
        ) {
            SheetHeader(title = "Choose a time", subtitle = "Currently ${timeText(initial, is24Hour)}")
            Spacer(Modifier.height(AppSpacing.lg))

            // The time: hour and minute as one connected pair, the part the tiles below set ringed.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
                    PickerSegment(
                        label = "Hour",
                        value = two(shownHour),
                        shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
                        onSelect = {
                            haptics.selection()
                            editing = TimePart.Hour
                        },
                        modifier = Modifier.heightIn(min = TimeHeight),
                        active = editing == TimePart.Hour,
                        spoken = "Hour, $shownHour",
                        actionLabel = "Set the hour",
                        valueStyle = big,
                    )
                    PickerSegment(
                        label = "Minute",
                        value = two(minute),
                        shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
                        onSelect = {
                            haptics.selection()
                            editing = TimePart.Minute
                        },
                        modifier = Modifier.heightIn(min = TimeHeight),
                        active = editing == TimePart.Minute,
                        spoken = "Minute, $minute",
                        actionLabel = "Set the minute",
                        valueStyle = big,
                    )
                }
                if (!is24Hour) {
                    PeriodToggle(
                        afternoon = afternoon,
                        onChange = { pm ->
                            haptics.selection()
                            hour = if (pm) hour % 12 + 12 else hour % 12
                        },
                        modifier = Modifier.width(PeriodWidth).fillMaxHeight(),
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.md))

            QuickTileRow {
                for (pick in picks) {
                    val spoken = "${pick.label}, ${timeText(pick.time, is24Hour)}"
                    QuickTile(
                        label = if (pick.label == "Now") pick.label else "${pick.label} · ${timeText(pick.time, is24Hour)}",
                        spoken = spoken,
                        selected = hour == pick.time.hour && minute == pick.time.minute,
                        onPick = {
                            haptics.selection()
                            hour = pick.time.hour
                            minute = pick.time.minute
                        },
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))

            AnimatedContent(
                targetState = editing,
                transitionSpec = {
                    if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(AppMotion.MEDIUM_MS, easing = AppMotion.enter)) togetherWith
                            fadeOut(tween(AppMotion.SHORT_MS, easing = AppMotion.exit)) using SizeTransform(clip = false)
                    }
                },
                label = "time_part",
            ) { part ->
                when (part) {
                    TimePart.Hour -> HourTiles(
                        shown = shownHour,
                        is24Hour = is24Hour,
                        onPick = { value ->
                            haptics.selection()
                            hour = if (is24Hour) value else value % 12 + if (afternoon) 12 else 0
                            // The hour is the quick half: on to the minute, as the clock does.
                            editing = TimePart.Minute
                        },
                    )
                    TimePart.Minute -> MinuteTiles(
                        minute = minute,
                        onPick = { value ->
                            haptics.selection()
                            minute = value
                        },
                    )
                }
            }
        }
        SheetButton("Use ${timeText(LocalTime.of(hour, minute), is24Hour)}") {
            haptics.selection()
            onPick(LocalTime.of(hour, minute))
        }
    }
}

/** AM | PM as a connected pair, one above the other, the one chosen filled. */
@Composable
private fun PeriodToggle(afternoon: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val outer = CornerSize(50)
    val inner = CornerSize(InnerCorner)
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(SplitGap)) {
        PeriodSegment(
            label = "AM",
            selected = !afternoon,
            shape = RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner),
            onPick = { onChange(false) },
        )
        PeriodSegment(
            label = "PM",
            selected = afternoon,
            shape = RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer),
            onPick = { onChange(true) },
        )
    }
}

@Composable
private fun ColumnScope.PeriodSegment(label: String, selected: Boolean, shape: Shape, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        selected = selected,
        onClick = onPick,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.RadioButton
                this.selected = selected
                onClick { onPick(); true }
            },
        shape = shape,
        color = if (selected) scheme.primary else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimary else scheme.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/** The hours as round tiles, six to a row: 1 to 12 for a 12-hour clock ([shown] the one now set), 0 to 23 for a 24-hour one. */
@Composable
private fun HourTiles(shown: Int, is24Hour: Boolean, onPick: (Int) -> Unit) {
    val values = if (is24Hour) (0..23).toList() else (1..12).toList()
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        for (row in values.chunked(TILES_PER_ROW)) {
            Row(Modifier.fillMaxWidth()) {
                for (value in row) {
                    NumberTile(
                        text = if (is24Hour) two(value) else "$value",
                        spoken = "Hour $value",
                        selected = value == shown,
                        onPick = { onPick(value) },
                    )
                }
            }
        }
    }
}

/** The minutes, five apart, as round tiles, and under them a step of one minute either way for the ones between. */
@Composable
private fun MinuteTiles(minute: Int, onPick: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val outer = CornerSize(50)
    val inner = CornerSize(InnerCorner)
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().selectableGroup()) {
            for (row in (0..55 step 5).toList().chunked(TILES_PER_ROW)) {
                Row(Modifier.fillMaxWidth()) {
                    for (value in row) {
                        NumberTile(text = two(value), spoken = "Minute $value", selected = value == minute, onPick = { onPick(value) })
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
            Surface(
                onClick = { onPick((minute + 59) % 60) },
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "One minute earlier" },
                shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
                color = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurface,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Remove, contentDescription = null)
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("1 min", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
            Surface(
                onClick = { onPick((minute + 1) % 60) },
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "One minute later" },
                shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
                color = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurface,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("1 min", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private const val TILES_PER_ROW = 6

/** A number on a round tile, the one chosen filled: the calendar's day tile for hours and minutes. */
@Composable
private fun RowScope.NumberTile(text: String, spoken: String, selected: Boolean, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .weight(1f)
            .height(DayHeight)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.RadioButton
                this.selected = selected
                onClick { onPick(); true }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = DayCircle / 2),
                onClick = onPick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(DayCircle)
                .clip(CircleShape)
                .background(if (selected) scheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.onPrimary else scheme.onSurface,
            )
        }
    }
}

/** "09", "30": two digits, for the pair and the tiles of a 24-hour clock. */
private fun two(number: Int): String = if (number < 10) "0$number" else "$number"

/** [time] the way the phone writes it: "6:30 PM", or "18:30" on a 24-hour clock. */
internal fun timeText(time: LocalTime, is24Hour: Boolean): String = timeLabel(time.atDate(LocalDate.of(2000, 1, 1)), is24Hour)

// ---------------------------------------------------------------------------------------------------------------------
// The calendar, shared with the range sheet
// ---------------------------------------------------------------------------------------------------------------------

/**
 * The calendar the day and range sheets share: the month with a step either side, its days as round tiles under the weekdays
 * (always six weeks tall, so a sheet keeps its height from month to month), changing month with a slide. The days from
 * [first] to [last] are filled at the ends and joined by a band between ([last] null while the last day is still to be
 * tapped; the same day for both is one chosen day), [today] is ringed, days after [latest] are not offered. [shown] is the
 * month on screen, changed through [onShown]; a tap on a day calls [onPick].
 */
@Composable
internal fun MonthCalendar(
    shown: YearMonth,
    onShown: (YearMonth) -> Unit,
    first: LocalDate?,
    last: LocalDate?,
    today: LocalDate,
    latest: LocalDate?,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    // The phone's language and region, read so that a change to them shows at once.
    val locale = LocalConfiguration.current.locales[0]
    val firstDayOfWeek = rememberFirstDayOfWeek()
    val lastMonth = YearMonth.from(latest ?: today.plusYears(10))
    val outer = CornerSize(50)
    val inner = CornerSize(InnerCorner)

    Column(modifier.fillMaxWidth()) {
        // The month, with a step either side: the month sheet's year control.
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
            val back = shown.isAfter(FirstMonth)
            Surface(
                onClick = {
                    haptics.selection()
                    onShown(shown.minusMonths(1))
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
                    onShown(shown.plusMonths(1))
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
                    firstDayOfWeek.plus(i.toLong()).getDisplayName(JavaTextStyle.NARROW, locale),
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
            label = "calendar_month",
        ) { month ->
            MonthDays(
                month = month,
                first = first,
                last = last,
                today = today,
                latest = latest,
                firstDayOfWeek = firstDayOfWeek,
                onPick = onPick,
            )
        }
    }
}

/** The day the phone's calendar starts its week on, read again when the phone's language or region changes. */
@Composable
internal fun rememberFirstDayOfWeek(): DayOfWeek {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { WeekFields.of(locale).firstDayOfWeek }
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
internal fun dayLabel(day: LocalDate): String = shortDate(day.atStartOfDay())

// ---------------------------------------------------------------------------------------------------------------------
// Pieces the sheets share
// ---------------------------------------------------------------------------------------------------------------------

/** A run of [QuickTile]s, side by side, scrolling sideways when they do not all fit; one is chosen at a time. */
@Composable
internal fun QuickTileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        content = content,
    )
}

/** A ready-made choice as a tile, as the month sheet's: the one chosen filled and morphed to a full pill. [spoken] is what a screen reader says. */
@Composable
internal fun QuickTile(label: String, spoken: String, selected: Boolean, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(if (selected) 20.dp else AppRadius.md, AppMotion.spatial<Dp>(), label = "preset_corner")
    Surface(
        selected = selected,
        onClick = onPick,
        modifier = Modifier
            .height(40.dp)
            .clearAndSetSemantics {
                contentDescription = spoken
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
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** The sheet's one action, full width at thumb height, under the part of the sheet that scrolls. */
@Composable
private fun SheetButton(label: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(start = AppSpacing.xxl, top = AppSpacing.lg, end = AppSpacing.xxl, bottom = AppSpacing.xl)) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * One half of a connected pair: what it is ([label]) over its value, on the tonal container, with a ring while it is the
 * one the next tap sets ([active]). [spoken] is what a screen reader says for it, [actionLabel] what a tap does.
 */
@Composable
internal fun RowScope.PickerSegment(
    label: String,
    value: String,
    shape: Shape,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    spoken: String = "$label, $value",
    actionLabel: String = "Choose ${label.lowercase()}",
    valueStyle: TextStyle = MaterialTheme.typography.titleMedium,
    interactionSource: MutableInteractionSource? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (active) scheme.primary else Color.Transparent, label = "picker_segment_ring")
    Surface(
        onClick = onSelect,
        modifier = modifier
            .weight(1f)
            .heightIn(min = PickerHeight)
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                if (active) selected = true
                onClick(label = actionLabel) { onSelect(); true }
            },
        shape = shape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        border = BorderStroke(2.dp, ring),
        interactionSource = interactionSource,
    ) {
        Column(Modifier.padding(horizontal = AppSpacing.lg), verticalArrangement = Arrangement.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSecondaryContainer.copy(alpha = 0.78f), maxLines = 1)
            Text(
                value,
                style = valueStyle,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One field of a [PickerFieldRow]: [label] ("Date"), its [value] ("12 Oct"), and what a tap does. */
@Immutable
class PickerField(val label: String, val value: String, val onOpen: () -> Unit)

/**
 * The date and time fields of a form as the Insights date range is: a connected pair at 56dp (or a single field, when
 * there is no [second]), fully round at the outer ends, the corners where the two meet opening up under a finger. Each
 * field reads "Date, 12 Oct" to a screen reader and opens its sheet when tapped.
 */
@Composable
fun PickerFieldRow(first: PickerField, modifier: Modifier = Modifier, second: PickerField? = null) {
    val haptics = rememberAppHaptics()
    val outer = CornerSize(50)
    val firstPress = remember { MutableInteractionSource() }
    val secondPress = remember { MutableInteractionSource() }
    val firstInner = CornerSize(pressedCorner(firstPress))
    val secondInner = CornerSize(pressedCorner(secondPress))
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
        if (second == null) {
            PickerSegment(
                label = first.label,
                value = first.value,
                shape = RoundedCornerShape(outer),
                onSelect = {
                    haptics.selection()
                    first.onOpen()
                },
            )
        } else {
            PickerSegment(
                label = first.label,
                value = first.value,
                shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = firstInner, bottomEnd = firstInner),
                onSelect = {
                    haptics.selection()
                    first.onOpen()
                },
                interactionSource = firstPress,
            )
            PickerSegment(
                label = second.label,
                value = second.value,
                shape = RoundedCornerShape(topStart = secondInner, bottomStart = secondInner, topEnd = outer, bottomEnd = outer),
                onSelect = {
                    haptics.selection()
                    second.onOpen()
                },
                interactionSource = secondPress,
            )
        }
    }
}
