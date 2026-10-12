package com.example.normpix.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.example.normpix.finance.share.decodePicture
import java.io.File
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The user's own picture for their profile. They pick a photo, choose the part of it that shows (a square, which the app wears
 * as a circle) and the app keeps a small copy of just that part in its own files, where no other app can read it. The
 * preferences hold the copy's file name; the picture the user picked is never kept, and neither is whatever else the photo
 * carried - where and when it was taken - since only the chosen square is written out again.
 */

/** The window the user has chosen onto a picture: how far it is zoomed in, and where the middle of the window sits. */
data class PhotoCrop(
    /** 1 shows the most the picture can give as a square (its shorter side); higher shows less of it, closer. */
    val zoom: Float = MIN_ZOOM,
    /** Where the middle of the window is, as a share of the picture's width (0 left edge, 1 right edge). */
    val centerX: Float = 0.5f,
    /** The same, down the picture's height. */
    val centerY: Float = 0.5f,
) {
    /** This crop moved back inside a [width] x [height] picture, with its zoom within bounds. */
    fun fitted(width: Int, height: Int): PhotoCrop {
        val zoomed = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val side = min(width, height) / zoomed
        val halfX = side / 2f / width
        val halfY = side / 2f / height
        return PhotoCrop(zoomed, centerX.coerceIn(halfX, 1f - halfX), centerY.coerceIn(halfY, 1f - halfY))
    }

    /** The same window, zoomed to [value] about its middle. */
    fun zoomedTo(value: Float, width: Int, height: Int): PhotoCrop = copy(zoom = value).fitted(width, height)

    /**
     * What a gesture on a square view [viewport] pixels wide does: the picture follows the fingers by [panX] and [panY] pixels
     * and is zoomed by the factor [zoomBy] (a pinch).
     */
    fun transformed(panX: Float, panY: Float, zoomBy: Float, viewport: Float, width: Int, height: Int): PhotoCrop {
        val pixelsPerViewPixel = min(width, height) / zoom / viewport.coerceAtLeast(1f)
        return PhotoCrop(
            zoom = zoom * zoomBy,
            centerX = centerX - panX * pixelsPerViewPixel / width,
            centerY = centerY - panY * pixelsPerViewPixel / height,
        ).fitted(width, height)
    }

    /** The window as a square of the picture in its own pixels. */
    fun window(width: Int, height: Int): CropWindow {
        val fit = fitted(width, height)
        val side = min(width, height) / fit.zoom
        return CropWindow(
            left = (fit.centerX * width - side / 2f).coerceIn(0f, width - side),
            top = (fit.centerY * height - side / 2f).coerceIn(0f, height - side),
            side = side,
        )
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 4f
    }
}

/** A square of a picture, in the picture's pixels. */
data class CropWindow(val left: Float, val top: Float, val side: Float)

/** Whether [name] is the name of a profile photo file ([ProfilePhotos.save] makes these) - and so safe to build a path from. */
fun isProfilePhotoName(name: String?): Boolean = name != null && PHOTO_NAME.matches(name)

private val PHOTO_NAME = Regex("photo-\\d{1,19}\\.jpg")

/** The profile photo files, kept in a folder of their own in the app's files. */
class ProfilePhotos(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    private val dir = File(context.filesDir, DIR)

    /** The photo file called [name], or null when there is no such file (any longer). */
    fun file(name: String?): File? = if (isProfilePhotoName(name)) File(dir, name!!).takeIf { it.isFile } else null

    /** The picture at [source] ready to be framed: upright and at most [OPEN_SIDE] on its longer side. Null if it can't be read. */
    suspend fun open(source: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            decodePicture(context.contentResolver, source, OPEN_SIDE)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /** The kept photo called [name] decoded, or null when it isn't there or can't be read. */
    suspend fun read(name: String?): Bitmap? = withContext(Dispatchers.IO) {
        val file = file(name) ?: return@withContext null
        try {
            BitmapFactory.decodeFile(file.path)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /**
     * Keeps the part of [source] that [crop] frames as a [SIDE] pixel square JPEG, and returns its file name. A new file each
     * time, so a picture drawn from the old name is never half-replaced. Throws [IOException] when it can't be written.
     */
    suspend fun save(source: Bitmap, crop: PhotoCrop): String = withContext(Dispatchers.IO) {
        val window = crop.window(source.width, source.height)
        val side = window.side.roundToInt().coerceIn(1, min(source.width, source.height))
        val left = window.left.roundToInt().coerceIn(0, source.width - side)
        val top = window.top.roundToInt().coerceIn(0, source.height - side)
        val square = Bitmap.createBitmap(source, left, top, side, side)
        val sized = if (side > SIDE) square.scale(SIDE, SIDE) else square
        // A picture with see-through parts would turn black in a JPEG: put it on white first.
        val flat = if (sized.hasAlpha()) onWhite(sized) else sized
        dir.mkdirs()
        var stamp = clock()
        while (File(dir, "photo-$stamp.jpg").exists()) stamp++
        val name = "photo-$stamp.jpg"
        val target = File(dir, name)
        try {
            target.outputStream().use { out -> if (!flat.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) throw IOException("not encoded") }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        name
    }

    /** Deletes every photo but [keep] (a new one has just replaced the others). */
    suspend fun keepOnly(keep: String?) = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { if (it.name != keep) it.delete() }
    }

    /** Deletes every photo. */
    suspend fun deleteAll() = keepOnly(null)

    private fun onWhite(bitmap: Bitmap): Bitmap {
        val out = createBitmap(bitmap.width, bitmap.height)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, null)
        }
        return out
    }

    companion object {
        const val DIR = "profile"

        /** The longest side of a picture while it is being framed: sharp enough to place the window, light on memory. */
        const val OPEN_SIDE = 1_600

        /** The side of the kept square, in pixels: sharp at the largest the picture is shown (the Profile page's), a few tens of KB. */
        const val SIDE = 512
        private const val QUALITY = 90
    }
}
