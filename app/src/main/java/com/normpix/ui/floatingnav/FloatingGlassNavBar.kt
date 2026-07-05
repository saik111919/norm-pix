package com.normpix.ui.floatingnav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A destination shown in [FloatingGlassNavBar]. */
@Immutable
data class FloatingNavItem(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    /** When > 0, a small error-colored count badge is drawn on the icon. */
    val badgeCount: Int = 0,
    /** Fired on a long-press of this item, in addition to the normal tap selection. */
    val onLongClick: (() -> Unit)? = null,
)

/**
 * A floating, glass-like Material 3 Expressive bottom navigation bar.
 *
 * The active tab shows icon + label inside a soft tinted pill; inactive tabs show
 * icons only. Labels animate in with a combined fade / slide / scale driven by
 * expressive springs, so motion feels calm and intentional.
 *
 * Selection state is hoisted: pass [selectedIndex] and react in [onItemSelected].
 *
 * Pass a [GlassBackdropState] (and tag your content with `Modifier.glassSource(state)`)
 * to get a real blurred backdrop on API 31+. Without it — or on older devices — the
 * bar falls back to a clean translucent surface.
 *
 * @param blurRadius blur strength of the glass backdrop (0.dp disables blur)
 * @param backgroundOpacity opacity of the tint layered over the blur (0f..1f)
 * @param cornerRadius corner radius of the floating pill container
 * @param activeContentColor color of the active tab's icon and label
 * @param inactiveIconColor color of inactive tab icons
 * @param activeIndicatorColor fill of the pill behind the active tab
 * @param animationDurationMillis overall animation speed; springs scale with it
 * @param barHeight total bar height (keep >= 60.dp so touch targets stay >= 48.dp)
 * @param itemSpacing horizontal gap between items
 * @param showLabels when false, items never reveal their label even when selected — used for
 * an icon-only pill (design's separate single-item Settings pill next to the main 4-tab pill)
 */
@Composable
fun FloatingGlassNavBar(
    items: List<FloatingNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    glassState: GlassBackdropState? = null,
    blurRadius: Dp = 22.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    backgroundOpacity: Float = 0.55f,
    cornerRadius: Dp = 32.dp,
    activeContentColor: Color = MaterialTheme.colorScheme.primary,
    inactiveIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    activeIndicatorColor: Color = activeContentColor.copy(alpha = 0.14f),
    animationDurationMillis: Int = 420,
    barHeight: Dp = 64.dp,
    itemSpacing: Dp = 4.dp,
    shadowElevation: Dp = 12.dp,
    saturation: Float = 1.25f,
    borderColor: Color = Color.White,
    showLabels: Boolean = true,
) {
    val shape = RoundedCornerShape(cornerRadius)
    // Keeps item pills at ~48.dp for accessible touch targets.
    val verticalPadding = ((barHeight - 48.dp) / 2).coerceAtLeast(4.dp)

    Row(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .glassPanel(
                state = glassState,
                shape = shape,
                blurRadius = blurRadius,
                backgroundColor = backgroundColor,
                backgroundOpacity = backgroundOpacity,
                saturation = saturation,
            )
            // Design: 1px border @ 35% + inset top highlight — approximated with a
            // gradient border that is brightest along the top edge.
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    0f to borderColor.copy(alpha = 0.40f),
                    1f to borderColor.copy(alpha = 0.18f),
                ),
                shape = shape,
            )
            .height(barHeight)
            .padding(horizontal = 8.dp, vertical = verticalPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            GlassNavItem(
                item = item,
                selected = index == selectedIndex,
                onClick = { onItemSelected(index) },
                activeContentColor = activeContentColor,
                inactiveIconColor = inactiveIconColor,
                indicatorColor = activeIndicatorColor,
                durationMillis = animationDurationMillis,
                showLabel = showLabels,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassNavItem(
    item: FloatingNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    activeContentColor: Color,
    inactiveIconColor: Color,
    indicatorColor: Color,
    durationMillis: Int,
    showLabel: Boolean = true,
) {
    val stiffness = remember(durationMillis) { spatialStiffnessFor(durationMillis) }

    val contentColor by animateColorAsState(
        targetValue = if (selected) activeContentColor else inactiveIconColor,
        animationSpec = tween(durationMillis, easing = EmphasizedEasing),
        label = "navItemContentColor",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) indicatorColor else Color.Transparent,
        animationSpec = tween(durationMillis, easing = EmphasizedEasing),
        label = "navItemPillColor",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .background(pillColor)
            .semantics { role = Role.Tab; this.selected = selected }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(color = activeContentColor),
                onLongClick = item.onLongClick,
                onClick = onClick,
            )
            .sizeIn(minWidth = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        // Crossfade + subtle scale between outlined and filled icon variants.
        Box {
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (fadeIn(tween(durationMillis / 2, easing = EmphasizedEasing)) +
                        scaleIn(
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = stiffness),
                            initialScale = 0.7f,
                        ))
                        .togetherWith(
                            fadeOut(tween(durationMillis / 3)) +
                                scaleOut(tween(durationMillis / 3), targetScale = 0.8f),
                        )
                        .using(SizeTransform(clip = false))
                },
                label = "navItemIcon",
            ) { isSelected ->
                Icon(
                    imageVector = if (isSelected) item.selectedIcon else item.icon,
                    // When the label is hidden, the icon carries the item's name for TalkBack.
                    contentDescription = if (isSelected) null else item.label,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (item.badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-5).dp)
                        .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                        .padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.badgeCount.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onError,
                        maxLines = 1,
                    )
                }
            }
        }

        // Label: visible only while selected (and only for pills that show labels at all);
        // fade + slide + scale, softly spring-driven.
        AnimatedVisibility(
            visible = showLabel && selected,
            enter = fadeIn(tween(durationMillis, easing = EmphasizedEasing)) +
                expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = stiffness,
                        visibilityThreshold = IntSize.VisibilityThreshold,
                    ),
                    expandFrom = Alignment.Start,
                ) +
                slideInHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = stiffness,
                        visibilityThreshold = IntOffset.VisibilityThreshold,
                    ),
                    initialOffsetX = { -it / 2 },
                ) +
                scaleIn(
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = stiffness),
                    initialScale = 0.9f,
                ),
            exit = fadeOut(tween(durationMillis * 3 / 5, easing = EmphasizedEasing)) +
                shrinkHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = stiffness,
                        visibilityThreshold = IntSize.VisibilityThreshold,
                    ),
                    shrinkTowards = Alignment.Start,
                ) +
                slideOutHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.9f,
                        stiffness = stiffness,
                        visibilityThreshold = IntOffset.VisibilityThreshold,
                    ),
                    targetOffsetX = { -it / 2 },
                ) +
                scaleOut(tween(durationMillis * 3 / 5), targetScale = 0.9f),
            label = "navItemLabel",
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** M3 emphasized easing — used for fades and color shifts (non-spatial "effects" motion). */
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Maps the duration knob to spring stiffness so spatial motion (slide/scale/expand)
 * keeps M3 Expressive's springy character at any speed. 420ms ≈ the expressive
 * default spatial spring (stiffness ~380).
 */
private fun spatialStiffnessFor(durationMillis: Int): Float =
    (380f * 420f / durationMillis.coerceAtLeast(60)).coerceIn(40f, 8_000f)
