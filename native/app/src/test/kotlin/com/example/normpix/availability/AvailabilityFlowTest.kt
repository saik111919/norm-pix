package com.example.normpix.availability

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.wallet.nfc.NfcCardOutletService
import com.example.normpix.wallet.nfc.NfcExchangeSheet
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Availability rules decide whether the NFC sheet lets you share or
 * receive cards, or explains why not and when the window opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AvailabilityFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val anchorMonday = LocalDate.of(2025, 6, 2)
    private lateinit var env: TestEnvironment

    private fun monday(hour: Int, minute: Int = 0) =
        anchorMonday.atTime(hour, minute).atZone(zone).toInstant()

    private fun weekday(day: DayOfWeek, hour: Int, minute: Int = 0) =
        anchorMonday.with(TemporalAdjusters.nextOrSame(day)).atTime(hour, minute).atZone(zone).toInstant()

    private class HandClock(private val instant: java.time.Instant, private val zone: ZoneId = ZoneId.systemDefault()) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = HandClock(instant, zone)
        override fun instant(): java.time.Instant = instant
    }

    @BeforeTest
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment()
        NfcCardOutletService.clearOutlet()
    }

    @AfterTest
    fun tearDown() {
        env.close()
        NfcCardOutletService.clearOutlet()
    }

    private fun showSheet(clock: Clock, onDismiss: () -> Unit = {}, onOpenSchedule: (() -> Unit)? = null) = compose.setContent {
        WithAvailabilityClock(clock) {
            env.Content { NfcExchangeSheet(onDismiss = onDismiss, onOpenSchedule = onOpenSchedule) }
        }
    }

    @Test
    fun switchedOffExplainsItselfAndStartsNothing() {
        env.preferences.setAvailability(ScheduledFeature.NfcExchange, AvailabilityRule(AvailabilityMode.Off))
        showSheet(HandClock(monday(19)), onOpenSchedule = {})

        compose.onNodeWithText("NFC is unavailable right now.").assertIsDisplayed()
        compose.onNodeWithText("Turned off in Settings.").assertIsDisplayed()
        compose.onNodeWithText("Open schedule settings").assertIsDisplayed()
        compose.onNodeWithText("Receive").assertDoesNotExist()
        compose.onNodeWithText("Share").assertDoesNotExist()
        assertNull(NfcCardOutletService.activePayload, "nothing may be armed for sharing")
    }

    @Test
    fun outsideTheWindowSaysWhenItOpensToday() {
        env.preferences.setAvailability(
            ScheduledFeature.NfcExchange,
            AvailabilityRule(
                mode = AvailabilityMode.Scheduled,
                schedule = WeeklySchedule(setOf(DayOfWeek.MONDAY), LocalTime.of(10, 0), LocalTime.of(18, 0)),
            ),
        )
        showSheet(HandClock(monday(8, 30)), onOpenSchedule = {})

        compose.onNodeWithText("NFC is unavailable right now.").assertIsDisplayed()
        compose.onNodeWithText("Available today from 10:00 AM to 6:00 PM.").assertIsDisplayed()
        compose.onNodeWithText("Receive").assertDoesNotExist()
    }

    @Test
    fun outsideTheWindowSaysWhenItOpensOnLaterDay() {
        env.preferences.setAvailability(
            ScheduledFeature.NfcExchange,
            AvailabilityRule(
                mode = AvailabilityMode.Scheduled,
                schedule = WeeklySchedule(setOf(DayOfWeek.FRIDAY), LocalTime.of(9, 0), LocalTime.of(17, 0)),
            ),
        )
        // Today is Monday.
        showSheet(HandClock(monday(12, 0)), onOpenSchedule = {})

        compose.onNodeWithText("NFC is unavailable right now.").assertIsDisplayed()
        compose.onNodeWithText("Available Friday from 9:00 AM to 5:00 PM.").assertIsDisplayed()
    }

    @Test
    fun windowCrossingMidnightReportsCorrectOpening() {
        // Night window: 22:00 to 04:00 every night.
        val nightWindow = AvailabilityRule(
            mode = AvailabilityMode.Scheduled,
            schedule = WeeklySchedule(DayOfWeek.entries.toSet(), LocalTime.of(22, 0), LocalTime.of(4, 0)),
        )
        env.preferences.setAvailability(ScheduledFeature.NfcExchange, nightWindow)

        // At 15:00 on Monday, the window opens tonight at 22:00.
        showSheet(HandClock(monday(15, 0)), onOpenSchedule = {})
        compose.onNodeWithText("Available today from 10:00 PM to 4:00 AM.").assertIsDisplayed()
    }
}
