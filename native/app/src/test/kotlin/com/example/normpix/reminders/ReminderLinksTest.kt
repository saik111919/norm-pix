package com.example.normpix.reminders

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What a reminder can be attached to - a card, an account, a transaction, a repeating payment - how that is stored, how a
 * reminder from before attachments existed carries over, and the drafts that start a reminder already about something.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderLinksTest {
    private val now = LocalDateTime.of(2026, 10, 11, 12, 0)
    private val zone = ZoneId.of("Asia/Kolkata")
    private lateinit var context: Context

    @BeforeTest
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("reminder_links_test.db")
    }

    @AfterTest
    fun tearDown() {
        context.deleteDatabase("reminder_links_test.db")
    }

    private fun reminder(link: ReminderLink?) = Reminder(
        title = "Pay it",
        category = ReminderCategory.Payments,
        dueAt = now.plusDays(1),
        repeatRule = RepeatRule.Once,
        link = link,
        createdAt = now,
        updatedAt = now,
    )

    // --- storage -----------------------------------------------------------------------------------------------------

    @Test
    fun `every kind of attachment is stored and read back, and none is the default`() = runBlocking {
        val database = RemindersDatabase(context, name = null)
        val dao = ReminderDao(database, zone = zone, dispatcher = Dispatchers.Unconfined)
        try {
            for (link in listOf(
                ReminderLink.card("card-1"),
                ReminderLink.account(7),
                ReminderLink.transaction(42),
                ReminderLink.recurring(3),
                null,
            )) {
                val id = dao.insert(reminder(link))
                assertEquals(link, dao.getById(id)!!.link, "round trip of $link")
            }
            // Changing what it is attached to, or taking it away, sticks.
            val id = dao.insert(reminder(ReminderLink.transaction(1)))
            dao.update(dao.getById(id)!!.copy(link = ReminderLink.account(2)))
            assertEquals(ReminderLink.account(2), dao.getById(id)!!.link)
            dao.update(dao.getById(id)!!.copy(link = null))
            assertNull(dao.getById(id)!!.link)
        } finally {
            database.close()
        }
    }

    @Test
    fun `a card link is also kept where it always was, so nothing reads it differently`() = runBlocking {
        val database = RemindersDatabase(context, name = null)
        val dao = ReminderDao(database, zone = zone, dispatcher = Dispatchers.Unconfined)
        try {
            val id = dao.insert(reminder(ReminderLink.card("card-9")))
            val legacy = database.readableDatabase.rawQuery("SELECT linked_account_id FROM reminders WHERE id = $id", null)
                .use { it.moveToFirst(); it.getString(0) }
            assertEquals("card-9", legacy)
            val other = dao.insert(reminder(ReminderLink.transaction(5)))
            val none = database.readableDatabase.rawQuery("SELECT linked_account_id FROM reminders WHERE id = $other", null)
                .use { it.moveToFirst(); it.isNull(0) }
            assertEquals(true, none, "only a card is written to the card column")
        } finally {
            database.close()
        }
    }

    /** The version 1 table as the previous release left it: a card link in `linked_account_id` and no kind or reference. */
    private class VersionOne(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE reminders (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, notes TEXT, category TEXT NOT NULL,
                  custom_category_label TEXT, due_at INTEGER NOT NULL, repeat_rule_json TEXT NOT NULL,
                  status TEXT NOT NULL DEFAULT 'active', snoozed_until INTEGER, linked_account_id TEXT,
                  created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, completed_at INTEGER, last_fired_at INTEGER
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    @Test
    fun `reminders from before attachments keep their card, and the rest stay unattached`() = runBlocking {
        val name = "reminder_links_test.db"
        val stamp = now.atZone(zone).toInstant().toEpochMilli()
        VersionOne(context, name).use { helper ->
            val db = helper.writableDatabase
            for ((title, card) in listOf("Pay card bill" to "card-1", "Water the plants" to null)) {
                db.insertOrThrow(
                    "reminders",
                    null,
                    ContentValues().apply {
                        put("title", title); put("category", "payments"); put("due_at", stamp); put("repeat_rule_json", """{"kind":"once"}""")
                        put("linked_account_id", card); put("created_at", stamp); put("updated_at", stamp)
                    },
                )
            }
        }
        val database = RemindersDatabase(context, name = name)
        val dao = ReminderDao(database, zone = zone, dispatcher = Dispatchers.Unconfined)
        try {
            val all = dao.getAll().associateBy { it.title }
            assertEquals(ReminderLink.card("card-1"), all.getValue("Pay card bill").link, "the card it had")
            assertNull(all.getValue("Water the plants").link, "nothing invented")
            // And the upgraded table takes the new kinds.
            dao.update(all.getValue("Pay card bill").copy(link = ReminderLink.recurring(4)))
            assertEquals(ReminderLink.recurring(4), dao.getById(all.getValue("Pay card bill").id!!)!!.link)
        } finally {
            database.close()
        }
    }

    @Test
    fun `only a card counts as a linked card`() {
        assertEquals("card-1", reminder(ReminderLink.card("card-1")).linkedCardId)
        assertNull(reminder(ReminderLink.account(1)).linkedCardId)
        assertNull(reminder(null).linkedCardId)
    }

    // --- drafts ------------------------------------------------------------------------------------------------------

    private fun expense(at: LocalDateTime, type: TransactionType = TransactionType.Expense) = FinanceTransaction(
        id = 42,
        type = type,
        amount = 64_900,
        accountId = 1,
        categoryId = 2,
        occurredAt = at,
        createdAt = now,
        updatedAt = now,
    )

    @Test
    fun `a reminder about a past expense is a follow-up for tomorrow morning, attached to it`() {
        val draft = ReminderDrafts.aboutTransaction(expense(now.minusDays(3)), "Netflix", "Expense of ₹649 on 8 Oct from HDFC Savings", now)
        assertEquals("Netflix", draft.title)
        assertEquals("Expense of ₹649 on 8 Oct from HDFC Savings", draft.notes)
        assertEquals(ReminderCategory.Expenses, draft.category)
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 0), draft.dueAt)
        assertEquals(ReminderLink.transaction(42), draft.link)
        assertEquals(RepeatRule.Once, draft.repeatRule)
        assertNull(draft.id, "a draft is a new reminder")
    }

    @Test
    fun `one dated ahead is due that day, and income and transfers have their own categories`() {
        val ahead = now.plusDays(5)
        assertEquals(ahead, ReminderDrafts.aboutTransaction(expense(ahead), "Rent", "", now).dueAt)
        assertEquals(ReminderCategory.Income, ReminderDrafts.aboutTransaction(expense(now, TransactionType.Income), "Salary", "", now).category)
        assertEquals(ReminderCategory.Accounts, ReminderDrafts.aboutTransaction(expense(now, TransactionType.Transfer), "Move", "", now).category)
    }

    private fun rule(frequency: RecurrenceFrequency, interval: Int = 1, next: LocalDateTime = now.plusDays(3), type: TransactionType = TransactionType.Expense) =
        RecurringRule(
            id = 9,
            type = type,
            amount = 1_000,
            accountId = 1,
            categoryId = 2,
            frequency = frequency,
            interval = interval,
            startDate = now.minusMonths(2),
            nextDate = next,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun `a reminder about a repeating payment is due when it next is, and repeats the way it does`() {
        val next = LocalDateTime.of(2026, 11, 5, 9, 30) // a Thursday
        val monthly = ReminderDrafts.aboutRecurring(rule(RecurrenceFrequency.Monthly, next = next), "Rent", "Every month · ₹1,000", now)
        assertEquals(next, monthly.dueAt)
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 5), monthly.repeatRule)
        assertEquals(ReminderLink.recurring(9), monthly.link)
        assertEquals(ReminderCategory.Payments, monthly.category)

        assertEquals(RepeatRule(RepeatKind.Weekly, weekdays = setOf(4)), ReminderDrafts.reminderRepeatFor(rule(RecurrenceFrequency.Weekly, next = next)))
        assertEquals(RepeatRule(RepeatKind.Daily), ReminderDrafts.reminderRepeatFor(rule(RecurrenceFrequency.Daily)))
        assertEquals(RepeatRule(RepeatKind.Custom, intervalDays = 14), ReminderDrafts.reminderRepeatFor(rule(RecurrenceFrequency.Weekly, interval = 2)))
        assertEquals(RepeatRule(RepeatKind.Custom, intervalDays = 3), ReminderDrafts.reminderRepeatFor(rule(RecurrenceFrequency.Daily, interval = 3)))
        // A reminder cannot say "every 2 months" or "every year": it is then a one-off, not a wrong rhythm.
        assertEquals(RepeatRule.Once, ReminderDrafts.reminderRepeatFor(rule(RecurrenceFrequency.Monthly, interval = 2)))
        assertEquals(RepeatRule.Once, ReminderDrafts.reminderRepeatFor(rule(RecurrenceFrequency.Yearly)))
        assertEquals(ReminderCategory.Income, ReminderDrafts.aboutRecurring(rule(RecurrenceFrequency.Monthly, type = TransactionType.Income), "Salary", "", now).category)
    }

    @Test
    fun `a payment already due says tomorrow morning rather than a time that has passed`() {
        val draft = ReminderDrafts.aboutRecurring(rule(RecurrenceFrequency.Monthly, next = now.minusDays(1)), "Rent", "", now)
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 0), draft.dueAt)
    }

    @Test
    fun `a reminder about a card with a bill is to pay it, and about any other account is by name`() {
        val card = Account(id = 5, name = "HDFC Credit", kind = AccountKind.Card, createdAt = now, updatedAt = now)
        val owing = ReminderDrafts.aboutAccount(card, owed = 120_000, summary = "₹1,200 owed on HDFC Credit", now = now)
        assertEquals("Pay HDFC Credit bill", owing.title)
        assertEquals(ReminderCategory.Payments, owing.category)
        assertEquals(ReminderLink.account(5), owing.link)

        assertEquals("HDFC Credit", ReminderDrafts.aboutAccount(card, owed = 0, summary = null, now = now).title)
        val savings = Account(id = 6, name = "Savings", kind = AccountKind.Savings, createdAt = now, updatedAt = now)
        val plain = ReminderDrafts.aboutAccount(savings, owed = 0, summary = null, now = now)
        assertEquals("Savings", plain.title)
        assertEquals(ReminderCategory.Accounts, plain.category)
    }
}
