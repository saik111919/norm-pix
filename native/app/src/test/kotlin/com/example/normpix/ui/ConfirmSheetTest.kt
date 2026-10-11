package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Decisions ("Reset all settings?", "Delete this card?", "Discard changes?") are bottom sheets, not dialogs: reachable
 * under the thumb, one kind of surface, and "no" is everything but the confirm button - cancel, dismissing the sheet (swipe, scrim, back), the lock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ConfirmSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val overlays = AppOverlays()
    private var answer: Boolean? = null

    private fun host() {
        compose.setContent { NormPixTheme { OverlayHost(overlays) } }
        compose.waitForIdle()
    }

    private fun ask(destructive: Boolean = true) {
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                answer = overlays.confirm(
                    title = "Reset all settings?",
                    message = "Every preference goes back to its default. Your data isn't touched.",
                    confirmLabel = "Reset",
                    destructive = destructive,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aDecisionIsASheetWithItsQuestionAndBothAnswers() {
        host()
        ask()

        compose.onNodeWithText("Reset all settings?").assertIsDisplayed()
        compose.onNodeWithText("Every preference goes back to its default. Your data isn't touched.").assertIsDisplayed()
        compose.onNodeWithText("Reset").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        // A sheet, not a dialog: it carries the sheet's drag handle.
        assertTrue(compose.onAllNodes(hasContentDescription("Drag handle", substring = true)).fetchSemanticsNodes().isNotEmpty(), "the decision sits on a bottom sheet")
        assertNull(answer, "nothing is decided until the user answers")
    }

    @Test
    fun theConfirmButtonSaysYes() {
        host()
        ask()
        compose.onNodeWithText("Reset").performClick()
        compose.waitUntil(5_000) { answer != null }
        assertEquals(true, answer)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Reset all settings?").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun cancelSaysNo() {
        host()
        ask()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { answer != null }
        assertEquals(false, answer)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Reset all settings?").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun dismissingTheSheetSaysNo() {
        host()
        ask()
        // The route swiping it down, tapping the scrim, back and TalkBack's dismiss gesture all take.
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitUntil(5_000) { answer != null }
        assertEquals(false, answer, "dismissing is a no, never a yes")
    }

    @Test
    fun lockingTheAppSaysNoToEveryOpenDecision() {
        host()
        ask()
        compose.runOnUiThread { overlays.dismissAll() }
        compose.waitUntil(5_000) { answer != null }
        assertEquals(false, answer)
    }

    @Test
    fun anAlertHasOneButtonAndNoCancel() {
        host()
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch { overlays.alert("Backup saved", "Keep the file somewhere safe.") }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Backup saved").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Cancel").fetchSemanticsNodes().isEmpty(), "an acknowledgement has nothing to cancel")
    }
}
