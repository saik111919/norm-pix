package com.normpix.cashflow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Fires when a scheduled reminder's [android.app.AlarmManager] alarm goes off — runs even if the
 * app process isn't alive, since the manifest registers this receiver as its own entry point.
 * Posts a full-screen-intent notification that launches [AlarmActivity], then reschedules the
 * next occurrence for a recurring reminder (exact alarms are one-shot).
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("reminderId") ?: return
        val reminders = loadReminders(context) ?: return
        val r = reminders.find { it.id == id } ?: return

        if (r.on) showAlarmNotification(context, r)

        // Recurring reminders reschedule themselves for the next occurrence; one-offs don't.
        if (r.on && r.freq != "once") scheduleReminder(context, r)
    }
}

/** Channel id encodes sound/vibration since Android only lets you set those at channel creation (API 26+). */
private fun channelIdFor(r: Reminder): String = "reminder_alarms_s${r.sound}_v${r.vibration}"

private fun ensureChannel(context: Context, r: Reminder): String {
    val channelId = channelIdFor(r)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Reminder alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Full-screen reminder alerts"
            enableVibration(r.vibration)
            if (!r.sound) setSound(null, null)
            else setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
        }
        manager.createNotificationChannel(channel)
    }
    return channelId
}

private fun showAlarmNotification(context: Context, r: Reminder) {
    if (!hasNotificationPermission(context)) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // A "silent" reminder is a quiet heads-up, not a lock-screen takeover.
    if (r.priority == "silent") {
        val quietChannelId = "reminder_quiet"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(quietChannelId, "Quiet reminders", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openAppIntent = PendingIntent.getActivity(
            context, r.id.hashCode(),
            Intent(context, com.normpix.MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, quietChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(r.title)
            .setContentText("Tap to open NormPix")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(("alarm_" + r.id).hashCode(), notification)
        return
    }

    val channelId = ensureChannel(context, r)
    val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
        putExtra("reminderId", r.id)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION
    }
    val fullScreenPendingIntent = PendingIntent.getActivity(
        context, r.id.hashCode(), fullScreenIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(r.title)
        .setContentText("Tap to open NormPix")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .setContentIntent(fullScreenPendingIntent)
        .setAutoCancel(true)
        .setOngoing(r.priority == "urgent")

    if (r.sound && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
    }
    if (r.vibration && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        builder.setVibrate(longArrayOf(0, 500, 300, 500, 300, 500))
    }

    manager.notify(("alarm_" + r.id).hashCode(), builder.build())
}
