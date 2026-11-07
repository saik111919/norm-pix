package com.example.normpix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.toHct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlin.math.abs

/*
 * Artwork colours (2026-11-07, owner: "Cover-Based Dynamic Colors for Add/Edit Screens", the way Komikku does it for a manga's
 * cover): one seed colour from the picture or card a form shows -> a whole generated colour scheme for that sheet only -> the
 * normal theme again when there is no picture, the seed is unusable, or the setting is off.
 *
 * This file is the pure part (no Compose state): picking the seed from pixels, deciding whether a seed is usable or the same
 * as the last, and making the scheme from it. The composable part - what carries a seed up to the sheet, animates the change
 * and tints the sheet's own surface - is in ui/components/ArtworkTheme.kt.
 */
object ArtworkColors {
    /**
     * The one place the palette style is chosen. The owner asked for restrained, soft tonal surfaces, so TonalSpot (Material's
     * default: fixed chroma, so the picture's hue leads and its saturation does not) rather than Komikku's Vibrant.
     */
    val Style: PaletteStyle = PaletteStyle.TonalSpot

    /** The colours the quantizer reduces a picture to before the best one is chosen. */
    private const val QUANTIZE_COLORS = 64

    /**
     * A seed with less chroma than this is a grey: a scheme made from it takes whatever hue the grey happens to have (a black
     * card's is red), so it is no seed at all.
     */
    const val MIN_CHROMA = 8.0

    /** The hue (degrees) two seeds may differ by and still be "the same colour": the scheme follows the hue, so a hair's difference is not worth an animation. */
    private const val SAME_HUE = 2.0

    /**
     * The one colour a picture says, from its [pixels] (ARGB, as `Bitmap.getPixels` gives them): the most prominent colourful one
     * (Material's own scoring over a quantized palette, which drops greys and near-empty corners), or null when there is none -
     * a grey or white picture, an empty or fully transparent one, or anything the quantizer could not read.
     */
    fun seedFromPixels(pixels: IntArray): Color? {
        if (pixels.isEmpty()) return null
        return try {
            val quantized = QuantizerCelebi.quantize(pixels, QUANTIZE_COLORS)
            Score.score(quantized, desired = 1, fallbackColorArgb = null, filter = true).firstOrNull()?.let { Color(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Whether [seed] has enough colour to make a scheme from (see [MIN_CHROMA]). */
    fun usable(seed: Color): Boolean = seed.alpha > 0.5f && seed.toHct().chroma >= MIN_CHROMA

    /** Whether [a] and [b] would make (about) the same scheme: both none, or the same hue. */
    fun isSame(a: Color?, b: Color?): Boolean {
        if (a == null || b == null) return a == b
        val hueGap = abs(((a.toHct().hue - b.toHct().hue + 540.0) % 360.0) - 180.0)
        return hueGap < SAME_HUE
    }

    /**
     * The scheme for a sheet whose artwork says [seed], on top of the app's own [base] (light or dark as it is).
     *
     * The whole generated scheme, except what must not follow a picture: the error family and the scrim stay the app's. With
     * [keepSurfaces] (Pure, and Volt, whose black, white and lime are drawn by hand) only the accent families - primary, secondary
     * and tertiary, with their containers and on-colours - are taken, and every surface and the background stay the app's, so black
     * stays black.
     *
     * The status colours (success, warning, info: a paid bill, an overdue one, an expense and an income) are not in a scheme
     * at all - they come from [LocalAppStatusColors] - so they are the app's whatever the picture is.
     */
    fun scheme(base: ColorScheme, seed: Color, keepSurfaces: Boolean): ColorScheme {
        val made = dynamicColorScheme(seedColor = seed, isDark = base.isDark, style = Style)
        if (keepSurfaces) {
            return base.copy(
                primary = made.primary,
                onPrimary = made.onPrimary,
                primaryContainer = made.primaryContainer,
                onPrimaryContainer = made.onPrimaryContainer,
                inversePrimary = made.inversePrimary,
                secondary = made.secondary,
                onSecondary = made.onSecondary,
                secondaryContainer = made.secondaryContainer,
                onSecondaryContainer = made.onSecondaryContainer,
                tertiary = made.tertiary,
                onTertiary = made.onTertiary,
                tertiaryContainer = made.tertiaryContainer,
                onTertiaryContainer = made.onTertiaryContainer,
            )
        }
        return made.copy(
            error = base.error,
            onError = base.onError,
            errorContainer = base.errorContainer,
            onErrorContainer = base.onErrorContainer,
            scrim = base.scrim,
        )
    }
}
