package com.example.normpix.widgets

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ClockWidgetTokensTest {

    @Test
    fun `dark tokens match the required color palette`() {
        val tokens = ClockWidgetTokens.Dark
        assertEquals(Color(0xFF08090D), tokens.background)
        assertEquals(Color(0xFFF5F5FA), tokens.primaryText)
        assertEquals(Color(0xFFB9B9F2), tokens.secondaryText)
        assertEquals(Color(0xFF484B5D), tokens.tickMarks)
        assertEquals(Color(0xFFFF8A24), tokens.accent)
    }

    @Test
    fun `light tokens match the required color palette`() {
        val tokens = ClockWidgetTokens.Light
        assertEquals(Color(0xFFF7F8FC), tokens.background)
        assertEquals(Color(0xFF171923), tokens.primaryText)
        assertEquals(Color(0xFF646B7A), tokens.secondaryText)
        assertEquals(Color(0xFFD5D9E3), tokens.tickMarks)
        assertEquals(Color(0xFFFF8A24), tokens.accent)
    }

    @Test
    fun `pill border active matches accent color`() {
        assertEquals(Color(0xFFFF8A24), ClockWidgetTokens.Dark.pillBorderActive)
        assertEquals(Color(0xFFFF8A24), ClockWidgetTokens.Light.pillBorderActive)
    }
}
