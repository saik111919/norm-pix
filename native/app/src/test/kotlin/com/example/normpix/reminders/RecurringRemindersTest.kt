package com.example.normpix.reminders

import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A repeating payment and its reminder are kept in step: made with it, edited with it, paused with it, deleted with it. */
class RecurringRemindersTest {
    private val now = LocalDateTime.of(2026, 11, 5, 10, 0)
    private val words = RecurringReminders.Words(
        title = "Rent",
        summary = "Every month · ₹15,000",
        beforeTitle = "Rent",
        beforeSummary = "Every month · ₹12,000",
    )

    private fun rule(
        id: Long = 7,
        frequency: RecurrenceFrequency = RecurrenceFrequency.Monthly,
        interval: Int = 1,
        next: LocalDateTime = LocalDateTime.of(2026, 12, 1, 0, 0),
        end: LocalDateTime? = null,
        active: Boolean = true,
    ) = RecurringRule(
        id = id,
        type = TransactionType.Expense,
        amount = 15_000_00,
        accountId = 1,
        payee = "Rent",
        frequency = frequency,
        interval = interval,
        startDate = LocalDateTime.of(2026, 10, 1, 0, 0),
        endDate = end,
        nextDate = next,
        active = active,
        createdAt = now,
        updatedAt = now,
    )

    /** The reminder a payment is given, as it is first made. */
    private fun reminderOf(rule: RecurringRule, id: Long = 70, title: String = "Rent", notes: String? = "Every month · ₹12,000") =
        ReminderDrafts.aboutRecurring(rule, title, notes.orEmpty(), now).copy(id = id, notes = notes)

    @Test
    fun aNewPaymentIsGivenItsReminder() {
        val steps = RecurringReminders.stepsAfterSave(rule(), before = null, linked = emptyList(), words = words, now = now)
        val add = assertIs<RecurringReminders.Step.Add>(steps.single())
        assertEquals(ReminderLink.recurring(7), add.reminder.link)
        assertEquals(LocalDateTime.of(2026, 12, 1, 9, 0), add.reminder.dueAt, "on its day, in the morning")
        assertEquals(RepeatKind.Monthly, add.reminder.repeatRule.kind)
    }

    @Test
    fun aPaymentThatAlreadyHasOneIsNotGivenAnother() {
        val r = rule()
        assertTrue(RecurringReminders.stepsAfterSave(r, before = null, linked = listOf(reminderOf(r)), words = words, now = now).isEmpty())
    }

    @Test
    fun editingTheAmountRewritesTheLineUnderTheReminder_andNothingElse() {
        val before = rule()
        val reminder = reminderOf(before)
        val steps = RecurringReminders.stepsAfterSave(before.copy(amount = 15_000_00), before, listOf(reminder), words, now)
        val changed = assertIs<RecurringReminders.Step.Change>(steps.single()).reminder
        assertEquals("Every month · ₹15,000", changed.notes)
        assertEquals(reminder.dueAt, changed.dueAt, "the day is the same: the schedule did not change")
        assertEquals(reminder.repeatRule, changed.repeatRule)
    }

    @Test
    fun theirOwnWordsOnAReminderStay() {
        val before = rule()
        val mine = reminderOf(before, title = "Pay the landlord!", notes = "Cash, ask for the receipt")
        val steps = RecurringReminders.stepsAfterSave(before, before, listOf(mine), words, now)
        assertTrue(steps.isEmpty(), "nothing NormPix wrote was there to rewrite, so nothing changes")
    }

    @Test
    fun changingTheScheduleMovesTheReminderToTheNewDay_atTheTimeItAlreadyRangAt() {
        val before = rule()
        val reminder = reminderOf(before).copy(dueAt = LocalDateTime.of(2026, 12, 1, 18, 30))
        val after = before.copy(frequency = RecurrenceFrequency.Weekly, interval = 2, nextDate = LocalDateTime.of(2026, 11, 12, 0, 0))
        val changed = assertIs<RecurringReminders.Step.Change>(
            RecurringReminders.stepsAfterSave(after, before, listOf(reminder), words, now).single(),
        ).reminder
        assertEquals(LocalDateTime.of(2026, 11, 12, 18, 30), changed.dueAt, "the new day, at its own hour")
        assertEquals(RepeatKind.Custom, changed.repeatRule.kind)
        assertEquals(14, changed.repeatRule.intervalDays)
        assertEquals(ReminderStatus.Active, changed.status)
    }

    @Test
    fun aPausedOrEndedPaymentHasNoReminder() {
        val going = rule()
        val reminder = reminderOf(going)
        val paused = RecurringReminders.stepsAfterSave(going.copy(active = false), going, listOf(reminder), words, now)
        assertEquals(listOf<RecurringReminders.Step>(RecurringReminders.Step.Delete(70)), paused)

        val over = going.copy(endDate = LocalDateTime.of(2026, 11, 20, 0, 0), nextDate = LocalDateTime.of(2026, 12, 1, 0, 0))
        assertTrue(over.isFinished)
        assertEquals(
            listOf<RecurringReminders.Step>(RecurringReminders.Step.Delete(70)),
            RecurringReminders.stepsAfterSave(over, going, listOf(reminder), words, now),
        )
    }

    @Test
    fun aPaymentComingBackFromAPauseGetsItsReminderAgain() {
        val paused = rule(active = false)
        val resumed = paused.copy(active = true)
        val add = RecurringReminders.stepsAfterSave(resumed, paused, emptyList(), words, now).single()
        assertIs<RecurringReminders.Step.Add>(add)
    }

    @Test
    fun aReminderThePersonTookAwayIsNotBroughtBackBehindTheirBack() {
        val going = rule()
        assertTrue(RecurringReminders.stepsAfterSave(going.copy(amount = 1), going, emptyList(), words, now).isEmpty())
    }

    @Test
    fun aDeletedPaymentTakesItsRemindersWithIt() {
        val r = rule()
        val steps = RecurringReminders.stepsAfterDelete(listOf(reminderOf(r, id = 70), reminderOf(r, id = 71)))
        assertEquals(listOf(70L, 71L), steps.map { (it as RecurringReminders.Step.Delete).id })
    }

    @Test
    fun onlyTheRemindersAboutThatPaymentAreFound() {
        val a = rule(id = 7)
        val b = rule(id = 8)
        val all = listOf(reminderOf(a, id = 70), reminderOf(b, id = 71))
        assertEquals(listOf(70L), RecurringReminders.about(all, 7).map { it.id })
    }
}
