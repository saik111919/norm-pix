package com.example.normpix.storage

import com.example.normpix.wallet.KeystoreKeyProvider
import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** The databases' key can't be had: it is gone, or there is none and none may be made. */
class DatabaseKeyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The key the databases are encrypted with: [KEY_BYTES] random bytes made once on this phone and kept in [file], sealed by a key of
 * the Android Keystore with AES-256-GCM - as the card vault is - so the file alone opens nothing and the Keystore key never leaves
 * the device. The file is `[12-byte IV][the key sealed, with its 16-byte tag]`, written to a temporary file and renamed into place.
 */
class DatabaseKey(
    private val file: File,
    private val keys: VaultKeyProvider = KeystoreKeyProvider(alias = ALIAS),
    private val random: SecureRandom = SecureRandom(),
) {
    /** Whether a key has been made on this phone (whether or not it can still be opened). */
    val exists: Boolean get() = file.exists() && file.length() > 0

    /**
     * The key. The first time, when [mayCreate], it is made, written and read back before it is given out, so nothing is ever
     * encrypted with a key that wasn't kept. Throws [DatabaseKeyException] when there is none and it may not be made, or when the
     * file can't be opened (its Keystore key is gone).
     */
    @Synchronized
    fun get(mayCreate: Boolean): ByteArray {
        if (exists) return open()
        if (!mayCreate) throw DatabaseKeyException("There is no database key.")
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keys.key())
            val temp = File(file.parentFile, file.name + ".tmp")
            file.parentFile?.mkdirs()
            temp.writeBytes(cipher.iv + cipher.doFinal(key))
            if (!temp.renameTo(file)) {
                file.delete()
                check(temp.renameTo(file)) { "could not put the key file in place" }
            }
        } catch (e: Exception) {
            throw DatabaseKeyException("The database key couldn't be kept.", e)
        }
        if (!open().contentEquals(key)) throw DatabaseKeyException("The database key didn't read back as it was written.")
        return key
    }

    /** Forgets the key, so that a new one is made next. Only for a key nothing is encrypted with. */
    @Synchronized
    fun discard() {
        file.delete()
    }

    private fun open(): ByteArray = try {
        val sealed = file.readBytes()
        require(sealed.size == IV_BYTES + KEY_BYTES + TAG_BYTES) { "key file of the wrong size" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BYTES * 8, sealed, 0, IV_BYTES))
        cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    } catch (e: Exception) {
        throw DatabaseKeyException("The database key can't be opened on this phone.", e)
    }

    companion object {
        const val ALIAS = "normpix_database_key_v1"
        const val FILE_NAME = "database.key"
        const val KEY_BYTES = 32
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BYTES = 16
    }
}
