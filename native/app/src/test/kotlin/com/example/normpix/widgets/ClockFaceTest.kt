package com.example.normpix.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The clock widget's text goes where the face drawables put the hour and the pill, at every size. */
class ClockFaceTest {
    @Test
    fun `the face is the largest circle that fits, and never larger than its drawing`() {
        assertEquals(110f, ClockFace(110f, 180f).sizeDp)
        assertEquals(150f, ClockFace(240f, 150f).sizeDp)
        assertEquals(ClockFace.MAX_FACE_DP, ClockFace(400f, 320f).sizeDp)
    }

    @Test
    fun `the text grows with the face, the hour much larger than the minute`() {
        val small = ClockFace(100f, 100f)
        val large = ClockFace(200f, 200f)
        assertEquals(2f, large.hourTextDp / small.hourTextDp, 0.001f)
        assertEquals(2f, large.minuteTextDp / small.minuteTextDp, 0.001f)
        assertTrue(large.hourTextDp > 2.5f * large.minuteTextDp)
        // The hour's digits are 30 of the face's 200 units: 30 dp tall on a 200 dp face.
        assertEquals(30f, large.hourTextDp * ClockFace.DIGIT_HEIGHT_EM, 0.01f)
    }

    @Test
    fun `the minute sits in the pill, right of the middle, whatever the widget's shape`() {
        val face = ClockFace(200f, 200f)
        assertEquals(53f, face.minuteOffsetDp, 0.01f)
        // Padding a centred box on its left by twice the offset moves its middle by the offset.
        assertEquals(2 * face.minuteOffsetDp, face.minuteBoxPaddingDp)
        assertEquals(ClockFace(200f, 200f).minuteOffsetDp, ClockFace(300f, 200f).minuteOffsetDp)
        assertTrue(face.digitsPaddingDp(face.hourTextDp) in 1f..4f)
    }
}
