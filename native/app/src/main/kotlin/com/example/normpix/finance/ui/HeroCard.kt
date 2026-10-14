package com.example.normpix.finance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.example.normpix.ui.theme.AppSpacing

/*
 * The hero: the one expressive object on Home and Insights, as a bento group. On top, a vivid primary card holds the
 * number the screen exists for - the amount as the loudest thing on screen and a pill saying how it compares - with a
 * cookie shape bleeding off its corner. Under it, joined to it, two tiles in the secondary and tertiary containers carry
 * one supporting figure each. Corners are large on the outside of the group and small where its pieces meet, so it reads
 * as one object made of parts (the same language as the connected buttons under it). Kept to that on purpose: a figure
 * that needs explaining belongs further down the screen, not in the hero.
 */

/** Material 3 Expressive's extra-large-increased corner, for the outside of the group. */
private val OuterCorner = 32.dp

/** Where two pieces of the group meet. */
private val InnerCorner = 8.dp

/** The seam between the pieces. */
private val GroupGap = 4.dp

/** The main card's shape: round outside, tight along the edge the tiles join (round all over when there are none). */
private fun heroMainShape(hasTiles: Boolean): Shape =
    if (hasTiles) {
        RoundedCornerShape(topStart = OuterCorner, topEnd = OuterCorner, bottomStart = InnerCorner, bottomEnd = InnerCorner)
    } else {
        RoundedCornerShape(OuterCorner)
    }

/** A tile's shape from where it sits under the main card: the group's outer corners on the outside, tight ones inside. */
private fun heroTileShape(index: Int, count: Int, stacked: Boolean): Shape {
    val last = index == count - 1
    return if (stacked) {
        val bottom = if (last) OuterCorner else InnerCorner
        RoundedCornerShape(topStart = InnerCorner, topEnd = InnerCorner, bottomStart = bottom, bottomEnd = bottom)
    } else {
        RoundedCornerShape(
            topStart = InnerCorner,
            topEnd = InnerCorner,
            bottomStart = if (index == 0) OuterCorner else InnerCorner,
            bottomEnd = if (last) OuterCorner else InnerCorner,
        )
    }
}

/** Which container a tile is in: the group alternates them so neighbours stay apart. */
enum class HeroTone { Secondary, Tertiary }

/** Where a tile goes in the group: its [shape] and the [modifier] that sizes it there. */
class HeroSlot internal constructor(val shape: Shape, val modifier: Modifier)

/**
 * The hero group: [content] on the main card (tappable when [onClick] is set - Home opens Insights from it), then the
 * [tiles] side by side under it - or, where large text could not fit them across, one above another.
 */
@Composable
fun Hero(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tiles: List<@Composable (HeroSlot) -> Unit> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GroupGap)) {
        HeroSurface(
            color = scheme.primary,
            contentColor = scheme.onPrimary,
            shape = heroMainShape(tiles.isNotEmpty()),
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .heroDecoration(scheme.onPrimary.copy(alpha = 0.08f))
                    .padding(start = AppSpacing.xxl, top = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xl),
                content = content,
            )
        }
        if (tiles.isEmpty()) return@Column
        if (LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE) {
            Column(verticalArrangement = Arrangement.spacedBy(GroupGap)) {
                tiles.forEachIndexed { i, tile -> tile(HeroSlot(heroTileShape(i, tiles.size, stacked = true), Modifier.fillMaxWidth())) }
            }
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(GroupGap)) {
                tiles.forEachIndexed { i, tile ->
                    tile(HeroSlot(heroTileShape(i, tiles.size, stacked = false), Modifier.weight(1f).fillMaxHeight()))
                }
            }
        }
    }
}

@Composable
private fun HeroSurface(
    color: Color,
    contentColor: Color,
    shape: Shape,
    onClick: (() -> Unit)?,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    if (onClick != null) {
        Surface(onClick = onClick, color = color, contentColor = contentColor, shape = shape, modifier = modifier, content = content)
    } else {
        Surface(color = color, contentColor = contentColor, shape = shape, modifier = modifier, content = content)
    }
}

/**
 * A tile of the group, in its [tone]'s container: [content] stacked from the top, padded. Tappable when [onClick] is set
 * (each tile leads to where its figures live).
 */
@Composable
fun HeroTile(slot: HeroSlot, tone: HeroTone, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = when (tone) {
        HeroTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        HeroTone.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    HeroSurface(color = container, contentColor = onContainer, shape = slot.shape, onClick = onClick, modifier = slot.modifier) {
        Column(Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg), content = content)
    }
}

/** The decoration: Material's nine-sided cookie, the same one the loading indicator passes through. */
private val HeroCookie: RoundedPolygon by lazy {
    RoundedPolygon.star(9, innerRadius = 0.82f, rounding = CornerRounding(0.5f))
}

/** A large cookie, turned a little, centred just inside the top-end corner so most of it bleeds off the card. */
private fun Modifier.heroDecoration(color: Color): Modifier = this.drawWithCache {
    val outline = Path()
    val cubics = HeroCookie.cubics
    if (cubics.isNotEmpty()) {
        outline.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
        for (c in cubics) outline.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
        outline.close()
    }
    val radius = 128.dp.toPx()
    val inset = 24.dp.toPx()
    val centreX = if (layoutDirection == LayoutDirection.Ltr) size.width - inset else inset
    onDrawBehind {
        translate(centreX, inset) {
            rotate(18f, pivot = Offset.Zero) {
                scale(radius, radius, pivot = Offset.Zero) { drawPath(outline, color) }
            }
        }
    }
}

/** The line above the amount ("Spent in October"), with room at its end for [trailing] (Home's way on to Insights). */
@Composable
fun HeroEyebrow(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = LocalContentColor.current.copy(alpha = 0.82f),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * The amount, the loudest thing on the screen: large display type at an emphasized weight, shrinking rather than wrapping
 * when it is long. Proportional figures: tabular ones (every digit as wide as a 0) look loose at this size.
 */
@Composable
fun HeroAmount(text: String) {
    val display = MaterialTheme.typography.displayLarge
    Text(
        text,
        style = display.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = display.fontSize),
    )
}

/** Which way spending went, as the arrow the comparison pill leads with: a fact, not a verdict, so no red or green. */
fun trendIcon(change: Double?): ImageVector = when {
    change == null || change == 0.0 -> Icons.Rounded.TrendingFlat
    change < 0 -> Icons.Rounded.TrendingDown
    else -> Icons.Rounded.TrendingUp
}

/** How this compares with before, on a pill washed in the card's own content color: the trend arrow, then the sentence. */
@Composable
fun HeroChangePill(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val ink = LocalContentColor.current
    Row(
        modifier
            .semantics(mergeDescendants = true) {}
            .background(ink.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(start = AppSpacing.xs + AppSpacing.xxs, top = AppSpacing.xs + AppSpacing.xxs, end = AppSpacing.md, bottom = AppSpacing.xs + AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(22.dp).background(ink, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Text(text, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

/**
 * "9% less than this time last month": how spending compares with [basis], in round percent - the one comparison both
 * heroes make, worded the same way.
 */
fun comparedWith(change: Double, basis: String): String {
    val percent = (abs(change) * 100).roundToInt()
    if (percent == 0) return "About the same as $basis"
    return "$percent% ${if (change < 0) "less" else "more"} than $basis"
}

/** A labelled figure on a tile: the label small, the value under it the stronger of the two. */
@Composable
fun HeroFigure(label: String, value: String, modifier: Modifier = Modifier) {
    val ink = LocalContentColor.current
    val type = MaterialTheme.typography
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = type.labelLarge, color = ink.copy(alpha = 0.82f), maxLines = 2)
        Spacer(Modifier.height(AppSpacing.xxs))
        Text(
            value,
            style = type.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR_FIGURES),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = type.titleLarge.fontSize),
        )
    }
}

/**
 * How much of something is used, as Material's determinate ring: the arc from the top, a gap, then the rest of the track.
 * [center] sits in the middle (the percentage).
 */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    thickness: Dp = 6.dp,
    center: (@Composable () -> Unit)? = null,
) {
    val value = progress.coerceIn(0f, 1f)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = thickness.toPx()
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            // Where arc and track meet (at the top and at the arc's end) they keep 4dp apart, past their round caps.
            val circumference = (Math.PI * arc.width).toFloat()
            val gap = if (value <= 0f || value >= 1f) 0f else (stroke + 4.dp.toPx()) / circumference * 360f
            val sweep = 360f * value
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            if (value < 1f && 360f - sweep - gap > 0f) {
                drawArc(trackColor, -90f + sweep + gap / 2, 360f - sweep - gap, useCenter = false, topLeft = topLeft, size = arc, style = style)
            }
            if (value > 0f) {
                drawArc(color, -90f + gap / 2, (sweep - gap).coerceAtLeast(0.1f), useCenter = false, topLeft = topLeft, size = arc, style = style)
            }
        }
        center?.invoke()
    }
}
