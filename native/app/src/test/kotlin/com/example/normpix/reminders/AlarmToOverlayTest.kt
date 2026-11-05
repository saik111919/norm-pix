package com.example.normpix.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.NormPixApplication
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings

/**
 * The whole way an alarm goes, with the app's real pieces and only Android's clock and screen stood in for: a reminder is saved and
 * arms an exact alarm for its minute; when the alarm rings, the system hands its intent to NormPix's receiver; the receiver loads
 * the reminder and delivers it - the notification, always, and the floating card on top where it is allowed - and records that it
 * has alerted. Each place the card must NOT come up is checked too, since a card that comes where it should not is as wrong as one
 * that never comes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmToOverlayTest {
    private lateinit var app: NormPixApplication
    private lateinit var alarms: ShadowAlarmManager
    private lateinit var notifications: org.robolectric.shadows.ShadowNotificationManager
    private val context: Context get() = app

    @BeforeTest
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<Application>() as NormPixApplication
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications = shadowOf(app.getSystemService(NotificationManager::class.java))
        alarms = shadowOf(app.getSystemService(android.app.AlarmManager::class.java))
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        app.container.preferences.setNotificationsEnabled(true)
        app.container.preferences.setReminderOverlayEnabled(true)
        ShadowSettings.setCanDrawOverlays(true)
    }

    @AfterTest
    fun tearDown() {
        ReminderOverlay.dismiss()
        ShadowLooper.idleMainLooper()
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        ShadowSettings.setCanDrawOverlays(false)
    }

    private fun save(dueIn: Long = 30, notes: String? = "Cash only"): Reminder {
        val now = LocalDateTime.now()
        return runBlocking {
            app.container.reminders.ensureLoaded()
            app.container.reminders.addReminder(
                Reminder(
                    title = "Pay electricity bill",
                    notes = notes,
                    category = ReminderCategory.Payments,
                    dueAt = now.plusSeconds(dueIn),
                    repeatRule = RepeatRule.Once,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** The alarm the reminder armed, found by what it would start: the reminders' alarm receiver, with this reminder's id. */
    private fun alarmOf(reminder: Reminder): ShadowAlarmManager.ScheduledAlarm = assertNotNull(
        alarms.scheduledAlarms.firstOrNull { alarm ->
            val intent = shadowOf(alarm.operation).savedIntent
            intent.component?.className == ReminderAlarmReceiver::class.java.name &&
                intent.getLongExtra(ReminderNotifications.EXTRA_REMINDER_ID, -1) == reminder.id
        },
        "the reminder armed an alarm",
    )

    /** The alarm rings: the system delivers the intent it was set with. Returns once the receiver's own work is done. */
    private fun ring(reminder: Reminder) {
        val intent = shadowOf(alarmOf(reminder).operation).savedIntent
        ReminderAlarmReceiver().onReceive(context, intent)
        val deadline = System.currentTimeMillis() + 5_000
        // The receiver works on its own thread; its last step is stamping the reminder as alerted.
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            if (app.container.reminders.findById(reminder.id!!)?.lastFiredAt != null) break
            Thread.sleep(20)
        }
        ShadowLooper.idleMainLooper()
    }

    @Test
    fun aReminderArmsAnExactAlarmForItsMinute() {
        val reminder = save(dueIn = 30)
        val alarm = alarmOf(reminder)
        val due = reminder.dueAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(kotlin.math.abs(due - alarm.triggerAtTime) <= 1_500, "set for the reminder's own time (${due - alarm.triggerAtTime} ms off)")
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs, "on the minute, not within a window")
        assertEquals(android.app.AlarmManager.RTC_WAKEUP, alarm.type, "wakes the phone")
    }

    @Test
    fun whenTheAlarmRingsTheNotificationAndTheCardAreBothOnScreen() {
        val reminder = save()
        assertFalse(ReminderOverlay.isShowing, "nothing before it rings")
        ring(reminder)

        assertNotNull(notifications.getNotification(reminder.id!!.toInt()), "the notification is always there")
        assertTrue(ReminderOverlay.isShowing, "and the card is over whatever is on the screen")
        assertNotNull(app.container.reminders.findById(reminder.id!!)?.lastFiredAt, "and the reminder knows it alerted")
    }

    @Test
    fun withTheFloatingCardSwitchedOffAlarmsStillNotify() {
        app.container.preferences.setReminderOverlayEnabled(false)
        val reminder = save()
        ring(reminder)
        assertNotNull(notifications.getNotification(reminder.id!!.toInt()))
        assertFalse(ReminderOverlay.isShowing)
    }

    @Test
    fun withoutAndroidsPermissionThereIsNoCardButTheNotificationComes() {
        ShadowSettings.setCanDrawOverlays(false)
        val reminder = save()
        ring(reminder)
        assertNotNull(notifications.getNotification(reminder.id!!.toInt()))
        assertFalse(ReminderOverlay.isShowing)
    }

    @Test
    fun aReminderFinishedBeforeItRangDoesNotRing() {
        val reminder = save()
        // The alarm's intent, as the system would still hold it if finishing the reminder came a moment too late to take it back.
        val intent = shadowOf(alarmOf(reminder).operation).savedIntent
        runBlocking { app.container.reminders.completeReminder(reminder.id!!) }
        ReminderAlarmReceiver().onReceive(context, intent)
        Thread.sleep(300)
        ShadowLooper.idleMainLooper()
        assertNull(notifications.getNotification(reminder.id!!.toInt()))
        assertFalse(ReminderOverlay.isShowing)
    }

    @Test
    fun anAlarmForAReminderDeletedMeanwhileDoesNothingAndDoesNotCrash() {
        val reminder = save()
        val intent = shadowOf(alarmOf(reminder).operation).savedIntent
        runBlocking { app.container.reminders.deleteReminder(reminder.id!!) }
        ReminderAlarmReceiver().onReceive(context, intent)
        Thread.sleep(300)
        ShadowLooper.idleMainLooper()
        assertFalse(ReminderOverlay.isShowing)
    }
}
