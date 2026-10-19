package com.example.normpix.reminders

import androidx.test.core.app.ApplicationProvider
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Reminders in an app backup: all of them come back as they were, and a restore sets their alarms again. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderBackupTest {
    private val now = LocalDateTime.of(2026, 10, 3, 9, 0)

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

    private fun reminder(
        title: String = "Electricity bill",
        id: Long? = null,
        status: ReminderStatus = ReminderStatus.Active,
        rule: RepeatRule = RepeatRule.Once,
    ) = Reminder(
        id = id,
        title = title,
        notes = "Account 1234",
        category = ReminderCategory.Payments,
        dueAt = now.plusDays(1),
        repeatRule = rule,
        status = status,
        link = ReminderLink.card("card-1"),
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `every field of a reminder comes back`() {
        val full = reminder(id = 7, rule = RepeatRule(RepeatKind.Weekly, weekdays = setOf(1, 5))).copy(
            customCategoryLabel = "Home",
            category = ReminderCategory.Custom,
            status = ReminderStatus.Snoozed,
            snoozedUntil = now.plusMinutes(10),
            updatedAt = now.plusHours(1),
            completedAt = now.plusHours(2),
            lastFiredAt = now.plusHours(3),
        )
        val plain = reminder("Rent", id = 8)
        val other = reminder("Gym", id = 9, rule = RepeatRule(RepeatKind.Monthly, dayOfMonth = 31)).copy(notes = null, link = null)
        val everyKind = listOf(
            RepeatRule(RepeatKind.Daily),
            RepeatRule(RepeatKind.Weekdays),
            RepeatRule(RepeatKind.Custom, intervalDays = 10),
        ).mapIndexed { i, rule -> reminder("Kind $i", id = 20L + i, rule = rule) }

        val all = listOf(full, plain, other) + everyKind
        val read = assertNotNull(ReminderBackupCodec.decode(ReminderBackupCodec.encode(all)))
        assertEquals(all, read.reminders)
        assertEquals(0, read.skipped)
    }

    @Test
    fun `a reminder rule written by a version with more in it is still read, and one written with less`() {
        // An extra field in a repeat rule (a later version's) is ignored, never a reason to lose the reminder.
        val withExtra = """
            {"reminders": [{
              "id": 3, "title": "Pay rent", "category": "payments", "dueAt": "2026-10-04T09:00:00",
              "repeat": {"kind": "monthly", "dayOfMonth": 4, "endDate": "2027-01-04", "somethingNew": [1, 2]},
              "status": "active", "createdAt": "2026-10-03T09:00:00", "updatedAt": "2026-10-03T09:00:00"
            }]}
        """.trimIndent()
        val read = assertNotNull(ReminderBackupCodec.decode(withExtra))
        assertEquals(0, read.skipped)
        val rule = read.reminders.single().repeatRule
        assertEquals(RepeatKind.Monthly, rule.kind)
        assertEquals(4, rule.dayOfMonth)

        // An older file has no repeat rule at all, or one with a kind and nothing else.
        val older = """
            {"reminders": [
              {"title": "A", "dueAt": "2026-10-04T09:00:00"},
              {"title": "B", "dueAt": "2026-10-04T09:00:00", "repeat": {"kind": "daily"}}
            ]}
        """.trimIndent()
        val rules = assertNotNull(ReminderBackupCodec.decode(older)).reminders.map { it.repeatRule }
        assertEquals(listOf(RepeatRule.Once, RepeatRule(RepeatKind.Daily)), rules)
    }

    @Test
    fun `what a reminder may lack gets what the database would give it`() {
        val text = """{"reminders": [{"title": "Bare", "dueAt": "2026-10-04T09:00:00", "category": "nonsense", "status": "nonsense"}]}"""
        val bare = assertNotNull(ReminderBackupCodec.decode(text)).reminders.single()
        assertNull(bare.id)
        assertEquals(ReminderCategory.Personal, bare.category)
        assertEquals(ReminderStatus.Active, bare.status)
        assertEquals(RepeatRule.Once, bare.repeatRule)
        assertEquals(LocalDateTime.of(2026, 10, 4, 9, 0), bare.createdAt, "made when it was due, if the file doesn't say")
        assertEquals(bare.createdAt, bare.updatedAt)
    }

    @Test
    fun `damaged entries are left out and counted, and the rest are kept`() {
        val text = """
            {"reminders": [
              {"title": "Fine", "dueAt": "2026-10-04T09:00:00"},
              {"dueAt": "2026-10-04T09:00:00"},
              {"title": "  ", "dueAt": "2026-10-04T09:00:00"},
              {"title": "No due time"},
              {"title": "Bad due time", "dueAt": "tomorrow"},
              7,
              {"title": "Bad rule", "dueAt": "2026-10-04T09:00:00", "repeat": {"nokind": true}}
            ]}
        """.trimIndent()
        val read = assertNotNull(ReminderBackupCodec.decode(text))
        assertEquals(listOf("Fine", "Bad rule"), read.reminders.map { it.title })
        assertEquals(5, read.skipped)
        assertEquals(RepeatRule.Once, read.reminders.last().repeatRule, "a rule it can't read is no repeat")
    }

    @Test
    fun `a file that is not a reminders file is not read`() {
        assertNull(ReminderBackupCodec.decode("not json"))
        assertNull(ReminderBackupCodec.decode("[]"))
        assertNull(ReminderBackupCodec.decode("""{"other": []}"""))
        assertTrue(assertNotNull(ReminderBackupCodec.decode("""{"reminders": []}""")).reminders.isEmpty())
    }

    @Test
    fun `ids that cannot stay are dropped, and two reminders never share one`() {
        val entry = { id: String, title: String ->
            """{"id": $id, "title": "$title", "dueAt": "2026-10-04T09:00:00"}"""
        }
        val text = """{"reminders": [${listOf(
            entry("5", "First"),
            entry("5", "Same id"),
            entry("0", "Zero"),
            entry("-3", "Negative"),
            entry("9999999999", "Too big for a notification id"),
            entry("\"6\"", "A quoted id"),
        ).joinToString(",")}]}"""
        val read = assertNotNull(ReminderBackupCodec.decode(text)).reminders
        assertEquals(listOf(5L, null, null, null, null, null), read.map { it.id })
    }

    @Test
    fun `a restore replaces the reminders, cancels the old alarms and sets the new ones`() = runBlocking {
        val old = listOf(controller.addReminder(reminder("Old one")), controller.addReminder(reminder("Old two")))
        val oldIds = old.map { it.id!! }
        scheduler.scheduled.clear()

        val done = reminder("Done one", status = ReminderStatus.Completed).copy(completedAt = now)
        val stored = controller.replaceAll(listOf(reminder("New one"), reminder("New two"), done))

        assertEquals(setOf("New one", "New two", "Done one"), stored.map { it.title }.toSet())
        assertEquals(3, stored.size)
        assertEquals(stored, controller.state.value.reminders)
        assertTrue(stored.all { it.id != null })
        assertEquals(oldIds.toSet(), scheduler.cancelled.toSet(), "the alarms of the old reminders are cancelled")
        val ids = stored.associate { it.title to it.id!! }
        assertEquals(setOf(ids.getValue("New one"), ids.getValue("New two")), scheduler.scheduled.toSet(), "the new ones are set; a done one isn't")
        assertEquals(stored, dao.getAll(), "stored, not only shown")
    }

    @Test
    fun `a reminder keeps its id through a restore`() = runBlocking {
        controller.addReminder(reminder("Old"))
        val stored = controller.replaceAll(listOf(reminder("Kept id", id = 42)))
        assertEquals(42L, stored.single().id)
        assertEquals(listOf(42L), scheduler.scheduled.takeLast(1))
    }

    @Test
    fun `restoring nothing empties the list`() = runBlocking {
        controller.addReminder(reminder("Old"))
        assertTrue(controller.replaceAll(emptyList()).isEmpty())
        assertTrue(controller.state.value.reminders.isEmpty())
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun `a restore that cannot be stored leaves the reminders as they were`() = runBlocking {
        val kept = controller.addReminder(reminder("Kept"))
        scheduler.cancelled.clear()

        // Two reminders with one id: the second cannot be stored, so none of it is.
        assertFailsWith<Exception> { controller.replaceAll(listOf(reminder("A", id = 5), reminder("B", id = 5))) }

        assertEquals(listOf(kept), dao.getAll())
        assertEquals(listOf(kept), controller.state.value.reminders)
        assertTrue(scheduler.cancelled.isEmpty(), "nothing was cancelled for a restore that didn't happen")
    }

    @Test
    fun `the file keeps the order and the shape of what was exported`() {
        val text = ReminderBackupCodec.encode(listOf(reminder("One", id = 1), reminder("Two", id = 2)))
        val root = Json.parseToJsonElement(text).jsonObject
        val list: JsonArray = root.getValue("reminders").jsonArray
        assertEquals(listOf("One", "Two"), list.map { (it as JsonObject).getValue("title").let { t -> (t as JsonPrimitive).content } })
        assertEquals("payments", (list[0] as JsonObject).getValue("category").let { (it as JsonPrimitive).content })
    }
}
