package com.example.normpix.finance.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
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

/**
 * "Ends" in the repeating payment's own editor, on a payment saved earlier: how a stored end reads back (as a date, with how
 * many payments that is), and that changing it saves what the rule needs - the day of the last payment, or none.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RecurringEndsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val repository get() = env.container.finance.repository
    private var done: Boolean? = null

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("recurring_ends")
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun choice(label: String) = compose.onNode(hasText(label) and radio)

    /** A monthly payment from [start], in the future so nothing is recorded by saving it, ending as [end] says. */
    private fun savedRule(start: LocalDateTime, end: LocalDateTime?): RecurringRule = runBlocking {
        val now = LocalDateTime.now()
        val account = env.container.finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val category = env.container.finance.state.value.categories.first { it.name == "Bills & utilities" }
        env.container.finance.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense,
                amount = 64_900,
                accountId = account.id!!,
                categoryId = category.id,
                frequency = RecurrenceFrequency.Monthly,
                startDate = start,
                endDate = end,
                nextDate = start,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun edit(rule: RecurringRule) {
        compose.setContent { env.Content { RecurringEditorSheet(existingId = rule.id, onDone = { done = it }) } }
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("Edit recurring").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun saveChanges() {
        // Save is pinned below the form's scrolling body, so there is nothing to scroll it into.
        compose.onNodeWithText("Save changes").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { done != null }
    }

    private val start = LocalDateTime.of(2030, 1, 5, 0, 0)

    @Test
    fun `a payment with no end opens with Ends at Never, and has no switch for it`() {
        edit(savedRule(start, end = null))
        choice("Never").performScrollTo().assertIsSelected()
        assertTrue(compose.onAllNodesWithText("Has an end date").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("last on", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a payment with an end opens on a date, with the day and how many payments that is`() {
        edit(savedRule(start, end = LocalDateTime.of(2030, 12, 5, 0, 0)))
        choice("On a date").performScrollTo().assertIsSelected()
        compose.onNodeWithContentDescription("Last payment by, 5 Dec 2030").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("12 payments · last on 5 Dec 2030").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a payment can be changed to end after a number of payments, and saves the day of the last`() {
        val rule = savedRule(start, end = null)
        edit(rule)
        choice("After").performScrollTo().performClick()
        compose.waitForIdle()
        repeat(9) { compose.onNodeWithContentDescription("Fewer").performScrollTo().performClick() }
        compose.waitForIdle()
        compose.onNodeWithText("After 3 payments · last on 5 Mar 2030").performScrollTo().assertIsDisplayed()
        saveChanges()
        assertEquals(LocalDateTime.of(2030, 3, 5, 0, 0), runBlocking { repository.recurringRules() }.single().endDate)
    }

    @Test
    fun `an end can be taken off`() {
        val rule = savedRule(start, end = LocalDateTime.of(2030, 12, 5, 0, 0))
        edit(rule)
        choice("Never").performScrollTo().performClick()
        compose.waitForIdle()
        saveChanges()
        assertNull(runBlocking { repository.recurringRules() }.single().endDate)
    }

    @Test
    fun `saving a payment whose end was not touched keeps its day`() {
        edit(savedRule(start, end = LocalDateTime.of(2030, 12, 5, 0, 0)))
        saveChanges()
        assertEquals(LocalDateTime.of(2030, 12, 5, 0, 0), runBlocking { repository.recurringRules() }.single().endDate)
    }

    @Test
    fun `a last day stored as midnight on a payment made at six thirty still includes the last payment`() {
        // Payments from the transaction editor take their time of day from the transaction; an end stored as midnight left the
        // payment on the last day out. Saving again says it the way the rule needs.
        val evening = LocalDateTime.of(2030, 1, 5, 18, 30)
        edit(savedRule(evening, end = LocalDateTime.of(2030, 3, 5, 0, 0)))
        compose.onNodeWithText("3 payments · last on 5 Mar 2030").performScrollTo().assertIsDisplayed()
        saveChanges()
        assertEquals(LocalDateTime.of(2030, 3, 5, 18, 30), runBlocking { repository.recurringRules() }.single().endDate)
    }
}
