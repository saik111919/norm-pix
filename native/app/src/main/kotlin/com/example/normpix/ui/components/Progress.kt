package com.example.normpix.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.star
import androidx.graphics.shapes.TransformResult
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.settings.ProgressStyle
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Progress the way Material 3 Expressive draws it, as the user's choice (Settings > Appearance > Progress style):
 *   Wavy - a bar that fills with a moving wave, and a loader that morphs between shapes;
 *   Flat - the straight bar and the round spinner Material has always had.
 * Every place the app shows progress goes through [AppLinearProgress] or [AppLoadingIndicator], so the choice is made once.
 * The dimensions are Material's own (the progress-indicator and loading-indicator specs).
 */

/** The progress style in force: the user's choice, wavy where there are no preferences (previews, a bare test). */
@Composable
fun rememberProgressStyle(): ProgressStyle =
    LocalAppPreferences.current?.state?.collectAsState()?.value?.progressStyle ?: ProgressStyle.Wavy

private object LinearSpec {
    val Thickness = 4.dp

    /** The thickest a wavy bar is drawn; a thicker flat bar (the Home budget bar) keeps its weight as far as this. */
    val MaxThickness = 8.dp
    val Amplitude = 3.dp
    val Wavelength = 40.dp
    val IndeterminateWavelength = 20.dp

    /** Between the filled part and the track, and the dot that ends the track. */
    val Gap = 4.dp
    val StopSize = 4.dp

    /** Material's default length, used where the caller does not give the bar one. */
    val Width = 240.dp

    /** Below this the wave is still rising; above 1 - RAMP_OUT it settles into a straight line, so empty and full are plain. */
    const val RAMP_IN = 0.1f
    const val RAMP_OUT = 0.05f
}

/**
 * A bar for something with a known amount done ([progress], 0..1), or - with a null [progress] - for something that is
 * under way with no way to say how far. The [thickness] is the bar's weight; wavy bars add the wave's height to it.
 *
 * [calm] keeps a wavy bar straight (it still has the track's gap and its end dot): for the many small bars of a list,
 * where a wave on every row is noise - the wave is for a bar that stands on its own. It changes nothing in the flat style.
 */
@Composable
fun AppLinearProgress(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    thickness: Dp = LinearSpec.Thickness,
    style: ProgressStyle = rememberProgressStyle(),
    calm: Boolean = false,
) {
    if (style == ProgressStyle.Flat) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress().coerceIn(0f, 1f) },
                modifier = modifier.height(thickness).clip(AppRadius.rFull),
                color = color,
                trackColor = trackColor,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        } else {
            LinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)
        }
        return
    }

    // The wave travels (one wavelength a second) unless animations are off; the clock itself is cancelled under test.
    val clock = rememberFrameClockMs()
    WavyLinearProgress(modifier, progress, color, trackColor, thickness, travels = !rememberReducedMotion(), calm = calm) { clock.value }
}

/** The wavy bar at the time [clockMs] says. */
@Composable
internal fun WavyLinearProgress(
    modifier: Modifier,
    progress: (() -> Float)?,
    color: Color,
    trackColor: Color,
    thickness: Dp,
    travels: Boolean,
    calm: Boolean = false,
    clockMs: () -> Long,
) {
    val stroke = thickness.coerceIn(LinearSpec.Thickness, LinearSpec.MaxThickness)
    // A calm bar has no wave to make room for.
    val waveHeight = if (calm) 0.dp else LinearSpec.Amplitude * 2
    val wavelength = if (progress == null) LinearSpec.IndeterminateWavelength else LinearSpec.Wavelength
    val path = remember { Path() }
    Canvas(
        modifier
            .size(width = LinearSpec.Width, height = stroke + waveHeight)
            .semantics {
                progressBarRangeInfo =
                    if (progress == null) ProgressBarRangeInfo.Indeterminate
                    else ProgressBarRangeInfo(progress().coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        val waveLength = wavelength.toPx()
        val now = clockMs()
        val phase = if (travels) (now / 1000f * waveLength) % waveLength else 0f
        val p = progress?.invoke()?.coerceIn(0f, 1f)
        drawWavyLinear(
            segments = if (p == null) indeterminateSegments(now) else listOfNotNull(if (p > 0f) BarSegment(0f, p) else null),
            amplitudeFactor = if (calm) 0f else if (p == null) 1f else waveAmplitudeFactor(p),
            phase = phase,
            wavelength = waveLength,
            color = color,
            trackColor = trackColor,
            stroke = stroke.toPx(),
            amplitude = LinearSpec.Amplitude.toPx(),
            gap = LinearSpec.Gap.toPx(),
            stopSize = LinearSpec.StopSize.toPx(),
            path = path,
        )
    }
}

/** A stretch of the bar, as fractions of its length. */
internal class BarSegment(val tail: Float, val head: Float)

/** How tall the wave is at [progress]: it rises over the first tenth and settles over the last twentieth. */
internal fun waveAmplitudeFactor(progress: Float): Float =
    min(min(progress / LinearSpec.RAMP_IN, (1f - progress) / LinearSpec.RAMP_OUT), 1f).coerceIn(0f, 1f)

/** Material's indeterminate timing: two stretches run along the bar, the second following the first, every 1.75 seconds. */
private const val INDETERMINATE_CYCLE_MS = 1750
private val IndeterminateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private fun lineAt(t: Int, delayMs: Int, durationMs: Int): Float =
    IndeterminateEasing.transform(((t - delayMs) / durationMs.toFloat()).coerceIn(0f, 1f))

internal fun indeterminateSegments(elapsedMs: Long): List<BarSegment> {
    val t = (elapsedMs % INDETERMINATE_CYCLE_MS).toInt()
    return listOf(
        BarSegment(tail = lineAt(t, 250, 1000), head = lineAt(t, 0, 1000)),
        BarSegment(tail = lineAt(t, 900, 850), head = lineAt(t, 650, 850)),
    ).filter { it.head > it.tail }
}

/**
 * Draws the bar: the track between and beyond [segments] as straight round-capped lines with [gap] between them and the
 * filled parts, the filled parts as a wave of [amplitude] x [amplitudeFactor], and a dot ending the track.
 */
private fun DrawScope.drawWavyLinear(
    segments: List<BarSegment>,
    amplitudeFactor: Float,
    phase: Float,
    wavelength: Float,
    color: Color,
    trackColor: Color,
    stroke: Float,
    amplitude: Float,
    gap: Float,
    stopSize: Float,
    path: Path,
) {
    val inset = stroke / 2f
    val usable = (size.width - stroke).coerceAtLeast(0f)
    val cy = size.height / 2f
    fun x(fraction: Float) = inset + fraction * usable
    // From the end of a filled part to the start of the track: its round cap, the gap, the track's round cap.
    val clearance = stroke + gap

    fun track(from: Float, to: Float, afterFilled: Boolean, beforeFilled: Boolean) {
        val start = x(from) + if (afterFilled) clearance else 0f
        val end = x(to) - if (beforeFilled) clearance else 0f
        if (end <= start) return
        drawLine(trackColor, Offset(start, cy), Offset(end, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        if (!beforeFilled && end - start >= stopSize) drawCircle(color, stopSize / 2f, Offset(size.width - stopSize / 2f, cy))
    }

    var cursor = 0f
    var afterFilled = false
    for (segment in segments.sortedBy { it.tail }) {
        track(cursor, segment.tail, afterFilled, beforeFilled = true)
        cursor = max(cursor, segment.head)
        afterFilled = true
    }
    track(cursor, 1f, afterFilled, beforeFilled = false)

    val step = min(wavelength / 16f, 2.dp.toPx())
    val k = (2.0 * PI / wavelength).toFloat()
    for (segment in segments) {
        val from = x(segment.tail)
        val to = x(segment.head)
        fun y(x: Float) = cy + amplitude * amplitudeFactor * sin(k * (x - phase))
        path.rewind()
        path.moveTo(from, y(from))
        var at = from + step
        while (at < to) {
            path.lineTo(at, y(at))
            at += step
        }
        path.lineTo(to, y(to))
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Milliseconds since this started drawing, ticking every frame; it stays at 0 where animations are cancelled (tests). */
@Composable
private fun rememberFrameClockMs(): State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) withInfiniteAnimationFrameNanos { clock.longValue = (it - start) / 1_000_000 }
    }
    return clock
}

// --- the loader --------------------------------------------------------------------------------------------------

private object LoaderSpec {
    /** Material's loading indicator: shapes 38dp across in a 48dp container. */
    val Container = 48.dp
    const val ACTIVE_FRACTION = 38f / 48f

    /** Each shape holds for this long, then the next one grows out of it. */
    const val STEP_MS = 650
    const val MORPH_MS = 420
    const val SPIN_MS = 4666
}

/** Where the loader's shapes are at a point in time: the outline to fill (unit radius) and how far it has turned. */
internal object LoaderGeometry {
    private val rounded = CornerRounding(0.5f)

    /** Soft burst, nine-sided cookie, pentagon, pill, sunny, four-sided cookie, oval: the loading indicator's sequence. */
    private val shapes: List<RoundedPolygon> by lazy {
        listOf(
            RoundedPolygon.star(10, innerRadius = 0.7f, rounding = CornerRounding(0.12f), innerRounding = CornerRounding(0.4f)),
            RoundedPolygon.star(9, innerRadius = 0.82f, rounding = rounded),
            RoundedPolygon(5, rounding = CornerRounding(0.2f)),
            RoundedPolygon.pill(width = 2f, height = 1.15f),
            RoundedPolygon.star(8, innerRadius = 0.8f, rounding = CornerRounding(0.2f)),
            RoundedPolygon.star(4, innerRadius = 0.66f, rounding = CornerRounding(0.4f)),
            RoundedPolygon.circle(numVertices = 8).transformed { x, y -> TransformResult(x, y * 0.66f) },
        )
    }

    private val morphs: List<Morph> by lazy { shapes.indices.map { Morph(shapes[it], shapes[(it + 1) % shapes.size]) } }
    private val ease = CubicBezierEasing(0.3f, 1.3f, 0.5f, 1f)

    val shapeCount: Int get() = shapes.size

    /** Fills [into] with the outline [elapsedMs] into the loop and returns how many degrees it has turned. */
    fun at(elapsedMs: Long, into: Path): Float {
        val step = (elapsedMs / LoaderSpec.STEP_MS).toInt()
        val within = (elapsedMs % LoaderSpec.STEP_MS).toFloat()
        val grown = ease.transform((within / LoaderSpec.MORPH_MS).coerceIn(0f, 1f))
        val cubics = morphs[step % morphs.size].asCubics(grown)
        into.rewind()
        if (cubics.isNotEmpty()) {
            into.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
            for (c: Cubic in cubics) into.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
            into.close()
        }
        // A steady turn, and a quarter turn more with every shape.
        return elapsedMs * 360f / LoaderSpec.SPIN_MS + (step + grown) * 90f
    }
}

/**
 * Something is happening and there is no telling how long it will take. Wavy: Material's loading indicator, a shape that
 * turns and morphs into the next. Flat: the round spinner, [strokeWidth] thick. Size it with [modifier] (48dp by default
 * wavy, 40dp flat).
 */
@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp,
    style: ProgressStyle = rememberProgressStyle(),
) {
    if (style == ProgressStyle.Flat) {
        CircularProgressIndicator(modifier = modifier, color = color, strokeWidth = strokeWidth)
        return
    }
    val clock = rememberFrameClockMs()
    MorphingLoader(modifier, color) { clock.value }
}

/** The morphing loader at the time [clockMs] says. */
@Composable
internal fun MorphingLoader(modifier: Modifier, color: Color, clockMs: () -> Long) {
    val path = remember { Path() }
    Canvas(modifier.size(LoaderSpec.Container).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
        val degrees = LoaderGeometry.at(clockMs(), path)
        val radius = size.minDimension / 2f * LoaderSpec.ACTIVE_FRACTION
        translate(size.width / 2f, size.height / 2f) {
            rotate(degrees, Offset.Zero) { scale(radius, radius, Offset.Zero) { drawPath(path, color) } }
        }
    }
}
