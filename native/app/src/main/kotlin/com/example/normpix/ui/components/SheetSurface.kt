package com.example.normpix.ui.components

import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import com.example.normpix.ui.theme.AppFrostedTokens
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.isDark
import java.util.function.Consumer

/*
 * Sheets and dialogs lie over a blurred page, which shows through them as soft colour - the launcher's app drawer. The blur
 * is the app's own (the root blurs the page it draws, see NormPixRoot), not the window manager's: the app can move it in
 * step with the sheet every frame, where a window attribute only reaches the screen a round trip later and so trailed in
 * after the sheet had already opened. Where nothing is blurred, sheets and dialogs are solid.
 */

/** Every sheet's shape: large (28dp) corners on top, square where it meets the screen's edge. */
val SheetShape = RoundedCornerShape(topStart = AppFrostedTokens.cornerRadius, topEnd = AppFrostedTokens.cornerRadius)

/**
 * What a sheet is made of: its tonal surface (see-through over a blurred page, solid otherwise), its tone when solid
 * ([ground]; the surfaces inside it tint against it, see [SeeThroughColors]), whether the page behind it is blurred
 * ([blurBehind]), and the dim over the page around it ([scrim]).
 */
@Immutable
class SheetSurface(
    val container: Color,
    val ground: Color,
    val blurBehind: Boolean,
    val scrim: Color,
    /** The drag handle's colour: the soft outline tone on a solid sheet; over a blurred page one that reads on any part of it. */
    val handle: Color,
    /**
     * The colour of text and icons that do not pick their own (a sheet works it out from its container by matching the
     * theme's colours; given here so that never depends on the match).
     */
    val content: Color = Color.Unspecified,
)

/**
 * The page behind is blurred when the user has blur on ([enabled]) and the system is blurring at all ([blurAvailable]:
 * Android 12+, not switched off by battery saver or the device) - the same rule as the floating bars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSheetSurface(enabled: Boolean = true, blurAvailable: Boolean = rememberWindowBlurAvailable()): SheetSurface {
    val scheme = solidColorScheme
    val blurBehind = enabled && blurAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val solid = scheme.surfaceContainerLow
    val container = if (blurBehind) solid.copy(alpha = AppFrostedTokens.surfaceAlpha) else solid
    // The blur already sets the page back; a sharp page gets Material's usual dim.
    val scrim = if (blurBehind) scheme.scrim.copy(alpha = AppFrostedTokens.scrimAlpha) else BottomSheetDefaults.ScrimColor
    val handle = if (blurBehind) scheme.onSurfaceVariant.copy(alpha = 0.5f) else scheme.outlineVariant
    val content = scheme.onSurface
    return remember(container, solid, scrim, handle, content, blurBehind) {
        SheetSurface(container, solid, blurBehind, scrim, handle, content)
    }
}

/** A centred dialog: its surface (see-through over a blurred page, as a sheet), the dialog shape, and whether the page is blurred. */
@Immutable
class DialogSurface(
    val container: Color,
    val ground: Color,
    val blurBehind: Boolean,
    val shape: RoundedCornerShape = AppRadius.rXxl,
)

@Composable
fun rememberDialogSurface(enabled: Boolean = true, blurAvailable: Boolean = rememberWindowBlurAvailable()): DialogSurface {
    // From the solid theme: a dialog opened from a see-through sheet is a surface of its own, not one of the sheet's tints.
    val solid = solidColorScheme.surfaceContainerHigh
    val blurBehind = enabled && blurAvailable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val container = if (blurBehind) solid.copy(alpha = AppFrostedTokens.surfaceAlpha) else solid
    return remember(container, solid, blurBehind) { DialogSurface(container, solid, blurBehind) }
}

/** The standard Material 3 handle (32 x 4dp pill), by default in the soft outline tone (see [SheetSurface.handle]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BottomSheetDefaults.DragHandle(color = color)
    }
}

/**
 * A bottom sheet's window is full-screen: transparent system bars over it, with icons that read against the app's theme.
 */
@Composable
fun SheetWindowChrome() {
    val view = LocalView.current
    val window = remember(view) { view.findDialogWindow() } ?: return
    val dark = MaterialTheme.colorScheme.isDark
    DisposableEffect(window, dark) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !dark
        insetsController.isAppearanceLightNavigationBars = !dark
        onDispose { }
    }
}

/**
 * For the content of a sheet: while [enabled], reports how much of the sheet is on screen as [owner]'s share of the page
 * blur (see [AppOverlays.backdropBlur]). It is read from where the content actually is each frame, so the blur rises
 * with the sheet, follows a drag and falls as it closes - never ahead of it or after it.
 */
@Composable
fun Modifier.blursBackdrop(overlays: AppOverlays, owner: Any, enabled: Boolean): Modifier {
    DisposableEffect(overlays, owner, enabled) { onDispose { overlays.clearBackdropBlur(owner) } }
    if (!enabled) return this
    return onGloballyPositioned { overlays.setBackdropBlur(owner, sheetShareOnScreen(it)) }
}

private fun sheetShareOnScreen(content: LayoutCoordinates): Float {
    var root = content
    while (true) root = root.parentLayoutCoordinates ?: break
    return sheetShareOnScreen(root.size.height.toFloat(), content.positionInRoot().y, content.size.height.toFloat())
}

/**
 * How far up a sheet's content is in a window [windowHeight] tall, 0..1: 0 while its top ([top]) is at or below the
 * bottom edge, 1 once all [height] of it is up - or, for a tall sheet, once it covers [FULL_BLUR_AT] of the window, so
 * one resting half-open (an expandable sheet) has the page fully blurred too.
 */
internal fun sheetShareOnScreen(windowHeight: Float, top: Float, height: Float): Float {
    val full = minOf(height, windowHeight * FULL_BLUR_AT)
    return if (full <= 0f) 0f else ((windowHeight - top) / full).coerceIn(0f, 1f)
}

/** A half-open sheet shows half the window, less its handle. */
private const val FULL_BLUR_AT = 0.4f

/**
 * [share] (0..1, read every frame) as it is - so a blur following a sheet stays in step with it - except that a sudden
 * drop, an overlay removed rather than slid away, eases out over a moment instead of snapping.
 */
@Composable
fun rememberEasedBlur(share: () -> Float): () -> Float {
    val latest = rememberUpdatedState(share)
    val settle = remember { Animatable(0f) }
    val ms = AppMotion.duration(AppMotion.SHORT_MS)
    LaunchedEffect(ms) {
        snapshotFlow { latest.value() }.collectLatest { target ->
            if (target < settle.value - 0.25f) settle.animateTo(target, tween(ms, easing = AppMotion.standard)) else settle.snapTo(target)
        }
    }
    return remember { { max(latest.value(), settle.value) } }
}

/**
 * Draws this blurred by [share] (0..1, read when drawing) of the full radius. Front-loaded, so what shows through the first
 * strip of a rising see-through sheet is already soft, not sharp text under a tint. [edges]: how the blur treats what lies
 * beyond this element's bounds - [TileMode.Clamp] for the full-screen page, [TileMode.Decal] (nothing there) for content
 * floating on a sheet.
 */
fun Modifier.blurredBy(share: () -> Float, edges: TileMode = TileMode.Clamp): Modifier = graphicsLayer {
    val s = share()
    val strength = 1f - (1f - s) * (1f - s)
    val radius = strength * AppFrostedTokens.blurRadius.toPx()
    renderEffect = if (radius >= 1f) BlurEffect(radius, radius, edges) else null
}

/**
 * For a centred dialog: blurs the page behind it while it is open, fading in as the dialog appears (a dialog has no
 * motion of its own to follow) and out as it goes.
 */
@Composable
fun DialogBackdropBlur(enabled: Boolean) {
    val overlays = LocalOverlays.current
    val owner = remember { Any() }
    val share = remember { Animatable(0f) }
    val duration = AppMotion.duration(AppMotion.SHORT_MS)
    LaunchedEffect(enabled) {
        if (enabled) share.animateTo(1f, tween(duration, easing = AppMotion.enter)) else share.snapTo(0f)
    }
    LaunchedEffect(overlays) { snapshotFlow { share.value }.collect { overlays.setBackdropBlur(owner, it) } }
    DisposableEffect(overlays) { onDispose { overlays.clearBackdropBlur(owner) } }
    // The window's own dim (60% black) would turn a see-through dialog grey; over a blurred page it gets a sheet's light
    // dim instead, and keeps the usual one over a sharp page.
    val view = LocalView.current
    val window = remember(view) { view.findDialogWindow() }
    DisposableEffect(window, enabled) {
        val usual = window?.attributes?.dimAmount
        if (enabled) window?.setDimAmount(AppFrostedTokens.scrimAlpha)
        onDispose { if (enabled && usual != null) window.setDimAmount(usual) }
    }
}

private fun View.findDialogWindow(): Window? {
    var v: View? = this
    while (v != null) {
        if (v is DialogWindowProvider) return v.window
        v = v.parent as? View
    }
    var ctx = context
    while (ctx is ContextWrapper) {
        if (ctx is DialogWindowProvider) return ctx.window
        ctx = ctx.baseContext
    }
    return null
}

/** Tests (and screenshot harnesses) can say whether the system blurs, which Robolectric never does. */
val LocalWindowBlurOverride = compositionLocalOf<Boolean?> { null }

/** Whether the system is blurring at all right now (Android 12+, and not switched off). Follows changes. */
@Composable
fun rememberWindowBlurAvailable(): Boolean {
    LocalWindowBlurOverride.current?.let { return it }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) rememberCrossWindowBlur() else false
}

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun rememberCrossWindowBlur(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { runCatching { context.getSystemService(WindowManager::class.java) }.getOrNull() }
    var enabled by remember(manager) { mutableStateOf(runCatching { manager?.isCrossWindowBlurEnabled == true }.getOrDefault(false)) }
    DisposableEffect(manager) {
        val listener = Consumer<Boolean> { enabled = it }
        runCatching { manager?.addCrossWindowBlurEnabledListener(context.mainExecutor, listener) }
        onDispose { runCatching { manager?.removeCrossWindowBlurEnabledListener(listener) } }
    }
    return enabled
}

/** How small a sheet or decision is as it starts to rise in the window style: it grows to full size as it comes up. */
private const val SHEET_WINDOW_SCALE = 0.9f

/**
 * A sheet that opens and closes like a window, for the Linux window page style: it grows out of the bottom edge as it
 * rises - quick to fade in, decelerating into place - and shrinks back towards it, fading, as it is dragged down or
 * closed, the way a page opens and closes in that style. Read from where [state] has the sheet each frame, so it follows
 * the finger and the sheet's own motion exactly; at rest it is the sheet as it is. Off ([enabled] false) it changes nothing.
 *
 * Material's sheet is laid out at the top of its window and drawn [SheetState.requireOffset] lower, so the window's
 * bottom-edge pivot is worked out from that offset rather than from where the sheet is laid out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Modifier.windowRise(state: SheetState, enabled: Boolean): Modifier {
    if (!enabled) return this
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    // The window's height and the sheet's own, as laid out (neither changes with the scaling below).
    val sizes = remember { floatArrayOf(0f, 0f) }
    return onGloballyPositioned { coordinates ->
        sizes[0] = (coordinates.parentLayoutCoordinates?.size?.height ?: 0).toFloat()
        sizes[1] = coordinates.size.height.toFloat()
    }.graphicsLayer {
        val height = sizes[1]
        val offset = runCatching { state.requireOffset() }.getOrNull()
        if (offset == null || height <= 0f) return@graphicsLayer
        val share = sheetShareOnScreen(sizes[0] - statusTop, offset, height)
        val scale = SHEET_WINDOW_SCALE + (1f - SHEET_WINDOW_SCALE) * share
        scaleX = scale
        scaleY = scale
        alpha = (share / 0.6f).coerceIn(0f, 1f)
        // The bottom of the sheet as drawn: it grows out of, and shrinks back into, the bottom edge.
        transformOrigin = TransformOrigin(0.5f, (offset + height) / height)
    }
}
