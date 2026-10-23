package com.example.normpix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.ui.TABULAR_FIGURES
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.isDark
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The card in front is this share of the window's width (within [MinCardWidth] and [MaxCardWidth]); the rest is for the ones
 * beside it, which show from behind it about 44dp each side on a 360dp phone.
 */
private const val CARD_SHARE = 0.66f
private val MinCardWidth = 272.dp
private val MaxCardWidth = 400.dp

/**
 * Where a deck's card is drawn: how wide it is, and how much of its lower edge the panel covers ([underlap]). A face that
 * wants its print to stay clear of the panel is drawn that much taller than a physical card - what shows above the panel is a
 * whole card - and, being narrower than a card of a list, draws its print at the usual size and shrinks it to [width], so it
 * is as roomy as that card is (a card this narrow once had its print crowded together, and its last line cut off by the panel).
 */
@Immutable
class DeckCardSpace(val width: Dp, val underlap: Dp)

/** Neighbouring cards' centres are this far apart, in card widths: far enough that each shows from behind the one in front of it. */
private const val STEP_SHARE = 0.52f

/** A card a whole step away is turned this many degrees about the upright, drawn this size, and shaded this much; farther ones stay so. */
private const val SIDE_ANGLE = 46f
private const val SIDE_SCALE = 0.82f
private const val SIDE_SHADE = 0.45f

/** The details turn, slide and fade as the cards do, only less: a step away they are turned this many degrees and gone before it. */
private const val DETAILS_ANGLE = 22f

/** The panel under the cards is drawn this far over their lower edge, so they stand in it. The card's own print stays clear of it. */
private val PocketOverlap = 10.dp

/** On a wide window the carousel is no wider than this, centred. */
private val MaxCarouselWidth = 560.dp

/** With more cards than this the page marker is "3 / 8", not a dot each. */
private const val MAX_DOTS = 6

/** The room a deck's details keep clear at the end of their first lines, for the page marker. */
val DeckMarkerInset = AppSpacing.x5l + AppSpacing.lg

/**
 * A 3D deck of cards with the front one's details in a panel the cards stand in: the carousel of the Wallet's saved cards
 * and of its accounts. It holds the look and the motion; what a card shows, what its details say and what the controls
 * at the panel's foot are come from the caller.
 *
 * The cards are fanned like a deck seen in perspective: the one in front faces you, those either side are turned away about
 * the upright, smaller and shaded, each tucked partly behind the next, all standing on the same line - the top of the panel.
 * Swipe to change card (one at a time), or tap one at the side to bring it forward.
 *
 * The panel holds the front card's [details] on a surface washed with its [tint], with [footer] under them. It moves with the
 * cards: as you swipe, the details of the card leaving slide away, turning and fading, those of the card arriving take their
 * place, and the panel's colour runs from one card's to the next's. All of it follows the finger, not a timer. With animations
 * off in system settings the cards are still turned to show depth but the details only cross-fade.
 *
 * The details of every card must be the same height (the same rows), so the panel does not change size mid-swipe; the first
 * line of them keeps clear of the page marker with `Modifier.padding(end = DeckMarkerInset)`.
 *
 * @param card one card's face, drawn at the card's full size; [inFront] says whether it is the one in front, and the
 *   [DeckCardSpace] where it is drawn (how wide, how much of its foot the panel covers). It is turned and shaded by the deck,
 *   and is not pressable at the side: a tap there brings the card forward.
 * @param sideDescription how a screen reader hears a card that is not in front: it has one action, to bring it forward.
 * @param footer the controls under the details - the same set for every card, so they stay still and act on [front].
 */
@Composable
fun <T : Any> DeckCarousel(
    items: List<T>,
    itemKey: (T) -> Any,
    tint: (T) -> Color?,
    sideDescription: (item: T, index: Int, count: Int) -> String,
    card: @Composable (item: T, inFront: Boolean, space: DeckCardSpace) -> Unit,
    details: @Composable ColumnScope.(item: T) -> Unit,
    footer: @Composable ColumnScope.(front: T) -> Unit,
    modifier: Modifier = Modifier,
    bringForwardLabel: String = "Show this card",
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    // Where the carousel is, in cards: 1.0 is the second card in front, 1.5 half way to the third. Read only while drawing and
    // placing, so a swipe moves things without recomposing them.
    val position = remember(pagerState) { derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction } }
    val tints = remember(items) { items.map(tint) }
    // A tick as each card settles in front, but not for the one it opened on, nor when the page moved only because the list did
    // (a card deleted, a bank filtered).
    val count by rememberUpdatedState(items.size)
    LaunchedEffect(pagerState) {
        var seenCount = count
        snapshotFlow { pagerState.settledPage }.drop(1).collect {
            if (count == seenCount) haptics.selection()
            seenCount = count
        }
    }

    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = MaxCarouselWidth).fillMaxWidth()) {
            val cardWidth = (maxWidth * CARD_SHARE).coerceIn(MinCardWidth, MaxCardWidth).coerceAtMost(maxWidth - AppSpacing.xxl)
            val space = DeckCardSpace(cardWidth, PocketOverlap)
            val step = cardWidth * STEP_SHARE
            val sidePadding = (maxWidth - step) / 2
            Column(Modifier.fillMaxWidth()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = AppSpacing.sm),
                    // A page is one step wide and its card is wider, so the cards overlap: each is centred on its page.
                    pageSize = PageSize.Fixed(step),
                    beyondViewportPageCount = 1,
                    key = { itemKey(items[it]) },
                ) { page ->
                    val item = items[page]
                    val inFront = page == pagerState.currentPage
                    fun bringForward() {
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                    Box(
                        Modifier
                            .requiredWidth(cardWidth)
                            // The nearest card is drawn last, so each is tucked behind the one in front of it.
                            .drawnInOrderOf { 2f - abs(page - position.value).coerceAtMost(2f) }
                            .graphicsLayer {
                                // Positive for a card to the right of the one in front, negative to the left.
                                val away = (page - position.value).coerceIn(-1.5f, 1.5f)
                                val near = abs(away).coerceAtMost(1f)
                                cameraDistance = 12f * density
                                // Everything turns about the bottom edge's middle: the cards all keep standing on the panel.
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                rotationY = -away * SIDE_ANGLE
                                val scale = 1f - (1f - SIDE_SCALE) * near
                                scaleX = scale
                                scaleY = scale
                            }
                            .drawWithContent {
                                drawContent()
                                // Farther from the front, farther from the light.
                                val shade = SIDE_SHADE * abs(page - position.value).coerceAtMost(1f)
                                if (shade > 0f) {
                                    drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), Color.Black.copy(alpha = shade))
                                }
                            }
                            .then(
                                if (inFront) {
                                    Modifier
                                } else {
                                    // Beside the one in front, a card is only a way to it: nothing to turn over, and none of the
                                    // card's own actions for a screen reader - it says which card, and brings it forward.
                                    Modifier.clearAndSetSemantics {
                                        contentDescription = sideDescription(item, page, items.size)
                                        role = Role.Button
                                        onClick(label = bringForwardLabel) {
                                            bringForward()
                                            true
                                        }
                                    }
                                },
                            ),
                    ) {
                        card(item, inFront, space)
                        if (!inFront) {
                            // A card at the side is a way to it, not a card to press: a tap brings it to the front. It has no ripple
                            // (it is turned and shaded), so keyboard focus is shown by a ring.
                            val source = remember { MutableInteractionSource() }
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .focusRing(source, AppRadius.rLg)
                                    .clickable(interactionSource = source, indication = null, onClick = ::bringForward),
                            )
                        }
                    }
                }
                PocketPanel(
                    items = items,
                    itemKey = itemKey,
                    pagerState = pagerState,
                    position = position,
                    tints = tints,
                    reduced = reduced,
                    details = details,
                    footer = footer,
                )
            }
        }
    }
}

/**
 * The panel the cards stand in, drawn over their lower edge: the front card's details, and under them the footer. The details
 * are two layers at most - the card leaving and the card arriving - and the surface behind them runs from one card's colour to
 * the next's with the swipe.
 */
@Composable
private fun <T : Any> PocketPanel(
    items: List<T>,
    itemKey: (T) -> Any,
    pagerState: PagerState,
    position: State<Float>,
    tints: List<Color?>,
    reduced: Boolean,
    details: @Composable ColumnScope.(T) -> Unit,
    footer: @Composable ColumnScope.(T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val base = scheme.surfaceContainerHigh
    val edge = scheme.outlineVariant
    val dark = scheme.isDark
    val shape = RoundedCornerShape(AppRadius.xxl)
    val last = items.lastIndex
    val frontIndex = pagerState.currentPage.coerceIn(0, last)
    val front = items[frontIndex]
    val lower by remember(position, last) { derivedStateOf { floor(position.value).toInt().coerceIn(0, last) } }
    Column(
        Modifier
            .padding(horizontal = AppSpacing.lg)
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Reported shorter than it is and placed higher: the top of it is over the cards' lower edge.
                val placeable = measurable.measure(constraints)
                val overlap = PocketOverlap.roundToPx().coerceAtMost(placeable.height)
                layout(placeable.width, placeable.height - overlap) { placeable.place(0, -overlap) }
            }
            .clip(shape)
            .drawBehind {
                // The card's colour at the top, where the cards stand, going out by the foot; it follows the swipe.
                val p = position.value
                val from = floor(p).toInt().coerceIn(0, last)
                val to = (from + 1).coerceAtMost(last)
                val tint = lerpTint(tints[from], tints[to], (p - from).coerceIn(0f, 1f))?.let { deckWashTint(it, dark) }
                val wash = if (tint == null) {
                    Brush.verticalGradient(listOf(base, base))
                } else {
                    Brush.verticalGradient(
                        0f to lerp(base, tint, DECK_WASH_TOP),
                        0.55f to lerp(base, tint, DECK_WASH_MIDDLE),
                        1f to base,
                    )
                }
                drawRect(wash)
            }
            .border(1.dp, Brush.verticalGradient(listOf(scheme.onSurface.copy(alpha = 0.22f), edge.copy(alpha = 0.4f))), shape)
            .padding(start = AppSpacing.xl, end = AppSpacing.xl, top = PocketOverlap + AppSpacing.lg, bottom = AppSpacing.lg),
    ) {
        Box(Modifier.fillMaxWidth()) {
            for (index in lower..minOf(lower + 1, last)) {
                key(itemKey(items[index])) {
                    DetailsLayer(
                        index = index,
                        position = position,
                        reduced = reduced,
                        modifier = if (index == frontIndex) Modifier else Modifier.clearAndSetSemantics { },
                    ) { details(items[index]) }
                }
            }
            if (items.size > 1) PageMarker(items.size, frontIndex, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(AppSpacing.lg))
        footer(front)
    }
}

/**
 * What one card holds, as text on the panel. [index] is its place in the deck: while the carousel is between two cards the
 * panel shows both, each by how far it is from the front - sliding in from the side it comes from, turning about the upright
 * the way its card does, and fading, so it is gone before it is a whole step away.
 */
@Composable
private fun DetailsLayer(
    index: Int,
    position: State<Float>,
    reduced: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val away = index - position.value
                val near = abs(away).coerceAtMost(1f)
                alpha = (1f - near * 1.6f).coerceIn(0f, 1f)
                if (!reduced) {
                    cameraDistance = 12f * density
                    translationX = away * size.width * 0.45f
                    rotationY = -away * DETAILS_ANGLE
                    val scale = 1f - 0.06f * near
                    scaleX = scale
                    scaleY = scale
                }
            },
        content = content,
    )
}

/** A small caps label over what it names, in the way a card names its own details. */
@Composable
fun DeckDetail(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    valueColor: Color = Color.Unspecified,
    /** What a screen reader says for [value] when the value itself would not say it (a dash). */
    valueDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        // The label may take a second line, and the value too: at large text neither is cut short, nor becomes dots.
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = scheme.onSurfaceVariant,
            maxLines = 2,
        )
        Spacer(Modifier.height(AppSpacing.xxs))
        Text(
            value,
            style = (if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
                .copy(fontFeatureSettings = TABULAR_FIGURES),
            fontWeight = FontWeight.SemiBold,
            color = if (valueColor == Color.Unspecified) scheme.onSurface else valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // A value that is not a word or a figure ("—") is said for a screen reader as what it means.
            modifier = if (valueDescription != null) Modifier.semantics { contentDescription = valueDescription } else Modifier,
        )
    }
}

/**
 * The one figure a deck's details are about, on a line of its own and as large as they get: [label] in small caps over [value],
 * which shrinks to fit rather than being cut. The label keeps clear of the page marker (the value is below it).
 */
@Composable
fun DeckHero(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    /** What a screen reader says for [value] when the value itself would not say it (amounts that are hidden are dots). */
    valueDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = TABULAR_FIGURES)
    Column(modifier) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = scheme.onSurfaceVariant,
            // Room for a second line: "Balance · Owed" beside the page marker, at large text, wraps rather than being cut to "Balance ·".
            maxLines = 2,
            modifier = Modifier.padding(end = DeckMarkerInset),
        )
        Spacer(Modifier.height(AppSpacing.xxs))
        Text(
            value,
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = if (valueColor == Color.Unspecified) scheme.onSurface else valueColor,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = style.fontSize),
            modifier = if (valueDescription != null) Modifier.semantics { contentDescription = valueDescription } else Modifier,
        )
    }
}

/** A hairline between two rows of a deck's details. */
@Composable
fun DeckDivider() {
    HorizontalDivider(Modifier.padding(vertical = AppSpacing.md), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/**
 * Where you are among the cards: a dot each, the one in front a longer pill - or "3 / 8" when there are too many for dots.
 * Spoken as the place, "Card 2 of 3", and announced as it changes.
 */
@Composable
private fun PageMarker(count: Int, current: Int, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = "Card ${current + 1} of $count"
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count > MAX_DOTS) {
            Text("${current + 1} / $count", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            return@Row
        }
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(
                if (selected) 20.dp else 8.dp,
                tween(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard),
                label = "carousel_dot_width",
            )
            val color by animateColorAsState(
                // The outline tone, not the softer one: a dot says where you are, and has to be seen against the panel (3:1).
                if (selected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = IDLE_DOT_ALPHA),
                tween(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard),
                label = "carousel_dot_color",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

/**
 * Draws this above its siblings with a larger [order] and below those with a smaller one, read while placing: the order
 * can follow a swipe without recomposing anything. (`Modifier.zIndex` takes its value when composed.)
 */
private fun Modifier.drawnInOrderOf(order: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0, zIndex = order()) }
}

/** How much of the front card's colour washes the panel at its top, and half way down it; the foot is the panel's own surface. */
internal const val DECK_WASH_TOP = 0.34f
internal const val DECK_WASH_MIDDLE = 0.12f

/** How much of the secondary text colour the page marker's idle dots keep: enough to be seen against the panel and its wash (3:1). */
internal const val IDLE_DOT_ALPHA = 0.7f

/**
 * How far the card's colour is lightened toward white before it washes a light panel. A bank's own colour is dark - a deep red, a
 * navy - and 34% of it turns a light panel dark under the dark text on it: measured over every bank and every accent, error
 * text fell to 2.4:1 and the small labels to 3.4:1. Lightened this much the same wash keeps error text above 4.5:1, the labels
 * above 6:1 and the page marker above 3:1 (a dark panel needs none: its text is light).
 */
internal const val DECK_WASH_LIGHT_PASTEL = 0.8f

/** The colour [tint] washes the panel with: itself on a dark panel, a lightened one on a light panel (see [DECK_WASH_LIGHT_PASTEL]). */
internal fun deckWashTint(tint: Color, dark: Boolean): Color = if (dark) tint else lerp(tint, Color.White, DECK_WASH_LIGHT_PASTEL)

/** Between two colours; either may be missing, and then the other is used. */
private fun lerpTint(from: Color?, to: Color?, fraction: Float): Color? = when {
    from == null -> to
    to == null -> from
    else -> lerp(from, to, fraction)
}
