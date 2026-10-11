package com.example.normpix.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppSpacing

/** One button of a [ConnectedButtonGroup]: what it says, its tone, and what it does. */
class GroupButton(
    val label: String,
    val icon: ImageVector?,
    val container: Color,
    val content: Color,
    val onClick: () -> Unit,
)

/**
 * Buttons joined as one (Material 3 Expressive's connected button group): equal segments a hair apart, fully round at the two
 * outer ends and squared off where they meet, so they read as a single control with several parts. Each part keeps its own tone.
 */
@Composable
fun ConnectedButtonGroup(buttons: List<GroupButton>, modifier: Modifier = Modifier, height: Dp = 48.dp) {
    val haptics = rememberAppHaptics()
    val outer = CornerSize(50)
    val inner = CornerSize(4.dp)
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        buttons.forEachIndexed { index, button ->
            val first = index == 0
            val last = index == buttons.lastIndex
            Segment(
                button = button,
                shape = RoundedCornerShape(
                    topStart = if (first) outer else inner,
                    bottomStart = if (first) outer else inner,
                    topEnd = if (last) outer else inner,
                    bottomEnd = if (last) outer else inner,
                ),
                height = height,
                onClick = {
                    haptics.selection()
                    button.onClick()
                },
            )
        }
    }
}

@Composable
private fun RowScope.Segment(button: GroupButton, shape: RoundedCornerShape, height: Dp, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(height),
        shape = shape,
        color = button.container,
        contentColor = button.content,
    ) {
        Row(
            Modifier.padding(horizontal = AppSpacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (button.icon != null) {
                Icon(button.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppSpacing.sm))
            }
            Text(
                button.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
