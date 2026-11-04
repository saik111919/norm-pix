package com.example.normpix.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * The system Back on an add / edit sheet does what a swipe down does: a clean form closes at once, one with changes asks
 * "Discard this entry?" first, and one that is saving stays. Back goes to the window on top - each sheet is a dialog
 * window of its own - which the semantics Dismiss action the other sheet tests use never passes through: on 2026-11-04
 * Back closed a form with changes on the owner's phone without asking, while a swipe asked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SheetBackTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val overlays = AppOverlays()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dirty by mutableStateOf(false)
    private var saving by mutableStateOf(false)
    private var closed = false
    private var closedWith: String? = null

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    // A wait on a plain value has to let the main looper run itself.
    private fun until(condition: () -> Boolean) = compose.waitUntil(10_000) {
        compose.waitForIdle()
        condition()
    }

    private fun openForm() {
        compose.setContent {
            NormPixTheme {
                CompositionLocalProvider(LocalOverlays provides overlays) { OverlayHost(overlays) }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            scope.launch {
                closedWith = overlays.sheet<String> { dismiss ->
                    EditorSheet(title = "New entry", saveLabel = "Save entry", onSave = { dismiss("saved") }, saving = saving) {
                        Box(Modifier.fillMaxWidth().height(48.dp).testTag(BODY))
                    }
                    DiscardChangesGuard(dirty = dirty, saving = saving, isEditing = false, keptMessage = "")
                }
                closed = true
            }
        }
        until { shown("New entry") }
    }

    /** The window Back goes to: the sheet (or the question over it) on top, not the activity under them. */
    private fun topWindow(): ComponentDialog = ShadowDialog.getShownDialogs().last { it.isShowing } as ComponentDialog

    /** A press of Back (the button, or a gesture let go at once), the way the system hands it to the window on top. */
    private fun pressBack() {
        compose.runOnUiThread { topWindow().onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /** A predictive back gesture swept all the way from the left edge, then let go. */
    private fun backGesture() {
        compose.runOnUiThread {
            val back = topWindow().onBackPressedDispatcher
            back.dispatchOnBackStarted(BackEventCompat(0f, 1_200f, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(150f, 1_200f, 0.5f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(300f, 1_200f, 1f, BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        pressBack()
    }

    private fun bodyWidth() = compose.onNodeWithTag(BODY, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width

    @Test
    fun back_onACleanForm_closesItAtOnce() {
        openForm()
        pressBack()
        until { closed }
        assertNull(closedWith, "closing by Back gives nothing back")
        assertFalse(shown("Discard this entry?"), "a clean form has nothing to ask about")
    }

    @Test
    fun back_onAFormWithChanges_asksFirst_keepEditingKeepsIt_discardClosesIt() {
        openForm()
        dirty = true
        compose.waitForIdle()

        pressBack()
        until { shown("Discard this entry?") }
        assertFalse(closed, "the form stays while it asks")
        compose.onNodeWithText("Keep editing").performClick()
        until { !shown("Discard this entry?") }
        compose.onNodeWithText("New entry").assertIsDisplayed()
        assertFalse(closed, "Keep editing keeps the form")

        pressBack()
        until { shown("Discard this entry?") }
        compose.onNodeWithText("Discard").performClick()
        until { closed }
        assertNull(closedWith)
        until { !shown("New entry") }
    }

    @Test
    fun back_whileSaving_leavesTheFormOpen() {
        openForm()
        dirty = true
        saving = true
        compose.waitForIdle()

        pressBack()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(closed, "a form that is saving can't be closed")
        assertFalse(shown("Discard this entry?"), "and there is nothing to ask while it saves")
        compose.onNodeWithText("New entry").assertIsDisplayed()
    }

    @Test
    fun aBackGesture_onAFormWithChanges_asks_andLeavesTheFormFullSize() {
        openForm()
        val fullWidth = bodyWidth()
        dirty = true
        compose.waitForIdle()

        backGesture()
        until { shown("Discard this entry?") }
        compose.onNodeWithText("Keep editing").performClick()
        until { !shown("Discard this entry?") }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertFalse(closed)
        // Material shrinks a sheet as a back gesture sweeps, to show it closing; one that stays must not stay shrunk.
        assertEquals(fullWidth, bodyWidth(), 0.5f, "the form is back to its full width")
    }

    private companion object {
        const val BODY = "sheet-back-body"
    }
}
