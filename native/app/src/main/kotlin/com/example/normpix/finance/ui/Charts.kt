package com.example.normpix.finance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.domain.DailyTotal
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

/*
 * Small, dependency-free charts for Insights, drawn the Material way: the data is the only loud thing - columns with rounded
 * data ends rising from one baseline, a line with a soft wash under it - while gridlines are hairlines, axis values are
 * small text at the end side, and a reference (an average, the month before) is dashed so it is never read as data. One
 * mark is emphasised at a time and the rest step back; touching a chart picks what it emphasises, and the screen says what
 * that is in a readout above it. Each chart only paints: the numbers are always also available as text (a semantics label
 * plus the lists and sentences next to it), so nothing depends on reading a shape or a color.
 */

/** Plays a chart's entrance once (instant when animations are off). */
@Composable
private fun rememberGrowIn(): State<Float> {
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, tween(AppMotion.LONG_MS, easing = AppMotion.enter))
    }
    return progress.asState()
}

class DonutSegment(val value: Long, val color: Color)

/** Share-of-total ring with a label in the middle. */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    size: Dp = 176.dp,
    thickness: Dp = 22.dp,
    center: (@Composable () -> Unit)? = null,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress by rememberGrowIn()
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics {
                contentDescription = semanticsLabel
                role = Role.Image
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            val style = Stroke(width = stroke, cap = StrokeCap.Butt)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
            val total = segments.sumOf { it.value }
            if (total <= 0) return@Canvas
            // A small gap between segments keeps adjacent colors distinguishable.
            val gap = if (segments.size > 1) 2f else 0f
            var start = -90f
            val sweepTotal = 360f * progress
            for (segment in segments) {
                val sweep = sweepTotal * segment.value / total
                val visible = sweep - gap
                if (visible > 0) {
                    drawArc(segment.color, start + gap / 2, visible, useCenter = false, topLeft = topLeft, size = arcSize, style = style)
                }
                start += sweep
            }
        }
        if (center != null) {
            Box(Modifier.padding(thickness + AppSpacing.sm), contentAlignment = Alignment.Center) { center() }
        }
    }
}

// --- scale ------------------------------------------------------------------------------------------------------------

/**
 * Where the gridlines go for values up to [max] (minor units): 0, then [steps] or fewer clean steps - 1, 2, 2.5 or 5 times a
 * power of ten in whole currency units, so the axis reads "₹20K, ₹40K", never "₹13,227". The last is the top of the scale.
 */
internal fun niceTicks(max: Long, minorPerMajor: Long, steps: Int = 3): List<Long> {
    val per = minorPerMajor.coerceAtLeast(1)
    val maxMajor = (max + per - 1) / per
    if (maxMajor <= 0) return listOf(0L, per)
    val rough = maxMajor.toDouble() / steps
    val magnitude = 10.0.pow(floor(log10(rough))).coerceAtLeast(1.0)
    // 2.5 only where it stays whole (25, 250...): a tick of 2.5 rupees would be labelled with a rounded number.
    val multipliers = if (magnitude >= 10) listOf(1.0, 2.0, 2.5, 5.0, 10.0) else listOf(1.0, 2.0, 5.0, 10.0)
    val step = multipliers.map { it * magnitude }.first { it >= rough }.roundToLong().coerceAtLeast(1)
    val top = (maxMajor + step - 1) / step * step
    return (0..top / step).map { it * step * per }
}

// --- emphasis ---------------------------------------------------------------------------------------------------------

/** What share of full color each mark has: all of it with nothing [selected], otherwise only the selected mark. */
private fun emphasisTarget(index: Int, selected: Int?): Float = if (selected == null || index == selected) 1f else 0f

/** One animated emphasis per mark, moving (a short standard ease) whenever the selection does. */
@Composable
private fun rememberEmphasis(count: Int, selected: Int?): List<Animatable<Float, AnimationVector1D>> {
    val reduced = rememberReducedMotion()
    val values = remember(count) { List(count) { Animatable(emphasisTarget(it, selected)) } }
    LaunchedEffect(values, selected, reduced) {
        values.forEachIndexed { i, value ->
            launch {
                val target = emphasisTarget(i, selected)
                if (reduced) value.snapTo(target) else value.animateTo(target, tween(AppMotion.SHORT_MS, easing = AppMotion.standard))
            }
        }
    }
    return values
}

/** How much a set-back column keeps of its color: still clearly there, plainly not the one being talked about. */
private const val SET_BACK_ALPHA = 0.5f

/** A column with nothing in it is a short pill on the baseline this tall, as a share of the column's width. */
private const val STUB_SHARE = 0.36f

// --- bar chart --------------------------------------------------------------------------------------------------------

/**
 * A column chart of one series: pill-shaped columns standing on one baseline, a short pill stub where a column has nothing
 * (so an empty day still reads as a day), hairline gridlines at clean values with their amounts on the start side, and
 * [labels] (by column) underneath as real text, so they scale with the user's font size. [reference] adds a dashed line
 * (an average). Columns from [elapsed] on have not happened yet (the rest of a month in progress): fainter stubs, fainter
 * labels.
 *
 * One column is the one being talked about: [selected], or else [highlight] (today, this month). It is drawn in full color
 * with its label in the accent and a small bar under it; the others step back. Tapping or dragging across the chart picks a
 * column ([onSelect]); tapping the selected one again clears the selection when [clearable].
 */
@Composable
fun BarChart(
    values: List<Long>,
    money: MoneyFormatter,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    labels: Map<Int, String> = emptyMap(),
    selected: Int? = null,
    onSelect: ((Int?) -> Unit)? = null,
    clearable: Boolean = false,
    reference: Long? = null,
    elapsed: Int = values.size,
    maxBarWidth: Dp = 28.dp,
    height: Dp = 176.dp,
    highlight: Int? = null,
) {
    if (values.isEmpty()) return
    val scheme = MaterialTheme.colorScheme
    val axisStyle = MaterialTheme.typography.labelSmall
    val labelStyle = MaterialTheme.typography.labelMedium
    val grid = scheme.outlineVariant.copy(alpha = scheme.outlineVariant.alpha * 0.6f)
    val axisText = scheme.onSurfaceVariant
    val empty = scheme.onSurface.copy(alpha = 0.10f)
    val emptyAhead = scheme.onSurface.copy(alpha = 0.05f)
    val referenceColor = scheme.onSurfaceVariant
    val focus = (selected ?: highlight)?.takeIf { it in values.indices }
    val progress by rememberGrowIn()
    val emphasis = rememberEmphasis(values.size, focus)
    val haptics = rememberAppHaptics()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val ticks = remember(values, reference, money) {
        niceTicks(max(values.maxOrNull() ?: 0L, reference ?: 0L), money.currency.minorPerMajor)
    }
    val tickLayouts = remember(ticks, axisStyle, density, money) { ticks.map { measurer.measure(money.compact(it), axisStyle) } }
    val labelLayouts = remember(labels, focus, labelStyle, density) {
        labels.mapValues { (i, text) ->
            measurer.measure(text, if (i == focus) labelStyle.copy(fontWeight = FontWeight.Bold) else labelStyle)
        }
    }
    val lineHeight = remember(labelStyle, density) { measurer.measure("0", labelStyle).size.height.toFloat() }
    val gap = with(density) { AppSpacing.md.toPx() }
    val reserved = (tickLayouts.maxOfOrNull { it.size.width } ?: 0).toFloat() + gap

    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val pick = { index: Int, fromTap: Boolean ->
        val next = if (fromTap && clearable && index == currentSelected) null else index
        if (next != currentSelected) {
            haptics.selection()
            currentOnSelect?.invoke(next)
        }
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                contentDescription = semanticsLabel
                role = Role.Image
            }
            .then(
                if (onSelect == null) Modifier else Modifier
                    .pointerInput(values.size, reserved) {
                        detectTapGestures { pick(columnAt(it.x - reserved, size.width - reserved, values.size), true) }
                    }
                    .pointerInput(values.size, reserved) {
                        detectHorizontalDragGestures(
                            onDragStart = { pick(columnAt(it.x - reserved, size.width - reserved, values.size), false) },
                        ) { change, _ ->
                            change.consume()
                            pick(columnAt(change.position.x - reserved, size.width - reserved, values.size), false)
                        }
                    },
            ),
    ) {
        val plotLeft = reserved
        val plotWidth = size.width - reserved
        val slot = plotWidth / values.size
        val barWidth = minOf(maxBarWidth.toPx(), slot * 0.56f, slot - 2.dp.toPx()).coerceAtLeast(1.dp.toPx())
        val stub = max(3.dp.toPx(), barWidth * STUB_SHARE)
        val marker = 3.dp.toPx()
        // Room under the baseline for half a stub, then the labels and the marker under the chosen one.
        val below = if (labels.isEmpty()) stub / 2 else stub / 2 + AppSpacing.sm.toPx() + lineHeight + AppSpacing.xs.toPx() + marker
        val plotTop = lineHeight / 2
        val plotBottom = size.height - below
        val scaleTop = ticks.last().toFloat()
        fun y(value: Long) = plotBottom - (plotBottom - plotTop) * (value / scaleTop)
        val hairline = 1.dp.toPx()

        // Gridlines first, so the columns sit on them; each with its value at the start.
        ticks.forEachIndexed { i, tick ->
            val ty = y(tick)
            drawLine(grid, Offset(plotLeft, ty), Offset(size.width, ty), strokeWidth = hairline)
            val layout = tickLayouts[i]
            drawText(layout, color = axisText, topLeft = Offset(plotLeft - gap - layout.size.width, ty - layout.size.height / 2f))
        }

        values.forEachIndexed { i, value ->
            val centre = plotLeft + slot * (i + 0.5f)
            val left = centre - barWidth / 2
            val strength = SET_BACK_ALPHA + (1f - SET_BACK_ALPHA) * emphasis[i].value
            val ahead = i >= elapsed
            if (ahead || value <= 0) {
                // Nothing here (yet): a stub on the baseline - the chosen column's in full color, so "today" shows even empty.
                val tone = when {
                    i == focus -> color
                    ahead -> emptyAhead
                    else -> empty
                }
                drawPill(tone, left, plotBottom - stub / 2, barWidth, stub)
                return@forEachIndexed
            }
            val top = plotBottom - max(stub / 2, (plotBottom - y(value)) * progress)
            drawPill(color.copy(alpha = color.alpha * strength), left, top, barWidth, plotBottom + stub / 2 - top)
        }

        if (reference != null && reference > 0) {
            val ry = y(reference)
            drawLine(
                referenceColor,
                Offset(plotLeft, ry),
                Offset(size.width, ry),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                cap = StrokeCap.Round,
            )
        }

        val labelTop = plotBottom + stub / 2 + AppSpacing.sm.toPx()
        for ((i, layout) in labelLayouts) {
            val centre = plotLeft + slot * (i + 0.5f)
            val left = (centre - layout.size.width / 2f).coerceIn(plotLeft - gap, max(plotLeft, size.width - layout.size.width))
            val ink = when {
                i == focus -> color
                i >= elapsed -> axisText.copy(alpha = axisText.alpha * 0.5f)
                else -> axisText
            }
            drawText(layout, color = ink, topLeft = Offset(left, labelTop))
            if (i == focus) {
                val markerWidth = min(16.dp.toPx(), max(barWidth, 8.dp.toPx()))
                drawPill(color, centre - markerWidth / 2, labelTop + layout.size.height + AppSpacing.xs.toPx(), markerWidth, marker)
            }
        }
    }
}

/** Which column [x] (from the plot's start) falls in, for a plot [plotWidth] wide holding [count] of them. */
private fun columnAt(x: Float, plotWidth: Float, count: Int): Int =
    if (plotWidth <= 0f) 0 else (x / (plotWidth / count)).toInt().coerceIn(0, count - 1)

/** A capsule: both ends fully rounded (a stub as short as it is round is a circle). */
private fun DrawScope.drawPill(color: Color, x: Float, y: Float, width: Float, height: Float) {
    val r = min(width, height) / 2
    drawRoundRect(color, topLeft = Offset(x, y), size = Size(width, height), cornerRadius = CornerRadius(r, r))
}

// --- pace chart -------------------------------------------------------------------------------------------------------

/**
 * How a month's spending built up, drawn as simply as the hero needs: its running total day by day as a line with a soft
 * wash under it, ending in a dot at the latest day, and the month before as a dashed line - where the two stand apart is
 * how much more or less had gone out by then. No gridlines or axis values (the amounts are said right above it); one
 * baseline. Tapping or dragging along it reads a day ([onScrub], 1-based; null when the finger lifts or the day is tapped
 * again).
 *
 * [current] and [previous] are running totals at the end of each day (index 0 is day 1); [days] is how many days the
 * x axis spans (the longer of the two months).
 */
@Composable
fun PaceChart(
    current: List<Long>,
    previous: List<Long>,
    days: Int,
    semanticsLabel: String,
    lineColor: Color,
    referenceColor: Color,
    guideColor: Color,
    ringColor: Color,
    modifier: Modifier = Modifier,
    scrubDay: Int? = null,
    onScrub: (Int?) -> Unit = {},
    height: Dp = 104.dp,
) {
    if (current.isEmpty() || days <= 0) return
    val progress by rememberGrowIn()
    val haptics = rememberAppHaptics()

    val currentDay by rememberUpdatedState(scrubDay)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val read = { day: Int? ->
        if (day != currentDay) {
            if (day != null) haptics.selection()
            currentOnScrub(day)
        }
    }
    fun dayAt(x: Float, width: Float): Int = ((x / width) * days).roundToInt().coerceIn(1, current.size)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics {
                contentDescription = semanticsLabel
                role = Role.Image
            }
            .pointerInput(current.size, days) {
                detectTapGestures {
                    val day = dayAt(it.x, size.width.toFloat())
                    read(if (day == currentDay) null else day)
                }
            }
            .pointerInput(current.size, days) {
                detectHorizontalDragGestures(
                    onDragStart = { read(dayAt(it.x, size.width.toFloat())) },
                    onDragEnd = { read(null) },
                    onDragCancel = { read(null) },
                ) { change, _ ->
                    change.consume()
                    read(dayAt(change.position.x, size.width.toFloat()))
                }
            },
    ) {
        // Room at the ends for the dot's ring; a little headroom over the higher line.
        val inset = 7.dp.toPx()
        val plotRight = size.width - inset
        val plotTop = inset
        val plotBottom = size.height - 1.dp.toPx()
        val scaleTop = (max(current.max(), previous.maxOrNull() ?: 0L) * 1.08f).coerceAtLeast(1f)
        fun x(day: Int) = plotRight * day / days
        fun y(value: Long) = plotBottom - (plotBottom - plotTop) * (value / scaleTop)
        val hairline = 1.dp.toPx()

        drawLine(guideColor, Offset(0f, plotBottom), Offset(plotRight, plotBottom), strokeWidth = hairline)

        fun line(totals: List<Long>): Path = Path().apply {
            moveTo(0f, plotBottom)
            totals.forEachIndexed { i, total -> lineTo(x(i + 1), y(total)) }
        }

        // The lines draw themselves in from the start of the month.
        clipRect(right = plotRight * progress + inset) {
            if (previous.isNotEmpty()) {
                drawPath(
                    line(previous),
                    referenceColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())),
                    ),
                )
            }
            val path = line(current)
            val wash = Path().apply {
                addPath(path)
                lineTo(x(current.size), plotBottom)
                close()
            }
            drawPath(wash, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f)), startY = plotTop, endY = plotBottom))
            drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        fun dot(at: Offset, fill: Color) {
            drawCircle(ringColor, radius = 6.dp.toPx(), center = at)
            drawCircle(fill, radius = 4.dp.toPx(), center = at)
        }
        val day = scrubDay
        if (day != null) {
            val dx = x(day)
            drawLine(guideColor, Offset(dx, plotTop), Offset(dx, plotBottom), strokeWidth = hairline)
            previous.getOrNull(day - 1)?.let { dot(Offset(dx, y(it)), referenceColor) }
            dot(Offset(dx, y(current[day - 1])), lineColor)
        } else if (progress >= 1f) {
            dot(Offset(x(current.size), y(current.last())), lineColor)
        }
    }
}

/** Running totals at the end of each of a month's first [days] days, from what went out on each day (index 0 is day 1). */
fun runningTotals(daily: List<DailyTotal>, days: Int): List<Long> {
    val totals = LongArray(days)
    for (d in daily) {
        val i = d.day.dayOfMonth - 1
        if (i in 0 until days) totals[i] += d.amount
    }
    for (i in 1 until days) totals[i] += totals[i - 1]
    return totals.toList()
}

// --- legends and readouts ---------------------------------------------------------------------------------------------

/** The key for a line in a legend: a short stroke of its color (dashed for a reference), never a filled box. */
@Composable
fun LineKey(color: Color, modifier: Modifier = Modifier, dashed: Boolean = false) {
    Canvas(modifier.size(width = 16.dp, height = 8.dp)) {
        val y = size.height / 2
        drawLine(
            color,
            Offset(1.dp.toPx(), y),
            Offset(size.width - 1.dp.toPx(), y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
        )
    }
}

/** A legend entry: the line key and what it stands for. */
@Composable
fun ChartKey(color: Color, label: String, modifier: Modifier = Modifier, dashed: Boolean = false, textColor: Color = Color.Unspecified) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LineKey(color, dashed = dashed)
        Spacer(Modifier.width(AppSpacing.xs + AppSpacing.xxs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant })
    }
}

/**
 * What a chart is pointing at, above it: a small label and the value, the value the stronger of the two (the reader came
 * for the number). [key] sits at the end (the legend for a reference line).
 */
@Composable
fun ChartReadout(label: String, value: String, modifier: Modifier = Modifier, key: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
                maxLines = 1,
            )
        }
        if (key != null) {
            Spacer(Modifier.width(AppSpacing.md))
            Box(Modifier.padding(bottom = AppSpacing.xs)) { key() }
        }
    }
}

/**
 * A figure that sums a chart up, under it: a small icon on a tinted disc, what the figure is, then the figure itself large
 * (the reader came for the number), and an optional [detail] under it ("a day", "on 9 Oct").
 */
@Composable
fun ChartStatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier, detail: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(AppRadius.rLg)
            .background(scheme.surfaceContainerHighest)
            .padding(AppSpacing.lg)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).background(scheme.primary.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(AppSpacing.sm))
            Text(label, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
            color = scheme.onSurface,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = MaterialTheme.typography.headlineSmall.fontSize),
        )
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Two [ChartStatTile]s side by side, equally wide. */
@Composable
fun ChartStats(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        first(Modifier.weight(1f).fillMaxHeight())
        second(Modifier.weight(1f).fillMaxHeight())
    }
}
