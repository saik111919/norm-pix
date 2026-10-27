package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.widget.RemoteViews

/**
 * What the five clock widgets share: a clock is drawn for its size and for what the user chose in Widget settings
 * ([ClockOptions]) - its face, or the plain digital clock where the face is too small or was chosen, with or without its
 * background - by one function, whatever kind of clock it is.
 */
internal object ClockWidgets {
    /** The size laid out for when the launcher hasn't said (dp). */
    const val DEFAULT_DP = 160f

    /** [widget] as the launcher is given it for the sizes in [options], with the user's [clock] choices. */
    fun views(context: Context, widget: HomeWidget, options: Bundle?, clock: ClockOptions): RemoteViews = when (widget) {
        HomeWidget.WatchDial -> ClockWidget.views(context, options, clock = clock)
        else -> CookieClockWidget.views(context, widget, options, clock)
    }

    /** [widget] drawn for a widget [widthDp] x [heightDp], as Settings > Widgets and Widget settings preview it. */
    fun preview(context: Context, widget: HomeWidget, widthDp: Float, heightDp: Float, clock: ClockOptions = ClockOptions()): RemoteViews = when (widget) {
        HomeWidget.WatchDial -> ClockWidget.preview(context, widthDp, heightDp, clock)
        else -> CookieClockWidget.preview(context, widget, widthDp, heightDp, clock)
    }

    /** Which clock widget [appWidgetId] is, or null when it is not one of NormPix's clocks. */
    fun widgetOf(context: Context, appWidgetId: Int): HomeWidget? {
        val provider = AppWidgetManager.getInstance(context)?.getAppWidgetInfo(appWidgetId)?.provider ?: return null
        return HomeWidget.entries.firstOrNull { it.group == HomeWidgetGroup.Clocks && it.provider.name == provider.className }
    }

    /** The widget's size now, in dp (the launcher's say of it), or [DEFAULT_DP] square before it has said. */
    fun sizeOf(context: Context, appWidgetId: Int): Pair<Float, Float> {
        val options = AppWidgetManager.getInstance(context)?.getAppWidgetOptions(appWidgetId)
        return uprightSize(options) ?: (DEFAULT_DP to DEFAULT_DP)
    }

    /** Draws [appWidgetId] again with what the user has chosen for it. */
    fun update(context: Context, appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val widget = widgetOf(context, appWidgetId) ?: return
        val options = manager.getAppWidgetOptions(appWidgetId)
        manager.updateAppWidget(appWidgetId, views(context, widget, options, ClockOptionsStore(context).get(appWidgetId)))
    }
}
