package com.example.normpix.reminders

import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Records what the controller asked the alarm backend to do. */
private class RecordingScheduler : ReminderScheduler {
    val scheduled = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()

    override suspend fun schedule(reminder: Reminder) {
        scheduled += reminder.id!!
    }

    override suspend fun cancel(id: Long) {
        cancelled += id
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RemindersControllerTest {
    private var now = LocalDateTime.of(2026, 10, 3, 9, 0)
    private lateinit var database: RemindersDatabase
    private lateinit var dao: ReminderDao
    private lateinit var scheduler: RecordingScheduler
    private lateinit var scope: CoroutineScope
    private lateinit var controller: RemindersController

    @BeforeTest
    fun setUp() {
        database = RemindersDatabase(ApplicationProvider.getApplicationContext(), name = null)
        dao = ReminderDao(database, zone = ZoneId.of("Asia/Kolkata"), dispatcher = Dispatchers.Unconfined)
        scheduler = RecordingScheduler()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        controller = RemindersController(dao, scheduler, clock = { now }, scope = scope)
    }

    @AfterTest
    fun tearDown() {
        controller.close()
        scope.cancel()
        database.close()
    }

    private fun draft(title: String = "Electricity bill", dueAt: LocalDateTime = now.plusDays(1), rule: RepeatRule = RepeatRule.Once) =
        Reminder(
            title = title,
            notes = "Account 1234",
            category = ReminderCategory.Payments,
            dueAt = dueAt,
            repeatRule = rule,
            link = ReminderLink.card("card-1"),
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun daoRoundTripsEveryField() = runBlocking {
        val original = draft(rule = RepeatRule(RepeatKind.Weekly, weekdays = setOf(1, 5))).copy(
            status = ReminderStatus.Snoozed,
            snoozedUntil = now.plusMinutes(10),
            customCategoryLabel = "Home",
            lastFiredAt = now.minusMinutes(1),
        )
        val id = dao.insert(original)
        assertEquals(original.copy(id = id), dao.getById(id))
    }

    @Test
    fun addingSchedulesAndPersists() = runBlocking {
        controller.load()
        val saved = controller.addReminder(draft())
        assertNotNull(saved.id)
        assertEquals(listOf(saved.id), scheduler.scheduled)
        assertEquals(listOf(saved), dao.getAll())
        assertEquals(listOf(saved), controller.state.value.upcoming(now))
    }

    @Test
    fun completingARepeatingReminderReschedulesTheNextOne() = runBlocking {
        controller.load()
        val saved = controller.addReminder(draft(dueAt = now, rule = RepeatRule(RepeatKind.Monthly, dayOfMonth = 3)))
        now = now.plusMinutes(5)
        controller.completeReminder(saved.id!!)
        val next = controller.findById(saved.id)!!
        assertEquals(ReminderStatus.Active, next.status)
        assertEquals(LocalDateTime.of(2026, 11, 3, 9, 0), next.dueAt)
        assertEquals(next, dao.getById(saved.id))
        assertTrue(saved.id in scheduler.cancelled)
        assertEquals(2, scheduler.scheduled.count { it == saved.id })
    }

    @Test
    fun completingAOneTimeReminderStopsItsAlarm() = runBlocking {
        controller.load()
        val saved = controller.addReminder(draft())
        scheduler.scheduled.clear()
        controller.completeReminder(saved.id!!)
        assertEquals(ReminderStatus.Completed, controller.findById(saved.id)!!.status)
        assertEquals(listOf(saved.id), scheduler.cancelled)
        assertTrue(scheduler.scheduled.isEmpty(), "a completed reminder must not be scheduled again")
    }

    @Test
    fun snoozeAndDelete() = runBlocking {
        controller.load()
        val saved = controller.addReminder(draft(dueAt = now.minusMinutes(1)))
        controller.snoozeReminder(saved.id!!, Duration.ofMinutes(30))
        assertEquals(now.plusMinutes(30), controller.findById(saved.id)!!.effectiveDueAt)
        controller.deleteReminder(saved.id)
        assertNull(dao.getById(saved.id))
        assertTrue(controller.state.value.reminders.isEmpty())
    }

    @Test
    fun receiversCanActBeforeTheUiLoaded() = runBlocking {
        val id = dao.insert(draft())
        // A notification action arrives first: the controller loads on demand.
        controller.snoozeReminder(id, Duration.ofMinutes(10))
        assertEquals(ReminderStatus.Snoozed, dao.getById(id)!!.status)
        assertEquals(false, controller.state.value.loading)
    }

    @Test
    fun rearmSchedulesOnlyPendingReminders() = runBlocking {
        val pending = dao.insert(draft(dueAt = now.plusHours(1)))
        dao.insert(draft(dueAt = now.minusHours(1))) // already overdue: nothing to ring
        dao.insert(draft(dueAt = now.plusHours(2)).copy(status = ReminderStatus.Completed, completedAt = now))
        controller.rearmAll()
        assertEquals(listOf(pending), scheduler.scheduled)
    }
}
