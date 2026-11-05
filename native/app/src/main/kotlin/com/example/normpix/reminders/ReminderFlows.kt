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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    override fun linkAutomatically(rule: RecurringRule) = follow(rule, null)

    // The repeating payments with a reminder that is still to come: a finished one no longer says the payment is looked after.
    override val remindedRules: StateFlow<Set<Long>> = container.reminders.state
        .map { remindedRuleIds(it.reminders) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), remindedRuleIds(container.reminders.state.value.reminders))

    override fun follow(rule: RecurringRule, before: RecurringRule?) {
        val id = rule.id ?: return
        scope.launch {
            controller.ensureLoaded()
            val state = finance
            val words = RecurringReminders.Words(
                title = recurringName(rule, state),
                summary = recurringSummary(rule, state),
                beforeTitle = before?.let { recurringName(it, state) },
                beforeSummary = before?.let { recurringSummary(it, state) },
            )
            val steps = RecurringReminders.stepsAfterSave(rule, before, RecurringReminders.about(controller.state.value.reminders, id), words, LocalDateTime.now())
            for (step in steps) {
                when (step) {
                    is RecurringReminders.Step.Add -> {
                        ensureAlertAccess()
                        val added = controller.addReminder(step.reminder)
                        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
                        snackbars.showUndoable(
                            "Reminder set for ${formatDueMoment(added.effectiveDueAt, is24Hour)}",
                            undo = { scope.launch { added.id?.let { controller.deleteReminder(it) } } },
                            kind = SnackbarKind.Done,
                        )
                    }
                    is RecurringReminders.Step.Change -> controller.updateReminder(step.reminder)
                    is RecurringReminders.Step.Delete -> controller.deleteReminder(step.id)
                }
            }
        }
    }

    override fun forget(ruleId: Long) {
        scope.launch {
            controller.ensureLoaded()
            for (step in RecurringReminders.stepsAfterDelete(RecurringReminders.about(controller.state.value.reminders, ruleId))) {
                if (step is RecurringReminders.Step.Delete) controller.deleteReminder(step.id)
            }
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

    /**
     * Opens the Clock app's alarm form for [reminder] (see [ClockAlarms]); the person saves the alarm there. NormPix keeps nothing
     * about it. Says so when there is no Clock app to take it.
     */
    fun addToClock(reminder: Reminder) {
        val intent = ClockAlarms.intentFor(reminder)
        if (intent == null) {
            snackbars.show("A clock alarm can only hold a time of day, so it can't take that reminder", SnackbarKind.Warning)
            return
        }
        // Leaving NormPix on purpose: the lock must not close behind the person.
        container.lock.expectExternalActivity()
        val opened = runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!opened) snackbars.show("No Clock app on this phone can take an alarm", SnackbarKind.Warning)
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

/** The ids of the repeating payments that have a reminder still to come (not finished). */
internal fun remindedRuleIds(reminders: List<Reminder>): Set<Long> = reminders
    .filter { it.status != ReminderStatus.Completed }
    .mapNotNull { reminder -> reminder.link?.takeIf { it.kind == ReminderLinkKind.Recurring }?.id?.toLongOrNull() }
    .toSet()
