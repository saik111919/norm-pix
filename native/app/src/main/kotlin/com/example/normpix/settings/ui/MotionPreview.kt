package com.example.normpix.settings.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.BackGesture
import com.example.normpix.navigation.BackPose
import com.example.normpix.navigation.BackPreview
import com.example.normpix.navigation.BottomRevealShape
import com.example.normpix.navigation.LinuxWindowMotion
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.ui.components.LocalLiveMotion
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/*
 * Small moving pictures of a choice of motion, for the settings where words do not show it (owner, 2026-11-11: "where we need the
 * previews of what that setting is doing"): a page over the page beneath it, on a screen the size of a thumb, doing what the choice
 * does. They are drawn from the app's own poses ([BackPreview], the ones the back gesture and the tabs are drawn with), so a preview
 * cannot say one thing and the app do another.
 *
 * A picture moves only where endless motion is allowed ([LocalLiveMotion]: the running app, never a test or a render) and
 * animations are on; otherwise it is one still frame part of the way through, which still tells the choices apart.
 */

/** How far the finger goes in the back gesture's preview before it lets go: an ordinary swipe, not a drag across the screen. */
private const val PREVIEW_DRAG = 0.55f

/** The frame a preview stands at where it may not move. */
private const val PREVIEW_STILL = 0.6f

private val PreviewWidth = 108.dp
private val PreviewHeight = 156.dp

/** The miniature screen's corners, and the most a page in it rounds as it moves: the design system's medium, a tile's. */
private val PreviewCorner = AppRadius.md

/**
 * The back gesture as the chosen style ([animation]) and speed ([totalMs]) play it: a finger comes in from the edge and the page
 * follows it a little, then it lets go and the rest of the close plays in what is left of the clock - over and over.
 */
@Composable
internal fun BackGesturePreview(animation: PageAnimationType, totalMs: Int, modifier: Modifier = Modifier) {
    val live = LocalLiveMotion.current && !rememberReducedMotion()
    val raw = remember { Animatable(PREVIEW_STILL) }
    var finger by remember { mutableStateOf(true) }
    LaunchedEffect(animation, totalMs, live) {
        if (!live) {
            raw.snapTo(PREVIEW_STILL)
            finger = true
            return@LaunchedEffect
        }
        while (true) {
            finger = false
            raw.snapTo(0f)
            delay(600)
            finger = true
            raw.animateTo(PREVIEW_DRAG, tween(700, easing = FastOutSlowInEasing))
            delay(250)
            finger = false
            raw.animateTo(1f, tween(BackGesture.finishMs(PREVIEW_DRAG, totalMs), easing = LinearEasing))
            delay(700)
        }
    }
    MiniScreen(
        modifier = modifier,
        leaving = { BackPreview.leaving(animation, BackGesture.eased(raw.value), 1f) },
        revealed = { BackPreview.revealed(animation, BackGesture.eased(raw.value), 1f) },
        finger = { if (finger) raw.value else null },
    )
}

/**
 * A page opening and closing in the page style [animation]: it comes over the page beneath, stays, and goes - the close as the
 * style closes a page, the opening as that played the other way, each in the time the style gives it.
 */
@Composable
internal fun PageTransitionPreview(animation: PageAnimationType, modifier: Modifier = Modifier) {
    val live = LocalLiveMotion.current && !rememberReducedMotion()
    // 1 is closed (the page gone), 0 open.
    val away = remember { Animatable(PREVIEW_STILL) }
    LaunchedEffect(animation, live) {
        if (!live) {
            away.snapTo(PREVIEW_STILL)
            return@LaunchedEffect
        }
        val window = animation == PageAnimationType.LinuxWindow
        val openMs = if (window) LinuxWindowMotion.OPEN_MS else AppMotion.MEDIUM_MS
        val closeMs = if (window) LinuxWindowMotion.CLOSE_MS else AppMotion.MEDIUM_MS
        while (true) {
            away.snapTo(1f)
            delay(500)
            away.animateTo(0f, tween(openMs, easing = FastOutSlowInEasing))
            delay(900)
            away.animateTo(1f, tween(closeMs, easing = LinearEasing))
            delay(400)
        }
    }
    MiniScreen(
        modifier = modifier,
        leaving = { BackPreview.leaving(animation, away.value, 1f) },
        revealed = { BackPreview.revealed(animation, away.value, 1f) },
        finger = { null },
    )
}

/**
 * A thumb-sized screen: the page beneath, the page on top of it drawn as [leaving] says and the one beneath as [revealed] does, and
 * - while [finger] gives how far it has come - the mark of a finger at the edge it came from. Decoration: nothing in it is read.
 */
@Composable
private fun MiniScreen(
    leaving: () -> BackPose,
    revealed: () -> BackPose,
    finger: () -> Float?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.lg)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(PreviewWidth, PreviewHeight)
                .clip(AppRadius.rMd)
                .background(scheme.surfaceDim),
        ) {
            MiniPage(scheme.surfaceContainerHighest, scheme.onSurfaceVariant, Modifier.graphicsLayer { pose(revealed(), PreviewCorner.toPx()) })
            MiniPage(scheme.primaryContainer, scheme.onPrimaryContainer, Modifier.graphicsLayer { pose(leaving(), PreviewCorner.toPx()) })
            // The finger: at the left edge, a little way in as the gesture goes on.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        val at = finger()
                        alpha = if (at == null) 0f else 1f
                        translationX = (at ?: 0f) * size.width * FINGER_TRAVEL
                    }
                    .size(FingerSize)
                    .background(scheme.inverseSurface.copy(alpha = 0.7f), AppRadius.rFull),
            )
        }
    }
}

/** The rows a miniature page has under its heading: enough to reach its foot. */
private const val MINI_ROWS = 6

/** How many of its own widths the finger's mark moves over a whole gesture. */
private const val FINGER_TRAVEL = 3.2f
private val FingerSize = 16.dp

/** A page in miniature: its tone, and a heading and a few rows as bars of its ink. */
@Composable
private fun MiniPage(tone: Color, ink: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(tone)) {
        Column(Modifier.padding(AppSpacing.sm), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Bar(ink.copy(alpha = 0.55f), width = 0.5f, height = AppSpacing.sm)
            Box(Modifier.height(AppSpacing.xs))
            // Rows all the way down: whatever part of the page a style leaves showing still reads as a page, not a blank block.
            repeat(MINI_ROWS) { Bar(ink.copy(alpha = 0.16f), width = 1f, height = AppSpacing.lg) }
        }
    }
}

@Composable
private fun Bar(color: Color, width: Float, height: Dp) {
    Box(Modifier.fillMaxWidth(width).height(height).background(color, AppRadius.rXs))
}

/** Draws a miniature page in [pose], as the app draws a real one (`applyBackPose`), with corners the size of the miniature's. */
private fun GraphicsLayerScope.pose(pose: BackPose, corner: Float) {
    scaleX = pose.scale
    scaleY = pose.scale
    alpha = pose.alpha
    translationX = pose.shiftX * size.width
    transformOrigin = pose.origin
    if (pose.reveal < 1f) {
        clip = true
        shape = BottomRevealShape(pose.reveal, corner * pose.corners)
    } else if (pose.corners > 0f) {
        clip = true
        shape = RoundedCornerShape(corner * pose.corners)
    }
}
