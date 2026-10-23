package com.example.normpix.finance.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.testing.TestEnvironment
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
 * The month control: one tonal track holding a step back, the month on a filled pill (with how far back it is under it) and a
 * step forward that dims in place when there is nothing later, with the way out of the month beside it - each a target a
 * finger finds, and every one named for a screen reader.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class MonthSwitcherTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val latest = MonthKey(2026, 10)
    private var month by mutableStateOf(MonthKey(2026, 8))
    private var dates = 0

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("month_switcher_test")
        compose.setContent {
            env.Content {
                MonthSwitcher(
                    month = month,
                    latest = latest,
                    onChanged = { month = it },
                    trailing = { MonthScopeButton("Dates", onClick = { dates++ }) },
                )
            }
        }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun theMonthAndHowFarBackItIsAreOnThePill_andItIsNamedForAScreenReader() {
        compose.onNodeWithText(monthLabel(MonthKey(2026, 8)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2 months ago", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("August 2026, 2 months ago, choose month")).assertHasClickAction()
    }

    @Test
    fun theNewMonthIsAnnouncedAsItChanges_soSteppingToItIsHeard() {
        // Pressing a step moves no focus a screen reader is on: the pill says the month it now shows, politely, when it changes.
        compose.onNode(hasContentDescription("August 2026, 2 months ago, choose month"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun theStepsGoOneMonthEitherWay() {
        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.waitForIdle()
        assertEquals(MonthKey(2026, 7), month)

        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.waitForIdle()
        assertEquals(MonthKey(2026, 9), month)
    }

    @Test
    fun withNothingLaterTheStepForwardIsDimmedInPlace_notGone() {
        month = latest
        compose.waitForIdle()

        // Still there, so the control keeps its shape; just not something to press.
        compose.onNodeWithContentDescription("Next month").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous month").assertIsEnabled()
        compose.onNodeWithText("This month", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun everyPieceIsATargetAFingerFinds() {
        for (description in listOf("Previous month", "Next month")) {
            val bounds = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()
            assertTrue(bounds.width >= 48.dp && bounds.height >= 48.dp, "$description is ${bounds.width} x ${bounds.height}")
        }
        val pill = compose.onNode(hasContentDescription("August 2026, 2 months ago, choose month")).getUnclippedBoundsInRoot()
        assertTrue(pill.height >= 48.dp, "the month pill is ${pill.height} tall")
        val scope = compose.onNodeWithText("Dates").getUnclippedBoundsInRoot()
        assertTrue(scope.height >= 48.dp, "Dates is ${scope.height} tall")
    }

    @Test
    fun theMonthPillIsInsideTheTrack_notAsTallAsIt() {
        val pill = compose.onNode(hasContentDescription("August 2026, 2 months ago, choose month")).getUnclippedBoundsInRoot()
        val scope = compose.onNodeWithText("Dates").getUnclippedBoundsInRoot()
        // The track is as tall as the button beside it (56dp); the pill is a hair shorter, held in it.
        assertTrue(pill.height < 56.dp, "the pill ${pill.height} sits inside the 56dp track")
        assertEquals(56f, scope.height.value, 0.5f, "the button beside it is as tall as the track")
    }

    @Test
    fun tappingThePillOpensTheMonthPicker_andPickingOneMovesThere() {
        compose.onNode(hasContentDescription("August 2026, 2 months ago, choose month")).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Choose a month").assertIsDisplayed()
        compose.onNode(hasContentDescription("March 2026")).performClick()
        compose.waitForIdle()
        assertEquals(MonthKey(2026, 3), month)
    }

    @Test
    fun theWayOutOfTheMonthIsBesideIt_andPressable() {
        compose.onNodeWithText("Dates").assertIsDisplayed().performClick()
        assertEquals(1, dates)
        // Sanity: the pill's own semantics were not swallowed by the button.
        assertTrue(compose.onNode(hasContentDescription("August 2026, 2 months ago, choose month")).fetchSemanticsNode().config.contains(SemanticsProperties.Role))
    }
}
