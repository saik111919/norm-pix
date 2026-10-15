package com.example.normpix.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.settings.AppIcon
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.FontAxes
import com.example.normpix.settings.FontPreset
import com.example.normpix.settings.FontShape
import com.example.normpix.settings.InterfaceFont
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.gsFlexFamily
import kotlin.math.roundToInt

/** How many app icons sit in a row: four, so each is big enough to judge, and the rows line up as a grid. */
private const val ICONS_PER_ROW = 4

/**
 * The head of a settings card: its icon on a tonal disc, a title and a line saying what the card is for. Used by the cards
 * that hold one bigger control (the app icons, the interface font) rather than a list of rows.
 */
@Composable
internal fun SettingsCardHeader(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(52.dp).background(scheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.padding(start = AppSpacing.lg).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = scheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        }
    }
}

/** The App icon card: a header, then every launcher icon in a grid, the chosen one ringed and its name in the accent. */
@Composable
internal fun AppIconCard(selected: AppIcon, onSelected: (AppIcon) -> Unit) {
    Column(Modifier.padding(AppSpacing.lg)) {
        SettingsCardHeader(Icons.Outlined.Palette, "Home screen icon", "Choose how NormPix looks on your home screen")
        Spacer(Modifier.height(AppSpacing.lg))
        Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            for (row in AppIcon.entries.chunked(ICONS_PER_ROW)) {
                Row(Modifier.fillMaxWidth()) {
                    for (icon in row) AppIconChoice(icon, icon == selected, onSelected, Modifier.weight(1f))
                    // A short last row keeps the grid's columns rather than spreading its icons out.
                    repeat(ICONS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AppIconChoice(icon: AppIcon, isSelected: Boolean, onSelected: (AppIcon) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(if (isSelected) scheme.primary else Color.Transparent, label = "appIconRing")
    val labelColor by animateColorAsState(if (isSelected) scheme.primary else scheme.onSurface, label = "appIconLabel")
    Column(
        modifier
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
        // The ring is always there, clear until chosen, so choosing one moves nothing.
        Box(
            Modifier.size(68.dp).border(3.dp, ring, CircleShape).padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppIconArt(icon, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            icon.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = labelColor,
            maxLines = 1,
        )
    }
}

/** An app icon as a round launcher shows it: its two layers, of which only the middle two thirds are ever shown. */
@Composable
internal fun AppIconArt(icon: AppIcon, modifier: Modifier = Modifier) {
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

private val fontOptions = listOf(
    ChoiceOption(InterfaceFont.GsFlex, InterfaceFont.GsFlex.label, Icons.Outlined.TextFields),
    ChoiceOption(InterfaceFont.System, InterfaceFont.System.label, Icons.Outlined.Smartphone),
)

/**
 * The Interface font card: GS Flex or the phone's own font; with GS Flex, one of its styles - or, with Custom variable font
 * on, its weight, width and roundness set by hand. A sample under the controls shows the result before the whole app is.
 */
@Composable
internal fun InterfaceFontCard(prefs: AppPrefsState, preferences: AppPreferences, haptics: AppHaptics) {
    val flex = prefs.interfaceFont == InterfaceFont.GsFlex
    // While a slider is dragged only the sample follows it; the app takes the value once the slider is let go.
    var weight by remember(prefs.fontWeightShift) { mutableFloatStateOf(prefs.fontWeightShift.toFloat()) }
    var width by remember(prefs.fontWidth) { mutableFloatStateOf(prefs.fontWidth.toFloat()) }
    var roundness by remember(prefs.fontRoundness) { mutableFloatStateOf(prefs.fontRoundness.toFloat()) }
    val live = if (prefs.customFont) FontShape(weight.roundToInt(), width.roundToInt(), roundness.roundToInt()) else prefs.fontShape
    fun commit() = preferences.setFontShape(weight.roundToInt(), width.roundToInt(), roundness.roundToInt())

    Column(Modifier.padding(AppSpacing.lg)) {
        SettingsCardHeader(Icons.Outlined.TextFields, "Font and style", "Customize the interface font and style")
        Spacer(Modifier.height(AppSpacing.lg))
        ConnectedChoiceRow(
            options = fontOptions,
            selected = prefs.interfaceFont,
            onSelect = { font ->
                haptics.selection()
                preferences.setInterfaceFont(font)
            },
        )
        RevealVisibility(visible = flex) {
            Column {
                Spacer(Modifier.height(AppSpacing.sm))
                SwitchRow(
                    headline = "Custom variable font",
                    supporting = "Manually adjust weight, width and roundness",
                    checked = prefs.customFont,
                    onCheckedChange = { on ->
                        haptics.selection()
                        preferences.setCustomFont(on)
                    },
                )
                RevealVisibility(visible = !prefs.customFont) {
                    FontPresetRow(
                        selected = prefs.fontPreset,
                        onSelect = { preset ->
                            haptics.selection()
                            preferences.setFontPreset(preset)
                        },
                    )
                }
                RevealVisibility(visible = prefs.customFont) {
                    Column(Modifier.padding(horizontal = AppSpacing.xs)) {
                        FontAxisSlider(
                            label = "Weight",
                            value = weight,
                            valueText = "${400 + weight.roundToInt()}",
                            range = FontAxes.WEIGHT_SHIFT,
                            steps = (FontAxes.WEIGHT_SHIFT.last - FontAxes.WEIGHT_SHIFT.first) / 25 - 1,
                            onChange = { weight = it },
                            onDone = ::commit,
                        )
                        FontAxisSlider(
                            label = "Width",
                            value = width,
                            valueText = "${width.roundToInt()}%",
                            range = FontAxes.WIDTH,
                            steps = FontAxes.WIDTH.last - FontAxes.WIDTH.first - 1,
                            onChange = { width = it },
                            onDone = ::commit,
                        )
                        FontAxisSlider(
                            label = "Roundness",
                            value = roundness,
                            valueText = "${roundness.roundToInt()}",
                            range = FontAxes.ROUNDNESS,
                            steps = (FontAxes.ROUNDNESS.last - FontAxes.ROUNDNESS.first) / 5 - 1,
                            onChange = { roundness = it },
                            onDone = ::commit,
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.md))
                FontSample(live)
            }
        }
    }
}

/** GS Flex's styles as a row of pills, each name set in its own style; the chosen one filled. */
@Composable
private fun FontPresetRow(selected: FontPreset, onSelect: (FontPreset) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = AppSpacing.sm)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        for (preset in FontPreset.entries) {
            val isSelected = preset == selected
            val container by animateColorAsState(if (isSelected) scheme.primary else scheme.surfaceContainerHighest, label = "presetFill")
            val ink by animateColorAsState(if (isSelected) scheme.onPrimary else scheme.onSurface, label = "presetInk")
            val family = remember(preset) { gsFlexFamily(FontShape(preset.weightShift, preset.width, preset.roundness)) }
            Box(
                Modifier
                    .widthIn(min = 76.dp)
                    .heightIn(min = 48.dp)
                    .clip(AppRadius.rFull)
                    .background(container)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { if (!isSelected) onSelect(preset) })
                    .padding(horizontal = AppSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                Text(preset.label, style = MaterialTheme.typography.titleSmall.copy(fontFamily = family), color = ink, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FontAxisSlider(
    label: String,
    value: Float,
    valueText: String,
    range: IntRange,
    steps: Int,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.padding(top = AppSpacing.sm)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onDone,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps.coerceAtLeast(0),
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = valueText
            },
        )
    }
}

/** A line of text and a figure in [shape], to judge it by before the rest of the app changes. */
@Composable
private fun FontSample(shape: FontShape) {
    val scheme = MaterialTheme.colorScheme
    val family = remember(shape) { gsFlexFamily(shape) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(AppRadius.rLg)
            .background(scheme.surfaceContainerHighest)
            .padding(AppSpacing.lg)
            .clearAndSetSemantics {},
    ) {
        Text("Spent this month", style = MaterialTheme.typography.labelLarge.copy(fontFamily = family), color = scheme.onSurfaceVariant)
        Text("₹12,480.50", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = family, fontWeight = FontWeight.SemiBold), color = scheme.onSurface)
        Text("The quick brown fox jumps over the lazy dog", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = family), color = scheme.onSurface)
    }
}
