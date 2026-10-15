package com.example.normpix.finance.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.ui.theme.NormPixTheme
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Days are picked from a sheet in the month picker's family: a tap sets the first day and the next the last, a day before
 * the first starts there instead, a ready-made run sets both, and days after the last one offered can't be picked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DateRangeSheetTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 15)
    private var picked: DateRange? = null

    private fun show(initial: DateRange? = null) {
        compose.setContent {
            NormPixTheme { DateRangeSheet(initial = initial, latest = today, onPick = { picked = it }, today = today) }
        }
        compose.waitForIdle()
    }

    private fun day(spoken: String) = compose.onNode(hasContentDescription(spoken, substring = true))

    @Test
    fun theFirstTapAndTheNextAreTheFirstAndLastDay() {
        show()
        day("Saturday, 3 October 2026").performClick()
        day("Thursday, 15 October 2026").performClick()
        compose.onNodeWithText("Show 13 days").performClick()
        assertEquals(DateRange(LocalDate.of(2026, 10, 3).atStartOfDay(), LocalDate.of(2026, 10, 16).atStartOfDay()), picked)
    }

    @Test
    fun aDayBeforeTheFirstStartsThereInstead() {
        show()
        day("Saturday, 10 October 2026").performClick()
        day("Monday, 5 October 2026").performClick()
        day("Monday, 12 October 2026").performClick()
        compose.onNodeWithText("Show 8 days").performClick()
        assertEquals(DateRange(LocalDate.of(2026, 10, 5).atStartOfDay(), LocalDate.of(2026, 10, 13).atStartOfDay()), picked)
    }

    @Test
    fun oneDayIsShownWhenOnlyTheFirstIsTapped() {
        show()
        day("Wednesday, 7 October 2026").performClick()
        compose.onNodeWithText("Show 1 day").performClick()
        assertEquals(DateRange(LocalDate.of(2026, 10, 7).atStartOfDay(), LocalDate.of(2026, 10, 8).atStartOfDay()), picked)
    }

    @Test
    fun aReadyMadeRunSetsBothDays() {
        show()
        compose.onNode(hasContentDescription("Last 7 days", substring = true)).performClick()
        compose.onNodeWithText("Show 7 days").performClick()
        assertEquals(DateRange(LocalDate.of(2026, 10, 9).atStartOfDay(), LocalDate.of(2026, 10, 16).atStartOfDay()), picked)
    }

    @Test
    fun daysAfterTheLastOfferedCantBePicked() {
        show()
        day("Thursday, 15 October 2026").assertIsEnabled()
        day("Friday, 16 October 2026").assertIsNotEnabled()
    }

    @Test
    fun theDaysShownBeforeArePickedAlready() {
        show(initial = DateRange(LocalDate.of(2026, 10, 1).atStartOfDay(), LocalDate.of(2026, 10, 11).atStartOfDay()))
        compose.onNodeWithText("Show 10 days").performClick()
        assertEquals(DateRange(LocalDate.of(2026, 10, 1).atStartOfDay(), LocalDate.of(2026, 10, 11).atStartOfDay()), picked)
    }

    @Test
    fun readyMadeRunsEndToday() {
        val presets = dayPresets(today, DayOfWeek.MONDAY).associate { it.label to (it.first to it.last) }
        assertEquals(LocalDate.of(2026, 10, 9) to today, presets["Last 7 days"])
        assertEquals(LocalDate.of(2026, 10, 12) to today, presets["This week"])
        assertEquals(LocalDate.of(2026, 1, 1) to today, presets["This year"])
    }
}
