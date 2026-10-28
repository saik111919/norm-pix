package com.example.normpix.navigation

import androidx.compose.runtime.Immutable

/*
 * How the "+" menu is arranged, as the user left it: which tile comes where, and at which size. Pure data, so it can be
 * kept (Settings > Appearance reset, app backup), tested and packed into the grid without a screen.
 *
 * Follows the Pixel Quick Settings editor (verified 2026-10-28, see references/verified-facts.md): tiles come in two sizes - the
 * icon-only square and the wide one that carries the name - and are put in order by dragging. The menu has one more rule of its
 * own, from its design: the first tile is the *featured* one, the tall capsule down the left edge, whatever action it is.
 */

/** The two sizes of a tile, as on Pixel: a square with the icon and a short name, or twice as wide. */
enum class ActionTileSize { Small, Wide }

/** One tile: which action ([FooterAction.id]) and its size. The featured (first) tile is always tall; its size is not used. */
data class ActionMenuEntry(val id: String, val size: ActionTileSize = ActionTileSize.Small)

/** An action as the menu shows it: in its place, at its size. */
data class ArrangedAction(val action: FooterAction, val size: ActionTileSize)

@Immutable
data class ActionMenuLayout(val entries: List<ActionMenuEntry>) {

    fun indexOf(id: String): Int = entries.indexOfFirst { it.id == id }

    /** [id] moved so that it sits at [toIndex] (clamped), the others making room; this layout when nothing would change. */
    fun moved(id: String, toIndex: Int): ActionMenuLayout {
        val from = indexOf(id)
        if (from < 0) return this
        val to = toIndex.coerceIn(0, entries.lastIndex)
        if (to == from) return this
        val list = entries.toMutableList()
        list.add(to, list.removeAt(from))
        return ActionMenuLayout(list)
    }

    fun resized(id: String, size: ActionTileSize): ActionMenuLayout {
        val at = indexOf(id)
        if (at < 0 || entries[at].size == size) return this
        return ActionMenuLayout(entries.mapIndexed { i, e -> if (i == at) e.copy(size = size) else e })
    }

    /**
     * [actions] in this layout's order and sizes. An action this layout does not know (one a newer NormPix added) comes
     * after the others, small; an entry for an action that is not there (one an older or a different screen lacks) is left out.
     */
    fun arranged(actions: List<FooterAction>): List<ArrangedAction> {
        val known = entries.map { it.id }.toSet()
        val placed = ArrayList<ArrangedAction>(actions.size)
        for (entry in entries) {
            val action = actions.firstOrNull { it.id == entry.id } ?: continue
            if (placed.none { it.action.id == entry.id }) placed += ArrangedAction(action, entry.size)
        }
        for (action in actions) if (action.id !in known) placed += ArrangedAction(action, ActionTileSize.Small)
        return placed
    }

    /** The text kept in the preferences: `id:s,id:w,...`, in order. [parse] reads it back. */
    fun encode(): String = entries.joinToString(",") { "${it.id}:${if (it.size == ActionTileSize.Wide) 'w' else 's'}" }

    companion object {
        /** The design's own arrangement: Expense the featured tile, Income wide, the rest small, in reading order. */
        val Default = ActionMenuLayout(
            listOf(
                ActionMenuEntry("expense"),
                ActionMenuEntry("income", ActionTileSize.Wide),
                ActionMenuEntry("recurring"),
                ActionMenuEntry("reminder"),
                ActionMenuEntry("card"),
                ActionMenuEntry("account"),
                ActionMenuEntry("categories"),
                ActionMenuEntry("budgets"),
                ActionMenuEntry("people"),
                ActionMenuEntry("search"),
                ActionMenuEntry("settings"),
            ),
        )

        /**
         * The layout in [raw] (what [encode] wrote), or null for nothing or nothing readable. Forgiving, because the text can
         * come from a backup: unknown sizes are small, repeats are dropped, blanks are skipped.
         */
        fun parse(raw: String?): ActionMenuLayout? {
            if (raw.isNullOrBlank()) return null
            val seen = HashSet<String>()
            val entries = raw.split(',').mapNotNull { part ->
                val id = part.substringBefore(':').trim()
                if (id.isEmpty() || !seen.add(id)) return@mapNotNull null
                val size = if (part.substringAfter(':', "").trim().equals("w", ignoreCase = true)) ActionTileSize.Wide else ActionTileSize.Small
                ActionMenuEntry(id, size)
            }
            return entries.takeIf { it.isNotEmpty() }?.let(::ActionMenuLayout)
        }

        /** What is kept for [layout]: nothing at all when it is the default, so a reset leaves no trace. */
        fun keptAs(layout: ActionMenuLayout): String = if (layout == Default) "" else layout.encode()

        /** The saved layout in [raw], or the default. */
        fun from(raw: String?): ActionMenuLayout = parse(raw) ?: Default
    }
}

/** The layout of what is shown now, whole: every arranged action in order at its size, so an edit keeps the ones not yet saved. */
internal fun List<ArrangedAction>.toLayout(): ActionMenuLayout = ActionMenuLayout(map { ActionMenuEntry(it.action.id, it.size) })

/* ---- Packing into the grid ---- */

/** The grid has five columns of equal cells: the featured tile's, then four for the rest. */
internal const val BentoColumns = 5

internal enum class BentoShape { Hero, Wide, Squircle, Circle }

/** Where a tile sits on the grid, in cells. */
internal data class BentoSlot(val row: Int, val col: Int, val rowSpan: Int, val colSpan: Int, val shape: BentoShape)

/** The tiles' slots (same order as the arranged actions), the Edit button's, and how many rows the grid has. */
internal data class BentoPlan(val tiles: List<BentoSlot>, val trailing: BentoSlot?, val rows: Int)

/** Small tiles of these actions are circles, as in the design; every other small tile is a squircle. */
private val RoundActionIds = setOf("reminder", "budgets")

/**
 * Packs [arranged] into the grid: the first tile is the featured one, a tall capsule in the first column as high as the grid;
 * the others flow left to right in the four columns beside it, a small tile taking one cell and a wide one two, moving on to
 * the next row when a wide one does not fit in what is left of this one (that leaves a cell empty, as on Pixel). The Edit
 * button, when offered ([withEditButton]), is the last tile of the flow - at the design's free corner when the grid is full.
 */
internal fun bentoPlan(arranged: List<ArrangedAction>, withEditButton: Boolean): BentoPlan {
    val tiles = ArrayList<BentoSlot>(arranged.size)
    var row = 0
    var col = 1
    fun place(span: Int, shape: BentoShape): BentoSlot {
        if (col + span > BentoColumns) {
            row++
            col = 1
        }
        return BentoSlot(row, col, 1, span, shape).also { col += span }
    }
    arranged.forEachIndexed { i, tile ->
        tiles += if (i == 0) {
            BentoSlot(0, 0, 1, 1, BentoShape.Hero)
        } else if (tile.size == ActionTileSize.Wide) {
            place(2, BentoShape.Wide)
        } else {
            place(1, if (tile.action.id in RoundActionIds) BentoShape.Circle else BentoShape.Squircle)
        }
    }
    val trailing = if (withEditButton) place(1, BentoShape.Circle) else null
    val rows = ((tiles.drop(1) + listOfNotNull(trailing)).maxOfOrNull { it.row + 1 } ?: 1)
    return BentoPlan(tiles.mapIndexed { i, s -> if (i == 0 && s.shape == BentoShape.Hero) s.copy(rowSpan = rows) else s }, trailing, rows)
}

/** How many beats a tile waits to arrive: its distance, in cells, from the bottom-right corner where the "+" button is. */
internal fun BentoSlot.arrivalRank(rows: Int): Int =
    (rows - 1 - (row + rowSpan - 1)) + (BentoColumns - 1 - (col + colSpan - 1))
