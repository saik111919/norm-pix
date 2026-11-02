package com.example.normpix.widgets

/**
 * Where the watch dial widget's text goes in a widget [widthDp] x [heightDp]. The face is the largest circle that fits, up to
 * [MAX_FACE_DP] (the picture of the rings is drawn at a fixed size and scaled), in the middle; the hour sits in the face's
 * middle and the minute in the pill at three o'clock, both sized with the face.
 *
 * The numbers are the face's ([WatchDialFace]): a face of 200 units, the hour's digits 30 units tall and the minute's 11, the
 * pill's middle 151 units from the face's left edge.
 */
class ClockFace(widthDp: Float, heightDp: Float) {
    /** How wide the face is drawn. */
    val sizeDp: Float = minOf(widthDp, heightDp, MAX_FACE_DP).coerceAtLeast(0f)

    private val unit = sizeDp / FACE_UNITS

    /** The hour's text size (in dp, so the face, not the phone's text size, decides it). */
    val hourTextDp: Float = HOUR_DIGIT_UNITS * unit / DIGIT_HEIGHT_EM

    /** The minute's text size. */
    val minuteTextDp: Float = MINUTE_DIGIT_UNITS * unit / DIGIT_HEIGHT_EM

    /** How far right of the middle the minute's middle is: the pill's middle. */
    val minuteOffsetDp: Float = (PILL_MIDDLE_UNITS - FACE_UNITS / 2) * unit

    /**
     * The left padding of the box the minute is centred in: twice the offset, so the centre of what is left moves right by
     * the offset whatever the widget's width - and so whatever padding a launcher puts round it.
     */
    val minuteBoxPaddingDp: Float = 2 * minuteOffsetDp

    /** The second's text size: smaller than the minute's, so the minute stays the number the pill is for. */
    val secondTextDp: Float = SECOND_DIGIT_UNITS * unit / DIGIT_HEIGHT_EM

    /** How far right of the middle the second's middle is: where the ring's number for it passes the pointer. */
    val secondOffsetDp: Float = (SECOND_MIDDLE_UNITS - FACE_UNITS / 2) * unit

    /** The left padding of the box the second is centred in (see [minuteBoxPaddingDp]). */
    val secondBoxPaddingDp: Float = 2 * secondOffsetDp

    /**
     * The top padding that puts a text's digits, rather than its line, in the middle: GS Flex's line sits a little low for
     * digits (0.026 em), and a centred view moves its content down by half its top padding.
     */
    fun digitsPaddingDp(textDp: Float): Float = DIGIT_DROP_EM * textDp

    companion object {
        const val MAX_FACE_DP = 300f
        const val FACE_UNITS = 200f
        const val HOUR_DIGIT_UNITS = 30f
        const val MINUTE_DIGIT_UNITS = 11f
        const val PILL_MIDDLE_UNITS = 144.3f
        const val SECOND_DIGIT_UNITS = 8f

        /**
         * Where the second's middle is: right of the accent dash and left of the seconds ring's numbers (which pass at
         * [WatchDialFace.OUTER_NUMBER_RADIUS] and are 8.6 units across there), so the ring is never covered. The minute (11 units
         * tall, up to 19.6 wide), the dash, and the second (8 tall, up to 14.3 wide) fit between the hour and the ring's numbers.
         */
        const val SECOND_MIDDLE_UNITS = 170.3f

        /** A digit's height in GS Flex, as a share of the text size. */
        const val DIGIT_HEIGHT_EM = 0.7326f
        const val DIGIT_DROP_EM = 0.053f
    }
}
