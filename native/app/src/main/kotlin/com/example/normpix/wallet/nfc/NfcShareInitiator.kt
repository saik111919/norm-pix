package com.example.normpix.wallet.nfc

import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.CHUNK
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.CLA
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.FIRST_CHUNK
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.INS_CARD
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.INS_KEYS
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.INS_NONCE
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.SW_NOTHING_SHARED
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.SW_OK
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.SW_REFUSED
import com.example.normpix.wallet.nfc.NfcShareResponder.Companion.SW_WAITING
import java.io.IOException
import java.security.SecureRandom

/** Why a share from another phone ended without a card. Nothing was stored in any of these. */
enum class ShareProblem(val title: String, val message: String) {
    /** It answered with the card in the clear (exchange version 1): refused, whatever it sent is dropped. */
    OlderPhone("Update NormPix on that phone", "That phone has an older NormPix. Update it there to share cards securely."),

    /** It speaks a later version of the exchange than this phone. */
    NewerPhone("Update NormPix on this phone", "That phone has a newer NormPix. Update this one to receive cards from it."),

    /** The person sharing tapped Doesn't match. */
    Refused("Not shared", "The other phone said the codes don't match, so nothing was shared."),

    /** The other phone's nonce didn't match what it had committed to, or its key was not a point on the curve. */
    Interfered("Share stopped", "This share was interfered with. Nothing was shared."),

    /** Nobody tapped Match on the other phone in time. */
    TimedOut("No answer from the other phone", "Nobody tapped Match on the other phone within a minute. Nothing was shared."),

    /** The phones parted after the exchange had started. */
    Parted("Phones moved apart", "Keep the phones together until the card arrives."),

    /** The sealed card came in pieces that don't add up, or didn't open with this share's key. */
    Broken("Couldn't read that", "The card didn't arrive whole. Try again."),

    /** The other phone stopped answering the exchange (its sheet closed, or it stopped being allowed to share). */
    Stopped("Sharing stopped", "The other phone stopped sharing. Nothing was shared."),
}

/**
 * The receiving phone's side of the card exchange, version 2 (the commands are in [NfcShareResponder]'s header, the cryptography
 * in [NfcPairing]). Pure and blocking: it runs on the NFC reader's thread, where waiting is fine, and in tests against a
 * [NfcShareResponder] directly.
 */
internal object NfcShareInitiator {
    /** How long the person sharing has to tap Match, from the moment the code is shown. */
    const val WAIT_MS = 60_000L

    /** How often the reader asks whether they have. */
    const val POLL_MS = 250L

    sealed interface Result {
        /** The card's plain payload, opened with this share's key. */
        data class Card(val payload: String) : Result

        /** NormPix answered, with nothing armed. */
        data object NotSharing : Result

        /** Not NormPix (a bank card, say): the caller goes on to the payment directory. */
        data object NotNormPix : Result

        data class Failed(val problem: ShareProblem) : Result
    }

    /**
     * Runs one exchange from SELECT to the card. [transceive] sends a command and returns the answer (an [IOException] there
     * before NormPix has answered is left to the caller as "lost"); [onCode] gets the code to show; [sleep] and [clock] (in
     * milliseconds) pace the waiting for Match.
     */
    fun run(
        transceive: (ByteArray) -> ByteArray,
        onCode: (String) -> Unit,
        sleep: (Long) -> Unit,
        clock: () -> Long,
        random: SecureRandom = SecureRandom(),
    ): Result {
        // 1. SELECT: is this NormPix, sharing, and which version?
        val selected = transceive(NfcShareResponder.SELECT_AID_COMMAND)
        when (selected.statusWord()) {
            SW_NOTHING_SHARED -> return Result.NotSharing
            SW_OK -> Unit
            else -> return Result.NotNormPix
        }
        val marker = selected.body()
        if (!marker.contentEquals(NfcShareResponder.VERSION_MARKER)) {
            val ours = NfcShareResponder.VERSION_MARKER
            val newer = marker.size == ours.size && marker.copyOf(3).contentEquals(ours.copyOf(3)) &&
                (marker[3].toInt() and 0xFF) > NfcPairing.VERSION
            // Anything else is an older NormPix, which answers SELECT with the card itself, in the clear (version 1). It has
            // crossed already; it is not kept, shown or logged.
            selected.fill(0)
            marker.fill(0)
            return Result.Failed(if (newer) ShareProblem.NewerPhone else ShareProblem.OlderPhone)
        }
        // From here on, the phones parting is "keep them together", not a plain lost tap.
        return try {
            exchange(transceive, onCode, sleep, clock, random)
        } catch (e: IOException) {
            Result.Failed(ShareProblem.Parted)
        } catch (e: SecurityException) {
            Result.Failed(ShareProblem.Parted)
        }
    }

    /** Steps 2 to 4, after a version-2 SELECT. */
    private fun exchange(
        transceive: (ByteArray) -> ByteArray,
        onCode: (String) -> Unit,
        sleep: (Long) -> Unit,
        clock: () -> Long,
        random: SecureRandom,
    ): Result {
        // 2. KEYS: this phone's fresh point out; the sharing phone's point and its commitment to a nonce back.
        val ownKey = NfcPairing.newKey(random)
        val keys = transceive(command(INS_KEYS, data = ownKey.point))
        if (keys.statusWord() != SW_OK) return Result.Failed(ShareProblem.Stopped)
        val keysBody = keys.body()
        if (keysBody.size != NfcPairing.POINT_BYTES + NfcPairing.COMMIT_BYTES) return Result.Failed(ShareProblem.Interfered)
        val senderPoint = keysBody.copyOfRange(0, NfcPairing.POINT_BYTES)
        val commitment = keysBody.copyOfRange(NfcPairing.POINT_BYTES, keysBody.size)

        // 3. NONCE: this phone's nonce out, the sharing phone's back - which must be the one it committed to before it saw ours.
        val readerNonce = NfcPairing.newNonce(random)
        val nonce = transceive(command(INS_NONCE, data = readerNonce))
        if (nonce.statusWord() != SW_OK) return Result.Failed(ShareProblem.Stopped)
        val senderNonce = nonce.body()
        if (!NfcPairing.commitmentHolds(commitment, senderNonce, senderPoint, ownKey.point)) {
            return Result.Failed(ShareProblem.Interfered)
        }
        // Null when the sharing phone's point isn't on the curve.
        val session = NfcPairing.session(
            own = ownKey,
            otherPoint = senderPoint,
            senderPoint = senderPoint,
            readerPoint = ownKey.point,
            senderNonce = senderNonce,
            readerNonce = readerNonce,
        ) ?: return Result.Failed(ShareProblem.Interfered)
        readerNonce.fill(0)
        onCode(session.code)

        // 4. CARD: ask until the person sharing decides (or a minute passes), then fetch the sealed card piece by piece.
        val started = clock()
        var answer: ByteArray
        while (true) {
            answer = transceive(command(INS_CARD, p1p2 = 0))
            when (answer.statusWord()) {
                SW_OK -> break
                SW_REFUSED -> return Result.Failed(ShareProblem.Refused)
                SW_WAITING -> {
                    if (clock() - started >= WAIT_MS) return Result.Failed(ShareProblem.TimedOut)
                    sleep(POLL_MS)
                }
                else -> return Result.Failed(ShareProblem.Stopped)
            }
        }
        // The first answer: the sealed card's length in two bytes, then its first bytes. Every piece must be exactly as long as
        // the sharing phone sends them (238 first, then 240, the last one the rest), so the loop below always ends.
        val first = answer.body()
        if (first.size < 2) return Result.Failed(ShareProblem.Broken)
        val total = ((first[0].toInt() and 0xFF) shl 8) or (first[1].toInt() and 0xFF)
        if (total == 0 || first.size - 2 != minOf(FIRST_CHUNK, total)) return Result.Failed(ShareProblem.Broken)
        val sealed = ByteArray(total)
        first.copyInto(sealed, destinationOffset = 0, startIndex = 2)
        var received = first.size - 2
        while (received < total) {
            val next = transceive(command(INS_CARD, p1p2 = received))
            val piece = next.body()
            if (next.statusWord() != SW_OK || piece.size != minOf(CHUNK, total - received)) return Result.Failed(ShareProblem.Broken)
            piece.copyInto(sealed, destinationOffset = received)
            received += piece.size
        }
        // Null when anything in it was changed, cut short, or sealed with another key.
        val card = NfcPairing.open(session, sealed) ?: return Result.Failed(ShareProblem.Broken)
        val payload = String(card, Charsets.UTF_8)
        card.fill(0)
        return Result.Card(payload)
    }

    /** A proprietary command: header, then Lc and [data] when there is any, then Le 00 (any length back, up to 256). */
    private fun command(ins: Int, p1p2: Int = 0, data: ByteArray = ByteArray(0)): ByteArray {
        val header = byteArrayOf(CLA.toByte(), ins.toByte(), (p1p2 shr 8).toByte(), p1p2.toByte())
        val body = if (data.isEmpty()) ByteArray(0) else byteArrayOf(data.size.toByte()) + data
        return header + body + byteArrayOf(0x00)
    }
}
