package com.example.normpix.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import kotlin.math.abs
import kotlinx.coroutines.launch

/** How many items show behind the front one; any more wait out of sight behind those. */
private const val BEHIND = 2

/** How much of each item behind shows above the one in front of it. */
private val Peek = 14.dp

/** Each step back is this much smaller, and this much darker. */
private const val STEP_SCALE = 0.06f
private const val STEP_DIM = 0.2f

/** A swipe counts once the front item is dragged this far across (as a share of its width), or flung faster than [FLING_SPEED]. */
private const val SWIPE_SHARE = 0.25f
private const val FLING_SPEED = 1500f

/** How far, in degrees, an item leans when dragged all the way across its own width. */
private const val DRAG_LEAN = 8f

/**
 * A deck of same-sized items as a stack: the front one shown in full by [face], the others a hair of colour above it, each
 * a little smaller, drawn by [plate]. Tapping the front item calls [onOpen]; swiping it sends it to the back (or, the other
 * way, brings the last one forward), and tapping an item behind brings that one forward.
 *
 * Items size themselves (the stack lays them all over one another), so [face] and [plate] of one item must be the same size.
 * Only the front item is something to read or press for a screen reader: it is spoken as [description] (item, its place in
 * [items], how many there are), with "show next" and "show previous" as actions.
 *
 * Pressing and holding the front item lays every item out in a column - each a full item, to read or open - with a "Stack"
 * button above them that gathers them into the stack again. A caller with a long press of its own ([onLongPress], called with the
 * front item and offered to a screen reader as [longPressLabel]) keeps it, and the stack does not unfold.
 *
 * An item with buttons of its own passes them as [extraActions]: its inner buttons are hidden from a screen reader (the item is
 * one thing to read), so the actions are offered on the item instead.
 *
 * @param shape the outline of an item, so the darkening of the ones behind follows its corners.
 */
@Composable
fun <T> DeckStack(
    items: List<T>,
    itemKey: (T) -> Any,
    shape: Shape,
    description: (item: T, index: Int, count: Int) -> String,
    onOpen: (T) -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 440.dp,
    onLongPress: ((T) -> Unit)? = null,
    longPressLabel: String = "Shows more options",
    extraActions: (T) -> List<CustomAccessibilityAction> = { emptyList() },
    plate: @Composable (T) -> Unit,
    face: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    val haptics = rememberAppHaptics()
    var frontKey by remember { mutableStateOf<Any?>(null) }
    var unfolded by remember { mutableStateOf(false) }
    val count = items.size
    val unfolds = onLongPress == null && count > 1
    val holdLabel = if (unfolds) "Shows all of them" else longPressLabel
    val hold: ((T) -> Unit)? = onLongPress ?: if (unfolds) ({ _: T ->
        haptics.strong()
        unfolded = true
    }) else null
    // Until one is brought forward - or once the one in front is gone - the first item leads.
    val front = items.indexOfFirst { itemKey(it) == frontKey }.coerceAtLeast(0)

    fun bringForward(index: Int) {
        val next = index.mod(count)
        if (next == front) return
        haptics.selection()
        frontKey = itemKey(items[next])
    }

    val arrive = AppMotion.duration(AppMotion.MEDIUM_MS)
    val leave = AppMotion.duration(AppMotion.SHORT_MS)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            targetState = unfolded && unfolds,
            transitionSpec = {
                (fadeIn(tween(arrive, easing = AppMotion.enter)) togetherWith
                    fadeOut(tween(leave, easing = AppMotion.exit))) using SizeTransform(clip = false)
            },
            label = "deck_unfolded",
        ) { open ->
            if (open) {
                Column(Modifier.widthIn(max = maxWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    StackButton(
                        onClick = {
                            haptics.selection()
                            unfolded = false
                        },
                        modifier = Modifier.align(Alignment.End),
                    )
                    items.forEachIndexed { index, item ->
                        key(itemKey(item)) {
                            val spoken = description(item, index, count)
                            val extra = extraActions(item)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(onClickLabel = "Opens the details") { onOpen(item) }
                                    .clearAndSetSemantics {
                                        contentDescription = spoken
                                        role = Role.Button
                                        onClick(label = "Opens the details") { onOpen(item); true }
                                        if (extra.isNotEmpty()) customActions = extra
                                    },
                            ) { face(item) }
                        }
                    }
                }
            } else {
                Box(Modifier.widthIn(max = maxWidth).fillMaxWidth().padding(top = Peek * minOf(count - 1, BEHIND))) {
                    // Always in the items' own order: an item keeps its place in the tree and only its depth changes, so each
                    // moves from where it was to where it goes.
                    items.forEachIndexed { index, item ->
                        key(itemKey(item)) {
                            DeckItem(
                                position = (index - front).mod(count),
                                count = count,
                                shape = shape,
                                spoken = description(item, index, count),
                                onOpen = { onOpen(item) },
                                onLongPress = hold?.let { press -> { press(item) } },
                                longPressLabel = holdLabel,
                                extra = extraActions(item),
                                onBringForward = { bringForward(index) },
                                onNext = { bringForward(front + 1) },
                                onPrevious = { bringForward(front - 1) },
                                plate = { plate(item) },
                                face = { face(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Stack": gathers an unfolded deck back into a stack. */
@Composable
private fun StackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "Stack them again" },
        shape = CircleShape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(start = AppSpacing.md, end = AppSpacing.lg, top = AppSpacing.sm, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.sm))
            Text("Stack", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckItem(
    /** 0 for the item in front, 1 for the one behind it, and so on. */
    position: Int,
    count: Int,
    shape: Shape,
    spoken: String,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    longPressLabel: String,
    extra: List<CustomAccessibilityAction>,
    onBringForward: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    plate: @Composable () -> Unit,
    face: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settle = AppMotion.spatial<Float>()
    // How far back the item is, as a number that moves: every reshuffle is this one value easing to its new place.
    val depth by animateFloatAsState(position.coerceAtMost(BEHIND + 1).toFloat(), settle, label = "deck_depth")
    val drag = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val peekPx = with(LocalDensity.current) { Peek.toPx() }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed && position == 0) 0.98f else 1f, settle, label = "deck_press")
    // Printed while at or near the front; an item on its way back turns to a plate before any of its print could show.
    val printed by remember { derivedStateOf { depth < 0.5f } }
    val isFront = position == 0

    // The outer layer places the item in the stack, from its top edge so a smaller item keeps its top where it is shown;
    // the inner one is the item following a finger, which turns about its own middle.
    Box(
        Modifier
            .zIndex(-depth)
            .fillMaxWidth()
            .graphicsLayer {
                val back = minOf(depth, BEHIND.toFloat())
                val scale = 1f - STEP_SCALE * back
                scaleX = scale
                scaleY = scale
                translationY = -peekPx * back
                alpha = 1f - (depth - BEHIND).coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            .drawWithContent {
                drawContent()
                val dim = STEP_DIM * minOf(depth, BEHIND.toFloat())
                if (dim > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), Color.Black, alpha = dim)
            },
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = drag.value
                    rotationZ = if (width > 0) drag.value / width * DRAG_LEAN else 0f
                    scaleX = pressScale
                    scaleY = pressScale
                }
                .onSizeChanged { width = it.width }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = isFront && count > 1,
                    state = rememberDraggableState { delta -> scope.launch { drag.snapTo(drag.value + delta) } },
                    onDragStopped = { velocity ->
                        val moved = drag.value
                        val far = abs(moved) > width * SWIPE_SHARE || abs(velocity) > FLING_SPEED
                        val toward = if (abs(moved) > 1f) moved else velocity
                        if (far && toward != 0f) {
                            if (toward < 0f) onNext() else onPrevious()
                        }
                        // Not in the drag's own scope: once the item is no longer in front the drag is gone, and the item
                        // must still come home.
                        scope.launch { drag.animateTo(0f, settle) }
                    },
                )
                .combinedClickable(
                    interactionSource = press,
                    indication = null,
                    enabled = position <= BEHIND,
                    onClickLabel = if (isFront) "Opens the details" else "Brings this one to the front",
                    onLongClickLabel = if (isFront && onLongPress != null) longPressLabel else null,
                    onLongClick = if (isFront) onLongPress else null,
                    onClick = if (isFront) onOpen else onBringForward,
                )
                .clearAndSetSemantics {
                    // Only the item in front is something to read or press; the others are the stack's depth.
                    if (isFront) {
                        contentDescription = spoken
                        role = Role.Button
                        onClick(label = "Opens the details") { onOpen(); true }
                        if (onLongPress != null) onLongClick(label = longPressLabel) { onLongPress(); true }
                        val actions = if (count > 1) {
                            listOf(
                                CustomAccessibilityAction("Show next") { onNext(); true },
                                CustomAccessibilityAction("Show previous") { onPrevious(); true },
                            ) + extra
                        } else {
                            extra
                        }
                        if (actions.isNotEmpty()) customActions = actions
                    }
                },
        ) {
            if (printed) face() else plate()
        }
    }
}
