package com.example.normpix.finance.share

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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
 * What a picture says: its [text], a piece per line in reading order; its [headline] - the figure printed largest, which
 * on a payment app's receipt is the amount (see [SharedTransactionParser.headline]); its [lines], each where it is on the
 * picture (empty when that isn't known); and the [name] of its file, which some apps write the payment into (Google
 * Pay's "… - 30.00 To UBER … on Google Pay.png").
 */
data class PictureText(
    val text: String,
    val headline: String? = null,
    val lines: List<PictureLine> = emptyList(),
    val name: String? = null,
)

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
                val lines = found.textBlocks.flatMap { block -> block.lines }.flatMap(::piecesOf)
                val name = withContext(Dispatchers.IO) { pictureName(context.contentResolver, picture) }
                if (lines.isEmpty()) {
                    PictureText(found.text, name = name)
                } else {
                    // Read as a person reads it, which is not the order the recognizer gives: a label can come out two lines from its value.
                    val text = ReceiptLayout(ReceiptLayout.rowsOf(lines)).readingText
                    PictureText(text, SharedTransactionParser.headline(lines), lines, name)
                }
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

    /** A recognized line as pieces: its words, each with any ₹ read as a digit put back, apart where a wide gap parts them. */
    private fun piecesOf(line: Text.Line): List<PictureLine> {
        val words = line.elements.mapNotNull { word ->
            val box = word.boundingBox ?: return@mapNotNull null
            val sure = try {
                word.symbols.map { it.confidence }
            } catch (_: RuntimeException) {
                emptyList()
            }
            PictureLine(rupeeRestored(word.text, sure), box.left, box.top, box.right, box.bottom)
        }
        if (words.isNotEmpty()) return splitAtGaps(words)
        val box = line.boundingBox ?: return emptyList()
        return listOf(PictureLine(line.text, box.left, box.top, box.right, box.bottom))
    }

    private companion object {
        const val MAX_SIDE = 3_000
        const val TIMEOUT_MS = 20_000L
    }
}

/** The name of the file at [uri], as the app that shared it gives it; null when it gives none. Never throws. */
internal fun pictureName(resolver: ContentResolver, uri: Uri): String? = try {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } else {
        null
    }
} catch (_: Exception) {
    null
}

/** How unsure of a character the recognizer is when it is reading a ₹ it has no letter for (as "7", "2" or "Z"). */
private const val RUPEE_DOUBT = 0.25f

/** A figure with something in front that may be a ₹: "730", "Z10", "7360", "21,250.50". */
private val rupeeFront = Regex("""^[\dA-Za-z%*?]\d(?:[\d,]{0,8}\d)?(?:\.\d{1,2})?$""")

/**
 * [word] with its first character put back as the ₹ it stands for. The recognizer has no ₹, and reads one as "7", "2"
 * or "Z" - scoring that character far below the digits after it ([sure], one score per character: 0.07-0.12 against
 * 0.6-0.95 on real PhonePe and Google Pay receipts, where a real digit beside a ₹ it swallowed still scores 0.29 or
 * more). Unchanged unless [word] is a figure whose first character is that unsure.
 */
internal fun rupeeRestored(word: String, sure: List<Float>): String {
    if (sure.size != word.length || word.length < 2 || !rupeeFront.matches(word)) return word
    val first = sure.first()
    if (first >= RUPEE_DOUBT || first >= sure.drop(1).min() / 2) return word
    return "₹" + word.substring(1)
}

/** Wider than this many times the text's height, a gap between two words parts them into pieces: a name, and far right, the amount. */
private const val WORD_GAP = 2

/** A recognized line's words as pieces: the words run together again, apart where a wide gap parts them. */
internal fun splitAtGaps(words: List<PictureLine>): List<PictureLine> {
    val pieces = mutableListOf<MutableList<PictureLine>>()
    for (word in words.sortedBy { it.left }) {
        val last = pieces.lastOrNull()?.last()
        if (last != null && word.left - last.right <= WORD_GAP * maxOf(last.height, word.height)) pieces.last() += word else pieces += mutableListOf(word)
    }
    return pieces.map { piece ->
        PictureLine(piece.joinToString(" ") { it.text }, piece.minOf { it.left }, piece.minOf { it.top }, piece.maxOf { it.right }, piece.maxOf { it.bottom })
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
