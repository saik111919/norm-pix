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
 * The hour and the minute are TextClocks, which the launcher keeps on time itself. The face and the inner (minutes) ring are a
 * picture ([WatchDialRenderer]) drawn again by an alarm that wakes nothing ([WatchDialTicker]: it waits for the phone to be in
 * use), so the inner ring steps with the minute and its numbers stay upright, which a ring Android turns for us can't do. The
 * outer ring is the seconds ring and does turn: an animation the launcher runs over the picture (`widget_dial_seconds_NN`, one for
 * each second it may start at), its numbers turning against it so they stay upright too. An animation can't be told where to
 * start, so the widget is given the one for the second it is each time it is drawn - every few seconds, see [WatchDialTicker] -
 * and the ring keeps time on its own between.
 *
 * The picture is drawn too when the widget is placed or resized, after a restart and when the clock or its zone changes. A tap
 * opens the phone's clock app. What the user chose in Widget settings ([ClockOptions]) applies: a transparent dial has no face,
 * and a dial too small to read is the plain digital clock.
 */
class ClockWidget : AppWidgetProvider() {
    override fun onEnabled(context: Context) = WatchDialTicker.schedule(context)

    override fun onDisabled(context: Context) = WatchDialTicker.cancel(context)

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val store = ClockOptionsStore(context)
        for (id in appWidgetIds) manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id), clock = store.get(id)))
        WatchDialTicker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        manager.updateAppWidget(appWidgetId, views(context, newOptions, clock = ClockOptionsStore(context).get(appWidgetId)))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = ClockOptionsStore(context).remove(appWidgetIds)

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
        private const val DEFAULT_DP = ClockWidgets.DEFAULT_DP

        /** The picture of the rings is drawn at the size the widget needs, between these: sharp when large, never a large bitmap. */
        private const val MIN_RINGS_PX = 320
        private const val MAX_RINGS_PX = 720

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, ClockWidget::class.java)) ?: IntArray(0)

        /** Draws every watch dial on the home screen again for the minute and second it is, and sets the next alarm - or none if there are none. */
        fun refreshAll(context: Context) {
            val ids = ids(context)
            if (ids.isEmpty()) {
                WatchDialTicker.cancel(context)
                return
            }
            val manager = AppWidgetManager.getInstance(context) ?: return
            val store = ClockOptionsStore(context)
            for (id in ids) manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id), clock = store.get(id)))
            WatchDialTicker.schedule(context)
        }

        /**
         * The watch dial for the sizes in [options], at [minute] and [second] (what the rings are drawn for), with the user's [clock]
         * choices. A size too small for the face, or a clock set to be digital, is the plain digital clock in its place.
         */
        fun views(
            context: Context,
            options: Bundle?,
            minute: Int = WatchDialTicker.minuteNow(),
            clock: ClockOptions = ClockOptions(),
            second: Int = WatchDialTicker.secondNow(),
        ): RemoteViews {
            val sizes = sizesOf(options, DEFAULT_DP to DEFAULT_DP)
            // One picture serves every size the launcher may show it at: the largest face decides how sharp it is.
            val faceDp = sizes.filter { (width, height) -> !clock.showsDigital(width, height) }
                .maxOfOrNull { (width, height) -> ClockFace(width, height).sizeDp }
            val rings = faceDp?.let { rings(context, minute, it, clock.transparent) }
            return viewsForSizes(options, DEFAULT_DP to DEFAULT_DP) { width, height ->
                if (rings == null || clock.showsDigital(width, height)) {
                    SimpleClock.views(context, width, height, clock)
                } else {
                    face(context, ClockFace(width, height), rings, second)
                }
            }
        }

        /** The widget drawn for a widget [widthDp] x [heightDp], as Settings > Widgets and Widget settings show it. */
        fun preview(context: Context, widthDp: Float, heightDp: Float = widthDp, clock: ClockOptions = ClockOptions()): RemoteViews =
            if (clock.showsDigital(widthDp, heightDp)) {
                SimpleClock.views(context, widthDp, heightDp, clock)
            } else {
                val face = ClockFace(widthDp, heightDp)
                face(context, face, rings(context, WatchDialTicker.minuteNow(), face.sizeDp, clock.transparent), WatchDialTicker.secondNow())
            }

        private var lastRings: Triple<Triple<Int, Int, Boolean>, Float, Bitmap>? = null

        private fun rings(context: Context, minute: Int, faceDp: Float, transparent: Boolean): Bitmap {
            val px = (faceDp * context.resources.displayMetrics.density).roundToInt().coerceIn(MIN_RINGS_PX, MAX_RINGS_PX)
            // Drawn again every few seconds for the seconds ring, but it only changes with the minute.
            val key = Triple(minute, px, transparent)
            val density = context.resources.displayMetrics.density
            lastRings?.let { (was, wasDensity, bitmap) -> if (was == key && wasDensity == density && !bitmap.isRecycled) return bitmap }
            return WatchDialRenderer.render(context, minute, px, transparent).also { lastRings = Triple(key, density, it) }
        }

        private fun face(context: Context, face: ClockFace, rings: Bitmap, second: Int): RemoteViews {
            val density = context.resources.displayMetrics.density
            fun Float.px(): Int = (this * density).roundToInt()
            return RemoteViews(context.packageName, R.layout.widget_clock).apply {
                setImageViewBitmap(R.id.widget_clock_rings, rings)
                // The seconds ring: a view that turns by itself, started at this second.
                removeAllViews(R.id.widget_clock_seconds)
                addView(R.id.widget_clock_seconds, RemoteViews(context.packageName, WatchDialSecondsLayouts[Math.floorMod(second, 60)]))
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
 * The alarm that has the watch dial drawn again every few seconds: for the minute ring, which steps on the minute, and for the
 * seconds ring, which turns by itself but must be started at the right second (it starts over whenever the launcher shows the
 * widget again, so how far it can be out is how long since it was last drawn). It is set for the next quarter of a minute and
 * sets the next when it goes off, and it wakes nothing: it is an elapsed-time alarm that is not a wake-up one, so while the
 * screen is off the phone sleeps through it, and when the screen comes on it goes off straight away and the rings are right.
 *
 * On the quarter only where the phone allows exact alarms (the "Alarms & reminders" switch Android 12 and later has, which the
 * reminders ask for too); otherwise Android delivers it a little late, and the rings are a minute or so behind the pill's text.
 */
object WatchDialTicker {
    /** The alarm's. */
    const val ACTION_TICK = "com.example.normpix.widget.CLOCK_TICK"

    /** How often the dial is drawn again, in milliseconds: the seconds ring is at most this far out after the launcher shows it again. */
    const val TICK_MS = 15_000L

    /** A moment past the tick, so the clock has turned by the time it is read. */
    private const val PAST_THE_TICK_MS = 150L

    /** How long a view takes to get from here to the screen, which the seconds ring is started ahead by. */
    private const val SECONDS_LATENCY_NS = 300_000_000L

    /** How long from [nowMillis] (the clock's, in milliseconds) to just after the next minute begins. */
    fun millisToNextMinute(nowMillis: Long): Long = 60_000L - Math.floorMod(nowMillis, 60_000L) + PAST_THE_TICK_MS

    /** How long from [nowMillis] to just after the next quarter of a minute (:00, :15, :30, :45). */
    fun millisToNextTick(nowMillis: Long): Long = TICK_MS - Math.floorMod(nowMillis, TICK_MS) + PAST_THE_TICK_MS

    /** The minute to draw the rings for: the one it is, or the one that is just about to begin. */
    fun minuteNow(): Int = LocalTime.now().plusNanos(400_000_000L).minute

    /** The second the seconds ring is to start at: the one it is by the time the view is on screen. */
    fun secondNow(): Int = LocalTime.now().plusNanos(SECONDS_LATENCY_NS).second

    /** Whether the alarm can go off on the quarter. */
    fun onTheMinute(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    }

    /** Sets the alarm for the next quarter of a minute, in place of any that is set. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + millisToNextTick(System.currentTimeMillis())
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
