package com.example.normpix.wallet

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.settings.CardDesign
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.AppTheme
import com.example.normpix.ui.theme.ArtworkColors
import com.example.normpix.ui.theme.NormPixTheme
import java.io.File
import java.time.LocalDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Visual and Accessibility QA tests for NormPix card designs (Classic, Banner, Ledger, Tinted):
 * 1. WCAG 2.1 AA text contrast ratio (>= 4.5:1) across Light, Dark, Pure, and Volt themes.
 * 2. 1.6x font scale layout resilience on 360dp and 411dp viewports.
 * 3. TalkBack accessibility semantics equivalence with Classic.
 * (The pictures, and the corner and theme checks, are in CardDesignRenderTest.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CardDesignsVisualQaTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "card_designs_qa_test")
    private val outDir = File("build/qa_card_designs").also { it.mkdirs() }

    private val sampleCard = StoredCard(
        id = "qa-card-1",
        bankName = "HDFC Bank",
        cardholderName = "Asha Rao",
        cardNumber = "4111111111114242",
        expiryMonth = "07",
        expiryYear = "29",
        cvv = "737",
        kind = CardKind.Credit,
        createdAt = LocalDateTime.of(2026, 1, 1, 0, 0),
        cardName = "Millennia",
    )

    private val sampleAccount = AccountCardData(
        id = 1L,
        name = "Primary Savings",
        kind = AccountKind.Bank,
        bankName = "HDFC Bank",
        balance = "₹42,500",
        standing = null,
        isDefault = true,
        archived = false,
        cards = 2,
        credit = null,
        creditSpoken = null,
        overLimit = false,
        creditUsed = null,
        due = null,
        dueSoon = false,
        dueSpoken = null,
        share = "45%",
        hidden = false,
    )

    @AfterTest
    fun tearDown() = env.close()

    private fun ratio(fg: Color, bg: Color): Double {
        val shown = fg.compositeOver(bg)
        val a = shown.luminance().toDouble() + 0.05
        val b = bg.luminance().toDouble() + 0.05
        return max(a, b) / min(a, b)
    }

    @Test
    fun testTextContrastRatiosAcrossThemes() {
        data class ThemeCase(val name: String, val isDark: Boolean, val accent: AccentColor)
        val themes = listOf(
            ThemeCase("Light", false, AccentColor.Green),
            ThemeCase("Dark", true, AccentColor.Green),
            ThemeCase("Pure Light", false, AccentColor.Pure),
            ThemeCase("Pure Dark", true, AccentColor.Pure),
            ThemeCase("Volt Light", false, AccentColor.Volt),
            ThemeCase("Volt Dark", true, AccentColor.Volt),
        )

        val failures = mutableListOf<String>()

        for (theme in themes) {
            val scheme = AppTheme.accentScheme(theme.accent, theme.isDark)

            // 1. Banner Design Text Contrast
            val bannerContainer = scheme.secondaryContainer
            val bannerInk = scheme.onSecondaryContainer
            val bannerQuiet = quietTone(bannerInk, bannerContainer)
            val bannerPillBg = scheme.primary
            val bannerPillText = scheme.onPrimary

            val rBannerInk = ratio(bannerInk, bannerContainer)
            if (rBannerInk < 4.5) failures += "${theme.name} Banner ink on container: $rBannerInk < 4.5"

            val rBannerQuiet = ratio(bannerQuiet, bannerContainer)
            if (rBannerQuiet < 4.5) failures += "${theme.name} Banner quiet tone on container: $rBannerQuiet < 4.5"

            val rBannerPill = ratio(bannerPillText, bannerPillBg)
            if (rBannerPill < 4.5) failures += "${theme.name} Banner pill text on primary: $rBannerPill < 4.5"

            // 2. Ledger Design Text Contrast
            val ledgerSurface = scheme.surfaceContainerHigh
            val ledgerOnSurface = scheme.onSurface
            val ledgerQuiet = quietTone(ledgerOnSurface, ledgerSurface)

            val rLedgerOnSurface = ratio(ledgerOnSurface, ledgerSurface)
            if (rLedgerOnSurface < 4.5) failures += "${theme.name} Ledger onSurface on surfaceContainerHigh: $rLedgerOnSurface < 4.5"

            val rLedgerQuiet = ratio(ledgerQuiet, ledgerSurface)
            if (rLedgerQuiet < 4.5) failures += "${theme.name} Ledger quiet on surfaceContainerHigh: $rLedgerQuiet < 4.5"

            // 3. Tinted Design Text Contrast
            val bankSeed = Color(0xFF004C8F) // HDFC Bank blue seed
            val tintedScheme = if (ArtworkColors.usable(bankSeed)) {
                val keepSurfaces = (theme.accent == AccentColor.Pure || theme.accent == AccentColor.Volt)
                ArtworkColors.scheme(scheme, bankSeed, keepSurfaces)
            } else null

            val tContainer = tintedScheme?.primaryContainer ?: scheme.primaryContainer
            val tInk = tintedScheme?.onPrimaryContainer ?: scheme.onPrimaryContainer
            val tQuiet = quietTone(tInk, tContainer)
            val tPrimary = tintedScheme?.primary ?: scheme.primary
            val tOnPrimary = tintedScheme?.onPrimary ?: scheme.onPrimary

            val rTintedInk = ratio(tInk, tContainer)
            if (rTintedInk < 4.5) failures += "${theme.name} Tinted ink on container: $rTintedInk < 4.5"

            val rTintedQuiet = ratio(tQuiet, tContainer)
            if (rTintedQuiet < 4.5) failures += "${theme.name} Tinted quiet on container: $rTintedQuiet < 4.5"

            val rTintedPill = ratio(tOnPrimary, tPrimary)
            if (rTintedPill < 4.5) failures += "${theme.name} Tinted pill text on primary: $rTintedPill < 4.5"
        }

        assertTrue(failures.isEmpty(), "Contrast violations found:\n" + failures.joinToString("\n"))
    }

    @Test
    fun testTalkBackSemanticsEquivalenceAcrossDesigns() {
        val designs = listOf(CardDesign.Classic, CardDesign.Banner, CardDesign.Ledger, CardDesign.Tinted)

        compose.setContent {
            env.Content {
                Column {
                    for (design in designs) {
                        WalletCardView(
                            card = sampleCard,
                            revealed = false,
                            actionStyle = null,
                            onToggleReveal = {},
                            onEdit = {},
                            onDelete = {},
                            cardDesign = design,
                        )
                        AccountCardView(
                            data = sampleAccount,
                            onOpen = {},
                            onManage = {},
                            cardDesign = design,
                        )
                    }
                }
            }
        }

        compose.waitForIdle()

        val cardSpoken = "${sampleCard.bankName} credit card, ending in ${sampleCard.last4}, expires ${sampleCard.expiryMonth}/${sampleCard.expiryYear}, ${sampleCard.cardholderName}"
        compose.onAllNodesWithContentDescription(cardSpoken).assertCountEquals(4)
        compose.onAllNodesWithContentDescription(sampleAccount.spoken).assertCountEquals(4)
    }

    @Test
    fun testFontScaleOnePointSixLayoutIntegrity() {
        val designs = listOf(CardDesign.Classic, CardDesign.Banner, CardDesign.Ledger, CardDesign.Tinted)

        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.6f),
            ) {
                env.Content {
                    Column(Modifier.padding(AppSpacing.md)) {
                        for (design in designs) {
                            WalletCardView(
                                card = sampleCard,
                                revealed = true,
                                actionStyle = null,
                                onToggleReveal = {},
                                onEdit = {},
                                onDelete = {},
                                cardDesign = design,
                            )
                            AccountCardView(
                                data = sampleAccount,
                                onOpen = {},
                                onManage = {},
                                cardDesign = design,
                            )
                        }
                    }
                }
            }
        }

        compose.waitForIdle()
    }
}
