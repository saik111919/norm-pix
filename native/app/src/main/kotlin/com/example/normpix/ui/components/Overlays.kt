package com.example.normpix.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import com.example.normpix.navigation.LocalAppPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.SheetAnimationType
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** The least time a [AppOverlays.loading] sheet stays up, so a quick job doesn't flash it up and down. */
internal const val LOADING_MIN_MS = 600L

/**
 * Imperative entry points for the app's transient surfaces - bottom sheets
 * and dialogs - so flows can read like straight-line code:
 *
 * ```
 * val saved = overlays.sheet<Account> { dismiss -> AccountForm(onDone = dismiss) }
 * if (overlays.confirm(title = "Delete?", confirmLabel = "Delete")) ...
 * ```
 *
 * [OverlayHost] renders whatever is pending; requests that are abandoned
 * (their caller left composition) clean themselves up.
 */
class AppOverlays {
    internal class SheetRequest(
        val result: CompletableDeferred<Any?> = CompletableDeferred(),
        /** Tall content opens compact and can be dragged to full height; otherwise it opens at once at its full height. */
        val expandable: Boolean = false,
        val guard: SheetDismissGuard = SheetDismissGuard(),
        val content: @Composable (dismiss: (Any?) -> Unit) -> Unit,
    )

    internal class DialogRequest(
        val result: CompletableDeferred<Boolean> = CompletableDeferred(),
        val title: String,
        val message: String?,
        val confirmLabel: String,
        val cancelLabel: String?,
        val destructive: Boolean,
        val icon: ImageVector?,
    )

    internal val sheets = mutableStateListOf<SheetRequest>()
    internal val dialogs = mutableStateListOf<DialogRequest>()

    /** Whether any sheet or decision is open (or still closing); [AppSnackbars] waits for none to be. */
    val isOpen: Boolean get() = sheets.isNotEmpty() || dialogs.isNotEmpty()

    private val blurShares = mutableStateMapOf<Any, Float>()

    /** The overlays blurring what is behind them, bottom to top: each one opened over the ones before it. */
    private val blurStack = mutableStateListOf<Any>()

    /**
     * How strongly the page behind is blurred, 0..1: the largest share any open sheet has on screen (it follows the sheet
     * as it rises, is dragged and falls; see [blursBackdrop]) or any open dialog has faded in. The app's root blurs the
     * page it draws by this.
     */
    val backdropBlur: Float get() = blurShares.values.maxOrNull() ?: 0f

    /**
     * How strongly [owner]'s own content is blurred, 0..1: by whatever has opened over it - a sheet over a sheet, a picker
     * over a form - the same way the page is blurred under them all. 0 with nothing above it.
     */
    fun blurAbove(owner: Any): Float {
        val at = blurStack.indexOf(owner)
        if (at < 0) return 0f
        var most = 0f
        for (i in at + 1 until blurStack.size) most = maxOf(most, blurShares[blurStack[i]] ?: 0f)
        return most
    }

    /** [owner]'s share of [backdropBlur], 0..1. The first share an overlay reports puts it on top of the others. */
    fun setBackdropBlur(owner: Any, share: Float) {
        if (owner !in blurShares) blurStack += owner
        blurShares[owner] = share.coerceIn(0f, 1f)
    }

    fun clearBackdropBlur(owner: Any) {
        blurShares.remove(owner)
        blurStack.remove(owner)
    }

    /**
     * Shows a modal bottom sheet; resolves with the value passed to `dismiss`, or null if dismissed.
     * Every sheet has a drag handle and dismisses by swipe, scrim tap or back; set [expandable] for
     * content that may be taller than half the screen (it opens compact, then drags to full height).
     */
    suspend fun <T> sheet(expandable: Boolean = false, content: @Composable (dismiss: (T?) -> Unit) -> Unit): T? {
        @Suppress("UNCHECKED_CAST")
        val request = SheetRequest(expandable = expandable, content = { dismiss -> content { value -> dismiss(value) } })
        sheets += request
        try {
            @Suppress("UNCHECKED_CAST")
            return request.result.await() as T?
        } finally {
            sheets -= request
        }
    }

    /**
     * A sheet that says what is going on ([title], [message]) while [work] runs, where a snackbar would sit under any sheet
     * open at the time. It closes by itself when the work is done and gives back what it returned (or throws what it
     * threw). Cancel, a swipe, the scrim or back stop the work and give back null. A quick job keeps it up for
     * [LOADING_MIN_MS], so it doesn't flash.
     *
     * The work is the caller's, not the sheet's: however the sheet closes, the work has ended by the time this returns. So
     * whatever the work left behind (a picture it copied) is all there for the caller to keep or clean up - even when
     * the work finished in the moment between Cancel and the sheet going.
     */
    suspend fun <T : Any> loading(
        title: String,
        message: String? = null,
        /**
         * False for work that changes data and must finish once it has begun (checking and repairing the ledger, restoring a
         * backup): the sheet has no Cancel and can't be swiped or tapped away, and if the app closes it all the same (locking
         * closes every sheet) the work still runs to its end and its result is given back.
         */
        cancellable: Boolean = true,
        work: suspend () -> T,
    ): T? = coroutineScope {
        val running = async {
            try {
                Outcome(if (cancellable) work() else withContext(NonCancellable) { work() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome(failure = e)
            }
        }
        val shownOutcome = try {
            sheet<Outcome<T>> { done ->
                if (!cancellable) SheetDismissConfirmation(needed = true) { false }
                LoadingSheetContent(title, message, onCancel = if (cancellable) ({ done(null) }) else null)
                LaunchedEffect(Unit) {
                    val result = coroutineScope {
                        val shown = launch { delay(LOADING_MIN_MS) }
                        val result = running.await()
                        shown.join()
                        result
                    }
                    done(result)
                }
            }
        } finally {
            // Closed by Cancel, a swipe, the scrim or back: stop the work. This scope waits for it to end either way.
            if (cancellable) running.cancel()
        }
        // Work that can't be stopped is waited for, however the sheet went.
        val outcome = shownOutcome ?: if (cancellable) null else running.await()
        outcome?.failure?.let { throw it }
        outcome?.value
    }

    private class Outcome<T : Any>(val value: T? = null, val failure: Exception? = null)

    /** A yes/no decision. Resolves true only when the confirm button is pressed. */
    suspend fun confirm(
        title: String,
        message: String? = null,
        confirmLabel: String,
        cancelLabel: String = "Cancel",
        destructive: Boolean = false,
        icon: ImageVector? = null,
    ): Boolean {
        val request = DialogRequest(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
            destructive = destructive,
            icon = icon,
        )
        dialogs += request
        try {
            return request.result.await()
        } finally {
            dialogs -= request
        }
    }

    /**
     * Cancels every open sheet and dialog, as if each were dismissed. Used when
     * the app locks, so nothing (a card form, a revealed number) stays on top
     * of the lock screen.
     */
    fun dismissAll() {
        for (request in sheets.toList()) request.result.complete(null)
        for (request in dialogs.toList()) request.result.complete(false)
    }

    /** An acknowledge-only dialog ("OK"). */
    suspend fun alert(title: String, message: String, icon: ImageVector? = null, okLabel: String = "OK") {
        val request = DialogRequest(
            title = title,
            message = message,
            confirmLabel = okLabel,
            cancelLabel = null,
            destructive = false,
            icon = icon,
        )
        dialogs += request
        try {
            request.result.await()
        } finally {
            dialogs -= request
        }
    }
}

/**
 * Lets a sheet's content hold off its own dismissal (swipe down, scrim tap, back) until the user
 * confirms - the way an editor asks before throwing away unsaved changes. See [SheetDismissConfirmation].
 */
class SheetDismissGuard {
    internal var needsConfirmation: () -> Boolean = { false }
    internal var confirm: suspend () -> Boolean = { true }

    /** Set once the sheet is closing for good (saved, confirmed), so the guard stops getting in the way. */
    internal var released = false
    internal var prompting = false
}

private val LocalSheetDismissGuard = compositionLocalOf<SheetDismissGuard?> { null }

/**
 * Inside a sheet: while [needed] is true, a dismissal gesture first runs [confirm] and the sheet only
 * closes if it returns true. Closing through the sheet's own `dismiss` callback is never held up.
 */
@Composable
fun SheetDismissConfirmation(needed: Boolean, confirm: suspend () -> Boolean) {
    val guard = LocalSheetDismissGuard.current ?: return
    val latestNeeded by rememberUpdatedState(needed)
    val latestConfirm by rememberUpdatedState(confirm)
    DisposableEffect(guard) {
        guard.needsConfirmation = { latestNeeded }
        guard.confirm = { latestConfirm() }
        onDispose {
            guard.needsConfirmation = { false }
            guard.confirm = { true }
        }
    }
}

val LocalOverlays = compositionLocalOf<AppOverlays> { error("No AppOverlays provided") }

/** A scope that outlives any single screen: flows that must finish (and report) even if their trigger leaves composition. */
val LocalAppScope = compositionLocalOf<CoroutineScope> { error("No app CoroutineScope provided") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayHost(overlays: AppOverlays) {
    val preferences = LocalAppPreferences.current
    val prefsState = preferences?.state?.collectAsState()
    val sheetBlurEnabled = prefsState?.value?.sheetBlurEnabled ?: true
    // Sheets and decisions open and close in the style chosen for them (Settings > Appearance > Transitions; see
    // sheetMotion) - apart from the pages' style - and with no motion of their own when the system asks for reduced motion.
    val sheetStyle = prefsState?.value?.sheetAnimation ?: SheetAnimationType.Window
    val sheetMotionOn = !rememberReducedMotion()

    for (request in overlays.sheets) {
        key(request) {
            val scope = rememberCoroutineScope()
            val guard = request.guard
            val stateRef = remember { arrayOfNulls<SheetState>(1) }
            // A tall sheet stops half way only when it does not fit on the screen; one that fits opens all the way, like
            // any other. Decided once, from its first measure - before it starts to rise - so it never changes on screen.
            var fits by remember { mutableStateOf<Boolean?>(null) }
            val handleHeight = remember { intArrayOf(0) }
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = !request.expandable || fits == true,
                // Every way of closing asks here first. A held-up close snaps the sheet back while the guard asks.
                confirmValueChange = { target ->
                    if (target != SheetValue.Hidden || guard.released || !guard.needsConfirmation()) {
                        true
                    } else {
                        if (!guard.prompting) {
                            guard.prompting = true
                            scope.launch {
                                try {
                                    if (guard.confirm()) {
                                        guard.released = true
                                        runCatching { stateRef[0]?.hide() }
                                        request.result.complete(null)
                                    }
                                } finally {
                                    guard.prompting = false
                                }
                            }
                        }
                        false
                    }
                },
            )
            stateRef[0] = sheetState
            val surface = rememberSheetSurface(enabled = sheetBlurEnabled)
            val hold = remember { SheetHold() }
            // Under a sheet or picker opened over it, this sheet is blurred like the page under them all.
            val above = rememberEasedBlur { overlays.blurAbove(request) }
            ModalBottomSheet(
                onDismissRequest = { request.result.complete(null) },
                sheetState = sheetState,
                // At full height a sheet stops under the status bar, which stays over the page (blurred, as around the sheet).
                // The padding looks up the insets of the window it ends up in - the sheet's own - unlike a WindowInsets
                // read here, in the page's window.
                modifier = Modifier.statusBarsPadding().sheetMotion(sheetState, sheetStyle, sheetMotionOn, hold),
                shape = SheetShape,
                containerColor = surface.container,
                contentColor = surface.content,
                scrimColor = surface.scrim,
                contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) },
                dragHandle = {
                    SheetDragHandle(Modifier.onSizeChanged { handleHeight[0] = it.height }.blurredBy(above), surface.handle)
                },
            ) {
                SheetWindowChrome()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .blursBackdrop(overlays, request, surface.blurBehind, hold)
                        .then(
                            if (request.expandable && fits == null) {
                                Modifier.measuresFit({ handleHeight[0] }) { fits = it }
                            } else {
                                Modifier
                            },
                        )
                        .blurredBy(above, TileMode.Decal)
                        .windowInsetsPadding(
                            WindowInsets.navigationBars
                                .union(WindowInsets.ime)
                                .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        ),
                ) {
                    SeeThroughColors(surface.blurBehind, surface.ground) {
                        CompositionLocalProvider(LocalSheetDismissGuard provides guard) {
                            request.content { value ->
                                scope.launch {
                                    guard.released = true
                                    runCatching { sheetState.hide() }
                                    request.result.complete(value)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    // Decisions are sheets too (not dialogs): one kind of surface, anchored where the thumb is, easier to reach and to
    // hear for a screen reader. Swipe, scrim and back all mean "no". Listed after the sheets, so one that asks "discard
    // this entry?" comes up over the form it is asking about.
    for (request in overlays.dialogs) {
        key(request) {
            val scope = rememberCoroutineScope()
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            fun answer(value: Boolean) {
                scope.launch {
                    runCatching { sheetState.hide() }
                    request.result.complete(value)
                }
            }
            val surface = rememberSheetSurface(enabled = sheetBlurEnabled)
            val hold = remember { SheetHold() }
            val above = rememberEasedBlur { overlays.blurAbove(request) }
            ModalBottomSheet(
                onDismissRequest = { request.result.complete(false) },
                sheetState = sheetState,
                // At full height a sheet stops under the status bar, which stays over the page (blurred, as around the sheet).
                // The padding looks up the insets of the window it ends up in - the sheet's own - unlike a WindowInsets
                // read here, in the page's window.
                modifier = Modifier.statusBarsPadding().sheetMotion(sheetState, sheetStyle, sheetMotionOn, hold),
                shape = SheetShape,
                containerColor = surface.container,
                contentColor = surface.content,
                scrimColor = surface.scrim,
                contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal) },
                dragHandle = { SheetDragHandle(Modifier.blurredBy(above), surface.handle) },
            ) {
                SheetWindowChrome()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .blursBackdrop(overlays, request, surface.blurBehind, hold)
                        .blurredBy(above, TileMode.Decal)
                        .windowInsetsPadding(
                            WindowInsets.navigationBars
                                .union(WindowInsets.ime)
                                .union(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        ),
                ) {
                    SeeThroughColors(surface.blurBehind, surface.ground) {
                        ConfirmSheetContent(
                            title = request.title,
                            message = request.message,
                            confirmLabel = request.confirmLabel,
                            cancelLabel = request.cancelLabel,
                            destructive = request.destructive,
                            icon = request.icon,
                            onConfirm = { answer(true) },
                            onCancel = { answer(false) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Short, non-blocking news of something that just happened ("Card added", "Couldn't unlock"): an [AppSnackbar] that
 * floats clear of the navigation bar, says with its badge what kind of news it is ([SnackbarKind]), can be swiped away,
 * and is announced by TalkBack.
 *
 * Never under a sheet or a decision ([heldBack]), where the scrim would dim it and the sheet cover it. One asked for while
 * one is open - a form's "Changes saved", said as it closes - shows once it has gone. One already up when a sheet opens
 * waits under it, paused, and comes back when it closes.
 */
class AppSnackbars(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
    /** True while a sheet or decision is open ([AppOverlays.isOpen]). */
    val heldBack: () -> Boolean = { false },
) {
    /** Space the current screen reserves at the bottom (e.g. the navigation bar). */
    var bottomInset: Dp by mutableStateOf(0.dp)

    fun show(
        message: String,
        kind: SnackbarKind = SnackbarKind.Done,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
        /** Called when it goes without its action being used: timed out, swiped away, or replaced by the next one. */
        onDismiss: (() -> Unit)? = null,
    ) {
        scope.launch {
            var result = SnackbarResult.Dismissed
            try {
                snapshotFlow(heldBack).first { held -> !held }
                hostState.currentSnackbarData?.dismiss()
                result = hostState.showSnackbar(AppSnackbarVisuals(message, actionLabel, kind))
            } finally {
                if (result == SnackbarResult.ActionPerformed) onAction?.invoke() else onDismiss?.invoke()
            }
        }
    }

    /**
     * Says [message] with an Undo for the few seconds the snackbar shows: [undo] takes the change back if it is used;
     * [settle] makes the change final once it has gone without being used.
     */
    fun showUndoable(message: String, undo: () -> Unit, settle: () -> Unit = {}, kind: SnackbarKind = SnackbarKind.Deleted) =
        show(message, kind, actionLabel = "Undo", onAction = undo, onDismiss = settle)
}

val LocalSnackbars = compositionLocalOf<AppSnackbars> { error("No AppSnackbars provided") }

@Composable
fun SnackbarOverlay(snackbars: AppSnackbars, modifier: Modifier = Modifier) {
    // Gone while a sheet or decision is open. The host times what it shows, so a snackbar it was showing isn't used up
    // unseen under the sheet: it comes back, for its whole time, once the sheet has closed.
    if (snackbars.heldBack()) return
    // Full size so BottomCenter means the bottom of the screen (it has no input handlers, so touches pass through).
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(
            hostState = snackbars.hostState,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = snackbars.bottomInset),
            snackbar = { data -> AppSnackbar(data) },
        )
    }
}

/**
 * Haptic vocabulary. Deliberately small: haptics mark meaningful moments (a
 * choice made, a task finished, something destroyed) - never plain
 * navigation taps.
 */
class AppHaptics(private val feedback: HapticFeedback) {
    /** A value was picked: toggles, segments, chips, card flip. */
    fun selection() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** Something was completed or saved. */
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** A destructive action was confirmed, or a physical event (NFC tag) was detected. */
    fun strong() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { AppHaptics(feedback) }
}

/** Marks content that updates on its own (progress, errors) so TalkBack announces changes politely. */
fun Modifier.politeLiveRegion(): Modifier = semantics { liveRegion = LiveRegionMode.Polite }

/**
 * Reports, from this sheet content's measure, whether the whole sheet - its handle ([handleHeight]) and this content - fits
 * on the screen below the status bar (and above the keyboard). Content that does not fit is held to the space there is,
 * so it comes out exactly as tall as that, never shorter.
 */
@Composable
private fun Modifier.measuresFit(handleHeight: () -> Int, onMeasured: (Boolean) -> Unit): Modifier {
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density)
    val bottom = WindowInsets.ime.getBottom(density)
    return onGloballyPositioned { content ->
        val handle = handleHeight()
        if (handle <= 0 || content.size.height <= 0) return@onGloballyPositioned
        var root = content
        while (true) root = root.parentLayoutCoordinates ?: break
        onMeasured(sheetFits(windowHeight = root.size.height, top = top, bottom = bottom, sheetHeight = handle + content.size.height))
    }
}

/** Whether a sheet [sheetHeight] tall fits in a window [windowHeight] tall, less [top] and [bottom] (status bar, keyboard). */
internal fun sheetFits(windowHeight: Int, top: Int, bottom: Int, sheetHeight: Int): Boolean =
    sheetHeight < windowHeight - top - bottom
