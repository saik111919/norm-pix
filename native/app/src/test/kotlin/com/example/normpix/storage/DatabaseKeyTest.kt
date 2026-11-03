package com.example.normpix.storage

import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The databases' key: made once, kept only sealed by the Keystore's key, never made again while it can't be opened unless asked. */
class DatabaseKeyTest {
    private val dir = Files.createTempDirectory("database-key").toFile()
    private val file = File(dir, DatabaseKey.FILE_NAME)
    private fun keystoreKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val keystore = keystoreKey()
    private fun key(sealedBy: javax.crypto.SecretKey = keystore) = DatabaseKey(file, VaultKeyProvider { sealedBy })

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `the key is made once and is the same ever after`() {
        val made = key().get(mayCreate = true)
        assertEquals(DatabaseKey.KEY_BYTES, made.size)
        assertContentEquals(made, key().get(mayCreate = true))
        assertContentEquals(made, key().get(mayCreate = false))
    }

    @Test
    fun `the file holds the key sealed, never as it is, and no half-written file is left`() {
        val made = key().get(mayCreate = true)
        val bytes = file.readBytes()
        assertEquals(12 + DatabaseKey.KEY_BYTES + 16, bytes.size)
        assertFalse(bytes.toList().windowed(8).any { it == made.take(8) }, "no part of the key in the clear")
        assertFalse(File(dir, DatabaseKey.FILE_NAME + ".tmp").exists())
    }

    @Test
    fun `no key is made where none may be`() {
        assertFailsWith<DatabaseKeyException> { key().get(mayCreate = false) }
        assertFalse(file.exists())
    }

    @Test
    fun `a key sealed by a Keystore key that is gone can't be opened, and is not replaced`() {
        key().get(mayCreate = true)
        val sealed = file.readBytes()
        assertFailsWith<DatabaseKeyException> { key(sealedBy = keystoreKey()).get(mayCreate = true) }
        assertContentEquals(sealed, file.readBytes(), "the file is left as it was")
    }

    @Test
    fun `a damaged key file is refused`() {
        key().get(mayCreate = true)
        file.writeBytes(file.readBytes().copyOf(30))
        assertFailsWith<DatabaseKeyException> { key().get(mayCreate = false) }
    }

    @Test
    fun `a discarded key is replaced by a new one`() {
        val first = key().get(mayCreate = true)
        key().discard()
        assertFalse(key().exists)
        val second = key().get(mayCreate = true)
        assertTrue(key().exists)
        assertFalse(first.contentEquals(second))
    }
}
