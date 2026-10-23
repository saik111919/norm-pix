package com.example.normpix.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.MonthControlStyle
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Appearance > Month control: the two looks of the month switcher, each with a sample of itself, the one in use
 * chosen, a choice taking effect at once - and a row for it on the Appearance overview that says which is in use.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class MonthControlSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("month_control_settings")
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private fun style(label: String) = compose.onNode(hasText(label) and radio)

    @Test
    fun theTrackIsChosenAtFirst_andTheEarlierButtonsAreOneTouchAway() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.MonthControl) } }
        compose.waitForIdle()

        style("Track").assertIsSelected()
        style("Split buttons").assertIsNotSelected()
        compose.onNodeWithText(MonthControlStyle.Track.description).assertIsDisplayed()
        compose.onNodeWithText(MonthControlStyle.Split.description).assertIsDisplayed()
    }

    @Test
    fun choosingTheEarlierButtonsAppliesAtOnce_andMovesTheSelection() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.MonthControl) } }
        compose.waitForIdle()

        style("Split buttons").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(MonthControlStyle.Split, env.preferences.state.value.monthControlStyle)
        style("Split buttons").assertIsSelected()
        style("Track").assertIsNotSelected()

        style("Track").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(MonthControlStyle.Track, env.preferences.state.value.monthControlStyle)
        style("Track").assertIsSelected()
    }

    @Test
    fun eachLookHasASampleOfItself_whichChoosesItWhenTouched() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.MonthControl) } }
        compose.waitForIdle()

        // A real month control under each choice (in the unmerged tree: a screen reader skips them, the row above says what they are).
        compose.onAllNodesWithContentDescription("Previous month", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Next month", useUnmergedTree = true).assertCountEquals(2)

        // Touching the second sample (the earlier buttons') chooses that look, and does not step a month.
        compose.onAllNodesWithContentDescription("Previous month", useUnmergedTree = true)[1].performClick()
        compose.waitForIdle()
        assertEquals(MonthControlStyle.Split, env.preferences.state.value.monthControlStyle)
    }

    @Test
    fun theAppearanceOverviewHasARowForIt_saidInTheLook() {
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()

        compose.onNodeWithText("Month control").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Track").assertIsDisplayed()

        env.preferences.setMonthControlStyle(MonthControlStyle.Split)
        compose.waitForIdle()
        compose.onNodeWithText("Split buttons").assertIsDisplayed()
    }

    @Test
    fun theOverviewRowOpensItsPage() {
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()

        compose.onNodeWithText("Month control").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(listOf(AppearanceSection.MonthControl), env.navigator.appearanceSections)
    }
}
