package com.example.normpix.wallet

import com.example.normpix.testing.InMemoryVault
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The CVV is optional, and the card settings (Store cards, Save CVV) decide what the vault may keep: a card without a CVV
 * saves, reads back and travels in a cards file; with Save CVV off no CVV is kept whichever way a card arrives; with Store
 * cards off nothing is added and a cards file is refused.
 */
class CardsPolicyTest {
    private val at = LocalDateTime.of(2026, 10, 15, 18, 0)

    private fun card(id: String, number: String, cvv: String = "123") = StoredCard(
        id = id,
        bankName = "HDFC Bank",
        cardholderName = "K SAI",
        cardNumber = number,
        expiryMonth = "08",
        expiryYear = "29",
        cvv = cvv,
        kind = CardKind.Credit,
        createdAt = at,
        bankKey = "hdfc",
    )

    private val keepEverything = CardsPolicy(storeCards = true, keepCvv = true)
    private val noCvv = CardsPolicy(storeCards = true, keepCvv = false)
    private val noCards = CardsPolicy(storeCards = false, keepCvv = true)

    private fun controller(vault: InMemoryVault, rules: () -> CardsPolicy) = CardsController(vault, rules)

    // ----- the CVV is optional -----

    @Test
    fun `a card without a CVV says so, and a card with one says that`() {
        assertFalse(card("a", "4111111111111111", cvv = "").hasCvv)
        assertTrue(card("b", "4111111111111111").hasCvv)
    }

    @Test
    fun `a card with no CVV survives being written and read`() {
        val bare = card("a", "4111111111111111", cvv = "")
        val read = StoredCard.fromJson(bare.toJson())
        assertEquals(bare, read)
        assertFalse(read.hasCvv)
    }

    @Test
    fun `a card from before the CVV was optional, or saved by a build that left it out, still reads`() {
        val withoutField = JsonObject(card("a", "4111111111111111").toJson().filterKeys { it != "cvv" })
        assertEquals("", StoredCard.fromJson(withoutField).cvv)

        val numberInsteadOfText = JsonObject(card("a", "4111111111111111").toJson() + ("cvv" to JsonPrimitive(123)))
        assertEquals("", StoredCard.fromJson(numberInsteadOfText).cvv)

        // And an old card keeps the CVV it had.
        assertEquals("123", StoredCard.fromJson(card("a", "4111111111111111").toJson()).cvv)
    }

    @Test
    fun `a card printed for the log names no card data`() {
        assertEquals("StoredCard(id=a)", card("a", "4111111111111111", cvv = "").toString())
    }

    @Test
    fun `a cards file carries a card that has no CVV`() {
        val cards = listOf(card("a", "4111111111111111", cvv = ""), card("b", "5500005555555559"))
        val text = CardsTransfer.encode(cards, "correct horse".toCharArray(), exportedAt = at, iterations = 10_000)
        val opened = assertIs<CardsTransfer.Result.Cards>(CardsTransfer.decode(text, "correct horse".toCharArray()))
        assertEquals(cards, opened.cards)
        assertEquals(listOf(false, true), opened.cards.map { it.hasCvv })
    }

    @Test
    fun `adding and editing a card works with an empty CVV`() = runBlocking<Unit> {
        val vault = InMemoryVault()
        val cards = controller(vault) { keepEverything }
        assertTrue(cards.add(card("a", "4111111111111111", cvv = "")))
        assertEquals("", vault.cards.single().cvv)

        assertTrue(cards.update(vault.cards.single().copy(cardholderName = "A PERSON")))
        assertEquals("A PERSON", vault.cards.single().cardholderName)
        assertEquals("", vault.cards.single().cvv)

        // A CVV can be added later, and taken off again.
        assertTrue(cards.update(vault.cards.single().copy(cvv = "9876")))
        assertEquals("9876", vault.cards.single().cvv)
        assertTrue(cards.update(vault.cards.single().copy(cvv = "")))
        assertFalse(vault.cards.single().hasCvv)
    }

    // ----- Save CVV off -----

    @Test
    fun `the policy takes the CVV off a card and leaves the rest`() {
        val shaped = noCvv.shape(card("a", "4111111111111111"))
        assertEquals("", shaped.cvv)
        assertEquals(card("a", "4111111111111111", cvv = ""), shaped)
        // Nothing changes while CVVs are kept.
        assertEquals(card("a", "4111111111111111"), keepEverything.shape(card("a", "4111111111111111")))
    }

    @Test
    fun `with Save CVV off a card added, edited or restored is kept without a CVV`() = runBlocking<Unit> {
        val vault = InMemoryVault()
        val cards = controller(vault) { noCvv }

        assertTrue(cards.add(card("a", "4111111111111111")))
        assertEquals("", vault.cards.single().cvv)

        assertTrue(cards.update(card("a", "4111111111111111", cvv = "555").copy(bankName = "ICICI Bank")))
        assertEquals("ICICI Bank", vault.cards.single().bankName)
        assertEquals("", vault.cards.single().cvv)

        assertTrue(cards.delete("a"))
        assertTrue(cards.restore(card("a", "4111111111111111"), 0))
        assertEquals("", vault.cards.single().cvv)
    }

    @Test
    fun `turning Save CVV off deletes the CVV from every stored card and nothing else`() = runBlocking<Unit> {
        val vault = InMemoryVault(listOf(card("a", "4111111111111111"), card("b", "5500005555555559", cvv = ""), card("c", "378282246310005", cvv = "1234")))
        val cards = controller(vault) { keepEverything }
        cards.load()

        assertTrue(cards.removeCvvs())

        assertEquals(listOf("", "", ""), vault.cards.map { it.cvv })
        assertEquals(listOf("4111111111111111", "5500005555555559", "378282246310005"), vault.cards.map { it.cardNumber })
        assertEquals(vault.cards, cards.cards)
        assertTrue(vault.cards.all { it.expiryMonth == "08" && it.cardholderName == "K SAI" })
    }

    @Test
    fun `when the vault can't be written no CVV is removed and the list still shows what is stored`() = runBlocking<Unit> {
        val vault = InMemoryVault(listOf(card("a", "4111111111111111")))
        val cards = controller(vault) { keepEverything }
        cards.load()
        vault.failToSave = true

        assertFalse(cards.removeCvvs())

        assertEquals("123", vault.cards.single().cvv)
        assertEquals("123", cards.cards.single().cvv)
    }

    // ----- the import path -----

    @Test
    fun `importing drops the CVVs while Save CVV is off, and keeps them otherwise`() {
        val incoming = listOf(card("a", "4111111111111111"), card("b", "5500005555555559", cvv = ""))

        val without = CardsTransfer.merge(emptyList(), incoming, noCvv)
        assertFalse(without.refused)
        assertEquals(listOf("a", "b"), without.added.map { it.id })
        assertTrue(without.added.none { it.hasCvv })

        val with = CardsTransfer.merge(emptyList(), incoming, keepEverything)
        assertEquals(listOf(true, false), with.added.map { it.hasCvv })
    }

    @Test
    fun `importing is refused while Store cards is off`() {
        val merge = CardsTransfer.merge(emptyList(), listOf(card("a", "4111111111111111")), noCards)
        assertTrue(merge.refused)
        assertTrue(merge.added.isEmpty())
    }

    @Test
    fun `the controller's import honours both settings and touches nothing when refused`() = runBlocking<Unit> {
        val incoming = listOf(card("a", "4111111111111111"), card("b", "5500005555555559"))

        val refusedVault = InMemoryVault()
        val refusing = controller(refusedVault) { noCards }
        val refused = assertNotNull(refusing.addAll(incoming))
        assertTrue(refused.refused)
        assertTrue(refusedVault.cards.isEmpty())

        val vault = InMemoryVault()
        val stripping = controller(vault) { noCvv }
        val merge = assertNotNull(stripping.addAll(incoming))
        assertFalse(merge.refused)
        assertEquals(2, merge.added.size)
        assertTrue(vault.cards.none { it.hasCvv })
        assertEquals(listOf("4111111111111111", "5500005555555559"), vault.cards.map { it.cardNumber })
    }

    @Test
    fun `the policy is asked afresh at every write`() = runBlocking<Unit> {
        var rules = keepEverything
        val vault = InMemoryVault()
        val cards = controller(vault) { rules }

        assertTrue(cards.add(card("a", "4111111111111111")))
        assertEquals("123", vault.cards.single().cvv)

        rules = noCvv
        assertTrue(cards.add(card("b", "5500005555555559")))
        assertEquals(listOf("123", ""), vault.cards.map { it.cvv })

        rules = noCards
        assertFalse(cards.add(card("c", "378282246310005")))
        assertFalse(cards.restore(card("d", "6011111111111117"), 0))
        assertEquals(listOf("a", "b"), vault.cards.map { it.id })
    }

    // ----- Store cards off -----

    @Test
    fun `deleting every card empties the vault and the list`() = runBlocking<Unit> {
        val vault = InMemoryVault(listOf(card("a", "4111111111111111"), card("b", "5500005555555559")))
        val cards = controller(vault) { keepEverything }
        cards.load()

        assertTrue(cards.deleteAll())

        assertTrue(vault.cards.isEmpty())
        assertEquals(CardsState.Ready(emptyList()), cards.state.value)
    }

    @Test
    fun `a vault that can't be erased keeps its cards, and says so`() = runBlocking<Unit> {
        val vault = InMemoryVault(listOf(card("a", "4111111111111111")))
        val cards = controller(vault) { keepEverything }
        cards.load()
        vault.failToClear = true

        assertFalse(cards.deleteAll())

        assertEquals(listOf("a"), vault.cards.map { it.id })
        assertEquals(listOf("a"), cards.cards.map { it.id }, "the list shows what is really stored")
        assertNull((cards.state.value as? CardsState.Failed))
    }
}
