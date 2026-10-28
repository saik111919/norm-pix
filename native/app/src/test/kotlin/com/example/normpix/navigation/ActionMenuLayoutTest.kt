package com.example.normpix.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
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

    /** Every cell a plan covers, as (row, col) pairs, hero and button included. */
    private fun cells(plan: BentoPlan): List<Pair<Int, Int>> =
        (plan.tiles + listOfNotNull(plan.trailing)).flatMap { s ->
            (0 until s.rowSpan).flatMap { r -> (0 until s.colSpan).map { c -> (s.row + r) to (s.col + c) } }
        }

    @Test
    fun the_default_is_the_design_eleven_in_reading_order_with_income_wide() {
        assertEquals(designIds, ActionMenuLayout.Default.entries.map { it.id })
        assertEquals(listOf("income"), ActionMenuLayout.Default.entries.filter { it.size == ActionTileSize.Wide }.map { it.id })
    }

    @Test
    fun the_default_packs_exactly_as_the_design_with_the_edit_button_in_the_free_corner() {
        val plan = bentoPlan(arranged(ActionMenuLayout.Default), withEditButton = true)
        assertEquals(3, plan.rows)
        assertEquals(BentoSlot(0, 0, 3, 1, BentoShape.Hero), plan.tiles[0])
        assertEquals(BentoSlot(0, 1, 1, 2, BentoShape.Wide), plan.tiles[1])
        assertEquals(BentoSlot(0, 3, 1, 1, BentoShape.Squircle), plan.tiles[2])
        assertEquals(BentoSlot(0, 4, 1, 1, BentoShape.Circle), plan.tiles[3]) // Reminder
        assertEquals(BentoSlot(1, 4, 1, 1, BentoShape.Circle), plan.tiles[7]) // Budgets
        assertEquals(BentoSlot(2, 3, 1, 1, BentoShape.Squircle), plan.tiles[10]) // Settings
        assertEquals(BentoSlot(2, 4, 1, 1, BentoShape.Circle), plan.trailing)
        // Every cell of the 5 x 3 grid is covered once: nothing is left over, nothing overlaps.
        val covered = cells(plan)
        assertEquals(15, covered.size)
        assertEquals(15, covered.toSet().size)
        assertTrue(covered.all { (r, c) -> r in 0..2 && c in 0..4 })
    }

    @Test
    fun without_the_edit_button_the_free_corner_is_empty() {
        val plan = bentoPlan(arranged(ActionMenuLayout.Default), withEditButton = false)
        assertNull(plan.trailing)
        val free = (0..2).flatMap { r -> (0..4).map { c -> r to c } } - cells(plan).toSet()
        assertEquals(listOf(2 to 4), free)
    }

    @Test
    fun a_wide_tile_that_does_not_fit_the_row_starts_the_next_one_and_leaves_a_hole() {
        // Featured, then three small ones fill columns 1-3; the wide one needs two cells and only column 4 is left.
        val tiles = small("a", "b", "c", "d") + ArrangedAction(action("e"), ActionTileSize.Wide)
        val plan = bentoPlan(tiles, withEditButton = false)
        assertEquals(BentoSlot(1, 1, 1, 2, BentoShape.Wide), plan.tiles[4])
        assertEquals(2, plan.rows)
        assertTrue((0 to 4) !in cells(plan))
    }

    @Test
    fun two_wide_tiles_share_a_row_and_the_next_small_one_goes_below() {
        val tiles = small("a") + listOf("b", "c").map { ArrangedAction(action(it), ActionTileSize.Wide) } + small("d")
        val plan = bentoPlan(tiles, withEditButton = false)
        assertEquals(BentoSlot(0, 1, 1, 2, BentoShape.Wide), plan.tiles[1])
        assertEquals(BentoSlot(0, 3, 1, 2, BentoShape.Wide), plan.tiles[2])
        assertEquals(BentoSlot(1, 1, 1, 1, BentoShape.Squircle), plan.tiles[3])
        assertEquals(2, plan.rows)
        assertEquals(2, plan.tiles[0].rowSpan)
    }

    @Test
    fun the_featured_tile_is_tall_whatever_it_is_and_whatever_size_it_was() {
        val layout = ActionMenuLayout.Default.moved("income", 0)
        val plan = bentoPlan(arranged(layout), withEditButton = true)
        assertEquals("income", arranged(layout)[0].action.id)
        assertEquals(BentoShape.Hero, plan.tiles[0].shape)
        assertEquals(1, plan.tiles[0].colSpan)
        assertEquals(plan.rows, plan.tiles[0].rowSpan)
        // Expense, pushed down, is an ordinary small tile now.
        assertEquals(BentoShape.Squircle, plan.tiles[1].shape)
    }

    @Test
    fun a_grid_of_one_has_one_row_and_the_button_goes_beside_it() {
        val plan = bentoPlan(small("expense"), withEditButton = true)
        assertEquals(1, plan.rows)
        assertEquals(BentoSlot(0, 0, 1, 1, BentoShape.Hero), plan.tiles[0])
        assertEquals(BentoSlot(0, 1, 1, 1, BentoShape.Circle), plan.trailing)
        assertEquals(1, bentoPlan(small("expense"), withEditButton = false).rows)
    }

    @Test
    fun a_full_last_row_pushes_the_edit_button_onto_a_new_one() {
        // Featured + 8 small tiles fill two rows of four; the button starts the third.
        val plan = bentoPlan(small("a", "b", "c", "d", "e", "f", "g", "h", "i"), withEditButton = true)
        assertEquals(BentoSlot(2, 1, 1, 1, BentoShape.Circle), plan.trailing)
        assertEquals(3, plan.rows)
    }

    @Test
    fun a_tile_nearer_the_plus_button_arrives_before_one_further_away() {
        val plan = bentoPlan(arranged(ActionMenuLayout.Default), withEditButton = true)
        assertEquals(0, plan.trailing!!.arrivalRank(plan.rows)) // the corner the "+" button is under
        assertEquals(1, plan.tiles[10].arrivalRank(plan.rows)) // Settings
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
