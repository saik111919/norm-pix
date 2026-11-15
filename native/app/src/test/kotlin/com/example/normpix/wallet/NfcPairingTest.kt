package com.example.normpix.wallet

import com.example.normpix.wallet.nfc.NfcPairing
import java.math.BigInteger
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The card-share cryptography (exchange version 2): both phones reach the same code and key from what they exchange, a phone in
 * the middle can't, the commitment binds the sharing phone's nonce, and nothing off the curve is ever used.
 */
class NfcPairingTest {
    private val random = SecureRandom()

    /** Runs the exchange as the two phones would, with an optional phone in the middle replacing the reader's point. */
    private class Run(val sender: NfcPairing.Session?, val reader: NfcPairing.Session?)

    private fun exchange(middle: Boolean = false): Run {
        val readerKey = NfcPairing.newKey(random)
        val senderKey = NfcPairing.newKey(random)
        val attacker = NfcPairing.newKey(random)
        // What the sender receives as the reader's point: the attacker's, when there is one in the middle.
        val readerPointAtSender = if (middle) attacker.point else readerKey.point
        val senderNonce = NfcPairing.newNonce(random)
        val commitment = NfcPairing.commitment(senderNonce, senderKey.point, readerPointAtSender)
        val readerNonce = NfcPairing.newNonce(random)
        // What the reader receives as the sender's point: the attacker's too, when there is one in the middle.
        val senderPointAtReader = if (middle) attacker.point else senderKey.point
        val commitmentAtReader = if (middle) NfcPairing.commitment(senderNonce, attacker.point, readerKey.point) else commitment
        assertTrue(NfcPairing.commitmentHolds(commitmentAtReader, senderNonce, senderPointAtReader, readerKey.point))
        val sender = NfcPairing.session(senderKey, readerPointAtSender, senderKey.point, readerPointAtSender, senderNonce, readerNonce)
        val reader = NfcPairing.session(readerKey, senderPointAtReader, senderPointAtReader, readerKey.point, senderNonce, readerNonce)
        return Run(sender, reader)
    }

    @Test
    fun `both phones reach the same six-digit code and the card crosses sealed`() {
        repeat(20) {
            val run = exchange()
            val sender = assertNotNull(run.sender)
            val reader = assertNotNull(run.reader)
            assertEquals(sender.code, reader.code)
            assertEquals(NfcPairing.CODE_DIGITS, sender.code.length)
            assertTrue(sender.code.all(Char::isDigit))
            val card = """{"type":"normpix_card","cardNumber":"4111111111111111","cvv":"123"}""".toByteArray()
            val sealed = NfcPairing.seal(sender, card, random)
            assertFalse(String(sealed, Charsets.ISO_8859_1).contains("4111111111111111"), "the number must not travel readable")
            assertContentEquals(card, NfcPairing.open(reader, sealed))
        }
    }

    @Test
    fun `a phone in the middle gets different codes on the two sides and can't open the card for the reader`() {
        var differed = 0
        repeat(20) {
            val run = exchange(middle = true)
            val sender = assertNotNull(run.sender)
            val reader = assertNotNull(run.reader)
            if (sender.code != reader.code) differed++
            // Sealed for whoever the sender agreed a key with (the attacker): the real reader can't open it.
            assertNull(NfcPairing.open(reader, NfcPairing.seal(sender, "card".toByteArray(), random)))
        }
        // Equal by chance is one in a million per run; twenty runs all differing is what is expected.
        assertEquals(20, differed)
    }

    @Test
    fun `the commitment binds the sender's nonce and both points`() {
        val s = NfcPairing.newKey(random)
        val r = NfcPairing.newKey(random)
        val nonce = NfcPairing.newNonce(random)
        val commitment = NfcPairing.commitment(nonce, s.point, r.point)
        assertEquals(NfcPairing.COMMIT_BYTES, commitment.size)
        assertTrue(NfcPairing.commitmentHolds(commitment, nonce, s.point, r.point))
        assertFalse(NfcPairing.commitmentHolds(commitment, NfcPairing.newNonce(random), s.point, r.point), "another nonce")
        assertFalse(NfcPairing.commitmentHolds(commitment, nonce, r.point, s.point), "points swapped")
        assertFalse(NfcPairing.commitmentHolds(commitment, nonce, s.point, NfcPairing.newKey(random).point), "another reader")
        assertFalse(NfcPairing.commitmentHolds(commitment, nonce.copyOf(8), s.point, r.point), "short nonce")
    }

    @Test
    fun `a sealed card that was changed or cut short doesn't open`() {
        val run = exchange()
        val sealed = NfcPairing.seal(run.sender!!, "card".toByteArray(), random)
        assertNull(NfcPairing.open(run.reader!!, sealed.copyOf(sealed.size - 1)))
        assertNull(NfcPairing.open(run.reader!!, sealed.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }))
        assertNull(NfcPairing.open(run.reader!!, ByteArray(10)))
    }

    @Test
    fun `points that aren't on P-256 are refused`() {
        val good = NfcPairing.newKey(random).point
        assertNotNull(NfcPairing.publicKeyOf(good))
        assertNull(NfcPairing.publicKeyOf(good.copyOf(64)), "short")
        assertNull(NfcPairing.publicKeyOf(good.copyOf().also { it[0] = 0x02 }), "compressed form")
        assertNull(NfcPairing.publicKeyOf(good.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }), "off the curve")
        val p = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
        val tooBig = byteArrayOf(0x04) + p.toByteArray().takeLast(32).toByteArray() + good.copyOfRange(33, 65)
        assertNull(NfcPairing.publicKeyOf(tooBig), "x not below p")
        // A session refuses a bad point outright.
        val own = NfcPairing.newKey(random)
        val bad = good.copyOf().also { it[64] = (it[64].toInt() xor 1).toByte() }
        assertNull(NfcPairing.session(own, bad, bad, own.point, NfcPairing.newNonce(random), NfcPairing.newNonce(random)))
    }

    @Test
    fun `the point and the X509 prefix rebuild exactly the platform's own encoding`() {
        repeat(10) {
            val generator = java.security.KeyPairGenerator.getInstance("EC")
            generator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"), random)
            val pub = generator.generateKeyPair().public as java.security.interfaces.ECPublicKey
            val point = NfcPairing.pointOf(pub)
            assertEquals(NfcPairing.POINT_BYTES, point.size)
            assertContentEquals(pub.encoded, NfcPairing.spkiPrefix + point)
            assertContentEquals(pub.encoded, NfcPairing.publicKeyOf(point)!!.encoded)
        }
    }

    @Test
    fun `HKDF matches RFC 5869 test case 1`() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = ByteArray(13) { it.toByte() }
        val info = ByteArray(10) { (0xf0 + it).toByte() }
        val prk = NfcPairing.hmac(salt, ikm)
        assertEquals("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5", prk.hex())
        // The first 32 bytes of the RFC's 42-byte OKM are T(1).
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf", NfcPairing.expand(prk, info).hex())
    }

    @Test
    fun `each share has its own code`() {
        val codes = (1..30).map { exchange().sender!!.code }.toSet()
        assertNotEquals(1, codes.size)
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
