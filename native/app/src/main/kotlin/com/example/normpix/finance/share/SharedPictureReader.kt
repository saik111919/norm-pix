package com.example.normpix.finance.share

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What a picture says: its [text], line by line in reading order, and its [headline] - the figure printed largest, which
 * on a payment app's receipt is the amount (see [SharedTransactionParser.headline]).
 */
data class PictureText(val text: String, val headline: String? = null)

/** Reads the text in a picture (a payment app's receipt screenshot, a photo of a bill). */
fun interface SharedPictureReader {
    /** The picture's text, or null when there is none or it can't be read (not a picture, no longer readable, too slow). */
    suspend fun read(picture: Uri): PictureText?
}

/** Reads nothing: where there is no text recognizer (tests). */
object NoSharedPictureReader : SharedPictureReader {
    override suspend fun read(picture: Uri): PictureText? = null
}

/**
 * Reads a shared picture's text on this phone with ML Kit's bundled recognizer - the one card scanning uses - so the
 * picture never leaves the device (the app has no network access at all). The picture is read straight from the app
 * that shared it and is never copied or kept.
 */
class MlKitSharedPictureReader(private val context: Context) : SharedPictureReader {
    override suspend fun read(picture: Uri): PictureText? = try {
        withTimeoutOrNull<PictureText?>(TIMEOUT_MS) {
            val bitmap = withContext(Dispatchers.IO) { decodePicture(context.contentResolver, picture, MAX_SIDE) }
                ?: return@withTimeoutOrNull null
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                val found = suspendCancellableCoroutine { continuation ->
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { continuation.resume(it) }
                        .addOnFailureListener { continuation.resumeWithException(it) }
                }
                val lines = found.textBlocks.flatMap { block -> block.lines }
                    .map { line -> PictureLine(line.text, line.boundingBox?.height() ?: 0) }
                PictureText(found.text, SharedTransactionParser.headline(lines))
            } finally {
                recognizer.close()
                bitmap.recycle()
            }
        }?.takeIf { it.text.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    }

    private companion object {
        const val MAX_SIDE = 3_000
        const val TIMEOUT_MS = 20_000L
    }
}

/**
 * The picture at [uri], upright (a camera photo's EXIF orientation applied) and scaled down by powers of two until its
 * longer side is at most [maxSide] - a screenshot stays as it is. Null when it isn't a picture or can't be opened.
 */
internal fun decodePicture(resolver: ContentResolver, uri: Uri, maxSide: Int): Bitmap? {
    // Measuring only: decodeStream returns null by design here and fills in the size.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val measuring = resolver.openInputStream(uri) ?: return null
    measuring.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
    val degrees = try {
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    } catch (_: Exception) {
        0
    }
    if (degrees == 0) return decoded
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
    if (upright !== decoded) decoded.recycle()
    return upright
}
