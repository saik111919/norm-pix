package com.example.normpix.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Layers
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.CategoryAvatarSample
import com.example.normpix.finance.ui.CategoryGlyph
import com.example.normpix.finance.ui.LocalCategoryIconShape
import com.example.normpix.finance.ui.LocalCategoryIconStyle
import com.example.normpix.finance.ui.outline
import com.example.normpix.finance.ui.paletteColor
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.AppIcon
import com.example.normpix.settings.AppIcons
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.settings.CategoryIconShape
import com.example.normpix.settings.CategoryIconStyle
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.InterfaceFont
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.settings.ProgressStyle
import com.example.normpix.settings.SheetAnimationType
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ConfirmSheetContent
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.AppTheme
import com.example.normpix.ui.theme.isDark
import kotlinx.coroutines.launch

/** One screen of Settings > Appearance: [section]'s own choices, under its name. */
@Composable
fun AppearanceSectionPage(section: AppearanceSection) {
    val navigator = LocalNavigator.current
    val preferences = LocalAppContainer.current.preferences
    val prefs by preferences.state.collectAsState()
    val haptics = rememberAppHaptics()

    AppSubPage(title = section.title, onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                when (section) {
                    AppearanceSection.Colors -> ColorsSection(prefs, preferences, haptics)
                    AppearanceSection.AppIcon -> AppIconSection(haptics)
                    AppearanceSection.InterfaceFont -> InterfaceFontSection(prefs, preferences, haptics)
                    AppearanceSection.CategoryIcons -> CategoryIconsSection(prefs, preferences, haptics)
                    AppearanceSection.ProgressIndicators -> ProgressIndicatorsSection(prefs, preferences, haptics)
                    AppearanceSection.Effects -> EffectsSection(prefs, preferences, haptics)
                    AppearanceSection.PageTransitions -> PageTransitionsSection(prefs, preferences, haptics)
                    AppearanceSection.SheetTransitions -> SheetTransitionsSection(prefs, preferences, haptics)
                    AppearanceSection.BackButton -> BackButtonSection(prefs, preferences, haptics)
                }
            }
        }
    }
}

@Composable
private fun ColorsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    val wallpaperColors = AppTheme.dynamicColorSupported
    val usingWallpaper = prefs.colorSource == ColorSource.System && wallpaperColors
    ListSection(
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
}

@Composable
private fun AppIconSection(haptics: AppHaptics) {
    val context = LocalContext.current
    val overlays = LocalOverlays.current
    val scope = LocalAppScope.current
    // Android holds the choice; read it each time the page opens.
    var appIcon by remember { mutableStateOf(AppIcons.current(context)) }
    ListSection(
        footer = "Android may close NormPix while it changes the icon, and some home screens drop the old one - " +
            "add it back from the app list.",
    ) {
        item {
            AppIconCard(
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
            )
        }
    }
}

@Composable
private fun InterfaceFontSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    val custom = rememberCustomFontActions()
    ListSection(
        footer = when (prefs.interfaceFont) {
            InterfaceFont.GsFlex -> "Applies to all text in NormPix. GS Flex is Google Sans Flex, under the SIL Open Font License."
            InterfaceFont.Custom ->
                "Applies to all text in NormPix. Where your font has no letter for something, it is drawn in your phone's font."
            InterfaceFont.System ->
                "NormPix uses your phone's font. Pick GS Flex to choose a style, or to set its weight, width and roundness " +
                    "yourself - or Your font, for a font file of your own."
        },
    ) {
        item { InterfaceFontCard(prefs, preferences, haptics, custom) }
    }
}

@Composable
private fun CategoryIconsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
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
        title = "Icon background",
        footer = "The shape behind category icons and people in lists, charts and the transaction editor. Brand logos, " +
            "like a payee's, are cut to the same shape.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (shape in CategoryIconShape.entries) {
            item {
                RadioRow(
                    headline = shape.label,
                    supporting = shape.description,
                    selected = prefs.categoryIconShape == shape,
                    onClick = {
                        haptics.selection()
                        preferences.setCategoryIconShape(shape)
                    },
                    trailing = { CategoryIconShapePreview(shape) },
                )
            }
        }
    }
}

@Composable
private fun ProgressIndicatorsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
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
}

@Composable
private fun EffectsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
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
}

@Composable
private fun PageTransitionsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        footer = "Pages, tabs, a screen's sections and the back gesture's preview all move in the style you pick. " +
            "Sheets and dialogs have a style of their own, under Sheet & dialog transitions. " +
            "When animations are turned off in system settings, pages change without animating.",
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
}

@Composable
private fun SheetTransitionsSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    val overlays = LocalOverlays.current
    val scope = LocalAppScope.current
    ListSection(
        footer = "Every sheet - the forms and pickers - and every yes-or-no question moves in this style, whichever style " +
            "your pages use. When animations are turned off in system settings, they come up without extra motion.",
        modifier = Modifier.selectableGroup(),
    ) {
        for (type in SheetAnimationType.entries) {
            item {
                RadioRow(
                    headline = type.label,
                    supporting = type.description,
                    selected = prefs.sheetAnimation == type,
                    onClick = {
                        haptics.selection()
                        preferences.setSheetAnimation(type)
                    },
                )
            }
        }
    }
    ListSection(
        title = "Try it",
        footer = "Opens a sample, so you can see the style without leaving this page.",
    ) {
        item {
            ListRow(
                headline = "Open a sheet",
                supporting = "Like a form or a picker",
                leading = { LeadingIcon(Icons.Outlined.Layers) },
                onClick = {
                    scope.launch {
                        overlays.sheet<Unit> { done ->
                            ConfirmSheetContent(
                                title = "A sheet",
                                message = "This is how sheets open and close. Drag it down, tap outside it or go back to close it.",
                                confirmLabel = "Done",
                                cancelLabel = null,
                                destructive = false,
                                icon = null,
                                onConfirm = { done(null) },
                                onCancel = { done(null) },
                            )
                        }
                    }
                },
            )
        }
        item {
            ListRow(
                headline = "Open a question",
                supporting = "Like the ones that ask before deleting",
                leading = { LeadingIcon(Icons.Outlined.Layers) },
                onClick = {
                    scope.launch {
                        overlays.confirm(
                            title = "A question",
                            message = "This is how a yes-or-no question opens and closes. Nothing happens either way.",
                            confirmLabel = "Yes",
                            cancelLabel = "No",
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun BackButtonSection(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    ListSection(
        footer = "Choose how pages present their back navigation. Top floating circle also floats the header of Home, " +
            "Plans & Activity, Insights, Wallet and search over their content.",
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

/**
 * What an icon style looks like: three categories as they would be drawn in that style, whichever one is chosen - on the
 * background the user has chosen, or with none.
 */
@Composable
private fun CategoryIconStylePreview(style: CategoryIconStyle) {
    CompositionLocalProvider(LocalCategoryIconStyle provides style) {
        val shape = LocalCategoryIconShape.current
        val bare = shape == CategoryIconShape.None
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (bare) 2.dp else (-6).dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            for ((index, key) in listOf("restaurant", "transport", "shopping").withIndex()) {
                val tone = paletteColor(index * 2)
                if (bare) {
                    CategoryGlyph(key, tone.accent, 24.dp)
                } else {
                    Box(
                        Modifier.size(32.dp).background(tone.container, shape.outline).border(2.dp, MaterialTheme.colorScheme.surfaceContainer, shape.outline),
                        contentAlignment = Alignment.Center,
                    ) { CategoryGlyph(key, tone.onContainer, 18.dp) }
                }
            }
        }
    }
}

/** What an icon background looks like: three avatars in that shape (one a brand's logo), whichever one is chosen. */
@Composable
private fun CategoryIconShapePreview(shape: CategoryIconShape) {
    CompositionLocalProvider(LocalCategoryIconShape provides shape) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            for ((index, icon) in listOf("restaurant", "swiggy", "shopping").withIndex()) {
                CategoryAvatarSample(icon, colorIndex = index * 2, size = 32.dp)
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
