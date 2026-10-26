package com.example.normpix.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.normpix.AppContainer
import com.example.normpix.NormPixApplication
import com.example.normpix.R
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The Recent widget: the latest transactions at a glance, as Home's Recent has them, with the way to add one.
 *
 * It is drawn again when the ledger changes (the app calls [refreshAll]), when it is resized, and when the day turns -
 * "Today" becomes "Yesterday" - by an alarm that wakes nothing (it waits for the phone to be in use) and after a restart or
 * a change of clock. Nothing on a timer. While NormPix locks as a whole it shows none of the ledger, only that it is locked.
 */
class RecentWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context, appWidgetIds)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) =
        refresh(context, intArrayOf(appWidgetId))

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_NEW_DAY,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            -> refresh(context, ids(context))
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(newDay(context))
    }

    private fun refresh(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val app = context.applicationContext
        val done = goAsync()
        scope.launch {
            try {
                update(app, ids)
            } finally {
                done.finish()
            }
        }
    }

    companion object {
        /** The alarm's: the day has turned. */
        const val ACTION_NEW_DAY = "com.example.normpix.widget.RECENT_NEW_DAY"

        /** As many transactions as the tallest widget lists. */
        const val MAX_ROWS = 8

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, RecentWidget::class.java)) ?: IntArray(0)

        /** Draws every Recent widget on the home screen again: the ledger, or how it is shown, changed. */
        fun refreshAll(context: Context) {
            val ids = ids(context)
            if (ids.isNotEmpty()) scope.launch { update(context.applicationContext, ids) }
        }

        suspend fun update(
            context: Context,
            ids: IntArray,
            container: AppContainer = (context.applicationContext as NormPixApplication).container,
        ) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val content = try {
                RecentWidgetData.load(container, MAX_ROWS)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                RecentContent.Unavailable
            }
            for (id in ids) manager.updateAppWidget(id, RecentWidgetViews.views(context, content, manager.getAppWidgetOptions(id)))
            remindAtMidnight(context)
        }

        /** Once the day has turned: an inexact alarm that doesn't wake the phone, set again at every update. */
        private fun remindAtMidnight(context: Context) {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val zone = ZoneId.systemDefault()
            val at = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 60_000
            alarms.set(AlarmManager.RTC, at, newDay(context))
        }

        private fun newDay(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, RecentWidget::class.java).setAction(ACTION_NEW_DAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** The Recent widget's views, for what it shows and how tall it is. */
object RecentWidgetViews {
    /** Padding (12 + 12) and the heading (48). */
    private const val CHROME_DP = 72f

    /** A row (52) and the gap above it (4). */
    private const val ROW_DP = 56f

    private val discs = intArrayOf(
        R.drawable.widget_avatar_0, R.drawable.widget_avatar_1, R.drawable.widget_avatar_2, R.drawable.widget_avatar_3,
        R.drawable.widget_avatar_4, R.drawable.widget_avatar_5, R.drawable.widget_avatar_6, R.drawable.widget_avatar_7,
    )

    /** How many rows fit a widget [heightDp] tall: one at least, so the latest always shows. */
    fun rowsFor(heightDp: Float): Int = ((heightDp - CHROME_DP) / ROW_DP).toInt().coerceIn(1, RecentWidget.MAX_ROWS)

    fun views(context: Context, content: RecentContent, options: Bundle?): RemoteViews =
        viewsForSizes(options, 250f to 180f) { _, height -> build(context, content, rowsFor(height)) }

    fun build(context: Context, content: RecentContent, rows: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_recent).apply {
            setOnClickPendingIntent(R.id.widget_recent_open, WidgetIntents.open(context, WidgetRequest.Activity))
            setOnClickPendingIntent(R.id.widget_recent_add, WidgetIntents.open(context, WidgetRequest.Add(TransactionType.Expense)))
            removeAllViews(R.id.widget_recent_rows)
            when (content) {
                RecentContent.Locked -> message(context, "NormPix is locked", "Open it to see your recent transactions", lock = true)
                RecentContent.Unavailable -> message(context, "Couldn't read your transactions", "Open NormPix to see them", lock = false)
                is RecentContent.Rows -> if (content.rows.isEmpty()) {
                    message(context, "No transactions yet", "Tap + to add your first one", lock = false)
                } else {
                    setViewVisibility(R.id.widget_recent_rows, View.VISIBLE)
                    setViewVisibility(R.id.widget_recent_message, View.GONE)
                    for (item in content.rows.take(rows)) addView(R.id.widget_recent_rows, row(context, item))
                }
            }
        }

    private fun RemoteViews.message(context: Context, title: String, body: String, lock: Boolean) {
        setViewVisibility(R.id.widget_recent_rows, View.GONE)
        setViewVisibility(R.id.widget_recent_message, View.VISIBLE)
        setViewVisibility(R.id.widget_recent_message_icon, if (lock) View.VISIBLE else View.GONE)
        setTextViewText(R.id.widget_recent_message_title, title)
        setTextViewText(R.id.widget_recent_message_body, body)
        setOnClickPendingIntent(R.id.widget_recent_message, WidgetIntents.open(context, WidgetRequest.Activity))
    }

    private fun row(context: Context, row: RecentRow): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_recent_row).apply {
            val transfer = row.type == TransactionType.Transfer
            setTextViewText(R.id.widget_row_title, row.title)
            setTextViewText(R.id.widget_row_detail, row.detail)
            setImageViewResource(R.id.widget_row_disc, if (transfer) R.drawable.widget_avatar_transfer else discs[row.palette])
            setTextViewText(R.id.widget_row_emoji, row.emoji)
            setViewVisibility(R.id.widget_row_emoji, if (transfer) View.GONE else View.VISIBLE)
            setViewVisibility(R.id.widget_row_transfer_icon, if (transfer) View.VISIBLE else View.GONE)
            // The amount in the text of its kind, which has its colour in the layout (so it follows light and dark).
            val amountId = when (row.type) {
                TransactionType.Expense -> R.id.widget_row_expense
                TransactionType.Income -> R.id.widget_row_income
                TransactionType.Transfer -> R.id.widget_row_transfer
            }
            for (id in intArrayOf(R.id.widget_row_expense, R.id.widget_row_income, R.id.widget_row_transfer)) {
                setViewVisibility(id, if (id == amountId) View.VISIBLE else View.GONE)
            }
            setTextViewText(amountId, row.amount)
            setContentDescription(R.id.widget_row, row.spoken)
            setOnClickPendingIntent(R.id.widget_row, WidgetIntents.open(context, WidgetRequest.Show(row.id)))
        }
}
