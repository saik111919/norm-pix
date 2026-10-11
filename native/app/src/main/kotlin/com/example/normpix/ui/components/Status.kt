package com.example.normpix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve

/**
 * Compact state/feature label ("On", "Overdue", "Coming soon"). Always text
 * (optionally with an icon), so meaning never depends on color alone.
 */
@Composable
fun StatusTag(
    label: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
    icon: ImageVector? = null,
) {
    val role = tone.resolve()
    Row(
        modifier
            .clip(AppRadius.rSm)
            .background(role.container)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = role.onContainer, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(AppSpacing.xs))
        }
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = role.onContainer,
        )
    }
}

/** A button offered by a [StatusSurface]. */
class StatusAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val isPrimary: Boolean = false,
)

/**
 * Contextual status surface - a compact, tonal summary of the single most
 * relevant thing right now (an overdue reminder, a security suggestion), with
 * the actions to resolve it in place. Inspired by Essentials' Island: it only
 * appears when there is something worth surfacing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusSurface(
    tone: StatusTone,
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    message: String? = null,
    actions: List<StatusAction> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    val role = tone.resolve()
    val text = MaterialTheme.typography
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = role.container,
        shape = AppRadius.rXl,
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(PaddingValues(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.md)),
        ) {
            Row(Modifier.semantics(mergeDescendants = true) {}) {
                Box(
                    Modifier.size(40.dp).background(role.color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = role.onColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    if (eyebrow != null) Text(eyebrow, style = text.labelLarge, color = role.onContainer)
                    Text(title, style = text.titleMedium, color = role.onContainer)
                    if (message != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(message, style = text.bodyMedium, color = role.onContainer)
                    }
                }
            }
            if (actions.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.sm))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                ) {
                    for (action in actions) {
                        if (action.isPrimary) {
                            Button(
                                onClick = action.onClick,
                                colors = ButtonDefaults.buttonColors(containerColor = role.color, contentColor = role.onColor),
                            ) { Text(action.label) }
                        } else {
                            TextButton(
                                onClick = action.onClick,
                                colors = ButtonDefaults.textButtonColors(contentColor = role.onContainer),
                            ) { Text(action.label) }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(AppSpacing.xs))
            }
        }
    }
}

/**
 * Shown when a list/page has nothing in it yet: what's missing, why it
 * matters, and (optionally) the one action that fixes it.
 */
@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(72.dp).background(scheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(AppIconSizes.xl))
            }
            Spacer(Modifier.height(AppSpacing.lg))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (message != null) {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(AppSpacing.xl))
                FilledTonalButton(onClick = onAction) {
                    if (actionIcon != null) {
                        Icon(actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AppSpacing.sm))
                    }
                    Text(actionLabel)
                }
            }
        }
    }
}

/** Shown when loading failed, with a retry. */
@Composable
fun AppErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp).padding(AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(AppIconSizes.hero))
            Spacer(Modifier.height(AppSpacing.lg))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (message != null) {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (onRetry != null) {
                Spacer(Modifier.height(AppSpacing.xl))
                FilledTonalButton(onClick = onRetry) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("Try again")
                }
            }
        }
    }
}

/** Indeterminate progress for a page or section that is loading. */
@Composable
fun AppLoadingState(modifier: Modifier = Modifier, label: String = "Loading") {
    Box(modifier.fillMaxWidth().padding(AppSpacing.xxl), contentAlignment = Alignment.Center) {
        AppLoadingIndicator(Modifier.politeLiveRegion().semantics { contentDescription = label })
    }
}

/** Visual-only element that screen readers should skip. */
fun Modifier.decorative(): Modifier = clearAndSetSemantics {}
