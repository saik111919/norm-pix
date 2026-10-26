package com.example.normpix.ui.theme

import androidx.core.graphics.drawable.toDrawable
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.settings.WallpaperPalette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/**
 * Builds the app's light/dark color scheme from either a curated accent seed
 * or the device's dynamic (wallpaper) colors.
 *
 * Every component reads from the color scheme, so screens never need raw
 * colors: surfaces are tonal (`surfaceContainer*`), elevation is expressed
 * through tone rather than shadow, and shapes come from [AppRadius].
 */
object AppTheme {
    /** True when the OS can supply wallpaper-derived colors (Android 12+). */
    val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * The full palette generated from a curated accent. `Fidelity` keeps the
     * primary recognizably close to the chosen hue while still producing
     * proper tonal container pairs.
     */
    fun accentScheme(accent: AccentColor, isDark: Boolean): ColorScheme = when (accent) {
        AccentColor.Volt -> voltScheme(isDark)
        AccentColor.Pure -> pureScheme(isDark)
        else -> dynamicColorScheme(seedColor = accent.seed, isDark = isDark, style = PaletteStyle.Fidelity)
    }

    /**
     * What a palette looks like in the color picker, drawn from the [scheme] it makes: its primary color (the controls), the
     * container the primary's fills are made of, and its tertiary (the accent beside them).
     */
    fun paletteSwatch(scheme: ColorScheme): PaletteSwatch =
        PaletteSwatch(primary = scheme.primary, container = scheme.primaryContainer, tertiary = scheme.tertiary)

    /**
     * Pure: black and white, and greys between. Light is a white page with black ink, dark a black page (the screen's pixels
     * off) with white ink; the surfaces step through greys from the page, so a card still stands off it. There is no hue anywhere
     * but in what means something - the error red - and the status colors drawn from the primary (success, warning, info), which
     * keep theirs so a paid bill and an overdue one still look different. Every text pair is checked for contrast in `PureThemeTest`.
     */
    fun pureScheme(isDark: Boolean): ColorScheme =
        if (isDark) {
            darkColorScheme(
                primary = Color(0xFFFFFFFF),
                onPrimary = Color(0xFF000000),
                primaryContainer = Color(0xFF3A3A3A),
                onPrimaryContainer = Color(0xFFFFFFFF),
                inversePrimary = Color(0xFF000000),
                secondary = Color(0xFFC4C4C4),
                onSecondary = Color(0xFF000000),
                secondaryContainer = Color(0xFF2E2E2E),
                onSecondaryContainer = Color(0xFFF2F2F2),
                tertiary = Color(0xFFA0A0A0),
                onTertiary = Color(0xFF000000),
                tertiaryContainer = Color(0xFF383838),
                onTertiaryContainer = Color(0xFFEDEDED),
                background = Color(0xFF000000),
                onBackground = Color(0xFFFFFFFF),
                surface = Color(0xFF000000),
                onSurface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFF1F1F1F),
                onSurfaceVariant = Color(0xFFBDBDBD),
                surfaceTint = Color(0xFFFFFFFF),
                inverseSurface = Color(0xFFEDEDED),
                inverseOnSurface = Color(0xFF000000),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                outline = Color(0xFF8A8A8A),
                outlineVariant = Color(0xFF2E2E2E),
                scrim = Color.Black,
                surfaceBright = Color(0xFF2B2B2B),
                surfaceDim = Color(0xFF000000),
                surfaceContainerLowest = Color(0xFF000000),
                surfaceContainerLow = Color(0xFF0A0A0A),
                surfaceContainer = Color(0xFF121212),
                surfaceContainerHigh = Color(0xFF1A1A1A),
                surfaceContainerHighest = Color(0xFF232323),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF000000),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFCCCCCC),
                onPrimaryContainer = Color(0xFF000000),
                inversePrimary = Color(0xFFFFFFFF),
                secondary = Color(0xFF404040),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFDADADA),
                onSecondaryContainer = Color(0xFF000000),
                tertiary = Color(0xFF666666),
                onTertiary = Color(0xFFFFFFFF),
                tertiaryContainer = Color(0xFFD2D2D2),
                onTertiaryContainer = Color(0xFF000000),
                background = Color(0xFFFFFFFF),
                onBackground = Color(0xFF000000),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF000000),
                surfaceVariant = Color(0xFFE6E6E6),
                onSurfaceVariant = Color(0xFF404040),
                surfaceTint = Color(0xFF000000),
                inverseSurface = Color(0xFF141414),
                inverseOnSurface = Color(0xFFFFFFFF),
                error = Color(0xFFBA1A1A),
                onError = Color(0xFFFFFFFF),
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002),
                outline = Color(0xFF6E6E6E),
                outlineVariant = Color(0xFFC8C8C8),
                scrim = Color.Black,
                surfaceBright = Color(0xFFFFFFFF),
                surfaceDim = Color(0xFFD9D9D9),
                surfaceContainerLowest = Color(0xFFFFFFFF),
                surfaceContainerLow = Color(0xFFF6F6F6),
                surfaceContainer = Color(0xFFF0F0F0),
                surfaceContainerHigh = Color(0xFFE9E9E9),
                surfaceContainerHighest = Color(0xFFE2E2E2),
            )
        }

    /**
     * Volt: lime and near-black green. Drawn by hand rather than generated from the lime, because a generated palette
     * walks the seed's hue and chroma toward something muddier, and the pairing is the whole point. The two modes turn
     * the pair around: in light the ink is the colour of controls and the lime fills the containers (so a hero panel or
     * a button is lime with ink on it); in dark the lime is the colour of controls on near-black surfaces. Every text
     * pair is checked for contrast in `VoltThemeTest`.
     */
    fun voltScheme(isDark: Boolean): ColorScheme =
        if (isDark) {
            darkColorScheme(
                primary = VoltLime,
                onPrimary = VoltInk,
                primaryContainer = Color(0xFF1C4A30),
                onPrimaryContainer = Color(0xFFCDF25A),
                inversePrimary = VoltInk,
                secondary = Color(0xFFA3D6C1),
                onSecondary = Color(0xFF00382A),
                secondaryContainer = Color(0xFF1F4F40),
                onSecondaryContainer = Color(0xFFC4EBDA),
                tertiary = Color(0xFFEBDD5B),
                onTertiary = Color(0xFF393200),
                tertiaryContainer = Color(0xFF554B00),
                onTertiaryContainer = Color(0xFFFFF1A0),
                background = Color(0xFF0A130E),
                onBackground = Color(0xFFE2E9DE),
                surface = Color(0xFF0A130E),
                onSurface = Color(0xFFE2E9DE),
                surfaceVariant = Color(0xFF2E3B31),
                onSurfaceVariant = Color(0xFFB9C5B6),
                surfaceTint = VoltLime,
                inverseSurface = Color(0xFFE2E9DE),
                inverseOnSurface = Color(0xFF1F2A22),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                outline = Color(0xFF84927F),
                outlineVariant = Color(0xFF3A483D),
                scrim = Color.Black,
                surfaceBright = Color(0xFF2B382F),
                surfaceDim = Color(0xFF0A130E),
                surfaceContainerLowest = Color(0xFF060C08),
                surfaceContainerLow = Color(0xFF0F1913),
                surfaceContainer = Color(0xFF131E17),
                surfaceContainerHigh = Color(0xFF1A261E),
                surfaceContainerHighest = Color(0xFF212F26),
            )
        } else {
            lightColorScheme(
                primary = VoltInk,
                onPrimary = VoltLime,
                primaryContainer = VoltLime,
                onPrimaryContainer = VoltInk,
                inversePrimary = VoltLime,
                secondary = Color(0xFF2F6F5E),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFCDE9DD),
                onSecondaryContainer = VoltInk,
                tertiary = Color(0xFF5A6400),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFE4EE8F),
                onTertiaryContainer = Color(0xFF1B2000),
                background = Color(0xFFF3F4EE),
                onBackground = Color(0xFF171C13),
                surface = Color(0xFFF3F4EE),
                onSurface = Color(0xFF171C13),
                surfaceVariant = Color(0xFFDFE4D2),
                onSurfaceVariant = Color(0xFF434A38),
                surfaceTint = VoltInk,
                inverseSurface = Color(0xFF22291D),
                inverseOnSurface = Color(0xFFEEF2E4),
                error = Color(0xFFBA1A1A),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002),
                outline = Color(0xFF737A66),
                outlineVariant = Color(0xFFC4CAB6),
                scrim = Color.Black,
                surfaceBright = Color(0xFFF9FAF3),
                surfaceDim = Color(0xFFD7DACC),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFEDEFE4),
                surfaceContainer = Color(0xFFE7EADD),
                surfaceContainerHigh = Color(0xFFE1E4D7),
                surfaceContainerHighest = Color(0xFFDBDFD1),
            )
        }

    /**
     * The system scheme keeps the OS-provided accent roles, but takes the whole
     * neutral family from one consistent seeded palette so `surface` and
     * `surfaceContainer*` always line up (the legacy palette API makes dark
     * `surface` identical to `surfaceContainerLow`, flattening every card
     * into the background).
     */
    fun normalizeDynamic(
        system: ColorScheme,
        isDark: Boolean,
        style: PaletteStyle = PaletteStyle.TonalSpot,
    ): ColorScheme {
        val seeded = dynamicColorScheme(seedColor = system.primary, isDark = isDark, style = style)
        return if (style == PaletteStyle.TonalSpot) {
            seeded.copy(
                primary = system.primary,
                onPrimary = system.onPrimary,
                primaryContainer = system.primaryContainer,
                onPrimaryContainer = system.onPrimaryContainer,
                secondary = system.secondary,
                onSecondary = system.onSecondary,
                secondaryContainer = system.secondaryContainer,
                onSecondaryContainer = system.onSecondaryContainer,
                tertiary = system.tertiary,
                onTertiary = system.onTertiary,
                tertiaryContainer = system.tertiaryContainer,
                onTertiaryContainer = system.onTertiaryContainer,
            )
        } else {
            seeded
        }
    }

    fun scheme(
        context: Context,
        source: ColorSource,
        accent: AccentColor,
        isDark: Boolean,
    ): ColorScheme = scheme(context, source, accent, WallpaperPalette.TonalSpot, isDark)

    fun scheme(
        context: Context,
        source: ColorSource,
        accent: AccentColor,
        wallpaperPalette: WallpaperPalette,
        isDark: Boolean,
    ): ColorScheme =
        if (source == ColorSource.System && dynamicColorSupported) {
            val system = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            normalizeDynamic(system, isDark, wallpaperPalette.style)
        } else {
            accentScheme(accent, isDark)
        }

    /** M3 shape scale (4/8/12/16/28). */
    val shapes = Shapes(
        extraSmall = AppRadius.rXs,
        small = AppRadius.rSm,
        medium = AppRadius.rMd,
        large = AppRadius.rLg,
        extraLarge = AppRadius.rXxl,
    )
}

/** The colors a picker tile draws for one palette (see [AppTheme.paletteSwatch]): the controls', their container's, and the accent. */
@Immutable
data class PaletteSwatch(val primary: Color, val container: Color, val tertiary: Color)

/** Volt's two colours: the near-black green and the lime. */
internal val VoltInk = Color(0xFF0A2C1B)
internal val VoltLime = Color(0xFFAFE22D)

/** Appearance inputs of [NormPixTheme]. */
data class Appearance(
    val mode: ThemeMode = ThemeMode.System,
    val colorSource: ColorSource = ColorSource.System,
    val accent: AccentColor = AccentColor.Volt,
    /** The interface's typeface (Settings > Appearance > Interface font). */
    val font: AppFont = AppFont(),
    val wallpaperPalette: WallpaperPalette = WallpaperPalette.TonalSpot,
)

@Composable
fun Appearance.resolveDark(): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun NormPixTheme(
    appearance: Appearance = Appearance(),
    content: @Composable () -> Unit,
) {
    val dark = appearance.resolveDark()
    val context = LocalContext.current
    val scheme = remember(appearance.colorSource, appearance.accent, appearance.wallpaperPalette, dark) {
        AppTheme.scheme(context, appearance.colorSource, appearance.accent, appearance.wallpaperPalette, dark)
    }
    val status = remember(scheme, dark) { AppStatusColors.fromScheme(scheme, dark) }
    val typography = remember(appearance.font) { appTypography(appearance.font) }
    // The window behind the app takes the app's background, not the system's: the app's light / dark choice can differ,
    // and any moment nothing is drawn over it (a page arriving, the keyboard resizing) would flash the wrong one.
    val view = LocalView.current
    SideEffect {
        var host: Context? = view.context
        while (host is ContextWrapper && host !is Activity) host = host.baseContext
        (host as? Activity)?.window?.setBackgroundDrawable(scheme.surface.toArgb().toDrawable())
    }
    CompositionLocalProvider(LocalAppStatusColors provides status) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography,
            shapes = AppTheme.shapes,
            content = content,
        )
    }
}

/** Convenience: is the ambient scheme a dark one? */
val ColorScheme.isDark: Boolean get() = surface.luminance() < 0.5f

/** Standard "elevation" for tonal surfaces. The app expresses depth through tone, not shadow. */
val AppTonalElevation = 0.dp
