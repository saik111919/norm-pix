package com.example.normpix.widgets

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextClock
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.R
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The plain digital clock: the time as large as the widget allows, read the way the user chose, nothing added. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SimpleClockTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val density get() = context.resources.displayMetrics.density

    private fun shown(views: RemoteViews): View = views.apply(context, FrameLayout(context))

    @Test
    fun `the time grows with the widget, up to a limit, and never leaves it`() {
        var last = 0f
        for (size in listOf(40f, 60f, 100f, 160f, 300f, 600f)) {
            val layout = SimpleClockLayout(size, size, showDate = false, showSeconds = false)
            assertTrue(layout.timeDp >= last, "no smaller at $size")
            assertTrue(layout.timeDp <= SimpleClockLayout.MAX_TIME_DP)
            assertTrue(layout.timeDp * SimpleClockLayout.TIME_EM <= size, "across: ${layout.timeDp} in $size")
            last = layout.timeDp
        }
        val strip = SimpleClockLayout(160f, 40f, showDate = false, showSeconds = false)
        assertTrue(strip.timeDp * SimpleClockLayout.LINE_EM <= 40f, "a strip's time fits its height")
    }

    @Test
    fun `seconds make the time wider, so it is smaller`() {
        val plain = SimpleClockLayout(110f, 110f, showDate = false, showSeconds = false)
        val withSeconds = SimpleClockLayout(110f, 110f, showDate = false, showSeconds = true)
        assertTrue(withSeconds.timeDp < plain.timeDp)
        assertTrue(withSeconds.timeDp * SimpleClockLayout.TIME_EM_SECONDS <= 110f)
    }

    @Test
    fun `the date takes a line of its own where there is room, and none where there is not`() {
        val room = SimpleClockLayout(160f, 160f, showDate = true, showSeconds = false)
        assertTrue(room.dateFits)
        assertTrue(room.dateDp < room.timeDp)
        val strip = SimpleClockLayout(160f, 40f, showDate = true, showSeconds = false)
        assertFalse(strip.dateFits, "a line tall has no second line")
    }

    @Test
    fun `it reads the time as the phone is set, and keeps time itself`() {
        val root = shown(SimpleClock.views(context, 110f, 110f, ClockOptions()))
        val time = root.findViewById<TextClock>(R.id.widget_simple_time)
        assertEquals("h:mm", time.format12Hour.toString())
        assertEquals("HH:mm", time.format24Hour.toString())
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_simple_date).visibility, "no date unless asked for")
        assertTrue(root.isClickable, "a tap opens the clock app")
    }

    @Test
    fun `twelve or twenty-four hours, with seconds, as chosen`() {
        fun time(options: ClockOptions) = shown(SimpleClock.views(context, 160f, 80f, options)).findViewById<TextClock>(R.id.widget_simple_time)
        time(ClockOptions(format = ClockTimeFormat.Hours12)).let {
            assertEquals("h:mm", it.format12Hour.toString())
            assertEquals("h:mm", it.format24Hour.toString())
        }
        time(ClockOptions(format = ClockTimeFormat.Hours24)).let {
            assertEquals("HH:mm", it.format12Hour.toString())
            assertEquals("HH:mm", it.format24Hour.toString())
        }
        time(ClockOptions(showSeconds = true)).let {
            assertEquals("h:mm:ss", it.format12Hour.toString())
            assertEquals("HH:mm:ss", it.format24Hour.toString())
        }
    }

    @Test
    fun `the date shows when asked for and there is room`() {
        val root = shown(SimpleClock.views(context, 160f, 160f, ClockOptions(showDate = true)))
        val date = root.findViewById<TextClock>(R.id.widget_simple_date)
        assertEquals(View.VISIBLE, date.visibility)
        assertEquals(SimpleClockLayout(160f, 160f, showDate = true, showSeconds = false).dateDp, date.textSize / density, 0.5f)
        val strip = shown(SimpleClock.views(context, 160f, 40f, ClockOptions(showDate = true)))
        assertEquals(View.GONE, strip.findViewById<View>(R.id.widget_simple_date).visibility, "no room for it on a strip")
    }

    @Test
    fun `transparent drops the background, opaque keeps it`() {
        val opaque = shown(SimpleClock.views(context, 110f, 110f, ClockOptions()))
        assertNotNull(opaque.findViewById<View>(R.id.widget_simple_root).background, "the face colour behind it")
        val clear = shown(SimpleClock.views(context, 110f, 110f, ClockOptions(transparent = true))).findViewById<View>(R.id.widget_simple_root)
        assertEquals(Color.TRANSPARENT, (clear.background as ColorDrawable).color)
    }
}
