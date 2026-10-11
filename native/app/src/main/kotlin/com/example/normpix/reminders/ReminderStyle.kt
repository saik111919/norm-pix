package com.example.normpix.reminders

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.normpix.ui.theme.StatusTone
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How urgently a reminder needs attention right now. Unlike
 * [Reminder.status], this is never stored - it's derived fresh from
 * status/due time every time it's read, so it can't go stale.
 */
enum class ReminderUrgency(val label: String, val icon: ImageVector, val tone: StatusTone) {
    Overdue("Overdue", Icons.Rounded.ErrorOutline, StatusTone.Error),
    DueSoon("Due soon", Icons.Rounded.Schedule, StatusTone.Warning),
    Upcoming("Upcoming", Icons.Outlined.Event, StatusTone.Neutral),
    Snoozed("Snoozed", Icons.Rounded.Snooze, StatusTone.Info),
    Completed("Completed", Icons.Outlined.CheckCircle, StatusTone.Success),
}

fun Reminder.urgency(now: LocalDateTime = LocalDateTime.now()): ReminderUrgency = when {
    status == ReminderStatus.Completed -> ReminderUrgency.Completed
    isOverdue(now) -> ReminderUrgency.Overdue
    status == ReminderStatus.Snoozed -> ReminderUrgency.Snoozed
    effectiveDueAt.isBefore(now.plusHours(24)) -> ReminderUrgency.DueSoon
    else -> ReminderUrgency.Upcoming
}

val ReminderCategory.icon: ImageVector
    get() = when (this) {
        ReminderCategory.Payments -> Icons.Outlined.Payments
        ReminderCategory.Expenses -> Icons.Outlined.ReceiptLong
        ReminderCategory.Income -> Icons.Outlined.AccountBalanceWallet
        ReminderCategory.Accounts -> Icons.Outlined.AccountBalance
        ReminderCategory.Cards -> Icons.Outlined.CreditCard
        ReminderCategory.Personal -> Icons.Outlined.Person
        ReminderCategory.Custom -> Icons.Outlined.Label
    }

private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

fun timeText(moment: LocalDateTime, is24Hour: Boolean): String =
    DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault()).format(moment)

/**
 * Formats [dueAt] relative to [now] the way the reminder list, Home preview and
 * alert screen all describe a due moment - "Today, 6:00 PM", "Tomorrow, 9:00 AM",
 * "Monday, 9:00 AM", "Jan 15, 9:00 AM". The clock format follows the device's
 * 12/24-hour preference.
 */
fun formatDueMoment(dueAt: LocalDateTime, is24Hour: Boolean, now: LocalDateTime = LocalDateTime.now()): String {
    val time = timeText(dueAt, is24Hour)
    val dayDiff = ChronoUnit.DAYS.between(now.toLocalDate(), dueAt.toLocalDate())
    return when {
        dayDiff == 0L -> "Today, $time"
        dayDiff == 1L -> "Tomorrow, $time"
        dayDiff == -1L -> "Yesterday, $time"
        dayDiff in 2..6 -> "${WEEKDAYS[dueAt.dayOfWeek.value - 1]}, $time"
        else -> "${MONTHS[dueAt.monthValue - 1]} ${dueAt.dayOfMonth}, $time"
    }
}

@Composable
fun formatDueMoment(dueAt: LocalDateTime): String = formatDueMoment(dueAt, DateFormat.is24HourFormat(LocalContext.current))

/** Compact "Overdue by ..." text, where the full date/time is too long. */
fun formatOverdueBy(dueAt: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
    val diff = Duration.between(dueAt, now)
    return when {
        diff.toDays() >= 1 -> "Overdue by ${diff.toDays()}d"
        diff.toHours() >= 1 -> "Overdue by ${diff.toHours()}h"
        diff.toMinutes() >= 1 -> "Overdue by ${diff.toMinutes()}m"
        else -> "Overdue"
    }
}

/**
 * One-line "when" text for a reminder, as shown in lists: overdue ones say how
 * late they are, completed ones say so, everything else gets its relative due
 * moment.
 */
@Composable
fun reminderDueText(reminder: Reminder): String = when (reminder.urgency()) {
    ReminderUrgency.Completed -> "Completed"
    ReminderUrgency.Overdue -> formatOverdueBy(reminder.effectiveDueAt)
    ReminderUrgency.Snoozed -> "Snoozed until ${formatDueMoment(reminder.effectiveDueAt)}"
    else -> formatDueMoment(reminder.effectiveDueAt)
}
