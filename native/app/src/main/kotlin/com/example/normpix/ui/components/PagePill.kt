package com.example.normpix.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppSpacing

/**
 * A small tonal pill that opens a page of its own: an icon and a word or two, in the secondary container. Something a page has
 * but that would crowd it - the update page's Changelog, the About page's Feature guide - sits behind one. A clickable [Surface],
 * so it keeps Material's 48 dp touch target around its 36 dp height.
 */
@Composable
fun PagePill(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Surface(
        onClick = {
            haptics.selection()
            onClick()
        },
        shape = CircleShape,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Row(
            Modifier.heightIn(min = 36.dp).padding(start = AppSpacing.md, end = AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.sm))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
