package com.example.normpix.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.example.normpix.finance.data.TransactionQuery
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An expense can be swiped wherever it is listed - the history, Home's Recent, search results - the way Gmail's rows are: to the
 * left it opens the editor, to the right it is deleted (with an Undo), and every list obeys the one swipe setting.
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
    private fun rowMatcher() = hasContentDescription("Corner Cafe", substring = true)

    private fun row() = compose.onNode(rowMatcher())

    private fun rowShown() = compose.onAllNodes(rowMatcher()).fetchSemanticsNodes().isNotEmpty()

    private fun waitForRow() {
        try {
            compose.waitUntil(timeoutMillis = 30_000) {
                // Home reads in several steps; idle on every check so each one's result reaches the screen before looking.
                compose.waitForIdle()
                rowShown()
            }
        } catch (e: ComposeTimeoutException) {
            // Say what the screen was showing, so a load that never finished can be told from a row that isn't there.
            throw AssertionError("No Corner Cafe row after 30 s. Showing: ${showing()}", e)
        }
    }

    private fun showing(): String = listOf("Loading your month", "Couldn't open your finances", "Recent").joinToString { text ->
        val matcher = hasText(text, substring = true) or hasContentDescription(text, substring = true)
        "\"$text\" ×${compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).size}"
    }

    // Both swipes end well outside the row: a drag that stops inside a clickable still counts as its tap, and the tap opens the
    // details sheet (which has an Edit button of its own).
    private fun swipeToEdit() {
        row().performTouchInput { swipeLeft(startX = right - 8f, endX = -width / 2f) }
        compose.waitForIdle()
    }

    private fun swipeToDelete() {
        row().performTouchInput { swipeRight(startX = left + 8f, endX = width * 1.5f) }
        compose.waitForIdle()
    }

    private fun stored(): List<FinanceTransaction> = runBlocking { env.container.finance.repository.transactions(TransactionQuery(), limit = 10).items }

    // The editor's own button; nothing else on Home, in search or in the details sheet says it.
    private fun editorShown() = compose.onAllNodes(hasText("Save changes")).fetchSemanticsNodes().isNotEmpty()

    private fun waitForEditor() = compose.waitUntil(timeoutMillis = 10_000) { compose.waitForIdle(); editorShown() }

    private fun waitForRowToGo() = compose.waitUntil(timeoutMillis = 10_000) { compose.waitForIdle(); !rowShown() }

    @Test
    fun homesRecentRowsSwipeLeftToEdit() {
        showHome()
        swipeToEdit()
        waitForEditor()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
    }

    @Test
    fun searchResultsSwipeLeftToEdit() {
        showSearch()
        swipeToEdit()
        waitForEditor()
        compose.onNodeWithText("Save changes").assertIsDisplayed()
    }

    @Test
    fun homesRecentRowsSwipeRightToDelete() {
        showHome()
        swipeToDelete()
        waitForRowToGo()
        assertEquals(emptyList<FinanceTransaction>(), stored())
    }

    @Test
    fun searchResultsSwipeRightToDelete() {
        showSearch()
        swipeToDelete()
        waitForRowToGo()
        assertEquals(emptyList<FinanceTransaction>(), stored())
    }

    @Test
    fun aShortSwipeDoesNothing() {
        showHome()
        row().performTouchInput { swipeRight(startX = left + 8f, endX = left + 40f) }
        compose.waitForIdle()
        assertEquals(1, stored().size)
        assert(rowShown())
    }

    @Test
    fun theChosenActionsApplyToHomeAndSearchAlike() {
        env.container.preferences.setTransactionSwipeActions(TransactionSwipeActions.DeleteOnly)
        showHome()
        // Edit is not chosen, so that side does not move and nothing opens.
        swipeToEdit()
        assert(!editorShown())
        assertEquals(1, stored().size)
        swipeToDelete()
        waitForRowToGo()
        assertEquals(emptyList<FinanceTransaction>(), stored())
    }

    @Test
    fun withEditOnlyTheDeleteSideDoesNotMove() {
        env.container.preferences.setTransactionSwipeActions(TransactionSwipeActions.EditOnly)
        showHome()
        swipeToDelete()
        assertEquals(1, stored().size)
        assert(rowShown())
    }

    @Test
    fun withSwipingOffNeitherListDoesAnything() {
        env.container.preferences.setSwipeActionsEnabled(false)
        showHome()
        swipeToEdit()
        swipeToDelete()
        assert(!editorShown())
        assertEquals(1, stored().size)
    }

    @Test
    fun withSwipingOffSearchDoesNothingEither() {
        env.container.preferences.setSwipeActionsEnabled(false)
        showSearch()
        swipeToEdit()
        swipeToDelete()
        assert(!editorShown())
        assertEquals(1, stored().size)
    }
}
