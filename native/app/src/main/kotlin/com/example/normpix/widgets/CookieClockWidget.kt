package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import com.example.normpix.R
import kotlin.math.roundToInt

/**
 * The cookie clocks: the time on a face in the shape of Material 3 Expressive's four-sided cookie, in four styles that are four
 * widgets in the picker ([CookieAnalogWidget], [CookieNumbersWidget], [CookiePlainWidget], [CookieDigitalWidget]) and four pills
 * in Settings > Widgets.
 *
 * Like the watch dial they keep time without NormPix: the analog ones are an AnalogClock turning hands over a drawn face, the
 * digital one TextClocks the launcher keeps on time itself. So no widget asks to be woken (no update period, no alarms); each
 * is laid out when it is placed, when it is resized and when its Widget settings change ([ClockOptions]), and a tap opens the
 * phone's clock app.
 *
 * A face that has become too small to read (a widget of one cell) is the plain digital clock instead ([SimpleClock]), and the
 * user can have that for good. "Transparent" leaves the cookie out, so the marks and hands sit on the wallpaper.
 */
abstract class CookieClockWidget : AppWidgetProvider() {
    protected abstract val widget: HomeWidget

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val store = ClockOptionsStore(context)
        for (id in appWidgetIds) manager.updateAppWidget(id, views(context, widget, manager.getAppWidgetOptions(id), store.get(id)))
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        manager.updateAppWidget(appWidgetId, views(context, widget, newOptions, ClockOptionsStore(context).get(appWidgetId)))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = ClockOptionsStore(context).remove(appWidgetIds)

    companion object {
        private const val DEFAULT_DP = ClockWidgets.DEFAULT_DP

        /** The clock [widget] for the sizes the launcher says ([options]; null before it has said) and what the user chose. */
        fun views(context: Context, widget: HomeWidget, options: Bundle?, clock: ClockOptions = ClockOptions()): RemoteViews =
            viewsForSizes(options, DEFAULT_DP to DEFAULT_DP) { width, height -> one(context, widget, width, height, clock) }

        /** [widget] for a widget [widthDp] x [heightDp], as Settings > Widgets shows it. */
        fun preview(context: Context, widget: HomeWidget, widthDp: Float, heightDp: Float, clock: ClockOptions = ClockOptions()): RemoteViews =
            one(context, widget, widthDp, heightDp, clock)

        private fun one(context: Context, widget: HomeWidget, widthDp: Float, heightDp: Float, clock: ClockOptions): RemoteViews =
            if (clock.showsDigital(widthDp, heightDp)) {
                SimpleClock.views(context, widthDp, heightDp, clock)
            } else {
                when (widget) {
                    HomeWidget.CookieAnalog -> analog(context, clock)
                    HomeWidget.CookieNumbers -> numbers(context, clock)
                    HomeWidget.CookiePlain -> plain(context, clock)
                    HomeWidget.CookieDigital -> digital(context, CookieFace(widthDp, heightDp), clock)
                    else -> error("$widget is not a cookie clock")
                }
            }

        /** The analog clock with ticks and no numbers: it fills whatever size it is, so it needs nothing laid out for one. */
        fun analog(context: Context, clock: ClockOptions = ClockOptions()): RemoteViews = analogClock(context, R.layout.widget_cookie_analog, clock)

        /** The analog clock with the hours numbered. */
        fun numbers(context: Context, clock: ClockOptions = ClockOptions()): RemoteViews = analogClock(context, R.layout.widget_cookie_numbers, clock)

        /** The analog clock with only its hands: no ticks, no numbers. */
        fun plain(context: Context, clock: ClockOptions = ClockOptions()): RemoteViews = analogClock(context, R.layout.widget_cookie_plain, clock)

        /** The digital clock for the size the launcher says ([options]); null before it has said. */
        fun digital(context: Context, options: Bundle?, clock: ClockOptions = ClockOptions()): RemoteViews =
            viewsForSizes(options, DEFAULT_DP to DEFAULT_DP) { width, height -> digital(context, CookieFace(width, height), clock) }

        /** The digital clock for a face [sizeDp] wide, as Settings > Widgets shows it. */
        fun digitalPreview(context: Context, sizeDp: Float, clock: ClockOptions = ClockOptions()): RemoteViews =
            digital(context, CookieFace(sizeDp, sizeDp), clock)

        private fun analogClock(context: Context, @LayoutRes layout: Int, clock: ClockOptions): RemoteViews =
            RemoteViews(context.packageName, layout).apply {
                if (clock.transparent) setViewVisibility(R.id.widget_cookie_fill, View.GONE)
                setOnClickPendingIntent(R.id.widget_cookie_root, ClockWidget.clockApp(context))
            }

        private fun digital(context: Context, face: CookieFace, clock: ClockOptions): RemoteViews {
            val density = context.resources.displayMetrics.density
            return RemoteViews(context.packageName, R.layout.widget_cookie_digital).apply {
                if (clock.transparent) setViewVisibility(R.id.widget_cookie_face, View.GONE)
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
    override val widget get() = HomeWidget.CookieAnalog
}

/** The cookie clock with hands, ticks and the hours numbered. */
class CookieNumbersWidget : CookieClockWidget() {
    override val widget get() = HomeWidget.CookieNumbers
}

/** The cookie clock with only its hands: no ticks and no numbers. */
class CookiePlainWidget : CookieClockWidget() {
    override val widget get() = HomeWidget.CookiePlain
}

/** The cookie clock in digits: the hour over the minute, and the date. */
class CookieDigitalWidget : CookieClockWidget() {
    override val widget get() = HomeWidget.CookieDigital
}
