package com.example.normpix.backup

import com.example.normpix.backup.BackupFreshness.Nudge
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * When Home says "back up your data": never for an empty phone, a phone in its first days, a recent backup, a "Later" or a reminder that is
 * off; after the reminder's days it says so, more loudly once it is twice as old. Every time is given, so nothing depends on a clock.
 */
class BackupFreshnessTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L
    private val installed = now - 40 * day

    private fun nudge(
        last: Long = 0,
        installedAt: Long = installed,
        reminderDays: Int = 14,
        hasData: Boolean = true,
        snoozedUntil: Long = 0,
    ) = BackupFreshness.nudge(last, installedAt, now, reminderDays, hasData, snoozedUntil)

    @Test
    fun `a recent backup says nothing, an old one says how old`() {
        assertEquals(Nudge.None, nudge(last = now - 13 * day))
        assertEquals(Nudge.Stale(14, overdue = false), nudge(last = now - 14 * day))
        assertEquals(Nudge.Stale(27, overdue = false), nudge(last = now - 27 * day))
    }

    @Test
    fun `twice the reminder's age is overdue`() {
        assertEquals(Nudge.Stale(28, overdue = true), nudge(last = now - 28 * day))
        assertEquals(Nudge.Stale(60, overdue = true), nudge(last = now - 60 * day))
        // The user's own choice moves both lines.
        assertEquals(Nudge.Stale(7, overdue = false), nudge(last = now - 7 * day, reminderDays = 7))
        assertEquals(Nudge.Stale(14, overdue = true), nudge(last = now - 14 * day, reminderDays = 7))
        assertEquals(Nudge.None, nudge(last = now - 20 * day, reminderDays = 30))
    }

    @Test
    fun `no backup at all is left alone for the first days, then said`() {
        assertEquals(Nudge.None, nudge(installedAt = now - 2 * day))
        assertEquals(Nudge.Never(3), nudge(installedAt = now - 3 * day))
        assertEquals(Nudge.Never(40), nudge())
    }

    @Test
    fun `an unknown install time is not guessed at`() {
        assertEquals(Nudge.None, nudge(installedAt = 0))
        // ... but a backup's own age is still known without it.
        assertEquals(Nudge.Stale(20, overdue = false), nudge(last = now - 20 * day, installedAt = 0))
    }

    @Test
    fun `nothing worth keeping, a reminder that is off, or a Later say nothing`() {
        assertEquals(Nudge.None, nudge(hasData = false))
        assertEquals(Nudge.None, nudge(last = now - 90 * day, hasData = false))
        assertEquals(Nudge.None, nudge(reminderDays = 0))
        assertEquals(Nudge.None, nudge(last = now - 90 * day, reminderDays = 0))
        assertEquals(Nudge.None, nudge(last = now - 90 * day, snoozedUntil = now + 1))
        assertEquals(Nudge.Stale(90, overdue = true), nudge(last = now - 90 * day, snoozedUntil = now), "a Later that has ended")
    }

    @Test
    fun `a clock set back after the backup reads as just now`() {
        assertEquals(Nudge.None, nudge(last = now + 5 * day))
    }

    @Test
    fun `Later puts it off for three days`() {
        assertEquals(now + 3 * day, BackupFreshness.snoozeUntil(now))
    }

    @Test
    fun `a stored reminder length that is not offered falls back to two weeks, and off stays off`() {
        assertEquals(0, BackupFreshness.normalise(0))
        for (days in BackupFreshness.REMINDER_CHOICES) assertEquals(days, BackupFreshness.normalise(days))
        assertEquals(14, BackupFreshness.normalise(5))
        assertEquals(14, BackupFreshness.normalise(-3))
        assertEquals(14, BackupFreshness.normalise(365))
    }

    @Test
    fun `how long ago reads in words`() {
        assertEquals("today", BackupFreshness.ago(now - 3 * 60 * 60 * 1000, now))
        assertEquals("yesterday", BackupFreshness.ago(now - day, now))
        assertEquals("5 days ago", BackupFreshness.ago(now - 5 * day, now))
        assertEquals("today", BackupFreshness.ago(now + day, now), "a clock set back")
    }
}
