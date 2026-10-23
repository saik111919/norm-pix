package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.settings.MonthControlStyle
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The month control in each of its two looks - the track and the earlier split buttons - is one control: the same steps, the
 * same pill, the same way out and the same words for a screen reader, whichever look it has; and where the look is not given
 * it is the user's choice (Settings > Appearance > Month control).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class MonthControlStylesTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val latest = MonthKey(2026, 10)
    private val picked = DateRange(LocalDateTime.of(2026, 10, 3, 0, 0), LocalDateTime.of(2026, 10, 16, 0, 0))
    private val pillSpoken = "August 2026, 2 months ago, choose month"

    /** The look the controls on screen are given; null leaves it to the user's choice. */
    private var style by mutableStateOf<MonthControlStyle?>(null)
    private var month by mutableStateOf(MonthKey(2026, 8))
    private var dates = 0
    private var byMonth = 0
    private var changeDates = 0

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("month_control_styles_test")
        compose.setContent {
            env.Content {
                Column {
                    // The same three controls a page can have, in the look under test (or the user's, with none given).
                    val look = style
                    if (look == null) {
                        MonthSwitcher(month, latest, onChanged = { month = it }, trailing = { MonthScopeButton("Dates", { dates++ }) })
                        AllTimeSwitcher(onByMonth = { byMonth++ })
                        DateRangeSwitcher(range = picked, onChange = { changeDates++ }, onByMonth = { byMonth++ })
                    } else {
                        MonthSwitcher(month, latest, onChanged = { month = it }, trailing = { MonthScopeButton("Dates", { dates++ }, style = look) }, style = look)
                        AllTimeSwitcher(onByMonth = { byMonth++ }, style = look)
                        DateRangeSwitcher(range = picked, onChange = { changeDates++ }, onByMonth = { byMonth++ }, style = look)
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = env.close()

    private fun use(look: MonthControlStyle?) {
        style = look
        compose.waitForIdle()
    }

    private fun pillHeight() = compose.onNode(hasContentDescription(pillSpoken)).getUnclippedBoundsInRoot().height

    private fun assertOneControl(look: MonthControlStyle) {
        use(look)
        month = MonthKey(2026, 8)
        compose.waitForIdle()

        // The month is a button named for what it says, and the steps go a month either way.
        compose.onNode(hasContentDescription(pillSpoken)).assertHasClickAction()
        compose.onNodeWithContentDescription("Previous month").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(MonthKey(2026, 7), month, "$look: previous")
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.waitForIdle()
        assertEquals(MonthKey(2026, 9), month, "$look: next, twice")

        // The way out of the month is beside it, and pressable.
        dates = 0
        compose.onNodeWithText("Dates").performClick()
        assertEquals(1, dates, "$look: Dates")

        // The other two stand in for it: "All time" with its way back, and the days picked with theirs.
        compose.onNodeWithText("All time").assertIsDisplayed()
        byMonth = 0
        changeDates = 0
        compose.onNode(hasContentDescription("3 Oct – 15 Oct, 13 days, choose dates")).performClick()
        assertEquals(1, changeDates, "$look: choose dates")
        val byMonthButtons = compose.onAllNodes(hasText("By month"))
        assertEquals(2, byMonthButtons.fetchSemanticsNodes().size, "$look: a way back to one month from each")
        byMonthButtons[0].performClick()
        byMonthButtons[1].performClick()
        assertEquals(2, byMonth, "$look: by month, from each")
    }

    @Test
    fun theTrackIsOneControl() = assertOneControl(MonthControlStyle.Track)

    @Test
    fun theEarlierSplitButtonsAreTheSameControl() = assertOneControl(MonthControlStyle.Split)

    @Test
    fun inEitherLookTheMonthPickerOpensFromThePill_andAPickMovesThere() {
        for (look in MonthControlStyle.entries) {
            use(look)
            month = MonthKey(2026, 8)
            compose.waitForIdle()

            compose.onNode(hasContentDescription(pillSpoken)).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Choose a month").assertIsDisplayed()
            compose.onNodeWithContentDescription("March 2026").performClick()
            compose.waitForIdle()
            assertEquals(MonthKey(2026, 3), month, "$look: picked")
        }
    }

    @Test
    fun inEitherLookThereIsNoGoingPastThisMonth() {
        for (look in MonthControlStyle.entries) {
            use(look)
            month = latest
            compose.waitForIdle()

            compose.onNodeWithContentDescription("Next month").assertIsDisplayed().assertIsNotEnabled()
            compose.onNodeWithText("This month", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Test
    fun theTrackHoldsItsPillInside_theEarlierButtonsMakeItAsTallAsTheControl() {
        use(MonthControlStyle.Track)
        assertTrue(pillHeight() < 56.dp, "track: the pill sits inside the 56dp track (${pillHeight()})")

        use(MonthControlStyle.Split)
        assertEquals(56f, pillHeight().value, 0.5f, "split: the month is a button the full height of the control")
    }

    @Test
    fun whereTheLookIsNotGivenItIsTheUsersChoice_atOnce() {
        use(null)
        assertTrue(pillHeight() < 56.dp, "the track unless chosen otherwise (${pillHeight()})")

        env.preferences.setMonthControlStyle(MonthControlStyle.Split)
        compose.waitForIdle()
        assertEquals(56f, pillHeight().value, 0.5f, "split: the full height of the control")

        env.preferences.setMonthControlStyle(MonthControlStyle.Track)
        compose.waitForIdle()
        assertTrue(pillHeight() < 56.dp, "and back to the track (${pillHeight()})")
    }
}
