package com.example.normpix.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.theme.AppFrostedTokens
import kotlin.math.roundToInt

private fun Outline.toPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}

/**
 * What the floating bars (the dock, the back bar) blur. Under a sheet the whole page is blurred (see NormPixRoot); a bar
 * blurs only the part of the page behind it: the page records itself into [layer] once ([frostedSource]), and each bar
 * draws a blurred copy of the part of it that lies behind the bar ([frostedGlass]).
 *
 * A bar must sit beside the source, never inside it, or it would be blurring a picture of itself.
 */
@Stable
class FrostedBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** Where the source sits in the window; a bar's offset from it says which part of the page lies behind the bar. */
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberFrostedBackdrop(): FrostedBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { FrostedBackdrop(layer) }
}

/** The backdrop of the page the bars on screen float over; null where there is none (previews, tests, a bare bar). */
val LocalFrostedBackdrop = staticCompositionLocalOf<FrostedBackdrop?> { null }

/**
 * Whether bars over [backdrop] are glass right now: the user has not switched background blur off, there is something to
 * blur, and the system is blurring at all (Android 12+, and not switched off by battery saver or the device). When it is
 * not, bars are solid - the same rule as a sheet, which is solid wherever nothing is blurred behind it, because a
 * see-through bar over sharp text is unreadable.
 */
@Composable
fun rememberFrostedChrome(
    backdrop: FrostedBackdrop? = LocalFrostedBackdrop.current,
    blurAvailable: Boolean = rememberWindowBlurAvailable(),
): Boolean {
    val enabled = LocalAppPreferences.current?.state?.collectAsState()?.value?.sheetBlurEnabled ?: true
    return enabled && backdrop != null && blurAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/** Marks this as what glass bars blur: it draws as usual and records what it drew for them. */
fun Modifier.frostedSource(backdrop: FrostedBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

/**
 * Draws, behind this element and inside [shape], the part of [backdrop] that lies behind it, blurred. Put the element's
 * translucent tint on top (its own background). Where bars are not glass ([rememberFrostedChrome]) it draws nothing, and
 * the element is just its tint - which is then solid.
 */
@Composable
fun Modifier.frostedGlass(
    backdrop: FrostedBackdrop?,
    shape: Shape,
    blurAvailable: Boolean = rememberWindowBlurAvailable(),
): Modifier {
    if (backdrop == null || !rememberFrostedChrome(backdrop, blurAvailable)) return this
    // One layer per bar, the bar's own size: the blur only ever works on that much, however big the page is.
    val blurred = rememberGraphicsLayer()
    var position by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { position = it.positionInWindow() }
        .drawWithContent {
            val behind = position - backdrop.origin
            val radius = AppFrostedTokens.chromeBlurRadius.toPx()
            blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            blurred.record(size = IntSize(size.width.roundToInt(), size.height.roundToInt())) {
                translate(-behind.x, -behind.y) { drawLayer(backdrop.layer) }
            }
            clipPath(shape.createOutline(size, layoutDirection, this).toPath()) { drawLayer(blurred) }
            drawContent()
        }
}
