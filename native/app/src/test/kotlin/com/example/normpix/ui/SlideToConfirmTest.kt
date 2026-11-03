package com.example.normpix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.SlideToConfirm
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * Slide-to-confirm tests: dragging past 85% springs to the end and confirms; short drags spring back;
 * accessibility semantics onClick confirms directly; disabled prevents drag/confirm; busy turning false
 * resets the knob so it can be confirmed again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SlideToConfirmTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("slide_to_confirm_test")
    }

    @After
    fun tearDown() = env.close()

    @Test
    fun draggingPast85PercentConfirmsOnce() {
        var confirms = 0
        compose.setContent {
            env.Content {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    SlideToConfirm(
                        label = "Slide to confirm",
                        onConfirm = { confirms++ },
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide to confirm").performTouchInput {
            swipeRight()
        }
        compose.waitForIdle()

        assertEquals(1, confirms)
    }

    @Test
    fun shortDragSpringsBackWithoutConfirming() {
        var confirms = 0
        compose.setContent {
            env.Content {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    SlideToConfirm(
                        label = "Slide to confirm",
                        onConfirm = { confirms++ },
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide to confirm").performTouchInput {
            swipe(start = centerLeft, end = center)
        }
        compose.waitForIdle()

        assertEquals(0, confirms)
    }

    @Test
    fun semanticsClickActionConfirms() {
        var confirms = 0
        compose.setContent {
            env.Content {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    SlideToConfirm(
                        label = "Slide to confirm",
                        onConfirm = { confirms++ },
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide to confirm").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        assertEquals(1, confirms)
    }

    @Test
    fun disabledControlDoesNotConfirm() {
        var confirms = 0
        compose.setContent {
            env.Content {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    SlideToConfirm(
                        label = "Slide to confirm",
                        onConfirm = { confirms++ },
                        enabled = false,
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide to confirm").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Slide to confirm").performTouchInput {
            swipeRight()
        }
        compose.waitForIdle()

        assertEquals(0, confirms)
    }

    @Test
    fun busyTurningFalseResetsKnobSoAnotherDragCanConfirm() {
        var confirms = 0
        var busy by mutableStateOf(false)
        compose.setContent {
            env.Content {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    SlideToConfirm(
                        label = "Slide to confirm",
                        onConfirm = { confirms++ },
                        busy = busy,
                    )
                }
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide to confirm").performTouchInput {
            swipeRight()
        }
        compose.waitForIdle()
        assertEquals(1, confirms)

        busy = true
        compose.waitForIdle()

        busy = false
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Slide to confirm").performTouchInput {
            swipeRight()
        }
        compose.waitForIdle()
        assertEquals(2, confirms)
    }
}
