package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.normpix.banks.Bank
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.BankCategory
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings > Banks: every bank with its logo, found by a name or by what it is also called, narrowed by a kind of bank. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class BanksSettingsPageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("banks_settings_page")
    }

    @After
    fun tearDown() = env.close()

    private fun open() {
        compose.setContent { env.Content { BanksSettingsPage() } }
        compose.waitForIdle()
    }

    /** The page is a lazy list: a row far down is not there until the list has been scrolled to it. */
    private fun scrollTo(text: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        compose.waitForIdle()
    }

    private fun search(text: String) {
        compose.onNodeWithText("Search banks").performTextInput(text)
        compose.waitForIdle()
    }

    @Test
    fun listsTheBanksGroupedByKind_withTheirCount() {
        open()
        val all = BankCatalog.BuiltIn.all
        compose.onNodeWithText("${all.size} banks", substring = true).assertExists()
        val publicSector = all.count { it.category == BankCategory.PublicSector }
        compose.onNodeWithText("Public sector banks · $publicSector").assertExists()
        compose.onNodeWithText("State Bank of India").assertExists()
    }

    @Test
    fun aSearchFindsABankByAnotherNameItGoesBy() {
        open()
        search("SBI")
        compose.onNodeWithText("State Bank of India").assertExists()
        compose.onNodeWithText("HDFC Bank").assertDoesNotExist()
        // The row says what else it is called.
        compose.onNodeWithText("SBI, State Bank").assertExists()
    }

    @Test
    fun aKindOfBankNarrowsTheList_andAllBringsThemBack() {
        open()
        compose.onNodeWithText("Co-operative").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Co-operative").assertIsSelected()
        compose.onNodeWithText("Saraswat Co-operative Bank").assertExists()
        compose.onNodeWithText("State Bank of India").assertDoesNotExist()
        compose.onNodeWithText("All").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("State Bank of India").assertExists()
    }

    @Test
    fun aNameNoBankHasOffersToAddIt() {
        open()
        search("Zzyzx Savings")
        compose.onNodeWithText("No bank called \"Zzyzx Savings\"").assertExists()
        compose.onNodeWithText("Add \"Zzyzx Savings\"").assertExists()
    }

    @Test
    fun theHeaderButtonAndTheLastRowAddABank() {
        open()
        compose.onNodeWithContentDescription("Add a bank").assertExists()
        scrollTo("Add a bank")
        compose.onNodeWithText("Add a bank").assertExists()
    }

    @Test
    fun aBankOpensItsSheet() {
        open()
        scrollTo("Syndicate Bank")
        compose.onNodeWithText("Syndicate Bank").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Now part of Canara Bank").assertCountEquals(2) // the row, and the sheet
        compose.onNodeWithText("Not used by any of your accounts or cards yet").assertExists()
    }

    // ---- the page's own rules, without a screen ----

    private val banks = BankCatalog.BuiltIn.all

    @Test
    fun groupsFollowTheDirectoryAndLeaveOutWhatIsEmpty() {
        val groups = bankGroups(banks, BankFilter.All, emptyMap())
        assertEquals(BankCategory.entries.map { it.name }, groups.map { it.key })
        assertEquals(banks.size, groups.sumOf { it.banks.size })

        val onlyCoOps = bankGroups(banks, BankFilter.Kind(BankCategory.CoOperative), emptyMap())
        assertEquals(listOf(BankCategory.CoOperative.name), onlyCoOps.map { it.key })

        val onlyDigital = bankGroups(banks, BankFilter.Kind(BankCategory.Digital), emptyMap())
        assertEquals(listOf(BankCategory.Digital.name), onlyDigital.map { it.key })
        assertTrue(onlyDigital.first().banks.any { it.name == "OneCard" })
        assertTrue(onlyDigital.first().banks.any { it.name == "Jupiter" })
    }

    @Test
    fun yourOwnBanksComeFirstAndHaveTheirOwnChip() {
        val mine = BankCatalog(listOf(CustomBank("custom-1", "My Credit Union", LocalDateTime.of(2026, 1, 1, 0, 0))))
        val groups = bankGroups(mine.all, BankFilter.All, emptyMap())
        assertEquals("yours", groups.first().key)
        assertEquals(listOf("My Credit Union"), groups.first().banks.map(Bank::name))
        assertEquals(listOf("yours"), bankGroups(mine.all, BankFilter.Yours, emptyMap()).map { it.key })
    }

    @Test
    fun inUseKeepsOnlyTheBanksYourAccountsAndCardsUse() {
        val used = mapOf("sbi" to BankUsage(accounts = 2, cards = 1))
        val groups = bankGroups(banks, BankFilter.InUse, used)
        assertEquals(listOf("sbi"), groups.flatMap { it.banks }.map { it.key })
    }

    @Test
    fun theLineUnderABankSaysWhatItIsAndWhatUsesIt() {
        val sbi = banks.first { it.key == "sbi" }
        assertEquals("SBI, State Bank", bankLine(sbi, null))
        assertEquals("SBI, State Bank · 2 accounts · 1 card", bankLine(sbi, BankUsage(2, 1)))
        assertEquals("Now part of Canara Bank", bankLine(banks.first { it.key == "syndicate" }, BankUsage(0, 0)))
        assertNull(bankLine(banks.first { it.key == "indian" }, null))
        assertNull(BankUsage(0, 0).words)
        assertEquals("1 account", BankUsage(1, 0).words)
        assertTrue(BankUsage(0, 1).any)
    }
}
