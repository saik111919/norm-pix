package com.example.normpix.widgets

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.widget.RemoteViews
import com.example.normpix.R
import kotlin.math.min

/**
 * Where the plain digital clock's text goes in a widget [widthDp] x [heightDp]: the time, as large as the widget allows without
 * leaving it, and under it - when asked for - the date. No ornament: this is the clock a widget turns into when its face would
 * be too small to read, or when the user chose it for good (Widget settings).
 *
 * The sizes are in dp, so the widget, not the phone's text size, decides them. [timeEm] is how wide the time is in ems of its
 * size: "12:34" is four digits and a colon, "12:34:56" six and two.
 */
class SimpleClockLayout(widthDp: Float, heightDp: Float, showDate: Boolean, showSeconds: Boolean) {
    private val roomWidth = (widthDp - 2 * PADDING_DP).coerceAtLeast(0f)
    private val roomHeight = (heightDp - 2 * PADDING_DP).coerceAtLeast(0f)
    private val timeEm = if (showSeconds) TIME_EM_SECONDS else TIME_EM

    /** The time's text size: what fits across, and what fits down once the date has its line. */
    val timeDp: Float = min(roomWidth / timeEm, roomHeight * (if (showDate) TIME_SHARE_WITH_DATE else TIME_SHARE) / LINE_EM)
        .coerceIn(MIN_TIME_DP, MAX_TIME_DP)

    /** The date's text size: a small share of the time's, and never wider than the widget (about ten letters). */
    val dateDp: Float = min(timeDp * DATE_SHARE, roomWidth / DATE_EM).coerceIn(MIN_DATE_DP, MAX_DATE_DP)

    /** Whether there is room for the date at all: a strip a line tall has none. */
    val dateFits: Boolean = roomHeight >= (timeDp + dateDp) * LINE_EM + DATE_GAP_DP && roomWidth >= MIN_DATE_DP * DATE_EM

    companion object {
        const val PADDING_DP = 6f
        const val MIN_TIME_DP = 12f
        const val MAX_TIME_DP = 120f
        const val MIN_DATE_DP = 9f
        const val MAX_DATE_DP = 18f

        /** Widths in ems (GS Flex: a digit 0.58, a colon 0.28), and a line's height in ems. */
        const val TIME_EM = 2.7f
        const val TIME_EM_SECONDS = 4.2f
        const val DATE_EM = 6.2f
        const val LINE_EM = 1.15f
        const val TIME_SHARE = 0.85f
        const val TIME_SHARE_WITH_DATE = 0.62f
        const val DATE_SHARE = 0.3f

        /** What a second line needs besides its own height: the gap between the two. */
        const val DATE_GAP_DP = 6f
    }
}

/** The plain digital clock as a widget's views: [SimpleClockLayout] sizes it, [ClockOptions] says how it reads. */
internal object SimpleClock {
    fun views(context: Context, widthDp: Float, heightDp: Float, clock: ClockOptions): RemoteViews {
        val layout = SimpleClockLayout(widthDp, heightDp, clock.showDate, clock.showSeconds)
        val showDate = clock.showDate && layout.dateFits
        val seconds = if (clock.showSeconds) ":ss" else ""
        val (twelve, twentyFour) = when (clock.format) {
            ClockTimeFormat.System -> "h:mm$seconds" to "HH:mm$seconds"
            ClockTimeFormat.Hours12 -> "h:mm$seconds" to "h:mm$seconds"
            ClockTimeFormat.Hours24 -> "HH:mm$seconds" to "HH:mm$seconds"
        }
        return RemoteViews(context.packageName, R.layout.widget_clock_simple).apply {
            setCharSequence(R.id.widget_simple_time, "setFormat12Hour", twelve)
            setCharSequence(R.id.widget_simple_time, "setFormat24Hour", twentyFour)
            setTextViewTextSize(R.id.widget_simple_time, TypedValue.COMPLEX_UNIT_DIP, layout.timeDp)
            setViewVisibility(R.id.widget_simple_date, if (showDate) android.view.View.VISIBLE else android.view.View.GONE)
            if (showDate) setTextViewTextSize(R.id.widget_simple_date, TypedValue.COMPLEX_UNIT_DIP, layout.dateDp)
            if (clock.transparent) setInt(R.id.widget_simple_root, "setBackgroundColor", Color.TRANSPARENT)
            setOnClickPendingIntent(R.id.widget_simple_root, ClockWidget.clockApp(context))
        }
    }
}
