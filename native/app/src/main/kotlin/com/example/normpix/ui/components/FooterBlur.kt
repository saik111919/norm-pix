package com.example.normpix.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
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
 * The foot of the page, behind the floating dock or back row: what scrolls under it goes out of focus and into the page's own
 * colour, more and more towards the screen's edge - the progressive blur of DropSauce's `BarBlur`, drawn from the same recording of
 * the page the bars' glass uses ([FrostedBackdrop]); it must sit beside the page, never inside it.
 *
 * On Android 13 and later it is the real thing: a shader whose blur radius grows with the square of the way to the edge
 * ([ProgressiveBlur]), so the page dissolves smoothly instead of a blurred copy fading in over a sharp one. On Android 12 a blurred
 * copy of the zone fades in from nothing ([FooterBlurFallback]).
 *
 * The zone is the navigation bar and the bars ([barsHeight]: the dock and its margin) and [AppFrostedTokens.footerZoneFactor] times
 * that, so it begins well above the dock. [visible] (0 to 1, read while drawing) is how much of it shows: as the dock slides away
 * on scroll the footer goes with it. It draws nothing where the blur is off or unavailable, and it takes no touches.
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
    val wash = MaterialTheme.colorScheme.surfaceContainer
    val blurred = rememberGraphicsLayer()
    val progressive = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { ProgressiveBlur() }.getOrNull()
        } else {
            null
        }
    }
    var position by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxWidth()
            .height((navigation + barsHeight) * AppFrostedTokens.footerZoneFactor)
            .onGloballyPositioned { position = it.positionInWindow() }
            // Composited on its own, so the fallback's fade is a mask on the blur and not on the page under it.
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                alpha = visible().coerceIn(0f, 1f)
            }
            .drawWithContent {
                if (visible().coerceIn(0f, 1f) <= 0.001f) return@drawWithContent
                val behind = position - backdrop.origin
                val radius = AppFrostedTokens.footerBlurRadius.toPx()
                val effect = runCatching {
                    progressive?.effect(size.width, size.height, radius, wash, AppFrostedTokens.footerWashAlpha)
                }.getOrNull()
                blurred.renderEffect = effect ?: BlurEffect(radius, radius, TileMode.Clamp)
                blurred.record(size = IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                    translate(-behind.x, -behind.y) { drawLayer(backdrop.layer) }
                }
                drawLayer(blurred)
                if (effect == null) FooterBlurFallback.fade(this, wash)
            },
    )
}

/** The Android 12 footer: the blurred copy fades in from nothing at the zone's top (a mask on it), under a wash of the page's colour. */
private object FooterBlurFallback {
    fun fade(scope: androidx.compose.ui.graphics.drawscope.DrawScope, wash: Color) = with(scope) {
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.5f to Color.Black, 1f to Color.Black),
            blendMode = BlendMode.DstIn,
        )
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to wash.copy(alpha = AppFrostedTokens.footerWashAlpha)))
    }
}

/**
 * DropSauce's progressive blur as an AGSL shader on the zone's picture of the page: at a pixel [t] of the way down the zone
 * (0 at its top, 1 at the screen's edge) the page is blurred over a radius of t^1.5 x the most, and mixed with the page's colour by
 * t x [washAlpha]. A grid of samples, jittered by a hash so the grid does not show, weighted by a Gaussian; samples are kept inside
 * the picture, so its edges are not darkened. At the top of the zone nothing is changed, so there is no edge where it begins.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class ProgressiveBlur {
    private val shader = RuntimeShader(AGSL)
    private var effect: RenderEffect? = null
    private var key: List<Any> = emptyList()

    fun effect(widthPx: Float, heightPx: Float, maxRadiusPx: Float, wash: Color, washAlpha: Float): RenderEffect {
        val now = listOf(widthPx, heightPx, maxRadiusPx, wash.toArgb(), washAlpha)
        effect?.let { if (now == key) return it }
        shader.setFloatUniform("size", widthPx, heightPx)
        shader.setFloatUniform("maxRadius", maxRadiusPx)
        shader.setFloatUniform("washAlpha", washAlpha)
        try {
            shader.setColorUniform("wash", wash.toArgb())
        } catch (_: Throwable) {
            shader.setFloatUniform("wash", wash.red, wash.green, wash.blue, wash.alpha)
        }
        key = now
        return android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect().also { effect = it }
    }

    private companion object {
        const val AGSL = """
            uniform shader content;
            uniform float2 size;
            uniform float maxRadius;
            uniform float washAlpha;
            layout(color) uniform half4 wash;

            const int SAMPLES = 4;

            half4 main(float2 p) {
                float t = clamp(p.y / size.y, 0.0, 1.0);
                float radius = pow(t, 1.5) * maxRadius;
                half4 base;
                if (radius < 0.75) {
                    base = content.eval(p);
                } else {
                    float dither = fract(sin(dot(p, float2(12.9898, 78.233))) * 43758.5453);
                    float2 jitter = float2(dither - 0.5, fract(dither * 1.618) - 0.5);
                    float stepPx = radius / float(SAMPLES);
                    float radiusSq = radius * radius;
                    half4 accum = half4(0.0);
                    float weights = 0.0;
                    for (int x = -SAMPLES; x <= SAMPLES; x++) {
                        for (int y = -SAMPLES; y <= SAMPLES; y++) {
                            float2 offset = (float2(float(x), float(y)) + jitter) * stepPx;
                            float distSq = dot(offset, offset);
                            if (distSq <= radiusSq) {
                                float w = exp(-3.0 * distSq / radiusSq);
                                accum += content.eval(clamp(p + offset, float2(0.5), size - float2(0.5))) * w;
                                weights += w;
                            }
                        }
                    }
                    base = weights > 0.0 ? accum / weights : content.eval(p);
                }
                return mix(base, wash, t * washAlpha);
            }
        """
    }
}
