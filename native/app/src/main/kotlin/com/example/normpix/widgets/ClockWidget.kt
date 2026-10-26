package com.example.normpix.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.util.TypedValue
import android.widget.RemoteViews
import com.example.normpix.MainActivity
import com.example.normpix.R
import kotlin.math.roundToInt

/**
 * The clock widget: the time on a face of turning rings, after a watch face - the hour in the middle, the minute in a pill
 * at three o'clock, the minute ring turning under it and (Android 12+) a seconds ring round it.
 *
 * It keeps time without NormPix: the rings are an AnalogClock's hands and the numbers TextClocks, which the launcher keeps
 * on time itself. So the widget never asks to be woken (no update period, no alarms); it is laid out when it is placed and
 * when it is resized, and a tap opens the phone's clock app.
 */
class ClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id)))
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        manager.updateAppWidget(appWidgetId, views(context, newOptions))
    }

    companion object {
        private const val DEFAULT_DP = 160f

        fun views(context: Context, options: Bundle?): RemoteViews =
            viewsForSizes(options, DEFAULT_DP to DEFAULT_DP) { width, height -> face(context, ClockFace(width, height)) }

        private fun face(context: Context, face: ClockFace): RemoteViews {
            val px = context.resources.displayMetrics.density
            fun Float.px(): Int = (this * px).roundToInt()
            return RemoteViews(context.packageName, R.layout.widget_clock).apply {
                setTextViewTextSize(R.id.widget_clock_hour, TypedValue.COMPLEX_UNIT_DIP, face.hourTextDp)
                setTextViewTextSize(R.id.widget_clock_minute, TypedValue.COMPLEX_UNIT_DIP, face.minuteTextDp)
                setViewPadding(R.id.widget_clock_hour, 0, face.digitsPaddingDp(face.hourTextDp).px(), 0, 0)
                setViewPadding(R.id.widget_clock_minute, 0, face.digitsPaddingDp(face.minuteTextDp).px(), 0, 0)
                setViewPadding(R.id.widget_clock_minute_box, face.minuteBoxPaddingDp.px(), 0, 0, 0)
                setOnClickPendingIntent(R.id.widget_clock_root, clockApp(context))
            }
        }

        /** The phone's clock app (its alarms), as a clock on the home screen opens; NormPix where there is none. */
        private fun clockApp(context: Context): PendingIntent {
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
