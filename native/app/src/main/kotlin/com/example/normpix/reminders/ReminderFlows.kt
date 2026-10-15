package com.example.normpix.reminders

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.normpix.AppContainer
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.ui.ReminderLauncher
import java.time.LocalDateTime
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalPermissions
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.PermissionRequester
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.wallet.StoredCard
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * User-facing reminder flows (sheet/dialog -> controller -> feedback), shared by
 * every screen that can act on a reminder so Home's contextual surface and the
 * Reminders tab behave identically. The state transitions themselves stay in
 * [RemindersController] / [ReminderActions]; these only orchestrate UI around them.
 */
class ReminderFlows(
    private val container: AppContainer,
    private val context: Context,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val haptics: AppHaptics,
    private val permissions: PermissionRequester,
    private val scope: CoroutineScope,
    /** Adds a card to the vault (used by the reminder form's "Add card"); null if cancelled. */
    private val addCard: suspend () -> StoredCard?,
) : ReminderLauncher {
    private val controller get() = container.reminders

    /** Notifications need a runtime grant on Android 13+; exact alarms a one-time nudge to the system page. */
    private suspend fun ensureAlertAccess() {
        if (Build.VERSION.SDK_INT >= 33 && !ReminderNotifications.hasPermission(context)) {
            permissions.request(Manifest.permission.POST_NOTIFICATIONS)
        }
        val alarms = container.alarms ?: return
        if (!alarms.canScheduleExact() && !container.preferences.exactAlarmPrompted) {
            // Asked once, and explained first: the system page alone doesn't say why NormPix wants it.
            container.preferences.exactAlarmPrompted = true
            val settings = alarms.exactAlarmSettingsIntent() ?: return
            val open = overlays.confirm(
                title = "Ring reminders on time?",
                message = "Android needs your OK for NormPix to ring reminders at the exact minute. " +
                    "Without it they still arrive, but can be a few minutes late.",
                confirmLabel = "Open settings",
                cancelLabel = "Not now",
                icon = Icons.Outlined.Alarm,
            )
            // The reminder is re-armed as an exact alarm when NormPix comes back to the foreground.
            if (open) runCatching { context.startActivity(settings.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    private suspend fun cards(): List<StoredCard> = runCatching { container.vault.loadCards() }.getOrDefault(emptyList())

    private suspend fun editor(existing: Reminder?, initial: Reminder? = null): Reminder? {
        val available = cards()
        return overlays.sheet<Reminder> { dismiss ->
            AddReminderSheet(existing = existing, cards = available, onAddCard = addCard, onDone = dismiss, initial = initial)
        }
    }

    fun add() = addFrom(null)

    /** A new reminder, opened on [initial] (a draft already about something) if given; saved only when the form is. */
    fun addFrom(initial: Reminder?) {
        scope.launch {
            val draft = editor(null, initial) ?: return@launch
            ensureAlertAccess()
            controller.addReminder(draft)
            haptics.success()
            snackbars.show("Reminder added")
        }
    }

    // ----------------------------------------------- "Remind me" from the ledger

    private val finance get() = container.finance.state.value

    override fun about(transaction: FinanceTransaction) {
        val state = finance
        addFrom(
            ReminderDrafts.aboutTransaction(
                transaction,
                name = transactionName(transaction, state),
                summary = transactionStory(transaction, state),
                now = LocalDateTime.now(),
            ),
        )
    }

    override fun about(rule: RecurringRule) {
        val state = finance
        addFrom(ReminderDrafts.aboutRecurring(rule, recurringName(rule, state), recurringSummary(rule, state), LocalDateTime.now()))
    }

    override fun linkAutomatically(rule: RecurringRule) {
        val id = rule.id ?: return
        scope.launch {
            controller.ensureLoaded()
            if (controller.state.value.reminders.any { it.link == ReminderLink.recurring(id) }) return@launch
            val now = LocalDateTime.now()
            val draft = ReminderDrafts.aboutRecurring(rule, recurringName(rule, finance), recurringSummary(rule, finance), now)
            ensureAlertAccess()
            val added = controller.addReminder(draft)
            val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
            snackbars.showUndoable(
                "Reminder set for ${formatDueMoment(added.effectiveDueAt, is24Hour)}",
                undo = { scope.launch { added.id?.let { controller.deleteReminder(it) } } },
                kind = SnackbarKind.Done,
            )
        }
    }

    override fun about(account: Account) {
        val state = finance
        val owed = (-state.balanceOf(account.id ?: return)).coerceAtLeast(0)
        val summary = if (owed > 0) "${state.money.format(owed)} owed on ${account.name}" else null
        addFrom(ReminderDrafts.aboutAccount(account, owed, summary, LocalDateTime.now()))
    }

    fun edit(reminder: Reminder) {
        scope.launch {
            val updated = editor(reminder) ?: return@launch
            ensureAlertAccess()
            controller.updateReminder(updated)
            snackbars.show("Reminder updated")
        }
    }

    /** Completing a repeating reminder advances it to its next occurrence, so the confirmation says when that is. */
    fun complete(reminder: Reminder) {
        scope.launch {
            controller.completeReminder(reminder.id!!)
            haptics.success()
            val next = controller.findById(reminder.id)
            snackbars.show(
                if (next != null && next.status == ReminderStatus.Active) {
                    "Done. Next one ${formatDueMoment(next.dueAt, android.text.format.DateFormat.is24HourFormat(context))}"
                } else {
                    "Reminder completed"
                },
            )
        }
    }

    fun snooze(reminder: Reminder) {
        scope.launch {
            val by = overlays.sheet<Duration> { dismiss -> SnoozeOptionsSheet(onDone = dismiss) } ?: return@launch
            controller.snoozeReminder(reminder.id!!, by)
            val snoozed = controller.findById(reminder.id)
            snackbars.show(
                if (snoozed == null) {
                    "Reminder snoozed"
                } else {
                    "Snoozed until ${formatDueMoment(snoozed.effectiveDueAt, android.text.format.DateFormat.is24HourFormat(context))}"
                },
            )
        }
    }

    fun delete(reminder: Reminder) {
        scope.launch {
            val confirmed = overlays.confirm(
                title = "Delete \"${reminder.title}\"?",
                message = "You can undo it for a few seconds.",
                confirmLabel = "Delete",
                destructive = true,
                icon = Icons.Outlined.DeleteOutline,
            )
            if (!confirmed) return@launch
            val removed = controller.findById(reminder.id!!) ?: reminder
            controller.deleteReminder(reminder.id)
            snackbars.showUndoable(
                "Reminder deleted",
                undo = {
                    scope.launch {
                        // Back under its own id, so whatever it was linked to still finds it, and armed again.
                        if (controller.findById(removed.id!!) == null) controller.addReminder(removed)
                        snackbars.show("Reminder restored")
                    }
                },
            )
        }
    }
}

@Composable
fun rememberReminderFlows(): ReminderFlows {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val permissions = LocalPermissions.current
    val scope = LocalAppScope.current
    val cardFlows = com.example.normpix.wallet.rememberCardFlows()
    return remember(container, context, overlays, snackbars, haptics, permissions, scope, cardFlows) {
        ReminderFlows(container, context, overlays, snackbars, haptics, permissions, scope, cardFlows::addAndSave)
    }
}
