package com.example.normpix.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.holdFocus
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertEquals
import kotlinx.coroutines.launch
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A card answers a finger in two steps and casts no shadow: it sinks while pressed, and once held for a long press it rises with
 * a ring in the primary colour along its edge, which goes when the finger does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h600dp-xhdpi")
@OptIn(ExperimentalFoundationApi::class)
class HoldFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private var primary = Color.Unspecified
    private var held = 0
    private val face = Color(0xFFEEEEEE)

    private fun show(longPress: Boolean = true) {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) {
                MaterialTheme {
                    primary = MaterialTheme.colorScheme.primary
                    val source = remember { MutableInteractionSource() }
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        Box(
                            Modifier
                                .size(160.dp, 100.dp)
                                .testTag("card")
                                .holdFocus(source, AppRadius.rLg, longPress = longPress)
                                .background(face, AppRadius.rLg)
                                .combinedClickable(interactionSource = source, indication = null, onLongClick = { held++ }, onClick = {}),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** A pixel just inside the left edge, half way down: where the ring is, once there is one. */
    private fun edge(): Color {
        val image = compose.onNodeWithTag("card").captureToImage().toPixelMap()
        return image[3, image.height / 2]
    }

    /** A pixel in the middle of the card: its own face. */
    private fun middle(): Color {
        val image = compose.onNodeWithTag("card").captureToImage().toPixelMap()
        return image[image.width / 2, image.height / 2]
    }

    @Test
    fun heldForALongPressItWearsTheRing_andLosesItWhenLetGo() {
        show()
        assertNotEquals(primary, edge(), "no ring at rest")
        compose.onNodeWithTag("card").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        assertNotEquals(primary, edge(), "pressed, but not held for long: it sinks, no ring yet")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(primary, edge(), "held: the ring in the primary colour along the edge")
        assertEquals(face, middle(), "and the card's face is as it was")
        compose.onNodeWithTag("card").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertNotEquals(primary, edge(), "the ring goes with the finger")
        assertTrue(held >= 1, "the long press itself still reached the card's own action")
    }

    @Test
    fun aHoldThatOpensASheetKeepsTheRingUntilTheSheetCloses() {
        val overlays = com.example.normpix.ui.components.AppOverlays()
        compose.setContent {
            CompositionLocalProvider(
                LocalReducedMotionOverride provides true,
                com.example.normpix.ui.components.LocalOverlaysOrNull provides overlays,
            ) {
                MaterialTheme {
                    primary = MaterialTheme.colorScheme.primary
                    val source = remember { MutableInteractionSource() }
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        Box(
                            Modifier
                                .size(160.dp, 100.dp)
                                .testTag("card")
                                .holdFocus(source, AppRadius.rLg)
                                .background(face, AppRadius.rLg)
                                // What a held card does: open a sheet about it.
                                .combinedClickable(
                                    interactionSource = source,
                                    indication = null,
                                    onLongClick = { scope.launch { overlays.sheet<Unit> { } } },
                                    onClick = {},
                                ),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("card").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue(overlays.isOpen, "the hold opened a sheet")
        assertEquals(primary, edge(), "ringed while held")
        compose.onNodeWithTag("card").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(primary, edge(), "the finger is up but the sheet is still about this card: the ring stays")
        compose.runOnIdle { overlays.dismissAll() }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertNotEquals(primary, edge(), "and goes once the sheet has closed")
    }

    @Test
    fun aCardWithNothingToDoOnAHoldNeverWearsTheRing() {
        show(longPress = false)
        compose.onNodeWithTag("card").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        assertNotEquals(primary, edge())
        compose.onNodeWithTag("card").performTouchInput { up() }
    }
}
