package com.example.normpix.finance.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.LocalLiveMotion
import com.example.normpix.ui.components.rememberLiveClock
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/*
 * The hero's weather (owner, 2026-11-01: "animation on the home hero section, similar to Overcast" - sameerasw/Overcast, a weather
 * app whose hero wears the sky: rain on the glass, falling snow, a heat haze that bends its big number). Home's month card wears
 * the month's: how the spending is going, said by a sky rather than a word, behind the same figures. Drawn here from scratch
 * (particles that are a pure function of their index and the time, so a frame allocates nothing and keeps no state), in the
 * card's own ink at low strength so the amount stays the loudest thing.
 */

/** What the month looks like, as weather on Home's month card. */
enum class HeroWeather {
    /** Nothing spent yet this month: a quiet snowfall. */
    Snow,

    /** On track: a slow, breathing sun glow and a few motes rising in it. */
    Clear,

    /** Running hot - the budget is nearly used, or (with no budget) spending is well up on last month: a warm haze that shimmers. */
    Heat,

    /** Over budget: rain across the card, and drops on the glass. */
    Rain,
}

/** Spending this far up on the same point last month (with no budget to go by) is a hot month. */
internal const val HEAT_CHANGE = 0.25

/**
 * The month's weather from what has been [spent] so far (minor units), the headline [budget]'s health if there is one, and how
 * spending compares with the same point last month ([change], a fraction; null when there is nothing to compare).
 */
internal fun heroWeatherFor(spent: Long, budget: BudgetHealth?, change: Double?): HeroWeather = when {
    spent <= 0L -> HeroWeather.Snow
    budget == BudgetHealth.Exceeded -> HeroWeather.Rain
    budget == BudgetHealth.Approaching -> HeroWeather.Heat
    budget == null && change != null && change >= HEAT_CHANGE -> HeroWeather.Heat
    else -> HeroWeather.Clear
}

/**
 * The time the weather is drawn at, in seconds, and whether it moves at all. It runs only where it can be seen and is wanted: the
 * real app ([LocalLiveMotion]), Settings > Appearance > Effects > Hero weather on, animations on, Battery Saver off, the screen
 * resumed and the card on it. Otherwise the clock holds still and the weather is a still picture of itself.
 */
internal class HeroWeatherClock(
    val time: State<Float>,
    /** The weather moves now. */
    val running: Boolean,
    /** The weather is shown at all (the setting); still while not [running]. */
    val shown: Boolean,
    /** Tells the clock whether the card is on screen. */
    val positioned: Modifier,
)

@Composable
internal fun rememberHeroWeatherClock(): HeroWeatherClock {
    val prefs by LocalAppContainer.current.preferences.state.collectAsState()
    val live = LocalLiveMotion.current
    val reduced = rememberReducedMotion()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val context = LocalContext.current
    // Read again whenever the screen comes back, which is when Battery Saver may have changed.
    val saving = remember(resumed) { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true }
    var onScreen by remember { mutableStateOf(false) }
    val running = live && prefs.heroWeather && !reduced && resumed && !saving && onScreen
    val time = rememberLiveClock(running)
    val positioned = Modifier.onGloballyPositioned { coordinates ->
        onScreen = coordinates.isAttached && coordinates.boundsInWindow().height > 0f
    }
    return HeroWeatherClock(time, running, prefs.heroWeather, positioned)
}

/** A fixed random number in 0..1 for particle [i]'s [k]th property: the same every frame, so nothing needs keeping. */
private fun rand(i: Int, k: Int): Float {
    val x = sin(i * 12.9898f + k * 78.233f) * 43_758.547f
    return x - floor(x)
}

/** The fraction of [x] past its whole number, for a particle's place through its loop. */
private fun loop(x: Float): Float = x - floor(x)

/**
 * Draws [weather] behind the card's content at [time()] seconds. What gives light (the sun, snow, the heat's glow) is drawn in
 * [Light] whatever the card's colour - in the card's own ink, dark on a dark theme's light card, they read as soot; what darkens
 * (rain, the shimmer) is in [ink], the card's content colour. Read in the draw phase only, so a frame of it recomposes nothing; the
 * card's shape clips it.
 */
internal fun Modifier.heroWeather(weather: HeroWeather, ink: Color, time: () -> Float): Modifier = drawBehind {
    val t = time()
    when (weather) {
        HeroWeather.Snow -> snow(t, Light)
        HeroWeather.Clear -> sun(t, Light)
        HeroWeather.Heat -> heat(t, glow = Light, ink = ink)
        HeroWeather.Rain -> rain(t, ink)
    }
}

/** The colour of light in the weather: the sun, snow, the heat's glow. */
private val Light = Color.White

private fun DrawScope.snow(t: Float, ink: Color) {
    val h = size.height
    for (i in 0 until 26) {
        val r = (1.4f + 1.8f * rand(i, 1)) * density
        val speed = 0.07f + 0.08f * rand(i, 2) // card heights a second
        val sway = sin(t * (0.5f + 0.6f * rand(i, 3)) + rand(i, 4) * 6.28f) * (4f + 6f * rand(i, 5)) * density
        val x = rand(i, 6) * size.width + sway
        val y = loop(rand(i, 7) + t * speed) * (h + 2 * r) - r
        drawCircle(ink.copy(alpha = 0.35f + 0.4f * rand(i, 8)), r, Offset(x, y))
    }
}

private fun DrawScope.sun(t: Float, ink: Color) {
    val w = size.width
    val h = size.height
    // The glow: high on the start side, drifting and breathing slowly.
    val centre = Offset(w * (0.2f + 0.06f * sin(t * 0.11f)), h * (0.02f + 0.03f * sin(t * 0.07f)))
    val strength = 0.22f + 0.06f * sin(t * 0.45f)
    drawCircle(
        Brush.radialGradient(listOf(ink.copy(alpha = strength), ink.copy(alpha = strength * 0.4f), Color.Transparent), centre, h * 1.1f),
        h * 1.1f,
        centre,
    )
    // The sun itself, soft-edged, at the heart of the glow.
    val disc = h * 0.16f
    drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = strength * 0.9f), Color.Transparent), centre, disc * 1.6f), disc * 1.6f, centre)
    // Motes rising in it.
    for (i in 0 until 12) {
        val r = (1f + 1.2f * rand(i, 1)) * density
        val y = h - loop(rand(i, 2) + t * (0.025f + 0.035f * rand(i, 3))) * (h + 2 * r)
        val x = rand(i, 4) * w + sin(t * (0.3f + 0.4f * rand(i, 5)) + rand(i, 6) * 6.28f) * 8f * density
        val twinkle = 0.5f + 0.5f * sin(t * (0.8f + rand(i, 7)) + rand(i, 8) * 6.28f)
        drawCircle(ink.copy(alpha = (0.18f + 0.25f * rand(i, 9)) * twinkle), r, Offset(x, y))
    }
}

private fun DrawScope.heat(t: Float, glow: Color, ink: Color) {
    val w = size.width
    val h = size.height
    // A warm haze rising off the bottom edge, pulsing.
    val base = Offset(w * 0.5f, h * 1.15f)
    val strength = 0.12f + 0.05f * sin(t * 0.9f)
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = strength * 1.6f), Color.Transparent), base, w * 0.85f), w * 0.85f, base)
    // Shimmer: wavy lines that rise and fade.
    val path = Path()
    val stroke = Stroke(width = 1.2f * density, cap = StrokeCap.Round)
    for (k in 0 until 4) {
        val rise = loop(t * 0.14f + k / 4f)
        val y = h * (1f - rise)
        val fade = sin(PI.toFloat() * rise)
        path.reset()
        var x = 0f
        val step = 6f * density
        path.moveTo(0f, y)
        while (x <= w) {
            path.lineTo(x, y + sin(x / (22f * density) + t * 2.2f + k) * 3f * density)
            x += step
        }
        drawPath(path, ink.copy(alpha = 0.13f * fade), style = stroke)
    }
}

private fun DrawScope.rain(t: Float, ink: Color) {
    val w = size.width
    val h = size.height
    val slant = 0.22f
    // Streaks falling across the card, all at one slant.
    for (i in 0 until 34) {
        val len = (12f + 14f * rand(i, 1)) * density
        val speed = 1.6f + 0.9f * rand(i, 2) // card heights a second
        val y = loop(rand(i, 3) + t * speed) * (h + len) - len
        val x = rand(i, 4) * (w + h * slant) - y * slant
        drawLine(
            ink.copy(alpha = 0.16f + 0.2f * rand(i, 5)),
            Offset(x, y),
            Offset(x + len * slant, y + len),
            strokeWidth = (0.9f + 0.6f * rand(i, 6)) * density,
            cap = StrokeCap.Round,
        )
    }
    // Drops on the glass: each gathers, slides a little way down, and is gone.
    for (i in 0 until 9) {
        val cycle = 4f + 3f * rand(i, 7)
        val phase = loop(t / cycle + rand(i, 8))
        val life = sin(PI.toFloat() * phase)
        val r = (2f + 2.5f * rand(i, 9)) * density
        val x = rand(i, 10) * w
        val y = rand(i, 11) * h * 0.85f + phase * phase * 18f * density
        drawCircle(ink.copy(alpha = 0.18f * life), r, Offset(x, y))
        drawCircle(ink.copy(alpha = 0.3f * life), r * 0.35f, Offset(x - r * 0.3f, y - r * 0.3f))
    }
}

/**
 * The heat haze: the card's content shimmers as if seen through hot air - Android 13 and later only (a runtime shader); before
 * that, the haze and shimmer behind it are all there is. Only while the clock runs, so a still card is a sharp one.
 */
@Composable
internal fun Modifier.heatHaze(on: Boolean, time: () -> Float): Modifier {
    if (!on || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    return heatHazeShader(LocalDensity.current.density, time)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.heatHazeShader(density: Float, time: () -> Float): Modifier {
    val shader = remember { runCatching { RuntimeShader(HEAT_HAZE_SHADER) }.getOrNull() } ?: return this
    return graphicsLayer {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", time())
        shader.setFloatUniform("density", density)
        renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

/** Each point is read from a little way off, by waves that rise through the card; most at its foot, least at its top. */
private const val HEAT_HAZE_SHADER = """
uniform shader content;
uniform float2 size;
uniform float time;
uniform float density;

half4 main(float2 p) {
    float rise = p.y / density * 0.05 + time * 2.0;
    float wave = sin(rise) + 0.5 * sin(rise * 1.9 + p.x / density * 0.04 + time * 0.8);
    float foot = 0.3 + 0.7 * clamp(p.y / size.y, 0.0, 1.0);
    float2 shift = float2(wave * 0.9 * density * foot, 0.0);
    return content.eval(p + shift);
}
"""
