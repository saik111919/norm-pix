package com.example.normpix.reminders

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepeatRuleTest {
    private fun dt(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    @Test
    fun onceHasNoNextOccurrence() {
        assertNull(RepeatRule.Once.nextOccurrence(dt(2026, 10, 1)))
    }

    @Test
    fun weekdaysSkipTheWeekendAndKeepTheTime() {
        val friday = dt(2026, 10, 2, 8, 30)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        assertEquals(dt(2026, 10, 5, 8, 30), RepeatRule(RepeatKind.Weekdays).nextOccurrence(friday))
    }

    @Test
    fun weeklyPicksTheNextChosenWeekday() {
        // Mon(1) and Thu(4); from Tuesday the next one is Thursday, from Thursday it's Monday.
        val rule = RepeatRule(RepeatKind.Weekly, weekdays = setOf(1, 4))
        assertEquals(dt(2026, 10, 1), rule.nextOccurrence(dt(2026, 9, 29)))
        assertEquals(dt(2026, 10, 5), rule.nextOccurrence(dt(2026, 10, 1)))
    }

    @Test
    fun monthlyClampsToShortMonths() {
        val rule = RepeatRule(RepeatKind.Monthly, dayOfMonth = 31)
        assertEquals(dt(2027, 2, 28), rule.nextOccurrence(dt(2027, 1, 31)))
        assertEquals(dt(2028, 2, 29), rule.nextOccurrence(dt(2028, 1, 31)))
        // ... and returns to the 31st once the month has one.
        assertEquals(dt(2027, 3, 31), rule.nextOccurrence(dt(2027, 2, 28)))
    }

    @Test
    fun customIntervalAddsDays() {
        assertEquals(dt(2026, 10, 11), RepeatRule(RepeatKind.Custom, intervalDays = 10).nextOccurrence(dt(2026, 10, 1)))
    }

    @Test
    fun everyNMonthsKeepsTheDayAndClampsToShortMonths() {
        val bimonthly = RepeatRule(RepeatKind.Months, dayOfMonth = 31, intervalMonths = 2)
        assertEquals(dt(2026, 12, 31), bimonthly.nextOccurrence(dt(2026, 10, 31)))
        // Two months on from 31 December is the end of February, and it goes back to the 31st once the month has one.
        assertEquals(dt(2027, 2, 28), bimonthly.nextOccurrence(dt(2026, 12, 31)))
        assertEquals(dt(2027, 4, 30), bimonthly.nextOccurrence(dt(2027, 2, 28)))
    }

    @Test
    fun everyYearIsTwelveMonthsAndHandlesALeapDay() {
        val yearly = RepeatRule(RepeatKind.Months, dayOfMonth = 29, intervalMonths = 12)
        assertEquals("Every year", yearly.label)
        assertEquals(dt(2029, 2, 28), yearly.nextOccurrence(dt(2028, 2, 29)))
        assertEquals(dt(2030, 2, 28), yearly.nextOccurrence(dt(2029, 2, 28)))
        assertEquals("Every 3 months", RepeatRule(RepeatKind.Months, dayOfMonth = 5, intervalMonths = 3).label)
    }

    @Test
    fun jsonRoundTrips() {
        val rules = listOf(
            RepeatRule.Once,
            RepeatRule(RepeatKind.Weekly, weekdays = setOf(7, 2)),
            RepeatRule(RepeatKind.Monthly, dayOfMonth = 15),
            RepeatRule(RepeatKind.Custom, intervalDays = 3),
            RepeatRule(RepeatKind.Months, dayOfMonth = 9, intervalMonths = 12),
        )
        for (rule in rules) assertEquals(rule, RepeatRule.fromJson(rule.toJson()))
    }
}

class ReminderActionsTest {
    private val now = LocalDateTime.of(2026, 10, 3, 9, 20)

    private fun reminder(rule: RepeatRule = RepeatRule.Once, dueAt: LocalDateTime = LocalDateTime.of(2026, 10, 3, 9, 0)) = Reminder(
        id = 1,
        title = "Pay card bill",
        category = ReminderCategory.Payments,
        dueAt = dueAt,
        repeatRule = rule,
        createdAt = now.minusDays(10),
        updatedAt = now.minusDays(10),
    )

    @Test
    fun completingAOneTimeReminderFinishesIt() {
        val done = ReminderActions.complete(reminder(), now)
        assertEquals(ReminderStatus.Completed, done.status)
        assertEquals(now, done.completedAt)
    }

    @Test
    fun completingARepeatingReminderAdvancesFromTheOriginalDueTime() {
        // Completed 20 minutes late, the next one is still at 9:00 tomorrow.
        val next = ReminderActions.complete(reminder(RepeatRule(RepeatKind.Daily)), now)
        assertEquals(ReminderStatus.Active, next.status)
        assertEquals(LocalDateTime.of(2026, 10, 4, 9, 0), next.dueAt)
        assertNull(next.snoozedUntil)
    }

    @Test
    fun completingAWeeksOverdueRepeatingReminderMovesToTheFirstTimeAhead() {
        // Left alone for 12 days: advancing one step would land on a time already past, which is never alarmed.
        val stale = reminder(RepeatRule(RepeatKind.Daily), dueAt = LocalDateTime.of(2026, 9, 21, 9, 0))
        val next = ReminderActions.complete(stale, now)
        assertEquals(ReminderStatus.Active, next.status)
        assertEquals(LocalDateTime.of(2026, 10, 4, 9, 0), next.dueAt, "the next 9:00 after now, the days missed skipped")
        assertTrue(next.dueAt.isAfter(now))
    }

    @Test
    fun aRepeatThatEndedWhileOverdueFinishesTheReminder() {
        val rule = RepeatRule(RepeatKind.Daily, endDate = java.time.LocalDate.of(2026, 9, 25))
        val done = ReminderActions.complete(reminder(rule, dueAt = LocalDateTime.of(2026, 9, 21, 9, 0)), now)
        assertEquals(ReminderStatus.Completed, done.status)
    }

    @Test
    fun nextCycleIsTheNextTimeAfterBothTheDueTimeAndNow() {
        val daily = reminder(RepeatRule(RepeatKind.Daily), dueAt = LocalDateTime.of(2026, 10, 3, 9, 0))
        assertEquals(LocalDateTime.of(2026, 10, 4, 9, 0), ReminderActions.nextCycle(daily, now))
        assertNull(ReminderActions.nextCycle(reminder(), now), "a reminder that does not repeat has no next cycle")
    }

    @Test
    fun theDoneButtonSaysWhatIsBeingDone() {
        assertEquals("Mark as paid", reminder().doneLabel)
        assertEquals("Mark as received", reminder().copy(category = ReminderCategory.Income).doneLabel)
        assertEquals("Mark as done", reminder().copy(category = ReminderCategory.Personal).doneLabel)
        // ... and the same in one word for a notification's button.
        assertEquals("Paid", reminder().shortDoneLabel)
        assertEquals("Received", reminder().copy(category = ReminderCategory.Income).shortDoneLabel)
        assertEquals("Done", reminder().copy(category = ReminderCategory.Cards).shortDoneLabel)
    }

    @Test
    fun aReminderIsAlertedOnceItsLastAlertIsAtOrAfterTheTimeItWaitsOn() {
        val r = reminder(dueAt = LocalDateTime.of(2026, 10, 3, 9, 0))
        assertFalse(r.wasAlerted())
        assertTrue(r.copy(lastFiredAt = LocalDateTime.of(2026, 10, 3, 9, 0, 1)).wasAlerted())
        // Snoozed to a later time: the earlier alert does not count for it.
        val snoozed = ReminderActions.snooze(r.copy(lastFiredAt = LocalDateTime.of(2026, 10, 3, 9, 0, 1)), Duration.ofHours(1), now)
        assertFalse(snoozed.wasAlerted())
    }

    @Test
    fun snoozeKeepsTheDueTime() {
        val original = reminder(RepeatRule(RepeatKind.Daily))
        val snoozed = ReminderActions.snooze(original, Duration.ofMinutes(15), now)
        assertEquals(ReminderStatus.Snoozed, snoozed.status)
        assertEquals(original.dueAt, snoozed.dueAt)
        assertEquals(now.plusMinutes(15), snoozed.effectiveDueAt)
    }

    @Test
    fun rescheduleRebasesAndClearsTheSnooze() {
        val snoozed = ReminderActions.snooze(reminder(), Duration.ofHours(1), now)
        val moved = ReminderActions.reschedule(snoozed, now.plusDays(2), now)
        assertEquals(ReminderStatus.Active, moved.status)
        assertEquals(now.plusDays(2), moved.dueAt)
        assertNull(moved.snoozedUntil)
    }
}

class RemindersStateTest {
    private val now = LocalDateTime.of(2026, 10, 3, 12, 0)

    private fun r(id: Long, dueAt: LocalDateTime, status: ReminderStatus = ReminderStatus.Active, snoozedUntil: LocalDateTime? = null) =
        Reminder(
            id = id,
            title = "R$id",
            category = ReminderCategory.Personal,
            dueAt = dueAt,
            repeatRule = RepeatRule.Once,
            status = status,
            snoozedUntil = snoozedUntil,
            createdAt = now.minusDays(1),
            updatedAt = now.minusDays(1),
            completedAt = if (status == ReminderStatus.Completed) now.minusHours(1) else null,
        )

    @Test
    fun everyUnfinishedReminderIsEitherOverdueOrUpcoming() {
        val state = RemindersState(
            reminders = listOf(
                r(1, now.minusHours(2)), // overdue
                r(2, now.plusHours(2)), // upcoming
                r(3, now.minusHours(3), ReminderStatus.Snoozed, snoozedUntil = now.plusMinutes(30)), // snoozed, still waiting
                r(4, now.minusHours(4), ReminderStatus.Snoozed, snoozedUntil = now.minusMinutes(5)), // snooze ran out
                r(5, now.minusDays(1), ReminderStatus.Completed),
            ),
            loading = false,
        )
        assertEquals(listOf(1L, 4L), state.overdue(now).map { it.id }, "an expired snooze counts as overdue, oldest first")
        assertEquals(listOf(3L, 2L), state.upcoming(now).map { it.id })
        assertEquals(listOf(5L), state.completed.map { it.id })
        val unfinished = state.reminders.filter { it.status != ReminderStatus.Completed }.map { it.id }.toSet()
        assertEquals(unfinished, (state.overdue(now) + state.upcoming(now)).map { it.id }.toSet())
    }

    @Test
    fun overdueIsDerivedNotStored() {
        val waiting = r(1, now.minusHours(1), ReminderStatus.Snoozed, snoozedUntil = now.plusHours(1))
        assertFalse(waiting.isOverdue(now))
        assertTrue(waiting.isOverdue(now.plusHours(2)))
        assertFalse(r(2, now.minusHours(1), ReminderStatus.Completed).isOverdue(now))
    }
}
