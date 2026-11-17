package com.example.normpix.quickadd

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.normpix.AppContainer
import com.example.normpix.finance.ui.FinanceActions
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.LocalReminderLauncher
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.ProvideAppEnvironment
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalPageBehindToBlur
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.theme.AppFont
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import com.example.normpix.ui.theme.rememberCustomFontFiles
import com.example.normpix.wallet.CardsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** What the system's prompt says when a locked app is asked for from the overlay. */
internal const val OVERLAY_UNLOCK_REASON = "Unlock NormPix"

/** How often the overlay looks, once its sheet has closed, for what the add flow still has to finish (a snackbar, an Undo). */
private const val WIND_DOWN_POLL_MS = 100L

/** The most the overlay waits, with no sheet open, for work the app scope still has (a snackbar is a few seconds; an Undo, less). */
private const val WIND_DOWN_MAX_MS = 20_000L

/** The most the overlay waits for the Wallet's cards to be read, before a shared receipt is matched to an account by their numbers. */
private const val CARDS_WAIT_MS = 3_000L

/**
 * The quick add overlay's whole screen, drawn by [QuickAddActivity] over whatever app the user is in: the user's theme, the app's
 * own sheets and snackbars, and one flow - the requests in [requests], one after the other - which ends in [onFinished].
 *
 * It is behind the app's lock exactly as the main screen is: while the app is locked nothing of the ledger is composed, and the
 * system's prompt is asked for at once; a prompt that fails or is cancelled finishes the overlay with nothing shown. If the lock
 * engages while the overlay is open (the user went away and came back after the timeout) the sheet goes and so does the overlay.
 */
@Composable
fun QuickAddHost(container: AppContainer, requests: QuickAddRequests, onFinished: () -> Unit) {
    val prefs by container.preferences.state.collectAsStateWithLifecycle()
    val customFont = rememberCustomFontFiles(prefs.interfaceFont, prefs.customFontRevision)
    NormPixTheme(
        Appearance(
            prefs.mode,
            prefs.colorSource,
            prefs.accent,
            AppFont(prefs.interfaceFont, prefs.fontShape, customFont?.file, customFont?.faces.orEmpty()),
            prefs.wallpaperPalette,
        ),
        // See-through: the app the user is in shows behind the sheet, dimmed by the sheet's own scrim.
        paintWindow = false,
    ) {
        val lockVersion by container.lock.version.collectAsStateWithLifecycle()
        val locked = remember(prefs, lockVersion) { container.lock.isAppLocked(prefs) }
        var wasOpen by remember { mutableStateOf(false) }
        LaunchedEffect(locked) {
            if (!locked) {
                wasOpen = true
            } else if (wasOpen) {
                // The lock engaged while the overlay was open: what was on screen is gone, and so is the overlay.
                onFinished()
            } else if (!container.lock.authenticate(OVERLAY_UNLOCK_REASON)) {
                onFinished()
            }
        }
        // Not composed at all while locked: no sheet, no ledger.
        if (!locked) QuickAddFlow(container, requests, onFinished)
    }
}

@Composable
private fun QuickAddFlow(container: AppContainer, requests: QuickAddRequests, onFinished: () -> Unit) {
    // Over another app there is no NormPix page to blur: the sheets are solid (as with blur off), the other app dimmed behind them.
    CompositionLocalProvider(LocalPageBehindToBlur provides false) { QuickAddEnvironment(container, requests, onFinished) }
}

@Composable
private fun QuickAddEnvironment(container: AppContainer, requests: QuickAddRequests, onFinished: () -> Unit) {
    ProvideAppEnvironment(container) {
        val context = LocalContext.current
        val finish by rememberUpdatedState(onFinished)
        val navigator = remember(context) { QuickAddNavigator(context) { finish() } }
        // What NormPixRoot provides above its pages and the add flow reads: the navigator (the shared actions need one) and the
        // reminder launcher (a new repeating payment is given its reminder).
        CompositionLocalProvider(LocalNavigator provides navigator) {
            CompositionLocalProvider(LocalReminderLauncher provides rememberReminderFlows()) {
                RunRequests(container, requests, onFinished)
            }
        }
    }
}

/**
 * Does the requests one after the other, then waits for what is left to finish - the snackbar a save said, with its Undo - and
 * ends. Touching the empty screen during that wait ends it at once (the change is saved already; Undo is only for the few seconds).
 */
@Composable
private fun RunRequests(container: AppContainer, requests: QuickAddRequests, onFinished: () -> Unit) {
    val actions = rememberFinanceActions()
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val appScope = LocalAppScope.current
    val finish by rememberUpdatedState(onFinished)
    var windingDown by remember { mutableStateOf(false) }
    LaunchedEffect(container) {
        if (!openLedger(container, overlays)) {
            finish()
            return@LaunchedEffect
        }
        val flow = QuickAddFlowRunner(container, actions, overlays, snackbars)
        // How long the wind-down has had nothing open: a failsafe, so work that never ends can't leave an invisible screen over the phone.
        var quietMs = 0L
        while (true) {
            val next = requests.takeNext()
            if (next != null) {
                windingDown = false
                quietMs = 0L
                flow.run(next)
                requests.finished()
            } else if (appScope.hasWork() && quietMs <= WIND_DOWN_MAX_MS) {
                windingDown = true
                quietMs = if (overlays.isOpen) 0L else quietMs + WIND_DOWN_POLL_MS
                delay(WIND_DOWN_POLL_MS)
            } else {
                break
            }
        }
        finish()
    }
    // Only a touch on the empty screen, only once the sheet is gone: while a sheet is open its own scrim takes touches.
    Box(
        Modifier
            .fillMaxSize()
            .then(if (windingDown) Modifier.pointerInput(Unit) { detectTapGestures { finish() } } else Modifier),
    )
}

/**
 * Makes the ledger ready before the first sheet opens, in a process where the main screen never ran: the ledger, the reminders (a new
 * repeating payment links one) and, briefly, the Wallet's cards (a receipt's account number is matched against them). Each is read
 * only if it has not been already. False, after saying so, when the ledger can't be opened.
 */
private suspend fun openLedger(container: AppContainer, overlays: AppOverlays): Boolean {
    container.finance.ensureLoaded()
    if (container.finance.state.value.status != FinanceStatus.Ready) {
        overlays.alert("Couldn't open your data", "Your data is safe on this device. Try again.")
        return false
    }
    try {
        container.reminders.ensureLoaded()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Reminders are only extra here: a repeating payment is saved without its reminder if they can't be read.
    }
    withTimeoutOrNull(CARDS_WAIT_MS) { container.cards.state.first { it !is CardsState.Loading } }
    return true
}

/** Whether the app's flows still have anything to do: a sheet, a snackbar waiting or showing, an Undo being done. */
private fun CoroutineScope.hasWork(): Boolean = coroutineContext[Job]?.children?.any { it.isActive } == true

/** What each kind of request does, the same as the app does it: the same sheets, through the same [FinanceActions]. */
private class QuickAddFlowRunner(
    private val container: AppContainer,
    private val actions: FinanceActions,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
) {
    /** Does [request] to its end: the sheet has closed (saved, discarded or dismissed) when this returns. */
    suspend fun run(request: QuickAddRequest) {
        when (request) {
            is QuickAddRequest.Add -> {
                if (leavesSampleData("What you add goes into your own data, not the sample set, which is thrown away. Turn sample data off to add it?")) {
                    actions.addTransaction(request.type).join()
                }
            }
            is QuickAddRequest.Share -> {
                if (leavesSampleData("A shared transaction goes into your own data, not the sample set, which is thrown away. Turn sample data off to add it?")) {
                    actions.addFromShare(request.text, request.picture, container.sharedPictures).join()
                } else {
                    snackbars.show("Shared transaction not added", SnackbarKind.Info)
                }
            }
        }
    }

    /**
     * Never into the sample data: while it is on, asks to switch to the user's own data first. True when there is nothing to ask (it
     * is off) or the user turned it off - the overlay always works on the user's own data, so the sheet then opens on it; false when
     * the user would rather not, and the request is dropped.
     */
    private suspend fun leavesSampleData(message: String): Boolean {
        val preferences = container.preferences
        if (!preferences.state.value.dummyDataEnabled) return true
        val switch = overlays.confirm(title = "Sample data is on", message = message, confirmLabel = "Turn off sample data")
        if (switch) preferences.setDummyDataEnabled(false)
        return switch
    }
}
