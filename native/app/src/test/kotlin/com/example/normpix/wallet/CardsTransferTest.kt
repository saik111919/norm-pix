package com.example.normpix.wallet

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A cards file: locked with a password, opened only by it, and adding cards without ever duplicating one. */
class CardsTransferTest {
    private val at = LocalDateTime.of(2026, 10, 15, 18, 0)

    private fun card(id: String, number: String, month: String = "08", year: String = "2029") = StoredCard(
        id = id,
        bankName = "HDFC Bank",
        cardholderName = "K SAI",
        cardNumber = number,
        expiryMonth = month,
        expiryYear = year,
        cvv = "123",
        kind = CardKind.Credit,
        createdAt = at,
        bankKey = "hdfc",
    )

    private val cards = listOf(card("a", "4111111111111111"), card("b", "5500005555555559", "01", "2030"))

    // The fewest rounds a file may have keeps the test quick; real files use CardsTransfer.ITERATIONS.
    private fun export(password: String = "correct horse") =
        CardsTransfer.encode(cards, password.toCharArray(), exportedAt = at, iterations = 10_000)

    @Test
    fun `the right password opens exactly the cards that were exported`() {
        val opened = assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(export(), "correct horse".toCharArray()))
        assertEquals(cards, opened.cards)
        assertEquals(at, opened.exportedAt)
    }

    @Test
    fun `the file shows nothing of the cards without the password`() {
        val text = export()
        assertTrue(CardsTransfer.isCardFile(text))
        for (secret in listOf("4111111111111111", "5500005555555559", "K SAI", "HDFC Bank", "cardNumber", "cvv\"")) assertFalse(secret in text, secret)
    }

    @Test
    fun `a wrong password or a changed file opens nothing`() {
        assertEquals(CardsTransfer.Result.WrongPassword, CardsTransfer.decode(export(), "wrong horse".toCharArray()))
        // Changing the rounds a file says it was made with doesn't get past the lock either.
        val fields = (Json.parseToJsonElement(export()) as JsonObject).toMutableMap()
        fields["iterations"] = JsonPrimitive(20_000)
        val tampered = JsonObject(fields).toString()
        assertEquals(CardsTransfer.Result.WrongPassword, CardsTransfer.decode(tampered, "correct horse".toCharArray()))
    }

    @Test
    fun `other files are told apart from card files`() {
        assertFalse(CardsTransfer.isCardFile("""{"format":"normpix-backup","version":1}"""))
        assertFalse(CardsTransfer.isCardFile("not json"))
        assertIs<CardsTransfer.Result.Invalid>(CardsTransfer.decode("""{"format":"normpix-cards","version":99}""", "x".toCharArray()))
    }

    @Test
    fun `importing adds new cards and skips ones already saved`() {
        val saved = listOf(card("a", "4111111111111111"), card("z", "5500 0055 5555 5559", "1", "30"))
        val merge = CardsTransfer.merge(saved, cards + card("c", "378282246310005"))
        // "a" by id; "b" is "z" again (same number and expiry, written differently).
        assertEquals(listOf("c"), merge.added.map { it.id })
        assertEquals(2, merge.alreadyThere)
        assertEquals(listOf("a", "z", "c"), merge.applyTo(saved).map { it.id })
    }
}
