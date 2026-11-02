package com.example.normpix.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * use), so the inner ring moves a little at every tick (it is drawn at the minute and the seconds into it) and its numbers stay
 * upright, which a ring Android turns for us can't do. The
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
            WatchDialTicker.ACTION_TICK -> refreshAllOnTheSecond(context, goAsync())
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

        /** The margin between the face and the widget's edge, on every side (see [faceIn]). */
        private const val INSET_DP = 6f

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
         * The tick: every watch dial drawn for the next second's edge it can make and held until it will reach the screen on it (see
         * [WatchDialTicker.nextSecondEdge]), then sent, and the next alarm set. [pending] is the alarm's broadcast, kept open until
         * then (null when called directly, as a test does).
         */
        fun refreshAllOnTheSecond(context: Context, pending: BroadcastReceiver.PendingResult?) {
            val ids = ids(context)
            val manager = AppWidgetManager.getInstance(context)
            if (ids.isEmpty() || manager == null) {
                if (ids.isEmpty()) WatchDialTicker.cancel(context)
                pending?.finish()
                return
            }
            val store = ClockOptionsStore(context)
            val edge = WatchDialTicker.nextSecondEdge(System.currentTimeMillis())
            val time = WatchDialTicker.timeAt(edge)
            val drawn = ids.map { id -> id to views(context, manager.getAppWidgetOptions(id), time.minute, store.get(id), time.second) }
            val send = Runnable {
                try {
                    for ((id, views) in drawn) manager.updateAppWidget(id, views)
                    WatchDialTicker.schedule(context)
                } finally {
                    pending?.finish()
                }
            }
            Handler(Looper.getMainLooper()).postDelayed(send, WatchDialTicker.holdBefore(edge, System.currentTimeMillis()))
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
            /** The seconds ring as an AnalogClock's hand the system turns (Android 12 and later), else the animated ring. */
            bezel: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        ): RemoteViews {
            val sizes = sizesOf(options, DEFAULT_DP to DEFAULT_DP)
            // One picture serves every size the launcher may show it at: the largest face decides how sharp it is.
            val faceDp = sizes.filter { (width, height) -> !clock.showsDigital(width, height) }
                .maxOfOrNull { (width, height) -> faceIn(width, height).sizeDp }
            val rings = faceDp?.let { rings(context, minute, second, it, clock.transparent, clock.backdropAlpha) }
            return viewsForSizes(options, DEFAULT_DP to DEFAULT_DP) { width, height ->
                if (rings == null || clock.showsDigital(width, height)) {
                    SimpleClock.views(context, width, height, clock)
                } else {
                    face(context, faceIn(width, height), rings, second, clock, bezel)
                }
            }
        }

        /** The widget drawn for a widget [widthDp] x [heightDp], as Settings > Widgets and Widget settings show it. */
        fun preview(context: Context, widthDp: Float, heightDp: Float = widthDp, clock: ClockOptions = ClockOptions()): RemoteViews =
            if (clock.showsDigital(widthDp, heightDp)) {
                SimpleClock.views(context, widthDp, heightDp, clock)
            } else {
                val face = faceIn(widthDp, heightDp)
                WatchDialTicker.minuteNow().let { minute -> WatchDialTicker.secondNow().let { second -> face(context, face, rings(context, minute, second, face.sizeDp, clock.transparent, clock.backdropAlpha), second, clock) } }
            }

        /**
         * The face for a widget [widthDp] x [heightDp]: the largest circle that fits inside a margin of [INSET_DP] all round. Flush
         * with the widget's edge the ring's outermost ticks touched the launcher's cell and, beside icons spaced by their own padding,
         * the dial looked packed against its neighbours; the margin is the face's own, so the picture and the text shrink together.
         */
        private fun faceIn(widthDp: Float, heightDp: Float) = ClockFace(widthDp - 2 * INSET_DP, heightDp - 2 * INSET_DP)

        /** What the rings picture is drawn for: it is drawn again only when one of these changes. */
        private data class RingsKey(val minute: Int, val second: Int, val px: Int, val transparent: Boolean, val backdropAlpha: Int)

        private var lastRings: Triple<RingsKey, Float, Bitmap>? = null

        private fun rings(context: Context, minute: Int, second: Int, faceDp: Float, transparent: Boolean, backdropAlpha: Int): Bitmap {
            val px = (faceDp * context.resources.displayMetrics.density).roundToInt().coerceIn(MIN_RINGS_PX, MAX_RINGS_PX)
            // Drawn again at every tick: the minutes ring is at the minute and the seconds into it, so it creeps round.
            val key = RingsKey(minute, second, px, transparent, backdropAlpha)
            val density = context.resources.displayMetrics.density
            lastRings?.let { (was, wasDensity, bitmap) -> if (was == key && wasDensity == density && !bitmap.isRecycled) return bitmap }
            return WatchDialRenderer.render(context, minute, px, transparent, backdropAlpha, second).also { lastRings = Triple(key, density, it) }
        }

        private fun face(
            context: Context,
            face: ClockFace,
            rings: Bitmap,
            second: Int,
            clock: ClockOptions,
            bezel: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        ): RemoteViews {
            val density = context.resources.displayMetrics.density
            fun Float.px(): Int = (this * density).roundToInt()
            return RemoteViews(context.packageName, R.layout.widget_clock).apply {
                setViewPadding(R.id.widget_clock_root, INSET_DP.px(), INSET_DP.px(), INSET_DP.px(), INSET_DP.px())
                setImageViewBitmap(R.id.widget_clock_rings, rings)
                // The seconds ring. From Android 12 a bezel the system turns by the real time: never out, never nudged, a step a
                // second (owner's choice, 2026-11-01). Before that, a view that turns by itself, started at this second.
                removeAllViews(R.id.widget_clock_seconds)
                val ring = if (bezel) R.layout.widget_dial_bezel else WatchDialSecondsLayouts[Math.floorMod(second, 60)]
                addView(R.id.widget_clock_seconds, RemoteViews(context.packageName, ring))
                setTextViewTextSize(R.id.widget_clock_hour, TypedValue.COMPLEX_UNIT_DIP, face.hourTextDp)
                setTextViewTextSize(R.id.widget_clock_minute, TypedValue.COMPLEX_UNIT_DIP, face.minuteTextDp)
                setTextViewTextSize(R.id.widget_clock_second, TypedValue.COMPLEX_UNIT_DIP, face.secondTextDp)
                setViewPadding(R.id.widget_clock_hour, 0, face.digitsPaddingDp(face.hourTextDp).px(), 0, 0)
                setViewPadding(R.id.widget_clock_minute, 0, face.digitsPaddingDp(face.minuteTextDp).px(), 0, 0)
                setViewPadding(R.id.widget_clock_second, 0, face.digitsPaddingDp(face.secondTextDp).px(), 0, 0)
                setViewPadding(R.id.widget_clock_minute_box, face.minuteBoxPaddingDp.px(), 0, 0, 0)
                setViewPadding(R.id.widget_clock_second_box, face.secondBoxPaddingDp.px(), 0, 0, 0)
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
 * widget again, so how far it can be out is how long since it was last drawn). It is set for the next [WatchDialTicker.TICK_MS]
 * on the clock and sets the next when it goes off, and it wakes nothing: it is an elapsed-time alarm that is not a wake-up one,
 * so while the screen is off the phone sleeps through it, and when the screen comes on it goes off straight away and the rings
 * are right.
 *
 * On the tick only where the phone allows exact alarms: the manifest asks for `USE_EXACT_ALARM`, which Android 13 and later
 * grants an app with an alarm-clock-like job without a switch (the "Alarms & reminders" one, `SCHEDULE_EXACT_ALARM`, is how
 * Android 12 lets it be asked for); otherwise Android delivers it a little late, and the rings are behind the pill's text.
 */
object WatchDialTicker {
    /** The alarm's. */
    const val ACTION_TICK = "com.example.normpix.widget.CLOCK_TICK"

    /**
     * How often the dial is drawn again, in milliseconds: the seconds ring is at most this far out after the launcher shows it
     * again. When the launcher comes back to the front it applies the last picture it was given, which starts the ring's animation
     * over at the second that picture was drawn for - so after leaving the home screen for an app and coming back, the ring is out by
     * however long ago the dial was last drawn (about 25 s on the phone, with the alarm inexact and a 15 s tick; the pointer showed :31
     * at :55). Five seconds keeps that jump small; it wakes nothing, and drawing the dial again is cheap.
     */
    const val TICK_MS = 5_000L

    /** A moment past the tick, so the clock has turned by the time it is read. */
    private const val PAST_THE_TICK_MS = 150L

    /** How long a view takes to get from here to the screen, which the seconds ring is started ahead by. */
    const val SCREEN_LATENCY_MS = 300L

    /** Time kept for drawing the dials before the moment they are sent at (a dial takes a few milliseconds; a slow phone more). */
    private const val DRAW_MARGIN_MS = 100L

    /** How long from [nowMillis] (the clock's, in milliseconds) to just after the next minute begins. */
    fun millisToNextMinute(nowMillis: Long): Long = 60_000L - Math.floorMod(nowMillis, 60_000L) + PAST_THE_TICK_MS

    /** How long from [nowMillis] to just after the next tick (every [TICK_MS] on the clock: :00, :05, :10 ...). */
    fun millisToNextTick(nowMillis: Long): Long = TICK_MS - Math.floorMod(nowMillis, TICK_MS) + PAST_THE_TICK_MS

    /*
     * Why the ticks are sent on a second's edge. The seconds ring turns smoothly, and each tick starts it again at a whole second
     * (one animation per second it may start at): wherever the ring was, it is put at that second's place at the moment the launcher
     * shows the new picture. So how far it jumps at a tick is how far that moment is from the second's edge - and with the picture
     * sent the moment the alarm came, that was the alarm's lateness plus the time to draw the dial, which differ from tick to tick:
     * a ring started at :55 shown at :55.4 one time and :55.9 the next jumps 3 degrees back, every five seconds (the owner saw it on
     * the phone, 2026-11-01; an earlier rule that started an on-time tick at the tick's own second only narrowed it, and a late alarm
     * still started it a whole second out). Now a tick draws its dials for the next whole second it can make ([nextSecondEdge]) and
     * holds them until [SCREEN_LATENCY_MS] before it ([holdBefore]), so every restart reaches the screen on an edge however late the
     * alarm was and however long the drawing took: the ring is put back where it already was, give or take the few hundredths of a
     * second the launcher varies by.
     */

    /** The whole second a tick's dials are drawn for: the first one far enough ahead to draw them and have them on screen on it. */
    fun nextSecondEdge(nowMillis: Long): Long = ceilToSecond(nowMillis + SCREEN_LATENCY_MS + DRAW_MARGIN_MS)

    /** How long to hold dials drawn for [edge] before sending them, at [nowMillis], so they are on screen on it (none if it is late). */
    fun holdBefore(edge: Long, nowMillis: Long): Long = (edge - SCREEN_LATENCY_MS - nowMillis).coerceAtLeast(0L)

    /**
     * The moment a dial sent at once is on screen at, to the nearest second (a widget placed or resized, a change of clock, the
     * picker's preview): [SCREEN_LATENCY_MS] on, so the ring starts at most half a second out, and the next tick puts it right.
     */
    fun dialMoment(nowMillis: Long): Long = roundToSecond(nowMillis + SCREEN_LATENCY_MS)

    private fun ceilToSecond(millis: Long): Long = -Math.floorDiv(-millis, 1_000L) * 1_000L

    private fun roundToSecond(millis: Long): Long = Math.floorDiv(millis + 500L, 1_000L) * 1_000L

    /** The time of day at [millis] on the clock, in the phone's zone. */
    fun timeAt(millis: Long): LocalTime = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()

    /** The minute to draw the rings for (see [dialMoment]): the same moment as [secondNow], so the two agree across a minute's edge. */
    fun minuteNow(nowMillis: Long = System.currentTimeMillis()): Int = timeAt(dialMoment(nowMillis)).minute

    /** The second the seconds ring is to start at (see [dialMoment]). */
    fun secondNow(nowMillis: Long = System.currentTimeMillis()): Int = timeAt(dialMoment(nowMillis)).second

    /** Whether the alarm can go off on the quarter. */
    fun onTheMinute(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    }

    /** Sets the alarm for the next tick, in place of any that is set. */
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
