package com.example.normpix.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/*
 * Inside a see-through sheet or dialog the theme's surface tones are see-through too. Each one becomes a tint that, laid
 * over the sheet's own tone, comes out as the tone it was - so on a solid sheet nothing changes and the fields, tracks and
 * cards keep their place in the hierarchy - while over the blurred page the page shows through them as it does through the
 * sheet, instead of their sitting on it as solid grey blocks.
 */

/** The theme's colours outside any see-through surface; null when not inside one. */
private val LocalSolidColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/**
 * The theme's colours as they are on solid ground, for anything that needs an opaque tone of its own inside a see-through
 * sheet: a menu (a popup over the sheet, where nothing behind it is blurred), a dialog opened from the sheet.
 */
val solidColorScheme: ColorScheme
    @Composable get() = LocalSolidColorScheme.current ?: MaterialTheme.colorScheme

/** While [enabled], [content] lies on a see-through surface whose own (solid) tone is [ground]: its surface tones are tints. */
@Composable
fun SeeThroughColors(enabled: Boolean, ground: Color, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val solid = solidColorScheme
    val tinted = remember(solid, ground) { solid.seeThroughOver(ground) }
    CompositionLocalProvider(LocalSolidColorScheme provides solid) {
        MaterialTheme(colorScheme = tinted, content = content)
    }
}

internal fun ColorScheme.seeThroughOver(ground: Color): ColorScheme {
    fun tint(level: Color) = tintOver(ground, level, onSurface)
    return copy(
        background = tint(background),
        surface = tint(surface),
        surfaceVariant = tint(surfaceVariant),
        surfaceBright = tint(surfaceBright),
        surfaceDim = tint(surfaceDim),
        surfaceContainerLowest = tint(surfaceContainerLowest),
        surfaceContainerLow = tint(surfaceContainerLow),
        surfaceContainer = tint(surfaceContainer),
        surfaceContainerHigh = tint(surfaceContainerHigh),
        surfaceContainerHighest = tint(surfaceContainerHighest),
    )
}

/**
 * A see-through colour that, laid over [ground], comes out as (about) [level]: [toward] at the alpha that gets there, or -
 * for a level on the other side of the ground (darker than it in a dark theme) - black or white. Never more than
 * [MAX_TINT], so the page shows through even the level furthest from the ground (pure white, in a light theme, can only
 * be reached by opaque white; the tint misses it by a level or two).
 */
internal fun tintOver(ground: Color, level: Color, toward: Color): Color {
    val a = alphaTowards(ground, level, toward)
    if (a >= 0.002f) return toward.copy(alpha = a.coerceAtMost(MAX_TINT))
    if (a > -0.002f) return toward.copy(alpha = 0f) // the ground's own tone: clear
    val opposite = if (toward.luminance() > ground.luminance()) Color.Black else Color.White
    return opposite.copy(alpha = alphaTowards(ground, level, opposite).coerceIn(0.002f, MAX_TINT))
}

private const val MAX_TINT = 0.6f

/** How far [level] lies from [ground] towards [target], 0 at the ground and 1 at the target (projected, per channel). */
private fun alphaTowards(ground: Color, level: Color, target: Color): Float {
    val dr = target.red - ground.red
    val dg = target.green - ground.green
    val db = target.blue - ground.blue
    val span = dr * dr + dg * dg + db * db
    if (span < 1e-6f) return 0f
    return ((level.red - ground.red) * dr + (level.green - ground.green) * dg + (level.blue - ground.blue) * db) / span
}
