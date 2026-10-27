package com.example.normpix.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.example.normpix.navigation.DockFootprint
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.theme.AppFrostedTokens
import kotlin.math.roundToInt

/**
 * Whether the footer is blurred right now: the bars over [backdrop] are glass ([rememberFrostedChrome]) and the user has not
 * switched the footer's blur off (Settings > Appearance > Effects > Footer blur).
 */
@Composable
fun rememberFooterBlur(backdrop: FrostedBackdrop?): Boolean {
    val on = LocalAppPreferences.current?.state?.collectAsState()?.value?.footerBlurEnabled ?: true
    return on && backdrop != null && rememberFrostedChrome(backdrop)
}

/**
 * The foot of the page, behind the floating dock or back row: what scrolls under it is blurred and washed with the page's own
 * colour, more and more towards the screen's edge, so the bars float on soft colour instead of over sharp text. It is the
 * "progressive blur" of a launcher's or a reader's bottom bar, drawn from the same recording of the page the bars' glass uses
 * ([FrostedBackdrop]); it must sit beside the page, never inside it.
 *
 * It takes in the navigation bar, [barsHeight] of bars (the dock and its margin) and a short tail ([AppFrostedTokens.footerFade])
 * where the blur eases in from nothing. [visible] (0 to 1, read while drawing) is how much of it shows: as the dock slides
 * away on scroll the footer goes with it. It draws nothing where the blur is off or unavailable, and it takes no touches.
 */
@Composable
fun FooterBlur(
    backdrop: FrostedBackdrop?,
    modifier: Modifier = Modifier,
    barsHeight: Dp = DockFootprint,
    visible: () -> Float = { 1f },
) {
    if (backdrop == null || !rememberFooterBlur(backdrop)) return
    val navigation = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val wash = MaterialTheme.colorScheme.surface
    val blurred = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxWidth()
            .height(navigation + barsHeight + AppFrostedTokens.footerFade)
            .onGloballyPositioned { position = it.positionInWindow() }
            // Composited on its own, so the fade below is a mask on the blur and not on the page under it.
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = visible().coerceIn(0f, 1f)
            }
            .drawWithContent {
                val behind = position - backdrop.origin
                val radius = AppFrostedTokens.footerBlurRadius.toPx()
                blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                blurred.record(size = IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                    translate(-behind.x, -behind.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(blurred)
                // Nothing at the top of the zone, all of the blur from a little way down: no edge where it starts.
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Black),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to wash.copy(alpha = AppFrostedTokens.footerWashAlpha)))
            },
    )
}
