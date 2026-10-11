package com.example.normpix.wallet

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Fields pulled off a photographed card via on-device OCR. Any field can be null if it wasn't confidently found. */
data class ScannedCardData(
    val cardNumber: String? = null,
    val expiryMonth: String? = null,
    val expiryYear: String? = null,
    val cardholderName: String? = null,
) {
    val isEmpty: Boolean get() = cardNumber == null && expiryMonth == null && expiryYear == null && cardholderName == null

    // Never print card data to logs.
    override fun toString(): String = "ScannedCardData(redacted)"
}

/** The OCR step failed in a way the caller should tell the user about. */
class CardScanException(message: String) : Exception(message)

/**
 * Parses the printed number, name and expiry out of recognized card text. CVV
 * is deliberately never parsed - it isn't meant to be captured this casually,
 * and the user should always type that one themselves.
 */
object CardTextParser {
    private val skipWords = setOf(
        "VALID", "THRU", "FROM", "MEMBER", "SINCE", "CARD", "BANK", "DEBIT", "CREDIT",
        "VISA", "MASTERCARD", "RUPAY", "EXPIRES", "GOOD",
    )
    private val numberPattern = Regex("(?:\\d[ -]?){12,19}")
    // Four-digit years first: with "\d{2}|\d{4}" the match would stop at "20" in "12/2031".
    private val expiryPattern = Regex("(0[1-9]|1[0-2])\\s*/\\s*(\\d{4}|\\d{2})(?!\\d)")
    private val capsNamePattern = Regex("^[A-Z][A-Z .]{4,26}$")

    fun parse(text: String): ScannedCardData {
        var cardNumber: String? = null
        for (match in numberPattern.findAll(text)) {
            val digits = match.value.filter { it in '0'..'9' }
            if (digits.length !in 12..19) continue
            if (cardNumber == null || digits.length > cardNumber.length) cardNumber = digits
        }

        var month: String? = null
        var year: String? = null
        expiryPattern.find(text)?.let { match ->
            month = match.groupValues[1]
            val raw = match.groupValues[2]
            year = if (raw.length == 4) raw.substring(2) else raw
        }

        var name: String? = null
        for (line in text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }) {
            if (!capsNamePattern.matches(line)) continue
            val words = line.split(Regex("\\s+"))
            if (words.size < 2) continue
            if (words.any { it in skipWords }) continue
            name = line
            break
        }
        return ScannedCardData(cardNumber, month, year, name)
    }
}

/**
 * Photo capture + on-device OCR. The photo is taken by the system camera app
 * into the app's private cache (so no camera permission is needed), read by
 * the bundled ML Kit text recognizer, and deleted straight afterwards - it
 * shows the full card face and must not outlive OCR.
 */
class CardScanner(private val context: Context) {
    /** A fresh, empty file in the cache plus the content URI the camera app should write to. */
    fun newPhotoTarget(): Pair<File, Uri> {
        val dir = File(context.cacheDir, "scan").apply { mkdirs() }
        val file = File(dir, "card-${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.scanfiles", file)
        return file to uri
    }

    /** Recognizes the text in [photo] and parses it. Always deletes [photo]. */
    suspend fun read(photo: File): ScannedCardData {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(photo)))
                    .addOnSuccessListener { continuation.resume(it.text) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            return CardTextParser.parse(text)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            throw CardScanException("Couldn't read that photo - try again with better lighting, or enter the details manually.")
        } finally {
            recognizer.close()
            photo.delete()
        }
    }

    /** Removes a photo target that was never used (the user cancelled). */
    fun discard(photo: File) {
        photo.delete()
    }
}
