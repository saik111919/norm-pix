package com.example.normpix.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.HeroAmount
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.ui.components.FloatingTopControls
import com.example.normpix.ui.components.rememberBackButtonStyle
import com.example.normpix.ui.theme.AppSpacing

/*
 * Home's number, collapsing as the page is scrolled - after the way Google's Weather shrinks its temperature into the top of the
 * screen and grows it back. "Spent in November" and the amount start large on the hero card; as the list scrolls up they shrink
 * and glide to the header, where they settle as a small chip (the primary colour, the amount under its label) in the place of the
 * page's title, and the rest of Home slides on beneath. Scrolling back down reverses it, frame for frame: it is the scroll that
 * drives it, not a timer, so it follows the finger, stops where the finger stops, and needs no reduced-motion variant.
 *
 * While it moves, the two lines are drawn here, over the page, because the hero card clips what is inside it; the card's own copies
 * stay in place (they give the layout its room and are what a screen reader reads) and are drawn only while the page is at rest.
 * The copies here are the same text in the same style at the same width, so the hand-over at the first pixel of scroll cannot be seen.
 */

/** How big the two lines are once pinned, against their size on the card: the amount to about 18 sp, the label to about 11.5 sp. */
internal const val AMOUNT_PINNED_SCALE = 0.32f
internal const val EYEBROW_PINNED_SCALE = 0.72f

/** The share of the way up by which the lines have reached their pinned size: they shrink first and then settle. */
private const val SHRUNK_BY = 0.6f

/** Where the header's room is, and the chip's measures, in pixels of the window. */
internal class HeroCollapseSpec(
    /** Where the pinned chip's left edge and top are. */
    val chipLeft: Float,
    val chipTop: Float,
    /** The room round the two lines inside the chip. */
    val padHorizontal: Float,
    val padVertical: Float,
    /** The foot of the header, where the page's colour stops covering the list: the chip is drawn solid before it gets there. */
    val headerBottom: Float,
    /** How far above [headerBottom] the chip starts to fade in. */
    val fade: Float,
    /** Where the list's viewport starts below the top of the page (0 under the floating header; the app bar's height otherwise). */
    val listTop: Float,
    /** Where the list's content column starts across the page (a wide window centres it). */
    val columnLeft: Float,
)

/** The two lines as they are with the page at rest: where (window pixels) and how big, each as drawn. */
internal class HeroCollapseRest(val eyebrow: Offset, val eyebrowSize: Size, val amount: Offset, val amountSize: Size)

/** How the collapse looks at one moment: each line's place and scale, and the chip behind them. */
internal class HeroCollapseFrame(
    /** 0 with the page at rest, 1 once the lines are pinned. */
    val progress: Float,
    val eyebrowOffset: Offset,
    val eyebrowScale: Float,
    val amountOffset: Offset,
    val amountScale: Float,
    val chip: Rect,
    val chipRadius: Float,
    /** 0 while the lines are over the card, 1 once they are over the page's own colour. */
    val chipAlpha: Float,
    /** How much of the page's own title still shows: all of it until the lines come near the header, none once they are in it. */
    val titleAlpha: Float,
)

private fun lerp(from: Float, to: Float, by: Float) = from + (to - from) * by

private fun smooth(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The collapse at [scroll] pixels of the list scrolled. The lines move up with the page - the way the card does - until, over the
 * last stretch, they ease into the chip's place instead of leaving with the page: `progress` is how far they have come, and their
 * place is the page's blended into the chip's by its square, so they arrive slowly (no jolt where they stop) and leave the page
 * at its own speed (no jump where they let go). They are also scaled, and moved sideways, by [smooth] of the way to [SHRUNK_BY].
 */
internal fun heroCollapseFrame(scroll: Float, rest: HeroCollapseRest, spec: HeroCollapseSpec): HeroCollapseFrame {
    val textLeft = spec.chipLeft + spec.padHorizontal
    val eyebrowTop = spec.chipTop + spec.padVertical
    val eyebrowHeight = rest.eyebrowSize.height * EYEBROW_PINNED_SCALE
    val amountTop = eyebrowTop + eyebrowHeight
    val travel = (rest.amount.y - amountTop).coerceAtLeast(1f)
    val progress = (scroll / travel).coerceIn(0f, 1f)
    val ease = progress * progress
    val shrink = smooth(progress / SHRUNK_BY)

    val eyebrowScale = lerp(1f, EYEBROW_PINNED_SCALE, shrink)
    val amountScale = lerp(1f, AMOUNT_PINNED_SCALE, shrink)
    val eyebrow = Offset(lerp(rest.eyebrow.x, textLeft, shrink), lerp(rest.eyebrow.y - scroll, eyebrowTop, ease))
    val amount = Offset(lerp(rest.amount.x, textLeft, shrink), lerp(rest.amount.y - scroll, amountTop, ease))

    val right = maxOf(eyebrow.x + rest.eyebrowSize.width * eyebrowScale, amount.x + rest.amountSize.width * amountScale)
    val chip = Rect(
        left = minOf(eyebrow.x, amount.x) - spec.padHorizontal * shrink,
        top = eyebrow.y - spec.padVertical * shrink,
        right = right + spec.padHorizontal * shrink,
        bottom = amount.y + rest.amountSize.height * amountScale + spec.padVertical * shrink,
    )
    // Drawn solid only once the lines are over the page's colour (the header covers the list there): over the card they are the
    // card's own colour, and a chip of it would cut a patch out of the card's decoration.
    val chipAlpha = smooth((spec.headerBottom + spec.fade - eyebrow.y) / spec.fade)
    // The title gives way as the lines arrive over it, not before: from the foot of the header, where the chip is already solid, to
    // the chip's own place - so the header's start is never left empty while the lines are still on their way.
    val titleGone = smooth((spec.headerBottom - eyebrow.y) / (spec.headerBottom - eyebrowTop).coerceAtLeast(1f))
    return HeroCollapseFrame(progress, eyebrow, eyebrowScale, amount, amountScale, chip, chip.height / 2f * shrink, chipAlpha, 1f - titleGone)
}

/** Which of the two lines a layout is the card's copy of. */
internal enum class CollapsingLine { Eyebrow, Amount }

/**
 * What Home's collapse knows: the two lines' words, the places of their copies on the card (read from the layout when a frame is
 * drawn, never stored as numbers, so a change of text size or window shows at once) and the list they follow. The layout
 * coordinates are plain fields: they are read as a frame is drawn, which the list's scroll invalidates, and keeping them out of
 * Compose's state means a scroll recomposes nothing.
 */
@Stable
internal class HeroCollapseState(private val list: LazyListState) {
    var eyebrow by mutableStateOf("")
    var amount by mutableStateOf("")

    /** The width the card gives the lines, so the copies are laid out - and, for the amount, sized - just as the card's are. */
    var contentWidth by mutableIntStateOf(0)

    internal var item: LayoutCoordinates? = null
    internal var eyebrowCoordinates: LayoutCoordinates? = null
    internal var amountCoordinates: LayoutCoordinates? = null

    /** How big the copies drawn over the page really are (wrapped to their text), for the chip round them. */
    var eyebrowDrawn by mutableStateOf(IntSize.Zero)
    var amountDrawn by mutableStateOf(IntSize.Zero)

    /** Where the overlay (the page's own box) is in the window; the list and the header are laid out from its top. */
    internal var origin: Offset = Offset.Zero

    private var restEyebrow: Offset? = null
    private var restAmount: Offset? = null

    /**
     * Whether the two lines are being drawn over the page now - it is scrolled and everything needed to draw them is known - and so
     * the card's own copies are to stay undrawn. Read as the card is drawn, so it follows the scroll.
     */
    val isCollapsing: Boolean
        get() {
            val scrolled = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0
            return scrolled && restEyebrow != null && restAmount != null && eyebrowDrawn != IntSize.Zero && amountDrawn != IntSize.Zero
        }

    /**
     * The frame to draw now, or null while there is nothing to draw (the page is at rest, or the card has not been laid out).
     *
     * Where the lines stand with the page at rest is worked out from numbers the list itself gives, which are exact for the frame
     * being drawn - where its viewport starts ([HeroCollapseSpec.listTop]) plus the room it leaves above its first item - and from
     * the lines' places *inside* the item, which a scroll never changes. It is deliberately not read from where the item is on the
     * screen: an item's place there lags the scroll by a frame while it is moving, and the lines, the chip and the page would each
     * be a frame off from one another (seen in a render: the chip a whole step ahead of the text).
     */
    fun frame(spec: HeroCollapseSpec): HeroCollapseFrame? {
        val atTop = list.firstVisibleItemIndex == 0
        val scroll = if (atTop) list.firstVisibleItemScrollOffset.toFloat() else Float.MAX_VALUE / 4f
        val item = item?.takeIf { it.isAttached }
        val e = eyebrowCoordinates?.takeIf { it.isAttached }
        val a = amountCoordinates?.takeIf { it.isAttached }
        if (item != null && e != null && a != null) {
            val restTop = Offset(origin.x + spec.columnLeft, origin.y + spec.listTop + list.layoutInfo.beforeContentPadding)
            restEyebrow = restTop + item.localPositionOf(e, Offset.Zero)
            restAmount = restTop + item.localPositionOf(a, Offset.Zero)
        }
        val eyebrowAt = restEyebrow ?: return null
        val amountAt = restAmount ?: return null
        if (scroll <= 0f || eyebrowDrawn == IntSize.Zero || amountDrawn == IntSize.Zero) return null
        val rest = HeroCollapseRest(
            eyebrow = eyebrowAt,
            eyebrowSize = Size(eyebrowDrawn.width.toFloat(), eyebrowDrawn.height.toFloat()),
            amount = amountAt,
            amountSize = Size(amountDrawn.width.toFloat(), amountDrawn.height.toFloat()),
        )
        return heroCollapseFrame(scroll, rest, spec)
    }
}

/** Marks a layout as the card's copy of [line], so the collapse can find where it is. */
internal fun Modifier.collapsingLine(state: HeroCollapseState, line: CollapsingLine): Modifier = onGloballyPositioned { coordinates ->
    when (line) {
        CollapsingLine.Eyebrow -> {
            state.eyebrowCoordinates = coordinates
            if (state.contentWidth != coordinates.size.width) state.contentWidth = coordinates.size.width
        }
        CollapsingLine.Amount -> state.amountCoordinates = coordinates
    }
}

/** Marks the page's item as the thing the lines' places are measured in. */
internal fun Modifier.collapseItem(state: HeroCollapseState): Modifier = onGloballyPositioned { state.item = it }

/** The header's room and the chip's measures for this window, style of controls and status bar. */
@Composable
internal fun rememberHeroCollapseSpec(maxWidth: Dp?): HeroCollapseSpec {
    val density = LocalDensity.current
    val floating = rememberBackButtonStyle() == BackButtonStyle.TopFloatingCircle
    val statusBar = WindowInsets.statusBars.getTop(density)
    val window = LocalWindowInfo.current.containerSize.width.toFloat()
    return remember(density, floating, statusBar, window, maxWidth) {
        with(density) {
            val chipHeight = FloatingTopControls.size.toPx()
            val barHeight = if (floating) FloatingTopControls.clearance.toPx() else 64.dp.toPx()
            val listTop = if (floating) 0f else statusBar + 64.dp.toPx()
            // The header's content column starts one page margin in, and a wide window centres it within its own width.
            val column = if (maxWidth == null) window else minOf(window, maxWidth.toPx())
            HeroCollapseSpec(
                chipLeft = (window - column) / 2f + AppSpacing.lg.toPx(),
                chipTop = statusBar + if (floating) FloatingTopControls.margin.toPx() else (64.dp.toPx() - chipHeight) / 2f,
                padHorizontal = 12.dp.toPx(),
                padVertical = 2.dp.toPx(),
                headerBottom = statusBar + barHeight,
                fade = 40.dp.toPx(),
                listTop = listTop,
                columnLeft = (window - column) / 2f,
            )
        }
    }
}

/**
 * The lines while they are on the way to the header, and the chip they settle in: laid over the whole page, drawn only while the
 * list has scrolled, and touching nothing (a tap goes through to what is under it).
 */
@Composable
internal fun HeroCollapseOverlay(state: HeroCollapseState, spec: HeroCollapseSpec, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val frame = { state.frame(spec) }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { state.origin = it.positionInWindow() }
            .clearAndSetSemantics { }
            .drawBehind {
                val f = frame() ?: return@drawBehind
                if (f.chipAlpha <= 0f) return@drawBehind
                drawRoundRect(
                    color = scheme.primary.copy(alpha = f.chipAlpha),
                    topLeft = f.chip.topLeft - state.origin,
                    size = f.chip.size,
                    cornerRadius = CornerRadius(f.chipRadius),
                )
            },
    ) {
        if (state.contentWidth <= 0) return@Box
        val width = with(density) { state.contentWidth.toDp() }
        CompositionLocalProvider(LocalContentColor provides scheme.onPrimary) {
            Box(
                Modifier
                    .widthIn(max = width)
                    .onSizeChanged { state.eyebrowDrawn = it }
                    .graphicsLayer {
                        val f = frame()
                        alpha = if (f == null) 0f else 1f
                        if (f != null) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = f.eyebrowOffset.x - state.origin.x
                            translationY = f.eyebrowOffset.y - state.origin.y
                            scaleX = f.eyebrowScale
                            scaleY = f.eyebrowScale
                        }
                    },
            ) {
                // The card's own line is a row with room at its end; this is the same text in the same style, wrapped to its words.
                Text(
                    state.eyebrow,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalContentColor.current.copy(alpha = 0.82f),
                    maxLines = 1,
                )
            }
            Box(
                Modifier
                    .widthIn(max = width)
                    .onSizeChanged { state.amountDrawn = it }
                    .graphicsLayer {
                        val f = frame()
                        alpha = if (f == null) 0f else 1f
                        if (f != null) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = f.amountOffset.x - state.origin.x
                            translationY = f.amountOffset.y - state.origin.y
                            scaleX = f.amountScale
                            scaleY = f.amountScale
                        }
                    },
            ) { HeroAmount(state.amount) }
        }
    }
}
