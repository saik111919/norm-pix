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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.MonthKey
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
import kotlinx.coroutines.launch

/** As tall as the search bar and the split buttons, so a page's controls line up as one family. */
private val SwitcherHeight = 56.dp
private val StepWidth = 48.dp

/**
 * The month a page shows, as one connected control in the app's split-button family: a step back, the month itself
 * (tonal, with how far back it is under it), and a step forward - a hair apart, fully round at the outer ends, the
 * corners where they meet opening up under a finger. The month slides in from the side it came from. Tapping it opens a
 * sheet to pick any month. [latest] caps forward navigation (it is "this month"). [trailing] sits after the control
 * (the history's way out of the month into everything).
 */
@Composable
fun MonthSwitcher(
    month: MonthKey,
    latest: MonthKey,
    onChanged: (MonthKey) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
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

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).height(SwitcherHeight), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
            StepSegment(
                icon = Icons.Rounded.ChevronLeft,
                description = "Previous month",
                outerStart = true,
                enabled = true,
                onClick = { go(month.previous) },
            )
            MonthSegment(
                month = month,
                latest = latest,
                open = picking,
                onOpen = {
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
                },
            )
            StepSegment(
                icon = Icons.Rounded.ChevronRight,
                description = "Next month",
                outerStart = false,
                enabled = canGoForward,
                onClick = { go(month.next) },
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(AppSpacing.xs))
            trailing()
        }
    }
}

/** "This month", "Last month", "3 months ago": how far [month] is from [latest], under the month's name. */
internal fun relativeMonth(month: MonthKey, latest: MonthKey): String {
    if (month == latest) return "This month"
    if (!month.isBefore(latest)) return "Ahead"
    val back = generateSequence(month) { it.next }.takeWhile { it.isBefore(latest) }.count()
    return if (back == 1) "Last month" else "$back months ago"
}

/** The inner corner of a segment: small, opening up while it is pressed (the split button's motion). */
@Composable
private fun pressedCorner(press: MutableInteractionSource): Dp {
    val pressed by press.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) PressedInnerCorner else InnerCorner, AppMotion.spatial<Dp>(), label = "month_corner")
    return corner
}

@Composable
private fun StepSegment(icon: ImageVector, description: String, outerStart: Boolean, enabled: Boolean, onClick: () -> Unit) {
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
private fun RowScope.MonthSegment(month: MonthKey, latest: MonthKey, open: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val press = remember { MutableInteractionSource() }
    val inner = pressedCorner(press)
    // Fully round while its sheet is open, as the split button's menu half is while its menu is.
    val corner by animateDpAsState(if (open) SwitcherHeight / 2 else inner, AppMotion.spatial<Dp>(), label = "month_open_corner")
    val chevronTurn by animateFloatAsState(if (open) 180f else 0f, AppMotion.spatial<Float>(), label = "month_chevron")
    val spoken = "${monthLabelFull(month)}, ${relativeMonth(month, latest).lowercase()}, choose month"
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
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
            AnimatedContent(
                targetState = month,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        // A later month comes in from the right, an earlier one from the left, like the pages of a calendar.
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
                    Text(
                        monthLabel(shown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        relativeMonth(shown, latest),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSecondaryContainer.copy(alpha = 0.78f),
                        maxLines = 1,
                    )
                }
            }
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = chevronTurn },
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
                modifier = Modifier.width(StepWidth).fillMaxHeight().semantics { contentDescription = "Previous year" },
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
                modifier = Modifier.width(StepWidth).fillMaxHeight().semantics { contentDescription = "Next year" },
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
 * A way out of the month - "All time" beside a [MonthSwitcher] - as a pill of the same height and family: the quieter,
 * untinted tone, fully round.
 */
@Composable
fun MonthScopeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        modifier = modifier.height(SwitcherHeight),
        shape = RoundedCornerShape(CornerSize(50)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(Modifier.padding(horizontal = AppSpacing.lg), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * What stands in for the [MonthSwitcher] while a list shows every month: the same connected control, its tonal part saying
 * "All time", and beside it the way back to one month at a time.
 */
@Composable
fun AllTimeSwitcher(onByMonth: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val outer = CornerSize(50)
    val press = remember { MutableInteractionSource() }
    val inner = CornerSize(pressedCorner(press))
    Row(modifier.fillMaxWidth().height(SwitcherHeight), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) { heading() },
            shape = RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner),
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) {
            Column(Modifier.padding(horizontal = AppSpacing.lg), verticalArrangement = Arrangement.Center) {
                Text("All time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "Every transaction, every month",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSecondaryContainer.copy(alpha = 0.78f),
                    maxLines = 1,
                )
            }
        }
        Surface(
            onClick = {
                haptics.selection()
                onByMonth()
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
}
