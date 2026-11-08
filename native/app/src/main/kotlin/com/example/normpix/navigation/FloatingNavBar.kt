package com.example.normpix.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.normpix.ui.components.Icon
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.DockSelectionStyle
import com.example.normpix.notifications.LocalNotifications
import com.example.normpix.ui.components.FrostedBackdrop
import com.example.normpix.ui.components.LocalFrostedBackdrop
import com.example.normpix.ui.components.frostedGlass
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.components.rememberFrostedChrome
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.isDark
import com.example.normpix.ui.theme.rememberReducedMotion
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
    /** What the "See all" under the notifications above the menu does. */
    onOpenNotifications: () -> Unit = {},
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
        // As wide as the panel whether it is composed or not, and the row between the middle and the end of it as the menu opens:
        // the footer then glides where it stood instead of jumping when the panel is let go (see [dockRowX]).
        Column(
            modifier = Modifier.atPanelWidth(),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(ActionMenuGap),
        ) {
            if (panelShown) {
                // What is new and needs the user, above the panel (see NotificationStrip).
                NotificationStrip(
                    notifications = LocalNotifications.current,
                    progress = progress,
                    onOpen = { item ->
                        onMoreToggle()
                        item.banner.onOpen()
                    },
                    onSeeAll = {
                        onMoreToggle()
                        onOpenNotifications()
                    },
                )
                ActionMenuPanel(
                    actions = actions,
                    layout = actionLayout,
                    onLayoutChange = onActionLayoutChange,
                    editing = editingActions,
                    onEditingChange = onEditingActionsChange,
                    colors = colors,
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

            val rowAlignment = remember(progress) { DockRowAlignment(progress) }
            Row(
                modifier = Modifier.align(rowAlignment),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Which way the "+" is: the end of the row, on the left in a right-to-left layout.
                val towardsPlus = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
                // The dock slides into the "+" as the menu opens and back out of it as the menu closes (see [dockShown]). Faded
                // out it must also stop being pressable (alpha does not do that) and stop being read out, so while the menu is
                // open a tap on it closes the menu. The Edit button overlays the dock's end, in the space it leaves, so nothing
                // here changes the dock's or the "+" button's size and place.
                Box {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val shown = dockShown(progress().coerceIn(0f, 1f))
                                val gone = 1f - shown
                                alpha = shown
                                translationX = gone * DockSlide.toPx() * towardsPlus
                                scaleX = 1f - DockShrink * gone
                                scaleY = 1f - DockShrink * gone
                                transformOrigin = TransformOrigin(if (towardsPlus < 0f) 0f else 1f, 0.5f)
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
                    // The Edit button exists only while the panel does, is shown only once the dock has gone, and is offered
                    // (to a finger or to TalkBack) only while the menu is open and the arrangement can be edited. In Edit
                    // mode the panel's own Reset/Done bar takes over.
                    if (panelShown && !editingActions && onActionLayoutChange != null) {
                        EditActionsButton(
                            progress = progress,
                            colors = colors,
                            enabled = menuOpen,
                            onClick = { onEditingActionsChange(true) },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
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
    /** How the pill moves between tabs: the user's choice (Settings > Appearance > Transitions), the glide where there is none. */
    selectionStyle: DockSelectionStyle = rememberDockSelectionStyle(),
) {
    val glide = selectionStyle == DockSelectionStyle.Glide
    val reduced = rememberReducedMotion()
    val selectedIndex = destinations.indexOf(current).coerceAtLeast(0)
    // In the glide there is one pill, drawn behind the items from where they are at each frame. It is on [target] at rest; when the
    // tab changes it flies there from where it was ([startEdges]: its left and right when the flight began), over [flight] 0 -> 1.
    val geometry = remember(destinations.size) { DockGeometry(destinations.size) }
    val flight = remember { Animatable(1f) }
    val startEdges = remember { FloatArray(2) }
    var target by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex, glide, reduced) {
        if (selectedIndex == target) return@LaunchedEffect
        if (!glide || reduced || !geometry.isKnown(selectedIndex) || !geometry.isKnown(target)) {
            // No flight to play (the other style, animations off, or the dock not yet laid out): the pill is just there.
            flight.snapTo(1f)
            target = selectedIndex
            return@LaunchedEffect
        }
        // Where the pill is now: at rest on the tab it was on, or part-way along a flight that this tap interrupts.
        val (left, right) = DockGlide.edges(startEdges[0], startEdges[1], geometry.left(target), geometry.right(target), flight.value)
        startEdges[0] = left
        startEdges[1] = right
        val tabs = kotlin.math.abs(selectedIndex - target)
        target = selectedIndex
        flight.snapTo(0f)
        flight.animateTo(1f, tween(DockGlide.durationMs(tabs), easing = LinearEasing))
    }

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
            modifier = Modifier
                .padding(DockPadding)
                .then(
                    if (glide) {
                        Modifier
                            // The row's own coordinates, where the items' places are measured (the pill is drawn in the same space).
                            .onGloballyPositioned { geometry.row = it }
                            .drawBehind {
                                // Read, so a tab that moves redraws the pill with it.
                                if (geometry.tick < 0 || !geometry.isKnown(target)) return@drawBehind
                                val (left, right) =
                                    DockGlide.edges(startEdges[0], startEdges[1], geometry.left(target), geometry.right(target), flight.value)
                                val height = geometry.height(target)
                                drawRoundRect(
                                    color = colors.activePill,
                                    topLeft = Offset(left, geometry.top(target)),
                                    size = Size(right - left, height),
                                    cornerRadius = CornerRadius(height / 2f),
                                )
                            }
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            destinations.forEachIndexed { index, destination ->
                FloatingNavItem(
                    destination = destination,
                    label = if (destination == AppDestination.PlansAndTransactions) currentLabel else destination.label,
                    selected = destination == current,
                    overdueCount = overdueCount,
                    colors = colors,
                    isNarrow = isNarrow,
                    onClick = { onDestinationSelected(destination) },
                    modifier = Modifier.onGloballyPositioned { geometry.place(index, it) },
                    maxLabelWidth = maxLabelWidth,
                    showLabel = showLabels,
                    glide = glide,
                )
            }
        }
    }
}

/** The user's choice of how the dock's pill moves (Settings > Appearance > Transitions); the glide where there are no preferences (a preview, a test). */
@Composable
private fun rememberDockSelectionStyle(): DockSelectionStyle =
    LocalAppPreferences.current?.state?.collectAsState()?.value?.dockSelection ?: DockSelectionStyle.Glide

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
 * The Edit button that lives in the bottom row while the "+" menu is open: it sits in the space the faded dock leaves, just
 * left of the close button, and only once the dock has gone ([progress] past [DockGoneAt]). It is a filled circle in the dock's
 * `companionContainer` with an `Icons.Outlined.Edit` icon in `companionContent` - the "+" button's own resting colours -
 * reading as the "x" button's partner and ensuring visibility across light and dark palettes (the previous outlined circle
 * on the near-white panel fill disappeared on light pages). It grows in from its right edge towards the close button, on
 * the menu's own clock and without recomposing a frame.
 */
@Composable
private fun EditActionsButton(
    progress: () -> Float,
    colors: DockColors,
    /** Whether it takes touches and is read out: the menu is open. It stays composed while the panel closes, fading with it. */
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    Box(
        modifier = modifier
            .size(DockHeight)
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                val reveal = ((p - DockGoneAt) / (1f - DockGoneAt)).coerceIn(0f, 1f)
                alpha = reveal
                val s = 0.6f + 0.4f * reveal
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(1f, 0.5f)
            }
            .shadow(6.dp, CircleShape)
            .background(colors.companionContainer, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClickLabel = "Edit actions", role = Role.Button) {
                haptics.selection()
                onClick()
            }
            .then(
                if (enabled) {
                    Modifier.semantics { contentDescription = "Edit actions" }
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.companionContent, modifier = Modifier.size(22.dp))
    }
}

/**
 * How far the "+" turns to become the "x": an eighth of a turn. It was 135 degrees, but a plus is the same shape every quarter turn,
 * so on the way it read plus, cross, plus, cross - on a close of a quarter of a second, a flicker (seen frame by frame in a screen
 * recording, in the very moment the footer comes back). An eighth of a turn is one clean change.
 */
internal const val PlusToCross = 45f

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
        targetValue = if (isActionMenu && isMoreOpen) PlusToCross else 0f,
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
                        .graphicsLayer { rotationZ = if (menuProgress != null) PlusToCross * menuProgress() else ownRotation },
                )
            }
        }
    }
}

/**
 * The dock's spring for what changes size: livelier than the app's own spatial spring ([AppMotion.spatial], damping 0.8, which
 * measured almost the same as the curve it replaced), after Essentials' toolbar (damping 0.5, low stiffness) but not as loose -
 * damping 0.65 and stiffness 300 overshoot by about 7% and settle in a third of a second. Instant where animations are off.
 */
@Composable
internal fun <T> dockSpring(): FiniteAnimationSpec<T> =
    if (rememberReducedMotion()) snap() else spring(dampingRatio = 0.65f, stiffness = 300f)

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
    /** The dock draws one pill that travels between items ([DockGlide]): this item draws none of its own. */
    glide: Boolean = false,
) {
    val haptics = rememberAppHaptics()
    val animDuration = AppMotion.duration(AppMotion.MEDIUM_MS)

    val containerColor by animateColorAsState(
        targetValue = if (selected && !glide) colors.activePill else colors.activePill.copy(alpha = 0f),
        animationSpec = tween(animDuration, easing = AppMotion.standard),
        label = "nav_item_container_color",
    )
    // With the pill flying in, the item turns to the colours it has on the pill only as the pill arrives - about a third of the way
    // into the flight, where DropSauce swaps its label - and the item it leaves lets go of them at once, so neither is ever the
    // pill's dark ink on the dock's own dark, or its light ink on the pill's light.
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.activePillContent else colors.inactiveIcon,
        animationSpec = if (glide) {
            tween(AppMotion.duration(150), delayMillis = if (selected) AppMotion.duration(110) else 0, easing = AppMotion.standard)
        } else {
            tween(animDuration, easing = AppMotion.standard)
        },
        label = "nav_item_content_color",
    )

    val shape = CircleShape
    // What changes SIZE moves on a spring, as Essentials' toolbar does (its items grow and shrink on a bouncy low-stiffness
    // spring, and its mode swaps are a short fade with a scale): a spring starts on the first frame, carries its speed into
    // whatever comes next - a second tap mid-change does not start over from a standstill - and settles with a little life, where
    // a fixed-length curve sat still for a moment and then stopped dead. Colours stay short curves: they must not overshoot.
    val sizeSpring = dockSpring<IntSize>()
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected && showLabel) {
            if (isNarrow) 10.dp else 14.dp
        } else {
            12.dp
        },
        animationSpec = dockSpring(),
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
                enter = expandHorizontally(sizeSpring, expandFrom = Alignment.Start) +
                    fadeIn(tween(animDuration / 2, delayMillis = animDuration / 4)),
                exit = shrinkHorizontally(sizeSpring, shrinkTowards = Alignment.Start) +
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
