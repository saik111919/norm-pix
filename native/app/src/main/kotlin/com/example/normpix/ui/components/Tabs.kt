package com.example.normpix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Secondary tabs: the sections inside one primary tab. Label-only, flat on the page
 * surface, full width, with an underline indicator over a hairline divider - so they
 * read as a lighter, sub-level band under [AppPrimaryTabs] instead of a second copy of it.
 */
@Composable
fun AppSecondaryTabs(
    tabs: List<AppTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val position = rememberIndicatorPosition(selectedIndex)
    val indicator = scheme.primary
    val divider = scheme.outlineVariant
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val dividerHeight = 1.dp.toPx()
                drawRect(divider, Offset(0f, size.height - dividerHeight), Size(size.width, dividerHeight))
                val width = size.width / tabs.size
                val travel = position.value * width
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width - width - travel else travel
                val thickness = SECONDARY_INDICATOR.toPx()
                drawRoundRect(
                    indicator,
                    topLeft = Offset(x, size.height - thickness),
                    size = Size(width, thickness),
                    cornerRadius = CornerRadius(thickness / 2),
                )
            }
            .selectableGroup(),
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val content by animateColorAsState(
                if (selected) scheme.primary else scheme.onSurfaceVariant,
                tween(AppMotion.SHORT_MS, easing = AppMotion.standard),
                label = "secondary_tab_content",
            )
            Row(
                Modifier
                    .weight(1f)
                    .height(SECONDARY_TAB_HEIGHT)
                    .selectable(selected = selected, role = Role.Tab, onClick = {
                        if (!selected) haptics.selection()
                        onSelect(index)
                    })
                    .padding(horizontal = AppSpacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                tab.badge?.let {
                    Spacer(Modifier.width(AppSpacing.xs))
                    it()
                }
            }
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
private val SECONDARY_TAB_HEIGHT = 48.dp
private val SECONDARY_INDICATOR = 3.dp
