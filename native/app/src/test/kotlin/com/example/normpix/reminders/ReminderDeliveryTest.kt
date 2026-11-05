package com.example.normpix.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.NormPixApplication
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/** What a due reminder puts in front of the person: the notification (always), with its actions, and the floating card (on top). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderDeliveryTest {
    private lateinit var context: Context
    private lateinit var notifications: org.robolectric.shadows.ShadowNotificationManager

    private val now = LocalDateTime.of(2026, 11, 4, 9, 0)

    private fun reminder(category: ReminderCategory = ReminderCategory.Payments) = Reminder(
        id = 7,
        title = "Pay HDFC card bill",
        notes = "Account 1234",
        category = category,
        dueAt = now,
        repeatRule = RepeatRule.Once,
        createdAt = now,
        updatedAt = now,
    )

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        (context as NormPixApplication).container.preferences.setNotificationsEnabled(true)
    }

    @AfterTest
    fun tearDown() {
        ReminderOverlay.dismiss()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        ShadowSettings.setCanDrawOverlays(false)
    }

    @Test
    fun theNotificationOffersPaidSnoozeAndOpen_andOpensTheReminderWhenTapped() {
        assertTrue(ReminderNotifications.post(context, reminder()))
        val notification = assertNotNull(notifications.getNotification(7))
        // Short, because three buttons share a heads-up's width and "Mark as paid" was cut to "Mark as p..." on a phone.
        assertEquals(listOf("Paid", "Snooze", "Open"), notification.actions.map { it.title.toString() })
        assertEquals("Pay HDFC card bill", notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertNotNull(notification.contentIntent, "tapping the body opens the reminder")
    }

    @Test
    fun anIncomeReminderSaysReceivedAndAnythingElseSaysDone() {
        ReminderNotifications.post(context, reminder(ReminderCategory.Income))
        assertEquals("Received", assertNotNull(notifications.getNotification(7)).actions.first().title.toString())
        ReminderNotifications.post(context, reminder(ReminderCategory.Personal))
        assertEquals("Done", assertNotNull(notifications.getNotification(7)).actions.first().title.toString())
    }

    @Test
    fun aLockScreenSetToHidePrivateContentShowsNoWordsOfTheReminder() {
        ReminderNotifications.post(context, reminder())
        val notification = assertNotNull(notifications.getNotification(7))
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, notification.visibility)
        val hidden = assertNotNull(notification.publicVersion)
        assertEquals("Reminder", hidden.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertFalse(hidden.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains("1234"))
    }

    @Test
    fun aFullScreenAlertComesWithItOnALockedPhoneWhereAllowed_andNotWhenTheUserSwitchedItOff() {
        val prefs = (context as NormPixApplication).container.preferences
        val keyguard = shadowOf(context.getSystemService(android.app.KeyguardManager::class.java))
        keyguard.setKeyguardLocked(true)
        prefs.setReminderFullScreenEnabled(true)
        ReminderNotifications.post(context, reminder())
        assertNotNull(assertNotNull(notifications.getNotification(7)).fullScreenIntent, "full-screen alert for a locked phone")

        prefs.setReminderFullScreenEnabled(false)
        ReminderNotifications.post(context, reminder())
        assertNull(assertNotNull(notifications.getNotification(7)).fullScreenIntent, "an ordinary notification once switched off")
    }

    @Test
    fun onAnUnlockedLitPhoneItIsAnOrdinaryHeadsUp_withNoFullScreenIntentToMakeItStick() {
        // Regression (seen on an emulator, Android 17): a full-screen intent on a notification that arrives while the phone is in
        // use made its heads-up stay on screen, over the floating card, instead of going in a few seconds.
        val keyguard = shadowOf(context.getSystemService(android.app.KeyguardManager::class.java))
        keyguard.setKeyguardLocked(false)
        (context as NormPixApplication).container.preferences.setReminderFullScreenEnabled(true)
        ReminderNotifications.post(context, reminder())
        assertNull(assertNotNull(notifications.getNotification(7)).fullScreenIntent)
    }

    @Test
    fun withNotificationsSwitchedOffNothingIsPosted() {
        (context as NormPixApplication).container.preferences.setNotificationsEnabled(false)
        assertFalse(ReminderNotifications.post(context, reminder()))
        assertNull(notifications.getNotification(7))
    }

    @Test
    fun theFloatingCardIsNeverAddedWithoutAndroidsPermission() {
        ShadowSettings.setCanDrawOverlays(false)
        ReminderOverlay.show(context, reminder()) {}
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(ReminderOverlay.isShowing)
        assertFalse(Settings.canDrawOverlays(context))
    }

    @Test
    fun withThePermissionTheCardIsAddedAndPutAwayAgain() {
        ShadowSettings.setCanDrawOverlays(true)
        ReminderOverlay.show(context, reminder()) {}
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertTrue(ReminderOverlay.isShowing)
        ReminderOverlay.dismiss()
        org.robolectric.shadows.ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS)
        assertFalse(ReminderOverlay.isShowing)
    }

    @Test
    fun aDueReminderGetsItsNotificationAndTheCardOnTop_onlyWhenEverythingAllowsIt() {
        val prefs = (context as NormPixApplication).container.preferences
        prefs.setReminderOverlayEnabled(true)
        ShadowSettings.setCanDrawOverlays(true)
        ReminderDelivery.deliver(context, reminder())
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNotNull(notifications.getNotification(7), "the notification is always there")
        assertTrue(ReminderOverlay.isShowing, "and the card is on top of it")
    }

    @Test
    fun withTheCardSwitchedOffOrNotificationsOffNoCardAppears_butTheNotificationStaysTheDefault() {
        val prefs = (context as NormPixApplication).container.preferences
        ShadowSettings.setCanDrawOverlays(true)

        prefs.setReminderOverlayEnabled(false)
        ReminderDelivery.deliver(context, reminder())
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertNotNull(notifications.getNotification(7), "off by default: the notification is all there is")
        assertFalse(ReminderOverlay.isShowing)

        prefs.setReminderOverlayEnabled(true)
        prefs.setNotificationsEnabled(false)
        ReminderDelivery.deliver(context, reminder())
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertFalse(ReminderOverlay.isShowing, "nothing alerts while notifications are off")
    }

    @Test
    fun theSampleReminderChangesNothing() {
        // Its buttons are for looking at: Mark as paid on the sample must not reach the stored reminders.
        ReminderDelivery.perform(context, ReminderDelivery.SAMPLE_ID, ReminderCardAction.Done)
        ReminderDelivery.perform(context, ReminderDelivery.SAMPLE_ID, ReminderCardAction.Snooze(java.time.Duration.ofHours(1)))
        assertNull(notifications.getNotification(ReminderDelivery.SAMPLE_ID.toInt()))
    }
}
