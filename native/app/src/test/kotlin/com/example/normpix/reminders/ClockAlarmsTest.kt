package com.example.normpix.reminders

import android.provider.AlarmClock
import java.time.LocalDateTime
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Add to Clock alarms": a reminder as an alarm in the phone's own Clock app, where a clock alarm - a time of day and some days of
 * the week - can say what the reminder does, and nowhere else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockAlarmsTest {
    private val now = LocalDateTime.of(2026, 11, 5, 21, 0)

    private fun reminder(
        due: LocalDateTime,
        repeat: RepeatRule = RepeatRule.Once,
        status: ReminderStatus = ReminderStatus.Active,
        snoozedUntil: LocalDateTime? = null,
    ) = Reminder(
        id = 1,
        title = "Pay rent",
        category = ReminderCategory.Payments,
        dueAt = due,
        repeatRule = repeat,
        status = status,
        snoozedUntil = snoozedUntil,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun aReminderDueTomorrowMorningIsAnAlarmWithItsTimeAndWords() {
        val intent = assertNotNull(ClockAlarms.intentFor(reminder(LocalDateTime.of(2026, 11, 6, 7, 30)), now))
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(7, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals("Pay rent", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true), "the person sees the alarm and saves it in the Clock app")
        assertNull(intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS), "one time: no days")
    }

    @Test
    fun aReminderBeyondADayIsNotOfferedAnAlarmThatWouldRingAtTheWrongTime() {
        assertNull(ClockAlarms.intentFor(reminder(LocalDateTime.of(2026, 11, 9, 9, 0)), now), "three days away: a clock alarm has no date")
        assertNull(ClockAlarms.intentFor(reminder(LocalDateTime.of(2026, 11, 5, 20, 0)), now), "already past")
    }

    @Test
    fun aSnoozedReminderIsAnAlarmForTheTimeItWasSnoozedTo() {
        val snoozed = reminder(
            LocalDateTime.of(2026, 11, 5, 9, 0),
            status = ReminderStatus.Snoozed,
            snoozedUntil = LocalDateTime.of(2026, 11, 5, 22, 15),
        )
        val intent = assertNotNull(ClockAlarms.intentFor(snoozed, now))
        assertEquals(22, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(15, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
    }

    @Test
    fun aFinishedReminderIsNotAnAlarm() {
        assertFalse(ClockAlarms.canBeAlarm(reminder(LocalDateTime.of(2026, 11, 6, 7, 0), status = ReminderStatus.Completed), now))
    }

    @Test
    fun everyDayEveryWeekdayAndWeeklyDaysAreTheClockAppsOwnRepeats() {
        fun days(rule: RepeatRule): List<Int>? = assertNotNull(ClockAlarms.intentFor(reminder(LocalDateTime.of(2026, 11, 20, 9, 0), rule), now))
            .getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)?.toList()

        assertEquals((1..7).toList(), days(RepeatRule(RepeatKind.Daily))!!.sorted(), "every day")
        assertEquals(
            listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
            days(RepeatRule(RepeatKind.Weekdays)),
        )
        // ISO Monday and Thursday are the Clock's 2 and 5; ISO Sunday is its 1.
        assertEquals(listOf(Calendar.MONDAY, Calendar.THURSDAY), days(RepeatRule(RepeatKind.Weekly, weekdays = setOf(1, 4))))
        assertEquals(listOf(Calendar.SUNDAY), days(RepeatRule(RepeatKind.Weekly, weekdays = setOf(7))))
    }

    @Test
    fun aRepeatingReminderKeepsItsOwnTimeNotTheNextOnes() {
        val intent = assertNotNull(ClockAlarms.intentFor(reminder(LocalDateTime.of(2026, 11, 20, 8, 45), RepeatRule(RepeatKind.Daily)), now))
        assertEquals(8, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(45, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
    }

    @Test
    fun monthlyYearlyAndEveryNDaysHaveNoClockAlarmToBe() {
        val monthly = RepeatRule(RepeatKind.Monthly, dayOfMonth = 5)
        val yearly = RepeatRule(RepeatKind.Months, dayOfMonth = 5, intervalMonths = 12)
        val every3 = RepeatRule(RepeatKind.Custom, intervalDays = 3)
        for (rule in listOf(monthly, yearly, every3)) {
            assertNull(ClockAlarms.intentFor(reminder(LocalDateTime.of(2026, 11, 6, 9, 0), rule), now), rule.label)
        }
        assertTrue(ClockAlarms.canBeAlarm(reminder(LocalDateTime.of(2026, 11, 6, 9, 0)), now))
    }
}
