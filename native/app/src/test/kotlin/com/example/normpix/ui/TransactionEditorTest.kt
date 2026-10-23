package com.example.normpix.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.nthOccurrence
import com.example.normpix.finance.domain.recurrenceStep
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.CATEGORY_PICKER_TAG
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AppPreferences
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.StoredCard
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The transaction editor as a bottom sheet: it saves, it asks before throwing work away, the type
 * selector reshapes the form, and the category is one field that opens a grouped, searchable picker.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TransactionEditorTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var financeDb: FinanceDatabase
    private lateinit var remindersDb: RemindersDatabase
    private lateinit var container: AppContainer

    @Before
    fun setUp() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        financeDb = FinanceDatabase(context, name = null)
        remindersDb = RemindersDatabase(context, name = null)
        val prefs = context.getSharedPreferences("transaction_editor_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val vault = object : CardVault {
            private var cards = emptyList<StoredCard>()
            override suspend fun loadCards(): List<StoredCard> = cards
            override suspend fun saveCards(cards: List<StoredCard>) {
                this.cards = cards
            }
        }
        container = AppContainer(
            preferences = AppPreferences(prefs),
            // The databases are read and written right where they are called, on the test's own thread. On the app's
            // database threads a read could come back there and carry the screen's next frame with it, which Compose
            // rejects ("multithreaded access to SnapshotStateObserver"): this test failed that way now and then on CI.
            finance = FinanceController(FinanceRepository(financeDb, dispatcher = Dispatchers.Unconfined)),
            reminders = RemindersController(ReminderDao(remindersDb, dispatcher = Dispatchers.Unconfined), scheduler = NoOpReminderScheduler),
            vault = vault,
        )
        container.finance.load()
        container.reminders.load()
    }

    @After
    fun tearDown() {
        financeDb.close()
        remindersDb.close()
    }

    private fun openEditor() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()
        compose.onNode(
            hasContentDescription("Plans & Activity") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
        compose.waitForIdle()
        // Contextual actions live behind the floating bar's More button.
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add expense").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New expense").assertIsDisplayed()
    }

    /** Swipe-down / scrim / back all reach the sheet as this one semantic action. */
    private fun dismissSheet() {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
    }

    private fun typeAmount(text: String) {
        compose.onNodeWithContentDescription("Amount in INR").performTextInput(text)
        compose.waitForIdle()
    }

    private fun chip(label: String): SemanticsNodeInteraction = compose.onNode(
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
    )

    @Test
    fun editor_savesAnExpense_andClosesTheSheet() {
        openEditor()
        typeAmount("191")
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Food & dining").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }

        // The expense is stored (19100 minor units = 191.00), and its row reads out its category.
        val stored = runBlocking { container.finance.repository.transactions(TransactionQuery(), limit = 5).items }
        assertEquals(1, stored.size)
        assertEquals(19_100L, stored.single().amount)
        assertEquals(TransactionType.Expense, stored.single().type)
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodes(hasContentDescription("Food & dining", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun category_opensAPickerGroupedByFamily_withSearchAndNewCategory() {
        openEditor()
        // No grid of chips and no long menu: the choices appear only once the picker is open.
        assertTrue(compose.onAllNodesWithText("Groceries").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Select a category").assertIsDisplayed()
        // A family is its category as a row; its ready-made sub-categories are chips under it, closed until the row's chevron opens them.
        compose.onNodeWithText("Food & dining").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Restaurants").fetchSemanticsNodes().isEmpty(), "closed: the chips are not shown")
        compose.onNodeWithContentDescription("Show sub-categories of Food & dining").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Restaurants").assertIsDisplayed()
        compose.onNodeWithText("Search categories").assertIsDisplayed()
        // The rest are a scroll down the list, ending with "New category".
        compose.onNodeWithTag(CATEGORY_PICKER_TAG).performScrollToNode(hasText("Groceries"))
        compose.onNodeWithText("Groceries").assertIsDisplayed()
        compose.onNodeWithTag(CATEGORY_PICKER_TAG).performScrollToNode(hasText("New category"))
        compose.onNodeWithText("New category").assertIsDisplayed()
    }

    @Test
    fun category_aSubCategoryChipPicksItAndTheFieldNamesWhereItSits() {
        openEditor()
        typeAmount("120")
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Show sub-categories of Food & dining").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pizza").performClick()
        compose.waitForIdle()
        // The picker closed, and the field reads "Food & dining › Pizza".
        assertTrue(compose.onAllNodesWithText("Select a category").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Food & dining › Pizza").assertIsDisplayed()

        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }
        val repo = container.finance.repository
        val saved = runBlocking { repo.transactions(TransactionQuery(), limit = 5).items.single() }
        val pizza = runBlocking { repo.categories().first { it.name == "Pizza" } }
        assertEquals(pizza.id, saved.categoryId)
    }

    @Test
    fun category_searchFindsASubCategoryByItsParentAndName() {
        openEditor()
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Search categories").performTextInput("food pizza")
        compose.waitForIdle()
        // Matches are named by where they sit, and the families are gone.
        compose.onNodeWithText("Food & dining › Pizza").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Restaurants").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Food & dining › Pizza").performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Select a category").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun category_searchWithNoMatchSaysSo() {
        openEditor()
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Search categories").performTextInput("zzzz")
        compose.waitForIdle()
        compose.onNodeWithText("No category matches “zzzz”.").assertIsDisplayed()
    }

    @Test
    fun category_frequentlyUsedIsOnScreenFirst_andOneTapPicksIt() {
        // History: groceries three times, transport once. The picker leads with what is used most.
        runBlocking {
            val state = container.finance.state.value
            val account = state.activeAccounts.first()
            fun spend(name: String) = FinanceTransaction(
                type = TransactionType.Expense,
                amount = 5_000,
                accountId = account.id!!,
                categoryId = state.categories.first { it.name == name && it.type == com.example.normpix.finance.domain.CategoryType.Expense }.id,
                occurredAt = java.time.LocalDateTime.now().minusDays(1),
                createdAt = java.time.LocalDateTime.now(),
                updatedAt = java.time.LocalDateTime.now(),
            )
            repeat(3) { container.finance.saveTransaction(spend("Groceries")) }
            container.finance.saveTransaction(spend("Transport"))
        }
        openEditor()
        compose.onNodeWithText("Category").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Frequently used").fetchSemanticsNodes().isNotEmpty() }
        // On screen, not just in the list: the section arrives after the sheet opens and must not scroll out of sight.
        compose.onNodeWithText("Frequently used").assertIsDisplayed()
        compose.onNodeWithText("All categories").assertIsDisplayed()

        // The first "Groceries" is the shortcut (the second is its own row further down).
        compose.onAllNodesWithText("Groceries").onFirst().performClick()
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("Select a category").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Groceries").assertIsDisplayed()
    }
    @Test
    fun typeSelector_reshapesTheForm() {
        openEditor()
        chip("Expense").assertIsSelected()
        compose.onNodeWithText("Paid from").assertIsDisplayed()

        chip("Income").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New income").assertIsDisplayed()
        chip("Income").assertIsSelected()

        chip("Transfer").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New transfer").assertIsDisplayed()
        // A transfer has no category; it names both ends instead.
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("Category").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("From").assertIsDisplayed()
        compose.onNodeWithText("To").assertIsDisplayed()
    }

    @Test
    fun dismissing_aCleanEditor_justCloses() {
        openEditor()
        dismissSheet()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun dismissing_anEditorWithChanges_asksFirst_andKeepEditingKeepsIt() {
        openEditor()
        typeAmount("42")

        dismissSheet()
        compose.onNodeWithText("Discard this entry?").assertIsDisplayed()

        compose.onNodeWithText("Keep editing").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New expense").assertIsDisplayed()

        dismissSheet()
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }
        assertEquals(0, compose.onAllNodesWithText("Discard this entry?").fetchSemanticsNodes().size)
    }

    @Test
    fun saving_withoutAnAmount_showsWhatIsMissing_andStaysOpen() {
        openEditor()
        compose.onNodeWithText("Save expense").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter an amount greater than zero").assertIsDisplayed()
        compose.onNodeWithText("Choose a category").assertIsDisplayed()
        compose.onNodeWithText("New expense").assertIsDisplayed()
    }

    // --- repeating from the editor -----------------------------------------------------------------------------

    private fun chooseFoodCategory() {
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Food & dining").performClick()
        compose.waitForIdle()
    }

    private fun openRepeatMenu() {
        compose.onNodeWithText("Repeat").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun repeat_isOfferedWhereTheExpenseIsEntered_andStartsAsOnce() {
        openEditor()
        compose.onNodeWithText("Repeat").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Does not repeat").assertExists()
        // Nothing about repeating is said until it is chosen.
        assertTrue(compose.onAllNodes(hasText("Next:", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun repeat_monthlySavesTheExpenseAsTheFirstOfARepeatingPayment() {
        openEditor()
        typeAmount("649")
        chooseFoodCategory()
        openRepeatMenu()
        compose.onNodeWithText("Every month").performClick()
        compose.waitForIdle()
        // The editor says what will happen before Save.
        compose.onNode(hasText("Next:", substring = true)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }

        val repo = container.finance.repository
        val rules = runBlocking { repo.recurringRules() }
        val saved = runBlocking { repo.transactions(TransactionQuery(), limit = 10).items }
        assertEquals(1, rules.size, "one repeating payment")
        assertEquals(1, saved.size, "and the expense just entered - not a second copy of it")
        assertEquals(rules.single().id, saved.single().recurringId, "linked to each other")
        assertEquals(64_900L, rules.single().amount)
        assertEquals(com.example.normpix.finance.domain.RecurrenceFrequency.Monthly, rules.single().frequency)
        assertEquals(saved.single().occurredAt.plusMonths(1), rules.single().nextDate)
    }

    @Test
    fun repeat_aCustomScheduleIsMadeInItsOwnSheet_andShownInTheField() {
        openEditor()
        typeAmount("300")
        chooseFoodCategory()
        openRepeatMenu()
        compose.onNodeWithText("Custom…").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Custom repeat").assertIsDisplayed()
        // Every month, then three times as far apart.
        compose.onNodeWithContentDescription("More apart").performClick()
        compose.onNodeWithContentDescription("More apart").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Every 3 months").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }
        val rule = runBlocking { container.finance.repository.recurringRules() }.single()
        assertEquals(3, rule.interval)
        assertEquals(com.example.normpix.finance.domain.RecurrenceFrequency.Monthly, rule.frequency)
    }

    @Test
    fun repeat_notChosenSavesAPlainExpenseWithNoRule() {
        openEditor()
        typeAmount("50")
        chooseFoodCategory()
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }
        assertTrue(runBlocking { container.finance.repository.recurringRules() }.isEmpty())
    }

    @Test
    fun repeat_changingItCountsAsAChange_soLeavingAsksFirst() {
        openEditor()
        openRepeatMenu()
        compose.onNodeWithText("Every week").performClick()
        compose.waitForIdle()
        dismissSheet()
        compose.onNodeWithText("Discard this entry?").assertIsDisplayed()
    }

    // --- how a repeat ends ---------------------------------------------------------------------------------------

    @Test
    fun repeat_hasNoEndsUntilItRepeats_thenEndsNeverUntilToldOtherwise() {
        openEditor()
        assertTrue(compose.onAllNodesWithText("Ends").fetchSemanticsNodes().isEmpty())
        openRepeatMenu()
        compose.onNodeWithText("Every month").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ends").performScrollTo().assertIsDisplayed()
        chip("Never").performScrollTo().assertIsSelected()
    }

    @Test
    fun repeat_endingAfterANumberOfPayments_isSavedAsTheDateOfTheLastOne() {
        openEditor()
        typeAmount("649")
        chooseFoodCategory()
        openRepeatMenu()
        compose.onNodeWithText("Every month").performClick()
        compose.waitForIdle()
        chip("After").performScrollTo().performClick()
        compose.waitForIdle()
        // Twelve to start with; eight fewer is four.
        repeat(8) { compose.onNodeWithContentDescription("Fewer").performScrollTo().performClick() }
        compose.waitForIdle()
        compose.onNode(hasText("After 4 payments", substring = true)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }
        val rule = runBlocking { container.finance.repository.recurringRules() }.single()
        val fourth = nthOccurrence(rule.startDate, 4, recurrenceStep(rule.startDate, com.example.normpix.finance.domain.RecurrenceFrequency.Monthly, 1))
        assertEquals(fourth, rule.endDate)
    }

    @Test
    fun repeat_aCustomRepeatEndsInItsOwnSheetToo() {
        openEditor()
        typeAmount("300")
        chooseFoodCategory()
        openRepeatMenu()
        compose.onNodeWithText("Custom…").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Custom repeat").assertIsDisplayed()
        chip("After").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Fewer").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("After 11 payments", substring = true)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()
        // What was chosen in the sheet is what the editor holds: still every month, ending after eleven.
        compose.onNodeWithText("Every month").performScrollTo().assertIsDisplayed()
        chip("After").performScrollTo().assertIsSelected()
        compose.onNode(hasText("After 11 payments", substring = true)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }
        val rule = runBlocking { container.finance.repository.recurringRules() }.single()
        val eleventh = nthOccurrence(rule.startDate, 11, recurrenceStep(rule.startDate, com.example.normpix.finance.domain.RecurrenceFrequency.Monthly, 1))
        assertEquals(eleventh, rule.endDate)
    }
}
