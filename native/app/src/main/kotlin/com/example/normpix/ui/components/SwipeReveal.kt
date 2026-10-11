package com.example.normpix.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps one swipe-revealed row open per list. Rows register with the coordinator of the nearest
 * [SwipeRevealHost]; opening a row closes whichever was open, and the host closes it on any press outside it.
 */
@Stable
class SwipeRevealCoordinator {
    /** The key of the row that is open, or null. */
    var openKey: Any? by mutableStateOf(null)
        private set

    private var openRow: LayoutCoordinates? = null
    internal var host: LayoutCoordinates? = null

    internal fun opened(key: Any, row: LayoutCoordinates?) {
        openRow = row
        openKey = key
    }

    internal fun closed(key: Any) {
        if (openKey == key) {
            openKey = null
            openRow = null
        }
    }

    fun closeAll() {
        openKey = null
        openRow = null
    }

    /** The open row's bounds in the host's coordinates, or null when nothing is open (or it scrolled away). */
    internal fun openBounds(): Rect? {
        val row = openRow?.takeIf { it.isAttached } ?: return null
        val host = host?.takeIf { it.isAttached } ?: return null
        return host.localBoundingBoxOf(row)
    }
}

internal val LocalSwipeRevealCoordinator = compositionLocalOf<SwipeRevealCoordinator?> { null }

/** Closes the swipe row that is showing its actions; [SwipeActionButton] calls this so an action never leaves the row open. */
internal val LocalSwipeRowClose = compositionLocalOf<() -> Unit> { {} }

/**
 * Wraps a list of [SwipeRevealRow]s. A tap anywhere outside the open row just closes it (the tap is
 * swallowed, so closing never also triggers whatever was underneath), and scrolling closes it too. Presses
 * never get consumed on the way down, so scrolling and swiping other rows keep working.
 */
@Composable
fun SwipeRevealHost(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val coordinator = remember { SwipeRevealCoordinator() }
    Box(
        modifier
            .onGloballyPositioned { coordinator.host = it }
            .pointerInput(coordinator) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (coordinator.openKey == null) return@awaitEachGesture
                    val inside = coordinator.openBounds()?.contains(down.position) == true
                    val slop = viewConfiguration.touchSlop
                    var closed = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val moved = change.position - down.position
                        // A drag along the row is the row's own business; anything else means the user moved on.
                        val leftTheRow = if (inside) abs(moved.y) > slop && abs(moved.y) > abs(moved.x) else moved.getDistance() > slop
                        if (!closed && leftTheRow) {
                            closed = true
                            coordinator.closeAll()
                        }
                        if (change.changedToUp()) {
                            if (!closed && !inside) {
                                change.consume()
                                coordinator.closeAll()
                            }
                            break
                        }
                        if (!change.pressed) break
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalSwipeRevealCoordinator provides coordinator, content = content)
    }
}

/**
 * A row that slides sideways to reveal [actions] behind it (Gmail-style). Dragging follows the finger one to
 * one (against the row's end edge, with the layout direction), lets go with a short ease to open or closed
 * depending on distance and fling, and never fights vertical scrolling. Every action must also be reachable
 * some other way - this is a shortcut, not the only path - and rows expose them as accessibility custom actions
 * at the call site.
 *
 * Put the rows inside a [SwipeRevealHost] to get "only one open at a time" and "tap elsewhere closes".
 * [key] identifies the row to the host; use something stable (a row id) so a recycled slot never inherits
 * the previous row's open state. With [enabled] false the row is just its [content].
 */
@Composable
fun SwipeRevealRow(
    actionsWidth: Dp,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    key: Any? = null,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier.clip(shape)) { content() }
        return
    }
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val coordinator = LocalSwipeRevealCoordinator.current
    val rowKey = remember(key) { key ?: Any() }
    val revealPx = with(density) { actionsWidth.toPx() }
    val flingPx = with(density) { OPEN_FLING_VELOCITY.toPx() }
    // How far the row has been pulled open, 0..revealPx, whichever way "open" points.
    var revealed by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableIntStateOf(0) }
    var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val sign = if (rtl) 1f else -1f

    fun settleTo(target: Float, velocity: Float = 0f) {
        settling?.cancel()
        settling = scope.launch {
            animate(
                initialValue = revealed,
                targetValue = target,
                initialVelocity = velocity,
                animationSpec = tween(if (reduced) 0 else AppMotion.SHORT_MS, easing = AppMotion.standard),
            ) { value, _ -> revealed = value }
        }
        if (target == 0f) coordinator?.closed(rowKey) else coordinator?.opened(rowKey, rowCoordinates)
    }

    // Another row opened, or the host closed everything: follow it.
    val isCurrent = coordinator?.openKey == rowKey
    LaunchedEffect(isCurrent) {
        if (coordinator != null && !isCurrent && revealed != 0f) settleTo(0f)
    }
    DisposableEffect(rowKey) { onDispose { coordinator?.closed(rowKey) } }

    val draggable = rememberDraggableState { delta ->
        revealed = (revealed + delta * sign).coerceIn(0f, revealPx)
    }
    val close: () -> Unit = { settleTo(0f) }

    // The whole row - actions included - is what counts as "inside" for the host's outside-press check.
    Box(modifier.clip(shape).onGloballyPositioned { rowCoordinates = it }) {
        if (revealed > 0.5f) {
            // The actions sit at the end, behind the sliding content. They are only composed while visible, so
            // they cannot be tapped or focused by TalkBack while hidden behind the row.
            Row(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(actionsWidth)
                    .then(if (height > 0) Modifier.height(with(density) { height.toDp() }) else Modifier.fillMaxHeight()),
            ) {
                CompositionLocalProvider(LocalSwipeRowClose provides close) { actions() }
            }
        }
        Box(
            Modifier
                .offset { IntOffset((revealed * sign).roundToInt(), 0) }
                .onSizeChanged { height = it.height }
                .graphicsLayer {
                    clip = true
                    this.shape = OpeningRowShape(shape, revealed / revealPx)
                }
                .draggable(
                    state = draggable,
                    orientation = Orientation.Horizontal,
                    onDragStarted = {
                        settling?.cancel()
                        // Starting to pull this row closes whichever other row is open.
                        if (coordinator?.openKey != rowKey) coordinator?.closeAll()
                    },
                    onDragStopped = { velocity ->
                        val towardOpen = velocity * sign
                        val open = when {
                            towardOpen > flingPx -> true
                            towardOpen < -flingPx -> false
                            else -> revealed > revealPx * OPEN_FRACTION
                        }
                        settleTo(if (open) revealPx else 0f, velocity = 0f)
                    },
                ),
        ) {
            content()
            if (revealed > 0.5f) {
                // While open, a tap on the row closes it instead of activating it.
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures { close() } },
                )
            }
        }
    }
}

/**
 * One tile of [SwipeRevealRow]'s action strip: icon over a short label (never color alone), filling its share
 * of the strip. Runs [onClick] and closes the row. The label doubles as its accessibility name.
 */
@Composable
fun RowScope.SwipeActionButton(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val close = LocalSwipeRowClose.current
    Column(
        modifier
            .weight(1f)
            .fillMaxHeight()
            .background(container)
            .clickable(role = Role.Button) {
                close()
                onClick()
            }
            .padding(horizontal = AppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(AppIconSizes.lg))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Width of one action tile; wide enough for a 48dp target and a short label at default font size. */
val SwipeActionWidth = 76.dp

/**
 * The row's own [base] shape, with its trailing corners squared off as it opens ([open] is 0 shut, 1 fully open): shut it
 * is exactly the segment it sits in; open, its edge meets the flat action tiles flush. A rounded corner there would leave
 * a notch of background between its curve and the tile's square corner.
 */
private class OpeningRowShape(private val base: Shape, private val open: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val progress = open.coerceIn(0f, 1f)
        if (progress == 0f) return base.createOutline(size, layoutDirection, density)
        val corners = base as? CornerBasedShape
        fun radius(corner: CornerSize?) = corner?.toPx(size, density) ?: 0f
        fun toEnd(from: Float) = from * (1f - progress)
        val topStart = radius(corners?.topStart)
        val bottomStart = radius(corners?.bottomStart)
        val topEnd = toEnd(radius(corners?.topEnd))
        val bottomEnd = toEnd(radius(corners?.bottomEnd))
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                topLeftCornerRadius = CornerRadius(if (ltr) topStart else topEnd),
                topRightCornerRadius = CornerRadius(if (ltr) topEnd else topStart),
                bottomRightCornerRadius = CornerRadius(if (ltr) bottomEnd else bottomStart),
                bottomLeftCornerRadius = CornerRadius(if (ltr) bottomStart else bottomEnd),
            ),
        )
    }
}

/** Opens when released past this fraction of the actions' width: a short, deliberate pull. */
private const val OPEN_FRACTION = 0.35f
private val OPEN_FLING_VELOCITY = 400.dp
