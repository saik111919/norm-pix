package com.example.normpix.reminders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.AnimatedEntrance
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SwipeRevealHost
import com.example.normpix.ui.theme.AppSpacing

private const val MAX_COMPLETED_SHOWN = 10

/**
 * Plan > Reminders: Overdue / Upcoming / Completed, backed by
 * [RemindersController]. What a reminder is attached to (a card from the vault Wallet
 * uses, or an expense, repeating payment or account) is read fresh for each row, so a
 * reminder never keeps its own copy of it.
 */
@Composable
fun RemindersView() {
    val container = LocalAppContainer.current
    val flows = rememberReminderFlows()
    val state by container.reminders.state.collectAsState()
    val overdue = state.overdue()
    val upcoming = state.upcoming()
    val completed = state.completed.take(MAX_COMPLETED_SHOWN)

    SwipeRevealHost(Modifier.padding(AppSpacing.page)) {
        Column {
        when {
            state.loading -> AppLoadingState(label = "Loading reminders")
            overdue.isEmpty() && upcoming.isEmpty() && completed.isEmpty() -> AppEmptyState(
                icon = Icons.Rounded.NotificationsNone,
                title = "No reminders yet",
                message = "Set a reminder for a bill, payment, or anything else you don't want to forget.",
                actionLabel = "Add reminder",
                actionIcon = Icons.Rounded.Add,
                onAction = flows::add,
            )
            else -> {
                @Composable
                fun Section(title: String, reminders: List<Reminder>, staggerStart: Int) {
                    ListSection(title = title) {
                        reminders.forEachIndexed { index, reminder ->
                            item {
                                AnimatedEntrance(delayMs = 30L * (staggerStart + index)) {
                                    ReminderListItem(
                                        reminder = reminder,
                                        onClick = { flows.edit(reminder) },
                                        onComplete = { flows.complete(reminder) },
                                        onSnooze = { flows.snooze(reminder) },
                                        onDelete = { flows.delete(reminder) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (overdue.isNotEmpty()) Section("Overdue", overdue, 0)
                if (upcoming.isNotEmpty()) Section("Upcoming", upcoming, overdue.size)
                if (completed.isNotEmpty()) Section("Completed", completed, overdue.size + upcoming.size)
            }
        }
        }
    }
}
