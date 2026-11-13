package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.DeckStack
import com.example.normpix.ui.components.rememberDeckStackState
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pulling the front item of a stack shows what is under it (owner, 2026-11-13: "when swiping can't we show the back cards' details"):
 * the faces of the next items are composed only while a pull is on, and print in step with how far it has gone - the plate behind a
 * deck's front item is blank otherwise. Items: the front is green, the next one's face is red, a plate (what stands behind) is blue.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w300dp-h300dp-mdpi")
class DeckPeekTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val facesComposed = mutableSetOf<String>()

    private fun show(items: List<String> = listOf("a", "b", "c")) {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                MaterialTheme {
                    DeckStack(
                        items = items,
                        itemKey = { it },
                        shape = AppRadius.rLg,
                        description = { item, _, _ -> "Item $item" },
                        onOpen = {},
                        state = rememberDeckStackState(),
                        plate = { Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Blue)) },
                        face = { item ->
                            SideEffect { facesComposed += item }
                            Box(Modifier.fillMaxWidth().height(120.dp).background(if (item == "a") Color.Green else Color.Red))
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun pixelAt(x: Float, y: Float): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt().coerceAtMost(pixels.width - 1), (pixels.height * y).toInt().coerceAtMost(pixels.height - 1)]
    }

    @Test
    fun atRestOnlyTheFrontItemHasAFace() {
        show()
        assertEquals(setOf("a"), facesComposed, "the ones behind are plates: nothing of theirs is composed until a pull")
    }

    @Test
    fun pullingTheFrontItemPrintsTheNextOnesFace_andLettingGoTakesItAway() {
        show()
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.8f, 60f))
            moveBy(Offset(-width * 0.45f, 0f))
        }
        compose.waitForIdle()
        assertTrue("b" in facesComposed, "the next item's face is composed while the front one is pulled: $facesComposed")

        // Far enough that the front one has left the right side: what is there is the next item - red, not the blank blue plate.
        val shown = pixelAt(0.85f, 0.2f)
        assertTrue(shown.red > 0.5f && shown.blue < 0.4f, "the next item's face shows where the front one was: $shown")

        compose.onRoot().performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun aSingleItemHasNothingToPeekAt() {
        show(listOf("a"))
        compose.onRoot().performTouchInput {
            down(Offset(width * 0.8f, 60f))
            moveBy(Offset(-width * 0.45f, 0f))
        }
        compose.waitForIdle()
        assertEquals(setOf("a"), facesComposed)
        compose.onRoot().performTouchInput { up() }
    }
}
