package com.example.normpix.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.nthOccurrence
import com.example.normpix.finance.domain.recurrenceStep
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.dayLabel
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AppPreferences
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.StoredCard
import java.time.LocalDate
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

/**
 * The recurring payment editor follows the transaction editor: a bottom sheet with the hero
 * amount, type selector, category dropdown and a pinned Save, closed by its handle / scrim / back
 * (asking first when something changed) rather than a pushed page with a close button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RecurringEditorTest {

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
        val prefs = context.getSharedPreferences("recurring_editor_test", Context.MODE_PRIVATE)
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
            finance = FinanceController(FinanceRepository(financeDb)),
            reminders = RemindersController(ReminderDao(remindersDb), scheduler = NoOpReminderScheduler),
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

    /** A tab on the page itself (the floating bar's items carry a content description, these don't). */
    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(
        hasText(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
    )

    private fun openEditor() {
        compose.setContent { NormPixRoot(container = container) }
        compose.waitForIdle()
        compose.onNode(
            hasContentDescription("Plans & Activity") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
        compose.waitForIdle()
        tab("Plans").performClick()
        compose.waitForIdle()
        tab("Recurring").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Add recurring payment").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New recurring").assertIsDisplayed()
    }

    private fun dismissSheet() {
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        compose.waitForIdle()
    }

    private fun gone(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()

    @Test
    fun opensAsTheTransactionEditorsSheet() {
        openEditor()
        // The editor's own controls: hero amount, connected type selector, category dropdown, pinned Save.
        compose.onNodeWithContentDescription("Amount in INR").assertIsDisplayed()
        compose.onNode(hasText("Expense") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        compose.onNodeWithText("Category").assertIsDisplayed()
        compose.onNode(hasText("Monthly") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).performScrollTo().assertIsSelected()
        compose.onNodeWithText("Save recurring").assertIsDisplayed()
        // No pushed page: no close button in a top bar.
        assertTrue(compose.onAllNodesWithContentDescription("Discard").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun aCleanEditor_justCloses_backToRecurring() {
        openEditor()
        dismissSheet()
        compose.waitUntil(timeoutMillis = 5_000) { gone("New recurring") }
        tab("Recurring").assertIsSelected()
    }

    @Test
    fun anEditorWithChanges_asksFirst() {
        openEditor()
        compose.onNodeWithContentDescription("Amount in INR").performTextInput("499")
        compose.waitForIdle()
        dismissSheet()
        compose.onNodeWithText("Discard this entry?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New recurring").assertIsDisplayed()

        dismissSheet()
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { gone("New recurring") }
    }

    @Test
    fun savingWithoutAnAmount_saysWhatIsMissing_andStaysOpen() {
        openEditor()
        compose.onNodeWithText("Save recurring").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Enter an amount greater than zero").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("New recurring").assertIsDisplayed()
    }

    @Test
    fun aCompleteRule_isSaved_andTheSheetCloses() {
        openEditor()
        compose.onNodeWithContentDescription("Amount in INR").performTextInput("649")
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Food & dining").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save recurring").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { gone("New recurring") }
        val rules = runBlocking { container.finance.repository.recurringRules() }
        assertEquals(1, rules.size)
        assertEquals(64_900L, rules.single().amount)
    }

    // --- how it ends -----------------------------------------------------------------------------------------------

    private fun fillRule() {
        compose.onNodeWithContentDescription("Amount in INR").performTextInput("649")
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Food & dining").performClick()
        compose.waitForIdle()
    }

    private fun ends(label: String): SemanticsNodeInteraction = compose.onNode(
        hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
    )

    private fun saveRule() {
        // Save is pinned below the form's scrolling body, so there is nothing to scroll it into.
        compose.onNodeWithText("Save recurring").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { gone("New recurring") }
    }

    @Test
    fun aNewPaymentNeverEndsUnlessItIsToldTo_andHasNoEndDateSwitchAnyMore() {
        openEditor()
        ends("Never").performScrollTo().assertIsSelected()
        ends("On a date").assertIsNotSelected()
        ends("After").assertIsNotSelected()
        assertTrue(gone("Has an end date"))
        fillRule()
        saveRule()
        assertNull(runBlocking { container.finance.repository.recurringRules() }.single().endDate)
    }

    @Test
    fun endingAfterANumberOfPayments_isSavedAsTheDateOfTheLastOne() {
        openEditor()
        fillRule()
        ends("After").performScrollTo().performClick()
        compose.waitForIdle()
        // Twelve to start with; nine fewer is three.
        compose.onNode(hasText("After 12 payments", substring = true)).performScrollTo().assertIsDisplayed()
        repeat(9) { compose.onNodeWithContentDescription("Fewer").performScrollTo().performClick() }
        compose.waitForIdle()
        compose.onNode(hasText("After 3 payments", substring = true)).performScrollTo().assertIsDisplayed()
        saveRule()
        val rule = runBlocking { container.finance.repository.recurringRules() }.single()
        val third = nthOccurrence(rule.startDate, 3, recurrenceStep(rule.startDate, RecurrenceFrequency.Monthly, 1))
        assertEquals(third, rule.endDate)
    }

    @Test
    fun endingOnADate_isPickedWithTheDateSheet_andSavedAsThatDay() {
        openEditor()
        fillRule()
        ends("On a date").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Last payment by", substring = true)).performScrollTo().performClick()
        compose.waitForIdle()
        val tomorrow = LocalDate.now().plusDays(1)
        // The date sheet, as everywhere: a quick pick, then the one button that uses it.
        compose.onNode(hasContentDescription("Tomorrow", substring = true)).performClick()
        compose.onNodeWithText("Use ${dayLabel(tomorrow)}").performClick()
        compose.waitForIdle()
        // A day before the second payment holds only the first.
        compose.onNodeWithText("Just the first payment").performScrollTo().assertIsDisplayed()
        saveRule()
        assertEquals(tomorrow, runBlocking { container.finance.repository.recurringRules() }.single().endDate?.toLocalDate())
    }
}
