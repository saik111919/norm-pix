package com.example.normpix.navigation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import com.example.normpix.ui.components.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.isDark
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/*
 * The "+" action menu: a bento panel that opens out of the "+" button above the dock.
 *
 *     ┌───────────────┬───────────────┐
 *     │    Expense    │    Income     │
 *     ├──────┬──────┬─┴────┬──────────┤
 *     │Recur.│Remind│ Card │ Account  │
 *     ├──────┼──────┼──────┼──────────┤
 *     │Categ.│Budget│People│  Search  │
 *     ├──────┴──────┴──────┴──────────┘
 *     │Settin│
 *     └──────┘
 *
 * The two main actions come first as capsules side by side (the featured tile, filled, then a wide one, tonal), then every other
 * action as an icon over its name on a grid of four equal cells, in the order and at the sizes the user left them (see
 * [ActionMenuLayout]; the picture is the design's own). The Edit button is not a cell of the grid: while the menu is open it
 * sits in the dock row, in the empty space left of the close button (see FloatingNavBar.kt), so the grid ends with its last
 * action and the two buttons read as a pair:
 *
 *     [   dock, faded out   ]  ( Edit )  ( × )
 *
 * Edit mode follows Pixel's Quick Settings editor: tiles stop running their action and are selected instead; a selected tile
 * has a handle on its right edge that drags between the two sizes, touch-and-hold lifts a tile to drag it into place (the
 * others make room), and a bar under the grid has Reset and Done, with buttons that do the same moves for those who don't
 * drag (and for screen readers, which are also given them as actions on each tile).
 *
 * One progress value (0 closed, 1 open) drives the whole thing - the panel's growth out of the "+" button, each tile arriving a
 * beat after its neighbour nearer the button, the button's turn into a cross, the dock stepping back, the Edit button arriving
 * where the dock's end stood and the page dimming - so they can never drift apart. It is only ever read in graphics layers and
 * draw lambdas, so a frame of this recomposes nothing.
 *
 * With large text, or on a screen too narrow for labels inside four columns, the same actions come as three columns of
 * icon-over-label tiles whose labels may take two lines (and reorder with the bar's buttons only).
 */

private val BentoGap = 8.dp
private val PanelPadding = 12.dp
private val PanelCorner = 32.dp
private val TileCorner = 22.dp

/** Narrowest cell that still holds a label of a word ("Categories") beside no more than the tile's padding. */
private val MinBentoCell = 64.dp

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

/** The close: a spring with no overshoot, stiff enough to answer a dismiss at once and to be gone in about a quarter of a second. */
private const val CloseStiffness = 520f

/**
 * Progress of the action menu: 0 closed, 1 open. Opens on the app's spatial spring (it may pass 1 by a hair while it
 * settles), closes on a stiffer spring that does not overshoot; instant when animations are off. A [State], so the readers that
 * draw from it recompose nothing.
 *
 * The close is a spring and not a curve of fixed length: a fast-start curve took the panel from open to nearly closed in
 * three frames, so it blinked out instead of shrinking back to the button, and a tap on "+" while it was closing started the
 * open from a standstill. A spring carries its speed over into whatever comes next.
 */
@Composable
fun rememberActionMenuProgress(open: Boolean): State<Float> {
    val progress = remember { Animatable(if (open) 1f else 0f) }
    val openSpec = AppMotion.spatial<Float>()
    val reduced = rememberReducedMotion()
    LaunchedEffect(open, openSpec, reduced) {
        when {
            open -> progress.animateTo(1f, openSpec)
            reduced -> progress.snapTo(0f)
            else -> progress.animateTo(0f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = CloseStiffness))
        }
    }
    return progress.asState()
}

/**
 * How much of the dock shows while the menu is at [progress]: gone once the panel is three-quarters open, and back as the panel
 * goes - so the dock and the panel cross over without ever being fully there together. While it is not all there it slides
 * ([dockSlide]) and shrinks towards the "+" it goes into, so it is seen to go and come back rather than fade.
 *
 * Until 2026-11-01 it was gone by half open and only faded, with a 10% shrink: on the close spring that is the last ~130 ms, and
 * the owner saw it "snap into place" instead of sliding in. Three-quarters gives it ~200 ms of the close, and the slide gives it
 * somewhere to come from.
 */
internal fun dockShown(progress: Float): Float = ((DockGoneAt - progress) / DockGoneAt).coerceIn(0f, 1f)

/**
 * Where the bottom row (the dock and the "+") stands across the width it shares with the panel, at [progress] of the menu: the
 * middle of it while the menu is closed, the end - under the panel's own end, where the panel grows out of the "+" - once it is
 * open, and on the way between in step with the panel. [space] is that width and [size] the row's, in pixels; at the end of a
 * right-to-left layout is its left edge.
 *
 * It used to be one or the other: the row was put at the end of a column the width of the panel while the panel was composed and in
 * the middle of one the width of the row once it was not, so as the panel finished closing the whole footer jumped sideways by half
 * the room around it (measured in a screen recording: 26 px in one frame), just as it was arriving.
 */
internal fun dockRowX(space: Int, size: Int, progress: Float, rtl: Boolean): Int {
    val middle = (space - size) / 2f
    val end = if (rtl) 0f else (space - size).toFloat()
    return (middle + (end - middle) * progress.coerceIn(0f, 1f)).roundToInt()
}

/** [dockRowX] as the alignment of the bottom row in its column; [progress] is read as the row is placed, so a frame recomposes nothing. */
internal class DockRowAlignment(private val progress: () -> Float) : Alignment.Horizontal {
    override fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int =
        dockRowX(space, size, progress(), rtl = layoutDirection == LayoutDirection.Rtl)
}

/**
 * Holds a column at the width the panel has - as wide as the room allows, up to [ActionMenuMaxWidth] - whether the panel is in it
 * or not, and wider if its content is. Without it the column was as wide as the panel while the panel was there and as the row
 * once it was gone, which moved everything in it when the panel left (see [dockRowX]).
 */
internal fun Modifier.atPanelWidth(): Modifier = layout { measurable, constraints ->
    val floor = if (constraints.hasBoundedWidth) minOf(constraints.maxWidth, ActionMenuMaxWidth.roundToPx()) else 0
    val placeable = measurable.measure(constraints.copy(minWidth = maxOf(constraints.minWidth, floor)))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
}

/** The menu's progress by which the dock has gone; the Edit button beside the "x" comes in only after it. */
internal const val DockGoneAt = 0.75f

/** How far the dock slides towards the "+" as it goes, at its farthest. */
internal val DockSlide = 72.dp

/** How much smaller the dock is, at its farthest, as it goes into the "+". */
internal const val DockShrink = 0.15f

/**
 * A tile's fill, the colour of its label and the colour of its icon (the same, unless the tile is a neutral one whose icon takes
 * the accent), from the theme so every palette (Light, Dark, Pure, wallpaper) works.
 */
internal data class TileTone(val container: Color, val content: Color, val accent: Color = content)

/**
 * Three roles, as the rest of the app uses them: the main action is **filled** primary (as Home's "Add income" is), the next is
 * the tonal primary container, and every other tile is the neutral tonal surface that fields and list segments are, its icon in
 * the accent. The rank is the size and the fill, not a different blue for each tile: an earlier ramp of five alternating tones read
 * as a patchwork, and its darkest tiles melted into the panel. [index] is the action's place in the design's own order, so a tile
 * keeps its look wherever the user moves it.
 */
internal fun tileTone(index: Int, scheme: ColorScheme): TileTone = when (index) {
    // Every pair is checked, not trusted: the hand-drawn palettes have no role made for some of them.
    0 -> tone(scheme.primary, listOf(scheme.onPrimary, scheme.onSurface))
    1 -> tone(scheme.primaryContainer, listOf(scheme.onPrimaryContainer, scheme.onSurface))
    else -> tone(scheme.surfaceContainerHighest, listOf(scheme.onSurface), accents = listOf(scheme.primary, scheme.onSurface))
}

/**
 * The first of [preferred] that reads on [container] at WCAG's 4.5:1 for small text, else whichever of black and white reads
 * better; the icon's colour is chosen the same way from [accents], or is the same ink.
 */
private fun tone(container: Color, preferred: List<Color>, accents: List<Color> = emptyList()): TileTone {
    fun readable(from: List<Color>): Color =
        from.firstOrNull { contrastRatio(it, container) >= 4.5f }
            ?: if (contrastRatio(Color.Black, container) >= contrastRatio(Color.White, container)) Color.Black else Color.White
    val ink = readable(preferred)
    return TileTone(container, ink, if (accents.isEmpty()) ink else readable(accents))
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** The panel's fill, a tone below the tiles; the Edit button in the dock row wears it too, so it looks as it did on the panel. */
internal fun actionMenuPanelColor(scheme: ColorScheme): Color =
    if (scheme.isDark) scheme.surfaceContainerLow else scheme.surfaceContainerLowest

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
    progress: () -> Float,
    open: Boolean,
    onActionClick: (FooterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(PanelCorner)
    val scheme = MaterialTheme.colorScheme
    // A tone below the tiles' (they are the highest container), so each tile stands off the panel in light and in dark alike, and
    // fully opaque: the panel draws no blur any more (it cost a frame's GPU and was invisible at 95%), and without one a faint
    // page behind it - a card's name, a banner - showed through the tiles as a ghost (seen on the phone).
    val panelColor = actionMenuPanelColor(scheme)
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
        selectedId = selectedId,
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
                // Seen early on the way open and kept to the end of the way back, so the panel is visibly carried to the button.
                alpha = (p * 3f).coerceIn(0f, 1f)
                // The alpha goes onto what is drawn, not onto a copy of the whole panel made for it every frame: the panel is
                // one surface of one colour, so nothing shows through itself, and a frame of the animation costs far less.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .fillMaxWidth()
            .widthIn(max = ActionMenuMaxWidth)
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
    val selectedId: String?,
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
    val plan = remember(arranged) { bentoPlan(arranged) }
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
    val count = arranged.size
    Column(verticalArrangement = Arrangement.spacedBy(BentoGap)) {
        val rows = (0 until count).chunked(CompactColumns)
        rows.forEachIndexed { r, indices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BentoGap)) {
                indices.forEachIndexed { c, index ->
                    val delay = if (open) ((rows.size - 1 - r) + (CompactColumns - 1 - c)) * TileStagger else 0f
                    val action = arranged[index]
                    CompactTile(
                        label = action.action.label,
                        description = action.action.contentDescription ?: action.action.label,
                        icon = { Icon(action.action.icon, contentDescription = null, tint = tileTone(toneIndexOf(action.action.id, index), scheme).accent, modifier = Modifier.size(24.dp)) },
                        container = tileTone(toneIndexOf(action.action.id, index), scheme).container,
                        delay = delay,
                        progress = progress,
                        interactive = open,
                        editing = edit.editing,
                        selected = edit.selectedId == action.action.id,
                        onClick = { if (edit.editing) edit.onSelect(action.action.id) else onActionClick(action.action) },
                        modifier = Modifier.weight(1f),
                    )
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
    // A dozen tiles fade at once: each with its own off-screen copy was the most expensive thing in the frame.
    compositingStrategy = CompositingStrategy.ModulateAlpha
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
    // A tile held to be moved rises and wears the primary ring - the same answer a held card gives (`holdFocus`), no shadow.
    val lift by animateFloatAsState(if (dragging) 1.06f else 1f, AppMotion.spatial(), label = "tile_lift")

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
            .clip(tileShape)
            .background(tone.container)
            .then(
                if (editing) {
                    Modifier.border(
                        if (dragging) 3.dp else if (selected) 2.dp else 1.dp,
                        if (dragging) scheme.primary else if (selected) scheme.onSurface else tone.content.copy(alpha = 0.35f),
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
        contentAlignment = if (slot.shape == BentoShape.Wide || slot.shape == BentoShape.Hero) Alignment.CenterStart else Alignment.Center,
    ) {
        if (slot.shape == BentoShape.Wide || slot.shape == BentoShape.Hero) {
            Row(
                modifier = Modifier.padding(start = 22.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = tone.accent, modifier = Modifier.size(if (slot.shape == BentoShape.Hero) 28.dp else 26.dp))
                Text(
                    text = action.label,
                    style = if (slot.shape == BentoShape.Hero) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = if (slot.shape == BentoShape.Hero) FontWeight.SemiBold else FontWeight.Medium,
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
                    tint = tone.accent,
                    modifier = Modifier.size(26.dp),
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
    // A word in a fifth of a phone's width: it steps down from 12 sp until it fits (a long word in a wide custom font
    // included) instead of running into the tile's edge or being cut. 12 sp is a label's size; the smallest, 9, is still legible.
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.sp),
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 12.sp),
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
