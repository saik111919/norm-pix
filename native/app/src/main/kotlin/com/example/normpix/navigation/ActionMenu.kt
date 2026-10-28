package com.example.normpix.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.normpix.ui.components.FrostedBackdrop
import com.example.normpix.ui.components.frostedGlass
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.isDark
import kotlinx.coroutines.launch

/*
 * The "+" action menu: a bento panel that opens out of the "+" button above the dock.
 *
 *     ┌───────┬───────────────┬──────┬──────┐
 *     │       │    Income     │Recur.│ (Rem)│
 *     │  Exp  ├──────┬────────┼──────┼──────┤
 *     │ ense  │ Card │Account │Categ.│(Budg)│
 *     │       ├──────┼────────┼──────┼──────┤
 *     │       │People│ Search │Setti.│ Edit │
 *     └───────┴──────┴────────┴──────┴──────┘
 *
 * One tall capsule (the featured tile), then wide capsules, squircles and circles on a grid of equal cells, in the order and at
 * the sizes the user left them (see [ActionMenuLayout]; the picture is the design's own). The last cell is the Edit button.
 *
 * Edit mode follows Pixel's Quick Settings editor: tiles stop running their action and are selected instead; a selected tile
 * has a handle on its right edge that drags between the two sizes, touch-and-hold lifts a tile to drag it into place (the
 * others make room), and a bar under the grid has Reset and Done, with buttons that do the same moves for those who don't
 * drag (and for screen readers, which are also given them as actions on each tile).
 *
 * One progress value (0 closed, 1 open) drives the whole thing - the panel's growth out of the "+" button, each tile arriving a
 * beat after its neighbour nearer the button, the button's turn into a cross, the dock stepping back and the page dimming -
 * so they can never drift apart. It is only ever read in graphics layers and draw lambdas, so a frame of this recomposes
 * nothing.
 *
 * With large text, or on a screen too narrow for labels inside five columns, the same actions come as three columns of
 * icon-over-label tiles whose labels may take two lines (and reorder with the bar's buttons only).
 */

private val BentoGap = 6.dp
private val PanelPadding = 10.dp
private val PanelCorner = 28.dp
private val TileCorner = 18.dp

/** Narrowest cell that still holds a label of a word ("Categories") beside no more than the tile's padding. */
private val MinBentoCell = 54.dp

/** Above this the labels would not fit their tiles, so the panel falls back to the three-column list. */
private const val MaxBentoFontScale = 1.15f
private const val CompactColumns = 3

/** The panel never grows wider than this, so on a tablet or a foldable it stays a phone-sized card at the "+" button. */
internal val ActionMenuMaxWidth = 380.dp

/** Gap between the panel and the dock row under it. */
internal val ActionMenuGap = 12.dp

/** How far the panel's base sits above the "+" button's centre: the gap between them and half the button. */
private val OriginBelowPanel = ActionMenuGap + DockHeight / 2

/** The share of the panel's progress a tile waits, per cell of distance from the "+" button, before it starts to arrive. */
private const val TileStagger = 0.045f

/** How far a handle must be dragged, as a share of a cell, before the tile changes size. */
private const val ResizeDragCells = 0.4f

/**
 * Progress of the action menu: 0 closed, 1 open. Opens on the app's spatial spring (it may pass 1 by a hair while it
 * settles), closes in 200 ms on the fast-start curve; instant when animations are off. A [State], so the readers that draw from it
 * recompose nothing.
 */
@Composable
fun rememberActionMenuProgress(open: Boolean): State<Float> {
    val progress = remember { Animatable(if (open) 1f else 0f) }
    val openSpec = AppMotion.spatial<Float>()
    val closeMs = AppMotion.duration(AppMotion.SHORT_MS)
    LaunchedEffect(open, openSpec, closeMs) {
        when {
            open -> progress.animateTo(1f, openSpec)
            closeMs == 0 -> progress.snapTo(0f)
            // The fast-start curve, not the accelerating exit one: a dismiss must answer the tap at once, not sit still first.
            else -> progress.animateTo(0f, tween(closeMs, easing = AppMotion.standard))
        }
    }
    return progress.asState()
}

/** A tile's fill and the colour of what is drawn on it, from the theme so every palette (Light, Dark, Pure, wallpaper) works. */
internal data class TileTone(val container: Color, val content: Color)

/**
 * A ramp of one hue from the theme's own roles - the saturated primary down through the containers to a quiet neutral - so
 * the panel reads as one family, as in the design, and every fill is paired with the role made to be read on it. [index] is
 * the action's place in the design's own order, so a tile keeps its colour wherever the user moves it.
 */
internal fun tileTone(index: Int, scheme: ColorScheme): TileTone {
    // Every pair is checked, not trusted: the hand-drawn palettes and the in-between step have no role made for them.
    val deep = tone(scheme.primary, listOf(scheme.onPrimary, scheme.onSurface))
    val strong = tone(scheme.primaryContainer, listOf(scheme.onPrimaryContainer, scheme.onSurface))
    val mid = tone(
        lerp(scheme.primaryContainer, scheme.secondaryContainer, 0.5f),
        listOf(scheme.onSecondaryContainer, scheme.onPrimaryContainer, scheme.onSurface),
    )
    val soft = tone(scheme.secondaryContainer, listOf(scheme.onSecondaryContainer, scheme.onSurface))
    val quiet = tone(scheme.surfaceContainerHighest, listOf(scheme.onSurface))
    return when (index) {
        0 -> deep
        1 -> strong
        2 -> mid
        3 -> soft
        4 -> mid
        5 -> soft
        6 -> mid
        7 -> soft
        8 -> mid
        9 -> strong
        10 -> quiet
        else -> if (index % 2 == 0) mid else soft
    }
}

/** The first of [preferred] that reads on [container] at WCAG's 4.5:1 for small text, else whichever of black and white reads better. */
private fun tone(container: Color, preferred: List<Color>): TileTone {
    val ink = preferred.firstOrNull { contrastRatio(it, container) >= 4.5f }
        ?: if (contrastRatio(Color.Black, container) >= contrastRatio(Color.White, container)) Color.Black else Color.White
    return TileTone(container, ink)
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** The tone for the action [id] at [position] in the arrangement: by its place in the design, else alternating. */
private fun toneIndexOf(id: String, position: Int): Int =
    ActionMenuLayout.Default.indexOf(id).takeIf { it >= 0 } ?: (2 + position % 2)

/**
 * The panel itself: glass, grown out of the "+" button as [progress] goes 0 -> 1. [open] says whether it is opening (tiles
 * arrive one after another) or closing (all go together), and whether its tiles take touches.
 *
 * [layout] is the saved arrangement; edits are handed to [onLayoutChange] as they are made (null: the menu cannot be edited
 * and has no Edit button), and shown at once without waiting for them to come back. [editing] is Edit mode, kept by the
 * caller so Back can leave it.
 */
@Composable
internal fun ActionMenuPanel(
    actions: List<FooterAction>,
    layout: ActionMenuLayout,
    onLayoutChange: ((ActionMenuLayout) -> Unit)?,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    colors: DockColors,
    backdrop: FrostedBackdrop?,
    progress: () -> Float,
    open: Boolean,
    onActionClick: (FooterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PanelCorner)
    val scheme = MaterialTheme.colorScheme
    val panelColor = if (scheme.isDark) {
        scheme.surfaceContainerHigh.copy(alpha = 0.88f)
    } else {
        scheme.surfaceContainerLowest.copy(alpha = 0.94f)
    }
    val fontScale = LocalDensity.current.fontScale
    val originBelow = OriginBelowPanel

    // What is on screen: the saved arrangement, and an edit as soon as it is made (the saved one catches up a frame later).
    var shown by remember(layout) { mutableStateOf(layout) }
    val arranged = remember(actions, shown) { shown.arranged(actions) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(editing) { if (!editing) selectedId = null }
    val editable = onLayoutChange != null
    val inEditMode = editing && editable

    fun commit(next: ActionMenuLayout) {
        shown = next
        onLayoutChange?.invoke(next)
    }
    fun nudge(id: String, by: Int) {
        val from = arranged.indexOfFirst { it.action.id == id }
        if (from >= 0) commit(arranged.toLayout().moved(id, from + by))
    }
    fun toggleSize(id: String) {
        val tile = arranged.firstOrNull { it.action.id == id } ?: return
        commit(arranged.toLayout().resized(id, if (tile.size == ActionTileSize.Small) ActionTileSize.Wide else ActionTileSize.Small))
    }

    val edit = MenuEdit(
        editing = inEditMode,
        offerEdit = editable && !editing,
        selectedId = selectedId,
        onStartEditing = { onEditingChange(true) },
        onSelect = { id -> selectedId = if (selectedId == id) null else id },
        onNudge = ::nudge,
        onToggleSize = ::toggleSize,
        onReorderLive = { id, to -> shown = arranged.toLayout().moved(id, to) },
        onReorderDone = { onLayoutChange?.invoke(shown) },
        onResize = { id, size -> commit(arranged.toLayout().resized(id, size)) },
    )

    Surface(
        shape = shape,
        color = panelColor,
        contentColor = scheme.onSurface,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier
            .graphicsLayer {
                // Grows out of the "+" button: the origin is its centre, below and at the end of the panel.
                val p = progress()
                transformOrigin = TransformOrigin(
                    pivotFractionX = ((size.width - DockHeight.toPx() / 2) / size.width),
                    pivotFractionY = ((size.height + originBelow.toPx()) / size.height),
                )
                val s = 0.4f + 0.6f * p
                scaleX = s
                scaleY = s
                alpha = (p * 2f).coerceIn(0f, 1f)
            }
            .fillMaxWidth()
            .widthIn(max = ActionMenuMaxWidth)
            .frostedGlass(backdrop, shape)
            .semantics { paneTitle = if (inEditMode) "Edit actions" else "Actions" },
    ) {
        BoxWithConstraints(Modifier.padding(PanelPadding)) {
            val cell = (maxWidth - BentoGap * (BentoColumns - 1)) / BentoColumns
            // The grid of tiles of two sizes, or - with large text, or too narrow a screen - a list of equal ones.
            val bento = fontScale <= MaxBentoFontScale && cell >= MinBentoCell
            Column(Modifier.animateContentSize(AppMotion.spatial())) {
                // The grid scrolls if it is taller than the room there is; the bar under it never does.
                Box(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (bento) {
                        BentoGrid(arranged, cell, scheme, progress, open, edit, onActionClick)
                    } else {
                        CompactGrid(arranged, scheme, progress, open, edit, onActionClick)
                    }
                }
                if (inEditMode) {
                    Spacer(Modifier.height(8.dp))
                    val selectedIndex = arranged.indexOfFirst { it.action.id == selectedId }
                    EditBar(
                        selected = arranged.getOrNull(selectedIndex),
                        index = selectedIndex,
                        count = arranged.size,
                        sizable = bento,
                        canReset = shown != ActionMenuLayout.Default,
                        onMoveEarlier = { selectedId?.let { nudge(it, -1) } },
                        onMoveLater = { selectedId?.let { nudge(it, 1) } },
                        onToggleSize = { selectedId?.let { toggleSize(it) } },
                        onReset = {
                            selectedId = null
                            commit(ActionMenuLayout.Default)
                        },
                        onDone = { onEditingChange(false) },
                    )
                }
            }
        }
    }
}

/** What the grids need of Edit mode, in one piece. */
private class MenuEdit(
    val editing: Boolean,
    /** The Edit button is offered (the menu can be edited and is not being). */
    val offerEdit: Boolean,
    val selectedId: String?,
    val onStartEditing: () -> Unit,
    val onSelect: (String) -> Unit,
    val onNudge: (id: String, by: Int) -> Unit,
    val onToggleSize: (id: String) -> Unit,
    /** A tile dragged over another's place: show the arrangement with it there, not yet kept. */
    val onReorderLive: (id: String, toIndex: Int) -> Unit,
    /** The drag has ended: keep what is shown. */
    val onReorderDone: () -> Unit,
    val onResize: (id: String, size: ActionTileSize) -> Unit,
)

@Composable
private fun BentoGrid(
    arranged: List<ArrangedAction>,
    cell: Dp,
    scheme: ColorScheme,
    progress: () -> Float,
    open: Boolean,
    edit: MenuEdit,
    onActionClick: (FooterAction) -> Unit,
) {
    val plan = remember(arranged, edit.offerEdit) { bentoPlan(arranged, edit.offerEdit) }
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val gapPx = with(density) { BentoGap.toPx() }
    val height = cell * plan.rows + BentoGap * (plan.rows - 1)

    Box(Modifier.fillMaxWidth().height(height)) {
        arranged.forEachIndexed { i, tile ->
            val slot = plan.tiles[i]
            key(tile.action.id) {
                BentoTile(
                    tile = tile,
                    slot = slot,
                    cellPx = cellPx,
                    gapPx = gapPx,
                    scheme = scheme,
                    tone = tileTone(toneIndexOf(tile.action.id, i), scheme),
                    delay = if (open) slot.arrivalRank(plan.rows) * TileStagger else 0f,
                    progress = progress,
                    interactive = open,
                    editing = edit.editing,
                    selected = edit.selectedId == tile.action.id,
                    featured = i == 0,
                    onClick = { onActionClick(tile.action) },
                    onSelect = { edit.onSelect(tile.action.id) },
                    onDragMove = { center ->
                        // The tile under the dragged one's middle is where it goes: the others make room.
                        val over = plan.tiles.indices.firstOrNull { j ->
                            j != i && plan.tiles[j].rectPx(cellPx, gapPx).contains(center)
                        }
                        if (over != null) edit.onReorderLive(tile.action.id, over)
                    },
                    onDragEnd = edit.onReorderDone,
                    onNudge = { by -> edit.onNudge(tile.action.id, by) },
                    onToggleSize = { edit.onToggleSize(tile.action.id) },
                    onResize = { size -> edit.onResize(tile.action.id, size) },
                )
            }
        }
        plan.trailing?.let { slot ->
            EditTile(
                slot = slot,
                cellPx = cellPx,
                gapPx = gapPx,
                scheme = scheme,
                delay = if (open) slot.arrivalRank(plan.rows) * TileStagger else 0f,
                progress = progress,
                interactive = open,
                onClick = edit.onStartEditing,
            )
        }
    }
}

private fun BentoSlot.offsetPx(cell: Float, gap: Float) = Offset(col * (cell + gap), row * (cell + gap))

private fun BentoSlot.sizePx(cell: Float, gap: Float) =
    Size(colSpan * cell + (colSpan - 1) * gap, rowSpan * cell + (rowSpan - 1) * gap)

private fun BentoSlot.rectPx(cell: Float, gap: Float) = androidx.compose.ui.geometry.Rect(offsetPx(cell, gap), sizePx(cell, gap))

/** The three-column fallback for large text and narrow screens: an icon tile with its label under it, two lines allowed. */
@Composable
private fun CompactGrid(
    arranged: List<ArrangedAction>,
    scheme: ColorScheme,
    progress: () -> Float,
    open: Boolean,
    edit: MenuEdit,
    onActionClick: (FooterAction) -> Unit,
) {
    // The tiles, then the Edit button as the last of them.
    val count = arranged.size + if (edit.offerEdit) 1 else 0
    Column(verticalArrangement = Arrangement.spacedBy(BentoGap)) {
        val rows = (0 until count).chunked(CompactColumns)
        rows.forEachIndexed { r, indices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BentoGap)) {
                indices.forEachIndexed { c, index ->
                    val delay = if (open) ((rows.size - 1 - r) + (CompactColumns - 1 - c)) * TileStagger else 0f
                    if (index < arranged.size) {
                        val action = arranged[index]
                        CompactTile(
                            label = action.action.label,
                            description = action.action.contentDescription ?: action.action.label,
                            icon = { Icon(action.action.icon, contentDescription = null, tint = tileTone(toneIndexOf(action.action.id, index), scheme).content, modifier = Modifier.size(24.dp)) },
                            container = tileTone(toneIndexOf(action.action.id, index), scheme).container,
                            delay = delay,
                            progress = progress,
                            interactive = open,
                            editing = edit.editing,
                            selected = edit.selectedId == action.action.id,
                            outlined = false,
                            onClick = { if (edit.editing) edit.onSelect(action.action.id) else onActionClick(action.action) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        CompactTile(
                            label = "Edit",
                            description = "Edit actions",
                            icon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp)) },
                            container = Color.Transparent,
                            delay = delay,
                            progress = progress,
                            interactive = open,
                            editing = false,
                            selected = false,
                            outlined = true,
                            onClick = edit.onStartEditing,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(CompactColumns - indices.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tile arriving after [delay] of the panel's progress: it scales up from a little smaller and fades in. */
private fun Modifier.arriving(progress: () -> Float, delay: Float): Modifier = graphicsLayer {
    val p = ((progress() - delay) / (1f - delay)).coerceIn(0f, 1.06f)
    val s = 0.55f + 0.45f * p
    scaleX = s
    scaleY = s
    alpha = (p * 1.4f).coerceIn(0f, 1f)
}

/** Presses sink a tile a little, on the app's spring, so a tap is felt as well as heard. */
@Composable
private fun rememberPressScale(interactions: MutableInteractionSource): State<Float> {
    val pressed by interactions.collectIsPressedAsState()
    val target = if (pressed) 0.94f else 1f
    val spec = AppMotion.spatial<Float>()
    return animateFloatAsState(target, spec, label = "action_tile_press")
}

@Composable
private fun BentoTile(
    tile: ArrangedAction,
    slot: BentoSlot,
    cellPx: Float,
    gapPx: Float,
    scheme: ColorScheme,
    tone: TileTone,
    delay: Float,
    progress: () -> Float,
    interactive: Boolean,
    editing: Boolean,
    selected: Boolean,
    featured: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onDragMove: (center: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onNudge: (by: Int) -> Unit,
    onToggleSize: () -> Unit,
    onResize: (ActionTileSize) -> Unit,
) {
    val action = tile.action
    val haptics = rememberAppHaptics()
    val density = LocalDensity.current
    val interactions = remember { MutableInteractionSource() }
    val press by rememberPressScale(interactions)
    val scope = rememberCoroutineScope()
    val tileShape = if (slot.shape == BentoShape.Squircle) RoundedCornerShape(TileCorner) else CircleShape
    val description = action.contentDescription ?: action.label

    // Where the tile sits, and its size: both follow the packing with the app's spring, so a move or a resize is seen as
    // the others making room rather than as a jump. While it is being dragged the finger has it.
    val sizePx = slot.sizePx(cellPx, gapPx)
    val target = slot.offsetPx(cellPx, gapPx).round()
    val width by animateDpAsState(with(density) { sizePx.width.toDp() }, AppMotion.spatial(), label = "tile_width")
    val height by animateDpAsState(with(density) { sizePx.height.toDp() }, AppMotion.spatial(), label = "tile_height")
    val settleSpec = AppMotion.spatial<IntOffset>()
    val offset = remember { Animatable(target, IntOffset.VectorConverter) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    val targetNow by rememberUpdatedState(target)
    val sizeNow by rememberUpdatedState(sizePx)
    val sizeKind by rememberUpdatedState(tile.size)
    val onDragMoveNow by rememberUpdatedState(onDragMove)
    val onDragEndNow by rememberUpdatedState(onDragEnd)
    val onResizeNow by rememberUpdatedState(onResize)
    LaunchedEffect(target) { if (!dragging) offset.animateTo(target, settleSpec) }
    val lift by animateFloatAsState(if (dragging) 1.06f else 1f, AppMotion.spatial(), label = "tile_lift")
    val liftElevation by animateDpAsState(if (dragging) 14.dp else 0.dp, AppMotion.spatial(), label = "tile_elevation")

    fun finishDrag() {
        scope.launch {
            // Hand the tile from the finger to its place without a jump: where it was let go, then home.
            offset.snapTo(dragPos.round())
            dragging = false
            onDragEndNow()
            offset.animateTo(targetNow, settleSpec)
        }
    }

    Box(
        modifier = Modifier
            .offset { if (dragging) dragPos.round() else offset.value }
            .zIndex(if (dragging) 2f else if (selected) 1f else 0f)
            .size(width, height)
            .arriving(progress, delay)
            .graphicsLayer {
                val s = press * lift
                scaleX = s
                scaleY = s
            }
            .shadow(liftElevation, tileShape, clip = false)
            .clip(tileShape)
            .background(tone.container)
            .then(
                if (editing) {
                    Modifier.border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) scheme.onSurface else tone.content.copy(alpha = 0.35f),
                        tileShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactions,
                indication = ripple(bounded = true, color = tone.content),
                enabled = interactive,
                onClickLabel = if (editing) "Select $description" else description,
            ) {
                haptics.selection()
                if (editing) onSelect() else onClick()
            }
            .pointerInput(editing) {
                if (!editing) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragPos = Offset(targetNow.x.toFloat(), targetNow.y.toFloat())
                        dragging = true
                        haptics.strong()
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragPos += amount
                        onDragMoveNow(dragPos + Offset(sizeNow.width / 2, sizeNow.height / 2))
                    },
                    onDragEnd = { finishDrag() },
                    onDragCancel = { finishDrag() },
                )
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                if (editing) {
                    this.selected = selected
                    customActions = buildList {
                        add(CustomAccessibilityAction("Move earlier") { onNudge(-1); true })
                        add(CustomAccessibilityAction("Move later") { onNudge(1); true })
                        if (!featured) {
                            add(CustomAccessibilityAction(if (tile.size == ActionTileSize.Small) "Make wide" else "Make small") { onToggleSize(); true })
                        }
                    }
                }
            },
        contentAlignment = if (slot.shape == BentoShape.Wide) Alignment.CenterStart else Alignment.Center,
    ) {
        if (slot.shape == BentoShape.Wide) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(24.dp))
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = tone.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = tone.content,
                    modifier = Modifier.size(if (slot.shape == BentoShape.Hero) 28.dp else 24.dp),
                )
                TileLabel(action.label, tone.content)
            }
        }
        // Pixel's handle: on the selected tile's right edge, dragged left for the icon-only size and right for the wide one.
        if (editing && selected && !featured) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(30.dp)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        var travelled = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { travelled = 0f },
                            onHorizontalDrag = { change, dx ->
                                change.consume()
                                travelled += dx
                                val threshold = cellPx * ResizeDragCells
                                if (travelled > threshold && sizeKind == ActionTileSize.Small) {
                                    onResizeNow(ActionTileSize.Wide)
                                    travelled = 0f
                                } else if (travelled < -threshold && sizeKind == ActionTileSize.Wide) {
                                    onResizeNow(ActionTileSize.Small)
                                    travelled = 0f
                                }
                            },
                        )
                    }
                    .clearAndSetSemantics { testTag = "action_resize_handle" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(width = 5.dp, height = 24.dp).background(tone.content.copy(alpha = 0.9f), CircleShape))
            }
        }
    }
}

/** The Edit button, where the design left a cell free: outlined rather than filled, so it reads as the menu's own, not an action. */
@Composable
private fun EditTile(
    slot: BentoSlot,
    cellPx: Float,
    gapPx: Float,
    scheme: ColorScheme,
    delay: Float,
    progress: () -> Float,
    interactive: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    val density = LocalDensity.current
    val sizePx = slot.sizePx(cellPx, gapPx)
    val offset by animateIntOffsetAsState(slot.offsetPx(cellPx, gapPx).round(), AppMotion.spatial(), label = "edit_tile_offset")
    val ink = scheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .offset { offset }
            .size(with(density) { sizePx.width.toDp() }, with(density) { sizePx.height.toDp() })
            .arriving(progress, delay)
            .clip(CircleShape)
            .border(1.5.dp, ink.copy(alpha = 0.55f), CircleShape)
            .clickable(enabled = interactive, onClickLabel = "Edit actions", role = Role.Button) {
                haptics.selection()
                onClick()
            }
            .semantics { contentDescription = "Edit actions" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null, tint = ink, modifier = Modifier.size(22.dp))
            TileLabel("Edit", ink)
        }
    }
}

@Composable
private fun CompactTile(
    label: String,
    description: String,
    icon: @Composable () -> Unit,
    container: Color,
    delay: Float,
    progress: () -> Float,
    interactive: Boolean,
    editing: Boolean,
    selected: Boolean,
    outlined: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val interactions = remember { MutableInteractionSource() }
    val press by rememberPressScale(interactions)
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .arriving(progress, delay)
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(RoundedCornerShape(TileCorner))
            .clickable(
                interactionSource = interactions,
                indication = ripple(bounded = true),
                enabled = interactive,
                onClickLabel = if (editing) "Select $description" else description,
            ) {
                haptics.selection()
                onClick()
            }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (editing) this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(TileCorner))
                .background(container)
                .then(
                    when {
                        selected -> Modifier.border(2.dp, scheme.onSurface, RoundedCornerShape(TileCorner))
                        outlined -> Modifier.border(1.5.dp, scheme.onSurfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(TileCorner))
                        editing -> Modifier.border(1.dp, scheme.onSurface.copy(alpha = 0.25f), RoundedCornerShape(TileCorner))
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) { icon() }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun TileLabel(text: String, color: Color) {
    // A word in a fifth of a phone's width: it steps down from 11 sp until it fits (a long word in a wide custom font
    // included) instead of running into the tile's edge or being cut.
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp),
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier.clearAndSetSemantics { },
    )
}

/**
 * Under the grid in Edit mode: what the selected tile can be told ([selected], else a hint), then Reset and Done. The buttons
 * do what dragging does, for those who don't drag and for screen readers.
 */
@Composable
private fun EditBar(
    selected: ArrangedAction?,
    index: Int,
    count: Int,
    /** Tiles have two sizes (the grid, not the list of equal tiles): the bar offers the resize, and says which tile is featured. */
    sizable: Boolean,
    canReset: Boolean,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onToggleSize: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected == null) {
                Text(
                    text = "Tap a tile to move or resize it. Touch and hold to drag it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = selected.action.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(onClick = onMoveEarlier, enabled = index > 0) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Move earlier")
                }
                IconButton(onClick = onMoveLater, enabled = index < count - 1) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Move later")
                }
                if (!sizable) {
                    // A list of equal tiles: nothing to resize.
                } else if (index == 0) {
                    Text("Featured", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(horizontal = 8.dp))
                } else {
                    TextButton(onClick = onToggleSize) {
                        Text(if (selected.size == ActionTileSize.Small) "Make wide" else "Make small")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Edit actions",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            TextButton(onClick = onReset, enabled = canReset) { Text("Reset") }
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

/** A page-sized dim that goes with the menu: the page behind a floating panel steps back instead of competing with it. */
internal fun Modifier.actionMenuScrim(progress: () -> Float, color: Color): Modifier =
    drawBehind { drawRect(color, alpha = progress().coerceIn(0f, 1f)) }

/** A tap on this closes the menu: laid over the dock, which the open menu has faded out, so it can't be pressed by mistake. */
internal fun Modifier.closesMenuOnTap(onClose: () -> Unit): Modifier =
    pointerInput(onClose) { detectTapGestures { onClose() } }
