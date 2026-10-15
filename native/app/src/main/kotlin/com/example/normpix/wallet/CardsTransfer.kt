package com.example.normpix.wallet

import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Saved cards as a file of their own, to move them to another phone or keep a copy: separate from the finance backup, which
 * never carries card numbers. A card file carries everything a card has - number, expiry, CVV - so it is always encrypted,
 * with a password the user chooses: AES-256-GCM under a key drawn from the password by PBKDF2-HMAC-SHA256.
 *
 * The file is JSON: `{"format": "normpix-cards", "version": 1, "kdf": ..., "iterations": n, "salt": b64, "iv": b64,
 * "data": b64}`, where `data` is the ciphertext (with its tag) of `{"exportedAt": ..., "cards": [card, ...]}`. Only the
 * format, version and key parameters are readable without the password; how many cards there are is not.
 */
object CardsTransfer {
    const val FORMAT = "normpix-cards"
    const val VERSION = 1

    /** The shortest password a card file may be locked with. */
    const val MIN_PASSWORD = 8

    /** PBKDF2 rounds for a new file: slow enough to make guessing expensive, quick enough on a phone (well under a second). */
    const val ITERATIONS = 310_000

    /** What a file may ask for: a file is read with the rounds it says, but never so few they protect nothing or so many that reading hangs. */
    private val ITERATIONS_ALLOWED = 10_000..5_000_000

    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /** The cards in a card file, locked with [password]. */
    fun encode(
        cards: List<StoredCard>,
        password: CharArray,
        exportedAt: LocalDateTime,
        random: SecureRandom = SecureRandom(),
        iterations: Int = ITERATIONS,
    ): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val clear = Json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("exportedAt", exportedAt.toString())
                put("cards", JsonArray(cards.map { it.toJson() }))
            },
        ).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header(iterations).toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(clear)
        clear.fill(0)
        val b64 = Base64.getEncoder()
        return Json.encodeToString(
            JsonElement.serializer(),
            buildJsonObject {
                put("format", FORMAT)
                put("version", VERSION)
                put("kdf", KDF)
                put("iterations", iterations)
                put("salt", b64.encodeToString(salt))
                put("iv", b64.encodeToString(iv))
                put("data", b64.encodeToString(sealed))
            },
        )
    }

    /** What reading a card file came to. */
    sealed interface Result {
        data class Cards(val cards: List<StoredCard>, val exportedAt: LocalDateTime?) : Result

        /** The password doesn't open it (or the file was changed since it was written: the two can't be told apart). */
        data object WrongPassword : Result

        /** Not a card file NormPix can read; [reason] says why, for the user. */
        data class Invalid(val reason: String) : Result
    }

    /** True when [text] looks like a card file at all, before any password is asked for. */
    fun isCardFile(text: String): Boolean = runCatching {
        (Json.parseToJsonElement(text) as? JsonObject)?.get("format")?.let { (it as? JsonPrimitive)?.content } == FORMAT
    }.getOrDefault(false)

    fun decode(text: String, password: CharArray): Result {
        val root = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return Result.Invalid("This file isn't a NormPix cards file.")
        fun field(name: String) = (root[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (field("format") != FORMAT) return Result.Invalid("This file isn't a NormPix cards file.")
        val version = (root["version"] as? JsonPrimitive)?.intOrNull
        if (version == null || version > VERSION) return Result.Invalid("This cards file was made by a newer NormPix. Update the app to open it.")
        if (field("kdf") != KDF) return Result.Invalid("This cards file is locked in a way NormPix can't open.")
        val iterations = (root["iterations"] as? JsonPrimitive)?.intOrNull?.takeIf { it in ITERATIONS_ALLOWED }
            ?: return Result.Invalid("This cards file is damaged.")
        val b64 = Base64.getDecoder()
        val salt = field("salt")?.let { runCatching { b64.decode(it) }.getOrNull() }
        val iv = field("iv")?.let { runCatching { b64.decode(it) }.getOrNull() }
        val data = field("data")?.let { runCatching { b64.decode(it) }.getOrNull() }
        if (salt == null || salt.size < 8 || iv == null || iv.size != IV_BYTES || data == null || data.size <= TAG_BITS / 8) {
            return Result.Invalid("This cards file is damaged.")
        }
        val clear = try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header(iterations).toByteArray(Charsets.UTF_8))
            cipher.doFinal(data)
        } catch (_: AEADBadTagException) {
            return Result.WrongPassword
        } catch (_: Exception) {
            return Result.Invalid("This cards file is damaged.")
        }
        return try {
            val content = Json.parseToJsonElement(clear.toString(Charsets.UTF_8)) as JsonObject
            val cards = (content["cards"] as JsonArray).map { StoredCard.fromJson(it as JsonObject) }
            val exportedAt = (content["exportedAt"] as? JsonPrimitive)?.content?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            Result.Cards(cards, exportedAt)
        } catch (_: Exception) {
            Result.Invalid("This cards file is damaged.")
        } finally {
            clear.fill(0)
        }
    }

    /**
     * What the cards file says about how it is locked, bound into the encryption: a file whose rounds were changed after it
     * was written fails to open rather than open with weaker protection than it was made with.
     */
    private fun header(iterations: Int) = "$FORMAT/$VERSION/$KDF/$iterations"

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** What adding [incoming] to [existing] does: the cards that are new, and how many were already there. */
    data class Merge(val added: List<StoredCard>, val alreadyThere: Int) {
        fun applyTo(existing: List<StoredCard>): List<StoredCard> = existing + added
    }

    /**
     * Adds [incoming] to [existing] without ever replacing or duplicating a card: one already saved - the same card (by id),
     * or the same number with the same expiry - is left as it is, and counted in [Merge.alreadyThere].
     */
    fun merge(existing: List<StoredCard>, incoming: List<StoredCard>): Merge {
        val ids = existing.mapTo(HashSet()) { it.id }
        val numbers = existing.mapTo(HashSet()) { sameCardKey(it) }
        val added = mutableListOf<StoredCard>()
        var already = 0
        for (card in incoming) {
            val key = sameCardKey(card)
            if (card.id in ids || key in numbers) {
                already++
            } else {
                added += card
                ids += card.id
                numbers += key
            }
        }
        return Merge(added, already)
    }

    private fun sameCardKey(card: StoredCard) =
        card.cardNumber.filter(Char::isDigit) + "|" + card.expiryMonth.trim().trimStart('0') + "/" + card.expiryYear.trim().takeLast(2)
}
