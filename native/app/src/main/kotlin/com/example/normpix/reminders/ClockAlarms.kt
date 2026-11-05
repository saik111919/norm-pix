package com.example.normpix.reminders

import android.content.Intent
import android.provider.AlarmClock
import java.time.Duration
import java.time.LocalDateTime
import java.util.Calendar

/*
 * A reminder as an alarm in the phone's own Clock app, on request ("Add to Clock alarms" in a reminder's actions): Android's
 * AlarmClock intent opens the Clock app's alarm form already filled in - the time, the label, the days - and the person saves it
 * there, so it is theirs to keep, change or delete with every other alarm, ringing as the Clock app rings. NormPix keeps nothing
 * about it. A clock alarm only knows a time of day and the days of the week, so only a reminder that can be said that way is
 * offered it: one that falls within the next day, or one that repeats every day, every weekday or on days of the week. A bill due
 * in three weeks, or on the 5th of every month, has no clock alarm to be (it stays a reminder, which is what knows dates).
 */
object ClockAlarms {
    /** The Clock app's alarm form for [reminder], or null where an alarm cannot say what the reminder does. */
    fun intentFor(reminder: Reminder, now: LocalDateTime = LocalDateTime.now()): Intent? {
        if (reminder.status == ReminderStatus.Completed) return null
        val days = repeatDays(reminder.repeatRule)
        val at = if (reminder.repeatRule.repeats) reminder.dueAt else reminder.effectiveDueAt
        if (reminder.repeatRule.repeats) {
            if (days == null) return null
        } else if (!at.isAfter(now) || Duration.between(now, at) > Duration.ofHours(24)) {
            // One time: the Clock app rings at the next such time, which is the reminder's only if that is within a day.
            return null
        }
        return Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, at.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, at.minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, reminder.title)
            // Shown to the person to save: it is their alarm, and they see what is being added.
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            .also { intent -> days?.let { intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(it)) } }
    }

    /** Whether [reminder] can be a clock alarm (what decides whether "Add to Clock alarms" is offered). */
    fun canBeAlarm(reminder: Reminder, now: LocalDateTime = LocalDateTime.now()): Boolean = intentFor(reminder, now) != null

    /**
     * The days a repeating rhythm falls on, as the Clock app wants them (`Calendar.SUNDAY` = 1 ... `Calendar.SATURDAY` = 7), or null
     * for one that is not a day-of-the-week rhythm (monthly, yearly, every N days) or that does not repeat.
     */
    internal fun repeatDays(rule: RepeatRule): List<Int>? = when (rule.kind) {
        RepeatKind.Daily -> (1..7).map(::calendarDay)
        RepeatKind.Weekdays -> (1..5).map(::calendarDay)
        RepeatKind.Weekly -> rule.weekdays?.takeIf { it.isNotEmpty() }?.sorted()?.map(::calendarDay)
        else -> null
    }

    /** An ISO weekday (1 = Monday ... 7 = Sunday) as a `Calendar` day (Sunday = 1 ... Saturday = 7). */
    private fun calendarDay(iso: Int): Int = if (iso == 7) Calendar.SUNDAY else iso + 1
}
