package com.example.normpix.ui.theme

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppIconsTest {
    private fun paths(icon: androidx.compose.ui.graphics.vector.ImageVector): List<VectorPath> =
        icon.root.iterator().asSequence().filterIsInstance<VectorPath>().toList()

    @Test
    fun theWalletIsTheSuppliedArtworkOnAFortyEightGrid() {
        val icon = AppIcons.Wallet
        assertEquals(48f, icon.viewportWidth)
        assertEquals(48f, icon.viewportHeight)
        // The same nominal size as every Material icon, so it sits in the dock and tabs without special spacing.
        assertEquals(24.dp, icon.defaultWidth)
        assertEquals(24.dp, icon.defaultHeight)
        // Three flap strokes and the body, all parsed from the artwork's path data.
        assertEquals(4, paths(icon).size)
        assertTrue(paths(icon).all { it.pathData.size > 2 && it.pathData.first() is PathNode.MoveTo })
    }

    @Test
    fun theOutlineIsStrokedAndTheActiveWalletFillsItsBody() {
        val outline = paths(AppIcons.Wallet)
        val filled = paths(AppIcons.WalletFilled)

        assertTrue(outline.all { it.stroke != null && it.fill == null }, "inactive: strokes only")
        assertEquals(4, filled.size)
        assertNotNull(filled.last().fill, "active: the body is solid")
        assertNull(filled.first().fill, "active: the flap stays a stroke")
        // Same silhouette either way, so selecting an item never changes its size.
        assertEquals(AppIcons.Wallet.defaultWidth, AppIcons.WalletFilled.defaultWidth)
        assertEquals(outline.map { it.strokeLineWidth }, filled.map { it.strokeLineWidth })
    }
}
