package com.example.normpix.reminders

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import java.time.Duration
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What [RemindersController] needs from a notification backend. */
interface ReminderScheduler {
    suspend fun schedule(reminder: Reminder)

    suspend fun cancel(id: Long)
}

/** A scheduler that does nothing (tests, previews). */
object NoOpReminderScheduler : ReminderScheduler {
    override suspend fun schedule(reminder: Reminder) = Unit

    override suspend fun cancel(id: Long) = Unit
}

@Immutable
data class RemindersState(
    val reminders: List<Reminder> = emptyList(),
    val loading: Boolean = true,
    /** Bumped when a due time passes, so lists derived from "now" re-evaluate. */
    val tick: Int = 0,
) {
    fun upcoming(now: LocalDateTime = LocalDateTime.now()): List<Reminder> = reminders
        .filter { it.status != ReminderStatus.Completed && it.effectiveDueAt.isAfter(now) }
        .sortedBy { it.effectiveDueAt }

    fun overdue(now: LocalDateTime = LocalDateTime.now()): List<Reminder> = reminders
        .filter { it.isOverdue(now) }
        .sortedBy { it.effectiveDueAt }

    val completed: List<Reminder>
        get() = reminders.filter { it.status == ReminderStatus.Completed }
            .sortedByDescending { it.completedAt ?: it.updatedAt }

    fun byId(id: Long): Reminder? = reminders.firstOrNull { it.id == id }
}

/**
 * Owns the in-memory [Reminder] list, backed by [ReminderDao]. Scheduling
 * happens only from the mutation methods (and [rearmAll] after a reboot).
 */
class RemindersController(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
    private val clock: () -> LocalDateTime = { LocalDateTime.now() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(RemindersState())
    val state: StateFlow<RemindersState> = _state.asStateFlow()

    /** Where the reminders database work runs (see [ReminderDao.dispatcher]); a sample set opened from these runs there too. */
    internal val databaseDispatcher: CoroutineDispatcher get() = dao.dispatcher

    private val _due = MutableSharedFlow<Reminder>(extraBufferCapacity = 8)

    /**
     * Fires when a reminder's due moment arrives while the app is in the
     * foreground, so the alert screen can open. Never fires for reminders that
     * were already overdue when the list loaded - those show up in the list.
     */
    val onDue: SharedFlow<Reminder> = _due.asSharedFlow()

    private val mutex = Mutex()
    private var dueTimer: Job? = null
    private var loaded = false

    suspend fun load() = mutex.withLock { loadLocked() }

    private suspend fun loadLocked() {
        val all = dao.getAll()
        _state.update { it.copy(reminders = all, loading = false) }
        loaded = true
        armDueTimer()
    }

    /** Loads on first use - the notification receivers can run before the UI ever did. */
    suspend fun ensureLoaded() = mutex.withLock { if (!loaded) loadLocked() }

    fun findById(id: Long): Reminder? = _state.value.byId(id)

    suspend fun addReminder(draft: Reminder): Reminder = mutex.withLock {
        val id = dao.insert(draft)
        val saved = draft.copy(id = id)
        _state.update { it.copy(reminders = it.reminders + saved) }
        scheduleIfNeeded(saved)
        armDueTimer()
        saved
    }

    suspend fun updateReminder(updated: Reminder) = mutex.withLock {
        dao.update(updated)
        replace(updated)
        scheduler.cancel(updated.id!!)
        scheduleIfNeeded(updated)
        armDueTimer()
    }

    suspend fun deleteReminder(id: Long) = mutex.withLock {
        dao.delete(id)
        scheduler.cancel(id)
        _state.update { s -> s.copy(reminders = s.reminders.filter { it.id != id }) }
        armDueTimer()
    }

    suspend fun completeReminder(id: Long) = applyAndPersist(id) { ReminderActions.complete(it, clock()) }

    suspend fun snoozeReminder(id: Long, by: Duration) = applyAndPersist(id) { ReminderActions.snooze(it, by, clock()) }

    suspend fun rescheduleReminder(id: Long, newDueAt: LocalDateTime) =
        applyAndPersist(id) { ReminderActions.reschedule(it, newDueAt, clock()) }

    /** Re-registers every pending alarm (after a reboot or app update, when the OS has dropped them). */
    suspend fun rearmAll() = mutex.withLock {
        if (!loaded) loadLocked()
        val now = clock()
        for (reminder in _state.value.reminders) {
            if (reminder.status != ReminderStatus.Completed && reminder.effectiveDueAt.isAfter(now)) scheduler.schedule(reminder)
        }
    }

    private suspend fun applyAndPersist(id: Long, transform: (Reminder) -> Reminder) = mutex.withLock {
        if (!loaded) loadLocked()
        val current = _state.value.byId(id) ?: dao.getById(id) ?: error("No reminder with id $id")
        val updated = transform(current)
        dao.update(updated)
        replace(updated)
        scheduler.cancel(id)
        scheduleIfNeeded(updated)
        armDueTimer()
    }

    private fun replace(updated: Reminder) {
        _state.update { s -> s.copy(reminders = s.reminders.map { if (it.id == updated.id) updated else it }) }
    }

    private suspend fun scheduleIfNeeded(reminder: Reminder) {
        if (reminder.status != ReminderStatus.Completed) scheduler.schedule(reminder)
    }

    /**
     * Re-arms a single timer to the soonest due time across active/snoozed
     * reminders - one shared timer, not one per reminder. Must follow every
     * mutation, or [onDue] silently stops firing after the first one comes due.
     */
    private fun armDueTimer() {
        dueTimer?.cancel()
        val now = clock()
        val soonest = _state.value.reminders
            .filter { it.status != ReminderStatus.Completed && it.effectiveDueAt.isAfter(now) }
            .minByOrNull { it.effectiveDueAt } ?: return
        val delayMs = Duration.between(now, soonest.effectiveDueAt).toMillis().coerceAtLeast(0)
        val soonestId = soonest.id
        dueTimer = scope.launch {
            delay(delayMs)
            val current = soonestId?.let(::findById)
            if (current != null) _due.emit(current)
            _state.update { it.copy(tick = it.tick + 1) }
            armDueTimer()
        }
    }

    fun close() {
        dueTimer?.cancel()
    }
}

val LocalReminders = compositionLocalOf<RemindersController> { error("No RemindersController provided") }
