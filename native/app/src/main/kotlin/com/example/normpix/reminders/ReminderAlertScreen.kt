package com.example.normpix.reminders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.DatePickerDialogFor
import com.example.normpix.finance.ui.TimePickerDialogFor
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.BankGlyph
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.StatusTag
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/**
 * The full-screen "reminder fired" alert: a dedicated, focused surface rather
 * than the normal app UI. Needs only a reminder id, so a notification tap, an
 * in-app due event, or a future full-screen intent can all open it.
 */
@Composable
fun ReminderAlertScreen(reminderId: Long) {
    val container = LocalAppContainer.current
    val navigator = LocalNavigator.current
    val overlays = LocalOverlays.current
    val snackbars = com.example.normpix.ui.components.LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val controller = container.reminders
    var busy by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    // The notification can launch the app cold, before the list has loaded. A wrapper tells "still loading" from "not there".
    val loaded by produceState<LoadedReminder?>(null, reminderId) {
        controller.ensureLoaded()
        value = LoadedReminder(controller.findById(reminderId))
    }
    // What it is attached to, read fresh, and the way to open it.
    val attached = rememberResolvedLink(loaded?.reminder?.link)
    val openLink = rememberOpenLink()

    BackHandler(enabled = busy) {}

    val state = loaded
    if (state == null) {
        AppLoadingState(Modifier.fillMaxSize(), label = "Loading reminder")
        return
    }
    val reminder = state.reminder
    if (reminder == null) {
        // Deleted (or completed elsewhere) between the notification firing and this screen opening.
        Scaffold { padding ->
            AppEmptyState(
                icon = Icons.Outlined.NotificationsOff,
                title = "Reminder not found",
                message = "It may have been deleted or completed already.",
                actionLabel = "Close",
                onAction = navigator::back,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        }
        return
    }

    val scheme = MaterialTheme.colorScheme
    val text = MaterialTheme.typography
    val isOverdue = reminder.isOverdue()
    val emphasis = if (isOverdue) scheme.error else scheme.primary

    fun finish(op: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                op()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                busy = false
                snackbars.show("Couldn't update the reminder. Try again.", SnackbarKind.Error)
                return@launch
            }
            navigator.back()
        }
    }

    Surface(Modifier.fillMaxSize(), color = scheme.surface) {
        BoxWithConstraints(Modifier.safeDrawingPadding()) {
            val minHeight = maxHeight - AppSpacing.xxl * 2
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(AppSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Fills the screen when there's room (actions pinned to the bottom, within thumb
                // reach) but scrolls instead of overflowing in landscape or at large text sizes.
                Column(Modifier.widthIn(max = 560.dp).heightIn(min = minHeight)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusTag(label = reminder.categoryLabel, icon = reminder.category.icon)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = navigator::back, enabled = !busy) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(AppSpacing.xxl))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isOverdue) Icons.Rounded.ErrorOutline else Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = emphasis,
                            modifier = Modifier.size(AppIconSizes.md),
                        )
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text(if (isOverdue) "Overdue" else "Due now", style = text.titleMedium, color = emphasis)
                    }
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(reminder.title, style = text.displaySmall, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(AppSpacing.xl))
                    InfoRow(text = formatDueMoment(reminder.dueAt)) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(AppIconSizes.md))
                    }
                    if (reminder.repeatRule.repeats) {
                        InfoRow(text = reminder.repeatRule.label) {
                            Icon(Icons.Rounded.Repeat, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(AppIconSizes.md))
                        }
                    }
                    if (attached != null) {
                        InfoRow(text = attached.line) {
                            if (attached.bankName != null) {
                                BankGlyph(attached.bankName, tint = scheme.onSurfaceVariant, size = AppIconSizes.md)
                            } else {
                                Icon(attached.icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(AppIconSizes.md))
                            }
                        }
                        // The thing this is about is one tap away: the receipt, the repeating payment, the account.
                        if (!attached.missing) {
                            TextButton(onClick = { openLink(attached) }, enabled = !busy) { Text(openLabel(attached)) }
                        }
                    }
                    if (!reminder.notes.isNullOrBlank()) {
                        Spacer(Modifier.height(AppSpacing.md))
                        Text(
                            reminder.notes,
                            style = text.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(scheme.surfaceContainer, AppRadius.rMd)
                                .padding(AppSpacing.lg),
                        )
                    }
                    Spacer(Modifier.weight(2f))
                    Spacer(Modifier.height(AppSpacing.xxxl))
                    Button(
                        onClick = { finish { controller.completeReminder(reminderId) } },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text("Mark as done", style = text.titleMedium)
                    }
                    Spacer(Modifier.height(AppSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val by = overlays.sheet<Duration> { dismiss -> SnoozeOptionsSheet(onDone = dismiss) } ?: return@launch
                                    finish { controller.snoozeReminder(reminderId, by) }
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Rounded.Snooze, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text("Snooze")
                        }
                        OutlinedButton(
                            onClick = { pickingDate = true },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Outlined.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text("Reschedule")
                        }
                    }
                }
            }
        }
    }

    if (pickingDate) {
        DatePickerDialogFor(
            initial = if (reminder.dueAt.isBefore(LocalDateTime.now())) LocalDate.now() else reminder.dueAt.toLocalDate(),
            onPicked = {
                pickingDate = false
                pickedDate = it
            },
            onDismiss = { pickingDate = false },
        )
    }
    pickedDate?.let { date ->
        TimePickerDialogFor(
            initial = reminder.dueAt,
            onPicked = { hour, minute ->
                pickedDate = null
                val newDueAt = LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
                finish { controller.rescheduleReminder(reminderId, newDueAt) }
            },
            onDismiss = { pickedDate = null },
        )
    }
}

/** The reminder behind the alert, or null when it is gone (deleted or completed elsewhere). */
private class LoadedReminder(val reminder: Reminder?)

@Composable
private fun InfoRow(text: String, leading: @Composable () -> Unit) {
    Row(Modifier.padding(vertical = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(AppSpacing.md))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
