package com.example.normpix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * Whether motion that never ends may run: the hero's weather, which draws every frame for as long as it is on screen. Only the
 * real app's activity (`MainActivity`) says yes. Everywhere else - every test, every preview, a page rendered on its own - it
 * stays still, because a test waits for the screen to settle and an endless animation never lets it.
 */
val LocalLiveMotion = staticCompositionLocalOf { false }

/** How long the clock runs before it starts again from naught, in seconds: floats lose the fractions of a second after hours. */
private const val CLOCK_WRAP_S = 3_600f

/**
 * Seconds that pass while [running], [fps] times a second - read it only where it is drawn (a draw lambda, a graphics layer), so a
 * tick redraws and recomposes nothing. It holds still while not running and goes on from there, so a scene paused when its
 * screen goes away is where it was when the screen comes back.
 *
 * Not every frame: a tick redraws what reads it and, on Home, the frosted dock's copy of the page under it, about 10 ms of GPU on
 * the owner's phone - at 120 frames a second that is the whole GPU (measured 2026-11-01). Ambient motion reads as smooth at 30.
 */
@Composable
fun rememberLiveClock(running: Boolean, fps: Int = LIVE_FPS): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, fps) {
        if (!running) return@LaunchedEffect
        val from = seconds.floatValue
        val start = withFrameNanos { it }
        val wait = (1_000L / fps - FRAME_SLACK_MS).coerceAtLeast(0L)
        while (true) {
            delay(wait) // the frames in between go by untouched
            val now = withFrameNanos { it }
            seconds.floatValue = (from + (now - start) / 1_000_000_000f) % CLOCK_WRAP_S
        }
    }
    return seconds
}

/** How often ambient motion moves, a second. */
const val LIVE_FPS = 30

/** Woken this much before the tick is due, so the frame it lands on is the one wanted, not the one after. */
private const val FRAME_SLACK_MS = 4L
