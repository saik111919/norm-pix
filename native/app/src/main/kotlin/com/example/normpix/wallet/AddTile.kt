package com.example.normpix.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

/**
 * The place to add one more, drawn as the thing it adds: a card-shaped outline with a plus in it, where the next account or
 * card would go - at the end of the stack or the list of cards, rather than a row under them. A full-size tile keeps a card's
 * proportions ([CARD_ASPECT]); a [compact] one is a card wide and a thumb tall, for under a stack, whose cards are a hair
 * of colour behind the front one.
 *
 * [title] is the action ("Add account"); [supporting] a line of what it is or why.
 */
@Composable
fun AddTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    compact: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val outline = scheme.outlineVariant
    val corner = AppRadius.lg
    Box(
        modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.heightIn(min = 72.dp) else Modifier.aspectRatio(CARD_ASPECT))
            .clip(AppRadius.rLg)
            .background(scheme.surfaceContainerLow.copy(alpha = 0.6f))
            .drawBehind {
                // A dashed edge says "empty, yours to fill" - the opposite of the solid, printed cards beside it.
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx()))),
                )
            }
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(AppSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                PlusBadge(size = 40.dp)
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    if (supporting != null) {
                        Text(supporting, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PlusBadge(size = 56.dp)
                Spacer(Modifier.height(AppSpacing.md))
                Text(title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface, textAlign = TextAlign.Center)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = AppSpacing.xxs),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlusBadge(size: androidx.compose.ui.unit.Dp) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.size(size).clip(CircleShape).background(scheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(size / 2))
    }
}
