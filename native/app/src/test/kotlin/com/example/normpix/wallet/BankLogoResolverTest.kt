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
    fun foreignBanksAndNeobanksResolveToTheirArtwork() {
        assertEquals("banks/boa.svg", BankLogoResolver.logoFor("Bank of America")?.pick(compact = false, onDark = true)?.path)
        assertEquals("banks/boa_symbol.svg", BankLogoResolver.logoFor("Bank of America")?.pick(compact = true, onDark = true)?.path)
        assertEquals("banks/sbm.svg", BankLogoResolver.logoFor("SBM Bank India")?.pick(compact = false, onDark = true)?.path)
        assertEquals("banks/sbm_symbol.svg", BankLogoResolver.logoFor("SBM Bank India")?.pick(compact = true, onDark = true)?.path)
        assertEquals("brandlogos/onecard.webp", BankLogoResolver.logoFor("OneCard")?.pick(compact = true, onDark = false)?.path)
        assertEquals("brandlogos/jupiterbank.webp", BankLogoResolver.logoFor("Jupiter")?.pick(compact = true, onDark = false)?.path)
    }

    @Test
    fun knownBanksWithoutArtworkFallBack() {
        assertEquals("capital", bank("Capital Small Finance Bank"))
        assertNull(BankLogoResolver.logoFor("Capital Small Finance Bank"))
        assertNull(BankLogoResolver.logoFor("Cosmos Co-operative Bank"))
        assertNull(BankLogoResolver.logoFor("Bank of Ceylon"))
    }

    @Test
    fun banksThatHaveOnlyARoundPictureShowItInBothSlots() {
        assertEquals("hsbc", bank("HSBC"))
        for (name in listOf("HSBC", "Equitas Small Finance Bank", "Saraswat Co-operative Bank", "Kerala Gramin Bank")) {
            val logo = BankLogoResolver.logoFor(name)!!
            val compact = logo.pick(compact = true, onDark = false)!!
            assertTrue(name, compact.isPicture && compact.path.startsWith("brandlogos/"))
            assertEquals(name, compact.path, logo.pick(compact = false, onDark = false)!!.path)
            // Full-colour artwork on a dark surface goes on a light backing, as the SVG ones do.
            assertTrue(name, logo.pick(compact = true, onDark = true)!!.needsLightBacking)
        }
        assertTrue(BankLogoResolver.logoFor("HDFC Bank")!!.pick(compact = true, onDark = false)!!.isPicture.not())
    }

    @Test
    fun moreOfIndiasBanksAreInTheDirectory() {
        val expected = mapOf(
            "Jana Small Finance Bank" to "jana", "Capital SFB" to "capital", "Cosmos Bank" to "cosmos",
            "Kerala Gramin Bank" to "keralagramin", "APGVB" to "apgvb", "Baroda UP Bank" to "barodaup",
            "Syndicate Bank" to "syndicate", "Allahabad Bank" to "allahabad", "Oriental Bank of Commerce" to "obc",
            "State Bank of Hyderabad" to "sbhyderabad", "JPMorgan Chase" to "jpmorgan", "Emirates NBD" to "emiratesnbd",
            "Saraswat Bank" to "saraswat", "SBM Bank" to "sbmindia", "Mashreq" to "mashreq",
            "OneCard" to "onecard", "Jupiter" to "jupiter", "Slice" to "slice", "LazyPay" to "lazypay",
        )
        for ((name, id) in expected) assertEquals(name, id, bank(name))
        // A bank that merged into another stays itself: "State Bank of Hyderabad" is not SBI.
        assertEquals("sbi", bank("State Bank of India"))
    }

    @Test
    fun everyBankHasItsOwnIdAndNoTwoBanksShareAName() {
        assertEquals(BankDirectory.entries.size, BankDirectory.entries.map { it.id }.toSet().size)
        val written = BankDirectory.entries.flatMap { entry ->
            (listOf(entry.name) + entry.aliases).map(BankLogoResolver::normalize).distinct().map { it to entry.id }
        }
        val shared = written.groupBy({ it.first }, { it.second }).filterValues { it.distinct().size > 1 }
        assertTrue("names two banks share: $shared", shared.isEmpty())
    }

    @Test
    fun everyKindOfBankIsRepresentedAndEarlierNamesSayWhatTheyBecame() {
        for (category in BankCategory.entries) assertTrue(category.name, BankDirectory.entries.any { it.category == category })
        assertTrue(BankDirectory.entries.size >= 150)
        for (entry in BankDirectory.entries.filter { it.category == BankCategory.Earlier }) {
            assertTrue(entry.name, entry.note!!.startsWith("Now part of "))
        }
        assertTrue(BankDirectory.entries.filter { it.category != BankCategory.Earlier }.all { it.note == null })
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
        // The SVG set is the bank logos' own folder; the round pictures are the brand set's, shared with merchants and payees.
        val (pictures, svgs) = referenced.partition { it.startsWith("brandlogos/") }
        for (path in svgs) assertTrue("missing asset $path", path.removePrefix("banks/") in bundled)
        val brands = File("src/main/assets/brandlogos").list()!!.toSet()
        for (path in pictures) assertTrue("missing picture $path", path.removePrefix("brandlogos/") in brands)
        val unused = bundled - svgs.map { it.removePrefix("banks/") }.toSet()
        assertTrue("assets no bank uses: $unused", unused.isEmpty())
    }

    @Test
    fun everyBankResolvesFromItsOwnNameAndAliases() {
        for (entry in BankDirectory.entries) {
            for (name in listOf(entry.name) + entry.aliases) assertEquals(name, entry.id, bank(name))
        }
    }
}
