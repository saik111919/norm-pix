package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import com.example.normpix.R
import kotlin.math.roundToInt

/**
 * The cookie clocks: the time on a face in the shape of Material 3 Expressive's four-sided cookie, in three styles that are three
 * widgets in the picker ([CookieAnalogWidget], [CookieNumbersWidget], [CookieDigitalWidget]) and three pills in Settings >
 * Widgets.
 *
 * Like the watch dial they keep time without NormPix: the analog ones are an AnalogClock turning hands over a drawn face, the
 * digital one TextClocks the launcher keeps on time itself. So no widget asks to be woken (no update period, no alarms); each
 * is laid out when it is placed and when it is resized, and a tap opens the phone's clock app.
 */
abstract class CookieClockWidget : AppWidgetProvider() {
    /** The widget as it is laid out for [options], the launcher's say of its size. */
    protected abstract fun views(context: Context, options: Bundle?): RemoteViews

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id)))
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        manager.updateAppWidget(appWidgetId, views(context, newOptions))
    }

    companion object {
        private const val DEFAULT_DP = 160f

        /** The analog clock without numbers: it fills whatever size it is, so it needs nothing laid out for one. */
        fun analog(context: Context): RemoteViews = analogClock(context, R.layout.widget_cookie_analog)

        /** The analog clock with the hours numbered. */
        fun numbers(context: Context): RemoteViews = analogClock(context, R.layout.widget_cookie_numbers)

        /** The digital clock for the size the launcher says ([options]); null before it has said. */
        fun digital(context: Context, options: Bundle?): RemoteViews =
            viewsForSizes(options, DEFAULT_DP to DEFAULT_DP) { width, height -> digital(context, CookieFace(width, height)) }

        /** The digital clock for a face [sizeDp] wide, as Settings > Widgets shows it. */
        fun digitalPreview(context: Context, sizeDp: Float): RemoteViews = digital(context, CookieFace(sizeDp, sizeDp))

        private fun analogClock(context: Context, @LayoutRes layout: Int): RemoteViews =
            RemoteViews(context.packageName, layout).apply {
                setOnClickPendingIntent(R.id.widget_cookie_root, ClockWidget.clockApp(context))
            }

        private fun digital(context: Context, face: CookieFace): RemoteViews {
            val density = context.resources.displayMetrics.density
            return RemoteViews(context.packageName, R.layout.widget_cookie_digital).apply {
                place(R.id.widget_cookie_hour, face.hour, density)
                place(R.id.widget_cookie_minute, face.minute, density)
                place(R.id.widget_cookie_date, face.date, density)
                setOnClickPendingIntent(R.id.widget_cookie_root, ClockWidget.clockApp(context))
            }
        }

        private fun RemoteViews.place(@IdRes id: Int, text: CookieFace.FaceText, density: Float) {
            setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_DIP, text.sizeDp)
            setViewPadding(id, 0, (text.topPaddingDp * density).roundToInt(), 0, (text.bottomPaddingDp * density).roundToInt())
        }
    }
}

/** The cookie clock with hands and ticks, no numbers. */
class CookieAnalogWidget : CookieClockWidget() {
    override fun views(context: Context, options: Bundle?): RemoteViews = analog(context)
}

/** The cookie clock with hands, ticks and the hours numbered. */
class CookieNumbersWidget : CookieClockWidget() {
    override fun views(context: Context, options: Bundle?): RemoteViews = numbers(context)
}

/** The cookie clock in digits: the hour over the minute, and the date. */
class CookieDigitalWidget : CookieClockWidget() {
    override fun views(context: Context, options: Bundle?): RemoteViews = digital(context, options)
}
