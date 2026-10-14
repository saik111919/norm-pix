package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.components.SnackbarOverlay
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the user sees while something takes a moment, and short messages around sheets: a loading sheet says what is
 * going on and goes by itself (or with Cancel, which stops the work); a snackbar never shows under a sheet or decision,
 * where it would be dimmed and covered.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LoadingSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val overlays = AppOverlays()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val snackbars = AppSnackbars(SnackbarHostState(), scope, heldBack = { overlays.isOpen })

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun host() {
        compose.setContent {
            NormPixTheme {
                SnackbarOverlay(snackbars)
                OverlayHost(overlays)
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(10_000) { shown(text) }

    private fun waitGone(text: String) = compose.waitUntil(10_000) { !shown(text) }

    // A wait on a plain value has to let the main looper run itself.
    private fun until(condition: () -> Boolean) = compose.waitUntil(10_000) {
        compose.waitForIdle()
        condition()
    }

    /** Opens a plain sheet saying [text]; closing it is [close]. */
    private fun openSheet(text: String): () -> Unit {
        val closed = CompletableDeferred<Unit>()
        compose.runOnUiThread {
            scope.launch {
                overlays.sheet<Unit> { dismiss ->
                    Text(text)
                    LaunchedEffect(Unit) {
                        closed.await()
                        dismiss(null)
                    }
                }
            }
        }
        waitFor(text)
        return { closed.complete(Unit) }
    }

    @Test
    fun aLoadingSheetSaysWhatIsGoingOnAndGoesByItselfWithTheResult() {
        host()
        val gate = CompletableDeferred<String>()
        var result: String? = null
        compose.runOnUiThread {
            scope.launch { result = overlays.loading("Reading the picture", "NormPix is reading it on this phone.") { gate.await() } }
        }
        waitFor("Reading the picture")
        compose.onNodeWithText("Reading the picture").assertIsDisplayed()
        compose.onNodeWithText("NormPix is reading it on this phone.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()

        compose.runOnUiThread { gate.complete("read") }
        waitGone("Reading the picture")
        until { result != null }
        assertEquals("read", result)
    }

    @Test
    fun cancelStopsTheWorkAndGivesBackNothing() {
        host()
        var stopped = false
        var finished = false
        var result: String? = "not yet"
        compose.runOnUiThread {
            scope.launch {
                result = overlays.loading("Reading the picture") {
                    try {
                        awaitCancellation()
                    } finally {
                        stopped = true
                    }
                }
                finished = true
            }
        }
        waitFor("Reading the picture")
        compose.onNodeWithText("Cancel").performClick()
        waitGone("Reading the picture")
        until { finished }
        assertNull(result)
        assertTrue(stopped, "the work was stopped")
    }

    @Test
    fun whatTheWorkThrowsComesBackToTheCaller() {
        host()
        var caught: Throwable? = null
        compose.runOnUiThread {
            scope.launch {
                try {
                    overlays.loading<String>("Reading the picture") { error("unreadable") }
                } catch (e: IllegalStateException) {
                    caught = e
                }
            }
        }
        until { caught != null }
        assertEquals("unreadable", caught?.message)
        waitGone("Reading the picture")
    }

    @Test
    fun aMessageAskedForUnderASheetShowsOnceItHasClosed() {
        host()
        val close = openSheet("A form")
        // What a form says as it saves: asked for while the form is still open.
        compose.runOnUiThread { snackbars.show("Changes saved") }
        compose.waitForIdle()
        assertFalse(shown("Changes saved"), "not under the open sheet")
        assertNull(snackbars.hostState.currentSnackbarData, "held until the sheet goes")

        compose.runOnUiThread { close() }
        waitFor("Changes saved")
        assertFalse(shown("A form"))
    }

    @Test
    fun aMessageAlreadyUpWaitsUnderASheetOpenedOverItAndComesBack() {
        host()
        compose.runOnUiThread { snackbars.show("Card added") }
        waitFor("Card added")

        val close = openSheet("A form")
        assertFalse(shown("Card added"), "not under the open sheet")
        compose.runOnUiThread { close() }
        waitFor("Card added")
    }

    @Test
    fun aDecisionHoldsMessagesBackToo() {
        host()
        compose.runOnUiThread { scope.launch { overlays.confirm(title = "Delete this card?", confirmLabel = "Delete") } }
        waitFor("Delete this card?")
        compose.runOnUiThread { snackbars.show("Card deleted") }
        compose.waitForIdle()
        assertFalse(shown("Card deleted"))

        compose.onNodeWithText("Delete").performClick()
        waitFor("Card deleted")
    }
}
