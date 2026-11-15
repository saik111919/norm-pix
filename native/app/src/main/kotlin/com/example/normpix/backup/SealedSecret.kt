package com.example.normpix.backup

import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** A sealed secret can't be had: there is none, its file is damaged, or the Keystore key that sealed it is gone. */
class SealedSecretException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * A small secret kept on this phone only sealed: AES-256-GCM under a key of the Android Keystore ([keys]), which never leaves the
 * device, so the file alone opens nothing. The file is `[12-byte IV][the secret sealed, with its 16-byte tag]`, written to a
 * temporary file and renamed into place, and read back before [seal] returns - as the databases' key is kept (`DatabaseKey`).
 *
 * The secret is handed in and out as bytes the caller zeroes when done with them; nothing here keeps a copy, logs it or puts it
 * anywhere but the file. Keep the file in `noBackupFilesDir`: it is meaningless off this phone, and Android's own backup must not
 * carry it.
 */
class SealedSecret(private val file: File, private val keys: VaultKeyProvider) {
    private val temp get() = File(file.parentFile, file.name + ".tmp")

    /** Whether a secret is kept (whether or not it can still be opened). */
    val exists: Boolean get() = file.isFile && file.length() > 0

    /**
     * Seals [secret] into the file, in place of any kept before, and checks it reads back as it was. Throws [SealedSecretException]
     * if it couldn't be kept; the secret before it may then be gone too. [secret] is the caller's to zero.
     */
    @Synchronized
    fun seal(secret: ByteArray) {
        require(secret.isNotEmpty()) { "nothing to seal" }
        var sealed: ByteArray? = null
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // No IV is given: the cipher makes a random one (the Keystore insists on it), read back from it below.
            cipher.init(Cipher.ENCRYPT_MODE, keys.key())
            sealed = cipher.iv + cipher.doFinal(secret)
            file.parentFile?.mkdirs()
            FileOutputStream(temp).use { out ->
                out.write(sealed)
                out.fd.sync()
            }
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "could not put the sealed file in place" }
            }
        } catch (e: Exception) {
            temp.delete()
            throw SealedSecretException("The secret couldn't be kept.", e)
        } finally {
            sealed?.fill(0)
        }
        val back = open()
        try {
            if (!back.contentEquals(secret)) throw SealedSecretException("The secret didn't read back as it was written.")
        } finally {
            back.fill(0)
        }
    }

    /**
     * The secret, opened: a new array the caller must zero when done. Throws [SealedSecretException] when there is none, the file
     * is damaged or cut short, or it can't be opened on this phone (its Keystore key is gone, or another key sealed it).
     */
    @Synchronized
    fun open(): ByteArray {
        if (!exists) throw SealedSecretException("There is no secret kept.")
        return try {
            val sealed = file.readBytes()
            try {
                require(sealed.size > IV_BYTES + TAG_BYTES) { "sealed file too short" }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BYTES * 8, sealed, 0, IV_BYTES))
                cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
            } finally {
                sealed.fill(0)
            }
        } catch (e: Exception) {
            throw SealedSecretException("The secret can't be opened on this phone.", e)
        }
    }

    /** Forgets the secret: the file (and a half-written one) deleted. True when none is left. */
    @Synchronized
    fun delete(): Boolean {
        temp.delete()
        return !file.exists() || file.delete()
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}

/**
 * [chars] as UTF-8 bytes, in a new array the caller zeroes; the encoder's own buffer is zeroed here. [chars] is left as it was.
 * Throws [java.nio.charset.CharacterCodingException] for text that isn't valid (a lone surrogate).
 */
internal fun utf8Bytes(chars: CharArray): ByteArray {
    val encoder = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val buffer = encoder.encode(CharBuffer.wrap(chars))
    return try {
        ByteArray(buffer.remaining()).also { buffer.get(it) }
    } finally {
        if (buffer.hasArray()) buffer.array().fill(0)
    }
}

/** [bytes] (UTF-8) as characters, in a new array the caller zeroes; the decoder's own buffer is zeroed here. [bytes] is left as it was. */
internal fun utf8Chars(bytes: ByteArray): CharArray {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val buffer = decoder.decode(ByteBuffer.wrap(bytes))
    return try {
        CharArray(buffer.remaining()).also { buffer.get(it) }
    } finally {
        if (buffer.hasArray()) buffer.array().fill('\u0000')
    }
}
