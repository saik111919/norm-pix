package com.example.normpix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing

/**
 * One entry of [AppPrimaryTabs] or [AppSecondaryTabs]. [selectedIcon] replaces [icon] while
 * selected (outlined to filled); [badge] sits after the label (a count, say).
 */
@Immutable
class AppTab(
    val label: String,
    val icon: ImageVector? = null,
    val selectedIcon: ImageVector? = icon,
    val badge: (@Composable () -> Unit)? = null,
)

/**
 * Primary tabs: the app's top-level views of a screen, as one tonal pill with a
 * selected pill that slides between the tabs (Material 3 Expressive: container and
 * shape carry the grouping, a springy indicator carries the state). Each tab is a
 * 48dp touch target. Pair with [AppSecondaryTabs] for the level below, which
 * deliberately looks lighter.
 */
@Composable
fun AppPrimaryTabs(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val position = rememberIndicatorPosition(selectedIndex)
    val indicator = scheme.primaryContainer
    val inset = PRIMARY_INSET
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
            .drawBehind {
                val inner = inset.toPx()
                val width = (size.width - 2 * inner) / tabs.size
                val travel = position.value * width
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - inner - width - travel else inner + travel
                drawRoundRect(
                    indicator,
                    topLeft = Offset(x, inner),
                    size = Size(width, size.height - 2 * inner),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            .padding(inset)
            .selectableGroup(),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val content by animateColorAsState(
                if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                tween(AppMotion.SHORT_MS, easing = AppMotion.standard),
                label = "primary_tab_content",
            )
            Row(
                Modifier
                    .weight(1f)
                    .height(PRIMARY_TAB_HEIGHT)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.Tab, onClick = {
                        if (!selected) haptics.selection()
                        onSelect(index)
                    })
                    // Tight on purpose: two tabs share the width, and large text needs the room.
                    .padding(horizontal = AppSpacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val icon = if (selected) tab.selectedIcon else tab.icon
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(AppIconSizes.md))
                    Spacer(Modifier.width(AppSpacing.sm))
                }
                Text(
                    tab.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                tab.badge?.let {
                    Spacer(Modifier.width(AppSpacing.sm))
                    it()
                }
            }
        }
    }
}

/**
 * Secondary tabs: the sections inside one primary tab, as a row of pills of equal width, each with its icon - the section you
 * are in filled and its icon solid, the others outlined. Separate pills rather than a second track, so they read as a lighter
 * choice under [AppPrimaryTabs] instead of a copy of it, and in the same shapes and tones as the rest of the app (the underline
 * over a hairline they replace belonged to an older design language, and sat beside the primary pill like another app's).
 * Each is a 48dp touch target around a 40dp pill.
 *
 * With large text the icons give way to the words, and from [SECONDARY_SCROLLS_FROM_FONT_SCALE] up the row scrolls sideways, each
 * pill as wide as its word and the one you are in kept in view: three equal pills on a phone cannot hold "Reminders" at twice
 * the text, and were "Budg…", "Recu…" and "Remin…".
 */
@Composable
fun AppSecondaryTabs(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val fontScale = LocalDensity.current.fontScale
    val showIcons = fontScale < SECONDARY_ICONS_BELOW_FONT_SCALE
    val choose = { index: Int ->
        if (index != selectedIndex) haptics.selection()
        onSelect(index)
    }
    if (fontScale >= SECONDARY_SCROLLS_FROM_FONT_SCALE) {
        val inView = remember(tabs.size) { List(tabs.size) { BringIntoViewRequester() } }
        LaunchedEffect(selectedIndex, tabs.size) { inView.getOrNull(selectedIndex)?.bringIntoView() }
        Row(
            modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            tabs.forEachIndexed { index, tab ->
                SecondaryTab(
                    tab = tab,
                    selected = index == selectedIndex,
                    showIcon = false,
                    wrap = true,
                    onClick = { choose(index) },
                    modifier = Modifier.bringIntoViewRequester(inView[index]),
                )
            }
        }
        return
    }
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            // The one you are in is a little wider: it has its icon to carry, and the others are words only.
            val weight by animateFloatAsState(if (selected) SECONDARY_SELECTED_WEIGHT else 1f, AppMotion.spatial(), label = "secondary_tab_weight")
            SecondaryTab(
                tab = tab,
                selected = selected,
                showIcon = showIcons,
                wrap = false,
                onClick = { choose(index) },
                modifier = Modifier.weight(weight.coerceAtLeast(0.1f)),
            )
        }
    }
}

@Composable
private fun SecondaryTab(
    tab: AppTab,
    selected: Boolean,
    showIcon: Boolean,
    wrap: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val spec = tween<Color>(AppMotion.SHORT_MS, easing = AppMotion.standard)
    val content by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, spec, label = "secondary_tab_content")
    val fill by animateColorAsState(
        when {
            selected -> scheme.secondaryContainer
            pressed -> scheme.onSurface.copy(alpha = 0.08f)
            else -> Color.Transparent
        },
        spec,
        label = "secondary_tab_fill",
    )
    // The outline tone, not the softer outlineVariant: the edge of an unselected pill is what shows it is one (3:1).
    val outline by animateColorAsState(if (selected) Color.Transparent else scheme.outline, spec, label = "secondary_tab_outline")
    // The pill grows with the text, so a bigger word is not squeezed into a 40dp one; the touch target stays around it.
    val grow = LocalDensity.current.fontScale.coerceIn(1f, SECONDARY_MAX_GROW)
    val pillHeight = SECONDARY_TAB_HEIGHT * grow
    val target = maxOf(SECONDARY_TAB_TARGET, pillHeight + 8.dp)
    // The press is shown by the fill (a ripple would be as tall as the touch target, taller than the pill), and keyboard
    // focus by a ring (the ripple's other job).
    Box(
        modifier
            .height(target)
            .selectable(selected = selected, interactionSource = press, indication = null, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            (if (wrap) Modifier.widthIn(min = SECONDARY_WRAP_MIN_WIDTH) else Modifier.fillMaxWidth())
                .height(pillHeight)
                .clip(CircleShape)
                .background(fill)
                .border(1.dp, outline, CircleShape)
                .focusRing(press, CircleShape)
                .padding(horizontal = if (wrap) AppSpacing.lg else AppSpacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Only the section you are in shows its icon (solid): enough to say where you are, and the words keep their room.
            if (showIcon && selected && tab.selectedIcon != null) {
                Icon(tab.selectedIcon, contentDescription = null, tint = content, modifier = Modifier.size(AppIconSizes.sm))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                tab.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                softWrap = !wrap,
                overflow = TextOverflow.Ellipsis,
                // In a scrolling row the width is unbounded, and a weighted child of a row with no width limit gets only the row's
                // minimum - the 72dp of the pill less its padding: 40dp for "Budgets". Left to size itself, the word is whole.
                modifier = if (wrap) Modifier else Modifier.weight(1f, fill = false),
            )
        }
        // A count rides the pill's corner, as on a bell, rather than taking room from the word.
        tab.badge?.let { badge ->
            Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp)) { badge() }
        }
    }
}

/** Where the indicator sits, in tab widths from the first tab, on the shared [AppMotion.spatial] spring. */
@Composable
private fun rememberIndicatorPosition(selectedIndex: Int): State<Float> = animateFloatAsState(
    targetValue = selectedIndex.toFloat(),
    animationSpec = AppMotion.spatial(),
    label = "tab_indicator",
)

private val PRIMARY_INSET = 4.dp
private val PRIMARY_TAB_HEIGHT = 48.dp

/** A secondary tab is a pill this tall inside a touch target this tall. */
private val SECONDARY_TAB_HEIGHT = 40.dp
private val SECONDARY_TAB_TARGET = 48.dp

/** From this font scale up, the secondary tabs drop their icons so the words keep their room. */
private const val SECONDARY_ICONS_BELOW_FONT_SCALE = 1.3f

/** The section you are in is this much wider than each of the others. */
private const val SECONDARY_SELECTED_WEIGHT = 1.2f

/** From this font scale up the secondary tabs scroll sideways, each pill as wide as its word (see [AppSecondaryTabs]). */
internal const val SECONDARY_SCROLLS_FROM_FONT_SCALE = 1.3f

/** The most the secondary tabs' pills grow with the text. */
private const val SECONDARY_MAX_GROW = 1.5f

/** A scrolling pill is at least this wide, so a short word is still an easy target. */
private val SECONDARY_WRAP_MIN_WIDTH = 72.dp
