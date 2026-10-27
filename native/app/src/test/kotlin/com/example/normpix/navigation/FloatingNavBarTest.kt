package com.example.normpix.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.NormPixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FloatingNavBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFloatingNavBarDisplaysAllDestinations() {
        var selectedDestination = AppDestination.Home

        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavBar(
                    destinations = AppDestination.entries,
                    current = selectedDestination,
                    overdueCount = 0,
                    onDestinationSelected = { selectedDestination = it },
                )
            }
        }

        // Check that all 4 destinations exist
        composeTestRule.onNodeWithContentDescription("Home").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Plans & Transactions").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Insights").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Wallet").assertIsDisplayed()

        // Home is active, so its text label is visible
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()

        // Inactive tabs show no label text
        composeTestRule.onAllNodesWithText("Insights").assertCountEquals(0)
    }

    @Test
    fun testFloatingNavBarTabSelection() {
        var selectedDestination by mutableStateOf(AppDestination.Home)

        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavBar(
                    destinations = AppDestination.entries,
                    current = selectedDestination,
                    overdueCount = 0,
                    onDestinationSelected = { selectedDestination = it },
                )
            }
        }

        // Tap Wallet tab
        composeTestRule.onNodeWithContentDescription("Wallet").performClick()
        assertEquals(AppDestination.Wallet, selectedDestination)

        // Wallet is now active, so its text label appears
        composeTestRule.onNodeWithText("Wallet").assertIsDisplayed()
        // Home label is now hidden
        composeTestRule.onAllNodesWithText("Home").assertCountEquals(0)
    }

    @Test
    fun testOverdueBadgeOnPlansAndTransactions() {
        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavBar(
                    destinations = AppDestination.entries,
                    current = AppDestination.Home,
                    overdueCount = 3,
                    onDestinationSelected = {},
                )
            }
        }

        // Badge semantics description
        composeTestRule.onNodeWithContentDescription("3 overdue").assertIsDisplayed()
    }

    @Test
    fun testFloatingNavigationRowContainsNavBarAndCompanionItem() {
        var companionClicked = false

        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavigationRow(
                    destinations = AppDestination.entries,
                    current = AppDestination.Home,
                    overdueCount = 0,
                    onDestinationSelected = {},
                    onOpenSettings = { companionClicked = true },
                )
            }
        }

        // The dock destinations exist
        composeTestRule.onNodeWithContentDescription("Home").assertIsDisplayed()

        // The Settings companion squircle exists and is clickable
        val settingsItem = composeTestRule.onNodeWithContentDescription("Settings")
        settingsItem.assertIsDisplayed()
        settingsItem.performClick()
        assertTrue(companionClicked)
    }

    @Test
    fun testFloatingBackRowRendersBackDiscAndTitle() {
        var backPressed = false
        var searchPressed = false

        composeTestRule.setContent {
            NormPixTheme {
                FloatingBackRow(
                    title = "Your Android",
                    onBack = { backPressed = true },
                    onCompanionClick = { searchPressed = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Your Android").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back, Your Android").performClick()
        assertTrue(backPressed)

        composeTestRule.onNodeWithContentDescription("Search").performClick()
        assertTrue(searchPressed)
    }

    @Test
    fun testMoreActionsMenuExpandsAndInvokesAction() {
        var actionInvoked = false
        var isMoreOpen by mutableStateOf(false)

        val testActions = listOf(
            FooterAction(
                label = "Add expense",
                icon = Icons.Rounded.Add,
                isPrimary = true,
                onClick = { actionInvoked = true },
            ),
        )

        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavigationRow(
                    destinations = AppDestination.entries,
                    current = AppDestination.Home,
                    overdueCount = 0,
                    onDestinationSelected = {},
                    actions = testActions,
                    isMoreOpen = isMoreOpen,
                    onMoreToggle = { isMoreOpen = !isMoreOpen },
                )
            }
        }

        // Before expansion, More actions button is displayed
        val moreButton = composeTestRule.onNodeWithContentDescription("More actions")
        moreButton.assertIsDisplayed()

        // Clicking More button expands the menu
        moreButton.performClick()
        composeTestRule.onNodeWithContentDescription("Close actions").assertIsDisplayed()

        // The contextual action is displayed and touch target is accessible
        val actionPill = composeTestRule.onNodeWithContentDescription("Add expense")
        actionPill.assertIsDisplayed()
        actionPill.assertHeightIsAtLeast(48.dp)

        // Clicking the action invokes callback
        actionPill.performClick()
        assertTrue(actionInvoked)
    }

    @Test
    fun testExpandableActionGridWith11Actions() {
        var clickedIndex = -1
        var isMoreOpen by mutableStateOf(false)

        val actionLabels = listOf(
            "Expense", "Income", "Recurring", "Reminder",
            "Card", "Account", "Categories", "Budgets",
            "People", "Search", "Settings",
        )
        val actions = actionLabels.mapIndexed { index, label ->
            FooterAction(
                label = label,
                icon = Icons.Rounded.Add,
                isPrimary = index == 0,
                onClick = { clickedIndex = index },
            )
        }

        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavigationRow(
                    destinations = AppDestination.entries,
                    current = AppDestination.Home,
                    overdueCount = 0,
                    onDestinationSelected = {},
                    actions = actions,
                    isMoreOpen = isMoreOpen,
                    onMoreToggle = { isMoreOpen = !isMoreOpen },
                )
            }
        }

        // Expand menu
        composeTestRule.onNodeWithContentDescription("More actions").performClick()
        composeTestRule.waitForIdle()

        // All 11 actions are displayed
        for (label in actionLabels) {
            composeTestRule.onNodeWithContentDescription(label).assertIsDisplayed()
        }

        // Clicking an action invokes its callback and toggles isMoreOpen
        composeTestRule.onNodeWithContentDescription("Budgets").performClick()
        assertEquals(7, clickedIndex)
        assertFalse(isMoreOpen)
    }

    @Test
    fun testFloatingNavBarHidesLabelWhenDisabled() {
        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavBar(
                    destinations = AppDestination.entries,
                    current = AppDestination.Home,
                    overdueCount = 0,
                    onDestinationSelected = {},
                    showLabels = false,
                )
            }
        }

        // Active destination is selected, but does NOT show text
        composeTestRule.onAllNodesWithText("Home").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("Home").assertIsSelected()
    }
}
