package com.example.normpix.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** The name the splash writes out; "Norm" in the text colour and "Pix" in the accent, as two words run together. */
private const val NAME = "NormPix"
private const val ACCENT_FROM = 4

/**
 * The line under the name, written a word after a word like the name is a letter after a letter. The dot that parts a word
 * from the one before it comes with it, so a word never turns up without it.
 */
private val TAGLINE = listOf("Finance", "· Cards", "· Reminders")

/** A letter starts this long after the one before it. */
internal const val SPLASH_LETTER_STAGGER_MS = 65L

/** How long one letter takes to rise into place. */
internal const val SPLASH_LETTER_MS = 420L

/** The first word of the line under the name starts this long after the last letter has, while the last letters still settle. */
private const val SPLASH_TAGLINE_LAG_MS = 110L

/** A word of the line under the name starts this long after the one before it. */
internal const val SPLASH_WORD_STAGGER_MS = 90L

/** How long one word takes to come in. */
internal const val SPLASH_WORD_MS = 340L

/** How long the whole name stays up, written, before the splash goes. */
internal const val SPLASH_HOLD_MS = 240L

/** How long the splash takes to fade into the app. */
internal const val SPLASH_EXIT_MS = 360

/** The longest the splash waits, once written, for the data to load: past it, the app's own loading screen says so. */
private const val SPLASH_MAX_WAIT_MS = 2_500L

/** How far the app recedes under the splash: it comes forward to full size as the splash fades, like a window opening. */
internal const val SPLASH_APP_SCALE = 0.94f

/** When letter [index] starts coming in, in milliseconds from the start of the writing. */
internal fun splashLetterStart(index: Int): Long = index * SPLASH_LETTER_STAGGER_MS

/** When word [index] of the line under the name starts coming in, in milliseconds from the start of the writing. */
internal fun splashWordStart(index: Int): Long =
    splashLetterStart(NAME.length - 1) + SPLASH_TAGLINE_LAG_MS + index * SPLASH_WORD_STAGGER_MS

/** How long the writing takes in all: until the last word is in place (or the last letter, if that is later). */
internal val SPLASH_WRITE_MS: Long =
    maxOf(splashLetterStart(NAME.length - 1) + SPLASH_LETTER_MS, splashWordStart(TAGLINE.size - 1) + SPLASH_WORD_MS)

/**
 * How far in, from 0 to 1, something is [elapsedMs] into the writing when it starts at [startMs] and takes [durationMs]:
 * nothing before it starts, all of it after, and in between eased by the emphasized-decelerate curve - fast at first, then
 * settling - which never goes past 1, so nothing overshoots and comes back. Every letter and word reads it off the same clock.
 */
internal fun splashProgress(elapsedMs: Float, startMs: Long, durationMs: Long): Float {
    val linear = ((elapsedMs - startMs) / durationMs).coerceIn(0f, 1f)
    return AppMotion.enter.transform(linear).coerceIn(0f, 1f)
}

/**
 * What a cold start shows after Android's own launch screen, as many apps do: the app's name written out letter by letter -
 * each rising into place, a beat after the one before - then the line under it, a word at a time, and a moment later it
 * fades away into the app ([leaving] runs 0 -> 1 as it does, for the app to come forward under it).
 *
 * The writing runs on one clock, so it keeps its pace and its order whatever the frames do: a frame that comes late shows
 * the letters where they should be by then, never all of them at once. It starts only once the splash has been drawn, twice
 * over, so the busy first frames of a cold start don't take its beginning. [onWritten] is called when it is all in place,
 * which is when the app below may do its heavy work: not while the letters are moving. With animations removed it is all
 * there at once.
 *
 * It holds (up to [SPLASH_MAX_WAIT_MS]) until [ready] - the data has loaded - so it opens into the app rather than onto a
 * loading screen, and calls [onFinished] once it has gone. Nothing under it can be touched, and a screen reader hears the
 * name once.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LaunchSplash(
    ready: Boolean,
    leaving: Animatable<Float, AnimationVector1D>,
    onFinished: () -> Unit,
    onWritten: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    // The one clock of the writing: milliseconds since it began. Each letter and word reads its own progress off it as it draws.
    val clock = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    val finished by rememberUpdatedState(onFinished)
    val written by rememberUpdatedState(onWritten)
    val reducedMotion = rememberReducedMotion()
    val exitMs = AppMotion.duration(SPLASH_EXIT_MS)

    LaunchedEffect(Unit) {
        if (reducedMotion) {
            clock.snapTo(SPLASH_WRITE_MS.toFloat())
        } else {
            // Not until the splash has been drawn - twice, since the first frame of a cold start is the busiest - or
            // the start of the writing would be spent on work the user can't see, and the first letters would come in late
            // and together.
            withFrameNanos { }
            withFrameNanos { }
            clock.animateTo(SPLASH_WRITE_MS.toFloat(), tween(SPLASH_WRITE_MS.toInt(), easing = LinearEasing))
        }
        written()
        delay(SPLASH_HOLD_MS)
        withTimeoutOrNull(SPLASH_MAX_WAIT_MS) { snapshotFlow { isReady }.first { it } }
        leaving.animateTo(1f, tween(exitMs, easing = AppMotion.standard))
        finished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - leaving.value }
            .background(scheme.surface)
            // Nothing under the splash is reachable while it is up.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .clearAndSetSemantics { contentDescription = NAME },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // The name grows a touch as it fades, as if the app came out of it.
            modifier = Modifier.graphicsLayer {
                val scale = 1f + 0.08f * leaving.value
                scaleX = scale
                scaleY = scale
            },
        ) {
            Row {
                NAME.forEachIndexed { i, letter ->
                    Text(
                        letter.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (i < ACCENT_FROM) scheme.onSurface else scheme.primary,
                        modifier = Modifier.graphicsLayer {
                            val t = splashProgress(clock.value, splashLetterStart(i), SPLASH_LETTER_MS)
                            alpha = t
                            translationY = (1f - t) * 28.dp.toPx()
                            val scale = 0.6f + 0.4f * t
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        },
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.sm))
            // A row that wraps, as the line did when it was one text: with very large text it goes onto two lines.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs, Alignment.CenterHorizontally)) {
                TAGLINE.forEachIndexed { i, word ->
                    Text(
                        word,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer {
                            val t = splashProgress(clock.value, splashWordStart(i), SPLASH_WORD_MS)
                            alpha = t
                            translationY = (1f - t) * 8.dp.toPx()
                        },
                    )
                }
            }
        }
    }
}
