package com.example.normpix.wallet.nfc

import android.nfc.FormatException
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.os.Build
import android.util.Log
import com.example.normpix.BuildConfig
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.CardNetwork
import java.io.IOException
import java.nio.charset.StandardCharsets

/*
 * What the Inlet reader can take from a tapped tag, card or phone.
 *
 * Android's reader mode hands over every tag it finds together with the technologies it speaks
 * (Tag.getTechList: IsoDep, NfcA, NfcB, NfcF, NfcV, Ndef, MifareClassic, MifareUltralight, ...).
 * Phone-to-phone sharing works because the sending phone emulates an ISO-DEP card
 * (NfcCardOutletService) that answers NormPix's private AID. A bank card never answers that AID
 * and holds no NDEF message, which is why tapping one used to end in "Unsupported NFC card".
 *
 * Full card details (saved straight to the wallet):
 *  - ISO-DEP answering SELECT F0010203040506: another phone in NormPix's Share (Outlet) mode.
 *  - NDEF (any NFC Forum tag type) holding a NormPix card record.
 *
 * Identified only, nothing secret is read:
 *  - EMV contactless payment cards and phone wallets: ISO-DEP answering SELECT "2PAY.SYS.DDF01"
 *    (the PPSE). Its answer is the card's public directory, the application ids and labels
 *    ("VISA DEBIT") every payment terminal reads before a payment starts. It gives the network
 *    and, when the label says so, debit or credit. The reader stops there: it never sends
 *    GET PROCESSING OPTIONS or READ RECORD, so the card number, expiry, name and cryptograms are
 *    never asked for. The CVV is only printed on the card; it is not on the chip at all.
 *
 * Detected, nothing usable: other ISO-DEP smart cards (ID, transit, access), MIFARE Classic /
 * Ultralight and other NfcA/NfcB memory tags, FeliCa (NfcF), ISO 15693 labels (NfcV), and NDEF tags
 * without a NormPix record.
 *
 * Nothing about where a card's antenna sits is available from any of these technologies: a tag
 * reports what it speaks, never its geometry.
 */

/** What reading one tapped tag produced. */
sealed interface NfcTagRead {
    /** A NormPix card: another phone sharing one, or a tag written by NormPix. */
    data class NormPixPayload(val payload: String) : NfcTagRead

    /** A contactless payment card or phone wallet: only its public payment directory was read. */
    data class PaymentCard(val info: PaymentCardInfo) : NfcTagRead

    /** Something was tapped that holds nothing NormPix can use. */
    data class Unsupported(val tag: DetectedTag) : NfcTagRead

    /** The tag left the field (or the connection broke) before it could be read. */
    data object Lost : NfcTagRead
}

/** What a tapped tag that can't give a card turned out to be, in the words the Inlet shows. */
enum class DetectedTag(val title: String, val message: String) {
    PhoneNotSharing(
        "That phone isn't sharing a card",
        "It has NormPix, but nothing is being shared right now. On that phone, open Wallet, choose a card and tap Share, then hold the phones back to back again.",
    ),
    TagWithoutCard(
        "No card on this NFC tag",
        "The tag was read, but it doesn't hold a card saved from NormPix.",
    ),
    OtherSmartCard(
        "This card can't be added",
        "It looks like an ID, transit or access card. Only bank cards, and phones sharing a card from NormPix, work here.",
    ),
    OtherTag(
        "This tag can't be added",
        "It looks like a transit pass, key card or NFC sticker. Only bank cards, and phones sharing a card from NormPix, work here.",
    ),
}

/**
 * A payment card identified from its public directory. [networkName] and [label] come from the
 * card itself; [kind] is only set when the card says debit or credit outright.
 */
data class PaymentCardInfo(
    val networkName: String?,
    val network: CardNetwork?,
    val kind: CardKind?,
    val label: String?,
) {
    /** "Visa debit card", "RuPay card", "Contactless card". */
    val title: String
        get() {
            val name = networkName ?: label ?: "Contactless"
            return if (kind == null) "$name card" else "$name ${kind.label.lowercase()} card"
        }
}

/** The I/O a read needs from a tag, so the reading logic can run without NFC hardware. */
internal interface TagChannel {
    /** Technologies Android reports for the tag, as simple names: "IsoDep", "NfcA", "Ndef", ... */
    val techs: Set<String>

    /** Runs [block] over an open ISO-DEP connection; null when the tag doesn't speak ISO-DEP. */
    fun <T> isoDep(block: (transceive: (ByteArray) -> ByteArray) -> T): T?

    /** The payloads of the tag's NDEF records; null when it doesn't speak NDEF. */
    fun ndefPayloads(): List<ByteArray>?
}

internal class AndroidTagChannel(private val tag: Tag) : TagChannel {
    override val techs: Set<String> = tag.techList.map { it.substringAfterLast('.') }.toSet()

    override fun <T> isoDep(block: ((ByteArray) -> ByteArray) -> T): T? {
        val iso = IsoDep.get(tag) ?: return null
        iso.connect()
        try {
            return block { apdu -> iso.transceive(apdu) }
        } finally {
            runCatching { iso.close() }
        }
    }

    override fun ndefPayloads(): List<ByteArray>? {
        val ndef = Ndef.get(tag) ?: return null
        ndef.connect()
        try {
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage ?: return emptyList()
            return message.records.mapNotNull { it.payload }
        } finally {
            runCatching { ndef.close() }
        }
    }
}

/**
 * Reads one tag: NormPix's own AID first (a sharing phone), then the payment directory (a bank
 * card), then NDEF. Runs on the NFC binder thread; blocking I/O is fine there.
 */
internal fun readTag(tag: TagChannel): NfcTagRead {
    var lost = false
    val viaIsoDep = try {
        tag.isoDep(::readIsoDep)
    } catch (_: IOException) {
        // TagLostException is an IOException too: the card moved, or the link dropped mid-exchange.
        lost = true
        null
    } catch (_: SecurityException) {
        // "Tag is out of date": Android already forgot this tag (it left the field).
        lost = true
        null
    }
    if (viaIsoDep != null) return viaIsoDep

    val records = try {
        tag.ndefPayloads()
    } catch (_: FormatException) {
        emptyList()
    } catch (_: IOException) {
        lost = true
        null
    } catch (_: SecurityException) {
        lost = true
        null
    }
    records?.firstNotNullOfOrNull { bytes -> String(bytes, StandardCharsets.UTF_8).takeIf { NORMPIX_MARKER in it } }
        ?.let { return NfcTagRead.NormPixPayload(it) }

    if (lost) return NfcTagRead.Lost
    return NfcTagRead.Unsupported(
        when {
            records != null -> DetectedTag.TagWithoutCard
            "IsoDep" in tag.techs -> DetectedTag.OtherSmartCard
            else -> DetectedTag.OtherTag
        },
    )
}

private fun readIsoDep(send: (ByteArray) -> ByteArray): NfcTagRead? {
    val normPix = send(NfcCardOutletService.SELECT_AID_COMMAND)
    if (normPix.statusWord() == SW_OK) return NfcTagRead.NormPixPayload(String(normPix.body(), StandardCharsets.UTF_8))
    // Only NormPix's own service is registered for this private AID, and it answers this way when it has nothing to send.
    if (normPix.statusWord() == NfcCardOutletService.SW_NOTHING_SHARED) return NfcTagRead.Unsupported(DetectedTag.PhoneNotSharing)

    val directory = send(SELECT_PPSE)
    if (directory.statusWord() == SW_OK) return parsePaymentDirectory(directory.body())?.let { NfcTagRead.PaymentCard(it) }
    return null
}

private const val NORMPIX_MARKER = "normpix_card"
private const val SW_OK = 0x9000

private fun ByteArray.statusWord(): Int =
    if (size < 2) -1 else ((this[size - 2].toInt() and 0xFF) shl 8) or (this[size - 1].toInt() and 0xFF)

private fun ByteArray.body(): ByteArray = copyOfRange(0, (size - 2).coerceAtLeast(0))

/** SELECT by name "2PAY.SYS.DDF01": the contactless Proximity Payment System Environment (EMV Book B). */
internal val SELECT_PPSE: ByteArray = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E) +
    "2PAY.SYS.DDF01".toByteArray(StandardCharsets.US_ASCII) + byteArrayOf(0x00)

/**
 * The payment directory (PPSE FCI) as a [PaymentCardInfo], or null when [fci] isn't one.
 * Reads only the directory entries' AID (4F), label (50) and priority (87).
 */
internal fun parsePaymentDirectory(fci: ByteArray): PaymentCardInfo? {
    val entries = mutableListOf<List<Tlv>>()
    var isDirectory = false
    walkTlv(fci) { tlv ->
        when (tlv.tag) {
            TAG_DF_NAME -> if (String(tlv.value, StandardCharsets.US_ASCII) == "2PAY.SYS.DDF01") isDirectory = true
            TAG_APPLICATION -> parseTlv(tlv.value)?.let { entries += it }
        }
    }
    if (entries.isEmpty()) {
        return if (isDirectory) PaymentCardInfo(networkName = null, network = null, kind = null, label = null) else null
    }
    // The card's own preferred application: lowest priority number (87), unnumbered ones last.
    val entry = entries.minBy { e -> e.firstOrNull { it.tag == TAG_PRIORITY }?.value?.firstOrNull()?.let { it.toInt() and 0x0F } ?: 16 }
    val aid = entry.firstOrNull { it.tag == TAG_AID }?.value?.toHex().orEmpty()
    val text = entry.firstOrNull { it.tag == TAG_LABEL }?.value?.let(::printable).orEmpty()
    val label = text.take(16).trim().ifBlank { null }
    val scheme = PAYMENT_SCHEMES.firstOrNull { aid.startsWith(it.rid) }
    // Debit / credit is read from the whole label, before it is cut down for display.
    val upper = text.uppercase()
    val kind = when {
        "DEBIT" in upper || aid in DEBIT_AIDS -> CardKind.Debit
        "CREDIT" in upper -> CardKind.Credit
        else -> null
    }
    return PaymentCardInfo(networkName = scheme?.name, network = scheme?.network, kind = kind, label = label)
}

private class PaymentScheme(val rid: String, val name: String, val network: CardNetwork?)

/** Registered application provider ids (the first five bytes of an AID). */
private val PAYMENT_SCHEMES = listOf(
    PaymentScheme("A000000003", "Visa", CardNetwork.Visa),
    PaymentScheme("A000000004", "Mastercard", CardNetwork.Mastercard),
    PaymentScheme("A000000524", "RuPay", CardNetwork.Rupay),
    PaymentScheme("A000000025", "American Express", CardNetwork.Amex),
    PaymentScheme("A000000152", "Discover", CardNetwork.Discover),
    PaymentScheme("A000000324", "Discover", CardNetwork.Discover),
    PaymentScheme("A000000065", "JCB", null),
    PaymentScheme("A000000333", "UnionPay", null),
    PaymentScheme("A000000277", "Interac", null),
)

/** Applications that only exist on debit cards: Visa Electron, V PAY, Maestro. */
private val DEBIT_AIDS = setOf("A0000000032010", "A0000000032020", "A0000000043060")

/** An application label is meant to be 1-16 printable characters; anything else from the card is dropped. */
private fun printable(bytes: ByteArray): String =
    String(bytes, StandardCharsets.ISO_8859_1).filter { it in ' '..'~' }.trim()

private const val TAG_APPLICATION = 0x61
private const val TAG_AID = 0x4F
private const val TAG_LABEL = 0x50
private const val TAG_PRIORITY = 0x87
private const val TAG_DF_NAME = 0x84

internal class Tlv(val tag: Int, val value: ByteArray, val constructed: Boolean)

/** One level of BER-TLV, or null when the bytes are malformed. */
internal fun parseTlv(bytes: ByteArray): List<Tlv>? {
    val out = mutableListOf<Tlv>()
    var i = 0
    while (i < bytes.size) {
        val first = bytes[i++].toInt() and 0xFF
        // 00 and FF are padding between objects.
        if (first == 0x00 || first == 0xFF) continue
        var tag = first
        if ((first and 0x1F) == 0x1F) {
            var more: Boolean
            var count = 0
            do {
                if (i >= bytes.size || ++count > 3) return null
                val next = bytes[i++].toInt() and 0xFF
                tag = (tag shl 8) or next
                more = (next and 0x80) != 0
            } while (more)
        }
        if (i >= bytes.size) return null
        var length = bytes[i++].toInt() and 0xFF
        if ((length and 0x80) != 0) {
            val count = length and 0x7F
            if (count !in 1..3 || i + count > bytes.size) return null
            length = 0
            repeat(count) { length = (length shl 8) or (bytes[i++].toInt() and 0xFF) }
        }
        if (length < 0 || i + length > bytes.size) return null
        out += Tlv(tag, bytes.copyOfRange(i, i + length), constructed = (first and 0x20) != 0)
        i += length
    }
    return out
}

/** Visits every object in [bytes], descending into constructed ones. */
private fun walkTlv(bytes: ByteArray, depth: Int = 0, visit: (Tlv) -> Unit) {
    if (depth > 8) return
    for (tlv in parseTlv(bytes) ?: return) {
        visit(tlv)
        if (tlv.constructed && tlv.tag != TAG_APPLICATION) walkTlv(tlv.value, depth + 1, visit)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it.toInt() and 0xFF) }

/** Debug builds log what each tap was, so the reported technologies can be checked on real cards. Never the card data. */
internal fun logTagRead(techs: Set<String>, read: NfcTagRead) {
    if (!BuildConfig.DEBUG) return
    val what = when (read) {
        is NfcTagRead.NormPixPayload -> "NormPix card"
        is NfcTagRead.PaymentCard -> "payment card network=${read.info.networkName} kind=${read.info.kind} label=${read.info.label}"
        is NfcTagRead.Unsupported -> "unsupported ${read.tag}"
        NfcTagRead.Lost -> "lost"
    }
    Log.d("NormPixNfc", "tag techs=${techs.sorted()} -> $what")
}

/** Where to tap on this phone. */
sealed interface PhoneNfcArea {
    /**
     * The device maker reports the antenna position (Android 14+). Each spot is a fraction of the
     * phone's width and height, seen from the back (the side the card touches).
     */
    data class Reported(val spots: List<Spot>) : PhoneNfcArea

    /** Nothing reported: guidance covers the usual area instead of inventing a spot. */
    data object Unknown : PhoneNfcArea

    data class Spot(val x: Float, val y: Float)
}

/** The antenna position this phone reports, or [PhoneNfcArea.Unknown]. Android doesn't publish it before Android 14, and most makers don't fill it in. */
fun phoneNfcArea(adapter: NfcAdapter): PhoneNfcArea {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return PhoneNfcArea.Unknown
    val info = runCatching { adapter.nfcAntennaInfo }.getOrNull() ?: return PhoneNfcArea.Unknown
    // Deprecated in API 37 with no other way to tell named; it is still what a foldable reports.
    @Suppress("DEPRECATION")
    val foldable = info.isDeviceFoldable
    return nfcAreaFrom(
        widthMm = info.deviceWidth,
        heightMm = info.deviceHeight,
        foldable = foldable,
        antennas = info.availableNfcAntennas.map { it.locationX to it.locationY },
    )
}

/**
 * Maps what the platform reports to spots on the back. The platform measures from the top-left
 * with the screen facing the user, so seen from the back the x axis flips. Foldables measure the
 * unfolded device, which says nothing reliable about the folded phone in the user's hand, and
 * out-of-range or missing values mean the maker didn't fill it in: those stay [PhoneNfcArea.Unknown].
 */
internal fun nfcAreaFrom(widthMm: Int, heightMm: Int, foldable: Boolean, antennas: List<Pair<Int, Int>>): PhoneNfcArea {
    if (foldable || widthMm <= 0 || heightMm <= 0 || antennas.isEmpty()) return PhoneNfcArea.Unknown
    if (antennas.any { (x, y) -> x !in 0..widthMm || y !in 0..heightMm }) return PhoneNfcArea.Unknown
    return PhoneNfcArea.Reported(
        antennas.map { (x, y) -> PhoneNfcArea.Spot(x = 1f - x / widthMm.toFloat(), y = y / heightMm.toFloat()) },
    )
}
