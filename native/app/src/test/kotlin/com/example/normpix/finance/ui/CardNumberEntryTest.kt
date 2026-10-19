package com.example.normpix.finance.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a card account's number field makes of what was typed: only the first six and last four digits ever come out of it,
 * so the whole number can't be kept further along by accident.
 */
class CardNumberEntryTest {
    @Test
    fun `nothing, or nothing but spaces and dashes, is nothing`() {
        assertEquals(CardNumberEntry.None, readCardNumber(""))
        assertEquals(CardNumberEntry.None, readCardNumber("  - "))
    }

    @Test
    fun `four digits are an ending`() {
        assertEquals(CardNumberEntry.Ending("4455"), readCardNumber("4455"))
        assertEquals(CardNumberEntry.Ending("0042"), readCardNumber(" 00-42 "))
    }

    @Test
    fun `a whole number comes out as its first six and last four digits and nothing between`() {
        val full = readCardNumber("4111 1111 1111 1111") as CardNumberEntry.Full
        assertEquals("411111", full.start)
        assertEquals("1111", full.ending)
        assertTrue(full.checksumOk)

        val longer = readCardNumber("6221260000000000123") as CardNumberEntry.Full
        assertEquals("622126", longer.start)
        assertEquals("0123", longer.ending)

        val shortest = readCardNumber("501800000009") as CardNumberEntry.Full
        assertEquals("501800", shortest.start)
        assertEquals("0009", shortest.ending)
    }

    @Test
    fun `the data class carries no middle digits to leak`() {
        val full = readCardNumber("4111111111111111") as CardNumberEntry.Full
        assertFalse("1111111111" in full.toString())
    }

    @Test
    fun `any other number of digits is unusable`() {
        for (typed in listOf("1", "12", "123", "12345", "12345678", "12345678901", "12345678901234567890")) {
            assertEquals(CardNumberEntry.Unusable, readCardNumber(typed), typed)
        }
    }

    @Test
    fun `the check catches a mistyped number and passes real ones`() {
        for (good in listOf("4111111111111111", "5500005555555559", "378282246310005", "6011111111111117")) assertTrue(luhnOk(good), good)
        for (bad in listOf("4111111111111112", "5500005555555558", "1234567812345678")) assertFalse(luhnOk(bad), bad)
        assertFalse(luhnOk(""))
        assertFalse(luhnOk("4"))
        assertFalse(luhnOk("41x1"))
        assertFalse((readCardNumber("4111111111111112") as CardNumberEntry.Full).checksumOk)
    }

    @Test
    fun `what is kept is drawn like a card, with the middle hidden`() {
        assertEquals("4111 11•• •••• 1111", keptNumberMask("411111", "1111"))
        assertEquals("•••• •••• •••• 1111", keptNumberMask(null, "1111"))
        assertEquals("•••• •••• •••• 4455", keptNumberMask("", "4455"))
    }

    @Test
    fun `the note under the field says what is kept and why`() {
        assertEquals("Only the first 6 and last 4 digits are kept, to recognise this card on receipts", CARD_NUMBER_KEPT_NOTE)
    }
}
