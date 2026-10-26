package com.example.normpix.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.example.normpix.R
import com.example.normpix.finance.domain.TransactionType

/**
 * The Add widget: a transaction in one tap from the home screen. In one cell it is a single button, like the app's own add
 * button, for an expense; two cells wide or more, a filled "Expense" button with tonal ones for income and a transfer. Each
 * opens NormPix's new-transaction sheet (once the app is unlocked). It shows nothing of the ledger, so it never needs
 * updating: laid out once, and again when resized.
 */
class AddWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) manager.updateAppWidget(id, views(context, manager.getAppWidgetOptions(id)))
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        manager.updateAppWidget(appWidgetId, views(context, newOptions))
    }

    companion object {
        /** From this width the widget has room for the three buttons. */
        const val WIDE_DP = 150f

        fun views(context: Context, options: Bundle?): RemoteViews {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // The launcher picks the larger of the two that fits as the widget is resized.
                return RemoteViews(mapOf(SizeF(40f, 40f) to small(context), SizeF(WIDE_DP, 40f) to wide(context)))
            }
            val width = uprightSize(options)?.first ?: WIDE_DP
            return if (width < WIDE_DP) small(context) else wide(context)
        }

        fun small(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_add_small).apply {
            setOnClickPendingIntent(android.R.id.background, WidgetIntents.open(context, WidgetRequest.Add(TransactionType.Expense)))
        }

        fun wide(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_add).apply {
            setOnClickPendingIntent(R.id.widget_add_expense, WidgetIntents.open(context, WidgetRequest.Add(TransactionType.Expense)))
            setOnClickPendingIntent(R.id.widget_add_income, WidgetIntents.open(context, WidgetRequest.Add(TransactionType.Income)))
            setOnClickPendingIntent(R.id.widget_add_transfer, WidgetIntents.open(context, WidgetRequest.Add(TransactionType.Transfer)))
        }
    }
}
