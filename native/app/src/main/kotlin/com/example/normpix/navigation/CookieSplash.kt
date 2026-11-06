package com.example.normpix.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.example.normpix.R
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/*
 * The cold start's "cookie" (Settings > Appearance > Transitions > Launch), after the way DropSauce opens - its timings and its idea,
 * drawn here from scratch. A cookie (a circle with nine soft lobes) spins in around the app's own mark as a spring: it overshoots
 * a little and settles, its lobes growing out of a plain circle as it turns. Once the app has loaded it plays out backwards and
 * inside out: the mark sinks, the cookie squishes, then turns a quarter of an eighth round and opens - its lobes smoothing into a
 * circle - until the window it makes is bigger than the screen, and the app is there. One clock drives each half, so a late frame
 * shows where the motion should be by then, never a jump.
 */

/** The pure motion of the cookie, in milliseconds, so it can be checked without a screen. */
internal object CookieMotion {
    const val INTRO_MS = 600f
    const val EXIT_MS = 750f

    /** How long the settled cookie is held before the exit may begin. */
    const val HOLD_MS = 120L

    /** The longest the settled cookie waits for the data to load: past it, the app's own loading screen says so. */
    const val MAX_WAIT_MS = 2_500L

    /**
     * The longest the intro waits for Android's own launch screen to go (12 and later keep it over the app until the app has drawn,
     * and the intro would play behind it): past it the cookie starts anyway.
     */
    const val SYSTEM_SPLASH_WAIT_MS = 1_500L

    /**
     * Once the data is in, the app below does its first heavy frames (the real Home where there was a loading screen); the opening
     * waits until they are past - this many frames in a row, none later than [CALM_MS] after the one before - or [SETTLE_MAX_MS]
     * has passed. Opening onto a stalled app skips its middle: seen on a phone, five frames in a row of the same small hole.
     * [CALM_MS] is a limit for a stall, not for the pace: a slow build runs its exit at ~23 ms a frame, and a first version that
     * wanted 22 ms never saw calm and always waited out the cap (a second and more added to every start).
     */
    const val CALM_FRAMES = 4
    const val CALM_MS = 40f
    const val SETTLE_MAX_MS = 900L

    const val LOBES = 9

    /** How far the lobes stand out of the circle, as a share of its radius. */
    const val WAVE = 0.0615f

    /** How small the cookie and the mark squish to before the cookie opens. */
    const val SQUISH = 0.82f

    /** The cookie's radius as a share of the shorter side of the screen. */
    const val RADIUS_OF_SCREEN = 0.21f

    /** How wide the mark's drawing is, against the cookie's radius (the drawing has room round the cards in it). */
    const val LOGO_OF_RADIUS = 2.6f

    /** How far past the screen's corners the opened cookie goes, so its edge is gone by the end. */
    const val CLEARANCE = 1.02f

    /** Where the mark starts, against its settled size: it grows out of this. */
    const val LOGO_FROM = 0.6f

    /** How far the cookie turns while it spins in: it starts this many degrees back. */
    const val SPIN_DEGREES = 150f

    /** How far it turns as it opens, in radians: an eighth of a turn, as the lobes smooth out. */
    const val OPEN_TURN = (PI / 4).toFloat()

    /**
     * The response of a spring to a step, 0 to 1 over [t] (0..1 of the time it has): it passes 1, overshoots by a share set by
     * [damping] (0.6 is about 9.5%) and settles. [omega] is its stiffness in radians over the whole time. A closed form, so
     * any moment can be asked for without playing the ones before it.
     */
    fun spring(t: Float, damping: Float, omega: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        val wd = omega * sqrt(1f - damping * damping)
        val decay = exp(-damping * omega * t)
        return 1f - decay * (cos(wd * t) + damping / sqrt(1f - damping * damping) * sin(wd * t))
    }

    /** How the cookie looks [ms] into the intro: its size, how far round it still has to turn, how far its lobes have come, and the mark. */
    class Intro(
        val scale: Float,
        val rotation: Float,
        /** 0 for a plain circle, 1 for the lobes at full height; it overshoots a little on the way. */
        val lobes: Float,
        val logoScale: Float,
        val logoAlpha: Float,
    )

    fun intro(ms: Float): Intro {
        val t = (ms / INTRO_MS).coerceIn(0f, 1f)
        val spin = 1f - AppMotion.enter.transform((t / 0.65f).coerceIn(0f, 1f))
        val logoT = ((t - 0.15f) / 0.65f)
        return Intro(
            scale = spring(t, 0.6f, 15.1f),
            rotation = -Math.toRadians(SPIN_DEGREES.toDouble()).toFloat() * spin,
            lobes = spring((t - 0.075f) / 0.925f, 0.7f, 11.8f),
            logoScale = LOGO_FROM + (1f - LOGO_FROM) * spring(logoT, 0.6f, 15.1f),
            logoAlpha = ((t - 0.15f) / 0.15f).coerceIn(0f, 1f),
        )
    }

    /** How the cookie looks [ms] into the exit. [scale] is a share of the settled cookie, and is [endScale] when the exit is done. */
    class Exit(
        val scale: Float,
        val rotation: Float,
        /** 1 for the lobes at full height, 0 once they have smoothed into a circle. */
        val lobes: Float,
        /** How much of the cookie's own colour is drawn: it goes at once, so the app shows through the opening. */
        val fill: Float,
        val logoScale: Float,
        val logoAlpha: Float,
    )

    private fun at(easing: Easing, ms: Float, start: Float, duration: Float) =
        easing.transform(((ms - start) / duration).coerceIn(0f, 1f))

    fun exit(ms: Float, endScale: Float): Exit {
        val squish = 1f - (1f - SQUISH) * at(AppMotion.standard, ms, 0f, 250f)
        val scale = squish + (endScale - SQUISH) * at(AppMotion.standard, ms, 250f, 500f)
        val sink = at(AppMotion.exit, ms, 100f, 150f)
        return Exit(
            scale = scale,
            rotation = OPEN_TURN * at(AppMotion.enter, ms, 150f, 400f),
            lobes = 1f - at(AppMotion.standard, ms, 250f, 100f),
            fill = 1f - at(AppMotion.standard, ms, 230f, 20f),
            logoScale = squish * (1f - 0.4f * sink),
            logoAlpha = 1f - sink,
        )
    }

    /** How much bigger than the settled cookie it must be to cover a screen whose corner is [screenRadius] from its centre. */
    fun endScale(screenRadius: Float, radius: Float): Float = screenRadius / radius * CLEARANCE

    private const val POINTS = LOBES * 24

    /**
     * The cookie as a closed curve in [path]: a circle of [radius] whose edge rises and falls [amplitude] nine times, turned by
     * [rotation]. Sampled and smoothed through the midpoints, so it has no corners at any size.
     */
    fun cookie(path: Path, cx: Float, cy: Float, radius: Float, amplitude: Float, rotation: Float, xs: FloatArray, ys: FloatArray) {
        val n = xs.size
        for (i in 0 until n) {
            val a = 2f * PI.toFloat() * i / n
            val r = radius + amplitude * cos(LOBES * a)
            xs[i] = cx + r * cos(a + rotation)
            ys[i] = cy + r * sin(a + rotation)
        }
        path.rewind()
        path.moveTo((xs[n - 1] + xs[0]) / 2f, (ys[n - 1] + ys[0]) / 2f)
        for (i in 0 until n) {
            val j = (i + 1) % n
            path.quadraticTo(xs[i], ys[i], (xs[i] + xs[j]) / 2f, (ys[i] + ys[j]) / 2f)
        }
        path.close()
    }

    fun newPoints(): Pair<FloatArray, FloatArray> = FloatArray(POINTS) to FloatArray(POINTS)
}

/**
 * Frames that come on time again: [onFrame] is told each frame's time and says whether [needed] in a row came no more than
 * [gapMs] after the one before. Kept apart from the clock so it can be checked with made-up times.
 */
internal class CalmFrames(private val needed: Int = CookieMotion.CALM_FRAMES, private val gapMs: Float = CookieMotion.CALM_MS) {
    private var last = -1L
    private var calm = 0

    fun onFrame(nanos: Long): Boolean {
        if (last >= 0) calm = if ((nanos - last) / 1_000_000f <= gapMs) calm + 1 else 0
        last = nanos
        return calm >= needed
    }
}

/** Returns once frames are coming on time (see [CalmFrames]); the caller gives it a limit. */
private suspend fun awaitCalmFrames() {
    val calm = CalmFrames()
    while (!calm.onFrame(withFrameNanos { it })) Unit
}

/**
 * The cold start's cookie: it spins in (once Android's own launch screen has gone, [systemSplashGone]: it would play behind it),
 * holds until the app is [ready] (up to [CookieMotion.MAX_WAIT_MS]) and has its frames on time, then opens onto the app and
 * calls [onFinished]. [onWritten] is called when the intro is done, which is when the app below may do its heavy work (as with
 * the name's splash, [LaunchSplash]: not while the cookie moves). Nothing under it can be touched, and a screen reader hears
 * the name once. It is not shown with animations removed; the app just opens.
 */
@Composable
internal fun CookieSplash(
    ready: Boolean,
    onFinished: () -> Unit,
    onWritten: () -> Unit = {},
    systemSplashGone: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val intro = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    val isSystemSplashGone by rememberUpdatedState(systemSplashGone)
    val finished by rememberUpdatedState(onFinished)
    val written by rememberUpdatedState(onWritten)
    val reducedMotion = rememberReducedMotion()
    val logo = painterResource(R.drawable.ic_launcher_monochrome)
    val path = remember { Path().apply { fillType = PathFillType.NonZero } }
    val frame = remember { Path().apply { fillType = PathFillType.EvenOdd } }
    val points = remember { CookieMotion.newPoints() }
    val background = scheme.surface
    val fill = scheme.primary
    val ink = scheme.onPrimary

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            intro.snapTo(CookieMotion.INTRO_MS)
        } else {
            // Not before Android's own launch screen has gone: on 12 and later it stays over the app until the app has drawn, and the
            // spin-in played behind it (a phone showed the cookie already full size when the launch screen went).
            withTimeoutOrNull(CookieMotion.SYSTEM_SPLASH_WAIT_MS) { snapshotFlow { isSystemSplashGone }.first { it } }
            // Nor until the splash has been drawn twice: the first frame of a cold start is the busiest, and the start of the
            // spin would be spent on work the user cannot see.
            withFrameNanos { }
            withFrameNanos { }
            intro.animateTo(CookieMotion.INTRO_MS, tween(CookieMotion.INTRO_MS.toInt(), easing = LinearEasing))
        }
        written()
        delay(CookieMotion.HOLD_MS)
        withTimeoutOrNull(CookieMotion.MAX_WAIT_MS) { snapshotFlow { isReady }.first { it } }
        // The app below has just been given the main thread for its first frames: the opening waits for them to come on time.
        withTimeoutOrNull(CookieMotion.SETTLE_MAX_MS) { awaitCalmFrames() }
        exit.animateTo(CookieMotion.EXIT_MS, tween(CookieMotion.EXIT_MS.toInt(), easing = LinearEasing))
        finished()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            // Nothing under the splash is reachable while it is up.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .clearAndSetSemantics { contentDescription = "NormPix" },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) * CookieMotion.RADIUS_OF_SCREEN
        val exitMs = exit.value
        if (exitMs <= 0f) {
            val s = CookieMotion.intro(intro.value)
            drawRect(background)
            val r = radius * s.scale
            CookieMotion.cookie(path, cx, cy, r, r * CookieMotion.WAVE * s.lobes, s.rotation, points.first, points.second)
            drawPath(path, fill)
            drawLogo(logo, ink, cx, cy, radius, s.logoScale, s.logoAlpha)
        } else {
            val screenRadius = hypot(max(cx, size.width - cx), max(cy, size.height - cy))
            val e = CookieMotion.exit(exitMs, CookieMotion.endScale(screenRadius, radius))
            val r = radius * e.scale
            CookieMotion.cookie(path, cx, cy, r, r * CookieMotion.WAVE * e.lobes, e.rotation, points.first, points.second)
            // The cookie's own colour is there only for the first moments; then what shows through the opening is the app.
            if (e.fill > 0f) drawPath(path, fill, alpha = e.fill)
            if (e.logoAlpha > 0f) drawLogo(logo, ink, cx, cy, radius, e.logoScale, e.logoAlpha)
            // The splash's own ground is the screen less the cookie: the opening grows until nothing of it is left.
            frame.rewind()
            frame.addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
            frame.addPath(path)
            drawPath(frame, background)
        }
    }
}

private fun DrawScope.drawLogo(
    painter: androidx.compose.ui.graphics.painter.Painter,
    ink: androidx.compose.ui.graphics.Color,
    cx: Float,
    cy: Float,
    radius: Float,
    scale: Float,
    alpha: Float,
) {
    val side = radius * CookieMotion.LOGO_OF_RADIUS
    withTransform({
        translate(cx - side / 2f, cy - side / 2f)
        scale(scale, scale, pivot = Offset(side / 2f, side / 2f))
    }) {
        with(painter) { draw(Size(side, side), alpha = alpha.coerceIn(0f, 1f), colorFilter = ColorFilter.tint(ink)) }
    }
}
