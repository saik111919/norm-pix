package com.example.normpix.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Decides whether a bar that floats over scrolling content (the navigation bar) should be out of the
 * way: it leaves while the user scrolls down - they are reading, not navigating - and returns on any
 * upward scroll. Whoever owns the scrolling list also forces it back at the top and bottom of the list
 * and when the list doesn't scroll at all (see `AppShell`).
 *
 * It only listens ([connection] never consumes anything), keeps its own state out of composition
 * until [hidden] actually flips, and counts scroll travel against a threshold, so a small wobble of
 * the finger never toggles it.
 */
@Stable
class ScrollAwareBarState(private val thresholdPx: Float) {
    /** True once the user has scrolled down far enough that the bar should leave. */
    var hidden by mutableStateOf(false)
        private set

    /** Signed scroll travel since the direction last changed: negative while scrolling down the list. */
    private var travel = 0f

    /** Bring the bar back regardless of scroll (a new screen, a new tab). */
    fun show() {
        hidden = false
        travel = 0f
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Flings carry on in the direction the finger already chose; only the finger decides.
            if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
            if (available.y * travel < 0f) travel = 0f
            travel += available.y
            if (travel <= -thresholdPx) {
                hidden = true
            } else if (travel >= thresholdPx) {
                hidden = false
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberScrollAwareBarState(threshold: Dp = 16.dp): ScrollAwareBarState {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    return remember(thresholdPx) { ScrollAwareBarState(thresholdPx) }
}
