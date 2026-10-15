package com.example.normpix.ui.components

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
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
        plate = { banner -> BannerFace(banner, plate = true) },
        face = { banner -> BannerFace(banner, plate = false) },
    )
}

private fun Banner.spoken(index: Int, count: Int): String = buildString {
    append("$eyebrow. $title. $message")
    if (count > 1) append(", ${index + 1} of $count")
}

/**
 * The banner itself. As a [plate] it is the same banner with nothing visible in it - and nothing to press, so a button on
 * a banner behind can never be pressed by mistake - which is the same size by construction, not by measurement.
 */
@Composable
private fun BannerFace(banner: Banner, plate: Boolean) {
    val role = banner.tone.resolve()
    val text = MaterialTheme.typography
    Surface(Modifier.fillMaxWidth(), color = role.container, shape = AppRadius.rXl) {
        Column(Modifier.padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.sm)) {
            Row(Modifier.graphicsLayer { alpha = if (plate) 0f else 1f }) {
                Box(Modifier.size(40.dp).background(role.color, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(banner.icon, contentDescription = null, tint = role.onColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(banner.eyebrow, style = text.labelLarge, color = role.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(banner.title, style = text.titleMedium, color = role.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(banner.message, style = text.bodyMedium, color = role.onContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            colors = ButtonDefaults.textButtonColors(contentColor = role.onContainer),
                            modifier = Modifier.weight(1f, fill = false),
                        ) { Text(action.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Button(
                        onClick = banner.primary.onClick,
                        colors = ButtonDefaults.buttonColors(containerColor = role.color, contentColor = role.onColor),
                        modifier = Modifier.weight(1f, fill = false),
                    ) { Text(banner.primary.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
