package com.example.normpix.availability

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * A feature whose use can be restricted to a time window from Settings -> Availability & Schedules.
 * Adding a feature here is all it takes for it to be configurable: the Settings page lists every entry
 * and [AvailabilityRule] is stored per entry.
 */
enum class ScheduledFeature(val label: String, val shortLabel: String, val defaultMode: AvailabilityMode) {
    /** Off until the user turns it on: nothing in the app offers a card exchange before then (see `rememberNfcExchangeOffered`). */
    NfcExchange("NFC card exchange", "NFC", AvailabilityMode.Off),
    ;

    /** The rule in force until the user edits it. */
    fun defaultRule(): AvailabilityRule = AvailabilityRule(mode = defaultMode)
}

enum class AvailabilityMode(val label: String) {
    Always("Always on"),
    Scheduled("On a schedule"),
    Off("Off"),
}

/**
 * A weekly window: on each of [days] from [start] until [end]. When [end] is not after [start] the
 * window runs past midnight into the next day (22:00 -> 06:00); equal times mean the whole day.
 */
data class WeeklySchedule(
    val days: Set<DayOfWeek> = DEFAULT_DAYS,
    val start: LocalTime = LocalTime.of(18, 0),
    val end: LocalTime = LocalTime.of(20, 0),
) {
    val isAllDay: Boolean get() = start == end
    val crossesMidnight: Boolean get() = end < start

    companion object {
        val EVERY_DAY: Set<DayOfWeek> = DayOfWeek.entries.toSet()
        val DEFAULT_DAYS: Set<DayOfWeek> = EVERY_DAY
    }
}

/** The user's choice for one [ScheduledFeature]. [schedule] is kept while the mode is not [AvailabilityMode.Scheduled], so switching back restores it. */
data class AvailabilityRule(
    val mode: AvailabilityMode = AvailabilityMode.Always,
    val schedule: WeeklySchedule = WeeklySchedule(),
)

/** Why a feature can or cannot be used at a moment in time. */
sealed interface Availability {
    val isOpen: Boolean

    /** Usable now; [closesAt] is null for "always on". */
    data class Open(val closesAt: LocalDateTime?) : Availability {
        override val isOpen get() = true
    }

    /** Switched off in Settings. */
    data object TurnedOff : Availability {
        override val isOpen get() = false
    }

    /** A schedule with no days selected: it can never open. */
    data object NoDays : Availability {
        override val isOpen get() = false
    }

    /** Outside the window; [opensAt] is the start of the next one. */
    data class Waiting(val opensAt: LocalDateTime, val closesAt: LocalDateTime) : Availability {
        override val isOpen get() = false
    }
}

/** Pure scheduling logic: no clock, no Android - the caller supplies "now". */
fun AvailabilityRule.availabilityAt(now: LocalDateTime): Availability = when (mode) {
    AvailabilityMode.Always -> Availability.Open(closesAt = null)
    AvailabilityMode.Off -> Availability.TurnedOff
    AvailabilityMode.Scheduled -> schedule.availabilityAt(now)
}

fun WeeklySchedule.availabilityAt(now: LocalDateTime): Availability {
    if (days.isEmpty()) return Availability.NoDays
    // A window that began yesterday may still be open (overnight), so look from the day before.
    val dates = (-1L..7L).map { now.toLocalDate().plusDays(it) }
    val windows = dates.filter { it.dayOfWeek in days }.map { date ->
        val from = date.atTime(start)
        val to = if (isAllDay) from.plusDays(1) else if (crossesMidnight) date.plusDays(1).atTime(end) else date.atTime(end)
        from to to
    }
    windows.firstOrNull { (from, to) -> !now.isBefore(from) && now.isBefore(to) }?.let { (_, to) ->
        // Back-to-back windows (an all-day schedule on consecutive days) read as one: report the last end,
        // and none at all when it runs unbroken past everything we looked at (every day, all day).
        var closes = to
        while (windows.any { it.first == closes }) closes = windows.first { it.first == closes }.second
        return Availability.Open(closesAt = closes.takeUnless { it == windows.last().second })
    }
    val next = windows.first { (from, _) -> from.isAfter(now) }
    return Availability.Waiting(opensAt = next.first, closesAt = next.second)
}

/** Time of day the way the rest of the app writes it: "6:00 PM", or "18:00" when the phone is set to 24-hour time. */
fun formatTime(time: LocalTime, is24Hour: Boolean = false): String =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(time)

/** "Every day", "Weekdays", "Weekends", or "Mon, Wed, Fri". */
fun describeDays(days: Set<DayOfWeek>): String = when {
    days.isEmpty() -> "No days"
    days == WeeklySchedule.EVERY_DAY -> "Every day"
    days == WEEKDAYS -> "Weekdays"
    days == WEEKEND -> "Weekends"
    else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

val WEEKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** "6:00 PM to 8:00 PM", "All day", or "10:00 PM to 6:00 AM (next day)". */
fun describeWindow(schedule: WeeklySchedule, is24Hour: Boolean = false): String = when {
    schedule.isAllDay -> "All day"
    schedule.crossesMidnight -> "${formatTime(schedule.start, is24Hour)} to ${formatTime(schedule.end, is24Hour)} (next day)"
    else -> "${formatTime(schedule.start, is24Hour)} to ${formatTime(schedule.end, is24Hour)}"
}

/** The rule in one line for a settings row: "Always on", "Off", "Weekdays: 6:00 PM to 8:00 PM". */
fun AvailabilityRule.summary(is24Hour: Boolean = false): String = when (mode) {
    AvailabilityMode.Always -> "Always on"
    AvailabilityMode.Off -> "Off"
    AvailabilityMode.Scheduled ->
        if (schedule.days.isEmpty()) "On a schedule: choose days"
        else "${describeDays(schedule.days)}: ${describeWindow(schedule, is24Hour)}"
}

/**
 * One sentence about the state right now, written for the person using the feature:
 * "Available now until 8:00 PM.", "Available today from 6:00 PM to 8:00 PM.", "Available Friday from ...".
 */
fun Availability.describe(now: LocalDateTime, is24Hour: Boolean = false): String = when (this) {
    is Availability.Open ->
        if (closesAt == null) "Available now."
        else "Available now until ${formatTime(closesAt.toLocalTime(), is24Hour)}${closesAt.dayNote(now)}."
    Availability.TurnedOff -> "Turned off in Settings."
    Availability.NoDays -> "The schedule has no days selected."
    is Availability.Waiting -> {
        val day = when (opensAt.toLocalDate()) {
            now.toLocalDate() -> "today"
            now.toLocalDate().plusDays(1) -> "tomorrow"
            else -> opensAt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        }
        val to = closesAt.toLocalTime()
        if (opensAt.toLocalTime() == to) "Available $day, all day."
        else "Available $day from ${formatTime(opensAt.toLocalTime(), is24Hour)} to ${formatTime(to, is24Hour)}."
    }
}

private fun LocalDateTime.dayNote(now: LocalDateTime): String = when {
    toLocalDate() == now.toLocalDate() -> ""
    toLocalDate() == now.toLocalDate().plusDays(1) -> " tomorrow"
    else -> " on ${dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}"
}

/** Time until the next change (window opening or closing) so a screen can re-evaluate exactly then; null when it never changes. */
fun Availability.nextChangeAfter(now: LocalDateTime): Duration? = when (this) {
    is Availability.Open -> closesAt?.let { Duration.between(now, it) }
    is Availability.Waiting -> Duration.between(now, opensAt)
    else -> null
}
