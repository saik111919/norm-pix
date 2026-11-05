package com.example.normpix.reminders

import androidx.core.net.toUri
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
import com.example.normpix.settings.AppNotifications
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

    /** Whether a reminder can be posted right now: the user's switch, Android's permission and Android's per-app switch. */
    fun canPost(context: Context): Boolean =
        AppNotifications.allowed(context) && hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun uri(id: Long, what: String): Uri = "normpix://reminder/$id/$what".toUri()

    private fun broadcast(context: Context, receiver: Class<*>, action: String, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, receiver).setAction(action).setData(uri(id, action)).putExtra(EXTRA_REMINDER_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun alarmIntent(context: Context, id: Long): PendingIntent = broadcast(context, ReminderAlarmReceiver::class.java, ACTION_ALARM, id)

    /** NormPix opened on a reminder's alert screen. */
    fun openIntent(context: Context, id: Long): Intent =
        Intent(context, MainActivity::class.java)
            .setData(uri(id, "open"))
            .putExtra(EXTRA_REMINDER_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** The alert on its own screen, over a locked phone, for the notification's full-screen intent. */
    private fun alertIntent(context: Context, id: Long): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, ReminderAlertActivity::class.java)
            .setData(uri(id, "alert"))
            .putExtra(EXTRA_REMINDER_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Whether a full-screen alert may come with the notification: the user's switch, and Android's permission for it. */
    fun fullScreenAllowed(context: Context): Boolean =
        AppNotifications.fullScreenWanted(context) && ReminderAccess.canUseFullScreen(context)

    /**
     * Shows the heads-up notification for a reminder that is due: Mark as paid / done, Snooze and Open, and tapping the body opens
     * the alert screen. Where Android allows it (and the user has not switched it off) it also carries a full-screen alert, which
     * Android raises only when the phone is locked or its screen is off; on an unlocked phone it is an ordinary heads-up.
     * Returns whether it was posted - it is not when notifications are off, here or in Android.
     */
    @SuppressLint("MissingPermission")
    fun post(context: Context, reminder: Reminder): Boolean {
        val id = reminder.id ?: return false
        // Turned off in Settings: the reminder still comes due in the list, it just doesn't alert.
        if (!canPost(context)) return false
        createChannel(context)
        val open = PendingIntent.getActivity(context, 0, openIntent(context, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // On a lock screen set to hide private content Android shows this instead of the reminder's words.
        val hidden = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("Reminder")
            .setContentText("Something is due")
            .build()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(reminder.title)
            .setContentText(reminder.notes?.trim()?.takeIf { it.isNotEmpty() } ?: "Reminder due now")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, reminder.shortDoneLabel, broadcast(context, ReminderActionReceiver::class.java, ACTION_COMPLETE, id))
            .addAction(0, "Snooze", broadcast(context, ReminderActionReceiver::class.java, ACTION_SNOOZE, id))
            .addAction(0, "Open", open)
        // Only when nobody is looking at the phone (locked, or screen off). Attached to a notification that arrives on an unlocked,
        // lit phone it made the heads-up sit on screen for as long as it was left (seen on an emulator, Android 17) - covering the
        // floating card that goes at the same place - when an ordinary heads-up is gone in a few seconds.
        val nobodyLooking = ReminderAccess.locked(context) || !ReminderAccess.screenOn(context)
        if (nobodyLooking && fullScreenAllowed(context)) builder.setFullScreenIntent(alertIntent(context, id), true)
        NotificationManagerCompat.from(context).notify(id.toInt(), builder.build())
        return true
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
        set(id, due)
    }

    override suspend fun scheduleAt(reminder: Reminder, at: LocalDateTime) {
        val id = reminder.id ?: return
        if (at.isBefore(LocalDateTime.now())) return
        set(id, at)
    }

    private fun set(id: Long, at: LocalDateTime) {
        val millis = at.atZone(zone).toInstant().toEpochMilli()
        val intent = ReminderNotifications.alarmIntent(context, id)
        // Without the exact-alarm opt-in the alarm still fires, just within a window rather than on the minute. A phone that
        // refuses even that (a security exception when the permission was taken away mid-way) falls back to the inexact one.
        if (canScheduleExact()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
                return
            } catch (_: SecurityException) {
                // fall through to the inexact alarm
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent)
    }

    override suspend fun cancel(id: Long) {
        alarms.cancel(ReminderNotifications.alarmIntent(context, id))
        ReminderNotifications.cancelPosted(context, id)
    }

    /** Opens the system page where the user can allow exact alarms for this app. */
    fun exactAlarmSettingsIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // A broadcast receiver has no screen to say it on: a failed alarm or action must not take the whole app down with it.
        } finally {
            pending.finish()
        }
    }
}

internal fun Context.reminders(): RemindersController = (applicationContext as NormPixApplication).container.reminders

/** An alarm went off: deliver that reminder - its notification, and where it may, the floating card. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1)
        if (id < 0) return
        work {
            val controller = context.reminders()
            controller.ensureLoaded()
            val reminder = controller.findById(id) ?: return@work
            if (reminder.status == ReminderStatus.Completed) return@work
            // A snooze that was moved on while this alarm was already on its way: its own alarm will deliver it.
            if (reminder.effectiveDueAt.isAfter(LocalDateTime.now().plusMinutes(1))) return@work
            ReminderDelivery.deliver(context, reminder)
            controller.alerted(id)
        }
    }
}

/** The Mark as paid / Snooze buttons on a reminder notification. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1)
        if (id < 0) return
        val action = intent.action
        work {
            val controller = context.reminders()
            controller.ensureLoaded()
            ReminderNotifications.cancelPosted(context, id)
            // A floating card for the same reminder is no longer needed.
            ReminderOverlay.dismiss()
            // A reminder deleted since the notification was posted has nothing to finish; that must not take the app down.
            runCatching {
                when (action) {
                    ReminderNotifications.ACTION_COMPLETE -> controller.completeReminder(id)
                    ReminderNotifications.ACTION_SNOOZE -> controller.snoozeReminder(id, ReminderNotifications.DEFAULT_SNOOZE)
                }
            }
        }
    }
}

/**
 * Alarms don't survive a reboot or an app update, and are set against the clock and the time zone: put them back whenever any of
 * those change. A reminder that came due while the phone was off or NormPix was being updated is delivered now (once), not lost;
 * a change of clock or zone only re-sets the alarms.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val restart = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        val reset = restart ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            // Android 12+: the user allowed (or took away) exact alarms; the same text as AlarmManager's constant, which is newer.
            action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        if (!reset) return
        work {
            val missed = context.reminders().rearmAll(deliverMissed = restart)
            for (reminder in missed) {
                ReminderDelivery.deliver(context, reminder)
                reminder.id?.let { context.reminders().alerted(it) }
            }
        }
    }
}
