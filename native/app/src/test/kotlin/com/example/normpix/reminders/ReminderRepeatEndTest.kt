package com.example.normpix.reminders

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RepeatEnd
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.TestEnvironment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private fun moment(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

private fun ymd(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

private fun json(text: String) = Json.parseToJsonElement(text) as JsonObject

/** Where a repeating reminder stops: the day it may not go past, the count that becomes a day, and what is said of both. */
class ReminderRepeatEndRuleTest {
    // --- stopping --------------------------------------------------------------------------------------------------

    @Test
    fun `a reminder with no end repeats as it always did`() {
        val rule = RepeatRule(RepeatKind.Daily)
        assertEquals(moment(2026, 10, 2), rule.nextOccurrence(moment(2026, 10, 1)))
        assertNull(rule.endDate)
        assertEquals(RepeatEnd.Never, rule.ends)
    }

    @Test
    fun `the last day itself is still a reminder, and the one after it is not`() {
        val rule = RepeatRule(RepeatKind.Daily, endDate = ymd(2026, 10, 3))
        assertEquals(moment(2026, 10, 2), rule.nextOccurrence(moment(2026, 10, 1)))
        assertEquals(moment(2026, 10, 3), rule.nextOccurrence(moment(2026, 10, 2)))
        assertNull(rule.nextOccurrence(moment(2026, 10, 3)))
    }

    @Test
    fun `a late time on the last day is inside it`() {
        val rule = RepeatRule(RepeatKind.Daily, endDate = ymd(2026, 10, 3))
        assertEquals(moment(2026, 10, 3, 23, 45), rule.nextOccurrence(moment(2026, 10, 2, 23, 45)))
    }

    @Test
    fun `a weekly reminder ends on the last chosen weekday before the end`() {
        // Mondays and Thursdays; the end is a Monday, which counts.
        val rule = RepeatRule(RepeatKind.Weekly, weekdays = setOf(1, 4), endDate = ymd(2026, 10, 5))
        assertEquals(moment(2026, 10, 5), rule.nextOccurrence(moment(2026, 10, 1)))
        assertNull(rule.nextOccurrence(moment(2026, 10, 5)))
        // An end between two of them stops at the earlier.
        assertNull(rule.copy(endDate = ymd(2026, 10, 4)).nextOccurrence(moment(2026, 10, 1)))
    }

    @Test
    fun `weekday reminders stop over a weekend that holds the end`() {
        val rule = RepeatRule(RepeatKind.Weekdays, endDate = ymd(2026, 10, 4))
        assertEquals(DayOfWeek.FRIDAY, moment(2026, 10, 2).dayOfWeek)
        assertNull(rule.nextOccurrence(moment(2026, 10, 2)))
    }

    @Test
    fun `a monthly reminder on the 31st stops at the end of February when that is the end`() {
        val rule = RepeatRule(RepeatKind.Monthly, dayOfMonth = 31, endDate = ymd(2027, 2, 28))
        assertEquals(moment(2027, 2, 28), rule.nextOccurrence(moment(2027, 1, 31)))
        assertNull(rule.nextOccurrence(moment(2027, 2, 28)))
    }

    @Test
    fun `an interval of days stops before the step that would pass the end`() {
        val rule = RepeatRule(RepeatKind.Custom, intervalDays = 10, endDate = ymd(2026, 10, 20))
        assertEquals(moment(2026, 10, 11), rule.nextOccurrence(moment(2026, 10, 1)))
        assertNull(rule.nextOccurrence(moment(2026, 10, 11)))
    }

    @Test
    fun `a day that does not exist on the clock is no problem - the dates are wall-clock dates`() {
        val before = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            // 2027-03-14 02:30 is skipped in New York; the reminder's own dates are local ones and keep 02:30.
            val rule = RepeatRule(RepeatKind.Daily, endDate = ymd(2027, 3, 14))
            assertEquals(moment(2027, 3, 14, 2, 30), rule.nextOccurrence(moment(2027, 3, 13, 2, 30)))
            assertNull(rule.nextOccurrence(moment(2027, 3, 14, 2, 30)))
        } finally {
            TimeZone.setDefault(before)
        }
    }

    @Test
    fun `a reminder that does not repeat has no next one whatever its end says`() {
        assertNull(RepeatRule(RepeatKind.Once, endDate = ymd(2030, 1, 1)).nextOccurrence(moment(2026, 10, 1)))
    }

    // --- completing ------------------------------------------------------------------------------------------------

    private fun reminder(rule: RepeatRule, dueAt: LocalDateTime) = Reminder(
        id = 1,
        title = "Pay rent",
        category = ReminderCategory.Payments,
        dueAt = dueAt,
        repeatRule = rule,
        createdAt = moment(2026, 9, 1),
        updatedAt = moment(2026, 9, 1),
    )

    @Test
    fun `completing a reminder before the last moves on to the next`() {
        val rule = RepeatRule(RepeatKind.Daily, endDate = ymd(2026, 10, 3))
        val done = ReminderActions.complete(reminder(rule, moment(2026, 10, 2)), now = moment(2026, 10, 2, 10))
        assertEquals(ReminderStatus.Active, done.status)
        assertEquals(moment(2026, 10, 3), done.dueAt)
    }

    @Test
    fun `completing the last reminder finishes it instead of scheduling another`() {
        val rule = RepeatRule(RepeatKind.Daily, endDate = ymd(2026, 10, 3))
        val done = ReminderActions.complete(reminder(rule, moment(2026, 10, 3)), now = moment(2026, 10, 3, 10))
        assertEquals(ReminderStatus.Completed, done.status)
        assertEquals(moment(2026, 10, 3), done.dueAt)
        assertNotNull(done.completedAt)
    }

    @Test
    fun `a last reminder completed late is still the last`() {
        val rule = RepeatRule(RepeatKind.Daily, endDate = ymd(2026, 10, 3))
        val done = ReminderActions.complete(reminder(rule, moment(2026, 10, 3)), now = moment(2026, 10, 9, 8))
        assertEquals(ReminderStatus.Completed, done.status)
    }

    // --- what is said ----------------------------------------------------------------------------------------------

    @Test
    fun `the label says where it stops`() {
        val end = ymd(2027, 3, 3)
        assertEquals("Every day until 3 Mar 2027", RepeatRule(RepeatKind.Daily, endDate = end).label)
        assertEquals("Every weekday until 3 Mar 2027", RepeatRule(RepeatKind.Weekdays, endDate = end).label)
        assertEquals("Weekly on Mon, Thu until 3 Mar 2027", RepeatRule(RepeatKind.Weekly, weekdays = setOf(4, 1), endDate = end).label)
        assertEquals("Monthly on day 15 until 3 Mar 2027", RepeatRule(RepeatKind.Monthly, dayOfMonth = 15, endDate = end).label)
        assertEquals("Every 3 days until 3 Mar 2027", RepeatRule(RepeatKind.Custom, intervalDays = 3, endDate = end).label)
    }

    @Test
    fun `without an end the label is what it was, and a reminder that does not repeat says so`() {
        assertEquals("Every day", RepeatRule(RepeatKind.Daily).label)
        assertEquals("Monthly on day 15", RepeatRule(RepeatKind.Monthly, dayOfMonth = 15).label)
        assertEquals("Does not repeat", RepeatRule.Once.label)
        assertEquals("Does not repeat", RepeatRule(RepeatKind.Once, endDate = ymd(2027, 3, 3)).label)
    }

    // --- after N times ---------------------------------------------------------------------------------------------

    @Test
    fun `after N times becomes the day of the Nth reminder, the first being the first`() {
        assertEquals(ymd(2026, 10, 3), RepeatRule(RepeatKind.Daily).ending(RepeatEnd.After(3), moment(2026, 10, 1)).endDate)
        // Thursday, Monday, Thursday.
        assertEquals(
            ymd(2026, 10, 8),
            RepeatRule(RepeatKind.Weekly, weekdays = setOf(1, 4)).ending(RepeatEnd.After(3), moment(2026, 10, 1)).endDate,
        )
        // 31 Jan, 28 Feb, 31 Mar.
        assertEquals(
            ymd(2027, 3, 31),
            RepeatRule(RepeatKind.Monthly, dayOfMonth = 31).ending(RepeatEnd.After(3), moment(2027, 1, 31)).endDate,
        )
    }

    @Test
    fun `the last of N reminders is the last one the rule gives`() {
        val rule = RepeatRule(RepeatKind.Weekly, weekdays = setOf(1, 4)).ending(RepeatEnd.After(4), moment(2026, 10, 1))
        var due: LocalDateTime? = moment(2026, 10, 1)
        var count = 0
        while (due != null) {
            count++
            due = rule.nextOccurrence(due)
        }
        assertEquals(4, count)
    }

    @Test
    fun `counting ignores the end the rule had before`() {
        val had = RepeatRule(RepeatKind.Daily, endDate = ymd(2026, 10, 2))
        assertEquals(ymd(2026, 10, 5), had.ending(RepeatEnd.After(5), moment(2026, 10, 1)).endDate)
    }

    @Test
    fun `an end on a date is that date, and never takes the end away`() {
        val rule = RepeatRule(RepeatKind.Daily)
        assertEquals(ymd(2026, 12, 25), rule.ending(RepeatEnd.OnDate(ymd(2026, 12, 25)), moment(2026, 10, 1)).endDate)
        assertNull(rule.copy(endDate = ymd(2026, 12, 25)).ending(RepeatEnd.Never, moment(2026, 10, 1)).endDate)
        // Not before the first reminder.
        assertEquals(ymd(2026, 10, 1), rule.ending(RepeatEnd.OnDate(ymd(2026, 9, 1)), moment(2026, 10, 1)).endDate)
    }

    @Test
    fun `a reminder that does not repeat is not given an end`() {
        assertEquals(RepeatRule.Once, RepeatRule.Once.ending(RepeatEnd.After(3), moment(2026, 10, 1)))
        assertEquals(RepeatRule.Once, RepeatRule.Once.ending(RepeatEnd.OnDate(ymd(2027, 1, 1)), moment(2026, 10, 1)))
    }

    @Test
    fun `the end of a rule reads as a day or none, and unending takes it off`() {
        val rule = RepeatRule(RepeatKind.Daily, endDate = ymd(2027, 1, 1))
        assertEquals(RepeatEnd.OnDate(ymd(2027, 1, 1)), rule.ends)
        assertEquals(RepeatRule(RepeatKind.Daily), rule.unending)
        assertEquals(RepeatRule(RepeatKind.Daily), RepeatRule(RepeatKind.Daily).unending)
    }

    // --- stored and backed up --------------------------------------------------------------------------------------

    @Test
    fun `the end goes into the rule's JSON as a date and comes back`() {
        val rules = listOf(
            RepeatRule(RepeatKind.Daily, endDate = ymd(2027, 3, 3)),
            RepeatRule(RepeatKind.Weekly, weekdays = setOf(7, 2), endDate = ymd(2030, 12, 31)),
            RepeatRule(RepeatKind.Monthly, dayOfMonth = 15, endDate = ymd(2028, 2, 29)),
            RepeatRule(RepeatKind.Custom, intervalDays = 3),
        )
        for (rule in rules) assertEquals(rule, RepeatRule.fromJson(rule.toJson()))
        assertEquals("2027-03-03", (rules.first().toJson()["endDate"] as JsonPrimitive).content)
        assertNull(rules.last().toJson()["endDate"], "no end, no key: what an older version wrote is what is written")
    }

    @Test
    fun `a rule written before repeats could end reads as one with no end`() {
        val old = RepeatRule.fromJson(json("""{"kind":"weekly","weekdays":[1,4]}"""))
        assertEquals(RepeatRule(RepeatKind.Weekly, weekdays = setOf(1, 4)), old)
        assertNull(old.endDate)
    }

    @Test
    fun `an end that can't be read is no end, and the rest of the rule is kept`() {
        assertEquals(RepeatRule(RepeatKind.Daily), RepeatRule.fromJson(json("""{"kind":"daily","endDate":"someday"}""")))
        assertEquals(RepeatRule(RepeatKind.Daily), RepeatRule.fromJson(json("""{"kind":"daily","endDate":20270303}""")))
        assertEquals(RepeatRule(RepeatKind.Daily), RepeatRule.fromJson(json("""{"kind":"daily","endDate":null}""")))
        assertEquals(RepeatRule(RepeatKind.Daily), RepeatRule.fromJson(json("""{"kind":"daily","endDate":"2027-02-30"}""")))
    }

    @Test
    fun `a backup carries the end and brings it back`() {
        val reminder = reminder(RepeatRule(RepeatKind.Monthly, dayOfMonth = 5, endDate = ymd(2027, 3, 5)), moment(2026, 11, 5))
        val decoded = ReminderBackupCodec.decode(ReminderBackupCodec.encode(listOf(reminder)))
        assertNotNull(decoded)
        assertEquals(0, decoded.skipped)
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 5, endDate = ymd(2027, 3, 5)), decoded.reminders.single().repeatRule)
    }

    @Test
    fun `a backup made before reminders could end still reads, as reminders that never do`() {
        val old = """
            {"reminders":[{"title":"Rent","category":"payments","dueAt":"2026-10-03T09:00",
              "repeat":{"kind":"monthly","dayOfMonth":3},"status":"active","createdAt":"2026-09-01T10:00"}]}
        """.trimIndent()
        val decoded = ReminderBackupCodec.decode(old)
        assertNotNull(decoded)
        assertEquals(0, decoded.skipped)
        val read = decoded.reminders.single()
        assertEquals("Rent", read.title)
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 3), read.repeatRule)
    }

    @Test
    fun `a backup with an end it can't read keeps the reminder and drops only the end`() {
        val damaged = """
            {"reminders":[{"title":"Rent","category":"payments","dueAt":"2026-10-03T09:00",
              "repeat":{"kind":"monthly","dayOfMonth":3,"endDate":"not a date"},"createdAt":"2026-09-01T10:00"}]}
        """.trimIndent()
        val decoded = ReminderBackupCodec.decode(damaged)
        assertNotNull(decoded)
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 3), decoded.reminders.single().repeatRule)
    }

    // --- a reminder about a repeating payment ----------------------------------------------------------------------

    private fun payment(frequency: RecurrenceFrequency, end: LocalDateTime?, interval: Int = 1) = RecurringRule(
        id = 9,
        type = TransactionType.Expense,
        amount = 100_000,
        accountId = 1,
        frequency = frequency,
        interval = interval,
        startDate = moment(2026, 11, 5, 9, 0),
        endDate = end,
        nextDate = moment(2026, 11, 5, 9, 0),
        createdAt = moment(2026, 10, 1),
        updatedAt = moment(2026, 10, 1),
    )

    private val now = moment(2026, 10, 19, 12, 0)

    @Test
    fun `a reminder about a repeating payment stops where the payment does`() {
        val draft = ReminderDrafts.aboutRecurring(payment(RecurrenceFrequency.Monthly, moment(2027, 3, 5, 9, 0)), "Rent", "", now)
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 5, endDate = ymd(2027, 3, 5)), draft.repeatRule)
    }

    @Test
    fun `a payment with no end gives a reminder with none`() {
        val draft = ReminderDrafts.aboutRecurring(payment(RecurrenceFrequency.Monthly, null), "Rent", "", now)
        assertNull(draft.repeatRule.endDate)
    }

    @Test
    fun `a payment that has already ended gives a reminder that ends on its first day`() {
        val ended = payment(RecurrenceFrequency.Monthly, moment(2026, 6, 1)).copy(nextDate = moment(2026, 8, 1))
        val draft = ReminderDrafts.aboutRecurring(ended, "Rent", "", now)
        assertEquals(draft.dueAt.toLocalDate(), draft.repeatRule.endDate)
    }

    @Test
    fun `a rhythm a reminder cannot have is still a reminder that does not repeat, with no end`() {
        val draft = ReminderDrafts.aboutRecurring(payment(RecurrenceFrequency.Yearly, moment(2030, 1, 1)), "Insurance", "", now)
        assertEquals(RepeatRule.Once, draft.repeatRule)
    }
}

/** The end rides in the rule's JSON, so the table needs no new column: a row from before reads without one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderRepeatEndStorageTest {
    private lateinit var database: RemindersDatabase
    private lateinit var dao: ReminderDao

    @Before
    fun setUp() {
        database = RemindersDatabase(ApplicationProvider.getApplicationContext(), name = null)
        dao = ReminderDao(database, zone = ZoneId.of("Asia/Kolkata"), dispatcher = Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = database.close()

    private fun reminder(rule: RepeatRule) = Reminder(
        title = "Pay rent",
        category = ReminderCategory.Payments,
        dueAt = moment(2026, 11, 5),
        repeatRule = rule,
        createdAt = moment(2026, 10, 1),
        updatedAt = moment(2026, 10, 1),
    )

    @Test
    fun `a reminder's end is stored and read back`() = runBlocking {
        val rule = RepeatRule(RepeatKind.Monthly, dayOfMonth = 5, endDate = ymd(2027, 3, 5))
        val id = dao.insert(reminder(rule))
        assertEquals(rule, dao.getById(id)?.repeatRule)
    }

    @Test
    fun `changing a reminder's end updates it, and taking it off removes it`() = runBlocking {
        val id = dao.insert(reminder(RepeatRule(RepeatKind.Daily, endDate = ymd(2027, 1, 1))))
        val stored = dao.getById(id)!!
        dao.update(stored.copy(repeatRule = stored.repeatRule.copy(endDate = ymd(2027, 6, 1))))
        assertEquals(ymd(2027, 6, 1), dao.getById(id)?.repeatRule?.endDate)
        dao.update(stored.copy(repeatRule = stored.repeatRule.unending))
        assertNull(dao.getById(id)?.repeatRule?.endDate)
    }

    @Test
    fun `a row saved before reminders could end reads as one that never does`() = runBlocking {
        database.writableDatabase.execSQL(
            "INSERT INTO ${RemindersDatabase.TABLE} (title, category, due_at, repeat_rule_json, status, created_at, updated_at) " +
                "VALUES ('Old', 'personal', 1000, '{\"kind\":\"weekly\",\"weekdays\":[2]}', 'active', 1000, 1000)",
        )
        val old = dao.getAll().single()
        assertEquals(RepeatRule(RepeatKind.Weekly, weekdays = setOf(2)), old.repeatRule)
        assertNull(old.repeatRule.endDate)
        // And it can be given an end like any other.
        dao.update(old.copy(repeatRule = old.repeatRule.copy(endDate = ymd(2027, 1, 1))))
        assertEquals(ymd(2027, 1, 1), dao.getAll().single().repeatRule.endDate)
    }
}

/** The reminder form's "Ends": the same three choices as a repeating payment, never unless the user says so. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ReminderEndsFormTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private var saved: Reminder? = null

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("reminder_ends")
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    private fun choice(label: String) = compose.onNode(hasText(label) and radio)

    private fun reminder(rule: RepeatRule) = Reminder(
        id = 4,
        title = "Rent",
        category = ReminderCategory.Payments,
        dueAt = moment(2030, 1, 15),
        repeatRule = rule,
        createdAt = moment(2029, 12, 1),
        updatedAt = moment(2029, 12, 1),
    )

    private fun showForm(initial: Reminder? = null, existing: Reminder? = null) {
        compose.setContent {
            env.Content {
                AddReminderSheet(existing = existing, cards = emptyList(), onAddCard = { null }, onDone = { saved = it }, initial = initial)
            }
        }
        compose.waitForIdle()
    }

    private fun save(label: String) {
        // Save is pinned below the form's scrolling body, so there is nothing to scroll it into.
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a reminder that does not repeat has no Ends`() {
        showForm(initial = reminder(RepeatRule.Once))
        assertTrue(compose.onAllNodesWithText("Ends").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `a repeating reminder ends never unless set, and is saved without an end`() {
        showForm(initial = reminder(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15)))
        choice("Never").performScrollTo().assertIsSelected()
        save("Save reminder")
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15), saved?.repeatRule)
    }

    @Test
    fun `after a number of times is saved as the day of the last`() {
        showForm(initial = reminder(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15)))
        choice("After").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("After 12 times · last on 15 Dec 2030").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("More").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("After 13 times · last on 15 Jan 2031").performScrollTo().assertIsDisplayed()
        save("Save reminder")
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15, endDate = ymd(2031, 1, 15)), saved?.repeatRule)
    }

    @Test
    fun `a reminder with an end shows it, and in its label`() {
        showForm(existing = reminder(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15, endDate = ymd(2030, 6, 15))))
        choice("On a date").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Monthly on day 15 until 15 Jun 2030").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("6 times · last on 15 Jun 2030").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `saving a reminder with an end changes nothing about it`() {
        val rule = RepeatRule(RepeatKind.Monthly, dayOfMonth = 15, endDate = ymd(2030, 6, 15))
        showForm(existing = reminder(rule))
        save("Save changes")
        assertEquals(rule, saved?.repeatRule)
    }

    @Test
    fun `the end can be taken off`() {
        showForm(existing = reminder(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15, endDate = ymd(2030, 6, 15))))
        choice("Never").performScrollTo().performClick()
        compose.waitForIdle()
        save("Save changes")
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15), saved?.repeatRule)
    }

    @Test
    fun `changing how often it repeats leaves the end where it was set`() {
        showForm(existing = reminder(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15, endDate = ymd(2030, 6, 15))))
        compose.onNodeWithText("Repeat").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Daily").performClick()
        compose.waitForIdle()
        save("Save changes")
        assertEquals(RepeatRule(RepeatKind.Daily, endDate = ymd(2030, 6, 15)), saved?.repeatRule)
    }

    @Test
    fun `turning repeating off leaves no end behind`() {
        showForm(existing = reminder(RepeatRule(RepeatKind.Monthly, dayOfMonth = 15, endDate = ymd(2030, 6, 15))))
        compose.onNodeWithText("Repeat").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Once").performClick()
        compose.waitForIdle()
        save("Save changes")
        assertEquals(RepeatRule.Once, saved?.repeatRule)
    }
}
