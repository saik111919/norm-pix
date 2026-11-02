package com.example.normpix.wallet

import com.example.normpix.banks.BankCatalog
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A card's own name: kept with it, harmless to older files, suggested from the bank's real products, and kept to what fits the face. */
class CardNamesTest {
    private val at = LocalDateTime.of(2026, 10, 31, 12, 0)

    private fun card(cardName: String = "") = StoredCard(
        id = "a", bankName = "HDFC Bank", cardholderName = "K SAI", cardNumber = "4111111111111111", expiryMonth = "08", expiryYear = "29",
        cvv = "", kind = CardKind.Credit, createdAt = at, bankKey = "hdfc", cardName = cardName,
    )

    @Test
    fun `a card name is kept in the card's file and read back`() {
        val named = card("Regalia Gold")
        assertEquals("Regalia Gold", StoredCard.fromJson(named.toJson()).cardName)
        assertEquals(named, StoredCard.fromJson(named.toJson()))
    }

    @Test
    fun `a card with no name writes nothing new, and a file from before names reads as having none`() {
        assertFalse("cardName" in card().toJson(), "an unnamed card's file is exactly what it was")
        val old = JsonObject(card("X").toJson().filterKeys { it != "cardName" })
        assertEquals("", StoredCard.fromJson(old).cardName)
        // A name of the wrong kind is ignored rather than failing the whole card.
        val odd = JsonObject(card().toJson() + ("cardName" to JsonPrimitive(7)))
        assertEquals("", StoredCard.fromJson(odd).cardName)
    }

    @Test
    fun `the name travels in an exported cards file`() {
        val file = CardsTransfer.encode(listOf(card("Magnus")), "pw".toCharArray(), exportedAt = at, iterations = 10_000)
        val opened = assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(file, "pw".toCharArray()))
        assertEquals("Magnus", opened.cards.single().cardName)
        assertFalse("Magnus" in file, "and it is as locked as the rest")
        // The file as an older build wrote it still opens.
        assertTrue(Json.parseToJsonElement(file) is JsonObject)
    }

    @Test
    fun `suggestions are offered only for banks that have a list, and only for built-in bank ids`() {
        assertTrue("Regalia Gold" in CardNames.suggestionsFor("hdfc"))
        assertTrue("Magnus" in CardNames.suggestionsFor("axis"))
        assertEquals(emptyList(), CardNames.suggestionsFor("jana"))
        assertEquals(emptyList(), CardNames.suggestionsFor(null))
        assertEquals(emptyList(), CardNames.suggestionsFor("custom-123"))
    }

    @Test
    fun `every list belongs to a real bank, has no repeat and nothing too long for the card`() {
        val ids = BankCatalog.BuiltIn.all.map { it.key }.toSet()
        for (bank in CardNames.banksWithNames) {
            assertTrue(bank in ids, "$bank is a bank in the directory")
            val names = CardNames.suggestionsFor(bank)
            assertTrue(names.isNotEmpty())
            assertEquals(names.size, names.map { it.lowercase() }.toSet().size, "no name twice for $bank")
            for (name in names) assertTrue(name.length <= CardNames.MAX_LENGTH, "$bank: $name fits the face")
        }
    }

    @Test
    fun `a typed name is tidied and kept to what fits`() {
        assertEquals("Regalia Gold", CardNames.clean("  Regalia    Gold "))
        assertEquals("", CardNames.clean("   "))
        val long = "X".repeat(50)
        assertEquals(CardNames.MAX_LENGTH, CardNames.clean(long).length)
        assertEquals("Air+", CardNames.clean("Air+"))
    }
}
