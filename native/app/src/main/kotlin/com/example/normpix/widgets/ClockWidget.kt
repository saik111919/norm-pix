package com.example.normpix.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.util.TypedValue
import android.widget.RemoteViews
import com.example.normpix.MainActivity
import com.example.normpix.R
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * The watch dial widget: the time on a face after a watch face - the hour in the middle, the minute in a pill at three o'clock,
 * and round them two rings of five-minute numbers, all upright ([WatchDialFace] says where everything goes).
 *
 * The hour and the minute are TextClocks, which the launcher keeps on time itself. The rings are a picture ([WatchDialRenderer])
 * drawn again once a minute by an alarm that wakes nothing ([WatchDialTicker]: it waits for the phone to be in use), so the inner
 * ring steps with the minute and the numbers stay upright, which a ring Android turns for us can't do. The outer ring is the
 * seconds ring and stands still; the picture is drawn again too when the widget is placed or resized, after a restart and when the
 * clock or its zone changes. A tap opens the phone's clock app.
 */
class ClockWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) = WatchDialTicker.schedule(context)

    override fun onDisabled(context: Context) = WatchDialTicker.cancel(context)

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id)))
        WatchDialTicker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        manager.updateAppWidget(appWidgetId, views(context, newOptions))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WatchDialTicker.ACTION_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> refreshAll(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private const val DEFAULT_DP = 160f

        /** The picture of the rings is drawn at the size the widget needs, between these: sharp when large, never a large bitmap. */
        private const val MIN_RINGS_PX = 320
        private const val MAX_RINGS_PX = 720

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, ClockWidget::class.java)) ?: IntArray(0)

        /** Draws every watch dial on the home screen again for the minute it is, and sets the next minute's alarm - or none if there are none. */
        fun refreshAll(context: Context) {
            val ids = ids(context)
            if (ids.isEmpty()) {
                WatchDialTicker.cancel(context)
                return
            }
            val manager = AppWidgetManager.getInstance(context) ?: return
            for (id in ids) manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id)))
            WatchDialTicker.schedule(context)
        }

        fun views(context: Context, options: Bundle?, minute: Int = WatchDialTicker.minuteNow()): RemoteViews {
            val sizes = sizesOf(options, DEFAULT_DP to DEFAULT_DP)
            // One picture serves every size the launcher may show it at: the largest face decides how sharp it is.
            val rings = rings(context, minute, sizes.maxOf { (width, height) -> ClockFace(width, height).sizeDp })
            return viewsForSizes(options, DEFAULT_DP to DEFAULT_DP) { width, height -> face(context, ClockFace(width, height), rings) }
        }

        /** The widget drawn for a face [sizeDp] wide, as Settings > Widgets shows it. */
        fun preview(context: Context, sizeDp: Float): RemoteViews =
            face(context, ClockFace(sizeDp, sizeDp), rings(context, WatchDialTicker.minuteNow(), sizeDp))

        private fun rings(context: Context, minute: Int, faceDp: Float): Bitmap {
            val px = (faceDp * context.resources.displayMetrics.density).roundToInt().coerceIn(MIN_RINGS_PX, MAX_RINGS_PX)
            return WatchDialRenderer.render(context, minute, px)
        }

        private fun face(context: Context, face: ClockFace, rings: Bitmap): RemoteViews {
            val density = context.resources.displayMetrics.density
            fun Float.px(): Int = (this * density).roundToInt()
            return RemoteViews(context.packageName, R.layout.widget_clock).apply {
                setImageViewBitmap(R.id.widget_clock_rings, rings)
                setTextViewTextSize(R.id.widget_clock_hour, TypedValue.COMPLEX_UNIT_DIP, face.hourTextDp)
                setTextViewTextSize(R.id.widget_clock_minute, TypedValue.COMPLEX_UNIT_DIP, face.minuteTextDp)
                setViewPadding(R.id.widget_clock_hour, 0, face.digitsPaddingDp(face.hourTextDp).px(), 0, 0)
                setViewPadding(R.id.widget_clock_minute, 0, face.digitsPaddingDp(face.minuteTextDp).px(), 0, 0)
                setViewPadding(R.id.widget_clock_minute_box, face.minuteBoxPaddingDp.px(), 0, 0, 0)
                setOnClickPendingIntent(R.id.widget_clock_root, clockApp(context))
            }
        }

        /** The phone's clock app (its alarms), as a clock on the home screen opens; NormPix where there is none. */
        internal fun clockApp(context: Context): PendingIntent {
            val alarms = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val intent = if (alarms.resolveActivity(context.packageManager) != null) {
                alarms
            } else {
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}

/**
 * The once-a-minute alarm that has the watch dial's rings drawn again. It is set for the start of the next minute and sets the
 * next when it goes off, and it wakes nothing: it is an elapsed-time alarm that is not a wake-up one, so while the screen is off
 * the phone sleeps through it, and when the screen comes on it goes off straight away and the rings are right.
 *
 * On the minute only where the phone allows exact alarms (the "Alarms & reminders" switch Android 12 and later has, which the
 * reminders ask for too); otherwise Android delivers it a little late, and the rings are a minute or so behind the pill's text.
 */
object WatchDialTicker {
    /** The alarm's. */
    const val ACTION_TICK = "com.example.normpix.widget.CLOCK_TICK"

    /** A moment past the minute, so the clock has turned by the time it is read. */
    private const val PAST_THE_MINUTE_MS = 150L

    /** How long from [nowMillis] (the clock's, in milliseconds) to just after the next minute begins. */
    fun millisToNextMinute(nowMillis: Long): Long = 60_000L - Math.floorMod(nowMillis, 60_000L) + PAST_THE_MINUTE_MS

    /** The minute to draw the rings for: the one it is, or the one that is just about to begin. */
    fun minuteNow(): Int = LocalTime.now().plusNanos(400_000_000L).minute

    /** Whether the alarm can go off on the minute. */
    fun onTheMinute(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    }

    /** Sets the alarm for the next minute, in place of any that is set. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + millisToNextMinute(System.currentTimeMillis())
        val intent = tick(context)
        val exact = onTheMinute(context) && runCatching { alarms.setExact(AlarmManager.ELAPSED_REALTIME, at, intent) }.isSuccess
        if (!exact) alarms.set(AlarmManager.ELAPSED_REALTIME, at, intent)
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(tick(context))
    }

    private fun tick(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ClockWidget::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
