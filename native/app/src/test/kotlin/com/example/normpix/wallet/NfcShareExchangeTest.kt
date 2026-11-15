package com.example.normpix.wallet

import com.example.normpix.wallet.nfc.NfcExchangeController
import com.example.normpix.wallet.nfc.NfcInletState
import com.example.normpix.wallet.nfc.NfcPairing
import com.example.normpix.wallet.nfc.NfcShareInitiator
import com.example.normpix.wallet.nfc.NfcShareResponder
import com.example.normpix.wallet.nfc.NfcTagRead
import com.example.normpix.wallet.nfc.ShareProblem
import com.example.normpix.wallet.nfc.TagChannel
import com.example.normpix.wallet.nfc.parseCardFromNfcPayload
import com.example.normpix.wallet.nfc.readTag
import com.example.normpix.wallet.nfc.sameCardAs
import com.example.normpix.wallet.nfc.toNfcExchangePayload
import java.io.IOException
import java.security.SecureRandom
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The encrypted card exchange (version 2) with both phones in one process: the reader's [NfcShareInitiator] talking straight to
 * the sharing phone's [NfcShareResponder]. The card only ever crosses sealed, only after Match, to one reader once; anything
 * that is not the exchange as designed ends it with nothing stored.
 */
class NfcShareExchangeTest {
    private val card = StoredCard(
        id = "sender-id",
        bankName = "HDFC Bank",
        cardholderName = "Asha Rao",
        cardNumber = "4111111111111111",
        expiryMonth = "09",
        expiryYear = "29",
        cvv = "123",
        kind = CardKind.Debit,
        createdAt = LocalDateTime.of(2026, 1, 1, 9, 0),
    )
    private val random = SecureRandom()

    /** What the sharing phone's sheet was told. */
    private class SheetEvents : NfcShareResponder.Events {
        val codes = mutableListOf<NfcShareResponder.Pairing>()
        var withdrawn = 0
        var transferred = 0
        override fun onCode(pairing: NfcShareResponder.Pairing) {
            codes += pairing
        }
        override fun onCodeWithdrawn() {
            withdrawn++
        }
        override fun onTransferred() {
            transferred++
        }
    }

    /** One sharing phone: its responder, armed with [payload], and what its sheet saw. */
    private inner class Sharer(payload: String = card.toNfcExchangePayload(), allowed: () -> Boolean = { true }) {
        val events = SheetEvents()
        val responder = NfcShareResponder(isAllowed = allowed, random = random).also {
            it.arm(payload.toByteArray(Charsets.UTF_8), events)
        }

        /** Every command sent and every answer given, in order. */
        val traffic = mutableListOf<ByteArray>()
        val commands = mutableListOf<ByteArray>()

        fun answer(command: ByteArray): ByteArray {
            commands += command
            traffic += command
            return responder.answer(command).also { traffic += it }
        }
    }

    /** A reader run against [sharer]: [decide] is called at each wait (the person sharing deciding while the reader asks). */
    private class ReaderRun(val result: NfcShareInitiator.Result, val code: String?, val waits: Int)

    private fun read(
        sharer: Sharer,
        transceive: (ByteArray) -> ByteArray = sharer::answer,
        decide: (wait: Int) -> Unit = { if (it == 2) sharer.responder.confirm(sharer.events.codes.last().id) },
    ): ReaderRun {
        var code: String? = null
        var waits = 0
        var now = 0L
        val result = NfcShareInitiator.run(
            transceive = transceive,
            onCode = { code = it },
            sleep = { ms ->
                now += ms
                decide(waits++)
            },
            clock = { now },
            random = random,
        )
        return ReaderRun(result, code, waits)
    }

    private fun ByteArray.status(): Int = ((this[size - 2].toInt() and 0xFF) shl 8) or (this[size - 1].toInt() and 0xFF)
    private fun ByteArray.body(): ByteArray = copyOfRange(0, size - 2)

    private val select = NfcShareResponder.SELECT_AID_COMMAND
    private fun keys(point: ByteArray) = byteArrayOf(0x80.toByte(), 0x10, 0x00, 0x00, point.size.toByte()) + point + byteArrayOf(0x00)
    private fun nonce(value: ByteArray) = byteArrayOf(0x80.toByte(), 0x12, 0x00, 0x00, value.size.toByte()) + value + byteArrayOf(0x00)
    private fun cardAt(offset: Int) = byteArrayOf(0x80.toByte(), 0x14, (offset shr 8).toByte(), offset.toByte(), 0x00)

    /** Runs SELECT, KEYS and NONCE by hand as a reader would; the reader's session, or null when the sharer refused a step. */
    private fun pairByHand(sharer: Sharer): NfcPairing.Session? {
        assertEquals(NfcShareResponder.SW_OK, sharer.answer(select).status())
        val own = NfcPairing.newKey(random)
        val keyAnswer = sharer.answer(keys(own.point))
        if (keyAnswer.status() != NfcShareResponder.SW_OK) return null
        val senderPoint = keyAnswer.body().copyOfRange(0, NfcPairing.POINT_BYTES)
        val commitment = keyAnswer.body().copyOfRange(NfcPairing.POINT_BYTES, keyAnswer.size - 2)
        val readerNonce = NfcPairing.newNonce(random)
        val nonceAnswer = sharer.answer(nonce(readerNonce))
        if (nonceAnswer.status() != NfcShareResponder.SW_OK) return null
        val senderNonce = nonceAnswer.body()
        assertTrue(NfcPairing.commitmentHolds(commitment, senderNonce, senderPoint, own.point))
        return NfcPairing.session(own, senderPoint, senderPoint, own.point, senderNonce, readerNonce)
    }

    /** Whether [needle] appears anywhere in [haystack]. */
    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean =
        (0..haystack.size - needle.size).any { start -> needle.indices.all { haystack[start + it] == needle[it] } }

    @Test
    fun `the card arrives sealed after Match, both phones showed the same code, and it parses into the same card`() {
        val sharer = Sharer()
        val run = read(sharer)

        val received = assertIs<NfcShareInitiator.Result.Card>(run.result)
        assertEquals(card.toNfcExchangePayload(), received.payload)
        assertTrue(assertNotNull(parseCardFromNfcPayload(received.payload)).sameCardAs(card))
        // The same six digits on both screens, from one key agreement.
        assertEquals(sharer.events.codes.single().code, run.code)
        assertEquals(NfcPairing.CODE_DIGITS, run.code!!.length)
        // The reader asked, and was told to wait, until Match.
        assertEquals(3, run.waits)
        assertEquals(1, sharer.events.transferred)
        assertFalse(sharer.responder.isArmed, "one reader, once")
    }

    @Test
    fun `nothing readable of the card ever crosses, in any command or answer`() {
        val payload = card.toNfcExchangePayload().toByteArray(Charsets.UTF_8)
        val sharer = Sharer()
        assertIs<NfcShareInitiator.Result.Card>(read(sharer).result)

        val everything = sharer.traffic.fold(ByteArray(0)) { all, apdu -> all + apdu }
        assertFalse(contains(everything, payload), "the plain payload")
        for (secret in listOf("4111111111111111", "\"cvv\":\"123\"", "Asha Rao", "normpix_card")) {
            assertFalse(contains(everything, secret.toByteArray(Charsets.UTF_8)), secret)
            for (apdu in sharer.traffic) assertFalse(contains(apdu, secret.toByteArray(Charsets.UTF_8)), secret)
        }
        // And no answer is longer than a short APDU allows (256 bytes of data + the status word).
        assertTrue(sharer.traffic.all { it.size <= 258 })
    }

    @Test
    fun `while the person sharing hasn't decided, the reader is told to wait`() {
        val sharer = Sharer()
        assertNotNull(pairByHand(sharer))
        repeat(5) { assertContentEquals(byteArrayOf(0x6F, 0x10), sharer.answer(cardAt(0))) }
        assertTrue(sharer.responder.isArmed)
        assertEquals(0, sharer.events.transferred)
    }

    @Test
    fun `Doesn't match tells the reader it was refused, disarms the card, and nothing is stored`() {
        val sharer = Sharer()
        val run = read(sharer, decide = { if (it == 1) sharer.responder.decline(sharer.events.codes.last().id) })

        assertEquals(NfcShareInitiator.Result.Failed(ShareProblem.Refused), run.result)
        assertFalse(sharer.responder.isArmed)
        assertEquals(0, sharer.events.transferred)
        // The next reader finds nothing armed.
        assertContentEquals(byteArrayOf(0x6F, 0x00), sharer.answer(select))
        assertEquals(NfcShareInitiator.Result.NotSharing, read(sharer).result)
    }

    @Test
    fun `a card goes to one reader once - a second full run finds nothing`() {
        val sharer = Sharer()
        assertIs<NfcShareInitiator.Result.Card>(read(sharer).result)
        assertEquals(NfcShareInitiator.Result.NotSharing, read(sharer, decide = {}).result)
        assertEquals(1, sharer.events.transferred)
    }

    @Test
    fun `a new SELECT starts a new session with a new code, and a Match for the old one is not applied to it`() {
        val sharer = Sharer()
        val first = assertNotNull(pairByHand(sharer))
        val firstPairing = sharer.events.codes.single()
        assertEquals(first.code, firstPairing.code)

        // Another SELECT: the code on screen is withdrawn and the old session is gone.
        assertEquals(NfcShareResponder.SW_OK, sharer.answer(select).status())
        assertEquals(1, sharer.events.withdrawn)
        assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), sharer.answer(cardAt(0)))
        assertFalse(sharer.responder.confirm(firstPairing.id), "Match on the old code must not release the card to a new session")

        val second = assertNotNull(pairByHand(sharer))
        val secondPairing = sharer.events.codes.last()
        assertNotEquals(firstPairing.id, secondPairing.id)
        assertEquals(second.code, secondPairing.code)
        // A Match tapped on the old code once the new one is up is not applied to it either.
        assertFalse(sharer.responder.confirm(firstPairing.id))
        // Still waiting: the old Match counted for nothing.
        assertContentEquals(byteArrayOf(0x6F, 0x10), sharer.answer(cardAt(0)))
        assertTrue(sharer.responder.isArmed)
    }

    @Test
    fun `each session's code is its own`() {
        val sharer = Sharer()
        val codes = (1..10).map { assertNotNull(pairByHand(sharer)).code }.toSet()
        assertTrue(codes.size > 1)
    }

    @Test
    fun `a reader point that isn't on P-256, or isn't 65 bytes, is refused and ends the session`() {
        val good = NfcPairing.newKey(random).point
        val offCurve = good.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }
        for (bad in listOf(offCurve, good.copyOf(64), good.copyOf().also { it[0] = 0x02 })) {
            val sharer = Sharer()
            sharer.answer(select)
            assertContentEquals(byteArrayOf(0x6A, 0x80.toByte()), sharer.answer(keys(bad)))
            // The session ended: there is nothing to send a nonce to.
            assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), sharer.answer(nonce(NfcPairing.newNonce(random))))
            assertTrue(sharer.events.codes.isEmpty())
        }
    }

    @Test
    fun `steps out of order are refused`() {
        val sharer = Sharer()
        // Nothing before SELECT.
        assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), sharer.answer(keys(NfcPairing.newKey(random).point)))
        sharer.answer(select)
        // NONCE before KEYS.
        assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), sharer.answer(nonce(NfcPairing.newNonce(random))))
        // CARD before the code.
        assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), sharer.answer(cardAt(0)))
        // KEYS twice in one session.
        sharer.answer(keys(NfcPairing.newKey(random).point))
        assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), sharer.answer(keys(NfcPairing.newKey(random).point)))
        // A nonce of the wrong length ends it.
        assertContentEquals(byteArrayOf(0x6A, 0x80.toByte()), sharer.answer(nonce(ByteArray(8))))
        assertContentEquals(byteArrayOf(0x69, 0x85.toByte()), sharer.answer(cardAt(0)))
        // Unknown commands.
        assertContentEquals(byteArrayOf(0x6D, 0x00), sharer.answer(byteArrayOf(0x80.toByte(), 0x7E, 0x00, 0x00, 0x00)))
        assertContentEquals(byteArrayOf(0x6D, 0x00), sharer.answer(byteArrayOf(0x00, 0xB2.toByte(), 0x01, 0x0C, 0x00)))
        assertTrue(sharer.responder.isArmed)
    }

    @Test
    fun `a sharing phone's key swapped on the way is caught by the commitment, and nothing is stored`() {
        val sharer = Sharer()
        val impostor = NfcPairing.newKey(random).point
        // Something between the phones replaces the sharing phone's point in its KEYS answer, keeping the commitment.
        val run = read(sharer, transceive = { command ->
            val answer = sharer.answer(command)
            if (command[1] == 0x10.toByte() && answer.status() == NfcShareResponder.SW_OK) impostor + answer.copyOfRange(65, answer.size) else answer
        })

        assertEquals(NfcShareInitiator.Result.Failed(ShareProblem.Interfered), run.result)
        assertNull(run.code, "no code is shown for an exchange that was tampered with")
        // The reader stopped before ever asking for the card.
        assertTrue(sharer.commands.none { it[1] == 0x14.toByte() })
        assertTrue(sharer.responder.isArmed)
    }

    @Test
    fun `a sealed card that was changed on the way doesn't open`() {
        val sharer = Sharer()
        val run = read(sharer, transceive = { command ->
            val answer = sharer.answer(command)
            // Flip one bit of the sealed card's first piece (after its two length bytes).
            if (command[1] == 0x14.toByte() && answer.status() == NfcShareResponder.SW_OK) answer.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() } else answer
        })
        assertEquals(NfcShareInitiator.Result.Failed(ShareProblem.Broken), run.result)
    }

    @Test
    fun `a card bigger than one answer comes in pieces and is put back together`() {
        val long = card.copy(bankName = "B".repeat(400), cardholderName = "C".repeat(300))
        val payload = long.toNfcExchangePayload()
        assertTrue(payload.length > 500)
        val sharer = Sharer(payload)
        val run = read(sharer)

        assertEquals(payload, assertIs<NfcShareInitiator.Result.Card>(run.result).payload)
        // The asks answered "wait" were CARD commands too; after Match, the pieces are at least three.
        val pieces = sharer.commands.count { it[1] == 0x14.toByte() } - run.waits
        assertTrue(pieces >= 3, "pieces: $pieces")
        // Offsets go up by what was received: 0, 238, 478, ...
        val offsets = sharer.commands.filter { it[1] == 0x14.toByte() }.map { ((it[2].toInt() and 0xFF) shl 8) or (it[3].toInt() and 0xFF) }.distinct()
        assertEquals(listOf(0, 238, 478), offsets.take(3))
    }

    @Test
    fun `nothing is shared while sharing isn't allowed, and the card stays ready`() {
        var allowed = false
        val sharer = Sharer(allowed = { allowed })
        assertEquals(NfcShareInitiator.Result.NotSharing, read(sharer, decide = {}).result)
        assertTrue(sharer.responder.isArmed)

        // Allowed at SELECT, then not any more (NormPix left the front) while the code is up: the share stops.
        allowed = true
        val run = read(sharer, decide = { allowed = false })
        assertEquals(NfcShareInitiator.Result.Failed(ShareProblem.Stopped), run.result)
        assertEquals(1, sharer.events.withdrawn)
        assertTrue(sharer.responder.isArmed)
        assertEquals(0, sharer.events.transferred)
    }

    @Test
    fun `an older NormPix that sends the card in the clear is refused, and nothing is stored`() {
        val sent = mutableListOf<ByteArray>()
        val olderPhone: (ByteArray) -> ByteArray = { command ->
            sent += command
            if (command.contentEquals(NfcShareResponder.SELECT_AID_COMMAND)) card.toNfcExchangePayload().toByteArray() + byteArrayOf(0x90.toByte(), 0x00)
            else byteArrayOf(0x6D, 0x00)
        }
        val result = NfcShareInitiator.run(olderPhone, onCode = { error("no code") }, sleep = {}, clock = { 0L })
        assertEquals(NfcShareInitiator.Result.Failed(ShareProblem.OlderPhone), result)
        assertEquals(1, sent.size)

        // Through the reader and the Inlet: the reason is shown and no card is handed over.
        val tag = object : TagChannel {
            override val techs = setOf("IsoDep", "NfcA")
            override fun <T> isoDep(block: ((ByteArray) -> ByteArray) -> T): T? = block(olderPhone)
            override fun ndefPayloads(): List<ByteArray>? = null
        }
        val read = readTag(tag)
        assertEquals(NfcTagRead.ShareFailed(ShareProblem.OlderPhone), read)
        val inlet = NfcExchangeController(ActivityHost())
        inlet.onTagRead(read, isAlreadySaved = { false }) { error("nothing may be stored") }
        assertEquals(NfcInletState.ShareFailed(ShareProblem.OlderPhone), inlet.inletState.value)
    }

    @Test
    fun `a later exchange version is named as such`() {
        val newer: (ByteArray) -> ByteArray = { byteArrayOf(0x4E, 0x50, 0x58, 0x03, 0x90.toByte(), 0x00) }
        assertEquals(
            NfcShareInitiator.Result.Failed(ShareProblem.NewerPhone),
            NfcShareInitiator.run(newer, onCode = {}, sleep = {}, clock = { 0L }),
        )
    }

    @Test
    fun `nobody tapping Match for a minute ends it, with nothing given`() {
        val sharer = Sharer()
        val run = read(sharer, decide = {})

        assertEquals(NfcShareInitiator.Result.Failed(ShareProblem.TimedOut), run.result)
        // Asked every 250 ms for 60 s.
        assertEquals((NfcShareInitiator.WAIT_MS / NfcShareInitiator.POLL_MS).toInt(), run.waits)
        assertTrue(sharer.responder.isArmed)
        assertEquals(0, sharer.events.transferred)
    }

    @Test
    fun `the phones parting mid-share says to keep them together, and the card stays ready for a new session`() {
        val sharer = Sharer()
        var cardAsks = 0
        val run = read(sharer, transceive = { command ->
            if (command[1] == 0x14.toByte() && ++cardAsks == 2) throw IOException("Tag was lost.")
            sharer.answer(command)
        })
        assertEquals(NfcShareInitiator.Result.Failed(ShareProblem.Parted), run.result)

        // Android tells the service the reader is gone: the code on screen is withdrawn, the card stays armed.
        sharer.responder.linkLost()
        assertEquals(1, sharer.events.withdrawn)
        assertTrue(sharer.responder.isArmed)
        assertIs<NfcShareInitiator.Result.Card>(read(sharer).result)
    }

    @Test
    fun `a sharing phone found through the reader gives the whole card`() {
        val sharer = Sharer()
        val tag = object : TagChannel {
            override val techs = setOf("IsoDep", "NfcA")
            override fun <T> isoDep(block: ((ByteArray) -> ByteArray) -> T): T? = block(sharer::answer)
            override fun ndefPayloads(): List<ByteArray>? = null
        }
        var shown: String? = null
        var now = 0L
        val read = readTag(
            tag,
            onCode = { shown = it },
            sleep = { now += it; sharer.responder.confirm(sharer.events.codes.last().id) },
            clock = { now },
        )
        assertEquals(NfcTagRead.NormPixPayload(card.toNfcExchangePayload()), read)
        assertEquals(sharer.events.codes.single().code, shown)

        val inlet = NfcExchangeController(ActivityHost())
        val received = mutableListOf<StoredCard>()
        inlet.onTagRead(read, isAlreadySaved = { false }) { received += it }
        assertTrue(received.single().sameCardAs(card))
    }
}
