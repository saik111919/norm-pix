package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.ui.SwipeableTransactionRow
import com.example.normpix.settings.TransactionSwipeActions
import com.example.normpix.testing.TestEnvironment
import androidx.compose.ui.test.hasContentDescription
import com.example.normpix.ui.components.SwipeActionButton
import com.example.normpix.ui.components.SwipeActionWidth
import com.example.normpix.ui.components.SwipeRevealHost
import com.example.normpix.ui.components.SwipeRevealRow
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A swiped-open row meets its action tiles flush, with a square edge: a rounded corner there would leave a notch of page
 * between its curve and the tile's square corner. Told apart by what is drawn - and shut, the row keeps its rounded corner.
 * And a transaction row pulled sideways shows its action's colour in the strip it leaves bare.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h200dp-xxhdpi")
class SwipeRowShapeTest {
    @get:Rule
    val compose = createComposeRule()

    private val page = Color.White
    private val row = Color.Red
    private val tile = Color.Blue
    private val density = 3f // xxhdpi

    private fun show() {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                Box(Modifier.fillMaxSize().background(page).padding(16.dp)) {
                    SwipeRevealHost {
                        SwipeRevealRow(
                            actionsWidth = SwipeActionWidth,
                            shape = RoundedCornerShape(20.dp),
                            actions = { SwipeActionButton(Icons.Outlined.Edit, "Edit", tile, Color.White, onClick = {}) },
                        ) {
                            Box(Modifier.testTag("row").fillMaxWidth().height(64.dp).background(row))
                        }
                    }
                }
            }
        }
    }

    private fun pixels() = compose.onRoot().captureToImage().toPixelMap()

    @Test
    fun anOpenRowMeetsItsActionsFlushWithASquareEdge() {
        show()
        compose.onNodeWithTag("row").performTouchInput { swipeLeft(startX = right - 8f, endX = left + 8f) }
        compose.waitForIdle()

        val bounds = compose.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val pixels = pixels()
        fun at(x: Float, y: Float) = pixels[(x * density).toInt(), (y * density).toInt()]
        val middle = (bounds.top.value + bounds.bottom.value) / 2

        assertEquals(row, at(bounds.right.value - 12f, middle), "the row itself")
        assertEquals(tile, at(bounds.right.value + 4f, middle), "the action tile starts right at the row's edge: no gap")
        // The corners where the row meets the tile are square: no page showing in a notch between a curve and the tile.
        assertEquals(row, at(bounds.right.value - 1f, bounds.top.value + 1f), "the row's top corner at the junction")
        assertEquals(row, at(bounds.right.value - 1f, bounds.bottom.value - 1f), "the row's bottom corner at the junction")
        assertEquals(tile, at(bounds.right.value + 1f, bounds.top.value + 1f), "the tile's top corner at the junction")
    }

    @Test
    fun aShutRowKeepsItsOwnRoundedCorner() {
        show()
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("row").getUnclippedBoundsInRoot()
        val pixels = pixels()
        fun at(x: Float, y: Float) = pixels[(x * density).toInt(), (y * density).toInt()]
        assertEquals(page, at(bounds.right.value - 1f, bounds.top.value + 1f), "shut, the corner is the segment's rounded one")
        assertEquals(row, at(bounds.right.value - 20f, (bounds.top.value + bounds.bottom.value) / 2), "and the row is there")
    }

    /**
     * A transaction row held part of the way across, and the colour (as ARGB) behind it 4 dp in from the edge it was pulled away
     * from: the strip the row has left bare. [farPull] pulls it far enough that letting go would do the action.
     */
    private fun backdrop(toTheLeft: Boolean, farPull: Boolean): Pair<Int, ColorScheme> {
        val env = TestEnvironment("swipe_row_backdrop")
        val at = java.time.LocalDateTime.of(2026, 10, 5, 9, 0)
        val transaction = FinanceTransaction(
            id = 7, type = TransactionType.Expense, amount = 12_500, accountId = 1, payee = "Corner Cafe",
            occurredAt = at, createdAt = at, updatedAt = at,
        )
        var scheme: ColorScheme? = null
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                env.Content {
                    scheme = MaterialTheme.colorScheme
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        SwipeableTransactionRow(
                            transaction = transaction,
                            shape = RoundedCornerShape(20.dp),
                            swipe = TransactionSwipeActions.EditAndDelete,
                            onOpen = {}, onEdit = {}, onDelete = {},
                        )
                    }
                }
            }
        }
        val rowNode = compose.onNode(hasContentDescription("Corner Cafe", substring = true))
        val bounds = rowNode.getUnclippedBoundsInRoot()
        val pull = (if (farPull) 600f else 120f) * (if (toTheLeft) -1f else 1f)
        rowNode.performTouchInput {
            down(center)
            moveBy(Offset(pull / 2, 0f))
            moveBy(Offset(pull / 2, 0f))
        }
        compose.waitForIdle()
        val x = if (toTheLeft) bounds.right.value - 4f else bounds.left.value + 4f
        val seen = pixels()[(x * density).toInt(), (((bounds.top.value + bounds.bottom.value) / 2) * density).toInt()].toArgb()
        val colours = scheme!!
        rowNode.performTouchInput { cancel() }
        env.close()
        return seen to colours
    }

    @Test
    fun aRowPulledToTheRightShowsDeleteInRedBehindIt() {
        val (seen, scheme) = backdrop(toTheLeft = false, farPull = false)
        assertEquals(scheme.errorContainer.toArgb(), seen, "the strip it leaves bare is Delete's colour")
    }

    @Test
    fun aRowPulledToTheLeftShowsEditBehindIt() {
        val (seen, scheme) = backdrop(toTheLeft = true, farPull = false)
        assertEquals(scheme.secondaryContainer.toArgb(), seen, "the strip it leaves bare is Edit's colour")
    }

    @Test
    fun theColourFirmsUpOnceLettingGoWouldDoIt() {
        val (seen, scheme) = backdrop(toTheLeft = false, farPull = true)
        assertEquals(scheme.error.toArgb(), seen, "far enough, Delete's colour is the strong one")
    }
}
