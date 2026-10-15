package com.example.normpix.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The name the splash writes out; "Norm" in the text colour and "Pix" in the accent, as two words run together. */
private const val NAME = "NormPix"
private const val ACCENT_FROM = 4

/** A letter starts this long after the one before it. */
internal const val SPLASH_LETTER_STAGGER_MS = 65L

/** How long the whole name stays up, written, before the splash goes. */
internal const val SPLASH_HOLD_MS = 240L

/** How long the splash takes to fade into the app. */
internal const val SPLASH_EXIT_MS = 360

/** The longest the splash waits, once written, for the data to load: past it, the app's own loading screen says so. */
private const val SPLASH_MAX_WAIT_MS = 2_500L

/** How far the app recedes under the splash: it comes forward to full size as the splash fades, like a window opening. */
internal const val SPLASH_APP_SCALE = 0.94f

/**
 * What a cold start shows after Android's own launch screen, as many apps do: the app's name written out letter by letter -
 * each rising into place and settling with a little spring, a beat after the one before - then the line under it, and a
 * moment later it fades away into the app ([leaving] runs 0 -> 1 as it does, for the app to come forward under it).
 *
 * It holds (up to [SPLASH_MAX_WAIT_MS]) until [ready] - the data has loaded - so it opens into the app rather than onto a
 * loading screen, and calls [onFinished] once it has gone. Nothing under it can be touched, and a screen reader hears the
 * name once.
 */
@Composable
internal fun LaunchSplash(ready: Boolean, leaving: Animatable<Float, AnimationVector1D>, onFinished: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val letters = remember { NAME.map { Animatable(0f) } }
    val tagline = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    val finished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        coroutineScope {
            letters.forEachIndexed { i, letter ->
                launch {
                    delay(i * SPLASH_LETTER_STAGGER_MS)
                    letter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
                }
            }
            launch {
                delay(NAME.length * SPLASH_LETTER_STAGGER_MS)
                tagline.animateTo(1f, tween(AppMotion.LONG_MS, easing = LinearOutSlowInEasing))
            }
        }
        delay(SPLASH_HOLD_MS)
        withTimeoutOrNull(SPLASH_MAX_WAIT_MS) { snapshotFlow { isReady }.first { it } }
        leaving.animateTo(1f, tween(SPLASH_EXIT_MS, easing = AppMotion.standard))
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
                            val t = letters[i].value
                            alpha = t.coerceIn(0f, 1f)
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
            Text(
                "Finance · Cards · Reminders",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer {
                    alpha = tagline.value
                    translationY = (1f - tagline.value) * 8.dp.toPx()
                },
            )
        }
    }
}
