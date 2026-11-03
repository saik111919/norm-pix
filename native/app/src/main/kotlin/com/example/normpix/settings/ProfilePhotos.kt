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

/**
 * Whether [name] is the name of a profile photo file ([ProfilePhotos.save] and [ProfilePhotos.saveGif] make these) - and so safe
 * to build a path from.
 */
fun isProfilePhotoName(name: String?): Boolean = name != null && PHOTO_NAME.matches(name)

/** Whether the profile photo called [name] is an animated picture kept as it came (a GIF), not a framed square JPEG. */
fun isAnimatedPhotoName(name: String?): Boolean = name != null && name.endsWith(".gif") && isProfilePhotoName(name)

private val PHOTO_NAME = Regex("photo-\\d{1,19}\\.(jpg|gif)")

/** Whether [head] - the first [count] bytes of a file - begin a GIF ("GIF87a" or "GIF89a"). */
fun looksLikeGif(head: ByteArray, count: Int): Boolean =
    count >= 6 && String(head, 0, 6, Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }

/**
 * A picture that comes with NormPix to wear as the profile picture without picking one (the files are in `assets/avatars`).
 * [crop] frames the square that shows - the robot's is high up, where its face is. Wearing one keeps a copy as any photo is kept
 * ([ProfilePhotos.savePreset]), so it is backed up, replaced and removed like a photo and needs no setting of its own.
 */
enum class AvatarPreset(val label: String, val asset: String, val crop: PhotoCrop) {
    Fox("Fox with goggles", "avatars/fox.jpg", PhotoCrop()),
    Robot("Little robot", "avatars/robot.jpg", PhotoCrop(centerY = 0.40f)),
    Mouse("Yellow mouse", "avatars/mouse.jpg", PhotoCrop()),
}

/** What came of keeping a picked GIF ([ProfilePhotos.saveGif]): its file [name], or why not ([problem]). */
class GifSave(val name: String?, val problem: GifProblem?)

enum class GifProblem {
    /** Over [ProfilePhotos.MAX_GIF_BYTES]: an animation this big would sit in memory frame by frame. */
    TooLarge,

    /** Not a GIF after all, or one that could not be read. */
    Unreadable,
}

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

    /** Keeps [preset]'s picture as the profile photo, framed as it says, and returns its file name. Throws [IOException] when it can't be read or written. */
    suspend fun savePreset(preset: AvatarPreset): String {
        val bitmap = withContext(Dispatchers.IO) {
            try {
                context.assets.open(preset.asset).use(BitmapFactory::decodeStream)
            } catch (e: IOException) {
                null
            }
        } ?: throw IOException("avatar not readable")
        return save(bitmap, preset.crop)
    }

    /**
     * Whether the picture at [source] is a GIF, by its first bytes (a picker's idea of its type is not always right): an animation
     * cannot be framed like a photo (it would stop moving), so it is kept as it came and the circle crops it to fit ([saveGif]).
     */
    suspend fun isGif(source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                val head = ByteArray(6)
                looksLikeGif(head, input.read(head))
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * Keeps the GIF at [source] as it is, in a new file, up to [MAX_GIF_BYTES]. Checked by its first bytes, not by what it was
     * called, and nothing is kept when it is refused. Unlike a photo it is not re-encoded, so whatever its metadata says travels
     * with it - a GIF has no location or camera to give away.
     */
    suspend fun saveGif(source: Uri): GifSave = withContext(Dispatchers.IO) {
        dir.mkdirs()
        var stamp = clock()
        while (File(dir, "photo-$stamp.gif").exists()) stamp++
        val target = File(dir, "photo-$stamp.gif")
        try {
            val stream = context.contentResolver.openInputStream(source) ?: return@withContext GifSave(null, GifProblem.Unreadable)
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            var head = ByteArray(0)
            val tooLarge = stream.use { input ->
                target.outputStream().use { out ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (total == 0L) head = buffer.copyOf(minOf(read, 6))
                        total += read
                        if (total > MAX_GIF_BYTES) return@use true
                        out.write(buffer, 0, read)
                    }
                    false
                }
            }
            when {
                tooLarge -> {
                    target.delete()
                    GifSave(null, GifProblem.TooLarge)
                }
                !looksLikeGif(head, head.size) -> {
                    target.delete()
                    GifSave(null, GifProblem.Unreadable)
                }
                else -> GifSave(target.name, null)
            }
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (_: Exception) {
            target.delete()
            GifSave(null, GifProblem.Unreadable)
        }
    }

    /** Deletes every photo but [keep] (a new one has just replaced the others). */
    suspend fun keepOnly(keep: String?) = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { if (it.name != keep) it.delete() }
    }

    /** Deletes every photo. */
    suspend fun deleteAll() = keepOnly(null)

    /**
     * Files [file] - a photo read from an app backup - as the kept photo called [name], unless a photo by that name is kept
     * already (a name is a time stamp, so it is the same photo). False when it couldn't be filed.
     */
    suspend fun adopt(file: File, name: String): Boolean = withContext(Dispatchers.IO) {
        if (!isProfilePhotoName(name) || !file.isFile) return@withContext false
        dir.mkdirs()
        val target = File(dir, name)
        if (target.isFile) return@withContext true
        file.renameTo(target) || runCatching { file.copyTo(target) }.isSuccess
    }

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

        /** The most a kept GIF may be: its frames are drawn from it as they play, so a big one is a lot of memory. */
        const val MAX_GIF_BYTES = 5L * 1024 * 1024
        private const val QUALITY = 90
    }
}
