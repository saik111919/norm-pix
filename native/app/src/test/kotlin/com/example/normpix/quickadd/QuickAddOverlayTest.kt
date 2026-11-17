package com.example.normpix.quickadd

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.CATEGORY_PICKER_TAG
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.settings.AppLockScope
import com.example.normpix.testing.OverlayHarness
import com.example.normpix.testing.RecordingAuthenticator
import com.example.normpix.testing.TestEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the quick add overlay's screen does, as the activity composes it ([QuickAddHost]): the sheet of the type a shortcut asked for,
 * one request after another, ending when the sheet and the snackbar a save said are over, and held behind the app's lock. The
 * activity's own part (the window, the services it borrows, the intents) is in [QuickAddActivityTest].
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class QuickAddOverlayTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "quick_add_overlay_test")
    private val overlay = OverlayHarness(compose, env)

    @After
    fun tearDown() = env.close()

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(text: String) = compose.waitUntil(timeoutMillis = 10_000) { shown(text) }

    private fun waitGone(text: String) = compose.waitUntil(timeoutMillis = 10_000) { !shown(text) }

    private fun stored(): List<FinanceTransaction> =
        runBlocking { env.container.finance.repository.transactions(TransactionQuery(), limit = 10).items }

    private fun dismissSheet() =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)

    // ------------------------------------------------------------------ a shortcut's request

    @Test
    fun `each kind of add opens the sheet of its own type`() {
        for ((type, title) in listOf(
            TransactionType.Income to "New income",
            TransactionType.Expense to "New expense",
            TransactionType.Transfer to "New transfer",
        )) {
            overlay.add(type)
            waitFor(title)
            // Not filled in, and nothing said about a share: it is the form as the app opens it.
            assertFalse(shown("Filled in from shared text"))
            dismissSheet()
            waitGone(title)
            compose.waitUntil(timeoutMillis = 3_000) { overlay.finished }
        }
        assertEquals(3, overlay.finishes)
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `saving records the transaction, and the overlay stays for the snackbar, then goes`() {
        overlay.add(TransactionType.Expense)
        waitFor("New expense")
        compose.onNodeWithContentDescription("Amount in INR").performTextInput("120")
        compose.onNodeWithText("Category").performScrollTo().performClick()
        compose.onNodeWithTag(CATEGORY_PICKER_TAG).performScrollToNode(hasText("Food & dining"))
        compose.onNodeWithText("Food & dining").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Save expense").performClick()
        waitGone("New expense")

        assertEquals(12_000L, stored().single().amount)
        // The usual snackbar says it was saved - the overlay is still there for it, and ends once it has gone.
        waitFor("Expense of ₹120.00 saved")
        assertFalse(overlay.finished, "the snackbar is still up")
        compose.waitUntil(timeoutMillis = 15_000) { overlay.finished }
        assertEquals(1, overlay.finishes)
    }

    @Test
    fun `dismissing the sheet ends the overlay at once`() {
        overlay.add(TransactionType.Expense)
        waitFor("New expense")
        dismissSheet()
        waitGone("New expense")
        // Nothing was said, so there is no snackbar to wait for.
        compose.waitUntil(timeoutMillis = 3_000) { overlay.finished }
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `a second request while one is open waits behind it, and the draft is not lost`() {
        overlay.add(TransactionType.Expense)
        waitFor("New expense")
        compose.onNodeWithContentDescription("Amount in INR").performTextInput("75")

        overlay.add(TransactionType.Income)
        compose.waitForIdle()
        assertTrue(shown("New expense"), "the first draft is still there")
        assertFalse(shown("New income"))
        assertEquals(listOf<QuickAddRequest>(QuickAddRequest.Add(TransactionType.Income)), overlay.requests.waiting.value)
        assertFalse(overlay.finished)

        // Done with the first, the second opens in its place.
        dismissSheet()
        waitFor("Discard this entry?")
        compose.onNodeWithText("Discard").performClick()
        waitFor("New income")
        assertTrue(overlay.requests.waiting.value.isEmpty())
        dismissSheet()
        waitGone("New income")
        compose.waitUntil(timeoutMillis = 3_000) { overlay.finished }
    }

    @Test
    fun `asking again for what is open is not queued twice`() {
        overlay.add(TransactionType.Expense)
        waitFor("New expense")
        overlay.add(TransactionType.Expense)
        overlay.add(TransactionType.Expense)
        assertTrue(overlay.requests.waiting.value.isEmpty())

        overlay.add(TransactionType.Income)
        overlay.add(TransactionType.Income)
        assertEquals(1, overlay.requests.waiting.value.size)
    }

    // -------------------------------------------------------------------------- sample data

    @Test
    fun `with sample data on, adding asks to turn it off first`() {
        env.preferences.setDummyDataEnabled(true)
        overlay.add(TransactionType.Expense)
        waitFor("Sample data is on")
        assertFalse(shown("New expense"))
        compose.onNodeWithText("Turn off sample data").performClick()
        waitFor("New expense")
        assertFalse(env.preferences.state.value.dummyDataEnabled)
    }

    @Test
    fun `leaving the sample data on drops the request and ends the overlay`() {
        env.preferences.setDummyDataEnabled(true)
        overlay.add(TransactionType.Expense)
        waitFor("Sample data is on")
        compose.onNodeWithText("Cancel").performClick()
        waitGone("Sample data is on")
        assertTrue(env.preferences.state.value.dummyDataEnabled)
        assertFalse(shown("New expense"))
        compose.waitUntil(timeoutMillis = 3_000) { overlay.finished }
    }

    // ------------------------------------------------------------------------------- the lock

    private fun lockTheApp(authenticator: RecordingAuthenticator) {
        env.preferences.setWalletLockEnabled(true)
        env.preferences.setAppLockScope(AppLockScope.EntireApp)
        env.container.lock.authenticator = authenticator
    }

    @Test
    fun `a locked app asks for the prompt first and shows nothing when it is refused`() {
        val prompt = RecordingAuthenticator(succeed = false)
        lockTheApp(prompt)
        overlay.add(TransactionType.Expense)
        compose.waitUntil(timeoutMillis = 10_000) { overlay.finished }

        assertEquals(1, prompt.asked)
        assertFalse(shown("New expense"))
        assertTrue(env.container.lock.isAppLocked(env.preferences.state.value))
        // Nothing was drawn at all: no sheet, and no ledger (the ledger of the app is behind the lock).
        assertTrue(compose.onAllNodesWithText("Save expense").fetchSemanticsNodes().isEmpty())
        assertTrue(stored().isEmpty())
    }

    @Test
    fun `a locked app opens the sheet once the prompt is passed`() {
        val prompt = RecordingAuthenticator(succeed = true)
        lockTheApp(prompt)
        overlay.add(TransactionType.Income)
        waitFor("New income")

        assertEquals(1, prompt.asked)
        assertFalse(env.container.lock.isAppLocked(env.preferences.state.value))
        assertFalse(overlay.finished)
    }

    @Test
    fun `the lock engaging while the overlay is open closes it`() {
        val prompt = RecordingAuthenticator(succeed = true)
        lockTheApp(prompt)
        overlay.add(TransactionType.Expense)
        waitFor("New expense")

        // The user went away and came back after the timeout: the sheet goes, and so does the overlay; nothing is asked again.
        compose.runOnUiThread { env.container.lock.lockAll() }
        waitGone("New expense")
        compose.waitUntil(timeoutMillis = 10_000) { overlay.finished }
        assertEquals(1, prompt.asked)
    }

    @Test
    fun `an app locked for the wallet only is not locked for the overlay`() {
        val prompt = RecordingAuthenticator(succeed = false)
        env.preferences.setWalletLockEnabled(true)
        env.preferences.setAppLockScope(AppLockScope.WalletOnly)
        env.container.lock.authenticator = prompt
        overlay.add(TransactionType.Expense)
        waitFor("New expense")

        // Nothing of the wallet is in the overlay, so nothing asks.
        assertEquals(0, prompt.asked)
    }

    // ---------------------------------------------------------------------------- the ledger

    @Test
    fun `the ledger is loaded once, when the first screen to need it asks`() {
        val context = env.context
        val database = FinanceDatabase(context, name = null)
        try {
            val controller = FinanceController(FinanceRepository(database, dispatcher = Dispatchers.Unconfined))
            assertEquals(FinanceStatus.Loading, controller.state.value.status)
            runBlocking { controller.ensureLoaded() }
            assertEquals(FinanceStatus.Ready, controller.state.value.status)
            val revision = controller.state.value.revision
            // Already loaded (by the main screen, say): asking again reads nothing.
            runBlocking { controller.ensureLoaded() }
            assertEquals(revision, controller.state.value.revision)
        } finally {
            database.close()
        }
    }
}
