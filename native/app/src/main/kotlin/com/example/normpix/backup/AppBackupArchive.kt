package com.example.normpix.backup

import com.example.normpix.finance.data.FinanceBackupArchive
import com.example.normpix.settings.CustomFont
import com.example.normpix.settings.isProfilePhotoName
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * An app backup: the full finance backup ([FinanceBackupArchive]) and, beside it in the same zip, what else the app keeps.
 *
 * ```
 * normpix-backup.json        the finance data, exactly as in a full backup
 * pictures/<name>            the pictures attached to transactions, exactly as in a full backup
 * app/manifest.json          that this is an app backup, its version, the app version and date, and the parts it holds
 * app/settings.json          the app's preferences, each with its type
 * app/reminders.json         every reminder
 * app/profile/<file>         the profile photo
 * app/fonts/<file>           the font file the user picked (always custom-interface-font.ttf, see CustomFont)
 * app/cards.json             the saved cards, locked with a password (a cards file, see CardsTransfer)
 * ```
 *
 * The first two are untouched, so a NormPix from before app backups restores the finance part of one and ignores the rest.
 *
 * Reading is as careful as for a full backup. An entry with a name that is absolute, has a `..` in it or otherwise isn't a plain
 * relative path makes the whole file unusable - nothing is read past it. Each entry has a size limit, and one over it makes the
 * file unusable too. Photos and fonts are taken only when they are named and shaped as the app makes them (a photo name
 * from `isProfilePhotoName` and a JPEG; the one font name [CustomFont.FILE_NAME] and a font's header), at most [MAX_FILES] of
 * each; a file that isn't is left out, and no file is ever written anywhere but a folder of its own inside the one it is read into.
 */
object AppBackupArchive {
    const val MANIFEST_ENTRY = "app/manifest.json"
    const val SETTINGS_ENTRY = "app/settings.json"
    const val REMINDERS_ENTRY = "app/reminders.json"
    const val PROFILE_PREFIX = "app/profile/"
    const val FONTS_PREFIX = "app/fonts/"
    const val CARDS_ENTRY = "app/cards.json"

    const val MAX_MANIFEST_BYTES = 64L * 1024
    const val MAX_SETTINGS_BYTES = 4L * 1024 * 1024
    const val MAX_REMINDERS_BYTES = 16L * 1024 * 1024
    const val MAX_CARDS_BYTES = 8L * 1024 * 1024

    /** Far above a kept profile photo (a 512 pixel square JPEG, a few tens of KB). */
    const val MAX_PHOTO_BYTES = 8L * 1024 * 1024

    /** As big a font as the app takes from the user in the first place ([CustomFont.MAX_BYTES]): a backup holds nothing it wouldn't. */
    const val MAX_FONT_BYTES = CustomFont.MAX_BYTES

    /** The most photos, and the most fonts, taken from one backup. */
    const val MAX_FILES = 8

    /** The message of the [IOException] reading throws for an entry with a name that isn't a plain relative path. */
    const val UNSAFE = "backup entry with an unsafe name"

    /** The message of the [IOException] reading throws for a backup made by a newer NormPix. */
    const val NEWER = "backup made by a newer version"

    /** What goes into the app's part of a backup. Files are read from where they are as they are written. */
    class Parts(
        val manifest: AppBackupManifest,
        val settings: String?,
        val reminders: String?,
        val profilePhotos: List<File>,
        val fonts: List<File>,
        /** A cards file ([com.example.normpix.wallet.CardsTransfer.encode]): already locked, never plain card numbers. */
        val cards: String?,
    )

    /** What the app's part of a backup held: the entries' text and the files, read into a folder of their own. */
    class App(
        val manifest: AppBackupManifest,
        val settings: String?,
        val reminders: String?,
        val cards: String?,
        val profilePhotos: Map<String, File>,
        val fonts: Map<String, File>,
    )

    /** What a zip held: the finance backup in it, and the app's part - null when it has none (a full backup from before app backups). */
    class Contents(val finance: FinanceBackupArchive.Contents, val app: App?)

    /** Whether [name] is the name the app keeps its font file under - the only font name a backup carries or a restore takes. */
    fun isFontName(name: String): Boolean = name == CustomFont.FILE_NAME

    /** Whether [name] is anything but a plain relative path: absolute, with a drive, a backslash, a NUL or a `..` in it. */
    fun isUnsafeName(name: String): Boolean =
        name.isEmpty() ||
            name.startsWith("/") ||
            name.contains('\\') ||
            name.contains('\u0000') ||
            (name.length > 1 && name[1] == ':') ||
            name.split('/').any { it == ".." }

    /** Writes the whole backup to [out]. Returns how many pictures went in (see [FinanceBackupArchive.write]). */
    fun write(out: OutputStream, financeJson: String, pictures: Collection<String>, app: Parts, pictureFile: (String) -> File?): Int =
        FinanceBackupArchive.write(out, financeJson, pictures, extras = { zip -> writeApp(zip, app) }, fileOf = pictureFile)

    private fun writeApp(zip: ZipOutputStream, app: Parts) {
        zip.setLevel(Deflater.DEFAULT_COMPRESSION)
        text(zip, MANIFEST_ENTRY, app.manifest.encode())
        app.settings?.let { text(zip, SETTINGS_ENTRY, it) }
        app.reminders?.let { text(zip, REMINDERS_ENTRY, it) }
        // A photo is a JPEG and the cards file is ciphertext: neither shrinks, so don't spend the time trying.
        zip.setLevel(Deflater.BEST_SPEED)
        for (photo in app.profilePhotos) if (isProfilePhotoName(photo.name)) file(zip, PROFILE_PREFIX + photo.name, photo)
        for (font in app.fonts) if (isFontName(font.name)) file(zip, FONTS_PREFIX + font.name, font)
        app.cards?.let { text(zip, CARDS_ENTRY, it) }
    }

    private fun text(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun file(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Reads a backup from [input]: the finance part as [FinanceBackupArchive.read] does (pictures into [into]), and the app's
     * part - its photos into `profile` and its fonts into `fonts` inside [into]. Throws an [IOException] with the message
     * [FinanceBackupArchive.TOO_LARGE] for an entry over its limit, [UNSAFE] for a bad entry name and [NEWER] for a backup
     * made by a newer NormPix. The caller cleans [into] up.
     */
    fun read(input: InputStream, into: File): Contents {
        var manifest: String? = null
        var settings: String? = null
        var reminders: String? = null
        var cards: String? = null
        val photos = LinkedHashMap<String, File>()
        val fonts = LinkedHashMap<String, File>()
        fun entry(name: String, data: InputStream) {
            if (isUnsafeName(name)) throw IOException(UNSAFE)
            when {
                name == MANIFEST_ENTRY -> manifest = textOf(data, MAX_MANIFEST_BYTES)
                name == SETTINGS_ENTRY -> settings = textOf(data, MAX_SETTINGS_BYTES)
                name == REMINDERS_ENTRY -> reminders = textOf(data, MAX_REMINDERS_BYTES)
                name == CARDS_ENTRY -> cards = textOf(data, MAX_CARDS_BYTES)
                name.startsWith(PROFILE_PREFIX) -> take(
                    data, name.removePrefix(PROFILE_PREFIX), File(into, "profile"), photos, MAX_PHOTO_BYTES,
                    named = { isProfilePhotoName(it) },
                    looksRight = { head, n -> looksLikeJpeg(head, n) },
                )
                name.startsWith(FONTS_PREFIX) -> take(
                    data, name.removePrefix(FONTS_PREFIX), File(into, "fonts"), fonts, MAX_FONT_BYTES,
                    named = { isFontName(it) },
                    looksRight = { head, n -> n >= HEADER_BYTES && CustomFont.hasFontHeader(head.copyOfRange(0, HEADER_BYTES)) },
                )
                else -> Unit
            }
        }

        val finance = FinanceBackupArchive.read(input, into) { name, data -> entry(name, data) }
        val manifestText = manifest ?: return Contents(finance, null)
        return when (val result = AppBackupManifest.decode(manifestText)) {
            is AppBackupManifest.Read.Valid -> Contents(finance, App(result.manifest, settings, reminders, cards, photos, fonts))
            AppBackupManifest.Read.Newer -> throw IOException(NEWER)
            AppBackupManifest.Read.Invalid -> Contents(finance, null)
        }
    }

    private fun textOf(data: InputStream, limit: Long): String =
        FinanceBackupArchive.readLimited(data, limit).toString(Charsets.UTF_8).removePrefix("\uFEFF")

    /** Keeps the entry's bytes as [name] in [folder] when it is a file of the kind wanted; otherwise leaves it out. */
    private fun take(
        data: InputStream,
        name: String,
        folder: File,
        into: MutableMap<String, File>,
        limit: Long,
        named: (String) -> Boolean,
        looksRight: (ByteArray, Int) -> Boolean,
    ) {
        if (!named(name) || name in into || into.size >= MAX_FILES) return
        folder.mkdirs()
        val target = File(folder, name)
        if (copyChecked(data, target, limit, looksRight)) into[name] = target else target.delete()
    }

    /**
     * Copies an entry to [target]. False (and the caller deletes what was written) when its first bytes aren't what [looksRight]
     * accepts; an [IOException] with the message [FinanceBackupArchive.TOO_LARGE] when it is longer than [limit].
     */
    private fun copyChecked(input: InputStream, target: File, limit: Long, looksRight: (ByteArray, Int) -> Boolean): Boolean {
        val buffer = ByteArray(64 * 1024)
        var filled = 0
        // The first few bytes tell what a file is; a read may hand over fewer than that at once.
        while (filled < HEADER_BYTES) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        if (!looksRight(buffer, filled)) return false
        var total = filled.toLong()
        if (total > limit) throw IOException(FinanceBackupArchive.TOO_LARGE)
        target.outputStream().use { out ->
            out.write(buffer, 0, filled)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw IOException(FinanceBackupArchive.TOO_LARGE)
                out.write(buffer, 0, read)
            }
        }
        return true
    }

    private const val HEADER_BYTES = 4

    private fun looksLikeJpeg(head: ByteArray, n: Int): Boolean =
        n >= 3 && head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
}
