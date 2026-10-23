package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.normpix.ui.components.AppSnackbarVisuals
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.SnackbarOverlay
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app's snackbar says what kind of news it brings, and can be swiped away. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SnackbarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val snackbars = AppSnackbars(SnackbarHostState(), scope)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun host() {
        compose.setContent { NormPixTheme { SnackbarOverlay(snackbars) } }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aMessageCarriesItsKind() {
        host()
        compose.runOnUiThread { snackbars.show("Couldn't unlock. Try again.", SnackbarKind.Error) }
        compose.waitUntil(10_000) { shown("Couldn't unlock. Try again.") }
        val visuals = snackbars.hostState.currentSnackbarData?.visuals as AppSnackbarVisuals
        assertEquals(SnackbarKind.Error, visuals.kind)
    }

    @Test
    fun aMessageIsDoneUnlessToldOtherwise() {
        host()
        compose.runOnUiThread { snackbars.show("Card added") }
        compose.waitUntil(10_000) { shown("Card added") }
        val visuals = snackbars.hostState.currentSnackbarData?.visuals as AppSnackbarVisuals
        assertEquals(SnackbarKind.Done, visuals.kind)
    }

    @Test
    fun aSwipeSendsItAway() {
        host()
        compose.runOnUiThread { snackbars.show("Card added") }
        compose.waitUntil(10_000) { shown("Card added") }

        val swiped = compose.mainClock.currentTime
        compose.onNodeWithText("Card added").performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { !shown("Card added") }
        // Gone because of the swipe, not because its time ran out (a short snackbar stays up for 4 s).
        assertTrue(compose.mainClock.currentTime - swiped < 4_000, "swiped away")
    }
}
