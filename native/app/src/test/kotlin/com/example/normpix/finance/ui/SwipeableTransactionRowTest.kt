package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.settings.TransactionSwipeActions
import com.example.normpix.testing.TestEnvironment
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

/**
 * A transaction row is swiped as Gmail's are: to the left for Edit (the editor opens and the row comes back), to the right for
 * Delete (the row goes and folds away, with no question - the Undo is the way back). A short pull does nothing.
 */
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
                Column {
                    SwipeableTransactionRow(
                        transaction = transaction,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("slot"),
                        swipe = swipe,
                        onOpen = { log += "open" },
                        onEdit = { log += "edit" },
                        onDelete = { log += "delete (asks first)" },
                        onSwipeDelete = { log += "delete" },
                    )
                }
            }
        }
    }

    // The tile reads as one phrase for screen readers ("Expense, 125.00, Corner Cafe, ..."), so find it by that.
    private fun row() = compose.onNode(hasContentDescription("Corner Cafe", substring = true))

    private fun left() = row().getUnclippedBoundsInRoot().left.value

    // The wrapper the row folds away inside: the tile in it keeps its size, the slot is what the list sees.
    private fun slot() = compose.onNodeWithTag("slot")

    @Test
    fun aSwipeToTheLeftEditsAndTheRowComesBack() {
        show(TransactionSwipeActions.EditAndDelete)
        val rest = left()

        row().performTouchInput { swipeLeft() }
        compose.waitForIdle()

        assertEquals(listOf("edit"), log)
        assertEquals(rest, left(), "the row springs back to its place")
    }

    @Test
    fun aSwipeToTheRightDeletesAtOnceAndTheRowFoldsAway() {
        show(TransactionSwipeActions.EditAndDelete)
        val height = slot().getUnclippedBoundsInRoot().height.value
        assertTrue(height > 0f)

        row().performTouchInput { swipeRight() }
        compose.waitForIdle()

        // The swipe's own delete, not the one that asks: the Undo that follows is the way back.
        assertEquals(listOf("delete"), log)
        assertEquals(0f, slot().getUnclippedBoundsInRoot().height.value, "the row folds its height away")
    }

    @Test
    fun aRowThatIsStillThereAfterBeingDeletedComesBack() {
        show(TransactionSwipeActions.EditAndDelete)
        val height = slot().getUnclippedBoundsInRoot().height.value
        val rest = left()

        row().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(0f, slot().getUnclippedBoundsInRoot().height.value)

        // Nothing took it out of the list (a delete that failed): it is not left as an empty strip.
        compose.mainClock.advanceTimeBy(4_000)
        compose.waitForIdle()
        assertEquals(height, slot().getUnclippedBoundsInRoot().height.value, "the row is back at its full height")
        assertEquals(rest, left(), "and in its place")
    }

    @Test
    fun aShortPullDoesNothingAndTheRowSpringsBack() {
        show(TransactionSwipeActions.EditAndDelete)
        val rest = left()

        row().performTouchInput {
            down(center)
            moveBy(Offset(24f, 0f))
            moveBy(Offset(24f, 0f))
            up()
        }
        compose.waitForIdle()

        assertTrue(log.isEmpty(), "a short pull is not a choice: $log")
        assertEquals(rest, left())
    }

    @Test
    fun onlyTheChosenSideMoves() {
        show(TransactionSwipeActions.DeleteOnly)
        val rest = left()

        row().performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(log.isEmpty(), "there is no Edit to the left")
        assertEquals(rest, left())

        row().performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf("delete"), log)
    }

    @Test
    fun withSwipingOffTheRowIsJustTheRowButAssistiveTechStillGetsBothActions() {
        show(null)
        val rest = left()

        // Ending well outside the row: with no drag to take it, a swipe that stops inside a clickable still counts as its tap.
        row().performTouchInput { swipeLeft(startX = right - 8f, endX = -width / 2f) }
        compose.waitForIdle()
        row().performTouchInput { swipeRight(startX = left + 8f, endX = width * 1.5f) }
        compose.waitForIdle()
        assertTrue(log.isEmpty(), "nothing happened, but the log says $log")
        assertEquals(rest, left())
        compose.onNodeWithText("Edit").assertDoesNotExist()

        // TalkBack users never needed the gesture: Edit and Delete are custom actions on the row, and Delete asks.
        val node = row().fetchSemanticsNode()
        val labels = node.config[SemanticsActions.CustomActions].map { it.label }
        assertEquals(listOf("Edit", "Delete"), labels)
        node.config[SemanticsActions.CustomActions].first { it.label == "Delete" }.action()
        assertEquals(listOf("delete (asks first)"), log)
    }
}
