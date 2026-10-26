package com.example.normpix.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.settings.WallpaperPalette
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.AppTheme
import com.example.normpix.ui.theme.PaletteSwatch
import com.example.normpix.ui.theme.isDark

/** How many palette tiles sit on a row of the Colors page when displayed in a grid. */
internal const val PALETTES_PER_ROW = 4

/** The name of the wallpaper's palette, on its tile (and, with "colors" after it, to a screen reader). */
internal const val WALLPAPER_TILE = "Wallpaper"

private val sourceOptions = listOf(
    ChoiceOption(ColorSource.System, "Wallpaper colors", Icons.Outlined.Wallpaper),
    ChoiceOption(ColorSource.Custom, "Other colors", Icons.Outlined.Palette),
)

/**
 * Settings > Appearance > Colors, after Pixel's Wallpaper & style picker:
 * - An app mockup preview at the top reflecting the theme and colors in real time.
 * - A card with the wallpaper or custom palette swatches and an in-card "Dark theme" switch.
 * - A segmented toggle pill at the bottom switching between "Wallpaper colors" and "Other colors".
 */
@Composable
internal fun ColorsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.isDark
    val wallpaperColors = AppTheme.dynamicColorSupported
    val usingWallpaper = prefs.colorSource == ColorSource.System && wallpaperColors

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App mockup preview at the top representing NormPix UI
        AppMockupPreview(
            dark = dark,
            activeScheme = MaterialTheme.colorScheme,
            modifier = Modifier.padding(bottom = AppSpacing.lg),
        )

        // Main Card containing swatches and Dark Theme switch
        ListSection(
            footer = if (!wallpaperColors) "This device doesn't provide wallpaper colors, so NormPix uses the color you pick." else null,
        ) {
            item {
                Column(Modifier.padding(AppSpacing.lg)) {
                    Text(
                        text = if (usingWallpaper) {
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectableGroup(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (palette in WallpaperPalette.entries) {
                                val swatch = remember(palette, dark, context) {
                                    AppTheme.paletteSwatch(
                                        AppTheme.scheme(context, ColorSource.System, prefs.accent, palette, dark),
                                    )
                                }
                                CircularSwatchTile(
                                    label = palette.label,
                                    swatch = swatch,
                                    selected = palette == prefs.wallpaperPalette,
                                    onClick = {
                                        haptics.selection()
                                        preferences.setWallpaperPalette(palette)
                                    },
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .selectableGroup()
                                .padding(horizontal = AppSpacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            for (accent in AccentColor.entries) {
                                val swatch = remember(accent, dark) {
                                    AppTheme.paletteSwatch(AppTheme.accentScheme(accent, dark))
                                }
                                CircularSwatchTile(
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

                    Spacer(Modifier.height(AppSpacing.lg))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(AppSpacing.sm))

                    // Dark theme switch row inside the card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(AppRadius.rMd)
                            .clickable(role = Role.Switch) {
                                haptics.selection()
                                preferences.setMode(if (dark) ThemeMode.Light else ThemeMode.Dark)
                            }
                            .padding(vertical = AppSpacing.sm, horizontal = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Dark theme",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = dark,
                            onCheckedChange = { isChecked ->
                                haptics.selection()
                                preferences.setMode(if (isChecked) ThemeMode.Dark else ThemeMode.Light)
                            },
                            thumbContent = {
                                if (dark) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        // Segmented choice row at the bottom
        if (wallpaperColors) {
            Spacer(Modifier.height(AppSpacing.lg))
            ConnectedChoiceRow(
                options = sourceOptions,
                selected = if (usingWallpaper) ColorSource.System else ColorSource.Custom,
                onSelect = preferences::setColorSource,
            )
        }
    }
}

/**
 * A Pixel-style circular swatch tile:
 * The 3-way circular swatch sits inside a squircle frame.
 * When selected, a 3dp primary-colored squircle ring surrounds the swatch.
 */
@Composable
private fun CircularSwatchTile(
    label: String,
    swatch: PaletteSwatch,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ringColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "swatchRing",
    )
    val squircleShape = AppRadius.rXl

    Box(
        modifier = modifier
            .size(54.dp)
            .clip(squircleShape)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = ringColor,
                shape = squircleShape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$label color"
                role = Role.RadioButton
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        SwatchCircle(
            swatch = swatch,
            modifier = Modifier.size(42.dp),
            edge = scheme.onSurface.copy(alpha = 0.16f),
        )
    }
}

/**
 * Miniature preview mockup representing the NormPix app UI:
 * - App bar with title "NormPix" and profile indicator
 * - Balance / Featured card in primaryContainer
 * - Quick action buttons (Add, Send, Cards)
 * - Recent transaction row
 * - Floating navigation dock at the bottom
 */
@Composable
private fun AppMockupPreview(
    dark: Boolean,
    activeScheme: ColorScheme,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .width(168.dp)
            .height(264.dp)
            .clip(shape)
            .border(2.dp, activeScheme.outlineVariant.copy(alpha = 0.45f), shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (dark) {
                        listOf(
                            activeScheme.surfaceContainerHighest,
                            activeScheme.surface,
                        )
                    } else {
                        listOf(
                            activeScheme.surfaceContainerLow,
                            activeScheme.surface,
                        )
                    },
                ),
            )
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.sm),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top: App bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "NormPix",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    color = activeScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(activeScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(activeScheme.primary),
                    )
                }
            }

            // Featured Card: Balance / Overview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(activeScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Total Balance",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = activeScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    )
                    Text(
                        text = "$2,450.00",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = activeScheme.onPrimaryContainer,
                    )
                }
            }

            // Quick action chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MiniChip(
                    text = "Add",
                    container = activeScheme.secondaryContainer,
                    contentColor = activeScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                MiniChip(
                    text = "Send",
                    container = activeScheme.tertiaryContainer,
                    contentColor = activeScheme.onTertiaryContainer,
                    modifier = Modifier.weight(1f),
                )
                MiniChip(
                    text = "Cards",
                    container = activeScheme.surfaceContainerHighest,
                    contentColor = activeScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            // Recent activity row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(activeScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(activeScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(activeScheme.primary),
                        )
                    }
                    Text(
                        text = "Groceries",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = activeScheme.onSurface,
                    )
                }
                Text(
                    text = "-$32.50",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
                    color = activeScheme.onSurfaceVariant,
                )
            }

            // Floating Navigation Dock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(activeScheme.surfaceContainerHighest)
                    .border(1.dp, activeScheme.outlineVariant.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Active item
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(activeScheme.primary),
                    )
                    // Inactive items
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(activeScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(activeScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(activeScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniChip(
    text: String,
    container: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(22.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = contentColor,
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
