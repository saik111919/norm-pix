package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.normpix.banks.Bank
import com.example.normpix.banks.BankCatalog
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.CustomBank
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.BankCategory
import java.time.LocalDateTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings > Banks & Categories, the Banks side: every bank with its logo, found by a name or by what it is also called, narrowed by a kind of bank. */
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
        compose.setContent { env.Content { BanksAndCategoriesPage() } }
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
    fun thePageOpensOnBanks_andItsSwitchBringsTheCategories() {
        open()
        // Both sides are there, and the page opens on Banks: a bank and its chips are on it.
        compose.onNodeWithText("Banks").assertExists().assertIsSelected()
        compose.onNodeWithText("Categories").assertExists()
        compose.onNodeWithText("State Bank of India").assertExists()

        compose.onNodeWithText("Categories").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Categories").assertIsSelected()
        // The categories page's own control, and no bank list.
        compose.onNodeWithText("Expense").assertExists()
        compose.onNodeWithText("State Bank of India").assertDoesNotExist()
    }

    @Test
    fun theCategoriesEntryOpensOnTheCategoriesTab() {
        // What Routes.CATEGORIES renders (Finance settings, the "+" menu's tile, Settings search).
        compose.setContent { env.Content { BanksAndCategoriesPage(BanksAndCategoriesTab.Categories) } }
        compose.waitForIdle()
        compose.onNodeWithText("Categories").assertIsSelected()
        compose.onNodeWithText("Expense").assertExists()
        compose.onNodeWithText("State Bank of India").assertDoesNotExist()
    }

    @Test
    fun settingsRowShowsBanksAndCategoriesWithCounts() {
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        compose.onNodeWithText("Banks & Categories").assertExists()
        val bankCount = env.container.finance.state.value.catalog.all.size
        val catCount = env.container.finance.state.value.categories.count { !it.archived }
        compose.onNodeWithText("$bankCount banks · $catCount categories").assertExists()
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

    // The icon's description is "Search banks" and so is the field's placeholder text, so the icon is found by its role.
    private fun headerSearchIcon() = compose.onAllNodes(hasContentDescription("Search banks") and hasClickAction())

    @Test
    fun withTheSearchFieldInViewTheHeaderHasNoSearchIcon() {
        open()
        headerSearchIcon().assertCountEquals(0)
        compose.onNodeWithContentDescription("Add a bank").assertExists()
    }

    @Test
    fun onceTheSearchFieldHasScrolledAwayItWaitsInTheHeader_andTappingItBringsTheFieldBack() {
        open()
        scrollTo("Syndicate Bank")
        headerSearchIcon().assertCountEquals(1)
        // "+" stays beside it.
        compose.onNodeWithContentDescription("Add a bank").assertExists()

        headerSearchIcon().onFirst().performClick()
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            compose.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
        headerSearchIcon().assertCountEquals(0)
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

    // ---- renaming and deleting a bank you added ----

    private val finance get() = env.container.finance

    private fun addBank(name: String): String = runBlocking { finance.bankKeyFor(name) }

    private fun storedBanks(): List<String> = runBlocking { finance.repository.customBanks() }.map { it.name }

    private fun until(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.waitForIdle()
        condition()
    }

    private fun openBankSheet(name: String) {
        compose.onNodeWithText(name).performClick()
        compose.waitForIdle()
        until { compose.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aBuiltInBanksSheetHasNoRenameOrDelete() {
        open()
        scrollTo("Syndicate Bank")
        compose.onNodeWithText("Syndicate Bank").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Rename").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun aBankYouAddedIsRenamedFromItsSheet() {
        addBank("My Credit Union")
        open()
        openBankSheet("My Credit Union")
        compose.onNodeWithText("Rename").performClick()
        until { compose.onAllNodesWithText("Rename bank").fetchSemanticsNodes().isNotEmpty() }
        // The page has a search field of its own: the sheet's is the one with the cursor.
        until { compose.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(hasSetTextAction() and isFocused()).performTextReplacement("Our Credit Union")
        compose.onNodeWithText("Rename").performClick()
        until { storedBanks() == listOf("Our Credit Union") }
        until { compose.onAllNodesWithText("Our Credit Union").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("My Credit Union").assertDoesNotExist()
    }

    @Test
    fun aRenameToAnotherBanksNameSaysWhoseItIsAndChangesNothing() {
        addBank("My Credit Union")
        open()
        openBankSheet("My Credit Union")
        compose.onNodeWithText("Rename").performClick()
        until { compose.onAllNodesWithText("Rename bank").fetchSemanticsNodes().isNotEmpty() }
        // The page has a search field of its own: the sheet's is the one with the cursor.
        until { compose.onAllNodes(hasSetTextAction() and isFocused()).fetchSemanticsNodes().isNotEmpty() }

        compose.onNode(hasSetTextAction() and isFocused()).performTextReplacement("HDFC Bank")
        compose.waitForIdle()
        compose.onNodeWithText("HDFC Bank is already in your list.").assertExists()
        compose.onNodeWithText("Rename").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Rename bank").assertExists()
        assertEquals(listOf("My Credit Union"), storedBanks())
    }

    @Test
    fun aBankNothingUsesIsDeletedAfterAsking() {
        addBank("My Credit Union")
        open()
        openBankSheet("My Credit Union")
        compose.onNodeWithText("Delete").assertIsEnabled().performClick()
        until { compose.onAllNodesWithText("Delete My Credit Union?").fetchSemanticsNodes().isNotEmpty() }
        // Nothing has gone yet: the question comes first.
        assertEquals(listOf("My Credit Union"), storedBanks())

        compose.onNodeWithText("Delete").performClick()
        until { storedBanks().isEmpty() }
    }

    @Test
    fun cancellingTheQuestionKeepsTheBank() {
        addBank("My Credit Union")
        open()
        openBankSheet("My Credit Union")
        compose.onNodeWithText("Delete").performClick()
        until { compose.onAllNodesWithText("Delete My Credit Union?").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        assertEquals(listOf("My Credit Union"), storedBanks())
    }

    @Test
    fun aBankAnAccountUsesCannotBeDeletedAndTheSheetSaysWhy() {
        val key = addBank("My Credit Union")
        runBlocking {
            finance.saveAccount(Account(name = "Co-op", kind = AccountKind.Savings, bankKey = key, createdAt = LocalDateTime.now(), updatedAt = LocalDateTime.now()))
        }
        open()
        openBankSheet("My Credit Union")
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        compose.onNodeWithText("To delete it, first change the accounts and cards that use it to another bank.").assertExists()
        // Renaming is still fine: the account follows the new name.
        compose.onNodeWithText("Rename").assertIsEnabled()
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
