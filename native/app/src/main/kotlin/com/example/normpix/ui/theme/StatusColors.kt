package com.example.normpix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.materialkolor.blend.Blend
import com.materialkolor.dynamicColorScheme

/**
 * Semantic meaning of a piece of UI state. Every status surface, tag, and icon
 * in the app picks one of these instead of a raw color, so meaning is
 * consistent and always paired with a text label or icon (never color alone).
 */
enum class StatusTone { Neutral, Info, Success, Warning, Error }

/**
 * A tonal color quartet for one [StatusTone], following the same
 * `color / onColor / container / onContainer` pairing as the primary,
 * secondary and error roles in the color scheme.
 */
@Immutable
data class StatusColorRole(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * Success / warning / info roles that the Material color scheme doesn't
 * provide.
 *
 * Each role is generated as a full tonal palette from a fixed hue that is
 * first harmonized toward the app's primary color (M3 "custom color"
 * guidance), so it sits comfortably next to any wallpaper-derived or curated
 * scheme and keeps accessible on/container contrast in both light and dark
 * themes.
 */
@Immutable
class AppStatusColors(
    val success: StatusColorRole,
    val warning: StatusColorRole,
    val info: StatusColorRole,
) {
    companion object {
        fun fromScheme(scheme: ColorScheme, isDark: Boolean): AppStatusColors {
            fun role(seed: Color): StatusColorRole {
                val harmonized = Color(Blend.harmonize(seed.toArgb(), scheme.primary.toArgb()))
                val generated = dynamicColorScheme(
                    seedColor = harmonized,
                    isDark = isDark,
                    style = PaletteStyle.Fidelity,
                )
                return StatusColorRole(
                    color = generated.primary,
                    onColor = generated.onPrimary,
                    container = generated.primaryContainer,
                    onContainer = generated.onPrimaryContainer,
                )
            }
            return AppStatusColors(
                success = role(Color(0xFF2E7D32)),
                warning = role(Color(0xFFB26A00)),
                info = role(Color(0xFF1565C0)),
            )
        }
    }
}

val LocalAppStatusColors = compositionLocalOf<AppStatusColors?> { null }

/** Resolves this tone against the ambient theme. */
@Composable
fun StatusTone.resolve(): StatusColorRole {
    val scheme = MaterialTheme.colorScheme
    val status = LocalAppStatusColors.current
    return when (this) {
        StatusTone.Neutral -> StatusColorRole(
            color = scheme.onSurfaceVariant,
            onColor = scheme.surface,
            container = scheme.surfaceContainerHighest,
            onContainer = scheme.onSurfaceVariant,
        )
        StatusTone.Info -> status?.info ?: scheme.asRole()
        StatusTone.Success -> status?.success ?: scheme.asRole()
        StatusTone.Warning -> status?.warning ?: scheme.asRole()
        StatusTone.Error -> StatusColorRole(
            color = scheme.error,
            onColor = scheme.onError,
            container = scheme.errorContainer,
            onContainer = scheme.onErrorContainer,
        )
    }
}

private fun ColorScheme.asRole() = StatusColorRole(primary, onPrimary, primaryContainer, onPrimaryContainer)
