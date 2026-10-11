package com.example.normpix.finance.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.RecurringRule

/**
 * Starts a reminder about something in the ledger - the reminder form, already filled in and attached to it. Reminders are
 * their own part of the app; this is the one door the finance screens use to reach them, so an expense's receipt, a
 * repeating payment or an account can say "Remind me" without knowing how reminders are made.
 */
interface ReminderLauncher {
    fun about(transaction: FinanceTransaction)

    fun about(rule: RecurringRule)

    fun about(account: Account)
}

/** The launcher the screens use, or null where reminders are not available (then no "Remind me" is offered). */
val LocalReminderLauncher = staticCompositionLocalOf<ReminderLauncher?> { null }
