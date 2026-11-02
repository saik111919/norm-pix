package com.example.normpix.wallet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A card that was given the name of a known product is drawn in that product's colours, as legibly as every other card. */
class CardLooksTest {
    private fun card(bank: String, bankKey: String?, name: String) = StoredCard(
        id = "c", bankName = bank, cardholderName = "K SAI", cardNumber = "4111111111111111", expiryMonth = "08", expiryYear = "29",
        cvv = "", kind = CardKind.Credit, createdAt = LocalDateTime.of(2026, 10, 31, 0, 0), bankKey = bankKey, cardName = name,
    )

    private fun ratio(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance().toDouble() + 0.05
        val b = bg.luminance().toDouble() + 0.05
        return max(a, b) / min(a, b)
    }

    @Test
    fun `every look is a product the card name suggestions list, for a bank that has them`() {
        for ((bank, name) in CardLooks.products) {
            assertTrue(CardNames.suggestionsFor(bank).any { CardLooks.key(it) == CardLooks.key(name) }, "$bank / $name is suggested")
        }
        assertEquals(CardLooks.products.size, CardLooks.products.map { it.first to CardLooks.key(it.second) }.toSet().size, "none twice")
    }

    @Test
    fun `white print, the captions and the kind pill are readable on every colour of every look`() {
        for ((bank, name) in CardLooks.products) {
            val theme = BankCardDesign.forCard(card("x", bank, name))
            val drawn = theme.gradientColors + (theme.gradient as GradientSpec.Linear).colors
            for (stop in drawn) {
                assertTrue(ratio(Color.White, stop) >= 4.5, "$bank / $name: white on #%08X is %.2f".format(stop.value.toLong().toInt(), ratio(Color.White, stop)))
                assertTrue(ratio(CardCaptionColor, stop) >= 4.5, "$bank / $name: captions on #%08X".format(stop.value.toLong().toInt()))
                val behind = theme.badgeColor.compositeOver(CardBadgeUnderlay.compositeOver(stop))
                assertTrue(ratio(theme.badgeTextColor, behind) >= 4.5, "$bank / $name: the kind pill on #%08X".format(stop.value.toLong().toInt()))
            }
        }
    }

    @Test
    fun `a colour is darkened only as far as white print needs, and keeps its hue`() {
        val cyan = Color(0xFF05AAE6)
        assertTrue(ratio(Color.White, cyan) < 4.5, "the picture's cyan is too bright for white print")
        val fixed = CardLooks.readableOnWhite(cyan)
        assertTrue(CardLooks.contrastWithWhite(fixed) >= 4.5)
        assertTrue(fixed.blue > fixed.red && fixed.green > fixed.red, "still a blue-green: $fixed")
        val black = Color.Black
        // Already readable: untouched.
        assertEquals(black, CardLooks.readableOnWhite(black))
    }

    @Test
    fun `a known product gets its own look and the product name as its tier`() {
        val theme = BankCardDesign.forCard(card("HDFC Bank", "hdfc", "Regalia Gold"))
        val bank = BankCardDesign.themeFor("HDFC Bank")
        assertEquals("REGALIA GOLD", theme.tierName.uppercase())
        assertTrue(theme.gradientColors != bank.gradientColors, "its own colours, not HDFC's general ones")
        assertEquals(bank.logo, theme.logo, "the bank's mark stays")
        assertEquals(bank.chipFinish, theme.chipFinish)
    }

    @Test
    fun `the name is matched however it is typed, and only for its own bank`() {
        assertNotNull(CardLooks.forProduct("axis", "magnus"))
        assertNotNull(CardLooks.forProduct("axis", "  MAGNUS "))
        assertNotNull(CardLooks.forProduct("hdfc", "moneyback plus"))
        assertNotNull(CardLooks.forProduct("hdfc", "MoneyBack+"))
        assertNotNull(CardLooks.forProduct("kotak", "811 DreamDifferent"))
        assertNull(CardLooks.forProduct("hdfc", "Magnus"), "Magnus is Axis's")
        assertNull(CardLooks.forProduct("jana", "Magnus"))
        assertNull(CardLooks.forProduct(null, "Magnus"))
        assertNull(CardLooks.forProduct("axis", ""))
        assertNull(CardLooks.forProduct("axis", "Something I made up"))
    }

    @Test
    fun `a card with no name, an unknown one or a light product keeps its bank's face`() {
        val bank = BankCardDesign.themeFor("HDFC Bank")
        assertSame(bank.id, BankCardDesign.forCard(card("HDFC Bank", "hdfc", "")).id)
        assertEquals(bank.gradientColors, BankCardDesign.forCard(card("HDFC Bank", "hdfc", "Nothing of the sort")).gradientColors)
        // Kotak White is a white card: white print would not read on it, so it has no look.
        assertEquals(BankCardDesign.themeFor("Kotak").gradientColors, BankCardDesign.forCard(card("Kotak", "kotak", "White")).gradientColors)
        // The bank is found from its name when the card has no key yet (a card saved before banks were shared).
        assertNotNull(CardLooks.forProduct(BankLogoResolver.bankFor("HDFC Bank")?.id, "Regalia Gold"))
        assertEquals("REGALIA GOLD", BankCardDesign.forCard(card("HDFC Bank", null, "Regalia Gold")).tierName.uppercase())
    }
}
