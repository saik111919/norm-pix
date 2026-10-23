package com.example.normpix.finance.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.navigation.NormPixRoot
import com.example.normpix.testing.TestEnvironment
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Nothing read from a bill or a receipt makes a payment repeat, or sets where a repeat stops: a draft from shared text starts
 * as a one-off, "Does not repeat", with no "Ends" to answer, and saves as a plain expense. Repeating is the user's choice.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class RepeatNotPrefilledTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "repeat_not_prefilled")

    private val sms = "Sent Rs.250.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 10/10/26\nRef 428312345678\n" +
        "Not You?\nCall 18002586161/SMS BLOCK UPI to 7308080808"

    @After
    fun tearDown() = env.close()

    private fun shared(text: String) {
        compose.setContent { NormPixRoot(container = env.container) }
        compose.waitForIdle()
        compose.runOnUiThread { env.container.sharedText.offer(text) }
        compose.waitForIdle()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Filled in from shared text").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun aSharedBillStartsAsAOneOffWithNoEnds() {
        shared(sms)
        compose.onNodeWithText("Does not repeat").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Ends").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText("last on", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun savingASharedBillRecordsAPlainExpense_notARepeatingPayment() {
        shared(sms)
        // Save is pinned below the form's scrolling body, so there is nothing to scroll it into.
        compose.onNodeWithText("Save expense").performClick()
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithText("New expense").fetchSemanticsNodes().isEmpty() }
        val repository = env.container.finance.repository
        val saved = runBlocking { repository.transactions(TransactionQuery(), limit = 10).items }.single()
        assertNull(saved.recurringId)
        assertTrue(runBlocking { repository.recurringRules() }.isEmpty())
    }
}
