package com.normpix.ui.floatingnav

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Shared state connecting a screen's content ([glassSource]) with any panel that
 * wants to render a blurred "glass" backdrop of it ([glassPanel]).
 *
 * The content is recorded into a [GraphicsLayer] display list (no bitmap copies),
 * which glass panels re-draw through a blur [RenderEffect], clipped to their own
 * bounds. Fully local, no extra dependencies.
 */
@Stable
class GlassBackdropState internal constructor(
    internal val contentLayer: GraphicsLayer,
) {
    /** Top-left of the recorded content in root coordinates. */
    internal var contentPositionInRoot: Offset by mutableStateOf(Offset.Zero)

    /**
     * Bumped every time the content re-draws. Glass panels read this from their
     * draw scope so they re-record while the content scrolls or animates.
     */
    internal var frame: Int by mutableIntStateOf(0)

    /**
     * Forces glass panels to re-record their backdrop slice. Needed when content
     * moves inside its own layer (e.g. scrolling) without re-drawing the source:
     * layer translations don't re-run [glassSource]'s draw, so panels would
     * otherwise show a stale blur.
     */
    fun invalidate() {
        frame++
    }
}

/** Ambient [GlassBackdropState] so scrollables anywhere in the tree can keep panels in sync. */
val LocalGlassBackdropState = staticCompositionLocalOf<GlassBackdropState?> { null }

/** Creates and remembers a [GlassBackdropState]; the backing layer is released automatically. */
@Composable
fun rememberGlassBackdropState(): GlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdropState(layer) }
}

/**
 * Marks this composable as the content that glass panels blur.
 * Apply it to the content that sits *behind* the floating bar (e.g. your LazyColumn
 * or NavHost container). Drawing is unchanged; the content is simply also recorded
 * into a display list for re-use.
 */
fun Modifier.glassSource(state: GlassBackdropState): Modifier =
    this
        .onGloballyPositioned { state.contentPositionInRoot = it.positionInRoot() }
        .drawWithContent {
            val width = size.width.roundToInt()
            val height = size.height.roundToInt()
            if (width > 0 && height > 0) {
                state.contentLayer.record(this, layoutDirection, IntSize(width, height)) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(state.contentLayer)
                state.frame++ // Invalidate glass panels.
            } else {
                drawContent()
            }
        }

/**
 * Renders a blurred, tinted backdrop of the [glassSource] content behind this element,
 * clipped to [shape].
 *
 * On API 31+ the backdrop is blurred (and gently saturated, which is what makes glass
 * read as "glass"). On older devices — or when [state] is null — it gracefully falls
 * back to a plain translucent surface at [fallbackOpacity].
 */
@Composable
fun Modifier.glassPanel(
    state: GlassBackdropState?,
    shape: Shape,
    blurRadius: Dp = 20.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    backgroundOpacity: Float = 0.5f,
    fallbackOpacity: Float = 0.93f,
    saturation: Float = 1.25f,
): Modifier {
    val blurLayer = rememberGraphicsLayer()
    val density = LocalDensity.current
    // Created once per configuration, not per frame.
    val glassEffect: RenderEffect? = remember(blurRadius, saturation, density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
            createGlassRenderEffect(with(density) { blurRadius.toPx() }, saturation)
        } else {
            null
        }
    }
    var panelPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    return this
        .onGloballyPositioned { panelPositionInRoot = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            // Subscribe to content re-draws so the blur tracks scrolling content.
            if (state != null && state.frame == Int.MIN_VALUE) return@drawBehind
            val blurCapable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            if (state != null && blurCapable && state.contentLayer.size != IntSize.Zero) {
                // Re-draw only the slice of content behind this panel, then blur it.
                if (glassEffect != null) {
                    val offset = panelPositionInRoot - state.contentPositionInRoot
                    val width = size.width.roundToInt()
                    val height = size.height.roundToInt()
                    if (width > 0 && height > 0) {
                        blurLayer.record(this, layoutDirection, IntSize(width, height)) {
                            translate(-offset.x, -offset.y) {
                                drawLayer(state.contentLayer)
                            }
                        }
                        blurLayer.renderEffect = glassEffect
                        drawLayer(blurLayer)
                    }
                }
                // Tint: use a slightly more sophisticated blend if possible, 
                // but for now, just ensure the opacity is applied to a layer 
                // that doesn't completely kill the blur.
                drawRect(backgroundColor.copy(alpha = backgroundOpacity))
            } else {
                // Graceful fallback: slightly more translucent for better visibility of content.
                drawRect(backgroundColor.copy(alpha = fallbackOpacity.coerceAtMost(0.8f)))
            }
        }
}

/** Blur chained with a slight saturation boost so colors stay lively through the glass. */
@RequiresApi(Build.VERSION_CODES.S)
private fun createGlassRenderEffect(radiusPx: Float, saturation: Float): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(
        radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP,
    )
    if (saturation == 1f) return blur.asComposeRenderEffect()
    val colorMatrix = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
    val saturate = android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(colorMatrix),
    )
    return android.graphics.RenderEffect.createChainEffect(blur, saturate).asComposeRenderEffect()
}
