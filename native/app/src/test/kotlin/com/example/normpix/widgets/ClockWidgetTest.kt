package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
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
import org.robolectric.annotation.GraphicsMode

/**
 * The watch dial widget is a picture of the rings under the platform's own TextClocks for the hour and the minute, laid out for
 * the widget's size: the text sized with the face, the minute moved into its pill.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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
    fun `the hour and the minute keep time themselves, and the rings are a picture`() {
        val root = shown(ClockWidget.views(context, options(180, 180)))
        val rings = root.findViewById<ImageView>(R.id.widget_clock_rings)
        assertNotNull((rings.drawable as? BitmapDrawable)?.bitmap, "the rings are a picture")
        val hour = root.findViewById<TextClock>(R.id.widget_clock_hour)
        assertEquals("hh", hour.format12Hour.toString())
        assertEquals("HH", hour.format24Hour.toString())
        val minute = root.findViewById<TextClock>(R.id.widget_clock_minute)
        assertEquals("mm", minute.format12Hour.toString())
        assertEquals("mm", minute.format24Hour.toString())
    }

    @Test
    fun `the picture is square, sharp for the face and never a large bitmap`() {
        for (size in listOf(110, 160, 300, 600)) {
            val expected = (minOf(size.toFloat(), ClockFace.MAX_FACE_DP) * density).roundToInt().coerceIn(320, 720)
            val rings = shown(ClockWidget.views(context, options(size, size))).findViewById<ImageView>(R.id.widget_clock_rings)
            val bitmap = (rings.drawable as BitmapDrawable).bitmap
            assertEquals(bitmap.width, bitmap.height)
            assertEquals(expected, bitmap.width, "at $size dp")
        }
    }

    @Test
    fun `the text is sized with the face and the minute moved into its pill`() {
        for (size in listOf(110, 160, 200, 280)) {
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
        val root = shown(ClockWidget.views(context, options(420, 420)))
        val hour = root.findViewById<TextClock>(R.id.widget_clock_hour).textSize / density
        assertEquals(ClockFace(ClockFace.MAX_FACE_DP, ClockFace.MAX_FACE_DP).hourTextDp, hour, 0.5f)
        assertTrue(root.isClickable, "a tap opens the clock app")
    }

    @Test
    fun `the rings are drawn for the minute it is, and differ from one minute to the next`() {
        fun ringsAt(minute: Int): ByteArray {
            val bitmap = (shown(ClockWidget.views(context, options(160, 160), minute)).findViewById<ImageView>(R.id.widget_clock_rings).drawable as BitmapDrawable).bitmap
            return java.io.ByteArrayOutputStream().also { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
        assertTrue(!ringsAt(10).contentEquals(ringsAt(11)), "the inner ring has turned a minute")
        assertTrue(ringsAt(10).contentEquals(ringsAt(10)), "and the same minute draws the same")
    }

    @Test
    fun `the preview is the widget at the size it is previewed at`() {
        val root = shown(ClockWidget.preview(context, 168f))
        val face = ClockFace(168f, 168f)
        assertEquals(face.hourTextDp, root.findViewById<TextClock>(R.id.widget_clock_hour).textSize / density, 0.5f)
        assertNotNull((root.findViewById<ImageView>(R.id.widget_clock_rings).drawable as? BitmapDrawable)?.bitmap)
    }
}
