package com.example.normpix.widgets

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.core.content.edit
import kotlin.math.min

/** How a digital clock writes the time. */
enum class ClockTimeFormat(val label: String) {
    /** 12 or 24 hours as the phone is set. */
    System("As the phone is set"),
    Hours12("12-hour"),
    Hours24("24-hour"),
}

/**
 * The four strengths of tint a transparent clock could keep behind it before the strength became a slider ([ClockOptions.backdropPercent]).
 * A widget cannot blur the wallpaper under it (Android gives it no way to), so what is offered instead is a tint of the clock's own
 * face colour: its marks and digits are made to read on that colour, so a stronger tint keeps them readable on a bright or busy
 * wallpaper. Still used to read what an earlier version saved (by name), to keep saving the nearest one by name for an earlier
 * version, and to say in words what a strength on the slider is for.
 */
enum class ClockBackdrop(val label: String, val description: String, val percent: Int) {
    Clear("Clear", "Nothing behind the clock", 0),
    Soft("Soft", "A light tint", 30),
    Medium("Medium", "Readable on most wallpapers", 55),
    Strong("Strong", "For bright or busy wallpapers", 80),
    ;

    /** The tint as an alpha, 0 to 255, for the face colour. */
    val alpha: Int get() = tintAlpha(percent)

    companion object {
        /** The strength nearest [percent]: [Clear] only for none at all, so any tint at all is described as one. */
        fun nearest(percent: Int): ClockBackdrop = when {
            percent <= 0 -> Clear
            else -> entries.drop(1).minBy { kotlin.math.abs(it.percent - percent) }
        }
    }
}

/** A tint of [percent] (0 to 100) as an alpha, 0 to 255. */
fun tintAlpha(percent: Int): Int = (percent.coerceIn(0, 100) * 255 / 100.0).toInt()

/** What a clock widget shows: its face, or the plain digital clock. */
enum class ClockStyle(val label: String) {
    Face("Clock face"),
    Digital("Digital clock"),
}

/**
 * What the user chose for one clock widget in Android's own widget settings (press and hold the widget, Widget settings): see
 * [ClockWidgetConfigActivity]. Every clock has them, so a widget is told apart by its home-screen id, not by its kind.
 *
 * The plain digital clock is the clock with nothing added: the time in one size and no face. It stands in for a face that has
 * become too small to read ([digitalWhenSmall]), or is the widget's face for good ([style] Digital).
 */
data class ClockOptions(
    /** The face's background is left out, so the wallpaper shows through; the marks, hands and digits stay as they are. */
    val transparent: Boolean = false,
    /**
     * With [transparent]: how much tint of the face's colour stays behind the clock, 0 (none: the default, so a transparent clock
     * chosen before this existed looks as it did) to 100 (the face whole). Without it the face is whole, so this says nothing.
     */
    val backdropPercent: Int = 0,
    val style: ClockStyle = ClockStyle.Face,
    /** A face too small to read (a widget of one cell, or a narrow strip) is replaced by the digital clock. */
    val digitalWhenSmall: Boolean = true,
    val format: ClockTimeFormat = ClockTimeFormat.System,
    val showSeconds: Boolean = false,
    val showDate: Boolean = false,
) {
    /** The alpha of the tint behind a transparent clock, 0 to 255: 0 for a clear one, and [backdropPercent] of it. Meaningless (0) when the face is whole. */
    val backdropAlpha: Int get() = if (transparent) tintAlpha(backdropPercent) else 0

    /** The words for the tint chosen: what [ClockBackdrop] it is nearest. */
    val backdrop: ClockBackdrop get() = ClockBackdrop.nearest(backdropPercent)

    /** Whether a widget [widthDp] x [heightDp] shows the digital clock rather than its face. */
    fun showsDigital(widthDp: Float, heightDp: Float): Boolean =
        style == ClockStyle.Digital || (digitalWhenSmall && isTooSmallForFace(widthDp, heightDp))

    companion object {
        /**
         * The least a face may be across, in dp, before its ticks and hands are too small to tell: one cell of the home screen
         * is about 40 to 70 dp, two cells 110 or more, so a clock of one cell, or a two by one strip, gives way to the digits.
         */
        const val SMALL_FACE_DP = 92f

        fun isTooSmallForFace(widthDp: Float, heightDp: Float): Boolean = min(widthDp, heightDp) < SMALL_FACE_DP
    }
}

/**
 * The picture behind a clock's marks (the cookie, the digital clock's rounded rectangle), for a clock the user made transparent: left
 * out for a clear one, or kept at the strength they chose so the marks stay readable on a bright wallpaper. A clock that is not
 * transparent keeps it whole, so nothing is done.
 */
internal fun RemoteViews.tintBehind(@IdRes fill: Int, clock: ClockOptions) {
    if (!clock.transparent) return
    if (clock.backdropAlpha == 0) setViewVisibility(fill, View.GONE) else setInt(fill, "setImageAlpha", clock.backdropAlpha)
}

/**
 * The options of every clock widget on the home screen, kept on the phone by widget id (SharedPreferences `normpix_widget_clocks`).
 * Nothing in it is private - no ledger, no card, no text of the user's - and it is not part of a backup: a widget's id means
 * nothing on another phone.
 */
class ClockOptionsStore(private val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))

    fun get(appWidgetId: Int): ClockOptions {
        val defaults = ClockOptions()
        fun key(name: String) = "$appWidgetId.$name"
        return ClockOptions(
            transparent = prefs.getBoolean(key(TRANSPARENT), defaults.transparent),
            // The strength as a number since it became a slider; before that, one of four by name, which still reads as it was.
            backdropPercent = if (prefs.contains(key(BACKDROP_PERCENT))) {
                prefs.getInt(key(BACKDROP_PERCENT), defaults.backdropPercent).coerceIn(0, 100)
            } else {
                prefs.enum(key(BACKDROP), ClockBackdrop.Clear).percent
            },
            style = prefs.enum(key(STYLE), defaults.style),
            digitalWhenSmall = prefs.getBoolean(key(WHEN_SMALL), defaults.digitalWhenSmall),
            format = prefs.enum(key(FORMAT), defaults.format),
            showSeconds = prefs.getBoolean(key(SECONDS), defaults.showSeconds),
            showDate = prefs.getBoolean(key(DATE), defaults.showDate),
        )
    }

    fun set(appWidgetId: Int, options: ClockOptions) {
        fun key(name: String) = "$appWidgetId.$name"
        prefs.edit {
            putBoolean(key(TRANSPARENT), options.transparent)
            putInt(key(BACKDROP_PERCENT), options.backdropPercent.coerceIn(0, 100))
            // The nearest of the old four too, so an earlier version (an older build restored over this one) still finds one.
            putString(key(BACKDROP), options.backdrop.name)
            putString(key(STYLE), options.style.name)
            putBoolean(key(WHEN_SMALL), options.digitalWhenSmall)
            putString(key(FORMAT), options.format.name)
            putBoolean(key(SECONDS), options.showSeconds)
            putBoolean(key(DATE), options.showDate)
        }
    }

    /** Forgets the options of widgets that have been taken off the home screen. */
    fun remove(appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        val gone = appWidgetIds.map { "$it." }
        prefs.edit { for (key in prefs.all.keys) if (gone.any { key.startsWith(it) }) remove(key) }
    }

    private inline fun <reified E : Enum<E>> SharedPreferences.enum(key: String, default: E): E =
        getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    companion object {
        const val FILE = "normpix_widget_clocks"
        private const val TRANSPARENT = "transparent"
        private const val BACKDROP = "backdrop"
        private const val BACKDROP_PERCENT = "backdrop_percent"
        private const val STYLE = "style"
        private const val WHEN_SMALL = "when_small"
        private const val FORMAT = "format"
        private const val SECONDS = "seconds"
        private const val DATE = "date"
    }
}
