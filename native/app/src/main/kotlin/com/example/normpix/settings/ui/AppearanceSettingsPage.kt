package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AppIcon
import com.example.normpix.settings.AppIcons
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.InterfaceFont
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.AppTheme

/*
 * Settings > Appearance is an overview: the theme, which is a quick switch and stays on the page, then one row for each part
 * of how NormPix looks and moves, grouped as Look, Motion and Layout. A row shows what is chosen now and opens a screen of its
 * own for that part ([AppearanceSectionPage]).
 */

/** What the switch on the Colors row is called, for a screen reader (the Colors page's own switch row says the same). */
internal const val USE_WALLPAPER_COLORS = "Use wallpaper colors"

/** The groups of the Appearance overview. [hue] is the colour of its rows' icons (see [SettingsGlyph]). */
internal enum class AppearanceGroup(val title: String, val hue: Int) {
    Look("Look", hue = 3),
    Motion("Motion", hue = 6),
    Layout("Layout", hue = 5),
}

/** The group a section sits in on the Appearance overview. Every section must be placed: the build stops at one that is not. */
internal val AppearanceSection.group: AppearanceGroup
    get() = when (this) {
        AppearanceSection.Colors,
        AppearanceSection.AppIcon,
        AppearanceSection.InterfaceFont,
        AppearanceSection.CategoryIcons,
        AppearanceSection.ProgressIndicators,
        AppearanceSection.Effects,
        -> AppearanceGroup.Look
        AppearanceSection.PageTransitions,
        AppearanceSection.SheetTransitions,
        -> AppearanceGroup.Motion
        AppearanceSection.BackButton -> AppearanceGroup.Layout
    }

/** The icon of a section's row. */
internal val AppearanceSection.icon: ImageVector
    get() = when (this) {
        AppearanceSection.Colors -> Icons.Outlined.ColorLens
        AppearanceSection.AppIcon -> Icons.Outlined.Widgets
        AppearanceSection.InterfaceFont -> Icons.Outlined.TextFields
        AppearanceSection.CategoryIcons -> Icons.Outlined.Category
        AppearanceSection.ProgressIndicators -> Icons.Outlined.Autorenew
        AppearanceSection.Effects -> Icons.Outlined.AutoAwesome
        AppearanceSection.PageTransitions -> Icons.Outlined.SwapHoriz
        AppearanceSection.SheetTransitions -> Icons.Outlined.Layers
        AppearanceSection.BackButton -> Icons.Rounded.ChevronLeft
    }

/** What a section's row says under its name: the choice made now. [appIcon] is the launcher icon in use (Android holds that one). */
internal fun AppearanceSection.summary(prefs: AppPrefsState, appIcon: AppIcon): String = when (this) {
    AppearanceSection.Colors ->
        if (prefs.colorSource == ColorSource.System && AppTheme.dynamicColorSupported) "Wallpaper colors" else "${prefs.accent.label} accent"
    AppearanceSection.AppIcon -> appIcon.label
    AppearanceSection.InterfaceFont -> when (prefs.interfaceFont) {
        InterfaceFont.GsFlex -> if (prefs.customFont) "GS Flex · Set by hand" else "GS Flex · ${prefs.fontPreset.label}"
        InterfaceFont.System -> "System font"
        InterfaceFont.Custom -> prefs.customFontName.ifBlank { InterfaceFont.Custom.label }
    }
    AppearanceSection.CategoryIcons -> "${prefs.categoryIconStyle.label} · ${prefs.categoryIconShape.label}"
    AppearanceSection.ProgressIndicators -> prefs.progressStyle.label
    AppearanceSection.Effects -> if (prefs.sheetBlurEnabled) "Background blur on" else "Background blur off"
    AppearanceSection.PageTransitions -> prefs.pageAnimation.label
    AppearanceSection.SheetTransitions -> prefs.sheetAnimation.label
    AppearanceSection.BackButton -> prefs.backButtonStyle.label
}

private val themeOptions = listOf(
    ChoiceOption(ThemeMode.System, "System", Icons.Outlined.BrightnessAuto),
    ChoiceOption(ThemeMode.Light, "Light", Icons.Outlined.LightMode),
    ChoiceOption(ThemeMode.Dark, "Dark", Icons.Outlined.DarkMode),
)

/** Settings > Appearance: the light / dark theme, and a row for each part of how the app looks and moves. */
@Composable
fun AppearanceSettingsPage() {
    val navigator = LocalNavigator.current
    val preferences = LocalAppContainer.current.preferences
    val prefs by preferences.state.collectAsState()
    val context = LocalContext.current
    // Android holds the launcher icon; read it each time the page opens.
    val appIcon = remember { AppIcons.current(context) }
    val groupStyle = settingsGroupStyle()
    val haptics = rememberAppHaptics()

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
                for (group in AppearanceGroup.entries) {
                    ListSection(title = group.title, style = groupStyle) {
                        for (section in AppearanceSection.entries.filter { it.group == group }) {
                            item {
                                if (section == AppearanceSection.Colors) {
                                    // The one row that is also a switch: wallpaper colors or the user's own, without opening the page.
                                    val wallpaperColors = AppTheme.dynamicColorSupported
                                    SettingsSplitSwitchRow(
                                        headline = section.title,
                                        supporting = section.summary(prefs, appIcon),
                                        icon = section.icon,
                                        hue = group.hue,
                                        checked = prefs.colorSource == ColorSource.System && wallpaperColors,
                                        enabled = wallpaperColors,
                                        switchLabel = USE_WALLPAPER_COLORS,
                                        onClick = { navigator.openAppearance(section) },
                                        onCheckedChange = { on ->
                                            haptics.selection()
                                            preferences.setColorSource(if (on) ColorSource.System else ColorSource.Custom)
                                        },
                                    )
                                } else {
                                    SettingsRow(
                                        headline = section.title,
                                        supporting = section.summary(prefs, appIcon),
                                        icon = section.icon,
                                        hue = group.hue,
                                        onClick = { navigator.openAppearance(section) },
                                        trailing = {
                                            Icon(
                                                Icons.Rounded.ChevronRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
