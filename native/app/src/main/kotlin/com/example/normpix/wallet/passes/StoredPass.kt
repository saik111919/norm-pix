package com.example.normpix.wallet.passes

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 * Loyalty, membership and gift cards in the Wallet (owner, 2026-11-19, from the suggestions list: "Loyalty, membership and gift cards in
 * the Wallet. Scan the barcode once, show it at the counter, stored in the encrypted vault"). A pass is what such a card is for the
 * app: a name, its number and how that number is drawn as a code. It lives only in its own encrypted vault file (`FilePassVault`), never
 * in the finance database or a plain backup; it moves to another phone in the password-locked cards file, with the cards.
 */

/** What a pass is, as the user picks it. The keys are stored, so they never change. */
enum class PassKind(val key: String, val label: String) {
    Loyalty("loyalty", "Loyalty card"),
    Membership("membership", "Membership"),
    GiftCard("gift", "Gift card"),
    Other("other", "Other"),
    ;

    companion object {
        fun of(key: String?): PassKind = entries.firstOrNull { it.key == key } ?: Other
    }
}

/**
 * How a pass's number is drawn at the counter, as the shop's scanner reads it. The keys are stored, so they never change; [None] keeps
 * only the number (a card the shop types in).
 */
enum class PassCodeFormat(val key: String, val label: String, val twoDimensional: Boolean) {
    Code128("code128", "Code 128", false),
    Qr("qr", "QR code", true),
    Ean13("ean13", "EAN-13", false),
    Ean8("ean8", "EAN-8", false),
    UpcA("upca", "UPC-A", false),
    UpcE("upce", "UPC-E", false),
    Code39("code39", "Code 39", false),
    Code93("code93", "Code 93", false),
    Codabar("codabar", "Codabar", false),
    Itf("itf", "ITF", false),
    Pdf417("pdf417", "PDF417", true),
    Aztec("aztec", "Aztec", true),
    DataMatrix("datamatrix", "Data Matrix", true),
    None("none", "Number only", false),
    ;

    companion object {
        fun of(key: String?): PassCodeFormat = entries.firstOrNull { it.key == key } ?: Code128
    }
}

/**
 * One loyalty, membership or gift card. [number] is what the code holds (and what is typed when a shop asks); [colorIndex] is its tile's
 * colour, from the app's palette. A gift card may keep its [pin] and the [balance] left on it (in the currency's smallest unit). [expiry]
 * is the day it runs out, if it says. Its [toString] says nothing of the number or the PIN, so a log line can never carry them.
 */
@Immutable
data class StoredPass(
    val id: String = UUID.randomUUID().toString(),
    val kind: PassKind,
    val name: String,
    val number: String,
    val format: PassCodeFormat,
    val colorIndex: Int = 0,
    val pin: String = "",
    val balance: Long? = null,
    val expiry: LocalDate? = null,
    val note: String = "",
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    val hasPin: Boolean get() = pin.isNotEmpty()

    /** The last four characters of the number, for a tile and a screen reader: never the whole of it. */
    val ending: String get() = number.filter { !it.isWhitespace() }.takeLast(4)

    override fun toString(): String = "StoredPass(id=$id, kind=$kind, name=$name, format=$format)"

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("kind", kind.key)
        put("name", name)
        put("number", number)
        put("format", format.key)
        put("color", colorIndex)
        if (pin.isNotEmpty()) put("pin", pin)
        balance?.let { put("balance", it) }
        expiry?.let { put("expiry", it.toString()) }
        if (note.isNotEmpty()) put("note", note)
        put("createdAt", createdAt.toString())
        put("updatedAt", updatedAt.toString())
    }

    companion object {
        /** The longest name and note a pass keeps, and the longest number a code is asked to hold. */
        const val MAX_NAME = 40
        const val MAX_NOTE = 200
        const val MAX_NUMBER = 300

        /** A pass as the vault or a cards file writes it. Keys a newer app adds are ignored; a missing optional one is none. */
        fun fromJson(o: JsonObject): StoredPass {
            fun s(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
            val now = LocalDateTime.now()
            return StoredPass(
                id = s("id") ?: UUID.randomUUID().toString(),
                kind = PassKind.of(s("kind")),
                name = s("name").orEmpty(),
                number = s("number").orEmpty(),
                format = PassCodeFormat.of(s("format")),
                colorIndex = (o["color"] as? JsonPrimitive)?.intOrNull ?: 0,
                pin = s("pin").orEmpty(),
                balance = (o["balance"] as? JsonPrimitive)?.longOrNull,
                expiry = s("expiry")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                note = s("note").orEmpty(),
                createdAt = s("createdAt")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: now,
                updatedAt = s("updatedAt")?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: now,
            )
        }
    }
}

/** What is wrong with a pass, in the words the form says it; null when it can be kept. [passCodeFault] adds whether its code can be drawn. */
fun StoredPass.fault(): String? = when {
    name.isBlank() -> "Give the card a name, like the shop's."
    name.trim().length > StoredPass.MAX_NAME -> "Keep the name to ${StoredPass.MAX_NAME} characters."
    number.isBlank() -> "Enter the card's number, or scan its code."
    number.length > StoredPass.MAX_NUMBER -> "That number is longer than a card's code can hold."
    note.length > StoredPass.MAX_NOTE -> "Keep the note to ${StoredPass.MAX_NOTE} characters."
    (balance ?: 0L) < 0 -> "The balance can't be below zero."
    else -> null
}

/**
 * Adds [incoming] to [existing] without replacing or doubling one: a pass already kept - the same id, or the same name and number - is
 * left as it is and counted. Gives the passes to add and how many were there already.
 */
fun mergePasses(existing: List<StoredPass>, incoming: List<StoredPass>): Pair<List<StoredPass>, Int> {
    val ids = existing.mapTo(HashSet()) { it.id }
    val keys = existing.mapTo(HashSet()) { samePassKey(it) }
    val added = mutableListOf<StoredPass>()
    var already = 0
    for (pass in incoming) {
        val key = samePassKey(pass)
        if (pass.id in ids || key in keys) {
            already++
        } else {
            added += pass
            ids += pass.id
            keys += key
        }
    }
    return added to already
}

private fun samePassKey(pass: StoredPass) = pass.name.trim().lowercase() + "|" + pass.number.filter { !it.isWhitespace() }
