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
import com.example.normpix.navigation.BottomRevealShape
import com.example.normpix.navigation.LinuxWindowMotion
import com.example.normpix.settings.SheetAnimationType
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
            // Deprecated in Android 15 (API 35); still what keeps the scrim off the status bar on Android 10 to 14.
            @Suppress("DEPRECATION")
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
 *
 * A sheet whose motion holds it where it rests while it appears (see [sheetMotion]) tells where it really is, by [hold]: the
 * blur follows the sheet's own rise, not the place it is drawn at.
 */
@Composable
fun Modifier.blursBackdrop(overlays: AppOverlays, owner: Any, enabled: Boolean, hold: SheetHold? = null): Modifier {
    DisposableEffect(overlays, owner, enabled) { onDispose { overlays.clearBackdropBlur(owner) } }
    if (!enabled) return this
    return onGloballyPositioned { overlays.setBackdropBlur(owner, sheetShareOnScreen(it, hold?.shift ?: 0f)) }
}

private fun sheetShareOnScreen(content: LayoutCoordinates, shift: Float): Float {
    var root = content
    while (true) root = root.parentLayoutCoordinates ?: break
    // Drawn [shift] px from where it is laid out: the layout place is that much the other way.
    return sheetShareOnScreen(root.size.height.toFloat(), content.positionInRoot().y - shift, content.size.height.toFloat())
}

/**
 * How far a sheet has risen, 0..1: 0 while its top ([top]) is at or below the bottom of a window [windowHeight] tall, 1 once
 * all [height] of it is up - or half the window, where a tall sheet stops half way. Spread over the whole rise (unlike the
 * blur's share, which is full well before), so a sheet that grows or fades is seen doing it all the way up.
 */
internal fun sheetRiseShare(windowHeight: Float, top: Float, height: Float): Float {
    val full = minOf(height, windowHeight * 0.5f)
    return if (full <= 0f) 0f else ((windowHeight - top) / full).coerceIn(0f, 1f)
}

/**
 * How far, in pixels, a sheet is below the place it comes to rest at (where [sheetRiseShare] is 1): all of its rise while it
 * is hidden, nothing once it is up - and nothing while it is dragged higher than that.
 */
internal fun sheetLag(windowHeight: Float, top: Float, height: Float): Float {
    val full = minOf(height, windowHeight * 0.5f)
    return if (full <= 0f) 0f else (top - (windowHeight - full)).coerceAtLeast(0f)
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

/** How small a sheet or decision is as it starts to rise in the Window style: it grows to full size as it comes up. */
private const val SHEET_WINDOW_SCALE = 0.85f

/** How small a sheet is as it starts to appear in the Scale style. */
private const val SHEET_SCALE_FROM = 0.9f

/** How much of its rise a sheet takes to become fully visible in the Window style, and in the Scale style. */
private const val WINDOW_FADE_SPAN = 0.5f
private const val SCALE_FADE_SPAN = 0.6f

/** The Linux window style: by this share of its rise the strip a sheet opens from is as visible as it will be before it fades in on. */
private const val LINUX_STRIP_APPEARS_BY = 0.06f

/** How far the rise must change between frames to count as a direction: below this it is noise, and the last direction stands. */
private const val TREND_EPSILON = 0.0005f

/**
 * How a sheet is drawn at one moment: how big ([scale], about the height [pivotY] of its own, as a fraction of its height), how
 * visible ([alpha]), how far it is moved ([translationY], px, down is positive) from where Material draws it, and how much of
 * its height shows ([reveal], 0..1: the part nearest its bottom edge - a window opening from a strip, see [LinuxWindowMotion]).
 */
@Immutable
internal class SheetPose(val scale: Float, val alpha: Float, val translationY: Float, val pivotY: Float, val reveal: Float = 1f) {
    companion object {
        /** Material's own sheet, untouched: it slides. */
        val Rest = SheetPose(scale = 1f, alpha = 1f, translationY = 0f, pivotY = 0.5f)
    }
}

/**
 * The pose of a sheet in [style] when its top is [top] below the top of a window [windowHeight] tall, and it is [height] tall.
 * Material lays a sheet out at the top of the window and draws it [top] lower, and slides it between hidden and open; the
 * styles are what is done to that:
 * - [SheetAnimationType.Slide]: nothing. Material's own slide.
 * - [SheetAnimationType.Window]: it still rises, and grows out of the window's bottom edge as it does, quick to fade in -
 *   and shrinks back into it, fading, as it is dragged down or closed.
 * - [SheetAnimationType.Fade]: it does not rise; it is held where it rests and fades in (and out) there.
 * - [SheetAnimationType.Scale]: held where it rests, it grows from its bottom edge as it fades in, and shrinks back into it.
 * - [SheetAnimationType.LinuxWindow]: a desktop window's open and close, as the pages have them ([LinuxWindowMotion]). Held where
 *   it rests - it does not rise - it is a thin strip along its bottom edge that expands upward into the whole sheet, its top edge
 *   rising, on a long soft landing and with a subtle fade. It closes ([closing]: it is going away, as it is when it is dragged
 *   down too) the other way: the top edge sinks, shrinking it down into the strip, and the last of it fades. Nothing is scaled, so
 *   nothing in it is squashed on the way. How long each takes is Material's sheet's own.
 *
 * Because it is worked out from where the sheet is, it follows a finger dragging it, and the sheet's own motion, exactly.
 */
internal fun sheetPose(style: SheetAnimationType, windowHeight: Float, top: Float, height: Float, closing: Boolean = false): SheetPose {
    if (height <= 0f) return SheetPose.Rest
    val share = sheetRiseShare(windowHeight, top, height)
    // The bottom of the sheet as drawn: it grows out of, and shrinks back into, that edge.
    val bottom = (top + height) / height
    return when (style) {
        SheetAnimationType.Slide -> SheetPose.Rest
        SheetAnimationType.Window -> SheetPose(
            scale = SHEET_WINDOW_SCALE + (1f - SHEET_WINDOW_SCALE) * share,
            alpha = (share / WINDOW_FADE_SPAN).coerceIn(0f, 1f),
            translationY = 0f,
            pivotY = bottom,
        )
        SheetAnimationType.LinuxWindow -> if (closing) {
            val away = 1f - share
            SheetPose(
                scale = 1f,
                alpha = LinuxWindowMotion.closeAlpha(away),
                translationY = -sheetLag(windowHeight, top, height),
                pivotY = 0.5f,
                reveal = LinuxWindowMotion.closeReveal(away),
            )
        } else {
            SheetPose(
                scale = 1f,
                // Nothing at all while it is hidden - the strip must not show before the sheet has started to come - and the
                // strip's own visibility within the first few percent of the rise.
                alpha = minOf(LinuxWindowMotion.openAlpha(share), share / LINUX_STRIP_APPEARS_BY),
                translationY = -sheetLag(windowHeight, top, height),
                pivotY = 0.5f,
                reveal = LinuxWindowMotion.openReveal(share),
            )
        }
        SheetAnimationType.Fade -> SheetPose(
            scale = 1f,
            alpha = share,
            translationY = -sheetLag(windowHeight, top, height),
            pivotY = 0.5f,
        )
        SheetAnimationType.Scale -> SheetPose(
            scale = SHEET_SCALE_FROM + (1f - SHEET_SCALE_FROM) * share,
            alpha = (share / SCALE_FADE_SPAN).coerceIn(0f, 1f),
            translationY = -sheetLag(windowHeight, top, height),
            pivotY = bottom,
        )
    }
}

/**
 * Where a sheet's motion has put its drawing, for whatever needs to know where the sheet really is ([blursBackdrop]): [shift]
 * is how many pixels it is drawn from where it is laid out, as of the last frame (negative: higher). Not state: only read.
 */
class SheetHold {
    var shift = 0f
}

/**
 * Makes a sheet - or a decision, which is the same surface - open and close in [style] (see [sheetPose]); every sheet and
 * decision the app shows has it, so they all move alike. Read from where [state] has the sheet each frame, so it follows the
 * finger and the sheet's own motion exactly; at rest it is the sheet as it is. [enabled] false (reduced motion) or
 * [SheetAnimationType.Slide] changes nothing: no motion beyond Material's own. [hold] is told where the drawing is.
 *
 * Material's sheet is laid out at the top of its window and drawn [SheetState.requireOffset] lower, so the pivots and the rise
 * are worked out from that offset rather than from where the sheet is laid out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Modifier.sheetMotion(state: SheetState, style: SheetAnimationType, enabled: Boolean, hold: SheetHold? = null): Modifier {
    if (!enabled || style == SheetAnimationType.Slide) {
        // Not moved by us: if it was a moment ago (the style was changed with a sheet open), it no longer is.
        hold?.shift = 0f
        return this
    }
    val statusTop = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    // The window's height and the sheet's own, as laid out (neither changes with the scaling below).
    val sizes = remember { floatArrayOf(0f, 0f) }
    // How far up it was a frame ago, and whether it was last going away (1) or coming up (0): a style that closes differently
    // from how it opens (Linux window) needs to know which way the sheet is going - by a finger dragging it down, as by closing.
    val trend = remember { floatArrayOf(0f, 0f) }
    return onGloballyPositioned { coordinates ->
        sizes[0] = (coordinates.parentLayoutCoordinates?.size?.height ?: 0).toFloat()
        sizes[1] = coordinates.size.height.toFloat()
    }.graphicsLayer {
        val height = sizes[1]
        val offset = runCatching { state.requireOffset() }.getOrNull()
        if (offset == null || height <= 0f) return@graphicsLayer
        val windowHeight = sizes[0] - statusTop
        val share = sheetRiseShare(windowHeight, offset, height)
        if (share < trend[0] - TREND_EPSILON) trend[1] = 1f else if (share > trend[0] + TREND_EPSILON) trend[1] = 0f
        trend[0] = share
        val pose = sheetPose(style, windowHeight, offset, height, closing = trend[1] == 1f)
        scaleX = pose.scale
        scaleY = pose.scale
        alpha = pose.alpha
        translationY = pose.translationY
        transformOrigin = TransformOrigin(0.5f, pose.pivotY)
        if (pose.reveal < 1f) {
            // A window opening from, or sinking into, its strip: only the part along its bottom edge shows, its top corners as a sheet's.
            clip = true
            shape = BottomRevealShape(pose.reveal, AppFrostedTokens.cornerRadius.toPx())
        }
        hold?.shift = pose.translationY
    }
}
