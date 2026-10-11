package com.example.normpix.reminders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.label
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.BankGlyph
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.SwipeActionButton
import com.example.normpix.ui.components.SwipeActionWidth
import com.example.normpix.ui.components.SwipeRevealRow
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.resolve
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Read-only summary of a reminder: category icon, title, and a "when" line
 * whose icon and tone carry its urgency.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderTile(
    reminder: Reminder,
    modifier: Modifier = Modifier,
    /** What it is attached to, if anything: shown as a line under the time. */
    link: ResolvedLink? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val urgency = reminder.urgency()
    val tone = urgency.tone.resolve()
    val isCompleted = urgency == ReminderUrgency.Completed
    val isUrgent = urgency == ReminderUrgency.Overdue || urgency == ReminderUrgency.DueSoon
    val whenColor = if (isUrgent) tone.color else scheme.onSurfaceVariant
    val dueText = reminderDueText(reminder)

    ListItem(
        modifier = modifier.then(
            if (onClick != null || onLongClick != null) {
                Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
            } else {
                Modifier
            },
        ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            LeadingIcon(
                reminder.category.icon,
                background = if (isUrgent) tone.container else scheme.secondaryContainer,
                foreground = if (isUrgent) tone.onContainer else scheme.onSecondaryContainer,
            )
        },
        headlineContent = {
            Text(
                reminder.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = if (isCompleted) {
                    MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            )
        },
        supportingContent = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(urgency.icon, contentDescription = null, tint = whenColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = whenColor, fontWeight = if (isUrgent) FontWeight.SemiBold else null)) { append(dueText) }
                            if (reminder.repeatRule.repeats && !isCompleted) append(" · ${reminder.repeatRule.label}")
                        },
                    )
                }
                if (link != null) ReminderLinkLine(link, Modifier.padding(top = AppSpacing.xxs))
            }
        },
        trailingContent = trailing,
    )
}

/**
 * A reminder row in the Reminders list, with every action reachable three
 * ways: the visible "mark as done" button, a right-to-left swipe, and a
 * long-press action sheet (also exposed to TalkBack as custom actions).
 * Tapping the row opens it for editing.
 */
@Composable
fun ReminderListItem(
    reminder: Reminder,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val isCompleted = reminder.status == ReminderStatus.Completed
    // What it is attached to, read fresh, and the way to open it.
    val attached = rememberResolvedLink(reminder.link)
    val openLink = rememberOpenLink()
    val canOpen = attached != null && !attached.missing

    fun showActions() {
        scope.launch {
            val action = overlays.sheet<() -> Unit> { dismiss ->
                Column {
                    Box(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm)) {
                        SheetHeader(title = reminder.title)
                    }
                    if (!isCompleted) {
                        ActionRow(Icons.Outlined.CheckCircle, "Mark as done") { dismiss(onComplete) }
                        ActionRow(Icons.Rounded.Snooze, "Snooze") { dismiss(onSnooze) }
                    }
                    if (canOpen && attached != null) ActionRow(Icons.Outlined.OpenInNew, openLabel(attached)) { dismiss { openLink(attached) } }
                    ActionRow(Icons.Outlined.Edit, "Edit") { dismiss(onClick) }
                    ActionRow(Icons.Outlined.DeleteOutline, "Delete") { dismiss(onDelete) }
                    Spacer(Modifier.height(AppSpacing.sm))
                }
            }
            action?.invoke()
        }
    }

    val prefs by LocalAppContainer.current.preferences.state.collectAsState()
    val actionWidth = SwipeActionWidth * if (isCompleted) 1 else 3
    SwipeRevealRow(
        actionsWidth = actionWidth,
        key = reminder.id,
        enabled = prefs.swipeActionsEnabled,
        modifier = modifier.semantics {
            customActions = buildList {
                if (!isCompleted) {
                    add(CustomAccessibilityAction("Mark as done") { onComplete(); true })
                    add(CustomAccessibilityAction("Snooze") { onSnooze(); true })
                }
                if (canOpen && attached != null) add(CustomAccessibilityAction(openLabel(attached)) { openLink(attached); true })
                add(CustomAccessibilityAction("Delete") { onDelete(); true })
            }
        },
        actions = {
            if (!isCompleted) {
                SwipeActionButton(Icons.Outlined.CheckCircle, "Done", scheme.secondaryContainer, scheme.onSecondaryContainer, onComplete)
                SwipeActionButton(Icons.Rounded.Snooze, "Snooze", scheme.surfaceContainerHighest, scheme.onSurfaceVariant, onSnooze)
            }
            SwipeActionButton(Icons.Outlined.DeleteOutline, "Delete", scheme.errorContainer, scheme.onErrorContainer, onDelete)
        },
    ) {
        Box(Modifier.fillMaxWidth().background(scheme.surfaceContainer)) {
            ReminderTile(
                reminder = reminder,
                link = attached,
                onClick = onClick,
                onLongClick = ::showActions,
                trailing = if (isCompleted) {
                    null
                } else {
                    {
                        IconButton(onClick = onComplete) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "Mark as done")
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(label) },
    )
}

/** What opening an attachment is called: "Open expense", "Open repeating payment", "Open account", "Open card". */
internal fun openLabel(link: ResolvedLink): String = when (link.link.kind) {
    ReminderLinkKind.Transaction -> "Open ${link.transaction?.type?.label?.lowercase() ?: "transaction"}"
    ReminderLinkKind.Recurring -> "Open repeating payment"
    ReminderLinkKind.Account -> "Open account"
    ReminderLinkKind.Card -> "Open card"
}
