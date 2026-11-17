package com.example.normpix.quickadd

import android.content.Intent
import android.net.Uri
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.share.SharedTransactionParser
import com.example.normpix.finance.share.handedOverPictureOf
import com.example.normpix.finance.share.sharedTextOf
import com.example.normpix.widgets.WidgetIntents
import com.example.normpix.widgets.WidgetRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Put on a share by [com.example.normpix.finance.share.ShareTransactionActivity] as it hands it over (see [quickAddRequestOf]). */
const val EXTRA_HANDED_OVER = "com.example.normpix.quickadd.HANDED_OVER"

/** What [QuickAddActivity] is asked to do: one sheet, over whatever app the user is in. */
sealed interface QuickAddRequest {
    /** The new-transaction sheet of [type]: the app icon's shortcuts, the Quick Settings tile and the Add widget's buttons. */
    data class Add(val type: TransactionType) : QuickAddRequest

    /**
     * Something another app shared: its [text] (empty when it had none) and, for an image, the [picture] to read the text from. Not a
     * data class, so the shared text is never put in a log or a message by accident.
     */
    class Share(val text: String, val picture: Uri? = null) : QuickAddRequest {
        override fun toString(): String = "Share"
    }
}

/**
 * The requests the overlay has not done yet, in the order they came: memory only (a shared text is never written to disk or logged,
 * and goes with the activity). A request that arrives while another is open - a second share, say - waits behind it, so nothing the
 * user was filling in is lost; the overlay does one after the other.
 */
class QuickAddRequests {
    private val queue = MutableStateFlow<List<QuickAddRequest>>(emptyList())

    /** The request the overlay is doing now, if any: asking for the same thing again (a double tap on a shortcut) does not queue it twice. */
    private var active: QuickAddRequest? = null

    /** The requests not yet taken, oldest first. */
    val waiting: StateFlow<List<QuickAddRequest>> = queue.asStateFlow()

    val isEmpty: Boolean get() = queue.value.isEmpty() && active == null

    @Synchronized
    fun offer(request: QuickAddRequest) {
        if (request is QuickAddRequest.Add && (request == active || request == queue.value.lastOrNull())) return
        queue.update { (it + request).takeLast(MAX_WAITING) }
    }

    /** The next request, now the overlay's to do (until [finished]); null when none is waiting. */
    @Synchronized
    fun takeNext(): QuickAddRequest? {
        val next = queue.value.firstOrNull() ?: return null
        queue.update { it.drop(1) }
        active = next
        return next
    }

    /** The request taken last is over: its sheet has closed. */
    @Synchronized
    fun finished() {
        active = null
    }

    companion object {
        /** Older requests beyond this are dropped: nobody shares more than a handful of payments in one go. */
        const val MAX_WAITING = 5
    }
}

/**
 * The request [intent] carries, if any: a share (`ACTION_SEND`, read straight from the intent - never a picture of NormPix's own,
 * see [sharedPictureOf]) or one of [WidgetIntents]' add requests. None when the intent has neither, or when the activity was
 * reopened from Recents (the system hands back the intent it began with, and that was done then). A text far longer than any
 * transaction message is cut to just past [SharedTransactionParser.MAX_LENGTH] - enough for the parser to tell it is too long.
 */
fun quickAddRequestOf(intent: Intent?, ownPackage: String): QuickAddRequest? {
    if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    if (intent.action == Intent.ACTION_SEND) {
        // Only a share ShareTransactionActivity has just handed over. A task Android brings back after NormPix's process went (as an
        // update does) restarts the overlay with its first intent emptied of its extras - an old share, not a new one with nothing in it.
        if (!intent.getBooleanExtra(EXTRA_HANDED_OVER, false)) return null
        val text = sharedTextOf(intent).orEmpty().take(SharedTransactionParser.MAX_LENGTH + 1)
        return QuickAddRequest.Share(text, handedOverPictureOf(intent, ownPackage))
    }
    return (WidgetIntents.requestOf(intent) as? WidgetRequest.Add)?.let { QuickAddRequest.Add(it.type) }
}
