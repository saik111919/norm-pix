package com.example.normpix.finance.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.theme.NormPixTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A day and a time are picked from sheets in the Insights date range's family: quick picks as tiles, the calendar or the
 * clock below them, and one button that uses what is chosen. A tap on a day or a number only chooses it; the button returns it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DateTimeSheetsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 10, 15)
    private var pickedDate: LocalDate? = null
    private var pickedTime: LocalTime? = null

    private fun showDate(initial: LocalDate = today, latest: LocalDate? = today.plusYears(1)) {
        compose.setContent {
            NormPixTheme { DateSheet(initial = initial, latest = latest, onPick = { pickedDate = it }, today = today) }
        }
        compose.waitForIdle()
    }

    private fun showTime(initial: LocalTime, is24Hour: Boolean, now: LocalTime = LocalTime.of(14, 7)) {
        compose.setContent {
            NormPixTheme { TimeSheet(initial = initial, is24Hour = is24Hour, onPick = { pickedTime = it }, now = now) }
        }
        compose.waitForIdle()
    }

    private fun day(spoken: String) = compose.onNode(hasContentDescription(spoken, substring = true))

    private fun described(spoken: String) = compose.onNode(hasContentDescription(spoken))

    private fun use(label: String) = compose.onNodeWithText(label)

    // --- a day ------------------------------------------------------------------------------------------------------

    @Test
    fun aDayTappedAndUsedIsReturned() {
        showDate()
        day("Saturday, 3 October 2026").performClick()
        use("Use ${dayLabel(LocalDate.of(2026, 10, 3))}").performClick()
        assertEquals(LocalDate.of(2026, 10, 3), pickedDate)
    }

    @Test
    fun tappingADayDoesNotReturnItUntilTheButtonIs() {
        showDate()
        day("Saturday, 3 October 2026").performClick()
        assertNull(pickedDate)
    }

    @Test
    fun theDayShownBeforeIsReturnedAsItIs() {
        showDate(initial = LocalDate.of(2026, 10, 8))
        use("Use ${dayLabel(LocalDate.of(2026, 10, 8))}").performClick()
        assertEquals(LocalDate.of(2026, 10, 8), pickedDate)
    }

    @Test
    fun aQuickPickChoosesTheDay() {
        showDate()
        day("Yesterday").performClick()
        use("Use ${dayLabel(today.minusDays(1))}").performClick()
        assertEquals(LocalDate.of(2026, 10, 14), pickedDate)
    }

    @Test
    fun aQuickPickToComeIsChosenOnTheCalendarToo() {
        showDate()
        day("In a week").performClick()
        day("Thursday, 22 October 2026, chosen").assertExists()
        use("Use ${dayLabel(today.plusWeeks(1))}").performClick()
        assertEquals(LocalDate.of(2026, 10, 22), pickedDate)
    }

    @Test
    fun theMonthCanBeSteppedAndADayOfItPicked() {
        showDate()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.waitForIdle()
        day("Sunday, 1 November 2026").performClick()
        use("Use ${dayLabel(LocalDate.of(2026, 11, 1))}").performClick()
        assertEquals(LocalDate.of(2026, 11, 1), pickedDate)
    }

    @Test
    fun theChosenDayAndTodayAreToldApart() {
        showDate(initial = LocalDate.of(2026, 10, 8))
        day("Thursday, 8 October 2026, chosen").assertExists()
        day("Thursday, 15 October 2026, today").assertExists()
    }

    @Test
    fun daysAfterTheLatestOfferedCantBePicked_andNeitherAreQuickPicksToComeAfterIt() {
        showDate(latest = today)
        day("Thursday, 15 October 2026").assertIsEnabled()
        day("Friday, 16 October 2026").assertIsNotEnabled()
        compose.onAllNodes(hasContentDescription("Tomorrow", substring = true)).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("In a week", substring = true)).assertCountEquals(0)
        day("Yesterday").assertExists()
    }

    @Test
    fun theQuickPicksOfferedFollowTheLatestDay() {
        assertEquals(listOf("Today", "Yesterday", "Tomorrow", "In a week"), datePicks(today, null).map { it.label })
        assertEquals(listOf("Today", "Yesterday", "Tomorrow", "In a week"), datePicks(today, today.plusYears(10)).map { it.label })
        assertEquals(listOf("Today", "Yesterday", "Tomorrow"), datePicks(today, today.plusDays(1)).map { it.label })
        assertEquals(listOf("Today", "Yesterday"), datePicks(today, today).map { it.label })
        assertEquals(listOf("Yesterday"), datePicks(today, today.minusDays(1)).map { it.label })
    }

    @Test
    fun aQuickPickIsTheOneChosenWhileItsDayIs() {
        showDate()
        described("Today, ${dayLabel(today)}").assertIsSelected()
        described("Yesterday, ${dayLabel(today.minusDays(1))}").assertIsNotSelected()
        day("Yesterday").performClick()
        described("Yesterday, ${dayLabel(today.minusDays(1))}").assertIsSelected()
        described("Today, ${dayLabel(today)}").assertIsNotSelected()
    }

    // --- a time -----------------------------------------------------------------------------------------------------

    @Test
    fun anHourAndAMinuteTappedAndUsedAreReturned_on24HourTime() {
        showTime(LocalTime.of(9, 30), is24Hour = true)
        described("Hour 14").performClick()
        compose.waitForIdle()
        // The hour done, the tiles move on to the minute.
        described("Minute 45").performClick()
        use("Use 14:45").performClick()
        assertEquals(LocalTime.of(14, 45), pickedTime)
    }

    @Test
    fun theTimeShownBeforeIsReturnedAsItIs() {
        showTime(LocalTime.of(9, 30), is24Hour = true)
        use("Use 09:30").performClick()
        assertEquals(LocalTime.of(9, 30), pickedTime)
    }

    @Test
    fun aQuickPickSetsBothHourAndMinute() {
        showTime(LocalTime.of(9, 30), is24Hour = true)
        day("Noon").performClick()
        use("Use 12:00").performClick()
        assertEquals(LocalTime.NOON, pickedTime)
    }

    @Test
    fun nowIsTheTimeItIsToTheMinute() {
        showTime(LocalTime.of(9, 30), is24Hour = true, now = LocalTime.of(14, 7, 41, 500))
        day("Now").performClick()
        use("Use 14:07").performClick()
        assertEquals(LocalTime.of(14, 7), pickedTime)
    }

    @Test
    fun theQuickPicksAreTheUsualTimesOfDay() {
        val picks = timePicks(LocalTime.of(14, 7, 41)).associate { it.label to it.time }
        assertEquals(LocalTime.of(14, 7), picks["Now"])
        assertEquals(LocalTime.of(9, 0), picks["Morning"])
        assertEquals(LocalTime.of(12, 0), picks["Noon"])
        assertEquals(LocalTime.of(18, 0), picks["Evening"])
        assertEquals(LocalTime.of(21, 0), picks["Night"])
    }

    @Test
    fun theMinuteCanBeNudgedByOne_andWrapsRound() {
        showTime(LocalTime.of(9, 0), is24Hour = true)
        // The minute half of the pair brings the minute tiles and their steps.
        described("Minute, 0").performClick()
        compose.waitForIdle()
        described("One minute earlier").performClick()
        use("Use 09:59").performClick()
        assertEquals(LocalTime.of(9, 59), pickedTime)
    }

    @Test
    fun aMinuteNudgedLaterIsAMinuteLater() {
        showTime(LocalTime.of(9, 30), is24Hour = true)
        described("Minute, 30").performClick()
        compose.waitForIdle()
        described("One minute later").performClick()
        use("Use 09:31").performClick()
        assertEquals(LocalTime.of(9, 31), pickedTime)
    }

    @Test
    fun on12HourTime_amAndPmAreChosenBesideTheTime() {
        showTime(LocalTime.of(9, 30), is24Hour = false)
        described("AM").assertIsSelected()
        described("PM").assertIsNotSelected()
        described("PM").performClick()
        described("PM").assertIsSelected()
        use("Use ${timeText(LocalTime.of(21, 30), is24Hour = false)}").performClick()
        assertEquals(LocalTime.of(21, 30), pickedTime)
    }

    @Test
    fun on12HourTime_anHourTileIsInTheHalfOfTheDayChosen() {
        showTime(LocalTime.of(9, 30), is24Hour = false)
        described("PM").performClick()
        described("Hour 7").performClick()
        compose.waitForIdle()
        use("Use ${timeText(LocalTime.of(19, 30), is24Hour = false)}").performClick()
        assertEquals(LocalTime.of(19, 30), pickedTime)
    }

    @Test
    fun on12HourTime_twelveIsMidnightInTheMorningAndNoonInTheAfternoon() {
        showTime(LocalTime.of(9, 30), is24Hour = false)
        described("Hour 12").performClick()
        compose.waitForIdle()
        use("Use ${timeText(LocalTime.of(0, 30), is24Hour = false)}").performClick()
        assertEquals(LocalTime.of(0, 30), pickedTime)
    }

    @Test
    fun on24HourTime_thereIsNoAmOrPm() {
        showTime(LocalTime.of(9, 30), is24Hour = true)
        compose.onAllNodes(hasContentDescription("AM")).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("PM")).assertCountEquals(0)
    }

    @Test
    fun theHoursOfA12HourClockAreMappedToTheDay() {
        assertEquals(12, clockHour(0))
        assertEquals(1, clockHour(1))
        assertEquals(11, clockHour(11))
        assertEquals(12, clockHour(12))
        assertEquals(1, clockHour(13))
        assertEquals(11, clockHour(23))

        assertEquals(0, hourFromTile(12, afternoon = false, is24Hour = false))
        assertEquals(12, hourFromTile(12, afternoon = true, is24Hour = false))
        assertEquals(1, hourFromTile(1, afternoon = false, is24Hour = false))
        assertEquals(19, hourFromTile(7, afternoon = true, is24Hour = false))
        assertEquals(23, hourFromTile(23, afternoon = false, is24Hour = true))
        assertEquals(0, hourFromTile(0, afternoon = true, is24Hour = true))

        assertEquals(21, inHalfOfDay(9, afternoon = true))
        assertEquals(9, inHalfOfDay(21, afternoon = false))
        assertEquals(12, inHalfOfDay(0, afternoon = true))
        assertEquals(0, inHalfOfDay(12, afternoon = false))
    }

    // --- the fields that open them ----------------------------------------------------------------------------------

    @Test
    fun aPairOfFieldsOpensEachItsOwnSheet_atTheHeightOfTheInsightsRangeControl() {
        var opened = ""
        compose.setContent {
            NormPixTheme {
                PickerFieldRow(
                    first = PickerField("Date", "12 Oct") { opened = "date" },
                    second = PickerField("Time", "9:30 AM") { opened = "time" },
                )
            }
        }
        compose.onNodeWithContentDescription("Date, 12 Oct").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithContentDescription("Time, 9:30 AM").performClick()
        assertEquals("time", opened)
        compose.onNodeWithContentDescription("Date, 12 Oct").performClick()
        assertEquals("date", opened)
    }

    @Test
    fun aSingleFieldIsAloneInTheRow() {
        var opened = 0
        compose.setContent { NormPixTheme { PickerFieldRow(PickerField("Last repeat by", "5 Oct 2027") { opened++ }) } }
        compose.onNodeWithContentDescription("Last repeat by, 5 Oct 2027").performClick()
        assertEquals(1, opened)
    }
}

/**
 * [DatePickerDialogFor] and [TimePickerDialogFor] open the sheets over whatever is open, as the range picker does: they give
 * the choice back, or say they were closed without one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class DateTimePickerOverlayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val overlays = AppOverlays()
    private var openDate by mutableStateOf(false)
    private var openTime by mutableStateOf(false)
    private var pickedDate: LocalDate? = null
    private var pickedTime: Pair<Int, Int>? = null
    private var dismissed = 0

    private fun host() {
        compose.setContent {
            CompositionLocalProvider(LocalOverlays provides overlays) {
                NormPixTheme {
                    OverlayHost(overlays)
                    if (openDate) {
                        DatePickerDialogFor(
                            initial = LocalDate.of(2026, 10, 8),
                            onPicked = {
                                pickedDate = it
                                openDate = false
                            },
                            onDismiss = {
                                dismissed++
                                openDate = false
                            },
                        )
                    }
                    if (openTime) {
                        TimePickerDialogFor(
                            initial = LocalDateTime.of(2026, 10, 8, 9, 30),
                            onPicked = { hour, minute ->
                                pickedTime = hour to minute
                                openTime = false
                            },
                            onDismiss = {
                                dismissed++
                                openTime = false
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theDateSheetOpensWhenAskedFor_andReturnsTheDayPicked() {
        host()
        compose.onAllNodes(hasText("Choose a date")).assertCountEquals(0)
        openDate = true
        compose.waitForIdle()
        compose.onNodeWithText("Choose a date").assertExists()

        compose.onNode(hasContentDescription("Friday, 9 October 2026", substring = true)).performClick()
        compose.onNodeWithText("Use ${dayLabel(LocalDate.of(2026, 10, 9))}").performClick()
        compose.waitForIdle()
        assertEquals(LocalDate.of(2026, 10, 9), pickedDate)
        assertEquals(0, dismissed)
        compose.onAllNodes(hasText("Choose a date")).assertCountEquals(0)
    }

    @Test
    fun theDateSheetClosedWithoutADayIsDismissed() {
        host()
        openDate = true
        compose.waitForIdle()
        compose.runOnUiThread { overlays.dismissAll() }
        compose.waitForIdle()
        assertNull(pickedDate)
        assertEquals(1, dismissed)
    }

    @Test
    fun theTimeSheetOpensWhenAskedFor_andReturnsTheHourAndMinute() {
        host()
        openTime = true
        compose.waitForIdle()
        compose.onNodeWithText("Choose a time").assertExists()

        compose.onNode(hasContentDescription("Evening", substring = true)).performClick()
        compose.onNode(hasText("Use ", substring = true)).performClick()
        compose.waitForIdle()
        assertEquals(18 to 0, pickedTime)
        assertEquals(0, dismissed)
    }

    @Test
    fun theTimeSheetClosedWithoutATimeIsDismissed() {
        host()
        openTime = true
        compose.waitForIdle()
        compose.runOnUiThread { overlays.dismissAll() }
        compose.waitForIdle()
        assertNull(pickedTime)
        assertEquals(1, dismissed)
    }
}
