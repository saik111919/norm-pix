package com.example.normpix.wallet

import com.example.normpix.finance.data.parseDateTime
import java.time.LocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A credit or debit card kind, used to badge the card and drive some display choices. */
enum class CardKind(val key: String, val label: String) {
    Credit("credit", "Credit"),
    Debit("debit", "Debit"),
}

/**
 * A single stored card. Every field here is written to disk only inside the
 * card vault's AES-256-GCM ciphertext - this class itself holds plaintext in
 * memory only, for the duration it's actually displayed or edited.
 */
data class StoredCard(
    val id: String,
    val bankName: String,
    val cardholderName: String,
    val cardNumber: String,
    val expiryMonth: String,
    val expiryYear: String,
    /** The 3-4 digit security code, or empty when none is kept: it is optional, and Settings > Wallet & Cards can turn keeping it off. */
    val cvv: String,
    val kind: CardKind,
    val createdAt: LocalDateTime,
    /**
     * The bank this card belongs to (see `BankCatalog`), the same bank an account can name. [bankName] stays as the name
     * the card shows; cards saved before banks were shared have no key until they are linked.
     */
    val bankKey: String? = null,
) {
    val last4: String get() = if (cardNumber.length >= 4) cardNumber.takeLast(4) else cardNumber

    /** Whether a CVV is kept for this card; everything that shows or copies one checks this first. */
    val hasCvv: Boolean get() = cvv.isNotEmpty()

    // Deliberately not the data-class default: never print card data to logs.
    override fun toString(): String = "StoredCard(id=$id)"

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("bankName", bankName)
        put("cardholderName", cardholderName)
        put("cardNumber", cardNumber)
        put("expiryMonth", expiryMonth)
        put("expiryYear", expiryYear)
        put("cvv", cvv)
        put("kind", kind.key)
        put("createdAt", createdAt.toString())
        bankKey?.let { put("bankKey", it) }
    }

    companion object {
        fun fromJson(json: JsonObject): StoredCard {
            fun text(key: String) = (json[key] as? JsonPrimitive)?.content ?: error("card field '$key' missing")
            return StoredCard(
                id = text("id"),
                bankName = text("bankName"),
                cardholderName = text("cardholderName"),
                cardNumber = text("cardNumber"),
                expiryMonth = text("expiryMonth"),
                expiryYear = text("expiryYear"),
                // Optional: a card saved without one, or a file from a build that left it out, has none.
                cvv = (json["cvv"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty(),
                kind = CardKind.entries.firstOrNull { it.key == text("kind") } ?: CardKind.Credit,
                createdAt = parseDateTime(text("createdAt")) ?: LocalDateTime.now(),
                // Absent in vaults written before banks were shared.
                bankKey = (json["bankKey"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() },
            )
        }
    }
}
