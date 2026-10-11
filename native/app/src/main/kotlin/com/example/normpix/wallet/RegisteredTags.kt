package com.example.normpix.wallet

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the (already encrypted) tag entries are kept. */
interface TagStore {
    fun load(): Set<String>

    fun save(entries: Set<String>)
}

class InMemoryTagStore : TagStore {
    private var entries: Set<String> = emptySet()

    override fun load(): Set<String> = entries

    override fun save(entries: Set<String>) {
        this.entries = entries.toSet()
    }
}

class PrefsTagStore(private val prefs: SharedPreferences) : TagStore {
    override fun load(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet().orEmpty()

    override fun save(entries: Set<String>) {
        prefs.edit { putStringSet(KEY, entries.toSet()) }
    }

    private companion object {
        const val KEY = "tags"
    }
}

/** What came of asking to register a tag. */
enum class TagRegistration { Added, AlreadyRegistered, ChangesEveryTap, LimitReached, Unavailable }

/**
 * The NFC tags the user has registered as theirs, so that revealing a card can ask for one of *these* and not just any
 * tag or phone that happens to be tapped.
 *
 * What identifies a tag is its hardware ID. It is a convenience lock, not a cryptographic one - anyone who can read a
 * tag can read its ID, and some can be copied - but it turns "any tag at all" into "this particular one". Tags (and
 * phones) that show a different random ID every tap can't be recognised, so they can't be registered.
 *
 * IDs are only ever stored encrypted (AES-256-GCM, key from [keys] - the Android Keystore in the app), each with its
 * own IV, so the stored entries say nothing about the tag even to someone holding the preferences file. They are
 * never logged. If the key is gone (a restored or reset device), the entries can't be read and count as no tags.
 */
class RegisteredTags(
    private val store: TagStore = InMemoryTagStore(),
    private val keys: VaultKeyProvider = ephemeralKeys(),
    val limit: Int = MAX_TAGS,
) {
    private val registered = MutableStateFlow(readable().size)

    /** How many tags are registered (observable, for settings). */
    val count: StateFlow<Int> = registered.asStateFlow()

    /** Whether [id] is one of the registered tags. Looks at every entry either way, so timing doesn't tell which matched. */
    fun matches(id: ByteArray): Boolean {
        var found = false
        for ((_, known) in readable()) if (MessageDigest.isEqual(known, id)) found = true
        return found
    }

    fun register(id: ByteArray): TagRegistration {
        if (!isRecognisable(id)) return TagRegistration.ChangesEveryTap
        val known = try {
            readable()
        } catch (_: Exception) {
            return TagRegistration.Unavailable
        }
        if (known.any { MessageDigest.isEqual(it.second, id) }) return TagRegistration.AlreadyRegistered
        if (known.size >= limit) return TagRegistration.LimitReached
        val entry = runCatching { encrypt(id) }.getOrNull() ?: return TagRegistration.Unavailable
        // Entries that can no longer be read are dropped here: they could never match anything.
        store.save(known.map { it.first }.toSet() + entry)
        registered.value = known.size + 1
        return TagRegistration.Added
    }

    fun clear() {
        store.save(emptySet())
        registered.value = 0
    }

    /** The stored entries that can be read (with their IDs), skipping any that can't. */
    private fun readable(): List<Pair<String, ByteArray>> =
        store.load().mapNotNull { entry -> decrypt(entry)?.let { entry to it } }

    private fun encrypt(id: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())
        cipher.updateAAD(AAD)
        val body = cipher.doFinal(id)
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    private fun decrypt(entry: String): ByteArray? = runCatching {
        val raw = Base64.decode(entry, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES))
        cipher.updateAAD(AAD)
        cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES)
    }.getOrNull()

    companion object {
        const val MAX_TAGS = 3
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private val AAD = "normpix-registered-nfc-tag".toByteArray()

        /**
         * Whether a tag shows the same ID every time. A 4-byte ID starting 0x08 is, by ISO 14443-3, a random one made
         * up fresh for each tap (phones emulating a card, many payment cards): nothing to recognise it by.
         */
        fun isRecognisable(id: ByteArray): Boolean = id.isNotEmpty() && !(id.size == 4 && id[0] == RANDOM_ID_PREFIX)

        private const val RANDOM_ID_PREFIX: Byte = 0x08

        /** A key that lives only as long as the process: for tests and anywhere nothing must survive a restart. */
        fun ephemeralKeys(): VaultKeyProvider {
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            return VaultKeyProvider { key }
        }
    }
}
