package com.example.normpix.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.navigation.AppSearchPage
import com.example.normpix.screens.HomeScreen
import com.example.normpix.settings.TransactionSwipeActions
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An expense can be swiped to Edit or Delete wherever it is listed - the history, Home's Recent, search results - and
 * every list obeys the one swipe setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SwipeOnEveryTransactionListTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val now = LocalDateTime.now()

    @Before
    fun setUp() {
        env = TestEnvironment("swipe_every_list")
        runBlocking {
            val finance = env.container.finance
            val account = finance.saveAccount(Account(name = "Main", kind = AccountKind.Bank, createdAt = now, updatedAt = now))
            val food = finance.saveCategory(Category(name = "Food", type = CategoryType.Expense, icon = "category", createdAt = now, updatedAt = now))
            finance.saveTransaction(
                FinanceTransaction(
                    type = TransactionType.Expense,
                    amount = 12_500,
                    accountId = account.id!!,
                    categoryId = food.id,
                    payee = "Corner Cafe",
                    occurredAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    @After
    fun tearDown() = env.close()

    private fun showHome() {
        compose.setContent { CompositionLocalProvider(LocalReducedMotionOverride provides true) { env.Content { HomeScreen() } } }
        waitForRow()
    }

    private fun showSearch() {
        compose.setContent { CompositionLocalProvider(LocalReducedMotionOverride provides true) { env.Content { AppSearchPage() } } }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("Corner")
        waitForRow()
    }

    // The tile reads as one phrase for screen readers ("Expense, ₹125.00, Corner Cafe, ..."), so find it by that.
    private fun row() = compose.onNode(hasContentDescription("Corner Cafe", substring = true))

    private fun waitForRow() {
        try {
            compose.waitUntil(timeoutMillis = 30_000) {
                // Home reads in several steps on the database's thread, and each comes back through the main looper, which
                // only runs here when the test lets it. Without idling on every check, a step that lands after the last
                // idle is never delivered and Home stays on "Loading your month" (it did, now and then, on CI).
                compose.waitForIdle()
                compose.onAllNodes(hasContentDescription("Corner Cafe", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: ComposeTimeoutException) {
            // Say what the screen was showing and where the database threads were, so a slow load can be told from a stuck one.
            throw AssertionError("No Corner Cafe row after 30 s. Showing: ${showing()}\n${threads()}", e)
        }
    }

    private fun showing(): String = listOf("Loading your month", "Couldn't open your finances", "Recent").joinToString { text ->
        val matcher = hasText(text, substring = true) or hasContentDescription(text, substring = true)
        "\"$text\" ×${compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size}"
    }

    private fun threads(): String = Thread.getAllStackTraces()
        .filterKeys { it.name.startsWith("test-") }
        .entries.joinToString("\n") { (thread, frames) ->
            "--- ${thread.name} (${thread.state})\n" + frames.take(15).joinToString("\n") { "    at $it" }
        }

    // Ends well outside the row: a drag that stops inside a clickable still counts as its tap, and the tap opens the
    // details sheet (which has an Edit button of its own).
    private fun swipeRow() {
        row().performTouchInput { swipeLeft(startX = right - 8f, endX = -width / 2f) }
        compose.waitForIdle()
    }

    @Test
    fun homesRecentRowsSwipeToEditAndDelete() {
        showHome()
        swipeRow()
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun searchResultsSwipeToEditAndDelete() {
        showSearch()
        swipeRow()
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun theChosenActionsApplyToHomeAndSearchAlike() {
        env.container.preferences.setTransactionSwipeActions(TransactionSwipeActions.DeleteOnly)
        showHome()
        swipeRow()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test
    fun withSwipingOffNeitherListRevealsAnything() {
        env.container.preferences.setSwipeActionsEnabled(false)
        showHome()
        swipeRow()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun withSwipingOffSearchRevealsNothingEither() {
        env.container.preferences.setSwipeActionsEnabled(false)
        showSearch()
        swipeRow()
        compose.onNodeWithText("Edit").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }
}
