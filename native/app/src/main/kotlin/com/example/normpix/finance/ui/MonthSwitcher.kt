package com.example.normpix.finance.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.settings.MonthControlStyle
import com.example.normpix.ui.components.InnerCorner
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.PressedInnerCorner
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.SplitGap
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/** As tall as the search bar and the split buttons, so a page's controls line up as one family. */
internal val SwitcherHeight = 56.dp

/** The gap between the track and what sits in it, as the primary tabs have: what is inside is [SwitcherHeight] less this twice. */
private val TrackInset = 4.dp
private val StepSize = SwitcherHeight - TrackInset * 2

/** The most a step button grows with the text: a comfortable target, no more (see [StepButton]). */
private val StepMaxSize = 56.dp

/** The most a control grows with the user's text size (see [switcherHeight]). */
private const val MAX_HEIGHT_SCALE = 1.75f

/**
 * How tall the month control is: [SwitcherHeight] at the usual text size and taller as the text grows - the month pill holds a
 * title and a line under it, and at twice the text a fixed 56dp clipped the second line (the "13 days" under a range, the
 * "Every transaction, every month" under "All time").
 */
@Composable
internal fun switcherHeight(): Dp = SwitcherHeight * LocalDensity.current.fontScale.coerceIn(1f, MAX_HEIGHT_SCALE)

/**
 * The tonal track the month control sits in: one pill, the same as the primary tabs' (Activity | Plans) just above it on the
 * pages that have both, so the page's controls read as one family - the thing you are looking at is the filled pill inside it,
 * the way a primary tab is. Callers give it the width.
 */
@Composable
private fun SwitcherTrack(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .height(switcherHeight())
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(TrackInset),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The user's choice of how the month is switched (Settings > Appearance > Month control), unless the caller says which. */
@Composable
internal fun rememberMonthControlStyle(): MonthControlStyle =
    LocalAppPreferences.current?.state?.collectAsState()?.value?.monthControlStyle ?: MonthControlStyle.Track

/**
 * The month a page shows, as one control, in the user's [style]: by default a tonal track holding a step back, the month itself
 * on a filled pill (with how far back it is under it) and a step forward - which, with nothing later to go to, is dimmed in
 * place, so the control keeps its shape; or, in [MonthControlStyle.Split], the earlier separate buttons of the split-button
 * family (see [SplitMonthControl]). Either way the month slides in from the side it came from, tapping it opens a sheet to pick
 * any month, and the steps and the pill are named the same for a screen reader. [latest] caps forward navigation (it is "this
 * month"). [trailing] sits after the control (the history's way out of the month into everything).
 */
@Composable
fun MonthSwitcher(
    month: MonthKey,
    latest: MonthKey,
    onChanged: (MonthKey) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    style: MonthControlStyle = rememberMonthControlStyle(),
) {
    val haptics = rememberAppHaptics()
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val go = { next: MonthKey ->
        haptics.selection()
        onChanged(next)
    }
    val canGoForward = month.isBefore(latest)
    val openPicker = {
        haptics.selection()
        picking = true
        scope.launch {
            try {
                val picked = overlays.sheet<MonthKey> { dismiss -> MonthPickerSheet(month, latest, onPick = dismiss) }
                if (picked != null && picked != month) go(picked)
            } finally {
                picking = false
            }
        }
        Unit
    }

    val control: @Composable (Modifier) -> Unit = { controlModifier ->
        when (style) {
            MonthControlStyle.Track -> SwitcherTrack(controlModifier) {
                StepButton(icon = Icons.Rounded.ChevronLeft, description = "Previous month", enabled = true, onClick = { go(month.previous) })
                MonthSegment(month = month, latest = latest, open = picking, onOpen = openPicker)
                StepButton(icon = Icons.Rounded.ChevronRight, description = "Next month", enabled = canGoForward, onClick = { go(month.next) })
            }
            MonthControlStyle.Split -> SplitMonthControl(
                month = month,
                latest = latest,
                open = picking,
                canGoForward = canGoForward,
                onPrevious = { go(month.previous) },
                onNext = { go(month.next) },
                onOpen = openPicker,
                modifier = controlModifier,
            )
        }
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The month needs room beside its way out ("All time", "Dates"): when big text or a narrow phone would leave it less
        // than it can be read in, the way out goes under the control, which then has the whole width.
        val stacked = trailing != null && maxWidth - MONTH_CONTROL_CHROME - TRAILING_WIDTH * fontScale < MIN_MONTH_ROOM
        if (stacked) {
            Column(Modifier.fillMaxWidth()) {
                control(Modifier.fillMaxWidth())
                Spacer(Modifier.height(AppSpacing.sm))
                Box(Modifier.fillMaxWidth()) { trailing() }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                control(Modifier.weight(1f))
                if (trailing != null) {
                    Spacer(Modifier.width(if (style == MonthControlStyle.Track) AppSpacing.sm else AppSpacing.xs))
                    trailing()
                }
            }
        }
    }
}

/** What the month control takes of its width apart from the month's own text: two steps, the track's padding, the pill's padding and chevron. */
private val MONTH_CONTROL_CHROME = 148.dp

/** About how wide the way out beside it is at the usual text size ("All time" with its icon); it grows with the text. */
private val TRAILING_WIDTH = 110.dp

/** The least room the month's name has beside the way out: below this it would be a few letters and a dot. */
private val MIN_MONTH_ROOM = 64.dp

/** "This month", "Last month", "3 months ago": how far [month] is from [latest], under the month's name. */
internal fun relativeMonth(month: MonthKey, latest: MonthKey): String {
    if (month == latest) return "This month"
    if (!month.isBefore(latest)) return "Ahead"
    val back = generateSequence(month) { it.next }.takeWhile { it.isBefore(latest) }.count()
    return if (back == 1) "Last month" else "$back months ago"
}

/** The inner corner of a segment: small, opening up while it is pressed (the split button's motion). */
@Composable
internal fun pressedCorner(press: MutableInteractionSource): Dp {
    val pressed by press.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) PressedInnerCorner else InnerCorner, AppMotion.spatial<Dp>(), label = "month_corner")
    return corner
}

/** A step either side of the month: a round, plain button on the track (a filled one would be a second thing to look at). */
@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            // A step is a target, not text: it grows a little with the text but never takes the room the month needs (at twice
            // the text it was 90dp, and left the month "...").
            .size(minOf(switcherHeight() - TrackInset * 2, StepMaxSize))
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(AppIconSizes.lg),
        )
    }
}

/**
 * The month's name over how far back it is, on a month pill (either style's): a later month slides in from the right, an
 * earlier one from the left, like the pages of a calendar.
 */
@Composable
internal fun MonthLabel(month: MonthKey, latest: MonthKey, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    AnimatedContent(
        targetState = month,
        modifier = modifier,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction = if (targetState.isBefore(initialState)) -1 else 1
                val arrive = tween<Float>(AppMotion.MEDIUM_MS, easing = AppMotion.enter)
                val leave = tween<Float>(AppMotion.SHORT_MS, easing = AppMotion.exit)
                (
                    slideInHorizontally(tween(AppMotion.MEDIUM_MS, easing = AppMotion.enter)) { direction * it / 3 } + fadeIn(arrive)
                    ) togetherWith (
                    slideOutHorizontally(tween(AppMotion.SHORT_MS, easing = AppMotion.exit)) { -direction * it / 3 } + fadeOut(leave)
                    ) using SizeTransform(clip = false)
            }
        },
        label = "month_label",
    ) { shown ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val style = MaterialTheme.typography.titleMedium
            Text(
                monthLabel(shown),
                style = style,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                // A long name ("September") beside a wide way out ("All time") shrinks to fit rather than being cut short.
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize),
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                relativeMonth(shown, latest),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSecondaryContainer,
                maxLines = 1,
            )
        }
    }
}

/** What a screen reader hears of the month pill, and does with it. */
internal fun monthPillSpoken(month: MonthKey, latest: MonthKey): String =
    "${monthLabelFull(month)}, ${relativeMonth(month, latest).lowercase()}, choose month"

@Composable
private fun RowScope.MonthSegment(month: MonthKey, latest: MonthKey, open: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val chevronTurn by animateFloatAsState(if (open) 180f else 0f, AppMotion.spatial<Float>(), label = "month_chevron")
    val spoken = monthPillSpoken(month, latest)
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                // Stepping a month moves nothing a screen reader is on: the new month is announced as it arrives.
                liveRegion = LiveRegionMode.Polite
                onClick(label = "Choose month") { onOpen(); true }
            },
        shape = CircleShape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthLabel(month, latest, Modifier.weight(1f))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(AppIconSizes.md).graphicsLayer { rotationZ = chevronTurn },
            )
        }
    }
}

/**
 * Any month, picked from a sheet: the year with a step either side, then its twelve months as tiles - the one showing
 * filled and fully round, this month ringed, months still to come not offered. Calls [onPick] with the month, or null.
 */
@Composable
fun MonthPickerSheet(initial: MonthKey, latest: MonthKey, onPick: (MonthKey?) -> Unit) {
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    var year by remember { mutableIntStateOf(initial.year) }
    val outer = CornerSize(50)
    val inner = CornerSize(InnerCorner)

    Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xl)) {
        SheetHeader(title = "Choose a month", subtitle = "Showing ${monthLabelFull(initial)}")
        Spacer(Modifier.height(AppSpacing.lg))

        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
            Surface(
                onClick = {
                    haptics.selection()
                    year--
                },
                modifier = Modifier.width(StepSize).fillMaxHeight().semantics { contentDescription = "Previous year" },
                shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
                color = scheme.surfaceContainerHighest,
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChevronLeft, contentDescription = null) } }
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight().semantics { heading() },
                shape = RoundedCornerShape(inner),
                color = scheme.surfaceContainerHigh,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("$year", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            val nextYear = year < latest.year
            Surface(
                onClick = {
                    haptics.selection()
                    year++
                },
                enabled = nextYear,
                modifier = Modifier.width(StepSize).fillMaxHeight().semantics { contentDescription = "Next year" },
                shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
                color = scheme.surfaceContainerHighest,
                contentColor = if (nextYear) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChevronRight, contentDescription = null) } }
        }
        Spacer(Modifier.height(AppSpacing.md))

        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            for (row in (1..12).chunked(3)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    for (m in row) {
                        val key = MonthKey(year, m)
                        MonthTile(
                            label = monthShort(key),
                            spoken = monthLabelFull(key),
                            selected = key == initial,
                            current = key == latest,
                            enabled = !key.isAfter(latest),
                            onPick = {
                                haptics.selection()
                                onPick(key)
                            },
                        )
                    }
                }
            }
        }

        if (initial != latest) {
            Spacer(Modifier.height(AppSpacing.lg))
            FilledTonalButton(
                onClick = {
                    haptics.selection()
                    onPick(latest)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.Today, contentDescription = null, modifier = Modifier.size(AppIconSizes.sm))
                Spacer(Modifier.width(AppSpacing.sm))
                Text("Go to this month")
            }
        }
    }
}

@Composable
private fun RowScope.MonthTile(label: String, spoken: String, selected: Boolean, current: Boolean, enabled: Boolean, onPick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // The month showing morphs to a full pill; the others are the medium rounded tile.
    val corner by animateDpAsState(if (selected) 24.dp else AppRadius.md, AppMotion.spatial<Dp>(), label = "month_tile_corner")
    Surface(
        selected = selected,
        onClick = onPick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clearAndSetSemantics {
                contentDescription = if (current) "$spoken, this month" else spoken
                role = Role.RadioButton
                if (enabled) onClick { onPick(); true } else disabled()
                this.selected = selected
            },
        shape = RoundedCornerShape(corner),
        color = when {
            selected -> scheme.primary
            enabled -> scheme.surfaceContainerHigh
            else -> scheme.surfaceContainerLow
        },
        contentColor = when {
            selected -> scheme.onPrimary
            enabled -> scheme.onSurface
            else -> scheme.onSurface.copy(alpha = 0.38f)
        },
        border = if (current && !selected) BorderStroke(1.5.dp, scheme.primary) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/**
 * A way out of the month - "Dates" or "All time" beside a [MonthSwitcher] - as a pill of the same height and tone as the
 * switcher's track, with an [icon] saying what it does, so the two read as a pair. In [MonthControlStyle.Split] it is the
 * earlier quiet pill of words alone, as that style has it ([SplitMonthScopeButton]).
 */
@Composable
fun MonthScopeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: MonthControlStyle = rememberMonthControlStyle(),
) {
    if (style == MonthControlStyle.Split) {
        SplitMonthScopeButton(label, onClick, modifier)
        return
    }
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        modifier = modifier.height(switcherHeight()),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // Close to its icon at the start: the month beside it needs every dp it can have.
        Row(
            Modifier.padding(start = if (icon != null) AppSpacing.md else AppSpacing.lg, end = AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(AppIconSizes.md))
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** What is in the way back to one month at a time: the calendar, and the words. */
@Composable
private fun ByMonthContent() {
    Row(Modifier.padding(start = AppSpacing.md, end = AppSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(AppIconSizes.md))
        Spacer(Modifier.width(AppSpacing.sm))
        Text("By month", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** The way back to one month at a time, on the track beside what stands in for the [MonthSwitcher]: plain, with the calendar. */
@Composable
private fun ByMonthButton(onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        modifier = Modifier.fillMaxHeight(),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { ByMonthContent() }
}

/** The same way back, standing on its own under the pill (see [ScopeSwitcher]): a pill of the track's tone. */
@Composable
private fun ByMonthPill(onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        modifier = Modifier.height(switcherHeight()),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) { ByMonthContent() }
}

/** From this font scale up the way back to one month moves under the "All time" / picked-days pill, which then has the whole width. */
internal const val STACK_SCOPE_AT_FONT_SCALE = 1.4f

/**
 * What stands in for the month switcher: [pill] on the track and the way back to one month at a time ([onByMonth]) beside it -
 * or, with large text, the pill on a track of its own, full width, and the way back under it. Beside it, with twice the text,
 * "3 Oct – 15 Oct" was "3 Oct …": the dates themselves were what was cut.
 */
@Composable
private fun ScopeSwitcher(modifier: Modifier, onByMonth: () -> Unit, pill: @Composable RowScope.() -> Unit) {
    if (LocalDensity.current.fontScale >= STACK_SCOPE_AT_FONT_SCALE) {
        Column(modifier.fillMaxWidth()) {
            SwitcherTrack(Modifier.fillMaxWidth(), pill)
            Spacer(Modifier.height(AppSpacing.sm))
            ByMonthPill(onByMonth)
        }
    } else {
        SwitcherTrack(modifier.fillMaxWidth()) {
            pill()
            ByMonthButton(onByMonth)
        }
    }
}

/**
 * What stands in for the [MonthSwitcher] while a list shows every month: the same track, its filled pill saying "All time",
 * and beside it the way back to one month at a time.
 */
@Composable
fun AllTimeSwitcher(onByMonth: () -> Unit, modifier: Modifier = Modifier, style: MonthControlStyle = rememberMonthControlStyle()) {
    if (style == MonthControlStyle.Split) {
        SplitAllTimeSwitcher(onByMonth, modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    ScopeSwitcher(modifier, onByMonth) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics(mergeDescendants = true) {
                    heading()
                    // Said whole whether or not there is room to show the second line.
                    contentDescription = "All time. $ALL_TIME_CAPTION"
                },
            shape = CircleShape,
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) {
            Column(Modifier.padding(horizontal = AppSpacing.lg), verticalArrangement = Arrangement.Center) {
                Text("All time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                // With large text the line under it is longer than the pill is wide: it would show as "Every tr…". It only
                // says again what "All time" says, so it is left out rather than cut.
                if (LocalDensity.current.fontScale < STACK_SCOPE_AT_FONT_SCALE) {
                    Text(
                        ALL_TIME_CAPTION,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal const val ALL_TIME_CAPTION = "Every transaction, every month"

/** How many whole days [range] covers (a range picked from the calendar is always whole days). */
internal fun dayCount(range: DateRange): Int =
    ChronoUnit.DAYS.between(range.start.toLocalDate(), range.end.toLocalDate()).toInt().coerceAtLeast(1)

/** "3 Oct – 15 Oct", or "15 Oct" for a single day: the days a page shows, in words. */
internal fun rangeLabel(range: DateRange): String {
    val last = range.end.minusDays(1)
    return if (dayCount(range) == 1) shortDate(range.start) else "${shortDate(range.start)} – ${shortDate(last)}"
}

/** "1 day", "13 days". */
internal fun daysLabel(days: Int): String = if (days == 1) "1 day" else "$days days"

/**
 * What stands in for the [MonthSwitcher] while a page shows days the user picked: the same track, its filled pill saying which
 * days and how many (tap it to pick others), and beside it the way back to one month at a time - as the [AllTimeSwitcher] has.
 */
@Composable
fun DateRangeSwitcher(
    range: DateRange,
    onChange: () -> Unit,
    onByMonth: () -> Unit,
    modifier: Modifier = Modifier,
    style: MonthControlStyle = rememberMonthControlStyle(),
) {
    if (style == MonthControlStyle.Split) {
        SplitDateRangeSwitcher(range, onChange, onByMonth, modifier)
        return
    }
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val label = rangeLabel(range)
    val length = daysLabel(dayCount(range))
    val change = {
        haptics.selection()
        onChange()
    }
    ScopeSwitcher(modifier, onByMonth) {
        Surface(
            onClick = change,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clearAndSetSemantics {
                    contentDescription = "$label, $length, choose dates"
                    role = Role.Button
                    onClick(label = "Choose dates") { change(); true }
                },
            shape = CircleShape,
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) {
            Row(Modifier.padding(start = AppSpacing.lg, end = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        length,
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSecondaryContainer,
                        maxLines = 1,
                    )
                }
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(AppIconSizes.md))
            }
        }
    }
}
