package com.example.normpix.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
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
    /** A bar under the lines, when what it is about fills up or runs down: a budget's use, an update's download. */
    val bar: BannerBar? = null,
    /** The panel beside the lines: one figure with a title and a note, when there is a value to say (what is left, a date). */
    val figure: BannerFigure? = null,
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
        shape = AppRadius.rXxl,
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

/**
 * One banner on its own, in full, with nothing laid over it: what a page that lists banners shows (the Notifications page). It is
 * one thing to read and press, as in a [BannerStack]: the lines are spoken together and its buttons are offered as actions, so a
 * screen reader never lands on a button inside it by mistake.
 */
@Composable
fun BannerCard(banner: Banner, modifier: Modifier = Modifier) {
    val spoken = remember(banner) { banner.spoken() }
    val actions = remember(banner) {
        listOfNotNull(banner.secondary, banner.primary).map { action ->
            CustomAccessibilityAction(action.label) {
                action.onClick()
                true
            }
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Outer))
            .clickable(onClickLabel = "Opens the details", onClick = banner.onOpen)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = "Opens the details") { banner.onOpen(); true }
                if (actions.isNotEmpty()) customActions = actions
            },
    ) {
        BannerFace(banner, plate = false)
    }
}

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

/** What a screen reader says of one banner: its three lines, then its bar or figure where it has one. */
internal fun Banner.spoken(): String = buildString {
    append("$eyebrow. $title. $message")
    figure?.let {
        append(". ${it.title} ${it.figure}")
        it.note?.let { note -> append(", $note") }
    }
    bar?.let { append(". ${it.label}") }
}

private fun Banner.spoken(index: Int, count: Int): String =
    if (count > 1) "${spoken()}, ${index + 1} of $count" else spoken()

/**
 * The banner itself, in the summary banner's design (`SplitStatBanner`): two panels joined by a seam - what it is on the left, with
 * its bar if it has one, and one figure on the right if it has one - and the way to deal with it in a bar underneath, all in the
 * status colour: the surface washed with it and edged with a hairline of it, curved lines across, the icon white-on-colour in a solid
 * disc of it, the eyebrow in it, the pill filled with it, and the main button filled with a tint of it. The title and its detail stay
 * the page's ink. Every one of those is the status *colour* at a fixed strength, never the status *container*: the roles'
 * containers differ in strength (an error's is pale, a warning's deep), which made one banner louder than the next.
 *
 * (Until 2026-11-07 it was one slab with the icon beside three lines; until 2026-11-01 only the eyebrow and a pale icon disc were
 * coloured, and the owner found it "not informative".)
 *
 * Each line is one line, so the banners of one kind - which have the same parts - are the same size and can share a [BannerStack].
 * With large text the panels stack instead of sitting side by side.
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
    val ink = scheme.onSurface
    val quiet = remember(colors) { quietInk(ink, colors.surface) }
    val edge = remember(colors) { BorderStroke(1.dp, colors.edge) }
    val figure = banner.figure
    val shown = if (plate) 0f else 1f
    val stacked = LocalDensity.current.fontScale > StackAtFontScale

    val info: @Composable (Shape, Modifier, Boolean) -> Unit = { shape, modifier, fillHeight ->
        Panel(shape, colors.surface, ink, null, modifier, fillHeight, edge, shown) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).background(role.color, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(banner.icon, contentDescription = null, tint = colors.icon, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(
                        banner.eyebrow,
                        style = text.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(AppSpacing.sm))
                // Beside a figure the panel is narrow, so a title may take two lines - and then always has the room for two, so the
                // banners of one kind stay the same size.
                val beside = figure != null && !stacked
                Text(
                    banner.title,
                    style = text.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = ink,
                    minLines = if (beside) 2 else 1,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(banner.message, style = text.bodyMedium, color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            banner.bar?.let { bar ->
                Spacer(Modifier.height(AppSpacing.md))
                PillBody(bar, fill = role.color, onFill = colors.icon, ink = ink, quiet = quiet)
            }
        }
    }
    val figurePanel: @Composable (Shape, Modifier, Boolean) -> Unit = { shape, modifier, fillHeight ->
        if (figure != null) {
            Panel(shape, colors.surface, ink, null, modifier, fillHeight, edge, shown) { FigureBody(figure, quiet) }
        }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Seam)) {
        if (figure == null || stacked) {
            info(TopPanel, Modifier.fillMaxWidth(), false)
            if (figure != null) figurePanel(MiddlePanel, Modifier.fillMaxWidth(), false)
        } else {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Seam)) {
                info(TopStartPanel, Modifier.weight(ProgressWeight).fillMaxHeight(), true)
                figurePanel(TopEndPanel, Modifier.weight(FigureWeight).fillMaxHeight(), true)
            }
        }
        Surface(Modifier.fillMaxWidth(), color = colors.surface, contentColor = ink, shape = StackedBottom, border = edge) {
            Box(Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)) {
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
                        // The main button is measured first and takes what it needs; the quieter one has what is left.
                        FilledTonalButton(
                            onClick = banner.primary.onClick,
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = colors.buttonFill, contentColor = colors.buttonInk),
                        ) {
                            Text(banner.primary.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

// The top row of the group sits on the action bar, so its lower corners are the small ones.
private val TopPanel = RoundedCornerShape(topStart = Outer, topEnd = Outer, bottomStart = Inner, bottomEnd = Inner)
private val TopStartPanel = RoundedCornerShape(topStart = Outer, topEnd = Inner, bottomStart = Inner, bottomEnd = Inner)
private val TopEndPanel = RoundedCornerShape(topStart = Inner, topEnd = Outer, bottomStart = Inner, bottomEnd = Inner)
private val MiddlePanel = RoundedCornerShape(Inner)
