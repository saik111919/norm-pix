package com.example.normpix.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.NormPixTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val designLabels = listOf("Expense", "Income", "Recurring", "Reminder", "Card", "Account", "Categories", "Budgets", "People", "Search", "Settings")

/**
 * Edit mode of the "+" menu, the way Pixel's Quick Settings editor works: pick a tile and move or resize it, drag it into
 * place, Reset, Done. Everything is also reachable without dragging.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ActionMenuEditTest {

    @get:Rule
    val compose = createComposeRule()

    private var open by mutableStateOf(false)
    private var editing by mutableStateOf(false)
    private var layout by mutableStateOf(ActionMenuLayout.Default)
    private val kept = mutableListOf<ActionMenuLayout>()
    private var clicked: String? = null

    private fun actions() = designLabels.mapIndexed { i, label ->
        FooterAction(label = label, icon = Icons.Rounded.Add, isPrimary = i == 0, onClick = { clicked = label }, id = label.lowercase())
    }

    private var scheme: ColorScheme? = null
    private var dockColors: DockColors? = null

    /** [saved]: the callback puts an edit into [layout], as the preferences do; false, it only records it (saving that is slow). */
    private fun show(
        fontScale: Float = 1f,
        editable: Boolean = true,
        saved: Boolean = true,
        startOpen: Boolean = true,
        appearance: Appearance = Appearance(ThemeMode.Light),
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                NormPixTheme(appearance) {
                    val currentScheme = MaterialTheme.colorScheme
                    scheme = currentScheme
                    val currentColors = rememberDockColors()
                    dockColors = currentColors
                    Box(Modifier.fillMaxSize().background(currentScheme.surface)) {
                        FloatingNavigationRow(
                            destinations = AppDestination.entries,
                            current = AppDestination.Home,
                            overdueCount = 0,
                            onDestinationSelected = {},
                            actions = actions(),
                            isMoreOpen = open,
                            onMoreToggle = { open = !open },
                            actionLayout = layout,
                            onActionLayoutChange = if (editable) ({ if (saved) layout = it; kept += it }) else null,
                            editingActions = editing,
                            onEditingActionsChange = { editing = it },
                            colors = currentColors,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        if (startOpen) {
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.waitForIdle()
        }
    }

    private fun startEditing() {
        compose.onNodeWithContentDescription("Edit actions").performClick()
        compose.waitForIdle()
    }

    private fun tap(description: String) {
        compose.onNodeWithContentDescription(description).performClick()
        compose.waitForIdle()
    }

    private fun press(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private fun bounds(description: String) = compose.onNodeWithContentDescription(description).getUnclippedBoundsInRoot()

    private fun order() = layout.entries.map { it.id }

    @Test
    fun the_edit_button_sits_beside_the_close_button_in_the_dock_row() {
        show()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val edit = bounds("Edit actions")
        val close = bounds("Close actions")
        assertEquals("a 10 dp gap, as the row spaces its children", 10f, (close.left - edit.right).value, 1f)
        assertEquals("vertically centred on the close button", close.top.value + close.height.value / 2, edit.top.value + edit.height.value / 2, 1f)
        assertEquals("the same diameter as the close button", close.width.value, edit.width.value, 1f)
        assertEquals(edit.width.value, edit.height.value, 1f)
        compose.onAllNodesWithContentDescription("Edit actions").assertCountEquals(1)
    }

    @Test
    fun the_edit_button_is_visible_in_a_light_theme() {
        show()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val pixels = compose.onNodeWithContentDescription("Edit actions").captureToImage().toPixelMap()
        val buttonFill = pixels[(pixels.width * 0.20f).toInt(), (pixels.height * 0.50f).toInt()]
        val expectedCompanion = dockColors!!.companionContainer
        val pageBackground = scheme!!.surface
        assertEquals("the edit button fill matches the dock companionContainer colour", expectedCompanion, buttonFill)
        assertNotEquals("the edit button fill is clearly different from the page background", pageBackground, buttonFill)
    }

    @Test
    fun the_edit_button_appears_only_with_the_menu_open_and_starts_edit_mode() {
        show(startOpen = false)
        compose.onAllNodesWithContentDescription("Edit actions").assertCountEquals(0)
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Edit actions").assertIsDisplayed()
        startEditing()
        assertTrue(editing)
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun a_menu_that_cannot_be_edited_has_no_edit_button() {
        show(editable = false)
        compose.onAllNodesWithContentDescription("Edit actions").assertCountEquals(0)
    }

    @Test
    fun in_edit_mode_a_tap_selects_a_tile_instead_of_running_it() {
        show()
        startEditing()
        assertTrue(editing)
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Edit actions").assertCountEquals(0) // the button has done its job
        tap("Income")
        assertNull("the action must not run while editing", clicked)
        assertTrue(open)
        // The bar names the selected tile and offers what can be done to it.
        compose.onNodeWithText("Income").assertIsDisplayed() // the bar names it
        compose.onNodeWithContentDescription("Move earlier").assertIsEnabled()
        compose.onNodeWithText("Make small").assertIsDisplayed()
    }

    @Test
    fun move_later_swaps_a_tile_with_the_next_and_keeps_it() {
        show()
        startEditing()
        val before = bounds("Recurring").left
        tap("Recurring")
        compose.onNodeWithContentDescription("Move later").performClick()
        compose.waitForIdle()
        assertEquals(listOf("expense", "income", "reminder", "recurring"), order().take(4))
        assertEquals(order(), kept.last().entries.map { it.id })
        assertTrue("it moved a cell to the right", bounds("Recurring").left > before)
    }

    @Test
    fun a_tile_can_be_made_wide_and_small_again() {
        show()
        startEditing()
        val cell = bounds("Card").width
        tap("Card")
        press("Make wide")
        assertEquals(ActionTileSize.Wide, layout.entries.single { it.id == "card" }.size)
        assertEquals(2 * cell.value + 8f, bounds("Card").width.value, 1.5f)
        press("Make small")
        assertEquals(ActionTileSize.Small, layout.entries.single { it.id == "card" }.size)
        assertEquals(cell.value, bounds("Card").width.value, 1f)
    }

    @Test
    fun the_handle_drags_a_tile_between_its_two_sizes() {
        show()
        startEditing()
        tap("Card")
        compose.onNodeWithTag("action_resize_handle", useUnmergedTree = true).performTouchInput { down(center); moveBy(Offset(60f, 0f)); up() }
        compose.waitForIdle()
        assertEquals(ActionTileSize.Wide, layout.entries.single { it.id == "card" }.size)
        compose.onNodeWithTag("action_resize_handle", useUnmergedTree = true).performTouchInput { down(center); moveBy(Offset(-60f, 0f)); up() }
        compose.waitForIdle()
        assertEquals(ActionTileSize.Small, layout.entries.single { it.id == "card" }.size)
    }

    @Test
    fun the_featured_tile_is_the_wide_capsule_and_cannot_be_resized_or_moved_before_the_start() {
        show()
        startEditing()
        tap("Expense")
        compose.onNodeWithText("Featured").assertIsDisplayed()
        compose.onAllNodesWithText("Make wide").assertCountEquals(0)
        compose.onNodeWithContentDescription("Move earlier").assertIsNotEnabled()
        compose.onAllNodesWithTag("action_resize_handle", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun moving_a_tile_to_the_start_makes_it_the_featured_capsule() {
        show()
        startEditing()
        tap("Budgets")
        repeat(7) { compose.onNodeWithContentDescription("Move earlier").performClick() }
        compose.waitForIdle()
        assertEquals("budgets", order().first())
        val budgets = bounds("Budgets")
        val expense = bounds("Expense")
        assertTrue("Budgets is the wide capsule now", budgets.width.value > 2 * budgets.height.value)
        assertTrue("Expense is an ordinary square tile", expense.width.value < 1.2f * expense.height.value)
    }

    @Test
    fun touch_and_hold_lifts_a_tile_and_dragging_puts_it_in_a_new_place() {
        show()
        startEditing()
        val budgets = bounds("Budgets")
        val recurring = bounds("Recurring")
        compose.onNodeWithContentDescription("Budgets").performTouchInput {
            down(center)
            advanceEventTime(800)
            val to = center + Offset(
                (recurring.left - budgets.left).toPx() + 2f,
                (recurring.top - budgets.top).toPx() + 2f,
            )
            moveTo(to)
            advanceEventTime(50)
            moveTo(to + Offset(1f, 1f))
            up()
        }
        compose.waitForIdle()
        // Budgets was the eighth; over Recurring's place (the third) it takes that place and the others move along.
        assertEquals(listOf("expense", "income", "budgets", "recurring"), order().take(4))
        assertEquals(order(), kept.last().entries.map { it.id })
    }

    @Test
    fun reset_puts_the_design_back_and_is_only_offered_when_something_changed() {
        show()
        startEditing()
        compose.onNodeWithText("Reset").assertIsNotEnabled()
        tap("Search")
        compose.onNodeWithContentDescription("Move earlier").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Reset").assertIsEnabled()
        press("Reset")
        assertEquals(ActionMenuLayout.Default, layout)
        assertEquals(ActionMenuLayout.Default, kept.last())
        compose.onNodeWithText("Reset").assertIsNotEnabled()
    }

    @Test
    fun done_ends_edit_mode_and_the_arrangement_stays() {
        show()
        startEditing()
        tap("Card")
        press("Make wide")
        press("Done")
        assertFalse(editing)
        compose.onNodeWithContentDescription("Edit actions").assertIsDisplayed()
        assertEquals(ActionTileSize.Wide, layout.entries.single { it.id == "card" }.size)
        // Outside Edit mode a tile runs its action again.
        tap("Card")
        assertEquals("Card", clicked)
    }

    @Test
    fun an_edit_shows_at_once_without_waiting_for_the_saved_layout_to_come_back() {
        // The saved layout reaches the menu a frame after an edit is made; the menu shows the edit meanwhile, not the old one
        // flashing back. Here the saved layout never changes at all, as when saving is slow: the edit must still show.
        show(saved = false)
        startEditing()
        val before = bounds("People").left
        tap("People")
        compose.onNodeWithContentDescription("Move later").performClick()
        compose.waitForIdle()
        assertEquals(ActionMenuLayout.Default, layout)
        assertEquals(listOf("expense", "income", "recurring", "reminder", "card", "account", "categories", "budgets", "search", "people", "settings"), kept.last().entries.map { it.id })
        assertTrue("People moved a place along", bounds("People").left > before)
    }

    @Test
    fun with_large_text_the_list_has_the_same_edit_mode() {
        show(fontScale = 1.6f)
        startEditing()
        tap("Card")
        compose.onNodeWithContentDescription("Move later").performClick()
        compose.waitForIdle()
        assertEquals(listOf("account", "card"), order().subList(4, 6))
        // Sizes mean nothing in a list of equal tiles: the bar offers none.
        compose.onAllNodesWithText("Make wide").assertCountEquals(0)
        compose.onAllNodesWithText("Featured").assertCountEquals(0)
        compose.onNodeWithText("Done").assertIsDisplayed()
    }
}
