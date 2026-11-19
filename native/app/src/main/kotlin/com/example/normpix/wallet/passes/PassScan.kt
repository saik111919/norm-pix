package com.example.normpix.wallet.passes

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** What a picture of a card's code said: the number it holds and the type of code it is. Its [toString] does not say the number. */
class ScannedPassCode(val number: String, val format: PassCodeFormat) {
    override fun toString(): String = "ScannedPassCode(format=$format)"
}

/** A picture with no code in it NormPix could read, said to the user. */
class PassScanException(message: String) : Exception(message)

/** Reads the code on a loyalty or gift card from a picture (a photo the camera app took, or a screenshot); a seam for tests. */
fun interface PassCodeReader {
    suspend fun read(picture: Uri): ScannedPassCode
}

/**
 * The bundled ML Kit barcode model, on the phone: nothing is uploaded (its telemetry backend is dropped at build time, as the text
 * recognizer's is). Of the codes in the picture the largest is taken - a card's own, not a small one printed beside it.
 */
class MlKitPassCodeReader(private val context: Context) : PassCodeReader {
    override suspend fun read(picture: Uri): ScannedPassCode {
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())
        try {
            val codes = suspendCancellableCoroutine { continuation ->
                scanner.process(InputImage.fromFilePath(context, picture))
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            val best = codes
                .filter { !it.rawValue.isNullOrEmpty() }
                .maxByOrNull { code -> code.boundingBox?.let { it.width() * it.height() } ?: 0 }
                ?: throw PassScanException("No code found in that picture. Try again closer up, with the whole code in it, or type the number.")
            return ScannedPassCode(best.rawValue!!, formatOf(best.format))
        } catch (e: PassScanException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            throw PassScanException("Couldn't read that picture. Try another, or type the number.")
        } finally {
            scanner.close()
        }
    }

    companion object {
        /** ML Kit's code type as the app's; one it has no name for is drawn as Code 128, which holds any text. */
        fun formatOf(format: Int): PassCodeFormat = when (format) {
            Barcode.FORMAT_CODE_128 -> PassCodeFormat.Code128
            Barcode.FORMAT_QR_CODE -> PassCodeFormat.Qr
            Barcode.FORMAT_EAN_13 -> PassCodeFormat.Ean13
            Barcode.FORMAT_EAN_8 -> PassCodeFormat.Ean8
            Barcode.FORMAT_UPC_A -> PassCodeFormat.UpcA
            Barcode.FORMAT_UPC_E -> PassCodeFormat.UpcE
            Barcode.FORMAT_CODE_39 -> PassCodeFormat.Code39
            Barcode.FORMAT_CODE_93 -> PassCodeFormat.Code93
            Barcode.FORMAT_CODABAR -> PassCodeFormat.Codabar
            Barcode.FORMAT_ITF -> PassCodeFormat.Itf
            Barcode.FORMAT_PDF417 -> PassCodeFormat.Pdf417
            Barcode.FORMAT_AZTEC -> PassCodeFormat.Aztec
            Barcode.FORMAT_DATA_MATRIX -> PassCodeFormat.DataMatrix
            else -> PassCodeFormat.Code128
        }
    }
}
