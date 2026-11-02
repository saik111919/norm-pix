package com.example.normpix.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The watch dial's picture: a round face, the outer ring still and the inner ring turned to the minute. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WatchDialRendererTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val size = 400

    private fun render(minute: Int): Bitmap = WatchDialRenderer.render(context, minute, size)

    @Test
    fun `the picture is the size asked for, round, in the face's colour`() {
        val bitmap = render(45)
        assertEquals(size, bitmap.width)
        assertEquals(size, bitmap.height)
        // Outside the face it is clear, so the wallpaper shows; in the middle, where only the hour's text goes, it is the face.
        assertEquals(0, Color.alpha(bitmap.getPixel(2, 2)), "the corner")
        assertEquals(context.getColor(R.color.widget_clock_face), bitmap.getPixel(size / 2, size / 2), "the middle")
    }

    @Test
    fun `the picture holds the minutes ring only - the seconds ring is an animation over it`() {
        val a = render(10)
        val b = render(40)
        val face = context.getColor(R.color.widget_clock_face)
        // Out where the seconds ring is (beyond the minutes ring, away from the pill) the picture is the bare face.
        val x = (size / 2f - size / 2f * 0.86f).toInt()
        for (y in listOf(size / 2, size / 2 + 20, size / 2 - 20)) assertEquals(face, a.getPixel(x, y), "bare face at $y")
        val innerDiffers = (0 until size).any { y ->
            (0 until size).any { px -> hypot(px - size / 2f, y - size / 2f) < size / 2f * 0.7f && a.getPixel(px, y) != b.getPixel(px, y) }
        }
        assertTrue(innerDiffers, "the inner ring has turned")
        val outerSame = (0 until size).all { y ->
            (0 until size).all { px -> hypot(px - size / 2f, y - size / 2f) < size / 2f * 0.72f || a.getPixel(px, y) == b.getPixel(px, y) }
        }
        assertTrue(outerSame, "nothing out there changes with the minute")
    }

    @Test
    fun `a transparent dial has no face, and its pill clears what is under it`() {
        val bitmap = WatchDialRenderer.render(context, 45, size, transparent = true)
        assertEquals(0, Color.alpha(bitmap.getPixel(size / 2, size / 2)), "no face in the middle")
        assertEquals(0, Color.alpha(bitmap.getPixel(2, 2)), "nor outside it")
        val unit = size / WatchDialFace.UNITS
        // Inside the pill, away from its rim and the minute's tick: clear, so no ring tick shows under the minute.
        val inPill = bitmap.getPixel(((WatchDialFace.PILL_LEFT + 25) * unit).toInt(), (WatchDialFace.MIDDLE * unit).toInt())
        assertEquals(0, Color.alpha(inPill), "inside the pill")
        // The ring is still there.
        val ringed = (0 until size).any { y -> (0 until size).any { px -> hypot(px - size / 2f, y - size / 2f) < size / 2f * 0.7f && Color.alpha(bitmap.getPixel(px, y)) > 0 } }
        assertTrue(ringed, "the minutes ring is drawn")
    }

    @Test
    fun `a transparent dial can keep a tint of its face behind it, in the pill as well`() {
        val face = context.getColor(R.color.widget_clock_face)
        val unit = size / WatchDialFace.UNITS
        val inPill = ((WatchDialFace.PILL_LEFT + 25) * unit).toInt() to (WatchDialFace.MIDDLE * unit).toInt()
        for (alpha in listOf(77, 140, 204)) {
            val bitmap = WatchDialRenderer.render(context, 45, size, transparent = true, backdropAlpha = alpha)
            val middle = bitmap.getPixel(size / 2, size / 2)
            assertEquals(alpha, Color.alpha(middle), "the tint's strength in the middle")
            // Stored premultiplied, so a colour at partial alpha is off by a unit or two once read back.
            assertTrue(kotlin.math.abs(Color.red(face) - Color.red(middle)) <= 2, "in the face's own colour: ${Color.red(middle)}")
            assertEquals(0, Color.alpha(bitmap.getPixel(2, 2)), "nothing outside the face")
            // The pill matches the tint around it: the ring's ticks do not show through it.
            assertEquals(alpha, Color.alpha(bitmap.getPixel(inPill.first, inPill.second)), "inside the pill")
        }
        // Opaque is untouched by it: the tint is for a transparent dial.
        assertEquals(face, WatchDialRenderer.render(context, 45, size, transparent = false, backdropAlpha = 40).getPixel(size / 2, size / 2))
    }

    @Test
    fun `the same minute is drawn the same, and a minute later it has turned`() {
        fun pixels(minute: Int): IntArray = render(minute).let { bitmap -> IntArray(size * size) { bitmap.getPixel(it % size, it / size) } }
        assertTrue(pixels(7).contentEquals(pixels(7)))
        assertTrue(!pixels(7).contentEquals(pixels(8)))
    }

    @Test
    fun `the pill's rim is drawn, and it runs out to the face's edge`() {
        val bitmap = render(45)
        val unit = size / WatchDialFace.UNITS
        val face = context.getColor(R.color.widget_clock_face)
        val y = ((WatchDialFace.MIDDLE - WatchDialFace.PILL_HALF_HEIGHT) * unit).toInt()
        // The top of the rim, a little in from the pill's rounded end, and again close to the face's edge at the right.
        for (x in listOf((WatchDialFace.PILL_LEFT + 25) * unit, (WatchDialFace.MIDDLE + 90f) * unit)) {
            val rim = (y - 1..y + 1).map { bitmap.getPixel(x.toInt(), it) }
            assertTrue(rim.any { it != face }, "the rim at x=${x.toInt()}")
        }
    }

    @Test
    fun `the minute's tick is in the accent`() {
        val bitmap = render(12)
        val accent = context.getColor(R.color.widget_clock_accent)
        val unit = size / WatchDialFace.UNITS
        val x = ((WatchDialFace.MIDDLE + (WatchDialFace.INNER_TICK_IN_MAJOR + WatchDialFace.INNER_TICK_OUT) / 2) * unit).toInt()
        val y = (WatchDialFace.MIDDLE * unit).toInt()
        assertEquals(accent, bitmap.getPixel(x, y))
    }

    @Test
    fun `a minute out of range is read round the hour`() {
        assertNotEquals(0, render(-1).getPixel(size / 2, size / 2))
        val a = render(59)
        val b = render(-1)
        assertTrue((0 until size).all { y -> (0 until size).all { x -> a.getPixel(x, y) == b.getPixel(x, y) } })
    }

    @Test
    fun `the inner ring creeps through the minute instead of waiting to jump at its end`() {
        fun pixels(minute: Int, second: Int): IntArray = WatchDialRenderer.render(context, minute, size, second = second)
            .let { bitmap -> IntArray(size * size) { bitmap.getPixel(it % size, it / size) } }

        fun differing(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }
        val start = pixels(10, 0)
        val half = pixels(10, 30)
        val end = pixels(10, 59)
        val next = pixels(11, 0)
        assertTrue(!start.contentEquals(half), "half a minute on, the ring has turned")
        assertTrue(!half.contentEquals(end), "and goes on turning")
        // Just before the minute ends the ring is nearly where the next minute puts it: the step at the change is a sliver of a
        // minute's turn, not the whole of it.
        assertTrue(differing(end, next) * 3 < differing(start, next), "${differing(end, next)} vs ${differing(start, next)}")
        // The same moment is drawn the same, and the default is the minute's own place.
        assertTrue(pixels(10, 30).contentEquals(pixels(10, 30)))
        assertTrue(pixels(10, 0).contentEquals(render(10).let { bitmap -> IntArray(size * size) { bitmap.getPixel(it % size, it / size) } }))
    }
}
