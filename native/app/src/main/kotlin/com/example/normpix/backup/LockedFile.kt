package com.example.normpix.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * A backup locked with a password: a backup file (the app backup or the full backup .zip, the backup .json) sealed whole, so that
 * nothing in it - not even which kind of backup it is - can be read without the password. Unlocked, it is that file byte for byte,
 * read by the same readers as ever; a backup that isn't locked is read exactly as it always was.
 *
 * The file: the line `normpix-locked`, then a line of JSON saying how it is locked - `{"version": 1, "kdf": ..., "iterations": n,
 * "salt": b64, "cipher": ..., "chunk": 65536}` - then the backup in blocks of `chunk` bytes, each sealed on its own with AES-256-GCM
 * under a key drawn from the password by PBKDF2-HMAC-SHA256. A block's nonce is its number (11 bytes) and whether it is the last
 * (1 byte), and both lines are bound into every block: a block moved, dropped, added at the end or cut short, or a header asking for
 * fewer rounds, stops the file opening rather than opening something else (the STREAM construction the age tool uses, with AES-GCM).
 * Numbered nonces are safe because the key is new for every file: each has a salt of its own, and a [Locker] locks one file only.
 *
 * Blocks rather than one sealed whole, because GCM gives nothing back until its tag is checked: a backup with its pictures could be
 * far bigger than the memory a phone allows the app.
 */
object LockedFile {
    const val VERSION = 1

    /** The shortest password a backup may be locked with: the same as a cards file's. */
    const val MIN_PASSWORD = 8

    /** PBKDF2 rounds for a new file, as a cards file has: slow enough to make guessing expensive, well under a second on a phone. */
    const val ITERATIONS = 310_000

    /** How much of the backup each sealed block holds. */
    const val CHUNK = 64 * 1024

    /** How a locked file is saved: as nothing in particular, so no app takes it for a zip it can't open. */
    const val MIME_TYPE = "application/octet-stream"

    /** What is said when a locked file has been changed or cut short since it was written. */
    const val DAMAGED = "This locked backup is damaged, so it can't be used."

    /** How many bytes from its start [isLocked] needs to see of a file. */
    const val HEAD_BYTES = 15

    private val MAGIC = "normpix-locked\n".toByteArray(Charsets.US_ASCII).also { check(it.size == HEAD_BYTES) }

    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val SALT_BYTES = 16
    private const val TAG_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val KEY_BITS = 256

    /** The longest header line read: far longer than any this version writes. */
    private const val MAX_HEADER = 4 * 1024

    /** What a file may ask for: it is read with the rounds and block size it says, within these. */
    private val ITERATIONS_ALLOWED = 10_000..5_000_000
    private val CHUNKS_ALLOWED = 1024..1024 * 1024

    /** True when a file starting with [head] (at least its first [HEAD_BYTES] bytes) is a locked one. */
    fun isLocked(head: ByteArray): Boolean = head.size >= HEAD_BYTES && MAGIC.indices.all { head[it] == MAGIC[it] }

    /** The name a locked [fileName] is saved under: what it holds, and that it is locked. */
    fun fileName(fileName: String) = "$fileName.locked"

    /**
     * A key drawn from [password], for locking one file: slow (it is meant to be), so it is made before the file is asked for. A
     * [Locker] locks a single file, as numbered nonces may never be used twice under the same key.
     */
    fun locker(password: CharArray, random: SecureRandom = SecureRandom(), iterations: Int = ITERATIONS): Locker {
        require(iterations in ITERATIONS_ALLOWED) { "rounds out of range" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = buildJsonObject {
            put("version", VERSION)
            put("kdf", KDF)
            put("iterations", iterations)
            put("salt", Base64.getEncoder().encodeToString(salt))
            put("cipher", CIPHER)
            put("chunk", CHUNK)
        }
        val line = (Json.encodeToString(JsonElement.serializer(), header) + "\n").toByteArray(Charsets.UTF_8)
        return Locker(key(password, salt, iterations), MAGIC + line)
    }

    /** Locks one file: [lock] a stream as it is written, or a file already in memory. */
    class Locker internal constructor(private val key: SecretKeySpec, private val header: ByteArray) {
        private var used = false

        /**
         * A stream that writes what is written to it into [out], locked. Closing it seals the last block and flushes [out], which
         * stays open: it is the caller's to close. Unclosed, the file is unfinished and won't open.
         */
        fun lock(out: OutputStream): OutputStream {
            check(!used) { "a locker locks one file only" }
            used = true
            out.write(header)
            return Sealing(out, key, header)
        }

        /** [bytes], locked. */
        fun lock(bytes: ByteArray): ByteArray {
            val out = ByteArrayOutputStream(header.size + bytes.size + (bytes.size / CHUNK + 1) * TAG_BYTES)
            lock(out).use { it.write(bytes) }
            return out.toByteArray()
        }
    }

    /** What reading the start of a locked file came to. */
    sealed interface Opening {
        /** A locked file, waiting for its password. */
        class Locked internal constructor(
            private val source: PushbackInputStream,
            private val salt: ByteArray,
            private val iterations: Int,
            private val chunk: Int,
            private val header: ByteArray,
            private val first: ByteArray,
            private val firstIsLast: Boolean,
        ) : Opening {
            private var opened = false

            /**
             * The file unlocked with [password], as a stream of the backup it holds; null when the password doesn't open it (or the
             * start of the file was changed: the two can't be told apart), and it may be tried again with another. Reading the
             * stream throws [DamagedException] where a later part of the file was changed or cut off. Slow, as drawing the key is.
             */
            fun unlock(password: CharArray): InputStream? {
                check(!opened) { "already unlocked" }
                val key = key(password, salt, iterations)
                val plain = try {
                    open(key, header, first, first.size, 0, firstIsLast)
                } catch (_: AEADBadTagException) {
                    return null
                }
                opened = true
                return Unsealing(source, key, header, chunk, plain, firstIsLast)
            }
        }

        /** Not a locked file NormPix can open; [reason] says why, for the user. */
        data class Invalid(val reason: String) : Opening
    }

    /**
     * Reads how the file [input] holds is locked, and its first block, which is what tells a right password from a wrong one. The
     * stream must be at the very start of the file; it is read on from where this leaves it.
     */
    fun open(input: InputStream): Opening {
        val source = PushbackInputStream(input, 1)
        val damaged = Opening.Invalid(DAMAGED)
        val head = ByteArray(HEAD_BYTES)
        if (readUpTo(source, head) < HEAD_BYTES || !isLocked(head)) return Opening.Invalid("This file isn't a locked NormPix backup.")
        val line = readLine(source) ?: return damaged
        val fields = runCatching { Json.parseToJsonElement(line.toString(Charsets.UTF_8)) as? JsonObject }.getOrNull() ?: return damaged
        fun text(name: String) = (fields[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun number(name: String) = (fields[name] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        val version = number("version") ?: return damaged
        if (version > VERSION) return Opening.Invalid("This backup was locked by a newer NormPix. Update NormPix, then restore it.")
        if (version < 1) return damaged
        if (text("kdf") != KDF || text("cipher") != CIPHER) return Opening.Invalid("This backup is locked in a way NormPix can't open.")
        val iterations = number("iterations")?.takeIf { it in ITERATIONS_ALLOWED } ?: return damaged
        val chunk = number("chunk")?.takeIf { it in CHUNKS_ALLOWED } ?: return damaged
        val salt = text("salt")?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }?.takeIf { it.size >= 8 } ?: return damaged
        val sealed = ByteArray(chunk + TAG_BYTES)
        val got = readUpTo(source, sealed)
        if (got < TAG_BYTES) return damaged
        val last = got < sealed.size || atEnd(source)
        return Opening.Locked(source, salt, iterations, chunk, MAGIC + line, sealed.copyOf(got), last)
    }

    /** A locked file was changed or cut short after its first block; [message] is [DAMAGED]. */
    class DamagedException : IOException(DAMAGED)

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Block [index]'s nonce: its number, big-endian in the first 11 bytes, then 1 for the last block and 0 for any other. */
    private fun nonce(index: Long, last: Boolean): ByteArray {
        val nonce = ByteArray(NONCE_BYTES)
        for (i in 0 until 8) nonce[NONCE_BYTES - 2 - i] = (index ushr (8 * i)).toByte()
        nonce[NONCE_BYTES - 1] = if (last) 1 else 0
        return nonce
    }

    private fun cipher(mode: Int, key: SecretKeySpec, header: ByteArray, index: Long, last: Boolean): Cipher =
        Cipher.getInstance(CIPHER).apply {
            init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce(index, last)))
            updateAAD(header)
        }

    /** Block [index] opened: throws [AEADBadTagException] if it isn't what was sealed there. */
    private fun open(key: SecretKeySpec, header: ByteArray, sealed: ByteArray, size: Int, index: Long, last: Boolean): ByteArray =
        cipher(Cipher.DECRYPT_MODE, key, header, index, last).doFinal(sealed, 0, size)

    /** Reads into [buffer] until it is full or the file ends; how many bytes were read. */
    private fun readUpTo(input: InputStream, buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        return filled
    }

    /** The next line with its newline, or null if none ends within [MAX_HEADER] bytes. */
    private fun readLine(input: InputStream): ByteArray? {
        val line = ByteArrayOutputStream()
        while (line.size() < MAX_HEADER) {
            val b = input.read()
            if (b < 0) return null
            line.write(b)
            if (b == '\n'.code) return line.toByteArray()
        }
        return null
    }

    private fun atEnd(input: PushbackInputStream): Boolean {
        val b = input.read()
        if (b < 0) return true
        input.unread(b)
        return false
    }

    /**
     * Seals what is written in blocks of [CHUNK] bytes. A full block is only sealed once more comes after it, so the one sealed as
     * last on close always holds the end of the file (and is empty only for an empty file).
     */
    private class Sealing(private val out: OutputStream, private val key: SecretKeySpec, private val header: ByteArray) : OutputStream() {
        private val block = ByteArray(CHUNK)
        private var filled = 0
        private var index = 0L
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (closed) throw IOException("The locked file is already finished.")
            var at = off
            var left = len
            while (left > 0) {
                if (filled == block.size) seal(last = false)
                val n = minOf(left, block.size - filled)
                System.arraycopy(b, at, block, filled, n)
                filled += n
                at += n
                left -= n
            }
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            seal(last = true)
            closed = true
            block.fill(0)
            out.flush()
        }

        private fun seal(last: Boolean) {
            out.write(cipher(Cipher.ENCRYPT_MODE, key, header, index, last).doFinal(block, 0, filled))
            index++
            filled = 0
        }
    }

    /** The backup in a locked file, opened block by block as it is read; [first] is block 0, already opened. */
    private class Unsealing(
        private val source: PushbackInputStream,
        private val key: SecretKeySpec,
        private val header: ByteArray,
        chunk: Int,
        first: ByteArray,
        private var done: Boolean,
    ) : InputStream() {
        private val sealed = ByteArray(chunk + TAG_BYTES)
        private var plain = first
        private var at = 0
        private var index = 1L

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (at == plain.size) {
                if (done) return -1
                next()
            }
            val n = minOf(len, plain.size - at)
            System.arraycopy(plain, at, b, off, n)
            at += n
            return n
        }

        override fun available(): Int = plain.size - at

        override fun close() = source.close()

        private fun next() {
            val got = readUpTo(source, sealed)
            // Nothing more where a block was due: the file was cut off after a block that wasn't sealed as the last.
            if (got < TAG_BYTES) throw DamagedException()
            val last = got < sealed.size || atEnd(source)
            plain = try {
                open(key, header, sealed, got, index, last)
            } catch (_: AEADBadTagException) {
                throw DamagedException()
            }
            at = 0
            index++
            done = last
        }
    }
}
