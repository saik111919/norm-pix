package com.example.normpix.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.ColorSource
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.AppTheme
import com.example.normpix.ui.theme.PaletteSwatch
import com.example.normpix.ui.theme.isDark

/** How many palette tiles sit on a row of the Colors page. */
internal const val PALETTES_PER_ROW = 4

/** The name of the wallpaper's palette, on its tile (and, with "colors" after it, to a screen reader). */
internal const val WALLPAPER_TILE = "Wallpaper"

private val sourceOptions = listOf(
    ChoiceOption(ColorSource.System, "Wallpaper colors", Icons.Outlined.Wallpaper),
    ChoiceOption(ColorSource.Custom, "Other colors", Icons.Outlined.Palette),
)

/**
 * Settings > Appearance > Colors, after the way Pixel's wallpaper & style screen picks colors: a switch between the wallpaper's
 * colors and the ones NormPix offers, and under it each palette as a tile - a circle in the three colors the palette gives the
 * app (its primary above, its container and its accent below) - so what is chosen is seen before it is. The choice is drawn in the
 * current light or dark, since that is how the app is drawn.
 *
 * Wallpaper colors are the one palette Android makes from the wallpaper, so that side has the one tile. Where the phone has no
 * wallpaper colors (before Android 12) there is no switch, and the offered colors are all there is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.isDark
    val wallpaperColors = AppTheme.dynamicColorSupported
    val usingWallpaper = prefs.colorSource == ColorSource.System && wallpaperColors

    if (wallpaperColors) {
        ConnectedChoiceRow(
            options = sourceOptions,
            selected = if (usingWallpaper) ColorSource.System else ColorSource.Custom,
            onSelect = preferences::setColorSource,
        )
        Spacer(Modifier.height(AppSpacing.lg))
    }
    ListSection(
        footer = if (!wallpaperColors) "This device doesn't provide wallpaper colors, so NormPix uses the color you pick." else null,
    ) {
        item {
            Column(Modifier.padding(AppSpacing.lg)) {
                Text(
                    if (usingWallpaper) {
                        "Icons, text and more match the colors in your wallpaper"
                    } else {
                        "Pick the colors NormPix wears. Light or dark follows the theme you chose on the Appearance page"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(AppSpacing.lg))
                if (usingWallpaper) {
                    PaletteGrid(count = 1) {
                        val wallpaper = remember(context, dark) {
                            AppTheme.paletteSwatch(AppTheme.scheme(context, ColorSource.System, prefs.accent, dark))
                        }
                        PaletteTile(WALLPAPER_TILE, wallpaper, selected = true, onClick = {})
                    }
                } else {
                    PaletteGrid(count = AccentColor.entries.size) {
                        for (accent in AccentColor.entries) {
                            val swatch = remember(accent, dark) { AppTheme.paletteSwatch(AppTheme.accentScheme(accent, dark)) }
                            PaletteTile(
                                label = accent.label,
                                swatch = swatch,
                                selected = accent == prefs.accent,
                                onClick = {
                                    haptics.selection()
                                    preferences.setAccent(accent)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * [PALETTES_PER_ROW] tiles to a row, the last row padded out with empty cells so its tiles are as wide as the rest rather than
 * stretching to fill it. [count] is how many tiles [content] draws.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteGrid(count: Int, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        maxItemsInEachRow = PALETTES_PER_ROW,
    ) {
        content()
        repeat((PALETTES_PER_ROW - count % PALETTES_PER_ROW) % PALETTES_PER_ROW) { Spacer(Modifier.weight(1f)) }
    }
}

/**
 * One palette: a square tile with the [swatch] as a circle - the primary on its upper half, the container and the accent
 * side by side on the lower - and its [label] beneath. The chosen tile is ringed in the primary color and ticked; a screen reader
 * hears "[label] color", as a radio button that is selected or not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowScope.PaletteTile(label: String, swatch: PaletteSwatch, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "paletteRing")
    val tile = AppRadius.rXl
    Column(
        Modifier
            .weight(1f)
            .clip(AppRadius.rLg)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$label color"
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(AppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scheme.surfaceContainerHighest, tile)
                    .border(3.dp, ring, tile),
            ) {
                SwatchCircle(swatch, Modifier.fillMaxSize().padding(12.dp), edge = scheme.onSurface.copy(alpha = 0.16f))
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(AppSpacing.xs).size(20.dp).background(scheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The circle of a palette: [PaletteSwatch.primary] above, [PaletteSwatch.container] and [PaletteSwatch.tertiary] below it, a hairline [edge] around. */
@Composable
internal fun SwatchCircle(swatch: PaletteSwatch, modifier: Modifier = Modifier, edge: Color = Color.Transparent) {
    Canvas(modifier) {
        val d = minOf(size.width, size.height)
        val origin = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val half = d / 2f
        val disc = Path().apply { addOval(Rect(origin, Size(d, d))) }
        clipPath(disc) {
            drawRect(swatch.primary, origin, Size(d, half))
            drawRect(swatch.container, Offset(origin.x, origin.y + half), Size(half, half))
            drawRect(swatch.tertiary, Offset(origin.x + half, origin.y + half), Size(half, half))
        }
        if (edge != Color.Transparent) {
            val width = 1.dp.toPx()
            drawCircle(edge, radius = half - width / 2f, center = Offset(origin.x + half, origin.y + half), style = Stroke(width))
        }
    }
}
