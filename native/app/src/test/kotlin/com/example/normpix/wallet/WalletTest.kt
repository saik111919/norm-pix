package com.example.normpix.wallet

import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private fun card(id: String, number: String = "4111111111111111", bank: String = "HDFC Bank") = StoredCard(
    id = id,
    bankName = bank,
    cardholderName = "ASHA RAO",
    cardNumber = number,
    expiryMonth = "08",
    expiryYear = "29",
    cvv = "123",
    kind = CardKind.Credit,
    createdAt = LocalDateTime.of(2026, 10, 1, 10, 0),
)

class CardTextParserTest {
    @Test
    fun readsNumberExpiryAndName() {
        val text = """
            HDFC BANK
            4111 1111 1111 1111
            VALID THRU 08/29
            ASHA RAO
            VISA
        """.trimIndent()
        val scanned = CardTextParser.parse(text)
        assertEquals("4111111111111111", scanned.cardNumber)
        assertEquals("08", scanned.expiryMonth)
        assertEquals("29", scanned.expiryYear)
        assertEquals("ASHA RAO", scanned.cardholderName)
    }

    @Test
    fun fourDigitYearsAreShortened() {
        assertEquals("31", CardTextParser.parse("EXPIRES 12/2031").expiryYear)
    }

    @Test
    fun bankAndNetworkLinesAreNotMistakenForTheName() {
        val scanned = CardTextParser.parse("STATE BANK\nDEBIT CARD\nRUPAY PLATINUM")
        assertNull(scanned.cardholderName)
    }

    @Test
    fun nothingRecognizableIsEmpty() {
        assertTrue(CardTextParser.parse("hello world").isEmpty)
    }

    @Test
    fun scanResultsNeverPrintCardData() {
        val scanned = CardTextParser.parse("4111 1111 1111 1111\nASHA RAO")
        assertFalse(scanned.toString().contains("4111"))
        assertFalse(card("c1").toString().contains("4111"))
        assertFalse(card("c1").toString().contains("123"))
    }
}

class CardRulesTest {
    @Test
    fun networksFromIin() {
        assertEquals(CardNetwork.Visa, CardNetwork.fromNumber("4111111111111111"))
        assertEquals(CardNetwork.Mastercard, CardNetwork.fromNumber("5500000000000004"))
        assertEquals(CardNetwork.Mastercard, CardNetwork.fromNumber("2221000000000009"))
        assertEquals(CardNetwork.Amex, CardNetwork.fromNumber("371449635398431"))
        assertEquals(CardNetwork.Rupay, CardNetwork.fromNumber("6521000000000000"))
        assertEquals(CardNetwork.Rupay, CardNetwork.fromNumber("6070000000000000"))
        assertEquals(CardNetwork.Discover, CardNetwork.fromNumber("6011000000000004"))
        assertEquals(CardNetwork.DinersClub, CardNetwork.fromNumber("30569309025904"))
        assertEquals(CardNetwork.Unknown, CardNetwork.fromNumber(""))
    }

    @Test
    fun indianBanksRequireSixteenDigits() {
        assertEquals(16, IndianBankCardLength.digitsFor("Union Bank of India"))
        assertEquals(16, IndianBankCardLength.digitsFor("  bank of maharashtra "))
        assertNull(IndianBankCardLength.digitsFor("Chase"))
    }
}

class FileCardVaultTest {
    private val dir: File = Files.createTempDirectory("vault").toFile()
    private val file = File(dir, "card_vault.bin")
    private val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val vault = FileCardVault(file) { key }

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun missingFileIsAnEmptyVault() = runBlocking {
        assertEquals(emptyList(), vault.loadCards())
    }

    @Test
    fun roundTripsAndNeverStoresPlaintext() = runBlocking {
        val cards = listOf(card("a"), card("b", number = "5500000000000004", bank = "Bank of Maharashtra"))
        vault.saveCards(cards)
        assertEquals(cards, vault.loadCards())
        val raw = file.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(raw.contains("4111111111111111"))
        assertFalse(raw.contains("ASHA"))
        assertFalse(File(dir, "card_vault.bin.tmp").exists(), "the temporary file is renamed into place")
    }

    @Test
    fun eachSaveUsesAFreshIv() = runBlocking {
        vault.saveCards(listOf(card("a")))
        val first = file.readBytes().copyOfRange(0, 12)
        vault.saveCards(listOf(card("a")))
        val second = file.readBytes().copyOfRange(0, 12)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun tamperingIsDetected() = runBlocking {
        vault.saveCards(listOf(card("a")))
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertFailsWith<CardVaultException> { vault.loadCards() }
        Unit
    }

    @Test
    fun aDifferentKeyCannotRead() = runBlocking {
        vault.saveCards(listOf(card("a")))
        val other = FileCardVault(file) { SecretKeySpec(ByteArray(32) { 7 }, "AES") }
        assertFailsWith<CardVaultException> { other.loadCards() }
        Unit
    }
}

/** A vault whose reads and writes can be made to fail. */
private class FakeVault(var stored: List<StoredCard>) : CardVault {
    var readable = true
    var writable = true
    var writes = 0

    override suspend fun loadCards(): List<StoredCard> {
        if (!readable) throw CardVaultException("unreadable")
        return stored
    }

    override suspend fun saveCards(cards: List<StoredCard>) {
        if (!writable) throw CardVaultException("unwritable")
        writes++
        stored = cards
    }
}

class CardsControllerTest {
    @Test
    fun addingBeforeTheListLoadedKeepsExistingCards() = runBlocking {
        // e.g. "Add card" from the reminder form before Wallet was ever opened.
        val vault = FakeVault(listOf(card("a"), card("b")))
        val controller = CardsController(vault)
        assertTrue(controller.add(card("c")))
        assertEquals(listOf("a", "b", "c"), vault.stored.map { it.id })
    }

    @Test
    fun nothingIsWrittenOverAnUnreadableVault() = runBlocking {
        val vault = FakeVault(listOf(card("a"))).apply { readable = false }
        val controller = CardsController(vault)
        controller.load()
        assertFalse(controller.add(card("new")))
        assertFalse(controller.delete("a"))
        assertEquals(0, vault.writes)
        assertEquals(listOf("a"), vault.stored.map { it.id })
    }

    @Test
    fun aFailedWriteShowsWhatIsActuallyStored() = runBlocking {
        val vault = FakeVault(listOf(card("a")))
        val controller = CardsController(vault)
        controller.load()
        vault.writable = false
        assertFalse(controller.add(card("b")))
        assertEquals(listOf("a"), controller.cards.map { it.id })
    }

    @Test
    fun updateAndDelete() = runBlocking {
        val vault = FakeVault(listOf(card("a"), card("b")))
        val controller = CardsController(vault)
        controller.load()
        assertTrue(controller.update(card("a", bank = "Union Bank of India")))
        assertTrue(controller.delete("b"))
        assertEquals(listOf("Union Bank of India"), vault.stored.map { it.bankName })
    }
}
