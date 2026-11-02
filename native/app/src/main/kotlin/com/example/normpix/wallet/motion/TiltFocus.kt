package com.example.normpix.wallet.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs

/**
 * Tracks and coordinates card tilt focus across the screen.
 *
 * Ensures that only the single focused card (e.g. the front card in a stack/carousel,
 * or the card closest to the vertical center of the viewport in a scrolling list) receives
 * tilt rotation and sheen. All other cards remain flat.
 */
@Stable
class TiltFocusState {
    private val candidatePositions = mutableStateMapOf<Any, Float>()
    var rootCenterY by mutableFloatStateOf(0f)
        internal set

    /** The key of the card currently closest to the vertical center of the viewport. */
    val focusedKey: Any? by derivedStateOf {
        if (candidatePositions.isEmpty()) null
        else candidatePositions.minByOrNull { abs(it.value - rootCenterY) }?.key
    }

    fun reportPosition(key: Any, centerY: Float, rootHeight: Float) {
        candidatePositions[key] = centerY
        if (rootHeight > 0f) {
            rootCenterY = rootHeight / 2f
        }
    }

    fun removeCandidate(key: Any) {
        candidatePositions.remove(key)
    }

    fun isFocused(key: Any): Boolean {
        val focused = focusedKey
        return focused == null || focused == key
    }
}

/** Provides the ambient [TiltFocusState] down the composition tree. */
val LocalTiltFocus = compositionLocalOf<TiltFocusState?> { null }

@Composable
fun rememberTiltFocusState(): TiltFocusState = remember { TiltFocusState() }

/**
 * Registers this card as a candidate for tilt focus with its unique [key].
 * Only the candidate closest to the vertical center of the screen will receive tilt.
 */
@Composable
fun Modifier.tiltFocusCandidate(
    key: Any,
    enabled: Boolean = true,
): Modifier {
    val coordinator = LocalTiltFocus.current ?: return this
    if (!enabled) return this

    DisposableEffect(key, coordinator) {
        onDispose {
            coordinator.removeCandidate(key)
        }
    }

    return this.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val root = coordinates.findRootCoordinates()
            val bounds = coordinates.boundsInRoot()
            if (bounds.bottom > 0 && bounds.top < root.size.height) {
                coordinator.reportPosition(key, bounds.center.y, root.size.height.toFloat())
            } else {
                coordinator.removeCandidate(key)
            }
        }
    }
}

/**
 * Filters the [CardTiltState] so it is only non-null when this card with [key] has focus.
 * If no [LocalTiltFocus] is active, the original tilt state is returned untouched.
 */
@Composable
fun CardTiltState?.ifFocused(key: Any): CardTiltState? {
    if (this == null) return null
    val coordinator = LocalTiltFocus.current ?: return this
    return if (coordinator.isFocused(key)) this else null
}
