package com.example.normpix.reminders

import com.example.normpix.finance.domain.RecurringRule
import java.time.LocalDateTime

/*
 * A repeating payment and its reminder are one thing seen from two sides: the payment says what is due and when, the reminder is
 * how the person is told as the day nears. So they are kept in step, in one place, whichever screen the payment is changed from:
 *
 *  - a payment that is going (not paused, not over) has a reminder: a new one is given its own at once, and one that comes back
 *    from a pause or an end gets it again;
 *  - editing the payment edits its reminder - the name and the line under it (only where the person has not put words of their own
 *    there), and, when the payment's schedule changed, the day it falls on and how it repeats;
 *  - a paused or ended payment has none, since no payment is coming; and a deleted one takes its reminder with it.
 *
 * A reminder the person has taken away from a payment that is still going stays away: nothing here brings it back behind their
 * back. (The editor's bell makes a new one on request.) This is the rule; [ReminderFlows] applies it to the stored reminders.
 */
object RecurringReminders {
    /** The reminders about the repeating payment [ruleId], in whatever state they are. */
    fun about(reminders: List<Reminder>, ruleId: Long): List<Reminder> =
        reminders.filter { it.link == ReminderLink.recurring(ruleId) }

    /** Whether the payments of [rule] are still to come: it is neither paused nor over. */
    fun isGoing(rule: RecurringRule): Boolean = rule.active && !rule.isFinished

    /** One change to the stored reminders. */
    sealed interface Step {
        data class Add(val reminder: Reminder) : Step

        data class Change(val reminder: Reminder) : Step

        data class Delete(val id: Long) : Step
    }

    /** How a payment and its schedule read as words, now and (for an edit) before: what a reminder was made to say. */
    class Words(val title: String, val summary: String, val beforeTitle: String? = null, val beforeSummary: String? = null)

    /**
     * What to do to the reminders [linked] to [rule] after it was saved. [before] is the payment as it was (null for a new one).
     * Nothing is returned for the common case of nothing to change.
     */
    fun stepsAfterSave(rule: RecurringRule, before: RecurringRule?, linked: List<Reminder>, words: Words, now: LocalDateTime): List<Step> {
        if (!isGoing(rule)) return linked.mapNotNull { reminder -> reminder.id?.let(Step::Delete) }
        val draft = ReminderDrafts.aboutRecurring(rule, words.title, words.summary, now)
        if (linked.isEmpty()) {
            // New, or back from a pause or an end: given its reminder. Edited while going, with none: the person's choice.
            val comesBack = before == null || !isGoing(before)
            return if (comesBack) listOf(Step.Add(draft)) else emptyList()
        }
        if (before == null) return emptyList()
        val rescheduled = before.frequency != rule.frequency || before.interval != rule.interval ||
            before.startDate != rule.startDate || before.endDate != rule.endDate
        return linked.mapNotNull { reminder ->
            var next = reminder.copy(
                // Their own words stay: only what NormPix wrote is rewritten.
                title = if (reminder.title == words.beforeTitle) words.title else reminder.title,
                notes = if (reminder.notes == words.beforeSummary) words.summary else reminder.notes,
            )
            if (rescheduled) {
                // The day the payment now falls on, at the time of day the reminder already rings at.
                val time = if (reminder.status == ReminderStatus.Completed) draft.dueAt.toLocalTime() else reminder.dueAt.toLocalTime()
                val due = LocalDateTime.of(draft.dueAt.toLocalDate(), time).takeIf { it.isAfter(now) } ?: draft.dueAt
                next = next.copy(
                    dueAt = due,
                    repeatRule = draft.repeatRule,
                    status = ReminderStatus.Active,
                    snoozedUntil = null,
                    lastFiredAt = null,
                    completedAt = null,
                )
            }
            next.takeIf { it != reminder }?.copy(updatedAt = now)?.let(Step::Change)
        }
    }

    /** The reminders to take away with the payment [ruleId] that was deleted. */
    fun stepsAfterDelete(linked: List<Reminder>): List<Step> = linked.mapNotNull { reminder -> reminder.id?.let(Step::Delete) }
}
