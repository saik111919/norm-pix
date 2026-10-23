package com.example.normpix.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.DECK_WASH_MIDDLE
import com.example.normpix.ui.components.DECK_WASH_TOP
import com.example.normpix.ui.components.IDLE_DOT_ALPHA
import com.example.normpix.ui.components.deckWashTint
import com.example.normpix.wallet.AccountCardDesign
import com.example.normpix.wallet.BankCardDesign
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * WCAG 2.1 AA contrast (1.4.3 text 4.5:1, 1.4.11 the edges and marks that say what a control is 3:1) of the colours the app's
 * newer controls are made of, measured from the real theme - every accent, light and dark - and, for the account carousel's
 * panel, over every bank's and account kind's colour. A colour that falls short is a failing test, not a judgement.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ThemeContrastTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private var scheme: ColorScheme? = null

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("theme_contrast_test")
        compose.setContent { env.Content { scheme = MaterialTheme.colorScheme } }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = env.close()

    private fun ratio(fg: Color, bg: Color): Double {
        val shown = fg.compositeOver(bg)
        val a = shown.luminance().toDouble() + 0.05
        val b = bg.luminance().toDouble() + 0.05
        return max(a, b) / min(a, b)
    }

    /** Runs [check] with the theme of every accent in light and in dark; it returns the failures it found, which are all reported. */
    private fun everyTheme(check: (ColorScheme, tag: String) -> List<String>) {
        val failures = mutableListOf<String>()
        for (accent in AccentColor.entries) {
            for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
                env.preferences.setMode(mode)
                env.preferences.setAccent(accent)
                compose.waitForIdle()
                failures += check(scheme!!, "${accent.name}/${mode.name}")
            }
        }
        assertTrue(failures.isEmpty(), "contrast below the minimum:\n" + failures.joinToString("\n"))
    }

    private fun List<String>.plusIf(failed: Boolean, message: () -> String) = if (failed) this + message() else this

    @Test
    fun theMonthPillAndTheSelectedTabKeepTheirWordsReadable() = everyTheme { s, tag ->
        var out = emptyList<String>()
        // The title and the caption under it are the same colour (the caption is smaller, not fainter: dimming it fell to 3.1:1).
        val pill = ratio(s.onSecondaryContainer, s.secondaryContainer)
        out = out.plusIf(pill < 4.5) { "$tag: pill words $pill" }
        val unselected = ratio(s.onSurfaceVariant, s.surface)
        out = out.plusIf(unselected < 4.5) { "$tag: unselected tab label $unselected" }
        val onTrack = ratio(s.onSurface, s.surfaceContainer)
        out = out.plusIf(onTrack < 4.5) { "$tag: month step icons and Dates on the track $onTrack" }
        out
    }

    @Test
    fun theEdgesAndMarksThatSayWhatAControlIsAreSeen() = everyTheme { s, tag ->
        var out = emptyList<String>()
        // An unselected tab's edge is what shows it is a tab; the page marker's dots are where you are.
        val edge = ratio(s.outline, s.surface)
        out = out.plusIf(edge < 3.0) { "$tag: outline against the page $edge" }
        val dots = ratio(s.onSurfaceVariant.copy(alpha = IDLE_DOT_ALPHA), s.surfaceContainerHigh)
        out = out.plusIf(dots < 3.0) { "$tag: unselected marker dot on the panel $dots" }
        val current = ratio(s.primary, s.surfaceContainerHigh)
        out = out.plusIf(current < 3.0) { "$tag: current marker dot on the panel $current" }
        out
    }

    private val banks = listOf(
        "HDFC Bank", "State Bank of India", "ICICI Bank", "Axis Bank", "Kotak", "American Express", "Chase", "Citi", "Bank of America",
        "HSBC", "Capital One", "Wells Fargo", "Barclays", "Discover", "PNB", "Bank of Baroda", "IndusInd", "Yes Bank", "PNC", "TD Bank",
        "US Bank", "Some Bank Nobody Knows",
    )

    private fun tints(): List<Color> =
        banks.map { BankCardDesign.themeFor(it).gradientColors.first() } + AccountKind.entries.map { AccountCardDesign.themeFor(null, it).gradientColors.first() }

    @Test
    fun theAccountPanelStaysReadableOverEveryBanksColour_lightAndDark() = everyTheme { s, tag ->
        val dark = s.background.luminance() < 0.5f
        var out = emptyList<String>()
        for (tint in tints()) {
            // The wash is strongest at the panel's top and a third of that half way down; the labels and figures sit anywhere on it.
            for (strength in listOf(DECK_WASH_TOP, DECK_WASH_MIDDLE)) {
                val wash = lerp(s.surfaceContainerHigh, deckWashTint(tint, dark), strength)
                val label = ratio(s.onSurfaceVariant, wash)
                val error = ratio(s.error, wash)
                val body = ratio(s.onSurface, wash)
                val dot = ratio(s.onSurfaceVariant.copy(alpha = IDLE_DOT_ALPHA), wash)
                val current = ratio(s.primary, wash)
                out = out.plusIf(label < 4.5) { "$tag: label on the wash of #%08X: $label".format(tint.value.toLong().toInt()) }
                out = out.plusIf(error < 4.5) { "$tag: error text on the wash of #%08X: $error".format(tint.value.toLong().toInt()) }
                out = out.plusIf(body < 4.5) { "$tag: body text on the wash of #%08X: $body".format(tint.value.toLong().toInt()) }
                out = out.plusIf(dot < 3.0) { "$tag: marker dot on the wash of #%08X: $dot".format(tint.value.toLong().toInt()) }
                out = out.plusIf(current < 3.0) { "$tag: current marker on the wash of #%08X: $current".format(tint.value.toLong().toInt()) }
            }
        }
        out
    }
}
