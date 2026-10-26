package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextClock
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
 * The cookie clocks are the platform's own clocks - an AnalogClock turning hands, TextClocks for the digits - so they keep time
 * without NormPix; the digital one is laid out for the widget's size, the text sized with the face and moved to its place on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CookieClockWidgetTest {
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
    fun `the analog clocks are an AnalogClock the platform keeps on time, and a tap opens the clock app`() {
        for ((views, layout) in listOf(
            CookieClockWidget.analog(context) to R.layout.widget_cookie_analog,
            CookieClockWidget.numbers(context) to R.layout.widget_cookie_numbers,
        )) {
            assertEquals(layout, views.layoutId)
            val root = shown(views)
            assertNotNull(root.findViewById<AnalogClock>(R.id.widget_cookie_clock))
            assertTrue(root.isClickable, "a tap opens the clock app")
        }
    }

    @Test
    fun `the digital clock is TextClocks the platform keeps on time`() {
        val root = shown(CookieClockWidget.digital(context, options(160, 160)))
        val hour = root.findViewById<TextClock>(R.id.widget_cookie_hour)
        assertEquals("h", hour.format12Hour.toString())
        assertEquals("HH", hour.format24Hour.toString())
        val minute = root.findViewById<TextClock>(R.id.widget_cookie_minute)
        assertEquals("mm", minute.format12Hour.toString())
        assertEquals("mm", minute.format24Hour.toString())
        val date = root.findViewById<TextClock>(R.id.widget_cookie_date)
        assertEquals("EEE d MMM", date.format12Hour.toString())
        assertEquals("EEE d MMM", date.format24Hour.toString())
        assertTrue(root.isClickable, "a tap opens the clock app")
    }

    @Test
    fun `the digital clock's text is sized with the face and moved to its place on it`() {
        for (size in listOf(110, 160, 220, 300)) {
            val root = shown(CookieClockWidget.digital(context, options(size, size)))
            val face = CookieFace(size.toFloat(), size.toFloat())
            for ((id, text) in listOf(
                R.id.widget_cookie_hour to face.hour,
                R.id.widget_cookie_minute to face.minute,
                R.id.widget_cookie_date to face.date,
            )) {
                val view = root.findViewById<TextClock>(id)
                assertEquals(text.sizeDp, view.textSize / density, 0.5f, "size at $size")
                assertEquals((text.topPaddingDp * density).roundToInt(), view.paddingTop, "top padding at $size")
                assertEquals((text.bottomPaddingDp * density).roundToInt(), view.paddingBottom, "bottom padding at $size")
            }
        }
    }

    @Test
    fun `a wider than tall widget keeps the face square, in the middle`() {
        val wide = shown(CookieClockWidget.digital(context, options(300, 150)))
        val square = shown(CookieClockWidget.digital(context, options(150, 150)))
        assertEquals(
            square.findViewById<TextClock>(R.id.widget_cookie_hour).textSize,
            wide.findViewById<TextClock>(R.id.widget_cookie_hour).textSize,
        )
    }

    @Test
    fun `the preview is the widget at the size it is previewed at`() {
        val root = shown(CookieClockWidget.digitalPreview(context, 168f))
        val face = CookieFace(168f, 168f)
        assertEquals(face.hour.sizeDp, root.findViewById<TextClock>(R.id.widget_cookie_hour).textSize / density, 0.5f)
    }
}
