package com.example.normpix.finance.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RepeatEnd
import com.example.normpix.finance.domain.RepeatPlan
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDate
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
 * The editor's "Repeat" field: what it says for each plan, how it ends (never, unless said), and that a transaction already in a
 * repeating payment is not offered another.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RepeatFieldTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("repeat_field")
    }

    @After
    fun tearDown() = env.close()

    private val from = LocalDateTime.of(2030, 1, 15, 9, 30)

    @Test
    fun `a one-off says it does not repeat and previews nothing`() {
        compose.setContent { env.Content { RepeatField(repeat = null, onChange = {}, from = from) } }
        compose.onNodeWithText("Does not repeat").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Next:", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a monthly schedule says so and previews the next dates after the first`() {
        compose.setContent {
            env.Content { RepeatField(repeat = RepeatPlan(RecurrenceFrequency.Monthly), onChange = {}, from = from) }
        }
        compose.onNodeWithText("Every month").assertIsDisplayed()
        compose.onNodeWithText("Next: 15 Feb 2030 · 15 Mar 2030 · 15 Apr 2030.").assertIsDisplayed()
    }

    @Test
    fun `a custom schedule is named in the field, and its end is told under Ends`() {
        val plan = RepeatPlan(RecurrenceFrequency.Weekly, interval = 2, ends = RepeatEnd.OnDate(LocalDate.of(2030, 3, 1)))
        compose.setContent { env.Content { RepeatField(repeat = plan, onChange = {}, from = from) } }
        compose.onNodeWithText("Every 2 weeks").assertIsDisplayed()
        radio("On a date").assertIsSelected()
        compose.onNodeWithContentDescription("Last payment by, 1 Mar 2030").assertIsDisplayed()
        // 15 Jan (the first), 29 Jan, 12 Feb and 26 Feb fall on or before the end; 12 Mar does not.
        compose.onNodeWithText("4 payments · last on 26 Feb 2030").assertIsDisplayed()
        compose.onNodeWithText("Next: 29 Jan 2030 · 12 Feb 2030 · 26 Feb 2030.").assertIsDisplayed()
    }

    private fun radio(label: String) =
        compose.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    @Test
    fun `a repeat ends never until it is told to`() {
        compose.setContent {
            env.Content { RepeatField(repeat = RepeatPlan(RecurrenceFrequency.Monthly), onChange = {}, from = from) }
        }
        compose.onNodeWithText("Ends").assertIsDisplayed()
        radio("Never").assertIsSelected()
        radio("On a date").assertIsNotSelected()
        radio("After").assertIsNotSelected()
        assertTrue(compose.onAllNodesWithText("last on", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a one-off has no Ends to choose`() {
        compose.setContent { env.Content { RepeatField(repeat = null, onChange = {}, from = from) } }
        assertTrue(compose.onAllNodesWithText("Ends").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `an end set on a monthly repeat leaves Every month chosen, not Custom`() {
        val plan = RepeatPlan(RecurrenceFrequency.Monthly, ends = RepeatEnd.After(6))
        compose.setContent { env.Content { RepeatField(repeat = plan, onChange = {}, from = from) } }
        compose.onNodeWithText("Every month").assertIsDisplayed()
        radio("After").assertIsSelected()
        compose.onNodeWithText("After 6 payments · last on 15 Jun 2030").assertIsDisplayed()
    }

    @Test
    fun `choosing After counts twelve payments and tells the date of the last, and the count can be changed`() {
        var plan: RepeatPlan? = null
        compose.setContent {
            env.Content {
                var current by remember { mutableStateOf<RepeatPlan?>(RepeatPlan(RecurrenceFrequency.Monthly)) }
                plan = current
                RepeatField(repeat = current, onChange = { current = it }, from = from)
            }
        }
        radio("After").performClick()
        compose.waitForIdle()
        assertEquals(RepeatPlan(RecurrenceFrequency.Monthly, 1, RepeatEnd.After(12)), plan)
        compose.onNodeWithText("After 12 payments · last on 15 Dec 2030").assertIsDisplayed()

        compose.onNodeWithContentDescription("More").performClick()
        compose.waitForIdle()
        assertEquals(RepeatEnd.After(13), plan?.ends)
        compose.onNodeWithText("After 13 payments · last on 15 Jan 2031").assertIsDisplayed()

        compose.onNodeWithContentDescription("Fewer").performClick()
        compose.onNodeWithContentDescription("Fewer").performClick()
        compose.waitForIdle()
        assertEquals(RepeatEnd.After(11), plan?.ends)
        compose.onNodeWithText("After 11 payments · last on 15 Nov 2030").assertIsDisplayed()

        radio("Never").performClick()
        compose.waitForIdle()
        assertEquals(RepeatEnd.Never, plan?.ends)
    }

    @Test
    fun `the count cant go below two`() {
        compose.setContent {
            env.Content { RepeatField(repeat = RepeatPlan(RecurrenceFrequency.Monthly, ends = RepeatEnd.After(2)), onChange = {}, from = from) }
        }
        compose.onNodeWithContentDescription("Fewer").assertIsNotEnabled()
        compose.onNodeWithContentDescription("More").assertIsEnabled()
    }

    @Test
    fun `changing how often it repeats keeps the end that was set`() {
        var plan: RepeatPlan? = null
        compose.setContent {
            env.Content {
                var current by remember { mutableStateOf<RepeatPlan?>(RepeatPlan(RecurrenceFrequency.Monthly, ends = RepeatEnd.After(3))) }
                plan = current
                RepeatField(repeat = current, onChange = { current = it }, from = from)
            }
        }
        compose.onNodeWithText("Repeat").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Every week").performClick()
        compose.waitForIdle()
        assertEquals(RepeatPlan(RecurrenceFrequency.Weekly, 1, RepeatEnd.After(3)), plan)
        // Three weekly payments from 15 Jan end on 29 Jan - the count follows the new frequency.
        compose.onNodeWithText("After 3 payments · last on 29 Jan 2030").assertIsDisplayed()
    }

    @Test
    fun `a schedule that started in the past says what is recorded now, and still previews the dates ahead`() {
        val started = LocalDateTime.now().minusDays(10)
        compose.setContent {
            env.Content { RepeatField(repeat = RepeatPlan(RecurrenceFrequency.Weekly), onChange = {}, from = started) }
        }
        // One weekly repeat has already fallen due (seven days after the first); the ones shown as next come after it and after now.
        compose.onNode(androidx.compose.ui.test.hasText("1 more since", substring = true)).assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasText("Next:", substring = true)).assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("No more after that", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a transaction already in a repeating payment is not offered another, and leads to that payment's editor`() {
        compose.setContent { env.Content { RepeatField(repeat = null, onChange = {}, from = from, ruleId = 7) } }
        compose.onNodeWithText("Part of a repeating payment").assertIsDisplayed()
        compose.onNodeWithText("Edit schedule").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Does not repeat").fetchSemanticsNodes().size)
    }

    @Test
    fun `the schedules read the way a person would say them`() {
        val now = LocalDateTime.of(2030, 1, 1, 0, 0)
        assertEquals("Every day", describeRepeat(RepeatSchedule(RecurrenceFrequency.Daily), now))
        assertEquals("Every 3 months", describeRepeat(RepeatSchedule(RecurrenceFrequency.Monthly, 3), now))
        assertEquals("Every year", describeRepeat(RepeatSchedule(RecurrenceFrequency.Yearly), now))
        assertEquals(
            "Every week until 5 Jun",
            describeRepeat(RepeatSchedule(RecurrenceFrequency.Weekly, endDate = LocalDateTime.of(2030, 6, 5, 0, 0)), now),
        )
    }

    @Test
    fun `a plan reads as the schedule it makes for its first payment, a count being the day of its last`() {
        val now = LocalDateTime.of(2030, 1, 1, 0, 0)
        val start = LocalDateTime.of(2030, 1, 15, 9, 30)
        assertEquals("Every month", describeRepeat(RepeatPlan(RecurrenceFrequency.Monthly), start, now))
        assertEquals("Every month until 15 Jun", describeRepeat(RepeatPlan(RecurrenceFrequency.Monthly, ends = RepeatEnd.After(6)), start, now))
        assertEquals(
            "Every 2 weeks until 26 Feb",
            describeRepeat(RepeatPlan(RecurrenceFrequency.Weekly, 2, RepeatEnd.OnDate(LocalDate.of(2030, 2, 26))), start, now),
        )
    }
}
