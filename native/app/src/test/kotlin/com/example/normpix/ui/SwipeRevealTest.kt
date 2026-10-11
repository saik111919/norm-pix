package com.example.normpix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.SwipeActionButton
import com.example.normpix.ui.components.SwipeActionWidth
import com.example.normpix.ui.components.SwipeRevealHost
import com.example.normpix.ui.components.SwipeRevealRow
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeRevealTest {
    @get:Rule
    val compose = createComposeRule()

    private val log = mutableListOf<String>()

    @Composable
    private fun Rows(enabledB: Boolean = true) {
        CompositionLocalProvider(LocalReducedMotionOverride provides true) {
            MaterialTheme {
                SwipeRevealHost {
                    Column {
                        TestRow("a", enabled = true)
                        TestRow("b", enabled = enabledB)
                        Text("Elsewhere", Modifier.testTag("elsewhere").fillMaxWidth().height(48.dp).clickable { log += "elsewhere" })
                    }
                }
            }
        }
    }

    @Composable
    private fun TestRow(id: String, enabled: Boolean) {
        SwipeRevealRow(
            actionsWidth = SwipeActionWidth * 2,
            key = id,
            enabled = enabled,
            actions = {
                SwipeActionButton(Icons.Outlined.Edit, "Edit $id", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, onClick = { log += "edit $id" })
                SwipeActionButton(Icons.Outlined.DeleteOutline, "Delete $id", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, onClick = { log += "delete $id" })
            },
        ) {
            Text("Row $id", Modifier.testTag("row-$id").fillMaxWidth().height(64.dp).clickable { log += "open $id" })
        }
    }

    @Test
    fun swipingLeftRevealsTheActionsAndNothingElseRuns() {
        compose.setContent { Rows() }
        compose.onNodeWithText("Edit a").assertDoesNotExist()

        compose.onNodeWithTag("row-a").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Edit a").assertIsDisplayed()
        compose.onNodeWithText("Delete a").assertIsDisplayed()
        assertTrue(log.isEmpty(), "a swipe is not a tap: $log")
    }

    @Test
    fun actionRunsItsHandlerAndClosesTheRow() {
        compose.setContent { Rows() }
        compose.onNodeWithTag("row-a").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Delete a").performClick()
        compose.waitForIdle()

        assertEquals(listOf("delete a"), log)
        compose.onNodeWithText("Delete a").assertDoesNotExist()
    }

    @Test
    fun onlyOneRowIsOpenAtATime() {
        compose.setContent { Rows() }
        compose.onNodeWithTag("row-a").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Edit a").assertIsDisplayed()

        compose.onNodeWithTag("row-b").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Edit b").assertIsDisplayed()
        compose.onNodeWithText("Edit a").assertDoesNotExist()
    }

    @Test
    fun tappingAnOpenRowClosesItInsteadOfActivatingIt() {
        compose.setContent { Rows() }
        compose.onNodeWithTag("row-a").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithTag("row-a").performTouchInput { click(centerLeft) }
        compose.waitForIdle()

        compose.onNodeWithText("Edit a").assertDoesNotExist()
        assertTrue(log.isEmpty(), "closing must not also open the row: $log")
    }

    @Test
    fun tappingElsewhereOnlyClosesTheOpenRow() {
        compose.setContent { Rows() }
        compose.onNodeWithTag("row-a").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithTag("elsewhere").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Edit a").assertDoesNotExist()
        assertTrue(log.isEmpty(), "the tap that closes a row is swallowed: $log")

        // With nothing open, the same tap works normally.
        compose.onNodeWithTag("elsewhere").performClick()
        assertEquals(listOf("elsewhere"), log)
    }

    @Test
    fun scrollingClosesTheOpenRow() {
        compose.setContent { Rows() }
        compose.onNodeWithTag("row-a").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithTag("elsewhere").performTouchInput { swipeDown() }
        compose.waitForIdle()

        compose.onNodeWithText("Edit a").assertDoesNotExist()
    }

    @Test
    fun aDisabledRowDoesNotSwipe() {
        compose.setContent { Rows(enabledB = false) }
        compose.onNodeWithTag("row-b").performTouchInput { swipeLeft() }
        compose.waitForIdle()

        compose.onNodeWithText("Edit b").assertDoesNotExist()
        // (A plain clickable still activates on a sideways release inside it - ordinary Compose behavior.)
        log.clear()
        compose.onNodeWithTag("row-b").performClick()
        assertEquals(listOf("open b"), log)
    }

    @Test
    fun aPlainTapStillActivatesAClosedRow() {
        compose.setContent { Rows() }
        compose.onNodeWithTag("row-a").performClick()
        assertEquals(listOf("open a"), log)
    }
}
