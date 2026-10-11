package com.example.normpix.wallet

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The vault could not be read or written (corrupt file, or its key is gone). */
class CardVaultException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Local-only encrypted storage for [StoredCard]s. */
interface CardVault {
    /** Every stored card, or an empty list on first run. Throws [CardVaultException] if unreadable. */
    suspend fun loadCards(): List<StoredCard>

    /** Replaces the stored cards. */
    suspend fun saveCards(cards: List<StoredCard>)
}

/** Supplies the AES key the vault encrypts with. */
fun interface VaultKeyProvider {
    fun key(): SecretKey
}

/**
 * An AES-256 key that lives in the Android Keystore and never leaves it: even
 * with the vault file in hand, the key cannot be extracted from the device.
 */
class KeystoreKeyProvider(private val alias: String = DEFAULT_ALIAS) : VaultKeyProvider {
    override fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "normpix_card_vault_key_v1"
        private const val PROVIDER = "AndroidKeyStore"
    }
}

/**
 * Cards as one AES-256-GCM encrypted file: `[12-byte IV][ciphertext + 16-byte tag]`.
 * Writes go to a temporary file first and are renamed into place, so a crash
 * mid-write can never leave a half-written vault.
 */
class FileCardVault(
    private val file: File,
    private val keys: VaultKeyProvider = KeystoreKeyProvider(),
) : CardVault {
    private val lock = Mutex()

    override suspend fun loadCards(): List<StoredCard> = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!file.exists() || file.length() == 0L) return@withLock emptyList()
            try {
                val raw = file.readBytes()
                require(raw.size > IV_BYTES + TAG_BYTES) { "vault file too short" }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BYTES * 8, raw, 0, IV_BYTES))
                val clear = cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES)
                val array = Json.parseToJsonElement(clear.toString(Charsets.UTF_8)) as JsonArray
                array.map { StoredCard.fromJson(it as JsonObject) }
            } catch (e: Exception) {
                throw CardVaultException("The card vault can't be read on this device.", e)
            }
        }
    }

    override suspend fun saveCards(cards: List<StoredCard>) = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val clear = Json.encodeToString(JsonElement.serializer(), JsonArray(cards.map { it.toJson() }))
                    .toByteArray(Charsets.UTF_8)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, keys.key())
                val sealed = cipher.iv + cipher.doFinal(clear)
                val temp = File(file.parentFile, file.name + ".tmp")
                temp.writeBytes(sealed)
                if (!temp.renameTo(file)) {
                    file.delete()
                    check(temp.renameTo(file)) { "could not replace the vault file" }
                }
            } catch (e: Exception) {
                throw CardVaultException("The card vault couldn't be saved.", e)
            }
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
    }
}
