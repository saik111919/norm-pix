package com.example.normpix.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.example.normpix.navigation.PlanSection
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.RepeatKind
import com.example.normpix.reminders.RepeatRule
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.screens.HomeScreen
import com.example.normpix.screens.PlansAndTransactionsScreen
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Do I have anything overdue?" - answered from the places people actually look, from the live reminders state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class OverdueDiscoverabilityTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("overdue_discoverability")
    }

    @After
    fun tearDown() = env.close()

    private fun addReminder(title: String, dueAt: LocalDateTime) = runBlocking {
        val now = LocalDateTime.now()
        env.container.reminders.addReminder(
            Reminder(
                title = title,
                category = ReminderCategory.Payments,
                dueAt = dueAt,
                repeatRule = RepeatRule(RepeatKind.Once),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun showPlans() = compose.setContent { env.Content { PlansAndTransactionsScreen() } }

    @Test
    fun nothingOverdueMeansNoIndicators() {
        addReminder("Rent", LocalDateTime.now().plusDays(3))
        showPlans()
        compose.waitForIdle()

        compose.onAllNodesWithContentDescription("1 overdue").assertCountEquals(0)
        compose.onNodeWithText("Plans").assertIsDisplayed()
    }

    @Test
    fun theCountShowsOnThePlansTabWithoutOpeningIt() {
        addReminder("Rent", LocalDateTime.now().minusDays(2))
        addReminder("Phone bill", LocalDateTime.now().minusHours(5))
        addReminder("Insurance", LocalDateTime.now().plusDays(9))
        showPlans()
        compose.waitForIdle()

        // On the Transactions view, the Plans tab already carries "2" - announced as "2 overdue", not just a number.
        compose.onNodeWithContentDescription("2 overdue", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun plansSaysSoOnBudgetsAndOneTapReachesTheReminders() {
        addReminder("Rent", LocalDateTime.now().minusDays(2))
        showPlans()
        compose.onNodeWithText("Plans").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("1 reminder overdue").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()
        compose.waitForIdle()

        assertEquals(PlanSection.Reminders, env.shell.section)
        // The Overdue section is the first thing on the Reminders section, and the banner steps aside.
        compose.onNodeWithText("Overdue").assertIsDisplayed()
        compose.onNodeWithText("1 reminder overdue").assertDoesNotExist()
    }

    @Test
    fun resolvingTheLastOverdueReminderClearsEveryIndicator() {
        val saved = addReminder("Rent", LocalDateTime.now().minusDays(2))
        showPlans()
        compose.onNodeWithText("Plans").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1 reminder overdue").assertIsDisplayed()

        runBlocking { env.container.reminders.completeReminder(saved.id!!) }
        compose.waitForIdle()

        compose.onNodeWithText("1 reminder overdue").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("1 overdue", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun homeStacksSeveralOverdueAndLongPressLaysThemAllOut() {
        addReminder("Rent", LocalDateTime.now().minusDays(2))
        addReminder("Phone bill", LocalDateTime.now().minusHours(5))
        compose.setContent { env.Content { HomeScreen() } }
        // The reminders reach the screen from a background write; wait for them rather than racing it.
        compose.waitUntil(timeoutMillis = 15_000) {
            compose.waitForIdle()
            compose.onAllNodesWithContentDescription("1 of 2", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // One stack under the month, the longest overdue in front; the other waits behind it.
        compose.onNodeWithText("Needs attention").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rent", substring = true).assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Phone bill", substring = true).assertCountEquals(0)

        compose.onNodeWithContentDescription("1 of 2", substring = true).performTouchInput { longClick() }
        compose.waitForIdle()

        // Laid out, each overdue reminder is there to read and open.
        compose.onNodeWithContentDescription("Rent", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Phone bill", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stack them again").assertIsDisplayed()
    }
}
