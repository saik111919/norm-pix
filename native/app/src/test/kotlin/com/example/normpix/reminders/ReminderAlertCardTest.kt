package com.example.normpix.reminders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.ui.theme.NormPixTheme
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The reminder card (floating over another app, or on its own over a lock screen): Snooze, Dismiss and Open are one connected
 * button group, and a note the reminder has is shown - making the card taller - only where the caller says the screen is the
 * owner's to read.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ReminderAlertCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 11, 5, 9, 0)
    private val actions = mutableStateListOf<ReminderCardAction>()
    private var notes by mutableStateOf<String?>(null)
    private var showNotes by mutableStateOf(true)

    private fun reminder() = Reminder(
        id = 7,
        title = "Pay electricity bill",
        notes = notes,
        category = ReminderCategory.Payments,
        dueAt = now,
        repeatRule = RepeatRule.Once,
        createdAt = now,
        updatedAt = now,
    )

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                NormPixTheme(Appearance(ThemeMode.Dark, ColorSource.Custom, AccentColor.Orange)) {
                    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopCenter) {
                        ReminderAlertCard(
                            reminder = reminder(),
                            onAction = { actions += it },
                            modifier = Modifier.testTag("card"),
                            now = now,
                            showNotes = showNotes,
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun cardHeight() = compose.onNodeWithTag("card").getUnclippedBoundsInRoot().let { (it.bottom - it.top).value }

    @Test
    fun snoozeDismissAndOpenAreOneConnectedGroup() {
        show()
        val bounds = listOf("Snooze", "Dismiss", "Open app").map { compose.onNodeWithText(it).getUnclippedBoundsInRoot() }
        // The group's height - taller than the 48 dp they were - and joined: a hair apart (the group's own 2 dp), not spread out.
        for (b in bounds) assertEquals(52f, (b.bottom - b.top).value, 6f, "the segments are the group's height: $b")
        for (i in 0 until bounds.size - 1) {
            assertTrue((bounds[i + 1].left - bounds[i].right).value in -1f..4f, "joined, not spread: $bounds")
            assertEquals(bounds[i].top.value, bounds[i + 1].top.value, 1f, "in one row")
        }
    }

    @Test
    fun eachPartDoesWhatItSays() {
        show()
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Open app").performClick()
        assertEquals(listOf(ReminderCardAction.Dismiss, ReminderCardAction.Open), actions.toList())
        compose.onNodeWithText("Snooze").performClick()
        compose.waitForIdle()
        // Snooze opens its three choices, in the same kind of group, and a choice snoozes by that much.
        compose.onNodeWithText("Snooze for").assertExists()
        compose.onNodeWithText("1 hour").performClick()
        assertEquals(ReminderCardAction.Snooze(Duration.ofHours(1)), actions.last())
    }

    @Test
    fun aNoteIsShownWhereTheCallerAllowsIt_andTheCardGrowsToHoldIt() {
        show()
        val without = cardHeight()
        compose.onAllNodesWithText("Cash only", substring = true).assertCountEquals(0)

        notes = "Cash only. Take the old bill with the meter reading and ask for a receipt at the counter."
        compose.waitForIdle()
        compose.onNodeWithText("Cash only", substring = true).assertExists()
        assertTrue(cardHeight() > without + 20f, "a note makes the card taller ($without -> ${cardHeight()})")
    }

    @Test
    fun aVeryLongNoteIsCutAtAFewLinesSoTheCardStaysAnAlertNotAPage() {
        notes = "A note. ".repeat(80)
        show()
        val long = cardHeight()
        notes = "A note. ".repeat(40)
        compose.waitForIdle()
        assertEquals(long, cardHeight(), 1f, "past its limit more words add no height")
    }

    @Test
    fun whereTheScreenMayBeSeenByOthersTheNoteStaysOut() {
        notes = "Account ending 1234 owes more than usual"
        showNotes = false
        show()
        compose.onAllNodesWithText("Account ending", substring = true).assertCountEquals(0)
    }

    @Test
    fun noNoteMeansNoRoomIsLeftForOne() {
        show()
        val plain = cardHeight()
        notes = "   "
        compose.waitForIdle()
        assertEquals(plain, cardHeight(), 0.5f, "blank notes are no notes")
    }
}
