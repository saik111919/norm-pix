package com.example.normpix.widgets

/**
 * Where the cookie digital clock's text goes in a widget [widthDp] x [heightDp]: the face is the largest square that fits, in
 * the middle, and the hour, the minute and the date sit on it as [CookieFace.hour], [CookieFace.minute] and [CookieFace.date],
 * sized with the face.
 *
 * The numbers are the face drawable's (design/cookie-clock/CookieFace.java): a face of 200 units, the hour's and the minute's
 * digits 44 units tall with their middles 68 and 122 units from the top, the date's 10 tall at 160.
 */
class CookieFace(widthDp: Float, heightDp: Float) {
    /** How wide the face is drawn. It scales up with the widget: the digital clock has no AnalogClock to cap it. */
    val sizeDp: Float = minOf(widthDp, heightDp).coerceAtLeast(0f)

    private val unit = sizeDp / FACE_UNITS

    val hour = text(HOUR_DIGIT_UNITS, HOUR_MIDDLE_UNITS)
    val minute = text(MINUTE_DIGIT_UNITS, MINUTE_MIDDLE_UNITS)
    val date = text(DATE_DIGIT_UNITS, DATE_MIDDLE_UNITS)

    /**
     * One text of the face: its size (in dp, so the face, not the phone's text size, decides it) and the padding that puts its
     * digits - rather than its line - at [middleUnits] from the face's top. The text is centred in the widget, and a centred
     * view moves its content by half its padding, so a text that sits above the middle is padded below and one under it above.
     */
    private fun text(digitUnits: Float, middleUnits: Float): FaceText {
        val textDp = digitUnits * unit / DIGIT_HEIGHT_EM
        val shiftDp = (middleUnits - FACE_UNITS / 2) * unit + DIGIT_DROP_EM * textDp / 2
        return FaceText(textDp, topPaddingDp = maxOf(0f, 2 * shiftDp), bottomPaddingDp = maxOf(0f, -2 * shiftDp))
    }

    /** A text of the face: [sizeDp] its size, the paddings (in dp) that move it from the middle of the widget to its place. */
    class FaceText(val sizeDp: Float, val topPaddingDp: Float, val bottomPaddingDp: Float)

    companion object {
        const val FACE_UNITS = 200f
        const val HOUR_DIGIT_UNITS = 44f
        const val MINUTE_DIGIT_UNITS = 44f
        const val DATE_DIGIT_UNITS = 10f
        const val HOUR_MIDDLE_UNITS = 68f
        const val MINUTE_MIDDLE_UNITS = 122f
        const val DATE_MIDDLE_UNITS = 160f

        /** A digit's height in GS Flex, as a share of the text size; and how far its line sits from the digits' middle (the clock widget's numbers). */
        const val DIGIT_HEIGHT_EM = ClockFace.DIGIT_HEIGHT_EM
        const val DIGIT_DROP_EM = ClockFace.DIGIT_DROP_EM
    }
}
