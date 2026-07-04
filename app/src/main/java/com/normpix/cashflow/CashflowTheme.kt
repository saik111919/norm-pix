package com.normpix.cashflow

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Theme seed colors. The first three come straight from the Cashflow design;
 * the next four are Theme Factory palettes (Ocean Depths, Sunset Boulevard,
 * Forest Canopy, Modern Minimalist). DYNAMIC follows the device wallpaper
 * (Material You) on Android 12+.
 */
enum class Seed(val swatch: Color, val label: String) {
    GREEN(Color(0xFF1B6B50), "Forest"),
    BLUE(Color(0xFF0B57D0), "Ocean"),
    PURPLE(Color(0xFF6750A4), "Orchid"),
    OCEAN_DEPTHS(Color(0xFF2D8B8B), "Depths"),
    SUNSET(Color(0xFFE76F51), "Sunset"),
    CANOPY(Color(0xFF2D4A2B), "Canopy"),
    MINIMAL(Color(0xFF36454F), "Mono"),
    DYNAMIC(Color(0xFF888888), "Dynamic"),
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** Full token set used across the app (names mirror the design prototype). */
@Immutable
data class CashTokens(
    val p: Color,      // primary
    val onP: Color,    // on primary
    val pc: Color,     // primary container
    val onPc: Color,   // on primary container
    val sc: Color,     // secondary container
    val onSc: Color,   // on secondary container
    val surf: Color,   // surface
    val cont: Color,   // surface container
    val hi: Color,     // surface container highest / card
    val onS: Color,    // on surface
    val onSV: Color,   // on surface variant
    val out: Color,    // outline
    val err: Color,    // error
    val warnBg: Color, // warning chip background
    val warnFg: Color, // warning chip foreground
    val dark: Boolean,
)

private fun c(v: Long) = Color(v)

private val WARN_BG_LIGHT = c(0xFFF6E3C2)
private val WARN_FG_LIGHT = c(0xFF5F4200)
private val WARN_BG_DARK = c(0xFF4A3A12)
private val WARN_FG_DARK = c(0xFFF6E3C2)

private val LIGHT = mapOf(
    Seed.GREEN to CashTokens(
        c(0xFF1B6B50), c(0xFFFFFFFF), c(0xFFA6F2D0), c(0xFF0F5138),
        c(0xFFCCE8DA), c(0xFF1E4A38), c(0xFFF5FBF5), c(0xFFE9EFE9),
        c(0xFFFFFFFF), c(0xFF171D19), c(0xFF404943), c(0xFFC0C9C1),
        c(0xFFBA1A1A), WARN_BG_LIGHT, WARN_FG_LIGHT, dark = false,
    ),
    Seed.BLUE to CashTokens(
        c(0xFF0B57D0), c(0xFFFFFFFF), c(0xFFD3E3FD), c(0xFF0B305F),
        c(0xFFE0E6F5), c(0xFF2E4A6B), c(0xFFF8F9FF), c(0xFFEDF0F7),
        c(0xFFFFFFFF), c(0xFF1A1C20), c(0xFF44474E), c(0xFFC4C6D0),
        c(0xFFBA1A1A), WARN_BG_LIGHT, WARN_FG_LIGHT, dark = false,
    ),
    Seed.PURPLE to CashTokens(
        c(0xFF6750A4), c(0xFFFFFFFF), c(0xFFEADDFF), c(0xFF21005D),
        c(0xFFE8DEF8), c(0xFF4A4458), c(0xFFFEF7FF), c(0xFFF3EDF7),
        c(0xFFFFFFFF), c(0xFF1D1B20), c(0xFF49454F), c(0xFFCAC4D0),
        c(0xFFBA1A1A), WARN_BG_LIGHT, WARN_FG_LIGHT, dark = false,
    ),
    // Theme Factory: Ocean Depths — teal #2D8B8B, seafoam #A8DADC, cream #F1FAEE, deep navy #1A2332
    Seed.OCEAN_DEPTHS to CashTokens(
        c(0xFF1F6E70), c(0xFFFFFFFF), c(0xFFA8DADC), c(0xFF003738),
        c(0xFFCDE7E8), c(0xFF1E4A4B), c(0xFFF1FAEE), c(0xFFE3EFEA),
        c(0xFFFFFFFF), c(0xFF171D1C), c(0xFF3F4948), c(0xFFBEC9C7),
        c(0xFFBA1A1A), WARN_BG_LIGHT, WARN_FG_LIGHT, dark = false,
    ),
    // Theme Factory: Sunset Boulevard — burnt orange #E76F51, coral #F4A261, warm sand #E9C46A, deep slate #264653
    Seed.SUNSET to CashTokens(
        c(0xFFB3492E), c(0xFFFFFFFF), c(0xFFFFDBD1), c(0xFF3E0A00),
        c(0xFFF6DDB8), c(0xFF574419), c(0xFFFFF8F5), c(0xFFF7ECE5),
        c(0xFFFFFFFF), c(0xFF221A16), c(0xFF53433D), c(0xFFD0C1BA),
        c(0xFFBA1A1A), WARN_BG_LIGHT, WARN_FG_LIGHT, dark = false,
    ),
    // Theme Factory: Forest Canopy — forest green #2D4A2B, sage #7D8471, olive #A4AC86, ivory #FAF9F6
    Seed.CANOPY to CashTokens(
        c(0xFF2D4A2B), c(0xFFFFFFFF), c(0xFFC9D2AC), c(0xFF12200F),
        c(0xFFDDE2CE), c(0xFF3C4433), c(0xFFFAF9F6), c(0xFFEDECE4),
        c(0xFFFFFFFF), c(0xFF1A1C17), c(0xFF44483E), c(0xFFC5C8BA),
        c(0xFFBA1A1A), WARN_BG_LIGHT, WARN_FG_LIGHT, dark = false,
    ),
    // Theme Factory: Modern Minimalist — charcoal #36454F, slate #708090, light gray #D3D3D3, white
    Seed.MINIMAL to CashTokens(
        c(0xFF36454F), c(0xFFFFFFFF), c(0xFFD7E3ED), c(0xFF0B1D28),
        c(0xFFE0E3E7), c(0xFF3C444B), c(0xFFFBFCFD), c(0xFFEFF1F3),
        c(0xFFFFFFFF), c(0xFF191C1E), c(0xFF42474C), c(0xFFC3C7CC),
        c(0xFFBA1A1A), WARN_BG_LIGHT, WARN_FG_LIGHT, dark = false,
    ),
)

private val DARK = mapOf(
    Seed.GREEN to CashTokens(
        c(0xFF8BD6B4), c(0xFF00382A), c(0xFF14513C), c(0xFFA6F2D0),
        c(0xFF2A473A), c(0xFFCCE8DA), c(0xFF0F1512), c(0xFF1B211D),
        c(0xFF252B27), c(0xFFDFE4DE), c(0xFFBFC9C1), c(0xFF49514A),
        c(0xFFFFB4AB), WARN_BG_DARK, WARN_FG_DARK, dark = true,
    ),
    Seed.BLUE to CashTokens(
        c(0xFFA8C7FA), c(0xFF062E6F), c(0xFF0842A0), c(0xFFD3E3FD),
        c(0xFF2E4560), c(0xFFD3E3FD), c(0xFF101418), c(0xFF1C2024),
        c(0xFF262A2F), c(0xFFE2E2E9), c(0xFFC4C6D0), c(0xFF454A50),
        c(0xFFFFB4AB), WARN_BG_DARK, WARN_FG_DARK, dark = true,
    ),
    Seed.PURPLE to CashTokens(
        c(0xFFD0BCFF), c(0xFF381E72), c(0xFF4F378B), c(0xFFEADDFF),
        c(0xFF4A4458), c(0xFFE8DEF8), c(0xFF141218), c(0xFF211F26),
        c(0xFF2B2930), c(0xFFE6E0E9), c(0xFFCAC4D0), c(0xFF49454F),
        c(0xFFFFB4AB), WARN_BG_DARK, WARN_FG_DARK, dark = true,
    ),
    // Ocean Depths dark — deep navy #1A2332 base
    Seed.OCEAN_DEPTHS to CashTokens(
        c(0xFF86D2D3), c(0xFF003738), c(0xFF1A5153), c(0xFFA8DADC),
        c(0xFF2C4A4B), c(0xFFCDE7E8), c(0xFF121A26), c(0xFF1B2432),
        c(0xFF252F3D), c(0xFFDEE3E5), c(0xFFBEC9C9), c(0xFF495253),
        c(0xFFFFB4AB), WARN_BG_DARK, WARN_FG_DARK, dark = true,
    ),
    // Sunset Boulevard dark — warm near-black base
    Seed.SUNSET to CashTokens(
        c(0xFFFFB59F), c(0xFF5F1A05), c(0xFF8A3A22), c(0xFFFFDBD1),
        c(0xFF4E4426), c(0xFFF6DDB8), c(0xFF1B1411), c(0xFF251C18),
        c(0xFF302620), c(0xFFF0DFD9), c(0xFFD7C2BA), c(0xFF59463F),
        c(0xFFFFB4AB), WARN_BG_DARK, WARN_FG_DARK, dark = true,
    ),
    // Forest Canopy dark — olive/sage on deep green-black
    Seed.CANOPY to CashTokens(
        c(0xFFA9C595), c(0xFF15300F), c(0xFF2D4A2B), c(0xFFC6E4B4),
        c(0xFF3E4636), c(0xFFDDE2CE), c(0xFF11150F), c(0xFF1B2018),
        c(0xFF252B21), c(0xFFE0E4D8), c(0xFFC2C9B6), c(0xFF4B5244),
        c(0xFFFFB4AB), WARN_BG_DARK, WARN_FG_DARK, dark = true,
    ),
    // Modern Minimalist dark — slate on charcoal
    Seed.MINIMAL to CashTokens(
        c(0xFFA9B9C4), c(0xFF101F28), c(0xFF26343E), c(0xFFCDDEE9),
        c(0xFF3A4149), c(0xFFE0E3E7), c(0xFF101416), c(0xFF1A1F22),
        c(0xFF242A2E), c(0xFFE1E3E5), c(0xFFC0C6CB), c(0xFF474D52),
        c(0xFFFFB4AB), WARN_BG_DARK, WARN_FG_DARK, dark = true,
    ),
)

val LocalCashTokens = staticCompositionLocalOf { LIGHT.getValue(Seed.GREEN) }

/** The lighter "container" tone paired with a seed's accent color, for the two-tone theme swatch. */
fun seedToneColor(seed: Seed): Color = LIGHT.getOrElse(seed) { LIGHT.getValue(Seed.GREEN) }.pc

/** True when Material You dynamic color is available on this device. */
val dynamicColorAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** The device's current dynamic primary color, for the theme-picker swatch. */
@Composable
fun dynamicSwatch(dark: Boolean): Color? {
    if (!dynamicColorAvailable) return null
    val context = LocalContext.current
    return (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).primary
}

private fun dynamicTokens(s: ColorScheme, dark: Boolean) = CashTokens(
    p = s.primary, onP = s.onPrimary, pc = s.primaryContainer, onPc = s.onPrimaryContainer,
    sc = s.secondaryContainer, onSc = s.onSecondaryContainer,
    surf = s.surface, cont = s.surfaceContainer,
    hi = if (dark) s.surfaceContainerHigh else s.surfaceContainerLowest,
    onS = s.onSurface, onSV = s.onSurfaceVariant, out = s.outline, err = s.error,
    warnBg = if (dark) WARN_BG_DARK else WARN_BG_LIGHT,
    warnFg = if (dark) WARN_FG_DARK else WARN_FG_LIGHT,
    dark = dark,
)

/**
 * Resolves tokens for [seed] + [mode] and installs them into [MaterialTheme] + [LocalCashTokens].
 * [Seed.DYNAMIC] uses Material You wallpaper colors on Android 12+, like Pixel system apps;
 * on older devices it falls back to the Forest palette.
 */
@Composable
fun CashflowTheme(seed: Seed, mode: ThemeMode, content: @Composable () -> Unit) {
    val sysDark = isSystemInDarkTheme()
    val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && sysDark)
    val context = LocalContext.current

    val dynScheme: ColorScheme? = if (seed == Seed.DYNAMIC && dynamicColorAvailable) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else null

    val palettes = if (dark) DARK else LIGHT
    val t = if (dynScheme != null) {
        dynamicTokens(dynScheme, dark)
    } else {
        palettes.getOrElse(seed) { palettes.getValue(Seed.GREEN) }
    }

    val scheme = dynScheme ?: if (dark) {
        darkColorScheme(
            primary = t.p, onPrimary = t.onP, primaryContainer = t.pc, onPrimaryContainer = t.onPc,
            secondaryContainer = t.sc, onSecondaryContainer = t.onSc,
            surface = t.surf, background = t.surf, onSurface = t.onS, onSurfaceVariant = t.onSV,
            surfaceVariant = t.cont, outline = t.out, error = t.err,
        )
    } else {
        lightColorScheme(
            primary = t.p, onPrimary = t.onP, primaryContainer = t.pc, onPrimaryContainer = t.onPc,
            secondaryContainer = t.sc, onSecondaryContainer = t.onSc,
            surface = t.surf, background = t.surf, onSurface = t.onS, onSurfaceVariant = t.onSV,
            surfaceVariant = t.cont, outline = t.out, error = t.err,
        )
    }
    CompositionLocalProvider(LocalCashTokens provides t) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
