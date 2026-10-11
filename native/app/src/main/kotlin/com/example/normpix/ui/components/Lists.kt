package com.example.normpix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

/**
 * Heading for a block of content on a page, with an optional trailing action
 * ("View all"). Marked as a semantic heading for screen readers.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    padding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(
            start = AppSpacing.lg + AppSpacing.xs,
            top = AppSpacing.lg,
            end = AppSpacing.sm,
            bottom = AppSpacing.sm,
        ),
) {
    Row(
        modifier.fillMaxWidth().padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Collects the rows of a [ListSection]. */
class ListSectionScope {
    internal val rows = mutableListOf<@Composable () -> Unit>()

    fun item(content: @Composable () -> Unit) {
        rows += content
    }

    fun <T> items(list: List<T>, content: @Composable (T) -> Unit) {
        for (element in list) rows += { content(element) }
    }
}

/**
 * The small primary-coloured heading above a group on a page: what a [ListSection]'s title looks like, for a group that
 * is not a list (a connected choice row, say), so every group on a page is introduced the same way.
 */
@Composable
fun SectionSubheader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.sm)
            .semantics { heading() },
    )
}

/**
 * A grouped list in the Material 3 Expressive style: each row sits on its own
 * tonal segment, separated by a small gap, with large outer and small inner
 * corners - the grouping is carried by shape and tone instead of borders,
 * dividers, or shadows.
 *
 * [title] renders as a list subheader above the group. [intro] is a line that sits between the title and the rows - the
 * answer the rows then show.
 */
@Composable
fun ListSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    intro: String? = null,
    content: ListSectionScope.() -> Unit,
) {
    val rows = ListSectionScope().apply(content).rows
    Column(modifier.fillMaxWidth()) {
        if (title != null) SectionSubheader(title)
        if (intro != null) {
            Text(
                intro,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.sm),
            )
        }
        rows.forEachIndexed { index, row ->
            Box(Modifier.padding(top = if (index == 0) 0.dp else LIST_GAP)) {
                ListSegment(isFirst = index == 0, isLast = index == rows.lastIndex) { row() }
            }
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.sm, end = AppSpacing.lg),
            )
        }
    }
}

private val LIST_GAP = 2.dp

fun segmentShape(isFirst: Boolean, isLast: Boolean) = RoundedCornerShape(
    topStart = if (isFirst) AppRadius.xl else AppRadius.xs,
    topEnd = if (isFirst) AppRadius.xl else AppRadius.xs,
    bottomStart = if (isLast) AppRadius.xl else AppRadius.xs,
    bottomEnd = if (isLast) AppRadius.xl else AppRadius.xs,
)

/**
 * One tonal segment of a [ListSection]. Also a surface, so ink from list items
 * and buttons inside it is clipped to the segment's shape.
 */
@Composable
fun ListSegment(
    modifier: Modifier = Modifier,
    isFirst: Boolean = true,
    isLast: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = color,
        shape = segmentShape(isFirst, isLast),
        content = content,
    )
}

/** Round tonal badge for a list row's leading icon. */
@Composable
fun LeadingIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    foreground: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 40.dp,
) {
    Box(
        modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(size / 2))
    }
}

/** Same badge, with arbitrary content (a bank logo, a letter). */
@Composable
fun LeadingBadge(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(size).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

val RowGap = Arrangement.spacedBy(AppSpacing.md)

/**
 * A single row inside a [ListSection] segment (the Material `ListTile`): tonal
 * container comes from the segment, so the row itself is transparent.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    headlineColor: Color = Color.Unspecified,
    supportingMaxLines: Int = 2,
    onLongClick: (() -> Unit)? = null,
) {
    androidx.compose.material3.ListItem(
        modifier = modifier.then(
            if (onLongClick != null) {
                Modifier.combinedClickable(enabled = enabled, onClick = { onClick?.invoke() }, onLongClick = onLongClick)
            } else if (onClick != null) {
                Modifier.clickable(enabled = enabled, onClick = onClick)
            } else {
                Modifier
            },
        ),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = leading,
        headlineContent = { Text(headline, color = headlineColor) },
        supportingContent = supporting?.let {
            { Text(it, maxLines = supportingMaxLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        },
        trailingContent = trailing,
    )
}

/**
 * An on/off setting: the whole row toggles (one touch target, announced as a
 * switch), with the [androidx.compose.material3.Switch] as its visual state.
 */
@Composable
fun SwitchRow(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    val disabledAlpha = if (enabled) 1f else 0.38f
    androidx.compose.material3.ListItem(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = leading?.let { { Box(Modifier.alpha(disabledAlpha)) { it() } } },
        headlineContent = { Text(headline, modifier = Modifier.alpha(disabledAlpha)) },
        supportingContent = supporting?.let { { Text(it, modifier = Modifier.alpha(disabledAlpha)) } },
        trailingContent = { androidx.compose.material3.Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

/** One option of a single-choice setting. Put a group of these in a [ListSection] with `Modifier.selectableGroup()`. */
@Composable
fun RadioRow(
    headline: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.ListItem(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { androidx.compose.material3.RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = trailing,
    )
}
