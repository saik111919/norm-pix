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
import kotlin.math.roundToInt
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
    fun `a transparent cookie clock can keep a tint of its cookie`() {
        for ((widget, id) in listOf(
            HomeWidget.CookieAnalog to R.id.widget_cookie_fill,
            HomeWidget.CookieNumbers to R.id.widget_cookie_fill,
            HomeWidget.CookiePlain to R.id.widget_cookie_fill,
            HomeWidget.CookieDigital to R.id.widget_cookie_face,
        )) {
            val tinted = shown(ClockWidgets.views(context, widget, options(160, 160), ClockOptions(transparent = true, backdropPercent = ClockBackdrop.Medium.percent)))
            val cookie = tinted.findViewById<ImageView>(id)
            assertEquals(View.VISIBLE, cookie.visibility, "${widget.name} keeps its cookie")
            assertEquals(ClockBackdrop.Medium.alpha, cookie.imageAlpha, "${widget.name} at the strength chosen")
        }
    }

    @Test
    fun `a transparent watch dial keeps a tint of its face at the strength chosen`() {
        fun middle(clock: ClockOptions): Int {
            val rings = shown(ClockWidgets.views(context, HomeWidget.WatchDial, options(180, 180), clock)).findViewById<ImageView>(R.id.widget_clock_rings)
            val bitmap = (rings.drawable as BitmapDrawable).bitmap
            return Color.alpha(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        }
        val faint = middle(ClockOptions(transparent = true, backdropPercent = ClockBackdrop.Soft.percent))
        val strong = middle(ClockOptions(transparent = true, backdropPercent = ClockBackdrop.Strong.percent))
        assertAbout(ClockBackdrop.Soft.alpha, faint)
        assertAbout(ClockBackdrop.Strong.alpha, strong)
        assertTrue(faint < strong)
        // A new strength is drawn at once: the picture is not reused from the one before.
        assertEquals(0, middle(ClockOptions(transparent = true)))
    }

    private fun assertAbout(expected: Int, actual: Int) =
        assertTrue(kotlin.math.abs(expected - actual) <= 2, "expected about $expected but was $actual")

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
    fun `from Android 12 the seconds ring is a bezel the system turns by the real time`() {
        val root = shown(ClockWidget.views(context, options(180, 180)))
        val host = root.findViewById<FrameLayout>(R.id.widget_clock_seconds)
        assertEquals(1, host.childCount, "one ring")
        // An AnalogClock whose second hand is the whole ring: nothing to restart, so nothing to nudge (owner's choice, 2026-11-01).
        assertTrue(host.getChildAt(0) is AnalogClock, "the platform's clock turns it")
        val layout = File("src/main/res/layout/widget_dial_bezel.xml").readText()
        assertTrue("android:hand_second=\"@drawable/widget_dial_bezel\"" in layout, "the ring is its second hand")
        assertTrue("android:dial=\"@drawable/widget_dial_bezel_frame\"" in layout, "a dial as large as the bezel, so it is fitted to the face")
    }

    @Test
    fun `the bezel is the ring at naught, as large as a widget can be, its numbers set along it`() {
        val bezel = File("src/main/res/drawable/widget_dial_bezel.xml").readText()
        val frame = File("src/main/res/drawable/widget_dial_bezel_frame.xml").readText()
        assertTrue("android:width=\"1200dp\"" in bezel && "android:width=\"1200dp\"" in frame, "AnalogClock only scales down")
        assertTrue("#00000000" in frame, "the frame draws nothing")
        assertTrue("widget_clock_tick" in bezel && "widget_clock_secondary" in bezel, "the ring's ticks and numbers, in the dial's colours")
    }

    @Test
    fun `before Android 12 the seconds ring is the animation started at the second it is`() {
        val root = shown(ClockWidget.views(context, options(180, 180), bezel = false))
        val host = root.findViewById<FrameLayout>(R.id.widget_clock_seconds)
        assertEquals(1, host.childCount, "one ring")
        val ring = host.getChildAt(0) as ProgressBar
        assertTrue(ring.isIndeterminate, "a progress bar is what runs the animation")
        assertTrue(ring.indeterminateDrawable is android.graphics.drawable.Animatable, "an animated vector")
    }

    @Test
    fun `the second is in the pill, kept by the platform, and the pill is over the ring`() {
        for (size in listOf(160, 280)) {
            val root = shown(ClockWidget.views(context, options(size, size)))
            val face = ClockFace(size - 12f, size - 12f)
            val second = root.findViewById<android.widget.TextClock>(R.id.widget_clock_second)
            assertEquals("ss", second.format24Hour.toString())
            assertEquals("ss", second.format12Hour.toString())
            assertEquals(face.secondTextDp, second.textSize / context.resources.displayMetrics.density, 0.5f, "sized with the face at $size")
            val box = root.findViewById<View>(R.id.widget_clock_second_box)
            val density = context.resources.displayMetrics.density
            assertEquals((face.secondBoxPaddingDp * density).roundToInt(), box.paddingLeft, "moved into the pill at $size")
            assertTrue(face.secondOffsetDp > face.minuteOffsetDp, "after the minute, where the ring passes the pointer")
            // The pill is a layer above the ring, so the ring's numbers pass behind the second's.
            val parent = root as android.view.ViewGroup
            val ringAt = (0 until parent.childCount).first { parent.getChildAt(it).id == R.id.widget_clock_seconds }
            val pillAt = (0 until parent.childCount).first { parent.getChildAt(it).id == R.id.widget_clock_pill }
            val secondAt = (0 until parent.childCount).first { parent.getChildAt(it).id == R.id.widget_clock_second_box }
            assertTrue(ringAt < pillAt && pillAt < secondAt, "ring, then the pill over it, then the second on the pill")
        }
    }

    @Test
    fun `the pill is solid on a transparent clock too, so the ring never shows through under the second`() {
        fun pillAlpha(clock: ClockOptions) =
            shown(ClockWidget.views(context, options(180, 180), clock = clock)).findViewById<android.widget.ImageView>(R.id.widget_clock_pill).imageAlpha
        assertEquals(255, pillAlpha(ClockOptions(transparent = true)), "clear: still a whole pill to read the second on")
        assertEquals(255, pillAlpha(ClockOptions()), "an opaque clock: whole")
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
        for (second in listOf(0, 17, 59)) assertNotNull(shown(ClockWidget.views(context, options(180, 180), second = second, bezel = false)), "at $second")
        // A second out of range is read round the minute.
        assertNotNull(shown(ClockWidget.views(context, options(180, 180), second = 75, bezel = false)))
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
    fun `the ticker draws the dial every five seconds, a moment past it`() {
        assertEquals(5_000L, WatchDialTicker.TICK_MS)
        assertEquals(5_150L, WatchDialTicker.millisToNextTick(0L))
        assertEquals(5_150L - 1_000L, WatchDialTicker.millisToNextTick(1_000L))
        assertEquals(150L + 1L, WatchDialTicker.millisToNextTick(4_999L))
        assertEquals(5_000L + 150L, WatchDialTicker.millisToNextTick(30_000L))
        val seconds = (0..59).map { WatchDialTicker.secondNow() }
        assertTrue(seconds.all { it in 0..59 })
    }
}
