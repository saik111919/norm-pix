package com.example.normpix.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A locked backup: opened only by its password, giving back exactly what was locked, and refusing any file changed since. */
class LockedFileTest {
    private val password = "correct horse"

    // The fewest rounds a file may have keeps the test quick; real files use LockedFile.ITERATIONS.
    private fun lock(bytes: ByteArray, with: String = password) = LockedFile.locker(with.toCharArray(), iterations = 10_000).lock(bytes)

    private fun opening(file: ByteArray) = LockedFile.open(ByteArrayInputStream(file))

    private fun unlock(file: ByteArray, with: String = password): InputStream? =
        assertIs<LockedFile.Opening.Locked>(opening(file)).unlock(with.toCharArray())

    private fun content(size: Int) = Random(size).nextBytes(size)

    private val block = LockedFile.CHUNK + 16

    /** Where the blocks start: after the marker and the header line. */
    private fun bodyStart(file: ByteArray) = file.indexOfFirst(LockedFile.HEAD_BYTES) { it == '\n'.code.toByte() } + 1

    private fun ByteArray.indexOfFirst(from: Int, predicate: (Byte) -> Boolean): Int = (from until size).first { predicate(this[it]) }

    @Test
    fun `the right password gives back exactly what was locked, whatever its size`() {
        val c = LockedFile.CHUNK
        for (size in listOf(0, 1, 1000, c - 1, c, c + 1, 2 * c, 3 * c + 17)) {
            val bytes = content(size)
            assertContentEquals(bytes, assertNotNull(unlock(lock(bytes))).readBytes(), "size $size")
        }
    }

    @Test
    fun `locking a stream written a little at a time gives the same backup`() {
        val bytes = content(3 * LockedFile.CHUNK + 5)
        val out = ByteArrayOutputStream()
        LockedFile.locker(password.toCharArray(), iterations = 10_000).lock(out).use { stream ->
            var at = 0
            var step = 1
            while (at < bytes.size) {
                val n = minOf(step, bytes.size - at)
                stream.write(bytes, at, n)
                at += n
                step = step * 3 % 70_001 + 1
            }
        }
        assertContentEquals(bytes, assertNotNull(unlock(out.toByteArray())).readBytes())
    }

    @Test
    fun `the file says it is locked and shows nothing of what it holds`() {
        val secret = "normpix-backup.json Cafe Coffee Day 4111111111111111".toByteArray()
        val file = lock(secret + content(200))
        assertTrue(LockedFile.isLocked(file))
        assertTrue(file.decodeToString(0, LockedFile.HEAD_BYTES) == "normpix-locked\n")
        assertFalse(file.toList().windowed(secret.size).any { it == secret.toList() })
        // Nothing else is taken for a locked file: a zip, a backup's JSON, too short a start.
        assertFalse(LockedFile.isLocked(byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4) + ByteArray(20)))
        assertFalse(LockedFile.isLocked("{\"format\":\"normpix-finance-backup\"}".toByteArray()))
        assertFalse(LockedFile.isLocked("normpix-lock".toByteArray()))
    }

    @Test
    fun `the same backup locked twice with the same password is two different files`() {
        val bytes = content(500)
        assertFalse(lock(bytes).contentEquals(lock(bytes)))
    }

    @Test
    fun `a wrong password opens nothing, and the right one still can after it`() {
        val bytes = content(2 * LockedFile.CHUNK + 3)
        val locked = assertIs<LockedFile.Opening.Locked>(opening(lock(bytes)))
        assertNull(locked.unlock("wrong horse".toCharArray()))
        assertNull(locked.unlock("".toCharArray()))
        assertContentEquals(bytes, assertNotNull(locked.unlock(password.toCharArray())).readBytes())
    }

    @Test
    fun `a header changed to ask for fewer rounds does not get past the lock`() {
        val file = lock(content(100))
        val text = String(file, Charsets.ISO_8859_1)
        val tampered = text.replace("\"iterations\":10000", "\"iterations\":20000")
        assertTrue(tampered != text)
        assertNull(unlock(tampered.toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun `a file cut short, at the end of a block or inside one, is refused as damaged`() {
        val file = lock(content(3 * LockedFile.CHUNK + 100))
        val body = bodyStart(file)
        // Three whole blocks and the short last one: lose the last block, then cut into the third.
        for (end in listOf(body + 3 * block, body + 2 * block + 500)) {
            val stream = assertNotNull(unlock(file.copyOf(end)), "the first block is whole, so the password opens it")
            assertFailsWith<LockedFile.DamagedException> { stream.readBytes() }
        }
    }

    @Test
    fun `a block added, moved or changed is refused as damaged`() {
        val file = lock(content(3 * LockedFile.CHUNK + 100))
        val body = bodyStart(file)
        fun blockAt(i: Int) = file.copyOfRange(body + i * block, body + (i + 1) * block)
        val head = file.copyOf(body)
        val tail = file.copyOfRange(body + 3 * block, file.size)

        val added = file + blockAt(1)
        val moved = head + blockAt(0) + blockAt(2) + blockAt(1) + tail
        val changed = file.copyOf().also { it[body + 2 * block + 10] = (it[body + 2 * block + 10] + 1).toByte() }
        for ((what, tampered) in listOf("added" to added, "moved" to moved, "changed" to changed)) {
            val stream = assertNotNull(unlock(tampered), what)
            assertFailsWith<LockedFile.DamagedException>(what) { stream.readBytes() }
        }
    }

    @Test
    fun `files NormPix can't open say why`() {
        val file = lock(content(100))
        val text = String(file, Charsets.ISO_8859_1)
        fun reason(of: String) = assertIs<LockedFile.Opening.Invalid>(opening(of.toByteArray(Charsets.ISO_8859_1))).reason

        assertEquals(
            "This backup was locked by a newer NormPix. Update NormPix, then restore it.",
            reason(text.replace("\"version\":1", "\"version\":2")),
        )
        assertEquals("This backup is locked in a way NormPix can't open.", reason(text.replace("PBKDF2WithHmacSHA256", "Argon2id")))
        assertEquals(LockedFile.DAMAGED, reason("normpix-locked\nnot json\n"))
        assertEquals(LockedFile.DAMAGED, reason("normpix-locked\n" + "x".repeat(10_000)))
        assertEquals(LockedFile.DAMAGED, reason(text.substring(0, bodyStart(file) + 5)))
        assertEquals("This file isn't a locked NormPix backup.", reason("{\"format\":\"normpix-finance-backup\"}"))
    }

    @Test
    fun `a locker locks one file only`() {
        val locker = LockedFile.locker(password.toCharArray(), iterations = 10_000)
        locker.lock(content(10))
        assertFailsWith<IllegalStateException> { locker.lock(content(10)) }
    }
}
