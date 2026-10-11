package com.example.normpix.banks

import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.wallet.BankDirectory
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one list of banks that accounts and cards share: what is in it, how a name finds a bank, how pickers order it. */
class BankCatalogTest {
    private val created = LocalDateTime.of(2026, 10, 1, 9, 0)
    private fun custom(key: String, name: String) = CustomBank(key, name, created)

    @Test
    fun theBuiltInBanksAreTheDirectorysAndOnlyTheDirectorys() {
        val catalog = BankCatalog()
        assertEquals(BankDirectory.entries.map { it.id }, catalog.all.map { it.key })
        assertTrue(catalog.all.all { it.builtIn })
        assertEquals("HDFC Bank", catalog.byKey("hdfc")?.name)
        assertNotNull("a built-in bank carries its artwork", catalog.byKey("hdfc")?.logo)
        assertNull(catalog.byKey("nope"))
        assertNull(catalog.byKey(null))
    }

    @Test
    fun theUsersBanksFollowTheBuiltInOnesAlphabetically() {
        val catalog = BankCatalog(listOf(custom("custom-b", "Zeta Credit Union"), custom("custom-a", "Alpha Co-op")))
        val own = catalog.all.filter { !it.builtIn }
        assertEquals(listOf("Alpha Co-op", "Zeta Credit Union"), own.map { it.name })
        assertTrue(own.all { it.logo == null })
        assertEquals("custom-a", catalog.byKey("custom-a")?.key)
    }

    @Test
    fun aNameFindsItsBankWhateverTheCaseOrSuffix() {
        val catalog = BankCatalog()
        for (written in listOf("HDFC Bank", "hdfc", "  HDFC BANK LTD. ", "H.D.F.C. Bank Limited")) {
            assertEquals(written, "hdfc", catalog.exact(written)?.key)
        }
        assertNull(catalog.exact(""))
        assertNull(catalog.exact("   "))
        assertNull(catalog.exact("A Bank Nobody Has Heard Of"))
    }

    @Test
    fun theUsersOwnBanksAreFoundTheSameWay() {
        val catalog = BankCatalog(listOf(custom("custom-1", "My Credit Union")))
        assertEquals("custom-1", catalog.exact("my credit union")?.key)
        assertEquals("custom-1", catalog.exact("MY CREDIT UNION LTD")?.key)
        assertNull(catalog.exact("My Credit"))
    }

    @Test
    fun exactIsNotFuzzyButResolveIsForNamesTypedBeforeBanksWereShared() {
        val catalog = BankCatalog()
        assertNull(catalog.exact("HDFC Bank, Fort branch"))
        assertEquals("hdfc", catalog.resolve("HDFC Bank, Fort branch")?.key)
        assertEquals("hdfc", catalog.resolve("hdfc")?.key)
        assertNull(catalog.resolve("My Credit Union"))
    }

    @Test
    fun anAccountsNameOnlyLinksToABankItStartsWith() {
        val catalog = BankCatalog()
        assertEquals("hdfc", catalog.leading("HDFC Savings")?.key)
        assertEquals("icici", catalog.leading("ICICI Credit Card")?.key)
        assertEquals("paytm", catalog.leading("Paytm Wallet")?.key)
        assertNull("only a leading bank counts", catalog.leading("Savings for Axis bike"))
        assertNull(catalog.leading("Cash"))
        assertNull(catalog.leading("Emergency fund"))
        assertNull(catalog.leading(""))
    }

    @Test
    fun theLongestLeadingNameWins() {
        val catalog = BankCatalog()
        assertEquals("sbi", catalog.leading("State Bank of India Salary")?.key)
        assertEquals("boi", catalog.leading("Bank of India Salary")?.key)
    }

    @Test
    fun theBanksInUseComeFirstInPickers() {
        val catalog = BankCatalog(listOf(custom("custom-1", "Zeta Credit Union")))
        val ordered = catalog.ordered(setOf("custom-1", "sbi", "axis"))
        assertEquals(listOf("axis", "sbi", "custom-1"), ordered.take(3).map { it.key }) // by name: Axis, State Bank, Zeta
        assertEquals(catalog.all.size, ordered.size)
        assertEquals(ordered.size, ordered.map { it.key }.toSet().size)
        assertEquals(catalog.all.map { it.key }, catalog.ordered(emptySet()).map { it.key })
    }

    @Test
    fun typingNarrowsTheListByNameOrAlias() {
        val catalog = BankCatalog(listOf(custom("custom-1", "My Credit Union")))
        assertTrue(catalog.matching("axi").any { it.key == "axis" })
        assertTrue("an alias finds its bank", catalog.matching("SBI").any { it.key == "sbi" })
        assertEquals(listOf("custom-1"), catalog.matching("credit union").map { it.key })
        assertTrue(catalog.matching("zzzz").isEmpty())
        assertEquals(catalog.all.size, catalog.matching("  ").size)
        assertFalse(catalog.matching("HDFC").isEmpty())
    }

    @Test
    fun newCustomKeysAreUnique() {
        val keys = List(50) { BankCatalog.newCustomKey() }
        assertEquals(50, keys.toSet().size)
        assertTrue(keys.all { it.startsWith("custom-") })
        assertTrue("never collides with a directory id", keys.none { BankCatalog.BuiltIn.byKey(it) != null })
    }
}
