package com.example.normpix.wallet.clipboard

import com.example.normpix.settings.ClipboardTimeout
import com.example.normpix.wallet.StoredCard
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The four details of a card that can be copied on their own. */
enum class CardField(val label: String) {
    Number("Card number"),
    Holder("Cardholder name"),
    Expiry("Expiry date"),
    Cvv("CVV"),
    ;

    /** What lands on the clipboard: the number without spaces (every payment form accepts it), the expiry as MM/YY. */
    fun valueOf(card: StoredCard): String = when (this) {
        Number -> card.cardNumber
        Holder -> card.cardholderName
        Expiry -> "${card.expiryMonth}/${card.expiryYear.takeLast(2)}"
        Cvv -> card.cvv
    }
}

/**
 * Puts card details on the system clipboard and takes them off again.
 *
 * Nothing here ever logs, stores or reads back what was copied: a copy is identified by a random token
 * carried in the clip's description, and the only thing persisted is "token + deadline".
 */
interface SecureClipboard {
    /**
     * Copies [value] (labelled [label]) as a sensitive clip, and clears it after [timeout] unless something
     * else has replaced it by then. A newer copy supersedes this one's timer. False when there is no clipboard.
     */
    fun copy(label: String, value: String, timeout: ClipboardTimeout): Boolean

    /**
     * Clears the last copy if its time is up and nobody has replaced it, or resumes its timer if there is time left.
     * Called when the app's window gains focus (the one moment the system lets it look at the clipboard, after the
     * timer died with the process) and by the backup alarm.
     */
    fun reconcile()

    /**
     * Takes NormPix's last copy off the clipboard now, if it is still there and still NormPix's own (a copy since, from
     * anywhere, is left alone), and forgets its timer. Used when the cards it came from are deleted. Nothing happens
     * where there is no clipboard.
     */
    fun clear() = Unit
}

/** Who put what is on the clipboard, as far as the system lets NormPix tell. */
sealed interface ClipboardOwner {
    /** A clip NormPix wrote; [token] says which copy. */
    data class NormPix(val token: String) : ClipboardOwner

    /** Readable, and not NormPix's (or empty). */
    data object Someone : ClipboardOwner

    /**
     * The system won't say. From Android 10 only the app in front may look at the clipboard, so a timer that
     * fires while the user is in the app they are pasting into learns nothing.
     */
    data object Unknown : ClipboardOwner
}

/** The system clipboard, as little of it as the guard needs. */
interface ClipboardPort {
    fun write(label: String, value: String, token: String)

    fun owner(): ClipboardOwner

    fun clear()
}

/** The copy still waiting to be cleared: when, and which. Holds no card data. */
data class PendingClear(val token: String, val deadlineMillis: Long)

/** Keeps the pending copy across process death. */
interface PendingClearStore {
    fun load(): PendingClear?

    fun save(pending: PendingClear?)
}

/** A wake-up for the case where the process is gone when the time is up. */
interface ClearBackup {
    fun schedule(atMillis: Long)

    fun cancel()
}

/**
 * The clearing rules, with the platform pushed to the edges so they can be tested with a fake clipboard and virtual time.
 *
 *  - Every copy gets its own timer and its own token. A newer copy supersedes the older one's timer, because the
 *    older clip is no longer on the clipboard.
 *  - When the timer fires it clears only a clip that is still this copy. Something copied since - from NormPix
 *    or anywhere else - is left alone.
 *  - If the system won't say who owns the clipboard (the user is in another app), the last copy is cleared anyway:
 *    leaving a card number there is worse than the rare case of removing something newer, and no other copy
 *    of NormPix's can be involved because a newer one would have superseded this timer.
 *  - Timers are not tied to a screen. Leaving the card, the tab or the app does not cancel them; if the process
 *    dies, [reconcile] (or the [ClearBackup] alarm) finishes the job.
 */
class ClipboardGuard(
    private val port: ClipboardPort,
    private val store: PendingClearStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
    private val backup: ClearBackup? = null,
    private val newToken: () -> String = { UUID.randomUUID().toString() },
) : SecureClipboard {
    private var pending: PendingClear? = null
    private var loaded = false
    private var timer: Job? = null

    @Synchronized
    override fun copy(label: String, value: String, timeout: ClipboardTimeout): Boolean {
        val token = newToken()
        try {
            port.write(label, value, token)
        } catch (_: RuntimeException) {
            return false // the older copy is still on the clipboard, so its timer keeps running
        }
        supersede()
        if (timeout.clears) arm(PendingClear(token, now() + timeout.seconds * 1000L))
        return true
    }

    @Synchronized
    override fun reconcile() {
        val current = pending() ?: return
        if (now() >= current.deadlineMillis) {
            expire(current.token)
        } else if (timer == null) {
            arm(current)
        }
    }

    @Synchronized
    override fun clear() {
        val current = pending() ?: return
        timer?.cancel()
        expire(current.token)
    }

    private fun pending(): PendingClear? {
        if (!loaded) {
            pending = store.load()
            loaded = true
        }
        return pending
    }

    private fun arm(next: PendingClear) {
        pending = next
        loaded = true
        store.save(next)
        backup?.schedule(next.deadlineMillis)
        timer = scope.launch {
            delay((next.deadlineMillis - now()).coerceAtLeast(0))
            synchronized(this@ClipboardGuard) { expire(next.token) }
        }
    }

    private fun supersede() {
        timer?.cancel()
        timer = null
        if (pending() != null) finish()
    }

    private fun finish() {
        pending = null
        timer = null
        store.save(null)
        backup?.cancel()
    }

    /** The time is up for [token]. Returns whether the clipboard was cleared. */
    private fun expire(token: String): Boolean {
        val current = pending() ?: return false
        if (current.token != token) return false // a newer copy took over
        val clear = when (val owner = port.owner()) {
            is ClipboardOwner.NormPix -> owner.token == token
            ClipboardOwner.Someone -> false
            ClipboardOwner.Unknown -> true
        }
        if (clear) port.clear()
        finish()
        return clear
    }
}

/** For devices and tests without a clipboard: copying reports failure and nothing happens. */
object NoClipboard : SecureClipboard {
    override fun copy(label: String, value: String, timeout: ClipboardTimeout): Boolean = false

    override fun reconcile() = Unit
}
