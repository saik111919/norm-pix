package com.example.normpix.wallet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The monogram a bank without a logo wears: complete, legible, distinct, and sensible for a bank the user adds. */
class BankMarksTest {
    private val withoutArtwork = BankDirectory.entries.filter { it.logo == null }

    @Test
    fun everyBankWithoutArtworkHasAListedMark() {
        val missing = withoutArtwork.filter { it.id !in BankMarks.listed }.map { it.id }
        assertTrue(missing.isEmpty(), "no listed mark for: $missing")
    }

    @Test
    fun noListedMarkIsForABankThatHasArtworkOrDoesNotExist() {
        val ids = BankDirectory.entries.associateBy { it.id }
        val stray = BankMarks.listed.keys.filter { ids[it] == null || ids[it]!!.logo != null }
        assertTrue(stray.isEmpty(), "marks for banks that don't need one: $stray")
    }

    @Test
    fun everyMarkIsOneToFourCapitalLettersOrDigits() {
        val bad = BankMarks.listed.filterValues { !Regex("[A-Z0-9]{1,${BankMarks.MAX_LENGTH}}").matches(it) }
        assertTrue(bad.isEmpty(), "marks that are not 1-4 capitals: $bad")
    }

    @Test
    fun noTwoBanksShareAMark() {
        val shared = BankMarks.listed.values.groupingBy { it }.eachCount().filterValues { it > 1 }
        assertTrue(shared.isEmpty(), "marks used twice: $shared")
    }

    @Test
    fun theRegionalRuralBanksAreNotToldApartByOneOrTwoLetters() {
        // "UB", "MP", "HP" read as nothing: the conventional longer form is used.
        val short = withoutArtwork.filter { it.category == BankCategory.RegionalRural }.filter { BankMarks.of(it.id, it.name).length < 3 }
        assertTrue(short.isEmpty(), "too short: ${short.map { it.id }}")
    }

    @Test
    fun aListedMarkWinsOverTheRule() {
        assertEquals("TGB", BankMarks.of("telanganagb", "Telangana Grameena Bank"))
        assertEquals("SBMY", BankMarks.of("sbmysore", "State Bank of Mysore"))
        assertEquals("SBM", BankMarks.of("sbmindia", "SBM Bank India"))
    }

    @Test
    fun aBankTheUserAddsGetsTheInitialsOfItsDistinctiveWords() {
        assertEquals("FCU", BankMarks.initials("Federal Credit Union"))
        assertEquals("MCU", BankMarks.initials("My Credit Union"))
        // Generic words do not count: this is the "Anand" bank, not "AC".
        assertEquals("ANA", BankMarks.initials("Anand Co-operative Bank"))
        assertEquals("ANA", BankMarks.initials("Anand Sahakari Bank Ltd"))
        assertEquals("UBS", BankMarks.initials("UBS"))
        assertEquals("ZE", BankMarks.initials("Ze"))
        assertEquals("ST", BankMarks.initials("St. Bank")) // only "St" is distinctive
        assertEquals("ABCD", BankMarks.initials("Alpha Bravo Charlie Delta Echo Bank"))
    }

    @Test
    fun aNameOfNothingButCommonWordsStillGetsALetterOrTwo() {
        assertEquals("GRA", BankMarks.initials("Gramin"))
        assertEquals("BOT", BankMarks.initials("Bank of the"))
        assertEquals("?", BankMarks.initials("   "))
    }

    @Test
    fun theMarkNeverRunsPastTheBadge() {
        assertTrue(BankMarks.initials("A B C D E F G H").length <= BankMarks.MAX_LENGTH)
        assertTrue(BankMarks.initials("Supercalifragilistic").length <= BankMarks.MAX_LENGTH)
    }
}
