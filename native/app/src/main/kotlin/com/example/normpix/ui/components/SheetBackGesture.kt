package com.example.normpix.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import com.example.normpix.navigation.BackGesture
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * The back gesture on a sheet or a decision, as it is everywhere else in the app (owner, 2026-11-11: "same applies to the modals and
 * sheets too"; see [BackGesture]): the finger plays the start of the sheet's own close and letting go plays the rest. Material's own
 * way - the sheet shrinking a little where it stands, whatever the app's sheet style - is what this takes the place of.
 *
 * The close a sheet has is being drawn down, so that is what the finger does to it: the sheet is moved down by as much of its way out
 * as [BackGesture.eased] of the gesture's progress, **as a drag would move it** - through the nested scroll its own content gives it
 * (the public way in: Material's sheet takes what its content leaves of a scroll). So everything a drag has, this has: the sheet's
 * style follows it (`sheetMotion` draws the sheet from where it is), and so does the blur of the page behind. Letting go calls
 * [onBack], which closes it for good from where it is; giving the gesture up puts it back where it rested.
 *
 * It is a back handler in the sheet's own window and newer than Material's, so it is asked first. A form that has to ask before it
 * closes registers one newer still (`SheetDismissConfirmation`), which is asked before this: such a sheet is not moved at all.
 * [enabled] false (animations removed) leaves back to Material.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Modifier.sheetBackGesture(state: SheetState, enabled: Boolean, onBack: suspend () -> Unit): Modifier {
    val dispatcher = remember { NestedScrollDispatcher() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled = enabled) { progress ->
        // From where the sheet is to out of its window: Material lays it out at the top and draws it its offset lower.
        val top = runCatching { state.requireOffset() }.getOrNull()
        val travel = if (top == null) 0f else (view.rootView.height - top).coerceAtLeast(0f)
        // Where it rests, to put it back if the gesture is given up: all the way open, or half way.
        val rest = state.currentValue
        var moved = 0f
        try {
            progress.collect { event ->
                val to = BackGesture.eased(event.progress) * travel
                dispatcher.dispatchPostScroll(Offset.Zero, Offset(0f, to - moved), NestedScrollSource.UserInput)
                moved = to
            }
            back()
        } catch (_: CancellationException) {
            // Given up: back to where it rested. Said outright, not left to the sheet to settle: once it is more than a thumb's
            // width (56 dp) from its place Material settles it by where it is - which here would be away, closing a sheet whose
            // gesture was taken back (seen in SheetBackTest; an upward fling does not change that, a fling counts only in the
            // direction of the drag).
            scope.launch {
                runCatching { if (rest == SheetValue.PartiallyExpanded) state.partialExpand() else state.expand() }
            }
        }
    }
    return nestedScroll(NoScroll, dispatcher)
}

/** Takes nothing of a scroll: [sheetBackGesture] is in the nested scroll only to hand the sheet a movement of its own. */
private val NoScroll = object : NestedScrollConnection {}
