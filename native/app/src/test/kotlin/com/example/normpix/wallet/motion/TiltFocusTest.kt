package com.example.normpix.wallet.motion

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TiltFocusTest {
    @Test
    fun `empty coordinator has no focused key and defaults isFocused to true`() {
        val coordinator = TiltFocusState()
        assertNull(coordinator.focusedKey)
        assertTrue(coordinator.isFocused("any-key"))
    }

    @Test
    fun `single candidate is always focused`() {
        val coordinator = TiltFocusState()
        coordinator.reportPosition("card-1", centerY = 300f, rootHeight = 800f)
        assertEquals("card-1", coordinator.focusedKey)
        assertTrue(coordinator.isFocused("card-1"))
        assertFalse(coordinator.isFocused("card-2"))
    }

    @Test
    fun `candidate closest to vertical center of viewport is focused`() {
        val coordinator = TiltFocusState()
        // Screen height = 1000f, center = 500f
        // card-1 is at 200f (distance 300f)
        // card-2 is at 480f (distance 20f)
        // card-3 is at 750f (distance 250f)
        coordinator.reportPosition("card-1", centerY = 200f, rootHeight = 1000f)
        coordinator.reportPosition("card-2", centerY = 480f, rootHeight = 1000f)
        coordinator.reportPosition("card-3", centerY = 750f, rootHeight = 1000f)

        assertEquals("card-2", coordinator.focusedKey)
        assertFalse(coordinator.isFocused("card-1"))
        assertTrue(coordinator.isFocused("card-2"))
        assertFalse(coordinator.isFocused("card-3"))
    }

    @Test
    fun `scrolling moves candidate positions and shifts focus to the newly centered card`() {
        val coordinator = TiltFocusState()
        // Screen center = 500f
        coordinator.reportPosition("card-1", centerY = 450f, rootHeight = 1000f)
        coordinator.reportPosition("card-2", centerY = 750f, rootHeight = 1000f)
        assertEquals("card-1", coordinator.focusedKey)

        // User scrolls down by 300f:
        // card-1 moves to 150f (distance 350f)
        // card-2 moves to 450f (distance 50f)
        coordinator.reportPosition("card-1", centerY = 150f, rootHeight = 1000f)
        coordinator.reportPosition("card-2", centerY = 450f, rootHeight = 1000f)

        assertEquals("card-2", coordinator.focusedKey)
        assertTrue(coordinator.isFocused("card-2"))
        assertFalse(coordinator.isFocused("card-1"))
    }

    @Test
    fun `removing candidate updates focus to the next closest candidate`() {
        val coordinator = TiltFocusState()
        coordinator.reportPosition("card-1", centerY = 480f, rootHeight = 1000f)
        coordinator.reportPosition("card-2", centerY = 550f, rootHeight = 1000f)
        assertEquals("card-1", coordinator.focusedKey)

        coordinator.removeCandidate("card-1")
        assertEquals("card-2", coordinator.focusedKey)
        assertTrue(coordinator.isFocused("card-2"))
    }
}
