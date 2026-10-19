package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.example.normpix.navigation.NormPixRoot
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
 * A recurring payment can be added from the Recurring screen at any time, not only while it is empty: a row at the end of the
 * list, and the floating bar's More menu, which puts it first on this screen (and nowhere else).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RecurringAddTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment("recurring_add")

    @After
    fun tearDown() = env.close()

    /** A tab on the page itself (the floating bar's items carry a content description, these don't). */
    private fun tab(label: String): SemanticsNodeInteraction = compose.onNode(
        hasText(label) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
    )

    private fun openPlans(section: String) {
        compose.setContent { NormPixRoot(container = env.container) }
        compose.waitForIdle()
        compose.onNode(
            hasContentDescription("Plans & Activity") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
        compose.waitForIdle()
        tab("Plans").performClick()
        compose.waitForIdle()
        tab(section).performClick()
        compose.waitForIdle()
    }

    private fun aPayment() = runBlocking {
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

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun anEmptyListOffersItOnceInItsOwnEmptyState() {
        openPlans("Recurring")
        waitForText("No recurring payments")
        assertEquals(1, count("Add recurring payment"))
    }

    @Test
    fun aListWithPaymentsStillOffersToAddAnotherAtItsEnd() {
        aPayment()
        openPlans("Recurring")
        waitForText("Add recurring payment")
        // Not the empty state's: that is only for an empty list.
        assertEquals(0, count("No recurring payments"))
        assertEquals(1, count("Add recurring payment"))
        compose.onNodeWithText("Add recurring payment").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New recurring").assertIsDisplayed()
    }

    @Test
    fun theMoreMenuOnRecurringAddsARecurringPaymentFirst() {
        aPayment()
        openPlans("Recurring")
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add reminder").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add recurring payment").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New recurring").assertIsDisplayed()
    }

    @Test
    fun theMoreMenuOfAnEmptyRecurringScreenOffersItToo() {
        openPlans("Recurring")
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add recurring payment").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("New recurring").assertIsDisplayed()
    }

    @Test
    fun otherPlanSectionsKeepTheirOwnActions() {
        openPlans("Reminders")
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add reminder").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithContentDescription("Add recurring payment").fetchSemanticsNodes().isEmpty())
    }
}
