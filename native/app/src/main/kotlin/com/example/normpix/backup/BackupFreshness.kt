package com.example.normpix.backup

/*
 * When to say "back up your data" (2026-11-07, suggested after the owner asked what the app should have next). Everything NormPix
 * keeps is on one phone and encrypted there, so a lost or broken phone loses all of it unless a backup was made. This is the
 * rule for when Home says so; nothing here reads a clock or a file: the caller gives the times, so every case is testable.
 *
 * A backup counts only when it was saved from the real data (not the sample set) as one of the restorable kinds - the small
 * file, the full zip or the app backup - and only the time of the last one is kept, in `AppPreferences` (never the file).
 */
object BackupFreshness {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Remind after this many days without a backup unless the user chose another (Settings > Data & backup > Backup reminder). */
    const val DEFAULT_REMINDER_DAYS = 14

    /** What the user can pick: every week, two weeks or month. 0 turns the reminder off. */
    val REMINDER_CHOICES: List<Int> = listOf(7, 14, 30)

    /** How long a phone with data and no backup at all is left alone before the first nudge: time to have used the app. */
    const val GRACE_DAYS = 3

    /** "Later" on the banner puts the nudge off this long. */
    const val SNOOZE_DAYS = 3

    /** A stored reminder length that is not one the app offers (an old or edited file) falls back to the default. */
    fun normalise(days: Int): Int = if (days == 0 || days in REMINDER_CHOICES) days else DEFAULT_REMINDER_DAYS

    /** The reminder's length in words, for a line under a figure: "every week", "every 2 weeks", "every month". */
    fun words(reminderDays: Int): String = when (reminderDays) {
        7 -> "every week"
        14 -> "every 2 weeks"
        30 -> "every month"
        else -> "every $reminderDays days"
    }

    /** When a snooze pressed at [now] ends. */
    fun snoozeUntil(now: Long): Long = now + SNOOZE_DAYS * DAY_MS

    /** "today", "yesterday" or "5 days ago": how long ago [lastBackupAt] was, for a settings row. */
    fun ago(lastBackupAt: Long, now: Long): String = when (val days = ((now - lastBackupAt).coerceAtLeast(0)) / DAY_MS) {
        0L -> "today"
        1L -> "yesterday"
        else -> "$days days ago"
    }

    /** What Home should say about backups, if anything. */
    sealed interface Nudge {
        /** Nothing: reminders are off, there is nothing worth keeping yet, the last backup is recent, or it was put off. */
        data object None : Nudge

        /** Data and no backup at all, [days] after the app was installed. */
        data class Never(val days: Long) : Nudge

        /** The last backup is [days] days old, past the reminder. [overdue] once it is twice as old as the reminder asks. */
        data class Stale(val days: Long, val overdue: Boolean) : Nudge
    }

    /**
     * @param lastBackupAt when the last real backup was saved (epoch millis), 0 for never.
     * @param installedAt when the app was installed (epoch millis): where "never" counts from.
     * @param reminderDays the user's choice: 0 for off, else one of [REMINDER_CHOICES].
     * @param hasData whether there is anything worth backing up (a recorded transaction, or an account beyond the starting one).
     * @param snoozedUntil epoch millis the user put the nudge off until, 0 for not at all.
     */
    fun nudge(lastBackupAt: Long, installedAt: Long, now: Long, reminderDays: Int, hasData: Boolean, snoozedUntil: Long): Nudge {
        if (reminderDays <= 0 || !hasData || now < snoozedUntil) return Nudge.None
        if (lastBackupAt > 0) {
            // A clock set back after the backup reads as "just now", not as a negative age.
            val days = ((now - lastBackupAt).coerceAtLeast(0)) / DAY_MS
            return if (days >= reminderDays) Nudge.Stale(days, overdue = days >= reminderDays * 2L) else Nudge.None
        }
        // Where "never" counts from is unknown (the system gave no install time): say nothing rather than guess.
        if (installedAt <= 0) return Nudge.None
        val days = ((now - installedAt).coerceAtLeast(0)) / DAY_MS
        return if (days >= GRACE_DAYS) Nudge.Never(days) else Nudge.None
    }
}
