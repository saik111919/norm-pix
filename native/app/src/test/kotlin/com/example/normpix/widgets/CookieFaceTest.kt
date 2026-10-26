package com.example.normpix.widgets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The cookie digital clock's text goes where the face drawable puts the hour, the minute and the date, at every size. */
class CookieFaceTest {
    /** How far a text's line sits from its digits' middle, in dp: the padding corrects half of what it is padded by. */
    private fun drop(text: CookieFace.FaceText) = CookieFace.DIGIT_DROP_EM * text.sizeDp / 2

    @Test
    fun `the face is the largest square that fits, and grows with the widget`() {
        assertEquals(110f, CookieFace(110f, 180f).sizeDp)
        assertEquals(150f, CookieFace(240f, 150f).sizeDp)
        // Nothing caps it: the digital clock has no AnalogClock to draw it no larger than its drawing.
        assertEquals(400f, CookieFace(400f, 500f).sizeDp)
        assertEquals(0f, CookieFace(-5f, 100f).sizeDp)
    }

    @Test
    fun `the text grows with the face, the hour and the minute far larger than the date`() {
        val small = CookieFace(100f, 100f)
        val large = CookieFace(200f, 200f)
        assertEquals(2f, large.hour.sizeDp / small.hour.sizeDp, 0.001f)
        assertEquals(2f, large.minute.sizeDp / small.minute.sizeDp, 0.001f)
        assertEquals(2f, large.date.sizeDp / small.date.sizeDp, 0.001f)
        assertTrue(large.hour.sizeDp > 3.5f * large.date.sizeDp)
        // The hour's digits are 44 of the face's 200 units: 44 dp tall on a 200 dp face.
        assertEquals(44f, large.hour.sizeDp * CookieFace.DIGIT_HEIGHT_EM, 0.01f)
        assertEquals(10f, large.date.sizeDp * CookieFace.DIGIT_HEIGHT_EM, 0.01f)
    }

    @Test
    fun `the hour sits above the middle, the minute and the date below it`() {
        val face = CookieFace(200f, 200f)
        // A centred text moves by half its padding: the hour, above the middle, is padded below; the rest are padded above.
        assertEquals(0f, face.hour.topPaddingDp)
        assertTrue(face.hour.bottomPaddingDp > 0f)
        for (text in listOf(face.minute, face.date)) {
            assertEquals(0f, text.bottomPaddingDp)
            assertTrue(text.topPaddingDp > 0f)
        }
        // Their middles are 68, 122 and 160 of the face's 200 units from its top: 32 above the middle, then 22 and 60 below.
        assertEquals(32f, face.hour.bottomPaddingDp / 2 + drop(face.hour), 0.01f)
        assertEquals(22f, face.minute.topPaddingDp / 2 - drop(face.minute), 0.01f)
        assertEquals(60f, face.date.topPaddingDp / 2 - drop(face.date), 0.01f)
    }

    @Test
    fun `the texts keep their places on the face whatever its size`() {
        val small = CookieFace(110f, 110f)
        val large = CookieFace(220f, 220f)
        for ((a, b) in listOf(small.hour to large.hour, small.minute to large.minute, small.date to large.date)) {
            assertEquals(2 * a.topPaddingDp, b.topPaddingDp, 0.001f)
            assertEquals(2 * a.bottomPaddingDp, b.bottomPaddingDp, 0.001f)
        }
    }
}
