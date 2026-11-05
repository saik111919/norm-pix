package com.example.normpix.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

/** Where the bottom row stands between the middle of the screen and the end of the "+" panel, as a pure rule. */
class DockRowXTest {
    @Test
    fun itIsTheMiddleWhileClosedAndTheEndOnceOpen() {
        assertEquals(30, dockRowX(space = 400, size = 340, progress = 0f, rtl = false))
        assertEquals(60, dockRowX(space = 400, size = 340, progress = 1f, rtl = false))
    }

    @Test
    fun inARightToLeftLayoutTheEndIsTheLeftEdge() {
        assertEquals(30, dockRowX(space = 400, size = 340, progress = 0f, rtl = true))
        assertEquals(0, dockRowX(space = 400, size = 340, progress = 1f, rtl = true))
    }

    @Test
    fun onTheWayItGoesSmoothlyAndNeverLeavesTheTwoPlaces() {
        var last = dockRowX(400, 340, 0f, rtl = false)
        for (i in 1..100) {
            val x = dockRowX(400, 340, i / 100f, rtl = false)
            assertTrue(x >= last && x - last <= 1, "one step of a hundredth moves it at most a pixel: $last -> $x")
            last = x
        }
        // A spring that overshoots, or a value not yet settled, never throws it outside the two places.
        assertEquals(60, dockRowX(400, 340, 1.07f, rtl = false))
        assertEquals(30, dockRowX(400, 340, -0.05f, rtl = false))
    }

    @Test
    fun aRowAsWideAsTheSpaceHasNowhereToGo() {
        assertEquals(0, dockRowX(400, 400, 0.5f, rtl = false))
    }
}

/**
 * The footer when the menu closes, frame by frame on a manual clock: its "+" button goes from under the panel's end to its place in
 * the middle of the screen in small steps, in step with the panel, and does not sit still and then jump when the panel leaves.
 * (A screen recording of the old behaviour showed it standing at the end until the panel was let go and then moving 26 px in one
 * frame.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class DockRowTravelTest {
    @get:Rule
    val compose = createComposeRule()

    private var open by mutableStateOf(true)

    private fun show() {
        val actions = listOf("Expense", "Income", "Recurring", "Reminder", "Card", "Account", "Categories", "Budgets", "People", "Search", "Settings")
            .mapIndexed { i, label -> FooterAction(label, Icons.Rounded.Add, isPrimary = i == 0, onClick = {}) }
        compose.setContent {
            NormPixTheme(Appearance(ThemeMode.Dark, ColorSource.Custom, AccentColor.Orange)) {
                Box(Modifier.fillMaxSize()) {
                    FloatingNavigationRow(
                        // Three places, so the row is narrow enough to have somewhere to travel on a phone's width.
                        destinations = AppDestination.entries.take(3),
                        current = AppDestination.Home,
                        overdueCount = 0,
                        onDestinationSelected = {},
                        actions = actions,
                        isMoreOpen = open,
                        onMoreToggle = { open = !open },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    /** The "+" button's left edge now, whichever way it is labelled (it says "Close actions" while the menu is open). */
    private fun plusLeft(): Float =
        compose.onNode(hasContentDescription("More actions") or hasContentDescription("Close actions")).fetchSemanticsNode().boundsInRoot.left

    @Test
    fun theFooterGlidesToItsPlaceWhenTheMenuCloses_itDoesNotJump() {
        show()
        compose.waitForIdle()
        val whileOpen = plusLeft()

        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            open = false
            Snapshot.sendApplyNotifications()
        }
        val steps = mutableListOf(whileOpen)
        repeat(60) {
            compose.mainClock.advanceTimeByFrame()
            steps += plusLeft()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val closed = plusLeft()

        val travel = whileOpen - closed
        assertTrue(travel > 20f, "the row should have somewhere to travel (open at $whileOpen, closed at $closed)")
        // Never outside the two places, and never going the wrong way.
        for (i in 1 until steps.size) {
            assertTrue(steps[i] <= steps[i - 1] + 0.5f, "frame $i: it went back, ${steps[i - 1]} -> ${steps[i]}")
            assertTrue(steps[i] >= closed - 0.5f, "frame $i: it went past its place, ${steps[i]} < $closed")
        }
        // The old behaviour stood still, then moved the whole way in one frame.
        val biggestStep = (1 until steps.size).maxOf { steps[it - 1] - steps[it] }
        assertTrue(biggestStep < travel * 0.5f, "no frame should carry half the way: the biggest step was $biggestStep of $travel")
        assertTrue(abs(steps.last() - closed) < 1f, "it ends where the settled closed footer is")
    }
}
