package com.example.normpix.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InteractiveClockWidgetTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `clock displays initial hour and minute in semantics description`() {
        compose.setContent {
            InteractiveClockWidget(
                modifier = Modifier.size(200.dp),
                initialHour = 12,
                initialMinute = 45,
                isInteractive = true,
                tokens = ClockWidgetTokens.Dark,
            )
        }

        compose.onNodeWithContentDescription("Clock widget showing 12:45")
            .assertIsDisplayed()
    }

    @Test
    fun `clock formats 24 hour mode with leading zero`() {
        compose.setContent {
            InteractiveClockWidget(
                modifier = Modifier.size(200.dp),
                initialHour = 9,
                initialMinute = 5,
                is24Hour = true,
                isInteractive = false,
                tokens = ClockWidgetTokens.Light,
            )
        }

        compose.onNodeWithContentDescription("Clock widget showing 09:05")
            .assertIsDisplayed()
    }

    @Test
    fun `clicking center area increments the hour and triggers onTimeChanged`() {
        var callbackHour = -1
        var callbackMinute = -1

        compose.setContent {
            InteractiveClockWidget(
                modifier = Modifier.size(200.dp),
                initialHour = 10,
                initialMinute = 30,
                isInteractive = true,
                onTimeChanged = { h, m ->
                    callbackHour = h
                    callbackMinute = m
                },
            )
        }

        // Tap center of widget (where hour numeral is located)
        compose.onNodeWithContentDescription("Clock widget showing 10:30")
            .performClick()

        // In 12-hour mode, 10 + 1 = 11
        compose.onNodeWithContentDescription("Clock widget showing 11:30")
            .assertIsDisplayed()
        assertEquals(11, callbackHour)
        assertEquals(30, callbackMinute)
    }

    @Test
    fun `controlled time parameters update the clock state`() {
        compose.setContent {
            InteractiveClockWidget(
                modifier = Modifier.size(200.dp),
                hour = 3,
                minute = 15,
                isInteractive = false,
                tokens = ClockWidgetTokens.Dark,
            )
        }

        compose.onNodeWithContentDescription("Clock widget showing 3:15")
            .assertIsDisplayed()
    }
}
