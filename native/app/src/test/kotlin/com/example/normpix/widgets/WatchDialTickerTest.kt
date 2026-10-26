package com.example.normpix.widgets

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The watch dial's rings are drawn again once a minute by an alarm that wakes nothing: set for the start of the next minute, set
 * again by each one that goes off, and cancelled with the last widget.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WatchDialTickerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    @Test
    fun `the alarm is for just after the next minute begins`() {
        assertEquals(60_150L, WatchDialTicker.millisToNextMinute(0))
        assertEquals(30_150L, WatchDialTicker.millisToNextMinute(30_000))
        assertEquals(151L, WatchDialTicker.millisToNextMinute(59_999))
        assertEquals(60_150L, WatchDialTicker.millisToNextMinute(60_000))
        // A clock before 1970 still counts to its next minute.
        assertEquals(150L + 40_000L, WatchDialTicker.millisToNextMinute(-100_000))
    }

    @Test
    fun `it is an elapsed-time alarm that wakes nothing, with or without the exact alarms switch`() {
        for (exact in listOf(true, false)) {
            ShadowAlarmManager.setCanScheduleExactAlarms(exact)
            WatchDialTicker.cancel(context)
            WatchDialTicker.schedule(context)
            val alarm = assertNotNull(alarms.peekNextScheduledAlarm(), "an alarm is set (exact: $exact)")
            assertEquals(AlarmManager.ELAPSED_REALTIME, alarm.type, "not a wake-up alarm (exact: $exact)")
            val window = if (exact) ShadowAlarmManager.WINDOW_EXACT else ShadowAlarmManager.WINDOW_HEURISTIC
            assertEquals(window, alarm.windowLengthMs, "exact only where the phone allows it")
            assertEquals(exact, WatchDialTicker.onTheMinute(context))
        }
    }

    @Test
    fun `scheduling again replaces the alarm rather than adding one, and cancelling clears it`() {
        WatchDialTicker.schedule(context)
        WatchDialTicker.schedule(context)
        assertEquals(1, alarms.scheduledAlarms.size)
        WatchDialTicker.cancel(context)
        assertNull(alarms.peekNextScheduledAlarm())
    }

    @Test
    fun `an alarm that goes off draws the widgets again and sets the next`() {
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val id = manager.createWidget(ClockWidget::class.java, R.layout.widget_clock)
        ClockWidget().onReceive(context, Intent(WatchDialTicker.ACTION_TICK))
        val rings = manager.getViewFor(id).findViewById<ImageView>(R.id.widget_clock_rings)
        assertNotNull(rings.drawable, "the rings were drawn")
        assertEquals(1, alarms.scheduledAlarms.size, "the next minute's alarm is set")
    }

    @Test
    fun `with no widget left an alarm that goes off sets nothing`() {
        WatchDialTicker.schedule(context)
        ClockWidget().onReceive(context, Intent(WatchDialTicker.ACTION_TICK))
        assertTrue(alarms.scheduledAlarms.isEmpty(), "it is cancelled, not set again")
    }

    @Test
    fun `a restart or a change of clock draws the widgets again too`() {
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        manager.createWidget(ClockWidget::class.java, R.layout.widget_clock)
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            WatchDialTicker.cancel(context)
            ClockWidget().onReceive(context, Intent(action))
            assertEquals(1, alarms.scheduledAlarms.size, action)
        }
    }
}
