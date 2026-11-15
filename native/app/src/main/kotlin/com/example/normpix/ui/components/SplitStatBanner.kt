package com.example.normpix.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * A two-panel summary banner (the owner's reference, 2026-11-07: a "Due date" panel with a progress pill, and a "Total" panel with the
 * figure). Left: what it is about and how far along it is - a title, a line under it, a pill that fills as the end comes near with
 * its words inside the fill, and the two ends of the bar under it. Right: a title and the one figure the banner is for, with a note.
 * The panels are one object made of parts, like Home's hero: big corners on the outside, small ones where they meet, a seam
 * between - and the same tonal container, so the seam is the divider. Fine curved lines sweep across both, in the ink at a whisper.
 * Anything with "how long is left" and "how much" is a banner of this kind: a card's bill, a month's budget.
 */

/** The left panel: what the banner is about, and how far along that is. */
@Immutable
class BannerProgress(
    val title: String,
    /** The line under the title ("Tomorrow"). */
    val subtitle: String,
    /** How much of the way has gone, 0..1. */
    val fraction: Float,
    /** The words inside the pill's fill ("8 days left"). */
    val label: String,
    /** The bar's two ends, under it ("5 Nov", "20 Nov"). */
    val from: String,
    val to: String,
)

/** The right panel: the one figure, with a title above it and a note under it. */
@Immutable
class BannerFigure(val title: String, val figure: String, val note: String? = null)

/**
 * The banner. [spoken] is the whole of it for a screen reader (the panels are drawn, not read one by one). With an [onClick] the
 * whole banner is one button, [onClickLabel] saying what it does. A [tone] puts the pill in that status colour (a bill due within
 * days is a warning); without one the pill is the theme's primary.
 */
@Composable
fun SplitStatBanner(
    progress: BannerProgress,
    figure: BannerFigure,
    spoken: String,
    modifier: Modifier = Modifier,
    tone: StatusTone? = null,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    /**
     * The banner with nothing printed on it: what stands behind the front one in a stack of them (a `DeckStack`'s plate) - the same
     * size by construction, with nothing to read or press.
     */
    plate: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val status = tone?.resolve()
    val fill = status?.color ?: scheme.primary
    val onFill = status?.onColor ?: scheme.onPrimary
    val container = scheme.secondaryContainer
    val ink = scheme.onSecondaryContainer
    val stacked = LocalDensity.current.fontScale > StackAtFontScale
    val shown = if (plate) 0f else 1f

    val quiet = quietInk(ink, container)

    val left: @Composable (Shape, Modifier, Boolean) -> Unit = { shape, panel, fillHeight ->
        Panel(shape, container, ink, onClick, panel, fillHeight, contentAlpha = shown) {
            Column {
                Text(progress.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(progress.subtitle, style = MaterialTheme.typography.bodyMedium, color = quiet)
            }
            // However short the panel, the title's lines and the pill are this far apart.
            Spacer(Modifier.height(AppSpacing.md))
            PillBody(BannerBar(progress.fraction, progress.label, progress.from, progress.to), fill, onFill, ink, quiet)
        }
    }
    val right: @Composable (Shape, Modifier, Boolean) -> Unit = { shape, panel, fillHeight ->
        Panel(shape, container, ink, onClick, panel, fillHeight, contentAlpha = shown) {
            FigureBody(figure, quiet)
        }
    }

    val press = onClick
    val semantics = Modifier.semantics(mergeDescendants = true) {
        contentDescription = spoken
        if (press != null) {
            role = Role.Button
            // `this.`: the parameter is also called onClick, and must not be what this resolves to.
            this.onClick(label = onClickLabel) {
                press()
                true
            }
        }
    }
    if (stacked) {
        // Large text: one panel above the other, each as wide as the banner, rather than two cramped columns.
        Column(modifier.fillMaxWidth().then(semantics), verticalArrangement = Arrangement.spacedBy(Seam)) {
            left(StackedTop, Modifier.fillMaxWidth(), false)
            right(StackedBottom, Modifier.fillMaxWidth(), false)
        }
    } else {
        Row(
            modifier.fillMaxWidth().height(IntrinsicSize.Min).then(semantics),
            horizontalArrangement = Arrangement.spacedBy(Seam),
        ) {
            left(SideStart, Modifier.weight(ProgressWeight).fillMaxHeight(), true)
            right(SideEnd, Modifier.weight(FigureWeight).fillMaxHeight(), true)
        }
    }
}

/**
 * The banner with a figure in each panel, side by side and as wide as each other (a month's cost and a year's; the money a
 * forecast ends with and the least it gets to): the same panels, seam, corners and curved lines, for two things that are both
 * just an amount. [spoken] is the whole of it for a screen reader; with an [onClick] the whole banner is one button.
 */
@Composable
fun SplitStatBanner(
    first: BannerFigure,
    second: BannerFigure,
    spoken: String,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val container = scheme.secondaryContainer
    val ink = scheme.onSecondaryContainer
    val stacked = LocalDensity.current.fontScale > StackAtFontScale
    val quiet = quietInk(ink, container)

    val left: @Composable (Shape, Modifier, Boolean) -> Unit = { shape, panel, fillHeight ->
        Panel(shape, container, ink, onClick, panel, fillHeight) { FigureBody(first, quiet) }
    }
    val right: @Composable (Shape, Modifier, Boolean) -> Unit = { shape, panel, fillHeight ->
        Panel(shape, container, ink, onClick, panel, fillHeight) { FigureBody(second, quiet) }
    }
    val press = onClick
    val semantics = Modifier.semantics(mergeDescendants = true) {
        contentDescription = spoken
        if (press != null) {
            role = Role.Button
            // `this.`: the parameter is also called onClick, and must not be what this resolves to.
            this.onClick(label = onClickLabel) {
                press()
                true
            }
        }
    }
    if (stacked) {
        Column(modifier.fillMaxWidth().then(semantics), verticalArrangement = Arrangement.spacedBy(Seam)) {
            left(StackedTop, Modifier.fillMaxWidth(), false)
            right(StackedBottom, Modifier.fillMaxWidth(), false)
        }
    } else {
        Row(
            modifier.fillMaxWidth().height(IntrinsicSize.Min).then(semantics),
            horizontalArrangement = Arrangement.spacedBy(Seam),
        ) {
            left(SideStart, Modifier.weight(FigureWeight).fillMaxHeight(), true)
            right(SideEnd, Modifier.weight(FigureWeight).fillMaxHeight(), true)
        }
    }
}

/** [ink] at [SecondaryAlpha] over [container], or in full where that would not read at 4.5:1 (some accents' containers are near the limit). */
internal fun quietInk(ink: Color, container: Color): Color =
    readableOn(container, ink.copy(alpha = SecondaryAlpha).compositeOver(container), ink)

/** One panel: the tonal container with the curved lines, its content spread from the top to the bottom. */
@Composable
internal fun Panel(
    shape: Shape,
    container: Color,
    ink: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    /** Side by side the panel is as tall as its neighbour; stacked it is as tall as what it holds. */
    fillHeight: Boolean,
    /** A hairline round the panel (the status banners edge theirs in their colour). */
    border: BorderStroke? = null,
    /** How much of what is on the panel shows: a banner's plate behind the front one is the panel with none of it. */
    contentAlpha: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The banner is one button and one spoken thing: what the panels hold is not read, nor are they buttons of their own.
    val panel = modifier.clearAndSetSemantics {}
    val body: @Composable () -> Unit = {
        Column(
            (if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .graphicsLayer { alpha = contentAlpha }
                .curvedLines(ink.copy(alpha = LineAlpha))
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
            content = content,
        )
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = panel, shape = shape, color = container, contentColor = ink, border = border, content = body)
    } else {
        Surface(modifier = panel, shape = shape, color = container, contentColor = ink, border = border, content = body)
    }
}

/**
 * A bar for a banner: how far something has gone ([fraction]) and what that is in words ([label], "83% used" - what a screen reader
 * says). The summary banner draws it as a pill with [label] inside and - if given - its two ends under it; a status banner
 * (`BannerStack`) as a ring with [short] inside ("83%"; the percentage of [fraction] when not given).
 */
@Immutable
class BannerBar(
    val fraction: Float,
    val label: String,
    val from: String? = null,
    val to: String? = null,
    val short: String? = null,
)

/**
 * A status banner's bar as a ring, beside its lines (owner, 2026-11-15, on a budget banner's full-width pill: "move this to left
 * place and make it circle", so it takes less room): the arc goes [BannerBar.fraction] of the way round from the top in [fill],
 * over a faint track of [ink], with the bar's short words in [ink] inside. It is [RingSize] across, or as much more as its words
 * need - "120%" at large text still fits inside the ring, never over it.
 */
@Composable
internal fun BannerRing(bar: BannerBar, fill: Color, ink: Color, modifier: Modifier = Modifier) {
    val track = ink.copy(alpha = TrackAlpha)
    val words = bar.short ?: "${(bar.fraction.coerceIn(0f, 1f) * 100).roundToInt()}%"
    Layout(
        content = {
            Text(
                words,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = modifier.drawBehind {
            val stroke = RingStroke.toPx()
            val inset = Offset(stroke / 2, stroke / 2)
            val arc = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(track, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = inset, size = arc, style = Stroke(stroke))
            val sweep = 360f * bar.fraction.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(
                    fill,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = inset,
                    size = arc,
                    style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        },
        measurePolicy = RingPolicy,
    )
}

/**
 * The ring's size: a square as wide as its words plus its stroke and a little room each side, never less than [RingSize]. It answers
 * the intrinsic questions itself: the banner's panels are made one height through them, and the default answers (made by measuring
 * with a stand-in width) made the ring - and the panel round it - as tall as the screen (rendered and seen, 2026-11-15).
 */
private val RingPolicy = object : MeasurePolicy {
    private fun IntrinsicMeasureScope.side(textWidth: Int, textHeight: Int): Int =
        max(RingSize.roundToPx(), max(textWidth, textHeight) + (RingStroke + AppSpacing.xs).roundToPx() * 2)


    private fun IntrinsicMeasureScope.sideOf(measurables: List<IntrinsicMeasurable>): Int {
        val words = measurables[0]
        val width = words.maxIntrinsicWidth(Constraints.Infinity)
        return side(width, words.minIntrinsicHeight(width))
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val text = measurables[0].measure(Constraints())
        val side = side(text.width, text.height)
        return layout(side, side) { text.placeRelative((side - text.width) / 2, (side - text.height) / 2) }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = sideOf(measurables)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) = sideOf(measurables)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = sideOf(measurables)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = sideOf(measurables)
}

/** How wide a banner's ring is (it grows for longer words): in the icon's place, a little larger than its disc. How thick its arc. */
private val RingSize = AppSpacing.xxxl + AppSpacing.sm
private val RingStroke = AppSpacing.xs

/** The right panel's contents: its title, then the figure and its note at the bottom. Shared with the status banners. */
@Composable
internal fun FigureBody(figure: BannerFigure, quiet: Color, aside: (@Composable () -> Unit)? = null) {
    val title = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    if (aside == null) {
        Text(figure.title, style = title, maxLines = 1)
    } else {
        // Something beside the title at the panel's top end: a status banner's ring.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(figure.title, style = title, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(AppSpacing.sm))
            aside()
        }
    }
    Spacer(Modifier.height(AppSpacing.md))
    Column {
        val style = MaterialTheme.typography.headlineSmall
        Text(
            figure.figure,
            style = style.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = style.fontSize),
        )
        if (figure.note != null) {
            Text(figure.note, style = MaterialTheme.typography.labelMedium, color = quiet, maxLines = 2)
        }
    }
}

/** The left panel's bar: the pill, and its two ends under it where there are any. */
@Composable
internal fun PillBody(bar: BannerBar, fill: Color, onFill: Color, ink: Color, quiet: Color) {
    Column {
        CyclePill(bar.fraction, bar.label, fill, onFill, track = ink.copy(alpha = TrackAlpha))
        if (bar.from != null || bar.to != null) {
            Spacer(Modifier.height(AppSpacing.xs))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bar.from.orEmpty(), style = MaterialTheme.typography.labelMedium, color = quiet, maxLines = 1)
                Text(bar.to.orEmpty(), style = MaterialTheme.typography.labelMedium, color = quiet, maxLines = 1)
            }
        }
    }
}

/**
 * The pill: a track, and on it a fill that has gone [fraction] of the way - never narrower than its own words, which sit at the
 * fill's end. Taller than 28 dp where the text is.
 */
@Composable
internal fun CyclePill(fraction: Float, label: String, fill: Color, onFill: Color, track: Color, modifier: Modifier = Modifier) {
    Layout(
        content = {
            Spacer(Modifier.background(fill, AppRadius.rFull))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = onFill,
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = modifier.fillMaxWidth().background(track, AppRadius.rFull),
    ) { measurables, constraints ->
        val pad = AppSpacing.md.roundToPx()
        val text = measurables[1].measure(Constraints())
        val width = constraints.maxWidth
        val height = max(PillHeight.roundToPx(), text.height + AppSpacing.sm.roundToPx())
        val fillWidth = max((width * fraction.coerceIn(0f, 1f)).roundToInt(), text.width + pad * 2).coerceAtMost(width)
        val filler = measurables[0].measure(Constraints.fixed(fillWidth, height))
        layout(width, height) {
            filler.placeRelative(0, 0)
            text.placeRelative(max(pad, fillWidth - pad - text.width), (height - text.height) / 2)
        }
    }
}

/** Fine arcs about a point beyond the panel's top-end corner, as far as its farthest corner: the banner's texture. */
internal fun Modifier.curvedLines(color: Color): Modifier = drawBehind {
    val centre = Offset(size.width * 0.9f, -size.height * 0.35f)
    val reach = hypot(max(centre.x, size.width - centre.x), size.height - centre.y)
    val stroke = Stroke(width = 1.dp.toPx())
    val step = AppSpacing.lg.toPx()
    var radius = size.height * 0.45f
    while (radius < reach) {
        drawCircle(color, radius, centre, style = stroke)
        radius += step
    }
}

private val PillHeight = 28.dp

/** Where the large-text banner stacks: the hero's tiles stack at the same scale. */
internal const val StackAtFontScale = 1.3f

/** The second line and the pill's ends: quieter than the title, still above 4.5:1 on the container (`SplitStatBannerTest` checks every theme). */
internal const val SecondaryAlpha = 0.78f

/** The pill's empty part. */
internal const val TrackAlpha = 0.14f

/** The curved lines: a whisper. */
internal const val LineAlpha = 0.07f

/** The progress panel is the wider of the two, as in the reference. */
internal const val ProgressWeight = 1.35f
internal const val FigureWeight = 1f

internal val Seam = AppSpacing.xs

internal val Outer = AppRadius.xxl
internal val Inner = AppRadius.sm
internal val SideStart = RoundedCornerShape(topStart = Outer, bottomStart = Outer, topEnd = Inner, bottomEnd = Inner)
internal val SideEnd = RoundedCornerShape(topStart = Inner, bottomStart = Inner, topEnd = Outer, bottomEnd = Outer)
internal val StackedTop = RoundedCornerShape(topStart = Outer, topEnd = Outer, bottomStart = Inner, bottomEnd = Inner)
internal val StackedBottom = RoundedCornerShape(topStart = Inner, topEnd = Inner, bottomStart = Outer, bottomEnd = Outer)
