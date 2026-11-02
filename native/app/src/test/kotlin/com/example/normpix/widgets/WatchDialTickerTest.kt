package com.example.normpix.widgets

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.ImageView
import java.time.Duration
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
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_200)) // the hold until the second's edge
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

    @Test
    fun `however late the alarm came, a tick reaches the screen on a second's edge`() {
        // 12:34:55 on some day, in the phone's own zone: a tick (a multiple of five seconds) and alarms delivered after it.
        val tick = java.time.LocalDateTime.of(2026, 11, 1, 12, 34, 55).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val latency = WatchDialTicker.SCREEN_LATENCY_MS
        for (late in listOf(0L, 150L, 300L, 450L, 600L, 601L, 900L, 3_000L, 4_999L)) {
            val now = tick + late
            val edge = WatchDialTicker.nextSecondEdge(now)
            assertEquals(0L, Math.floorMod(edge, 1_000L), "$late ms late: a whole second")
            val hold = WatchDialTicker.holdBefore(edge, now)
            // Sent after the hold, it is on screen on the edge: the ring is started at that second as it reaches it, so it is put
            // back where it already was instead of a fraction of a second away (the jump every five seconds).
            assertEquals(edge, now + hold + latency, "$late ms late")
            assertTrue(hold in 100L..1_100L, "$late ms late: held $hold ms, about a second at most")
        }
        // The dial is drawn for the edge's own minute and second, so the two agree across a minute's edge.
        val edge = WatchDialTicker.nextSecondEdge(tick + 4_700)
        assertEquals(35, WatchDialTicker.timeAt(edge).minute)
        assertEquals(1, WatchDialTicker.timeAt(edge).second)
    }

    @Test
    fun `a dial sent at once starts its ring at the second nearest to when it is on screen`() {
        val tick = java.time.LocalDateTime.of(2026, 11, 1, 12, 34, 55).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        // On screen at :58.3: the ring starts at 58.
        assertEquals(58, WatchDialTicker.secondNow(tick + 3_000))
        // On screen at :59.1: the ring starts at 59, not a second behind.
        assertEquals(59, WatchDialTicker.secondNow(tick + 3_800))
        // And a minute's edge is crossed with the second: never a minute and a second from two different moments.
        val edge = java.time.LocalDateTime.of(2026, 11, 1, 12, 34, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() + 800
        assertEquals(35, WatchDialTicker.minuteNow(edge))
        assertEquals(0, WatchDialTicker.secondNow(edge))
    }

    @Test
    fun `a tick is held, drawn ahead, and sent for the edge`() {
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val id = manager.createWidget(ClockWidget::class.java, R.layout.widget_clock)
        WatchDialTicker.cancel(context)
        ClockWidget().onReceive(context, Intent(WatchDialTicker.ACTION_TICK))
        assertTrue(alarms.scheduledAlarms.isEmpty(), "nothing is sent, and no alarm set, before the hold is over")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_200))
        assertNotNull(manager.getViewFor(id).findViewById<ImageView>(R.id.widget_clock_rings).drawable)
        assertEquals(1, alarms.scheduledAlarms.size, "then the next tick's alarm is set")
    }
}
