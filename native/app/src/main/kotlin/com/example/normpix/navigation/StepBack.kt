package com.example.normpix.navigation

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A back gesture that takes a screen one step back inside itself - Banks & Categories to the side it opened on, a settings page on a
 * tablet up to the page it is a step inside of - as it is in Plans & Activity (owner, 2026-11-14: "check for similar patterns and
 * apply to them"). What [preview] says is the gesture's: how far ([TabBackPreview.progress], already eased by the look, [BackLook.eased]), from which
 * side, and where to ([TabBackPreview.target], an index the screen chose, or [TabBackPreview.NO_TAB] at rest). [handoff] is true for
 * the moment the finished gesture makes the step, so what was beneath - already on screen, whole - does not arrive again.
 */
@Stable
internal class StepBackState {
    val preview = TabBackPreview()
    var handoff by mutableStateOf(false)

    /** True from a finished gesture until its step has been made: a gesture started then is let go by. */
    internal var finishing = false

    /** The ease back to rest after a gesture given up, so a new gesture can stop it. */
    internal var settling: Job? = null
}

@Composable
internal fun rememberStepBack(): StepBackState = remember { StepBackState() }

/**
 * The back gesture for one step inside a screen, drawn as every back gesture in the app is ([BackGesture]): while [enabled], the finger
 * plays the start of the close of what is shown over what the step goes back to ([to], written to the preview as its target), letting go
 * plays the rest in what is left of the gesture's clock and then [onStep] makes the step, and a gesture given up eases back to rest.
 * The screen draws it with [StepBackLayers] (or reads [StepBackState.preview] itself).
 */
@Composable
internal fun StepBackHandler(state: StepBackState, enabled: Boolean, to: Int, onStep: () -> Unit) {
    val scope = rememberCoroutineScope()
    val look = rememberBackLook()
    val gestureMs = AppMotion.duration(LocalBackGestureMs.current)
    val settle = tween<Float>(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard)
    val step by rememberUpdatedState(onStep)
    val target by rememberUpdatedState(to)
    PredictiveBackHandler(enabled = enabled) { progress ->
        if (state.finishing) {
            progress.collect { }
            return@PredictiveBackHandler
        }
        state.settling?.cancel()
        val preview = state.preview
        preview.target = target
        var raw = 0f
        try {
            progress.collect { event ->
                preview.direction = BackGesture.directionOf(event.swipeEdge)
                raw = event.progress
                preview.progress = look.eased(raw)
            }
            state.finishing = true
            val from = raw
            // In the screen's scope, not the handler's: the step disables this handler, which must not cut the close short.
            scope.launch {
                try {
                    animate(from, 1f, animationSpec = tween(BackGesture.finishMs(from, gestureMs), easing = LinearEasing)) { value, _ ->
                        preview.progress = look.eased(value)
                    }
                    state.handoff = true
                    Snapshot.withMutableSnapshot {
                        step()
                        preview.target = TabBackPreview.NO_TAB
                        preview.progress = 0f
                    }
                    // Two frames: the one that draws the step, and the one after it.
                    withFrameNanos { }
                    withFrameNanos { }
                } finally {
                    state.handoff = false
                    state.finishing = false
                }
            }
        } catch (_: CancellationException) {
            // Given up: what is shown eases back to rest instead of jumping.
            state.settling = scope.launch {
                animate(preview.progress, 0f, animationSpec = settle) { value, _ -> preview.progress = value }
                preview.target = TabBackPreview.NO_TAB
            }
        }
    }
}

/**
 * What a screen with one step back inside it shows: [shown] - and, while the back gesture is on its way to another, that one ([state]'s
 * target) whole beneath it, as [BackLook.revealed] in the Back gesture style, with [shown] closing over it as [BackLook.leaving]
 * on the page's surface. Each is [content] of its own key, kept in a `key` of its own so the one beneath carries on as the one shown
 * once the step is made (its scroll, its search) instead of being made again. Nothing beneath with reduced motion: the gesture just goes back.
 */
@Composable
internal fun <T : Any> StepBackLayers(
    state: StepBackState,
    shown: T,
    of: (Int) -> T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val reduced = rememberReducedMotion()
    val look = rememberBackLook()
    val surface = MaterialTheme.colorScheme.surface
    val preview = state.preview.takeIf { !reduced && it.target != TabBackPreview.NO_TAB }
    val beneath = preview?.let { of(it.target) }?.takeIf { it != shown }
    // The gesture, only while there is something beneath to show.
    val gesture = preview?.takeIf { beneath != null }
    Box(modifier.fillMaxSize()) {
        for (item in listOfNotNull(beneath, shown)) {
            key(item) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (item == shown) 1f else 0f)
                        .then(
                            when {
                                gesture == null -> Modifier
                                item == shown -> Modifier
                                    .graphicsLayer { applyBackPose(look.leaving(gesture.progress, gesture.direction)) }
                                    .background(surface)
                                else -> Modifier
                                    .graphicsLayer { applyBackPose(look.revealed(gesture.progress, gesture.direction)) }
                                    .clearAndSetSemantics {}
                            },
                        ),
                ) { content(item) }
            }
        }
    }
}
