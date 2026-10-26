package com.example.normpix.widgets

import kotlin.math.abs

/**
 * The watch dial's geometry, in a face of 200 units with its middle at (100, 100), measured from the watch face it follows: two
 * rings of five-minute numbers, each with its 60 ticks, a large hour in the middle and a pill at three o'clock that the current
 * minute sits in. The outer ring is the seconds ring and stays where it is at :00; the inner ring is the minutes ring and turns
 * one minute a minute, so that the minute in the pill is the number that has come round to it. Every number stays upright.
 *
 * [WatchDialRenderer] draws it; `design/cookie-clock/CookieFace.java` draws the same face as the picker's preview (it reads
 * the same numbers - keep them in step), and [ClockFace] places the hour and the minute, which are text the launcher keeps.
 * Angles are degrees clockwise from twelve.
 */
object WatchDialFace {
    const val UNITS = 200f
    const val MIDDLE = 100f

    /** The outer (seconds) ring: ticks at the edge, numbers inside them. */
    const val OUTER_TICK_OUT = 99.5f
    const val OUTER_TICK_IN_MINOR = 94.5f
    const val OUTER_TICK_IN_MAJOR = 90.5f
    const val OUTER_NUMBER_RADIUS = 82.4f
    const val OUTER_NUMBER_SIZE = 11.7f

    /** The inner (minutes) ring. */
    const val INNER_TICK_OUT = 69f
    const val INNER_TICK_IN_MINOR = 64.5f
    const val INNER_TICK_IN_MAJOR = 61f
    const val INNER_NUMBER_RADIUS = 54f
    const val INNER_NUMBER_SIZE = 12.8f

    /** The pill: from [PILL_LEFT] out past the edge of the face, which clips it, so it is open at the right. */
    const val PILL_LEFT = 128.6f
    const val PILL_HALF_HEIGHT = 15.2f

    /** Stroke widths. */
    const val OUTER_MINOR_STROKE = 0.9f
    const val OUTER_MAJOR_STROKE = 1.6f
    const val INNER_MINOR_STROKE = 0.8f
    const val INNER_MAJOR_STROKE = 1.4f
    const val ACTIVE_STROKE = 1.9f
    const val PILL_STROKE = 1.3f

    /**
     * An inner number that comes round to the pill would run under its rim and its text, so it fades out before it gets there: gone
     * within [NUMBER_CLEARANCE_DEGREES] of the pill, whole again [NUMBER_FADE_DEGREES] further out (the next number is 30 away).
     */
    const val NUMBER_CLEARANCE_DEGREES = 22f
    const val NUMBER_FADE_DEGREES = 6f

    /** Where the pill is: three o'clock. */
    const val PILL_ANGLE = 90f

    /** Where the minute [value] sits on the inner ring when it is [minute] past the hour: at the pill when it is the minute. */
    fun innerAngle(value: Int, minute: Int): Float = normalized(PILL_ANGLE - 6f * (value - minute))

    /** Where [value] sits on the outer ring, which doesn't turn. */
    fun outerAngle(value: Int): Float = normalized(PILL_ANGLE - 6f * value)

    /** How much of the inner number at [angle] shows, 0 to 1: none near the pill, all of it from [NUMBER_FADE_DEGREES] past its clearance. */
    fun innerNumberOpacity(angle: Float): Float =
        ((angularDistance(angle, PILL_ANGLE) - NUMBER_CLEARANCE_DEGREES) / NUMBER_FADE_DEGREES).coerceIn(0f, 1f)

    private fun normalized(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    private fun angularDistance(a: Float, b: Float): Float {
        val d = abs(normalized(a) - normalized(b))
        return if (d > 180f) 360f - d else d
    }
}
