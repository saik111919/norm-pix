package com.example.normpix.wallet.expiry

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.NormPixApplication
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import java.time.Clock
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The real store, alarm, notification and receivers behind the tracker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidExpiryTest {
    private lateinit var context: Context
    private lateinit var alarms: org.robolectric.shadows.ShadowAlarmManager
    private lateinit var notifications: org.robolectric.shadows.ShadowNotificationManager

    private val zone = ZoneId.systemDefault()
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 10, 6, 14, 0, 0, 0, zone).toInstant(), zone)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        notifications = shadowOf(context.getSystemService(NotificationManager::class.java))
        context.getSharedPreferences("android_expiry_test", Context.MODE_PRIVATE).edit().clear().commit()
        CardExpiryNotifications.createChannel(context)
    }

    private fun tracker() = CardExpiryTracker(
        PrefsCardExpiryStore(context.getSharedPreferences("android_expiry_test", Context.MODE_PRIVATE)),
        AlarmExpiryScheduler(context),
        SystemExpiryNotifier(context),
        clock,
    )

    private fun card(id: String, month: String, year: String, bank: String = "HDFC Bank") = StoredCard(
        id, bank, "Asha Rao", "4111111111114242", month, year, "737", CardKind.Debit, LocalDateTime.of(2026, 1, 1, 0, 0),
    )

    private val soon get() = YearMonth.of(2026, 11)

    @Test
    fun theStoreRoundTripsEveryFieldAndKeepsNoCardNumber() {
        val prefs = context.getSharedPreferences("android_expiry_test", Context.MODE_PRIVATE)
        val store = PrefsCardExpiryStore(prefs)
        val record = CardExpiryRecord("a", "HDFC Bank", "4242", soon, completedFor = soon, remindedAt = 99L)
        store.put(record)

        assertEquals(mapOf("a" to record), PrefsCardExpiryStore(prefs).all(), "a fresh store over the same file reads it back")
        assertTrue(prefs.all.values.none { it.toString().contains("4111") || it.toString().contains("737") })

        store.remove("a")
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun aDamagedRecordIsSkippedNotFatal() {
        val prefs = context.getSharedPreferences("android_expiry_test", Context.MODE_PRIVATE)
        prefs.edit().putString("card:broken", "{not json").putString("unrelated", "x").commit()
        assertTrue(PrefsCardExpiryStore(prefs).all().isEmpty())
    }

    @Test
    fun syncingACardInItsWindowSetsOneAlarmForTomorrowMorning() {
        tracker().sync(listOf(card("a", "11", "26")))
        val alarm = assertNotNull(alarms.peekNextScheduledAlarm())
        assertEquals(ZonedDateTime.of(2026, 10, 7, 9, 0, 0, 0, zone).toInstant().toEpochMilli(), alarm.triggerAtMs)
    }

    @Test
    fun theAlarmDeliversANotificationThatNamesTheCardAndOffersMarkAsDone() {
        val tracker = tracker()
        tracker.sync(listOf(card("a", "11", "26")))
        tracker.onAlarm("a")

        val posted = notifications.allNotifications.single()
        val text = posted.extras.getCharSequence("android.text").toString()
        assertEquals("HDFC Bank •••• 4242 expires 11/26", text)
        assertEquals("Card expiring soon", posted.extras.getCharSequence("android.title").toString())
        assertEquals(listOf("Mark as done"), posted.actions.map { it.title.toString() })
        assertFalse(text.contains("4111"), "never the number")
    }

    @Test
    fun theLockScreenOnlyHintsAtIt() {
        val tracker = tracker()
        tracker.sync(listOf(card("a", "11", "26")))
        tracker.onAlarm("a")

        val posted = notifications.allNotifications.single()
        assertEquals(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE, posted.visibility)
        val publicVersion = assertNotNull(posted.publicVersion)
        assertEquals("Card expiry reminder", publicVersion.extras.getCharSequence("android.title").toString())
        assertNull(publicVersion.extras.getCharSequence("android.text"), "no bank or digits before unlock")
    }

    @Test
    fun anExpiredCardIsToldSo() {
        val tracker = tracker()
        tracker.sync(listOf(card("a", "03", "25")))
        tracker.onAlarm("a")
        val posted = notifications.allNotifications.single()
        assertEquals("Card expired", posted.extras.getCharSequence("android.title").toString())
    }

    @Test
    fun twoCardsGetTwoNotificationsAndMarkingOneDoneWithdrawsOnlyThatOne() {
        val tracker = tracker()
        tracker.sync(listOf(card("a", "11", "26", "HDFC Bank"), card("b", "11", "26", "ICICI Bank")))
        tracker.onAlarm("a")
        tracker.onAlarm("b")
        assertEquals(2, notifications.allNotifications.size)

        tracker.markDone("a")
        val left = notifications.allNotifications.single()
        assertTrue(left.extras.getCharSequence("android.text").toString().startsWith("ICICI Bank"))
    }

    @Test
    fun withTheChannelBlockedNothingIsShownAndTheTrackerKnows() {
        notifications.setNotificationsEnabled(false)
        val tracker = tracker()
        assertFalse(tracker.notificationsEnabled())
        tracker.sync(listOf(card("a", "11", "26")))
        tracker.onAlarm("a")
        assertTrue(notifications.allNotifications.isEmpty())
        assertEquals(CardExpiryPhase.ExpiringSoon, tracker.states.value["a"]!!.phase, "still due: the in-app notice carries it")
    }

    @Test
    fun deletingTheCardCancelsItsAlarmAndNotification() {
        val tracker = tracker()
        tracker.sync(listOf(card("a", "11", "26")))
        tracker.onAlarm("a")
        assertTrue(notifications.allNotifications.isNotEmpty())

        tracker.sync(emptyList())
        assertTrue(notifications.allNotifications.isEmpty())
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun theReceiversDriveTheRealTrackerInTheApp() {
        val app = context as NormPixApplication
        val tracker = app.container.expiry
        val real = card("receiver-card", "11", "26")
        tracker.sync(listOf(real))
        assertEquals(CardExpiryPhase.ExpiringSoon, tracker.states.value["receiver-card"]!!.phase)

        // "Mark as done" on the notification.
        CardExpiryActionReceiver().onReceive(
            context,
            Intent(CardExpiryNotifications.ACTION_DONE).putExtra(CardExpiryNotifications.EXTRA_CARD_ID, "receiver-card"),
        )
        assertEquals(CardExpiryPhase.Completed, tracker.states.value["receiver-card"]!!.phase)

        // A stray alarm for that card changes nothing; one with no card id is ignored.
        CardExpiryAlarmReceiver().onReceive(context, Intent(CardExpiryNotifications.ACTION_ALARM).putExtra(CardExpiryNotifications.EXTRA_CARD_ID, "receiver-card"))
        CardExpiryAlarmReceiver().onReceive(context, Intent(CardExpiryNotifications.ACTION_ALARM))
        assertEquals(CardExpiryPhase.Completed, tracker.states.value["receiver-card"]!!.phase)

        tracker.sync(emptyList())
    }

    @Test
    fun theBootReceiverOnlyReactsToTheSystemBroadcastsItAsksFor() {
        val app = context as NormPixApplication
        val tracker = app.container.expiry
        tracker.sync(listOf(card("boot-card", "11", "26")))
        AlarmExpiryScheduler(context).cancel("boot-card") // a reboot forgets every alarm
        assertNull(alarms.peekNextScheduledAlarm())

        CardExpiryBootReceiver().onReceive(context, Intent("com.example.not.a.system.broadcast"))
        assertNull(alarms.peekNextScheduledAlarm(), "a spoofed action does nothing")

        CardExpiryBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNotNull(alarms.peekNextScheduledAlarm(), "a reboot re-arms the alarms")

        tracker.sync(emptyList())
    }
}
