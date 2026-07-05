package com.normpix.cashflow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

private const val REMINDERS_PREFS = "cashflow_reminders"
private const val REMINDERS_KEY = "reminders_json"

/** Reads persisted reminders, or null if none have ever been saved (first run). */
fun loadReminders(context: Context): MutableList<Reminder>? {
    val prefs = context.getSharedPreferences(REMINDERS_PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(REMINDERS_KEY, null) ?: return null
    val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
    val out = mutableListOf<Reminder>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val linkedTxnId = o.optString("linkedTxnId", "").ifBlank { null }
        val type = o.optString("type")
        val daysArr = o.optJSONArray("days")
        val days = if (daysArr != null) (0 until daysArr.length()).map { daysArr.getString(it) } else emptyList()
        out.add(
            Reminder(
                id = o.optString("id"),
                type = type,
                icon = reminderIcon(type, hasLinkedTxn = linkedTxnId != null),
                title = o.optString("title"),
                on = o.optBoolean("on", true),
                time = o.optString("time", "").ifBlank { null },
                freq = o.optString("freq"),
                days = days,
                sound = o.optBoolean("sound", true),
                vibration = o.optBoolean("vibration", true),
                priority = o.optString("priority", "normal"),
                date = o.optString("date", "").ifBlank { null },
                linkedTxnId = linkedTxnId,
            ),
        )
    }
    return out
}

/** Persists the current reminder list — [Reminder.icon] is deliberately left out; it's derived
 * from [Reminder.type] via [reminderIcon] on load, since [androidx.compose.ui.graphics.vector.ImageVector] isn't serializable. */
fun saveReminders(context: Context, reminders: List<Reminder>) {
    val arr = JSONArray()
    reminders.forEach { r ->
        arr.put(
            JSONObject().apply {
                put("id", r.id)
                put("type", r.type)
                put("title", r.title)
                put("on", r.on)
                put("time", r.time ?: "")
                put("freq", r.freq)
                put("days", JSONArray(r.days))
                put("sound", r.sound)
                put("vibration", r.vibration)
                put("priority", r.priority)
                put("date", r.date ?: "")
                put("linkedTxnId", r.linkedTxnId ?: "")
            },
        )
    }
    context.getSharedPreferences(REMINDERS_PREFS, Context.MODE_PRIVATE)
        .edit().putString(REMINDERS_KEY, arr.toString()).apply()
}

/** True on API < 31 (no permission needed there) or once the user has granted exact-alarm scheduling. */
fun canScheduleExactAlarms(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

/** Sends the user to system Settings to grant "Alarms & reminders" — there's no runtime dialog for this permission. */
fun requestExactAlarmPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
        )
    }
}

private fun weekdayConstant(day: String): Int = when (day) {
    "Sun" -> Calendar.SUNDAY
    "Mon" -> Calendar.MONDAY
    "Tue" -> Calendar.TUESDAY
    "Wed" -> Calendar.WEDNESDAY
    "Thu" -> Calendar.THURSDAY
    "Fri" -> Calendar.FRIDAY
    else -> Calendar.SATURDAY
}

/**
 * Next trigger time for [r], or null if it shouldn't get a background alarm at all — "instant"
 * reminders (budget cap warnings) only ever fire from a live in-app check, matching the design;
 * there's no reliable way to watch spending thresholds while the app isn't running.
 */
fun nextTriggerMillis(r: Reminder, from: Long = System.currentTimeMillis()): Long? {
    if (!r.on) return null
    val time = r.time ?: return null
    val parts = time.split(":")
    val hh = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val mm = parts.getOrNull(1)?.toIntOrNull() ?: 0

    fun atTime(cal: Calendar): Calendar = cal.apply {
        set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    return when (r.freq) {
        "daily" -> {
            val cal = atTime(Calendar.getInstance())
            if (cal.timeInMillis <= from) cal.add(Calendar.DATE, 1)
            cal.timeInMillis
        }
        "weekly", "custom" -> {
            if (r.days.isEmpty()) return null
            val targets = r.days.map { weekdayConstant(it) }.toSet()
            val cal = atTime(Calendar.getInstance())
            repeat(8) {
                if (cal.get(Calendar.DAY_OF_WEEK) in targets && cal.timeInMillis > from) return cal.timeInMillis
                cal.add(Calendar.DATE, 1)
            }
            null
        }
        "once" -> {
            val dateParts = (r.date ?: return null).split("-").mapNotNull { it.toIntOrNull() }
            if (dateParts.size != 3) return null
            val cal = atTime(Calendar.getInstance()).apply {
                set(Calendar.YEAR, dateParts[0]); set(Calendar.MONTH, dateParts[1] - 1); set(Calendar.DAY_OF_MONTH, dateParts[2])
            }
            cal.timeInMillis.takeIf { it > from }
        }
        else -> null // "instant"
    }
}

private fun reminderPendingIntent(context: Context, id: String): PendingIntent {
    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply { putExtra("reminderId", id) }
    return PendingIntent.getBroadcast(
        context, id.hashCode(), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun setExact(context: Context, triggerAt: Long, pi: PendingIntent) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    if (canScheduleExactAlarms(context)) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    } else {
        // Still fires, just not necessarily to the exact minute, if the user hasn't granted
        // "Alarms & reminders" — better than silently never firing.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }
}

/** Schedules (or cancels, if it has no future occurrence) the real OS alarm for [r]. */
fun scheduleReminder(context: Context, r: Reminder) {
    val pi = reminderPendingIntent(context, r.id)
    val trigger = nextTriggerMillis(r)
    if (trigger == null) {
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        return
    }
    setExact(context, trigger, pi)
}

/** Schedules a one-off alarm for [r] at an explicit time — used for "Snooze 10 min". */
fun scheduleExactAt(context: Context, r: Reminder, triggerAt: Long) {
    setExact(context, triggerAt, reminderPendingIntent(context, r.id))
}

fun cancelReminder(context: Context, id: String) {
    context.getSystemService(AlarmManager::class.java).cancel(reminderPendingIntent(context, id))
}

/** Re-syncs every reminder's real OS alarm against its current on/off + schedule — cheap enough to call after any edit. */
fun syncReminderAlarms(context: Context, reminders: List<Reminder>) {
    reminders.forEach { r ->
        if (r.on && r.freq != "instant") scheduleReminder(context, r) else cancelReminder(context, r.id)
    }
}
