package com.example.normpix.navigation

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.NormPixTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class FloatingNavBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testFloatingNavBarRendersAllDestinations() {
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

        // Active destination shows text and is selected
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Home").assertIsSelected()

        // Other destinations are present but not selected; Plans and Transactions are one item.
        composeTestRule.onNodeWithContentDescription("Plans & Transactions").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithContentDescription("Insights").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onNodeWithContentDescription("Wallet").assertIsDisplayed().assertIsNotSelected()
        composeTestRule.onAllNodesWithContentDescription("Transactions").assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription("Plan").assertCountEquals(0)
    }

    @Test
    fun testDestinationClickUpdatesSelection() {
        var selectedDestination: AppDestination = AppDestination.Home

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

        composeTestRule.onNodeWithContentDescription("Plans & Transactions").performClick()
        assertEquals(AppDestination.PlansAndTransactions, selectedDestination)
    }

    @Test
    fun testSettingsSitsBesideTheDock() {
        var settingsOpened = false

        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavigationRow(
                    destinations = AppDestination.entries,
                    current = AppDestination.Home,
                    overdueCount = 0,
                    onDestinationSelected = {},
                    onOpenSettings = { settingsOpened = true },
                )
            }
        }

        val settingsNode = composeTestRule.onNodeWithContentDescription("Settings")
        settingsNode.assertIsDisplayed()
        settingsNode.performClick()
        assertTrue(settingsOpened)
    }

    @Test
    fun testEveryItemIsATouchTarget_andSettingsIsItsOwnTileBesideTheDock() {
        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavigationRow(
                    destinations = AppDestination.entries,
                    current = AppDestination.Home,
                    overdueCount = 0,
                    onDestinationSelected = {},
                    onOpenSettings = {},
                )
            }
        }

        // However compact the dock looks, nothing in it is smaller than a 48dp target.
        for (label in listOf("Home", "Plans & Transactions", "Insights", "Wallet", "Settings")) {
            composeTestRule.onNodeWithContentDescription(label).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }

        // The dock holds exactly the four destinations; Settings is a separate tile after it, not a fifth tab.
        composeTestRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
        val lastDestination = composeTestRule.onNodeWithContentDescription("Wallet").fetchSemanticsNode().boundsInRoot
        val settings = composeTestRule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot
        assertTrue("Settings should sit beyond the dock", settings.left > lastDestination.right)
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

        // Badge is displayed with overdue count
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("3 overdue", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun testSelectedItemNamesTheCurrentView() {
        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavigationRow(
                    destinations = AppDestination.entries,
                    current = AppDestination.PlansAndTransactions,
                    overdueCount = 1,
                    onDestinationSelected = {},
                    onOpenSettings = {},
                    currentLabel = PlansAndTransactionsTab.Plans.label,
                )
            }
        }

        // The pill shows which view is open, while the item keeps its one destination name.
        composeTestRule.onNodeWithText("Plans", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Plans & Transactions").assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun testSelectedItemAnnouncesWhichViewIsOpen() {
        composeTestRule.setContent {
            NormPixTheme {
                FloatingNavBar(
                    destinations = AppDestination.entries,
                    current = AppDestination.PlansAndTransactions,
                    overdueCount = 0,
                    onDestinationSelected = {},
                    currentLabel = PlansAndTransactionsTab.Transactions.label,
                )
            }
        }

        // Screen readers hear "Plans & Transactions, selected, Showing Transactions"; plain tabs add nothing.
        composeTestRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Showing Transactions"),
        ).assertIsDisplayed()
        composeTestRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).assertCountEquals(1)
    }

    @Test
    fun testFloatingBackRowRendersTitleAndTriggersBack() {
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
}
