package com.example.normpix.wallet.motion

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TiltFilterTest {
    private val dt = 0.02f // a 50 Hz sensor

    /** Feeds a steady attitude for [seconds]; returns whether any sample moved the card enough to redraw. */
    private fun TiltFilter.hold(pitch: Float, roll: Float, seconds: Float): Boolean {
        var moved = false
        repeat((seconds / dt).toInt()) { if (update(pitch, roll, dt)) moved = true }
        return moved
    }

    @Test
    fun theFirstSampleOnlySetsTheRestingPose() {
        val filter = TiltFilter()
        assertFalse(filter.update(63f, -4f, 0f))
        assertEquals(0f, filter.pitch)
        assertEquals(0f, filter.roll)
    }

    @Test
    fun aPhoneHeldStillStaysFlatHoweverItIsHeld() {
        for ((pitch, roll) in listOf(0f to 0f, 60f to 0f, 85f to 12f, -30f to -20f)) {
            val filter = TiltFilter()
            filter.update(pitch, roll, 0f)
            assertFalse(filter.hold(pitch, roll, 5f), "a steady phone at ($pitch, $roll) never redraws the card")
            assertEquals(0f, filter.pitch)
            assertEquals(0f, filter.roll)
        }
    }

    @Test
    fun movingThePhoneLeansTheCardButNeverBeyondTheLimit() {
        val filter = TiltFilter()
        filter.update(60f, 0f, 0f)
        filter.hold(60f + 8f, 0f, 0.3f) // a gentle tilt of 8 degrees
        assertTrue(filter.pitch > 1f && filter.pitch < MAX_CARD_TILT_DEGREES, "leans: ${filter.pitch}")

        filter.hold(60f + 90f, 40f, 0.3f) // a violent one
        assertTrue(filter.pitch in (MAX_CARD_TILT_DEGREES - 0.5f)..MAX_CARD_TILT_DEGREES, "pinned at the limit: ${filter.pitch}")
        assertTrue(abs(filter.roll) <= MAX_CARD_TILT_DEGREES + 0.001f)
    }

    @Test
    fun theCardSettlesBackToFlatOnceThePhoneStopsMoving() {
        val filter = TiltFilter()
        filter.update(60f, 0f, 0f)
        filter.hold(70f, 5f, 0.3f)
        assertTrue(abs(filter.pitch) > 0.5f)

        // Held at the new angle: the resting pose follows it and the card relaxes, then nothing moves any more.
        filter.hold(70f, 5f, 8f)
        assertEquals(0f, filter.pitch)
        assertEquals(0f, filter.roll)
        assertFalse(filter.hold(70f, 5f, 2f), "a settled card is not redrawn")
    }

    @Test
    fun tremorInsideTheDeadZoneDoesNotMoveTheCard() {
        val filter = TiltFilter()
        filter.update(60f, 0f, 0f)
        var redraws = 0
        repeat(200) { i ->
            val wobble = if (i % 2 == 0) 0.3f else -0.3f // +-0.3 degrees, scaled by the gain it is below the dead zone
            if (filter.update(60f + wobble, wobble, dt)) redraws++
        }
        assertEquals(0, redraws)
        assertEquals(0f, filter.pitch)
    }

    @Test
    fun theMotionIsSmoothedNotJumped() {
        val filter = TiltFilter()
        filter.update(60f, 0f, 0f)
        filter.update(70f, 0f, dt)
        val afterOneSample = filter.pitch
        assertTrue(afterOneSample > 0f && afterOneSample < 5f, "a 10 degree step is not followed in one sample: $afterOneSample")
    }

    @Test
    fun anglesWrapTheShortWay() {
        val filter = TiltFilter()
        filter.update(179f, 0f, 0f)
        filter.hold(-179f, 0f, 0.3f) // 2 degrees of real movement, not 358
        assertTrue(abs(filter.pitch) < 1.5f, "wrapped: ${filter.pitch}")
    }

    @Test
    fun resetForgetsTheRestingPose() {
        val filter = TiltFilter()
        filter.update(60f, 0f, 0f)
        filter.hold(75f, 0f, 0.3f)
        filter.reset()
        assertEquals(0f, filter.pitch)
        assertFalse(filter.update(10f, 0f, dt), "the next sample primes again")
    }
}

class AttitudeTest {
    private fun up(pitchDeg: Double, rollDeg: Double): Triple<Float, Float, Float> {
        // The up vector of a phone leaned [pitchDeg] back and [rollDeg] to the side.
        val ux = Math.sin(Math.toRadians(rollDeg))
        val rest = Math.cos(Math.toRadians(rollDeg))
        return Triple(ux.toFloat(), (rest * Math.cos(Math.toRadians(pitchDeg))).toFloat(), (rest * Math.sin(Math.toRadians(pitchDeg))).toFloat())
    }

    @Test
    fun anUprightPhoneIsAtZero() {
        assertEquals(0f, pitchDegrees(uy = 1f, uz = 0f), 0.001f)
        assertEquals(0f, rollDegrees(0f), 0.001f)
    }

    @Test
    fun aPhoneLyingFaceUpIsNinetyDegreesBack() {
        assertEquals(90f, pitchDegrees(uy = 0f, uz = 1f), 0.001f)
    }

    @Test
    fun leaningForwardIsNegative() {
        assertTrue(pitchDegrees(uy = 0.9f, uz = -0.4f) < 0f)
    }

    @Test
    fun theTwoLeansAreIndependentAndRoundTrip() {
        for ((pitch, roll) in listOf(30.0 to 0.0, 60.0 to 10.0, 75.0 to -15.0, 10.0 to 25.0)) {
            val (ux, uy, uz) = up(pitch, roll)
            assertEquals(pitch.toFloat(), pitchDegrees(uy, uz), 0.05f, "pitch of ($pitch, $roll)")
            assertEquals(roll.toFloat(), rollDegrees(ux), 0.05f, "roll of ($pitch, $roll)")
        }
    }

    @Test
    fun aSlightlyOverUnitReadingDoesNotBreakTheArcsine() {
        assertEquals(90f, rollDegrees(1.0001f), 0.001f)
    }
}
