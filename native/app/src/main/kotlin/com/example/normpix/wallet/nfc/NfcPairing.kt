package com.example.normpix.wallet.nfc

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * The cryptography of sharing a card between two phones over NFC (version 2 of the exchange; version 1 sent the card as plain
 * JSON). Owner's choice, 2026-11-14: the design Bluetooth pairing calls "numeric comparison" - both phones show the same 6-digit
 * code, the person sharing checks the two screens match and taps Match, and only then does the card leave the phone, sealed for
 * the other phone alone.
 *
 * Researched 2026-11-14 (Haiku 5.5 xhigh and Gemini 3.8 Flash High, checked against developer.android.com, RFC 5869, RFC 6189 and
 * the Bluetooth numeric-comparison order): P-256 ECDH is in the platform from long before minSdk 26 (X25519 only from API 33);
 * there is no HKDF in the platform, so it is built here from HMAC-SHA256 (RFC 5869); the sharing phone COMMITS to its nonce before
 * it learns the reader's - without that, a man in the middle could try nonces until both screens showed the same code.
 *
 * The exchange (R = the phone receiving, the NFC reader; S = the phone sharing, the card emulation service):
 *   1. R -> S  SELECT the AID                     S -> R  its version marker
 *   2. R -> S  KEYS  R's public point (65 B)      S -> R  S's public point (65 B) + commitment Cs (32 B)
 *   3. R -> S  NONCE R's nonce Nr (16 B)          S -> R  S's nonce Ns (16 B); R checks Cs against it
 *      Both work out the same code and the same card key; both show the code.
 *   4. R -> S  CARD (offset)                      S -> R  "waiting" until S's user taps Match, then the sealed card in chunks
 * A recording of all of it gives nothing to guess: the code and the key come from the ECDH secret, which never travels.
 *
 * What this does NOT stop (said in the UI, not solvable here): a relay that faithfully forwards both sides - both screens then
 * show the same code, honestly - so the person sharing should only tap Match for a phone they can see.
 */
object NfcPairing {
    const val VERSION = 2

    /** An uncompressed P-256 point: 0x04, then X and Y of 32 bytes each. */
    const val POINT_BYTES = 65
    const val NONCE_BYTES = 16
    const val COMMIT_BYTES = 32
    const val CODE_DIGITS = 6

    private const val COORD_BYTES = 32
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private val LABEL = "NormPix-NFC-v2".toByteArray(Charsets.US_ASCII)
    private val INFO_CODE = "NormPix-NFC-v2 code".toByteArray(Charsets.US_ASCII)
    private val INFO_CARD = "NormPix-NFC-v2 card-aes256gcm".toByteArray(Charsets.US_ASCII)

    /**
     * The fixed start of a P-256 public key's X.509 SubjectPublicKeyInfo (the algorithm and curve), which with the 65-byte point
     * makes the 91 bytes `X509EncodedKeySpec` reads (RFC 5480). Checked against a generated key in the tests.
     */
    private val SPKI_PREFIX = hex("3059301306072a8648ce3d020106082a8648ce3d030107034200")

    // P-256's prime and curve constant b (a = p - 3), to check that a point received is on the curve before it is used.
    private val P = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
    private val B = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)

    /** One phone's key for one share: made fresh every time, never stored. [point] is what is sent. */
    class EphemeralKey internal constructor(internal val private: PrivateKey, val point: ByteArray)

    /** What both phones end up with: the code to compare and the key the card is sealed with. Lives for one share only. */
    class Session internal constructor(val code: String, internal val cardKey: SecretKeySpec, internal val transcript: ByteArray)

    fun newKey(random: SecureRandom = SecureRandom()): EphemeralKey {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), random)
        val pair = generator.generateKeyPair()
        return EphemeralKey(pair.private, pointOf(pair.public as ECPublicKey))
    }

    fun newNonce(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(NONCE_BYTES).also(random::nextBytes)

    /** The sharing phone's commitment to [senderNonce], made once it has the reader's point and before it sees the reader's nonce. */
    fun commitment(senderNonce: ByteArray, senderPoint: ByteArray, readerPoint: ByteArray): ByteArray =
        hmac(senderNonce, LABEL, senderPoint, readerPoint)

    /** Whether [commitment] (received at step 2) is the one [senderNonce] (received at step 3) was committed with. */
    fun commitmentHolds(commitment: ByteArray, senderNonce: ByteArray, senderPoint: ByteArray, readerPoint: ByteArray): Boolean =
        senderNonce.size == NONCE_BYTES && MessageDigest.isEqual(commitment, commitment(senderNonce, senderPoint, readerPoint))

    /**
     * The session both phones derive: [own] is this phone's key, [otherPoint] the other phone's point, and the points and nonces
     * are given as sender's and reader's so both sides feed them in the same order. Null when [otherPoint] isn't a valid point on
     * P-256 or a nonce has the wrong length - the share is then abandoned.
     */
    fun session(
        own: EphemeralKey,
        otherPoint: ByteArray,
        senderPoint: ByteArray,
        readerPoint: ByteArray,
        senderNonce: ByteArray,
        readerNonce: ByteArray,
    ): Session? {
        if (senderNonce.size != NONCE_BYTES || readerNonce.size != NONCE_BYTES) return null
        if (!MessageDigest.isEqual(otherPoint, senderPoint) && !MessageDigest.isEqual(otherPoint, readerPoint)) return null
        val other = publicKeyOf(otherPoint) ?: return null
        val shared = try {
            KeyAgreement.getInstance("ECDH").run {
                init(own.private)
                doPhase(other, true)
                generateSecret()
            }
        } catch (e: Exception) {
            return null
        }
        val prk = hmac(senderNonce + readerNonce, shared)
        shared.fill(0)
        val transcript = MessageDigest.getInstance("SHA-256").run {
            update(LABEL)
            update(senderPoint)
            update(readerPoint)
            update(senderNonce)
            update(readerNonce)
            digest()
        }
        val codeKey = expand(prk, INFO_CODE)
        val codeBytes = hmac(codeKey, transcript)
        codeKey.fill(0)
        // Eight bytes taken modulo a million: far more than six digits' worth, so every code is (practically) as likely as any other.
        var value = 0L
        for (i in 0 until 8) value = (value shl 8) or (codeBytes[i].toLong() and 0xFF)
        val code = java.lang.Long.remainderUnsigned(value, 1_000_000L).toString().padStart(CODE_DIGITS, '0')
        val cardKey = expand(prk, INFO_CARD)
        prk.fill(0)
        return Session(code, SecretKeySpec(cardKey, "AES"), transcript).also { cardKey.fill(0) }
    }

    /** [card] sealed for the other phone: `[12-byte nonce][ciphertext + tag]`, bound to this session's transcript. */
    fun seal(session: Session, card: ByteArray, random: SecureRandom = SecureRandom()): ByteArray {
        val iv = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, session.cardKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(session.transcript)
        return iv + cipher.doFinal(card)
    }

    /** The card in [sealed], or null when it wasn't sealed in this session (another key, changed, cut short). */
    fun open(session: Session, sealed: ByteArray): ByteArray? {
        if (sealed.size <= GCM_NONCE_BYTES + GCM_TAG_BITS / 8) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, session.cardKey, GCMParameterSpec(GCM_TAG_BITS, sealed, 0, GCM_NONCE_BYTES))
            cipher.updateAAD(session.transcript)
            cipher.doFinal(sealed, GCM_NONCE_BYTES, sealed.size - GCM_NONCE_BYTES)
        } catch (e: AEADBadTagException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /** [key]'s point, uncompressed: 0x04, then X and Y, each exactly 32 bytes. */
    internal fun pointOf(key: ECPublicKey): ByteArray =
        byteArrayOf(0x04) + fixed(key.w.affineX) + fixed(key.w.affineY)

    /**
     * The P-256 public key at [point], or null when it isn't an uncompressed point on the curve: a point off the curve could leak
     * bits of this phone's key (an "invalid curve" attack), and whether the platform's KeyFactory checks this was not established.
     */
    internal fun publicKeyOf(point: ByteArray): PublicKey? {
        if (point.size != POINT_BYTES || point[0] != 0x04.toByte()) return null
        val x = BigInteger(1, point.copyOfRange(1, 1 + COORD_BYTES))
        val y = BigInteger(1, point.copyOfRange(1 + COORD_BYTES, POINT_BYTES))
        if (x >= P || y >= P) return null
        // y^2 = x^3 - 3x + b (mod p)
        val left = y.multiply(y).mod(P)
        val right = x.multiply(x).multiply(x).subtract(x.multiply(BigInteger.valueOf(3))).add(B).mod(P)
        if (left != right) return null
        return try {
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(SPKI_PREFIX + point))
        } catch (e: Exception) {
            null
        }
    }

    /** RFC 5869 HKDF-Expand for one 32-byte block (all this needs): T(1) = HMAC(prk, info || 0x01). */
    internal fun expand(prk: ByteArray, info: ByteArray): ByteArray = hmac(prk, info, byteArrayOf(0x01))

    internal fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        for (part in parts) mac.update(part)
        return mac.doFinal()
    }

    /** [n] as exactly 32 big-endian bytes (BigInteger adds a sign byte, or drops leading zeros). */
    private fun fixed(n: BigInteger): ByteArray {
        val raw = n.toByteArray()
        return when {
            raw.size == COORD_BYTES -> raw
            raw.size > COORD_BYTES -> raw.copyOfRange(raw.size - COORD_BYTES, raw.size)
            else -> ByteArray(COORD_BYTES - raw.size) + raw
        }
    }

    internal val spkiPrefix: ByteArray get() = SPKI_PREFIX.copyOf()

    private fun hex(text: String): ByteArray = ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
