package com.example.normpix.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.notifications.AppNotification
import com.example.normpix.notifications.Notifications
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.LocalFrostedBackdrop
import com.example.normpix.ui.components.frostedGlass
import com.example.normpix.ui.components.rememberFrostedChrome
import com.example.normpix.ui.components.solidColorScheme
import com.example.normpix.ui.theme.AppFrostedTokens
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.resolve

/** How many notifications the "+" menu shows above its panel: a glance. The rest, and these in full, are on the Notifications page. */
internal const val NOTIFICATION_STRIP_MAX = 3

/**
 * The newest unread notifications, above the "+" menu's panel and on the menu's own clock ([progress]: they come up with the panel and
 * go with it): what needs the user is in front of them when they reach for an action. Each opens what it is about (the menu closes
 * first); the last row opens the whole page. Nothing at all when nothing is unread.
 *
 * They sit on one container of their own (owner, 2026-11-13: "give background to this and follow the same blur background as
 * sheets"): the surface a sheet has - the page behind it blurred and a 62% tint of the sheet's tone over it, solid where nothing is
 * blurred (blur off in Settings, before Android 12, or switched off by the system) - with the rows as tonal items on it.
 */
@Composable
internal fun NotificationStrip(
    notifications: Notifications,
    progress: () -> Float,
    onOpen: (AppNotification) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = notifications.unread.take(NOTIFICATION_STRIP_MAX)
    if (shown.isEmpty()) return
    val scheme = solidColorScheme
    val backdrop = LocalFrostedBackdrop.current
    val glass = rememberFrostedChrome(backdrop)
    val shape = AppRadius.rXxl
    // What the rows are made of: solid tones, so they read the same over any part of the blurred page behind the container.
    val row = scheme.surfaceContainerHigh
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * 16.dp.toPx()
            }
            .clip(shape)
            .frostedGlass(backdrop, shape)
            .background(if (glass) scheme.surfaceContainerLow.copy(alpha = AppFrostedTokens.surfaceAlpha) else scheme.surfaceContainerLow)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        for (item in shown) StripRow(item, row, onClick = { onOpen(item) })
        val more = notifications.unread.size - shown.size
        Surface(
            onClick = onSeeAll,
            shape = AppRadius.rFull,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
        ) {
            Text(
                if (more > 0) "All notifications ($more more)" else "All notifications",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            )
        }
    }
}

@Composable
private fun StripRow(item: AppNotification, container: Color, onClick: () -> Unit) {
    val banner = item.banner
    val tone = banner.tone.resolve()
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        // Rows inside the container's large corners are the next size down, as the segments of a list are inside theirs.
        shape = AppRadius.rXl,
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            // One thing to read: the sentence, not the three lines again under it.
            .clearAndSetSemantics {
                contentDescription = "${banner.eyebrow}. ${banner.title}. ${banner.message}"
                role = Role.Button
                // `this.`: the parameter is also called onClick, and must not be what this resolves to.
                this.onClick { onClick(); true }
            },
    ) {
        Row(
            Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(tone.color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(banner.icon, contentDescription = null, tint = tone.color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(banner.eyebrow, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1)
                Text(
                    banner.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
    }
}
