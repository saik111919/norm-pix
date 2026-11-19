package com.example.normpix.wallet.passes

import com.example.normpix.wallet.KeystoreKeyProvider
import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The pass vault could not be read or written (a damaged file, or its key is gone). */
class PassVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Local-only encrypted storage for [StoredPass]es. */
interface PassVault {
    /** Every stored pass, or none on first run. Throws [PassVaultException] if unreadable. */
    suspend fun load(): List<StoredPass>

    /** Replaces the stored passes. */
    suspend fun save(passes: List<StoredPass>)
}

/**
 * Passes as one AES-256-GCM encrypted file of their own, beside the card vault and made the same way - `[12-byte IV][ciphertext + 16-byte
 * tag]` of a JSON list, under the card vault's Keystore key, written to a temporary file and renamed into place so a crash mid-write
 * never leaves half a vault. A file of its own so the card vault's file and its format stay exactly as they were.
 */
class FilePassVault(
    private val file: File,
    private val keys: VaultKeyProvider = KeystoreKeyProvider(),
) : PassVault {
    private val lock = Mutex()

    override suspend fun load(): List<StoredPass> = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!file.exists() || file.length() == 0L) return@withLock emptyList()
            try {
                val raw = file.readBytes()
                require(raw.size > IV_BYTES + TAG_BYTES) { "pass vault file too short" }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BYTES * 8, raw, 0, IV_BYTES))
                val clear = cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES)
                try {
                    (Json.parseToJsonElement(clear.toString(Charsets.UTF_8)) as JsonArray).map { StoredPass.fromJson(it as JsonObject) }
                } finally {
                    clear.fill(0)
                }
            } catch (e: Exception) {
                throw PassVaultException("Your loyalty and gift cards can't be read on this device.", e)
            }
        }
    }

    override suspend fun save(passes: List<StoredPass>) = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val clear = Json.encodeToString(JsonElement.serializer(), JsonArray(passes.map { it.toJson() })).toByteArray(Charsets.UTF_8)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, keys.key())
                val sealed = cipher.iv + cipher.doFinal(clear)
                clear.fill(0)
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writeBytes(sealed)
                if (!temp.renameTo(file)) {
                    file.delete()
                    check(temp.renameTo(file)) { "could not replace the pass vault file" }
                }
            } catch (e: Exception) {
                throw PassVaultException("Your loyalty and gift cards couldn't be saved.", e)
            }
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}

/** Passes kept in memory only: the sample data's, and tests'. */
class InMemoryPassVault(initial: List<StoredPass> = emptyList()) : PassVault {
    @Volatile private var passes = initial

    override suspend fun load(): List<StoredPass> = passes

    override suspend fun save(passes: List<StoredPass>) {
        this.passes = passes
    }
}
