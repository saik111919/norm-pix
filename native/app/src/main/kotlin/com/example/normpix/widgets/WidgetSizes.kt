package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews

/**
 * The views for a widget at every size the launcher may show it at. From Android 12 the launcher says which sizes those
 * are, and picks the views for the size it is at as it is resized; before, the widget is laid out for its size upright
 * (its least width and its greatest height, as a phone in portrait shows it) and again when resized.
 */
internal fun viewsForSizes(options: Bundle?, fallback: Pair<Float, Float>, build: (widthDp: Float, heightDp: Float) -> RemoteViews): RemoteViews {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        @Suppress("DEPRECATION")
        val sizes = options?.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        if (!sizes.isNullOrEmpty()) {
            // At most 16, each once: what RemoteViews takes.
            return RemoteViews(sizes.distinct().take(16).associateWith { build(it.width, it.height) })
        }
    }
    val (width, height) = uprightSize(options) ?: fallback
    return build(width, height)
}

/** The widget's size with the phone upright, in dp; null when the launcher hasn't said. */
internal fun uprightSize(options: Bundle?): Pair<Float, Float>? {
    val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
    val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
    return if (width > 0 && height > 0) width.toFloat() to height.toFloat() else null
}
