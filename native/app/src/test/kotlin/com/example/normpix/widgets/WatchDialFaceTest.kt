package com.example.normpix.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the watch dial's numbers go, pinned to the watch face it follows: at 12:45 the minutes ring has 45 at the pill (three
 * o'clock), 00 at the top, 15 at the left and 30 at the bottom, and the seconds ring has 00 at the pill, 15 at the top, 30 at
 * the left and 45 at the bottom.
 */
class WatchDialFaceTest {
    @Test
    fun `at 12 45 the minutes ring reads as the watch face does`() {
        assertEquals(90f, WatchDialFace.innerAngle(45, 45))
        assertEquals(0f, WatchDialFace.innerAngle(0, 45), "00 at the top")
        assertEquals(270f, WatchDialFace.innerAngle(15, 45), "15 at the left")
        assertEquals(180f, WatchDialFace.innerAngle(30, 45), "30 at the bottom")
    }

    @Test
    fun `the seconds ring stands still, with 00 at the pill`() {
        assertEquals(90f, WatchDialFace.outerAngle(0))
        assertEquals(0f, WatchDialFace.outerAngle(15), "15 at the top")
        assertEquals(270f, WatchDialFace.outerAngle(30), "30 at the left")
        assertEquals(180f, WatchDialFace.outerAngle(45), "45 at the bottom")
    }

    @Test
    fun `whatever the minute, it is the number at the pill`() {
        for (minute in 0 until 60) assertEquals(90f, WatchDialFace.innerAngle(minute, minute), "minute $minute")
    }

    @Test
    fun `the minutes ring turns clockwise, six degrees a minute`() {
        for (minute in 0 until 59) {
            for (value in 0 until 60 step 5) {
                val turned = WatchDialFace.innerAngle(value, minute + 1) - WatchDialFace.innerAngle(value, minute)
                assertEquals(6f, if (turned < 0) turned + 360f else turned, 0.001f, "$value from $minute")
            }
        }
    }

    @Test
    fun `every angle is a turn or less, clockwise from twelve`() {
        for (minute in 0 until 60) for (value in 0 until 60) {
            assertTrue(WatchDialFace.innerAngle(value, minute) in 0f..359.999f)
            assertTrue(WatchDialFace.outerAngle(value) in 0f..359.999f)
        }
    }

    @Test
    fun `a number that comes round to the pill fades out before it reaches the pill's rim`() {
        // At the pill, and a minute either side of it (the picture drawn a moment early or late), and well short of the rim: nothing.
        for (angle in listOf(90f, 96f, 84f, 100f, 78f, 108f)) assertEquals(0f, WatchDialFace.innerNumberOpacity(angle), "at $angle")
        // Whole again by the time it is as far as the next number is, 30 degrees away.
        for (angle in listOf(120f, 60f, 0f, 270f, 180f)) assertEquals(1f, WatchDialFace.innerNumberOpacity(angle), "at $angle")
        // And in between it is partly there.
        assertEquals(0.5f, WatchDialFace.innerNumberOpacity(115f), 0.001f)
        assertTrue(WatchDialFace.innerNumberOpacity(113f) < WatchDialFace.innerNumberOpacity(117f))
    }

    @Test
    fun `the pill runs from its left end past the face's edge, and the minute sits in it`() {
        assertTrue(WatchDialFace.PILL_LEFT > WatchDialFace.MIDDLE, "right of the middle")
        assertTrue(ClockFace.PILL_MIDDLE_UNITS > WatchDialFace.PILL_LEFT + WatchDialFace.PILL_HALF_HEIGHT, "the minute is inside the pill")
        assertTrue(ClockFace.PILL_MIDDLE_UNITS < WatchDialFace.INNER_TICK_IN_MAJOR + WatchDialFace.MIDDLE, "and left of the minute's tick")
        // The seconds ring's 00 is inside the pill too, beyond the minute.
        assertTrue(WatchDialFace.MIDDLE + WatchDialFace.OUTER_NUMBER_RADIUS > ClockFace.PILL_MIDDLE_UNITS + 10)
    }
}
