package com.example.normpix.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.screens.PlansAndTransactionsScreen
import com.example.normpix.settings.ProgressStyle
import com.example.normpix.settings.ui.AppearanceSectionPage
import com.example.normpix.testing.TestEnvironment
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two controls this round added, through the screens that own them: Settings > Appearance > Progress indicators, and
 * the transaction history's search field with its filter button joined to it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ProgressChoiceAndFilterFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val env = TestEnvironment("progress_choice_and_filter_flow")

    @After
    fun tearDown() = env.close()

    // --- Settings > Appearance -------------------------------------------------------------------------------

    @Test
    fun appearanceOffersTheProgressStyles_wavySelectedToStartWith() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.ProgressIndicators) } }
        compose.waitForIdle()
        compose.onNodeWithText("Wavy").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Flat").performScrollTo()
        compose.onNodeWithText("Bars that fill with a moving wave, and a loader that morphs between shapes").performScrollTo()
        compose.onNodeWithText("Straight bars, and a round spinner for waits").performScrollTo()
    }

    @Test
    fun choosingFlatTakesEffectAtOnce_andWavyBringsItBack() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.ProgressIndicators) } }
        compose.waitForIdle()

        compose.onNodeWithText("Flat").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(ProgressStyle.Flat, env.preferences.state.value.progressStyle)
        compose.onNodeWithText("Flat").assertIsSelected()

        compose.onNodeWithText("Wavy").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(ProgressStyle.Wavy, env.preferences.state.value.progressStyle)
        compose.onNodeWithText("Wavy").assertIsSelected()
    }

    // --- Transactions: the search field with the filter button joined to it ------------------------------------

    private fun showHistory() {
        compose.setContent { env.Content { PlansAndTransactionsScreen() } }
        compose.waitForIdle()
    }

    /** The filter button: its description says how many filters are on, so it is found by what it is, not by that. */
    private fun filterButton() = compose.onNode(hasContentDescription("Filters", substring = true) and hasClickAction())

    private fun openMenu() {
        filterButton().performClick()
        compose.waitForIdle()
    }

    @Test
    fun theSearchFieldAndTheFilterButtonAreOneJoinedControl() {
        showHistory()
        val field = compose.onNode(hasSetTextAction()).getBoundsInRoot()
        val button = filterButton().getBoundsInRoot()

        // One line: their middles are level, and the button is the same height as the field.
        assertTrue(abs((field.top + field.bottom).value - (button.top + button.bottom).value) < 1f, "centres: $field vs $button")
        assertTrue(abs((field.height - button.height).value) < 1f, "one height: $field vs $button")
        // Joined: the button is the field's end, a hair apart (the split button's gap), not a separate control across a gutter.
        val gap = button.left - field.right
        assertTrue(gap >= 0.dp && gap <= 4.dp, "a hair apart, not a gutter: $gap")
        assertTrue(field.width > 150.dp, "the field keeps a usable width: ${field.width}")
    }

    @Test
    fun thereIsNoSeparateFiltersLabel_theChevronAloneOpensTheMenu() {
        showHistory()
        compose.onAllNodesWithText("Filters").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Quick filters").assertCountEquals(0)
        compose.onNodeWithContentDescription("Filters").assertExists()
    }

    @Test
    fun theFilterChipsFollowTheRow_beneathTheControl() {
        showHistory()
        openMenu()
        menuItem("Expense").performClick()
        compose.waitForIdle()

        val field = compose.onNode(hasSetTextAction()).getBoundsInRoot()
        val button = compose.onNodeWithContentDescription("Filters, 1 active").getBoundsInRoot()
        val chip = compose.onNodeWithContentDescription("Remove Expense filter").getBoundsInRoot()
        assertTrue(chip.top >= field.bottom && chip.top >= button.bottom, "the chip is below the row: $chip vs $field / $button")
    }

    @Test
    fun theMenuLeadsOnToTheFullFilters() {
        showHistory()
        openMenu()
        menuItem("All filters").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Filter transactions").assertExists()
    }

    /** An item of the open menu - not the chip of the same name that choosing it adds to the page. */
    private fun menuItem(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isPopup()))

    @Test
    fun theMenuFiltersByKind_andClearsThemAgain() {
        showHistory()
        compose.onAllNodesWithContentDescription("Remove Expense filter").assertCountEquals(0)

        openMenu()
        menuItem("Expense").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Remove Expense filter").assertExists()
        compose.onNodeWithContentDescription("Filters, 1 active").assertExists()

        openMenu()
        menuItem("Income").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Filters, 2 active").assertExists()

        // Choosing a kind that is on turns it off again.
        openMenu()
        menuItem("Expense").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Remove Expense filter").assertCountEquals(0)
        compose.onNodeWithContentDescription("Filters, 1 active").assertExists()

        openMenu()
        menuItem("Clear filters").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Remove Income filter").assertCountEquals(0)
        compose.onNodeWithContentDescription("Filters").assertExists()
    }
}
