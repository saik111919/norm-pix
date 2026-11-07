package com.example.normpix.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.normpix.navigation.contrastRatio
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.ui.theme.NormPixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class OptionSheetFloatingBarTest {

    @get:Rule
    val compose = createComposeRule()

    private val radioMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val checkMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun themed(
        mode: ThemeMode = ThemeMode.Light,
        accent: AccentColor = AccentColor.Green,
        content: @Composable () -> Unit,
    ) {
        compose.setContent {
            NormPixTheme(Appearance(mode = mode, colorSource = ColorSource.Custom, accent = accent)) {
                CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                    content()
                }
            }
        }
        compose.waitForIdle()
    }

    // --- OptionSheet: Single-choice mode ---

    @Test
    fun optionSheetSingleChoiceSelectsAndHasRadioRole() {
        var selectedItem = "apple"
        themed {
            OptionSheet(
                title = "Choose fruit",
                options = listOf(
                    OptionSheetItem("apple", "Apple"),
                    OptionSheetItem("banana", "Banana"),
                    OptionSheetItem("orange", "Orange"),
                ),
                selected = setOf(selectedItem),
                onSelect = { selectedItem = it },
            )
        }

        val appleNode = compose.onNode(hasText("Apple") and hasClickAction())
        appleNode.assert(radioMatcher)
        appleNode.assertIsSelected()

        val bananaNode = compose.onNode(hasText("Banana") and hasClickAction())
        bananaNode.assert(radioMatcher)
        bananaNode.performClick()
        compose.waitForIdle()

        assertEquals("banana", selectedItem)
    }

    // --- OptionSheet: Multiple-choice mode ---

    @Test
    fun optionSheetMultipleChoiceTogglesAndAppliesViaConfirmCircle() {
        var confirmed: Set<String>? = null
        themed {
            var selectedSet by remember { mutableStateOf(setOf("apple")) }
            OptionSheet(
                title = "Choose fruits",
                options = listOf(
                    OptionSheetItem("apple", "Apple"),
                    OptionSheetItem("banana", "Banana"),
                    OptionSheetItem("orange", "Orange"),
                ),
                selected = selectedSet,
                multiple = true,
                onSelect = { item ->
                    selectedSet = if (item in selectedSet) selectedSet - item else selectedSet + item
                },
                onConfirm = { confirmed = it },
            )
        }

        // Initially "Apple" is selected (1 selected)
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNode(hasText("Apple") and hasClickAction()).assert(checkMatcher).assertIsSelected()

        // Toggle Banana
        compose.onNode(hasText("Banana") and hasClickAction()).assert(checkMatcher).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("2 selected").assertIsDisplayed()
        val confirmButton = compose.onNodeWithContentDescription("Apply (2)")
        confirmButton.assertIsDisplayed()
        confirmButton.performClick()
        compose.waitForIdle()

        assertEquals(setOf("apple", "banana"), confirmed)
    }

    // --- OptionSheet: Search filtering ---

    @Test
    fun optionSheetSearchFiltersItems() {
        themed {
            OptionSheet(
                title = "Choose fruit",
                searchPlaceholder = "Search…",
                options = listOf(
                    OptionSheetItem("apple", "Apple"),
                    OptionSheetItem("apricot", "Apricot"),
                    OptionSheetItem("banana", "Banana"),
                ),
                selected = emptySet(),
                onSelect = {},
            )
        }

        compose.onAllNodesWithText("Apple").assertCountEquals(1)
        compose.onAllNodesWithText("Banana").assertCountEquals(1)

        compose.onNodeWithText("Search…").performTextInput("Ap")
        compose.waitForIdle()

        compose.onAllNodesWithText("Apple").assertCountEquals(1)
        compose.onAllNodesWithText("Apricot").assertCountEquals(1)
        compose.onAllNodesWithText("Banana").assertCountEquals(0)
    }

    // --- OptionSheet: Action Pill ---

    @Test
    fun optionSheetActionPillTriggersCallback() {
        var actionClicked = false
        themed {
            OptionSheet(
                title = "Choose fruit",
                options = listOf(OptionSheetItem("apple", "Apple")),
                selected = setOf("apple"),
                actionLabel = "Add new fruit",
                onAction = { actionClicked = true },
            )
        }

        val actionNode = compose.onNode(hasText("Add new fruit") and hasClickAction())
        actionNode.assertIsDisplayed()
        actionNode.performClick()
        compose.waitForIdle()

        assertTrue(actionClicked)
    }

    // --- FloatingActionBar: Sizing and Actions layout ---

    @Test
    fun floatingActionBarGeometryAndActions() {
        var primaryClicked = false
        var secondaryClicked = false
        themed {
            Box(Modifier.fillMaxSize()) {
                Column {
                    Text("Invalid entry")
                    FloatingActionBar(
                        primaryLabel = "Save changes",
                        onPrimary = { primaryClicked = true },
                        actions = {
                            FloatingActionIconButton(
                                icon = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete",
                                onClick = { secondaryClicked = true },
                            )
                        },
                    )
                }
            }
        }

        // Error text is displayed
        compose.onNodeWithText("Invalid entry").assertIsDisplayed()

        val primaryNode = compose.onNode(hasText("Save changes") and hasClickAction())
        val secondaryNode = compose.onNode(hasContentDescription("Delete") and hasClickAction())

        val primaryBounds = primaryNode.getUnclippedBoundsInRoot()
        val secondaryBounds = secondaryNode.getUnclippedBoundsInRoot()

        // Sizing constraints
        val primaryHeight = (primaryBounds.bottom - primaryBounds.top).value
        assertTrue(primaryHeight >= 48f)

        val secondaryHeight = (secondaryBounds.bottom - secondaryBounds.top).value
        val secondaryWidth = (secondaryBounds.right - secondaryBounds.left).value
        assertTrue(secondaryHeight >= 48f)
        assertTrue(secondaryWidth >= 48f)

        // Secondary action is to the left of the primary action
        assertTrue(secondaryBounds.right <= primaryBounds.left)

        // Click actions work
        primaryNode.performClick()
        secondaryNode.performClick()
        compose.waitForIdle()

        assertTrue(primaryClicked)
        assertTrue(secondaryClicked)
    }

    // --- CheckboxRow component ---

    @Test
    fun checkboxRowSemanticsAndToggle() {
        var checked by mutableStateOf(false)
        themed {
            CheckboxRow(
                headline = "Enable notifications",
                selected = checked,
                onClick = { checked = !checked },
                modifier = Modifier.testTag("row"),
            )
        }

        val row = compose.onNodeWithTag("row")
        row.assert(checkMatcher)
        row.assertIsDisplayed()

        val bounds = row.getUnclippedBoundsInRoot()
        val rowHeight = (bounds.bottom - bounds.top).value
        assertTrue(rowHeight >= 48f)

        row.performClick()
        compose.waitForIdle()

        assertTrue(checked)
        row.assertIsSelected()
    }

    // --- Contrast ratchet across all themes and accents ---

    @Test
    fun selectedLabelContrastExceedsFourPointFiveAcrossAllThemesAndAccents() {
        val schemes = mutableMapOf<String, ColorScheme>()
        themed {
            for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                for (accent in AccentColor.entries) {
                    NormPixTheme(Appearance(mode = mode, colorSource = ColorSource.Custom, accent = accent)) {
                        schemes["$mode $accent"] = MaterialTheme.colorScheme
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(2 * AccentColor.entries.size, schemes.size)

        for ((name, scheme) in schemes) {
            val ground = scheme.surfaceContainerHigh
            val preferred = scheme.primary
            val fallback = scheme.onSurface
            val labelColor = readableOn(ground, preferred, fallback)

            val ratio = contrastRatio(ground, labelColor)
            assertTrue(
                "Selected label on $name container must satisfy WCAG AA (>= 4.5:1), was $ratio:1",
                ratio >= 4.5f,
            )
        }
    }
}
