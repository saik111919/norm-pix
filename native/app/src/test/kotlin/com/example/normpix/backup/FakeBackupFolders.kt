package com.example.normpix.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** A folder in memory: what is in it, what was made, written and deleted - and failures to order. */
class FakeBackupFolder : BackupFolder {
    class Stored(val id: String, val name: String) {
        val bytes = ByteArrayOutputStream()
    }

    /** The files in the folder, by id, in the order they came. */
    val files = LinkedHashMap<String, Stored>()

    /** The names of the files deleted, in order. */
    val deleted = mutableListOf<String>()

    /** Listing the folder throws this (a folder gone). */
    var listError: Exception? = null

    /** Making a file throws this. */
    var createError: Exception? = null

    /** Writing throws [writeError] once this many bytes have gone in (a disk filling up mid-way). */
    var failAfterBytes: Int? = null
    var writeError: Exception = IOException("write failed: ENOSPC (No space left on device)")

    private var next = 1

    /** A file that was there already. */
    fun add(name: String, content: ByteArray = byteArrayOf(1, 2, 3)): Stored =
        Stored("doc-${next++}", name).also {
            it.bytes.write(content)
            files[it.id] = it
        }

    fun named(name: String): Stored? = files.values.firstOrNull { it.name == name }

    val names: List<String> get() = files.values.map { it.name }

    override fun list(): List<BackupFolder.Entry> {
        listError?.let { throw it }
        return files.values.map { BackupFolder.Entry(it.id, it.name) }
    }

    override fun create(name: String, mimeType: String): BackupFolder.Entry {
        createError?.let { throw it }
        val stored = Stored("doc-${next++}", name)
        files[stored.id] = stored
        return BackupFolder.Entry(stored.id, stored.name)
    }

    override fun write(entry: BackupFolder.Entry): OutputStream {
        val stored = files.getValue(entry.id)
        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(b: ByteArray, off: Int, len: Int) {
                val limit = failAfterBytes
                if (limit != null && stored.bytes.size() + len > limit) throw writeError
                stored.bytes.write(b, off, len)
            }
        }
    }

    override fun delete(entry: BackupFolder.Entry): Boolean {
        val gone = files.remove(entry.id) ?: return false
        deleted += gone.name
        return true
    }
}

/** The folders a user could pick: one, in memory; remembers whose access was kept and given back. */
class FakeBackupFolders(val folder: FakeBackupFolder = FakeBackupFolder()) : BackupFolders {
    val kept = mutableListOf<String>()
    val released = mutableListOf<String>()

    /** Keeping the folder's access is refused, as for a folder the provider won't share for good. */
    var refuseKeep = false

    /** The folder is gone (or NormPix's access to it was taken back in Android's settings). */
    var gone = false

    override fun keep(tree: String) {
        if (refuseKeep) throw SecurityException("No persistable permission grants found")
        if (tree !in kept) kept += tree
    }

    override fun release(tree: String) {
        released += tree
        kept -= tree
    }

    override fun nameOf(tree: String): String = "Backups"

    override fun open(tree: String): BackupFolder? = if (gone || tree !in kept) null else folder
}
