package com.example.normpix.finance.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RepeatSchedule
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

/** The editor's "Repeat" field: what it says for each schedule, and that a transaction already in a repeating payment is not offered another. */
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
            env.Content { RepeatField(repeat = RepeatSchedule(RecurrenceFrequency.Monthly), onChange = {}, from = from) }
        }
        compose.onNodeWithText("Every month").assertIsDisplayed()
        compose.onNodeWithText("Next: 15 Feb 2030 · 15 Mar 2030 · 15 Apr 2030.").assertIsDisplayed()
    }

    @Test
    fun `a custom schedule is named in the field, with its end`() {
        val schedule = RepeatSchedule(RecurrenceFrequency.Weekly, interval = 2, endDate = LocalDateTime.of(2030, 3, 1, 0, 0))
        compose.setContent { env.Content { RepeatField(repeat = schedule, onChange = {}, from = from) } }
        compose.onNodeWithText("Every 2 weeks until 1 Mar 2030").assertIsDisplayed()
        // 29 Jan and 12 Feb fall before the end; 26 Feb does too; 12 Mar does not.
        compose.onNodeWithText("Next: 29 Jan 2030 · 12 Feb 2030 · 26 Feb 2030.").assertIsDisplayed()
    }

    @Test
    fun `a schedule that started in the past says what is recorded now, and still previews the dates ahead`() {
        val started = LocalDateTime.now().minusDays(10)
        compose.setContent {
            env.Content { RepeatField(repeat = RepeatSchedule(RecurrenceFrequency.Weekly), onChange = {}, from = started) }
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
}
