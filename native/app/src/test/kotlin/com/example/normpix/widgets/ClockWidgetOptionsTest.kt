package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.AnalogClock
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the user's Widget settings do to the clocks: a face too small to read (or one set to be digital) is the plain digital
 * clock, "Transparent" leaves the face's background out - and the watch dial's seconds ring is a view that turns by itself,
 * started at the second it is.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ClockWidgetOptionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun options(width: Int, height: Int) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
    }

    private fun shown(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    private fun View.isDigital() = findViewById<View>(R.id.widget_simple_time) != null

    private val small = ClockOptions()

    // ---- every clock gives way to the digital clock when small ----

    @Test
    fun `every clock widget is the plain digital clock at one cell, and keeps its face at two`() {
        for (widget in HomeWidget.inGroup(HomeWidgetGroup.Clocks)) {
            val one = shown(ClockWidgets.views(context, widget, options(57, 57), small))
            assertTrue(one.isDigital(), "${widget.name} at one cell")
            val two = shown(ClockWidgets.views(context, widget, options(150, 150), small))
            assertTrue(!two.isDigital(), "${widget.name} at two cells")
        }
    }

    @Test
    fun `the digital clock is for good when the user chose it`() {
        for (widget in HomeWidget.inGroup(HomeWidgetGroup.Clocks)) {
            val root = shown(ClockWidgets.views(context, widget, options(300, 300), ClockOptions(style = ClockStyle.Digital)))
            assertTrue(root.isDigital(), widget.name)
        }
    }

    @Test
    fun `a small face is kept when the user turned the fallback off`() {
        for (widget in HomeWidget.inGroup(HomeWidgetGroup.Clocks)) {
            val root = shown(ClockWidgets.views(context, widget, options(57, 57), ClockOptions(digitalWhenSmall = false)))
            assertTrue(!root.isDigital(), widget.name)
        }
    }

    @Test
    fun `each size the launcher may show it at gets what suits it`() {
        // Android 12 gives every size at once: the strip is digital, the square keeps its face.
        val sizes = Bundle().apply {
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(160f, 50f), SizeF(160f, 160f)))
        }
        for (widget in HomeWidget.inGroup(HomeWidgetGroup.Clocks)) {
            assertNotNull(ClockWidgets.views(context, widget, sizes, small), widget.name)
        }
        val dial = ClockWidget.views(context, sizes)
        assertNotNull(dial)
        val onlyStrips = Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(160f, 50f))) }
        assertTrue(shown(ClockWidget.views(context, onlyStrips)).isDigital(), "no picture is drawn when no size needs it")
    }

    @Test
    fun `the digital clock says it is the plain one - the time and nothing else`() {
        val root = shown(ClockWidgets.views(context, HomeWidget.CookieAnalog, options(57, 57), small))
        assertNull(root.findViewById<View>(R.id.widget_cookie_clock))
        assertNull(root.findViewById<View>(R.id.widget_clock_rings))
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_simple_date).visibility)
    }

    // ---- transparent ----

    @Test
    fun `transparent leaves the cookie out of the analog clocks and keeps the hands`() {
        for (widget in listOf(HomeWidget.CookieAnalog, HomeWidget.CookieNumbers, HomeWidget.CookiePlain)) {
            val opaque = shown(ClockWidgets.views(context, widget, options(160, 160), small))
            assertEquals(View.VISIBLE, opaque.findViewById<View>(R.id.widget_cookie_fill).visibility, "${widget.name} opaque")
            val clear = shown(ClockWidgets.views(context, widget, options(160, 160), ClockOptions(transparent = true)))
            assertEquals(View.GONE, clear.findViewById<View>(R.id.widget_cookie_fill).visibility, "${widget.name} transparent")
            assertNotNull(clear.findViewById<AnalogClock>(R.id.widget_cookie_clock), "the hands stay")
        }
    }

    @Test
    fun `transparent leaves the cookie out of the digital clock too`() {
        val opaque = shown(ClockWidgets.views(context, HomeWidget.CookieDigital, options(160, 160), small))
        assertEquals(View.VISIBLE, opaque.findViewById<View>(R.id.widget_cookie_face).visibility)
        val clear = shown(ClockWidgets.views(context, HomeWidget.CookieDigital, options(160, 160), ClockOptions(transparent = true)))
        assertEquals(View.GONE, clear.findViewById<View>(R.id.widget_cookie_face).visibility)
        assertNotNull(clear.findViewById<View>(R.id.widget_cookie_hour))
    }

    @Test
    fun `a transparent watch dial is drawn without its face`() {
        fun middle(clock: ClockOptions): Int {
            val rings = shown(ClockWidgets.views(context, HomeWidget.WatchDial, options(180, 180), clock)).findViewById<ImageView>(R.id.widget_clock_rings)
            val bitmap = (rings.drawable as BitmapDrawable).bitmap
            return Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        }
        assertEquals(255, middle(small))
        assertEquals(0, middle(ClockOptions(transparent = true)))
    }

    // ---- the plain cookie ----

    @Test
    fun `the plain cookie clock is a cookie with hands and no marks`() {
        val root = shown(CookieClockWidget.plain(context))
        assertNotNull(root.findViewById<AnalogClock>(R.id.widget_cookie_clock))
        assertTrue(root.isClickable, "a tap opens the clock app")
    }

    // ---- the seconds ring ----

    @Test
    fun `the watch dial has a seconds ring that turns by itself`() {
        val root = shown(ClockWidget.views(context, options(180, 180)))
        val host = root.findViewById<FrameLayout>(R.id.widget_clock_seconds)
        assertEquals(1, host.childCount, "one ring")
        val ring = host.getChildAt(0) as ProgressBar
        assertTrue(ring.isIndeterminate, "a progress bar is what runs the animation")
        assertTrue(ring.indeterminateDrawable is android.graphics.drawable.Animatable, "an animated vector")
    }

    @Test
    fun `drawn again, the ring is replaced and not added to`() {
        val views = ClockWidget.views(context, options(180, 180), second = 10)
        val root = FrameLayout(context)
        val first = views.apply(context, root)
        views.reapply(context, first)
        views.reapply(context, first)
        assertEquals(1, first.findViewById<FrameLayout>(R.id.widget_clock_seconds).childCount)
    }

    @Test
    fun `every second of the minute has its own ring, started there`() {
        assertEquals(60, WatchDialSecondsLayouts.size)
        assertEquals(60, WatchDialSecondsLayouts.toSet().size)
        for (second in listOf(0, 17, 59)) assertNotNull(shown(ClockWidget.views(context, options(180, 180), second = second)), "at $second")
        // A second out of range is read round the minute.
        assertNotNull(shown(ClockWidget.views(context, options(180, 180), second = 75)))
    }

    @Test
    fun `the ring's animations turn the ring and each number against it, from the second they are for`() {
        for (second in 0 until 60) {
            val n = "%02d".format(second)
            val orbit = File("src/main/res/animator/widget_dial_orbit_$n.xml").readText()
            val upright = File("src/main/res/animator/widget_dial_upright_$n.xml").readText()
            assertTrue("valueFrom=\"${6 * second}\"" in orbit && "valueTo=\"${6 * second + 360}\"" in orbit, "orbit $n")
            assertTrue("valueFrom=\"${-6 * second}\"" in upright && "valueTo=\"${-6 * second - 360}\"" in upright, "upright $n")
            for (xml in listOf(orbit, upright)) assertTrue("android:duration=\"60000\"" in xml && "android:repeatCount=\"-1\"" in xml, "a minute, for ever: $n")
            val avd = File("src/main/res/drawable/widget_dial_seconds_$n.xml").readText()
            assertTrue("widget_dial_orbit_$n" in avd, "its orbit")
            assertEquals(12, Regex("widget_dial_upright_$n").findAll(avd).count(), "a turn for each of the 12 numbers")
        }
    }

    @Test
    fun `the ring's vector has the ticks and every number in a group of its own`() {
        val vector = File("src/main/res/drawable/widget_dial_seconds.xml").readText()
        assertTrue("android:name=\"orbit\"" in vector)
        for (value in 0 until 60 step 5) assertTrue("android:name=\"n${"%02d".format(value)}\"" in vector, "number $value")
    }

    @Test
    fun `the ticker draws the dial every quarter of a minute, a moment past it`() {
        assertEquals(15_150L, WatchDialTicker.millisToNextTick(0L) + 0)
        assertEquals(15_150L - 1_000L, WatchDialTicker.millisToNextTick(1_000L))
        assertEquals(150L + 1L, WatchDialTicker.millisToNextTick(14_999L))
        assertEquals(15_000L + 150L, WatchDialTicker.millisToNextTick(30_000L))
        val seconds = (0..59).map { WatchDialTicker.secondNow() }
        assertTrue(seconds.all { it in 0..59 })
    }
}
