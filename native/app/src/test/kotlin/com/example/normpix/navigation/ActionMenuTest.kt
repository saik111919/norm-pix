package com.example.normpix.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val elevenLabels = listOf(
    "Expense", "Income", "Recurring", "Reminder",
    "Card", "Account", "Categories", "Budgets",
    "People", "Search", "Settings",
)

/** The "+" action menu: the bento grid, its large-text fallback, and what the open menu does to the dock under it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ActionMenuTest {

    @get:Rule
    val compose = createComposeRule()

    private var open by mutableStateOf(false)
    private var tabSelected: AppDestination? = null
    private var clicked: String? = null

    private fun actions(labels: List<String> = elevenLabels) = labels.mapIndexed { i, label ->
        FooterAction(label = label, icon = Icons.Rounded.Add, isPrimary = i == 0, onClick = { clicked = label }, id = label.lowercase())
    }

    private fun show(labels: List<String> = elevenLabels, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                NormPixTheme {
                    FloatingNavigationRow(
                        destinations = AppDestination.entries,
                        current = AppDestination.Home,
                        overdueCount = 0,
                        onDestinationSelected = { tabSelected = it },
                        actions = actions(labels),
                        isMoreOpen = open,
                        onMoreToggle = { open = !open },
                    )
                }
            }
        }
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
    }

    @Test
    fun tiles_keep_the_proportions_of_the_design() {
        show()
        openMenu()
        val expense = compose.onNodeWithContentDescription("Expense").getUnclippedBoundsInRoot()
        val income = compose.onNodeWithContentDescription("Income").getUnclippedBoundsInRoot()
        val recurring = compose.onNodeWithContentDescription("Recurring").getUnclippedBoundsInRoot()
        val reminder = compose.onNodeWithContentDescription("Reminder").getUnclippedBoundsInRoot()
        val settings = compose.onNodeWithContentDescription("Settings").getUnclippedBoundsInRoot()
        val cell = recurring.width.value
        val gap = 6f
        // The tall capsule is one cell wide and three tall; the wide one two cells wide and one tall; the circles round.
        assertEquals(cell, expense.width.value, 1f)
        assertEquals(3 * cell + 2 * gap, expense.height.value, 1.5f)
        assertEquals(2 * cell + gap, income.width.value, 1.5f)
        assertEquals(cell, income.height.value, 1f)
        assertEquals(reminder.width.value, reminder.height.value, 1f)
        // Read left to right: Expense, then Income, Recurring, Reminder along the top; Settings is below Recurring's column.
        assertTrue(expense.left < income.left && income.left < recurring.left && recurring.left < reminder.left)
        assertEquals(income.top.value, recurring.top.value, 1f)
        assertTrue(settings.top > recurring.top)
        assertEquals(recurring.left.value, settings.left.value, 1f)
        // Every tile is a comfortable target.
        for (label in elevenLabels) {
            val b = compose.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
            assertTrue("$label is ${b.width} x ${b.height}", b.width.value >= 48f && b.height.value >= 48f)
        }
    }

    @Test
    fun large_text_gets_the_three_column_list_and_loses_no_action() {
        show(fontScale = 1.6f)
        openMenu()
        for (label in elevenLabels) compose.onNodeWithContentDescription(label).assertIsDisplayed()
        val expense = compose.onNodeWithContentDescription("Expense").getUnclippedBoundsInRoot()
        val income = compose.onNodeWithContentDescription("Income").getUnclippedBoundsInRoot()
        val recurring = compose.onNodeWithContentDescription("Recurring").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithContentDescription("Card").getUnclippedBoundsInRoot()
        // Three to a row, and the labels have the room of a third of the panel each, not a fifth.
        assertEquals(expense.top.value, income.top.value, 1f)
        assertEquals(income.top.value, recurring.top.value, 1f)
        assertTrue(card.top > expense.top)
        assertTrue("tile ${expense.width} wide", expense.width.value > 90f)
    }

    @Test
    fun a_tap_on_an_action_runs_it_and_closes_the_menu() {
        show()
        openMenu()
        compose.onNodeWithContentDescription("Budgets").performClick()
        assertEquals("Budgets", clicked)
        assertFalse(open)
        compose.waitForIdle()
        // Closed, the panel is gone altogether, not parked at zero size or alpha.
        compose.onAllNodesWithContentDescription("Budgets").assertCountEquals(0)
    }

    @Test
    fun the_dock_under_an_open_menu_cannot_be_pressed_and_a_tap_there_closes_it() {
        show()
        val insights = compose.onNodeWithContentDescription("Insights").getUnclippedBoundsInRoot()
        val x = (insights.left + insights.width / 2)
        val y = (insights.top + insights.height / 2)
        openMenu()
        // The faded dock is not offered to screen readers...
        compose.onAllNodesWithContentDescription("Insights").assertCountEquals(0)
        // ...and a tap where its Insights tab is closes the menu without switching tab.
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(x.toPx(), y.toPx())) }
        compose.waitForIdle()
        assertFalse(open)
        assertEquals(null, tabSelected)
        // The dock is back, and works.
        compose.onNodeWithContentDescription("Insights").performClick()
        assertEquals(AppDestination.Insights, tabSelected)
    }

    @Test
    fun the_plus_button_names_what_it_will_do() {
        show()
        compose.onNodeWithContentDescription("More actions").assertIsDisplayed()
        openMenu()
        compose.onNodeWithContentDescription("Close actions").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("More actions").assertIsDisplayed()
    }

    @Test
    fun tile_labels_read_on_their_tiles_in_every_palette() {
        val schemes = linkedMapOf<String, ColorScheme>()
        compose.setContent {
            for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                for (accent in AccentColor.entries) {
                    NormPixTheme(Appearance(mode, ColorSource.Custom, accent)) {
                        schemes["$mode $accent"] = MaterialTheme.colorScheme
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2 * AccentColor.entries.size, schemes.size)
        for ((name, scheme) in schemes) {
            for (index in 0..12) {
                val tone = tileTone(index, scheme)
                val ratio = contrastRatio(tone.container, tone.content)
                assertTrue("$name tile $index reads at $ratio:1", ratio >= 4.5f)
            }
        }
    }
}
