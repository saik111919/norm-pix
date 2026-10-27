package com.example.normpix.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.MainActivity
import com.example.normpix.finance.domain.PaymentFrequency
import com.example.normpix.finance.domain.RecurringPayment
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.reminders.ReminderState
import com.example.normpix.testing.TestDatabaseDriver
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Adding recurring payments: on Recurring it can be added when the list is empty (its own empty state), when it already has
 * payments (a tile at the end of the list), and from the More menu across all screens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecurringAddTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = AppContainer(
            context = ApplicationProvider.getApplicationContext(),
            driver = TestDatabaseDriver.inMemory(),
            remindersFlow = MutableStateFlow(ReminderState.Empty),
        )
    }

    private fun openPlans(section: String) {
        compose.onNodeWithContentDescription("Plans & Transactions").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Plans").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(section).performClick()
        compose.waitForIdle()
    }

    private fun aPayment() = runBlocking {
        container.recurringPayments.save(
            RecurringPayment(
                id = 0,
                name = "Electricity",
                amountInCents = 4_500,
                frequency = PaymentFrequency.Monthly,
                startDate = LocalDate.now(),
                nextDueDate = LocalDate.now().plusWeeks(1),
                type = TransactionType.Expense,
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
    fun allPlanSectionsOfferAllActionsInMoreMenu() {
        openPlans("Reminders")
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Add reminder").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add recurring payment").assertIsDisplayed()
    }
}
