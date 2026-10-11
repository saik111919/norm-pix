package com.example.normpix.wallet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankLogoResolverTest {
    private fun bank(name: String) = BankLogoResolver.bankFor(name)?.id

    @Test
    fun normalizesCasePunctuationAndLegalSuffixes() {
        assertEquals("hdfc bank", BankLogoResolver.normalize("  HDFC   BANK LTD. "))
        assertEquals("icici bank", BankLogoResolver.normalize("ICICI Bank Limited"))
        assertEquals("hdfc bank", BankLogoResolver.normalize("H.D.F.C. Bank Pvt. Ltd"))
        assertEquals("jammu and kashmir bank", BankLogoResolver.normalize("The Jammu & Kashmir Bank"))
        assertEquals("idfc first bank", BankLogoResolver.normalize("IDFC-FIRST Bank"))
        assertEquals("", BankLogoResolver.normalize("  "))
    }

    @Test
    fun resolvesCommonSpellingsOfTheSameBank() {
        for (name in listOf("HDFC", "HDFC Bank", "HDFC BANK LTD", "hdfc bank limited", "HDFCBANK", "HDFC Savings")) {
            assertEquals(name, "hdfc", bank(name))
        }
        for (name in listOf("ICICI", "ICICI Bank", "ICICI BANK LIMITED", "ICICI Credit Card", "ICICIBANK")) {
            assertEquals(name, "icici", bank(name))
        }
        val expected = mapOf(
            "SBI" to "sbi", "State Bank of India" to "sbi", "SBI Card" to "sbi",
            "Axis Bank" to "axis", "AXIS" to "axis",
            "Kotak" to "kotak", "Kotak Mahindra Bank" to "kotak",
            "Yes Bank" to "yes", "YES BANK LTD" to "yes",
            "IndusInd Bank" to "indusind",
            "IDFC FIRST Bank" to "idfcfirst", "IDFC Bank" to "idfcfirst",
            "Federal Bank" to "federal",
            "Bank of Baroda" to "bob", "BOB" to "bob",
            "J&K Bank" to "jk", "Punjab & Sind Bank" to "psb", "Punjab and Sind Bank" to "psb",
            "Dhanlaxmi Bank" to "dhanalakshmi", "AU Small Finance Bank Ltd" to "au",
        )
        for ((name, id) in expected) assertEquals(name, id, bank(name))
    }

    @Test
    fun theMostSpecificBankWinsOverlappingNames() {
        assertEquals("sbi", bank("State Bank of India"))
        assertEquals("boi", bank("Bank of India"))
        assertEquals("central", bank("Central Bank of India"))
        assertEquals("union", bank("Union Bank of India"))
        assertEquals("indian", bank("Indian Bank"))
        assertEquals("iob", bank("Indian Overseas Bank"))
        assertEquals("southindian", bank("South Indian Bank"))
        assertEquals("pnb", bank("Punjab National Bank"))
    }

    @Test
    fun unknownNamesAreNotGuessed() {
        for (name in listOf("", "   ", "Cash", "Bank", "India", "Kisan Co-op Bank", "Bobcat Credit Union", "Citizens Bank", "Yesterday Savings")) {
            assertNull(name, bank(name))
            assertNull(name, BankLogoResolver.logoFor(name))
        }
    }

    @Test
    fun hdfcAndIciciResolveToTheirOwnArtwork() {
        assertEquals("banks/hdfc.svg", BankLogoResolver.logoFor("HDFC Bank")?.pick(compact = false, onDark = true)?.path)
        assertEquals("banks/hdfc_symbol.svg", BankLogoResolver.logoFor("HDFC Bank")?.pick(compact = true, onDark = true)?.path)
        assertEquals("banks/icic.svg", BankLogoResolver.logoFor("ICICI BANK LIMITED")?.pick(compact = false, onDark = true)?.path)
        assertEquals("banks/icic_symbol.svg", BankLogoResolver.logoFor("ICICI BANK LIMITED")?.pick(compact = true, onDark = true)?.path)
    }

    @Test
    fun knownBanksWithoutArtworkFallBack() {
        assertEquals("hsbc", bank("HSBC"))
        assertNull(BankLogoResolver.logoFor("HSBC"))
        assertNull(BankLogoResolver.logoFor("Equitas Small Finance Bank"))
    }

    @Test
    fun picksTheVariantForTheSlotAndFallsBackToTheOther() {
        val both = BankLogoArt(wordmark = "w.svg", symbol = "s.svg")
        assertEquals(BankLogoFile("w.svg", needsLightBacking = true), both.pick(compact = false, onDark = true))
        assertEquals(BankLogoFile("s.svg", needsLightBacking = false), both.pick(compact = true, onDark = false))
        assertEquals("w.svg", BankLogoArt(wordmark = "w.svg").pick(compact = true, onDark = false)?.path)
        assertEquals("s.svg", BankLogoArt(symbol = "s.svg").pick(compact = false, onDark = false)?.path)
        assertNull(BankLogoArt().pick(compact = false, onDark = false))

        // Reversed-out artwork is used as is on dark surfaces, and only there.
        val reversed = BankLogoArt(wordmark = "w.svg", symbol = "s.svg", wordmarkOnDark = "w_dark.svg")
        assertEquals(BankLogoFile("w_dark.svg", needsLightBacking = false), reversed.pick(compact = false, onDark = true))
        assertEquals(BankLogoFile("w.svg", needsLightBacking = false), reversed.pick(compact = false, onDark = false))
        assertEquals(BankLogoFile("s.svg", needsLightBacking = true), reversed.pick(compact = true, onDark = true))
    }

    @Test
    fun directoryAndBundledArtworkAgree() {
        val bundled = File("src/main/assets/banks").list()!!.toSet()
        val referenced = BankDirectory.entries.flatMap { listOfNotNull(it.logo?.wordmark, it.logo?.symbol, it.logo?.wordmarkOnDark, it.logo?.symbolOnDark) }
        for (path in referenced) assertTrue("missing asset $path", path.removePrefix("banks/") in bundled)
        val unused = bundled - referenced.map { it.removePrefix("banks/") }.toSet()
        assertTrue("assets no bank uses: $unused", unused.isEmpty())
    }

    @Test
    fun everyBankResolvesFromItsOwnNameAndAliases() {
        for (entry in BankDirectory.entries) {
            for (name in listOf(entry.name) + entry.aliases) assertEquals(name, entry.id, bank(name))
        }
    }
}
