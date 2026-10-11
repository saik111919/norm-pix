package com.example.normpix.finance.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The real decoding a shared or attached picture goes through before it is read (no stand-in decoder). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PictureDecodingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = context.contentResolver

    private fun picture(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        return ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
    }

    private fun serve(name: String, bytes: ByteArray): Uri {
        val uri = Uri.parse("content://com.example.payments.files/$name")
        shadowOf(resolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
        return uri
    }

    @Test
    fun `a screenshot is decoded as it is`() {
        val bitmap = assertNotNull(decodePicture(resolver, serve("shot.png", picture(1080, 2400)), maxSide = 3_000))
        assertEquals(1080, bitmap.width)
        assertEquals(2400, bitmap.height)
    }

    @Test
    fun `a large picture is scaled down to fit`() {
        val bitmap = assertNotNull(decodePicture(resolver, serve("big.png", picture(4000, 1000)), maxSide = 3_000))
        assertEquals(2000, bitmap.width)
        assertEquals(500, bitmap.height)
    }

    @Test
    fun `a camera photo is turned upright`() {
        val file = File(context.cacheDir, "sideways.jpg")
        file.writeBytes(picture(400, 200, Bitmap.CompressFormat.JPEG))
        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val bitmap = assertNotNull(decodePicture(resolver, serve("sideways.jpg", file.readBytes()), maxSide = 3_000))
        assertEquals(200, bitmap.width)
        assertEquals(400, bitmap.height)
    }

    @Test
    fun `what isn't a picture is nothing, and nothing is read from it`() {
        val text = serve("note.txt", "Paid ₹250 to Swiggy".toByteArray())
        assertNull(decodePicture(resolver, text, maxSide = 3_000))
        assertNull(runBlocking { MlKitSharedPictureReader(context).read(text) })
    }
}
