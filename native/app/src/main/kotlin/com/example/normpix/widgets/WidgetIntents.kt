package com.example.normpix.widgets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.normpix.MainActivity

/** How a widget's tap reaches the app: an intent that opens MainActivity with a [WidgetRequest] on it. */
object WidgetIntents {
    const val EXTRA_REQUEST = "com.example.normpix.widget.REQUEST"
    const val EXTRA_TRANSACTION_ID = "com.example.normpix.widget.TRANSACTION_ID"

    /**
     * Opens NormPix to do [request]. Each request is its own PendingIntent (its own action, and a transaction's own request
     * code), so one row's tap never opens another's transaction. Immutable: nothing outside the app can change what it asks.
     */
    fun open(context: Context, request: WidgetRequest): PendingIntent {
        val shown = (request as? WidgetRequest.Show)?.transactionId
        val code = shown?.let { (it % Int.MAX_VALUE).toInt() } ?: 0
        return PendingIntent.getActivity(context, code, intent(context, request), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * The intent that opens NormPix to do [request]: explicit (it names MainActivity), carrying only the request's plain words. The
     * home-screen shortcuts and the Quick Settings tile launch it directly; a widget wraps it ([open]).
     */
    fun intent(context: Context, request: WidgetRequest): Intent {
        val shown = (request as? WidgetRequest.Show)?.transactionId
        val intent = Intent(context, MainActivity::class.java)
            .setAction("com.example.normpix.widget." + request.key)
            .putExtra(EXTRA_REQUEST, request.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (shown != null) intent.putExtra(EXTRA_TRANSACTION_ID, shown)
        return intent
    }

    /**
     * The request [intent] carries, if any. None when the app was reopened from Recents: the system hands back the intent
     * the task began with, and the widget's tap was already done then.
     */
    fun requestOf(intent: Intent?): WidgetRequest? {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        return WidgetRequest.of(intent.getStringExtra(EXTRA_REQUEST), intent.getLongExtra(EXTRA_TRANSACTION_ID, -1))
    }

    /** Takes the request off [intent] once it has been passed on, so a configuration change doesn't do it again. */
    fun clear(intent: Intent) {
        intent.removeExtra(EXTRA_REQUEST)
        intent.removeExtra(EXTRA_TRANSACTION_ID)
    }
}
