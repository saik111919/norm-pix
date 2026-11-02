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
    fun `a transparent clock keeps no tint to begin with, and the tint chosen is kept for that widget alone`() {
        val store = ClockOptionsStore(context)
        assertEquals(ClockBackdrop.Clear, store.get(3).backdrop)
        assertEquals(0, ClockOptions(transparent = true).backdropAlpha, "clear is no tint at all")
        store.set(3, ClockOptions(transparent = true, backdropPercent = ClockBackdrop.Strong.percent))
        assertEquals(ClockBackdrop.Strong, ClockOptionsStore(context).get(3).backdrop)
        assertEquals(ClockBackdrop.Clear, store.get(4).backdrop)
        assertEquals(ClockBackdrop.Strong.alpha, store.get(3).backdropAlpha)
    }

    @Test
    fun `the slider's tint is kept as a number, and a strength an earlier version saved by name still reads as it was`() {
        val store = ClockOptionsStore(context)
        store.set(3, ClockOptions(transparent = true, backdropPercent = 65))
        assertEquals(65, ClockOptionsStore(context).get(3).backdropPercent)
        assertEquals(tintAlpha(65), store.get(3).backdropAlpha)
        // The nearest of the old four is saved by name beside it, for an earlier version to find.
        val saved = context.getSharedPreferences(ClockOptionsStore.FILE, Context.MODE_PRIVATE)
        assertEquals("Medium", saved.getString("3.backdrop", null))
        // A widget set up before the slider: only the name.
        saved.edit().putBoolean("9.transparent", true).putString("9.backdrop", "Soft").commit()
        assertEquals(ClockBackdrop.Soft.percent, store.get(9).backdropPercent)
        assertEquals(ClockBackdrop.Soft.alpha, store.get(9).backdropAlpha, "it looks exactly as it did")
        // Out of range never gets through.
        saved.edit().putInt("10.backdrop_percent", 140).commit()
        assertEquals(100, store.get(10).backdropPercent)
    }

    @Test
    fun `any tint at all is described as one, and the words follow the strength`() {
        assertEquals(ClockBackdrop.Clear, ClockBackdrop.nearest(0))
        assertEquals(ClockBackdrop.Soft, ClockBackdrop.nearest(5))
        assertEquals(ClockBackdrop.Soft, ClockBackdrop.nearest(40))
        assertEquals(ClockBackdrop.Medium, ClockBackdrop.nearest(50))
        assertEquals(ClockBackdrop.Strong, ClockBackdrop.nearest(70))
        assertEquals(ClockBackdrop.Strong, ClockBackdrop.nearest(100))
        assertEquals(255, tintAlpha(100))
    }

    @Test
    fun `a tint says nothing for a clock whose face is whole, and the strengths rise`() {
        assertEquals(0, ClockOptions(transparent = false, backdropPercent = ClockBackdrop.Strong.percent).backdropAlpha)
        val alphas = ClockBackdrop.entries.map { it.alpha }
        assertEquals(alphas.sorted(), alphas, "each choice is stronger than the one before")
        assertEquals(0, ClockBackdrop.Clear.alpha)
        assertTrue(ClockBackdrop.Strong.alpha in 150..230, "strong is strong but not solid: ${ClockBackdrop.Strong.alpha}")
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
            .putString("5.style", "Hologram").putString("5.format", "Julian").putString("5.backdrop", "Frosted").commit()
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
