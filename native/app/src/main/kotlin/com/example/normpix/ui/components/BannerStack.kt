package com.example.normpix.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusColorRole
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve

/**
 * One banner: a tonal summary of one thing that needs attention, with the way to deal with it in place. Every banner has the
 * same three lines and the same row of buttons, so banners of different kinds are the same size and can share a [BannerStack].
 */
@Immutable
class Banner(
    /** Stable across refreshes, so a banner keeps its place in its stack while others come and go. */
    val key: String,
    val tone: StatusTone,
    val icon: ImageVector,
    /** What kind of attention it needs: "Overdue by 2d", "Over budget". */
    val eyebrow: String,
    /** What it is about. */
    val title: String,
    /** The detail, in one line. */
    val message: String,
    /** The way to deal with it, as the filled button. */
    val primary: StatusAction,
    /** A quieter alternative, shown before [primary]. */
    val secondary: StatusAction? = null,
    /** Pressing the banner itself: the details. */
    val onOpen: () -> Unit,
)

/** The row of buttons is at least this tall, whatever is in it: the face and the plate behind it must agree. */
private val ActionRowHeight = 48.dp

/**
 * Banners of one kind as a stack - the one [DeckStack] the cards and the top spending use: the first in front in full, the
 * others a hair of colour behind it. Pressing and holding the front one lays them all out, each with its own buttons.
 */
@Composable
fun BannerStack(banners: List<Banner>, state: DeckStackState, modifier: Modifier = Modifier) {
    DeckStack(
        items = banners,
        itemKey = { it.key },
        shape = AppRadius.rXl,
        description = { banner, index, count -> banner.spoken(index, count) },
        onOpen = { it.onOpen() },
        state = state,
        modifier = modifier,
        // The item is one thing to a screen reader, so its buttons are offered as actions on it.
        extraActions = { banner ->
            listOfNotNull(banner.secondary, banner.primary).map { action ->
                CustomAccessibilityAction(action.label) {
                    action.onClick()
                    true
                }
            }
        },
        behindDim = BehindDim,
        plate = { banner -> BannerFace(banner, plate = true) },
        face = { banner -> BannerFace(banner, plate = false) },
    )
}

/** How much darker each banner behind the front one is: a pale surface needs a hint of shade to read as a card behind, not a slab. */
private const val BehindDim = 0.06f

/** How much of the status colour washes the banner's surface: enough to tell an overdue one from a budget one at a glance, not a slab. */
private const val CardWash = 0.08f

/** How much of the status colour is in the banner's hairline edge. */
private const val EdgeTint = 0.28f

/** How much of the status colour fills the banner's main button (over the washed surface). */
private const val ButtonTint = 0.18f

/** Every colour a banner of one status wears (see [BannerFace]). */
internal class BannerColors(
    val surface: Color,
    val edge: Color,
    /** The icon's disc, and the eyebrow and the quieter button's ink. */
    val accent: Color,
    val icon: Color,
    val buttonFill: Color,
    val buttonInk: Color,
)

internal fun bannerColors(role: StatusColorRole, scheme: ColorScheme): BannerColors {
    val surface = role.color.copy(alpha = CardWash).compositeOver(scheme.surfaceContainer)
    // The main button: the status colour on a tint of itself (not the role's onContainer: that is made for the role's own
    // container, which for a warning or an update is deep, so it came out white on a pale tint), checked for 4.5:1.
    val buttonFill = role.color.copy(alpha = ButtonTint).compositeOver(surface)
    return BannerColors(
        surface = surface,
        edge = role.color.copy(alpha = EdgeTint),
        accent = readableOn(surface, role.color, scheme.onSurface),
        icon = role.onColor,
        buttonFill = buttonFill,
        buttonInk = readableOn(buttonFill, role.color, scheme.onSurface),
    )
}

/** [preferred] if it reads on [fill] at WCAG's 4.5:1 for text, else [fallback]. */
internal fun readableOn(fill: Color, preferred: Color, fallback: Color): Color {
    val a = preferred.luminance()
    val b = fill.luminance()
    return if ((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f) preferred else fallback
}

private fun Banner.spoken(index: Int, count: Int): String = buildString {
    append("$eyebrow. $title. $message")
    if (count > 1) append(", ${index + 1} of $count")
}

/**
 * The banner itself: the page's own tonal surface (as Home's other sections are) carrying its status in every colour it has, so
 * what kind of attention it needs is seen before a word is read - an overdue reminder red, a budget or a card amber, an update blue:
 * the surface washed with the status colour and edged with a hairline of it, the icon white-on-colour in a solid disc of it, the
 * eyebrow in it, and the main button filled with a tint of it. The title and its detail stay the page's ink. Every one of those is
 * the status *colour* at a fixed strength, never the status *container*: the roles' containers differ in strength (an error's is
 * pale, a warning's deep), which made one banner louder than the next.
 *
 * (Until 2026-11-01 only the eyebrow and a pale icon disc were coloured, and the owner found it "not informative"; before that the
 * whole banner was a slab of the status container, out of key with the page.)
 *
 * As a [plate] it is the same banner with nothing visible in it - and nothing to press, so a button on a banner behind can never
 * be pressed by mistake - which is the same size by construction, not by measurement.
 */
@Composable
private fun BannerFace(banner: Banner, plate: Boolean) {
    val role = banner.tone.resolve()
    val scheme = MaterialTheme.colorScheme
    val text = MaterialTheme.typography
    val colors = remember(role, scheme) { bannerColors(role, scheme) }
    Surface(
        Modifier.fillMaxWidth(),
        color = colors.surface,
        contentColor = scheme.onSurface,
        shape = AppRadius.rXl,
        border = BorderStroke(1.dp, colors.edge),
    ) {
        Column(Modifier.padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.sm)) {
            Row(Modifier.graphicsLayer { alpha = if (plate) 0f else 1f }) {
                Box(Modifier.size(40.dp).background(role.color, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(banner.icon, contentDescription = null, tint = colors.icon, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        banner.eyebrow,
                        style = text.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(banner.title, style = text.titleMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(banner.message, style = text.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(AppSpacing.xs))
            if (plate) {
                Spacer(Modifier.fillMaxWidth().height(ActionRowHeight))
            } else {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = ActionRowHeight),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    banner.secondary?.let { action ->
                        TextButton(
                            onClick = action.onClick,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.accent),
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    FilledTonalButton(
                        onClick = banner.primary.onClick,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.buttonFill, contentColor = colors.buttonInk),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(banner.primary.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

