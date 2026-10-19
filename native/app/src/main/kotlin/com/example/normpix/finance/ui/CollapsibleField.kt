package com.example.normpix.finance.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing

/**
 * A part of a form that stays folded until it is wanted, so a long form opens short: a tonal row with the part's [label],
 * what is chosen in it now ([value], with [leading] to show it: an avatar, a colour) and a chevron that turns. A tap opens
 * [content] right under the row, easing the form open (at once where animations are off), and the next tap folds it again.
 * A screen reader hears a button that is "Expanded" or "Collapsed".
 */
@Composable
internal fun CollapsibleField(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val turn by animateFloatAsState(if (expanded) 180f else 0f, AppMotion.spatial<Float>(), label = "field_chevron")
    Column(modifier.fillMaxWidth()) {
        Surface(
            onClick = {
                haptics.selection()
                onToggle()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    role = Role.Button
                    stateDescription = if (expanded) "Expanded" else "Collapsed"
                },
            shape = TonalFieldShape,
            color = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
        ) {
            Row(
                Modifier.heightIn(min = 64.dp).padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading()
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1)
                    Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.rotate(turn))
            }
        }
        RevealVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(AppSpacing.md))
                content()
            }
        }
    }
}
