package com.example.normpix.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the "+" menu is arranged and packed into its grid, without a screen. */
class ActionMenuLayoutTest {

    private fun action(id: String) = FooterAction(label = id.replaceFirstChar { it.uppercase() }, icon = Icons.Rounded.Add, onClick = {}, id = id)

    private val designIds = listOf("expense", "income", "recurring", "reminder", "card", "account", "categories", "budgets", "people", "search", "settings")
    private val designActions = designIds.map(::action)

    private fun arranged(layout: ActionMenuLayout, actions: List<FooterAction> = designActions) = layout.arranged(actions)

    private fun small(vararg ids: String) = ids.map { ArrangedAction(action(it), ActionTileSize.Small) }

    /** Every cell a plan covers, as (row, col) pairs, hero included. */
    private fun cells(plan: BentoPlan): List<Pair<Int, Int>> =
        plan.tiles.flatMap { s ->
            (0 until s.rowSpan).flatMap { r -> (0 until s.colSpan).map { c -> (s.row + r) to (s.col + c) } }
        }

    @Test
    fun the_default_is_the_design_eleven_in_reading_order_with_income_wide() {
        assertEquals(designIds, ActionMenuLayout.Default.entries.map { it.id })
        assertEquals(listOf("income"), ActionMenuLayout.Default.entries.filter { it.size == ActionTileSize.Wide }.map { it.id })
    }

    @Test
    fun the_default_packs_exactly_as_the_design_and_ends_with_its_last_tile() {
        val plan = bentoPlan(arranged(ActionMenuLayout.Default))
        assertEquals(4, plan.rows)
        // The two main actions are capsules side by side on the top row.
        assertEquals(BentoSlot(0, 0, 1, 2, BentoShape.Hero), plan.tiles[0])
        assertEquals(BentoSlot(0, 2, 1, 2, BentoShape.Wide), plan.tiles[1])
        // Then every other action is the same squircle, four to a row.
        assertEquals(BentoSlot(1, 0, 1, 1, BentoShape.Squircle), plan.tiles[2]) // Recurring
        assertEquals(BentoSlot(1, 1, 1, 1, BentoShape.Squircle), plan.tiles[3]) // Reminder
        assertEquals(BentoSlot(2, 1, 1, 1, BentoShape.Squircle), plan.tiles[7]) // Budgets
        assertEquals(BentoSlot(3, 0, 1, 1, BentoShape.Squircle), plan.tiles[10]) // Settings
        assertTrue("no mix of shapes among the small tiles", plan.tiles.drop(2).all { it.shape == BentoShape.Squircle })
        // 13 of the 16 cells of the 4 x 4 grid are covered once: nothing overlaps, and no cell is kept for a button.
        val covered = cells(plan)
        assertEquals(13, covered.size)
        assertEquals(13, covered.toSet().size)
        assertTrue(covered.all { (r, c) -> r in 0..3 && c in 0..3 })
    }

    @Test
    fun the_last_row_has_room_left_where_the_edit_button_used_to_be() {
        val plan = bentoPlan(arranged(ActionMenuLayout.Default))
        val free = (0..3).flatMap { r -> (0..3).map { c -> r to c } } - cells(plan).toSet()
        assertEquals(listOf(3 to 1, 3 to 2, 3 to 3), free)
    }

    @Test
    fun a_wide_tile_that_does_not_fit_the_row_starts_the_next_one_and_leaves_a_hole() {
        // Featured and two small ones fill the top row, three small ones take the second row's first columns; the wide one needs two
        // cells and only the last is left, so it starts the next row.
        val tiles = small("a", "b", "c", "d", "e", "f") + ArrangedAction(action("g"), ActionTileSize.Wide)
        val plan = bentoPlan(tiles)
        assertEquals(BentoSlot(2, 0, 1, 2, BentoShape.Wide), plan.tiles[6])
        assertEquals(3, plan.rows)
        assertTrue((1 to 3) !in cells(plan))
    }

    @Test
    fun a_second_wide_tile_goes_below_the_top_row_and_the_next_small_one_follows_it() {
        val tiles = small("a") + listOf("b", "c").map { ArrangedAction(action(it), ActionTileSize.Wide) } + small("d")
        val plan = bentoPlan(tiles)
        assertEquals(BentoSlot(0, 2, 1, 2, BentoShape.Wide), plan.tiles[1])
        assertEquals(BentoSlot(1, 0, 1, 2, BentoShape.Wide), plan.tiles[2])
        assertEquals(BentoSlot(1, 2, 1, 1, BentoShape.Squircle), plan.tiles[3])
        assertEquals(2, plan.rows)
        assertEquals("the featured tile is one row tall", 1, plan.tiles[0].rowSpan)
    }

    @Test
    fun the_featured_tile_is_the_wide_capsule_whatever_it_is_and_whatever_size_it_was() {
        val layout = ActionMenuLayout.Default.moved("income", 0)
        val plan = bentoPlan(arranged(layout))
        assertEquals("income", arranged(layout)[0].action.id)
        assertEquals(BentoShape.Hero, plan.tiles[0].shape)
        assertEquals(FeaturedSpan, plan.tiles[0].colSpan)
        assertEquals(1, plan.tiles[0].rowSpan)
        // Expense, pushed down, is an ordinary small tile now, beside the capsule.
        assertEquals(BentoSlot(0, 2, 1, 1, BentoShape.Squircle), plan.tiles[1])
    }

    @Test
    fun a_grid_of_one_has_one_row() {
        val plan = bentoPlan(small("expense"))
        assertEquals(1, plan.rows)
        assertEquals(BentoSlot(0, 0, 1, 2, BentoShape.Hero), plan.tiles[0])
    }

    @Test
    fun a_full_last_row_does_not_get_an_extra_row_for_a_button() {
        // Featured and two small tiles fill the top row, then two rows of four: the grid ends there, three rows.
        val plan = bentoPlan(small("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k"))
        assertEquals(3, plan.rows)
        assertEquals(BentoSlot(2, 3, 1, 1, BentoShape.Squircle), plan.tiles[10])
    }

    @Test
    fun a_tile_nearer_the_plus_button_arrives_before_one_further_away() {
        val plan = bentoPlan(arranged(ActionMenuLayout.Default))
        // The "+" button is under the bottom-right corner: Settings starts the bottom row, three cells from it; the featured
        // capsule at the far corner is the last to arrive.
        assertEquals(3, plan.tiles[10].arrivalRank(plan.rows)) // Settings
        assertEquals(plan.tiles.maxOf { it.arrivalRank(plan.rows) }, plan.tiles[0].arrivalRank(plan.rows))
    }

    @Test
    fun moving_puts_a_tile_in_place_and_the_others_make_room() {
        val moved = ActionMenuLayout.Default.moved("budgets", 2)
        assertEquals(listOf("expense", "income", "budgets", "recurring", "reminder", "card", "account", "categories", "people", "search", "settings"), moved.entries.map { it.id })
        // Out of range clamps to the ends; an unknown tile or the same place changes nothing.
        assertEquals("expense", ActionMenuLayout.Default.moved("expense", 99).entries.last().id)
        assertEquals("settings", ActionMenuLayout.Default.moved("settings", -5).entries.first().id)
        assertSame(ActionMenuLayout.Default, ActionMenuLayout.Default.moved("nope", 1))
        assertSame(ActionMenuLayout.Default, ActionMenuLayout.Default.moved("card", 4))
    }

    @Test
    fun resizing_changes_only_that_tile() {
        val resized = ActionMenuLayout.Default.resized("card", ActionTileSize.Wide).resized("income", ActionTileSize.Small)
        assertEquals(listOf("card"), resized.entries.filter { it.size == ActionTileSize.Wide }.map { it.id })
        assertSame(ActionMenuLayout.Default, ActionMenuLayout.Default.resized("card", ActionTileSize.Small))
        assertSame(ActionMenuLayout.Default, ActionMenuLayout.Default.resized("nope", ActionTileSize.Wide))
    }

    @Test
    fun it_is_kept_as_text_and_read_back_the_same() {
        val layout = ActionMenuLayout.Default.moved("people", 1).resized("search", ActionTileSize.Wide)
        assertEquals(layout, ActionMenuLayout.parse(layout.encode()))
        assertEquals("expense:s,income:w", ActionMenuLayout(listOf(ActionMenuEntry("expense"), ActionMenuEntry("income", ActionTileSize.Wide))).encode())
        // The default is kept as nothing, so a reset leaves no trace; nothing saved is the default.
        assertEquals("", ActionMenuLayout.keptAs(ActionMenuLayout.Default))
        assertEquals(layout.encode(), ActionMenuLayout.keptAs(layout))
        assertEquals(ActionMenuLayout.Default, ActionMenuLayout.from(""))
        assertEquals(ActionMenuLayout.Default, ActionMenuLayout.from(null))
        assertEquals(ActionMenuLayout.Default, ActionMenuLayout.from(" ,, :"))
    }

    @Test
    fun text_from_a_backup_is_read_forgivingly() {
        val parsed = ActionMenuLayout.parse("income:w, ,expense:x,income:s,zzz,:w,Card:W")!!
        // A repeat is dropped (the first stays), a blank or nameless piece is skipped, an unknown size is small, W is wide.
        assertEquals(listOf("income", "expense", "zzz", "Card"), parsed.entries.map { it.id })
        assertEquals(listOf(ActionTileSize.Wide, ActionTileSize.Small, ActionTileSize.Small, ActionTileSize.Wide), parsed.entries.map { it.size })
    }

    @Test
    fun a_layout_is_applied_to_the_actions_there_are() {
        val layout = ActionMenuLayout(listOf(ActionMenuEntry("c"), ActionMenuEntry("gone", ActionTileSize.Wide), ActionMenuEntry("a", ActionTileSize.Wide)))
        val result = layout.arranged(listOf("a", "b", "c").map(::action))
        // In the layout's order and sizes; "gone" has no action and is left out; "b" is new to it, so it comes last, small.
        assertEquals(listOf("c", "a", "b"), result.map { it.action.id })
        assertEquals(listOf(ActionTileSize.Small, ActionTileSize.Wide, ActionTileSize.Small), result.map { it.size })
    }

    @Test
    fun editing_what_is_shown_keeps_the_actions_the_saved_layout_did_not_know() {
        val saved = ActionMenuLayout(listOf(ActionMenuEntry("c")))
        val shown = arranged(saved, listOf("a", "b", "c").map(::action))
        val edited = shown.toLayout().moved("a", 0)
        assertEquals(listOf("a", "c", "b"), edited.entries.map { it.id })
        assertNotSame(saved, edited)
    }
}
