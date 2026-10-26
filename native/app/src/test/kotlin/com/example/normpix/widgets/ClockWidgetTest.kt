package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.TextClock
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The clock widget is the platform's own clock - an AnalogClock whose hands are the rings, TextClocks for the hour and the
 * minute - laid out for the widget's size: the text sized with the face, the minute moved into its pill.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density get() = context.resources.displayMetrics.density

    private fun options(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    private fun shown(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    @Test
    fun `the face keeps time itself - the rings are an AnalogClock's hands, the numbers TextClocks`() {
        val root = shown(ClockWidget.views(context, options(180, 180)))
        assertNotNull(root.findViewById<AnalogClock>(R.id.widget_clock_rings))
        val hour = root.findViewById<TextClock>(R.id.widget_clock_hour)
        assertEquals("hh", hour.format12Hour.toString())
        assertEquals("HH", hour.format24Hour.toString())
        val minute = root.findViewById<TextClock>(R.id.widget_clock_minute)
        assertEquals("mm", minute.format12Hour.toString())
        assertEquals("mm", minute.format24Hour.toString())
    }

    @Test
    fun `the text is sized with the face and the minute moved into its pill`() {
        for (size in listOf(110, 160, 200)) {
            val root = shown(ClockWidget.views(context, options(size, size)))
            val face = ClockFace(size.toFloat(), size.toFloat())
            assertEquals(face.hourTextDp, root.findViewById<TextClock>(R.id.widget_clock_hour).textSize / density, 0.5f, "hour at $size")
            assertEquals(face.minuteTextDp, root.findViewById<TextClock>(R.id.widget_clock_minute).textSize / density, 0.5f, "minute at $size")
            val box = root.findViewById<View>(R.id.widget_clock_minute_box)
            assertEquals((face.minuteBoxPaddingDp * density).roundToInt(), box.paddingLeft, "the minute's box at $size")
        }
    }

    @Test
    fun `a widget larger than the face keeps the face's size`() {
        val root = shown(ClockWidget.views(context, options(320, 320)))
        val hour = root.findViewById<TextClock>(R.id.widget_clock_hour).textSize / density
        assertEquals(ClockFace(200f, 200f).hourTextDp, hour, 0.5f)
        assertTrue(root.isClickable, "a tap opens the clock app")
    }
}
