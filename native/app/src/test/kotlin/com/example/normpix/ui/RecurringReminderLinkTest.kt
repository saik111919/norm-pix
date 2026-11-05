package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.reminders.ReminderDrafts
import com.example.normpix.testing.TestEnvironment
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In Plans a repeating payment and its reminder are one thing: the row says when one is set, pausing the payment takes the reminder
 * with it, and deleting it (once the Undo is let go) takes it too, instead of leaving a reminder about "a repeating payment that
 * no longer exists". (The rules themselves are `RecurringRemindersTest`.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RecurringReminderLinkTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment("recurring_reminder_link")

    @After
    fun tearDown() = env.close()

    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(
        hasText(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
    )

    private fun openRecurring() {
        compose.setContent { NormPixRoot(container = env.container) }
        compose.waitForIdle()
        compose.onNode(
            hasContentDescription("Plans & Activity") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
        compose.waitForIdle()
        tab("Plans").performClick()
        compose.waitForIdle()
        tab("Recurring").performClick()
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodes(hasContentDescription("Netflix", substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun aPayment(): RecurringRule = runBlocking {
        val now = LocalDateTime.now()
        val account = env.container.finance.saveAccount(Account(name = "HDFC Savings", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
        val category = env.container.finance.state.value.categories.first { it.name == "Bills & utilities" }
        env.container.finance.saveRecurringRule(
            RecurringRule(
                type = TransactionType.Expense,
                amount = 64_900,
                accountId = account.id!!,
                categoryId = category.id,
                payee = "Netflix",
                frequency = RecurrenceFrequency.Monthly,
                startDate = LocalDateTime.of(2035, 1, 5, 0, 0),
                nextDate = LocalDateTime.of(2035, 1, 5, 0, 0),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun giveItsReminder(rule: RecurringRule) = runBlocking {
        env.container.reminders.ensureLoaded()
        env.container.reminders.addReminder(ReminderDrafts.aboutRecurring(rule, "Netflix", "Every month · ₹649.00", LocalDateTime.now()))
    }

    private fun reminders() = env.container.reminders.state.value.reminders

    private fun rowsWithAReminder() = compose.onAllNodes(hasContentDescription("with a reminder", substring = true)).fetchSemanticsNodes().size

    @Test
    fun theRowSaysWhenAReminderIsSet() {
        val withIt = aPayment()
        giveItsReminder(withIt)
        openRecurring()
        compose.waitUntil(timeoutMillis = 10_000) { rowsWithAReminder() == 1 }
        assertEquals(1, rowsWithAReminder())
    }

    @Test
    fun aRowWithNoReminderDoesNotSayItHasOne() {
        aPayment()
        openRecurring()
        assertEquals(0, rowsWithAReminder())
    }

    @Test
    fun pausingThePaymentTakesItsReminderWithIt() {
        val rule = aPayment()
        giveItsReminder(rule)
        assertEquals(1, reminders().size)
        openRecurring()

        compose.onNode(hasContentDescription("Netflix", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Active") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save changes").performClick()
        // First that the payment was saved paused (else the save never happened), then that its reminder went with it.
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            runBlocking { env.container.finance.repository.recurringRules() }.none { it.active }
        }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            reminders().isEmpty()
        }
        assertTrue(reminders().isEmpty(), "no payment is coming, so nothing reminds about it")
    }

    @Test
    fun deletingThePaymentKeepsItsReminderWhileTheUndoIsOnOffer() {
        val rule = aPayment()
        giveItsReminder(rule)
        openRecurring()

        compose.onNode(hasContentDescription("Netflix", substring = true)).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        // Taken back, the payment keeps its reminder: it goes only when the delete is final (`OfferUndoSettlesTest`).
        assertEquals(1, reminders().size, "while the Undo is on offer the reminder waits")
    }
}
