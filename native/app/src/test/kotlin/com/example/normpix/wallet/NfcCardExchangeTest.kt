package com.example.normpix.wallet

import com.example.normpix.wallet.nfc.parseCardFromNfcPayload
import com.example.normpix.wallet.nfc.sameCardAs
import com.example.normpix.wallet.nfc.toNfcExchangePayload
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What one phone sends (Outlet) is exactly what the other turns into a card (Inlet); anything else is refused. */
class NfcCardExchangeTest {
    private val card = StoredCard(
        id = "sender-id",
        bankName = "HDFC Bank",
        cardholderName = "Asha Rao",
        cardNumber = "4111111111111111",
        expiryMonth = "09",
        expiryYear = "29",
        cvv = "123",
        kind = CardKind.Debit,
        createdAt = LocalDateTime.of(2026, 1, 1, 9, 0),
    )

    @Test
    fun outletPayload_becomesTheSameCardOnTheInletSide() {
        val received = assertNotNull(parseCardFromNfcPayload(card.toNfcExchangePayload()))

        assertEquals(card.bankName, received.bankName)
        assertEquals(card.cardholderName, received.cardholderName)
        assertEquals(card.cardNumber, received.cardNumber)
        assertEquals(card.expiryMonth, received.expiryMonth)
        assertEquals(card.expiryYear, received.expiryYear)
        assertEquals(card.cvv, received.cvv)
        assertEquals(card.kind, received.kind)
        // It is a new card in the receiver's wallet, not the sender's record.
        assertNotEquals(card.id, received.id)
        assertTrue(received.sameCardAs(card))
    }

    @Test
    fun exchangeWorksBothWays() {
        val other = card.copy(id = "b", bankName = "ICICI Bank", cardNumber = "5500005555555559", kind = CardKind.Credit)
        val atB = assertNotNull(parseCardFromNfcPayload(card.toNfcExchangePayload()))
        val atA = assertNotNull(parseCardFromNfcPayload(other.toNfcExchangePayload()))
        assertTrue(atB.sameCardAs(card))
        assertTrue(atA.sameCardAs(other))
        assertFalse(atA.sameCardAs(atB))
    }

    @Test
    fun anNdefTextRecord_withItsStatusByteAndLanguageCode_isStillRead() {
        // RTD_TEXT payloads start with a status byte and a language code, then the text.
        val received = parseCardFromNfcPayload("\u0002en" + card.toNfcExchangePayload())
        assertNotNull(received)
        assertEquals(card.cardNumber, received.cardNumber)
    }

    @Test
    fun foreignOrBrokenPayloads_areRefused() {
        val valid = card.toNfcExchangePayload()
        val refused = listOf(
            "",
            "hello",
            "{",
            "{}",
            valid.replace("normpix_card", "something_else"),
            valid.replace("4111111111111111", ""),
            valid.replace("4111111111111111", "4111-ABCD-1111-1111"),
            valid.replace("4111111111111111", "4111"),
            valid.replace("4111111111111111", "41111111111111111111111"),
            valid.dropLast(6),
        )
        for (payload in refused) assertNull(parseCardFromNfcPayload(payload), "should refuse: $payload")
    }

    @Test
    fun oversizedText_isCapped_andGroupedNumbersAreAccepted() {
        val long = "x".repeat(500)
        val received = assertNotNull(
            parseCardFromNfcPayload(card.copy(bankName = long, cardholderName = long, cardNumber = "4111 1111 1111 1111").toNfcExchangePayload()),
        )
        assertEquals(64, received.bankName.length)
        assertEquals(64, received.cardholderName.length)
        assertEquals("4111 1111 1111 1111", received.cardNumber)
    }

    @Test
    fun sameCard_ignoresGroupingAndExpiryFormat_butNotTheNumber() {
        assertTrue(card.sameCardAs(card.copy(id = "x", cardNumber = "4111 1111 1111 1111")))
        assertTrue(card.sameCardAs(card.copy(id = "x", expiryMonth = "9", expiryYear = "2029")))
        assertFalse(card.sameCardAs(card.copy(cardNumber = "4111111111111112")))
        assertFalse(card.sameCardAs(card.copy(expiryYear = "30")))
    }
}
