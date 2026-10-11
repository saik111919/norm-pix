package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.settings.TransactionSwipeActions
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.SwipeRevealHost
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SwipeableTransactionRowTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private val log = mutableListOf<String>()

    private val transaction = FinanceTransaction(
        id = 7,
        type = TransactionType.Expense,
        amount = 12_500,
        accountId = 1,
        payee = "Corner Cafe",
        occurredAt = LocalDateTime.of(2026, 10, 5, 9, 0),
        createdAt = LocalDateTime.of(2026, 10, 5, 9, 0),
        updatedAt = LocalDateTime.of(2026, 10, 5, 9, 0),
    )

    @Before
    fun setUp() {
        env = TestEnvironment("swipeable_transaction_row")
    }

    @After
    fun tearDown() = env.close()

    private fun show(swipe: TransactionSwipeActions?) = compose.setContent {
        CompositionLocalProvider(LocalReducedMotionOverride provides true) {
            env.Content {
                SwipeRevealHost {
                    Column {
                        SwipeableTransactionRow(
                            transaction = transaction,
                            shape = RoundedCornerShape(12.dp),
                            swipe = swipe,
                            onOpen = { log += "open" },
                            onEdit = { log += "edit" },
                            onDelete = { log += "delete" },
                        )
                    }
                }
            }
        }
    }

    // The tile reads as one phrase for screen readers ("Expense, 125.00, Corner Cafe, ..."), so find it by that.
    private fun row() = compose.onNode(hasContentDescription("Corner Cafe", substring = true))

    @Test
    fun swipingRevealsEditAndDeleteAndTheyRunTheExistingHandlers() {
        show(TransactionSwipeActions.EditAndDelete)

        row().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Edit").assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        assertTrue(log.isEmpty())

        compose.onNodeWithText("Edit").performClick()
        compose.waitForIdle()
        assertEquals(listOf("edit"), log)

        row().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("edit", "delete"), log)
    }

    @Test
    fun onlyTheChosenActionIsOffered() {
        show(TransactionSwipeActions.DeleteOnly)

        row().performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Edit").assertDoesNotExist()
    }

    @Test
    fun withSwipingOffTheRowIsJustTheRowButAssistiveTechStillGetsBothActions() {
        show(null)

        row().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Edit").assertDoesNotExist()

        // TalkBack users never needed the gesture: Edit and Delete are custom actions on the row.
        val node = row().fetchSemanticsNode()
        val labels = node.config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Edit", "Delete"), labels)
    }
}
