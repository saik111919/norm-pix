package com.example.normpix.reminders

import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import java.time.LocalDateTime
import java.time.LocalTime

/*
 * A reminder that starts out knowing what it is about. Asking to be reminded of an expense, a repeating payment or an account
 * opens the ordinary reminder form already filled in - a title, a category, a time and, for a repeating payment, the same
 * rhythm - with the attachment made. Nothing is saved until the form is: this only supplies the starting point.
 */
object ReminderDrafts {
    /** When nothing in the thing itself says when: the next morning, a time people notice a reminder at. */
    fun tomorrowMorning(now: LocalDateTime): LocalDateTime = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(9, 0))

    /**
     * About a recorded [transaction]. [name] is what to call it ("Netflix", "Groceries") and [summary] the line that says what
     * it was ("Expense of ₹649 on 12 Oct from HDFC Savings"). One dated in the future (a bill entered ahead of time) is due
     * that day; one already past is something to follow up, so it is due tomorrow morning.
     */
    fun aboutTransaction(transaction: FinanceTransaction, name: String, summary: String, now: LocalDateTime): Reminder = Reminder(
        title = name,
        notes = summary,
        category = when (transaction.type) {
            TransactionType.Expense -> ReminderCategory.Expenses
            TransactionType.Income -> ReminderCategory.Income
            TransactionType.Transfer -> ReminderCategory.Accounts
        },
        dueAt = transaction.occurredAt.takeIf { it.isAfter(now) } ?: tomorrowMorning(now),
        repeatRule = RepeatRule.Once,
        link = transaction.id?.let(ReminderLink::transaction),
        createdAt = now,
        updatedAt = now,
    )

    /**
     * About a repeating payment: due when its next occurrence is, and repeating the way it does when a reminder can say so
     * (every day, every week, every month, or every N days), so one reminder keeps pace with the payment.
     */
    fun aboutRecurring(rule: RecurringRule, name: String, summary: String, now: LocalDateTime): Reminder = Reminder(
        title = name,
        notes = summary,
        category = when (rule.type) {
            TransactionType.Expense -> ReminderCategory.Payments
            TransactionType.Income -> ReminderCategory.Income
            TransactionType.Transfer -> ReminderCategory.Accounts
        },
        dueAt = rule.nextDate.takeIf { it.isAfter(now) } ?: tomorrowMorning(now),
        repeatRule = reminderRepeatFor(rule),
        link = rule.id?.let(ReminderLink::recurring),
        createdAt = now,
        updatedAt = now,
    )

    /** About an account: a card with something owed is a bill to pay; any other is just remembered by name. */
    fun aboutAccount(account: Account, owed: Long, summary: String?, now: LocalDateTime): Reminder = Reminder(
        title = if (account.kind == AccountKind.Card && owed > 0) "Pay ${account.name} bill" else account.name,
        notes = summary,
        category = if (account.kind == AccountKind.Card) ReminderCategory.Payments else ReminderCategory.Accounts,
        dueAt = tomorrowMorning(now),
        repeatRule = RepeatRule.Once,
        link = account.id?.let(ReminderLink::account),
        createdAt = now,
        updatedAt = now,
    )

    /**
     * The reminder rhythm that matches [rule]'s, or [RepeatRule.Once] when there is none: a reminder repeats daily, weekly on a
     * day, monthly on a day, or every N days - "every 2 months" and "every year" are not among them.
     */
    fun reminderRepeatFor(rule: RecurringRule): RepeatRule {
        val anchor = rule.nextDate
        return when (rule.frequency) {
            RecurrenceFrequency.Daily -> if (rule.interval == 1) RepeatRule(RepeatKind.Daily) else RepeatRule(RepeatKind.Custom, intervalDays = rule.interval)
            RecurrenceFrequency.Weekly -> if (rule.interval == 1) {
                RepeatRule(RepeatKind.Weekly, weekdays = setOf(anchor.dayOfWeek.value))
            } else {
                RepeatRule(RepeatKind.Custom, intervalDays = rule.interval * 7)
            }
            RecurrenceFrequency.Monthly -> if (rule.interval == 1) RepeatRule(RepeatKind.Monthly, dayOfMonth = anchor.dayOfMonth) else RepeatRule.Once
            RecurrenceFrequency.Yearly -> RepeatRule.Once
        }
    }
}
