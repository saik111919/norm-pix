package com.example.normpix.reminders

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.normpix.NormPixApplication
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.math.roundToInt

/**
 * The reminder as a card floating at the top of the screen over whatever app is open, Samsung-Reminder style - an optional extra
 * on top of the notification, which is always posted and is what a reminder relies on.
 *
 * It is an ordinary overlay window ([WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY]) of the kind Android's "Display over
 * other apps" switch allows, the permission the user grants in Android's own settings. Nothing is forced: it is never added
 * unless that permission is held, it cannot take focus (so the app underneath keeps its keyboard and its back button), it covers
 * only its own card, and a window that wants nothing drawn over it (a banking app can ask Android to hide overlays) is respected
 * by Android itself, not worked around. No Accessibility service is used.
 *
 * Whether to show it at all is [decideOverlay]'s call; this only draws it. It goes away on its own after [SHOWN_FOR_MS], on a swipe
 * up, or on any of its buttons; the notification stays until the reminder is dealt with.
 */
object ReminderOverlay {
    /** How long the card stays if left alone. The notification is there after that. */
    const val SHOWN_FOR_MS = 30_000L

    private val main = Handler(Looper.getMainLooper())
    private var current: Shown? = null

    /** The card for [reminder], replacing one already up. Safe to call from any thread. */
    fun show(context: Context, reminder: Reminder, onAction: (ReminderCardAction) -> Unit) {
        val app = context.applicationContext
        main.post { showNow(app, reminder, onAction) }
    }

    /** Takes the card down (if it is up). Safe to call from any thread. */
    fun dismiss() {
        main.post { dismissNow(animate = true) }
    }

    /** Whether a card is up. Main thread. */
    val isShowing: Boolean get() = current != null

    private class Shown(val view: ComposeView, val owner: OverlayOwner, val windows: WindowManager) {
        var visible by mutableStateOf(true)
        var removal: Runnable? = null
        var timeout: Runnable? = null
    }

    private fun showNow(app: Context, reminder: Reminder, onAction: (ReminderCardAction) -> Unit) {
        if (!ReminderAccess.canDrawOverlays(app)) return
        dismissNow(animate = false)
        val windows = app.getSystemService(WindowManager::class.java) ?: return
        val container = (app as? NormPixApplication)?.container
        val owner = OverlayOwner()
        lateinit var shown: Shown
        // The look the person chose, read once as the card goes up: it is on screen for seconds, so it need not follow a change.
        val prefs = container?.preferences?.state?.value
        val appearance = if (prefs == null) {
            Appearance()
        } else {
            Appearance(prefs.mode, prefs.colorSource, prefs.accent, wallpaperPalette = prefs.wallpaperPalette)
        }
        val view = ComposeView(app).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            ViewCompat.setAccessibilityPaneTitle(this, "Reminder")
            setContent {
                NormPixTheme(appearance) {
                    var drag by remember { mutableStateOf(0f) }
                    // Starts out of sight and slides in, rather than being there on the first frame.
                    var arrived by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { arrived = true }
                    AnimatedVisibility(
                        visible = arrived && shown.visible,
                        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(200)),
                        exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(160)),
                    ) {
                        ReminderAlertCard(
                            reminder = reminder,
                            onAction = { action ->
                                dismissNow(animate = true)
                                onAction(action)
                            },
                            // A swipe up puts it away: the drag state only reads the direction, the card does not follow the finger.
                            modifier = Modifier.draggable(
                                orientation = Orientation.Vertical,
                                state = rememberDraggableState { delta ->
                                    drag += delta
                                    if (drag < -SWIPE_AWAY_PX) {
                                        drag = 0f
                                        dismissNow(animate = true)
                                    }
                                },
                                onDragStopped = { drag = 0f },
                            ),
                        )
                    }
                }
            }
        }
        shown = Shown(view, owner, windows)
        val metrics = windows.windowMetrics(app)
        val density = app.resources.displayMetrics.density
        val margin = (MARGIN_DP * density).roundToInt()
        val width = minOf(metrics.width - 2 * margin, (MAX_WIDTH_DP * density).roundToInt())
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Never focusable (the app underneath keeps its keyboard and Back) and outside touches go through to it.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = metrics.topInset + margin / 2
            title = "NormPix reminder"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val added = runCatching { windows.addView(view, params) }.isSuccess
        if (!added) {
            owner.destroy()
            return
        }
        current = shown
        shown.timeout = Runnable { dismissNow(animate = true) }.also { main.postDelayed(it, SHOWN_FOR_MS) }
    }

    private fun dismissNow(animate: Boolean) {
        val shown = current ?: return
        current = null
        shown.timeout?.let(main::removeCallbacks)
        fun remove() {
            runCatching { shown.windows.removeViewImmediate(shown.view) }
            shown.owner.destroy()
        }
        if (animate) {
            shown.visible = false
            shown.removal = Runnable(::remove).also { main.postDelayed(it, EXIT_MS) }
        } else {
            remove()
        }
    }

    private const val MARGIN_DP = 12f
    private const val MAX_WIDTH_DP = 460f
    private const val SWIPE_AWAY_PX = 60f
    private const val EXIT_MS = 240L

    private class Metrics(val width: Int, val topInset: Int)

    private fun WindowManager.windowMetrics(context: Context): Metrics {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val m = currentWindowMetrics
            val bars = m.windowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Metrics(m.bounds.width(), bars.top)
        }
        val res = context.resources
        val id = res.getIdentifier("status_bar_height", "dimen", "android")
        return Metrics(res.displayMetrics.widthPixels, if (id > 0) res.getDimensionPixelSize(id) else 0)
    }

    /** A lifecycle for a window that has no Activity: always resumed until the window goes. */
    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)

        init {
            saved.performAttach()
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
