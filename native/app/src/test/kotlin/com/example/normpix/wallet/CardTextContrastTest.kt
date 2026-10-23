package com.example.normpix.wallet

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.example.normpix.finance.domain.AccountKind
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The print on a card face - white, and its captions ([CardCaptionColor]) - against every colour the card is drawn in, for
 * every bank the app knows and every kind of account: WCAG 1.4.3 asks 4.5:1 of text this small. The card's own darkening along
 * its lower edge and the text's shadow are not counted, so this is the worst case, not the usual one. Captions used to be white
 * at 62%, which was 2.4:1 on Kotak's red and under 4:1 on a third of the banks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardTextContrastTest {
    private fun ratio(fg: Color, bg: Color): Double {
        val shown = fg.compositeOver(bg)
        val a = shown.luminance().toDouble() + 0.05
        val b = bg.luminance().toDouble() + 0.05
        return max(a, b) / min(a, b)
    }

    private val banks = listOf(
        "HDFC Bank", "State Bank of India", "ICICI Bank", "Axis Bank", "Kotak", "American Express", "Chase", "Citi", "Bank of America",
        "HSBC", "Capital One", "Wells Fargo", "Barclays", "Discover", "PNB", "Bank of Baroda", "IndusInd", "Yes Bank", "PNC", "TD Bank",
        "US Bank", "Some Bank Nobody Knows",
    )

    /** Every theme with a name, and every colour it is drawn in: the stops it lists and the gradient it paints. */
    private fun themes(): List<Pair<String, BankBrandTheme>> =
        banks.map { it to BankCardDesign.themeFor(it) } + AccountKind.entries.map { "kind ${it.name}" to AccountCardDesign.themeFor(null, it) }

    private fun BankBrandTheme.colorsDrawn(): List<Color> = gradientColors + when (val g = gradient) {
        is GradientSpec.Linear -> g.colors
        is GradientSpec.Radial -> g.colors
    }

    private fun every(check: (name: String, stop: Color, theme: BankBrandTheme) -> String?) {
        val failures = themes().flatMap { (name, theme) -> theme.colorsDrawn().mapNotNull { stop -> check(name, stop, theme) } }
        assertTrue(failures.isEmpty(), "below 4.5:1:\n" + failures.distinct().joinToString("\n"))
    }

    @Test
    fun whitePrintIsReadableOnEveryCard() = every { name, stop, _ ->
        val r = ratio(Color.White, stop)
        if (r < 4.5) "$name: white on #%08X is %.2f".format(stop.value.toLong().toInt(), r) else null
    }

    @Test
    fun theCaptionsAreReadableOnEveryCard() = every { name, stop, _ ->
        val r = ratio(CardCaptionColor, stop)
        if (r < 4.5) "$name: captions on #%08X are %.2f".format(stop.value.toLong().toInt(), r) else null
    }

    @Test
    fun theKindPillsWordsAreReadableOnEveryCard() = every { name, stop, theme ->
        val behind = theme.badgeColor.compositeOver(CardBadgeUnderlay.compositeOver(stop))
        val r = ratio(theme.badgeTextColor, behind)
        if (r < 4.5) "$name: the kind pill's words on #%08X are %.2f".format(stop.value.toLong().toInt(), r) else null
    }
}
