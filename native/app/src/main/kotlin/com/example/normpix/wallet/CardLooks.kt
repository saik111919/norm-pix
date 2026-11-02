package com.example.normpix.wallet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import java.util.Locale

/** What a card product's face looks like: the colours it is drawn in - the main one first, then those it blends into - and the accent of its details. */
@Immutable
class ProductLook(val stops: List<Color>, val accent: Color)

/**
 * The look of the card products listed in [CardNames]: when a card is saved with the name of one, its face is drawn in that
 * product's colours instead of the bank's general ones.
 *
 * Where the colours come from: the picture of each card on its CardInsider page (2026-10-31). Each look is the card's own pixels
 * clustered into a main colour, up to two more, and the most vivid one as its accent - the picture's white margins and black
 * letterbox bars left out, and any second or third colour that was really lettering or a logo replaced by the main one - and each
 * was compared with the picture by eye. A colour was never taken from a description. Only colours are used: no logo, pattern or
 * artwork of the card is drawn.
 *
 * Two kinds of product are left out and wear their bank's face: a light card (white print would not read on it - the card face
 * draws white), and one with no single look (HPCL Super Saver is a mosaic). The colours are then darkened, keeping their hue,
 * only as far as white print needs ([readableOnWhite]): a bright cyan becomes a deeper blue, as it must in this app.
 */
object CardLooks {
    private class Look(val bank: String, val name: String, val main: Long, val second: Long, val third: Long, val accent: Long)

    private val looks = listOf(
        Look("hdfc", "Regalia Gold", 0x1A1E22, 0xC17124, 0x9B5B1D, 0xC17124),
        Look("hdfc", "MoneyBack+", 0x05AAE6, 0x074F8C, 0xE82028, 0x05AAE6),
        Look("hdfc", "Diners Club Black Metal", 0x14121C, 0x323138, 0x28272D, 0x14121C),
        Look("hdfc", "Infinia Metal", 0x163B66, 0x0D1218, 0x0B0F13, 0x163B66),
        Look("hdfc", "Tata Neu Infinity", 0x4E2A77, 0x31194C, 0xA34CEF, 0xA34CEF),
        Look("hdfc", "Marriott Bonvoy", 0x0C0603, 0x0C0603, 0x0C0603, 0xBCA860),
        Look("sbi", "SimplyCLICK", 0xA95D64, 0x773238, 0x8E474E, 0xA95D64),
        Look("sbi", "SimplySAVE", 0x165CA7, 0x104075, 0x0D335E, 0x165CA7),
        Look("sbi", "Tata Neu Infinity", 0x311B51, 0x4F3770, 0x3F2C5A, 0x3C215F),
        Look("sbi", "Flipkart", 0x1A2563, 0x1A2563, 0x1A2563, 0xFBE708),
        Look("sbi", "Cashback", 0x512F91, 0x512F91, 0x512F91, 0xFBE00F),
        Look("sbi", "Aurum", 0x080706, 0x050505, 0x040404, 0x080706),
        Look("icici", "Coral", 0x120301, 0xDE4416, 0x871D07, 0xDE4416),
        Look("icici", "Sapphiro", 0x1943A2, 0x0C1B66, 0x0A0A16, 0x1943A2),
        Look("icici", "Rubyx", 0xAC1722, 0x170602, 0x6A0E0E, 0xAC1722),
        Look("icici", "Emeralde Private Metal", 0x161B14, 0x124534, 0x0D735A, 0x279D7F),
        Look("icici", "MakeMyTrip", 0x2F1913, 0x2F1913, 0x2F1913, 0xE5780D),
        Look("icici", "Adani One", 0x0B0B0C, 0x1E1E20, 0x3A3A3D, 0xC0C0C4),
        Look("icici", "Amazon Pay", 0x0B0A0A, 0x0B0A0A, 0x0B0A0A, 0x4B3E3B),
        Look("axis", "ACE", 0x140A14, 0x370F2E, 0x661945, 0xA22C67),
        Look("axis", "Magnus", 0x211F20, 0x9D295C, 0x591532, 0x9D295C),
        Look("axis", "Privilege", 0x050707, 0x95194D, 0x650A33, 0x95194D),
        Look("axis", "SELECT", 0x953554, 0x652837, 0x240D0F, 0x953554),
        Look("axis", "Airtel Axis Bank", 0x180608, 0x421720, 0x180608, 0x421720),
        Look("axis", "Horizon", 0x32031F, 0x600731, 0x4C0627, 0x600731),
        Look("axis", "My Zone", 0x220915, 0x541632, 0xB0275F, 0xB0275F),
        Look("kotak", "League Platinum", 0x090C0B, 0x686D64, 0x202824, 0x686D64),
        Look("kotak", "Zen Signature", 0x2A180F, 0x592F14, 0x8D471A, 0x8D471A),
        Look("kotak", "Mojo Platinum", 0xD853B3, 0x6B369E, 0x443184, 0xD853B3),
        Look("kotak", "811 #DreamDifferent", 0x24404D, 0x192D36, 0x14242B, 0x324D5E),
        Look("indusind", "Legend", 0x252223, 0x4C4845, 0x3D3937, 0x4C4845),
        Look("indusind", "Indulge", 0x313030, 0x090809, 0x070707, 0x313030),
        Look("indusind", "Tiger", 0x080808, 0x2F2F2F, 0x575657, 0x868686),
        Look("indusind", "EazyDiner", 0x121211, 0x363533, 0x73726E, 0x73726E),
        Look("indusind", "Avios Visa Infinite", 0x474747, 0x252221, 0x1D1B1A, 0x474747),
        Look("indusind", "Nexxt", 0x2F8BBE, 0x2F8BBE, 0x18355F, 0x2F8BBE),
    )

    private val byKey: Map<Pair<String, String>, ProductLook> by lazy {
        looks.associate { look ->
            // In the picture's order of importance: the main colour first, then the others it blends into.
            val stops = listOf(look.main, look.second, look.third).map { readableOnWhite(Color(0xFF000000 or it)) }
                .fold(emptyList<Color>()) { kept, c -> if (c == kept.lastOrNull()) kept else kept + c }
            (look.bank to key(look.name)) to ProductLook(stops, Color(0xFF000000 or look.accent))
        }
    }

    /** The look of the product called [cardName] at the bank [bankKey] (a directory id), or null: no name, no such product, or another bank's. */
    fun forProduct(bankKey: String?, cardName: String): ProductLook? =
        if (bankKey == null || cardName.isBlank()) null else byKey[bankKey to key(cardName)]

    /** Every (bank, name) that has a look, as the card names list spells it, for tests. */
    internal val products: List<Pair<String, String>> get() = looks.map { it.bank to it.name }

    /** [name] as compared: case, spacing and punctuation don't matter, and "+" is "plus". */
    internal fun key(name: String): String =
        name.lowercase(Locale.ROOT).replace("+", " plus").replace("#", "").replace(Regex("[^a-z0-9]+"), " ").trim()

    /**
     * [color] darkened - mixed with black in small steps, so its hue stays - until the card's print reads on it at 4.5:1 (WCAG 1.4.3),
     * as every colour a card is drawn in must (`CardTextContrastTest`): the white print, and the captions, which are white too but
     * translucent and so need a little more. Unchanged when it already does.
     */
    internal fun readableOnWhite(color: Color): Color {
        var c = color
        var steps = 0
        while (!reads(c) && steps < 60) {
            c = lerp(c, Color.Black, 0.05f)
            steps++
        }
        return c
    }

    internal fun reads(c: Color): Boolean = contrast(Color.White, c) >= 4.55 && contrast(CardCaptionColor, c) >= 4.55

    internal fun contrastWithWhite(c: Color): Double = contrast(Color.White, c)

    private fun contrast(fg: Color, bg: Color): Double {
        val a = fg.compositeOver(bg).luminance().toDouble() + 0.05
        val b = bg.luminance().toDouble() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }
}
