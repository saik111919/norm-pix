package com.example.normpix.ui.components

import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A slide-to-confirm track used instead of a plain Save button for irreversible or high-intent
 * actions like adding a card or an account. Dragging the knob past 85% of the travel springs to the
 * end, plays a success haptic, and calls [onConfirm]. Releasing earlier springs back to the start.
 *
 * For accessibility (TalkBack / keyboard), the whole control is exposed as a single Button node
 * whose onClick action confirms without requiring a drag. Plain finger taps do not confirm.
 */
@Composable
fun SlideToConfirm(
    label: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val spatialSpec = AppMotion.spatial<Float>()

    val knobSize = 52.dp
    val knobInset = 6.dp
    val knobSizePx = with(density) { knobSize.toPx() }
    val knobInsetPx = with(density) { knobInset.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        val widthPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else with(density) { 360.dp.toPx() }
        val maxTravelPx = (widthPx - 2 * knobInsetPx - knobSizePx).coerceAtLeast(0f)

        var travel by remember { mutableFloatStateOf(0f) }
        var hasConfirmed by remember { mutableStateOf(false) }
        var settling by remember { mutableStateOf<Job?>(null) }

        LaunchedEffect(busy, maxTravelPx) {
            if (busy) {
                settling?.cancel()
                if (maxTravelPx > 0f && travel < maxTravelPx) {
                    Animatable(travel).animateTo(maxTravelPx, spatialSpec) { travel = value }
                }
            } else if (travel > 0f) {
                hasConfirmed = false
                settling?.cancel()
                Animatable(travel).animateTo(0f, spatialSpec) { travel = value }
            }
        }

        val stillBusy by rememberUpdatedState(busy)

        // Confirmed: to the end, the haptic, and the action. If no save follows (a form that refuses - a missing field says
        // why), the knob comes back after a moment so it can be slid again; a save that starts keeps it at the end (busy).
        fun confirm() {
            hasConfirmed = true
            haptics.success()
            settling?.cancel()
            settling = scope.launch {
                Animatable(travel).animateTo(maxTravelPx, spatialSpec) { travel = value }
                delay(NO_SAVE_RETURN_MS)
                if (!stillBusy) {
                    hasConfirmed = false
                    Animatable(travel).animateTo(0f, spatialSpec) { travel = value }
                }
            }
            onConfirm()
        }

        val draggableState = rememberDraggableState { delta ->
            val travelDelta = if (isRtl) -delta else delta
            travel = (travel + travelDelta).coerceIn(0f, maxTravelPx)
        }

        val progress = if (maxTravelPx > 0f) (travel / maxTravelPx).coerceIn(0f, 1f) else 0f
        val labelAlpha = if (!enabled || busy) 0.38f else (1f - progress * 1.5f).coerceIn(0f, 1f)
        val chevronAlpha = if (!enabled || busy) 0.19f else 0.5f
        val chevronIcon = if (isRtl) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight
        val chevronTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = chevronAlpha)

        val travelledPx = knobInsetPx + travel + knobSizePx / 2f
        val travelledWidth = with(density) { travelledPx.toDp() }

        // From the start edge: offset and the alignments below follow the layout direction, so right-to-left needs nothing more.
        val knobX = knobInsetPx + travel
        val knobY = knobInsetPx

        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clearAndSetSemantics {
                    role = Role.Button
                    contentDescription = label
                    if (enabled && !busy) {
                        onClick {
                            if (!hasConfirmed) confirm()
                            true
                        }
                    } else {
                        disabled()
                    }
                }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled && !busy && maxTravelPx > 0f,
                    onDragStarted = {
                        settling?.cancel()
                    },
                    onDragStopped = {
                        val pastThreshold = maxTravelPx > 0f && travel >= maxTravelPx * 0.85f
                        if (pastThreshold) {
                            if (!hasConfirmed) confirm()
                        } else {
                            settling = scope.launch {
                                Animatable(travel).animateTo(0f, spatialSpec) { travel = value }
                            }
                        }
                    },
                ),
        ) {
            // Low-alpha primary fill tracking behind the knob
            val fillAlignment = Alignment.CenterStart
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(travelledWidth)
                    .align(fillAlignment)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (!enabled || busy) 0.08f else 0.16f)),
            )

            // Centered label that fades as the knob approaches
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = labelAlpha),
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
            )

            // Faint chevrons at the end indicating direction of travel
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy((-8).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = chevronIcon,
                    contentDescription = null,
                    tint = chevronTint,
                    modifier = Modifier.size(22.dp),
                )
                Icon(
                    imageVector = chevronIcon,
                    contentDescription = null,
                    tint = chevronTint,
                    modifier = Modifier.size(22.dp),
                )
            }

            // Draggable knob
            Box(
                modifier = Modifier
                    .offset { IntOffset(knobX.roundToInt(), knobY.roundToInt()) }
                    .size(knobSize)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** How long a confirmed slide waits for its save to start before it comes back (a form that refused to save). */
private const val NO_SAVE_RETURN_MS = 400L
