package com.example.normpix.finance.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the "For" field calls the user: the saved name with "(me)", or a bare "Me" (owner, 2026-11-08). */
class MyLabelTest {

    @Test
    fun noSavedName_isABareMe() {
        assertEquals("Me", myLabel(""))
        assertEquals("Me", myLabel("   "))
    }

    @Test
    fun aSavedName_getsMeAfterIt() {
        assertEquals("Asha (me)", myLabel("Asha"))
        assertEquals("Asha Rao (me)", myLabel("Asha Rao"))
    }

    @Test
    fun spacesAreTidied() {
        assertEquals("Asha Rao (me)", myLabel("  Asha    Rao \n"))
    }

    @Test
    fun aNameJustAtTheLimit_isNotCut() {
        val name = "A".repeat(24)
        assertEquals("$name (me)", myLabel(name))
    }

    @Test
    fun aLongName_isCutBeforeMe_soMeIsNeverTheCutPart() {
        val label = myLabel("Venkata Subramanian Iyer Narayanan")
        assertEquals("Venkata Subramanian Iye… (me)", label)
        assertTrue(label.endsWith(" (me)"))
        assertTrue(label.length <= 24 + " (me)".length)
    }

    @Test
    fun aCutNeverSplitsAnEmoji() {
        // The 24th character would fall in the middle of the emoji's pair of UTF-16 units.
        val name = "A".repeat(22) + "😀" + "BBBB"
        val label = myLabel(name)
        assertEquals("A".repeat(22) + "… (me)", label)
        assertTrue(label.none { it.isSurrogate() }, "no half of a pair is left: $label")
    }
}
