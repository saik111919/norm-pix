package com.example.normpix.widgets

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Centralized, configurable design tokens for the clock widget.
 *
 * Dark theme:
 * - Background: #08090D
 * - Primary text: #F5F5FA (central hour numeral, selected minute in pill)
 * - Secondary text: #B9B9F2 (5-minute scale numerals, idle pill border)
 * - Tick marks: #484B5D
 * - Accent: #FF8A24 (active indicator, drag highlight, tick accent)
 *
 * Light theme:
 * - Background: #F7F8FC
 * - Primary text: #171923
 * - Secondary text: #646B7A
 * - Tick marks: #D5D9E3
 * - Accent: #FF8A24
 */
@Immutable
data class ClockWidgetTokens(
    val background: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tickMarks: Color,
    val accent: Color,
    val pillBorder: Color = secondaryText.copy(alpha = 0.55f),
    val pillBorderActive: Color = accent,
    val pillBackground: Color = background.copy(alpha = 0.85f),
) {
    companion object {
        val Dark = ClockWidgetTokens(
            background = Color(0xFF08090D),
            primaryText = Color(0xFFF5F5FA),
            secondaryText = Color(0xFFB9B9F2),
            tickMarks = Color(0xFF484B5D),
            accent = Color(0xFFFF8A24),
            pillBorder = Color(0xFFB9B9F2).copy(alpha = 0.60f),
            pillBorderActive = Color(0xFFFF8A24),
            pillBackground = Color(0xFF08090D).copy(alpha = 0.85f),
        )

        val Light = ClockWidgetTokens(
            background = Color(0xFFF7F8FC),
            primaryText = Color(0xFF171923),
            secondaryText = Color(0xFF646B7A),
            tickMarks = Color(0xFFD5D9E3),
            accent = Color(0xFFFF8A24),
            pillBorder = Color(0xFF646B7A).copy(alpha = 0.50f),
            pillBorderActive = Color(0xFFFF8A24),
            pillBackground = Color(0xFFF7F8FC).copy(alpha = 0.85f),
        )
    }
}

/**
 * Automatically adapts the clock widget tokens to the current theme mode (light, dark, or system default).
 */
@Composable
fun rememberClockWidgetTokens(
    darkTheme: Boolean = isSystemInDarkTheme(),
    customTokens: ClockWidgetTokens? = null,
): ClockWidgetTokens {
    if (customTokens != null) return customTokens
    return if (darkTheme) ClockWidgetTokens.Dark else ClockWidgetTokens.Light
}
