package com.example.normpix.reminders

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A reminder about a card that has a due day follows that day: it is first due on the next one, at nine in the morning,
 * and repeats on it every month. A card with no due day is as it always was.
 */
class BillingReminderDraftTest {
    private fun card(dueDay: Int?, kind: AccountKind = AccountKind.Card) =
        Account(id = 5, name = "HDFC Credit", kind = kind, dueDay = dueDay, createdAt = NOW, updatedAt = NOW)

    private fun draft(dueDay: Int?, now: LocalDateTime, owed: Long = 120_000, kind: AccountKind = AccountKind.Card) =
        ReminderDrafts.aboutAccount(card(dueDay, kind), owed = owed, summary = null, now = now)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 9) = LocalDateTime.of(year, month, day, hour, 0)

    @Test
    fun `a card with a due day is reminded on it, every month`() {
        val reminder = draft(dueDay = 20, now = at(2026, 10, 12, 12))
        assertEquals("Pay HDFC Credit bill", reminder.title)
        assertEquals(at(2026, 10, 20), reminder.dueAt)
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 20), reminder.repeatRule)
        assertEquals(ReminderCategory.Payments, reminder.category)
        assertEquals(ReminderLink.account(5), reminder.link)
    }

    @Test
    fun `the first reminder is today's while nine o'clock has not struck, and next month's after`() {
        assertEquals(at(2026, 10, 20), draft(dueDay = 20, now = at(2026, 10, 20, 8)).dueAt)
        assertEquals(at(2026, 11, 20), draft(dueDay = 20, now = at(2026, 10, 20, 9)).dueAt, "nine exactly is already gone")
        assertEquals(at(2026, 11, 20), draft(dueDay = 20, now = at(2026, 10, 20, 15)).dueAt)
    }

    @Test
    fun `it rolls over into the next year`() {
        assertEquals(at(2027, 1, 20), draft(dueDay = 20, now = at(2026, 12, 21, 12)).dueAt)
    }

    @Test
    fun `a last-days due date lands on the last day of a short month`() {
        val reminder = draft(dueDay = 31, now = at(2026, 2, 10, 12))
        assertEquals(at(2026, 2, 28), reminder.dueAt)
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 31), reminder.repeatRule, "it still repeats on the 31st, and clamps month by month")
    }

    @Test
    fun `a card with a due day is a bill to pay even when nothing is owed yet`() {
        val reminder = draft(dueDay = 7, now = at(2026, 10, 12, 12), owed = 0)
        assertEquals("Pay HDFC Credit bill", reminder.title)
        assertEquals(at(2026, 11, 7), reminder.dueAt)
        assertEquals(RepeatRule(RepeatKind.Monthly, dayOfMonth = 7), reminder.repeatRule)
    }

    @Test
    fun `a card with no due day starts tomorrow morning and does not repeat`() {
        val now = at(2026, 10, 12, 12)
        val owing = draft(dueDay = null, now = now)
        assertEquals("Pay HDFC Credit bill", owing.title)
        assertEquals(at(2026, 10, 13), owing.dueAt)
        assertEquals(RepeatRule.Once, owing.repeatRule)

        val clear = draft(dueDay = null, now = now, owed = 0)
        assertEquals("HDFC Credit", clear.title)
        assertEquals(RepeatRule.Once, clear.repeatRule)
    }

    @Test
    fun `an account that is not a card ignores a due day`() {
        val reminder = draft(dueDay = 20, now = at(2026, 10, 12, 12), owed = 0, kind = AccountKind.Bank)
        assertEquals("HDFC Credit", reminder.title)
        assertEquals(at(2026, 10, 13), reminder.dueAt)
        assertEquals(RepeatRule.Once, reminder.repeatRule)
        assertEquals(ReminderCategory.Accounts, reminder.category)
    }

    @Test
    fun `the morning of a day is nine o'clock on the next time it comes round`() {
        assertEquals(at(2026, 10, 20), ReminderDrafts.nextMorningOnDay(20, at(2026, 10, 1, 0)))
        assertEquals(at(2026, 10, 20), ReminderDrafts.nextMorningOnDay(20, at(2026, 10, 20, 8)))
        assertEquals(at(2026, 11, 20), ReminderDrafts.nextMorningOnDay(20, at(2026, 10, 20, 10)))
        assertEquals(at(2026, 4, 30), ReminderDrafts.nextMorningOnDay(31, at(2026, 4, 30, 8)))
        assertEquals(at(2026, 5, 31), ReminderDrafts.nextMorningOnDay(31, at(2026, 4, 30, 10)))
    }

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    }
}
