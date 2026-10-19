package com.example.normpix.updates

import com.example.normpix.settings.UpdateCheckEvery
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The arithmetic of checking for updates at a set time (Settings > Automatic updates > When): the times of day a check is
 * made around, and when the next one is due. Pure, so the schedule's decisions can be tested without a phone.
 */
object UpdateCheckTimes {
    private const val DAY_MINUTES = 24 * 60

    /** The most a check may be late and still count for its time (see [slackFor]). */
    private const val MAX_SLACK_MS = 6L * 60 * 60 * 1000

    /**
     * How late a check may have been made and still count as made at its time. Android runs a job once the phone is ready for
     * it, not on the dot, and a launch check can come a little after the set time as well: without this the next check
     * would be pushed a whole slot further out each time. A quarter of the wait, and never more than six hours.
     */
    fun slackFor(every: UpdateCheckEvery): Long = minOf(every.intervalMs / 4, MAX_SLACK_MS)

    /**
     * The times of day (minutes since midnight, in order) a check is made around: [minuteOfDay], and for [UpdateCheckEvery.TwiceADay]
     * the same time 12 hours later by the clock. Once a day or less often, it is the one time. A time outside the day wraps.
     */
    fun minutesOfDay(every: UpdateCheckEvery, minuteOfDay: Int): List<Int> {
        val first = minuteOfDay.mod(DAY_MINUTES)
        return if (every == UpdateCheckEvery.TwiceADay) listOf(first, (first + DAY_MINUTES / 2) % DAY_MINUTES).sorted() else listOf(first)
    }

    /**
     * When the next check at a set time is due (epoch millis): the first of [minutesOfDay] that is not in the past and comes at
     * least one wait ([UpdateCheckEvery.intervalMs], less [slackFor]) after the last check, [lastCheckAt] (0 for never).
     * A clock set back before the last check counts as never having checked. Times are read on the wall clock of [zone], so
     * a change of daylight saving time moves the wait, not the time of day (a time that doesn't exist that day comes after
     * the gap, and one that happens twice is the first).
     */
    fun nextCheckAt(
        now: Long,
        lastCheckAt: Long,
        every: UpdateCheckEvery,
        minuteOfDay: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val due = if (lastCheckAt in 1L..now) lastCheckAt + every.intervalMs - slackFor(every) else now
        val earliest = maxOf(now, due)
        val minutes = minutesOfDay(every, minuteOfDay)
        // From the day before: the time may already have come on the day [earliest] falls on, or not yet.
        val firstDay = Instant.ofEpochMilli(earliest).atZone(zone).toLocalDate().minusDays(1)
        for (offset in 0L..3L) {
            val date = firstDay.plusDays(offset)
            for (minute in minutes) {
                val at = ZonedDateTime.of(date, LocalTime.of(minute / 60, minute % 60), zone).toInstant().toEpochMilli()
                if (at >= earliest) return at
            }
        }
        return earliest
    }
}
