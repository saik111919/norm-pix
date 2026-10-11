package com.example.normpix.finance.share

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Something another app shared with NormPix, waiting for the UI: its [text] (empty when it had none) and, for a shared
 * image, the [picture] to read the text from. [id] tells two shares of the same thing apart.
 */
class SharedText(val id: Long, val text: String, val picture: Uri? = null)

/**
 * Hands text shared with NormPix ([ShareTransactionActivity]) to the UI, which reads it into a draft for the user to check
 * and save. Memory only: shared text is never written to disk or logged, and goes with the process.
 *
 * Each share is taken once ([take]), so a recreated activity, a recomposition or the screen being rebuilt can't open the
 * same share twice. Shares that arrive while one is waiting (the app is locked, say) queue behind it, a few at most.
 */
class SharedTextInbox {
    private var lastId = 0L
    private val queue = MutableStateFlow<List<SharedText>>(emptyList())

    /** The shares not yet taken, oldest first. */
    val waiting: StateFlow<List<SharedText>> = queue.asStateFlow()

    /**
     * Keeps [text] (and a shared [picture]) for the UI. A text far longer than any transaction message is cut to just past
     * [SharedTransactionParser.MAX_LENGTH] - enough for the parser to tell it is too long, without holding all of it.
     */
    @Synchronized
    fun offer(text: String?, picture: Uri? = null): SharedText {
        val share = SharedText(++lastId, text.orEmpty().take(SharedTransactionParser.MAX_LENGTH + 1), picture)
        queue.update { (it + share).takeLast(MAX_WAITING) }
        return share
    }

    /** True when [share] was still waiting and is now the caller's; false when someone already took it. */
    fun take(share: SharedText): Boolean {
        while (true) {
            val current = queue.value
            if (current.none { it === share }) return false
            if (queue.compareAndSet(current, current.filterNot { it === share })) return true
        }
    }

    companion object {
        /** Older shares beyond this are dropped: nobody shares more than a handful of payments in one go. */
        const val MAX_WAITING = 5
    }
}

/**
 * The text an [Intent.ACTION_SEND] share carries (its subject first, when the text doesn't already contain it), or null
 * when it carries none. Never throws: an extra of the wrong type or one that can't be unpacked is just no text.
 */
fun sharedTextOf(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_SEND) return null
    return try {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()?.takeIf { it.isNotBlank() && text?.contains(it) != true }
        listOfNotNull(subject, text).joinToString("\n").ifBlank { null }
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * The picture an image share carries, or null. Only a `content://` picture from another app is taken: never a file path,
 * and never one of NormPix's own files ([ownPackage]'s providers) - another app must not be able to have NormPix read
 * its private files back into a form. Never throws.
 */
fun sharedPictureOf(intent: Intent?, ownPackage: String): Uri? {
    if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("image/") != true) return null
    return try {
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        uri?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT && it.authority?.startsWith(ownPackage) == false }
    } catch (_: RuntimeException) {
        null
    }
}
