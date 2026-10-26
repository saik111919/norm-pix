package com.example.normpix.finance.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.ui.components.SplitGap
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing

/*
 * The earlier look of the month control, kept as a choice (Settings > Appearance > Navigation & controls > Split buttons): the steps
 * and the month as separate tonal buttons in the app's split-button family, a hair apart, fully round at the outer ends, the
 * corners where they meet opening up under a finger. Everything a screen reader or a test finds is the same as in the track
 * style: the steps are "Previous month" and "Next month", the pill says its month and how far back, and so on.
 */

private val StepWidth = 48.dp

/**
 * The month as three connected buttons: a step back, the month itself (tonal, with how far back it is under it) and a step
 * forward. [modifier] gives it its width; its height is that of the page's other controls.
 */
@Composable
internal fun SplitMonthControl(
    month: MonthKey,
    latest: MonthKey,
    open: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.height(switcherHeight()), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
        SplitStepSegment(icon = Icons.Rounded.ChevronLeft, description = "Previous month", outerStart = true, enabled = true, onClick = onPrevious)
        SplitMonthSegment(month = month, latest = latest, open = open, onOpen = onOpen)
        SplitStepSegment(icon = Icons.Rounded.ChevronRight, description = "Next month", outerStart = false, enabled = canGoForward, onClick = onNext)
    }
}

@Composable
internal fun SplitStepSegment(icon: ImageVector, description: String, outerStart: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val inner = CornerSize(pressedCorner(press))
    val outer = CornerSize(50)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(StepWidth).fillMaxHeight().semantics { contentDescription = description },
        shape = if (outerStart) {
            RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
        } else {
            RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
        },
        color = scheme.surfaceContainerHigh,
        contentColor = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
        interactionSource = press,
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
    }
}

@Composable
private fun RowScope.SplitMonthSegment(month: MonthKey, latest: MonthKey, open: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val inner = pressedCorner(press)
    // Fully round while its sheet is open, as the split button's menu half is while its menu is.
    val corner by animateDpAsState(if (open) switcherHeight() / 2 else inner, AppMotion.spatial<Dp>(), label = "month_open_corner")
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
        shape = RoundedCornerShape(CornerSize(corner)),
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        interactionSource = press,
    ) {
        Row(
            Modifier.padding(start = AppSpacing.md, end = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthLabel(month, latest, Modifier.weight(1f))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = chevronTurn },
            )
        }
    }
}

/** A way out of the month - "All time" beside a split [MonthSwitcher] - as a pill of the same height and family: the quieter, untinted tone, fully round. */
@Composable
internal fun SplitMonthScopeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        modifier = modifier.height(switcherHeight()),
        shape = RoundedCornerShape(CornerSize(50)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.padding(horizontal = AppSpacing.lg), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** The way back to one month at a time, as the second half of a split control: the untinted tone, with the calendar. */
@Composable
private fun RowScope.SplitByMonthSegment(onClick: () -> Unit, press: MutableInteractionSource, inner: CornerSize, outer: CornerSize) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        modifier = Modifier.fillMaxHeight(),
        shape = RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        interactionSource = press,
    ) {
        Row(Modifier.padding(start = AppSpacing.md, end = AppSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(AppIconSizes.md))
            Spacer(Modifier.width(AppSpacing.sm))
            Text("By month", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** The same way back, standing on its own under the pill (large text, see [SplitAllTimeSwitcher]): a pill of the buttons' tone. */
@Composable
internal fun SplitByMonthPill(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        modifier = Modifier.height(switcherHeight()),
        shape = RoundedCornerShape(CornerSize(50)),
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
    ) {
        Row(Modifier.padding(start = AppSpacing.md, end = AppSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(AppIconSizes.md))
            Spacer(Modifier.width(AppSpacing.sm))
            Text("By month", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * What stands in for a split [MonthSwitcher] while a list shows every month: the same connected control, its tonal part saying
 * "All time", and beside it the way back to one month at a time - which, with large text, is a pill of its own under it, so the
 * tonal part has the whole width.
 */
@Composable
internal fun SplitAllTimeSwitcher(onByMonth: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val outer = CornerSize(50)
    val press = remember { MutableInteractionSource() }
    val inner = CornerSize(pressedCorner(press))
    val stacked = LocalDensity.current.fontScale >= STACK_SCOPE_AT_FONT_SCALE
    val pill: @Composable RowScope.() -> Unit = {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .semantics(mergeDescendants = true) {
                    heading()
                    contentDescription = "All time. $ALL_TIME_CAPTION"
                },
            shape = if (stacked) RoundedCornerShape(outer) else RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) {
            Column(Modifier.padding(horizontal = AppSpacing.lg), verticalArrangement = Arrangement.Center) {
                Text("All time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                // Left out with large text, where it would show as "Every tr…" and only says again what "All time" says.
                if (!stacked) {
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
    if (stacked) {
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(switcherHeight())) { pill() }
            Spacer(Modifier.height(AppSpacing.sm))
            SplitByMonthPill(onByMonth)
        }
    } else {
        Row(modifier.fillMaxWidth().height(switcherHeight()), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
            pill()
            SplitByMonthSegment(onByMonth, press, inner, outer)
        }
    }
}

/**
 * What stands in for a split [MonthSwitcher] while a page shows days the user picked: the same connected control, its tonal
 * part saying which days and how many (tap it to pick others), and beside it the way back to one month at a time - under it,
 * with large text, so the dates are not cut short.
 */
@Composable
internal fun SplitDateRangeSwitcher(range: DateRange, onChange: () -> Unit, onByMonth: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val outer = CornerSize(50)
    val rangePress = remember { MutableInteractionSource() }
    val rangeInner = CornerSize(pressedCorner(rangePress))
    val monthPress = remember { MutableInteractionSource() }
    val monthInner = CornerSize(pressedCorner(monthPress))
    val label = rangeLabel(range)
    val length = daysLabel(dayCount(range))
    val change = {
        haptics.selection()
        onChange()
    }
    val stacked = LocalDensity.current.fontScale >= STACK_SCOPE_AT_FONT_SCALE
    val pill: @Composable RowScope.() -> Unit = {
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
            shape = if (stacked) RoundedCornerShape(outer) else RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = rangeInner, bottomEnd = rangeInner),
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
            interactionSource = rangePress,
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
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
    if (stacked) {
        Column(modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(switcherHeight())) { pill() }
            Spacer(Modifier.height(AppSpacing.sm))
            SplitByMonthPill(onByMonth)
        }
    } else {
        Row(modifier.fillMaxWidth().height(switcherHeight()), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
            pill()
            SplitByMonthSegment(onByMonth, monthPress, monthInner, outer)
        }
    }
}
