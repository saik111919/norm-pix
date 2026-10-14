package com.example.normpix.finance.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.normpix.finance.domain.isPictureName
import com.example.normpix.finance.share.decodePicture
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The pictures attached to transactions - a bill, a shop receipt, a payment screenshot - kept in the app's own files,
 * where no other app can read them, each under a random name ([isPictureName]) that its transaction's row refers to.
 * The database is what says which pictures exist: a file no transaction names is left over (an interrupted save, a
 * deleted transaction) and is swept away ([sweep]).
 *
 * A picture is first *staged* ([stage]): turned upright, scaled down to [MAX_SIDE] and saved again as a JPEG - which
 * also drops whatever else the file carried, such as where and when a photo was taken - into a draft in the cache. The
 * form shows the draft; only saving the transaction keeps it ([keep]). A draft nobody saved is deleted with the form, or
 * by [sweep] a day later at the latest.
 *
 * [kept] and [drafts] are the user's own folders unless given: the sample data keeps its pictures apart (see
 * [com.example.normpix.DummyData]), where its [sweep] can't reach the user's. Drafts have to be where the camera app is
 * let write (`res/xml/scan_file_paths.xml`).
 */
class TransactionPictures(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
    private val kept: File = File(context.filesDir, KEPT_DIR),
    private val drafts: File = File(context.cacheDir, DRAFTS_DIR),
) {

    /** A new, empty file for the camera app to write a photo into, and the content URI to hand it. */
    fun newCaptureTarget(): Pair<File, Uri> {
        drafts.mkdirs()
        val file = File(drafts, "capture-${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.scanfiles", file)
    }

    /**
     * The picture at [source] as a draft ready to attach, or null when it isn't a picture that can be opened. Reads it
     * once; [source] can be another app's (a shared or picked picture) or a capture target.
     */
    suspend fun stage(source: Uri): File? = withContext(Dispatchers.IO) {
        try {
            val decoded = decodePicture(context.contentResolver, source, MAX_SIDE) ?: return@withContext null
            // A screenshot with transparency would turn black where it is see-through: put it on white first.
            val opaque = if (decoded.hasAlpha()) onWhite(decoded).also { decoded.recycle() } else decoded
            drafts.mkdirs()
            val draft = File(drafts, "draft-${UUID.randomUUID()}.jpg")
            try {
                draft.outputStream().use { out ->
                    if (!opaque.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) throw IOException("not encoded")
                }
                draft
            } catch (e: Exception) {
                draft.delete()
                throw e
            } finally {
                opaque.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun onWhite(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return out
    }

    /** Deletes a draft (or capture target) that won't be attached. Anything that isn't one is left alone. */
    fun discard(draft: File?) {
        if (draft != null && draft.parentFile == drafts) draft.delete()
    }

    /**
     * Files a copy of [draft] as a kept picture and returns the name its transaction refers to it by. The draft stays
     * the form's until it closes, so a save that fails can simply be tried again.
     */
    suspend fun keep(draft: File): String = withContext(Dispatchers.IO) {
        require(draft.parentFile == drafts && draft.isFile) { "not a draft picture" }
        kept.mkdirs()
        val name = "pic-${UUID.randomUUID()}.jpg"
        draft.copyTo(File(kept, name))
        name
    }

    /** The kept picture called [name], or null when there is no such picture (any longer). */
    fun file(name: String?): File? {
        if (!isPictureName(name)) return null
        return File(kept, name!!).takeIf { it.isFile }
    }

    /** Whether the kept picture called [name] is there. */
    fun exists(name: String?): Boolean = file(name) != null

    suspend fun delete(name: String?) {
        if (!isPictureName(name)) return
        withContext(Dispatchers.IO) { File(kept, name!!).delete() }
    }

    /**
     * Deletes every kept picture no transaction refers to ([referenced] are the names that are), and the drafts left
     * from forms that were never closed properly - the ones more than a day old, so a form open right now keeps its own.
     */
    suspend fun sweep(referenced: Set<String>) = withContext(Dispatchers.IO) {
        kept.listFiles()?.forEach { if (it.name !in referenced) it.delete() }
        val stale = clock() - DRAFT_LIFETIME_MS
        drafts.listFiles()?.forEach { if (it.lastModified() < stale) it.delete() }
    }

    /** Deletes every picture, kept or draft (when all the data is erased). */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        kept.listFiles()?.forEach { it.delete() }
        drafts.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val KEPT_DIR = "pictures"
        const val DRAFTS_DIR = "picture-drafts"

        /** Longest side of a kept picture, in pixels: a phone screenshot stays sharp, a camera photo shrinks to a few hundred KB. */
        const val MAX_SIDE = 2_400
        private const val QUALITY = 85
        private const val DRAFT_LIFETIME_MS = 24 * 60 * 60 * 1000L
    }
}
