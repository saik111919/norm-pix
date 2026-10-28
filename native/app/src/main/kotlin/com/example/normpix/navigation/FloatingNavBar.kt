package com.example.normpix.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.FrostedBackdrop
import com.example.normpix.ui.components.LocalFrostedBackdrop
import com.example.normpix.ui.components.frostedGlass
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.components.rememberFrostedChrome
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.isDark
import com.example.normpix.ui.theme.rememberWindowWidthDp

/*
 * Modern Floating Navigation Dock & Back Pill inspired by Google Pixel / Essentials design language.
 *
 * Characteristics:
 *   - Floats above the bottom edge and navigation bar with clean margins;
 *   - Deep saturated container color (primary teal in light theme) with subtle hairline border;
 *   - Active destination expands into an expressive pure white pill capsule containing icon + label;
 *   - Inactive destinations present clean white icons with accessible 48dp touch targets;
 *   - Circular "+" action button beside the dock opens the bento action panel (ActionMenu.kt);
 *   - Coordinated motion: one progress value turns "+" into "×", grows the panel out of it and steps the dock back;
 *   - Floating back pill provides thumb-friendly back navigation with circular white arrow disc;
 *   - Transforms smoothly during scroll and IME without triggering layout reflows.
 */

private val ItemHeight = 48.dp
private val DockPadding = 6.dp
private val DockCorner = 30.dp
internal val DockHeight = 60.dp
private val CompanionCorner = 18.dp
private val MinLabelWidth = 36.dp

/** How much of the screen's foot, above the system navigation bar, the dock (or the back row) takes: its height and the margin under it. */
val DockFootprint = DockHeight + 12.dp

@Immutable
data class DockColors(
    val container: Color,
    val content: Color,
    val activePill: Color,
    val activePillContent: Color,
    val inactiveIcon: Color,
    val companionContainer: Color,
    val companionContent: Color,
    val border: Color,
)

/**
 * Contextual action surfaced in the expanded More / Actions footer control.
 */
data class FooterAction(
    val label: String,
    val icon: ImageVector,
    val isPrimary: Boolean = false,
    val contentDescription: String? = null,
    val onClick: () -> Unit,
    /** What a saved arrangement of the "+" menu calls this action: stable, unlike the label (see [ActionMenuLayout]). */
    val id: String = label,
)

/** [blurred]: the bars are glass (see [rememberFrostedChrome]), so their tint is translucent; otherwise it is solid. */
@Composable
fun rememberDockColors(blurred: Boolean = rememberFrostedChrome()): DockColors {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.isDark
    return remember(scheme, isDark, blurred) {
        if (isDark) {
            DockColors(
                container = if (blurred) scheme.surfaceContainerHigh.copy(alpha = 0.82f) else scheme.surfaceContainerHigh,
                content = scheme.onSurface,
                activePill = scheme.primary,
                activePillContent = scheme.onPrimary,
                inactiveIcon = scheme.onSurface.copy(alpha = 0.85f),
                companionContainer = if (blurred) scheme.secondaryContainer.copy(alpha = 0.82f) else scheme.secondaryContainer,
                companionContent = scheme.onSecondaryContainer,
                border = scheme.outlineVariant.copy(alpha = 0.40f),
            )
        } else {
            // Saturated deep teal/accent container as shown in the Essentials reference
            DockColors(
                container = if (blurred) scheme.primary.copy(alpha = 0.88f) else scheme.primary,
                content = Color.White,
                activePill = Color.White,
                activePillContent = scheme.primary,
                inactiveIcon = Color.White.copy(alpha = 0.88f),
                companionContainer = scheme.secondaryContainer,
                companionContent = scheme.onSecondaryContainer,
                border = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

/**
 * The floating navigation dock and adjacent Settings / More Actions button as one floating row:
 * [ ( [◉ Home]  ⊞ Plans & Tx  📊 Insights  💳 Wallet ) ]   [ + / × / ⚙ ]
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingNavigationRow(
    destinations: List<AppDestination>,
    current: AppDestination,
    overdueCount: Int,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    actions: List<FooterAction> = emptyList(),
    isMoreOpen: Boolean = false,
    onMoreToggle: () -> Unit = {},
    /** The menu's progress, 0 closed to 1 open; the shell passes its own so the page's dim can follow the same clock. */
    menuProgress: State<Float> = rememberActionMenuProgress(isMoreOpen && actions.isNotEmpty()),
    /** How the menu's tiles are arranged (see [ActionMenuLayout]); [onActionLayoutChange] null means it cannot be edited. */
    actionLayout: ActionMenuLayout = ActionMenuLayout.Default,
    onActionLayoutChange: ((ActionMenuLayout) -> Unit)? = null,
    /** The menu is in Edit mode; kept by the caller so Back can leave it before it closes the menu. */
    editingActions: Boolean = false,
    onEditingActionsChange: (Boolean) -> Unit = {},
    colors: DockColors = rememberDockColors(),
    currentLabel: String = current.label,
    showLabels: Boolean = true,
) {
    val backdrop = LocalFrostedBackdrop.current
    val screenWidth = rememberWindowWidthDp()
    val isNarrow = screenWidth < 360
    // Room for the selected label: the screen, less margins, the Settings tile, the dock's padding, one
    // 48dp item (plus gap) for every other destination, and the pill's own padding and icon.
    val maxLabelWidth = (
        screenWidth - 2 * 16 - DockHeight.value - 10 - 2 * DockPadding.value -
            (destinations.size - 1) * (48 + 4) - 2 * (if (isNarrow) 10 else 14) - 22 - 8
        ).dp

    val menuOpen = isMoreOpen && actions.isNotEmpty()
    // The panel stays composed while it closes, and not a frame longer.
    val panelShown by remember(menuProgress, menuOpen) {
        derivedStateOf { menuOpen || menuProgress.value > 0.001f }
    }
    val progress: () -> Float = remember(menuProgress) { { menuProgress.value } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(ActionMenuGap),
        ) {
            if (panelShown) {
                ActionMenuPanel(
                    actions = actions,
                    layout = actionLayout,
                    onLayoutChange = onActionLayoutChange,
                    editing = editingActions,
                    onEditingChange = onEditingActionsChange,
                    colors = colors,
                    backdrop = backdrop,
                    progress = progress,
                    open = menuOpen,
                    onActionClick = { action ->
                        onMoreToggle()
                        action.onClick()
                    },
                    // A tall panel (large text, many actions) gives way to the dock row instead of pushing it off screen.
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The dock steps back towards the "+" as the menu opens. Faded out it must also stop being pressable
                // (alpha does not do that) and stop being read out, so while the menu is open a tap on it closes the menu.
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val p = progress().coerceIn(0f, 1f)
                            alpha = 1f - (p * 1.6f).coerceAtMost(1f)
                            scaleX = 1f - 0.1f * p
                            scaleY = 1f - 0.1f * p
                            transformOrigin = TransformOrigin(1f, 0.5f)
                        }
                        .then(if (menuOpen) Modifier.clearAndSetSemantics { } else Modifier),
                ) {
                    FloatingNavBar(
                        destinations = destinations,
                        current = current,
                        overdueCount = overdueCount,
                        onDestinationSelected = {
                            if (isMoreOpen) onMoreToggle()
                            onDestinationSelected(it)
                        },
                        colors = colors,
                        backdrop = backdrop,
                        isNarrow = isNarrow,
                        currentLabel = currentLabel,
                        maxLabelWidth = maxLabelWidth,
                        showLabels = showLabels,
                    )
                    if (menuOpen) Box(Modifier.matchParentSize().closesMenuOnTap(onMoreToggle))
                }
                FloatingNavCompanionItem(
                    icon = if (actions.isEmpty()) Icons.Outlined.Settings else Icons.Rounded.Add,
                    contentDescription = if (actions.isEmpty()) "Settings" else if (isMoreOpen) "Close actions" else "More actions",
                    colors = colors,
                    backdrop = backdrop,
                    isActionMenu = actions.isNotEmpty(),
                    isMoreOpen = isMoreOpen,
                    menuProgress = progress,
                    onClick = {
                        if (actions.isEmpty()) {
                            onOpenSettings()
                        } else {
                            onMoreToggle()
                        }
                    },
                )
            }
        }
    }
}

/**
 * The floating back dock shown on sub-screens and detail pages:
 * [ (←)  Title ]   [ 🔍 / Action ]
 *
 * Matching the Essentials design reference (Image 1).
 *
 * [actions] are the page's own (an add button, an options menu): with Bottom controls on, they sit here in a pill made as the
 * companion tile is, after the back pill, so everything a page has at its top is within the thumb's reach. The back pill gives
 * up width to them, its title shortening, before they or the tile are squeezed. With [spread] the row takes the whole width
 * - back at the start, the actions at the end, as a search card above it spans - instead of one cluster in the middle.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingBackRow(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCompanionClick: (() -> Unit)? = null,
    companionIcon: ImageVector = Icons.Outlined.Search,
    companionContentDescription: String = "Search",
    actions: (@Composable RowScope.() -> Unit)? = null,
    spread: Boolean = false,
    colors: DockColors? = null,
) {
    val backdrop = LocalFrostedBackdrop.current
    val effectiveColors = colors ?: rememberDockColors(blurred = rememberFrostedChrome(backdrop))

    val isImeVisible = WindowInsets.isImeVisible
    val animDuration = AppMotion.duration(AppMotion.MEDIUM_MS)
    val haptics = rememberAppHaptics()

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            // Spread, the row takes the width: the back pill at its start, the page's actions at its end.
            modifier = if (spread) Modifier.fillMaxWidth() else Modifier,
            horizontalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The floating back pill: [ (←)  Title ]
            Surface(
                onClick = {
                    haptics.selection()
                    onBack()
                },
                shape = CircleShape,
                color = effectiveColors.container,
                contentColor = effectiveColors.content,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, effectiveColors.border),
                modifier = Modifier
                    .height(DockHeight)
                    .widthIn(max = 280.dp)
                    .then(if (actions != null) Modifier.weight(1f, fill = false) else Modifier)
                    .frostedGlass(backdrop, CircleShape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Back, $title"
                    },
            ) {
                Row(
                    modifier = Modifier.padding(start = 7.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // High-contrast circular disc with back arrow inside
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(effectiveColors.activePill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = effectiveColors.activePillContent,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    // Sub-page title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = effectiveColors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            // The page's own actions, in a pill beside the back pill
            if (actions != null) {
                // Spread, nothing spaces the pills but the room left: this keeps a gap when there is none.
                Box(if (spread) Modifier.padding(start = 10.dp) else Modifier) {
                    FloatingActionsPill(colors = effectiveColors, backdrop = backdrop, content = actions)
                }
            }

            // Companion action tile beside the back pill
            if (onCompanionClick != null) {
                FloatingNavCompanionItem(
                    icon = companionIcon,
                    contentDescription = companionContentDescription,
                    colors = effectiveColors,
                    backdrop = backdrop,
                    onClick = onCompanionClick,
                )
            }
        }
    }
}

/**
 * Main floating pill dock container holding the navigation destination items.
 */
@Composable
fun FloatingNavBar(
    destinations: List<AppDestination>,
    current: AppDestination,
    overdueCount: Int,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    colors: DockColors = rememberDockColors(),
    backdrop: FrostedBackdrop? = LocalFrostedBackdrop.current,
    isNarrow: Boolean = false,
    currentLabel: String = current.label,
    showLabels: Boolean = true,
    /** The most the selected label may take, so the dock never outgrows its row; [Dp.Unspecified] for no limit. */
    maxLabelWidth: Dp = Dp.Unspecified,
) {
    Surface(
        shape = RoundedCornerShape(DockCorner),
        color = colors.container,
        contentColor = colors.content,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .height(DockHeight)
            .wrapContentWidth()
            .frostedGlass(backdrop, RoundedCornerShape(DockCorner))
            .selectableGroup(),
    ) {
        // No animateContentSize here: each item grows and shrinks its own label (see FloatingNavItem), so the dock's width
        // follows them frame by frame. Animating the row's bounds as well made the items jump into place while only the
        // outline moved.
        Row(
            modifier = Modifier.padding(DockPadding),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEach { destination ->
                FloatingNavItem(
                    destination = destination,
                    label = if (destination == AppDestination.PlansAndTransactions) currentLabel else destination.label,
                    selected = destination == current,
                    overdueCount = overdueCount,
                    colors = colors,
                    isNarrow = isNarrow,
                    onClick = { onDestinationSelected(destination) },
                    maxLabelWidth = maxLabelWidth,
                    showLabel = showLabels,
                )
            }
        }
    }
}

/**
 * Pill carrying a sub-page's own actions beside the floating back pill.
 */
@Composable
private fun FloatingActionsPill(
    colors: DockColors,
    backdrop: FrostedBackdrop?,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = CircleShape
    Surface(
        shape = shape,
        color = colors.container,
        contentColor = colors.content,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .height(DockHeight)
            .wrapContentWidth()
            .frostedGlass(backdrop, shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/**
 * Expressive companion button beside the dock or back row.
 * When [isActionMenu] is true, renders a circular "+" button that rotates into "×" when open.
 * Otherwise renders a squircle companion tile with [icon].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloatingNavCompanionItem(
    colors: DockColors,
    backdrop: FrostedBackdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Settings,
    contentDescription: String = "Settings",
    isActionMenu: Boolean = false,
    isMoreOpen: Boolean = false,
    /** The menu's progress when it has one: the "+" then turns on the menu's own clock, in step with the panel. */
    menuProgress: (() -> Float)? = null,
) {
    val haptics = rememberAppHaptics()
    val tooltipState = rememberTooltipState()

    val ownRotation by animateFloatAsState(
        targetValue = if (isActionMenu && isMoreOpen) 135f else 0f,
        animationSpec = AppMotion.spatial(),
        label = "companion_rotation",
    )

    val containerColor by animateColorAsState(
        targetValue = if (isActionMenu && isMoreOpen) colors.activePill else colors.companionContainer,
        animationSpec = tween(AppMotion.duration(AppMotion.SHORT_MS)),
        label = "companion_container",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActionMenu && isMoreOpen) colors.activePillContent else colors.companionContent,
        animationSpec = tween(AppMotion.duration(AppMotion.SHORT_MS)),
        label = "companion_content",
    )

    val effectiveDescription = if (isActionMenu) {
        if (isMoreOpen) "Close actions" else "More actions"
    } else {
        contentDescription
    }

    val tooltipText = if (isActionMenu) {
        if (isMoreOpen) "Close actions" else "Open actions"
    } else {
        contentDescription
    }

    val shape = if (isActionMenu) CircleShape else RoundedCornerShape(CompanionCorner)

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(tooltipText)
            }
        },
        state = tooltipState,
    ) {
        Surface(
            onClick = {
                haptics.selection()
                onClick()
            },
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, colors.border),
            modifier = modifier
                .size(DockHeight)
                .frostedGlass(backdrop, shape)
                .semantics {
                    role = Role.Button
                    this.contentDescription = effectiveDescription
                    this[SemanticsProperties.Text] = listOf(AnnotatedString(effectiveDescription))
                },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isActionMenu) Icons.Rounded.Add else icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = if (menuProgress != null) 135f * menuProgress() else ownRotation },
                )
            }
        }
    }
}

/**
 * Individual navigation item inside the dock.
 * - Selected: pure white high-contrast pill capsule with dark icon + label.
 * - Inactive: recognizable clean icon with 48dp touch target.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingNavItem(
    destination: AppDestination,
    label: String,
    selected: Boolean,
    overdueCount: Int,
    colors: DockColors,
    isNarrow: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    maxLabelWidth: Dp = Dp.Unspecified,
    showLabel: Boolean = true,
) {
    val haptics = rememberAppHaptics()
    val animDuration = AppMotion.duration(AppMotion.MEDIUM_MS)

    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.activePill else colors.activePill.copy(alpha = 0f),
        animationSpec = tween(animDuration, easing = AppMotion.standard),
        label = "nav_item_container_color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.activePillContent else colors.inactiveIcon,
        animationSpec = tween(animDuration, easing = AppMotion.standard),
        label = "nav_item_content_color",
    )

    val shape = CircleShape
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected && showLabel) {
            if (isNarrow) 10.dp else 14.dp
        } else {
            12.dp
        },
        animationSpec = tween(animDuration, easing = AppMotion.standard),
        label = "nav_item_padding",
    )

    // Icon-only items name themselves on long press. The wrapper stays in place when the item is selected (its
    // label is already showing) so the container/content color animations are never restarted.
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(destination.label) } },
        state = rememberTooltipState(),
        enableUserInput = !selected || !showLabel,
    ) {
    Box(
        modifier = modifier
            .height(ItemHeight)
            .widthIn(min = ItemHeight)
            .background(color = containerColor, shape = shape)
            .clip(shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, color = if (selected) colors.activePillContent else colors.content),
                onClick = {
                    if (!selected) {
                        haptics.selection()
                        onClick()
                    }
                },
            )
            .semantics {
                this.selected = selected
                role = Role.Tab
                contentDescription = destination.label
                // "Plans & Transactions, selected, showing Plans": the pill's label is the view that is open.
                if (selected && label != destination.label) stateDescription = "Showing $label"
                // Only the open tab has a word on it (an icon alone is described, not "texted"): the label may be folding
                // away this frame, and the tab must not keep saying it once it has.
                if (selected && showLabel) this[SemanticsProperties.Text] = listOf(AnnotatedString(destination.label))
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val icon = if (selected) destination.selectedIcon else destination.icon
            val showBadge = destination == AppDestination.PlansAndTransactions && overdueCount > 0

            if (showBadge) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = if (selected) colors.container else colors.activePill,
                            contentColor = if (selected) colors.content else colors.activePillContent,
                            modifier = Modifier.semantics {
                                contentDescription = "$overdueCount overdue"
                            },
                        ) {
                            Text(if (overdueCount > 99) "99+" else overdueCount.toString())
                        }
                    },
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Too little room for even a few letters: the pill alone says which item is selected. The label opens out of the
            // icon as the pill fills in, and folds back into it as the pill empties, so a tab change moves every item smoothly
            // instead of swapping labels in one frame.
            AnimatedVisibility(
                visible = selected && showLabel && (maxLabelWidth == Dp.Unspecified || maxLabelWidth >= MinLabelWidth),
                enter = expandHorizontally(tween(animDuration, easing = AppMotion.standard), expandFrom = Alignment.Start) +
                    fadeIn(tween(animDuration / 2, delayMillis = animDuration / 3)),
                exit = shrinkHorizontally(tween(animDuration, easing = AppMotion.standard), shrinkTowards = Alignment.Start) +
                    fadeOut(tween(animDuration / 3)),
            ) {
                Text(
                    text = label,
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    // No softWrap = false: that lays the text out unbounded and never ellipsizes, which is how a
                    // large font used to push the last icon out of the dock.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .then(if (maxLabelWidth != Dp.Unspecified) Modifier.widthIn(max = maxLabelWidth) else Modifier),
                )
            }
        }
    }
    }
}
