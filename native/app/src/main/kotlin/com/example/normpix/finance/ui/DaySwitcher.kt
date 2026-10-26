package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.example.normpix.settings.MonthControlStyle
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SplitGap
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/** "Today", "Yesterday", or how many days back: under the day's date on its pill, as "Last month" is under a month. */
internal fun relativeDay(day: LocalDate, today: LocalDate): String {
    val back = ChronoUnit.DAYS.between(day, today)
    return when {
        back == 0L -> "Today"
        back == 1L -> "Yesterday"
        back < 0L -> "Ahead"
        back < 7L -> day.format(DateTimeFormatter.ofPattern("EEEE"))
        else -> "$back days ago"
    }
}

/** "Mon, 26 Oct" (with the year once it isn't this one): the day a list shows. */
internal fun dayPillLabel(day: LocalDate, today: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE, d MMM" else "EEE, d MMM yyyy"))

/**
 * What stands in for the [MonthSwitcher] while a list shows one day: the same family of control - a step back, the day on its
 * filled pill (with how far back it is under it; tap it to pick another day from the calendar) and a step forward, dimmed once it
 * is today - with the way back to one month at a time beside it, as "All time" and picked dates have. In the split style the
 * parts are the split buttons, a hair apart.
 */
@Composable
fun DaySwitcher(
    day: LocalDate,
    onChanged: (LocalDate) -> Unit,
    onByMonth: () -> Unit,
    modifier: Modifier = Modifier,
    latest: LocalDate = LocalDate.now(),
    style: MonthControlStyle = rememberMonthControlStyle(),
) {
    val haptics = rememberAppHaptics()
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val go = { next: LocalDate ->
        haptics.selection()
        onChanged(next)
    }
    val canGoForward = day.isBefore(latest)
    val pick = {
        haptics.selection()
        scope.launch {
            val picked = overlays.sheet<LocalDate>(expandable = true) { done -> DateSheet(day, latest, onPick = done) }
            if (picked != null && picked != day) onChanged(picked)
        }
        Unit
    }
    val label = dayPillLabel(day, latest)
    val relative = relativeDay(day, latest)
    val pill: @Composable RowScope.() -> Unit = {
        Surface(
            onClick = pick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clearAndSetSemantics {
                    contentDescription = "${day.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))}, ${relative.lowercase()}, choose day"
                    role = Role.Button
                    // Stepping a day moves nothing a screen reader is on: the new day is announced as it arrives.
                    liveRegion = LiveRegionMode.Polite
                    onClick(label = "Choose day") { pick(); true }
                },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(Modifier.padding(start = AppSpacing.md, end = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val type = MaterialTheme.typography.titleMedium
                    Text(
                        label,
                        style = type,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = type.fontSize),
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(relative, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(AppIconSizes.md))
            }
        }
    }
    val stacked = LocalDensity.current.fontScale >= STACK_SCOPE_AT_FONT_SCALE
    when (style) {
        MonthControlStyle.Track -> if (stacked) {
            Column(modifier.fillMaxWidth()) {
                SwitcherTrack(Modifier.fillMaxWidth()) {
                    StepButton(Icons.Rounded.ChevronLeft, "Previous day", enabled = true, onClick = { go(day.minusDays(1)) })
                    pill()
                    StepButton(Icons.Rounded.ChevronRight, "Next day", enabled = canGoForward, onClick = { go(day.plusDays(1)) })
                }
                Spacer(Modifier.height(AppSpacing.sm))
                ByMonthPill(onByMonth)
            }
        } else {
            Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SwitcherTrack(Modifier.weight(1f)) {
                    StepButton(Icons.Rounded.ChevronLeft, "Previous day", enabled = true, onClick = { go(day.minusDays(1)) })
                    pill()
                    StepButton(Icons.Rounded.ChevronRight, "Next day", enabled = canGoForward, onClick = { go(day.plusDays(1)) })
                }
                Spacer(Modifier.width(AppSpacing.sm))
                ByMonthPill(onByMonth)
            }
        }
        MonthControlStyle.Split -> {
            val control: @Composable (Modifier) -> Unit = { m ->
                Row(m.height(switcherHeight()), horizontalArrangement = Arrangement.spacedBy(SplitGap)) {
                    SplitStepSegment(icon = Icons.Rounded.ChevronLeft, description = "Previous day", outerStart = true, enabled = true, onClick = { go(day.minusDays(1)) })
                    pill()
                    SplitStepSegment(icon = Icons.Rounded.ChevronRight, description = "Next day", outerStart = false, enabled = canGoForward, onClick = { go(day.plusDays(1)) })
                }
            }
            if (stacked) {
                Column(modifier.fillMaxWidth()) {
                    control(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(AppSpacing.sm))
                    SplitByMonthPill(onByMonth)
                }
            } else {
                Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    control(Modifier.weight(1f))
                    Spacer(Modifier.width(AppSpacing.xs))
                    SplitByMonthPill(onByMonth)
                }
            }
        }
    }
}
