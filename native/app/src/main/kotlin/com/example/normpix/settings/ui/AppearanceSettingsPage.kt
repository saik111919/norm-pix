package com.example.normpix.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.AppIcon
import com.example.normpix.settings.AppIcons
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.settings.CategoryIconStyle
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.settings.ProgressStyle
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.finance.ui.CategoryGlyph
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.finance.ui.LocalCategoryIconStyle
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.AppTheme
import com.example.normpix.ui.theme.isDark
import kotlinx.coroutines.launch

private val themeOptions = listOf(
    ChoiceOption(ThemeMode.System, "System", Icons.Outlined.BrightnessAuto),
    ChoiceOption(ThemeMode.Light, "Light", Icons.Outlined.LightMode),
    ChoiceOption(ThemeMode.Dark, "Dark", Icons.Outlined.DarkMode),
)

/** Settings > Appearance: light/dark theme, colors, and page transitions. */
@Composable
fun AppearanceSettingsPage() {
    val navigator = LocalNavigator.current
    val preferences = LocalAppContainer.current.preferences
    val prefs by preferences.state.collectAsState()
    val haptics = rememberAppHaptics()
    val wallpaperColors = AppTheme.dynamicColorSupported
    val usingWallpaper = prefs.colorSource == ColorSource.System && wallpaperColors
    val context = LocalContext.current
    val overlays = LocalOverlays.current
    val scope = LocalAppScope.current
    // Android holds the choice; read it each time the page opens.
    var appIcon by remember { mutableStateOf(AppIcons.current(context)) }

    AppSubPage(title = "Appearance", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                // The app's own choice control (as in the editors), straight on the page: it is a control, not a list.
                SectionSubheader("Theme")
                ConnectedChoiceRow(
                    options = themeOptions,
                    selected = prefs.mode,
                    onSelect = preferences::setMode,
                )
                ListSection(
                    title = "Colors",
                    footer = if (!wallpaperColors) {
                        "This device doesn't provide wallpaper colors, so NormPix uses the color you pick."
                    } else {
                        null
                    },
                ) {
                    item {
                        SwitchRow(
                            headline = "Use wallpaper colors",
                            supporting = "Match the colors of your wallpaper and system",
                            checked = usingWallpaper,
                            enabled = wallpaperColors,
                            onCheckedChange = { on ->
                                haptics.selection()
                                preferences.setColorSource(if (on) ColorSource.System else ColorSource.Custom)
                            },
                        )
                    }
                    if (!usingWallpaper) {
                        item {
                            AccentPicker(
                                selected = prefs.accent,
                                onSelected = { accent ->
                                    haptics.selection()
                                    preferences.setAccent(accent)
                                },
                                modifier = Modifier.padding(AppSpacing.lg),
                            )
                        }
                    }
                }
                ListSection(
                    title = "App icon",
                    footer = "How NormPix looks on your home screen and in the app list. Android may close NormPix while it " +
                        "changes the icon, and some home screens drop the old one - add it back from the app list.",
                ) {
                    item {
                        AppIconPicker(
                            selected = appIcon,
                            onSelected = { icon ->
                                haptics.selection()
                                scope.launch {
                                    val change = overlays.confirm(
                                        title = "Use the ${icon.label} icon?",
                                        message = "NormPix may close for a moment while Android changes its icon. " +
                                            "If it leaves your home screen, add it back from the app list.",
                                        confirmLabel = "Change icon",
                                    )
                                    if (!change) return@launch
                                    AppIcons.use(context, icon)
                                    appIcon = icon
                                }
                            },
                            modifier = Modifier.padding(AppSpacing.lg),
                        )
                    }
                }
                ListSection(
                    title = "Effects",
                    footer = "Needs Android 12 or later with the system's blur effects on. Where blur is off or unavailable, " +
                        "sheets, dialogs and the floating bars are solid and the page behind them stays sharp.",
                ) {
                    item {
                        SwitchRow(
                            headline = "Background blur",
                            supporting = "Blur the page behind sheets and dialogs, and under the floating bars",
                            checked = prefs.sheetBlurEnabled,
                            onCheckedChange = { on ->
                                haptics.selection()
                                preferences.setSheetBlurEnabled(on)
                            },
                        )
                    }
                }
                ListSection(
                    title = "Progress indicators",
                    footer = "How budgets, spending shares and anything loading show their progress.",
                    modifier = Modifier.selectableGroup(),
                ) {
                    for (style in ProgressStyle.entries) {
                        item {
                            RadioRow(
                                headline = style.label,
                                supporting = style.description,
                                selected = prefs.progressStyle == style,
                                onClick = {
                                    haptics.selection()
                                    preferences.setProgressStyle(style)
                                },
                                trailing = { ProgressPreview(style) },
                            )
                        }
                    }
                }
                ListSection(
                    title = "Category icons",
                    footer = "How categories are pictured in lists, charts and the transaction editor. A category with an emoji of " +
                        "its own keeps it in every style.",
                    modifier = Modifier.selectableGroup(),
                ) {
                    for (style in CategoryIconStyle.entries) {
                        item {
                            RadioRow(
                                headline = style.label,
                                supporting = style.description,
                                selected = prefs.categoryIconStyle == style,
                                onClick = {
                                    haptics.selection()
                                    preferences.setCategoryIconStyle(style)
                                },
                                trailing = { CategoryIconStylePreview(style) },
                            )
                        }
                    }
                }
                ListSection(
                    title = "Page transitions",
                    footer = "Pages, tabs, a screen's sections and the back gesture's preview all move in the style you pick. " +
                        "When animations are turned off in system settings, they change without animating.",
                    modifier = Modifier.selectableGroup(),
                ) {
                    for (type in PageAnimationType.entries) {
                        item {
                            RadioRow(
                                headline = type.label,
                                supporting = type.description,
                                selected = prefs.pageAnimation == type,
                                onClick = {
                                    haptics.selection()
                                    preferences.setPageAnimation(type)
                                },
                            )
                        }
                    }
                }
                ListSection(
                    title = "Back button style",
                    footer = "Choose how pages present their back navigation. Top floating circle also floats the header of Home, Plans & Transactions, Insights, Wallet and search over their content.",
                    modifier = Modifier.selectableGroup(),
                ) {
                    for (style in BackButtonStyle.entries) {
                        item {
                            RadioRow(
                                headline = style.label,
                                supporting = style.description,
                                selected = prefs.backButtonStyle == style,
                                onClick = {
                                    haptics.selection()
                                    preferences.setBackButtonStyle(style)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** What an icon style looks like: three categories as they would be drawn in that style, whichever one is chosen. */
@Composable
private fun CategoryIconStylePreview(style: CategoryIconStyle) {
    CompositionLocalProvider(LocalCategoryIconStyle provides style) {
        Row(
            horizontalArrangement = Arrangement.spacedBy((-6).dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            for ((index, key) in listOf("restaurant", "transport", "shopping").withIndex()) {
                val tone = paletteColor(index * 2)
                Box(
                    Modifier.size(32.dp).background(tone.container, CircleShape).border(2.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { CategoryGlyph(key, tone.onContainer, 18.dp) }
            }
        }
    }
}

/** What a progress style looks like: a bar part-way along, and the loader, drawn in that style whichever is chosen. */
@Composable
private fun ProgressPreview(style: ProgressStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clearAndSetSemantics {}) {
        AppLinearProgress(Modifier.width(56.dp), progress = { 0.6f }, style = style)
        Spacer(Modifier.width(AppSpacing.sm))
        AppLoadingIndicator(Modifier.size(32.dp), style = style, strokeWidth = 3.dp)
    }
}

/** The launcher icons, five to a row, each as a round launcher shows it, with its name underneath. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppIconPicker(selected: AppIcon, onSelected: (AppIcon) -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    FlowRow(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        maxItemsInEachRow = 5,
    ) {
        for (icon in AppIcon.entries) {
            val isSelected = icon == selected
            val ring by animateColorAsState(if (isSelected) primary else Color.Transparent, label = "appIconRing")
            Column(
                Modifier
                    .weight(1f)
                    .clip(AppRadius.rMd)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { if (!isSelected) onSelected(icon) })
                    .clearAndSetSemantics {
                        contentDescription = "${icon.label} icon"
                        role = Role.RadioButton
                        this.selected = isSelected
                    }
                    .padding(vertical = AppSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    AppIconImage(icon, Modifier.size(52.dp).border(2.dp, ring, CircleShape).padding(4.dp))
                    if (isSelected) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                                .background(primary, CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainer, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.xs))
                Text(icon.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** An app icon as a round launcher shows it: its two layers, of which only the middle two thirds are ever shown. */
@Composable
private fun AppIconImage(icon: AppIcon, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape)) {
        for (layer in listOf(icon.background, icon.foreground)) {
            Image(
                painterResource(layer),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                },
            )
        }
    }
}

/**
 * Accent swatches, three to a row so six sit as two even rows rather than five and one left over. Each shows the primary
 * color actually generated from its seed for the current brightness (Volt: its ink with the lime inside), with its name
 * underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentPicker(selected: AccentColor, onSelected: (AccentColor) -> Unit, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.isDark
    val onSurface = MaterialTheme.colorScheme.onSurface
    FlowRow(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        maxItemsInEachRow = 3,
    ) {
        for (accent in AccentColor.entries) {
            val scheme = remember(accent, dark) { AppTheme.accentScheme(accent, dark) }
            val swatch = remember(accent, dark) { AppTheme.accentSwatch(accent, dark) }
            val isSelected = accent == selected
            val ring by animateColorAsState(if (isSelected) onSurface else Color.Transparent, label = "accentRing")
            Column(
                Modifier
                    .weight(1f)
                    .clip(AppRadius.rMd)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(accent) })
                    .clearAndSetSemantics {
                        contentDescription = "${accent.label} color"
                        role = Role.RadioButton
                        this.selected = isSelected
                    }
                    .padding(AppSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // One colour, or a pair (the outer one with the other inside it).
                val middle = swatch.last()
                val tick = if (swatch.size == 1) scheme.onPrimary else if (middle.luminance() > 0.5f) Color.Black else Color.White
                Box(
                    Modifier
                        .size(48.dp)
                        .border(2.dp, ring, CircleShape)
                        .padding(3.dp)
                        .background(swatch.first(), CircleShape)
                        .then(if (swatch.size > 1) Modifier.border(1.dp, onSurface.copy(alpha = 0.24f), CircleShape) else Modifier)
                        .padding(if (swatch.size > 1) 9.dp else 0.dp)
                        .background(middle, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = tick)
                }
                Spacer(Modifier.height(AppSpacing.xs))
                Text(accent.label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
