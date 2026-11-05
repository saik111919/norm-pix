package com.example.normpix.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How far a card sinks under a finger (a touch is a press) and how far it rises once the hold counts as a long press. */
internal const val PRESS_SINK = 0.98f
internal const val HOLD_LIFT = 1.02f

/** How long to wait for the sheet a hold opens before deciding it opens none. */
private const val LATCH_WAIT_MS = 1_500L

/** The ring that marks a held card, inside its edge. Thick enough to see on any card colour, as the keyboard focus ring is. */
private val HoldRingWidth = 3.dp

/**
 * The one way a card answers a finger, with no shadow under it:
 *
 *  - **pressed**: it sinks a little (a touch is a press, and a scroll that starts on it lets go again);
 *  - **held** for as long as a long press takes: it rises a little and a ring in the theme's primary colour is drawn along its
 *    own edge (inside it, following its corners) - the card is the one the hold is about, which is what the sheet or the spread
 *    that follows acts on. The ring goes when the finger does - or, where the hold opened a sheet or a decision
 *    ([LocalOverlaysOrNull]), when that has closed again, so the card stays marked as the one the sheet is about.
 *
 * Give it the same [interactionSource] the card's click uses and its [shape]. [enabled] false (a card behind another one) draws
 * nothing; [longPress] false (nothing happens on a hold) leaves only the sink. Motion is instant under reduced motion; the ring
 * is a shape, not a colour alone, so it reads the same without colour.
 */
@Composable
fun Modifier.holdFocus(
    interactionSource: InteractionSource,
    shape: Shape,
    enabled: Boolean = true,
    longPress: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val overlays = LocalOverlaysOrNull.current
    var held by remember { mutableStateOf(false) }
    // The hold's sheet (a card's actions, say) opens as the finger is still down and the finger lifts before long: so the card
    // that was held stays marked until that sheet has gone again, and the person can see which card the sheet is about.
    var latched by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, enabled, longPress) {
        held = false
        if (pressed && enabled && longPress) {
            delay(timeout)
            held = true
            if (overlays != null) latched = true
        }
    }
    LaunchedEffect(latched) {
        if (!latched || overlays == null) return@LaunchedEffect
        // Wait for the sheet to appear (a hold that opens none - a stack spreading out - lets go after a moment), then for it to go.
        val opened = withTimeoutOrNull(LATCH_WAIT_MS) { snapshotFlow { overlays.isOpen }.first { it } } != null
        if (opened) snapshotFlow { overlays.isOpen }.first { !it }
        latched = false
    }
    val marked = held || latched
    val spec = AppMotion.spatial<Float>()
    val scale by animateFloatAsState(
        when {
            !enabled -> 1f
            marked -> HOLD_LIFT
            pressed -> PRESS_SINK
            else -> 1f
        },
        spec,
        label = "hold_scale",
    )
    val ring by animateFloatAsState(if (marked && enabled) 1f else 0f, spec, label = "hold_ring")
    val color = MaterialTheme.colorScheme.primary
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(if (ring > 0.01f) Modifier.border(HoldRingWidth, color.copy(alpha = ring.coerceIn(0f, 1f)), shape) else Modifier)
}
