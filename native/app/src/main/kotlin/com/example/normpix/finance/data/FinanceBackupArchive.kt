package com.example.normpix.finance.data

import com.example.normpix.finance.domain.isPictureName
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A full backup: the backup's own JSON ([FinanceBackupCodec]) and the pictures attached to transactions, in one zip file.
 * The JSON alone only names a transaction's picture; this carries the picture too, so a backup restored on another phone
 * (or after the app was reinstalled) still has them.
 *
 * Inside: `normpix-backup.json`, and `pictures/<name>` for every picture the backup's transactions name. Reading takes only
 * those two kinds of entry, only pictures named the way the app names its own ([isPictureName]) and only JPEGs, each of a
 * size a picture can be - anything else in the zip is ignored, and no entry can write outside the folder it is read into.
 */
object FinanceBackupArchive {
    const val BACKUP_ENTRY = "normpix-backup.json"
    const val PICTURES_PREFIX = "pictures/"

    /** No backup's JSON is this big; it is the same limit as for a backup file on its own. */
    const val MAX_JSON_BYTES = 32L * 1024 * 1024

    /** Far above any kept picture (they are scaled down to a few hundred KB, see [TransactionPictures.MAX_SIDE]). */
    const val MAX_PICTURE_BYTES = 20L * 1024 * 1024

    /** The message of the [IOException] reading throws for a backup's JSON bigger than any backup's could be. */
    const val TOO_LARGE = "backup entry too large"

    /** Whether a file starting with [head] (its first four bytes) is a zip, and so a full backup rather than a JSON one. */
    fun isArchive(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() && head[2] == 3.toByte() && head[3] == 4.toByte()

    /**
     * Writes a full backup to [out]: [json], then each of [pictures] that [fileOf] finds on this phone. Returns how many
     * pictures went in (one that is missing is simply left out; the backup still names it).
     */
    fun write(out: OutputStream, json: String, pictures: Collection<String>, fileOf: (String) -> File?): Int {
        val zip = ZipOutputStream(out)
        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        zip.putNextEntry(ZipEntry(BACKUP_ENTRY))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        // A JPEG is already compressed: storing it as fast as possible saves the time, not the space.
        zip.setLevel(Deflater.BEST_SPEED)
        var written = 0
        for (name in pictures.distinct()) {
            val file = fileOf(name) ?: continue
            zip.putNextEntry(ZipEntry(PICTURES_PREFIX + name))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            written++
        }
        zip.finish()
        zip.flush()
        return written
    }

    /** What a full backup held: its JSON (null when it had none), and the pictures it carried, read into files. */
    class Contents(val json: String?, val pictures: Map<String, File>)

    /** Reads a full backup from [input], its pictures into [into] (a folder of their own, which the caller cleans up). */
    fun read(input: InputStream, into: File): Contents {
        into.mkdirs()
        var json: String? = null
        val pictures = LinkedHashMap<String, File>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                when {
                    entry.name == BACKUP_ENTRY -> json = readLimited(zip, MAX_JSON_BYTES).toString(Charsets.UTF_8).removePrefix("\uFEFF")
                    entry.name.startsWith(PICTURES_PREFIX) -> {
                        val name = entry.name.removePrefix(PICTURES_PREFIX)
                        // Only a picture name of the app's own: never a path, so it can only land in [into].
                        if (!isPictureName(name) || name in pictures) continue
                        val target = File(into, name)
                        if (copyPicture(zip, target)) pictures[name] = target else target.delete()
                    }
                }
            }
        }
        return Contents(json, pictures)
    }

    private fun readLimited(input: InputStream, limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException(TOO_LARGE)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    /** Copies one picture entry to [target]; false (and nothing kept) when it isn't a JPEG or is too big to be a picture. */
    private fun copyPicture(input: InputStream, target: File): Boolean {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var first = true
        target.outputStream().use { out ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (first) {
                    if (read < 3 || buffer[0] != 0xFF.toByte() || buffer[1] != 0xD8.toByte() || buffer[2] != 0xFF.toByte()) return false
                    first = false
                }
                total += read
                if (total > MAX_PICTURE_BYTES) return false
                out.write(buffer, 0, read)
            }
        }
        return !first
    }
}
