package com.example.normpix.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What came of taking a font file ([CustomFont.install]). */
enum class FontInstall {
    /** The file is a font NormPix can use, and is now the saved one. */
    Saved,

    /** The file is bigger than [CustomFont.MAX_BYTES]. */
    TooLarge,

    /** The file is not a font file, or one Android cannot load. */
    NotAFont,

    /** The file could not be read, or the copy could not be written. */
    Failed,

    /** The file is a zip with no font in it that NormPix can use (no .ttf or .otf, or none that Android can load). */
    NoFonts,
}

/** [install]'s outcome for the file the user picked, and what to call it ([name], as the file manager showed it). */
class FontImport(val install: FontInstall, val name: String)

/** One weight of the user's font: the file the text system draws with, and the weight (100 to 900) it stands for. */
data class CustomFontFace(val file: File, val weight: Int)

/**
 * The font file the user picked for the interface (Settings > Appearance > Interface font > Your font), kept in the app's own
 * storage at [file] so it stays when the original moves, and backed up with the rest of the app's data. The name it had
 * is a preference (see [AppPreferences.useCustomFont]); the choice of this font is [InterfaceFont.Custom].
 *
 * A file is only ever saved after it has been checked: not too big, a font file by its first bytes, and one Android's
 * own [Typeface] can load. What the app draws with is [loadable], a copy of it.
 */
object CustomFont {
    /** The biggest font file taken: more than any font for a phone needs, and a cap on what a stray pick can fill the storage with. */
    const val MAX_BYTES = 10L * 1024 * 1024

    /** The folder in the app's files that holds the font (and, in the cache, the copies drawn with). */
    const val FOLDER = "fonts"

    /** The saved font's file name; whatever type the file was, it is kept as this. */
    const val FILE_NAME = "custom-interface-font.ttf"

    /**
     * A family that comes as several static files (a download's Regular, Medium, Bold ...) keeps the one that draws regular text as
     * [FILE_NAME] and each other weight beside it as `custom-interface-font-w<weight>.ttf` (see [extraName]). A variable font is
     * one file, and so is a font picked as a single file: neither has extras.
     */
    private const val EXTRA_PREFIX = "custom-interface-font-w"
    private val EXTRA_NAME = Regex("custom-interface-font-w([1-9]00)\\.ttf")

    /** The most one zip may hold in all, and the most entries looked at: a stray pick cannot fill the storage. */
    const val MAX_ZIP_BYTES = 40L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 200

    private const val HEADER_BYTES = 4
    private val TRUETYPE = byteArrayOf(0x00, 0x01, 0x00, 0x00)
    private val OPENTYPE = "OTTO".toByteArray(Charsets.US_ASCII)
    private val APPLE_TRUETYPE = "true".toByteArray(Charsets.US_ASCII)
    private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /** Where the saved font is (it may not exist). */
    fun file(context: Context): File = File(File(context.filesDir, FOLDER), FILE_NAME)

    /** Whether a font has been saved. */
    fun exists(context: Context): Boolean = file(context).isFile

    /** The name the file of [weight] (a multiple of 100, 100 to 900) is kept under beside the main one. */
    fun extraName(weight: Int): String = "$EXTRA_PREFIX$weight.ttf"

    /** Whether [name] is a name the app keeps a font file under: the main one or an extra weight. The only names a backup carries. */
    fun isSavedName(name: String): Boolean = name == FILE_NAME || EXTRA_NAME.matches(name)

    /** The saved extra weights, lightest first (none for a variable font or one picked as a single file). */
    fun extras(context: Context): List<File> =
        file(context).parentFile?.listFiles()?.filter { it.isFile && EXTRA_NAME.matches(it.name) }?.sortedBy { it.name }.orEmpty()

    /** Every saved font file: the main one, then the extra weights. */
    fun savedFiles(context: Context): List<File> = listOfNotNull(file(context).takeIf { it.isFile }) + extras(context)

    /** Whether [head], the first bytes of a file, begin a zip. */
    fun hasZipHeader(head: ByteArray): Boolean = head.size >= ZIP.size && head.copyOfRange(0, ZIP.size).contentEquals(ZIP)

    /**
     * What the saved font gives, in words for the Interface font page: "All weights" for a variable font, "5 weights" for a
     * family kept as several files, nothing for a single static file (or one that can't be read).
     */
    fun describe(context: Context): String? {
        val info = FontFileInfo.read(file(context)) ?: return null
        val extra = extras(context).size
        return when {
            info.isVariable -> "All weights"
            extra > 0 -> "${extra + 1} weights"
            else -> null
        }
    }

    /** Whether [head], the first bytes of a file, begin a TrueType (`00 01 00 00` or `true`) or OpenType (`OTTO`) font. */
    fun hasFontHeader(head: ByteArray): Boolean {
        if (head.size < HEADER_BYTES) return false
        val tag = head.copyOfRange(0, HEADER_BYTES)
        return tag.contentEquals(TRUETYPE) || tag.contentEquals(OPENTYPE) || tag.contentEquals(APPLE_TRUETYPE)
    }

    /** Whether the file at [file] begins like a font (see [hasFontHeader]). */
    fun hasFontHeader(file: File): Boolean = try {
        file.inputStream().use { input ->
            val head = ByteArray(HEADER_BYTES)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
            read == head.size && hasFontHeader(head)
        }
    } catch (_: IOException) {
        false
    }

    /** Whether Android can build a typeface from [file]. This is the real test of "is it a font"; call it off the main thread. */
    fun loads(file: File): Boolean = runCatching { Typeface.Builder(file).build() != null }.getOrDefault(false)

    /**
     * Checks the font in [source] and, if it is a usable one, saves it as [target]. It is first written next to [target] and
     * moved into place only once it has passed, so a file that is refused (or a copy that fails half way) never touches
     * the font already in use. Reads at most [MAX_BYTES]. Blocks: call it off the main thread. [canLoad] is the check that
     * Android can load the file; tests give their own.
     */
    fun install(source: InputStream, target: File, canLoad: (File) -> Boolean = { loads(it) }): FontInstall {
        val folder = target.parentFile ?: return FontInstall.Failed
        val staged = File(folder, target.name + ".part")
        try {
            folder.mkdirs()
            if (!folder.isDirectory) return FontInstall.Failed
            val fits = staged.outputStream().buffered().use { out -> copyAtMost(source, out, MAX_BYTES) }
            if (!fits) return FontInstall.TooLarge
            if (!hasFontHeader(staged) || !canLoad(staged)) return FontInstall.NotAFont
            // A rename replaces the old file in one step; where that fails the copy is the fallback.
            if (!staged.renameTo(target)) staged.copyTo(target, overwrite = true)
            // A single file stands alone: the weights of a family picked before are not its.
            clearExtras(folder)
            return FontInstall.Saved
        } catch (_: IOException) {
            return FontInstall.Failed
        } finally {
            staged.delete()
        }
    }

    private fun clearExtras(folder: File) {
        folder.listFiles()?.forEach { if (EXTRA_NAME.matches(it.name)) it.delete() }
    }

    /** A font file read out of a zip, waiting in the staging folder to be chosen or thrown away. */
    private class Staged(val file: File, val info: FontFileInfo)

    /**
     * Takes the fonts out of the zip in [source] - a download from Google Fonts, say - and saves the family's: one variable file
     * that holds every weight, else a file for each weight ([FontSets.choose]), the regular as [FILE_NAME] in [folder] and the
     * others beside it as extras. Only the .ttf and .otf entries are looked at, and none of the zip's own paths is ever used as a
     * path: what is kept is written under this object's names. Like [install], nothing is touched until what is kept has passed
     * the checks, so a zip that is refused never replaces the font in use. Blocks: call it off the main thread.
     */
    fun installZip(source: InputStream, folder: File, canLoad: (File) -> Boolean = { loads(it) }): FontInstall {
        val staging = File(folder, "incoming")
        try {
            staging.deleteRecursively()
            staging.mkdirs()
            if (!staging.isDirectory) return FontInstall.Failed
            val staged = ArrayList<Staged>()
            var oversize = false
            var total = 0L
            ZipInputStream(source).use { zip ->
                var entries = 0
                while (entries++ < MAX_ZIP_ENTRIES) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !isFontEntry(entry.name)) continue
                    val target = File(staging, "font-${staged.size + 1}")
                    val fits = target.outputStream().buffered().use { out -> copyAtMost(zip, out, MAX_BYTES) }
                    if (!fits) {
                        oversize = true
                        target.delete()
                        continue
                    }
                    total += target.length()
                    if (total > MAX_ZIP_BYTES) return FontInstall.TooLarge
                    if (!hasFontHeader(target)) {
                        target.delete()
                        continue
                    }
                    staged += Staged(target, FontFileInfo.read(target) ?: FontFileInfo(400, false, 5, null))
                }
            }
            val kept = FontSets.choose(staged) { it.info }.filter { canLoad(it.file) }
            if (kept.isEmpty()) return if (staged.isEmpty()) (if (oversize) FontInstall.TooLarge else FontInstall.NoFonts) else FontInstall.NotAFont

            val main = kept.first()
            // The extras are named by their weight, so two files of one weight cannot both be kept (choose gives one each).
            val others = kept.drop(1).associateBy { FontSets.nearestWeight(it.info.weight) }
            folder.mkdirs()
            val target = File(folder, FILE_NAME)
            if (!main.file.renameTo(target)) main.file.copyTo(target, overwrite = true)
            clearExtras(folder)
            for ((weight, face) in others) {
                val extra = File(folder, extraName(weight))
                if (!face.file.renameTo(extra)) face.file.copyTo(extra, overwrite = true)
            }
            return FontInstall.Saved
        } catch (_: IOException) {
            // Including a zip that is damaged: it could not be read, which is not the same as having no font in it.
            return FontInstall.Failed
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Whether the zip entry called [name] is a font file worth reading: .ttf or .otf, not a folder's resource fork or hidden file. */
    private fun isFontEntry(name: String): Boolean {
        val base = name.substringAfterLast('/')
        if (base.startsWith(".") || name.split('/').any { it == "__MACOSX" }) return false
        val lower = base.lowercase()
        return lower.endsWith(".ttf") || lower.endsWith(".otf")
    }

    /** Copies [input] to [out], unless it runs past [max] bytes: then false, and what was written is not a whole file. */
    private fun copyAtMost(input: InputStream, out: OutputStream, max: Long): Boolean {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) return true
            total += read
            if (total > max) return false
            out.write(buffer, 0, read)
        }
    }

    /**
     * Takes the font file at [uri] (from the system file picker): its size is looked at first, then it is copied and checked
     * as [install] does, on the IO thread. Saves nothing unless the file is good.
     */
    suspend fun import(context: Context, uri: Uri, canLoad: (File) -> Boolean = { loads(it) }): FontImport =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val name = displayName(context, uri)
            val declared = query(context, uri, OpenableColumns.SIZE)?.toLongOrNull()
            val stream = runCatching { resolver.openInputStream(uri) }.getOrNull()
                ?: return@withContext FontImport(FontInstall.Failed, name)
            val result = try {
                stream.use { raw ->
                    // A zip - Google Fonts hands out a family as one - is told by its first bytes, not by what it is called.
                    val input = raw.buffered()
                    input.mark(ZIP.size)
                    val head = ByteArray(ZIP.size)
                    val read = input.read(head)
                    input.reset()
                    val folder = file(context).parentFile
                    if (read == ZIP.size && hasZipHeader(head) && folder != null) {
                        if (declared != null && declared > MAX_ZIP_BYTES) FontInstall.TooLarge else installZip(input, folder, canLoad)
                    } else if (declared != null && declared > MAX_BYTES) {
                        FontInstall.TooLarge
                    } else {
                        install(input, file(context), canLoad)
                    }
                }
            } catch (_: IOException) {
                FontInstall.Failed
            }
            FontImport(result, name)
        }

    /** What the file at [uri] is called, as the file manager shows it; "Your font" when it will not say. */
    fun displayName(context: Context, uri: Uri): String =
        cleanName(query(context, uri, OpenableColumns.DISPLAY_NAME) ?: uri.lastPathSegment)

    /** [raw] as a name to show: its last path part, trimmed, cut to [MAX_CUSTOM_FONT_NAME]; "Your font" if nothing is left. */
    fun cleanName(raw: String?): String =
        raw.orEmpty().substringAfterLast('/').trim().ifBlank { "Your font" }.take(MAX_CUSTOM_FONT_NAME)

    private fun query(context: Context, uri: Uri, column: String): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
    }.getOrNull()

    /**
     * The font file as the app draws with it, or null when there is none it can use - the app then sets its text in GS Flex,
     * without a word, rather than fail.
     *
     * It is a copy in the cache, named after the version of the saved file it copies, not the saved file itself: the text system
     * keeps a loaded font by its path, so a font replaced at the same path would go on showing the old one until the app was
     * restarted. A new version is a new path. The copy is made the first time it is wanted, after the file has passed the
     * checks it passed on the way in; a file that is gone, or no longer passes, gives null. Older copies are cleared away.
     */
    fun loadable(context: Context, canLoad: (File) -> Boolean = { loads(it) }): File? {
        val source = file(context)
        val size = source.length()
        if (!source.isFile || size <= 0L || size > MAX_BYTES) return null
        val folder = File(context.cacheDir, FOLDER)
        val copy = stampedCopy(folder, source, size)
        if (copy.isFile && copy.length() == size) return copy
        if (!hasFontHeader(source) || !canLoad(source)) return null
        val staged = File(folder, copy.name + ".part")
        return try {
            folder.mkdirs()
            // Older copies go; the copies of this version's extra weights ([faces]) stay.
            val keep = stampedCopy(folder, source, size).name.removeSuffix(".ttf") + "-w"
            folder.listFiles()?.forEach { if (!it.name.startsWith(keep)) it.delete() }
            source.copyTo(staged, overwrite = true)
            if (staged.renameTo(copy)) copy else null
        } catch (_: IOException) {
            staged.delete()
            null
        }
    }

    /**
     * The copy [loadable] made of the saved font, if it is already there: two file lookups and nothing else, so the screen can
     * ask for it on the first frame and draw in the user's font at once. Null when there is no copy yet (then [loadable]
     * makes it, off the main thread).
     */
    fun ready(context: Context): File? {
        val source = file(context)
        val size = source.length()
        if (!source.isFile || size <= 0L || size > MAX_BYTES) return null
        val copy = stampedCopy(File(context.cacheDir, FOLDER), source, size)
        return copy.takeIf { it.isFile && it.length() == size }
    }

    /**
     * The extra weights of the saved font as the app draws with them: copies in the cache named like the main one's (by its size and
     * its last change, then the weight), for the same reason - a different set is a different path. Made now if they are not
     * there; call it after [loadable]. Empty for a font with no extras.
     */
    fun faces(context: Context): List<CustomFontFace> {
        val source = file(context)
        if (!source.isFile) return emptyList()
        val folder = File(context.cacheDir, FOLDER)
        val base = stampedCopy(folder, source, source.length()).name.removeSuffix(".ttf")
        return extras(context).mapNotNull { extra ->
            val weight = EXTRA_NAME.matchEntire(extra.name)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val copy = File(folder, "$base-w$weight.ttf")
            if (!(copy.isFile && copy.length() == extra.length())) {
                try {
                    folder.mkdirs()
                    extra.copyTo(copy, overwrite = true)
                } catch (_: IOException) {
                    return@mapNotNull null
                }
            }
            CustomFontFace(copy, weight)
        }
    }

    /** [faces] as they already are, or null when any is not made yet (then [faces] makes them, off the main thread). */
    fun readyFaces(context: Context): List<CustomFontFace>? {
        val source = file(context)
        if (!source.isFile) return emptyList()
        val folder = File(context.cacheDir, FOLDER)
        val base = stampedCopy(folder, source, source.length()).name.removeSuffix(".ttf")
        val made = extras(context).mapNotNull { extra ->
            val weight = EXTRA_NAME.matchEntire(extra.name)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val copy = File(folder, "$base-w$weight.ttf")
            if (copy.isFile && copy.length() == extra.length()) CustomFontFace(copy, weight) else null
        }
        return made.takeIf { it.size == extras(context).size }
    }

    /** The cache copy of [source] as it is now: named by its size and its last change, so a different file is a different path. */
    private fun stampedCopy(folder: File, source: File, size: Long): File =
        File(folder, "interface-$size-${source.lastModified()}.ttf")

    /** Removes the saved font and the copies drawn with. */
    fun delete(context: Context) {
        file(context).delete()
        File(context.cacheDir, FOLDER).deleteRecursively()
    }
}
