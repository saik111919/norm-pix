package com.example.normpix.wallet

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.min

/** Supported background watermark / geometric patterns for bank cards. */
enum class BankPatternType {
    HdfcGeometricGrid,
    SbiConcentricRings,
    IciciWaves,
    AxisAngularFacets,
    KotakInfinity,
    AmexCenturionKey,
    ChaseOctagonRays,
    CitiArcWaves,
    HsbcHexagonGrid,
    MetallicBrushed,
    MinimalGeometric,
}

/** Smart EMV chip finish for the card face. */
enum class EmvChipFinish { Gold, Silver }

/** A point inside a box in Flutter-style alignment units: -1 is the near edge, 1 the far edge. */
data class Anchor(val x: Float, val y: Float) {
    fun offset(size: Size) = Offset((x + 1f) / 2f * size.width, (y + 1f) / 2f * size.height)
}

/** A card-face gradient, resolved against the card's size when drawn. */
sealed interface GradientSpec {
    fun brush(size: Size): Brush

    data class Linear(
        val begin: Anchor,
        val end: Anchor,
        val colors: List<Color>,
        val stops: List<Float>? = null,
    ) : GradientSpec {
        override fun brush(size: Size): Brush {
            val start = begin.offset(size)
            val finish = end.offset(size)
            return if (stops == null) {
                Brush.linearGradient(colors, start, finish)
            } else {
                Brush.linearGradient(*colors.zip(stops) { c, s -> s to c }.toTypedArray(), start = start, end = finish)
            }
        }
    }

    /** [radius] is a fraction of the card's shorter side. */
    data class Radial(val center: Anchor, val radius: Float, val colors: List<Color>) : GradientSpec {
        override fun brush(size: Size): Brush =
            Brush.radialGradient(colors, center = center.offset(size), radius = radius * min(size.width, size.height))
    }
}

/** Which hand-drawn mark a theme uses when the bank has no bundled logo artwork. */
sealed interface BankLogo {
    data object Hdfc : BankLogo
    data object Sbi : BankLogo
    data object Icici : BankLogo
    data object Axis : BankLogo
    data object Kotak : BankLogo
    data object Amex : BankLogo
    data object Chase : BankLogo
    data object Citi : BankLogo
    data object Boa : BankLogo
    data object Hsbc : BankLogo
    data object CapitalOne : BankLogo
    data object WellsFargo : BankLogo
    data object Barclays : BankLogo
    data object Discover : BankLogo
    data object Pnb : BankLogo
    data object Bob : BankLogo
    data object IndusInd : BankLogo
    data object Yes : BankLogo

    /** Just the bank's name in the card's text style. */
    data class Text(val name: String) : BankLogo

    /** A generic bank icon beside the name, for banks nobody recognizes. */
    data class Generic(val bankName: String) : BankLogo
}

/** Comprehensive branding profile for a bank card. */
@Immutable
class BankBrandTheme(
    val id: String,
    val displayName: String,
    val gradientColors: List<Color>,
    val gradient: GradientSpec,
    val patternType: BankPatternType,
    val chipFinish: EmvChipFinish = EmvChipFinish.Gold,
    val tierName: String = "PLATINUM",
    val badgeColor: Color = Color(0x33FFFFFF),
    val badgeBorderColor: Color = Color(0x55FFFFFF),
    val badgeTextColor: Color = Color.White,
    val accentColor: Color = Color.White,
    /** The hand-drawn mark, for banks with no bundled logo artwork (see [BankLogoResolver]). */
    val logo: BankLogo,
)

/**
 * Maps a bank name to a brand-specific design theme for the wallet card UI.
 *
 * Recognizes a curated list of major Indian and global banks by case-insensitive
 * keyword matching. Any unrecognized bank deterministically resolves to a
 * metallic/obsidian premium card theme. The logo itself is not chosen here:
 * [BankLogoView] asks [BankLogoResolver] for the bank's artwork.
 */
object BankCardDesign {
    /** Gradient colors for a bank name. */
    fun gradientFor(bankName: String): List<Color> = themeFor(bankName).gradientColors

    /** Resolves the branding theme for [bankName]. */
    fun themeFor(bankName: String): BankBrandTheme {
        val normalized = bankName.trim().lowercase()
        if (normalized.isEmpty()) return fallback("Card")

        fun has(vararg keys: String) = keys.any { normalized.contains(it) }

        return when {
            has("hdfc") -> hdfcTheme
            has("sbi", "state bank of india", "state bank") -> sbiTheme
            has("icici") -> iciciTheme
            has("axis") -> axisTheme
            has("kotak") -> kotakTheme
            has("amex", "american express") -> amexTheme
            has("chase") -> chaseTheme
            has("citi", "citibank") -> citiTheme
            has("bank of america", "boa", "bofa") -> boaTheme
            has("hsbc") -> hsbcTheme
            has("capital one") -> capitalOneTheme
            has("wells fargo") -> wellsFargoTheme
            has("barclays") -> barclaysTheme
            has("discover") -> discoverTheme
            has("pnb", "punjab national bank") -> pnbTheme
            has("bob", "bank of baroda", "baroda") -> bobTheme
            has("indusind") -> indusIndTheme
            has("yes bank") || normalized == "yes" -> yesBankTheme
            has("pnc") -> pncTheme
            has("td bank") || normalized == "td" -> tdBankTheme
            has("us bank") -> usBankTheme
            else -> fallback(bankName)
        }
    }

    // ----------------------------------------------------------------------------------------
    // Deterministic fallback for custom banks
    // ----------------------------------------------------------------------------------------

    private val fallbackPalettes = listOf(
        listOf(Color(0xFF3F2B96), Color(0xFF261961), Color(0xFF130B33)), // Deep Amethyst
        listOf(Color(0xFF005F73), Color(0xFF0A3A40), Color(0xFF041C1F)), // Dark Teal
        listOf(Color(0xFF1B4332), Color(0xFF0E281E), Color(0xFF07140F)), // Emerald Forest
        listOf(Color(0xFF831843), Color(0xFF500727), Color(0xFF280213)), // Ruby Rose
        listOf(Color(0xFF7C2D12), Color(0xFF4A1A09), Color(0xFF260D04)), // Auburn Amber
        listOf(Color(0xFF312E81), Color(0xFF1E1B4B), Color(0xFF0F0D27)), // Midnight Indigo
        listOf(Color(0xFF111827), Color(0xFF1F2937), Color(0xFF030712)), // Obsidian Black
        listOf(Color(0xFF374151), Color(0xFF1F2937), Color(0xFF111827)), // Titanium Slate
    )

    private fun fallback(bankName: String): BankBrandTheme {
        val trimmed = bankName.trim()
        val hash = trimmed.lowercase().fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff }
        val palette = fallbackPalettes[hash % fallbackPalettes.size]
        return BankBrandTheme(
            id = "custom_$hash",
            displayName = trimmed.ifEmpty { "Wallet Card" },
            gradientColors = palette,
            gradient = GradientSpec.Linear(Anchor(-1f, -1f), Anchor(1f, 1f), palette),
            patternType = if (hash % 3 == 0) BankPatternType.MetallicBrushed else BankPatternType.MinimalGeometric,
            chipFinish = if (hash % 2 == 0) EmvChipFinish.Gold else EmvChipFinish.Silver,
            tierName = "SIGNATURE",
            logo = BankLogo.Generic(trimmed),
        )
    }

    // ----------------------------------------------------------------------------------------
    // Brand themes (generated from the reference design, see git history)
    // ----------------------------------------------------------------------------------------

    private val hdfcTheme = BankBrandTheme(
        id = "hdfc",
        displayName = "HDFC BANK",
        gradientColors = listOf(Color(0xFF003366), Color(0xFF001F3F), Color(0xFF0D1B2A)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF004C8F), Color(0xFF002855), Color(0xFF081426)), stops = listOf(0.0f, 0.55f, 1.0f)),
        patternType = BankPatternType.HdfcGeometricGrid,
        chipFinish = EmvChipFinish.Gold,
        tierName = "REGALIA GOLD",
        badgeColor = Color(0x33ED232A),
        badgeBorderColor = Color(0x66ED232A),
        accentColor = Color(0xFFED232A),
        logo = BankLogo.Hdfc,
    )

    private val sbiTheme = BankBrandTheme(
        id = "sbi",
        displayName = "State Bank of India",
        gradientColors = listOf(Color(0xFF28559A), Color(0xFF163765), Color(0xFF0C1E38)),
        gradient = GradientSpec.Radial(center = Anchor(0.6f, -0.4f), radius = 1.3f, colors = listOf(Color(0xFF2B5B9E), Color(0xFF183B6D), Color(0xFF0E2240))),
        patternType = BankPatternType.SbiConcentricRings,
        chipFinish = EmvChipFinish.Gold,
        tierName = "SIMPLYCLICK",
        badgeColor = Color(0x2E00B4D8),
        badgeBorderColor = Color(0x5500B4D8),
        accentColor = Color(0xFF00B4D8),
        logo = BankLogo.Sbi,
    )

    private val iciciTheme = BankBrandTheme(
        id = "icici",
        displayName = "ICICI Bank",
        gradientColors = listOf(Color(0xFF9E1F35), Color(0xFF6B1123), Color(0xFF380812)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFFB8283B), Color(0xFF7A1327), Color(0xFF450A15)), stops = listOf(0.0f, 0.6f, 1.0f)),
        patternType = BankPatternType.IciciWaves,
        chipFinish = EmvChipFinish.Gold,
        tierName = "SAPPHIRO",
        badgeColor = Color(0x3DF37021),
        badgeBorderColor = Color(0x66F37021),
        accentColor = Color(0xFFF37021),
        logo = BankLogo.Icici,
    )

    private val axisTheme = BankBrandTheme(
        id = "axis",
        displayName = "AXIS BANK",
        gradientColors = listOf(Color(0xFF97144D), Color(0xFF610D33), Color(0xFF33051A)),
        gradient = GradientSpec.Linear(begin = Anchor(-0.8f, -1.0f), end = Anchor(0.8f, 1.0f), colors = listOf(Color(0xFFA61856), Color(0xFF700F3A), Color(0xFF3A061E)), stops = listOf(0.0f, 0.5f, 1.0f)),
        patternType = BankPatternType.AxisAngularFacets,
        chipFinish = EmvChipFinish.Gold,
        tierName = "MAGNUS",
        badgeColor = Color(0x3397144D),
        badgeBorderColor = Color(0x66D42A75),
        accentColor = Color(0xFFE84284),
        logo = BankLogo.Axis,
    )

    private val kotakTheme = BankBrandTheme(
        id = "kotak",
        displayName = "kotak",
        gradientColors = listOf(Color(0xFFED1C24), Color(0xFF990E14), Color(0xFF4A0508)),
        gradient = GradientSpec.Linear(begin = Anchor(1f, -1f), end = Anchor(-1f, 1f), colors = listOf(Color(0xFFE51A22), Color(0xFF9E1016), Color(0xFF470609)), stops = listOf(0.0f, 0.55f, 1.0f)),
        patternType = BankPatternType.KotakInfinity,
        chipFinish = EmvChipFinish.Gold,
        tierName = "PRIVY LEAGUE",
        badgeColor = Color(0x33FFFFFF),
        badgeBorderColor = Color(0x55FFFFFF),
        accentColor = Color(0xFFFF4D55),
        logo = BankLogo.Kotak,
    )

    private val amexTheme = BankBrandTheme(
        id = "amex",
        displayName = "AMERICAN EXPRESS",
        gradientColors = listOf(Color(0xFF3A4750), Color(0xFF263038), Color(0xFF14191E)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF4B5D69), Color(0xFF2C3740), Color(0xFF171E24)), stops = null),
        patternType = BankPatternType.AmexCenturionKey,
        chipFinish = EmvChipFinish.Silver,
        tierName = "PLATINUM",
        badgeColor = Color(0x330070D2),
        badgeBorderColor = Color(0x660070D2),
        accentColor = Color(0xFF0070D2),
        logo = BankLogo.Amex,
    )

    private val chaseTheme = BankBrandTheme(
        id = "chase",
        displayName = "CHASE",
        gradientColors = listOf(Color(0xFF0C4A9E), Color(0xFF08336E), Color(0xFF041B3B)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF1156B5), Color(0xFF0C3D82), Color(0xFF051D40)), stops = null),
        patternType = BankPatternType.ChaseOctagonRays,
        chipFinish = EmvChipFinish.Silver,
        tierName = "SAPPHIRE",
        badgeColor = Color(0x331156B5),
        badgeBorderColor = Color(0x661156B5),
        accentColor = Color(0xFF4BA3E3),
        logo = BankLogo.Chase,
    )

    private val citiTheme = BankBrandTheme(
        id = "citi",
        displayName = "citi",
        gradientColors = listOf(Color(0xFF003B70), Color(0xFF002244), Color(0xFF001428)),
        gradient = GradientSpec.Linear(begin = Anchor(0f, -1f), end = Anchor(0f, 1f), colors = listOf(Color(0xFF004482), Color(0xFF002D57), Color(0xFF001933)), stops = null),
        patternType = BankPatternType.CitiArcWaves,
        chipFinish = EmvChipFinish.Gold,
        tierName = "PREMIER",
        badgeColor = Color(0x33ED232A),
        badgeBorderColor = Color(0x55ED232A),
        accentColor = Color(0xFFED232A),
        logo = BankLogo.Citi,
    )

    private val boaTheme = BankBrandTheme(
        id = "boa",
        displayName = "Bank of America",
        gradientColors = listOf(Color(0xFF012169), Color(0xFFB3131E), Color(0xFF001030)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF0C2B6A), Color(0xFF051638), Color(0xFF6B0E14)), stops = listOf(0.0f, 0.6f, 1.0f)),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "CUSTOMIZED CASH",
        badgeColor = Color(0x33B3131E),
        badgeBorderColor = Color(0x66B3131E),
        accentColor = Color(0xFFE31B23),
        logo = BankLogo.Boa,
    )

    private val hsbcTheme = BankBrandTheme(
        id = "hsbc",
        displayName = "HSBC",
        gradientColors = listOf(Color(0xFF2B2B2B), Color(0xFF141414), Color(0xFF0A0A0A)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF333333), Color(0xFF1A1A1A), Color(0xFF0D0D0D)), stops = null),
        patternType = BankPatternType.HsbcHexagonGrid,
        chipFinish = EmvChipFinish.Silver,
        tierName = "PREMIER",
        badgeColor = Color(0x33DB0011),
        badgeBorderColor = Color(0x66DB0011),
        accentColor = Color(0xFFDB0011),
        logo = BankLogo.Hsbc,
    )

    private val capitalOneTheme = BankBrandTheme(
        id = "capital_one",
        displayName = "Capital One",
        gradientColors = listOf(Color(0xFF004977), Color(0xFF0B2238), Color(0xFF040E17)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF005387), Color(0xFF0A2945), Color(0xFF061421)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Silver,
        tierName = "VENTURE X",
        badgeColor = Color(0x33D03027),
        badgeBorderColor = Color(0x66D03027),
        accentColor = Color(0xFFD03027),
        logo = BankLogo.CapitalOne,
    )

    private val wellsFargoTheme = BankBrandTheme(
        id = "wells_fargo",
        displayName = "WELLS FARGO",
        gradientColors = listOf(Color(0xFFD71E28), Color(0xFF8F1017), Color(0xFF47070B)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFFC71B24), Color(0xFF7A0E14), Color(0xFF3D0609)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "ACTIVE CASH",
        badgeColor = Color(0x33FFCD41),
        badgeBorderColor = Color(0x66FFCD41),
        accentColor = Color(0xFFFFCD41),
        logo = BankLogo.WellsFargo,
    )

    private val barclaysTheme = BankBrandTheme(
        id = "barclays",
        displayName = "BARCLAYS",
        gradientColors = listOf(Color(0xFF00395D), Color(0xFF002238), Color(0xFF001320)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF005285), Color(0xFF003050), Color(0xFF00192A)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Silver,
        tierName = "VIEW",
        badgeColor = Color(0x3300AEEF),
        badgeBorderColor = Color(0x6600AEEF),
        accentColor = Color(0xFF00AEEF),
        logo = BankLogo.Barclays,
    )

    private val discoverTheme = BankBrandTheme(
        id = "discover",
        displayName = "DISCOVER",
        gradientColors = listOf(Color(0xFF2C3238), Color(0xFF191D21), Color(0xFF0C0E10)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF343B42), Color(0xFF1E2328), Color(0xFF101316)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "IT CASH BACK",
        badgeColor = Color(0x33E87722),
        badgeBorderColor = Color(0x66E87722),
        accentColor = Color(0xFFE87722),
        logo = BankLogo.Discover,
    )

    private val pnbTheme = BankBrandTheme(
        id = "pnb",
        displayName = "punjab national bank",
        gradientColors = listOf(Color(0xFF7A1B28), Color(0xFF4A0E17), Color(0xFF26050B)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF8B202F), Color(0xFF57121D), Color(0xFF2D070D)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "SELECT",
        badgeColor = Color(0x33FFB81C),
        badgeBorderColor = Color(0x66FFB81C),
        accentColor = Color(0xFFFFB81C),
        logo = BankLogo.Pnb,
    )

    private val bobTheme = BankBrandTheme(
        id = "bob",
        displayName = "Bank of Baroda",
        gradientColors = listOf(Color(0xFFD95A11), Color(0xFF9E3A06), Color(0xFF592003)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFFE86214), Color(0xFFA63E07), Color(0xFF632403)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "PREMIER",
        badgeColor = Color(0x33FFFFFF),
        badgeBorderColor = Color(0x55FFFFFF),
        accentColor = Color(0xFFFF884D),
        logo = BankLogo.Bob,
    )

    private val indusIndTheme = BankBrandTheme(
        id = "indusind",
        displayName = "IndusInd Bank",
        gradientColors = listOf(Color(0xFF701919), Color(0xFF420D0D), Color(0xFF210505)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF7F1F1F), Color(0xFF4A1010), Color(0xFF260707)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "PINNACLE",
        badgeColor = Color(0x33D4AF37),
        badgeBorderColor = Color(0x66D4AF37),
        accentColor = Color(0xFFD4AF37),
        logo = BankLogo.IndusInd,
    )

    private val yesBankTheme = BankBrandTheme(
        id = "yes",
        displayName = "YES BANK",
        gradientColors = listOf(Color(0xFF004B87), Color(0xFF002C52), Color(0xFF001529)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF00589E), Color(0xFF003563), Color(0xFF001B33)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "MARQUEE",
        badgeColor = Color(0x33E31837),
        badgeBorderColor = Color(0x66E31837),
        accentColor = Color(0xFFE31837),
        logo = BankLogo.Yes,
    )

    private val pncTheme = BankBrandTheme(
        id = "pnc",
        displayName = "PNC",
        gradientColors = listOf(Color(0xFF1B428A), Color(0xFF0C2454), Color(0xFF05122B)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF2251A8), Color(0xFF113270), Color(0xFF07193D)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "POINTS",
        badgeColor = Color(0x33F58025),
        badgeBorderColor = Color(0x66F58025),
        accentColor = Color(0xFFF58025),
        logo = BankLogo.Text("PNC"),
    )

    private val tdBankTheme = BankBrandTheme(
        id = "td",
        displayName = "TD",
        gradientColors = listOf(Color(0xFF008A44), Color(0xFF005228), Color(0xFF002914)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF009C4D), Color(0xFF005C2D), Color(0xFF003017)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Silver,
        tierName = "CASH",
        badgeColor = Color(0x33FFFFFF),
        badgeBorderColor = Color(0x55FFFFFF),
        accentColor = Color(0xFF00FF7F),
        logo = BankLogo.Text("TD Bank"),
    )

    private val usBankTheme = BankBrandTheme(
        id = "us_bank",
        displayName = "us bank",
        gradientColors = listOf(Color(0xFF0C2074), Color(0xFF071242), Color(0xFF030821)),
        gradient = GradientSpec.Linear(begin = Anchor(-1f, -1f), end = Anchor(1f, 1f), colors = listOf(Color(0xFF112BA1), Color(0xFF091959), Color(0xFF040C2B)), stops = null),
        patternType = BankPatternType.MinimalGeometric,
        chipFinish = EmvChipFinish.Gold,
        tierName = "ALTITUDE",
        badgeColor = Color(0x33D12630),
        badgeBorderColor = Color(0x66D12630),
        accentColor = Color(0xFFD12630),
        logo = BankLogo.Text("us bank"),
    )
}
