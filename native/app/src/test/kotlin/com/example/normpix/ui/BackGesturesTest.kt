package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.DeckStack
import com.example.normpix.ui.components.SwipeActionButton
import com.example.normpix.ui.components.SwipeActionWidth
import com.example.normpix.ui.components.SwipeRevealHost
import com.example.normpix.ui.components.SwipeRevealRow
import com.example.normpix.ui.components.rememberDeckStackState
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Back is one idea everywhere: it undoes the last thing the person opened, innermost first, before it leaves the page. A swiped
 * row that is showing its actions is shut, and a stack that was spread out is gathered again, by the same Back (the key or the
 * predictive gesture) that would otherwise have left the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class BackGesturesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun backShutsASwipedRowBeforeLeavingThePage() {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                MaterialTheme {
                    SwipeRevealHost {
                        Column {
                            SwipeRevealRow(
                                actionsWidth = SwipeActionWidth,
                                key = "a",
                                actions = {
                                    SwipeActionButton(
                                        Icons.Outlined.DeleteOutline,
                                        "Delete a",
                                        MaterialTheme.colorScheme.errorContainer,
                                        MaterialTheme.colorScheme.onErrorContainer,
                                        onClick = {},
                                    )
                                },
                            ) {
                                Text("Row a", Modifier.testTag("row-a").fillMaxWidth().height(64.dp).clickable { })
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("row-a").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Delete a").assertIsDisplayed()

        back()
        compose.waitForIdle()

        compose.onNodeWithText("Delete a").assertDoesNotExist()
        assertFalse(compose.activity.isFinishing, "the first Back only shut the row")
    }

    @Test
    fun backGathersASpreadOutStackAgain() {
        lateinit var unfolded: () -> Boolean
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                MaterialTheme {
                    val state = rememberDeckStackState()
                    unfolded = { state.unfolded }
                    LaunchedEffectOnce { state.unfold() }
                    DeckStack(
                        items = listOf("one", "two", "three"),
                        itemKey = { it },
                        shape = AppRadius.rLg,
                        description = { item, _, _ -> item },
                        onOpen = {},
                        state = state,
                        plate = { Box(Modifier.fillMaxWidth().height(80.dp).background(Color.Gray)) },
                        face = { item -> Box(Modifier.fillMaxWidth().height(80.dp).background(Color.LightGray)) { Text(item) } },
                    )
                }
            }
        }
        compose.waitForIdle()
        assertTrue(unfolded(), "spread out")

        back()
        compose.waitForIdle()

        assertFalse(unfolded(), "Back gathered it again")
        assertFalse(compose.activity.isFinishing, "and did not leave the page")
    }
}

@androidx.compose.runtime.Composable
private fun LaunchedEffectOnce(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}
