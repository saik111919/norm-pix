package com.example.normpix.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.ui.theme.AppTheme
import com.example.normpix.ui.theme.ArtworkColors
import com.example.normpix.ui.theme.rememberReducedMotion
import com.materialkolor.ktx.animateColorScheme

/*
 * The composable half of the artwork colours (the pure half is ui/theme/ArtworkColors.kt). A form that shows artwork says what
 * colour it is with ONE call, [ProvideArtworkSeed]; the sheet host ([OverlayHost]) wraps every sheet in [ArtworkScope], which reads
 * that, makes the scheme, animates it, and dresses the whole sheet in it - the form's content AND the sheet's own surface, handle
 * and scrim, which the host draws around the content and which a theme wrapped only around the content would leave behind.
 */

/** Where one sheet's form says what colour its artwork is; the host reads it. */
@Stable
class ArtworkTint {
    var seed: Color? by mutableStateOf(null)
}

private val LocalArtworkTint = staticCompositionLocalOf<ArtworkTint?> { null }

/** The app's own scheme as the sheet's host sees it: kept for what must not follow a picture, whatever the sheet around it wears. */
private val LocalAppBaseScheme = staticCompositionLocalOf<ColorScheme?> { null }

/**
 * The app's own colour scheme - not the artwork's - for the few things inside an artwork sheet whose colour means something
 * and so must not follow the picture (a transfer's neutral, next to the warm expense and the positive income). Outside an
 * artwork sheet it is just the theme.
 */
val appColorScheme: ColorScheme
    @Composable get() = LocalAppBaseScheme.current ?: MaterialTheme.colorScheme

/**
 * Says that the form this is in shows artwork whose colour is [seed] (null: it shows none, or has not read it yet), so its sheet
 * wears colours made from it. Call it once, anywhere in the form; it follows [seed] as it changes (a picture added, replaced or
 * removed) and lets go when the form leaves. Does nothing outside a sheet, or with Settings > Appearance > Colors > Artwork
 * colors off (the host ignores the seed then).
 */
@Composable
fun ProvideArtworkSeed(seed: Color?) {
    val tint = LocalArtworkTint.current ?: return
    SideEffect { tint.seed = seed }
    DisposableEffect(tint) { onDispose { tint.seed = null } }
}

/** A sheet's colours arrive in place when they come in its first moments - it opens already in them, with no flash from the app's. */
private const val OPENING_MS = 500L

/** How a colour moves to another: a spring that settles without overshooting (a colour that overshoots reads as a flicker). */
private val ColorSpring: FiniteAnimationSpec<Color> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

/**
 * Dresses [content] - a whole sheet, surface included - in the colours of the artwork its form says it shows ([tint]), on top of
 * the app's own colours: none (the app's colours, exactly as without this) when the form says nothing, the seed is a grey or the
 * same hue as the last, or the setting is off. A change of seed after the sheet has opened animates (a spring, per colour, as
 * Compose's own colour animations do) and does not at all when the phone asks for reduced motion.
 */
@Composable
internal fun ArtworkScope(tint: ArtworkTint, content: @Composable () -> Unit) {
    val prefs = LocalAppPreferences.current?.state?.collectAsState()?.value
    val enabled = prefs?.artworkColors ?: true
    val base = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    // The hand-drawn themes (Pure, Volt) keep their surfaces; the wallpaper's colours are only the app's unless the artwork says otherwise.
    val keepSurfaces = prefs != null &&
        (prefs.accent == AccentColor.Pure || prefs.accent == AccentColor.Volt) &&
        !(prefs.colorSource == ColorSource.System && AppTheme.dynamicColorSupported)

    val wanted = tint.seed?.takeIf { enabled && ArtworkColors.usable(it) }
    // A seed that would make the same scheme as the one in force is ignored: no animation for a hair's difference.
    var applied by remember { mutableStateOf(wanted) }
    if (!ArtworkColors.isSame(applied, wanted)) applied = wanted

    val born = remember { SystemClock.uptimeMillis() }
    val target = remember(base, applied, keepSurfaces) { applied?.let { ArtworkColors.scheme(base, it, keepSurfaces) } ?: base }
    val shown = animateColorScheme(
        colorScheme = target,
        animationSpec = { if (reduced || SystemClock.uptimeMillis() - born < OPENING_MS) snap<Color>() else ColorSpring },
    )
    CompositionLocalProvider(LocalArtworkTint provides tint, LocalAppBaseScheme provides base) {
        MaterialTheme(colorScheme = shown, content = content)
    }
}
