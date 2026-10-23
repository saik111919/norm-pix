package com.example.normpix.finance.ui

import androidx.core.graphics.createBitmap
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.FileProvider
import com.example.normpix.wallet.ActivityHost
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Takes a finished receipt out of the app. */
interface ReceiptSharer {
    /** Offers [png] to the system share sheet as [fileName]; false when nothing could be offered. */
    suspend fun shareImage(png: ByteArray, fileName: String): Boolean
}

/** Shares nothing: where there is no share sheet (tests that do not look at sharing). */
object NoReceiptSharer : ReceiptSharer {
    override suspend fun shareImage(png: ByteArray, fileName: String): Boolean = false
}

/**
 * The system share sheet. The picture is written to the app's cache and handed over as a content URI the receiving app may
 * read once - no storage permission, nothing left in the gallery - and what was written is swept away a day later.
 */
class AndroidReceiptSharer(private val context: Context, private val host: ActivityHost) : ReceiptSharer {
    override suspend fun shareImage(png: ByteArray, fileName: String): Boolean {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
            val stale = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            dir.listFiles()?.filter { it.lastModified() < stale }?.forEach { it.delete() }
            val file = File(dir, fileName)
            file.writeBytes(png)
            FileProvider.getUriForFile(context, "${context.packageName}.scanfiles", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share receipt")
        val activity = host.activity
        if (activity == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return withContext(Dispatchers.Main) {
            runCatching { (activity ?: context).startActivity(chooser) }
                .onFailure { android.util.Log.w("ReceiptShare", "Could not open the share sheet", it) }
                .isSuccess
        }
    }

    private companion object {
        /** Under the cache, where the file provider's paths allow it (see res/xml/scan_file_paths.xml). */
        const val DIRECTORY = "receipts"
    }
}

private const val OPAQUE = 0xFF000000.toInt()

/**
 * The receipt as the picture that is shared: [receipt] - which is see-through round its notches and scalloped edge - laid
 * on [background] with [margin] pixels all round, as a PNG. The picture is always opaque: a see-through [background] would
 * leave the receipt's light writing on whatever the picture is later shown over.
 */
fun framedReceiptPng(receipt: Bitmap, background: Int, margin: Int): ByteArray {
    // A bitmap that lives on the GPU cannot be drawn into this canvas.
    val source = if (receipt.config == Bitmap.Config.HARDWARE) receipt.copy(Bitmap.Config.ARGB_8888, false) else receipt
    val framed = createBitmap(source.width + 2 * margin, source.height + 2 * margin)
    val canvas = Canvas(framed)
    canvas.drawColor(background or OPAQUE)
    canvas.drawBitmap(source, margin.toFloat(), margin.toFloat(), null)
    return ByteArrayOutputStream().use { out ->
        framed.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}
