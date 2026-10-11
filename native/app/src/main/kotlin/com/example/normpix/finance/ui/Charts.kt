package com.example.normpix.finance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min

/*
 * Small, dependency-free charts for Insights. Each chart only paints; the
 * numbers it shows are always also available as text (a caller-provided
 * semantics label plus the lists next to it), so nothing depends on reading a
 * shape or a color.
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

class BarGroup(
    /** Axis label under the group ("Sep"). */
    val label: String,
    /** One value per series, in series order. */
    val values: List<Long>,
)

/**
 * Grouped vertical bars (e.g. income vs. spending per month) with axis labels
 * as real text, so they scale with the user's font size.
 */
@Composable
fun GroupedBarChart(
    groups: List<BarGroup>,
    colors: List<Color>,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    /** Index of the group to emphasize (others are drawn lighter). */
    highlight: Int? = null,
    height: Dp = 160.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall
    val progress by rememberGrowIn()
    val baseline = scheme.outlineVariant
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = semanticsLabel
            role = Role.Image
        },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (groups.isEmpty()) return@Canvas
            val maxValue = groups.maxOf { g -> g.values.maxOrNull() ?: 0L }
            drawLine(baseline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
            if (maxValue <= 0) return@Canvas
            val slot = size.width / groups.size
            val series = colors.size
            val barWidth = min(18.dp.toPx(), slot * 0.7f / series)
            val barGap = 3.dp.toPx()
            val groupWidth = series * barWidth + (series - 1) * barGap
            val radius = 4.dp.toPx()
            groups.forEachIndexed { g, group ->
                val left = g * slot + (slot - groupWidth) / 2
                val dim = highlight != null && g != highlight
                for (s in 0 until series) {
                    val value = group.values.getOrElse(s) { 0L }
                    if (value <= 0) continue
                    val h = max(2.dp.toPx(), size.height * value / maxValue * progress)
                    val x = left + s * (barWidth + barGap)
                    drawTopRoundedBar(if (dim) colors[s].copy(alpha = 0.55f) else colors[s], x, size.height - h, barWidth, h, radius)
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.xs))
        Row(Modifier.fillMaxWidth()) {
            groups.forEachIndexed { i, group ->
                Text(
                    group.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    style = labelStyle,
                    color = if (i == highlight) scheme.onSurface else scheme.onSurfaceVariant,
                    fontWeight = if (i == highlight) FontWeight.Bold else null,
                )
            }
        }
    }
}

/** One bar per day of a month, with a dashed average line. */
@Composable
fun DailyBarChart(
    /** Index 0 = day 1. */
    values: List<Long>,
    color: Color,
    semanticsLabel: String,
    modifier: Modifier = Modifier,
    /** Drawn as a reference line when set. */
    average: Long? = null,
    height: Dp = 120.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall
    val progress by rememberGrowIn()
    val guide = scheme.outline
    val baseline = scheme.outlineVariant
    val days = values.size
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = semanticsLabel
            role = Role.Image
        },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            drawLine(baseline, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1f)
            val maxValue = values.maxOrNull() ?: 0L
            if (maxValue <= 0 || values.isEmpty()) return@Canvas
            val slot = size.width / values.size
            val barWidth = max(2.dp.toPx(), slot * 0.6f)
            val radius = 2.dp.toPx()
            values.forEachIndexed { i, value ->
                if (value <= 0) return@forEachIndexed
                val h = max(2.dp.toPx(), size.height * value / maxValue * progress)
                drawTopRoundedBar(color, i * slot + (slot - barWidth) / 2, size.height - h, barWidth, h, radius)
            }
            if (average != null && average > 0 && average <= maxValue) {
                val y = size.height - size.height * average / maxValue
                var x = 0f
                val dash = 4.dp.toPx()
                while (x < size.width) {
                    drawLine(guide, Offset(x, y), Offset(min(x + dash, size.width), y), strokeWidth = 1f)
                    x += dash * 2
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.xs))
        Row(Modifier.fillMaxWidth()) {
            for (day in listOf(1, 8, 15, 22)) {
                Text("$day", style = labelStyle, color = scheme.onSurfaceVariant, modifier = Modifier.weight(7f))
            }
            Text(
                "$days",
                style = labelStyle,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(max(1, days - 28).toFloat()),
            )
        }
    }
}

private fun DrawScope.drawTopRoundedBar(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float) {
    val r = minOf(radius, width / 2, height)
    // Rounded top corners only, as one filled shape: two overlapping pieces would double up a translucent bar's color
    // along the overlap and leave a darker band at its foot.
    val bar = Path().apply {
        addRoundRect(RoundRect(Rect(Offset(x, y), Size(width, height)), topLeft = CornerRadius(r, r), topRight = CornerRadius(r, r)))
    }
    drawPath(bar, color)
}

/** A color swatch + label, for chart legends. */
@Composable
fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(AppRadius.rXs).background(color))
        Spacer(Modifier.width(AppSpacing.sm))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
