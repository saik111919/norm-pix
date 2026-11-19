package com.example.normpix.wallet.passes

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/*
 * A pass's code as modules to draw (ZXing's encoders: pure Java, nothing leaves the phone). The matrix is made at its natural size - one
 * cell a module, no margin - and the screen scales it, keeping the quiet zone round it itself. Pure: no Android.
 */

/** ZXing's name for [format]; null for [PassCodeFormat.None], which draws no code. */
internal fun PassCodeFormat.zxing(): BarcodeFormat? = when (this) {
    PassCodeFormat.Code128 -> BarcodeFormat.CODE_128
    PassCodeFormat.Qr -> BarcodeFormat.QR_CODE
    PassCodeFormat.Ean13 -> BarcodeFormat.EAN_13
    PassCodeFormat.Ean8 -> BarcodeFormat.EAN_8
    PassCodeFormat.UpcA -> BarcodeFormat.UPC_A
    PassCodeFormat.UpcE -> BarcodeFormat.UPC_E
    PassCodeFormat.Code39 -> BarcodeFormat.CODE_39
    PassCodeFormat.Code93 -> BarcodeFormat.CODE_93
    PassCodeFormat.Codabar -> BarcodeFormat.CODABAR
    PassCodeFormat.Itf -> BarcodeFormat.ITF
    PassCodeFormat.Pdf417 -> BarcodeFormat.PDF_417
    PassCodeFormat.Aztec -> BarcodeFormat.AZTEC
    PassCodeFormat.DataMatrix -> BarcodeFormat.DATA_MATRIX
    PassCodeFormat.None -> null
}

/**
 * [number] as [format]'s modules, or null when it draws no code ([PassCodeFormat.None]) or the number can't be one in it (letters in an
 * EAN-13, a wrong check digit, too long): [passCodeFault] says which in words. A one-dimensional code comes back one row tall.
 */
fun passCodeMatrix(number: String, format: PassCodeFormat): BitMatrix? {
    val zxing = format.zxing() ?: return null
    if (number.isEmpty()) return null
    val hints = buildMap<EncodeHintType, Any> {
        put(EncodeHintType.MARGIN, 0)
        put(EncodeHintType.CHARACTER_SET, "UTF-8")
        if (format == PassCodeFormat.Qr) put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
    }
    return try {
        MultiFormatWriter().encode(number, zxing, 0, 0, hints)
    } catch (_: Exception) {
        // IllegalArgumentException for contents the format can't hold, WriterException for too much: either way, no code.
        null
    }
}

/** Why [number] can't be drawn as [format], in the form's words; null when it can (or when it draws no code at all). */
fun passCodeFault(number: String, format: PassCodeFormat): String? {
    if (format == PassCodeFormat.None || number.isBlank()) return null
    return if (passCodeMatrix(number, format) == null) {
        "\"${number.take(24)}\" can't be drawn as ${format.label}. Check the number, or pick another type of code."
    } else {
        null
    }
}

/** The modules of a one-dimensional code as runs of bars: (start, width) of each dark run along its single row. */
fun barsOf(matrix: BitMatrix): List<Pair<Int, Int>> {
    val bars = mutableListOf<Pair<Int, Int>>()
    var start = -1
    for (x in 0 until matrix.width) {
        val dark = matrix[x, 0]
        if (dark && start < 0) start = x
        if (!dark && start >= 0) {
            bars += start to (x - start)
            start = -1
        }
    }
    if (start >= 0) bars += start to (matrix.width - start)
    return bars
}

/**
 * The type of code a typed number most likely is, for a pass whose code was not scanned: 13 digits with a right check digit is an
 * EAN-13, 8 an EAN-8, 12 a UPC-A; anything else Code 128, which holds any printable text.
 */
fun likelyFormatOf(number: String): PassCodeFormat {
    val digits = number.filter { !it.isWhitespace() }
    if (digits.all(Char::isDigit)) {
        when (digits.length) {
            13 -> if (passCodeMatrix(digits, PassCodeFormat.Ean13) != null) return PassCodeFormat.Ean13
            8 -> if (passCodeMatrix(digits, PassCodeFormat.Ean8) != null) return PassCodeFormat.Ean8
            12 -> if (passCodeMatrix(digits, PassCodeFormat.UpcA) != null) return PassCodeFormat.UpcA
        }
    }
    return PassCodeFormat.Code128
}
