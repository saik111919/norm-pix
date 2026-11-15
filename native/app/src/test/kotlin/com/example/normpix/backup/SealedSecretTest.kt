package com.example.normpix.backup

import com.example.normpix.wallet.VaultKeyProvider
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The automatic backup's password on the phone: kept only sealed by the Keystore's key, opened only by that key, gone when deleted. */
class SealedSecretTest {
    private val dir = Files.createTempDirectory("sealed-secret").toFile()
    private val file = File(dir, AutoBackupService.FILE_NAME)
    private fun keystoreKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val keystore = keystoreKey()
    private fun secret(sealedBy: SecretKey = keystore) = SealedSecret(file, VaultKeyProvider { sealedBy })
    private val password = "correct horse battery".toByteArray(Charsets.UTF_8)

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `a sealed secret opens to what was sealed, here and in a new instance`() {
        secret().seal(password)
        assertTrue(secret().exists)
        assertContentEquals(password, secret().open())
    }

    @Test
    fun `the file holds the secret sealed, never as it is, and no half-written file is left`() {
        secret().seal(password)
        val bytes = file.readBytes()
        assertEquals(12 + password.size + 16, bytes.size, "[IV][ciphertext][tag]")
        assertFalse(bytes.toList().windowed(6).any { it == password.take(6) }, "no part of the secret in the clear")
        assertFalse(File(dir, file.name + ".tmp").exists())
    }

    @Test
    fun `sealing again replaces the secret, each time under a new IV`() {
        secret().seal(password)
        val first = file.readBytes()
        secret().seal(password)
        assertFalse(first.contentEquals(file.readBytes()), "a new IV each time")
        val other = "another password".toByteArray()
        secret().seal(other)
        assertContentEquals(other, secret().open())
    }

    @Test
    fun `a different key can't open it`() {
        secret().seal(password)
        assertFailsWith<SealedSecretException> { secret(sealedBy = keystoreKey()).open() }
    }

    @Test
    fun `a truncated or changed file fails cleanly`() {
        secret().seal(password)
        val sealed = file.readBytes()
        file.writeBytes(sealed.copyOf(20))
        assertFailsWith<SealedSecretException> { secret().open() }
        file.writeBytes(sealed.copyOf(10))
        assertFailsWith<SealedSecretException> { secret().open() }
        file.writeBytes(sealed.also { it[15] = (it[15].toInt() xor 1).toByte() })
        assertFailsWith<SealedSecretException> { secret().open() }
        file.writeBytes(ByteArray(0))
        assertFalse(secret().exists)
        assertFailsWith<SealedSecretException> { secret().open() }
    }

    @Test
    fun `none kept can't be opened`() {
        assertFalse(secret().exists)
        assertFailsWith<SealedSecretException> { secret().open() }
    }

    @Test
    fun `delete leaves nothing, and deleting nothing is fine`() {
        secret().seal(password)
        File(dir, file.name + ".tmp").writeBytes(byteArrayOf(1))
        assertTrue(secret().delete())
        assertFalse(file.exists())
        assertFalse(File(dir, file.name + ".tmp").exists())
        assertFalse(secret().exists)
        assertTrue(secret().delete())
    }

    @Test
    fun `a key that can't be had keeps nothing`() {
        val broken = SealedSecret(file) { throw IllegalStateException("Keystore unavailable") }
        assertFailsWith<SealedSecretException> { broken.seal(password) }
        assertFalse(file.exists())
        assertFalse(File(dir, file.name + ".tmp").exists())
    }

    @Test
    fun `characters and UTF-8 bytes go back and forth, beyond ASCII too`() {
        val chars = "pässwörd ₹ 🔑".toCharArray()
        val bytes = utf8Bytes(chars)
        assertContentEquals("pässwörd ₹ 🔑".toByteArray(Charsets.UTF_8), bytes)
        assertContentEquals(chars, utf8Chars(bytes))
        assertContentEquals("pässwörd ₹ 🔑".toCharArray(), chars, "the characters handed in are left as they were")
    }
}
