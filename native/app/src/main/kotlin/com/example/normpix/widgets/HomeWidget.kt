package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import com.example.normpix.R

/** How Settings > Widgets lists the widgets: the clocks together, and the two that work with the ledger. */
enum class HomeWidgetGroup(val title: String) {
    Clocks("Clocks"),
    Transactions("Transactions"),
}

/**
 * Every widget NormPix puts on the home screen, as Settings > Widgets lists them: what each is called on its pill, what it
 * says it is, and the size it is previewed at. The list is the one the widget picker shows (the receivers in the manifest);
 * `HomeWidgetTest` keeps the two the same.
 */
enum class HomeWidget(
    /** On its pill in Settings. */
    val pill: String,
    /** Over its preview. */
    val title: String,
    val description: String,
    val group: HomeWidgetGroup,
    /** The provider the launcher is asked to place. */
    val provider: Class<out AppWidgetProvider>,
    /** How big it is previewed, in dp: a 2 x 2 cookie, the 4 x 2 Recent... */
    val previewWidthDp: Float,
    val previewHeightDp: Float,
) {
    CookieAnalog(
        pill = "Cookie analog",
        title = "Cookie clock: analog",
        description = "Hour and minute hands and a sweeping second hand over a cookie-shaped face with a ring of ticks, and no numbers. " +
            "The second hand is Android 12 and later.",
        group = HomeWidgetGroup.Clocks,
        provider = CookieAnalogWidget::class.java,
        previewWidthDp = 168f,
        previewHeightDp = 168f,
    ),
    CookieNumbers(
        pill = "Cookie numbers",
        title = "Cookie clock: numbers",
        description = "The same cookie-shaped analog clock with the hours numbered 1 to 12, shorter hands to stay clear of them.",
        group = HomeWidgetGroup.Clocks,
        provider = CookieNumbersWidget::class.java,
        previewWidthDp = 168f,
        previewHeightDp = 168f,
    ),
    CookiePlain(
        pill = "Cookie plain",
        title = "Cookie clock: plain",
        description = "The cookie-shaped analog clock with only its hands: no ticks and no numbers, just the face and a sweeping second hand. " +
            "The second hand is Android 12 and later.",
        group = HomeWidgetGroup.Clocks,
        provider = CookiePlainWidget::class.java,
        previewWidthDp = 168f,
        previewHeightDp = 168f,
    ),
    CookieDigital(
        pill = "Cookie digital",
        title = "Cookie clock: digital",
        description = "The hour over the minute in big digits on a cookie-shaped face, and the date under them.",
        group = HomeWidgetGroup.Clocks,
        provider = CookieDigitalWidget::class.java,
        previewWidthDp = 168f,
        previewHeightDp = 168f,
    ),
    WatchDial(
        pill = "Watch dial",
        title = "Clock: watch dial",
        description = "The time on a face after a watch: the hour in the middle, the minute in its pill, and two rings of numbers round them. The inner ring steps with each minute and the outer ring turns with the seconds.",
        group = HomeWidgetGroup.Clocks,
        provider = ClockWidget::class.java,
        previewWidthDp = 168f,
        previewHeightDp = 168f,
    ),
    Recent(
        pill = "Recent",
        title = "Recent transactions",
        description = "Your latest transactions at a glance, and a button to add one. Nothing of your ledger shows while NormPix is locked.",
        group = HomeWidgetGroup.Transactions,
        provider = RecentWidget::class.java,
        previewWidthDp = 290f,
        previewHeightDp = 186f,
    ),
    Add(
        pill = "Quick add",
        title = "Add transaction",
        description = "Start an expense, an income or a transfer in one tap. One cell wide it is a single add button.",
        group = HomeWidgetGroup.Transactions,
        provider = AddWidget::class.java,
        previewWidthDp = 280f,
        previewHeightDp = 56f,
    ),
    ;

    /** The widget as it would look on the home screen, drawn for [previewWidthDp] x [previewHeightDp]. */
    fun previewViews(context: Context): RemoteViews = when (this) {
        CookieAnalog, CookieNumbers, CookiePlain, CookieDigital, WatchDial -> ClockWidgets.preview(context, this, previewWidthDp, previewHeightDp)
        // The picker's own preview: made-up transactions, since the real ones are not for showing on a settings page.
        Recent -> RemoteViews(context.packageName, R.layout.widget_recent_preview)
        Add -> AddWidget.wide(context)
    }

    /** The component the launcher is asked to place. */
    fun component(context: Context): ComponentName = ComponentName(context, provider)

    /** How many of this widget are on the home screen now. */
    fun placed(context: Context): Int =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(component(context))?.size ?: 0

    companion object {
        fun inGroup(group: HomeWidgetGroup): List<HomeWidget> = entries.filter { it.group == group }
    }
}

/**
 * Asking the launcher to place a widget (Android 8 and later): it shows its own "Add to home screen" and the user chooses where.
 * Nothing is placed behind the user's back, and a launcher that doesn't support it says so ([supported]).
 */
object WidgetPinning {
    fun supported(context: Context): Boolean =
        AppWidgetManager.getInstance(context)?.isRequestPinAppWidgetSupported == true

    /** Asks the launcher to place [widget]; false when it can't be asked or turned the request down. */
    fun request(context: Context, widget: HomeWidget): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        if (!manager.isRequestPinAppWidgetSupported) return false
        return runCatching { manager.requestPinAppWidget(widget.component(context), null, null) }.getOrDefault(false)
    }
}

/**
 * Makes a widget laid out for the preview a picture: its taps (a widget opens the clock app or the new-transaction sheet) must
 * do nothing on a settings page, where it is only being looked at.
 */
internal fun View.makeInert() {
    setOnClickListener(null)
    isClickable = false
    isLongClickable = false
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).makeInert()
}
