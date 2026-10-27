package com.example.normpix.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import com.example.normpix.R
import com.example.normpix.widgets.WatchDialFace.ACTIVE_STROKE
import com.example.normpix.widgets.WatchDialFace.INNER_MAJOR_STROKE
import com.example.normpix.widgets.WatchDialFace.INNER_MINOR_STROKE
import com.example.normpix.widgets.WatchDialFace.INNER_NUMBER_RADIUS
import com.example.normpix.widgets.WatchDialFace.INNER_NUMBER_SIZE
import com.example.normpix.widgets.WatchDialFace.INNER_TICK_IN_MAJOR
import com.example.normpix.widgets.WatchDialFace.INNER_TICK_IN_MINOR
import com.example.normpix.widgets.WatchDialFace.INNER_TICK_OUT
import com.example.normpix.widgets.WatchDialFace.MIDDLE
import com.example.normpix.widgets.WatchDialFace.PILL_ANGLE
import com.example.normpix.widgets.WatchDialFace.PILL_HALF_HEIGHT
import com.example.normpix.widgets.WatchDialFace.PILL_LEFT
import com.example.normpix.widgets.WatchDialFace.PILL_STROKE
import com.example.normpix.widgets.WatchDialFace.UNITS
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the watch dial's face and minutes ring as a picture ([WatchDialFace] says where everything goes): the numbers upright,
 * the inner ring turned to [minute], the pill open at the right edge. Not in it: the hour in the middle and the minute in the pill
 * (text the launcher keeps on the time, so they are right even when the picture is a moment behind) and the seconds ring (an
 * animation the launcher runs over the picture: see `design/cookie-clock/CookieFace.java`, `widget_dial_seconds_NN`).
 *
 * The colours are the widget's own (`widget_clock_*`, light and night), read from [Context] as the system has it now. A
 * transparent dial has no face: what is left is the ring, and the pill, which clears what lies under it.
 */
object WatchDialRenderer {
    /** A digit's height in GS Flex, as a share of the text size (the same number [ClockFace] uses). */
    private const val DIGIT_HEIGHT_EM = ClockFace.DIGIT_HEIGHT_EM

    fun render(context: Context, minute: Int, sizePx: Int, transparent: Boolean = false): Bitmap {
        val m = ((minute % 60) + 60) % 60
        val face = context.getColor(R.color.widget_clock_face)
        val secondary = context.getColor(R.color.widget_clock_secondary)
        val tick = context.getColor(R.color.widget_clock_tick)
        val accent = context.getColor(R.color.widget_clock_accent)
        val major = ColorUtils.setAlphaComponent(secondary, 190)
        val border = ColorUtils.setAlphaComponent(secondary, 150)
        val font = ResourcesCompat.getFont(context, R.font.gs_flex) ?: Typeface.DEFAULT

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        canvas.scale(sizePx / UNITS, sizePx / UNITS)
        val lines = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            textAlign = Paint.Align.CENTER
            fontVariationSettings = "'wght' 480"
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        // The face; what is drawn after it is clipped to it, which is what leaves the pill open at the right.
        if (!transparent) {
            fill.color = face
            canvas.drawCircle(MIDDLE, MIDDLE, UNITS / 2, fill)
        }
        canvas.clipPath(Path().apply { addCircle(MIDDLE, MIDDLE, UNITS / 2, Path.Direction.CW) })

        // The minutes ring: it turns, a minute at a time.
        for (i in 0 until 60) {
            val big = i % 5 == 0
            lines.color = if (big) major else tick
            lines.strokeWidth = if (big) INNER_MAJOR_STROKE else INNER_MINOR_STROKE
            canvas.line(lines, WatchDialFace.innerAngle(i, m), if (big) INNER_TICK_IN_MAJOR else INNER_TICK_IN_MINOR, INNER_TICK_OUT)
        }
        for (value in 0 until 60 step 5) {
            val angle = WatchDialFace.innerAngle(value, m)
            val opacity = WatchDialFace.innerNumberOpacity(angle)
            if (opacity > 0f) {
                canvas.number(text, "%02d".format(value), angle, INNER_NUMBER_RADIUS, INNER_NUMBER_SIZE, ColorUtils.setAlphaComponent(secondary, (Color.alpha(secondary) * opacity).roundToInt()))
            }
        }

        // The pill over it, filled with the face so the ring's ticks pass under (a transparent dial clears them instead); open at
        // the right, where the face clips it.
        val pill = RectF(PILL_LEFT, MIDDLE - PILL_HALF_HEIGHT, MIDDLE + UNITS / 2 + PILL_HALF_HEIGHT + 2, MIDDLE + PILL_HALF_HEIGHT)
        if (transparent) {
            fill.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            canvas.drawRoundRect(pill, PILL_HALF_HEIGHT, PILL_HALF_HEIGHT, fill)
            fill.xfermode = null
        } else {
            fill.color = face
            canvas.drawRoundRect(pill, PILL_HALF_HEIGHT, PILL_HALF_HEIGHT, fill)
        }
        lines.color = border
        lines.strokeWidth = PILL_STROKE
        canvas.drawRoundRect(pill, PILL_HALF_HEIGHT, PILL_HALF_HEIGHT, lines)

        // The tick of the minute that is in the pill, in the accent: the dash between its number and the seconds ring's number.
        lines.color = accent
        lines.strokeWidth = ACTIVE_STROKE
        canvas.line(lines, PILL_ANGLE, INNER_TICK_IN_MAJOR, INNER_TICK_OUT)
        return bitmap
    }

    /** A line along the radius at [angle], from [from] to [to] units out. */
    private fun Canvas.line(paint: Paint, angle: Float, from: Float, to: Float) {
        val a = Math.toRadians(angle.toDouble())
        val s = sin(a).toFloat()
        val c = cos(a).toFloat()
        drawLine(MIDDLE + from * s, MIDDLE - from * c, MIDDLE + to * s, MIDDLE - to * c, paint)
    }

    /** [value] upright, with its digits' middle at [radius] units out along [angle]. */
    private fun Canvas.number(paint: Paint, value: String, angle: Float, radius: Float, size: Float, color: Int) {
        val a = Math.toRadians(angle.toDouble())
        paint.textSize = size
        paint.color = color
        drawText(value, MIDDLE + radius * sin(a).toFloat(), MIDDLE - radius * cos(a).toFloat() + size * DIGIT_HEIGHT_EM / 2, paint)
    }
}
