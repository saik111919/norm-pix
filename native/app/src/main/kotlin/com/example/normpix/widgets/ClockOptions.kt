package com.example.normpix.widgets

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.min

/** How a digital clock writes the time. */
enum class ClockTimeFormat(val label: String) {
    /** 12 or 24 hours as the phone is set. */
    System("As the phone is set"),
    Hours12("12-hour"),
    Hours24("24-hour"),
}

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
    val style: ClockStyle = ClockStyle.Face,
    /** A face too small to read (a widget of one cell, or a narrow strip) is replaced by the digital clock. */
    val digitalWhenSmall: Boolean = true,
    val format: ClockTimeFormat = ClockTimeFormat.System,
    val showSeconds: Boolean = false,
    val showDate: Boolean = false,
) {
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
        private const val STYLE = "style"
        private const val WHEN_SMALL = "when_small"
        private const val FORMAT = "format"
        private const val SECONDS = "seconds"
        private const val DATE = "date"
    }
}
