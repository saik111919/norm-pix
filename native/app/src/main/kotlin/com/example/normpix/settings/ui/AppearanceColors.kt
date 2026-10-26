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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Phone
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
 * - A phone preview at the top reflecting the wallpaper/theme colors in real time.
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
        // Phone mockup preview at the top
        PhoneMockupPreview(
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
 * Pixel-style phone preview mockup displaying the wallpaper background, live status clock,
 * themed home screen app icons, dock icons, and search pill.
 */
@Composable
private fun PhoneMockupPreview(
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
                            activeScheme.primaryContainer.copy(alpha = 0.65f),
                            activeScheme.surfaceContainerHighest,
                            activeScheme.surface,
                        )
                    } else {
                        listOf(
                            activeScheme.primaryContainer.copy(alpha = 0.45f),
                            activeScheme.surfaceContainerLow,
                            activeScheme.surface,
                        )
                    },
                ),
            )
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.md),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top: Date & Clock
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = AppSpacing.xs),
            ) {
                Text(
                    text = "Sun, Oct 26",
                    style = MaterialTheme.typography.labelSmall,
                    color = activeScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
                Text(
                    text = "10:40",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = activeScheme.onSurface,
                )
            }

            // Middle: App icon grid (4 mini apps)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniAppIcon(color = activeScheme.primary, symbol = "▶")
                MiniAppIcon(color = activeScheme.secondary, symbol = "✉")
                MiniAppIcon(color = activeScheme.tertiary, symbol = "✿")
                MiniAppIcon(color = activeScheme.error, symbol = "●")
            }

            // Bottom: Dock icons + Google search pill
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MiniDockIcon(
                        container = activeScheme.primaryContainer,
                        iconTint = activeScheme.onPrimaryContainer,
                        icon = Icons.Outlined.Phone,
                    )
                    MiniDockIcon(
                        container = activeScheme.secondaryContainer,
                        iconTint = activeScheme.onSecondaryContainer,
                        icon = Icons.AutoMirrored.Outlined.Chat,
                    )
                    MiniDockIcon(
                        container = activeScheme.tertiaryContainer,
                        iconTint = activeScheme.onTertiaryContainer,
                        icon = Icons.Outlined.Language,
                    )
                    MiniDockIcon(
                        container = activeScheme.surfaceContainerHighest,
                        iconTint = activeScheme.onSurface,
                        icon = Icons.Outlined.CameraAlt,
                    )
                }

                // Google search bar pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(CircleShape)
                        .background(activeScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                        .border(1.dp, activeScheme.outlineVariant.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = AppSpacing.sm),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "G",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = activeScheme.primary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = activeScheme.onSurfaceVariant,
                            )
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = activeScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniAppIcon(color: Color, symbol: String) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}

@Composable
private fun MiniDockIcon(container: Color, iconTint: Color, icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(14.dp),
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
