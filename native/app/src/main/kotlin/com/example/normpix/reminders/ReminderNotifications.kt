package com.example.normpix.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.normpix.MainActivity
import com.example.normpix.NormPixApplication
import com.example.normpix.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Notification channel, notification content and the intents behind its actions. */
object ReminderNotifications {
    // A channel's sound and audio attributes are locked in by Android the first
    // time it's created, so this id is versioned: change the id (not the
    // attributes) if they ever need to change.
    const val CHANNEL_ID = "normpix_reminders_alarm"
    const val ACTION_ALARM = "com.example.normpix.REMINDER_ALARM"
    const val ACTION_COMPLETE = "com.example.normpix.REMINDER_COMPLETE"
    const val ACTION_SNOOZE = "com.example.normpix.REMINDER_SNOOZE"
    const val EXTRA_REMINDER_ID = "reminder_id"

    /** What the notification's Snooze action snoozes for. */
    val DEFAULT_SNOOZE: Duration = Duration.ofHours(1)

    /**
     * Alarm-stream audio plays even when the ringer is on Vibrate or Silent
     * (those only mute the notification stream) - the way alarm clocks guarantee
     * a reminder is actually heard, which a bill reminder needs just as much.
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Reminders you've set in NormPix"
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun uri(id: Long, what: String): Uri = Uri.parse("normpix://reminder/$id/$what")

    private fun broadcast(context: Context, receiver: Class<*>, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, receiver).setAction(action).setData(uri(id, action)).putExtra(EXTRA_REMINDER_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun alarmIntent(context: Context, id: Long): PendingIntent = broadcast(context, ReminderAlarmReceiver::class.java, ACTION_ALARM, id)

    /** Shows the heads-up notification for a reminder that is due. Tapping the body opens the alert screen. */
    @SuppressLint("MissingPermission")
    fun post(context: Context, reminder: Reminder) {
        val id = reminder.id ?: return
        if (!hasPermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setData(uri(id, "open"))
                .putExtra(EXTRA_REMINDER_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.title)
            .setContentText(reminder.notes?.trim()?.takeIf { it.isNotEmpty() } ?: "Reminder due now")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Complete", broadcast(context, ReminderActionReceiver::class.java, ACTION_COMPLETE, id))
            .addAction(0, "Snooze", broadcast(context, ReminderActionReceiver::class.java, ACTION_SNOOZE, id))
            .build()
        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }

    fun cancelPosted(context: Context, id: Long) {
        NotificationManagerCompat.from(context).cancel(id.toInt())
    }
}

/** Schedules each reminder as a wake-up alarm for its effective due time. */
class AlarmReminderScheduler(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ReminderScheduler {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    /** Whether alarms can fire at the exact minute (a user-granted special permission on Android 12+). */
    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    override suspend fun schedule(reminder: Reminder) {
        val id = reminder.id ?: return
        val due = reminder.effectiveDueAt
        if (due.isBefore(LocalDateTime.now())) return
        val at = due.atZone(zone).toInstant().toEpochMilli()
        val intent = ReminderNotifications.alarmIntent(context, id)
        // Without the exact-alarm opt-in the alarm still fires, just within a window rather than on the minute.
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    override suspend fun cancel(id: Long) {
        alarms.cancel(ReminderNotifications.alarmIntent(context, id))
        ReminderNotifications.cancelPosted(context, id)
    }

    /** Opens the system page where the user can allow exact alarms for this app. */
    fun exactAlarmSettingsIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            null
        }
}

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun BroadcastReceiver.work(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

private fun Context.reminders(): RemindersController = (applicationContext as NormPixApplication).container.reminders

/** An alarm went off: show the notification for that reminder. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1)
        if (id < 0) return
        work {
            val controller = context.reminders()
            controller.ensureLoaded()
            val reminder = controller.findById(id) ?: return@work
            if (reminder.status != ReminderStatus.Completed) ReminderNotifications.post(context, reminder)
        }
    }
}

/** The Complete / Snooze buttons on a reminder notification. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1)
        if (id < 0) return
        val action = intent.action
        work {
            val controller = context.reminders()
            controller.ensureLoaded()
            ReminderNotifications.cancelPosted(context, id)
            when (action) {
                ReminderNotifications.ACTION_COMPLETE -> controller.completeReminder(id)
                ReminderNotifications.ACTION_SNOOZE -> controller.snoozeReminder(id, ReminderNotifications.DEFAULT_SNOOZE)
            }
        }
    }
}

/** Alarms don't survive a reboot or an app update: put them back. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            work { context.reminders().rearmAll() }
        }
    }
}
