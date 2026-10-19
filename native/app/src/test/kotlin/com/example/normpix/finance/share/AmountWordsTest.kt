package com.example.normpix.finance.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The amount a receipt prints in words ("Rupees Seventy Four Only"), read as a number. */
class AmountWordsTest {
    @Test
    fun `rupees in words are read as an amount in paise`() {
        assertEquals(7_400L, AmountWords.parse("Rupees Seventy Four Only"))
        assertEquals(7_400L, AmountWords.parse("Seventy Four Rupees Only"))
        assertEquals(10_000L, AmountWords.parse("Rupees One Hundred Only"))
        assertEquals(15_000L, AmountWords.parse("Rupees One Hundred Fifty Only"))
        assertEquals(15_000L, AmountWords.parse("One Hundred and Fifty Rupees Only"))
        assertEquals(500L, AmountWords.parse("Five Rupees Only"))
        assertEquals(1_900L, AmountWords.parse("Rupees Nineteen Only"))
    }

    @Test
    fun `lakh and crore count the Indian way`() {
        assertEquals(12_500_050L, AmountWords.parse("One Lakh Twenty Five Thousand Rupees and Fifty Paise"))
        assertEquals(12_500_000L, AmountWords.parse("Rupees One Lakh Twenty Five Thousand Only"))
        assertEquals(12_500_000L, AmountWords.parse("Rupees One Lac Twenty Five Thousand Only"))
        assertEquals(3_000_000_000L, AmountWords.parse("Rupees Three Crore Only"))
        assertEquals(10_005_000_000L, AmountWords.parse("Rupees Ten Crore Fifty Thousand Only"))
        assertEquals(520_000L, AmountWords.parse("Rupees Five Thousand Two Hundred Only"))
        assertEquals(1_550_000L, AmountWords.parse("Rupees Fifteen Thousand Five Hundred Only"))
    }

    @Test
    fun `paise are the number before the word`() {
        assertEquals(12_550L, AmountWords.parse("Rupees One Hundred Twenty Five and Fifty Paise Only"))
        assertEquals(50L, AmountWords.parse("Fifty Paise Only"))
        assertEquals(10_001L, AmountWords.parse("Rupees One Hundred and One Paise Only"))
        // More than ninety nine paise is not paise.
        assertNull(AmountWords.parse("Rupees Ten and One Hundred Paise Only"))
    }

    @Test
    fun `hyphens, case and a letter the recognizer got wrong do not matter`() {
        assertEquals(7_400L, AmountWords.parse("RUPEES SEVENTY-FOUR ONLY"))
        assertEquals(7_400L, AmountWords.parse("rupees seventy four only"))
        assertEquals(7_400L, AmountWords.parse("Rupees Sevnty Four Only"))
        assertEquals(12_500_000L, AmountWords.parse("Rupees One Lakh Twenty Five Thosand Only"))
        assertEquals(7_400L, AmountWords.parse("Rupees SeventyFour Only"))
        assertEquals(4_000L, AmountWords.parse("Rupees Fourty Only"))
    }

    @Test
    fun `a line that is not only an amount in words is not one`() {
        assertNull(AmountWords.parse("Paid to Seventy Four Stores"))
        assertNull(AmountWords.parse("Rupees Only"))
        assertNull(AmountWords.parse("Only"))
        assertNull(AmountWords.parse("Seventy Four"))
        assertNull(AmountWords.parse("UPI Ref No"))
        assertNull(AmountWords.parse("Rupees Zero Only"))
        assertNull(AmountWords.parse(""))
        // A word one letter off a number word is put right only when it has no near neighbour: "Fine" is not "Five".
        assertNull(AmountWords.parse("Rupees Fine Only"))
    }

    @Test
    fun `an amount in words is found among a receipt's lines`() {
        val lines = listOf("Paid Successfully", "₹74", "Rupees Seventy Four Only", "Auto", "To: Mantu Yadav")
        assertEquals(7_400L, AmountWords.find(lines))
        assertNull(AmountWords.find(listOf("Paid Successfully", "₹74", "Auto")))
        // Whole lines of figures are not words.
        assertNull(AmountWords.find(listOf("Rupees 74 Only")))
    }

    @Test
    fun `a long amount wrapped over lines is put together`() {
        assertEquals(
            12_550_000L,
            AmountWords.find(listOf("₹1,25,500", "Rupees One Lakh Twenty Five Thousand", "Five Hundred Only", "To: Asha")),
        )
        assertEquals(
            12_500_000L,
            AmountWords.find(listOf("Rupees One Lakh Twenty Five", "Thousand Only")),
        )
        // A line of other words ends the run.
        assertEquals(
            7_400L,
            AmountWords.find(listOf("Rupees Seventy Four Only", "Auto", "Rupees Eighty Only")),
        )
    }

    @Test
    fun `an amount of a currency with no decimals is whole units`() {
        assertEquals(74L, AmountWords.parse("Rupees Seventy Four Only", decimals = 0))
        assertNull(AmountWords.parse("Rupees Seventy Four and Fifty Paise Only", decimals = 0))
        assertEquals(74_000L, AmountWords.parse("Rupees Seventy Four Only", decimals = 3))
    }
}
