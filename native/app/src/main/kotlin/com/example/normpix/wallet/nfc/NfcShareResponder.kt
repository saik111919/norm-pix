package com.example.normpix.wallet.nfc

import java.security.SecureRandom

/**
 * The sharing phone's side of the card exchange, version 2 (the cryptography is [NfcPairing]; the reader's side is
 * [NfcShareInitiator]). Pure: [NfcCardOutletService] hands it every command a reader sends and returns what it answers, so the
 * whole exchange runs in a test without NFC hardware.
 *
 * The commands (R = the receiving phone, the reader; short APDUs only: at most 255 bytes of data in, 256 out):
 *   1. SELECT  00 A4 04 00 07 F0010203040506 00   -> "NPX" 02 + 90 00, or 6F 00 when nothing is armed or sharing isn't allowed.
 *                                                    Every SELECT starts a new session: fresh keys, a fresh code.
 *   2. KEYS    80 10 00 00 41 <R's point, 65> 00   -> S's point (65) + commitment (32) + 90 00; 6A 80 for a point not on P-256.
 *   3. NONCE   80 12 00 00 10 <R's nonce, 16> 00   -> S's nonce (16) + 90 00; the code goes to the screen. 69 85 before KEYS.
 *   4. CARD    80 14 <offset, 2 bytes> 00          -> 6F 10 while the person sharing hasn't decided; 6F 11 once they said the codes
 *                                                    don't match; after Match the sealed card: at offset 0 its length in two
 *                                                    bytes and its first 238 bytes, at later offsets up to 240 bytes, + 90 00.
 * Anything else in the wrong state is 69 85; an unknown command 6D 00.
 *
 * What the card's plain bytes ([payload]) never do: travel. Only [NfcPairing.seal]'s output does, and only after Match.
 * Nothing here logs anything.
 *
 * Threading: Android calls the service on the main thread, as the sheet calls [confirm] and [decline]; every entry point still
 * holds [lock], and the [Events] are called after it is released.
 */
class NfcShareResponder(
    /** Whether sharing is allowed right now (the user's NFC schedule, NormPix in front); asked at every command. */
    private val isAllowed: () -> Boolean,
    private val random: SecureRandom = SecureRandom(),
) {
    /** A code on screen for the person sharing to compare. [id] tells one session from the next, so a Match is never applied to a newer one. */
    data class Pairing(val id: Long, val code: String)

    /** What the sheet is told. Called on the thread that answered the command (the main thread, in the app). */
    interface Events {
        /** A reader got as far as the code: show it, with Match and Doesn't match. */
        fun onCode(pairing: Pairing)

        /** The code on screen no longer stands (the phones parted, sharing stopped being allowed, another reader started over). */
        fun onCodeWithdrawn()

        /** The last byte of the sealed card has gone to the reader; the card is no longer armed. */
        fun onTransferred()
    }

    private enum class Phase {
        /** No session: waiting for a SELECT. */
        Idle,

        /** SELECT answered; waiting for KEYS. */
        Selected,

        /** KEYS answered: this phone's key and nonce exist and it is committed to the nonce; waiting for NONCE. */
        Committed,

        /** NONCE answered: both phones show the code; waiting for the person sharing to decide. CARD answers "waiting". */
        Paired,

        /** Match tapped: the card is sealed for this session and goes out in chunks. */
        Sending,

        /** "Doesn't match" tapped: the card is already disarmed; the reader's next CARD is told so, then the session ends. */
        Declined,
    }

    private val lock = Any()

    // The armed card: its plain bytes, kept only on this phone, zeroed as soon as it is given or disarmed.
    private var payload: ByteArray? = null
    private var events: Events? = null

    // The current session. Everything here is dropped by endSession().
    private var phase = Phase.Idle
    private var ownKey: NfcPairing.EphemeralKey? = null
    private var readerPoint: ByteArray? = null
    private var senderNonce: ByteArray? = null
    private var session: NfcPairing.Session? = null
    private var pairing: Pairing? = null
    private var sealed: ByteArray? = null
    private var sessions = 0L

    /** Whether a card is waiting to be shared. */
    val isArmed: Boolean get() = synchronized(lock) { payload != null }

    /** Arms [card] (the plain payload; a copy is kept, and zeroed when it is given or disarmed). Ends any session. */
    fun arm(card: ByteArray, events: Events) {
        synchronized(lock) {
            clearPayload()
            endSession()
            payload = card.copyOf()
            this.events = events
        }
    }

    /** Nothing is shared any more: the card's bytes are zeroed and any session ends. No event is sent: the caller asked for it. */
    fun disarm() {
        synchronized(lock) {
            clearPayload()
            endSession()
            events = null
        }
    }

    /**
     * The person sharing tapped Match for [id]'s code. True when the card is now sealed for that session and will go to the reader
     * at its next CARD; false when that session has already ended (the sheet goes back to waiting: a newer one needs its own Match).
     */
    fun confirm(id: Long): Boolean = synchronized(lock) {
        val card = payload
        val current = session
        if (phase != Phase.Paired || pairing?.id != id || card == null || current == null) return@synchronized false
        // Sealed once, for this session alone; the plain bytes stay armed until the last chunk is out, so if the phones part
        // now the card is still there for a new session (which needs a new code and a new Match).
        val box = NfcPairing.seal(current, card, random)
        // The length goes in two bytes. A card's payload is a few hundred bytes, so this can't happen; if it ever did, nothing goes.
        if (box.size > MAX_SEALED_BYTES) {
            endSession()
            return@synchronized false
        }
        sealed = box
        phase = Phase.Sending
        true
    }

    /**
     * The person sharing tapped "Doesn't match" (for [id]'s code). The card is disarmed at once, whatever the session: they said no.
     * If [id] is the session in progress, the reader's next CARD is answered "refused" so it can say so.
     */
    fun decline(id: Long) {
        synchronized(lock) {
            val tellReader = phase == Phase.Paired && pairing?.id == id
            clearPayload()
            endSession()
            events = null
            if (tellReader) phase = Phase.Declined
        }
    }

    /** The reader went away (Android's onDeactivated: the field dropped, or another AID was selected). The session ends. */
    fun linkLost() {
        val then = synchronized(lock) { if (endSession()) tell { onCodeWithdrawn() } else null }
        then?.invoke()
    }

    /** The answer to one command [apdu] from a reader. */
    fun answer(apdu: ByteArray): ByteArray {
        val reply = synchronized(lock) { respond(apdu) }
        reply.then?.invoke()
        return reply.bytes
    }

    /** An answer and what to tell the sheet once the lock is released. */
    private class Reply(val bytes: ByteArray, val then: (() -> Unit)? = null)

    private fun reply(status: Int, data: ByteArray = ByteArray(0), then: (() -> Unit)? = null) = Reply(data + statusBytes(status), then)

    /** [event] for the sheet armed right now (read under the lock), to be run once the lock is released; null with none armed. */
    private fun tell(event: Events.() -> Unit): (() -> Unit)? {
        val listener = events ?: return null
        return { listener.event() }
    }

    /** A [reply] that also ends the session, withdrawing the code on screen when there was one. */
    private fun replyEnding(status: Int): Reply {
        val withdrawn = endSession()
        return reply(status, then = if (withdrawn) tell { onCodeWithdrawn() } else null)
    }

    private fun respond(apdu: ByteArray): Reply {
        if (isOurSelect(apdu)) return select()
        // Asked at every command, not only at SELECT: NormPix leaving the front, or the schedule closing, stops a share mid-way.
        if (!isAllowed()) return replyEnding(SW_NOTHING_SHARED)
        val command = Command.parse(apdu) ?: return replyEnding(SW_WRONG_DATA)
        if (command.cla != CLA) return reply(SW_UNKNOWN)
        return when (command.ins) {
            INS_KEYS -> keys(command)
            INS_NONCE -> nonce(command)
            INS_CARD -> card(command)
            else -> reply(SW_UNKNOWN)
        }
    }

    /** Step 1: a new session, or "nothing here". */
    private fun select(): Reply {
        val then = if (endSession()) tell { onCodeWithdrawn() } else null
        if (payload == null || !isAllowed()) return reply(SW_NOTHING_SHARED, then = then)
        sessions++
        phase = Phase.Selected
        return reply(SW_OK, VERSION_MARKER, then)
    }

    /** Step 2: the reader's point in; this phone's point and its commitment to a nonce out. */
    private fun keys(command: Command): Reply {
        if (phase != Phase.Selected) return reply(SW_WRONG_STATE)
        val theirs = command.data
        // Off the curve (or not 65 bytes): never used for anything, and the reader has to start over.
        if (NfcPairing.publicKeyOf(theirs) == null) return replyEnding(SW_WRONG_DATA)
        val key = NfcPairing.newKey(random)
        val nonce = NfcPairing.newNonce(random)
        ownKey = key
        readerPoint = theirs.copyOf()
        senderNonce = nonce
        phase = Phase.Committed
        return reply(SW_OK, key.point + NfcPairing.commitment(nonce, key.point, theirs))
    }

    /** Step 3: the reader's nonce in; this phone's nonce out (which the reader checks against the commitment), and the code shown. */
    private fun nonce(command: Command): Reply {
        val key = ownKey
        val theirPoint = readerPoint
        val ourNonce = senderNonce
        if (phase != Phase.Committed || key == null || theirPoint == null || ourNonce == null) return reply(SW_WRONG_STATE)
        // Null for a nonce that isn't 16 bytes: the session ends and the reader has to start over.
        val agreed = NfcPairing.session(
            own = key,
            otherPoint = theirPoint,
            senderPoint = key.point,
            readerPoint = theirPoint,
            senderNonce = ourNonce,
            readerNonce = command.data,
        ) ?: return replyEnding(SW_WRONG_DATA)
        session = agreed
        val shown = Pairing(sessions, agreed.code)
        pairing = shown
        phase = Phase.Paired
        return reply(SW_OK, ourNonce.copyOf(), then = tell { onCode(shown) })
    }

    /** Step 4: "waiting", "refused", or a chunk of the sealed card. The last chunk releases the card. */
    private fun card(command: Command): Reply {
        when (phase) {
            Phase.Paired -> return reply(SW_WAITING)
            Phase.Declined -> {
                endSession()
                return reply(SW_REFUSED)
            }
            Phase.Sending -> Unit
            else -> return reply(SW_WRONG_STATE)
        }
        val box = sealed ?: return reply(SW_WRONG_STATE)
        val offset = command.p1p2
        if (offset >= box.size) return reply(SW_WRONG_STATE)
        val head = if (offset == 0) byteArrayOf((box.size shr 8).toByte(), box.size.toByte()) else ByteArray(0)
        val end = minOf(box.size, offset + if (offset == 0) FIRST_CHUNK else CHUNK)
        val chunk = head + box.copyOfRange(offset, end)
        if (end < box.size) return reply(SW_OK, chunk)
        // The last byte goes out with this answer: the card is given. One reader, once - the plain bytes are zeroed now and the
        // next SELECT finds nothing armed (the sheet stays on "Card shared" until it is closed).
        val then = tell { onTransferred() }
        clearPayload()
        endSession()
        events = null
        return reply(SW_OK, chunk, then = then)
    }

    /** Zeroes and forgets the armed card's plain bytes. */
    private fun clearPayload() {
        payload?.fill(0)
        payload = null
    }

    /** Drops the session's keys, nonces, code and sealed card. True when a code was on screen (so the sheet should withdraw it). */
    private fun endSession(): Boolean {
        val hadCode = phase == Phase.Paired || phase == Phase.Sending
        phase = Phase.Idle
        ownKey = null
        readerPoint = null
        senderNonce?.fill(0)
        senderNonce = null
        session = null
        pairing = null
        sealed = null
        return hadCode
    }

    /** A short command APDU's parts (ISO 7816-4 cases 1 to 4, short lengths only). */
    private class Command(val cla: Int, val ins: Int, val p1p2: Int, val data: ByteArray) {
        companion object {
            /** Null when the lengths don't add up. */
            fun parse(apdu: ByteArray): Command? {
                if (apdu.size < 4) return null
                val data = if (apdu.size <= 5) {
                    ByteArray(0) // header alone, or header + Le
                } else {
                    val lc = apdu[4].toInt() and 0xFF
                    // Header + Lc + data, with or without Le after it.
                    if (lc == 0 || (apdu.size != 5 + lc && apdu.size != 6 + lc)) return null
                    apdu.copyOfRange(5, 5 + lc)
                }
                val p1p2 = ((apdu[2].toInt() and 0xFF) shl 8) or (apdu[3].toInt() and 0xFF)
                return Command(apdu[0].toInt() and 0xFF, apdu[1].toInt() and 0xFF, p1p2, data)
            }
        }
    }

    companion object {
        /** NormPix's private application id. */
        val AID: ByteArray get() = byteArrayOf(0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06)

        /** SELECT by name of [AID], with Le. */
        val SELECT_AID_COMMAND: ByteArray get() = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07) + AID + byteArrayOf(0x00)

        /** "NPX" and the exchange's version: what SELECT answers while a card is armed. */
        val VERSION_MARKER: ByteArray get() = byteArrayOf(0x4E, 0x50, 0x58, NfcPairing.VERSION.toByte())

        const val CLA = 0x80
        const val INS_KEYS = 0x10
        const val INS_NONCE = 0x12
        const val INS_CARD = 0x14

        const val SW_OK = 0x9000

        /** Nothing armed, or sharing not allowed right now (also what an older NormPix answered with nothing to send). */
        const val SW_NOTHING_SHARED = 0x6F00

        /** The person sharing hasn't tapped Match or Doesn't match yet. */
        const val SW_WAITING = 0x6F10

        /** The person sharing tapped Doesn't match. */
        const val SW_REFUSED = 0x6F11
        const val SW_WRONG_DATA = 0x6A80
        const val SW_WRONG_STATE = 0x6985
        const val SW_UNKNOWN = 0x6D00

        /** The first chunk carries the two length bytes too; every answer stays at 240 bytes of data, under the 256 a short APDU allows. */
        const val FIRST_CHUNK = 238
        const val CHUNK = 240

        /** What two length bytes can say. */
        const val MAX_SEALED_BYTES = 0xFFFF

        fun statusBytes(status: Int): ByteArray = byteArrayOf((status shr 8).toByte(), status.toByte())

        /** NormPix's own SELECT, with or without Le. */
        internal fun isOurSelect(apdu: ByteArray): Boolean {
            val select = SELECT_AID_COMMAND
            val withoutLe = select.size - 1
            if (apdu.size != withoutLe && apdu.size != select.size) return false
            for (i in 0 until withoutLe) if (apdu[i] != select[i]) return false
            return true
        }
    }
}
