package com.example.normpix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What one side of a [SwipeActionRow] does: how it looks behind the row ([container] with [onContainer] on it while the swipe is
 * short, [armed] with [onArmed] once letting go would do it), and what happens then.
 */
@Immutable
class SwipeAction(
    val icon: ImageVector,
    val label: String,
    val container: Color,
    val onContainer: Color,
    val armed: Color,
    val onArmed: Color,
    /** The row goes off the edge and out of the list (a delete), rather than coming back (an edit). */
    val leaves: Boolean,
    val onTrigger: () -> Unit,
)

/**
 * A row that is swiped the way Gmail's are: one action to each side, each with its own colour and icon behind the row, and no
 * actions to tap afterwards. The row follows the finger; past a deliberate distance (or a fast flick) the colour firms up, the icon
 * pops and the phone ticks, so it is known what letting go will do. Letting go short of that springs the row back. Past it, an
 * action that [SwipeAction.leaves] sends the row the rest of the way off the edge and then folds its height away, so the rows
 * under it close up; any other action runs and the row springs back.
 *
 * [startAction] is done by pulling the row toward the end edge (to the right, with the layout direction left-to-right) and
 * [endAction] by pulling it toward the start edge; a side with no action does not move. With neither, the row is just its
 * [content]. A swipe is only ever a shortcut: whatever it does must be reachable another way, and the call site puts both
 * actions on the row as accessibility custom actions.
 *
 * [key] identifies the row, so a slot that comes to show another row does not keep the swipe state of the one before it.
 */
@Composable
fun SwipeActionRow(
    startAction: SwipeAction?,
    endAction: SwipeAction?,
    modifier: Modifier = Modifier,
    key: Any? = null,
    shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    if (startAction == null && endAction == null) {
        Box(modifier.clip(shape)) { content() }
        return
    }
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val density = LocalDensity.current
    val haptics = rememberAppHaptics()
    // Pulling toward the end edge is +x with the text running left to right and -x against it.
    val sign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val armMaxPx = with(density) { ARM_DISTANCE_MAX.toPx() }
    val flingPx = with(density) { FLING_VELOCITY.toPx() }
    val start by rememberUpdatedState(startAction)
    val end by rememberUpdatedState(endAction)

    var width by remember(key) { mutableFloatStateOf(0f) }
    /** How far the row is pulled: positive toward the end edge (showing the start action), negative toward the start edge. */
    var pulled by remember(key) { mutableFloatStateOf(0f) }
    /** The share of its height the row still takes, 1 to 0 as it folds away after leaving. */
    var height by remember(key) { mutableFloatStateOf(1f) }
    /** The row has been sent off and is folding away; it takes no more swipes. */
    var gone by remember(key) { mutableStateOf(false) }
    var dragging by remember(key) { mutableStateOf(false) }
    var settling by remember(key) { mutableStateOf<Job?>(null) }

    fun armDistance() = min(width * ARM_FRACTION, armMaxPx)
    val armed by remember(key) { derivedStateOf { armDistance().let { it > 0f && abs(pulled) >= it } } }
    val side by remember(key) { derivedStateOf { if (pulled > 0f) 1 else if (pulled < 0f) -1 else 0 } }

    val back: AnimationSpec<Float> = if (reduced) snap() else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

    fun animatePulled(target: Float, spec: AnimationSpec<Float>, velocity: Float = 0f, then: suspend () -> Unit = {}) {
        settling?.cancel()
        settling = scope.launch {
            animate(pulled, target, velocity, spec) { value, _ -> pulled = value }
            then()
        }
    }

    fun foldAway() = scope.launch {
        animate(height, 0f, 0f, if (reduced) snap() else tween(FOLD_MS, easing = AppMotion.standard)) { value, _ -> height = value }
    }

    fun sendOff(toward: Float, action: SwipeAction) {
        gone = true
        animatePulled(toward * width, if (reduced) snap() else tween(LEAVE_MS, easing = AppMotion.exit)) {
            action.onTrigger()
            foldAway()
        }
    }

    // The tick as the pull reaches the point where letting go does it (and not on the way back).
    LaunchedEffect(armed) {
        if (armed && dragging) haptics.selection()
    }
    // Whatever the swipe asked for should have taken the row out of the list by now; if it didn't (a change that failed), the row
    // comes back rather than leaving an empty strip where it was.
    LaunchedEffect(gone) {
        if (gone) {
            delay(COMES_BACK_AFTER_MS)
            gone = false
            scope.launch { animate(height, 1f, 0f, if (reduced) snap() else tween(FOLD_MS, easing = AppMotion.standard)) { v, _ -> height = v } }
            animatePulled(0f, back)
        }
    }

    val dragState = rememberDraggableState { delta ->
        val lowest = if (end != null) -width else 0f
        val highest = if (start != null) width else 0f
        pulled = (pulled + delta * sign).coerceIn(lowest, highest)
    }

    Box(
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * height).roundToInt()) { placeable.placeRelative(0, 0) }
            }
            .clip(shape)
            .onSizeChanged { width = it.width.toFloat() },
    ) {
        // Only while the row is off its place: shut, the row covers it, and its edge must not show through the corners.
        if (side != 0) {
            val action = if (side > 0) start else end
            if (action != null) {
                SwipeBackdrop(
                    action = action,
                    towardEnd = side > 0,
                    armed = armed,
                    revealed = { abs(pulled) },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset((pulled * sign).roundToInt(), 0) }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = !gone,
                    onDragStarted = {
                        settling?.cancel()
                        dragging = true
                    },
                    onDragStopped = { velocity ->
                        dragging = false
                        val direction = if (pulled >= 0f) 1f else -1f
                        val action = if (pulled == 0f) null else if (direction > 0f) start else end
                        // Speed toward the side being revealed: negative is the finger coming back.
                        val along = velocity * sign * direction
                        val distance = abs(pulled)
                        val far = distance >= armDistance() && along > -flingPx
                        val flicked = along > flingPx && distance > armDistance() * MIN_FLICK_FRACTION
                        when {
                            action != null && (far || flicked) && action.leaves -> sendOff(direction, action)
                            action != null && (far || flicked) -> {
                                action.onTrigger()
                                animatePulled(0f, back, velocity * sign)
                            }
                            else -> animatePulled(0f, back, velocity * sign)
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

/** What shows behind the row as it is pulled: [action]'s colour filling the strip the row has left, its icon and name at the edge. */
@Composable
private fun SwipeBackdrop(
    action: SwipeAction,
    towardEnd: Boolean,
    armed: Boolean,
    revealed: () -> Float,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(if (armed) action.armed else action.container, tween(COLOR_MS), label = "swipe background")
    val tint by animateColorAsState(if (armed) action.onArmed else action.onContainer, tween(COLOR_MS), label = "swipe icon")
    // The icon pops as the swipe arms and settles back if it is let go of; it never wobbles.
    val pop by animateFloatAsState(if (armed) POP_SCALE else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "swipe pop")
    Box(modifier.drawBehind { drawRect(background) }) {
        Column(
            Modifier
                .align(if (towardEnd) Alignment.CenterStart else Alignment.CenterEnd)
                .padding(horizontal = ICON_EDGE_PADDING)
                .graphicsLayer {
                    // Grows into view as the row leaves enough of the strip bare, rather than being cut by the row's edge.
                    val shown = ((revealed() - ICON_FADE_START.toPx()) / ICON_FADE_SPAN.toPx()).coerceIn(0f, 1f)
                    alpha = shown
                    val scale = (ICON_MIN_SCALE + (1f - ICON_MIN_SCALE) * shown) * pop
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(AppIconSizes.lg))
            Text(action.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
        }
    }
}

/** Armed once pulled this far across the row (or [ARM_DISTANCE_MAX], on a wide one), where Gmail's is about a third to a half. */
private const val ARM_FRACTION = 0.4f
private val ARM_DISTANCE_MAX = 160.dp

/** A flick faster than this does it from a shorter pull; one that is slower, or comes back, does not. */
private val FLING_VELOCITY = 300.dp
private const val MIN_FLICK_FRACTION = 0.2f

private const val LEAVE_MS = 200
private const val FOLD_MS = 220
private const val COLOR_MS = 120
private const val COMES_BACK_AFTER_MS = 3_000L

private const val POP_SCALE = 1.2f
private const val ICON_MIN_SCALE = 0.6f
private val ICON_EDGE_PADDING = 24.dp
private val ICON_FADE_START = 8.dp
private val ICON_FADE_SPAN = 40.dp
