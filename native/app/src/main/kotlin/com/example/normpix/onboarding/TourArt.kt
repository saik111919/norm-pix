package com.example.normpix.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.star
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.normpix.ui.components.AssetImage
import com.example.normpix.ui.components.LocalLiveMotion
import com.example.normpix.ui.components.rememberLiveClock
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.sin

/*
 * The welcome tour's pictures, as stickers: Microsoft's Fluent 3D emoji (MIT, the app's own set in assets/fluent3d) each on a pad
 * of one of Material 3 Expressive's shapes - the nine-sided cookie of Home's hero, the sunny, the four-sided cookie, the soft burst,
 * a pill - in the theme's containers, so the pads follow Light, Dark, Pure and the wallpaper's colours while the pictures keep
 * their own. The owner chose this look on 2026-11-04 over a flat drawn scene and a single morphing badge.
 *
 * Motion, none of it when animations are off: the stickers pop onto their pads one after another when a step comes into view;
 * they lag a little behind the page as it is swiped, the far ones more (depth); and on the step at rest they bob gently, but only
 * where endless motion may run at all (the app itself, see LocalLiveMotion) and at the live clock's 30 frames a second.
 */

/** What a step's picture is about: one for each step of the tour. */
internal enum class TourScene { Look, Profile, Reminders, Start }

/** The picture's tag, for tests. */
internal const val TOUR_ART_TAG = "tour_art"

/** How tall the picture is on a phone; the stickers are placed by fractions of its box. */
internal val TourArtHeight = 168.dp

/**
 * Where a step stands in the pager, for its picture's motion: [offset] is how far it is from the middle (0 there, 1 a whole page
 * to the left, -1 to the right) and [current] says it is the step at rest.
 */
internal class StepPlace(val offset: () -> Float, val current: Boolean)

/** The place of the step being composed; the tour provides it around each page. */
internal val LocalStepPlace = compositionLocalOf { StepPlace({ 0f }, current = true) }

/** Which of the theme's colours a pad wears. */
private enum class Pad { Primary, Secondary, Tertiary, Ink, FaintInk }

private fun Pad.color(scheme: ColorScheme): Color = when (this) {
    Pad.Primary -> scheme.primaryContainer
    Pad.Secondary -> scheme.secondaryContainer
    Pad.Tertiary -> scheme.tertiaryContainer
    Pad.Ink -> scheme.onPrimary.copy(alpha = 0.75f)
    Pad.FaintInk -> scheme.onPrimary.copy(alpha = 0.45f)
}

/**
 * One sticker, centred at ([x], [y]) of the picture's box: a [shape] [radius] across in [pad]'s colour, turned [turn] degrees, with
 * the 3D picture of [emoji] ([size] across, turned [tilt]) on it. A sticker without a shape is the picture alone; one without a
 * picture is a sparkle. [depth] is how far it lags behind the page as it slides (0 moves with it).
 */
private class Sticker(
    val x: Float,
    val y: Float,
    val shape: RoundedPolygon? = null,
    val radius: Dp = 0.dp,
    val pad: Pad = Pad.Primary,
    val turn: Float = 0f,
    val emoji: String? = null,
    val size: Dp = 0.dp,
    val tilt: Float = 0f,
    val depth: Float = 0.2f,
) {
    /** The box it is drawn in: wide enough for its pad (a pill is twice its radius wide) and its picture. */
    val box: Dp get() = max(radius * 2, size)
}

private object Shapes {
    val cookie9 = RoundedPolygon.star(9, innerRadius = 0.82f, rounding = CornerRounding(0.5f))
    val cookie4 = RoundedPolygon.star(4, innerRadius = 0.66f, rounding = CornerRounding(0.4f))
    val sunny = RoundedPolygon.star(8, innerRadius = 0.8f, rounding = CornerRounding(0.2f))
    val burst = RoundedPolygon.star(10, innerRadius = 0.7f, rounding = CornerRounding(0.12f), innerRounding = CornerRounding(0.4f))
    val pill = RoundedPolygon.pill(width = 2f, height = 1.15f)
    val sparkle = RoundedPolygon.star(4, innerRadius = 0.32f, rounding = CornerRounding(0.12f))
}

/** The pictures are 128 px: the main one stays near 80 dp, where a phone's 2.5-3x screen enlarges it least. */
private val MainSize = 80.dp
private val MainPad = 70.dp

/** A step's stickers, the main one first (it pops in first, and the Profile step puts the avatar on its pad instead). */
private fun stickersOf(scene: TourScene): List<Sticker> = when (scene) {
    // Welcome, and the look: the colours being chosen, a card and a coin.
    TourScene.Look -> listOf(
        Sticker(0.36f, 0.52f, Shapes.cookie9, MainPad, Pad.Primary, turn = 14f, emoji = "1f3a8", size = MainSize, tilt = -8f, depth = 0.12f),
        Sticker(0.75f, 0.31f, Shapes.cookie4, 40.dp, Pad.Tertiary, turn = -12f, emoji = "1f4b3", size = 48.dp, tilt = 10f, depth = 0.3f),
        Sticker(0.76f, 0.80f, Shapes.pill, 32.dp, Pad.Secondary, turn = 10f, emoji = "1fa99", size = 36.dp, depth = 0.4f),
        Sticker(0.11f, 0.17f, emoji = "2728", size = 30.dp, depth = 0.55f),
        Sticker(0.94f, 0.60f, Shapes.sparkle, 7.dp, Pad.FaintInk, depth = 0.6f),
    )
    // Who you are: the avatar itself, an ID card and a writing hand. The avatar's own disc is the primary container, so its pad
    // is the tertiary one (on the primary one an avatar with no picture melted into it).
    TourScene.Profile -> listOf(
        Sticker(0.36f, 0.52f, Shapes.sunny, MainPad, Pad.Tertiary, turn = 8f, size = 96.dp, depth = 0.12f),
        Sticker(0.76f, 0.30f, Shapes.cookie9, 38.dp, Pad.Primary, turn = 20f, emoji = "1faaa", size = 46.dp, tilt = -8f, depth = 0.3f),
        Sticker(0.75f, 0.80f, Shapes.pill, 32.dp, Pad.Secondary, turn = -8f, emoji = "270d", size = 36.dp, depth = 0.4f),
        Sticker(0.08f, 0.20f, Shapes.sparkle, 11.dp, Pad.Ink, depth = 0.55f),
        Sticker(0.95f, 0.58f, Shapes.sparkle, 7.dp, Pad.FaintInk, depth = 0.6f),
    )
    // Reminders: a ringing bell, an alarm clock, the calendar.
    TourScene.Reminders -> listOf(
        Sticker(0.36f, 0.52f, Shapes.cookie4, MainPad, Pad.Primary, turn = 45f, emoji = "1f514", size = MainSize, tilt = 14f, depth = 0.12f),
        Sticker(0.76f, 0.30f, Shapes.cookie9, 38.dp, Pad.Tertiary, turn = -6f, emoji = "23f0", size = 46.dp, tilt = -10f, depth = 0.3f),
        Sticker(0.75f, 0.80f, Shapes.pill, 32.dp, Pad.Secondary, turn = 6f, emoji = "1f5d3", size = 36.dp, depth = 0.4f),
        Sticker(0.10f, 0.18f, Shapes.sparkle, 11.dp, Pad.Ink, depth = 0.55f),
        Sticker(0.60f, 0.10f, Shapes.sparkle, 6.dp, Pad.FaintInk, depth = 0.6f),
    )
    // Off you go: a rocket, a party popper, a receipt to share in.
    TourScene.Start -> listOf(
        Sticker(0.36f, 0.52f, Shapes.burst, MainPad, Pad.Primary, turn = 0f, emoji = "1f680", size = MainSize, depth = 0.12f),
        Sticker(0.76f, 0.31f, Shapes.cookie4, 40.dp, Pad.Tertiary, turn = 12f, emoji = "1f389", size = 48.dp, depth = 0.3f),
        Sticker(0.75f, 0.80f, Shapes.pill, 32.dp, Pad.Secondary, turn = -10f, emoji = "1f9fe", size = 36.dp, tilt = 6f, depth = 0.4f),
        Sticker(0.11f, 0.17f, emoji = "2728", size = 30.dp, depth = 0.55f),
        Sticker(0.94f, 0.60f, Shapes.sparkle, 7.dp, Pad.FaintInk, depth = 0.6f),
    )
}

private fun picture(emoji: String) = "fluent3d/$emoji.webp"

/**
 * How far off the middle of its 128 px box a picture's artwork sits, as shares of the box: x to the right, y downwards (measured
 * from where each picture is opaque: `TourArtCentringTest` re-measures them). The credit card and the ID card are drawn in the
 * lower part of theirs, so centred in the box they sat about 7 dp under the middle of their pads (the welcome step's card looked
 * pushed down in its blob); the receipt is a little to the left. Only the pictures off by more than a pixel or two are listed.
 */
internal fun pictureOffset(emoji: String): Pair<Float, Float> = when (emoji) {
    "1f4b3", "1faaa" -> 0f to 20f / 128f
    "1f3a8" -> 0f to 3f / 128f
    "1f9fe" -> -6f / 128f to 0f
    else -> 0f to 0f
}

/** The pictures that stand on a pad of their own (the rest float free), which is where being off-centre shows. */
internal val paddedPictures: List<String> by lazy {
    TourScene.entries.flatMap { scene -> stickersOf(scene).filter { it.shape != null }.mapNotNull { it.emoji } }.distinct()
}

/** Every picture the tour shows, so it can decode them before they are needed. */
internal val tourPictures: List<String> by lazy { TourScene.entries.flatMap { scene -> stickersOf(scene).mapNotNull { it.emoji?.let(::picture) } } }

// The pop: each sticker grows from nothing to a little past its size and settles, STAGGER_MS after the one before.
private const val POP_MS = 520
private const val STAGGER_MS = 80
private val PopEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** How far sticker [index] is through its pop [elapsedMs] after the step came into view: 0 not yet, past 1 overshooting, 1 settled. */
internal fun popOf(elapsedMs: Float, index: Int): Float {
    val local = ((elapsedMs - index * STAGGER_MS) / POP_MS).coerceIn(0f, 1f)
    return PopEasing.transform(local)
}

/**
 * The picture of [scene], on the hero card of its step. [main] takes the main sticker's place on its pad (the Profile step's avatar,
 * which changes as a name is typed). Decorative: TalkBack has the step's title and text, so the picture says nothing.
 */
@Composable
internal fun TourArt(scene: TourScene, modifier: Modifier = Modifier, main: (@Composable () -> Unit)? = null) {
    val stickers = remember(scene) { stickersOf(scene) }
    val place = LocalStepPlace.current
    val still = rememberReducedMotion()
    val end = (POP_MS + STAGGER_MS * (stickers.size - 1)).toFloat()
    val elapsed = remember { Animatable(if (still) end else 0f) }
    LaunchedEffect(still) {
        if (still) elapsed.snapTo(end) else elapsed.animateTo(end, tween(end.toInt(), easing = LinearEasing))
    }
    val bob = rememberBob(running = !still && place.current)

    BoxWithConstraints(modifier.fillMaxWidth().height(TourArtHeight).testTag(TOUR_ART_TAG).clearAndSetSemantics { }) {
        val w = maxWidth
        val h = maxHeight
        stickers.forEachIndexed { index, sticker ->
            StickerView(
                sticker = sticker,
                index = index,
                left = w * sticker.x - sticker.box / 2,
                top = h * sticker.y - sticker.box / 2,
                pop = { if (still) 1f else popOf(elapsed.value, index) },
                slide = { if (still) 0f else place.offset() * sticker.depth * w.toPx() },
                bob = bob,
                content = if (index == 0) main else null,
            )
        }
    }
}

/** Seconds of the gentle bob, ticking only where endless motion may run (the app on screen) and [running]; else held at 0. */
@Composable
private fun rememberBob(running: Boolean): State<Float> {
    val live = LocalLiveMotion.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return rememberLiveClock(live && running && lifecycle.isAtLeast(Lifecycle.State.RESUMED))
}

@Composable
private fun StickerView(
    sticker: Sticker,
    index: Int,
    left: Dp,
    top: Dp,
    pop: () -> Float,
    slide: androidx.compose.ui.unit.Density.() -> Float,
    bob: State<Float>,
    content: (@Composable () -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val padColor = sticker.pad.color(scheme)
    // Each sticker bobs at its own pace, so they start together at rest and soon drift apart.
    val period = 3.4f + index * 0.55f
    Box(
        Modifier
            .offset(left, top)
            .size(sticker.box)
            .graphicsLayer {
                val p = pop()
                val wave = sin(bob.value * 2f * PI.toFloat() / period)
                scaleX = p
                scaleY = p
                alpha = (p * 3f).coerceIn(0f, 1f)
                rotationZ = (1f - p) * -24f + wave * 2f
                translationX = slide()
                translationY = wave * 3.dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        val shape = sticker.shape
        if (shape != null) {
            Canvas(Modifier.fillMaxSize()) {
                val outline = outlineOf(shape)
                val r = sticker.radius.toPx()
                translate(size.width / 2f, size.height / 2f) {
                    rotate(sticker.turn, Offset.Zero) { scale(r, r, Offset.Zero) { drawPath(outline, padColor) } }
                }
            }
        }
        when {
            content != null -> content()
            sticker.emoji != null -> {
                // Moved by how far off the middle of its box the artwork sits, and turned about the artwork's own middle, so it is
                // centred on its pad.
                val (dx, dy) = pictureOffset(sticker.emoji)
                AssetImage(
                    picture(sticker.emoji),
                    Modifier
                        .size(sticker.size)
                        .offset(x = -sticker.size * dx, y = -sticker.size * dy)
                        .graphicsLayer {
                            rotationZ = sticker.tilt
                            transformOrigin = TransformOrigin(0.5f + dx, 0.5f + dy)
                        },
                )
            }
        }
    }
}

private val outlines = HashMap<RoundedPolygon, Path>()

/** [shape]'s outline at unit radius, made once. */
private fun outlineOf(shape: RoundedPolygon): Path = outlines.getOrPut(shape) {
    val out = Path()
    val cubics = shape.cubics
    if (cubics.isNotEmpty()) {
        out.moveTo(cubics.first().anchor0X, cubics.first().anchor0Y)
        for (c in cubics) out.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
        out.close()
    }
    out
}
