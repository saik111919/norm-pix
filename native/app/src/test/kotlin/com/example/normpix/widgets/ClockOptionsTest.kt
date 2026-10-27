package com.example.normpix.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What a clock widget remembers of the user's choices, and when a face gives way to the digital clock. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockOptionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        context.getSharedPreferences(ClockOptionsStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `a clock starts as it always was - a face, opaque, the digital clock only when small`() {
        val options = ClockOptionsStore(context).get(42)
        assertEquals(ClockOptions(), options)
        assertFalse(options.transparent)
        assertEquals(ClockStyle.Face, options.style)
        assertTrue(options.digitalWhenSmall)
        assertEquals(ClockTimeFormat.System, options.format)
        assertFalse(options.showSeconds)
        assertFalse(options.showDate)
    }

    @Test
    fun `every choice is kept, for that widget alone`() {
        val store = ClockOptionsStore(context)
        val mine = ClockOptions(
            transparent = true, style = ClockStyle.Digital, digitalWhenSmall = false,
            format = ClockTimeFormat.Hours24, showSeconds = true, showDate = true,
        )
        store.set(7, mine)
        assertEquals(mine, ClockOptionsStore(context).get(7), "read again by another store")
        assertEquals(ClockOptions(), store.get(8), "another widget keeps its own")
        assertEquals(ClockOptions(), store.get(70), "and 7 is not a prefix of 70")
    }

    @Test
    fun `a widget taken off the home screen is forgotten, and no other`() {
        val store = ClockOptionsStore(context)
        store.set(7, ClockOptions(transparent = true))
        store.set(70, ClockOptions(transparent = true))
        store.set(8, ClockOptions(showDate = true))
        store.remove(intArrayOf(7, 8))
        assertEquals(ClockOptions(), store.get(7))
        assertEquals(ClockOptions(), store.get(8))
        assertTrue(store.get(70).transparent)
        store.remove(IntArray(0))
        assertTrue(store.get(70).transparent)
    }

    @Test
    fun `a name that is not a choice any more reads as the default`() {
        context.getSharedPreferences(ClockOptionsStore.FILE, Context.MODE_PRIVATE).edit()
            .putString("5.style", "Hologram").putString("5.format", "Julian").commit()
        val options = ClockOptionsStore(context).get(5)
        assertEquals(ClockStyle.Face, options.style)
        assertEquals(ClockTimeFormat.System, options.format)
    }

    @Test
    fun `a face too small to read gives way to the digital clock`() {
        val options = ClockOptions()
        // A cell of the home screen is about 40 to 70 dp; two are 110 or more.
        assertTrue(options.showsDigital(57f, 57f), "one cell")
        assertTrue(options.showsDigital(160f, 57f), "a strip two cells wide and one tall")
        assertTrue(options.showsDigital(57f, 160f), "and one cell wide")
        assertFalse(options.showsDigital(110f, 110f), "two by two keeps its face")
        assertFalse(options.showsDigital(ClockOptions.SMALL_FACE_DP, ClockOptions.SMALL_FACE_DP))
        assertTrue(options.showsDigital(ClockOptions.SMALL_FACE_DP - 1, 300f))
    }

    @Test
    fun `the user can turn the small fallback off, or have the digital clock for good`() {
        assertFalse(ClockOptions(digitalWhenSmall = false).showsDigital(57f, 57f))
        for (size in listOf(57f, 110f, 300f)) {
            assertTrue(ClockOptions(style = ClockStyle.Digital).showsDigital(size, size), "digital at $size")
            assertTrue(ClockOptions(style = ClockStyle.Digital, digitalWhenSmall = false).showsDigital(size, size))
        }
    }
}
