package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.focusRing
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A control that draws its own press has no ripple to show where keyboard (or switch) focus is, and WCAG 2.4.7 asks that focus
 * be visible: [focusRing] draws a ring along its edge while it has focus, and nothing otherwise.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w200dp-h200dp-xxhdpi")
class FocusRingTest {
    @get:Rule
    val compose = createComposeRule()

    private var primary = Color.Unspecified

    private fun show() {
        compose.setContent {
            val source = remember { MutableInteractionSource() }
            primary = MaterialTheme.colorScheme.primary
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Box(Modifier.testTag("box").size(100.dp).background(Color.White).focusRing(source, RectangleShape).focusable(interactionSource = source))
            }
        }
        compose.waitForIdle()
    }

    /** The colour 1.5dp in from the left edge, half way down: inside a 3dp ring, and clear of the box's inside. */
    private fun edge(): Color {
        val pixels = compose.onNodeWithTag("box").captureToImage().toPixelMap()
        return pixels[(pixels.width * 1.5f / 100f).toInt(), pixels.height / 2]
    }

    @Test
    fun withoutFocusThereIsNoRing() {
        show()
        assertEquals(Color.White, edge())
    }

    @Test
    fun withFocusTheEdgeIsRingedInThePrimaryColour() {
        show()
        compose.onNodeWithTag("box").requestFocus()
        compose.waitForIdle()
        assertNotEquals(Color.White, edge(), "the ring is drawn")
        assertEquals(primary, edge(), "in the theme's primary colour, solid")
    }
}
