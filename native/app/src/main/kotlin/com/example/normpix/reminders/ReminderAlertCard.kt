package com.example.normpix.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import com.example.normpix.ui.components.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.ConnectedButtonGroup
import com.example.normpix.ui.components.GroupButton
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/** What the person chose on a reminder card (the floating one over other apps, or the full-screen one on a locked phone). */
sealed interface ReminderCardAction {
    /** Mark as paid / received / done: finishes the reminder (a repeating one moves on to its next time). */
    data object Done : ReminderCardAction

    /** Come back to it in [by]. */
    data class Snooze(val by: Duration) : ReminderCardAction

    /** Put the card away; the reminder stays due, in its notification and in Plans. */
    data object Dismiss : ReminderCardAction

    /** Open NormPix on the reminder. */
    data object Open : ReminderCardAction
}

/**
 * The reminder as a card with its four actions: Mark as paid (or received, or done), Snooze (with three choices), Dismiss and
 * Open. It is one design for both places it is shown - floating over another app while the phone is unlocked, and on its own
 * screen over a locked one - so what a person learns once works in both. It shows the title and when it was due; the reminder's notes
 * only when [showNotes] (the floating card, on an unlocked phone): the one over a lock screen shows nothing more than the title, as
 * it may be on screen in front of other people. Snooze, Dismiss and Open are a connected button group.
 */
@Composable
fun ReminderAlertCard(
    reminder: Reminder,
    onAction: (ReminderCardAction) -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime = LocalDateTime.now(),
    /** Show the reminder's own notes under its title, which makes the card taller. Off where the screen may be seen by others. */
    showNotes: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val text = MaterialTheme.typography
    val overdue = reminder.isOverdue(now) && Duration.between(reminder.effectiveDueAt, now).toMinutes() >= 1
    var snoozing by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = AppRadius.rXl,
        color = scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
    ) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingIcon(
                    reminder.category.icon,
                    background = if (overdue) scheme.errorContainer else scheme.secondaryContainer,
                    foreground = if (overdue) scheme.onErrorContainer else scheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        reminder.title,
                        style = text.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        if (overdue) formatOverdueBy(reminder.effectiveDueAt, now) else "Due now",
                        style = text.bodyMedium,
                        color = if (overdue) scheme.error else scheme.onSurfaceVariant,
                    )
                }
            }
            // The reminder's notes, in as many lines as they take (up to a few), so a note is read without opening the app: the card
            // grows to hold it. Only where the caller says so - the floating card, on a phone that is unlocked and in use.
            val notes = if (showNotes) reminder.notes?.trim().orEmpty() else ""
            if (notes.isNotEmpty()) {
                Text(
                    notes,
                    style = text.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = NOTE_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!snoozing) {
                Button(
                    onClick = { onAction(ReminderCardAction.Done) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(reminder.doneLabel)
                }
                // Snooze, Dismiss and Open are one connected group (Material 3 Expressive): round at the two ends, squared where they meet.
                ConnectedButtonGroup(
                    buttons = listOf(
                        GroupButton("Snooze", Icons.Rounded.Snooze, scheme.secondaryContainer, scheme.onSecondaryContainer) { snoozing = true },
                        GroupButton("Dismiss", Icons.Rounded.Close, scheme.secondaryContainer, scheme.onSecondaryContainer) { onAction(ReminderCardAction.Dismiss) },
                        GroupButton("Open app", Icons.AutoMirrored.Rounded.OpenInNew, scheme.secondaryContainer, scheme.onSecondaryContainer) { onAction(ReminderCardAction.Open) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    height = GroupHeight,
                    contentPadding = AppSpacing.sm,
                )
            } else {
                Text("Snooze for", style = text.labelLarge, color = scheme.onSurfaceVariant)
                ConnectedButtonGroup(
                    buttons = snoozeChoices(now).map { (label, by) ->
                        GroupButton(label, null, scheme.secondaryContainer, scheme.onSecondaryContainer) { onAction(ReminderCardAction.Snooze(by)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    height = GroupHeight,
                    contentPadding = AppSpacing.sm,
                )
                TextButton(onClick = { snoozing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back") }
            }
        }
    }
}

/** How tall the connected buttons are, and how many lines of a note the card shows before it cuts the rest. */
private val GroupHeight = 52.dp
private const val NOTE_LINES = 5

/** The three snoozes a card offers: a short one, an hour, and the next morning at nine. */
internal fun snoozeChoices(now: LocalDateTime): List<Pair<String, Duration>> = listOf(
    "15 min" to Duration.ofMinutes(15),
    "1 hour" to Duration.ofHours(1),
    "Tomorrow" to Duration.between(now, LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(9, 0))),
)
